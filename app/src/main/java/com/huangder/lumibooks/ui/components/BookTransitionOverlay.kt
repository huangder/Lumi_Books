package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.KaiTi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 全屏加载过渡页
 *
 * - 深色半透明遮罩
 * - 白色页面居中淡入 + 放大（同步）：封面 → 书名 → 加载指示器
 * - 页面四角大圆角
 * - 加载完成后淡出缩小，遮罩消失
 */
@Composable
fun BookTransitionOverlay(
    title: String,
    coverPath: String? = null,
    isReady: Boolean,
    onTransitionComplete: () -> Unit
) {
    val scrimAlpha = remember { Animatable(0f) }
    val sheetAlpha = remember { Animatable(0f) }
    val sheetScale = remember { Animatable(0.9f) }
    val isClosing = remember { mutableStateOf(false) }

    // 入场动画：遮罩 + 页面同步淡入放大
    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(300)) }
        launch { sheetAlpha.animateTo(1f, tween(300)) }
        launch { sheetScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    }

    // 退场动画
    LaunchedEffect(isReady) {
        if (isReady && sheetAlpha.value > 0.5f && !isClosing.value) {
            isClosing.value = true
            delay(200)
            launch { sheetAlpha.animateTo(0f, tween(200)) }
            launch { sheetScale.animateTo(0.95f, tween(250)) }
            launch { scrimAlpha.animateTo(0f, tween(300)) }
            delay(300)
            onTransitionComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 深色半透明遮罩（不用 blur，避免卡顿）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value)
                .background(Color.Black.copy(alpha = 0.55f))
        )

        // 白色加载页（居中淡入 + 放大，大圆角）
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = sheetScale.value
                        scaleY = sheetScale.value
                        alpha = sheetAlpha.value
                        // transformOrigin 默认居中，不需要额外设置
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
            ) {
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = AppSpace.xl,
                            end = AppSpace.xl,
                            bottom = navBarPadding.calculateBottomPadding()
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    // 封面占位
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(270.dp)
                            .clip(RoundedCornerShape(AppRadius.md))
                            .background(AppColors.BgGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverPath != null) {
                            AsyncImage(
                                model = File(coverPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(AppRadius.md)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // 书名
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = KaiTi,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // 副标题占位
                    Text(
                        text = "加载中",
                        fontSize = 14.sp,
                        color = Color(0xFF999999),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    // 加载指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.Black,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}
