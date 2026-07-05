package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import com.huangder.lumibooks.domain.model.Book
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 长按书本效果 Modifier
 *
 * 替代 cardPressEffect + clickable：
 * - 短按（<300ms）→ onClick
 * - 长按（≥300ms）→ 进入上下文菜单
 * - 按下期间持续缩小到 0.95
 *
 * @param state 上下文菜单状态
 * @param book 当前书本
 * @param onClick 短按回调（进入阅读器）
 * @param onCoverBounds 封面位置回调（用于 overlay 定位）
 */
fun Modifier.longPressBookEffect(
    state: BookContextMenuState,
    book: () -> Book,
    onClick: () -> Unit,
    onCoverBounds: (Rect) -> Unit,
    onHaptic: () -> Unit = {}
): Modifier = composed {
    val scope = rememberCoroutineScope()

    pointerInput(Unit) {
        awaitEachGesture {
            // 等待手指按下
            val down = awaitFirstDown(requireUnconsumed = false)

            // 通知状态：按下开始（传入当前书本，仅缩小目标书）
            state.onPressDown(book())

            // 启动长按计时
            var isLongPress = false
            val longPressJob = scope.launch {
                delay(300)
                isLongPress = true
                // 长按确认，触发菜单
                onCoverBounds(state.coverBounds) // bounds 由外部 onGloballyPositioned 设置
                state.onLongPressConfirmed(
                    book = book(),
                    bounds = state.coverBounds,
                    onHaptic = onHaptic
                )
            }

            // 等待手指抬起或取消
            val up = waitForUpOrCancellation()

            longPressJob.cancel()

            if (!isLongPress) {
                // 未达到长按阈值 → 短按
                state.onPressUp()
                if (up != null) {
                    onClick()
                }
            }
            // 如果是长按，状态已经在 onLongPressConfirmed 中切换，不需要额外处理
        }
    }
}
