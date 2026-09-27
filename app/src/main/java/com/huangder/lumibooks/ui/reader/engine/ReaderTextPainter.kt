package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Path
import android.graphics.RectF
import android.icu.text.BreakIterator
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import com.huangder.lumibooks.util.parser.InlineFootnoteMarkerDrawable
import com.huangder.lumibooks.util.parser.ReaderImageSizing

/** Resolve the real glyph paint, without the measurement-only punctuation scale. */
internal fun readerGlyphPaint(
    base: TextPaint,
    text: Spanned,
    start: Int,
    end: Int,
    out: TextPaint = TextPaint()
): TextPaint {
    out.set(base)
    out.bgColor = 0
    out.baselineShift = 0
    text.getSpans(start, end, CharacterStyle::class.java).forEach { span ->
        if (span !is ReaderPunctuationCompressionSpan && span !is ReplacementSpan &&
            span !is BackgroundColorSpan) span.updateDrawState(out)
    }
    return out
}

internal fun readerVisibleLines(canvas: Canvas, layout: Layout): IntRange {
    if (layout.lineCount == 0) return IntRange.EMPTY
    val clip = canvas.clipBounds
    if (clip.bottom <= 0 || clip.top >= layout.height) return IntRange.EMPTY
    return layout.getLineForVertical(clip.top.coerceAtLeast(0))..
        layout.getLineForVertical((clip.bottom - 1).coerceAtMost(layout.height - 1))
}

/**
 * Shared ink layer for paged and continuous reading. Layout remains the owner
 * of line breaks and bidi; only punctuation ink is drawn at its natural scale.
 * Work is bounded by the canvas clip, even when a chapter has thousands of lines.
 */
internal class ReaderTextPainter {
    private val glyphPaint = TextPaint()
    private val clusters = BreakIterator.getCharacterInstance()
    private val runPath = Path()
    private val runBounds = RectF()

