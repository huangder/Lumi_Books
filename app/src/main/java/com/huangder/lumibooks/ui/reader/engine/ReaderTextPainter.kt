package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Path
import android.graphics.RectF
import android.icu.text.BreakIterator
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import com.huangder.lumibooks.util.parser.InlineFootnoteMarkerDrawable
import com.huangder.lumibooks.util.parser.ReaderImageSizing
import com.huangder.lumibooks.ui.reader.usesReaderEnglishHyphenation

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
             frameworkImagePlacement: Boolean = false, imageBleedLeft: Int = 0, imageBleedRight: Int = 0) {
        val saved = canvas.save()
        canvas.clipRect(-imageBleedLeft.toFloat(), 0f, (layout.width + imageBleedRight).toFloat(), layout.height.toFloat())
        for (line in readerVisibleLines(canvas, layout)) {
            val native = geometry.nativeLine(line)
            if (native != null) {
                native.draw(canvas)
                continue
            }
            val start = layout.getLineStart(line)
            val end = readerLineContentEnd(text, start, layout.getLineEnd(line))
            if (start >= end) continue
            // A line-sized copy avoids copying/scanning an entire long chapter per frame.
            val lineText = TextUtils.substring(text, start, end)
            val lineChars = lineText.toCharArray()
            clusters.setText(lineText)
            val baseline = layout.getLineBaseline(line).toFloat()
            var shapedContext: IntRange? = null
            val shapedAdvances = FloatArray(lineChars.size)
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
                // Latin uses shaped clusters below: drawing a whole word from
                // Layout's unexpanded selection path bypasses reader justification
                // and puts its ink at different coordinates from punctuation/touches.
                val script = Character.UnicodeScript.of(Character.codePointAt(text, index))
                val joining = script != Character.UnicodeScript.HAN &&
                    script != Character.UnicodeScript.COMMON &&
                    script != Character.UnicodeScript.LATIN &&
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
                val compressed = next == index + 1 && readerIsCompressedPunctuation(text, index)
                val rtl = layout.isRtlCharAt(index)
                val context = if (!joining && !compressed) {
                    (shapedContext?.takeIf { index >= it.first && next <= it.last + 1 }
                        ?: drawContext(layout, text,
                            maxOf(start, shapedContext?.let { it.last + 1 } ?: start),
                            end, index, next, rtl)).also { context ->
                        // Each cluster is positioned by the same geometry used by
                        // hit testing. Keep the complete shaping context and merge
                        // zero-advance continuations (e.g. the ffi ligature in office)
                        // so one glyph is never painted once per UTF-16 character.
                        paint.letterSpacing = 0f
                        if (shapedContext != context) {
                            val localStart = context.first - start
                            val count = context.last + 1 - context.first
                            paint.getTextRunAdvances(lineChars, localStart, count, localStart, count,
                                rtl, shapedAdvances, localStart)
                            shapedContext = context
                        }
                        while (next <= context.last && shapedAdvances[next - start] == 0f) {
                            val boundary = clusters.following(next - start)
                            next = if (boundary == BreakIterator.DONE) context.last + 1
                                else minOf(context.last + 1, start + boundary)
                        }
                    }
                } else null
                val range = geometry.horizontalRange(line, index, next)
                if (range != null) {
                    if (compressed) {
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
                    } else if (context != null) {
                        canvas.drawTextRun(lineChars, index - start, next - index,
                            context.first - start, context.last + 1 - context.first,
                            range.left,
                            baseline + paint.baselineShift, rtl, paint)
                    }
                }
                index = next
            }
        }
        canvas.restoreToCount(saved)
    }

    private fun drawContext(layout: Layout, text: Spanned, lineStart: Int, lineEnd: Int,
                            start: Int, end: Int, rtl: Boolean): IntRange {
        var contextStart = start
        while (contextStart > lineStart && layout.isRtlCharAt(contextStart - 1) == rtl &&
            text.getSpans(contextStart - 1, contextStart, ReplacementSpan::class.java).isEmpty() &&
            !readerIsCompressedPunctuation(text, contextStart - 1)) contextStart--
        // Bound the scan by the next style change before looking for bidi,
        // punctuation or replacement boundaries. Scanning the whole line for
        // every bold/plain prefix makes bionic lines quadratic in word count.
        val styleEnd = text.nextSpanTransition(start, lineEnd, CharacterStyle::class.java).coerceAtLeast(end)
        var contextEnd = end
        while (contextEnd < styleEnd && layout.isRtlCharAt(contextEnd) == rtl &&
            text.getSpans(contextEnd, contextEnd + 1, ReplacementSpan::class.java).isEmpty() &&
            !readerIsCompressedPunctuation(text, contextEnd)) contextEnd++
        // A span END also starts a new shaping run. Looking only at active
        // spans' starts can shape plain text using a preceding font/size run.
        var transition = contextStart
        while (transition < start) {
            val nextTransition = text.nextSpanTransition(transition, contextEnd, CharacterStyle::class.java)
            if (nextTransition > start) break
            contextStart = nextTransition
            transition = nextTransition
        }
        contextEnd = text.nextSpanTransition(start, contextEnd, CharacterStyle::class.java).coerceAtLeast(end)
        return contextStart until contextEnd
    }

    private fun drawImage(canvas: Canvas, layout: Layout, line: Int, span: ImageSpan,
                          left: Float, textSize: Float) {
        val drawable = span.drawable
        val isInlineFootnoteMarker =
            (drawable as? InlineFootnoteMarkerDrawable)?.isInlineFootnoteMarker == true
        val savedBounds = Rect(drawable.bounds)
        val width = savedBounds.width().toFloat()
        val height = savedBounds.height().toFloat()
        val top = layout.getLineTop(line).toFloat()
        val bottom = layout.getLineBottom(line).toFloat()
        val slotTop = if (isInlineFootnoteMarker) {
            // Footnote icons are text-like inline marks. ALIGN_BOTTOM uses the whole
            // line box (including descent/line spacing), which makes them look like
            // subscripts; pin their bottom edge to the text baseline instead.
            layout.getLineBaseline(line) - height
        } else when (span.verticalAlignment) {
            DynamicDrawableSpan.ALIGN_BASELINE -> layout.getLineBaseline(line) - height
            DynamicDrawableSpan.ALIGN_CENTER -> top + (bottom - top - height) / 2f
            else -> bottom - height
        }
        val rect = ReaderImageSizing.drawRect(left, slotTop, width, height,
            isInlineFootnoteMarker,
            textSize * ReaderImageSizing.INLINE_MARKER_EM)
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(),
            (rect.left + rect.width).toInt(), (rect.top + rect.height).toInt())
        try { drawable.draw(canvas) } finally { drawable.bounds = savedBounds }
    }
}
