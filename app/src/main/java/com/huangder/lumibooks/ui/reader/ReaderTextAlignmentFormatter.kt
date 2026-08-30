package com.huangder.lumibooks.ui.reader

import android.text.Layout
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment

internal fun ReaderTextAlignment.usesFullLineJustification(): Boolean =
    this == ReaderTextAlignment.NATURAL || this == ReaderTextAlignment.JUSTIFY

internal fun ReaderTextAlignment.readerJustificationMode(
    sdkInt: Int = Build.VERSION.SDK_INT
): Int = when {
    !usesFullLineJustification() -> Layout.JUSTIFICATION_MODE_NONE
    sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
        Layout.JUSTIFICATION_MODE_INTER_CHARACTER
    else -> Layout.JUSTIFICATION_MODE_INTER_WORD
}

internal fun ReaderTextAlignment.readerBreakStrategy(): Int =
    if (usesFullLineJustification()) {
        Layout.BREAK_STRATEGY_HIGH_QUALITY
    } else {
        Layout.BREAK_STRATEGY_SIMPLE
    }

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

    if (result.isNotEmpty()) {
        result.setSpan(
            AlignmentSpan.Standard(layoutAlignment),
            0,
            result.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return result
}
