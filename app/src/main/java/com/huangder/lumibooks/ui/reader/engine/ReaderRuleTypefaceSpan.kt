package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/** Applies a rule-local typeface while preserving the surrounding reader typeface. */
internal class ReaderRuleTypefaceSpan(
    val typeface: Typeface,
    internal val bold: Boolean,
    internal val italic: Boolean
) : MetricAffectingSpan() {
    private val style: Int
        get() = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)

    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

    private fun apply(textPaint: TextPaint) {
        val resolved = Typeface.create(typeface, style)
        textPaint.typeface = resolved
        // Some imported fonts do not expose a bold face. Keep the requested rule
        // weight visible without replacing the selected family.
        textPaint.isFakeBoldText = bold && (resolved.style and Typeface.BOLD) == 0
        if (italic && (resolved.style and Typeface.ITALIC) == 0) {
            textPaint.textSkewX = -0.25f
        }
    }
}
