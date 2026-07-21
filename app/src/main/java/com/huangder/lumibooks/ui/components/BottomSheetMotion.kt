package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

suspend fun Animatable<Float, AnimationVector1D>.animateBottomSheetIn() {
    animateTo(
        targetValue = 0f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 145f,
            visibilityThreshold = 0.001f
        )
    )
}

suspend fun Animatable<Float, AnimationVector1D>.animateBottomSheetOut() {
    animateTo(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 240f,
            visibilityThreshold = 0.001f
        )
    )
}

/** Applies the same predictive-back scale distances used by Material 3 ModalBottomSheet. */
fun Modifier.materialBottomSheetMotion(
    entryOffset: Float,
    predictiveBackProgress: Float,
    alpha: Float = 1f
): Modifier = graphicsLayer {
    val entry = entryOffset.coerceIn(-0.06f, 1.08f)
    val hiddenProgress = entryOffset.coerceIn(0f, 1f)
    translationY = entry * size.height
    this.alpha = alpha.coerceIn(0f, 1f) * (1f - hiddenProgress)

    val progress = predictiveBackProgress.coerceIn(0f, 1f)
    // Entry overshoot is shared by translation and scale, so the floating sheet
    // grows from below, expands slightly past 100%, then settles as one motion.
    val entryScaleX = 1f - entry * 0.14f
    val entryScaleY = 1f - entry * 0.14f
    scaleX = if (size.width > 0f) {
        entryScaleX * (1f - min(48.dp.toPx(), size.width) * progress / size.width)
    } else {
        entryScaleX
    }
    scaleY = if (size.height > 0f) {
        entryScaleY * (1f - min(24.dp.toPx(), size.height) * progress / size.height)
    } else {
        entryScaleY
    }
    transformOrigin = TransformOrigin(0.5f, 1f)
}
