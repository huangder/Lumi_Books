package com.huangder.lumibooks.ui.components

/*
 * Button interaction is adapted from AndroidLiquidGlass' InteractiveHighlight.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import kotlin.math.min
import kotlin.math.tanh

@Stable
internal class LiquidGlassButtonInteractionState(
    private val animationScope: CoroutineScope
) {
    // Thresholds are chosen so a spring stops once its motion is below a fraction of a pixel,
    // instead of re-rendering the glass for an invisible tail.
    private val pressProgressAnimation = Animatable(0f, 0.004f)
    // Volume-preserving jelly: positive widens and flattens the glass, negative narrows and
    // lengthens it. Press and release kick it, and a soft spring lets it ring out, so the
    // surface keeps quivering briefly after the finger has left.
    private val wobbleAnimation = Animatable(0f, 0.0015f)
    private var wobbleJob: Job? = null
    private var wobbleVertical = false
    private val positionAnimation = Animatable(
        Offset.Zero,
        Offset.VectorConverter,
        Offset.VisibilityThreshold
    )

    private var pressJob: Job? = null
    private var positionJob: Job? = null
    private var startPosition = Offset.Zero
    private var fingerPosition by mutableStateOf(Offset.Zero)
    private var fingerOrigin = Offset.Zero
    private var carriedOffset = Offset.Zero
    private val velocityTracker = VelocityTracker()
    private var pressed = false
    private var pressVelocity = 0f

    /** Clamped press amount for alpha, highlight and optics consumers. */
    val pressProgress: Float get() = pressProgressAnimation.value.coerceIn(0f, 1f)
    /** Unclamped spring value: swells above 1 on press and dips below 0 on release. */
    val scaleProgress: Float get() = pressProgressAnimation.value
    val wobble: Float get() = wobbleAnimation.value
    val wobbleIsVertical: Boolean get() = wobbleVertical
    val position: Offset get() = fingerPosition
    val offset: Offset get() = positionAnimation.value - startPosition

    fun press(position: Offset, timeMillis: Long = 0L) {
        // Rebase the finger without snapping an interrupted return to its new origin.
        carriedOffset = offset
        startPosition = positionAnimation.value - carriedOffset
        fingerOrigin = position
        fingerPosition = position
        pressed = true
        velocityTracker.resetTracking()
        velocityTracker.addPosition(timeMillis, position)
        pressJob?.cancel()
        positionJob?.cancel()
        val initialVelocity = pressVelocity.coerceIn(-12f, 12f)
        pressJob = animationScope.launch {
            pressProgressAnimation.animateTo(1f, buttonPressSpring(), initialVelocity) {
                pressVelocity = velocity
            }
            pressVelocity = 0f
        }
        // Touching liquid spreads it sideways for a moment before it lifts.
        kickWobble(LiquidGlassPressWobbleKick, vertical = false)
    }

    fun dragTo(position: Offset, timeMillis: Long = 0L) {
        fingerPosition = position
        velocityTracker.addPosition(timeMillis, position)
        positionJob?.cancel()
        positionJob = animationScope.launch {
            positionAnimation.snapTo(startPosition + carriedOffset + position - fingerOrigin)
        }
    }

    fun release(cancelled: Boolean = false) {
        if (!pressed) return
        pressed = false
        val tracked = velocityTracker.calculateVelocity()
        val initialVelocity = if (cancelled) Offset.Zero else
            boundedButtonVelocity(Offset(tracked.x, tracked.y))
        pressJob?.cancel()
        positionJob?.cancel()
        val initialPressVelocity = pressVelocity.coerceIn(-12f, 12f)
        // Read the pull before the return animation starts moving it.
        val pull = offset
        pressJob = animationScope.launch {
            pressProgressAnimation.animateTo(0f, buttonReleaseSpring(), initialPressVelocity) {
                pressVelocity = velocity
            }
            pressVelocity = 0f
        }
        positionJob = animationScope.launch {
            positionAnimation.animateTo(startPosition, buttonPositionSpring(), initialVelocity)
        }
        // The droplet snaps back along the axis it was pulled, then rings out.
        val vertical = abs(pull.y) > abs(pull.x)
        val pullBoost = if (cancelled) 0f else (pull.getDistance() / 24f).coerceAtMost(1f)
        kickWobble(-LiquidGlassReleaseWobbleKick * (1f + pullBoost), vertical)
    }

    private fun kickWobble(velocity: Float, vertical: Boolean) {
        // Keep the current axis while the surface is still visibly ringing.
        if (abs(wobbleAnimation.value) < 0.004f) wobbleVertical = vertical
        val combined = (wobbleAnimation.velocity + velocity).coerceIn(-3f, 3f)
        wobbleJob?.cancel()
        wobbleJob = animationScope.launch {
            wobbleAnimation.animateTo(0f, buttonWobbleSpring(), combined)
        }
    }

    fun reset() {
        pressed = false
        pressJob?.cancel()
        positionJob?.cancel()
        pressVelocity = 0f
        wobbleJob?.cancel()
        wobbleJob = animationScope.launch { wobbleAnimation.snapTo(0f) }
        pressJob = animationScope.launch { pressProgressAnimation.snapTo(0f) }
        positionJob = animationScope.launch { positionAnimation.snapTo(startPosition) }
    }

}

