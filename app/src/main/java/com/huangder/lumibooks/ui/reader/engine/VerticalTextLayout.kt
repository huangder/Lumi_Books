package com.huangder.lumibooks.ui.reader.engine

import android.icu.text.BreakIterator
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import java.util.Locale
import kotlin.math.max

data class VerticalRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

sealed interface VerticalLayoutItem {
    val startOffset: Int
    val endOffset: Int
    val bounds: VerticalRect
}

data class VerticalGlyphLayout(
    override val startOffset: Int,
    override val endOffset: Int,
    override val bounds: VerticalRect,
    val columnIndex: Int,
    val rotateClockwise: Boolean
) : VerticalLayoutItem

data class VerticalImageLayout(
    override val startOffset: Int,
    override val endOffset: Int,
    override val bounds: VerticalRect
) : VerticalLayoutItem

data class VerticalPageGeometry(
    val items: List<VerticalLayoutItem>,
    val width: Float,
    val height: Float
) {
    val glyphs: List<VerticalGlyphLayout> get() = items.filterIsInstance<VerticalGlyphLayout>()
    val image: VerticalImageLayout? get() = items.filterIsInstance<VerticalImageLayout>().firstOrNull()
}

internal data class VerticalPageSlice(
    val startOffset: Int,
    val endOffset: Int,
    val geometry: VerticalPageGeometry
)

internal fun verticalPresentationText(text: String): String = when (text) {
    "，" -> "︐"
    "、" -> "︑"
    "。" -> "︒"
    "：" -> "︓"
    "；" -> "︔"
    "！" -> "︕"
    "？" -> "︖"
    "（" -> "︵"
    "）" -> "︶"
    "〔" -> "︹"
    "〕" -> "︺"
    "【" -> "︻"
    "】" -> "︼"
    "《" -> "︽"
    "》" -> "︾"
    "〈" -> "︿"
    "〉" -> "﹀"
    "「" -> "﹁"
    "」" -> "﹂"
    "『" -> "﹃"
    "』" -> "﹄"
    else -> text
}

internal fun shouldRotateVerticalCluster(cluster: String): Boolean {
    if (cluster.isEmpty()) return false
    val codePoint = cluster.codePointAt(0)
    val script = Character.UnicodeScript.of(codePoint)
    return script == Character.UnicodeScript.LATIN ||
        script == Character.UnicodeScript.GREEK ||
        script == Character.UnicodeScript.CYRILLIC ||
        cluster.all { it.isDigit() || it in "+-=/%#@&*_" }
}

private val prohibitedColumnStart = setOf(
    '、', '。', '，', '．', '：', '；', '！', '？', '）', '］', '】', '〕', '〉', '》',
    '」', '』', '”', '’', '…', '—', '・', '︐', '︑', '︒', '︓', '︔', '︕', '︖'
)

private val prohibitedColumnEnd = setOf(
    '（', '［', '【', '〔', '〈', '《', '「', '『', '“', '‘', '︵', '︷', '︹', '︻', '︽', '︿', '﹁', '﹃'
)

internal fun isVerticalColumnStartProhibited(cluster: String): Boolean =
    cluster.firstOrNull() in prohibitedColumnStart

internal fun isVerticalColumnEndProhibited(cluster: String): Boolean =
    cluster.lastOrNull() in prohibitedColumnEnd

