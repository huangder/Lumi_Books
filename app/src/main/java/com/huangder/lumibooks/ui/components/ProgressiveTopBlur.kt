package com.huangder.lumibooks.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop
import com.huangder.lumibooks.ui.theme.LocalProgressiveBlurBackgroundBackdrop
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.LocalBackdropSamplingScale
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.MiuixProgressiveBlurEffect
import com.kyant.backdrop.effects.ProgressiveBlurDirection
import com.kyant.backdrop.effects.progressiveBlurFraction
import kotlin.math.roundToInt

/** Discrete scroll strengths keep the effect chain stable between frames. */
internal const val PROGRESSIVE_BLUR_STEPS = 24
internal const val PROGRESSIVE_BLUR_TINT_ALPHA = 0.08f
internal const val PROGRESSIVE_BLUR_TINT_ALPHA_DARK = 0.10f
private const val GRADIENT_SAMPLE_COUNT = 32

val ProgressiveTopBlurMaxRadius: Dp = 28.dp
val ProgressiveTopBlurBandHeight: Dp = 120.dp

internal fun progressiveBlurStrength(strength: Float): Float =
    if (strength.isFinite()) strength.coerceIn(0f, 1f) else 0f

internal fun quantizeBlurStrength(strength: Float, steps: Int = PROGRESSIVE_BLUR_STEPS): Float {
    val clamped = progressiveBlurStrength(strength)
    if (steps <= 0) return clamped
    return ((clamped * steps).roundToInt().toFloat() / steps).coerceIn(0f, 1f)
}

