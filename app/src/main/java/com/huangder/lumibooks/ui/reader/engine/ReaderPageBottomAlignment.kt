package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Paint
import android.text.Layout
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LineHeightSpan
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Vertical justification of a full page.
 *
 * Pagination always stops before a line that would not fit, so the gap
 * between the last line and the bottom margin varies from page to page
 * (between zero and one line height, more after paragraph spacing). This span
 * spreads that remainder evenly over the page's inter-line gaps, so every
 * non-final page ends at the same bottom edge.
 *
 * The span lives in the page Spannable itself. The selectable TextView's
 * Layout, which is the visible renderer's geometry source, and the visible
 * renderer's fallback StaticLayout therefore get the same line positions.
 */
internal class ReaderPageBottomAlignSpan(
    /** Line end offset -> extra descent in pixels. */
    private val extraByLineEnd: Map<Int, Int>
) : LineHeightSpan {
    // StaticLayout hands the same FontMetricsInt to every line of a paragraph,
    // so a plain `+=` would accumulate line after line. Remember what this span
    // wrote for the previous line and undo it when the metrics were carried over.
    private var lastEnd = -1
    private var lastDescent = 0
    private var lastBottom = 0
    private var lastExtra = 0

    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int,
        spanstartv: Int, lineHeight: Int,
        fm: Paint.FontMetricsInt
    ) {
        val carried = start == lastEnd && lastExtra != 0 &&
            fm.descent == lastDescent && fm.bottom == lastBottom
        if (carried) {
            fm.descent -= lastExtra
            fm.bottom -= lastExtra
        }
        val extra = extraByLineEnd[end] ?: 0
        fm.descent += extra
        fm.bottom += extra
        lastEnd = end
        lastDescent = fm.descent
        lastBottom = fm.bottom
        lastExtra = extra
    }
}

internal data class ReaderPageTextMetrics(
    val paint: TextPaint,
    val width: Int,
    val height: Int,
    val lineSpacingExtra: Float,
    val lineSpacingMultiplier: Float,
    val breakStrategy: Int,
    val hyphenationFrequency: Int,
    val justificationMode: Int
)

private const val BOTTOM_SAFETY_PX = 2

private fun ReaderPageTextMetrics.build(text: CharSequence): StaticLayout {
    val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        .setIncludePad(false)
        .setBreakStrategy(breakStrategy)
        .setHyphenationFrequency(hyphenationFrequency)
        .setJustificationMode(justificationMode)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        builder.setUseLineSpacingFromFallbacks(false)
    }
    return builder.build()
}

private fun isBlankLine(text: CharSequence, layout: Layout, line: Int): Boolean {
    val start = layout.getLineStart(line)
    val end = layout.getLineEnd(line)
    return (start until end).all { text[it].isWhitespace() }
}

private fun lastInkLine(text: CharSequence, layout: Layout): Int {
    var last = layout.lineCount - 1
    while (last >= 0 && isBlankLine(text, layout, last)) last--
    return last
}

/**
 * Attach a [ReaderPageBottomAlignSpan] to [page] so its last visible line ends
 * at the page's bottom margin. Pages with a large remainder (an image or
 * heading moved to the next page) stay as they are, because spreading that
 * much space would open visibly large gaps.
 */
internal fun applyReaderPageBottomAlignment(page: Spannable, metrics: ReaderPageTextMetrics) {
    page.getSpans(0, page.length, ReaderPageBottomAlignSpan::class.java).forEach(page::removeSpan)
    if (page.isEmpty() || metrics.width <= 0 || metrics.height <= 0) return
    val target = metrics.height - BOTTOM_SAFETY_PX
    val descent = metrics.paint.fontMetricsInt.descent

    val base = metrics.build(page)
    val lastInk = lastInkLine(page, base)
    if (lastInk <= 0) return
    val slack = target - (base.getLineBaseline(lastInk) + descent)
    if (slack <= 0) return

    val bodyLine = (metrics.paint.fontMetricsInt.let { it.descent - it.ascent } *
        metrics.lineSpacingMultiplier + metrics.lineSpacingExtra).coerceAtLeast(1f)
    if (slack > bodyLine * 2f) return

    // Spacer rows (paragraph spacing) already have a fixed height owned by
    // ParagraphLineHeightSpan; only text lines followed by another line receive extra space.
    val gaps = (0 until lastInk).filter { !isBlankLine(page, base, it) }
    if (gaps.isEmpty()) return
    if (slack.toFloat() / gaps.size > bodyLine * 0.5f) return

    // Extra descent is scaled again by the line spacing multiplier.
    val multiplier = metrics.lineSpacingMultiplier.coerceAtLeast(0.1f)
    fun distribute(budget: Int): Map<Int, Int> {
        val each = budget / gaps.size
        val remainder = budget % gaps.size
        val extras = HashMap<Int, Int>(gaps.size)
        gaps.forEachIndexed { index, line ->
            // Spread the remainder over the whole page rather than the top lines only.
            val bonus = if (remainder > 0 && (index * remainder) / gaps.size !=
                ((index + 1) * remainder) / gaps.size) 1 else 0
            val extra = each + bonus
            if (extra > 0) extras[base.getLineEnd(line)] = extra
        }
        return extras
    }

    // Spacing rounding makes the result inexact; converge on the closest fit
    // that never crosses the bottom margin.
    var budget = floor(slack / multiplier).toInt()
    var best: Map<Int, Int>? = null
    var bestGap = slack
    val tried = HashSet<Int>()
    for (attempt in 0 until 5) {
        if (budget <= 0 || !tried.add(budget)) break
        val extras = distribute(budget)
        val span = ReaderPageBottomAlignSpan(extras)
        page.setSpan(span, 0, page.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        val aligned = metrics.build(page)
        page.removeSpan(span)
        val alignedInk = lastInkLine(page, aligned)
        if (alignedInk != lastInk || aligned.lineCount != base.lineCount) break
        val gap = target - (aligned.getLineBaseline(alignedInk) + descent)
        if (gap in 0 until bestGap) {
            best = extras
            bestGap = gap
        }
        if (gap == 0) break
        budget += if (gap > 0) floor(gap / multiplier).toInt().coerceAtLeast(1)
            else -ceil(-gap / multiplier).toInt().coerceAtLeast(1)
    }
    best?.let { page.setSpan(ReaderPageBottomAlignSpan(it), 0, page.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE) }
}