    fun draw(canvas: Canvas, layout: Layout, text: Spanned, geometry: ReaderLineGeometry,
             frameworkImagePlacement: Boolean = false) {
        val saved = canvas.save()
        canvas.clipRect(0f, 0f, layout.width.toFloat(), layout.height.toFloat())
        for (line in readerVisibleLines(canvas, layout)) {
            val start = layout.getLineStart(line)
            val end = readerLineContentEnd(text, start, layout.getLineEnd(line))
            if (start >= end) continue
            // A line-sized copy avoids copying/scanning an entire long chapter per frame.
            clusters.setText(text.subSequence(start, end).toString())
            val baseline = layout.getLineBaseline(line).toFloat()
            var index = start
            while (index < end) {
                val replacement = text.getSpans(index, index + 1, ReplacementSpan::class.java)
                    .lastOrNull()
                if (replacement != null) {
                    val spanStart = text.getSpanStart(replacement)
                    val spanEnd = text.getSpanEnd(replacement).coerceAtMost(end)
                    if (spanStart == index && spanEnd > index) {
                        val paint = readerGlyphPaint(layout.paint, text, index, spanEnd, glyphPaint)
                        val range = geometry.horizontalRange(line, index, spanEnd)
                        if (range != null) {
                            if (replacement is ImageSpan && !frameworkImagePlacement) {
                                drawImage(canvas, layout, line, replacement, range.left, layout.paint.textSize)
                            } else {
                                replacement.draw(canvas, text, index, spanEnd, range.left,
                                    layout.getLineTop(line), baseline.toInt(), layout.getLineBottom(line), paint)
                            }
                        }
                    }
                    index = maxOf(index + 1, spanEnd)
                    continue
                }
                val nextBoundary = clusters.following(index - start)
                var next = if (nextBoundary == BreakIterator.DONE) end else start + nextBoundary
                // Joining scripts must be painted as a complete style/bidi run, not
                // once per grapheme (a ligature can cover multiple graphemes).
                val script = Character.UnicodeScript.of(Character.codePointAt(text, index))
                val joining = script != Character.UnicodeScript.HAN &&
                    script != Character.UnicodeScript.COMMON &&
                    script != Character.UnicodeScript.HIRAGANA && script != Character.UnicodeScript.KATAKANA &&
                    script != Character.UnicodeScript.HANGUL
                if (joining) {
                    val limit = text.nextSpanTransition(index, end, CharacterStyle::class.java)
                    while (next < limit && Character.UnicodeScript.of(Character.codePointAt(text, next)) == script &&
                        layout.isRtlCharAt(next) == layout.isRtlCharAt(index)) {
                        val boundary = clusters.following(next - start)
                        next = if (boundary == BreakIterator.DONE) limit else minOf(limit, start + boundary)
                    }
                }
                val paint = readerGlyphPaint(layout.paint, text, index, next, glyphPaint)
                val range = geometry.horizontalRange(line, index, next)
                if (range != null) {
                    val rtl = layout.isRtlCharAt(index)
                    if (next == index + 1 && readerIsCompressedPunctuation(text, index)) {
                        val slot = readerPunctuationSlotWidth(paint, text, index, next)
                        val shift = readerPunctuationDrawShift(paint, text.subSequence(index, next), slot)
                        // Tracking belongs between slots, not inside their visible ink.
                        paint.letterSpacing = 0f
                        canvas.drawText(text, index, next, range.left + shift,
                            baseline + paint.baselineShift, paint)
                    } else if (joining) {
                        runPath.reset()
                        layout.getSelectionPath(index, next, runPath)
                        runPath.computeBounds(runBounds, true)
                        canvas.drawTextRun(text, index, next, index, next,
                            runBounds.left,
                            baseline + paint.baselineShift, rtl, paint)
                    } else {
                        // Give the shaper the complete style/direction run. Drawing an
                        // isolated UTF-16 char breaks emoji, combining marks and Arabic.
                        var contextStart = index
                        while (contextStart > start && layout.isRtlCharAt(contextStart - 1) == rtl &&
                            text.getSpans(contextStart - 1, contextStart, ReplacementSpan::class.java).isEmpty() &&
                            !readerIsCompressedPunctuation(text, contextStart - 1)) contextStart--
                        var contextEnd = next
                        while (contextEnd < end && layout.isRtlCharAt(contextEnd) == rtl &&
                            text.getSpans(contextEnd, contextEnd + 1, ReplacementSpan::class.java).isEmpty() &&
                            !readerIsCompressedPunctuation(text, contextEnd)) contextEnd++
                        contextStart = maxOf(contextStart,
                            text.getSpans(contextStart, index + 1, CharacterStyle::class.java)
                                .filter { text.getSpanStart(it) <= index && text.getSpanEnd(it) > index }
                                .maxOfOrNull { text.getSpanStart(it) } ?: contextStart)
                        contextEnd = text.nextSpanTransition(index, contextEnd, CharacterStyle::class.java)
                            .coerceAtLeast(next)
                        // Geometry already positions these individual clusters
                        // with tracking. Applying tracking inside drawTextRun as
                        // well adds a half-gap to the ink, including at the final
                        // glyph, where it can spill into the reader's right margin.
                        paint.letterSpacing = 0f
                        canvas.drawTextRun(text, index, next, contextStart, contextEnd,
                            range.left,
                            baseline + paint.baselineShift, rtl, paint)
                    }
                }
                index = next
            }
        }
        canvas.restoreToCount(saved)
    }

    private fun drawImage(canvas: Canvas, layout: Layout, line: Int, span: ImageSpan,
                          left: Float, textSize: Float) {
        val drawable = span.drawable
        val savedBounds = Rect(drawable.bounds)
        val width = savedBounds.width().toFloat()
        val height = savedBounds.height().toFloat()
        val top = layout.getLineTop(line).toFloat()
        val bottom = layout.getLineBottom(line).toFloat()
        val slotTop = when (span.verticalAlignment) {
            DynamicDrawableSpan.ALIGN_BASELINE -> layout.getLineBaseline(line) - height
            DynamicDrawableSpan.ALIGN_CENTER -> top + (bottom - top - height) / 2f
            else -> bottom - height
        }
        val rect = ReaderImageSizing.drawRect(left, slotTop, width, height,
            (drawable as? InlineFootnoteMarkerDrawable)?.isInlineFootnoteMarker == true,
            textSize * ReaderImageSizing.INLINE_MARKER_EM)
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(),
            (rect.left + rect.width).toInt(), (rect.top + rect.height).toInt())
        try { drawable.draw(canvas) } finally { drawable.bounds = savedBounds }
    }
}
