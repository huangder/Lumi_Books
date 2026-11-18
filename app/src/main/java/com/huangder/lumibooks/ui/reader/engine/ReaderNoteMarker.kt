package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import com.huangder.lumibooks.domain.model.Note

/**
 * Marks the last character of a saved annotation that carries note text.
 * [text] may be empty when the owner resolves the note lazily (paged reader).
 */
internal class ReaderNoteMarkerSpan(val text: String = "", val chapterEnd: Int = -1)

/** One drawn note icon. [rect] is in Layout coordinates of the owning text view. */
internal data class ReaderNoteMarker(
    val line: Int,
    val rect: RectF,
    val texts: List<String>,
    val chapterEnds: List<Int>
) {
    val text: String get() = joinReaderNoteTexts(texts)
}

internal fun Note.hasReaderNoteMarker(): Boolean =
    note.isNotBlank() && !isGeneratedByHighlightRule && !isInkStroke

/**
 * End offset (exclusive) of a note's last visible character. Trailing line
 * breaks/spaces can be trimmed from a page or fall between blocks, so the
 * icon must not be anchored to them.
 */
internal fun readerNoteMarkerEnd(text: CharSequence, start: Int, end: Int): Int {
    val floor = start.coerceIn(0, text.length)
    var e = end.coerceIn(floor, text.length)
    while (e > floor && text[e - 1].isWhitespace()) e--
    return if (e > floor) e else end.coerceIn(floor, text.length)
}

internal fun joinReaderNoteTexts(texts: List<String>): String =
    texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")

/**
 * Small speech-bubble icon placed after the last glyph of the line on which a
 * note ends. It is purely decorative over the existing layout: it never takes
 * layout space, so it may extend into the right page margin.
 */
internal object ReaderNoteMarkers {
    const val ALPHA = 0.45f
    private const val SIZE_EM = 0.78f
    private const val GAP_EM = 0.28f

    /** Placement in Layout coordinates for horizontal text. */
    fun collect(
        layout: Layout,
        text: Spanned,
        geometry: ReaderLineGeometry?,
        lines: IntRange? = null
    ): List<ReaderNoteMarker> {
        val spans = text.getSpans(0, text.length, ReaderNoteMarkerSpan::class.java)
        if (spans.isEmpty() || layout.lineCount == 0) return emptyList()
        val byLine = sortedMapOf<Int, MutableList<ReaderNoteMarkerSpan>>()
        spans.sortedBy { text.getSpanEnd(it) }.forEach { span ->
            val end = text.getSpanEnd(span)
            if (end <= 0) return@forEach
            val line = layout.getLineForOffset((end - 1).coerceIn(0, text.length - 1))
            if (lines != null && line !in lines) return@forEach
            byLine.getOrPut(line) { mutableListOf() }.add(span)
        }
        val textSize = layout.paint.textSize
        val size = textSize * SIZE_EM
        val gap = textSize * GAP_EM
        val fm = layout.paint.fontMetrics
        return byLine.map { (line, lineSpans) ->
            val right = geometry?.lineRange(line)?.right ?: layout.getLineRight(line)
            val baseline = layout.getLineBaseline(line).toFloat()
            val centerY = baseline + (fm.ascent + fm.descent) / 2f
            val left = right + gap
            ReaderNoteMarker(
                line = line,
                rect = RectF(left, centerY - size / 2f, left + size, centerY + size / 2f),
                texts = lineSpans.map { it.text },
                chapterEnds = lineSpans.map { it.chapterEnd }
            )
        }
    }

    /** Placement below the last glyph of a vertical column. */
    fun verticalRect(columnCenterX: Float, columnBottom: Float, textSize: Float): RectF {
        val size = textSize * SIZE_EM
        val top = columnBottom + textSize * GAP_EM
        return RectF(columnCenterX - size / 2f, top, columnCenterX + size / 2f, top + size)
    }

    fun hit(markers: List<ReaderNoteMarker>, x: Float, y: Float, minTouchPx: Float): ReaderNoteMarker? =
        markers.firstOrNull { touchRect(it.rect, minTouchPx).contains(x, y) }

    fun touchRect(rect: RectF, minTouchPx: Float): RectF {
        val dx = ((minTouchPx - rect.width()) / 2f).coerceAtLeast(0f)
        val dy = ((minTouchPx - rect.height()) / 2f).coerceAtLeast(0f)
        return RectF(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val bubble = RectF()

    /** Draws an outlined speech bubble with two text lines, tinted with [textColor]. */
    fun draw(canvas: Canvas, rect: RectF, textColor: Int) {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        val stroke = (w * 0.085f).coerceAtLeast(1f)
        val alpha = ((textColor ushr 24) * ALPHA).toInt().coerceIn(0, 255)
        paint.color = (alpha shl 24) or (textColor and 0x00FFFFFF)
        paint.strokeWidth = stroke
        val inset = stroke / 2f
        bubble.set(rect.left + inset, rect.top + h * 0.08f, rect.right - inset, rect.top + h * 0.76f)
        val radius = bubble.height() * 0.3f
        path.reset()
        path.addRoundRect(bubble, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, paint)
        // Tail under the bottom-left corner.
        path.reset()
        path.moveTo(rect.left + w * 0.24f, bubble.bottom)
        path.lineTo(rect.left + w * 0.24f, rect.bottom - inset)
        path.lineTo(rect.left + w * 0.46f, bubble.bottom)
        canvas.drawPath(path, paint)
        // Two "text" strokes inside the bubble.
        val lineLeft = bubble.left + bubble.width() * 0.24f
        val firstY = bubble.top + bubble.height() * 0.38f
        val secondY = bubble.top + bubble.height() * 0.66f
        canvas.drawLine(lineLeft, firstY, bubble.right - bubble.width() * 0.24f, firstY, paint)
        canvas.drawLine(lineLeft, secondY, bubble.right - bubble.width() * 0.42f, secondY, paint)
    }
}
