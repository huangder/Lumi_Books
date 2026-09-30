package com.huangder.lumibooks.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Opt-in readability veil, provided only by the reading screen. */
val LocalLiquidGlassContrastEnabled = compositionLocalOf { false }

internal fun liquidGlassContrastScrim(enabled: Boolean, surfaceColor: Color): Color {
    if (!enabled) return Color.Transparent
    // Follow the actual surface (including custom reader themes), not system night mode.
    val neutral = if (surfaceColor.copy(alpha = 1f).luminance() < 0.4f) Color.Black else Color.White
    // Block most background detail even at maximum glass transparency. A faint veil
    // still leaves the page's text competing with the controls' labels.
    return neutral.copy(alpha = 0.60f)
}

internal fun liquidGlassContrastTransparency(enabled: Boolean, transparency: Float): Float =
    if (enabled) transparency.coerceAtMost(0.35f) else transparency
