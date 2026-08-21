package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

/**
 * 底部弹出容器（背景压暗 + 模糊，卡片从底部滑入）
 */
@Composable
fun BottomSheetContainer(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val motionEnabled = LocalMotionEnabled.current
    if (visible) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景压暗 + 模糊
            ScrimOverlay(onClick = onDismiss)

            // 卡片从底部滑入
            AnimatedVisibility(
                visible = visible,
                enter = if (motionEnabled) {
                    slideInVertically(
                        animationSpec = tween(LumiMotion.SheetEnterMillis, easing = AppEasing.Smooth)
                    ) { it } + fadeIn(animationSpec = tween(300))
                } else {
                    fadeIn(animationSpec = tween(140))
                },
                exit = if (motionEnabled) {
                    slideOutVertically(
                        animationSpec = tween(LumiMotion.SheetExitMillis, easing = AppEasing.Accelerate)
                    ) { it } + fadeOut(animationSpec = tween(200))
                } else {
                    fadeOut(animationSpec = tween(160))
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

/**
 * 背景蒙版：压暗 + 高斯模糊
 */
@Composable
fun ScrimOverlay(
    alpha: Float = 1f,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(AppColors.Scrim.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    )
}

/**
 * 通用淡入淡出容器
 */
@Composable
fun FadeContainer(
    visible: Boolean,
    duration: Int = 300,
    content: @Composable () -> Unit
) {
    val motionEnabled = LocalMotionEnabled.current
    AnimatedVisibility(
        visible = visible,
        enter = if (motionEnabled) fadeIn(animationSpec = tween(duration)) else EnterTransition.None,
        exit = if (motionEnabled) fadeOut(animationSpec = tween(duration)) else ExitTransition.None
    ) {
        content()
    }
}
