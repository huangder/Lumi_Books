package com.ebook.reader.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 数字递增动画
 *
 * @param targetValue 目标值
 * @param duration 动画时长（ms）
 * @param onValueChange 每帧回调
 */
@Composable
fun animateIntAsState(
    targetValue: Int,
    duration: Int = 800
): Float {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(duration, easing = AppEasing.Decelerate)
        )
    }

    return animatable.value
}

/**
 * 进度条平滑填充动画
 *
 * @param targetProgress 目标进度（0f ~ 1f）
 * @param duration 动画时长（ms）
 */
@Composable
fun animateProgressAsState(
    targetProgress: Float,
    duration: Int = 600
): Float {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetProgress) {
        animatable.animateTo(
            targetValue = targetProgress.coerceIn(0f, 1f),
            animationSpec = tween(duration, easing = AppEasing.Smooth)
        )
    }

    return animatable.value
}

/**
 * 环形进度动画
 */
@Composable
fun animateArcProgressAsState(
    targetProgress: Float,
    duration: Int = 1000
): Float {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetProgress) {
        animatable.animateTo(
            targetValue = targetProgress.coerceIn(0f, 1f),
            animationSpec = tween(duration, easing = AppEasing.Smooth)
        )
    }

    return animatable.value
}
