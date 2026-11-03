package com.huangder.lumibooks.util.kindle

import android.content.Context
import com.huangder.lumibooks.util.BookFileAccess
import java.io.Closeable
import java.io.File

/** A comic decoder owns its own publication pin, independent of the text parser lifecycle. */
internal class ReadableEpubSource private constructor(val path: String, private val lease: Closeable) : Closeable {
    override fun close() = lease.close()

    companion object {
        fun open(context: Context, location: String): ReadableEpubSource {
            val source = BookFileAccess.openSeekable(context, location)
            try {
                val signature = File(source.path).inputStream().use { input -> ByteArray(2).also { input.read(it) } }
                if (signature.contentEquals(byteArrayOf(0x50, 0x4b))) return ReadableEpubSource(source.path, source)
                val publication = KindleEpubCache.acquire(context, location)
                source.close()
                return ReadableEpubSource(publication.file.absolutePath, publication)
            } catch (error: Throwable) {
                source.close()
                throw error
            }
        }
    }
}
