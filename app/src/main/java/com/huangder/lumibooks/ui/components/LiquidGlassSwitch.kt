package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs

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
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val thumbInsetPx = with(density) { LiquidSwitchThumbInset.toPx() }
    val travelPx = with(density) { LiquidSwitchThumbTravel.toPx() }
    var settledPosition by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var dragPosition by remember { mutableFloatStateOf(settledPosition) }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    var isGestureActive by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    val position by animateFloatAsState(
        targetValue = if (isGestureActive) dragPosition else settledPosition,
        animationSpec = if (isGestureActive) {
            snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "liquidSwitchPosition"
    )
    val currentPosition by rememberUpdatedState(position)
    val pressProgress by animateFloatAsState(
        targetValue = if (isGestureActive || isSettling) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 520f),
        label = "liquidSwitchPress"
    )

    LaunchedEffect(isSettling, position, settledPosition) {
        if (isSettling && abs(position - settledPosition) < 0.045f) {
            isSettling = false
        }
    }

    LaunchedEffect(checked, isGestureActive) {
        if (!isGestureActive) {
            settledPosition = if (checked) 1f else 0f
        }
    }

    val acceptsInput = enabled && onCheckedChange != null
    val gestureModifier = if (acceptsInput) {
        Modifier.pointerInput(travelPx, isLtr) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                dragPosition = currentPosition
                isGestureActive = true
                val startPosition = currentPosition
                var totalDrag = 0f
                var hasDragged = false
                var released = false

                try {
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
                                dragPosition =
                                    (startPosition + directedDrag / travelPx).coerceIn(0f, 1f)
                            }
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            released = true
                            break
                        }
                        if (!change.pressed) break
                    }

                    val targetChecked = if (released) {
                        if (hasDragged) dragPosition >= 0.5f else !currentChecked
                    } else {
                        currentChecked
                    }
                    if (targetChecked != currentChecked) {
                        currentOnCheckedChange?.invoke(targetChecked)
                    }
                    settledPosition = if (targetChecked) 1f else 0f
                    isSettling = released
                } finally {
                    isGestureActive = false
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
                currentOnCheckedChange?.invoke(!currentChecked)
                true
            }
        }
    }
    val visualPosition = if (isLtr) position else 1f - position
    val checkedProgress = position
    val uncheckedTrackScrim = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }
    val trackScrim = lerp(
        start = uncheckedTrackScrim,
        stop = AppColors.Accent.copy(alpha = 0.58f),
        fraction = checkedProgress
    )
    val restingThumbAlpha = if (isDark) 0.34f else 0.68f
    val thumbScrim = Color.White.copy(
        alpha = restingThumbAlpha + (0.10f - restingThumbAlpha) * pressProgress
    )
    Box(
        modifier = modifier
            .size(LiquidSwitchWidth, LiquidSwitchHeight)
            .then(semanticsModifier)
            .then(gestureModifier)
            .graphicsLayer {
                scaleX = 1f + pressProgress * 0.018f
                scaleY = 1f - pressProgress * 0.025f
                alpha = if (enabled) 1f else 0.48f
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backdrop != null) {
                        Modifier.liquidSwitchGlass(
                            backdrop = backdrop,
                            isDark = isDark,
                            transparency = transparency,
                            surfaceScrim = trackScrim,
                            pressProgress = pressProgress * 0.45f,
                            isThumb = false
                        )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(trackScrim)
                            .border(
                                0.8.dp,
                                Color.White.copy(alpha = if (isDark) 0.20f else 0.72f),
                                CircleShape
                            )
                    }
                )
        )

        Box(
            modifier = Modifier
                .size(width = LiquidSwitchThumbWidth, height = LiquidSwitchThumbHeight)
                .graphicsLayer {
                    translationX = thumbInsetPx + visualPosition * travelPx
                    scaleX = 1f + pressProgress * 0.28f
                    scaleY = 1f + pressProgress * 0.14f
                }
                .then(
                    if (backdrop != null) {
                        Modifier.liquidSwitchGlass(
                            backdrop = backdrop,
                            isDark = isDark,
                            transparency = transparency,
                            surfaceScrim = thumbScrim,
                            pressProgress = pressProgress,
                            isThumb = true
                        )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(
                                (if (isDark) Color(0xFFE9E9ED) else Color.White).copy(
                                    alpha = 1f - pressProgress * 0.62f
                                )
                            )
                            .border(
                                0.8.dp,
                                Color.White.copy(alpha = if (isDark) 0.32f else 0.92f),
                                CircleShape
                            )
                    }
                )
        )
    }
}

private fun Modifier.liquidSwitchGlass(
    backdrop: Backdrop,
    isDark: Boolean,
    transparency: Float,
    surfaceScrim: Color,
    pressProgress: Float,
    isThumb: Boolean
): Modifier {
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.42f + pressProgress * 0.14f),
                Color.White.copy(alpha = 0.12f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.94f),
                Color.White.copy(alpha = 0.28f + pressProgress * 0.12f)
            )
        }
    )

    return drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            vibrancy()
            if (transparency < 1f) {
                blur((4.dp * (1f - transparency)).toPx())
            }
            lens(
                ((if (isThumb) 9.dp else 6.dp) + 2.dp * pressProgress).toPx(),
                ((if (isThumb) 16.dp else 10.dp) + 3.dp * pressProgress).toPx(),
                chromaticAberration = pressProgress > 0.05f
            )
        },
        highlight = {
            Highlight.Default.copy(alpha = 0.22f + pressProgress * 0.36f)
        },
        shadow = {
            Shadow(
                radius = if (isThumb) 8.dp else 5.dp,
                alpha = 0.18f + pressProgress * 0.08f
            )
        },
        innerShadow = {
            InnerShadow(
                radius = if (isThumb) 4.dp else 3.dp,
                alpha = 0.16f + pressProgress * 0.22f
            )
        },
        onDrawSurface = {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        surfaceScrim.copy(
                            alpha = (surfaceScrim.alpha * 1.18f).coerceAtMost(1f)
                        ),
                        surfaceScrim.copy(alpha = surfaceScrim.alpha * 0.76f)
                    )
                )
            )
        }
    ).border(0.8.dp, borderBrush, CircleShape)
}
