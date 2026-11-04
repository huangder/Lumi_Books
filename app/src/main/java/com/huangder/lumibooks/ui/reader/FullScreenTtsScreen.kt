package com.huangder.lumibooks.ui.reader

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.tts.*
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.bookshelf.loadCoverSwatches
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.util.ReaderBackgroundBlurTransformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

internal data class ListeningScreenState(
    val title: String = "", val author: String = "", val cover: String? = null, val pdf: Boolean = false,
    val playback: TtsPlaybackState = TtsPlaybackState.PAUSED,
    val text: ListeningText = ListeningText(), val subtitles: Boolean = false,
    val rate: Float = 1f, val pitch: Float = 1f,
    val rateMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE,
    val pitchMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE,
    val androidEngine: Boolean = true, val timer: Long? = null,
    val provider: TtsProviderSelection = TtsProviderSelection.SystemDefault,
    val engines: List<InstalledTtsEngine> = emptyList(), val aiAvailable: Boolean = false,
    val chapterBusy: Boolean = false, val engineBusy: Boolean = false
)

internal data class ListeningActions(
    val toggleMode: () -> Unit = {}, val togglePlayback: () -> Unit = {},
    val chapter: (Boolean) -> Unit = {}, val seek: (ListeningSentence) -> Unit = {},
    val progress: (Float) -> Unit = {}, val rate: (Float) -> Unit = {}, val pitch: (Float) -> Unit = {},
    val rateMode: (TtsProsodyMode) -> Unit = {}, val pitchMode: (TtsProsodyMode) -> Unit = {},
    val timer: (Int) -> Unit = {}, val cancelTimer: () -> Unit = {},
    val engine: (TtsProviderSelection) -> Unit = {}
)

@Composable
internal fun FullScreenTtsRoute(model: FullScreenTtsViewModel, onExit: () -> Unit) {
    val book by model.book.collectAsState()
    val playback by model.controller.playbackState.collectAsState()
    val activeBook by model.controller.activeBookId.collectAsState()
    val text by model.text.collectAsState()
    val subtitles by model.subtitleMode.collectAsState()
    val rate by model.controller.speechRate.collectAsState()
    val pitch by model.controller.pitch.collectAsState()
    val rateMode by model.controller.speechRateMode.collectAsState()
    val pitchMode by model.controller.pitchMode.collectAsState()
    val androidEngine by model.controller.usesAndroidTts.collectAsState()
    val timer by model.controller.sleepTimerRemainingMs.collectAsState()
    val provider by model.controller.providerSelection.collectAsState()
    val external by model.settings.externalTtsSettings.collectAsState(ExternalTtsSettings())
    val chapterBusy by model.chapterBusy.collectAsState()
    val engineBusy by model.engineBusy.collectAsState()
    FullScreenTtsScreen(
        ListeningScreenState(book?.title.orEmpty(), book?.author.orEmpty(), book?.coverPath,
            book?.format == BookFormat.PDF, playback, text, subtitles, rate, pitch, rateMode, pitchMode,
            androidEngine, timer, provider, model.engines,
            external.enabled && external.hasRequiredFields && external.consentVersion >= ExternalTtsConfig.CONSENT_VERSION,
            chapterBusy, engineBusy),
        ListeningActions(model::toggleMode, model::togglePlayback, model::chapter, model::seek,
            model::seekProgress, model::rate, model::pitch, model::rateMode, model::pitchMode,
            model.controller::setSleepTimer, model.controller::cancelSleepTimer, model::engine),
        entered = model.entered, onEntered = { model.entered = true },
        sessionEnded = activeBook != model.bookId || playback == TtsPlaybackState.IDLE || !model.isCurrentSession(),
        onExit = onExit
    )
}

