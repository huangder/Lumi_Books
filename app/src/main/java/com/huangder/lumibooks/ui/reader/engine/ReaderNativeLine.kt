package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan

/** Native shaped runs, including discretionary hyphens, shared by ink and selection. */
internal class ReaderNativeLine private constructor(
    val start: Int,
    val end: Int,
    val positions: FloatArray,
    val endHyphenEdit: Int,
    private val runs: List<Run>,
    private val decorations: List<Decoration>
) {
    private class Run(val chars: CharArray, val index: Int, val count: Int,
        val offset: Int, var x: Float, val y: Float, val paint: TextPaint)
    private class Decoration(var left: Float, val top: Float, var right: Float,
        val bottom: Float, val paint: Paint)

    fun draw(canvas: Canvas) {
        decorations.forEach { canvas.drawRect(it.left, it.top, it.right, it.bottom, it.paint) }
        runs.forEach { canvas.drawTextRun(it.chars, it.index, it.count, 0, it.chars.size,
            it.x, it.y, false, it.paint) }
    }

    fun position(offset: Int): Float = positions[(offset - start).coerceIn(0, positions.lastIndex)]

    fun offset(x: Float): Int {
        for (i in 0 until positions.lastIndex) {
            if (x < (positions[i] + positions[i + 1]) / 2f) return start + i
        }
        return end
    }

    companion object {
        /** Uses public Canvas callbacks; no reflection into Layout's hidden hyphen APIs. */
        fun measure(layout: Layout, line: Int, justification: Int, forceLast: Boolean = false,
            trailingHyphenEdit: Int = 0): ReaderNativeLine? {
            if (Build.VERSION.SDK_INT < 29 || layout.getParagraphDirection(line) != Layout.DIR_LEFT_TO_RIGHT) return null
            val text = layout.text
            val start = layout.getLineStart(line)
            val rawEnd = layout.getLineEnd(line)
            val end = readerLineContentEnd(text, start, rawEnd)
            if (end <= start || (text is Spanned &&
                text.getSpans(start, end, ReplacementSpan::class.java).isNotEmpty())) return null
            val runs = mutableListOf<Run>()
            val decorations = mutableListOf<Decoration>()
            var supported = true
            val capture = object : Canvas() {
                override fun getClipBounds(bounds: Rect): Boolean {
                    bounds.set(0, layout.getLineTop(line), layout.width, layout.getLineBottom(line) - 1)
                    return true
                }
                override fun drawTextRun(text: CharSequence, a: Int, b: Int, contextStart: Int,
                    contextEnd: Int, x: Float, y: Float, rtl: Boolean, paint: Paint) {
                    if (rtl) { supported = false; return }
                    // AOSP supplies chapter offsets; Xiaomi TextLine supplies a
                    // line subsequence with local offsets. Preserve the original
                    // source positions for drawing, selection and cached advances.
                    val lineLocal = text !== layout.text && text.length in (end - start)..(rawEnd - start) &&
                        text.indices.all { text[it] == layout.text[start + it] }
                    val offset = if (lineLocal) start + a else a
                    val chars = CharArray(contextEnd - contextStart)
                    TextUtils.getChars(text, contextStart, contextEnd, chars, 0)
                    record(chars, a - contextStart, b - a, offset, x, y, paint)
                }
                override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int,
                    contextCount: Int, x: Float, y: Float, rtl: Boolean, paint: Paint) {
                    if (rtl) { supported = false; return }
                    record(text.copyOfRange(contextIndex, contextIndex + contextCount),
                        index - contextIndex, count, start + index, x, y, paint)
                }
                private fun record(chars: CharArray, index: Int, count: Int, offset: Int,
                    x: Float, y: Float, paint: Paint) {
                    if (offset < start || offset >= end) return
                    val copy = TextPaint().apply {
                        if (paint is TextPaint) set(paint) else set(paint)
                    }
                    if (offset + count == end && trailingHyphenEdit != 0) copy.endHyphenEdit = trailingHyphenEdit
                    runs.add(Run(chars, index, count, offset, x, y, copy))
                }
                override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
                    if (top >= layout.getLineBottom(line) || bottom <= layout.getLineTop(line)) return
                    decorations.add(Decoration(left, top, right, bottom, Paint(paint)))
                }
            }
            layout.draw(capture)
            if (!supported || runs.isEmpty()) return null
            fun positions(): FloatArray {
                val result = FloatArray(end - start + 1) { Float.NaN }
                for (run in runs) {
                    val advances = FloatArray(run.count)
                    run.paint.getTextRunAdvances(run.chars, run.index, run.count, 0,
                        run.chars.size, false, advances, 0)
                    var x = run.x
                    for (i in 0..run.count) {
                        val local = run.offset + i - start
                        if (local in result.indices) result[local] = x
                        if (i < run.count) x += advances[i]
                    }
                }
                return result
            }
            val native = positions()
            if (native.any { !it.isFinite() }) return null
            val spaces = (start until end).filter { text[it] == ' ' }
            val justify = justification == Layout.JUSTIFICATION_MODE_INTER_WORD &&
                layout.getParagraphAlignment(line) == Layout.Alignment.ALIGN_NORMAL &&
                shouldJustifyReaderLine(line, layout.lineCount,
                    readerLineEndsParagraph(text, start, layout.getLineEnd(line)), forceLast)
            // Some platform/font combinations round advances differently during
            // breaking and drawing. Absorb that small excess in word spaces too.
            val extra = if (justify && spaces.isNotEmpty()) {
                val minimumSpace = spaces.minOf { native[it + 1 - start] - native[it - start] }
                ((layout.getParagraphRight(line) - native.last()) / spaces.size)
                    .coerceAtLeast(-minimumSpace * 0.5f)
            } else 0f
            if (extra != 0f) {
                fun shifted(x: Float): Float = x + spaces.sumOf { offset ->
                    val left = native[offset - start]
                    val width = native[offset + 1 - start] - left
                    ((x - left) / width.coerceAtLeast(0.01f)).coerceIn(0f, 1f).toDouble()
                }.toFloat() * extra
                decorations.forEach { it.left = shifted(it.left); it.right = shifted(it.right) }
                runs.forEach { run ->
                    run.x += spaces.count { it < run.offset } * extra
                    run.paint.wordSpacing += extra
                }
            }
            return ReaderNativeLine(start, end, positions(), runs.last().paint.endHyphenEdit, runs, decorations)
        }
    }
}
