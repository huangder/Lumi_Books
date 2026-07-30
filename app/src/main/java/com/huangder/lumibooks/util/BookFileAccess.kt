package com.huangder.lumibooks.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Opens both app-managed file paths and Storage Access Framework document URIs.
 *
 * Android's ZIP/RandomAccessFile implementations reopen `/proc/self/fd/<fd>` paths.
 * On some ROMs that second open loses the SAF tree grant and is rejected by
 * MediaProvider. Seekable readers therefore use a short-lived cache file while
 * the database continues to keep the original document URI. The cache is
 * deleted when the parser closes; writable TXT leases explicitly write the
 * finished bytes back to the authorized original document.
 */
object BookFileAccess {
    private const val TAG = "BookFileAccess"

    fun isContentUri(location: String): Boolean =
        runCatching { Uri.parse(location).scheme.equals("content", ignoreCase = true) }.getOrDefault(false)

    fun openSeekable(context: Context, location: String, writable: Boolean = false): SeekableBookSource {
        if (!isContentUri(location)) return SeekableBookSource(location)

        val uri = Uri.parse(location)
        val cacheDirectory = File(context.cacheDir, "seekable_books").apply { mkdirs() }
        val suffix = queryDisplayName(context, uri)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
            ?.let { ".$it" }
            ?: ".book"
        val temporaryFile = File.createTempFile("document_", suffix, cacheDirectory)

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open book document")
            input.use { source ->
                temporaryFile.outputStream().buffered().use { target ->
                    source.copyTo(target)
                }
            }
            Log.d(TAG, "Opened SAF document through transient seekable cache: $uri")
            return SeekableBookSource(
                path = temporaryFile.absolutePath,
                temporaryFile = temporaryFile,
                writeBackContext = context.applicationContext.takeIf { writable },
                writeBackUri = uri.takeIf { writable }
            )
        } catch (error: Throwable) {
            temporaryFile.delete()
            Log.e(TAG, "Unable to prepare seekable SAF document: $uri", error)
            throw error
        }
    }

    fun openDescriptor(context: Context, location: String, writable: Boolean = false): ParcelFileDescriptor {
        return if (isContentUri(location)) {
            context.contentResolver.openFileDescriptor(Uri.parse(location), if (writable) "rw" else "r")
                ?: error("Unable to open book document")
        } else {
            ParcelFileDescriptor.open(
                File(location),
                if (writable) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY
            )
        }
    }

    fun size(context: Context, location: String): Long {
        if (!isContentUri(location)) return File(location).takeIf { it.exists() }?.length() ?: 0L
        val uri = Uri.parse(location)
        val queriedSize = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (queriedSize != null) return queriedSize.coerceAtLeast(0L)
        return runCatching { openDescriptor(context, location).use { it.statSize.coerceAtLeast(0L) } }.getOrDefault(0L)
    }

    fun readBytes(context: Context, location: String): ByteArray {
        openInputStream(context, location).use { input ->
            return input.readBytes()
        }
    }

    fun openInputStream(context: Context, location: String): InputStream {
        if (!isContentUri(location)) return File(location).inputStream().buffered()
        return context.contentResolver.openInputStream(Uri.parse(location))
            ?.buffered()
            ?: error("Unable to open book document")
    }

    fun displayName(context: Context, location: String): String? {
        if (!isContentUri(location)) return File(location).name
        return queryDisplayName(context, Uri.parse(location))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}

class SeekableBookSource internal constructor(
    val path: String,
    private val temporaryFile: File? = null,
    private val writeBackContext: Context? = null,
    private val writeBackUri: Uri? = null
) : Closeable {
    fun writeBack() {
        val context = writeBackContext ?: error("This seekable source is read-only")
        val uri = writeBackUri ?: error("This seekable source has no source document")
        val sourceFile = temporaryFile ?: error("This seekable source is not backed by a cache file")

        val output = runCatching { context.contentResolver.openOutputStream(uri, "rwt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Unable to write book document")
        FileInputStream(sourceFile).buffered().use { input ->
            output.buffered().use { target -> input.copyTo(target) }
        }
    }

    override fun close() {
        temporaryFile?.delete()
    }
}

