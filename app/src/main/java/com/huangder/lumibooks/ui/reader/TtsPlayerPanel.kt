package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.huangder.lumibooks.ui.components.LiquidGlassMenuAnchorKind
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsProsodyMode
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TtsPlayerPanel(
    chapterTitle: String = "",
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
    onReturnToProgress: () -> Unit = {},
    onStartFromCurrentPage: () -> Unit = {},
    canReturnToProgress: Boolean = false,
    readerMenuVisible: Boolean = false,
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
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val eInkMode = LocalEInkMode.current
    var panelHidden by remember { mutableStateOf(false) }
    var interactionSerial by remember { mutableStateOf(0) }
    val markInteraction = {
        panelHidden = false
        interactionSerial += 1
    }
    LaunchedEffect(readerMenuVisible, interactionSerial) {
        panelHidden = false
        delay(5_000L)
        if (playbackState != TtsPlaybackState.IDLE) {
            panelHidden = true
            menuHost?.dismiss()
        }
    }
    val panelFallbackColor = if (readerContentColor.luminance() > 0.58f) {
        Color(0xFF2D2D30)
    } else {
        // TTS controls stay visibly separate from a paper/reader background in the
        // standard theme, while still adapting to dark reader palettes.
        Color.White
    }
    val panelContentColor = if (panelFallbackColor.luminance() < 0.4f) {
        Color.White
    } else {
        Color(0xFF332A24)
    }
    val glassScrim = if (panelContentColor.luminance() > 0.58f) {
        Color.Black.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
    val chapterScrollState = rememberScrollState()

    // Long chapter names remain discoverable without changing the capsule's geometry.
    LaunchedEffect(chapterTitle) {
        chapterScrollState.scrollTo(0)
        delay(1_400L)
        if (chapterScrollState.maxValue > 0) {
            chapterScrollState.animateScrollTo(chapterScrollState.maxValue)
        }
    }

    fun openMenu(kind: TtsMenuKind, toggle: Boolean = true) {
        markInteraction()
        val id = when (kind) {
            TtsMenuKind.Rate -> rateId
            TtsMenuKind.Pitch -> pitchId
            TtsMenuKind.Timer -> timerId
        }
        val options = when (kind) {
            TtsMenuKind.Rate -> rateOptions.map { rate ->
                TtsMenuChoice(formatSpeechRate(rate),
                    rate == speechRate && (!usesAndroidTts || speechRateMode == TtsProsodyMode.OVERRIDE)
                ) { markInteraction(); onRateChange(rate) }
            }
            TtsMenuKind.Pitch -> pitchOptions.map { value ->
                TtsMenuChoice(formatPitch(value),
                    value == pitch && pitchMode == TtsProsodyMode.OVERRIDE
                ) { markInteraction(); onPitchChange(value) }
            }
            TtsMenuKind.Timer -> timerOptionsMinutes.mapIndexed { index, minutes ->
                val remaining = sleepTimerRemainingMs?.let { ((it + 59_999) / 60_000).toInt() }
                TtsMenuChoice(timerOptionLabels[index], minutes == remaining) { markInteraction(); onSetSleepTimer(minutes) }
            } + if (timerActive) listOf(TtsMenuChoice(cancelTimerLabel, destructive = true) {
                markInteraction()
                onCancelSleepTimer()
            }) else emptyList()
        }
        val followEngine = if (usesAndroidTts && kind != TtsMenuKind.Timer) {
            val selected = if (kind == TtsMenuKind.Rate) speechRateMode else pitchMode
            TtsMenuChoice(followEngineLabel, selected == TtsProsodyMode.FOLLOW_ENGINE) {
                markInteraction()
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { capsuleBounds = it.boundsInWindow() }
    ) {
        AnimatedVisibility(
            visible = !panelHidden,
            enter = fadeIn(tween(if (eInkMode) 0 else 200)),
            exit = fadeOut(tween(if (eInkMode) 0 else 280))
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TtsActionPill(
                        label = stringResource(R.string.tts_return_to_progress),
                        contentDescription = stringResource(R.string.tts_return_to_reading_page),
                        enabled = canReturnToProgress,
                        backgroundColor = panelFallbackColor,
                        contentColor = panelContentColor,
                        glassScrim = glassScrim,
                        forceSolid = forceSolidSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { markInteraction(); onReturnToProgress() }
                    )
                    TtsActionPill(
                        label = stringResource(R.string.tts_from_this_page),
                        contentDescription = stringResource(R.string.tts_from_this_page),
                        enabled = true,
                        backgroundColor = panelFallbackColor,
                        contentColor = panelContentColor,
                        glassScrim = glassScrim,
                        forceSolid = forceSolidSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { markInteraction(); onStartFromCurrentPage() }
                    )
                    TtsActionPill(
                        label = stringResource(R.string.tts_pitch_label),
                        contentDescription = stringResource(R.string.tts_pitch_label),
                        enabled = usesAndroidTts,
                        backgroundColor = panelFallbackColor,
                        contentColor = panelContentColor,
                        glassScrim = glassScrim,
                        forceSolid = forceSolidSurface,
                        modifier = Modifier.weight(1f).liquidGlassMenuAnchor(pitchId, LiquidGlassMenuAnchorKind.Embedded),
                        onClick = { openMenu(TtsMenuKind.Pitch) }
                    )
                    TtsIconPill(
                        icon = AppIcons.Speedometer,
                        contentDescription = stringResource(R.string.tts_speech_rate),
                        backgroundColor = panelFallbackColor,
                        contentColor = panelContentColor,
                        glassScrim = glassScrim,
                        forceSolid = forceSolidSurface,
                        modifier = Modifier.liquidGlassMenuAnchor(rateId, LiquidGlassMenuAnchorKind.Embedded),
                        onClick = { openMenu(TtsMenuKind.Rate) }
                    )
                    TtsIconPill(
                        icon = AppIcons.MoonStars,
                        contentDescription = stringResource(R.string.tts_timer_label),
                        active = timerActive,
                        backgroundColor = panelFallbackColor,
                        contentColor = panelContentColor,
                        glassScrim = glassScrim,
                        forceSolid = forceSolidSurface,
                        modifier = Modifier.liquidGlassMenuAnchor(timerId, LiquidGlassMenuAnchorKind.Embedded),
                        onClick = { openMenu(TtsMenuKind.Timer) }
                    )
                }

                LiquidGlassSurface(
                    controlEdge = true,
                    shape = RoundedCornerShape(36.dp),
                    fallbackColor = panelFallbackColor,
                    contentScrimColor = glassScrim,
                    forceFallback = forceSolidSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chapterTitle,
                            color = panelContentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(chapterScrollState)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { markInteraction(); onSkipBackward() }, modifier = Modifier.size(44.dp)) {
                                Icon(AppIcons.SkipBackFilled, stringResource(R.string.tts_previous_sentence), tint = panelContentColor)
                            }
                            IconButton(
                                onClick = { markInteraction(); onPlayPause() },
                                enabled = playbackState != TtsPlaybackState.INITIALIZING,
                                modifier = Modifier.size(44.dp)
                            ) {
                                if (playbackState == TtsPlaybackState.INITIALIZING) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = panelContentColor)
                                } else {
                                    Icon(
                                        if (playbackState == TtsPlaybackState.PLAYING) AppIcons.PauseFilled else AppIcons.PlayFilled,
                                        stringResource(if (playbackState == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play),
                                        tint = panelContentColor
                                    )
                                }
                            }
                            IconButton(onClick = { markInteraction(); onSkipForward() }, modifier = Modifier.size(44.dp)) {
                                Icon(AppIcons.SkipForwardFilled, stringResource(R.string.tts_next_sentence), tint = panelContentColor)
                            }
                            IconButton(onClick = { markInteraction(); onStop() }, modifier = Modifier.size(44.dp)) {
                                Icon(AppIcons.X, stringResource(R.string.tts_stop), tint = panelContentColor.copy(alpha = 0.72f), modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }

                }
        }
    }
}
@Composable
private fun TtsActionPill(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    glassScrim: Color,
    forceSolid: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = RoundedCornerShape(28.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassScrim,
        forceFallback = forceSolid,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TtsIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    contentColor: Color,
    glassScrim: Color,
    forceSolid: Boolean,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        controlEdge = true,
        shape = RoundedCornerShape(28.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassScrim,
        forceFallback = forceSolid,
        onClick = onClick,
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (active) AppColors.Accent else contentColor,
            modifier = Modifier.size(21.dp)
        )
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

