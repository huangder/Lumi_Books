package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull

/** Callbacks of [longPressDragOrMenu]. Every callback runs on the main thread. */
internal class BookDragCallbacks(
    /**
     * Long-press threshold reached: lift the item (haptic) and let it grow over
     * [HOLD_TO_MENU_MILLIS] towards the size its menu opens at.
     */
    val onLift: () -> Unit,
    /** Starts the drag with the finger position before drag travel, in local pixels. */
    val onDragStart: (Offset) -> Boolean,
    /** Finger movement since the previous call, in pixels. */
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    /** Released or interrupted before a drag or the menu (or during a drag): settle back. */
    val onDragCancel: () -> Unit,
    /** Held still after the lift until fully grown: show the item's menu. */
    val onMenu: () -> Unit
)

/**
 * Long press that either reorders or opens a menu, decided by what the finger does next:
 *
 * - released or moved (scroll) before the long-press timeout: nothing happens here, so the
 *   item's own click and the list's scroll work as usual;
 * - long-press timeout: [BookDragCallbacks.onLift] (haptic; the item starts growing); the rest
 *   of the gesture belongs to this modifier (events are consumed in the initial pass, so
 *   neither the click nor the scroll container react);
 * - then moved past touch slop: drag ([BookDragCallbacks.onDragStart] / onDrag / onDragEnd);
 * - then held still for [HOLD_TO_MENU_MILLIS]: [BookDragCallbacks.onMenu], with the finger
 *   still down, like an app shortcut menu;
 * - then released before either: [BookDragCallbacks.onDragCancel], the item settles back.
 *
 * Place it before the item's `clickable` in the modifier chain; that clickable must not have a
 * long-click handler of its own.
 */
internal fun Modifier.longPressDragOrMenu(
    enabled: Boolean,
    key: Any?,
    callbacks: BookDragCallbacks
): Modifier = if (!enabled) this else composed {
    val latest by rememberUpdatedState(callbacks)
    // Forwards to the latest callbacks, so a gesture that spans recompositions uses fresh ones.
    val proxy = remember {
        BookDragCallbacks(
            onLift = { latest.onLift() },
            onDragStart = { latest.onDragStart(it) },
            onDrag = { latest.onDrag(it) },
            onDragEnd = { latest.onDragEnd() },
            onDragCancel = { latest.onDragCancel() },
            onMenu = { latest.onMenu() }
        )
    }
    longPressDragOrMenuAt(enabled = true, key = key) { proxy }
}

/**
 * [longPressDragOrMenu] for a container of several items: [resolve] picks the item under the
 * pressed point (local pixels) when the finger goes down, or returns null to leave the gesture
 * alone. Put it on a container that outlives its items, so a drag survives the item it started
 * on being recycled.
 */
internal fun Modifier.longPressDragOrMenuAt(
    enabled: Boolean,
    key: Any?,
    resolve: (Offset) -> BookDragCallbacks?
): Modifier = if (!enabled) this else composed {
    val latestResolve by rememberUpdatedState(resolve)
    pointerInput(key) {
        val slop = viewConfiguration.touchSlop
        val timeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointer = down.id
            val current = latestResolve(down.position) ?: return@awaitEachGesture

            // Phase 1: wait for the long-press timeout without taking anything from others.
            val aborted = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointer } ?: break
                    if (!change.pressed || change.isConsumed) break
                    if (event.changes.count { it.pressed } > 1) break
                    if ((change.position - down.position).getDistance() > slop) break
                }
                true
            }
            val reached = aborted == null
            if (!reached) return@awaitEachGesture

            // Phase 2: the gesture is ours. Holding still grows the item towards its menu;
            // moving first turns the hold into a drag.
            current.onLift()
            var travel = Offset.Zero
            var dragOrigin = down.position
            val decision = withTimeoutOrNull(HOLD_TO_MENU_MILLIS) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointer }
                    // Read the movement before consuming: positionChange() is zero once consumed.
                    val delta = change?.positionChangeIgnoreConsumed() ?: Offset.Zero
                    event.changes.forEach { it.consume() }
                    if (change == null || !change.pressed) return@withTimeoutOrNull HOLD_RELEASED
                    travel += delta
                    if (travel.getDistance() > slop) {
                        dragOrigin = change.position - travel
                        return@withTimeoutOrNull HOLD_MOVED
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                HOLD_RELEASED
            }
            when (decision) {
                null -> {
                    current.onMenu()
                    consumeUntilUp(pointer)
                    return@awaitEachGesture
                }
                HOLD_RELEASED -> {
                    current.onDragCancel()
                    return@awaitEachGesture
                }
            }
            if (!current.onDragStart(dragOrigin)) {
                current.onDragCancel()
                consumeUntilUp(pointer)
                return@awaitEachGesture
            }
            current.onDrag(travel)

            // Phase 3: dragging.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointer }
                val delta = change?.positionChangeIgnoreConsumed() ?: Offset.Zero
                event.changes.forEach { it.consume() }
                when {
                    change == null -> {
                        current.onDragCancel()
                        return@awaitEachGesture
                    }
                    !change.pressed -> {
                        current.onDragEnd()
                        return@awaitEachGesture
                    }
                    delta != Offset.Zero -> current.onDrag(delta)
                }
            }
        }
    }
}

/** After the long-press threshold, how long a still hold grows the item before its menu opens. */
internal const val HOLD_TO_MENU_MILLIS = 420L
private const val HOLD_RELEASED = 0
private const val HOLD_MOVED = 1

private suspend fun AwaitPointerEventScope.consumeUntilUp(pointer: PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull { it.id == pointer } ?: return
        if (!change.pressed) return
    }
}

/**
 * Accessibility stand-in for the long click that [longPressDragOrMenu] takes over: TalkBack's
 * long-press action still opens the menu.
 */
internal fun Modifier.longPressMenuSemantics(enabled: Boolean, onMenu: () -> Unit): Modifier =
    if (!enabled) this else semantics {
        onLongClick {
            onMenu()
            true
        }
    }

/**
 * Indices of the run of items around [index] that share its pinned state. Pinned books always
 * lead the shelf, so this is the range a book may be dragged within.
 */
internal fun <T> pinnedGroupRange(items: List<T>, index: Int, isPinned: (T) -> Boolean): IntRange {
    if (index !in items.indices) return IntRange.EMPTY
    val pinned = isPinned(items[index])
    var first = index
    while (first > 0 && isPinned(items[first - 1]) == pinned) first--
    var last = index
    while (last < items.lastIndex && isPinned(items[last + 1]) == pinned) last++
    return first..last
}
