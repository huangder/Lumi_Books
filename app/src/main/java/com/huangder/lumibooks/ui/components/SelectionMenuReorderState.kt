package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Gesture preview stays local; only a completed drop is sent to the preference store. */
@Stable
internal class SelectionMenuReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    initialOrder: List<SelectionMenuAction>,
    private val edgeZone: Float,
    private val maxScrollPerSecond: Float,
    private val motionEnabled: Boolean
) {
    var order by mutableStateOf(initialOrder)
        private set
    var draggedAction by mutableStateOf<SelectionMenuAction?>(null)
        private set
    var dragging by mutableStateOf(false)
        private set
    var scrollSpeed by mutableFloatStateOf(0f)
        private set
    var listCoordinates: LayoutCoordinates? = null
    private val handles = mutableMapOf<SelectionMenuAction, LayoutCoordinates>()
    private var externalOrder = initialOrder
    private var originalOrder = initialOrder
    private var startPointerY = 0f
    private var startTop = 0f
    private var draggedTop by mutableFloatStateOf(0f)
    private var releaseFraction by mutableFloatStateOf(0f)
    private var releaseJob: Job? = null

    fun syncOrder(value: List<SelectionMenuAction>) {
        if (value == externalOrder) return
        externalOrder = value
        if (value != order) {
            releaseJob?.cancel()
            draggedAction = null
            dragging = false
            scrollSpeed = 0f
            order = value
        }
    }

    fun registerHandle(action: SelectionMenuAction, coordinates: LayoutCoordinates) {
        handles[action] = coordinates
    }

    fun handleAt(position: Offset): SelectionMenuAction? {
        val parent = listCoordinates?.takeIf { it.isAttached } ?: return null
        return order.firstOrNull { action ->
            val handle = handles[action]?.takeIf { it.isAttached }
            handle != null && listState.layoutInfo.visibleItemsInfo.any { it.key == action.key } &&
                parent.localBoundingBoxOf(handle, clipBounds = false).contains(position)
        }
    }

    fun start(action: SelectionMenuAction, pointerY: Float): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == action.key } ?: return false
        releaseJob?.cancel()
        originalOrder = order
        startPointerY = pointerY
        startTop = item.offset.toFloat()
        draggedTop = startTop
        releaseFraction = 0f
        draggedAction = action
        dragging = true
        return true
    }

    fun dragTo(pointerY: Float) {
        val action = draggedAction ?: return
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == action.key } ?: return
        val rawTop = startTop + pointerY - startPointerY
        // Content padding reserves the toolbar/footer. The lifted card cannot enter those bands.
        val bottom = (layout.viewportEndOffset - layout.afterContentPadding - item.size).coerceAtLeast(0).toFloat()
        draggedTop = rawTop.coerceIn(0f, bottom)
        scrollSpeed = when {
            rawTop < edgeZone && listState.canScrollBackward ->
                -maxScrollPerSecond * ((edgeZone - rawTop) / edgeZone).coerceIn(0f, 1f)
            rawTop > bottom - edgeZone && listState.canScrollForward ->
                maxScrollPerSecond * ((rawTop - bottom + edgeZone) / edgeZone).coerceIn(0f, 1f)
            else -> 0f
        }
        reorderAtPointer()
    }

    fun reorderAtPointer() {
        if (!dragging) return
        val action = draggedAction ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == action.key } ?: return
        val index = order.indexOf(action)
        // A layout from the previous frame may still contain the old key/index mapping.
        if (current.index != index) return
        val center = draggedTop + current.size / 2f
        val previous = items.firstOrNull { it.index == index - 1 && it.key == order.getOrNull(index - 1)?.key }
        val next = items.firstOrNull { it.index == index + 1 && it.key == order.getOrNull(index + 1)?.key }
        val target = when {
            next != null && center >= next.offset + next.size / 2f -> index + 1
            previous != null && center <= previous.offset + previous.size / 2f -> index - 1
            else -> return
        }
        keepViewport()
        order = order.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun translationY(action: SelectionMenuAction): Float {
        if (action != draggedAction) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == action.key } ?: return 0f
        return (draggedTop - item.offset) * (1f - releaseFraction)
    }

    fun finish(commit: Boolean): List<SelectionMenuAction>? {
        if (!dragging) return null
        dragging = false
        scrollSpeed = 0f
        val changed = order != originalOrder
        if (!commit) {
            keepViewport()
            order = originalOrder
        }
        releaseJob = scope.launch {
            if (motionEnabled) {
                Animatable(0f).animateTo(1f, tween(140)) { releaseFraction = value }
            }
            draggedAction = null
            releaseFraction = 0f
        }
        return order.takeIf { commit && changed }
    }

    private fun keepViewport() {
        // Key anchoring otherwise follows the former first row when it moves down, hiding its replacement.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
}