@Composable
internal fun FullScreenTtsScreen(
    state: ListeningScreenState, actions: ListeningActions,
    entered: Boolean = false, onEntered: () -> Unit = {}, sessionEnded: Boolean = false, onExit: () -> Unit
) {
    val eInk = LocalEInkMode.current
    val animated = LocalMotionEnabled.current && !eInk
    val reduce = LocalReduceTransparency.current || eInk
    val foreground = if (eInk) Color.Black else Color.White
    val scope = rememberCoroutineScope()
    val motion = remember { ListeningMotion(entered || !animated) }
    var transition by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val latestExit by rememberUpdatedState(onExit)
    val menuHost = LocalLiquidGlassMenuHost.current
    val menuOpen = menuHost?.activeMenu != null
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var draggingProgress by remember { mutableStateOf(false) }
    var scrollingSubtitles by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    val touch = { activity++; controlsVisible = true }
    var predictive by remember { mutableFloatStateOf(0f) }
    fun dismiss(progress: Float? = null, velocity: Float = 0f) {
        if (closing || completed) return
        closing = true
        transition?.cancel()
        transition = scope.launch {
            motion.background.snapTo(progress ?: max(motion.background.value, predictive * .65f))
            dragProgress = null
            motion.hide(animated, velocity)
            if (!completed) { completed = true; latestExit() }
        }
    }
    fun close() = dismiss()
    val back = ConfigurableBackHandler(enabled = !menuOpen, onBack = ::close)
    SideEffect { predictive = back }
    LaunchedEffect(Unit) {
        onEntered()
        transition = scope.launch { motion.show(animated) }
    }
    LaunchedEffect(sessionEnded) { if (sessionEnded) close() }
    LaunchedEffect(state.subtitles, state.playback, menuOpen, dragProgress != null, draggingProgress, scrollingSubtitles, activity) {
        controlsVisible = true
        if (state.subtitles && state.playback == TtsPlaybackState.PLAYING && !menuOpen &&
            dragProgress == null && !draggingProgress && !scrollingSubtitles) {
            delay(3000)
            controlsVisible = false
        }
    }
    val controlsAlpha by animateFloatAsState(if (controlsVisible) 1f else 0f,
        if (animated) tween(220) else snap(), label = "listening controls")
    val mode by animateFloatAsState(if (state.subtitles) 1f else 0f,
        if (animated) tween(520, easing = ListeningCoverEasing) else snap(), label = "cover subtitle morph")
    val subtitleReveal by animateFloatAsState(if (state.subtitles) 1f else 0f,
        if (animated) tween(560, delayMillis = if (state.subtitles) 90 else 0,
            easing = LinearOutSlowInEasing) else snap(), label = "subtitles follow cover")
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val coverTone by produceState(Color(0xFF7196A9), state.cover) {
        value = loadCoverSwatches(context, state.cover).glow ?: Color(0xFF7196A9)
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("listening-page")) {
        val heightPx = constraints.maxHeight.toFloat()
        val effective = dragProgress ?: max(motion.background.value, back * .65f)
        val currentEffective by rememberUpdatedState(effective)
        val currentMenu by rememberUpdatedState(menuOpen)
        val dragModifier = Modifier.pointerInput(heightPx, animated) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startedOnCover = coverBounds.contains(down.position)
                val eligible = down.position.y < heightPx / 3f || startedOnCover
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var captured = false
                var cancelled = false
                var distance = 0f
                var base = currentEffective
                var consumedByChild = down.isConsumed && !startedOnCover
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) { cancelled = true; break }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.position - down.position
                        if (!captured && change.isConsumed) consumedByChild = true
                        if (!captured && eligible && !currentMenu && !consumedByChild &&
                            delta.y > viewConfiguration.touchSlop && delta.y > abs(delta.x) * 1.2f) {
                            transition?.cancel()
                            closing = false
                            base = currentEffective
                            captured = true
                            scope.launch { motion.stop() }
                            touch()
                        }
                        if (captured) {
                            distance = delta.y.coerceAtLeast(-base * heightPx)
                            dragProgress = (base + distance / heightPx).coerceIn(0f, 1f)
                            if (change.isConsumed) { cancelled = true; break }
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (!captured && !consumedByChild && delta.getDistance() < viewConfiguration.touchSlop) touch()
                            break
                        }
                    }
                } catch (error: CancellationException) {
                    cancelled = true
                    throw error
                } finally {
                    if (captured) {
                        val progress = dragProgress ?: base
                        val velocity = tracker.calculateVelocity().y
                        if (!cancelled && shouldDismissListening(distance, heightPx, velocity, density.density)) {
                            dismiss(progress, velocity / heightPx)
                        } else {
                            transition = scope.launch {
                                motion.background.snapTo(progress)
                                dragProgress = null
                                closing = false
                                motion.restore(animated)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().then(dragModifier)) {
            // One cached, downsampled blur. Translation never recomputes bitmap pixels.
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = effective * heightPx; clip = true }
                .background(if (eInk) Color.White else Color(0xFF789FB3))) {
                if (!reduce && state.cover != null) {
                    val request = remember(state.cover) {
                        ImageRequest.Builder(context).data(state.cover).size(400)
                            .transformations(ReaderBackgroundBlurTransformation(48)).build()
                    }
                    AsyncImage(request, null, Modifier.fillMaxSize().scale(1.2f), contentScale = ContentScale.Crop)
                }
                if (!eInk) Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x147AABC6), Color(0x337AABC6), Color(0xC0DFE5E7)))))
            }
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 16.dp)) {
                val w = maxWidth.coerceAtMost(480.dp)
                val h = maxHeight
                val xInset = (maxWidth - w) / 2
                val controlsLeft = xInset + w * .073f
                val controlsWidth = w * .854f
                val playbackY = (h - 48.dp).coerceAtLeast(0.dp)
                val progressY = (playbackY - 72.dp).coerceAtLeast(0.dp)
                val settingsY = (progressY - 54.dp).coerceAtLeast(0.dp)
                fun layer(index: Int, opacity: Float = 1f): Modifier = Modifier.graphicsLayer {
                    translationY = effective * heightPx * (1f + index * .025f) + motion.groups[index].value * heightPx * .18f
                    alpha = (1f - motion.groups[index].value).coerceIn(0f, 1f) * opacity
                }
                val largeW = minOf(w * .646f, h * .40f / 1.342f)
                val smallW = w * .146f
                val coverW = lerpDp(largeW, smallW, mode)
                val coverH = coverW * 1.342f
                val coverX = lerpDp((w - largeW) / 2, w * .0925f, mode)
                val coverY = lerpDp(h * .12f, h * .055f, mode)
                Box(Modifier.offset(x = xInset + coverX, y = coverY).size(coverW, coverH)
                    .then(layer(0)).zIndex(1f).testTag("listening-cover").onGloballyPositioned { coverBounds = it.boundsInRoot() }
                    .shadow(if (eInk) 0.dp else 24.dp, AppRoundedCornerShape(8.dp))
                    .clip(AppRoundedCornerShape(8.dp)).background(Color(0xFF44596F))
                    .clickable(enabled = !closing, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        touch(); actions.toggleMode()
                    }) {
                    if (state.cover != null) AsyncImage(state.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(AppIcons.BookOpen, null, Modifier.align(Alignment.Center).size(coverW * .4f), tint = Color.White.copy(.8f))
                }
                Column(Modifier.offset(x = xInset + lerpDp(w * .087f, w * .287f, mode))
                    .width(lerpDp(w * .826f, w * .568f, mode))
                    .layout { measurable, limits ->
                        val metadata = measurable.measure(limits)
                        val coverBottom = (h * .12f + largeW * 1.342f).toPx()
                        val centered = (coverBottom + settingsY.toPx() - metadata.height) / 2f
                        val y = centered + ((h * .065f).toPx() - centered) * mode
                        layout(metadata.width, metadata.height) { metadata.placeRelative(0, y.roundToInt()) }
                    }.testTag("listening-metadata").then(layer(1)).zIndex(1f)) {
                    val metadataColor = if (eInk) Color.Black else Color(0xFF182C35)
                    Text(state.title, color = metadataColor, fontSize = (20f - mode * 4f).sp,
                        fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = (28f - mode * 5f).sp,
                        textAlign = if (mode < .5f) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(state.author, color = metadataColor.copy(.5f), fontSize = (13f - mode).sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = if (mode < .5f) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth())
                }
                val subtitleTop = h * .075f
                val focusInset = h * .19f - subtitleTop
                // Keep one full-height layout; only the mask moves as controls fade.
                val subtitleEnd = lerpDp(h, settingsY - 8.dp, controlsAlpha)
                Box(Modifier.offset(xInset + w * .09f, subtitleTop).width(w * .82f)
                    .height((h - subtitleTop).coerceAtLeast(0.dp))
                    .testTag("listening-subtitles")
                    .then(layer(1, subtitleReveal))
                    .graphicsLayer { translationY = (1f - subtitleReveal) * 112.dp.toPx() }
                    .listeningFadingEdges(focusInset, 96.dp, subtitleEnd - subtitleTop, !eInk)) {
                    if (mode > .01f || subtitleReveal > .01f) ListeningSubtitles(state.text, animated, reduce, foreground,
                        enabled = state.subtitles && !closing, focusInset = focusInset,
                        onScrolling = { scrollingSubtitles = it }, onTouch = touch, onSeek = actions.seek)
                }
                val controlsEnabled = controlsVisible && !closing && effective < .1f
                ListeningSettings(state, actions, controlsEnabled, touch, foreground,
                    Modifier.offset(controlsLeft, settingsY).width(controlsWidth).testTag("listening-settings")
                        .then(layer(2, controlsAlpha)))
                Column(Modifier.offset(controlsLeft, progressY).width(controlsWidth)
                    .then(layer(3, controlsAlpha))) {
                    ListeningProgress(state.text.progress, controlsEnabled, animated, foreground, if (eInk) Color.Black else coverTone,
                        onDragging = { draggingProgress = it; touch() }, onSeek = actions.progress)
                    Text(if (state.text.chapterCount > 0) stringResource(
                        if (state.pdf) R.string.tts_full_page else R.string.tts_full_chapter,
                        state.text.chapter + 1, state.text.chapterCount) else "",
                        color = foreground.copy(.55f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Row(Modifier.offset(xInset, playbackY).width(w).testTag("listening-playback").then(layer(4, controlsAlpha)),
                    horizontalArrangement = Arrangement.spacedBy(w * .10f, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    ListeningPressControl(stringResource(if (state.pdf) R.string.tts_full_previous_page else R.string.tts_full_previous_chapter),
                        controlsEnabled && !state.chapterBusy && state.text.chapter > 0, animated, foreground,
                        direction = -1, onClick = { touch(); actions.chapter(false) }) {
                        Icon(AppIcons.SkipBackFilled, null, Modifier.size(28.dp), tint = foreground)
                    }
                    ListeningPressControl(stringResource(if (state.playback == TtsPlaybackState.PLAYING) R.string.tts_pause else R.string.tts_play),
                        controlsEnabled && state.playback != TtsPlaybackState.INITIALIZING, animated, foreground,
                        onClick = { touch(); actions.togglePlayback() }) {
                        AnimatedContent(state.playback == TtsPlaybackState.PLAYING,
                            transitionSpec = { (fadeIn(if (animated) tween(140) else snap()) + scaleIn(if (animated) tween(140) else snap(), initialScale = .7f))
                                .togetherWith(fadeOut(if (animated) tween(140) else snap()) + scaleOut(if (animated) tween(140) else snap(), targetScale = .7f)) },
                            label = "play pause") { playing ->
                            Icon(if (playing) AppIcons.PauseFilled else AppIcons.PlayFilled, null, Modifier.size(38.dp), tint = foreground)
                        }
                    }
                    ListeningPressControl(stringResource(if (state.pdf) R.string.tts_full_next_page else R.string.tts_full_next_chapter),
                        controlsEnabled && !state.chapterBusy && (state.text.chapterCount == 0 || state.text.chapter + 1 < state.text.chapterCount),
                        animated, foreground, direction = 1, onClick = { touch(); actions.chapter(true) }) {
                        Icon(AppIcons.SkipForwardFilled, null, Modifier.size(28.dp), tint = foreground)
                    }
                }
                Box(Modifier.align(Alignment.TopCenter).then(layer(0))) {
                    ListeningPressControl(stringResource(R.string.tts_full_close), !closing, animated, foreground,
                        onClick = ::close) { Box(Modifier.size(35.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(foreground.copy(.5f))) }
                }
                Box(Modifier.offset(xInset + w * .83f, h * .02f).then(layer(0))) {
                    ListeningPressControl(stringResource(if (state.subtitles) R.string.tts_full_cover else R.string.tts_full_subtitles),
                        !closing, animated, foreground, onClick = { touch(); actions.toggleMode() }) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(foreground.copy(.2f)),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_listening_lyrics), null, Modifier.size(21.dp), tint = foreground)
                        }
                    }
                }
            }
        }
    }
}

