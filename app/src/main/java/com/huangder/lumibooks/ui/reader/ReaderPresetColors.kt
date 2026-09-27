package com.huangder.lumibooks.ui.reader

import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.domain.model.ReaderThemeSuites

internal fun readerPresetBackgroundColor(selection: String, dark: Boolean): Int = when (selection) {
    ReaderThemeSuites.NIGHT_ID -> 0xFF1A1A1A.toInt()
    ReaderThemeSuites.SEPIA_ID -> if (dark) 0xFF2B2118.toInt() else 0xFFF5E6D3.toInt()
    ReaderThemeSuites.GREEN_ID -> if (dark) 0xFF142A1A.toInt() else 0xFFE8F5E9.toInt()
    else -> if (dark) 0xFF1A1A1A.toInt() else 0xFFFBFBFC.toInt()
}

internal fun readerPresetTextColor(selection: String, dark: Boolean): Int = when (selection) {
    ReaderThemeSuites.NIGHT_ID -> 0xFFCCCCCC.toInt()
    ReaderThemeSuites.SEPIA_ID -> if (dark) 0xFFE8D5BC.toInt() else 0xFF4A3728.toInt()
    ReaderThemeSuites.GREEN_ID -> if (dark) 0xFFC8E6C9.toInt() else 0xFF2E7D32.toInt()
    else -> if (dark) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
}

/** Match the dark-mode treatment of user-defined solid backgrounds in the reader. */
internal fun darkenReaderSolidColor(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = minOf(hsl[2], 0.22f)
    return ColorUtils.HSLToColor(hsl)
}
