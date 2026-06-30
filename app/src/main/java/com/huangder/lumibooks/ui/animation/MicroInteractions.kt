package com.ebook.reader.ui.animation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tab 切换动画（图标放大弹跳 + 颜色渐变）
 */
@Composable
fun animateTabScale(isSelected: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = if (isSelected) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            tween(200)
        },
        label = "tabScale"
    )
    return scale
}

/**
 * 收藏/点赞弹跳动画
 */
@Composable
fun animateBounceScale(trigger: Boolean): Float {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            scale.animateTo(1.3f, tween(100, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ))
        }
    }

    return scale.value
}

/**
 * 颜色渐变动画
 */
@Composable
fun animateColorChange(
    targetColor: Color,
    duration: Int = 300
): Color {
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(duration, easing = AppEasing.Standard),
        label = "colorChange"
    )
    return color
}

/**
 * 旋转动画（用于加载/刷新指示器）
 */
@Composable
fun animateRotation(trigger: Boolean): Float {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            while (true) {
                rotation.animateTo(360f, tween(1000, easing = AppEasing.Standard))
                rotation.snapTo(0f)
            }
        }
    }

    return rotation.value
}

/**
 * Modifier 扩展：弹跳入场效果
 */
fun Modifier.bounceEnter(): Modifier = this.graphicsLayer {
    scaleX = 0.8f
    scaleY = 0.8f
    alpha = 0f
}

/**
 * 弹跳入场动画 composable
 */
@Composable
fun animateBounceEnter(): Pair<Float, Float> {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ))
        alpha.animateTo(1f, tween(200))
    }

    return Pair(scale.value, alpha.value)
}