private fun lerpDp(start: Dp, end: Dp, progress: Float) = start + (end - start) * progress

@Composable
private fun ListeningPressControl(
    label: String, enabled: Boolean, animated: Boolean, foreground: Color,
    modifier: Modifier = Modifier, pill: Boolean = false, direction: Int = 0,
    onClick: () -> Unit, content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (enabled && pressed) if (pill) .96f else .9f else 1f,
        if (!animated) snap() else if (pressed) tween(70) else spring(.6f, 500f), label = "press scale")
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Hit area is outside the animated child, so scale/nudge never shifts touch targets.
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .semantics { contentDescription = label; role = Role.Button }
        .clickable(enabled = enabled, indication = null, interactionSource = interaction) {
            if (direction != 0 && animated) scope.launch {
                nudge.animateTo(direction * 4f, tween(65)); nudge.animateTo(0f, spring(.6f, 500f))
            }
            onClick()
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = nudge.value * density
            alpha = if (enabled) 1f else .35f
        }.clip(AppRoundedCornerShape(30.dp)).background(
            foreground.copy(alpha = if (pill) if (pressed && enabled) .24f else .13f else if (pressed && enabled) .10f else 0f)),
            contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun ListeningSettings(
    state: ListeningScreenState, actions: ListeningActions, enabled: Boolean, onTouch: () -> Unit,
    foreground: Color, modifier: Modifier
) {
    val host = LocalLiquidGlassMenuHost.current
    val ids = remember { List(4) { Any() } }
    val animated = LocalMotionEnabled.current && !LocalEInkMode.current
    val eInk = LocalEInkMode.current
    val solid = LocalReduceTransparency.current || eInk
    val system = stringResource(R.string.tts_full_system)
    val ai = stringResource(R.string.tts_full_ai)
    val follow = stringResource(R.string.tts_follow_engine)
    val cancel = stringResource(R.string.tts_timer_cancel)
    val engineLabel = when (val selection = state.provider) {
        TtsProviderSelection.SystemDefault -> system
        TtsProviderSelection.AiModel -> ai
        is TtsProviderSelection.AndroidEngine -> state.engines.find { it.packageName == selection.packageName }?.label ?: selection.packageName
    }
    val labels = listOf(stringResource(R.string.tts_full_rate_value, formatListeningNumber(state.rate)),
        stringResource(R.string.tts_full_pitch_value, formatListeningNumber(state.pitch)),
        state.timer?.let { "%02d:%02d".format(it / 60000, it / 1000 % 60) } ?: stringResource(R.string.tts_timer_label),
        engineLabel)
    val descriptions = listOf(stringResource(R.string.tts_speech_rate), stringResource(R.string.tts_pitch_label),
        stringResource(R.string.tts_timer_label), stringResource(R.string.tts_full_engine))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { index ->
            val active = enabled && (index != 1 || state.androidEngine) && (index != 3 || !state.engineBusy)
            ListeningPressControl(descriptions[index], active, animated, foreground,
                Modifier.weight(1f).height(36.dp).liquidGlassMenuAnchor(ids[index]), pill = true,
                onClick = {
                    onTouch()
                    fun option(label: String, selected: Boolean = false, click: () -> Unit) =
                        LiquidGlassMenuItem(label = label, selected = selected, onClick = click)
                    val options = when (index) {
                        0 -> (if (state.androidEngine) listOf(option(follow, state.rateMode == TtsProsodyMode.FOLLOW_ENGINE) {
                            actions.rateMode(TtsProsodyMode.FOLLOW_ENGINE) }) else emptyList()) +
                            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f).map { value ->
                                option("${formatListeningNumber(value)}x", state.rate == value && (!state.androidEngine || state.rateMode == TtsProsodyMode.OVERRIDE)) { actions.rate(value) } }
                        1 -> listOf(option(follow, state.pitchMode == TtsProsodyMode.FOLLOW_ENGINE) { actions.pitchMode(TtsProsodyMode.FOLLOW_ENGINE) }) +
                            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { value ->
                                option("${formatListeningNumber(value)}x", state.pitch == value && state.pitchMode == TtsProsodyMode.OVERRIDE) { actions.pitch(value) } }
                        2 -> listOf(10, 20, 30, 40, 50, 60, 90, 120, 150, 180).map { minutes ->
                            option("$minutes min", state.timer?.let { (it + 59999) / 60000 == minutes.toLong() } == true) { actions.timer(minutes) }
                        } + if (state.timer != null) listOf(option(cancel) { actions.cancelTimer() }) else emptyList()
                        else -> listOf(option(system, state.provider == TtsProviderSelection.SystemDefault) { actions.engine(TtsProviderSelection.SystemDefault) }) +
                            state.engines.map { engine -> option(engine.label, state.provider == TtsProviderSelection.AndroidEngine(engine.packageName)) {
                                actions.engine(TtsProviderSelection.AndroidEngine(engine.packageName)) } } +
                            if (state.aiAvailable) listOf(option(ai, state.provider == TtsProviderSelection.AiModel) { actions.engine(TtsProviderSelection.AiModel) }) else emptyList()
                    }
                    host?.toggle(LiquidGlassMenuSpec(Rect.Zero, if (index == 3) 240.dp else 180.dp,
                        options, sourceId = ids[index], preferAbove = true,
                        surfaceColor = if (eInk) Color.White else Color(0xFF25303F),
                        contentColor = foreground, forceSolid = solid))
                }) {
                Crossfade(labels[index], animationSpec = if (animated) tween(120) else snap(), label = "setting value") { value ->
                    Text(value, color = foreground, fontSize = AppType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp))
                }
            }
        }
    }
}

