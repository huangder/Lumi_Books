package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.huangder.lumibooks.ui.components.LiquidGlassMenuAnchorKind
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
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
    val menuHost = LocalLiquidGlassMenuHost.current
    val rateId = remember { Any() }
    val pitchId = remember { Any() }
    val timerId = remember { Any() }
    var capsuleBounds by remember { mutableStateOf(Rect.Zero) }
    val followEngineLabel = stringResource(R.string.tts_follow_engine)
    val cancelTimerLabel = stringResource(R.string.tts_timer_cancel)
    val capsuleShape = RoundedCornerShape(28.dp)
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val panelBackdrop = rememberLayerBackdrop()

    fun openMenu(kind: TtsMenuKind, toggle: Boolean = true) {
        val id = when (kind) {
            TtsMenuKind.Rate -> rateId
            TtsMenuKind.Pitch -> pitchId
            TtsMenuKind.Timer -> timerId
        }
        val options = when (kind) {
            TtsMenuKind.Rate -> rateOptions.map { rate ->
                TtsMenuChoice(formatSpeechRate(rate),
                    rate == speechRate && (!usesAndroidTts || speechRateMode == TtsProsodyMode.OVERRIDE)
                ) { onRateChange(rate) }
            }
            TtsMenuKind.Pitch -> pitchOptions.map { value ->
                TtsMenuChoice(formatPitch(value),
                    value == pitch && pitchMode == TtsProsodyMode.OVERRIDE
                ) { onPitchChange(value) }
            }
            TtsMenuKind.Timer -> timerOptionsMinutes.mapIndexed { index, minutes ->
                val remaining = sleepTimerRemainingMs?.let { ((it + 59_999) / 60_000).toInt() }
                TtsMenuChoice(timerOptionLabels[index], minutes == remaining) { onSetSleepTimer(minutes) }
            } + if (timerActive) listOf(TtsMenuChoice(cancelTimerLabel, destructive = true, action = onCancelSleepTimer)) else emptyList()
        }
        val followEngine = if (usesAndroidTts && kind != TtsMenuKind.Timer) {
            val selected = if (kind == TtsMenuKind.Rate) speechRateMode else pitchMode
            TtsMenuChoice(followEngineLabel, selected == TtsProsodyMode.FOLLOW_ENGINE) {
                if (kind == TtsMenuKind.Rate) onRateModeChange(TtsProsodyMode.FOLLOW_ENGINE)
                else onPitchModeChange(TtsProsodyMode.FOLLOW_ENGINE)
            }
        } else null
        val spec = LiquidGlassMenuSpec(
            anchorBounds = Rect.Zero,
            sourceId = id,
            width = if (kind == TtsMenuKind.Timer) 144.dp else 192.dp,
            items = emptyList(),
            preferAbove = true,
            passThroughBounds = { capsuleBounds },
            surfaceColor = readerBackgroundColor,
            contentColor = readerContentColor,
            forceSolid = forceSolidSurface,
            content = { enabled, select ->
                TtsMenuChoices(options, if (kind == TtsMenuKind.Timer) 1 else 3,
                    followEngine, readerContentColor, enabled, select)
            }
        )
        if (toggle) menuHost?.toggle(spec) else menuHost?.show(spec)
    }
    val latestOpenRate by rememberUpdatedState { openMenu(TtsMenuKind.Rate, toggle = false) }

    Box(modifier = modifier.fillMaxWidth().height(56.dp)) {

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .onGloballyPositioned { capsuleBounds = it.boundsInWindow() }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag < -24f) {
                                latestOpenRate()
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
                        AppIcons.SkipBackFilled,
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
                            if (playbackState == TtsPlaybackState.PLAYING) {
                                AppIcons.PauseFilled
                            } else {
                                AppIcons.PlayFilled
                            },
                            contentDescription = stringResource(
                                if (playbackState == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play
                            ),
                            tint = readerContentColor
                        )
                    }
                }

                IconButton(onClick = onSkipForward, modifier = Modifier.size(40.dp)) {
                    Icon(
                        AppIcons.SkipForwardFilled,
                        contentDescription = stringResource(R.string.tts_next_sentence),
                        tint = readerContentColor
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { openMenu(TtsMenuKind.Rate) },
                            modifier = Modifier.size(36.dp)
                                .liquidGlassMenuAnchor(rateId, LiquidGlassMenuAnchorKind.Embedded)
                        ) {
                            Icon(
                                AppIcons.Speedometer,
                                contentDescription = stringResource(R.string.tts_speech_rate),
                                tint = readerContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

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
                                    .liquidGlassMenuAnchor(pitchId, LiquidGlassMenuAnchorKind.Embedded, 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        openMenu(TtsMenuKind.Pitch)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .liquidGlassMenuAnchor(timerId, LiquidGlassMenuAnchorKind.Embedded, 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    openMenu(TtsMenuKind.Timer)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                AppIcons.Timer,
                                contentDescription = stringResource(R.string.tts_timer_label),
                                tint = if (timerActive) AppColors.Accent else readerContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            // 定时生效时保留倒计时数字，其余情况只显示图标。
                            sleepTimerRemainingMs?.let { remaining ->
                                Text(
                                    text = formatSleepTimer(remaining),
                                    color = AppColors.Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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

internal enum class TtsMenuKind { Rate, Pitch, Timer }

private data class TtsMenuChoice(
    val label: String,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val action: () -> Unit
)

@Composable
private fun TtsMenuChoices(
    options: List<TtsMenuChoice>,
    columns: Int,
    leading: TtsMenuChoice?,
    contentColor: Color,
    enabled: Boolean,
    select: (() -> Unit) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        leading?.let { TtsChoice(it, contentColor, enabled, select, Modifier.fillMaxWidth()) }
        options.chunked(columns).forEach { choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                choices.forEach { TtsChoice(it, contentColor, enabled, select, Modifier.weight(1f)) }
                repeat(columns - choices.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TtsChoice(
    choice: TtsMenuChoice,
    contentColor: Color,
    enabled: Boolean,
    select: (() -> Unit) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .then(if (choice.selected) Modifier.background(AppColors.Accent.copy(alpha = 0.14f)) else Modifier)
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                select(choice.action)
            }.padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(choice.label, color = when {
            choice.destructive -> Color(0xFFE53935)
            choice.selected -> AppColors.Accent
            else -> contentColor
        }, fontSize = 13.sp, fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
