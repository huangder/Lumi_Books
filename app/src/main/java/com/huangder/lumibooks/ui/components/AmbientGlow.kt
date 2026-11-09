package com.huangder.lumibooks.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import com.huangder.lumibooks.ui.theme.LocalPageVisible
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.huangder.lumibooks.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val coverColorCache = LruCache<String, List<Int>>(32)

/** Normalises a swatch so it glows: mid lightness, enough saturation, never muddy or neon. */
private fun glowTone(argb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = hsl[1].coerceIn(.35f, .85f)
    hsl[2] = hsl[2].coerceIn(.48f, .68f)
    return ColorUtils.HSLToColor(hsl)
}

private fun extractCoverColors(path: String): List<Int>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 120).coerceAtLeast(1)
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    try {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        listOfNotNull(palette.vibrantSwatch, palette.lightVibrantSwatch, palette.mutedSwatch,
            palette.darkVibrantSwatch, palette.dominantSwatch)
            .map { glowTone(it.rgb) }.distinct().take(3).takeIf { it.isNotEmpty() }
    } finally { bitmap.recycle() }
}.getOrNull()

/** Shifts a colour's hue, used to give single-colour glows some depth. */
internal fun Color.shiftHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}

/** 2–3 glow colours from a book cover (cached), falling back to the accent family. */
@Composable
internal fun rememberCoverColors(coverPath: String?): List<Color> {
    val accent = AppColors.Accent
    val fallback = remember(accent) { listOf(accent, accent.shiftHue(28f), accent.shiftHue(-24f)) }
    val colors by produceState(coverPath?.let { coverColorCache.get(it) }, coverPath) {
        if (coverPath.isNullOrBlank()) { value = null; return@produceState }
        value = coverColorCache.get(coverPath) ?: withContext(Dispatchers.IO) { extractCoverColors(coverPath) }
            ?.also { coverColorCache.put(coverPath, it) }
    }
    return colors?.map { Color(it) }?.let { if (it.size >= 3) it else it + fallback.drop(it.size) } ?: fallback
}

/**
 * Soft atmospheric glow behind a page's top (like Apple Music / Books). Purely decorative and drawn
 * in the draw phase only: slow drift, colour crossfade when the source changes, and it fades and
 * lifts as [scrollOffset] grows. Skipped for e-ink, reduce transparency and the artwork theme.
 * Place it in the page *background* — never inside a glass control's own source layer.
 * Disable [driftEnabled] for a stable background without continuous blur-source invalidation.
 */
@Composable
internal fun AmbientGlow(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 460.dp,
    intensity: Float = 1f,
    driftEnabled: Boolean = true,
    scrollOffset: () -> Float = { 0f }
) {
    if (LocalEInkMode.current || LocalReduceTransparency.current || LocalLumiBackgroundPresent.current) return
    val dark = LocalIsDarkTheme.current
    val motion = LocalMotionEnabled.current && driftEnabled
    val c0 by animateColorAsState(colors.getOrElse(0) { AppColors.Accent }, tween(700), label = "glow0")
    val c1 by animateColorAsState(colors.getOrElse(1) { c0 }, tween(700), label = "glow1")
    val c2 by animateColorAsState(colors.getOrElse(2) { c1 }, tween(700), label = "glow2")
    val visible = LocalPageVisible.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driftState = remember { Animatable(if (motion) 0f else .5f) }
    val towardEnd = remember { booleanArrayOf(true) }
    LaunchedEffect(motion, visible, lifecycle) {
        if (!motion) {
            driftState.snapTo(.5f)
        } else if (visible) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val target = if (towardEnd[0]) 1f else 0f
                    val duration = (abs(target - driftState.value) * 14_000).roundToInt().coerceAtLeast(1)
                    driftState.animateTo(target, tween(duration, easing = LinearEasing))
                    towardEnd[0] = !towardEnd[0]
                }
            }
        }
    }
    val baseAlpha = (if (dark) .26f else .34f) * intensity
    Canvas(modifier.fillMaxWidth().height(height)) {
        val scroll = scrollOffset().coerceAtLeast(0f)
        val fade = (1f - scroll / 300.dp.toPx()).coerceIn(0f, 1f)
        if (fade <= 0f) return@Canvas
        val t = driftState.value
        val w = size.width
        val h = size.height
        val a = baseAlpha * fade
        translate(top = -scroll * .35f) {
            glowBlob(c0, Offset(w * (.12f + .14f * t), h * (.16f + .05f * t)), w * .78f, a)
            glowBlob(c1, Offset(w * (.9f - .12f * t), h * (.1f + .1f * t)), w * .68f, a * .9f)
            glowBlob(c2, Offset(w * (.5f + .08f * (t - .5f)), h * (.44f - .08f * t)), w * .74f, a * .55f)
        }
    }
}

private fun DrawScope.glowBlob(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(
        0f to color.copy(alpha = alpha), .45f to color.copy(alpha = alpha * .45f), 1f to color.copy(alpha = 0f),
        center = center, radius = radius), radius, center)
}
