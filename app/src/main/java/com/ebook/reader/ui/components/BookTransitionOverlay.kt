package com.ebook.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebook.reader.ui.animation.AppEasing
import com.ebook.reader.ui.theme.AppColors
import com.ebook.reader.ui.theme.AppRadius
import com.ebook.reader.ui.theme.AppSpace
import com.ebook.reader.ui.theme.AppType
import com.ebook.reader.ui.theme.DingliSong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 加载过渡动画（和弹出容器风格一致）
 *
 * - 背景模糊压暗
 * - 主内容缩小 5%
 * - 全屏容器从底部滑入（标题 + 进度条）
 * - 加载完成后容器滑出，内容恢复
 */
@Composable
fun BookTransitionOverlay(
    title: String,
    isReady: Boolean,
    contentScale: Animatable<Float, *>,
    onTransitionComplete: () -> Unit
) {
    val scrimAlpha = remember { Animatable(0f) }
    val sheetOffset = remember { Animatable(1f) }  // 0=显示, 1=隐藏
    val sheetAlpha = remember { Animatable(0f) }
    val isClosing = remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
        // 背景压暗 + 内容缩小
        launch { scrimAlpha.animateTo(1f, tween(300)) }
        launch { contentScale.animateTo(0.95f, tween(300, easing = FastOutSlowInEasing)) }
        // 容器滑入
        sheetOffset.animateTo(0f, tween(400, easing = AppEasing.Smooth))
        sheetAlpha.animateTo(1f, tween(200))
    }

    // 退场动画
    LaunchedEffect(isReady) {
        if (isReady && sheetAlpha.value > 0.5f) {
            delay(200)
            isClosing.value = true
            // 容器滑出
            launch { sheetOffset.animateTo(1f, tween(300, easing = AppEasing.Accelerate)) }
            launch { sheetAlpha.animateTo(0f, tween(200)) }
            // 背景恢复 + 内容恢复
            launch { scrimAlpha.animateTo(0f, tween(300)) }
            contentScale.animateTo(1f, tween(300))
            onTransitionComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景遮罩（模糊 + 压暗）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value)
                .background(Color.Black.copy(alpha = 0.5f * scrimAlpha.value))
        )

        // 全屏容器（从底部滑入）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sheetOffset.value * size.height
                    alpha = sheetAlpha.value
                }
                .background(AppColors.WindowBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(200.dp))

                // 书名
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = DingliSong,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(260.dp)
                )

                Spacer(Modifier.height(32.dp))

                // 进度条
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(160.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AppColors.Accent,
                    trackColor = AppColors.Divider
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "正在加载...",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}
