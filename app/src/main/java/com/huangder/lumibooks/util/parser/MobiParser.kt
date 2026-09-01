package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.SeekableBookSource
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.epub.BookTextSearch
import com.huangder.lumibooks.util.epub.EpubSearchMatch
import com.huangder.lumibooks.util.epub.MobiFile
import com.huangder.lumibooks.util.epub.MobiRawml
import com.huangder.lumibooks.util.epub.MobiRenderSession
import com.huangder.lumibooks.util.epub.MobiText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File
import java.nio.charset.Charset
import kotlin.math.roundToInt

/**
 * Classic MOBI7 parser (reader-layout identity).
 *
 * parse() opens a seekable lease (shared with [MobiRenderSession]) and splits
 * the rawml into chapters heuristically. getChapterContent() returns a Spanned
 * for the Canvas engine, preserving headings, alignment, font sizes/colors and
 * images. getChapterHtml() returns a full HTML document with base64-embedded
 * images for compatibility paths. The book-layout identity lives in
 * [MobiRenderSession] via [openRenderSession].
 */
class MobiParser(private val context: Context? = null) : BookParser, BookRenderSource, BookSearchSource {

    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    override var useEpubCss: Boolean = false

    private var mobiFilePath: String = ""
    private var sourceLease: SeekableBookSource? = null
    private var mobiFile: MobiFile? = null
    private var activeSession: MobiRenderSession? = null

    // Rawml (text records concatenated + decompressed) with any leading junk stripped.
    private var rawmlBytes: ByteArray = ByteArray(0)
    // Byte offset stripped from the decompressed text so filepos values map into rawmlBytes.
    private var rawmlStartAdjustment: Int = 0
    private var charset: Charset = Charsets.UTF_8

    private var chapters: List<Chapter> = emptyList()
    private var chapterRanges: List<Pair<Int, Int>> = emptyList()

    private val fragmentCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val htmlCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val contentCache = java.util.concurrent.ConcurrentHashMap<Int, CharSequence>()

    override fun parse(filePath: String): BookContent {
        close()
        val lease = context?.let { BookFileAccess.openSeekable(it, filePath) }
        sourceLease = lease
        mobiFilePath = lease?.path ?: filePath
        val file = openMobiFile(mobiFilePath)
        mobiFile = file

        val header = file.header
        val raw = try {
            file.decompressedText()
        } catch (error: RuntimeException) {
            throw localizedMobiError(error)
        }
        val (bytes, adjustment) = locateRawmlStart(raw)
        rawmlBytes = bytes
        rawmlStartAdjustment = adjustment
        charset = MobiText.resolveCharset(
            rawmlBytes.copyOfRange(0, minOf(rawmlBytes.size, 4096)),
            header.textEncoding
        )

        val ranges = MobiRawml.splitChapters(rawmlBytes)
        val keptRanges = ArrayList<Pair<Int, Int>>(ranges.size)
        val keptFragments = ArrayList<String>(ranges.size)
        ranges.forEachIndexed { index, range ->
            val fragment = readerFragment(range, index)
            if (fragment.isNotBlank() || keptRanges.isEmpty()) {
                keptRanges += range
                keptFragments += fragment
            }
        }
        chapterRanges = keptRanges
        fragmentCache.clear()
        keptFragments.forEachIndexed { index, fragment ->
            fragmentCache[index] = fragment
        }
        chapters = keptFragments.mapIndexed { index, fragment ->
            Chapter(
                index = index,
                title = MobiRawml.chapterTitle(
                    fragment,
                    context?.getString(R.string.chapter_number, index + 1) ?: "Chapter ${index + 1}"
                ),
                content = "",
                htmlContent = ""
            )
        }

        val fallbackTitle = header.titleOverride.ifBlank { header.fullName }
            .ifBlank { File(mobiFilePath).nameWithoutExtension }
        return BookContent(
            title = fallbackTitle,
            author = header.author,
            chapters = chapters,
            coverPath = extractCover(file, filePath),
            tocEntries = chapters.map { TocEntry(title = it.title, level = 1, chapterIndex = it.index) }
        )
    }

    override fun getChapterCount(): Int = chapters.size

