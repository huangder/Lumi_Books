package com.huangder.lumibooks.ui.components

/*
 * Liquid Glass rendering and motion are adapted and modified from
 * AndroidLiquidGlass' LiquidToggle sample.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val LiquidSwitchWidth = 58.dp
private val LiquidSwitchHeight = 30.dp
private val LiquidSwitchThumbWidth = 32.dp
private val LiquidSwitchThumbHeight = 24.dp
private val LiquidSwitchThumbInset = 3.dp
private val LiquidSwitchThumbTravel = 20.dp

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val eInkMode = LocalEInkMode.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    if (eInkMode) {
        EInkSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    if (!isLiquidGlass) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.OnAccent,
                checkedTrackColor = AppColors.Accent,
                checkedBorderColor = AppColors.Accent,
                uncheckedThumbColor = AppColors.TextSecondary,
                uncheckedTrackColor = AppColors.BgGray,
                uncheckedBorderColor = AppColors.Divider
            )
        )
        return
    }

    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val motionEnabled = LocalMotionEnabled.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val thumbInsetPx = with(density) { LiquidSwitchThumbInset.toPx() }
    val travelPx = with(density) { LiquidSwitchThumbTravel.toPx() }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val motionState = remember(animationScope, motionEnabled) {
        LiquidGlassDampedMotionState(
            animationScope = animationScope,
            initialValue = if (checked) 1f else 0f,
            valueRange = 0f..1f,
            motionEnabled = motionEnabled,
            pressedScale = 1.5f
        )
    }

    LaunchedEffect(checked, motionState) {
        val target = if (checked) 1f else 0f
        if (!motionState.isInteracting && abs(motionState.targetValue - target) > 0.001f) {
            motionState.syncToValue(target)
        }
    }

    val acceptsInput = enabled && onCheckedChange != null
    val gestureModifier = if (acceptsInput) {
        Modifier.pointerInput(travelPx, isLtr, motionState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                motionState.beginInteraction()
                val startPosition = motionState.value
                var totalDrag = 0f
                var hasDragged = false
                var released = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.positionChange().x
                    if (delta != 0f) {
                        totalDrag += delta
                        if (hasDragged || abs(totalDrag) >= viewConfiguration.touchSlop) {
                            hasDragged = true
                            change.consume()
                            val directedDrag = if (isLtr) totalDrag else -totalDrag
                            motionState.dragTo(startPosition + directedDrag / travelPx)
                        }
                    }
                    if (change.changedToUpIgnoreConsumed()) {
                        released = true
                        break
                    }
                    if (!change.pressed) break
                }

                if (released) {
                    val targetChecked = resolveSwitchTarget(
                        currentChecked = currentChecked,
                        hasDragged = hasDragged,
                        dragValue = motionState.targetValue
                    )
                    motionState.settleTo(if (targetChecked) 1f else 0f)
                    if (targetChecked != currentChecked) {
                        currentOnCheckedChange?.invoke(targetChecked)
                    }
                } else {
                    motionState.cancelInteraction(if (currentChecked) 1f else 0f)
                }
            }
        }
    } else {
        Modifier
    }

    val semanticsModifier = Modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
        if (!enabled) disabled()
        if (acceptsInput) {
            onClick {
                val targetChecked = !currentChecked
                motionState.animateToValue(if (targetChecked) 1f else 0f)
                currentOnCheckedChange?.invoke(targetChecked)
                true
            }
        }
    }

    val position = motionState.value
    val visualPosition = if (isLtr) position else 1f - position
    val pressProgress = motionState.pressProgress
    val uncheckedTrackColor = if (isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val trackColor = lerp(
        start = uncheckedTrackColor,
        stop = AppColors.Accent.copy(alpha = 0.72f),
        fraction = position
    )
    val restingThumbAlpha = if (isDark) 0.34f else 0.68f
    val thumbScrim = Color.White.copy(
        alpha = restingThumbAlpha + (0.10f - restingThumbAlpha) * pressProgress
    )
    val trackBackdrop = rememberLayerBackdrop()
    val sampledTrackBackdrop = if (backdrop != null) {
        rememberBackdrop(trackBackdrop) { drawTrackBackdrop ->
            val scaleX = 2f / 3f + (0.75f - 2f / 3f) * pressProgress
            val scaleY = 0.75f * pressProgress
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

    Box(
        modifier = modifier
            .size(LiquidSwitchWidth, LiquidSwitchHeight)
            .then(semanticsModifier)
            .then(gestureModifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .background(trackColor)
                .border(
                    0.8.dp,
                    Color.White.copy(alpha = if (isDark) 0.18f else 0.52f),
                    CircleShape
                )
        )

        val thumbPositionModifier = Modifier
            .size(width = LiquidSwitchThumbWidth, height = LiquidSwitchThumbHeight)
            .offset {
                IntOffset(
                    x = (thumbInsetPx + visualPosition * travelPx).roundToInt(),
                    y = 0
                )
            }
        val thumbVisualModifier = if (thumbBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = thumbBackdrop,
                shape = { CircleShape },
                effects = {
                    val opticalProgress = if (motionEnabled) pressProgress else 0f
                    val blurRadius = 8.dp.toPx() *
                        (1f - transparency) * (1f - opticalProgress)
                    if (blurRadius > 0f) blur(blurRadius)
                    lens(
                        5.dp.toPx() * opticalProgress,
                        10.dp.toPx() * opticalProgress,
                        chromaticAberration = opticalProgress > 0.05f
                    )
                },
                highlight = {
                    Highlight.Ambient.copy(alpha = pressProgress)
                },
                shadow = {
                    Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                },
                innerShadow = {
                    InnerShadow(radius = 4.dp * pressProgress, alpha = pressProgress)
                },
                layerBlock = {
                    scaleX = motionState.scale
                    scaleY = motionState.scale
                    if (motionEnabled) {
                        val velocity = motionState.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }
                },
                onDrawSurface = { drawRect(thumbScrim) }
            )
        } else {
            Modifier
                .clip(CircleShape)
                .background(thumbScrim)
                .border(0.8.dp, Color.White.copy(alpha = 0.72f), CircleShape)
        }

        Box(modifier = thumbPositionModifier.then(thumbVisualModifier))
    }
}

internal fun resolveSwitchTarget(
    currentChecked: Boolean,
    hasDragged: Boolean,
    dragValue: Float
): Boolean = if (hasDragged) dragValue >= 0.5f else !currentChecked

@Composable
private fun EInkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackColor = if (checked) Color.Black else Color.White
    val thumbColor = if (checked) Color.White else Color(0xFF444444)
    val borderColor = if (enabled) Color.Black else Color(0xFF8A8A8A)
    val thumbAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = modifier
            .size(width = LiquidSwitchWidth, height = LiquidSwitchHeight)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, borderColor, CircleShape)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = thumbAlignment
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = LiquidSwitchThumbInset)
                .size(width = LiquidSwitchThumbWidth, height = LiquidSwitchThumbHeight)
                .clip(CircleShape)
                .background(thumbColor)
                .border(1.dp, borderColor, CircleShape)
        )
    }
}
