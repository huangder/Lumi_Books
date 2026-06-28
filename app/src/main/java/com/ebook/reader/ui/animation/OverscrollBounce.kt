package com.ebook.reader.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverscrollBounce(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    val connection = remember(scope, offset) {
        BounceNestedScrollConnection(offset, scope)
    }

    // 禁用 Android 原生 overscroll（拉伸效果）
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        Box(
            modifier = modifier
                .nestedScroll(connection)
                .graphicsLayer { translationY = offset.value },
            content = content
        )
    }
}

private class BounceNestedScrollConnection(
    private val offset: Animatable<Float, *>,
    private val scope: CoroutineScope
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
            scope.launch {
                // 阻尼系数 0.4（iOS 约 0.3~0.5）
                offset.snapTo(offset.value + dy * 0.4f)
            }
            return Offset(0f, dy) // 消耗全部剩余滚动
        }
        return Offset.Zero
    }

    // 松手时：弹性回弹
    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
    ): Velocity {
        if (abs(offset.value) > 0.5f) {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.65f,  // iOS 风格弹性
                    stiffness = 400f
                )
            )
        } else {
            offset.snapTo(0f)
        }
        return super.onPostFling(consumed, available)
    }
}
