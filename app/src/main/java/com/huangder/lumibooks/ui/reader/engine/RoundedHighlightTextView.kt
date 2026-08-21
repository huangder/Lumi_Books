package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView

/** A non-persistent highlight whose opacity is animated after navigating to a search result. */
internal class ReaderSearchHighlightSpan(var alpha: Int) : CharacterStyle(), UpdateAppearance {
    val color: Int
        get() = (alpha.coerceIn(0, 255) shl 24) or 0x00FFE082

    override fun updateDrawState(textPaint: TextPaint) = Unit
}

/** A saved highlight rendered by [RoundedHighlightTextView] instead of a square background span. */
internal class ReaderHighlightSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit
}

internal class TtsSentenceHighlightSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit

    companion object {
        fun computeHighlightColor(bgColor: Int, delta: Float = 0.06f): Int {
            val r = android.graphics.Color.red(bgColor)
            val g = android.graphics.Color.green(bgColor)
            val b = android.graphics.Color.blue(bgColor)
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            return if (luminance > 0.5f) {
                val factor = 1f - delta
                val nr = (r * factor).toInt().coerceIn(0, 255)
                val ng = (g * factor).toInt().coerceIn(0, 255)
                val nb = (b * factor).toInt().coerceIn(0, 255)
                android.graphics.Color.argb(0x66, nr, ng, nb)
            } else {
                val dr = (255 - r)
                val dg = (255 - g)
                val db = (255 - b)
                android.graphics.Color.argb(
                    0x66,
                    (r + dr * delta).toInt().coerceIn(0, 255),
                    (g + dg * delta).toInt().coerceIn(0, 255),
                    (b + db * delta).toInt().coerceIn(0, 255)
                )
            }
        }
    }
}
/** Marks a saved reader underline; rendering is owned by the reader views. */
internal class WaveUnderlineSpan(val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) = Unit
}
internal open class RoundedHighlightTextView(context: Context) : TextView(context) {
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        drawRoundedHighlights(canvas)
        drawWaveUnderlines(canvas)
        super.onDraw(canvas)
    }

    /** Draws each non-blank line segment without changing the underlying text layout. */
    private fun drawWaveUnderlines(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        if (spanned.isEmpty() || textLayout.lineCount == 0) return
        val density = resources.displayMetrics.density
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
        }
        val saveCount = canvas.save()
        canvas.translate(
            totalPaddingLeft.toFloat() - scrollX,
            totalPaddingTop.toFloat() - scrollY
        )
        spanned.getSpans(0, spanned.length, WaveUnderlineSpan::class.java).forEach { span ->
            val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
            val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
            if (spanStart >= spanEnd) return@forEach
            wavePaint.color = span.color
            val amplitude = 1.6f * density
            val wavelength = 5.5f * density
            for (line in textLayout.getLineForOffset(spanStart)..textLayout.getLineForOffset(spanEnd - 1)) {
                val layoutLineStart = textLayout.getLineStart(line)
                val rawLineEnd = textLayout.getLineEnd(line)
                val contentEnd = readerLineContentEnd(spanned, layoutLineStart, rawLineEnd)
                val lineStart = maxOf(spanStart, layoutLineStart)
                val lineEnd = minOf(spanEnd, contentEnd)
                if (lineStart >= lineEnd || !spanned.substring(lineStart, lineEnd).any { !it.isWhitespace() }) continue
                val x0 = textLayout.getPrimaryHorizontal(lineStart)
                val x1 = if (
                    lineEnd == rawLineEnd && line < textLayout.lineCount - 1 &&
                    spanned[rawLineEnd - 1] != '\n'
                ) {
                    textLayout.getLineRight(line)
                } else {
                    textLayout.getPrimaryHorizontal(lineEnd)
                }
                if (x1 <= x0) continue
                val baseline = textLayout.getLineBaseline(line).toFloat()
                val underlineCenter = baseline + paint.fontMetrics.descent.coerceAtLeast(1f) + 1f * density
                val path = android.graphics.Path()
                var x = x0
                var first = true
                while (x <= x1) {
                    val y = underlineCenter + amplitude * kotlin.math.sin((x - x0) / wavelength * 2.0 * Math.PI)
                    if (first) { path.moveTo(x, y.toFloat()); first = false } else { path.lineTo(x, y.toFloat()) }
                    x += 1f
                }
                canvas.drawPath(path, wavePaint)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoundedHighlights(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        if (spanned.isEmpty() || textLayout.lineCount == 0) return

        val saveCount = canvas.save()
        canvas.translate(
            totalPaddingLeft.toFloat() - scrollX,
            totalPaddingTop.toFloat() - scrollY
        )
        spanned.getSpans(0, spanned.length, ReaderHighlightSpan::class.java).forEach { span ->
            drawRoundedHighlight(canvas, spanned, textLayout, span, span.color)
        }
        spanned.getSpans(0, spanned.length, ReaderSearchHighlightSpan::class.java).forEach { span ->
            drawRoundedHighlight(canvas, spanned, textLayout, span, span.color)
        }
        spanned.getSpans(0, spanned.length, TtsSentenceHighlightSpan::class.java).forEach { span ->
            drawTtsSentenceHighlight(canvas, textLayout, span, span.color)
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoundedHighlight(
        canvas: Canvas,
        spanned: Spanned,
        textLayout: Layout,
        span: Any,
        color: Int
    ) {
        if (color ushr 24 == 0) return
        val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
        val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
        if (spanStart >= spanEnd) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 3f * density
        val glyphPadding = 2f * density
        val minimumLineGap = 1.5f * density
        val cornerRadius = 6f * density
        val fontMetrics = paint.fontMetrics
        val firstLine = textLayout.getLineForOffset(spanStart)
        val lastLine = textLayout.getLineForOffset(spanEnd - 1)
        highlightPaint.color = color

        for (line in firstLine..lastLine) {
            val lineStart = textLayout.getLineStart(line)
            val rawLineEnd = textLayout.getLineEnd(line)
            val contentEnd = readerLineContentEnd(spanned, lineStart, rawLineEnd)
            val segmentStart = maxOf(spanStart, lineStart)
            val segmentEnd = minOf(spanEnd, contentEnd)
            if (segmentStart >= segmentEnd) continue

            val paragraphIsLtr = textLayout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT
            val segmentStartX = textLayout.getPrimaryHorizontal(segmentStart)
            val segmentEndX = if (
                segmentEnd == rawLineEnd && line < textLayout.lineCount - 1 &&
                spanned[rawLineEnd - 1] != '\n'
            ) {
                if (paragraphIsLtr) textLayout.getLineRight(line) else textLayout.getLineLeft(line)
            } else {
                textLayout.getPrimaryHorizontal(segmentEnd)
            }
            val segmentLeft = minOf(segmentStartX, segmentEndX)
            val segmentRight = maxOf(segmentStartX, segmentEndX)
            if (segmentRight <= segmentLeft) continue

            val lineTop = textLayout.getLineTop(line).toFloat() + minimumLineGap
            val lineBottom = textLayout.getLineBottom(line).toFloat() - minimumLineGap
            val baseline = textLayout.getLineBaseline(line).toFloat()
            val glyphTop = baseline + fontMetrics.ascent - glyphPadding
            val glyphBottom = baseline + fontMetrics.descent + glyphPadding
            val top = glyphTop.coerceAtLeast(lineTop)
            val bottom = glyphBottom.coerceAtMost(lineBottom)
            if (bottom <= top) continue

            highlightBounds.set(
                (segmentLeft - horizontalPadding).coerceAtLeast(-horizontalPadding),
                top,
                (segmentRight + horizontalPadding).coerceAtMost(textLayout.width + horizontalPadding),
                bottom
            )
            val radius = minOf(cornerRadius, highlightBounds.height() / 2f)
            canvas.drawRoundRect(highlightBounds, radius, radius, highlightPaint)
        }
    }

    /** 褰撳墠鍙ュ彞 TTS 楂樹寒锛氭暣涓彞瀛愬潡鍏辩敤涓€涓ぇ鍦嗚鐭╁舰锛堣法琛屾暣浣?*/
    private fun drawTtsSentenceHighlight(
        canvas: Canvas,
        textLayout: Layout,
        span: TtsSentenceHighlightSpan,
        color: Int
    ) {
        if (color ushr 24 == 0) return
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(span).coerceIn(0, spanned.length)
        val spanEnd = spanned.getSpanEnd(span).coerceIn(spanStart, spanned.length)
        if (spanStart >= spanEnd) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 3f * density
        val cornerRadius = 12f * density
        val minimumLineGap = 1.5f * density
        val firstLine = textLayout.getLineForOffset(spanStart)
        val lastLine = textLayout.getLineForOffset(spanEnd - 1)
        val top = textLayout.getLineTop(firstLine).toFloat() + minimumLineGap
        val bottom = textLayout.getLineBottom(lastLine).toFloat() - minimumLineGap
        if (bottom <= top) return

        highlightPaint.color = color
        highlightBounds.set(
            -horizontalPadding,
            top,
            textLayout.width + horizontalPadding,
            bottom
        )
        val radius = minOf(cornerRadius, highlightBounds.height() / 2f)
        canvas.drawRoundRect(highlightBounds, radius, radius, highlightPaint)
    }
}
