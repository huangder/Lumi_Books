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
    /** 信息面板（书名+作者+编辑）alpha */
    val menuAlpha = Animatable(0f)
    /** 操作面板（删除/收藏/封面/书签）整体 alpha，内部单项再做交错 */
    val actionsAlpha = Animatable(0f)
    /** 原位书本的 alpha */
    val itemAlpha = Animatable(1f)

    fun updateCoverBounds(bounds: Rect) {
        coverBounds = bounds
    }

    /**
     * 手指按下 — 缩小书本（仅缩小目标书）
     */
    fun onPressDown(book: Book) {
        if (phase != ContextMenuPhase.Idle) return
        selectedBook = book
        phase = ContextMenuPhase.Pressing
        scope.launch {
            pressScale.snapTo(1f)
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
     * 长按确认 — 执行放大 + 震动 + 模糊 + 菜单分步入场
     *
     * 入场时序：
     * 0ms   - 封面 spring 放大 + 震动 + 遮罩淡入
     * 100ms - 信息面板淡入
     * 250ms - 操作项依次淡出（由 composable 层交错驱动）
     */
    fun onLongPressConfirmed(
        book: Book,
        bounds: Rect,
        onHaptic: () -> Unit
    ) {
        if (phase != ContextMenuPhase.Idle && phase != ContextMenuPhase.Pressing) return
        selectedBook = book
        coverBounds = bounds
        phase = ContextMenuPhase.Enlarging

        // 立即隐藏原位书本
        scope.launch { itemAlpha.snapTo(0f) }

        // 触发震动
        onHaptic()

        scope.launch {
            coroutineScope {
                // 封面放大
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
                // 遮罩淡入
                launch {
                    scrimAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = AppEasing.Decelerate)
                    )
                }
                // pressScale 恢复
                launch {
                    pressScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(200, easing = AppEasing.Decelerate)
                    )
                }
                // 信息面板淡入（延迟 150ms）
                launch {
                    delay(150)
                    menuAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(300, easing = AppEasing.Decelerate)
                    )
                }
                // 操作面板整体淡入（延迟 350ms，内部单项在 composable 层交错）
                launch {
                    delay(350)
                    actionsAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = AppEasing.Decelerate)
                    )
                }
            }
            phase = ContextMenuPhase.Visible
        }
    }

    /**
     * 关闭菜单
     *
     * 退出时序：
     * 0ms   - 操作项依次消失
     * 150ms - 信息面板消失
     * 200ms - 封面归位 + 遮罩消失
     * 460ms - 原位书本渐显
     */
    fun dismiss() {
        if (phase != ContextMenuPhase.Visible && phase != ContextMenuPhase.Enlarging) return
        phase = ContextMenuPhase.Dismissing

        scope.launch {
            // ① 操作项 + 信息面板并行消失
            coroutineScope {
                launch {
                    actionsAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(200, easing = AppEasing.Accelerate)
                    )
                }
                launch {
                    delay(80)
                    menuAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(200, easing = AppEasing.Accelerate)
                    )
                }
            }
            // ② 封面归位 + 遮罩消失（并行，等动画完成）
            coroutineScope {
                launch {
                    coverScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = AppEasing.Decelerate)
                    )
                }
                launch {
                    scrimAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(400, easing = AppEasing.Accelerate)
                    )
                }
            }
            // ③ 封面已完全归位，原位书本立即显示（overlay 封面盖在上面，用户无感知）
            itemAlpha.snapTo(1f)
            // ④ 切 Idle，overlay 消失，原位书本已就位
            pressScale.snapTo(1f)
            coverScale.snapTo(1f)
            scrimAlpha.snapTo(0f)
            menuAlpha.snapTo(0f)
            actionsAlpha.snapTo(0f)
            itemAlpha.snapTo(1f)
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
