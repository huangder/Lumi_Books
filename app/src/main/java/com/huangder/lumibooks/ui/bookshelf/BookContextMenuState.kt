package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.animation.AppEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 长按上下文菜单的阶段
 */
enum class ContextMenuPhase {
    Idle,       // 无操作
    Pressing,   // 手指按下中（缩小阶段）
    Enlarging,  // 放大弹出中
    Visible,    // 菜单完全显示
    Dismissing  // 收起中
}

/**
 * 长按上下文菜单状态管理
 *
 * @param scope 协程作用域，由 rememberBookContextMenuState 在 Composable 中提供
 */
class BookContextMenuState(private val scope: CoroutineScope) {
    var phase by mutableStateOf(ContextMenuPhase.Idle)
        private set

    var selectedBook by mutableStateOf<Book?>(null)
        private set

    var coverBounds by mutableStateOf(Rect.Zero)
        private set

    // 动画值
    val pressScale = Animatable(1f)
    val coverScale = Animatable(1f)
    val scrimAlpha = Animatable(0f)
    val menuAlpha = Animatable(0f)

    fun updateCoverBounds(bounds: Rect) {
        coverBounds = bounds
    }

    /**
     * 手指按下 — 缩小书本
     */
    fun onPressDown() {
        if (phase != ContextMenuPhase.Idle) return
        phase = ContextMenuPhase.Pressing
        scope.launch {
            pressScale.animateTo(
                targetValue = 0.95f,
                animationSpec = tween(100, easing = FastOutSlowInEasing)
            )
        }
    }

    /**
     * 手指抬起（未达到长按阈值）— 恢复大小
     */
    fun onPressUp() {
        if (phase == ContextMenuPhase.Enlarging || phase == ContextMenuPhase.Visible) return
        phase = ContextMenuPhase.Idle
        scope.launch {
            pressScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(100, easing = FastOutSlowInEasing)
            )
        }
    }

    /**
     * 长按确认 — 执行放大 + 震动 + 模糊 + 菜单入场
     */
    fun onLongPressConfirmed(
        book: Book,
        bounds: Rect,
        onHaptic: () -> Unit
    ) {
        if (phase != ContextMenuPhase.Pressing) return
        selectedBook = book
        coverBounds = bounds
        phase = ContextMenuPhase.Enlarging

        // 触发震动
        onHaptic()

        scope.launch {
            coroutineScope {
                // 并行执行：封面放大 + 背景遮罩淡入
                launch {
                    coverScale.snapTo(1f)
                    coverScale.animateTo(
                        targetValue = 1.08f,
                        animationSpec = spring(
                            dampingRatio = 0.6f,
                            stiffness = 300f
                        )
                    )
                }
                launch {
                    scrimAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(250, easing = AppEasing.Decelerate)
                    )
                }
                // pressScale 恢复到 1.0（因为 overlay 会接管显示）
                launch {
                    pressScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(150, easing = AppEasing.Decelerate)
                    )
                }
                // 菜单延迟淡入
                launch {
                    delay(100)
                    menuAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(200, easing = AppEasing.Decelerate)
                    )
                }
            }
            phase = ContextMenuPhase.Visible
        }
    }

    /**
     * 关闭菜单 — 反向动画
     */
    fun dismiss() {
        if (phase != ContextMenuPhase.Visible && phase != ContextMenuPhase.Enlarging) return
        phase = ContextMenuPhase.Dismissing

        scope.launch {
            coroutineScope {
                // 菜单先消失
                launch {
                    menuAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(150, easing = AppEasing.Accelerate)
                    )
                }
                // 延迟后遮罩和封面恢复
                launch {
                    delay(50)
                    scrimAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(200, easing = AppEasing.Accelerate)
                    )
                }
                launch {
                    delay(80)
                    coverScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(200, easing = AppEasing.Standard)
                    )
                }
            }
            // 重置状态
            selectedBook = null
            phase = ContextMenuPhase.Idle
        }
    }
}

@Composable
fun rememberBookContextMenuState(): BookContextMenuState {
    val scope = rememberCoroutineScope()
    return remember { BookContextMenuState(scope) }
}
