package com.huangder.lumibooks.ui.reader

import android.graphics.Paint
import android.graphics.RectF
import android.text.Spannable
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.LineHeightSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.MetricAffectingSpan
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import kotlin.math.roundToInt
import com.huangder.lumibooks.util.parser.EpubParser

internal fun continuousCharacterTop(layout: Layout, offset: Int): Int =
    layout.getLineTop(layout.getLineForOffset(offset.coerceIn(0, layout.text.length)))

/** Uses the same line edges and span paint as Android's DynamicDrawableSpan.draw. */
internal fun continuousImageBounds(layout: Layout, image: ImageSpan, justificationMode: Int): RectF? {
    val text = layout.text as? Spanned ?: return null
    val start = text.getSpanStart(image)
    if (start < 0) return null
    val line = layout.getLineForOffset(start)
    val bounds = image.drawable.bounds
    val left = if (image is ContinuousComicImageSpan) -image.leftInset.toFloat() else
        ReaderLineGeometry(layout, text, justificationMode).horizontalPosition(start)
            ?: layout.getPrimaryHorizontal(start)
    val top = layout.getLineTop(line)
    val bottom = layout.getLineTop(line + 1)
    val drawTop = when (image.verticalAlignment) {
        DynamicDrawableSpan.ALIGN_BASELINE -> {
            val paint = TextPaint().apply { set(layout.paint) }
            text.getSpans(start, text.getSpanEnd(image), MetricAffectingSpan::class.java)
                .forEach { it.updateMeasureState(paint) }
            bottom - bounds.bottom - paint.fontMetricsInt.descent
        }
        DynamicDrawableSpan.ALIGN_CENTER -> top + (bottom - top) / 2 - bounds.height() / 2
        else -> bottom - bounds.bottom
    }
    return RectF(left + bounds.left, (drawTop + bounds.top).toFloat(),
        left + bounds.right, (drawTop + bounds.bottom).toFloat())
}

internal fun continuousImageCharacterTop(text: CharSequence, width: Int, gap: Int, offset: Int): Int? {
    val images = continuousChapterImages(text)
    if (images.isEmpty() || width <= 0) return null
    val spanned = text as Spanned
    var top = 0
    images.forEachIndexed { index, image ->
        if (offset < spanned.getSpanEnd(image) || index == images.lastIndex) return top
        val drawable = image.drawable
        val imageWidth = drawable.bounds.width().takeIf { it > 0 } ?: drawable.intrinsicWidth.coerceAtLeast(1)
        val imageHeight = drawable.bounds.height().takeIf { it > 0 } ?: drawable.intrinsicHeight.coerceAtLeast(1)
        top += (width.toFloat() * imageHeight / imageWidth).roundToInt() + gap
    }
    return top
}

/** Only media-only chapters use standalone image views; mixed chapters keep all text/spans. */
internal fun continuousChapterImages(text: CharSequence): List<ImageSpan> {
    val spanned = text as? Spanned ?: return emptyList()
    if (text.any { !it.isWhitespace() && it != '\uFFFC' }) return emptyList()
    return spanned.getSpans(0, spanned.length, ImageSpan::class.java)
        .sortedBy { spanned.getSpanStart(it) }
}

internal fun continuousChapterIsCover(text: CharSequence): Boolean {
    val spanned = text as? Spanned ?: return false
    return spanned.getSpans(0, spanned.length, EpubParser.CoverPageSpan::class.java).isNotEmpty()
}

/** Image wrappers belong to this view; do not change parser-owned cached drawables. */
internal fun sizeContinuousComicImages(text: CharSequence, width: Int, leftInset: Int, rightInset: Int) {
    val spanned = text as? Spannable ?: return
    if (width <= 0) return
    spanned.getSpans(0, spanned.length, ImageSpan::class.java).forEach { image ->
        val drawable = image.drawable as? AdjustedReaderDrawable ?: return@forEach
        if (drawable.isInlineFootnoteMarker) return@forEach
        val start = spanned.getSpanStart(image)
        val end = spanned.getSpanEnd(image)
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)) + 1
        val lineEnd = text.indexOf('\n', end).takeIf { it >= 0 } ?: text.length
        if ((lineStart until start).any { !text[it].isWhitespace() } ||
            (end until lineEnd).any { !text[it].isWhitespace() }) return@forEach
        val original = drawable.source.bounds
        if (original.width() > 0 && original.height() > 0) {
            drawable.setBounds(0, 0, width, (width.toFloat() * original.height() / original.width()).roundToInt().coerceAtLeast(1))
            val flags = spanned.getSpanFlags(image)
            spanned.removeSpan(image)
            spanned.setSpan(ContinuousComicImageSpan(drawable, image.source.orEmpty(),
                (width - leftInset - rightInset).coerceAtLeast(1), leftInset), start, end, flags)
        }
    }
}

/** Measures inside the text column but draws across the view's own horizontal padding. */
internal class ContinuousComicImageSpan(
    drawable: android.graphics.drawable.Drawable, source: String,
    private val textColumnWidth: Int, val leftInset: Int
) : ImageSpan(drawable, source, DynamicDrawableSpan.ALIGN_BOTTOM) {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        super.getSize(paint, text, start, end, fm)
        return textColumnWidth
    }
    override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        super.draw(canvas, text, start, end, -leftInset.toFloat(), top, y, bottom, paint)
    }
}

