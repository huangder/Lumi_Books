package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Spannable
import android.text.Spanned
import android.widget.TextView
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.highlight.RuleStyleJson
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightPainter
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import com.huangder.lumibooks.ui.reader.engine.drawReaderUnderline
import com.huangder.lumibooks.ui.reader.engine.readerLineContentEnd
import com.huangder.lumibooks.ui.reader.engine.readerVisibleLines

/** Saved annotations share the EPUB reader's painters; Markwon still draws the document. */
internal open class MarkdownAnnotationTextView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private val highlightPainter = ReaderHighlightPainter(Paint(Paint.ANTI_ALIAS_FLAG), density,
        keepCornersInsideLayout = true)
    private var geometryLayout: android.text.Layout? = null
    private var geometryText: CharSequence? = null
    private var geometryJustificationMode = -1
    private var geometry: ReaderLineGeometry? = null
    private val underlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        drawAnnotations(canvas, underlines = false)
        super.onDraw(canvas)
        drawAnnotations(canvas, underlines = true)
    }

    private fun drawAnnotations(canvas: Canvas, underlines: Boolean) {
        val text = text as? Spanned ?: return
        val layout = layout ?: return
        if (text.isEmpty() || layout.lineCount == 0) return
        val saved = canvas.save()
        try {
            canvas.translate(totalPaddingLeft.toFloat() - scrollX, totalPaddingTop.toFloat() - scrollY)
            val visible = readerVisibleLines(canvas, layout)
            if (visible.isEmpty()) return
            val start = layout.getLineStart(visible.first)
            val end = layout.getLineEnd(visible.last)
            if (!underlines) {
                val spans = text.getSpans(start, end, ReaderHighlightSpan::class.java)
                if (spans.isEmpty()) return
                val lineGeometry = geometryFor(layout, text)
                spans.forEach { span ->
                    highlightPainter.drawRange(canvas, layout, text, lineGeometry,
                        text.getSpanStart(span), text.getSpanEnd(span), span.color)
                }
                return
            }
            val spans = text.getSpans(start, end, WaveUnderlineSpan::class.java)
            if (spans.isEmpty()) return
            val lineGeometry = geometryFor(layout, text)
            spans.forEach { span ->
                val a = text.getSpanStart(span).coerceIn(0, text.length)
                val b = text.getSpanEnd(span).coerceIn(a, text.length)
                if (a >= b) return@forEach
                underlinePaint.color = span.color
                for (line in maxOf(visible.first, layout.getLineForOffset(a))..
                    minOf(visible.last, layout.getLineForOffset(b - 1))) {
                    val lineStart = maxOf(a, layout.getLineStart(line))
                    val lineEnd = minOf(b, readerLineContentEnd(text, layout.getLineStart(line), layout.getLineEnd(line)))
                    if (lineStart >= lineEnd || (lineStart until lineEnd).all { text[it].isWhitespace() }) continue
                    val range = lineGeometry.horizontalRange(line, lineStart, lineEnd) ?: continue
                    val cross = layout.getLineBaseline(line) + paint.fontMetrics.descent.coerceAtLeast(1f) + density
                    drawReaderUnderline(canvas, underlinePaint, span.mode, range.left, range.right, cross, false, density)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun geometryFor(layout: android.text.Layout, text: CharSequence): ReaderLineGeometry {
        if (geometryLayout !== layout || geometryText !== text || geometryJustificationMode != justificationMode) {
            geometryLayout = layout
            geometryText = text
            geometryJustificationMode = justificationMode
            geometry = ReaderLineGeometry(layout, text, justificationMode)
        }
        return checkNotNull(geometry)
    }
}

internal fun applyMarkdownAnnotationSpans(text: Spannable, offset: Int, notes: List<Note>) {
    text.getSpans(0, text.length, ReaderHighlightSpan::class.java).forEach(text::removeSpan)
    text.getSpans(0, text.length, WaveUnderlineSpan::class.java).forEach(text::removeSpan)
    notes.filter { !it.isStale && it.startPosition < offset + text.length && it.endPosition > offset }
        .forEach { note ->
            val start = (note.startPosition - offset).coerceAtLeast(0)
            val end = (note.endPosition - offset).coerceAtMost(text.length)
            if (start >= end) return@forEach
            // ReaderScreen already resolves palette slots. Resolving a literal twice can
            // swap its slot again when a custom palette reuses another slot's old RGB.
            val color = runCatching { Color.parseColor(note.color) }
                .getOrDefault(if (note.type == "underline") 0xFFD6C58D.toInt() else 0x40D6C58D)
            val span = if (note.type == "underline") {
                val mode = RuleStyleJson.decode(note.styleSnapshotJson)?.underlineMode
                    ?.takeIf { it in 1..4 } ?: HighlightRule.UNDERLINE_WAVE
                WaveUnderlineSpan(color, mode, fromSavedAnnotation = true)
            } else ReaderHighlightSpan(color)
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
}
