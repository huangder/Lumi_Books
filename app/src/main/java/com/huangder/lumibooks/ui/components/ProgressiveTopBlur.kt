package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import kotlin.math.roundToInt

/**
 * Progressive (gradient) backdrop blur for top bars.
 *
 * The blur strength ramps continuously from full at the top edge to zero at the bottom of the band,
 * so scrolling content dissolves into the bar instead of hitting a rectangular edge. It is built as
 * a level stack, mirroring Miuix's `progressiveTextureBlur` approach on top of the Backdrop pipeline
 * already used by this project:
 *
 * * three Gaussian levels (heavy / medium / light) are drawn lighter-first, so a heavier level always
 *   paints over a lighter one;
 * * each level is masked *after* its own blur (DstIn on the blurred layer), so the transition band
 *   holds genuinely intermediate blur radii instead of a sharp image cross-fading with a heavily
 *   blurred one;
 * * the level masks overlap, so the band stays covered while the radius walks heavy -> medium ->
 *   light -> clear with no visible seam.
 *
 * Costs three full-resolution RenderEffect passes, but only across the band: nothing outside it is
 * sampled or blurred, and the whole band is skipped while the strength is zero.
 */

/** Discrete strength steps. Quantizing keeps the RenderEffect chain stable between frames. */
internal const val PROGRESSIVE_BLUR_STEPS = 24

/**
 * Normalized band position (0 = top edge) where each level has fully faded out. The strong end is
 * kept short and the lighter radii hold far down the band, so the radius walks towards zero over the
 * whole band instead of collapsing into a visible edge.
 */
internal const val PROGRESSIVE_BLUR_HEAVY_STOP = 0.34f
internal const val PROGRESSIVE_BLUR_MEDIUM_STOP = 0.64f
internal const val PROGRESSIVE_BLUR_LIGHT_STOP = 1f

/**
 * Positions where the lighter levels reach full opacity. They saturate before the heavier level has
 * finished fading, which keeps the band covered (no sharp content leaking through mid-transition).
 */
internal const val PROGRESSIVE_BLUR_MEDIUM_RISE = 0.55f * PROGRESSIVE_BLUR_HEAVY_STOP
internal const val PROGRESSIVE_BLUR_LIGHT_RISE = 0.80f * PROGRESSIVE_BLUR_HEAVY_STOP

internal const val PROGRESSIVE_BLUR_LEVEL_LIGHT = 0
internal const val PROGRESSIVE_BLUR_LEVEL_MEDIUM = 1
internal const val PROGRESSIVE_BLUR_LEVEL_HEAVY = 2

/** Blur radius of each level, as a fraction of the full-strength radius. */
internal const val PROGRESSIVE_BLUR_HEAVY_RADIUS_RATIO = 1f
internal const val PROGRESSIVE_BLUR_MEDIUM_RADIUS_RATIO = 0.45f
internal const val PROGRESSIVE_BLUR_LIGHT_RADIUS_RATIO = 0.18f

/** Gradient samples per level mask. The masks are smoothstep curves, not two-stop ramps. */
private const val MASK_SAMPLE_COUNT = 16

/** Tint scrim opacity at the top edge, before being scaled by the scroll-driven strength. */
internal const val PROGRESSIVE_BLUR_TINT_ALPHA = 0.08f
internal const val PROGRESSIVE_BLUR_TINT_ALPHA_DARK = 0.10f

/** Opacity at the top edge when the device cannot blur and a plain scrim keeps the bar readable. */
internal const val PROGRESSIVE_TOP_SCRIM_ALPHA = 0.62f

/** Default full-strength blur radius and band height for a settings top bar. */
val ProgressiveTopBlurMaxRadius: Dp = 28.dp
val ProgressiveTopBlurBandHeight: Dp = 120.dp

internal fun progressiveBlurStrength(strength: Float): Float =
    if (strength.isFinite()) strength.coerceIn(0f, 1f) else 0f

