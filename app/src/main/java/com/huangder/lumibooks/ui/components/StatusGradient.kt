package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

/**
 * Keeps the legacy color fade for regular themes and renders only sampled blur
 * for liquid glass. The source backdrop must be a sibling below this overlay.
 */
@Composable
fun StatusGradientOverlay(
    backdrop: Backdrop? = null,
    exportedBackdrop: LayerBackdrop? = null,
    height: Dp = 96.dp,
    blurRadius: Dp = 30.dp,
    solidFraction: Float = 0.12f
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    if (isLiquidGlass && backdrop != null) {
        ProgressiveBlurOverlay(
            backdrop = backdrop,
            exportedBackdrop = exportedBackdrop,
            height = height,
            blurRadius = blurRadius,
            solidFraction = solidFraction
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
private fun ProgressiveBlurOverlay(
    backdrop: Backdrop,
    exportedBackdrop: LayerBackdrop?,
    height: Dp,
    blurRadius: Dp,
    solidFraction: Float
) {
    val solidStop = solidFraction.coerceIn(0f, 0.82f)
    val middleStop = solidStop + (1f - solidStop) * 0.50f
    val lateStop = solidStop + (1f - solidStop) * 0.78f
    val samplingHeight = height + blurRadius
    val visibleFraction = if (samplingHeight.value > 0f) {
        (height.value / samplingHeight.value).coerceIn(0f, 1f)
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(samplingHeight)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(blurRadius.toPx()) },
                exportedBackdrop = exportedBackdrop,
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black,
                                solidStop * visibleFraction to Color.Black,
                                middleStop * visibleFraction to Color.Black.copy(alpha = 0.72f),
                                lateStop * visibleFraction to Color.Black.copy(alpha = 0.18f),
                                visibleFraction * 0.97f to Color.Black.copy(alpha = 0.04f),
                                visibleFraction to Color.Transparent,
                                1f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
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
