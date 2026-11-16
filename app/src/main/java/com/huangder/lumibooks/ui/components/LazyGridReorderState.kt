package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import com.huangder.lumibooks.ui.animation.VisualMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun <T : Any> rememberLazyGridReorderState(
    gridState: LazyGridState,
    keyOf: (T) -> Any
): LazyGridReorderState<T> {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(gridState, scope, density, keyOf) {
        LazyGridReorderState(
            gridState, scope, keyOf,
            edgeZonePx = with(density) { 72.dp.toPx() },
            maxScrollPerFramePx = with(density) { 16.dp.toPx() },
            slackPx = with(density) { 12.dp.toPx() }
        )
    }
}

/**
 * Index of the slot whose box (grown by [slack] on every side, to cover the grid spacing)
 * contains [point]; the nearest centre wins where grown boxes overlap. -1 when none does.
 */
internal fun reorderTargetIndex(point: Offset, slots: List<Rect>, slack: Float): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    slots.forEachIndexed { index, rect ->
        if (point.x < rect.left - slack || point.x > rect.right + slack ||
            point.y < rect.top - slack || point.y > rect.bottom + slack
        ) return@forEachIndexed
        val distance = (rect.center - point).getDistanceSquared()
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/**
 * Drag-to-reorder for a contiguous range of keyed items in a [LazyGridState], the
 * two-dimensional sibling of [LazyListReorderState].
 *
 * While a card is dragged the grid shows [draftOrder]; it is rearranged as soon as the card's
 * centre enters another slot of the range, and the other cards move there with their item
 * placement animation. Nothing is committed until [finish].
 */
@Stable
internal class LazyGridReorderState<T : Any>(
    private val gridState: LazyGridState,
    private val scope: CoroutineScope,
    private val keyOf: (T) -> Any,
    private val edgeZonePx: Float,
    private val maxScrollPerFramePx: Float,
    private val slackPx: Float
) {
    var draggedItem by mutableStateOf<T?>(null)
        private set
    var draftOrder by mutableStateOf<List<T>?>(null)
        private set

    /** Items of [draftOrder] that the dragged card may swap with (e.g. its pinned group). */
    private var movableRange = IntRange.EMPTY
    private var startOffset = Offset.Zero
    private var draggedWidth = 0
    private var draggedHeight = 0
    private var firstReorderIndex = 0
    private var travelX by mutableFloatStateOf(0f)
    private var travelY by mutableFloatStateOf(0f)
    private var autoScroll: Job? = null

    private var settlingItem by mutableStateOf<T?>(null)
    private var settleFrom = Offset.Zero
    private var settleProgress by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    val isDragging: Boolean get() = draggedItem != null

    /**
     * Starts dragging [item] within [order]; only positions in [movable] (indices of [order])
     * are swapped. Returns false when the card is not laid out.
     */
    fun start(item: T, order: List<T>, movable: IntRange = order.indices): Boolean {
        if (isDragging) return false
        val info = itemOf(item) ?: return false
        val index = order.indexOf(item)
        if (index < 0 || index !in movable) return false
        startOffset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + translationOf(item)
        draggedWidth = info.size.width
        draggedHeight = info.size.height
        firstReorderIndex = info.index - index
        movableRange = movable
        settleJob?.cancel()
        settlingItem = null
        travelX = 0f
        travelY = 0f
        draftOrder = order
        draggedItem = item
        autoScroll = scope.launch {
            snapshotFlow { edgeScrollStep() != 0f }.collectLatest { scrolling ->
                if (scrolling) while (isActive && edgeScrollStep() != 0f) {
                    withFrameNanos { }
                    val step = edgeScrollStep()
                    if (step != 0f) {
                        gridState.scrollBy(step)
                        followFinger()
                    }
                }
            }
        }
        return true
    }

    fun dragBy(delta: Offset) {
        if (!isDragging) return
        travelX += delta.x
        travelY += delta.y
        followFinger()
    }

    /** Ends the drag and returns the order to commit, or null if no drag was active. */
    fun finish(animateDrop: Boolean): List<T>? {
        val result = draftOrder
        val item = draggedItem
        if (animateDrop && item != null) {
            settleFrom = startOffset + Offset(travelX, travelY)
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

    fun translationOf(item: T): Offset {
        if (!isFloating(item)) return Offset.Zero
        val info = itemOf(item) ?: return Offset.Zero
        val offset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
        return if (item == draggedItem) {
            startOffset + Offset(travelX, travelY) - offset
        } else {
            (settleFrom - offset) * settleProgress
        }
    }

    private fun reset() {
        autoScroll?.cancel()
        autoScroll = null
        draggedItem = null
        draftOrder = null
        travelX = 0f
        travelY = 0f
        movableRange = IntRange.EMPTY
    }

    private fun itemOf(item: T): LazyGridItemInfo? {
        val key = keyOf(item)
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }

    private fun followFinger() {
        val item = draggedItem ?: return
        val order = draftOrder ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo.filter {
            it.size.width > 0 && it.index - firstReorderIndex in movableRange
        }
        if (visible.isEmpty()) return
        val topLeft = startOffset + Offset(travelX, travelY)
        // Keep a fast drag inside the visible slots so the gesture survives edge scrolling.
        val center = Offset(
            topLeft.x + draggedWidth / 2f,
            (topLeft.y + draggedHeight / 2f).coerceIn(
                visible.minOf { it.offset.y }.toFloat(),
                visible.maxOf { it.offset.y + it.size.height - 1 }.toFloat()
            )
        )
        val slots = visible.map {
            Rect(
                it.offset.x.toFloat(), it.offset.y.toFloat(),
                (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat()
            )
        }
        val hit = reorderTargetIndex(center, slots, slackPx)
        if (hit < 0) return
        val from = order.indexOf(item)
        val to = visible[hit].index - firstReorderIndex
        if (from < 0 || to !in order.indices || from == to) return

        // Keep the viewport in place if reordering shifts its first visible key.
        val anchor = gridState.firstVisibleItemIndex
        val anchorOffset = gridState.firstVisibleItemScrollOffset
        draftOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        if (anchor - firstReorderIndex in minOf(from, to)..maxOf(from, to)) {
            scope.launch { gridState.scrollToItem(anchor, anchorOffset) }
        }
    }

    private fun edgeScrollStep(): Float {
        val order = draftOrder ?: return 0f
        if (movableRange.isEmpty()) return 0f
        val info = gridState.layoutInfo
        val top = startOffset.y + travelY
        val bottom = top + draggedHeight
        val visibleTop = 0
        val visibleBottom = info.viewportEndOffset - info.afterContentPadding
        val first = itemOf(order[movableRange.first])
        val last = itemOf(order[movableRange.last])
        return when {
            top < visibleTop + edgeZonePx && gridState.canScrollBackward &&
                (first == null || first.offset.y < visibleTop) ->
                -maxScrollPerFramePx * ((visibleTop + edgeZonePx - top) / edgeZonePx).coerceIn(0f, 1f)
            bottom > visibleBottom - edgeZonePx && gridState.canScrollForward &&
                (last == null || last.offset.y + last.size.height > visibleBottom) ->
                maxScrollPerFramePx * ((bottom - visibleBottom + edgeZonePx) / edgeZonePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
