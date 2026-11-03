package com.huangder.lumibooks.util.kindle

import android.content.Context
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.epub.EpubPackageReader
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** All derived publications are disposable; library records always retain the original file. */
internal object KindleEpubCache {
    const val DIRECTORY = "kindle_epubs"
    const val CONVERTER_VERSION = "libmobi012-v1"
    private val stores = mutableMapOf<String, KindleConversionStore>()

    @Synchronized private fun store(context: Context): KindleConversionStore {
        val root = File(context.cacheDir, DIRECTORY)
        return stores.getOrPut(root.absolutePath) {
            KindleConversionStore(root, reconstruct = { source, target -> KindleNative.reconstruct(source, target) })
        }
    }

    fun acquire(context: Context, location: String): KindleEpubLease =
        BookFileAccess.openSeekable(context, location).use { source ->
            store(context).acquire(File(source.path))
        }

    fun clear(context: Context) = store(context).clear()
}

internal class KindleEpubLease(val file: File, private val release: () -> Unit) : Closeable {
    private var closed = false
    @Synchronized override fun close() {
        if (!closed) { closed = true; release() }
    }
}

/** Serializes memory-intensive native reconstruction and pins archives while readers use them. */
internal class KindleConversionStore(
    private val root: File,
    private val reconstruct: (String, String) -> Int,
    private val maxBytes: Long = 256L * 1024 * 1024,
    private val maxBooks: Int = 3,
    private val usableBytes: () -> Long = { root.usableSpace }
) {
    private val pins = mutableMapOf<String, Int>()
    private val pendingDeletion = mutableSetOf<String>()

    @Synchronized fun acquire(source: File): KindleEpubLease {
        KindleProbe.read(source)
        if (!root.isDirectory && !root.mkdirs()) throw KindleException(KindleFailure.IO)
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val key = digest.digest().joinToString("") { "%02x".format(it) } + "-${KindleEpubCache.CONVERTER_VERSION}"
        val target = File(root, "$key.epub")
        if (!valid(target)) {
            if ((pins[target.name] ?: 0) > 0) throw KindleException(KindleFailure.IO)
            convert(source, target)
        }
        target.setLastModified(System.currentTimeMillis())
        pins[target.name] = (pins[target.name] ?: 0) + 1
        trim()
        return KindleEpubLease(target) {
            synchronized(this) {
                val count = (pins[target.name] ?: 1) - 1
                if (count <= 0) {
                    pins.remove(target.name)
                    if (pendingDeletion.remove(target.name)) target.delete()
                } else pins[target.name] = count
                trim()
            }
        }
    }

    @Synchronized fun clear() {
        root.listFiles()?.forEach { file ->
            if ((pins[file.name] ?: 0) > 0) pendingDeletion += file.name
            else file.deleteRecursively()
        }
    }

    private fun trim() {
        var retained = 0
        var bytes = 0L
        root.listFiles()?.filter { it.extension == "epub" }?.sortedByDescending(File::lastModified)?.forEach {
            retained++
            bytes += it.length()
            if ((retained > maxBooks || bytes > maxBytes) && (pins[it.name] ?: 0) == 0) it.delete()
        }
    }

    private fun convert(source: File, target: File) {
        if (usableBytes() < source.length() * 2 + 8L * 1024 * 1024) {
            throw KindleException(KindleFailure.NO_SPACE)
        }
        val work = File(root, ".stage-${UUID.randomUUID()}")
        val resources = File(work, "resources")
        val epub = File(work, "publication.epub")
        try {
            if (!resources.mkdirs()) throw KindleException(KindleFailure.IO)
            checkCancellation()
            val result = reconstruct(source.absolutePath, resources.absolutePath)
            if (result != 0) throw KindleException(when (result) {
                1 -> KindleFailure.IO
                2 -> KindleFailure.INVALID
                3 -> KindleFailure.ENCRYPTED
                4 -> KindleFailure.UNSUPPORTED
                5 -> KindleFailure.LIMIT
                else -> KindleFailure.CONVERSION
            })
            checkCancellation()
            packageEpub(resources, epub)
            if (!valid(epub)) throw KindleException(KindleFailure.CONVERSION)
            Files.move(epub.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: java.io.IOException) {
            if (error is KindleException) throw error
            throw KindleException(if (usableBytes() < 8L * 1024 * 1024) KindleFailure.NO_SPACE else KindleFailure.IO, error)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun packageEpub(resources: File, target: File) {
        val files = mutableListOf<File>()
        var size = 0L
        resources.walkTopDown().filter(File::isFile).forEach { file ->
            checkCancellation()
            size += file.length()
            if (files.size >= 50_000 || size > 512L * 1024 * 1024) throw KindleException(KindleFailure.LIMIT)
            if (!file.canonicalPath.startsWith(resources.canonicalPath + File.separator)) throw KindleException(KindleFailure.INVALID)
            files += file
        }
        files.sortBy { it.relativeTo(resources).invariantSeparatorsPath }
        val opf = files.singleOrNull { it.extension == "opf" } ?: throw KindleException(KindleFailure.CONVERSION)
        val opfPath = opf.relativeTo(resources).invariantSeparatorsPath
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value; time = 0
            })
            zip.write(mime); zip.closeEntry()
            val container = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles></container>"""
            zip.putNextEntry(ZipEntry("META-INF/container.xml").apply { time = 0 })
            zip.write(container.toByteArray()); zip.closeEntry()
            files.forEach { file ->
                checkCancellation()
                val path = file.relativeTo(resources).invariantSeparatorsPath
                if (path == "mimetype" || path == "META-INF/container.xml") return@forEach
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    private fun valid(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return runCatching {
            val book = EpubPackageReader.read(file.absolutePath)
            ZipFile(file).use { zip ->
                book.manifest.values.all { zip.getEntry(it.fullPath)?.isDirectory == false }
            }
        }.getOrDefault(false)
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Kindle preparation cancelled")
    }
}
