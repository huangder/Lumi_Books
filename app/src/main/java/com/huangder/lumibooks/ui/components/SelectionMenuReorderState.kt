package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/**
 * Drag-to-reorder state for the selection menu editor.
 *
 * While a card is dragged the list shows [draftOrder]; it is rearranged as soon as the card's
 * centre enters a neighbour, so the lazy list's item animation lets the other cards make way.
 * The dragged card is translated to stay under the finger (see [translationOf]). Nothing is
 * committed until [finish], which hands the final order back to the caller.
 *
 * List items must be keyed by [SelectionMenuAction.storageKey] and contain nothing else.
 */
@Stable
internal class SelectionMenuReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    /** Distance from a list edge where auto-scroll starts. */
    private val edgeZonePx: Float,
    /** Auto-scroll distance per frame when the card reaches the very edge. */
    private val maxScrollPerFramePx: Float
) {
    var draggedAction by mutableStateOf<SelectionMenuAction?>(null)
        private set
    var draftOrder by mutableStateOf<List<SelectionMenuAction>?>(null)
        private set

    /** Viewport offset of the dragged card when the drag started. */
    private var startOffset = 0f
    private var draggedHeight = 0
    private var fingerTravel by mutableFloatStateOf(0f)
    private var autoScroll: Job? = null

    val isDragging: Boolean get() = draggedAction != null

    /** Returns false when the card is not laid out, e.g. after a fast fling. */
    fun start(action: SelectionMenuAction, order: List<SelectionMenuAction>): Boolean {
        if (isDragging) return false
        val item = itemOf(action) ?: return false
        startOffset = item.offset.toFloat()
        draggedHeight = item.size
        fingerTravel = 0f
        draftOrder = order
        draggedAction = action
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
    fun finish(): List<SelectionMenuAction>? {
        val result = draftOrder
        reset()
        return result
    }

    fun cancel() = reset()

    /** Layer translation for [action]; zero for every card except the dragged one. */
    fun translationOf(action: SelectionMenuAction): Float {
        if (action != draggedAction) return 0f
        val item = itemOf(action) ?: return 0f
        return startOffset + fingerTravel - item.offset
    }

    private fun reset() {
        autoScroll?.cancel()
        autoScroll = null
        draggedAction = null
        draftOrder = null
        fingerTravel = 0f
    }

    private fun itemOf(action: SelectionMenuAction): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == action.storageKey }

    /** Moves the dragged action into the slot of the card its centre currently overlaps. */
    private fun followFinger() {
        val action = draggedAction ?: return
        val order = draftOrder ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        // A fast pointer move can skip every visible row. Clamp to the visible end slot so
        // the dragged key follows the viewport instead of being disposed during auto-scroll.
        val centre = (startOffset + fingerTravel + draggedHeight / 2f).coerceIn(
            visible.first().offset.toFloat(),
            (visible.last().offset + visible.last().size - 1).toFloat()
        )
        val target = visible.firstOrNull { item ->
            centre >= item.offset && centre < item.offset + item.size
        } ?: return
        val targetAction = (target.key as? String)?.let(SelectionMenuAction::fromStorageKey) ?: return
        val from = order.indexOf(action)
        val to = order.indexOf(targetAction)
        if (from < 0 || to < 0 || from == to) return

        // LazyColumn keeps its first visible item anchored by key. Moving that item would
        // scroll the list along with it, so pin the current scroll position explicitly.
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        draftOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        if (anchorIndex == from || anchorIndex == to) {
            scope.launch { listState.scrollToItem(anchorIndex, anchorOffset) }
        }
    }

    private fun edgeScrollStep(): Float {
        if (!isDragging) return 0f
        val info = listState.layoutInfo
        val top = startOffset + fingerTravel
        val bottom = top + draggedHeight
        // Item offsets start after the leading content padding, so 0 is the first unobscured row.
        val visibleBottom = (info.viewportEndOffset - info.afterContentPadding).toFloat()
        return when {
            top < edgeZonePx && listState.canScrollBackward ->
                -maxScrollPerFramePx * ((edgeZonePx - top) / edgeZonePx).coerceIn(0f, 1f)
            bottom > visibleBottom - edgeZonePx && listState.canScrollForward ->
                maxScrollPerFramePx * ((bottom - visibleBottom + edgeZonePx) / edgeZonePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
