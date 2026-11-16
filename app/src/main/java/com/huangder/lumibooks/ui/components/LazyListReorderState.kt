package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun <T : Any> rememberLazyListReorderState(
    listState: LazyListState,
    keyOf: (T) -> String
): LazyListReorderState<T> {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(listState, scope, density, keyOf) {
        LazyListReorderState(
            listState, scope, keyOf,
            edgeZonePx = with(density) { 56.dp.toPx() },
            maxScrollPerFramePx = with(density) { 14.dp.toPx() }
        )
    }
}

internal fun <T : Any> LazyItemScope.reorderableItemMotion(
    item: T,
    state: LazyListReorderState<T>,
    motionEnabled: Boolean
): Modifier {
    val floating = state.isFloating(item)
    return Modifier
        .then(
            if (motionEnabled && !floating) Modifier.animateItem(
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = VisualMotion.tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
            ) else Modifier
        )
        .zIndex(if (floating) 1f else 0f)
        .graphicsLayer {
            translationY = state.translationOf(item)
            val scale = if (motionEnabled && state.draggedItem == item) 1.02f else 1f
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Drag-to-reorder state for a contiguous range of keyed items in a vertical lazy list.
 *
 * While a card is dragged the list shows [draftOrder]; it is rearranged as soon as the card's
 * centre enters a neighbour's slot, so item placement animations let the other cards make way.
 * The dragged card is translated to stay under the finger (see [translationOf]). Nothing is
 * committed until [finish], which hands the final order back to the caller.
 *
 * The reorderable items use [keyOf]. Other items can precede or follow them.
 */
@Stable
internal class LazyListReorderState<T : Any>(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val keyOf: (T) -> String,
    /** Distance from a list edge where auto-scroll starts. */
    private val edgeZonePx: Float,
    /** Auto-scroll distance per frame when the card reaches the very edge. */
    private val maxScrollPerFramePx: Float
) {
    var draggedItem by mutableStateOf<T?>(null)
        private set
    var draftOrder by mutableStateOf<List<T>?>(null)
        private set

    /** Viewport offset of the dragged card when the drag started. */
    private var startOffset = 0f
    private var draggedHeight = 0
    private var firstReorderIndex = 0
    private var fingerTravel by mutableFloatStateOf(0f)
    private var autoScroll: Job? = null

    private var settlingItem by mutableStateOf<T?>(null)
    private var settleFrom = 0f
    private var settleProgress by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    private val isDragging: Boolean get() = draggedItem != null

    /** Returns false when the card is not laid out, e.g. after a fast fling. */
    fun start(item: T, order: List<T>): Boolean {
        if (isDragging) return false
        val info = itemOf(item) ?: return false
        val index = order.indexOf(item)
        if (index < 0) return false
        startOffset = info.offset + translationOf(item)
        draggedHeight = info.size
        firstReorderIndex = info.index - index
        settleJob?.cancel()
        settlingItem = null
        fingerTravel = 0f
        draftOrder = order
        draggedItem = item
        autoScroll = scope.launch {
            snapshotFlow { edgeScrollStep() != 0f }.collectLatest { scrolling ->
                if (scrolling) while (isActive && edgeScrollStep() != 0f) {
                    withFrameNanos { }
                    val step = edgeScrollStep()
                    if (step != 0f) {
                        listState.scrollBy(step)
                        followFinger()
                    }
                }
            }
        }
        return true
    }

    fun dragBy(deltaY: Float) {
        if (!isDragging) return
        fingerTravel += deltaY
        followFinger()
    }

    /** Ends the drag and returns the order to commit, or null if no drag was active. */
    fun finish(animateDrop: Boolean): List<T>? {
        val result = draftOrder
        val item = draggedItem
        if (animateDrop && item != null) {
            settleFrom = startOffset + fingerTravel
            settlingItem = item
            settleProgress = 1f
            settleJob = scope.launch {
                Animatable(1f).animateTo(
                    0f,
                    VisualMotion.tween(LumiMotion.MenuEnterMillis, easing = AppEasing.Smooth)
                ) { settleProgress = value }
                settlingItem = null
            }
        }
        reset()
        return result
    }

    fun cancel() = reset()

    fun isFloating(item: T): Boolean = item == draggedItem || item == settlingItem

    fun translationOf(item: T): Float {
        if (!isFloating(item)) return 0f
        val offset = itemOf(item)?.offset ?: return 0f
        return if (item == draggedItem) {
            startOffset + fingerTravel - offset
        } else {
            (settleFrom - offset) * settleProgress
        }
    }

    private fun reset() {
        autoScroll?.cancel()
        autoScroll = null
        draggedItem = null
        draftOrder = null
        fingerTravel = 0f
    }

    private fun itemOf(item: T): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keyOf(item) }

    private fun followFinger() {
        val item = draggedItem ?: return
        val order = draftOrder ?: return
        val visible = listState.layoutInfo.visibleItemsInfo.filter {
            it.size > 0 && it.index - firstReorderIndex in order.indices
        }
        if (visible.isEmpty()) return
        // Keep a fast drag in a visible slot so the gesture survives edge scrolling.
        val center = (startOffset + fingerTravel + draggedHeight / 2f).coerceIn(
            visible.first().offset.toFloat(),
            (visible.last().offset + visible.last().size - 1).toFloat()
        )
        val target = visible.firstOrNull {
            center >= it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOf(item)
        val to = order.indexOfFirst { keyOf(it) == target.key }
        if (from < 0 || to < 0 || from == to) return

        // Keep the viewport in place if reordering shifts its first visible key.
        val anchor = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        draftOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        if (anchor - firstReorderIndex in minOf(from, to)..maxOf(from, to)) {
            scope.launch { listState.scrollToItem(anchor, anchorOffset) }
        }
    }

    private fun edgeScrollStep(): Float {
        val order = draftOrder ?: return 0f
        val info = listState.layoutInfo
        val top = startOffset + fingerTravel
        val bottom = top + draggedHeight
        val visibleTop = 0
        val visibleBottom = info.viewportEndOffset - info.afterContentPadding
        val first = itemOf(order.first())
        val last = itemOf(order.last())
        return when {
            top < visibleTop + edgeZonePx && listState.canScrollBackward &&
                (first == null || first.offset < visibleTop) ->
                -maxScrollPerFramePx * ((visibleTop + edgeZonePx - top) / edgeZonePx).coerceIn(0f, 1f)
            bottom > visibleBottom - edgeZonePx && listState.canScrollForward &&
                (last == null || last.offset + last.size > visibleBottom) ->
                maxScrollPerFramePx * ((bottom - visibleBottom + edgeZonePx) / edgeZonePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
