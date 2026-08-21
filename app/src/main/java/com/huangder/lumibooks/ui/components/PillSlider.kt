package com.huangder.lumibooks.ui.components

/*
 * Glass rendering and motion are adapted from AndroidLiquidGlass' LiquidSlider sample.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val LiquidSliderThumbWidth = 40.dp
private val LiquidSliderThumbHeight = 24.dp
private val LiquidSliderVerticalPadding = 5.dp
private val LiquidSliderTrackHeight = 6.dp
private const val SliderCommitStabilityMillis = 240L

/**
 * A thick pill slider with continuous drag preview and release-only commits.
 * The track is one cheap captured Canvas; only the fixed thumb renders glass.
 */
@Composable
fun PillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 0.1f,
    trackHeight: Dp = 28.dp,
    activeColor: Color = AppColors.ControlActive,
    inactiveColor: Color = AppColors.BgGray,
    onDragValueChange: ((Float) -> Unit)? = null
) {
    val rangeLength = valueRange.endInclusive - valueRange.start
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val motionEnabled = LocalMotionEnabled.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnDragValueChange by rememberUpdatedState(onDragValueChange)
    var pendingCommittedValue by remember { mutableStateOf<Float?>(null) }
    val motionState = remember(
        animationScope,
        motionEnabled,
        valueRange.start,
        valueRange.endInclusive
    ) {
        LiquidGlassDampedMotionState(
            animationScope = animationScope,
            initialValue = value.coerceIn(valueRange),
            valueRange = valueRange,
            motionEnabled = motionEnabled,
            pressedScale = 1.5f,
            visibilityThreshold = (abs(rangeLength) * 0.0001f).coerceAtLeast(0.0001f)
        )
    }

    LaunchedEffect(value, motionState, pendingCommittedValue) {
        val externalValue = value.coerceIn(valueRange)
        val pendingValue = pendingCommittedValue
        if (pendingValue != null) {
            if (abs(externalValue - pendingValue) <= 0.0001f) {
                // A preview callback can finish after the final commit and briefly
                // publish an older value. Only reopen external synchronization once
                // the committed value has remained stable long enough for those
                // queued preview updates to drain.
                delay(SliderCommitStabilityMillis)
                if (
                    pendingCommittedValue == pendingValue &&
                    abs(value.coerceIn(valueRange) - pendingValue) <= 0.0001f &&
                    !motionState.isInteracting
                ) {
                    pendingCommittedValue = null
                }
            }
        } else if (!motionState.isInteracting && abs(motionState.targetValue - externalValue) > 0.0001f) {
            motionState.syncToValue(externalValue)
        }
    }

    val semanticsModifier = Modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = value.coerceIn(valueRange),
            range = valueRange,
            steps = sliderSemanticsSteps(valueRange, step)
        )
        setProgress { requestedValue ->
            val target = snapSliderValue(requestedValue, valueRange, step)
            pendingCommittedValue = target
            motionState.animateToValue(target)
            latestOnDragValueChange?.invoke(target)
            if (abs(target - latestValue) > 0.0001f) latestOnValueChange(target)
            true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isLiquidGlass) LiquidSliderThumbHeight + LiquidSliderVerticalPadding * 2 else trackHeight)
            .then(semanticsModifier)
            .pointerInput(valueRange.start, valueRange.endInclusive, step, isLtr, motionState) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val widthPx = size.width.toFloat()
                    if (widthPx <= 0f || rangeLength <= 0f) return@awaitEachGesture

                    motionState.beginInteraction()
                    val startValue = motionState.value
                    val startX = down.position.x
                    val startY = down.position.y
                    var dragged = false
                    var released = false
                    var cancelledByScroll = false

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            released = true
                            break
                        }
                        if (!change.pressed) break

                        val positionDx = change.position.x - startX
                        val positionDy = change.position.y - startY
                        if (!dragged) {
                            if (change.isConsumed) {
                                cancelledByScroll = true
                                break
                            }
                            if (abs(positionDy) >= viewConfiguration.touchSlop && abs(positionDy) > abs(positionDx)) {
                                cancelledByScroll = true
                                break
                            }
                            if (abs(positionDx) < viewConfiguration.touchSlop || abs(positionDx) < abs(positionDy)) {
                                continue
                            }
                            dragged = true
                        }

                        val direction = if (isLtr) 1f else -1f
                        val directValue = (startValue + direction * positionDx / widthPx * rangeLength)
                            .coerceIn(valueRange)
                        motionState.dragTo(directValue)
                        latestOnDragValueChange?.invoke(directValue)
                        change.consume()
                    }

                    when {
                        cancelledByScroll || !released -> {
                            motionState.cancelInteraction(latestValue.coerceIn(valueRange))
                        }
                        dragged -> {
                            val target = snapSliderValue(motionState.targetValue, valueRange, step)
                            pendingCommittedValue = target
                            motionState.settleTo(target)
                            if (abs(target - latestValue) > 0.0001f) latestOnValueChange(target)
                        }
                        else -> {
                            val fraction = (startX / widthPx).coerceIn(0f, 1f)
                            val target = snapSliderValue(
                                sliderValueFromFraction(fraction, valueRange, isLtr),
                                valueRange,
                                step
                            )
                            pendingCommittedValue = target
                            motionState.animateToValue(target)
                            if (abs(target - latestValue) > 0.0001f) latestOnValueChange(target)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val visualFraction = sliderFraction(motionState.value, valueRange, isLtr)
        if (!isLiquidGlass) {
            Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight)) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = inactiveColor,
                    cornerRadius = CornerRadius(radius, radius),
                    size = size
                )
                val activeWidth = size.width * visualFraction
                if (activeWidth > 0f) {
                    clipRect(right = activeWidth) {
                        drawRoundRect(
                            color = activeColor,
                            cornerRadius = CornerRadius(radius, radius),
                            size = size
                        )
                    }
                }
            }
            return@BoxWithConstraints
        }

        val glassActiveColor = AppColors.Accent
        val trackBackdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LiquidSliderTrackHeight)
                .layerBackdrop(trackBackdrop)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val radius = size.height / 2f
                val glassTrackColor = if (isDark) {
                    Color(0xFF787880).copy(alpha = 0.36f)
                } else {
                    Color(0xFF787878).copy(alpha = 0.20f)
                }
                drawRoundRect(
                    color = glassTrackColor,
                    cornerRadius = CornerRadius(radius, radius),
                    size = Size(size.width, size.height)
                )
                val activeWidth = size.width * visualFraction
                if (activeWidth > 0f) {
                    clipRect(right = activeWidth) {
                        drawRoundRect(
                            color = glassActiveColor,
                            cornerRadius = CornerRadius(radius, radius),
                            size = Size(size.width, size.height)
                        )
                    }
                }
            }
        }

        val pressProgress = motionState.pressProgress
        val sampledTrackBackdrop = if (backdrop != null) {
            rememberBackdrop(trackBackdrop) { drawTrackBackdrop ->
                val scaleX = 2f / 3f + pressProgress / 12f
                val scaleY = pressProgress * 0.75f
                scale(scaleX, scaleY) { drawTrackBackdrop() }
            }
        } else {
            null
        }
        val thumbBackdrop = if (backdrop != null && sampledTrackBackdrop != null) {
            rememberCombinedBackdrop(backdrop, sampledTrackBackdrop)
        } else {
            null
        }

        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbWidthPx = with(density) { LiquidSliderThumbWidth.toPx() }
        val thumbX = sliderThumbOffsetPx(visualFraction, trackWidthPx, thumbWidthPx)
        val thumbPosition = Modifier
            .size(width = LiquidSliderThumbWidth, height = LiquidSliderThumbHeight)
            .offset { IntOffset(thumbX.roundToInt(), 0) }
        val idleThumbAlpha = if (isDark) 0.34f else 0.68f
        val thumbScrim = Color.White.copy(
            alpha = idleThumbAlpha + (0.10f - idleThumbAlpha) * pressProgress
        )
        val thumbVisual = if (thumbBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = thumbBackdrop,
                shape = { CircleShape },
                effects = {
                    val opticalProgress = if (motionEnabled) pressProgress else 0f
                    val blurRadius = 8.dp.toPx() * (1f - transparency) * (1f - opticalProgress)
                    if (blurRadius > 0f) blur(blurRadius)
                    lens(
                        10.dp.toPx() * opticalProgress,
                        14.dp.toPx() * opticalProgress,
                        chromaticAberration = opticalProgress > 0.05f
                    )
                },
                highlight = { Highlight.Ambient.copy(alpha = pressProgress) },
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                innerShadow = {
                    InnerShadow(radius = 4.dp * pressProgress, alpha = pressProgress)
                },
                layerBlock = {
                    scaleX = motionState.scale
                    scaleY = motionState.scale
                    if (motionEnabled) {
                        val velocity = motionState.velocity / rangeLength.coerceAtLeast(0.0001f) / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }
                },
                onDrawSurface = { drawRect(thumbScrim) }
            )
        } else {
            Modifier.clip(CircleShape).background(thumbScrim)
        }
        Box(modifier = thumbPosition.then(thumbVisual))
    }
}

