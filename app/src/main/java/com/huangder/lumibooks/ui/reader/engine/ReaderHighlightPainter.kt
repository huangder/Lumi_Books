package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan

/** 一行逐字左边界，以及该行内容结束位置（绘制文字用的同一套坐标）。 */
internal class ReaderLineOffsets(
    val lefts: FloatArray,
    val right: Float
)

/**
 * 计算一行的逐字坐标：含被挤压标点重排的结果，并均匀分配两端对齐余量。
 *
 * 返回 null 表示这一行不需要接管（例如没有挤压标点、或存在无法逐个度量的字符），
 * 调用方应回退到 Layout 坐标。
 */
internal fun readerLineOffsets(
    layout: Layout,
    text: Spanned,
    line: Int,
    lineStart: Int,
    contentEnd: Int,
    justificationMode: Int,
    forceLastLineJustification: Boolean
): ReaderLineOffsets? {
    val count = contentEnd - lineStart
    if (count <= 0 || layout.getParagraphDirection(line) != Layout.DIR_LEFT_TO_RIGHT) return null
    if (text.getSpans(lineStart, contentEnd, ReaderPunctuationCompressionSpan::class.java).isEmpty()) return null
    // Complex shaping and replacement runs keep the platform's cluster/bidi geometry.
    // updateDrawState and updateMeasureState agree, so these native carets are compressed too.
    if (text.getSpans(lineStart, contentEnd, android.text.style.ReplacementSpan::class.java).isNotEmpty()) return null
    for (index in lineStart until contentEnd) {
        val ch = text[index]
        val type = Character.getType(ch)
        val script = Character.UnicodeScript.of(ch.code)
        if (ch.isSurrogate() || ch == '\t' || layout.isRtlCharAt(index) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            (script != Character.UnicodeScript.HAN && script != Character.UnicodeScript.COMMON)) return null
    }
    val paint = android.text.TextPaint()
    val slots = FloatArray(count)
    val tracking = FloatArray(count)
    for (index in lineStart until contentEnd) {
        readerGlyphPaint(layout.paint, text, index, index + 1, paint)
        val compressed = readerIsCompressedPunctuation(text, index)
        tracking[index - lineStart] = paint.letterSpacing * paint.textSize * paint.textScaleX
        val slot = if (compressed) readerPunctuationSlotWidth(paint, text, index, index + 1) else {
            paint.letterSpacing = 0f
            readerNaturalAdvance(paint, text, index, index + 1)
        }
        if (!slot.isFinite() || slot <= 0f) return null
        slots[index - lineStart] = slot
    }
    val gaps = FloatArray((count - 1).coerceAtLeast(0)) { (tracking[it] + tracking[it + 1]) / 2f }
    // Android 13 and 15 trim run-edge tracking differently. Ask the same
    // styled text measurer for the line's advance, rather than assuming n-1
    // tracking gaps. Distribute only that edge delta across existing gaps.
    val estimatedTotal = slots.sum() + gaps.sum()
    // getDesiredWidth measures a standalone paragraph, so it includes every
    // LeadingMarginSpan's FIRST-line margin even for a continuation-line slice.
    // Layout has already placed that margin in the line origin. Only the text
    // advance belongs in tracking; including it again pushes ink into the right
    // padding and spreads paragraph-final punctuation away from the last glyph.
    val paragraphMargin = text.getSpans(lineStart, contentEnd, LeadingMarginSpan::class.java)
        .sumOf { it.getLeadingMargin(true) }
    val measuredTotal = (Layout.getDesiredWidth(text, lineStart, contentEnd, layout.paint) - paragraphMargin)
        .takeIf { it.isFinite() && it > 0f } ?: estimatedTotal
    val edgeCorrection = if (gaps.isNotEmpty()) (measuredTotal - estimatedTotal) / gaps.size else 0f
    val naturalTotal = if (gaps.isNotEmpty()) measuredTotal else estimatedTotal
    val alignment = layout.getParagraphAlignment(line)
    val canStretch = alignment == Layout.Alignment.ALIGN_NORMAL &&
        justificationMode != Layout.JUSTIFICATION_MODE_NONE &&
        shouldJustifyReaderLine(line, layout.lineCount,
            readerLineEndsParagraph(text, lineStart, layout.getLineEnd(line)), forceLastLineJustification)
    val lineRight = layout.getParagraphRight(line).toFloat()
    val baseStart = when (alignment) {
        Layout.Alignment.ALIGN_CENTER -> (layout.getParagraphLeft(line) + lineRight - naturalTotal) / 2f
        Layout.Alignment.ALIGN_OPPOSITE -> lineRight - naturalTotal
        else -> layout.getPrimaryHorizontal(lineStart)
    }
    if (!baseStart.isFinite()) return null
    val extra = if (canStretch && gaps.isNotEmpty())
        (lineRight - baseStart - naturalTotal).coerceAtLeast(0f) / gaps.size else 0f
    val lefts = FloatArray(count)
    var cursor = baseStart
    for (index in 0 until count) {
        lefts[index] = cursor
        cursor += slots[index]
        if (index < gaps.size) cursor += gaps[index] + edgeCorrection + extra
    }
    return ReaderLineOffsets(lefts, cursor)
}

