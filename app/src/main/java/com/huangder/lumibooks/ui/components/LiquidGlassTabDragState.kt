package com.huangder.lumibooks.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Adapted and modified by Lumi contributors from AndroidLiquidGlass' LiquidBottomTabs sample.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Changes include Lumi-specific sizing, settling behavior, gesture integration, and state exposure.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */
internal class LiquidGlassTabDragState(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    private val onDragStopped: LiquidGlassTabDragState.() -> Unit,
    private val onDrag: LiquidGlassTabDragState.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val dragValueAnimationSpec = spring(1f, 1000f, 0.001f)
    private val settleValueAnimationSpec = spring(0.80f, 240f, 0.001f)
    private val velocityAnimationSpec = spring(0.5f, 300f, 0.01f)
    private val pressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, 0.001f)
    private val velocityAnimation = Animatable(0f, 0.01f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectLiquidTabDragGestures(
            onDragStart = {
                press()
                onDrag(size, Offset.Zero)
            },
            onDragEnd = { onDragStopped() },
            onDragCancel = { onDragStopped() },
            onDrag = { _, dragAmount -> onDrag(size, dragAmount) }
        )
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressAnimationSpec) }
            launch { scaleXAnimation.animateTo(78f / 56f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(78f / 56f, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, dragValueAnimationSpec) {
                updateVelocity()
            }
        }
    }

    fun animateToValue(value: Float, pressDuringAnimation: Boolean = false) {
        animationScope.launch {
            mutatorMutex.mutate {
                if (pressDuringAnimation) press()
                val target = value.coerceIn(valueRange)
                launch {
                    valueAnimation.animateTo(target, settleValueAnimationSpec) {
                        updateVelocity()
                    }
                }
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                releaseNear(target)
            }
        }
    }

    private suspend fun releaseNear(target: Float) {
        if (value != target) {
            val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
            snapshotFlow { valueAnimation.value }
                .filter { abs(it - target) < threshold }
                .first()
        }
        coroutineScope {
            launch { pressProgressAnimation.animateTo(0f, pressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            SystemClock.uptimeMillis(),
            Offset(value, 0f)
        )
        val range = valueRange.endInclusive - valueRange.start
        val targetVelocity = if (range == 0f) 0f else velocityTracker.calculateVelocity().x / range
        animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}

private suspend fun PointerInputScope.inspectLiquidTabDragGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        val down = awaitFirstDown(requireUnconsumed = false)

        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = dragUntilUp(initialDown.id) { change ->
            onDrag(change, change.positionChange())
        }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.dragUntilUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null

    var activePointer = pointerId
    while (true) {
        val change = awaitDragOrUp(activePointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        activePointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var activePointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == activePointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val replacement = event.changes.firstOrNull { it.pressed }
            if (replacement == null) return change
            activePointer = replacement.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
