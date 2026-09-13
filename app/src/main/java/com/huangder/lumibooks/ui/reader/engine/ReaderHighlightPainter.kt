package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned

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
    forceLastLineJustification: Boolean,
    letterSpacingPx: Float,
    measure: (index: Int) -> Float
): ReaderLineOffsets? {
    val count = contentEnd - lineStart
    if (count <= 0) return null

    var hasCompressed = false
    for (index in lineStart until contentEnd) {
        if (readerIsCompressedPunctuation(text, index)) {
            hasCompressed = true
            break
        }
    }
    if (!hasCompressed) return null

    val slots = FloatArray(count)
    var naturalTotal = 0f
    for (index in lineStart until contentEnd) {
        val natural = measure(index)
        if (!natural.isFinite() || natural <= 0f) return null
        val slot = if (readerIsCompressedPunctuation(text, index)) {
            natural * READER_PUNCTUATION_COMPRESSION
        } else {
            natural
        }
        slots[index - lineStart] = slot
        naturalTotal += slot
    }

    val baseStart = layout.getPrimaryHorizontal(lineStart)
    if (!baseStart.isFinite()) return null
    val lineRight = layout.getParagraphRight(line).toFloat().takeIf { it.isFinite() } ?: return null
    val gapCount = count - 1
    val endsWithParagraphBreak = readerLineEndsParagraph(text, lineStart, layout.getLineEnd(line))
    val canStretch = justificationMode != Layout.JUSTIFICATION_MODE_NONE &&
        layout.getParagraphDirection(line) != Layout.DIR_RIGHT_TO_LEFT &&
        shouldJustifyReaderLine(
            lineIndex = line,
            lineCount = layout.lineCount,
            endsWithParagraphBreak = endsWithParagraphBreak,
            pageEndsMidParagraph = forceLastLineJustification
        )
    val extra = if (canStretch && gapCount > 0) {
        (lineRight - baseStart - naturalTotal - letterSpacingPx * gapCount).coerceAtLeast(0f)
    } else {
        0f
    }
    val extraPerGap = if (gapCount > 0) extra / gapCount else 0f

    val lefts = FloatArray(count)
    var cursor = baseStart
    for (index in 0 until count) {
        lefts[index] = cursor
        cursor += slots[index]
        if (index < gapCount) cursor += letterSpacingPx + extraPerGap
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
        color: Int,
        /**
         * 该行逐字左边界（含被挤压标点的重排结果）。文字是按这份坐标绘制的，
         * 高亮必须用同一份坐标，否则会和字形差几个像素。
         */
        lineOffsets: ((line: Int, lineStart: Int, contentEnd: Int) -> ReaderLineOffsets?)? = null
    ) {
        if (color ushr 24 == 0) return
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        if (safeStart >= safeEnd) return

        paint.color = color
        val fontMetrics = layout.paint.fontMetrics
        val firstLine = layout.getLineForOffset(safeStart)
        val lastLine = layout.getLineForOffset(safeEnd - 1)
        for (line in firstLine..lastLine) {
            drawLineSegment(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = safeStart,
                end = safeEnd,
                fontMetrics = fontMetrics,
                line = line,
                lineOffsets = lineOffsets
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
        line: Int,
        lineOffsets: ((line: Int, lineStart: Int, contentEnd: Int) -> ReaderLineOffsets?)?
    ) {
        val lineStart = layout.getLineStart(line)
        val rawLineEnd = layout.getLineEnd(line)
        val contentEnd = readerLineContentEnd(text, lineStart, rawLineEnd)
        val segmentStart = maxOf(start, lineStart)
        val segmentEnd = minOf(end, contentEnd)
        if (segmentStart >= segmentEnd) return

        val paragraphIsLtr = layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
        val geometryRange = geometry.horizontalRange(line, segmentStart, segmentEnd) ?: return
        val offsets = lineOffsets?.invoke(line, lineStart, contentEnd)
        val startOffset = offsets?.lefts?.getOrNull(segmentStart - lineStart)
        val endOffset = when {
            offsets == null -> null
            segmentEnd >= contentEnd -> offsets.right
            else -> offsets.lefts.getOrNull(segmentEnd - lineStart)
        }
        val segmentStartX = startOffset ?: geometryRange.left
        val segmentEndX = endOffset ?: geometryRange.right
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