private fun formatListeningNumber(value: Float) = "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')

@Composable
private fun ListeningProgress(
    progress: Float, enabled: Boolean, animated: Boolean, foreground: Color, tone: Color,
    onDragging: (Boolean) -> Unit, onSeek: (Float) -> Unit
) {
    var draft by remember { mutableStateOf<Float?>(null) }
    val latestDragging by rememberUpdatedState(onDragging)
    val latestSeek by rememberUpdatedState(onSeek)
    val displayedProgress by animateFloatAsState((draft ?: progress).coerceIn(0f, 1f),
        if (animated && draft == null) tween(550, easing = LinearEasing) else snap(), label = "listening progress")
    val latestProgress by rememberUpdatedState(displayedProgress)
    DisposableEffect(Unit) { onDispose { latestDragging(false) } }
    val scale by animateFloatAsState(if (draft == null) 1f else 1.15f,
        if (animated) spring(.65f, 450f) else snap(), label = "progress indicator")
    val description = stringResource(R.string.tts_full_progress)
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp)
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(draft ?: progress, 0f..1f)
            if (!enabled) disabled()
            else setProgress { value -> latestSeek(value.coerceIn(0f, 1f)); true }
        }.pointerInput(enabled) {
            if (enabled) detectHorizontalDragGestures(
                onDragStart = { draft = latestProgress; latestDragging(true) },
                onDragCancel = { draft = null; latestDragging(false) },
                onDragEnd = { draft?.let(latestSeek); draft = null; latestDragging(false) },
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    // Relative movement: touching the far end never jumps playback there.
                    draft = ((draft ?: latestProgress) + delta / size.width).coerceIn(0f, 1f)
                })
        }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(30.dp).testTag("listening-progress-track")
            .clip(RoundedCornerShape(50)).background(foreground.copy(.55f))) {
            Box(Modifier.fillMaxWidth(displayedProgress).fillMaxHeight().testTag("listening-progress-fill")
                .clip(RoundedCornerShape(50)).background(tone.copy(.55f)))
        }
        Text("${(displayedProgress * 100).roundToInt()}%", color = foreground, fontSize = 22.sp,
            fontWeight = FontWeight.Thin,
            modifier = Modifier.padding(start = 14.dp).graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

/** Fade the text itself, so it stays seamless over the moving cover artwork. */
private fun Modifier.listeningFadingEdges(top: Dp, bottom: Dp, end: Dp, enabled: Boolean): Modifier =
    if (!enabled) drawWithContent { clipRect(bottom = end.toPx().coerceIn(0f, size.height)) { this@drawWithContent.drawContent() } }
    else graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val topEnd = (top.toPx() / size.height).coerceIn(.01f, .45f)
            val bottomEnd = (end.toPx() / size.height).coerceIn(topEnd, 1f)
            val bottomStart = (bottomEnd - bottom.toPx() / size.height).coerceAtLeast(topEnd)
            val mask = Brush.verticalGradient(0f to Color.Transparent,
                topEnd * .65f to Color.Black.copy(.015f), topEnd * .85f to Color.Black.copy(.08f),
                topEnd to Color.Black, bottomStart to Color.Black,
                bottomEnd to Color.Transparent, 1f to Color.Transparent)
            onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
        }

