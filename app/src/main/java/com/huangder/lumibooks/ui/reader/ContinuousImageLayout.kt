package com.huangder.lumibooks.ui.reader

import android.graphics.Paint
import android.text.Spannable
import android.text.Layout
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.LineHeightSpan
import kotlin.math.roundToInt

internal fun continuousCharacterTop(layout: Layout, offset: Int): Int =
    layout.getLineTop(layout.getLineForOffset(offset.coerceIn(0, layout.text.length)))

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

/** Publisher paragraph-height spans must never shrink a row below its image. */
internal fun protectContinuousImageHeights(text: Spannable) {
    text.getSpans(0, text.length, ImageSpan::class.java).forEach { image ->
        text.setSpan(
            ContinuousImageLineHeight(image), text.getSpanStart(image), text.getSpanEnd(image),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

private class ContinuousImageLineHeight(private val image: ImageSpan) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt
    ) {
        val height = image.drawable.bounds.height().coerceAtLeast(1)
        fm.ascent = minOf(fm.ascent, -height)
        fm.top = minOf(fm.top, fm.ascent)
        fm.descent = maxOf(fm.descent, 0)
        fm.bottom = maxOf(fm.bottom, fm.descent)
    }
}