/** This API 33 gate is independent of Liquid Glass's API 31 RenderEffect support. */
internal fun progressiveBlurEnabled(
    supported: Boolean,
    eInkMode: Boolean,
    reduceTransparency: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= 33 && supported && !eInkMode && !reduceTransparency

internal fun progressiveTopScrimEnabled(
    supported: Boolean,
    eInkMode: Boolean,
    reduceTransparency: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = !eInkMode && !progressiveBlurEnabled(supported, eInkMode, reduceTransparency, sdkInt)

internal fun progressiveTintAlpha(fraction: Float): Float = 1f - progressiveBlurFraction(fraction)

/** Fraction measured from the strong edge; reversing the gradient never mirrors the source. */
internal fun progressiveEdgeFraction(fraction: Float, reverse: Boolean): Float =
    if (reverse) 1f - fraction else fraction

/** An opaque edge, optionally held behind the foreground toolbar, fading to transparent. */
internal fun progressiveScrimColor(tint: Color, fraction: Float, solidFraction: Float = 0f): Color {
    val hold = progressiveBlurStrength(solidFraction)
    val alpha = if (hold >= 1f) 1f else progressiveTintAlpha((fraction - hold) / (1f - hold))
    return tint.copy(alpha = alpha)
}

/** Large title scale at the end of the collapse ramp, matching Display -> Section type sizes. */
internal const val LARGE_TITLE_MIN_SCALE = 0.625f

/** Fraction of the collapse ramp over which the large title has finished fading out. */
internal const val LARGE_TITLE_FADE_END = 0.70f

/** Fraction of the collapse ramp where the toolbar title starts appearing. */
internal const val SMALL_TITLE_FADE_START = 0.35f

internal fun largeTitleScale(fraction: Float): Float {
    val progress = progressiveBlurStrength(fraction)
    return 1f + (LARGE_TITLE_MIN_SCALE - 1f) * progress
}

internal fun largeTitleAlpha(fraction: Float): Float {
    val progress = progressiveBlurStrength(fraction)
    return (1f - progress / LARGE_TITLE_FADE_END).coerceIn(0f, 1f)
}

internal fun smallTitleAlpha(fraction: Float): Float {
    val progress = progressiveBlurStrength(fraction)
    return ((progress - SMALL_TITLE_FADE_START) / (1f - SMALL_TITLE_FADE_START)).coerceIn(0f, 1f)
}

internal fun smallTitleScale(fraction: Float): Float {
    val alpha = smallTitleAlpha(fraction)
    return 0.94f + 0.06f * alpha
}

/** The backdrop captures scrolling content only; foreground controls remain outside it. */
@Composable
fun ProgressiveTopBlur(
    backdrop: Backdrop,
    strength: Float,
    modifier: Modifier = Modifier,
    maxBlurRadius: Dp = ProgressiveTopBlurMaxRadius,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color? = null,
) {
    ProgressiveSurface(backdrop, strength, modifier.fillMaxWidth().height(bandHeight),
        maxBlurRadius, tint, ProgressiveBlurDirection.Top)
}

@Composable
fun ProgressiveTopScrim(
    strength: Float,
    modifier: Modifier = Modifier,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color? = null,
    solidFraction: Float = 0f,
) {
    if (tint == null) return
    ProgressiveSurface(null, strength, modifier.fillMaxWidth().height(bandHeight),
        0.dp, tint, ProgressiveBlurDirection.Top, solidFraction)
}

@Composable
fun ProgressiveBottomBlur(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    maxBlurRadius: Dp = ProgressiveTopBlurMaxRadius,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color? = null,
) {
    ProgressiveSurface(backdrop, 1f, modifier.fillMaxWidth().height(bandHeight),
        maxBlurRadius, tint, ProgressiveBlurDirection.Bottom)
}

@Composable
fun ProgressiveBottomScrim(
    modifier: Modifier = Modifier,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color,
) {
    ProgressiveSurface(null, 1f, modifier.fillMaxWidth().height(bandHeight),
        0.dp, tint, ProgressiveBlurDirection.Bottom)
}

@Composable
internal fun ProgressiveStartBlur(
    backdrop: Backdrop?,
    tint: Color,
    modifier: Modifier = Modifier,
    bandWidth: Dp = 40.dp,
    maxBlurRadius: Dp = 18.dp
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl)
        ProgressiveBlurDirection.Right else ProgressiveBlurDirection.Left
    ProgressiveSurface(backdrop, 1f, modifier.fillMaxHeight().width(bandWidth),
        maxBlurRadius, tint, direction)
}

/** Shared Miuix full-resolution composite and blur-free, opaque-edge fallback. */
@Composable
internal fun ProgressiveSurface(
    backdrop: Backdrop?,
    strength: Float,
    modifier: Modifier,
    maxBlurRadius: Dp,
    tint: Color?,
    direction: ProgressiveBlurDirection,
    solidFraction: Float = 0f,
    exportedBackdrop: LayerBackdrop? = null,
    blurStartFraction: Float = 0f,
) {
    if (LocalEInkMode.current) return
    val resolvedStrength = quantizeBlurStrength(strength)
    if (resolvedStrength <= 0f) return
    val radius = with(LocalDensity.current) { maxBlurRadius.toPx() } * resolvedStrength
    val canBlur = backdrop != null && radius.isFinite() && radius > 0f &&
        progressiveBlurEnabled(LocalLiquidGlassCapability.current.supported, false, LocalReduceTransparency.current) &&
        LocalView.current.isHardwareAccelerated
    // The API guard precedes construction, including RuntimeShader compilation.
    val effect = remember(canBlur) {
        if (canBlur && Build.VERSION.SDK_INT >= 33) runCatching { MiuixProgressiveBlurEffect() }.getOrNull()
        else null
    }
    val fallbackTint = (tint ?: AppColors.WindowBg).copy(alpha = 1f)
    // PageBg is intentionally transparent for artwork themes; making it opaque would be black.
    val pageBackground = AppColors.WindowBg.copy(alpha = 1f)
    val artworkBackdrop = LocalProgressiveBlurBackgroundBackdrop.current ?: LocalLumiBackgroundBackdrop.current
    val activeBackdrop = if (artworkBackdrop != null && backdrop != null && artworkBackdrop !== backdrop)
        rememberCombinedBackdrop(artworkBackdrop, backdrop) else backdrop
    val fallbackBrush = remember(fallbackTint, direction, solidFraction) {
        progressiveBrush(fallbackTint, direction, solidFraction, opaqueEdge = true)
    }
    val tintBrush = remember(tint, direction) {
        tint?.let { progressiveBrush(it, direction, 0f, opaqueEdge = false) }
    }
    if (effect == null || backdrop == null) {
        Box(modifier.graphicsLayer { alpha = resolvedStrength }
            .then(if (exportedBackdrop != null) Modifier.layerBackdrop(exportedBackdrop) else Modifier)
            .background(fallbackBrush))
        return
    }
    // Miuix's native sharp end requires the ORIGINAL source pixels even when glass buttons use
    // reduced-resolution buffers. Limit this override to the edge band, never the whole page.
    CompositionLocalProvider(LocalBackdropSamplingScale provides 1f) {
        Box(modifier.drawPlainBackdrop(
            backdrop = activeBackdrop ?: backdrop,
            shape = { RectangleShape },
            effects = {
                padding = MiuixProgressiveBlurEffect.padding(radius)
                renderEffect = effect.effect(radius, size.width, size.height, padding, direction, blurStartFraction)
            },
            exportedBackdrop = exportedBackdrop,
            // Captures may contain transparent text/card gaps; include their actual page color
            // before blurring so alpha normalization cannot turn those gaps into dark halos.
            onDrawBackdrop = { drawBackdrop ->
                val pad = MiuixProgressiveBlurEffect.padding(radius)
                drawRect(pageBackground, topLeft = Offset(-pad, -pad),
                    size = Size(size.width + pad * 2f, size.height + pad * 2f))
                drawBackdrop()
            },
            onDrawSurface = {
                if (effect.failed) drawRect(fallbackBrush, alpha = resolvedStrength)
                else tintBrush?.let { drawRect(it, alpha = resolvedStrength) }
            }
        ))
    }
}

private fun progressiveBrush(
    tint: Color,
    direction: ProgressiveBlurDirection,
    solidFraction: Float,
    opaqueEdge: Boolean,
): Brush {
    val reverse = direction == ProgressiveBlurDirection.Bottom || direction == ProgressiveBlurDirection.Right
    val stops = Array(GRADIENT_SAMPLE_COUNT + 1) { index ->
        val fraction = index.toFloat() / GRADIENT_SAMPLE_COUNT
        val edge = progressiveEdgeFraction(fraction, reverse)
        fraction to if (opaqueEdge) progressiveScrimColor(tint, edge, solidFraction)
        else tint.copy(alpha = tint.alpha * progressiveTintAlpha(edge))
    }
    return if (direction == ProgressiveBlurDirection.Left || direction == ProgressiveBlurDirection.Right)
        Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
}
