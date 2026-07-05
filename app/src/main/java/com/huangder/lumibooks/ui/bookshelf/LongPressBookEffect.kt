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
 * - 短按（<300ms）→ onClick
 * - 长按（≥300ms）→ 进入上下文菜单
 * - 按下期间封面缩小到 0.95
 *
 * 使用 book.id 做 pointerInput key，确保手势协程绑定正确的书本。
 */
fun Modifier.longPressBookEffect(
    state: BookContextMenuState,
    book: () -> Book,
    onClick: () -> Unit,
    onCoverBounds: (Rect) -> Unit,
    onHaptic: () -> Unit = {}
): Modifier = composed {
    val scope = rememberCoroutineScope()
    // 在组合期间捕获当前书本，确保 pointerInput 使用正确的 book
    val currentBook = book()

    pointerInput(currentBook.id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            state.onPressDown(currentBook)

            var isLongPress = false
            val longPressJob = scope.launch {
                delay(300)
                isLongPress = true
                onCoverBounds(state.coverBounds)
                state.onLongPressConfirmed(
                    book = currentBook,
                    bounds = state.coverBounds,
                    onHaptic = onHaptic
                )
            }

            val up = waitForUpOrCancellation()
            longPressJob.cancel()

            if (!isLongPress) {
                state.onPressUp()
                if (up != null) {
                    onClick()
                }
            }
        }
    }
}
