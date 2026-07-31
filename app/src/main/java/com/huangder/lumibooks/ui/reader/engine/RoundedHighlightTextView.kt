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

internal open class RoundedHighlightTextView(context: Context) : TextView(context) {
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        drawRoundedHighlights(canvas)
        super.onDraw(canvas)
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
}
