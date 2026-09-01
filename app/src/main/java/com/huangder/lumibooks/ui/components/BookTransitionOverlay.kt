package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * 全屏加载过渡页
 *
 * - 深色半透明遮罩
 * - 白色页面居中淡入 + 放大（同步）：封面 → 书名 → 加载指示器
 * - 页面四角大圆角
 * - 加载完成后淡出缩小，遮罩消失
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookTransitionOverlay(
    title: String,
    coverPath: String? = null,
    isReady: Boolean,
    onBackNavigationStarted: () -> Unit,
    onBack: () -> Unit,
    onTransitionComplete: () -> Unit
) {
    val scrimAlpha = remember { Animatable(0f) }
    val sheetAlpha = remember { Animatable(0f) }
    val sheetScale = remember { Animatable(0.9f) }
    val isClosing = remember { mutableStateOf(false) }
    val backRequested = remember { mutableStateOf(false) }
    val stableStatusBarPadding = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
    val stableNavigationBarPadding = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
    val requestBack = {
        if (!isClosing.value) {
            isClosing.value = true
            backRequested.value = true
            onBackNavigationStarted()
        }
    }

    val predictiveBackProgress = ConfigurableBackHandler(
        enabled = !isClosing.value,
        onBack = requestBack
    )

    // 入场动画：遮罩 + 页面同步淡入放大
    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(300)) }
        launch { sheetAlpha.animateTo(1f, tween(300)) }
        launch { sheetScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    }

    LaunchedEffect(backRequested.value) {
        if (backRequested.value) {
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                launch { sheetScale.animateTo(0.94f, tween(280, easing = FastOutSlowInEasing)) }
                launch { scrimAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            }
            onBack()
        }
    }

    // The reader can become ready before the entrance animation is visibly complete.
    // Do not drop that one-shot ready signal just because the sheet alpha is still low.
    LaunchedEffect(isReady) {
        if (!isReady || isClosing.value) return@LaunchedEffect

        // Wait until the entrance is visible, unless the user requests back first.
        snapshotFlow { sheetAlpha.value to isClosing.value }
            .first { (alpha, closing) -> alpha > 0.35f || closing }
        if (isClosing.value) return@LaunchedEffect

        isClosing.value = true
        coroutineScope {
            launch { sheetAlpha.animateTo(0f, tween(160)) }
            launch { sheetScale.animateTo(1.04f, tween(190)) }
            launch { scrimAlpha.animateTo(0f, tween(220)) }
        }
        onTransitionComplete()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 深色半透明遮罩（不用 blur，避免卡顿）

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value * (1f - predictiveBackProgress))
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
                        val backScale = 1f - predictiveBackProgress * 0.05f
                        scaleX = sheetScale.value * backScale
                        scaleY = sheetScale.value * backScale
                        alpha = sheetAlpha.value * (1f - predictiveBackProgress * 0.12f)
                        translationX = predictiveBackProgress * 48.dp.toPx()
                        // transformOrigin 默认居中，不需要额外设置
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppColors.CardBg)
            ) {
                LiquidGlassIconButton(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    onClick = requestBack,
                    enabled = !isClosing.value,
                    size = 44.dp,
                    iconSize = 22.dp,
                    normalContainerColor = AppColors.BgGray,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = AppSpace.lg,
                            top = stableStatusBarPadding.calculateTopPadding() + AppSpace.sm
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = AppSpace.xl,
                            end = AppSpace.xl,
                            bottom = stableNavigationBarPadding.calculateBottomPadding()
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
                        fontFamily = resolveAppFontFamily(KaiTi),
                        color = AppColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // 副标题占位
                    Text(
                        text = stringResource(R.string.loading_text),
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    // 加载指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = AppColors.Accent,
                        strokeWidth = 2.5.dp
                    )

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = stringResource(R.string.reader_first_open_hint),
                        modifier = Modifier.padding(horizontal = AppSpace.lg),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = AppColors.TextSecondary.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
