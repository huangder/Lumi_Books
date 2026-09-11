package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsProsodyMode
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

@Composable
fun TtsPlayerPanel(
    playbackState: TtsPlaybackState,
    speechRate: Float,
    speechRateMode: TtsProsodyMode,
    pitch: Float,
    pitchMode: TtsProsodyMode,
    usesAndroidTts: Boolean,
    sleepTimerRemainingMs: Long?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onRateChange: (Float) -> Unit,
    onRateModeChange: (TtsProsodyMode) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPitchModeChange: (TtsProsodyMode) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    readerBackgroundColor: Color,
    readerContentColor: Color,
    forceSolidSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rateOptions = remember {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f)
    }
    val pitchOptions = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
    val timerOptionsMinutes = remember { listOf(10, 20, 30, 40, 50, 60, 90, 120, 150, 180) }
    val timerOptionLabels = timerOptionsMinutes.map { min ->
        when {
            min < 60 -> stringResource(R.string.time_minutes, min)
            min % 60 == 0 -> stringResource(R.string.time_hours, min / 60)
            else -> stringResource(R.string.tts_timer_decimal_hours, min / 60f)
        }
    }
    val timerActive = sleepTimerRemainingMs != null
    var showRateMenu by remember { mutableStateOf(false) }
    var showPitchMenu by remember { mutableStateOf(false) }
    var showTimerMenu by remember { mutableStateOf(false) }
    fun hideMenus() {
        showRateMenu = false
        showPitchMenu = false
        showTimerMenu = false
    }

    val capsuleShape = RoundedCornerShape(28.dp)
    val rateMenuShape = RoundedCornerShape(16.dp)
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val motionEnabled = LocalMotionEnabled.current
    val panelBackdrop = rememberLayerBackdrop()
    val panelHeight by animateDpAsState(
        targetValue = if (showRateMenu || (showPitchMenu && usesAndroidTts) || showTimerMenu) {
            328.dp
        } else {
            56.dp
        },
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.82f, stiffness = 360f)
        } else {
            tween(120)
        },
        label = "ttsRateMenuHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        AnimatedVisibility(
            visible = showRateMenu,
            enter = if (!motionEnabled) fadeIn(tween(120)) else fadeIn(spring(dampingRatio = 0.80f, stiffness = 420f)) +
                slideInVertically(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
                    initialOffsetY = { it / 5 }
                ) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f),
                    initialScale = 0.78f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            exit = if (!motionEnabled) fadeOut(tween(100)) else fadeOut(spring(dampingRatio = 0.88f, stiffness = 520f)) +
                slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 440f),
                    targetOffsetY = { it / 6 }
                ) +
                scaleOut(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    targetScale = 0.84f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            LiquidGlassSurface(
                shape = rateMenuShape,
                fallbackColor = readerBackgroundColor,
                contentScrimColor = readerBackgroundColor.copy(alpha = 0.18f),
                forceFallback = forceSolidSurface,
                modifier = Modifier.width(176.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (usesAndroidTts) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (speechRateMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                        Modifier.background(AppColors.Accent.copy(alpha = 0.14f))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    hideMenus()
                                    onRateModeChange(TtsProsodyMode.FOLLOW_ENGINE)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.tts_follow_engine),
                                color = if (speechRateMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                    AppColors.Accent
                                } else {
                                    readerContentColor
                                },
                                fontSize = 13.sp,
                                fontWeight = if (speechRateMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                }
                            )
                        }
                    }
                    rateOptions.chunked(3).forEach { rowRates ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowRates.forEach { rate ->
                                val selected = rate == speechRate &&
                                    (!usesAndroidTts || speechRateMode == TtsProsodyMode.OVERRIDE)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (selected) {
                                                Modifier.background(AppColors.Accent.copy(alpha = 0.14f))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            showRateMenu = false
                                            onRateChange(rate)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatSpeechRate(rate),
                                        color = if (selected) AppColors.Accent else readerContentColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPitchMenu && usesAndroidTts,
            enter = fadeIn(tween(120)) + slideInVertically(
                animationSpec = tween(160),
                initialOffsetY = { it / 5 }
            ),
            exit = fadeOut(tween(100)) + slideOutVertically(
                animationSpec = tween(140),
                targetOffsetY = { it / 6 }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            LiquidGlassSurface(
                shape = rateMenuShape,
                fallbackColor = readerBackgroundColor,
                contentScrimColor = readerBackgroundColor.copy(alpha = 0.18f),
                forceFallback = forceSolidSurface,
                modifier = Modifier.width(176.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (pitchMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                    Modifier.background(AppColors.Accent.copy(alpha = 0.14f))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                hideMenus()
                                onPitchModeChange(TtsProsodyMode.FOLLOW_ENGINE)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tts_follow_engine),
                            color = if (pitchMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                AppColors.Accent
                            } else {
                                readerContentColor
                            },
                            fontSize = 13.sp,
                            fontWeight = if (pitchMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            }
                        )
                    }
                    pitchOptions.chunked(3).forEach { rowPitches ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowPitches.forEach { pitchOption ->
                                val selected = pitchMode == TtsProsodyMode.OVERRIDE &&
                                    pitchOption == pitch
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (selected) {
                                                Modifier.background(AppColors.Accent.copy(alpha = 0.14f))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            hideMenus()
                                            onPitchChange(pitchOption)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatPitch(pitchOption),
                                        color = if (selected) AppColors.Accent else readerContentColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                            repeat(3 - rowPitches.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showTimerMenu,
            enter = if (!motionEnabled) fadeIn(tween(120)) else fadeIn(spring(dampingRatio = 0.80f, stiffness = 420f)) +
                slideInVertically(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
                    initialOffsetY = { it / 5 }
                ) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f),
                    initialScale = 0.78f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            exit = if (!motionEnabled) fadeOut(tween(100)) else fadeOut(spring(dampingRatio = 0.88f, stiffness = 520f)) +
                slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 440f),
                    targetOffsetY = { it / 6 }
                ) +
                scaleOut(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    targetScale = 0.84f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            LiquidGlassSurface(
                shape = rateMenuShape,
                fallbackColor = readerBackgroundColor,
                contentScrimColor = readerBackgroundColor.copy(alpha = 0.18f),
                forceFallback = forceSolidSurface,
                modifier = Modifier.width(120.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    timerOptionsMinutes.forEachIndexed { index, minutes ->
                        val label = timerOptionLabels[index]
                        val offset = sleepTimerRemainingMs?.let { ((it + 59_999) / 60_000).toInt() } ?: -1
                        val selected = timerActive && minutes == offset
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) {
                                        Modifier.background(AppColors.Accent.copy(alpha = 0.14f))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    hideMenus()
                                    onSetSleepTimer(minutes)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AppColors.Accent else readerContentColor,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                    if (timerActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    hideMenus()
                                    onCancelSleepTimer()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.tts_timer_cancel),
                                color = Color(0xFFE53935),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag < -24f) {
                                hideMenus()
                                showRateMenu = true
                            }
                        }
                    )
                }
                .then(
                    if (isLiquidGlass && !forceSolidSurface) {
                        Modifier
                    } else {
                        Modifier.shadow(
                            elevation = 8.dp,
                            shape = capsuleShape,
                            ambientColor = Color.Black.copy(alpha = 0.10f),
                            spotColor = Color.Black.copy(alpha = 0.14f)
                        )
                    }
                )
        ) {
            LiquidGlassSurface(
                shape = capsuleShape,
                fallbackColor = readerBackgroundColor,
                contentScrimColor = readerBackgroundColor.copy(alpha = 0.85f),
                forceFallback = forceSolidSurface,
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (isLiquidGlass && !forceSolidSurface) {
                            Modifier.layerBackdrop(panelBackdrop)
                        } else {
                            Modifier
                        }
                    )
            ) { }
            ProvideLiquidGlassBackdrop(panelBackdrop.takeIf { isLiquidGlass && !forceSolidSurface }) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipBackward, modifier = Modifier.size(40.dp)) {
                    Icon(
                        AppIcons.SkipBack,
                        contentDescription = stringResource(R.string.tts_previous_sentence),
                        tint = readerContentColor
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    enabled = playbackState != TtsPlaybackState.INITIALIZING,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (playbackState == TtsPlaybackState.INITIALIZING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = readerContentColor
                        )
                    } else {
                        Icon(
                            if (playbackState == TtsPlaybackState.PLAYING) AppIcons.Pause else AppIcons.Play,
                            contentDescription = stringResource(
                                if (playbackState == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play
                            ),
                            tint = readerContentColor
                        )
                    }
                }

                IconButton(onClick = onSkipForward, modifier = Modifier.size(40.dp)) {
                    Icon(
                        AppIcons.SkipForward,
                        contentDescription = stringResource(R.string.tts_next_sentence),
                        tint = readerContentColor
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (usesAndroidTts &&
                                speechRateMode == TtsProsodyMode.FOLLOW_ENGINE
                            ) {
                                stringResource(R.string.tts_follow_engine_short)
                            } else {
                                formatSpeechRate(speechRate)
                            },
                            color = readerContentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (showRateMenu) {
                                        hideMenus()
                                    } else {
                                        showRateMenu = true
                                        showPitchMenu = false
                                        showTimerMenu = false
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )

                        if (usesAndroidTts) {
                            Text(
                                text = stringResource(
                                    R.string.tts_pitch_short,
                                    if (pitchMode == TtsProsodyMode.FOLLOW_ENGINE) {
                                        stringResource(R.string.tts_follow_engine_short)
                                    } else {
                                        formatPitch(pitch)
                                    }
                                ),
                                color = readerContentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        if (showPitchMenu) {
                                            hideMenus()
                                        } else {
                                            showRateMenu = false
                                            showPitchMenu = true
                                            showTimerMenu = false
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = sleepTimerRemainingMs?.let(::formatSleepTimer)
                                ?: stringResource(R.string.tts_timer_label),
                            color = readerContentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (showTimerMenu) {
                                        hideMenus()
                                    } else {
                                        showRateMenu = false
                                        showPitchMenu = false
                                        showTimerMenu = true
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.tts_stop),
                    onClick = onStop,
                    modifier = Modifier.size(44.dp),
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = readerContentColor
                )
            }
            }
        }
    }
}

private fun formatSpeechRate(rate: Float): String {
    val formatted = String.format(Locale.US, "%.2f", rate)
        .trimEnd('0')
        .trimEnd('.')
    return "${formatted}x"
}

private fun formatPitch(pitch: Float): String {
    val formatted = String.format(Locale.US, "%.2f", pitch)
        .trimEnd('0')
        .trimEnd('.')
    return "${formatted}x"
}

private fun formatSleepTimer(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(1)
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return "%02d:%02d".format(minutes, seconds)
}