@Composable
private fun ListeningSubtitles(
    text: ListeningText, animated: Boolean, reduce: Boolean, foreground: Color,
    enabled: Boolean, focusInset: Dp, onScrolling: (Boolean) -> Unit,
    onTouch: () -> Unit, onSeek: (ListeningSentence) -> Unit
) {
    AnimatedContent(text, contentKey = { it.chapter }, modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState.chapter >= initialState.chapter) 1 else -1
            ((fadeIn(if (animated) tween(300, 70) else snap()) +
                slideInVertically(if (animated) tween(360, easing = LinearOutSlowInEasing) else snap()) { it / 8 * direction })
                .togetherWith(fadeOut(if (animated) tween(160) else snap()) +
                    slideOutVertically(if (animated) tween(200) else snap()) { -it / 12 * direction }))
                .using(SizeTransform(clip = false))
        }, label = "subtitle chapter") { chapter ->
        ListeningSubtitleChapter(chapter, animated, reduce, foreground, enabled && chapter.chapter == text.chapter,
            focusInset, onScrolling, onTouch, onSeek)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningSubtitleChapter(
    text: ListeningText, animated: Boolean, reduce: Boolean, foreground: Color,
    enabled: Boolean, focusInset: Dp, onScrolling: (Boolean) -> Unit,
    onTouch: () -> Unit, onSeek: (ListeningSentence) -> Unit
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = text.focus)
    var browsing by remember { mutableStateOf(false) }
    var returningFromBrowse by remember { mutableStateOf(false) }
    val latestTouch by rememberUpdatedState(onTouch)
    val latestScrolling by rememberUpdatedState(onScrolling)
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { event ->
            if (event is DragInteraction.Start) { browsing = true; returningFromBrowse = false; latestTouch() }
        }
    }
    LaunchedEffect(list, browsing) {
        snapshotFlow { browsing && list.isScrollInProgress }.collect { latestScrolling(it) }
    }
    DisposableEffect(Unit) { onDispose { latestScrolling(false) } }
    val focusId = text.sentences.getOrNull(text.focus)?.id
    LaunchedEffect(focusId) {
        // Browsing alone never seeks. The next whole sentence brings the list back.
        val slowReturn = browsing || returningFromBrowse
        browsing = false
        returningFromBrowse = slowReturn
        if (focusId != null) {
            if (!animated) list.scrollToItem(text.focus)
            else if (slowReturn) list.returnToListeningSentence(text.focus)
            else {
                val target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == focusId }
                if (target != null) list.animateScrollBy(target.offset.toFloat(), spring(.78f, 200f))
                else list.animateScrollToItem(text.focus)
            }
        }
        returningFromBrowse = false
    }
    val clearWhileScrolling = browsing && list.isScrollInProgress
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (text.sentences.isEmpty()) Text(stringResource(R.string.tts_full_empty),
            modifier = Modifier.padding(top = focusInset), color = foreground.copy(.7f), fontSize = 24.sp)
        LazyColumn(state = list, userScrollEnabled = enabled, modifier = Modifier.fillMaxSize().testTag("listening-subtitle-list"),
            contentPadding = PaddingValues(top = focusInset, bottom = (maxHeight - focusInset).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(text.sentences, key = { _, sentence -> sentence.id }) { index, sentence ->
                val relative = index - text.focus
                val lag = remember { Animatable(0f) }
                LaunchedEffect(focusId) {
                    if (animated && relative > 0 && !browsing) {
                        lag.animateTo(12f, tween(65))
                        delay(VisualMotion.delayMillis((relative * 35).coerceAtMost(140)).toLong())
                        lag.animateTo(0f, spring(.78f, 200f))
                    } else lag.snapTo(0f)
                }
                val opacity by animateFloatAsState(if (relative == 0) 1f else maxOf(.16f, .65f - abs(relative) * .1f),
                    if (animated) tween(220) else snap(), label = "sentence opacity")
                val blur by animateFloatAsState(if (relative > 0 && !clearWhileScrolling && !reduce)
                    minOf(relative * .65f, 2.5f) else 0f,
                    if (animated && !clearWhileScrolling) tween(140) else snap(), label = "sentence blur")
                Text(sentence.segment.text, color = foreground, fontSize = 24.sp, lineHeight = 37.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().graphicsLayer {
                        translationY = if (clearWhileScrolling) 0f else lag.value * density
                        alpha = opacity
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > .01f)
                            BlurEffect(blur * density, blur * density, TileMode.Decal) else null
                    }.testTag("sentence-${sentence.id}")
                        .combinedClickable(enabled = enabled, indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onDoubleClick = { onTouch(); onSeek(sentence) }, onClick = onTouch))
            }
        }
    }
}

/** Recalculate the remaining distance as variable-height sentences enter the viewport. */
private suspend fun LazyListState.returnToListeningSentence(index: Int) {
    scroll {
        val layout = LazyLayoutScrollScope(this@returnToListeningSentence, this)
        var previous = 0f
        animate(0f, 1f, animationSpec = tween(1100, easing = FastOutSlowInEasing)) { progress, _ ->
            val portion = if (previous < 1f) ((progress - previous) / (1f - previous)).coerceIn(0f, 1f) else 0f
            layout.scrollBy(layout.calculateDistanceTo(index) * portion)
            previous = progress
        }
        layout.snapToItem(index)
    }
}
