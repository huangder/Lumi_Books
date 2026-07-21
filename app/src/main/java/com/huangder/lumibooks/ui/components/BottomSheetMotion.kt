package com.huangder.lumibooks.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Applies the same predictive-back scale distances used by Material 3 ModalBottomSheet. */
fun Modifier.materialBottomSheetMotion(
    entryOffset: Float,
    predictiveBackProgress: Float,
    alpha: Float = 1f
): Modifier = graphicsLayer {
    translationY = entryOffset.coerceIn(0f, 1f) * size.height
    this.alpha = alpha.coerceIn(0f, 1f)

    val progress = predictiveBackProgress.coerceIn(0f, 1f)
    scaleX = if (size.width > 0f) {
        1f - min(48.dp.toPx(), size.width) * progress / size.width
    } else {
        1f
    }
    scaleY = if (size.height > 0f) {
        1f - min(24.dp.toPx(), size.height) * progress / size.height
    } else {
        1f
    }
    transformOrigin = TransformOrigin(0.5f, 1f)
}