// Lift: quick, with a barely perceptible swell past full size.
private fun buttonPressSpring() = spring(
    dampingRatio = 0.64f,
    stiffness = 600f,
    visibilityThreshold = 0.004f
)

// Return: softly under-damped, so the glass dips just below rest size once, then settles.
private fun buttonReleaseSpring() = spring(
    dampingRatio = 0.55f,
    stiffness = 380f,
    visibilityThreshold = 0.004f
)

// Elastic snap-back after a drag, slightly overshooting the origin once.
private fun buttonPositionSpring() = spring(
    dampingRatio = 0.58f,
    stiffness = 420f,
    visibilityThreshold = Offset.VisibilityThreshold
)

// Restrained jelly: one clear quiver and a faint echo, felt more than seen.
private fun buttonWobbleSpring() = spring(
    dampingRatio = 0.38f,
    stiffness = 340f,
    visibilityThreshold = 0.0015f
)

internal const val LiquidGlassPressWobbleKick = 0.45f
internal const val LiquidGlassReleaseWobbleKick = 0.75f
internal const val LiquidGlassMaxWobble = 0.035f

internal fun Modifier.liquidGlassButtonGesture(
    state: LiquidGlassButtonInteractionState,
    enabled: Boolean
): Modifier = if (enabled) {
    pointerInput(state) {
        try {
            inspectLiquidGlassButtonDrag(
                onDragStart = { state.press(it.position, it.uptimeMillis) },
                onDragEnd = { state.dragTo(it.position, it.uptimeMillis); state.release() },
                onDragCancel = { state.release(cancelled = true) },
                onDrag = { change -> state.dragTo(change.position, change.uptimeMillis) }
            )
        } finally {
            state.reset()
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

internal const val LiquidGlassButtonContentFollow = 0.55f

internal fun liquidGlassButtonLayerTransform(
    width: Float,
    height: Float,
    pressProgress: Float,
    dragOffset: Offset,
    expansionPx: Float,
    motionEnabled: Boolean,
    maxTranslationPx: Float = expansionPx * 1.5f,
    wobble: Float = 0f,
    wobbleVertical: Boolean = false
): LiquidGlassButtonLayerTransform {
    if (!motionEnabled || width <= 0f || height <= 0f) {
        return LiquidGlassButtonLayerTransform(0f, 0f, 1f, 1f)
    }

    // Let the spring overshoot through: a swell past 1 on press, a dip below 0 on release.
    val progress = pressProgress.coerceIn(-0.35f, 1.2f)
    val shortSide = min(width, height)
    val distance = dragOffset.getDistance()
    val direction = if (distance > 0f) dragOffset / distance else Offset.Zero
    // A radial bound keeps diagonal drags within the same limit as axial drags.
    val amount = tanh(distance / (shortSide * 0.55f))
    val translation = min(shortSide * 0.10f, maxTranslationPx.coerceAtLeast(0f)) * amount
    val xx = direction.x * direction.x
    val yy = direction.y * direction.y
    val baseScale = 1f + 0.16f * progress
    // Jelly deformation roughly preserves area: one axis grows while the other shrinks.
    val jelly = wobble.coerceIn(-LiquidGlassMaxWobble, LiquidGlassMaxWobble)
    val jellyX = if (wobbleVertical) -jelly * 0.8f else jelly
    val jellyY = if (wobbleVertical) jelly else -jelly * 0.8f
    return LiquidGlassButtonLayerTransform(
        translationX = direction.x * translation,
        translationY = direction.y * translation,
        scaleX = baseScale + amount * (0.06f * xx - 0.03f * yy) + jellyX,
        scaleY = baseScale + amount * (0.06f * yy - 0.03f * xx) + jellyY
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

internal fun boundedButtonVelocity(velocity: Offset, limit: Float = 2400f): Offset {
    val speed = velocity.getDistance()
    return if (!speed.isFinite()) Offset.Zero else if (speed > limit) velocity * (limit / speed) else velocity
}