/**
 * Snaps the scroll-driven strength to a fixed number of steps so the blur pipeline is rebuilt at
 * most [steps] times per scroll ramp instead of once per frame.
 */
internal fun quantizeBlurStrength(strength: Float, steps: Int = PROGRESSIVE_BLUR_STEPS): Float {
    val clamped = progressiveBlurStrength(strength)
    if (steps <= 0) return clamped
    return ((clamped * steps).roundToInt().toFloat() / steps).coerceIn(0f, 1f)
}

/** Whether the progressive blur can run at all. E-ink always opts out, even on capable devices. */
internal fun progressiveBlurEnabled(supported: Boolean, eInkMode: Boolean): Boolean =
    supported && !eInkMode

/** Whether the blur-free readability scrim should be used instead of the blur band. */
internal fun progressiveTopScrimEnabled(supported: Boolean, eInkMode: Boolean): Boolean =
    !supported && !eInkMode

private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun rampWithHold(fraction: Float, riseEnd: Float, holdEnd: Float, fallEnd: Float): Float = when {
    fraction <= 0f -> 0f
    riseEnd <= 0f -> 1f
    fraction < riseEnd -> smoothStep(fraction / riseEnd)
    fraction <= holdEnd -> 1f
    fraction >= fallEnd -> 0f
    else -> 1f - smoothStep((fraction - holdEnd) / (fallEnd - holdEnd))
}

/**
 * Mask opacity of one blur level at [fraction] of the band height, 0 being the top edge.
 *
 * Heavy fades out across the strong end, the lighter levels hold full opacity under it and only
 * dissolve further down, so the visible radius walks continuously towards zero.
 */
internal fun progressiveBlurLevelAlpha(level: Int, fraction: Float): Float {
    if (!fraction.isFinite()) return 0f
    val position = fraction.coerceIn(0f, 1f)
    return when (level) {
        PROGRESSIVE_BLUR_LEVEL_HEAVY -> 1f - smoothStep(position / PROGRESSIVE_BLUR_HEAVY_STOP)
        PROGRESSIVE_BLUR_LEVEL_MEDIUM -> rampWithHold(
            position,
            riseEnd = PROGRESSIVE_BLUR_MEDIUM_RISE,
            holdEnd = PROGRESSIVE_BLUR_HEAVY_STOP,
            fallEnd = PROGRESSIVE_BLUR_MEDIUM_STOP
        )
        PROGRESSIVE_BLUR_LEVEL_LIGHT -> rampWithHold(
            position,
            riseEnd = PROGRESSIVE_BLUR_LIGHT_RISE,
            holdEnd = PROGRESSIVE_BLUR_MEDIUM_STOP,
            fallEnd = PROGRESSIVE_BLUR_LIGHT_STOP
        )
        else -> 0f
    }
}

/**
 * Fraction of a band row covered by blurred content: 1 means fully covered, 0 means the row shows
 * the untouched content underneath.
 */
internal fun progressiveBlurCoverage(fraction: Float): Float {
    var clear = 1f
    for (level in PROGRESSIVE_BLUR_LEVEL_LIGHT..PROGRESSIVE_BLUR_LEVEL_HEAVY) {
        clear *= 1f - progressiveBlurLevelAlpha(level, fraction)
    }
    return 1f - clear
}

