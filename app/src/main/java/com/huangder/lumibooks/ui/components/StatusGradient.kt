package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors

/**
 * 状态栏渐变模糊遮罩
 * 从背景色渐变到透明，营造内容滑入状态栏的柔和过渡
 */
/**
 * 状态栏渐变遮罩
 * 固定在屏幕最顶部，覆盖状态栏区域，从背景色渐变到透明
 * 内容从下方滚动上来时，会柔和地消失在遮罩下
 */
@Composable
fun StatusGradientOverlay() {
    val bgColor = AppColors.WindowBg
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp) // 状态栏 + 少量渐变
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor,                      // 最顶部：纯背景色
                        0.55f to bgColor,                     // 状态栏下边界：仍然不透明
                        1.0f to bgColor.copy(alpha = 0f)      // 渐变到透明
                    )
                )
            )
    )
}

/**
 * 底部导航栏渐变遮罩（可选）
 */
@Composable
fun NavigationGradientOverlay() {
    val bgColor = AppColors.WindowBg
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor.copy(alpha = 0f),
                        bgColor.copy(alpha = 0.8f),
                        bgColor
                    )
                )
            )
    )
}
