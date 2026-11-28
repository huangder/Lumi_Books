package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.widget.TextView
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.highlight.RuleStyleJson
import com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightPainter
import com.huangder.lumibooks.ui.reader.engine.ReaderHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.ReaderNoteMarkerSpan
import com.huangder.lumibooks.ui.reader.engine.hasReaderNoteMarker
import com.huangder.lumibooks.ui.reader.engine.readerNoteMarkerEnd
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import com.huangder.lumibooks.ui.reader.engine.ReaderTextPainter
import com.huangder.lumibooks.ui.reader.engine.TtsSentenceHighlightSpan
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import com.huangder.lumibooks.ui.reader.engine.drawReaderUnderline
import com.huangder.lumibooks.ui.reader.engine.readerLineContentEnd
import com.huangder.lumibooks.ui.reader.engine.readerVisibleLines

/** Shares the reader's geometry for annotations and rows that need custom spacing. */
internal open class MarkdownAnnotationTextView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private val highlightPainter = ReaderHighlightPainter(Paint(Paint.ANTI_ALIAS_FLAG), density,
        keepCornersInsideLayout = true)
    private var geometryLayout: android.text.Layout? = null
    private var geometryText: CharSequence? = null
    private var geometry: ReaderLineGeometry? = null
    private val textPainter = ReaderTextPainter()
    var readerJustification = Layout.JUSTIFICATION_MODE_NONE
    var continuesParagraph = false
    private val underlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
    }

    /** 笔记高亮/划线的用户几何参数；只影响绘制。 */
    var annotationStyle: ReaderAnnotationStyle = ReaderAnnotationStyle.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        drawAnnotations(canvas, underlines = false)
        val text = text as? Spanned
        val layout = layout
        if (text == null || layout == null || (readerJustification == Layout.JUSTIFICATION_MODE_NONE &&
                text.getSpans(0, text.length, ReaderNaturalAlignmentSpan::class.java).isEmpty())) {
            super.onDraw(canvas)
        } else {
            val geometry = geometryFor(layout, text)
            val dx = totalPaddingLeft.toFloat() - scrollX
            val dy = totalPaddingTop.toFloat() - scrollY
            canvas.save()
            canvas.translate(dx, dy)
            val visible = readerVisibleLines(canvas, layout)
            geometry.retainLines(visible)
            val adjusted = visible.filter(geometry::isLineStretched)
            for (line in adjusted) canvas.clipOutRect(0, layout.getLineTop(line), layout.width, layout.getLineBottom(line))
            canvas.translate(-dx, -dy)
            super.onDraw(canvas)
            canvas.restore()
            if (adjusted.isNotEmpty()) {
                layout.paint.color = currentTextColor
                layout.paint.linkColor = paint.linkColor
                canvas.save()
                canvas.translate(dx, dy)
                textPainter.draw(canvas, layout, text, geometry, lines = adjusted, drawSpanBackgrounds = true)
                canvas.restore()
            }
        }
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
                val speech = text.getSpans(start, end, TtsSentenceHighlightSpan::class.java)
                if (spans.isEmpty() && speech.isEmpty()) return
                val lineGeometry = geometryFor(layout, text)
                spans.forEach { span ->
                    highlightPainter.drawRange(canvas, layout, text, lineGeometry,
                        text.getSpanStart(span), text.getSpanEnd(span), span.color, style = annotationStyle)
                }
                speech.forEach { span ->
                    highlightPainter.drawRange(canvas, layout, text, lineGeometry,
                        text.getSpanStart(span), text.getSpanEnd(span), span.color, singleBlock = true)
                }
                return
            }
            val spans = text.getSpans(start, end, WaveUnderlineSpan::class.java)
            if (spans.isEmpty()) return
            val lineGeometry = geometryFor(layout, text)
            underlinePaint.strokeWidth = annotationStyle.underlineThicknessDp * density
            val underlineOffset = annotationStyle.underlineOffsetYDp * density
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
                    val cross = layout.getLineBaseline(line) + paint.fontMetrics.descent.coerceAtLeast(1f) + density +
                        underlineOffset
                    drawReaderUnderline(canvas, underlinePaint, span.mode, range.left, range.right, cross, false,
                        density, annotationStyle.underlinePatternScale)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    internal fun geometryFor(layout: Layout, text: CharSequence = this.text): ReaderLineGeometry {
        if (geometryLayout !== layout || geometryText !== text) {
            geometryLayout = layout
            geometryText = text
            geometry = ReaderLineGeometry(layout, text, readerJustification, continuesParagraph)
        }
        return checkNotNull(geometry)
    }
}

internal fun applyMarkdownAnnotationSpans(
    text: Spannable,
    offset: Int,
    notes: List<Note>,
    /** Whole-document text, used to find a note's last visible character across blocks. */
    documentText: CharSequence? = null
) {
    text.getSpans(0, text.length, ReaderHighlightSpan::class.java).forEach(text::removeSpan)
    text.getSpans(0, text.length, WaveUnderlineSpan::class.java).forEach(text::removeSpan)
    text.getSpans(0, text.length, ReaderNoteMarkerSpan::class.java).forEach(text::removeSpan)
    notes.filter { !it.isStale && it.startPosition < offset + text.length && it.endPosition > offset }
        .forEach { note ->
            val start = (note.startPosition - offset).coerceAtLeast(0)
            val end = (note.endPosition - offset).coerceAtMost(text.length)
            if (start >= end) return@forEach
            // 只在笔记最后一个可见字所在的文本块显示消息图标
            if (note.hasReaderNoteMarker()) {
                val visibleEnd = documentText?.let { readerNoteMarkerEnd(it, note.startPosition, note.endPosition) }
                    ?: note.endPosition
                val local = visibleEnd - offset
                if (local in 1..text.length && local > start) {
                    text.setSpan(ReaderNoteMarkerSpan(note.note, note.endPosition), local - 1, local,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
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
