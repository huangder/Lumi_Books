package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun shouldDismissListening(distancePx: Float, heightPx: Float, velocityPx: Float, density: Float): Boolean =
    distancePx > heightPx * .18f || (distancePx > 48f * density && velocityPx >= 1000f * density)

/** Reciprocal falloff with a flat finish: a long, slowing tail without overshoot. */
internal val ListeningCoverEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    val time = t * (2f - t)
    3f * time / (1f + 2f * time)
}

/** All transitions start at the current values, including interrupted entrance/exit/drag. */
internal class ListeningMotion(entered: Boolean) {
    val background = Animatable(if (entered) 0f else 1f)
    val groups = List(5) { Animatable(if (entered) 0f else 1f) }
    suspend fun stop() { background.stop(); groups.forEach { it.stop() } }
    suspend fun show(animated: Boolean) = coroutineScope {
        if (!animated) { background.snapTo(0f); groups.forEach { it.snapTo(0f) }; return@coroutineScope }
        launch { background.animateTo(0f, tween(520, easing = LinearOutSlowInEasing)) }
        snapshotFlow { background.value }.first { it <= .7f }
        groups.forEachIndexed { index, value -> launch {
            delay(VisualMotion.delayMillis(index * 45).toLong())
            value.animateTo(0f, if (index <= 1) tween(480, easing = ListeningCoverEasing) else spring(.82f, 240f))
        } }
    }
    suspend fun hide(animated: Boolean, velocity: Float = 0f) = coroutineScope {
        if (!animated) { background.snapTo(1f); groups.forEach { it.snapTo(1f) }; return@coroutineScope }
        val remaining = (1f - background.value).coerceAtLeast(.001f)
        val duration = (420 * remaining).roundToInt().coerceIn(180, 420)
        // Continue at the released finger's speed, then decelerate. Never wait for
        // the foreground to leave before starting the background movement.
        val slope = if (velocity > 0f) (velocity * VisualMotion.durationMillis(duration) / 1000f / remaining)
            .coerceIn(.2f, 3f) else 1f
        val easing = CubicBezierEasing(.2f, .2f * slope, .4f, 1f)
        groups.asReversed().forEachIndexed { index, value -> launch {
            delay(VisualMotion.delayMillis(index * duration / 14).toLong())
            value.animateTo(1f, tween(duration * 2 / 5, easing = LinearOutSlowInEasing))
        } }
        launch { background.animateTo(1f, tween(duration, easing = easing)) }
    }
    suspend fun restore(animated: Boolean) = coroutineScope {
        if (!animated) { show(false); return@coroutineScope }
        launch { background.animateTo(0f, spring(.78f, 200f)) }
        groups.forEachIndexed { index, value -> launch {
            value.animateTo(0f, if (index <= 1) tween(420, easing = ListeningCoverEasing) else spring(.78f, 200f))
        } }
    }
}
