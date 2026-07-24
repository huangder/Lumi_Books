package com.huangder.lumibooks.ui.bookshelf

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.pdfconversion.PdfTextExtractor
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.ui.reader.engine.calculateReaderVerticalBalanceOffset
import com.huangder.lumibooks.ui.reader.shouldStyleTxtChapterTitle
import com.huangder.lumibooks.util.parser.BookParser
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BookNotesExportDocument(
    val fileName: String,
    val text: String
)

class BookNotesExportBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val pdfTextExtractor: PdfTextExtractor
) {

    suspend fun build(
        book: Book,
        bookmarks: List<Bookmark>,
        notes: List<Note>
    ): BookNotesExportDocument = withContext(Dispatchers.IO) {
        val parsed = if (book.format == BookFormat.PDF) {
            null
        } else {
            runCatching {
                val parser = BookParserFactory.createParser(book.format, context)
                parser to parser.parse(book.filePath)
            }.getOrNull()
        }
        val chapterTitles = parsed?.second?.chapters
            ?.associate { it.index to it.title }
            .orEmpty()
        val exportChapterTitles = buildMap {
            putAll(chapterTitles)
            if (book.format == BookFormat.PDF) {
                (bookmarks.map { it.chapterIndex } + notes.map { it.chapterIndex })
                    .distinct()
                    .forEach { pageIndex -> put(pageIndex, "第${pageIndex + 1}页") }
            }
        }
        val pageTexts = when (book.format) {
            BookFormat.PDF -> extractPdfBookmarkPages(book, bookmarks)
            BookFormat.EPUB, BookFormat.TXT -> parsed?.first?.let { parser ->
                layoutBookmarkPages(parser, book.format, chapterTitles, bookmarks)
            }.orEmpty()
        }
        val exportBookmarks = bookmarks.map { bookmark ->
            BookmarkExportItem(
                bookmark = bookmark,
                chapterTitle = exportChapterTitles[bookmark.chapterIndex]
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: if (book.format == BookFormat.PDF) {
                        "第${bookmark.chapterIndex + 1}页"
                    } else {
                        "第${bookmark.chapterIndex + 1}章"
                    },
                pageText = pageTexts[bookmark.id]
            )
        }

        BookNotesExportDocument(
            fileName = BookNotesExportFormatter.suggestedFileName(book.title),
            text = BookNotesExportFormatter.format(
                bookTitle = book.title,
                notes = notes,
                bookmarks = exportBookmarks,
                chapterTitles = exportChapterTitles
            )
        )
    }

    private suspend fun extractPdfBookmarkPages(
        book: Book,
        bookmarks: List<Bookmark>
    ): Map<Long, String> {
        val pageIndices = bookmarks.map { it.chapterIndex }.toSet()
        val pageTexts = runCatching {
            pdfTextExtractor.extractPages(File(book.filePath), pageIndices)
        }.getOrDefault(emptyMap())
        return bookmarks.associate { bookmark ->
            bookmark.id to pageTexts[bookmark.chapterIndex].orEmpty()
        }
    }

    private suspend fun layoutBookmarkPages(
        parser: BookParser,
        format: BookFormat,
        chapterTitles: Map<Int, String>,
        bookmarks: List<Bookmark>
    ): Map<Long, String> {
        if (bookmarks.isEmpty()) return emptyMap()

        val metrics = context.resources.displayMetrics
        val fontSizeSp = dataStoreManager.fontSize.first()
        val lineHeight = dataStoreManager.lineHeight.first()
        val letterSpacingDp = dataStoreManager.letterSpacing.first()
        val fontType = dataStoreManager.fontType.first()
        val customFontPath = dataStoreManager.customFontPath.first()
        val marginLeftDp = dataStoreManager.marginLeft.first()
        val marginRightDp = dataStoreManager.marginRight.first()
        val marginTopDp = dataStoreManager.marginTop.first()
        val marginBottomDp = dataStoreManager.marginBottom.first()
        val paragraphSpacingDp = dataStoreManager.paragraphSpacing().first()
        val firstLineIndent = dataStoreManager.firstLineIndent().first()
        val topLeft = dataStoreManager.readerCornerContent(ReaderPageCorner.TOP_LEFT).first()
        val topRight = dataStoreManager.readerCornerContent(ReaderPageCorner.TOP_RIGHT).first()
        val hasTopStatus = topLeft != ReaderCornerContent.NONE || topRight != ReaderCornerContent.NONE

        val density = metrics.density
        val fontSizePx = fontSizeSp * density
        val lineSpacingPx = 2.5f * density
        val typeface = resolveTypeface(fontType, customFontPath)
        val baseMarginTop = (marginTopDp + if (hasTopStatus) 38f else 0f) * density
        val baseMarginBottom = marginBottomDp * density
        val fontSpacing = Paint(Paint.ANTI_ALIAS_FLAG).run {
            textSize = fontSizePx
            this.typeface = typeface
            fontSpacing
        }
        val balanceOffset = calculateReaderVerticalBalanceOffset(
            availableHeightPx = metrics.heightPixels - baseMarginTop - baseMarginBottom,
            lineHeightPx = fontSpacing * lineHeight + lineSpacingPx,
            maxShiftPx = baseMarginBottom
        )
        val engine = PageLayoutEngine().apply {
            configure(
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                fontSizePx = fontSizePx,
                lineSpacingPx = lineSpacingPx,
                lineSpacingMult = lineHeight,
                letterSpacingPx = letterSpacingDp * density,
                fontType = fontType,
                customTypeface = typeface,
                marginLeftPx = marginLeftDp * density,
                marginRightPx = marginRightDp * density,
                marginTopPx = baseMarginTop + balanceOffset,
                marginBottomPx = baseMarginBottom - balanceOffset,
                chapterCount = parser.getChapterCount()
            )
        }

        val result = mutableMapOf<Long, String>()
        bookmarks.groupBy { it.chapterIndex }.forEach { (chapterIndex, chapterBookmarks) ->
            val chapterText = runCatching {
                formatChapterText(
                    raw = parser.getChapterContent(chapterIndex),
                    format = format,
                    chapterTitle = chapterTitles[chapterIndex].orEmpty(),
                    fontSizeSp = fontSizeSp,
                    paragraphSpacingDp = paragraphSpacingDp,
                    firstLineIndent = firstLineIndent
                )
            }.getOrNull() ?: return@forEach
            if (chapterText.isEmpty()) return@forEach

            val layout = runCatching { engine.layout(chapterIndex, chapterText) }.getOrNull()
                ?: return@forEach
            chapterBookmarks.forEach { bookmark ->
                val page = layout.pages.getOrNull(bookmark.position.toInt())
                    ?: layout.pages.lastOrNull()
                    ?: return@forEach
                result[bookmark.id] = chapterText
                    .subSequence(page.startCharOffset, page.endCharOffset)
                    .toString()
            }
        }
        return result
    }

    private fun formatChapterText(
        raw: CharSequence,
        format: BookFormat,
        chapterTitle: String,
        fontSizeSp: Float,
        paragraphSpacingDp: Float,
        firstLineIndent: Float
    ): CharSequence {
        if (raw.isEmpty()) return raw
        var skipFirstParagraphIndent = false
        val chapterText = if (format == BookFormat.TXT && raw !is Spanned) {
            val newlineIndex = raw.indexOf('\n')
            if (newlineIndex > 0) {
                val title = raw.substring(0, newlineIndex)
                if (shouldStyleTxtChapterTitle(title, chapterTitle)) {
                    skipFirstParagraphIndent = true
                    val body = raw.substring(newlineIndex + 1)
                    SpannableString("$title\n\n$body").apply {
                        setSpan(
                            AbsoluteSizeSpan(22, true),
                            0,
                            title.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            title.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } else {
                    raw
                }
            } else {
                raw
            }
        } else {
            raw
        }
        val metrics = context.resources.displayMetrics
        return ReaderParagraphFormatter.applyFirstLineIndent(
            text = chapterText,
            indentCharacters = firstLineIndent,
            textSizePx = fontSizeSp * metrics.scaledDensity,
            paragraphSpacingPx = paragraphSpacingDp * metrics.density,
            skipFirstNonEmptyParagraph = skipFirstParagraphIndent
        )
    }

    private fun resolveTypeface(fontType: String, customFontPath: String?): Typeface {
        return when (fontType) {
            "serif" -> Typeface.SERIF
            "fangsong" -> runCatching { ResourcesCompat.getFont(context, R.font.fandol_fang) }
                .getOrNull()
                ?: Typeface.DEFAULT
            "kaiti" -> runCatching { ResourcesCompat.getFont(context, R.font.lxgw_wenkai) }
                .getOrNull()
                ?: Typeface.DEFAULT
            "custom" -> customFontPath
                ?.let { path -> runCatching { Typeface.createFromFile(File(path)) }.getOrNull() }
                ?: Typeface.DEFAULT
            else -> Typeface.DEFAULT
        }
    }
}
