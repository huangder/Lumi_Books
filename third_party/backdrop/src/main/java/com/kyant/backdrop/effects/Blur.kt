package com.kyant.backdrop.effects

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toAndroidTileMode
import com.kyant.backdrop.BackdropEffectScope

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    // Local patch: keep the apparent screen-space blur unchanged at reduced resolution.
    val sampledRadius = radius * samplingScale
    val currentEffect = renderEffect
    renderEffect =
        if (currentEffect != null) {
            RenderEffect.createBlurEffect(
                sampledRadius,
                sampledRadius,
                currentEffect,
                edgeTreatment.toAndroidTileMode()
            )
        } else {
            RenderEffect.createBlurEffect(
                sampledRadius,
                sampledRadius,
                edgeTreatment.toAndroidTileMode()
            )
        }
}
