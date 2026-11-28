package com.huangder.lumibooks.util

// Tight tracking can use up the entire advance of a space or middle dot.
// Limit the applied value relative to the font size so small text keeps a gap.
internal const val MIN_READER_LETTER_SPACING_EM = -0.1f

internal fun readerLetterSpacingEm(spacingPx: Float, textSizePx: Float): Float =
    if (textSizePx > 0f) {
        (spacingPx / textSizePx).coerceAtLeast(MIN_READER_LETTER_SPACING_EM)
    } else {
        0f
    }
