package com.huangder.lumibooks.util.word

import android.content.Context
import android.net.Uri
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.cache.ReaderMirrorLease
import com.huangder.lumibooks.util.epub.EpubPackageReader
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object WordEpubCache {
    const val DIRECTORY = "word_epubs"
    private const val VERSION = "v2"
    private const val MAX_CACHED_BOOKS = 3
    private const val MAX_CACHE_BYTES = 192L * 1024 * 1024
    private val pins = mutableMapOf<File, Int>()
    private val pendingDeletion = mutableSetOf<File>()

    fun sourceHash(context: Context, location: String): String = try {
        readSource(context, location, DISCARD)
    } catch (error: WordException) {
        throw error
    } catch (error: IOException) {
        throw WordException(WordFailure.IO, error)
    } catch (error: SecurityException) {
        throw WordException(WordFailure.IO, error)
    }

    @Synchronized
    fun acquire(context: Context, location: String, extension: String): ReaderMirrorLease {
        checkWordCancellation()
        val root = File(context.cacheDir, DIRECTORY)
        try {
            Files.createDirectories(root.toPath())
            val stage = Files.createTempDirectory(root.toPath(), ".stage-").toFile()
            try {
                // Hash the same byte snapshot consumed by the converter, including for SAF files.
                val source = File(stage, "source.$extension")
                val hash = source.outputStream().buffered().use { readSource(context, location, it) }
                val target = File(root, "$hash-$extension-$VERSION.epub")
                if (!isReadable(target)) {
                    if (target in pins) throw WordException(WordFailure.IO)
                    val output = File(stage, "publication.epub")
                    WordConverter.convert(source, extension, hash, stage, output)
                    if (!isReadable(output)) throw WordException(WordFailure.CONVERSION)
                    checkWordCancellation()
                    Files.move(output.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                }
                target.setLastModified(System.currentTimeMillis())
                pins[target] = (pins[target] ?: 0) + 1
                trim(root)
                return ReaderMirrorLease(target) { release(target, root) }
            } finally {
                stage.deleteRecursively()
            }
        } catch (error: WordException) {
            throw error
        } catch (error: IOException) {
            throw WordException(WordFailure.IO, error)
        } catch (error: SecurityException) {
            throw WordException(WordFailure.IO, error)
        }
    }

    fun metadata(context: Context, location: String, extension: String, expectedHash: String): WordMetadata =
        acquire(context, location, extension).use { lease ->
            if (!lease.file.name.startsWith("$expectedHash-")) throw WordException(WordFailure.IO)
            val book = EpubPackageReader.read(lease.file.absolutePath, fallbackTitle = "")
            WordMetadata(book.title.takeIf(String::isNotBlank), book.author.takeIf(String::isNotBlank))
        }

    @Synchronized
    fun clear(context: Context) {
        File(context.cacheDir, DIRECTORY).listFiles()?.forEach { file ->
            if (file in pins) pendingDeletion.add(file) else file.deleteRecursively()
        }
    }

    @Synchronized
    private fun release(file: File, root: File) {
        val count = pins.getValue(file) - 1
        if (count == 0) {
            pins.remove(file)
            if (pendingDeletion.remove(file)) file.delete()
        } else {
            pins[file] = count
        }
        trim(root)
    }

    private fun trim(root: File) {
        var bytes = 0L
        root.listFiles { file -> file.extension == "epub" }?.sortedByDescending(File::lastModified)
            ?.forEachIndexed { index, file ->
                bytes += file.length()
                if ((index >= MAX_CACHED_BOOKS || bytes > MAX_CACHE_BYTES) && file !in pins) file.delete()
            }
    }

    private fun isReadable(file: File): Boolean = file.isFile && runCatching {
        val book = EpubPackageReader.read(file.absolutePath)
        book.spine.isNotEmpty() && ZipFile(file).use { zip ->
            book.manifest.values.all { zip.getEntry(it.fullPath)?.isDirectory == false }
        }
    }.getOrDefault(false)

    private fun readSource(context: Context, location: String, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = DigestOutputStream(output, digest)
        val path = if (location.startsWith("file:", ignoreCase = true)) {
            Uri.parse(location).path ?: throw WordException(WordFailure.IO)
        } else location
        val source = try {
            BookFileAccess.openInputStream(context, path)
        } catch (error: IllegalStateException) {
            throw WordException(WordFailure.IO, error)
        }
        source.buffered().use { input ->
            if (copyWordBytes(input, sink, MAX_WORD_BYTES) == 0L) throw WordException(WordFailure.INVALID)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val DISCARD = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}

internal fun WordException.userMessage(context: Context): String = context.getString(when (reason) {
    WordFailure.INVALID -> R.string.word_error_invalid
    WordFailure.ENCRYPTED -> R.string.word_error_encrypted
    WordFailure.UNSUPPORTED -> R.string.word_error_unsupported
    WordFailure.LIMIT -> R.string.word_error_limit
    WordFailure.IO -> R.string.word_error_io
    WordFailure.CONVERSION -> R.string.word_error_conversion
})

private val wordCacheTitle = Regex("[0-9a-f]{64}-docx?-v[0-9]+")

internal fun isWordCacheTitle(title: String): Boolean = wordCacheTitle.matches(title)
