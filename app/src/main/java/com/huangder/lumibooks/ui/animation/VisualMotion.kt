package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring as composeSpring
import androidx.compose.animation.core.tween as composeTween
import com.huangder.lumibooks.ui.theme.MotionPreference
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Runtime timing policy for all user-visible Compose motion. */
object VisualMotion {
    private const val FAST_FACTOR = 2f / 3f
    private const val FAST_STIFFNESS_FACTOR = 2.25f

    @Volatile
    var durationScale = 1f
        private set

    fun setPreference(preference: MotionPreference) {
        durationScale = preference.visualDurationScale
    }

    fun durationMillis(baseMillis: Int): Int =
        (baseMillis.coerceAtLeast(0) * durationScale).roundToInt().coerceAtLeast(0)

    fun delayMillis(baseMillis: Int): Int = durationMillis(baseMillis)

    fun durationMillis(baseMillis: Long): Long =
        (baseMillis.coerceAtLeast(0L) * durationScale.toDouble()).roundToLong().coerceAtLeast(0L)

    fun delayMillis(baseMillis: Long): Long = durationMillis(baseMillis)

    fun <T> tween(
        durationMillis: Int = 300,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): TweenSpec<T> = composeTween(
        durationMillis = durationMillis(durationMillis),
        delayMillis = delayMillis(delayMillis),
        easing = easing
    )

    fun <T> spring(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMedium,
        visibilityThreshold: T? = null
    ): SpringSpec<T> = composeSpring(
        dampingRatio = dampingRatio,
        stiffness = if (durationScale == FAST_FACTOR) stiffness * FAST_STIFFNESS_FACTOR else stiffness,
        visibilityThreshold = visibilityThreshold
    )
}
