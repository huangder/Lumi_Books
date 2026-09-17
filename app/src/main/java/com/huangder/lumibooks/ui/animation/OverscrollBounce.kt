package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * iOS 风格弹性回弹效果
 *
 * 包裹在可滚动内容外层，提供触底/触顶时的橡胶回弹。
 * 同时禁用 Android 原生拉伸 overscroll。
 *
 * 用法：
 * ```
 * OverscrollBounce {
 *     LazyColumn { ... }
 * }
 * ```
 *
 * 需要感知顶部下拉量时（例如"松手继续阅读"），把自己的 [OverscrollBounceState]
 * 传进来并读取 `offset`，同时可用 [onTopPullRelease] 在松手瞬间拿到最终下拉量。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverscrollBounce(
    modifier: Modifier = Modifier,
    state: OverscrollBounceState = remember { OverscrollBounceState() },
    onTopPullRelease: ((offsetPx: Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val releaseCallback = rememberUpdatedState(onTopPullRelease)
    val connection = remember(state) {
        BounceNestedScrollConnection(state) { offset ->
            releaseCallback.value?.invoke(offset)
        }
    }

    // 禁用 Android 原生 overscroll（拉伸效果）
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        Box(
            modifier = modifier
                .nestedScroll(connection)
                .graphicsLayer { translationY = state.offset },
            content = content
        )
    }
}

/** Horizontal rubber-band overscroll used by compact rows of controls. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalOverscrollBounce(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bounceState = remember { HorizontalBounceState() }
    val connection = remember(bounceState) {
        HorizontalBounceNestedScrollConnection(bounceState)
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Box(
            modifier = modifier
                .nestedScroll(connection)
                .graphicsLayer { translationX = bounceState.offset },
            content = content
        )
    }
}

private class BounceNestedScrollConnection(
    private val state: OverscrollBounceState,
    private val onRelease: (offsetPx: Float) -> Unit
) : NestedScrollConnection {

    // 拖拽时：施加阻尼
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero

        // 只在用户拖拽时处理，且有剩余滚动量（说明到顶/到底了）
        if (source == NestedScrollSource.UserInput && abs(dy) > 0.5f) {
            // 同步更新位移，避免为每个触摸事件创建一个主线程协程。
            state.dragBy(dy * 0.4f)
            return Offset(0f, dy) // 消耗全部剩余滚动
        }
        return Offset.Zero
    }

    // 松手时：弹性回弹
    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
    ): Velocity {
        onRelease(state.offset)
        state.animateBack()
        return super.onPostFling(consumed, available)
    }
}

/**
 * 垂直方向的越界下拉状态。
 *
 * [offset] 为内容当前的纵向位移（px）：顶部下拉为正、底部上拉为负、静置为 0。
 * 位移量与阻尼算法由 [OverscrollBounce] 维护，外部只读取。
 */
class OverscrollBounceState {
    var offset by mutableFloatStateOf(0f)
        internal set

    internal fun dragBy(delta: Float) {
        offset += delta
    }

    internal suspend fun animateBack() {
        if (abs(offset) <= 0.5f) {
            offset = 0f
            return
        }

        animate(
            initialValue = offset,
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.65f,
                stiffness = 400f
            )
        ) { value, _ ->
            offset = value
        }
    }
}

private class HorizontalBounceNestedScrollConnection(
    private val state: HorizontalBounceState
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val consumed = state.releaseBy(available.x)
        return if (consumed == 0f) Offset.Zero else Offset(consumed, 0f)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val dx = available.x
        if (source == NestedScrollSource.UserInput && abs(dx) > 0.5f) {
            state.dragBeyondBoundary(dx)
            return Offset(dx, 0f)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
    ): Velocity {
        state.animateBack()
        return super.onPostFling(consumed, available)
    }
}

private class HorizontalBounceState {
    var offset by mutableFloatStateOf(0f)
        private set

    fun releaseBy(delta: Float): Float {
        if (offset == 0f || delta == 0f || offset * delta >= 0f) return 0f
        val consumedMagnitude = minOf(abs(delta), abs(offset))
        val consumed = if (delta > 0f) consumedMagnitude else -consumedMagnitude
        offset += consumed
        if (abs(offset) < 0.5f) offset = 0f
        return consumed
    }

    fun dragBeyondBoundary(delta: Float) {
        val resistance = 0.44f / (1f + abs(offset) / 72f)
        offset += delta * resistance
    }

    suspend fun animateBack() {
        if (abs(offset) <= 0.5f) {
            offset = 0f
            return
        }
        animate(
            initialValue = offset,
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = 360f
            )
        ) { value, _ ->
            offset = value
        }
    }
}
