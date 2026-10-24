package com.huangder.lumibooks.ui.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.huangder.lumibooks.ui.theme.AppSpace
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun RecentBooksRow(
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val spacingPx = with(density) { AppSpace.md.toPx() }
    val fling = remember(state, itemWidthPx, spacingPx) {
        RecentBooksFlingBehavior(state, itemWidthPx, spacingPx)
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().testTag("recent_books_row"),
        contentPadding = PaddingValues(horizontal = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
        flingBehavior = fling,
        content = content
    )
}

/** Ease to a bounded destination so a short shelf doesn't abruptly hit its edge. */
private class RecentBooksFlingBehavior(
    private val state: LazyListState,
    private val itemWidth: Float,
    private val spacing: Float
) : FlingBehavior {
    private val decay = exponentialDecay<Float>()
    private val easing = CubicBezierEasing(.25f, .75f, .5f, 1f)

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) < 20f) return initialVelocity
        val layout = state.layoutInfo
        val contentWidth = layout.totalItemsCount * itemWidth +
            (layout.totalItemsCount - 1).coerceAtLeast(0) * spacing +
            layout.beforeContentPadding + layout.afterContentPadding
        val maxScroll = (contentWidth - layout.viewportSize.width).coerceAtLeast(0f)
        val currentScroll = state.firstVisibleItemIndex * (itemWidth + spacing) +
            state.firstVisibleItemScrollOffset
        val distance = decay.calculateTargetValue(0f, initialVelocity)
            .coerceIn(-currentScroll, (maxScroll - currentScroll).coerceAtLeast(0f))
        if (abs(distance) < .5f) return initialVelocity
        // The curve starts with slope 3 and ends at zero velocity. Keep enough time
        // to decelerate even when a fast flick has very little room left to travel.
        val duration = (abs(distance / initialVelocity) * 3_000f)
            .roundToInt().coerceIn(280, 850)
        var previous = 0f
        AnimationState(0f).animateTo(distance, tween(duration, easing = easing)) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > .5f) cancelAnimation()
        }
        return 0f
    }
}