/** Tint scrim opacity profile: strongest at the top, fully dissolved by the middle stop. */
internal fun progressiveTintAlpha(fraction: Float): Float {
    if (!fraction.isFinite()) return 0f
    return 1f - smoothStep(fraction.coerceIn(0f, 1f) / PROGRESSIVE_BLUR_MEDIUM_STOP)
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

/**
 * Draws the progressive blur band. Must be placed above the scrolling content and below the
 * foreground toolbar, and [backdrop] must capture the scrolling content only.
 *
 * @param strength Scroll-driven blur strength in `[0, 1]`. Values at or below zero draw nothing.
 * @param maxBlurRadius Full-strength blur radius at the top edge.
 * @param bandHeight Height of the dissolving band, measured from the top of the window.
 * @param tint Optional material tint drawn over the blurred levels for text contrast. `null` skips it.
 */
@Composable
fun ProgressiveTopBlur(
    backdrop: Backdrop,
    strength: Float,
    modifier: Modifier = Modifier,
    maxBlurRadius: Dp = ProgressiveTopBlurMaxRadius,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color? = null,
) {
    val resolvedStrength = quantizeBlurStrength(strength)
    if (resolvedStrength <= 0f) return

    val masks = remember { ProgressiveBlurMasks() }
    val fullRadiusPx = with(LocalDensity.current) { maxBlurRadius.toPx() } * resolvedStrength

    Box(modifier = modifier.fillMaxWidth().height(bandHeight)) {
        // Lighter levels first: every later (stronger) level paints over the weaker ones.
        ProgressiveBlurLevel(
            backdrop = backdrop,
            mask = masks.light,
            radiusPx = fullRadiusPx * PROGRESSIVE_BLUR_LIGHT_RADIUS_RATIO
        )
        ProgressiveBlurLevel(
            backdrop = backdrop,
            mask = masks.medium,
            radiusPx = fullRadiusPx * PROGRESSIVE_BLUR_MEDIUM_RADIUS_RATIO
        )
        ProgressiveBlurLevel(
            backdrop = backdrop,
            mask = masks.heavy,
            radiusPx = fullRadiusPx * PROGRESSIVE_BLUR_HEAVY_RADIUS_RATIO
        )
        if (tint != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = resolvedStrength }
                    .background(remember(tint) { tintBrush(tint) })
            )
        }
    }
}

/**
 * Blur-free fallback for devices without RenderEffect support: the same dissolve profile drawn as a
 * plain scrim so the foreground title stays readable while content scrolls underneath.
 */
@Composable
fun ProgressiveTopScrim(
    strength: Float,
    modifier: Modifier = Modifier,
    bandHeight: Dp = ProgressiveTopBlurBandHeight,
    tint: Color? = null,
) {
    val resolvedStrength = quantizeBlurStrength(strength)
    if (resolvedStrength <= 0f || tint == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bandHeight)
            .graphicsLayer { alpha = resolvedStrength * PROGRESSIVE_TOP_SCRIM_ALPHA }
            .background(remember(tint) { tintBrush(tint) })
    )
}

@Composable
private fun ProgressiveBlurLevel(
    backdrop: Backdrop,
    mask: Brush,
    radiusPx: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Own offscreen layer: the DstIn mask below must only punch through this level's blurred
            // backdrop, never through the page content drawn underneath the band.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radiusPx) },
                // Masking the already-blurred layer keeps the transition band at genuinely medium
                // radii; masking the source before blurring would only cross-fade blur with sharp.
                onDrawSurface = { drawRect(brush = mask, blendMode = BlendMode.DstIn) }
            )
    )
}

private class ProgressiveBlurMasks {
    val light: Brush = maskBrush(PROGRESSIVE_BLUR_LEVEL_LIGHT)
    val medium: Brush = maskBrush(PROGRESSIVE_BLUR_LEVEL_MEDIUM)
    val heavy: Brush = maskBrush(PROGRESSIVE_BLUR_LEVEL_HEAVY)
}

private fun maskBrush(level: Int): Brush = Brush.verticalGradient(
    colorStops = Array(MASK_SAMPLE_COUNT + 1) { index ->
        val fraction = index.toFloat() / MASK_SAMPLE_COUNT
        fraction to Color.Black.copy(alpha = progressiveBlurLevelAlpha(level, fraction))
    }
)

private fun tintBrush(tint: Color): Brush = Brush.verticalGradient(
    colorStops = Array(MASK_SAMPLE_COUNT + 1) { index ->
        val fraction = index.toFloat() / MASK_SAMPLE_COUNT
        fraction to tint.copy(alpha = tint.alpha * progressiveTintAlpha(fraction))
    }
)
