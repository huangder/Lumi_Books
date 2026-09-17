package com.huangder.lumibooks.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.OverscrollBounceState
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/**
 * 首页顶部下拉「松手继续阅读」。
 *
 * 列表已到顶后继续下拉时，上方留白里淡入提示文字；松手时内容位移达到
 * [PULL_TO_RESUME_THRESHOLD_DP] 才打开最近阅读的书籍，否则只回弹。
 * 下拉阻尼完全沿用 [com.huangder.lumibooks.ui.animation.OverscrollBounce] 的既有实现。
 */

/** 触发打开书籍所需的内容下移量。阻尼为 0.4，因此约等于 240dp 的手指行程，避免误触。 */
internal const val PULL_TO_RESUME_THRESHOLD_DP = 96f

/** 提示文字随下拉进度下滑的距离，进度为 1 时落到锚点位置。 */
internal const val PULL_TO_RESUME_HINT_SLIDE_DP = 12f

internal const val HOME_PULL_TO_RESUME_HINT_TAG = "home_pull_to_resume_hint"

/**
 * 下拉进度 `0f..1f`。
 *
 * 只有内容被向下拉出（`offsetPx > 0`）时才可见；底部回弹的负位移与静置都返回 0。
 */
internal fun pullToResumeProgress(offsetPx: Float, thresholdPx: Float): Float {
    if (offsetPx <= 0f || thresholdPx <= 0f) return 0f
    return (offsetPx / thresholdPx).coerceIn(0f, 1f)
}

/** 松手时是否应打开最近阅读的书籍。原路推回（位移回落）时不满足阈值，因此不做任何事。 */
internal fun shouldOpenRecentBookOnRelease(releasedOffsetPx: Float, thresholdPx: Float): Boolean =
    thresholdPx > 0f && releasedOffsetPx >= thresholdPx

/**
 * 绘制在首页列表上方的下拉提示。只读取 [bounceState] 的位移，
 * 因此拖动期间只有这一小块重组。
 */
@Composable
internal fun HomePullToResumeHint(
    bounceState: OverscrollBounceState,
    statusBarTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    val thresholdPx = with(LocalDensity.current) { PULL_TO_RESUME_THRESHOLD_DP.dp.toPx() }
    val progress = pullToResumeProgress(bounceState.offset, thresholdPx)
    if (progress <= 0f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarTopPadding + AppSpace.sm)
            .graphicsLayer {
                alpha = progress
                translationY = -PULL_TO_RESUME_HINT_SLIDE_DP.dp.toPx() * (1f - progress)
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = stringResource(R.string.home_pull_to_resume_recent_book),
            modifier = Modifier
                .padding(horizontal = AppSpace.lg)
                .testTag(HOME_PULL_TO_RESUME_HINT_TAG),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
