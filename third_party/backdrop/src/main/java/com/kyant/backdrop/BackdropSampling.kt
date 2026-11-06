package com.kyant.backdrop

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** Local extension to Backdrop 1.0.6. Only effect buffers use this linear pixel scale. */
val LocalBackdropSamplingScale = compositionLocalOf { 1f }

internal fun normalizedSamplingScale(scale: Float): Float =
    if (scale.isFinite()) scale.coerceIn(1f / 3f, 1f) else 1f

fun backdropSampleSize(size: IntSize, scale: Float): IntSize {
    val factor = normalizedSamplingScale(scale)
    return IntSize(
        ceil(size.width.coerceAtLeast(1) * factor).toInt().coerceAtLeast(1),
        ceil(size.height.coerceAtLeast(1) * factor).toInt().coerceAtLeast(1)
    )
}
