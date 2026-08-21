package com.huangder.lumibooks.ui.components

/*
 * Gesture inspection is adapted and modified from AndroidLiquidGlass.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun Modifier.liquidGlassTabDrag(
    state: LiquidGlassDampedMotionState,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (dragged: Boolean) -> Unit,
    onDragCancel: () -> Unit
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.beginInteraction()
        onDragStart()

        var accumulatedDrag = Offset.Zero
        var dragged = false
        var released = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                released = true
                break
            }
            if (!change.pressed || change.isConsumed) break

            val delta = change.positionChange()
            if (delta == Offset.Zero) continue
            accumulatedDrag += delta
            if (!dragged && kotlin.math.abs(accumulatedDrag.x) >= 8.dp.toPx()) {
                dragged = true
                onDrag(accumulatedDrag)
                change.consume()
            } else if (dragged) {
                onDrag(delta)
                change.consume()
            }
        }

        if (released) onDragEnd(dragged) else onDragCancel()
    }
}
