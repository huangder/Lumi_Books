package com.huangder.lumibooks.util.zip

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android's [ZipFile] rejects archives whose central directory repeats a name. EPUB and CBZ files
 * in the wild occasionally carry such duplicates (a second `mimetype` entry, a re-zipped page, a
 * duplicated folder), which would otherwise make the whole book unreadable.
 *
 * [prepare] returns the original path for healthy archives and otherwise builds a cached,
 * resource-identical copy with the later duplicates dropped. The user's file is never modified.
 */
object ZipCompatRepair {
    fun prepare(
        context: Context,
        sourcePath: String,
        cacheDirectoryName: String,
        cachedExtension: String,
        emptyArchiveMessage: String
    ): String {
        try {
            ZipFile(sourcePath).use { }
            return sourcePath
        } catch (error: ZipException) {
            if (!error.message.orEmpty().contains("duplicate", ignoreCase = true)) throw error
        }

        val source = File(sourcePath)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("${source.absolutePath}|${source.length()}|${source.lastModified()}".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(24)
        val directory = File(context.cacheDir, cacheDirectoryName).apply { mkdirs() }
        val target = File(directory, "$fingerprint.$cachedExtension")
        if (target.isFile && runCatching { ZipFile(target).use { } }.isSuccess) {
            return target.absolutePath
        }

        val temporary = File(directory, "$fingerprint.tmp")
        if (target.exists()) target.delete()
        if (temporary.exists()) temporary.delete()
        val seenNames = HashSet<String>()
        try {
            FileInputStream(source).buffered().use { fileInput ->
                ZipInputStream(fileInput).use { input ->
                    temporary.outputStream().buffered().use { fileOutput ->
                        ZipOutputStream(fileOutput).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val entry = input.nextEntry ?: break
                                val normalizedName = entry.name.replace('\\', '/')
                                if (seenNames.add(normalizedName)) {
                                    val copy = ZipEntry(normalizedName).apply {
                                        entry.comment?.let { comment = it }
                                        if (entry.time >= 0L) time = entry.time
                                    }
                                    output.putNextEntry(copy)
                                    if (!entry.isDirectory) {
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                    output.closeEntry()
                                }
                                input.closeEntry()
                            }
                        }
                    }
                }
            }
            check(seenNames.isNotEmpty()) { emptyArchiveMessage }
            check(temporary.renameTo(target)) { "Unable to finalize the repaired archive cache" }
            ZipFile(target).use { }
            return target.absolutePath
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }
}