    override fun getChapterContent(chapterIndex: Int): CharSequence {
        contentCache[chapterIndex]?.let { return it }
        if (chapterIndex !in chapters.indices) return ""
        val result = try {
            val spanned = htmlToSpanned(getFragment(chapterIndex))
            val formatted = applyParagraphFormatting(spanned)
            val trimmed = trimTrailingNewlines(formatted)
            if (trimmed.isBlank()) SpannableString(" ") else trimmed
        } catch (error: Throwable) {
            android.util.Log.e("MobiParser", "getChapterContent failed for $chapterIndex", error)
            ""
        }
        contentCache[chapterIndex] = result
        return result
    }

    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        htmlCache[chapterIndex]?.let { return it }
        if (chapterIndex !in chapters.indices) return ""
        val fragment = embedImagesAsDataUri(getFragment(chapterIndex))
        val css = if (optimizeLayout) {
            "body { margin: 0; padding: 0; } img { max-width: 100%; height: auto; }"
        } else {
            ""
        }
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>")
            if (css.isNotBlank()) append("<style>$css</style>")
            append("</head><body>")
            append(fragment)
            append("</body></html>")
        }
        htmlCache[chapterIndex] = html
        return html
    }

    override fun resolveLink(sourceChapterIndex: Int, href: String): BookLinkTarget? {
        val trimmed = href.trim()
        if (trimmed.isBlank()) return null
        val filepos = when {
            trimmed.startsWith("filepos:", ignoreCase = true) -> trimmed.substringAfter(':').toLongOrNull()
            trimmed.startsWith("filepos=", ignoreCase = true) -> trimmed.substringAfter('=').toLongOrNull()
            else -> trimmed.toLongOrNull()
        } ?: return null
        val adjusted = filepos + rawmlStartAdjustment
        if (adjusted < 0) return null
        val chapter = fileposToChapter(adjusted) ?: return null
        val range = chapterRanges.getOrNull(chapter) ?: return null
        val charOffset = MobiRawml.byteToCharOffset(rawmlBytes, range, charset, adjusted.toInt())
        return BookLinkTarget(chapter, charOffset)
    }

    override fun clearHtmlCache() {
        fragmentCache.clear()
        htmlCache.clear()
        contentCache.clear()
    }

    private fun openMobiFile(path: String): MobiFile = try {
        MobiFile.open(path)
    } catch (error: RuntimeException) {
        throw localizedMobiError(error)
    }

    private fun localizedMobiError(error: RuntimeException): RuntimeException {
        val message = when (error.message) {
            MobiFile.UNSUPPORTED_COMPRESSION_MESSAGE -> context?.getString(
                R.string.mobi_error_unsupported_compression
            ) ?: "This MOBI uses unsupported KF8/HUFF-CDIC compression"
            MobiFile.DRM_MESSAGE -> context?.getString(R.string.mobi_error_drm)
                ?: "Encrypted books are not supported"
            else -> context?.getString(R.string.mobi_error_invalid)
                ?: "The MOBI file is invalid or damaged"
        }
        return when (error) {
            is UnsupportedOperationException -> UnsupportedOperationException(message, error)
            else -> IllegalArgumentException(message, error)
        }
    }

    override fun extractCoverPath(filePath: String): String? {
        val ctx = context ?: return null
        var file: MobiFile? = null
        return try {
            val lease = BookFileAccess.openSeekable(ctx, filePath)
            try {
                file = MobiFile.open(lease.path)
                extractCover(file, filePath)
            } finally {
                lease.close()
            }
        } catch (error: Throwable) {
            android.util.Log.w("MobiParser", "extractCoverPath failed", error)
            null
        } finally {
            runCatching { file?.close() }
        }
    }

    override fun openRenderSession(): MobiRenderSession {
        val file = mobiFile ?: error("MOBI has not been parsed yet")
        return MobiRenderSession(this, file).also { activeSession = it }
    }

    override suspend fun searchBook(query: String, maxResults: Int): List<EpubSearchMatch> =
        BookTextSearch.collect(
            chapterCount = chapters.size,
            query = query,
            maxResults = maxResults,
            chapterText = { index -> getSearchText(index) },
            chapterHref = { index -> chapterHrefName(index) }
        )

    override fun close() {
        activeSession?.let { session -> runCatching { session.close() } }
        activeSession = null
        runCatching { mobiFile?.close() }
        mobiFile = null
        runCatching { sourceLease?.close() }
        sourceLease = null
        fragmentCache.clear()
        htmlCache.clear()
        contentCache.clear()
    }

    // -- internal accessors shared with MobiRenderSession --

    internal val sessionRawml: ByteArray get() = rawmlBytes
    internal val sessionChapterRanges: List<Pair<Int, Int>> get() = chapterRanges
    internal val sessionCharset: Charset get() = charset
    internal val sessionFile: MobiFile? get() = mobiFile

    internal fun chapterHrefName(chapterIndex: Int): String =
        if (chapterIndex in chapters.indices) "chapter-${chapterIndex.toString().padStart(3, '0')}.html" else ""

    /** recindex (1-based) -> absolute PDB record index of the image. */
    internal fun imageRecordIndex(recindex: Int): Int? {
        if (recindex <= 0) return null
        val file = mobiFile ?: return null
        if (file.header.firstImageIndex <= 0) return null
        val index = file.header.firstImageIndex + recindex - 1
        return index.takeIf { it in file.records.indices }
    }

    /** Maps a rawml byte offset (already adjusted) to a chapter index. */
    internal fun fileposToChapter(byteOffset: Long): Int? {
        if (byteOffset < 0 || chapterRanges.isEmpty()) return null
        for ((index, range) in chapterRanges.withIndex()) {
            if (byteOffset >= range.first && byteOffset < range.second) return index
        }
        return chapterRanges.last().second.takeIf { byteOffset >= it }?.let { chapterRanges.lastIndex }
    }

    /** Maps a rawml `filepos` value (relative to the rawml start) to a chapter index. */
    internal fun sessionFileposToChapter(filepos: Long): Int? =
        fileposToChapter(filepos + rawmlStartAdjustment)

    internal fun onSessionClosed(session: MobiRenderSession) {
        if (activeSession === session) activeSession = null
    }

    // -- fragment helpers --

    private fun readerFragment(range: Pair<Int, Int>, chapterIndex: Int): String = MobiRawml.chapterFragment(
        rawml = rawmlBytes,
        range = range,
        chapterIndex = chapterIndex,
        charset = charset,
        resolveImage = { recindex -> "recindex:$recindex" },
        resolveLink = { filepos -> "filepos:$filepos" }
    )

    private fun getFragment(chapterIndex: Int): String {
        fragmentCache[chapterIndex]?.let { return it }
        val range = chapterRanges.getOrNull(chapterIndex) ?: return ""
        val fragment = readerFragment(range, chapterIndex)
        fragmentCache[chapterIndex] = fragment
        return fragment
    }

    private fun getSearchText(chapterIndex: Int): String? {
        if (chapterIndex !in chapters.indices) return null
        return MobiRawml.searchText(getFragment(chapterIndex))
    }

    private fun embedImagesAsDataUri(fragment: String): String {
        val file = mobiFile ?: return fragment
        return fragment.replace(Regex("""<img\b[^>]*src="recindex:(\d+)"[^>]*>""", RegexOption.IGNORE_CASE)) { match ->
            val recindex = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val record = imageRecordIndex(recindex) ?: return@replace match.value
            val bytes = file.imageRecordBytes(record) ?: return@replace match.value
            if (bytes.isEmpty() || bytes.size > MAX_EMBEDDED_IMAGE_BYTES) return@replace match.value
            val mime = detectImageMime(bytes)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            match.value.replace(Regex("""src="recindex:\d+""""), "src=\"data:$mime;base64,$base64\"")
        }
    }

    private fun locateRawmlStart(bytes: ByteArray): Pair<ByteArray, Int> {
        val limit = minOf(bytes.size, 1 shl 15)
        val html = MobiRawml.indexOfAscii(bytes, "<html", 0, limit)
        val xml = MobiRawml.indexOfAscii(bytes, "<?xml", 0, limit)
        val candidates = listOfNotNull(html.takeIf { it >= 0 }, xml.takeIf { it >= 0 })
        val start = candidates.minOrNull() ?: return bytes to 0
        if (start <= 0) return bytes to 0
        return bytes.copyOfRange(start, bytes.size) to start
    }

    private fun extractCover(file: MobiFile, sourceKey: String): String? {
        val ctx = context ?: return null
        val recordIndex = file.coverRecordIndex() ?: return null
        val bytes = file.imageRecordBytes(recordIndex) ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
        val coversDir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val coverFile = File(coversDir, "${sourceKey.hashCode()}.jpg")
        return try {
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (error: Throwable) {
            android.util.Log.w("MobiParser", "write cover failed", error)
            null
        }
    }

    // -- reader-layout Spanned building --

    internal fun htmlToSpanned(fragment: String): Spanned {
        val document = Jsoup.parseBodyFragment(fragment)
        val builder = SpannableStringBuilder()
        val pageWidth = if (contentWidth > 0) {
            contentWidth
        } else {
            val dm = context?.resources?.displayMetrics
                ?: android.content.res.Resources.getSystem().displayMetrics
            val density = dm.density
            (dm.widthPixels - (44 * density * 2).toInt()).coerceAtLeast(1)
        }
        appendChildren(document.body(), builder, pageWidth)
        return builder
    }

    private fun appendChildren(parent: Node, builder: SpannableStringBuilder, pageWidth: Int) {
        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> builder.append(node.wholeText)
                is Element -> appendElement(node, builder, pageWidth)
                else -> Unit
            }
        }
    }

    private fun appendElement(element: Element, builder: SpannableStringBuilder, pageWidth: Int) {
        val tag = element.tagName().lowercase()
        when (tag) {
            "img" -> {
                val source = element.attr("src")
                val recindex = source.removePrefix("recindex:").toIntOrNull()
                if (recindex != null) {
                    val drawable = imageDrawable(recindex, pageWidth)
                    if (drawable != null) {
                        val start = builder.length
                        builder.append("\uFFFC")
                        builder.setSpan(
                            ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                            start, start + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            "br" -> if (builder.isEmpty() || builder.last() != '\n') builder.append('\n')
            "b", "strong" -> {
                val start = builder.length
                appendChildren(element, builder, pageWidth)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "i", "em" -> {
                val start = builder.length
                appendChildren(element, builder, pageWidth)
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "u" -> {
                val start = builder.length
                appendChildren(element, builder, pageWidth)
                builder.setSpan(UnderlineSpan(), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "font" -> appendFontElement(element, builder, pageWidth)
            "p", "div", "blockquote", "li", "h1", "h2", "h3", "h4", "h5", "h6" ->
                appendBlockElement(element, builder, pageWidth)
            else -> appendChildren(element, builder, pageWidth)
        }
    }

    private fun appendFontElement(element: Element, builder: SpannableStringBuilder, pageWidth: Int) {
        val start = builder.length
        appendChildren(element, builder, pageWidth)
        if (start == builder.length) return
        val size = element.attr("size").toIntOrNull()
        if (size != null && size in 1..7) {
            builder.setSpan(
                RelativeSizeSpan(FONT_SIZE_RATIOS.getValue(size)),
                start, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val colorText = element.attr("color").trim()
        if (colorText.isNotBlank()) {
            val color = runCatching { android.graphics.Color.parseColor(colorText) }.getOrNull()
            if (color != null) {
                builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        when (element.attr("face").lowercase()) {
            "serif" -> builder.setSpan(TypefaceSpan("serif"), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            "sans-serif", "sans" -> builder.setSpan(TypefaceSpan("sans-serif"), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            "monospace", "mono" -> builder.setSpan(TypefaceSpan("monospace"), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun appendBlockElement(element: Element, builder: SpannableStringBuilder, pageWidth: Int) {
        if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
        val start = builder.length
        val tag = element.tagName().lowercase()
        if (tag.startsWith("h") && tag.length == 2) {
            val level = tag[1].digitToIntOrNull()
            if (level != null) {
                appendChildren(element, builder, pageWidth)
                if (start < builder.length) {
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(
                        RelativeSizeSpan(HEADING_SIZE_RATIOS.getValue(level.coerceIn(1, 6))),
                        start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                appendChildren(element, builder, pageWidth)
            }
        } else {
            appendChildren(element, builder, pageWidth)
        }
        if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
        val alignment = when (element.attr("align").lowercase()) {
            "center" -> android.text.Layout.Alignment.ALIGN_CENTER
            "right" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
            else -> null
        }
        if (alignment != null && start < builder.length) {
            // SPAN_PARAGRAPH would grow at the end while later blocks are appended,
            // leaking a centered heading alignment across the rest of the chapter.
            builder.setSpan(
                AlignmentSpan.Standard(alignment),
                start, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun imageDrawable(recindex: Int, pageWidth: Int): Drawable? {
        val file = mobiFile ?: return null
        val record = imageRecordIndex(recindex) ?: return null
        val bytes = file.imageRecordBytes(record) ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            val originalWidth = opts.outWidth
            val originalHeight = opts.outHeight
            val sample = ReaderImageSizing.decodeSampleSize(
                originalWidth,
                originalHeight,
                pageWidth
            )
            opts.inSampleSize = sample
            opts.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val imageBounds = ReaderImageSizing.bounds(originalWidth, originalHeight, pageWidth)
                ?: return null
            val ratio = imageBounds.width.toFloat() / bitmap.width.coerceAtLeast(1)
            val drawable = BitmapDrawable(null, bitmap)
            drawable.setBounds(
                0,
                0,
                imageBounds.width,
                (bitmap.height * ratio).toInt().coerceAtLeast(1)
            )
            drawable
        } catch (error: Throwable) {
            android.util.Log.w("MobiParser", "decode image recindex=$recindex failed", error)
            null
        }
    }

    private fun applyParagraphFormatting(text: CharSequence): CharSequence {
        val ssb = SpannableStringBuilder(text)
        if (paragraphSpacingDp <= 0f) {
            var i = ssb.length - 1
            while (i > 0) {
                if (ssb[i] == '\n' && ssb[i - 1] == '\n') ssb.delete(i, i + 1)
                i--
            }
        }

        val density = context?.resources?.displayMetrics?.density ?: 2.75f
        val spacingPx = if (paragraphSpacingDp > 0f) (paragraphSpacingDp * density).toInt() else 0

        if (spacingPx > 0) {
            val newlinePositions = ArrayList<Int>()
            for (j in 0 until ssb.length) {
                if (ssb[j] == '\n') newlinePositions.add(j)
            }
            for (nl in newlinePositions.asReversed()) {
                ssb.insert(nl + 1, "\n")
                ssb.setSpan(
                    ParagraphLineHeightSpan(spacingPx),
                    nl + 1, nl + 2,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // First-line indent as full-width spaces instead of LeadingMarginSpan.
        // Per-page rendering slices the chapter with subSequence(); a LeadingMarginSpan
        // crossing the page boundary gets its start clamped to 0, so the page's first
        // line is wrongly treated as a paragraph start and shifts right, which breaks
        // the pagination/rendering consistency (clipped text + page-turn jitter).
        val indentCount = firstLineIndentChars.roundToInt().coerceIn(0, 4)
        if (indentCount > 0) {
            val paragraphStarts = ArrayList<Int>()
            var paragraphStart = 0
            for (j in 0..ssb.length) {
                if (j == ssb.length || ssb[j] == '\n') {
                    val hasImage = ssb.getSpans(paragraphStart, j, ImageSpan::class.java).isNotEmpty()
                    if (paragraphStart < j && !hasImage) paragraphStarts += paragraphStart
                    paragraphStart = j + 1
                }
            }
            val indentText = "\u3000".repeat(indentCount)
            for (start in paragraphStarts.asReversed()) {
                ssb.insert(start, indentText)
            }
        }
        return ssb
    }

    private fun trimTrailingNewlines(text: CharSequence): CharSequence {
        var end = text.length
        while (end > 0 && text[end - 1] == '\n') end--
        if (end < text.length) end++
        return if (end == text.length) text else text.subSequence(0, end)
    }

    private class ParagraphLineHeightSpan(val extraHeightPx: Int) : android.text.style.LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            v: Int,
            fm: android.graphics.Paint.FontMetricsInt
        ) {
            val isSpacerLine = start < end && (start until end).all { index -> text[index] == '\n' }
            if (!isSpacerLine) return
            val lineHeight = fm.descent - fm.ascent
            if (lineHeight <= 0) return
            val targetExtra = (extraHeightPx * lineHeight.toFloat() / lineHeight.coerceAtLeast(1)).toInt()
            fm.descent += targetExtra.coerceAtLeast(0)
        }
    }

    private fun detectImageMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() ->
            "image/gif"
        else -> "image/jpeg"
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
        const val MAX_COVER_BYTES = 64 * 1024 * 1024
        const val MAX_EMBEDDED_IMAGE_BYTES = 16 * 1024 * 1024
        val FONT_SIZE_RATIOS = mapOf(1 to 0.6f, 2 to 0.75f, 3 to 1.0f, 4 to 1.3f, 5 to 1.6f, 6 to 1.9f, 7 to 2.2f)
        val HEADING_SIZE_RATIOS = mapOf(1 to 1.6f, 2 to 1.4f, 3 to 1.25f, 4 to 1.1f, 5 to 1.0f, 6 to 0.9f)
    }
}
