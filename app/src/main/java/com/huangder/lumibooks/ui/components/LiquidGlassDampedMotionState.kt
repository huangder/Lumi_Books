package com.huangder.lumibooks.ui.components

/*
 * Adapted and modified from AndroidLiquidGlass' DampedDragAnimation sample.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Shared, interruptible motion state for Liquid Glass controls.
 *
 * Pointer-driven values are exposed synchronously for 1:1 tracking. Programmatic
 * motion uses a single press/move/release transaction so a tap cannot skip the
 * lift or release portions of the interaction.
 */
internal class LiquidGlassDampedMotionState(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val motionEnabled: Boolean,
    private val pressedScale: Float = 1.5f,
    private val visibilityThreshold: Float = 0.001f
) {
    private val valueAnimation = Animatable(initialValue.coerceIn(valueRange), visibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    private var transitionJob: Job? = null
    private var pressJob: Job? = null
    private var directValue by mutableFloatStateOf(initialValue.coerceIn(valueRange))
    private var requestedValue by mutableFloatStateOf(initialValue.coerceIn(valueRange))
    private var directInteraction by mutableStateOf(false)
    private var trackedVelocity by mutableFloatStateOf(0f)

    val value: Float get() = if (directInteraction) directValue else valueAnimation.value
    val targetValue: Float get() = requestedValue
    val progress: Float
        get() {
            val length = valueRange.endInclusive - valueRange.start
            return if (length == 0f) 0f else ((value - valueRange.start) / length).coerceIn(0f, 1f)
        }
    val pressProgress: Float get() = pressAnimation.value
    val scale: Float
        get() = if (motionEnabled) 1f + (pressedScale - 1f) * pressProgress else 1f
    val velocity: Float get() = trackedVelocity
    val isInteracting: Boolean get() = directInteraction

    fun beginInteraction() {
        transitionJob?.cancel()
        pressJob?.cancel()

        val start = value
        directValue = start
        requestedValue = start
        directInteraction = true
        trackedVelocity = 0f
        velocityTracker.resetTracking()
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(start, 0f))

        animationScope.launch {
            mutatorMutex.mutate(MutatePriority.UserInput) {
                valueAnimation.stop()
                valueAnimation.snapTo(start)
            }
        }
        pressJob = animationScope.launch {
            pressAnimation.animateTo(1f, pressInSpec())
        }
    }

    fun dragTo(value: Float) {
        val target = value.coerceIn(valueRange)
        directValue = target
        requestedValue = target
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(target, 0f))
        trackedVelocity = velocityTracker.calculateVelocity().x
    }

    fun settleTo(value: Float) {
        startTransition(
            value = value,
            ensurePressed = false,
            releaseAtEnd = false,
            animationSpec = dragSettleSpec()
        )
        startPressRelease()
    }

    fun animateToValue(value: Float) {
        startTransition(
            value = value,
            ensurePressed = true,
            releaseAtEnd = true,
            animationSpec = programmaticSpec()
        )
    }

    fun syncToValue(value: Float) {
        startTransition(
            value = value,
            ensurePressed = false,
            releaseAtEnd = false,
            animationSpec = programmaticSpec()
        )
    }

    fun cancelInteraction(value: Float = requestedValue) {
        startTransition(
            value = value,
            ensurePressed = false,
            releaseAtEnd = false,
            animationSpec = programmaticSpec()
        )
        startPressRelease()
    }

    private fun startTransition(
        value: Float,
        ensurePressed: Boolean,
        releaseAtEnd: Boolean,
        animationSpec: AnimationSpec<Float>
    ) {
        val target = value.coerceIn(valueRange)
        val start = this.value
        requestedValue = target
        trackedVelocity = 0f
        directInteraction = false
        transitionJob?.cancel()
        if (ensurePressed) pressJob?.cancel()

        transitionJob = animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.stop()
                valueAnimation.snapTo(start)
                coroutineScope {
                    val liftJob = if (ensurePressed) {
                        launch { pressAnimation.animateTo(1f, pressInSpec()) }
                    } else {
                        null
                    }
                    val moveJob = launch {
                        valueAnimation.animateTo(target, animationSpec)
                    }

                    if (releaseAtEnd) {
                        awaitNearTarget(target)
                        liftJob?.join()
                        startPressRelease()
                    }
                    moveJob.join()
                }
            }
        }
    }

    private suspend fun awaitNearTarget(target: Float) {
        val rangeLength = valueRange.endInclusive - valueRange.start
        val threshold = (rangeLength * 0.025f).coerceAtLeast(visibilityThreshold)
        if (abs(valueAnimation.value - target) <= threshold) return
        snapshotFlow { valueAnimation.value }
            .filter { abs(it - target) <= threshold }
            .first()
    }

    private fun programmaticSpec(): AnimationSpec<Float> =
        if (motionEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh,
                visibilityThreshold = visibilityThreshold
            )
        } else {
            tween(durationMillis = 100)
        }

    private fun dragSettleSpec(): AnimationSpec<Float> =
        if (motionEnabled) {
            spring(
                dampingRatio = 0.78f,
                stiffness = 420f,
                visibilityThreshold = visibilityThreshold
            )
        } else {
            tween(durationMillis = 100)
        }

    private fun pressInSpec(): AnimationSpec<Float> =
        if (motionEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh,
                visibilityThreshold = 0.001f
            )
        } else {
            tween(durationMillis = 80)
        }

    private fun pressOutSpec(): AnimationSpec<Float> =
        if (motionEnabled) tween(durationMillis = 180) else tween(durationMillis = 80)

    private fun startPressRelease() {
        pressJob?.cancel()
        pressJob = animationScope.launch {
            pressAnimation.animateTo(0f, pressOutSpec())
        }
    }
}
