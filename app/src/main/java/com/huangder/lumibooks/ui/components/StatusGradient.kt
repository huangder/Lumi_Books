package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.ProgressiveBlurDirection
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Keeps the legacy color fade for regular themes and renders Miuix progressive blur
 * for liquid glass. Reduced transparency uses a readable scrim and still exports
 * its completed backdrop for glass controls above it.
 */
@Composable
fun StatusGradientOverlay(
    backdrop: Backdrop? = null,
    exportedBackdrop: LayerBackdrop? = null,
    height: Dp = 96.dp,
    blurRadius: Dp = 30.dp,
    solidFraction: Float = 0.12f,
    /**
     * Optional 0..1 visibility read in the draw phase. Scrolling pages pass their scroll so the
     * overlay is invisible at rest (the large title, eyebrow and atmosphere stay clear) and only
     * fades in once content actually scrolls under the status bar.
     */
    visibility: (() -> Float)? = null
) {
    if (visibility != null) {
        Box(Modifier.graphicsLayer { alpha = visibility().coerceIn(0f, 1f) }) {
            StatusGradientOverlay(backdrop, exportedBackdrop, height, blurRadius, solidFraction, null)
        }
        return
    }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    // The shared surface composites artwork behind this scrolling content when needed.
    val activeBackdrop = backdrop ?: com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop.current
    if (isLiquidGlass) {
        val reduced = LocalReduceTransparency.current
        ProgressiveSurface(
            backdrop = activeBackdrop,
            strength = 1f,
            modifier = Modifier.fillMaxWidth().height(height),
            maxBlurRadius = blurRadius,
            tint = null,
            direction = ProgressiveBlurDirection.Top,
            solidFraction = if (reduced) solidFraction.coerceIn(0.55f, 0.82f) else solidFraction.coerceIn(0f, 0.82f),
            blurStartFraction = solidFraction.coerceIn(0f, 0.82f),
            exportedBackdrop = exportedBackdrop,
        )
        return
    }

    val bgColor = AppColors.WindowBg
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor,
                        0.55f to bgColor,
                        1.0f to bgColor.copy(alpha = 0f)
                    )
                )
            )
    )
}

@Composable
fun NavigationGradientOverlay() {
    val bgColor = AppColors.WindowBg
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor.copy(alpha = 0f),
                        bgColor.copy(alpha = 0.8f),
                        bgColor
                    )
                )
            )
    )
}
