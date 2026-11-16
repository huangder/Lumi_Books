package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.cache.ReaderMirrorLease
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.word.WordEpubCache
import com.huangder.lumibooks.util.word.WordException
import com.huangder.lumibooks.util.word.userMessage
import java.io.IOException

internal class WordParser(
    private val context: Context,
    private val extension: String,
    internal val epubParser: EpubParser = EpubParser(context)
) : BookParser by epubParser, BookRenderSource by epubParser, BookSearchSource by epubParser {
    private var publication: ReaderMirrorLease? = null

    override fun parse(filePath: String): BookContent {
        close()
        try {
            val lease = WordEpubCache.acquire(context, filePath, extension)
            publication = lease
            val content = epubParser.parse(lease.file.absolutePath)
            if (content.title != lease.file.nameWithoutExtension) return content
            val title = BookFileAccess.displayName(context, filePath)?.substringBeforeLast('.').orEmpty()
            return content.copy(title = title)
        } catch (error: Throwable) {
            close()
            if (error is WordException) throw IOException(error.userMessage(context), error)
            throw error
        }
    }

    // Word has no book-cover field. The bookshelf uses its normal generated or catalog cover.
    override fun extractCoverPath(filePath: String): String? = null

    override fun close() {
        try {
            epubParser.close()
        } finally {
            publication?.close()
            publication = null
        }
    }
}
