package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import androidx.core.graphics.ColorUtils
import kotlin.math.min

data class ReaderGuideLine(val startOffset: Int, val bounds: RectF)

internal fun readerGuideDimAlpha(level: Int): Int = when (level) {
    0 -> 0
    1 -> 46
    3 -> 133
    else -> 89
}

internal fun readerGuideShadeColor(backgroundColor: Int, level: Int): Int {
    val shade = if (ColorUtils.calculateLuminance(backgroundColor) < 0.42) Color.BLACK else Color.WHITE
    return ColorUtils.setAlphaComponent(shade, readerGuideDimAlpha(level))
}

internal fun drawReaderGuideOverlay(
    canvas: Canvas,
    width: Float,
    height: Float,
    band: RectF,
    shadeColor: Int,
    density: Float
) {
    if (band.isEmpty) return
    val radius = band.height() / 2f
    val highlight = Path().apply {
        addRoundRect(band, radius, radius, Path.Direction.CW)
    }
    if (Color.alpha(shadeColor) != 0) {
        val mask = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width, height, Path.Direction.CW)
            addPath(highlight)
        }
        canvas.drawPath(mask, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadeColor })
    }

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    for ((depthDp, alpha) in listOf(9f to 2, 5f to 3, 2f to 5)) {
        val depth = depthDp * density
        val inner = RectF(band).apply { inset(depth, depth) }
        if (inner.isEmpty) continue
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addPath(highlight)
            addRoundRect(inner, (radius - depth).coerceAtLeast(0f),
                (radius - depth).coerceAtLeast(0f), Path.Direction.CW)
        }
        shadow.color = Color.argb(alpha, 0, 0, 0)
        canvas.drawPath(ring, shadow)
    }
}

internal fun readerGuideStepIndex(current: Int, count: Int, direction: Int): Int? =
    (current + direction).takeIf { current in 0 until count && direction in -1..1 &&
        direction != 0 && it in 0 until count }

internal fun readerGuideScrollDistance(
    currentCenterY: Float,
    nextCenterY: Float?,
    currentHeight: Float,
    direction: Int
): Float = nextCenterY?.minus(currentCenterY) ?: direction * currentHeight.coerceAtLeast(1f)

internal fun readerGuideFocusedIndex(
    lines: List<ReaderGuideLine>,
    anchorY: Float,
    viewportHeight: Float
): Int? {
    val index = lines.indices.minByOrNull { line ->
        val bounds = lines[line].bounds
        when {
            anchorY < bounds.top -> bounds.top - anchorY
            anchorY > bounds.bottom -> anchorY - bounds.bottom
            else -> 0f
        }
    } ?: return null
    val bounds = lines[index].bounds
    val distance = when {
        anchorY < bounds.top -> bounds.top - anchorY
        anchorY > bounds.bottom -> anchorY - bounds.bottom
        else -> 0f
    }
    return index.takeIf {
        distance <= maxOf(bounds.height() * 1.5f, viewportHeight * 0.08f)
    }
}

internal fun readableGuideLines(
    text: CharSequence,
    layout: Layout,
    left: Float,
    right: Float,
    topOffset: Float,
    visibleTop: Float = Float.NEGATIVE_INFINITY,
    visibleBottom: Float = Float.POSITIVE_INFINITY,
    density: Float = 1f
): List<ReaderGuideLine> = buildList {
    if (layout.lineCount == 0 || visibleBottom <= topOffset ||
        visibleTop >= topOffset + layout.height) return@buildList
    val first = if (visibleTop.isFinite()) {
        layout.getLineForVertical((visibleTop - topOffset).toInt().coerceAtLeast(0))
    } else 0
    val last = if (visibleBottom.isFinite()) {
        layout.getLineForVertical((visibleBottom - topOffset).toInt().coerceAtLeast(0))
    } else layout.lineCount - 1
    for (line in first..last) {
        val lineTop = layout.getLineTop(line)
        val naturalHeight = layout.getLineBottom(line) - lineTop
        val previousHeight = if (line == layout.lineCount - 1 && line > 0) {
            layout.getLineTop(line) - layout.getLineTop(line - 1)
        } else 0
        val pitch = maxOf(naturalHeight, previousHeight).toFloat()
        val metrics = layout.paint.fontMetrics
        val glyphHeight = metrics.descent - metrics.ascent
        val bandHeight = min(pitch, maxOf(glyphHeight + 6f * density, pitch * 0.82f))
        val glyphCenter = topOffset + layout.getLineBaseline(line) +
            (metrics.ascent + metrics.descent) / 2f
        val top = glyphCenter - bandHeight / 2f
        val bottom = glyphCenter + bandHeight / 2f
        if (bottom <= visibleTop || top >= visibleBottom) continue
        val start = layout.getLineStart(line).coerceIn(0, text.length)
        val end = layout.getLineEnd(line).coerceIn(start, text.length)
        if ((start until end).none { text[it] != '\uFFFC' && !text[it].isWhitespace() }) continue
        add(ReaderGuideLine(start, RectF(
            left, top, right, bottom
        )))
    }
}
