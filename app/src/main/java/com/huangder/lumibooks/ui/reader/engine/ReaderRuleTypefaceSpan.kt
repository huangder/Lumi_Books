package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/** Applies a rule-local typeface while preserving the surrounding reader typeface. */
internal class ReaderRuleTypefaceSpan(
    private val font: ResolvedReaderTypeface,
    private val italic: Boolean
) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)

    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = font.typeface
        textPaint.isFakeBoldText = font.fakeBold && !font.typeface.isBold
        if (italic && (font.typeface.style and Typeface.ITALIC) == 0) {
            textPaint.textSkewX = -0.25f
        }
    }
}
