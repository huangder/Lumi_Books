package com.huangder.lumibooks.ui.reader

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment

internal fun applyReaderTextAlignment(
    text: CharSequence,
    alignment: ReaderTextAlignment
): CharSequence {
    if (alignment == ReaderTextAlignment.NATURAL) return text

    val result = SpannableStringBuilder(text)
    result.getSpans(0, result.length, AlignmentSpan::class.java).forEach(result::removeSpan)

    val layoutAlignment = when (alignment) {
        ReaderTextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        ReaderTextAlignment.NATURAL,
        ReaderTextAlignment.JUSTIFY -> null
    } ?: return result

    var paragraphStart = 0
    while (paragraphStart < result.length) {
        val newline = result.indexOf('\n', paragraphStart)
        val paragraphEnd = if (newline >= 0) newline + 1 else result.length
        result.setSpan(
            AlignmentSpan.Standard(layoutAlignment),
            paragraphStart,
            paragraphEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        paragraphStart = paragraphEnd
    }
    return result
}