internal object VerticalTextLayouter {
    fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        height: Int,
        lineSpacingExtra: Float,
        lineSpacingMultiplier: Float,
        letterSpacing: Float
    ): List<VerticalPageSlice> {
        if (text.isEmpty()) return emptyList()

        val baseEm = paint.textSize.coerceAtLeast(1f)
        val columnAdvance = (baseEm * lineSpacingMultiplier + lineSpacingExtra)
            .coerceAtLeast(baseEm)
        val columnCount = (width / columnAdvance).toInt().coerceAtLeast(1)
        val characterAdvance = (baseEm + letterSpacing).coerceAtLeast(baseEm * 0.55f)
        val graphemes = graphemeRanges(text.toString())
        val pages = mutableListOf<VerticalPageSlice>()
        var rangeIndex = 0

        while (rangeIndex < graphemes.size) {
            val pageItems = mutableListOf<VerticalLayoutItem>()
            val pageStart = graphemes[rangeIndex].first
            val firstRange = graphemes[rangeIndex]
            imageSpanAt(text, firstRange.first, firstRange.last + 1)?.let { image ->
                val spanStart = (text as Spanned).getSpanStart(image).coerceAtLeast(firstRange.first)
                val spanEnd = (text as Spanned).getSpanEnd(image).coerceAtLeast(spanStart + 1)
                val inset = baseEm * 0.25f
                pages += VerticalPageSlice(
                    startOffset = spanStart,
                    endOffset = spanEnd,
                    geometry = VerticalPageGeometry(
                        items = listOf(
                            VerticalImageLayout(
                                startOffset = spanStart,
                                endOffset = spanEnd,
                                bounds = VerticalRect(inset, inset, width - inset, height - inset)
                            )
                        ),
                        width = width.toFloat(),
                        height = height.toFloat()
                    )
                )
                while (rangeIndex < graphemes.size && graphemes[rangeIndex].first < spanEnd) rangeIndex++
                continue
            }

            var column = 0
            var y = paragraphIndent(text, pageStart)
            var pageEnd = pageStart
            var pendingImagePage: VerticalPageSlice? = null

            while (rangeIndex < graphemes.size && column < columnCount) {
                val range = graphemes[rangeIndex]
                val start = range.first
                val end = range.last + 1
                val cluster = text.subSequence(start, end).toString()

                val image = imageSpanAt(text, start, end)
                if (image != null) {
                    if (pageItems.isEmpty()) {
                        val spanned = text as Spanned
                        val spanStart = spanned.getSpanStart(image).coerceAtLeast(start)
                        val spanEnd = spanned.getSpanEnd(image).coerceAtLeast(spanStart + 1)
                        val inset = baseEm * 0.25f
                        pendingImagePage = VerticalPageSlice(
                            startOffset = pageStart,
                            endOffset = spanEnd,
                            geometry = VerticalPageGeometry(
                                items = listOf(
                                    VerticalImageLayout(
                                        startOffset = spanStart,
                                        endOffset = spanEnd,
                                        bounds = VerticalRect(inset, inset, width - inset, height - inset)
                                    )
                                ),
                                width = width.toFloat(),
                                height = height.toFloat()
                            )
                        )
                        while (rangeIndex < graphemes.size && graphemes[rangeIndex].first < spanEnd) {
                            rangeIndex++
                        }
                    }
                    break
                }

                if (cluster == "\n" || cluster == "\r\n") {
                    pageEnd = end
                    rangeIndex++
                    column++
                    y = if (rangeIndex < graphemes.size) paragraphIndent(text, graphemes[rangeIndex].first) else 0f
                    continue
                }

                val em = textSizeAt(text, start, baseEm, paint.density)
                val itemAdvance = max(characterAdvance, em + letterSpacing)
                if (y + itemAdvance > height && pageItems.any { it is VerticalGlyphLayout && it.columnIndex == column }) {
                    val previous = pageItems.lastOrNull() as? VerticalGlyphLayout
                    val previousText = previous?.let { text.subSequence(it.startOffset, it.endOffset).toString() }
                    if (isVerticalColumnStartProhibited(cluster) && previous != null && previous.columnIndex == column) {
                        pageItems.removeAt(pageItems.lastIndex)
                        pageEnd = previous.startOffset
                        rangeIndex--
                    } else if (previousText != null && isVerticalColumnEndProhibited(previousText)) {
                        pageItems.removeAt(pageItems.lastIndex)
                        pageEnd = previous.startOffset
                        rangeIndex--
                    }
                    column++
                    y = 0f
                    continue
                }

                if (column >= columnCount) break
                val left = width - (column + 1) * columnAdvance
                val bounds = VerticalRect(
                    left = left,
                    top = y,
                    right = (left + columnAdvance).coerceAtMost(width.toFloat()),
                    bottom = (y + itemAdvance).coerceAtMost(height.toFloat())
                )
                pageItems += VerticalGlyphLayout(
                    startOffset = start,
                    endOffset = end,
                    bounds = bounds,
                    columnIndex = column,
                    rotateClockwise = shouldRotateVerticalCluster(cluster)
                )
                y += itemAdvance
                pageEnd = end
                rangeIndex++
            }

            val imagePage = pendingImagePage
            if (imagePage != null) {
                pages += imagePage
                continue
            }

            if (pageEnd <= pageStart) {
                // A pathological font or viewport must still make progress.
                val range = graphemes[rangeIndex]
                val end = range.last + 1
                pageItems += VerticalGlyphLayout(
                    startOffset = range.first,
                    endOffset = end,
                    bounds = VerticalRect(0f, 0f, width.toFloat(), height.toFloat()),
                    columnIndex = 0,
                    rotateClockwise = shouldRotateVerticalCluster(text.subSequence(range.first, end).toString())
                )
                pageEnd = end
                rangeIndex++
            }

            pages += VerticalPageSlice(
                startOffset = pageStart,
                endOffset = pageEnd,
                geometry = VerticalPageGeometry(pageItems.toList(), width.toFloat(), height.toFloat())
            )
        }
        return pages
    }

    private fun graphemeRanges(text: String): List<IntRange> {
        val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
        iterator.setText(text)
        val ranges = mutableListOf<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            ranges += start until end
            start = end
            end = iterator.next()
        }
        return ranges
    }

    private fun imageSpanAt(text: CharSequence, start: Int, end: Int): ImageSpan? =
        (text as? Spanned)?.getSpans(start, end, ImageSpan::class.java)?.firstOrNull()

    private fun paragraphIndent(text: CharSequence, offset: Int): Float {
        val spanned = text as? Spanned ?: return 0f
        return spanned.getSpans(offset, (offset + 1).coerceAtMost(text.length), LeadingMarginSpan::class.java)
            .sumOf { it.getLeadingMargin(true).toDouble() }
            .toFloat()
    }

    private fun textSizeAt(text: CharSequence, offset: Int, base: Float, density: Float): Float {
        val spanned = text as? Spanned ?: return base
        var size = base
        spanned.getSpans(offset, (offset + 1).coerceAtMost(text.length), Any::class.java).forEach { span ->
            when (span) {
                is AbsoluteSizeSpan -> size = if (span.dip) span.size * density else span.size.toFloat()
                is RelativeSizeSpan -> size *= span.sizeChange
            }
        }
        return size.coerceAtLeast(1f)
    }
}
