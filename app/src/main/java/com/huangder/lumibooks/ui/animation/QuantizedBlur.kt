package com.huangder.lumibooks.ui.animation

import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.roundToInt

/**
 * Blur effects for an animated radius, rounded to [stepPx] and reused. Creating a new
 * [android.graphics.RenderEffect] on every frame of a scrim or search animation allocates and
 * hands the render thread a new effect each time; rounded steps are visually identical and let
 * repeated frames keep the same effect.
 */
internal class QuantizedBlur(private val stepPx: Float = 1f) {
    private val effects = HashMap<Int, RenderEffect>()

    /** The blur for [radiusPx], or null when there is nothing to blur or the platform cannot. */
    fun at(radiusPx: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val step = (radiusPx / stepPx).roundToInt()
        if (step <= 0) return null
        return effects.getOrPut(step) {
            val radius = step * stepPx
            android.graphics.RenderEffect.createBlurEffect(
                radius, radius, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
}
