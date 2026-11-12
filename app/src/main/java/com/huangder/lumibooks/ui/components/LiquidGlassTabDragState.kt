package com.huangder.lumibooks.ui.components

/*
 * Adapted from AndroidLiquidGlass 1.0.6's DampedDragAnimation,
 * DragGestureInspector, and InteractiveHighlight samples.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class LiquidGlassTabMotionState(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val motionEnabled: Boolean
) {
    private val visibilityThreshold = 0.001f
    private val initialScale = 1f
    private val pressedScale = if (motionEnabled) 78f / 56f else initialScale

    private val valueAnimationSpec = if (motionEnabled) {
        spring(1f, 1000f, visibilityThreshold)
    } else {
        snap()
    }
    private val velocityAnimationSpec = if (motionEnabled) {
        spring(0.5f, 300f, visibilityThreshold * 10f)
    } else {
        snap()
    }
    private val pressProgressAnimationSpec = if (motionEnabled) {
        spring(1f, 1000f, 0.001f)
    } else {
        snap()
    }
    private val scaleXAnimationSpec = if (motionEnabled) {
        spring(0.6f, 250f, 0.001f)
    } else {
        snap()
    }
    private val scaleYAnimationSpec = if (motionEnabled) {
        spring(0.7f, 250f, 0.001f)
    } else {
        snap()
    }
    // Landing: the axes ring at different rates, so the prism wobbles like jelly as it shrinks
    // back onto the tab instead of deflating evenly.
    private val releaseScaleXAnimationSpec = if (motionEnabled) {
        spring(0.50f, 300f, 0.001f)
    } else {
        snap()
    }
    private val releaseScaleYAnimationSpec = if (motionEnabled) {
        spring(0.58f, 240f, 0.001f)
    } else {
        snap()
    }

    private val valueAnimation = Animatable(initialValue.coerceIn(valueRange), visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private var requestedValue by mutableFloatStateOf(initialValue.coerceIn(valueRange))
    private var pressed = false
    private var moveGeneration = 0
    private var moveJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var velocityJob: Job? = null
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    fun press() {
        // Every new interaction owns its lift and release. A delayed release from
        // the previous gesture must never flatten the next held prism.
        releaseJob?.cancel()
        pressJob?.cancel()
        moveGeneration++
        moveJob?.cancel()
        relaxVelocity()
        pressed = true
        requestedValue = value
        velocityTracker.resetTracking()
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        pressJob = animationScope.launch {
            coroutineScope {
                launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
            }
        }
    }

    fun release() {
        pressed = false
        // Always neutralize velocity, including a queued final drag update whose
        // animation had not started when release was requested.
        relaxVelocity()
        releaseJob?.cancel()
        val target = requestedValue
        releaseJob = animationScope.launch {
            awaitFrame()
            val threshold = ((valueRange.endInclusive - valueRange.start) * 0.025f)
                .coerceAtLeast(visibilityThreshold)
            snapshotFlow { valueAnimation.value }
                .filter { abs(it - target) <= threshold }
                .first()
            pressJob?.cancel()
            coroutineScope {
                launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(initialScale, releaseScaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, releaseScaleYAnimationSpec) }
            }
        }
    }

    fun updateValue(value: Float) {
        if (!pressed) return
        moveTo(value, trackVelocity = true)
    }

    fun animateToValue(value: Float) {
        press()
        moveTo(value, trackVelocity = false)
        release()
    }

    private fun moveTo(value: Float, trackVelocity: Boolean) {
        val target = value.coerceIn(valueRange)
        // Publish the requested destination before scheduling the animation, so
        // rapid pointer events accumulate against the latest request.
        requestedValue = target
        val generation = ++moveGeneration
        moveJob = animationScope.launch {
            if (generation != moveGeneration) return@launch
            // Let Animatable replace its own running mutation: cancelling it first
            // would discard the current velocity/frame time on every drag sample.
            valueAnimation.animateTo(target, valueAnimationSpec) {
                if (generation == moveGeneration && trackVelocity && pressed && motionEnabled) updateVelocity()
            }
            if (generation == moveGeneration && pressed) relaxVelocity()
        }
    }

    private fun relaxVelocity() {
        velocityJob?.cancel()
        velocityJob = animationScope.launch {
            velocityAnimation.animateTo(0f, velocityAnimationSpec)
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val rangeLength = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val targetVelocity = (velocityTracker.calculateVelocity().x / rangeLength)
            .let { if (it.isFinite()) it else 0f }
        velocityJob?.cancel()
        velocityJob = animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}

internal class LiquidGlassTabInteractiveHighlight(
    private val animationScope: CoroutineScope,
    motionEnabled: Boolean,
    private val position: (size: Size, offset: Offset) -> Offset
) {
    private val pressSpec = if (motionEnabled) spring(0.5f, 300f, 0.001f) else snap()
    private val positionSpec = if (motionEnabled) {
        spring(0.5f, 300f, Offset.VisibilityThreshold)
    } else {
        snap()
    }
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(
        Offset.Zero,
        Offset.VectorConverter,
        Offset.VisibilityThreshold
    )
    private var startPosition = Offset.Zero

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent()
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val highlightPosition = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(alpha = 0.15f * progress).toArgb())
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        highlightPosition.x.coerceIn(0f, size.width),
                        highlightPosition.y.coerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(alpha = 0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectTabDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressAnimation.animateTo(1f, pressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        animationScope.launch {
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { positionAnimation.animateTo(startPosition, positionSpec) }
        }
    }
}

internal fun Modifier.liquidGlassTabDrag(
    state: LiquidGlassTabMotionState,
    onDragStart: () -> Unit = {},
    onDrag: (size: IntSize, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
): Modifier = pointerInput(state) {
    try {
        inspectTabDragGestures(
            onDragStart = {
                onDragStart()
                state.press()
            },
            onDragEnd = {
                onDragEnd()
                state.release()
            },
            onDragCancel = {
                onDragCancel()
                state.release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    } finally {
        state.release()
    }
}

private suspend fun PointerInputScope.inspectTabDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = dragUntilUp(initialDown.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.dragUntilUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.firstOrNull { it.pressed }
            if (otherDown == null) return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
