package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import com.huangder.lumibooks.tts.ReaderTtsDock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
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
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.LumiMotion
import java.util.Locale

@Composable
internal fun TtsPlayerOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val eInkMode = LocalEInkMode.current
    val motionEnabled = LocalMotionEnabled.current
    AnimatedVisibility(
        visible = visible,
        enter = when {
            eInkMode -> EnterTransition.None
            !motionEnabled -> fadeIn(tween(120))
            else -> slideInVertically(
                tween(LumiMotion.MenuEnterMillis, delayMillis = LumiMotion.MenuExitMillis, easing = AppEasing.Smooth)
            ) { it / 2 } + fadeIn(tween(LumiMotion.MenuEnterMillis, delayMillis = LumiMotion.MenuExitMillis))
        },
        exit = when {
            eInkMode -> ExitTransition.None
            !motionEnabled -> fadeOut(tween(100))
            else -> slideOutVertically(tween(LumiMotion.MenuExitMillis, easing = AppEasing.Accelerate)) { it / 2 } +
                fadeOut(tween(LumiMotion.MenuExitMillis))
        },
        modifier = modifier
    ) { content() }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun TtsPlayerPanel(
    chapterTitle: String = "",
    playbackState: TtsPlaybackState,
    speechRate: Float,
    speechRateMode: TtsProsodyMode,
    pitch: Float,
    pitchMode: TtsProsodyMode,
    usesAndroidTts: Boolean,
    sleepTimerRemainingMs: Long?,
    sleepTimerDurationMs: Long? = sleepTimerRemainingMs,
    followUserPageTurns: Boolean = false,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onOpenFullscreen: () -> Unit = {},
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onRateChange: (Float) -> Unit,
    onRateModeChange: (TtsProsodyMode) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPitchModeChange: (TtsProsodyMode) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onFollowUserPageTurnsChange: (Boolean) -> Unit = {},
    onReturnToProgress: () -> Unit = {},
    onStartFromCurrentPage: () -> Unit = {},
    canReturnToProgress: Boolean = false,
    readerMenuVisible: Boolean = false,
    readerBackgroundColor: Color,
    readerContentColor: Color,
    forceSolidSurface: Boolean = false,
    placement: ReaderTtsPlacement? = null,
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
    val eInkMode = LocalEInkMode.current
    val motionEnabled = LocalMotionEnabled.current && !eInkMode
    var actionsExpanded by remember { mutableStateOf(false) }
    val stopListening = {
        menuHost?.dismiss()
        actionsExpanded = false
        onStop()
    }
    DisposableEffect(menuHost) {
        onDispose {
            if (menuHost?.activeMenu?.sourceId in listOf(rateId, pitchId, timerId)) menuHost?.dismiss()
        }
    }
    LaunchedEffect(placement?.collapseKey) {
        actionsExpanded = false
        if (menuHost?.activeMenu?.sourceId in listOf(rateId, pitchId, timerId)) menuHost?.dismiss()
    }
    LaunchedEffect(readerMenuVisible) {
        if (readerMenuVisible) {
            actionsExpanded = false
            if (menuHost?.activeMenu?.sourceId in listOf(rateId, pitchId, timerId)) {
                menuHost?.dismiss()
            }
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
            } + if (timerActive) listOf(TtsMenuChoice(cancelTimerLabel, destructive = true) {
                onCancelSleepTimer()
            }) else emptyList()
        }
        val followEngine = if (usesAndroidTts && (kind == TtsMenuKind.Rate || kind == TtsMenuKind.Pitch)) {
            val selected = if (kind == TtsMenuKind.Rate) speechRateMode else pitchMode
            TtsMenuChoice(followEngineLabel, selected == TtsProsodyMode.FOLLOW_ENGINE) {
                if (kind == TtsMenuKind.Rate) onRateModeChange(TtsProsodyMode.FOLLOW_ENGINE)
                else onPitchModeChange(TtsProsodyMode.FOLLOW_ENGINE)
            }
        } else null
        val spec = LiquidGlassMenuSpec(
            anchorBounds = Rect.Zero,
            sourceId = id,
            width = when (kind) {
                TtsMenuKind.Timer -> 144.dp
                else -> 192.dp
            },
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
    val actionScrollState = rememberScrollState()
    LaunchedEffect(actionsExpanded) {
        if (actionsExpanded) actionScrollState.scrollTo(0)
    }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { capsuleBounds = it.boundsInWindow() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            AnimatedVisibility(
                visible = actionsExpanded && !readerMenuVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None
            ) {
                // Children share this visibility transition, so the row stays in place
                // until the last capsule finishes its reverse-order exit.
                fun Modifier.staggeredAction(index: Int): Modifier {
                    val enterDelay = if (motionEnabled) index * 32 else 0
                    val exitDelay = if (motionEnabled) (5 - index) * 32 else 0
                    val enterSpec = tween<Float>(if (motionEnabled) 240 else 0, enterDelay)
                    val exitSpec = tween<Float>(if (motionEnabled) 160 else 0, exitDelay)
                    return animateEnterExit(
                        enter = fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.86f,
                            transformOrigin = TransformOrigin(0.5f, 1f)) +
                            slideInVertically(tween(if (motionEnabled) 240 else 0, enterDelay)) { it / 3 },
                        exit = fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = 0.86f,
                            transformOrigin = TransformOrigin(0.5f, 1f)) +
                            slideOutVertically(tween(if (motionEnabled) 160 else 0, exitDelay)) { it / 3 }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = placement?.horizontalInset ?: 0.dp,
                            end = placement?.horizontalInset ?: 0.dp, bottom = 10.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeFraction = (18.dp.toPx() / size.width).coerceIn(0f, 0.5f)
                            val fadeLeft = actionScrollState.value > 0
                            val fadeRight = actionScrollState.value < actionScrollState.maxValue
                            val stops = buildList {
                                add(0f to if (fadeLeft) Color.Transparent else Color.White)
                                if (fadeLeft) add(edgeFraction to Color.White)
                                if (fadeRight) add((1f - edgeFraction).coerceAtLeast(last().first) to Color.White)
                                add(1f to if (fadeRight) Color.Transparent else Color.White)
                            }
                            drawRect(
                                brush = Brush.horizontalGradient(colorStops = stops.toTypedArray()),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(actionScrollState)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TtsActionPill(
                            label = stringResource(R.string.tts_return_to_progress),
                            contentDescription = stringResource(R.string.tts_return_to_reading_page),
                            enabled = actionsExpanded && canReturnToProgress,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(96.dp).staggeredAction(0),
                            onClick = onReturnToProgress
                        )
                        TtsActionPill(
                            label = stringResource(R.string.tts_from_this_page),
                            contentDescription = stringResource(R.string.tts_from_this_page),
                            enabled = actionsExpanded,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(80.dp).staggeredAction(1),
                            onClick = onStartFromCurrentPage
                        )
                        TtsActionPill(
                            label = stringResource(R.string.tts_follow_page_turns),
                            contentDescription = stringResource(R.string.tts_follow_page_turns),
                            enabled = actionsExpanded,
                            active = followUserPageTurns,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(104.dp).staggeredAction(2).semantics {
                                selected = followUserPageTurns
                            },
                            onClick = { onFollowUserPageTurnsChange(!followUserPageTurns) }
                        )
                        TtsTimerPill(
                            contentDescription = stringResource(R.string.tts_timer_label),
                            enabled = actionsExpanded,
                            active = timerActive,
                            remainingMs = sleepTimerRemainingMs,
                            durationMs = sleepTimerDurationMs,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(68.dp).height(48.dp).staggeredAction(3)
                                .liquidGlassMenuAnchor(timerId, LiquidGlassMenuAnchorKind.Embedded),
                            onClick = { openMenu(TtsMenuKind.Timer) }
                        )
                        TtsActionPill(
                            label = stringResource(R.string.tts_speech_rate),
                            contentDescription = stringResource(R.string.tts_speech_rate),
                            enabled = actionsExpanded,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(68.dp).staggeredAction(4)
                                .liquidGlassMenuAnchor(rateId, LiquidGlassMenuAnchorKind.Embedded),
                            onClick = { openMenu(TtsMenuKind.Rate) }
                        )
                        TtsActionPill(
                            label = stringResource(R.string.tts_pitch_label),
                            contentDescription = stringResource(R.string.tts_pitch_label),
                            enabled = actionsExpanded && usesAndroidTts,
                            backgroundColor = panelFallbackColor,
                            contentColor = panelContentColor,
                            glassScrim = glassScrim,
                            forceSolid = forceSolidSurface,
                            modifier = Modifier.width(68.dp).staggeredAction(5)
                                .liquidGlassMenuAnchor(pitchId, LiquidGlassMenuAnchorKind.Embedded),
                            onClick = { openMenu(TtsMenuKind.Pitch) }
                        )
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
            val availableWidth = maxWidth
            val anchorX = placement?.capsuleX ?: 0.dp
            val dockProgress = placement?.dockProgress ?: 0f
            val dock = placement?.dock ?: ReaderTtsDock.NONE
            val expandLeft = placement?.expandLeft ?: false
            val inset = (placement?.horizontalInset ?: 0.dp)
                .coerceAtMost(((availableWidth - 288.dp) / 2).coerceAtLeast(0.dp))
            val actionWidth = 48.dp
            val actionReach = actionWidth * 3 + 16.dp
            val fittedX = if (!actionsExpanded || readerMenuVisible || dock != ReaderTtsDock.NONE) anchorX
                else if (expandLeft) anchorX.coerceAtLeast(inset + actionReach)
                else anchorX.coerceAtMost((availableWidth - inset - 128.dp - actionReach).coerceAtLeast(0.dp))
            // Keep the menu handoff on one vector path.  A second anchor animation
            // made the capsule travel in two visible segments when it moved above
            // the catalog or made room for the action buttons.
            val capsuleX = fittedX
            LaunchedEffect(capsuleX) { placement?.onAnchorChanged?.invoke(capsuleX) }
            androidx.compose.animation.AnimatedVisibility(
                visible = actionsExpanded && !readerMenuVisible && dock == ReaderTtsDock.NONE,
                enter = if (motionEnabled) fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .9f) else EnterTransition.None,
                exit = if (motionEnabled) fadeOut(tween(140)) else ExitTransition.None,
                modifier = Modifier.offset(x = if (expandLeft) capsuleX - actionReach else capsuleX + 136.dp)
            ) {
                Row(Modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        Triple(AppIcons.SkipBackFilled, R.string.tts_previous_sentence, onSkipBackward),
                        Triple(AppIcons.SkipForwardFilled, R.string.tts_next_sentence, onSkipForward),
                        Triple(AppIcons.X, R.string.tts_stop, stopListening)
                    ).forEach { (icon, description, action) ->
                        TtsIconPill(icon, stringResource(description), enabled = actionsExpanded,
                            backgroundColor = panelFallbackColor, contentColor = panelContentColor,
                            glassScrim = glassScrim, forceSolid = forceSolidSurface,
                            modifier = Modifier.size(actionWidth), onClick = action)
                    }
                }
            }
            Box(Modifier.offset(x = capsuleX).width(placement?.capsuleWidth ?: 128.dp).height(48.dp)
                .then(placement?.dragModifier ?: Modifier)) {
            val handleRight = capsuleX > availableWidth / 2
            if (dockProgress < 1f) LiquidGlassSurface(
                role = com.huangder.lumibooks.ui.theme.AppSurfaceRole.Control,
                controlEdge = true,
                shape = AppRoundedCornerShape(24.dp),
                fallbackColor = panelFallbackColor,
                contentScrimColor = glassScrim,
                forceFallback = forceSolidSurface,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .width(128.dp)
                    .height(48.dp)
                    .graphicsLayer { alpha = 1f - dockProgress; clip = true }
                    .testTag("reader-tts-capsule")
                    .semantics { stateDescription = chapterTitle },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPlayPause,
                        enabled = playbackState != TtsPlaybackState.INITIALIZING && dock == ReaderTtsDock.NONE,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (playbackState == TtsPlaybackState.INITIALIZING) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = panelContentColor)
                        } else {
                            Icon(
                                if (playbackState == TtsPlaybackState.PLAYING) AppIcons.PauseFilled else AppIcons.PlayFilled,
                                stringResource(if (playbackState == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play),
                                tint = panelContentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { menuHost?.dismiss(); onOpenFullscreen() },
                        enabled = playbackState != TtsPlaybackState.IDLE && dock == ReaderTtsDock.NONE,
                        colors = IconButtonDefaults.iconButtonColors(disabledContentColor = panelContentColor),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(AppIcons.CornersOut, stringResource(R.string.tts_fullscreen),
                            tint = panelContentColor, modifier = Modifier.size(18.dp))
                    }
                    val toggleActions = {
                            menuHost?.dismiss()
                            if (!readerMenuVisible) actionsExpanded = !actionsExpanded
                    }
                    val exitLabel = stringResource(R.string.tts_stop)
                    val moreLabel = stringResource(if (actionsExpanded) R.string.tts_collapse_controls else R.string.tts_expand_controls)
                    Box(
                        modifier = Modifier.size(40.dp)
                            .combinedClickable(
                                enabled = dock == ReaderTtsDock.NONE,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = toggleActions,
                                onDoubleClick = stopListening
                            )
                            .semantics {
                                selected = actionsExpanded
                                contentDescription = moreLabel
                                customActions = listOf(CustomAccessibilityAction(exitLabel) { stopListening(); true })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp)
                                .background(panelContentColor.copy(alpha = if (actionsExpanded) 0.16f else 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.DotsThreeVertical,
                                null,
                                tint = panelContentColor,
                                modifier = Modifier.size(18.dp).rotate(90f)
                            )
                        }
                    }
                }
            }
            if (dockProgress > 0f) {
                val restoreLabel = stringResource(R.string.tts_restore_reader_controls)
                Canvas(Modifier.align(if (handleRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(40.dp, 48.dp)
                    .testTag("reader-tts-edge-handle")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Button, onClickLabel = restoreLabel) { placement?.onRestore?.invoke() }
                    .semantics { contentDescription = restoreLabel }) {
                    val right = handleRight
                    val path = Path().apply {
                        val endpoint = if (right) size.width - 12.dp.toPx() else 12.dp.toPx()
                        val control = if (right) endpoint - 8.dp.toPx() else endpoint + 8.dp.toPx()
                        moveTo(endpoint, 4.dp.toPx())
                        quadraticTo(control, size.height / 2f, endpoint, size.height - 4.dp.toPx())
                    }
                    drawPath(path, panelContentColor.copy(alpha = .22f * dockProgress),
                        style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
    active: Boolean = false,
    backgroundColor: Color,
    contentColor: Color,
    glassScrim: Color,
    forceSolid: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = AppColors.Accent
    LiquidGlassSurface(
        role = com.huangder.lumibooks.ui.theme.AppSurfaceRole.Control,
        controlEdge = true,
        shape = AppRoundedCornerShape(28.dp),
        fallbackColor = if (active) accent else backgroundColor,
        contentScrimColor = if (active) accent.copy(alpha = 0.86f) else glassScrim,
        tintColor = accent.takeIf { active },
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
            color = (if (active) AppColors.OnAccent else contentColor)
                .copy(alpha = if (enabled) 1f else 0.38f),
            fontSize = AppType.Caption,
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
    enabled: Boolean = true,
    backgroundColor: Color,
    contentColor: Color,
    glassScrim: Color,
    forceSolid: Boolean,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        role = com.huangder.lumibooks.ui.theme.AppSurfaceRole.Control,
        controlEdge = true,
        shape = AppRoundedCornerShape(28.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassScrim,
        forceFallback = forceSolid,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(48.dp),
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

@Composable
private fun TtsTimerPill(
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean,
    remainingMs: Long?,
    durationMs: Long?,
    backgroundColor: Color,
    contentColor: Color,
    glassScrim: Color,
    forceSolid: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        role = com.huangder.lumibooks.ui.theme.AppSurfaceRole.Control,
        controlEdge = true,
        shape = AppRoundedCornerShape(28.dp),
        fallbackColor = backgroundColor,
        contentScrimColor = glassScrim,
        forceFallback = forceSolid,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(48.dp).semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            role = Role.Button
        },
        contentAlignment = Alignment.Center
    ) {
        if (active && remainingMs != null) {
            val timerProgressColor = AppColors.Accent
            val progress = (remainingMs.toFloat() / (durationMs ?: remainingMs).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                Canvas(Modifier.size(44.dp)) {
                    drawArc(
                        color = timerProgressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Text(formatSleepTimer(remainingMs), color = contentColor, fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        } else {
            Icon(AppIcons.MoonStars, contentDescription, tint = contentColor, modifier = Modifier.size(21.dp))
        }
    }
}

internal enum class TtsMenuKind { Rate, Pitch, Timer }

private data class TtsMenuChoice(
    val label: String,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
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
        modifier.heightIn(min = 44.dp).clip(AppRoundedCornerShape(10.dp))
            .then(if (choice.selected) Modifier.background(AppColors.Accent.copy(alpha = 0.14f)) else Modifier)
        .clickable(enabled = enabled && choice.enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                select(choice.action)
            }.padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(choice.label, color = when {
            choice.destructive -> Color(0xFFE53935)
            choice.selected -> AppColors.Accent
            else -> contentColor
        }, fontSize = AppType.Footnote, fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Medium,
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

