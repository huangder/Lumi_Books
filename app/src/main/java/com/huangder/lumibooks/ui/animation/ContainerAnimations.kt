package com.huangder.lumibooks.ui.animation

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.huangder.lumibooks.ui.theme.AppColors

/**
 * 底部弹出容器（背景压暗 + 模糊，卡片从底部滑入）
 */
@Composable
fun BottomSheetContainer(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    if (visible) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景压暗 + 模糊
            ScrimOverlay(onClick = onDismiss)

            // 卡片从底部滑入
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(400, easing = AppEasing.Smooth)
                ) { it } + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    animationSpec = tween(300, easing = AppEasing.Accelerate)
                ) { it } + fadeOut(animationSpec = tween(200)),
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
            .then(
                if (Build.VERSION.SDK_INT >= 31) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                } else Modifier
            )
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(duration)),
        exit = fadeOut(animationSpec = tween(duration))
    ) {
        content()
    }
}
