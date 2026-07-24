package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A pill slider with continuous drag feedback and release-only value snapping.
 * The liquid-glass active segment becomes a dark refractive prism while held.
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
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnDragValueChange by rememberUpdatedState(onDragValueChange)
    val animatedValue = remember { Animatable(value.coerceIn(valueRange)) }
    val animationScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var directValue by remember { mutableFloatStateOf(value.coerceIn(valueRange)) }
    var gestureActive by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var showingDirectValue by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (gestureActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "pillSliderPress"
    )

    LaunchedEffect(value, valueRange, gestureActive, settling) {
        if (!gestureActive && !settling) {
            val externalValue = value.coerceIn(valueRange)
            if (abs(animatedValue.value - externalValue) > 0.0001f) {
                animatedValue.snapTo(externalValue)
            }
        }
    }

    val rangeLength = valueRange.endInclusive - valueRange.start
    val visualFraction = if (rangeLength == 0f) {
        0f
    } else {
        val visualValue = if (showingDirectValue) directValue else animatedValue.value
        ((visualValue - valueRange.start) / rangeLength).coerceIn(0f, 1f)
    }
    val sliderHeight = if (isLiquidGlass) trackHeight + 10.dp else trackHeight

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(sliderHeight)
            .pointerInput(valueRange, step) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val widthPx = size.width.toFloat()
                    if (widthPx <= 0f || rangeLength <= 0f) return@awaitEachGesture

                    settleJob?.cancel()
                    settling = false
                    directValue = animatedValue.value.coerceIn(valueRange)
                    showingDirectValue = true
                    gestureActive = true
                    val startX = down.position.x
                    val startY = down.position.y
                    val startValue = directValue
                    val touchSlop = viewConfiguration.touchSlop
                    var dragged = false
                    var released = false
                    var cancelledByScroll = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            released = true
                            break
                        }
                        if (!change.pressed) break

                        val totalDx = change.position.x - startX
                        val totalDy = change.position.y - startY
                        if (!dragged) {
                            if (change.isConsumed) {
                                cancelledByScroll = true
                                break
                            }
                            if (abs(totalDy) >= touchSlop && abs(totalDy) > abs(totalDx)) {
                                cancelledByScroll = true
                                break
                            }
                            if (abs(totalDx) < touchSlop || abs(totalDx) < abs(totalDy)) {
                                continue
                            }
                            dragged = true
                        }

                        val continuousValue = (startValue + totalDx / widthPx * rangeLength)
                            .coerceIn(valueRange)
                        directValue = continuousValue
                        latestOnDragValueChange?.invoke(continuousValue)
                        change.consume()
                    }

                    gestureActive = false
                    val releaseValue = when {
                        cancelledByScroll -> latestValue.coerceIn(valueRange)
                        dragged -> directValue
                        released -> {
                            val tapFraction = (startX / widthPx).coerceIn(0f, 1f)
                            valueRange.start + tapFraction * rangeLength
                        }
                        else -> latestValue.coerceIn(valueRange)
                    }
                    directValue = releaseValue
                    val targetValue = if (released) {
                        releaseValue.snapToStep(valueRange, step)
                    } else {
                        releaseValue
                    }
                    if (released) {
                        latestOnDragValueChange?.invoke(targetValue)
                    }
                    settling = true
                    settleJob = animationScope.launch {
                        animatedValue.snapTo(releaseValue)
                        showingDirectValue = false
                        animatedValue.animateTo(
                            targetValue = targetValue,
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f)
                        )
                        if (released) latestOnValueChange(targetValue)
                        settling = false
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackShape = RoundedCornerShape(trackHeight / 2)

        if (isLiquidGlass) {
            val idleTrackColor = if (isDark) Color.Black else inactiveColor
            val idleActiveColor = if (isDark) Color.White else Color.Black
            val trackScrim = if (isDark) {
                Color.Black.copy(alpha = 0.30f)
            } else {
                Color.Black.copy(alpha = 0.10f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .then(
                        if (backdrop != null) {
                            Modifier.liquidGlassBackdrop(
                                backdrop = backdrop,
                                shape = trackShape,
                                isDark = isDark,
                                transparency = (transparency + 0.12f).coerceAtMost(1f),
                                contentScrimColor = trackScrim,
                                scaleOnPress = false,
                                outlineWidth = 0.8.dp,
                                highlightAlpha = 0.20f
                            )
                        } else {
                            Modifier
                                .clip(trackShape)
                                .background(idleTrackColor.copy(alpha = 0.72f))
                        }
                    )
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(trackShape)
            ) {
                val radius = size.height / 2f
                val activeWidthPx = size.width * visualFraction
                if (activeWidthPx > 0f) {
                    clipRect(right = activeWidthPx) {
                        drawRoundRect(
                            color = idleActiveColor.copy(
                                alpha = (1f - pressProgress).coerceIn(0f, 1f)
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                            size = size
                        )
                    }
                }
            }

            val activeWidth = maxWidth * visualFraction
            if (visualFraction > 0.001f && pressProgress > 0.001f) {
                val pressedScrim = if (isDark) {
                    Color.White.copy(alpha = 0.48f)
                } else {
                    Color.Black.copy(alpha = 0.56f)
                }
                val activeShape = RoundedCornerShape(
                    topStart = trackHeight / 2,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                    bottomStart = trackHeight / 2
                )
                Box(
                    modifier = Modifier
                        .width(activeWidth)
                        .height(trackHeight)
                        .graphicsLayer {
                            scaleX = 1f + pressProgress * 0.05f
                            scaleY = 1f + pressProgress * 0.16f
                            alpha = pressProgress
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .then(
                            if (backdrop != null) {
                                Modifier.liquidGlassBackdrop(
                                    backdrop = backdrop,
                                    shape = activeShape,
                                    isDark = isDark,
                                    transparency = (transparency - 0.08f).coerceAtLeast(0f),
                                    contentScrimColor = pressedScrim,
                                    pressProgress = pressProgress,
                                    scaleOnPress = false,
                                    outlineWidth = 1.dp,
                                    highlightAlpha = 0.30f
                                )
                            } else {
                                Modifier
                                    .clip(activeShape)
                                    .background(pressedScrim)
                            }
                        )
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(trackShape)
            ) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = inactiveColor,
                    cornerRadius = CornerRadius(radius, radius),
                    size = Size(size.width, size.height)
                )
                val activeWidthPx = size.width * visualFraction
                if (activeWidthPx > 0f) {
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(0f, 0f, activeWidthPx, size.height)
                    drawRoundRect(
                        color = activeColor,
                        cornerRadius = CornerRadius(radius, radius),
                        size = Size(size.width, size.height)
                    )
                    drawContext.canvas.restore()
                }
            }
        }
    }
}

private fun Float.snapToStep(
    range: ClosedFloatingPointRange<Float>,
    step: Float
): Float {
    if (step <= 0f) return coerceIn(range)
    val snapped = range.start + ((this - range.start) / step).roundToInt() * step
    return ((snapped * 10_000f).roundToInt() / 10_000f).coerceIn(range)
}

private fun Float.coerceIn(range: ClosedFloatingPointRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
