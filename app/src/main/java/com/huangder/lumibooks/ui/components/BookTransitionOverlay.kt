package com.huangder.lumibooks.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
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
    val showLoadingDetails = remember { mutableStateOf(false) }
    val overlayShownAtMs = remember { mutableStateOf<Long?>(null) }
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

    LaunchedEffect(backRequested.value) {
        if (backRequested.value) {
            if (overlayShownAtMs.value == null) {
                onBack()
                return@LaunchedEffect
            }
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                launch { sheetScale.animateTo(0.94f, tween(280, easing = FastOutSlowInEasing)) }
                launch { scrimAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            }
            onBack()
        }
    }

    // Do not reveal the loading page for fast opens. The reader is already loading underneath,
    // so a ready signal during the grace period can transition directly to visible content.
    LaunchedEffect(isReady) {
        if (!isReady) {
            delay(BookTransitionTiming.OVERLAY_REVEAL_DELAY_MS)
            if (!isClosing.value) {
                overlayShownAtMs.value = SystemClock.elapsedRealtime()
                showLoadingDetails.value = true
                coroutineScope {
                    launch { scrimAlpha.animateTo(1f, tween(300)) }
                    launch { sheetAlpha.animateTo(1f, tween(300)) }
                    launch {
                        sheetScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                    }
                }
            }
            return@LaunchedEffect
        }
        if (isClosing.value) return@LaunchedEffect

        val shownAtMs = overlayShownAtMs.value
        if (shownAtMs == null) {
            isClosing.value = true
            scrimAlpha.snapTo(0f)
            sheetAlpha.snapTo(0f)
            sheetScale.snapTo(0.9f)
            onTransitionComplete()
            return@LaunchedEffect
        }

        val remainingVisibleMs = BookTransitionTiming.remainingOverlayVisibleMillis(
            shownAtMs = shownAtMs,
            nowMs = SystemClock.elapsedRealtime()
        )
        // If readiness arrives during entrance, finish the entrance rather than reversing a
        // partially visible surface. That partial reversal is perceived as a screen flash.
        coroutineScope {
            if (remainingVisibleMs > 0L) launch { delay(remainingVisibleMs) }
            if (scrimAlpha.value < 0.999f) {
                launch { scrimAlpha.animateTo(1f, tween(300)) }
            }
            if (sheetAlpha.value < 0.999f) {
                launch { sheetAlpha.animateTo(1f, tween(300)) }
            }
            if (sheetScale.value < 0.999f) {
                launch { sheetScale.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
            }
        }
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        BookTransitionLoadingDetails(visible = showLoadingDetails.value)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookTransitionLoadingDetails(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(BookTransitionTiming.LOADING_DETAILS_FADE_IN_MS)),
        exit = fadeOut(tween(BookTransitionTiming.LOADING_DETAILS_FADE_OUT_MS))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.loading_text),
                fontSize = 14.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

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

internal object BookTransitionTiming {
    const val OVERLAY_REVEAL_DELAY_MS = 500L
    const val MIN_OVERLAY_VISIBLE_MS = 300L
    const val LOADING_DETAILS_FADE_IN_MS = 140
    const val LOADING_DETAILS_FADE_OUT_MS = 100

    fun remainingOverlayVisibleMillis(shownAtMs: Long, nowMs: Long): Long {
        val visibleDurationMs = (nowMs - shownAtMs).coerceAtLeast(0L)
        return (MIN_OVERLAY_VISIBLE_MS - visibleDurationMs).coerceAtLeast(0L)
    }
}