/**
 * 高亮/选中的统一绘制：同一段文字按行各画一个圆角矩形。
 *
 * 逐字画方块会让字与字之间出现缝隙、上下相邻行又贴在一起；这里按行合并成一个圆角矩形，
 * 行间留出 [minimumLineGap] 的空隙，观感与已保存高亮一致。
 *
 * 坐标使用 Layout 坐标系；调用方需先把 canvas 平移到内容原点。
 */
internal class ReaderHighlightPainter(
    private val paint: Paint,
    density: Float
) {
    private val horizontalPadding = 3f * density
    private val glyphPadding = 2f * density
    private val minimumLineGap = 1.5f * density
    private val cornerRadius = 6f * density
    private val bounds = RectF()
    private val selectionPath = Path()
    private val pathBounds = RectF()

    fun drawRange(
        canvas: Canvas,
        layout: Layout,
        text: Spanned,
        geometry: ReaderLineGeometry,
        start: Int,
        end: Int,
        color: Int
    ) {
        if (color ushr 24 == 0) return
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        if (safeStart >= safeEnd) return

        paint.color = color
        val fontMetrics = layout.paint.fontMetrics
        val visible = readerVisibleLines(canvas, layout)
        if (visible.isEmpty()) return
        val firstLine = maxOf(layout.getLineForOffset(safeStart), visible.first)
        val lastLine = minOf(layout.getLineForOffset(safeEnd - 1), visible.last)
        for (line in firstLine..lastLine) {
            drawLineSegment(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = safeStart,
                end = safeEnd,
                fontMetrics = fontMetrics,
                line = line
            )
        }
    }

    private fun drawLineSegment(
        canvas: Canvas,
        layout: Layout,
        text: Spanned,
        geometry: ReaderLineGeometry,
        start: Int,
        end: Int,
        fontMetrics: Paint.FontMetrics,
        line: Int
    ) {
        val lineStart = layout.getLineStart(line)
        val rawLineEnd = layout.getLineEnd(line)
        val contentEnd = readerLineContentEnd(text, lineStart, rawLineEnd)
        val segmentStart = maxOf(start, lineStart)
        val segmentEnd = minOf(end, contentEnd)
        if (segmentStart >= segmentEnd) return

        val paragraphIsLtr = layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
        val geometryRange = geometry.horizontalRange(line, segmentStart, segmentEnd) ?: return
        val segmentStartX = geometryRange.left
        val segmentEndX = geometryRange.right
        // 常规 LTR 文本直接用排版推进量；选区路径边界可能带上相邻 run，只在 RTL/混排时使用。
        val hasRtlRun = !paragraphIsLtr || (segmentStart until segmentEnd)
            .any { offset -> layout.isRtlCharAt(offset) }
        if (hasRtlRun) {
            selectionPath.reset()
            layout.getSelectionPath(segmentStart, segmentEnd, selectionPath)
            selectionPath.computeBounds(pathBounds, true)
        } else {
            pathBounds.setEmpty()
        }
        val usePathBounds = !pathBounds.isEmpty() && !paragraphIsLtr
        val segmentLeft = if (usePathBounds) pathBounds.left else minOf(segmentStartX, segmentEndX)
        val segmentRight = if (usePathBounds) pathBounds.right else maxOf(segmentStartX, segmentEndX)
        if (segmentRight <= segmentLeft) return

        val lineTop = layout.getLineTop(line).toFloat() + minimumLineGap
        val lineBottom = layout.getLineBottom(line).toFloat() - minimumLineGap
        val baseline = layout.getLineBaseline(line).toFloat()
        val glyphTop = baseline + fontMetrics.ascent - glyphPadding
        val glyphBottom = baseline + fontMetrics.descent + glyphPadding
        val top = glyphTop.coerceAtLeast(lineTop)
        val bottom = glyphBottom.coerceAtMost(lineBottom)
        if (bottom <= top) return

        bounds.set(
            (segmentLeft - horizontalPadding).coerceAtLeast(-horizontalPadding),
            top,
            (segmentRight + horizontalPadding).coerceAtMost(layout.width + horizontalPadding),
            bottom
        )
        val radius = minOf(cornerRadius, bounds.height() / 2f)
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}