/** TextView must use multiplier 1: applying it to an ImageSpan also multiplies its height. */
internal fun protectContinuousImageHeights(text: Spannable, lineSpacing: Float = 1f, comicMode: Boolean = false) {
    text.getSpans(0, text.length, ContinuousLineHeight::class.java).forEach(text::removeSpan)
    val comicGaps = if (comicMode) {
        text.getSpans(0, text.length, ImageSpan::class.java)
            .sortedBy { text.getSpanStart(it) }.zipWithNext().mapNotNull { (before, after) ->
                val start = text.getSpanEnd(before)
                val end = text.getSpanStart(after)
                (start until end).takeIf { start < end && it.all { offset -> text[offset].isWhitespace() } }
            }
    } else emptyList()
    // Keep character offsets/bookmarks intact. Only collapse separator line metrics
    // between consecutive images; text before, between or after images remains selectable.
    comicGaps.forEach { gap ->
        text.getSpans(gap.first, gap.last + 1, EpubParser.ParagraphLineHeightSpan::class.java)
            .filter { text.getSpanStart(it) >= gap.first && text.getSpanEnd(it) <= gap.last + 1 }
            .forEach(text::removeSpan)
    }
    if (text.isNotEmpty()) text.setSpan(
        ContinuousLineHeight(lineSpacing, comicGaps), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}

private class ContinuousLineHeight(
    private val multiplier: Float, private val comicGaps: List<IntRange>
) : LineHeightSpan.WithDensity {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt
    ) = applyHeight(text, start, end, fm, null)

    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt, paint: TextPaint
    ) = applyHeight(text, start, end, fm, paint)

    private fun applyHeight(text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt, paint: TextPaint?) {
        val spanned = text as? Spanned ?: return
        val limit = end.coerceAtMost(text.length)
        val images = spanned.getSpans(start, limit, ImageSpan::class.java)
            .filter { spanned.getSpanStart(it) < limit && spanned.getSpanEnd(it) > start }
        val onlyImages = images.isNotEmpty() && (start until limit).all {
            text[it].isWhitespace() || images.any { image ->
                it >= spanned.getSpanStart(image) && it < spanned.getSpanEnd(image)
            }
        }
        if (onlyImages) {
            val height = images.maxOf { it.drawable.bounds.height().coerceAtLeast(1) }
            fm.ascent = -height
            fm.top = -height
            fm.descent = 0
            fm.bottom = 0
            return
        }
        // Parser-owned spacer lines already contain the exact paragraph gap in pixels.
        val blank = (start until limit).all { text[it] == '\n' || text[it] == '\r' }
        if (blank && comicGaps.any { start >= it.first && limit <= it.last + 1 }) {
            fm.ascent = 0
            fm.top = 0
            fm.descent = 0
            fm.bottom = 0
            return
        }
        if (blank && spanned.getSpans(start, limit, EpubParser.ParagraphLineHeightSpan::class.java)
                .any { spanned.getSpanStart(it) < limit && spanned.getSpanEnd(it) > start }) return
        // StaticLayout reuses the metrics mutated by chooseHeight for subsequent
        // wrapped lines in a paragraph. Derive fresh metrics from the font/spans,
        // otherwise a 1.5x multiplier grows as 1.5x, 2.25x, 3.375x, ... .
        if (paint != null) resetTextMetrics(spanned, start, limit, paint, fm)
        if (blank) return
        val textHeight = fm.descent - fm.ascent
        val imageHeight = images.maxOfOrNull { it.drawable.bounds.height() } ?: 0
        fm.ascent = minOf(fm.ascent, -imageHeight)
        fm.top = minOf(fm.top, fm.ascent)
        // Match TextView's last-line behavior, without scaling image or spacer rows.
        if (limit == text.length && text.lastOrNull() != '\n') return
        val extra = (textHeight * (multiplier.coerceAtLeast(0.1f) - 1f)).roundToInt()
        fm.descent += extra
        fm.bottom = maxOf(fm.bottom, fm.descent)
    }

    private fun resetTextMetrics(text: Spanned, start: Int, end: Int,
        base: TextPaint, target: Paint.FontMetricsInt) {
        var runStart = start
        var first = true
        while (runStart < end) {
            val runEnd = text.nextSpanTransition(runStart, end, MetricAffectingSpan::class.java)
            // TextPaint(Paint) only copies Paint fields, losing density (and
            // baselineShift). Dip-sized TXT headings would reserve 1x-density
            // line metrics while the layout/painter render full-size glyphs.
            val runPaint = TextPaint().apply { set(base) }
            text.getSpans(runStart, runEnd, MetricAffectingSpan::class.java)
                .filter { text.getSpanStart(it) < runEnd && text.getSpanEnd(it) > runStart }
                .forEach { it.updateMeasureState(runPaint) }
            val metrics = runPaint.fontMetricsInt
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                runPaint.getFontMetricsInt(text, runStart, runEnd - runStart,
                    runStart, runEnd - runStart, false, metrics)
            }
            if (runPaint.baselineShift < 0) {
                metrics.ascent += runPaint.baselineShift
                metrics.top += runPaint.baselineShift
            } else {
                metrics.descent += runPaint.baselineShift
                metrics.bottom += runPaint.baselineShift
            }
            target.ascent = if (first) metrics.ascent else minOf(target.ascent, metrics.ascent)
            target.top = if (first) metrics.top else minOf(target.top, metrics.top)
            target.descent = if (first) metrics.descent else maxOf(target.descent, metrics.descent)
            target.bottom = if (first) metrics.bottom else maxOf(target.bottom, metrics.bottom)
            first = false
            runStart = runEnd
        }
    }
}
