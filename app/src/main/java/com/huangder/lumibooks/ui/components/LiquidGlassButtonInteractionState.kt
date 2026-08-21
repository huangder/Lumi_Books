package com.huangder.lumibooks.ui.components

/*
 * Button interaction is adapted from AndroidLiquidGlass' InteractiveHighlight.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

@Stable
internal class LiquidGlassButtonInteractionState(
    private val animationScope: CoroutineScope
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(
        Offset.Zero,
        Offset.VectorConverter,
        Offset.VisibilityThreshold
    )

    private var pressJob: Job? = null
    private var positionJob: Job? = null
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value
    val position: Offset get() = positionAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    fun press(position: Offset) {
        startPosition = position
        pressJob?.cancel()
        positionJob?.cancel()
        pressJob = animationScope.launch {
            pressProgressAnimation.animateTo(1f, ButtonInteractionSpring)
        }
        positionJob = animationScope.launch {
            positionAnimation.snapTo(position)
        }
    }

    fun dragTo(position: Offset) {
        positionJob?.cancel()
        positionJob = animationScope.launch {
            positionAnimation.snapTo(position)
        }
    }

    fun release() {
        pressJob?.cancel()
        positionJob?.cancel()
        pressJob = animationScope.launch {
            pressProgressAnimation.animateTo(0f, ButtonInteractionSpring)
        }
        positionJob = animationScope.launch {
            positionAnimation.animateTo(startPosition, ButtonPositionSpring)
        }
    }
}

private val ButtonInteractionSpring = spring(
    dampingRatio = 0.5f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)

private val ButtonPositionSpring = spring(
    dampingRatio = 0.5f,
    stiffness = 300f,
    visibilityThreshold = Offset.VisibilityThreshold
)

internal fun Modifier.liquidGlassButtonGesture(
    state: LiquidGlassButtonInteractionState,
    enabled: Boolean
): Modifier = if (enabled) {
    pointerInput(state) {
        try {
            inspectLiquidGlassButtonDrag(
                onDragStart = { state.press(it.position) },
                onDragEnd = { state.release() },
                onDragCancel = { state.release() },
                onDrag = { change -> state.dragTo(change.position) }
            )
        } finally {
            state.release()
        }
    }
} else {
    this
}

private suspend fun PointerInputScope.inspectLiquidGlassButtonDrag(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        val down = awaitFirstDown(requireUnconsumed = false)

        onDragStart(down)
        onDrag(initialDown)
        val up = inspectDragOrUp(initialDown.id, onDrag)
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.inspectDragOrUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null

    var activePointer = pointerId
    while (true) {
        val change = awaitLiquidGlassDragOrUp(activePointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) {
            val replacement = currentEvent.changes.firstOrNull { it.pressed }
            if (replacement == null) return change
            activePointer = replacement.id
        } else {
            onDrag(change)
            activePointer = change.id
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitLiquidGlassDragOrUp(
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
        } else if (change.positionChange() != Offset.Zero) {
            return change
        }
    }
}

internal data class LiquidGlassButtonLayerTransform(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float
)

private const val ButtonDragLimitFraction = 0.75f
internal const val LiquidGlassButtonContentFollow = 0.55f

internal fun liquidGlassButtonLayerTransform(
    width: Float,
    height: Float,
    pressProgress: Float,
    dragOffset: Offset,
    expansionPx: Float,
    motionEnabled: Boolean
): LiquidGlassButtonLayerTransform {
    if (!motionEnabled || width <= 0f || height <= 0f) {
        return LiquidGlassButtonLayerTransform(0f, 0f, 1f, 1f)
    }

    val progress = pressProgress.coerceIn(0f, 1f)
    val dragDistance = dragOffset.getDistance()
    val maxDragDistance = min(width, height) * ButtonDragLimitFraction
    val limitedDragOffset = if (dragDistance > maxDragDistance && dragDistance > 0f) {
        dragOffset * (maxDragDistance / dragDistance)
    } else {
        dragOffset
    }
    val baseScale = 1f + expansionPx / height * progress
    val maxOffset = min(width, height).coerceAtLeast(0.001f)
    val maxDimension = max(width, height).coerceAtLeast(0.001f)
    val offsetAngle = atan2(limitedDragOffset.y, limitedDragOffset.x)
    val maxDragScale = expansionPx / height

    return LiquidGlassButtonLayerTransform(
        translationX = dampedLiquidGlassButtonOffset(limitedDragOffset.x, maxOffset),
        translationY = dampedLiquidGlassButtonOffset(limitedDragOffset.y, maxOffset),
        scaleX = baseScale +
            maxDragScale * abs(cos(offsetAngle) * limitedDragOffset.x / maxDimension) *
            min(width / height, 1f),
        scaleY = baseScale +
            maxDragScale * abs(sin(offsetAngle) * limitedDragOffset.y / maxDimension) *
            min(height / width, 1f)
    )
}

internal fun liquidGlassButtonContentTransform(
    surfaceTransform: LiquidGlassButtonLayerTransform
): LiquidGlassButtonLayerTransform = LiquidGlassButtonLayerTransform(
    translationX = surfaceTransform.translationX * LiquidGlassButtonContentFollow,
    translationY = surfaceTransform.translationY * LiquidGlassButtonContentFollow,
    scaleX = 1f + (surfaceTransform.scaleX - 1f) * LiquidGlassButtonContentFollow,
    scaleY = 1f + (surfaceTransform.scaleY - 1f) * LiquidGlassButtonContentFollow
)

internal fun dampedLiquidGlassButtonOffset(offset: Float, maxOffset: Float): Float {
    if (maxOffset <= 0f) return 0f
    return maxOffset * tanh(0.05f * offset / maxOffset)
}
