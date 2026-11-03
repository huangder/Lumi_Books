package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.epub.EpubSearchMatch
import com.huangder.lumibooks.util.kindle.KindleEpubCache
import com.huangder.lumibooks.util.kindle.KindleEpubLease
import com.huangder.lumibooks.util.kindle.KindleException
import com.huangder.lumibooks.util.kindle.KindleFailure
import com.huangder.lumibooks.util.kindle.KindleProbe
import java.io.File

/** Keeps classic MOBI's identity while exposing reconstructed KF8 through the EPUB engines. */
class KindleParser(private val context: Context) : BookParser, BookRenderSource, BookSearchSource {
    private var delegate: BookParser? = null
    private var publication: KindleEpubLease? = null
    internal val epubParser: EpubParser? get() = delegate as? EpubParser

    override var paragraphSpacingDp: Float = 0f
        set(value) { field = value; delegate?.paragraphSpacingDp = value }
    override var firstLineIndentChars: Float = 0f
        set(value) { field = value; delegate?.firstLineIndentChars = value }
    override var contentWidth: Int = 0
        set(value) { field = value; delegate?.contentWidth = value }
    override var contentHeight: Int = 0
        set(value) { field = value; delegate?.contentHeight = value }
    override var useEpubCss: Boolean = false
        set(value) { field = value; delegate?.useEpubCss = value }
    override var preserveEpubBackground: Boolean = true
        set(value) { field = value; delegate?.preserveEpubBackground = value }

    override fun parse(filePath: String): BookContent {
        close()
        try {
            val probe = BookFileAccess.openSeekable(context, filePath).use { KindleProbe.read(File(it.path)) }
            val path: String
            val parser: BookParser
            if (probe.needsReconstruction) {
                val lease = KindleEpubCache.acquire(context, filePath).also { publication = it }
                path = lease.file.absolutePath
                parser = EpubParser(context)
            } else {
                path = filePath
                parser = MobiParser(context)
            }
            delegate = parser
            parser.paragraphSpacingDp = paragraphSpacingDp
            parser.firstLineIndentChars = firstLineIndentChars
            parser.contentWidth = contentWidth
            parser.contentHeight = contentHeight
            parser.useEpubCss = useEpubCss
            parser.preserveEpubBackground = preserveEpubBackground
            return parser.parse(path)
        } catch (error: Exception) {
            close()
            if (error is KindleException) throw localized(error)
            throw error
        } catch (error: LinkageError) {
            close()
            throw localized(KindleException(KindleFailure.CONVERSION, error))
        }
    }

    override fun extractCoverPath(filePath: String): String? = try {
        val probe = BookFileAccess.openSeekable(context, filePath).use { KindleProbe.read(File(it.path)) }
        if (probe.needsReconstruction) KindleEpubCache.acquire(context, filePath).use { lease ->
            EpubParser(context).extractCoverPath(lease.file.absolutePath)
        } else MobiParser(context).extractCoverPath(filePath)
    } catch (_: Exception) { null } catch (_: LinkageError) { null }

    override fun getChapterContent(chapterIndex: Int): CharSequence = delegate?.getChapterContent(chapterIndex) ?: ""
    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String = delegate?.getChapterHtml(chapterIndex, optimizeLayout).orEmpty()
    override fun getChapterHtmlLight(chapterIndex: Int): String = delegate?.getChapterHtmlLight(chapterIndex).orEmpty()
    override fun getChapterCount(): Int = delegate?.getChapterCount() ?: 0
    override fun resolveLink(sourceChapterIndex: Int, href: String): BookLinkTarget? = delegate?.resolveLink(sourceChapterIndex, href)
    override fun isFootnoteHref(chapterIndex: Int, href: String): Boolean = delegate?.isFootnoteHref(chapterIndex, href) == true
    override fun resolveFootnoteText(sourceChapterIndex: Int, href: String): String? = delegate?.resolveFootnoteText(sourceChapterIndex, href)
    override fun clearHtmlCache() { delegate?.clearHtmlCache() }
    override fun openRenderSession(): BookRenderSession = (delegate as BookRenderSource).openRenderSession()
    override suspend fun searchBook(query: String, maxResults: Int): List<EpubSearchMatch> =
        (delegate as? BookSearchSource)?.searchBook(query, maxResults).orEmpty()

    override fun close() {
        try { delegate?.close() } finally {
            delegate = null
            publication?.close()
            publication = null
        }
    }

    private fun localized(error: KindleException): java.io.IOException {
        val resource = when (error.failure) {
            KindleFailure.ENCRYPTED -> R.string.kindle_error_encrypted
            KindleFailure.INVALID -> R.string.kindle_error_invalid
            KindleFailure.UNSUPPORTED -> R.string.kindle_error_unsupported
            KindleFailure.LIMIT -> R.string.kindle_error_limit
            KindleFailure.NO_SPACE -> R.string.kindle_error_no_space
            KindleFailure.IO -> R.string.kindle_error_io
            KindleFailure.CONVERSION -> R.string.kindle_error_conversion
        }
        return java.io.IOException(context.getString(resource), error)
    }
}

/** The native chapter fast paths also apply to Kindle books after reconstruction. */
internal val BookParser?.epubParserOrNull: EpubParser?
    get() = when (this) {
        is EpubParser -> this
        is KindleParser -> epubParser
        else -> null
    }