internal fun snapSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Float {
    if (step <= 0f) return value.coerceIn(range)
    val snapped = range.start + ((value - range.start) / step).roundToInt() * step
    return ((snapped * 10_000f).roundToInt() / 10_000f).coerceIn(range)
}

internal fun sliderValueFromFraction(
    fraction: Float,
    range: ClosedFloatingPointRange<Float>,
    isLtr: Boolean
): Float {
    val directedFraction = if (isLtr) fraction else 1f - fraction
    return range.start + directedFraction.coerceIn(0f, 1f) * (range.endInclusive - range.start)
}

internal fun sliderFraction(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isLtr: Boolean
): Float {
    val length = range.endInclusive - range.start
    val fraction = if (length == 0f) 0f else ((value - range.start) / length).coerceIn(0f, 1f)
    return if (isLtr) fraction else 1f - fraction
}

internal fun sliderThumbOffsetPx(fraction: Float, trackWidthPx: Float, thumbWidthPx: Float): Float {
    val minimum = -thumbWidthPx / 4f
    val maximum = trackWidthPx - thumbWidthPx * 3f / 4f
    return (trackWidthPx * fraction - thumbWidthPx / 2f).coerceIn(minimum, maximum)
}

private fun sliderSemanticsSteps(
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Int {
    if (step <= 0f) return 0
    val intervals = ((range.endInclusive - range.start) / step).roundToInt()
    return (intervals - 1).coerceAtLeast(0)
}

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
