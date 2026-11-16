package com.huangder.lumibooks.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsProsodyMode
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.reader.TtsPlayerPanel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.huangder.lumibooks.ui.theme.LumiBackgroundScene
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

/** Debug-only visual regression fixture; never starts audio or changes saved preferences. */
class MenuMotionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EBookReaderTheme(
                liquidGlassSamplingQuality = com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
                    .fromStoredValue(intent.getStringExtra("samplingQuality")),
                appTheme = intent.getStringExtra("theme") ?: "liquid_glass",
                lumiBackgroundScene = if (intent.getStringExtra("theme") == "lumi_chan") LumiBackgroundScene.BOOKSHELF else null,
                dynamicColor = intent.getStringExtra("theme") == "material3",
                darkTheme = intent.getBooleanExtra("dark", false),
                eInkMode = intent.getBooleanExtra("eink", false),
                menuAnimationStyle = MenuAnimationStyle.fromStoredValue(intent.getStringExtra("menuStyle")),
                motionPreference = if (intent.getBooleanExtra("reduced", false)) MotionPreference.REDUCED else MotionPreference.STANDARD
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (intent.getBooleanExtra("rtl", false)) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, intent.getFloatExtra("fontScale", 1f))
                ) {
                    if (intent.getBooleanExtra("surfaces", false)) SurfaceSamples()
                    else if (intent.getBooleanExtra("selection", false)) SelectionMenuPreview(
                        dark = intent.getBooleanExtra("dark", false)
                    )
                    else PreviewMenus(
                        intent.getBooleanExtra("solid", false),
                        intent.getBooleanExtra("transparentBackdrop", false),
                        intent.getBooleanExtra("demo", false),
                        intent.getBooleanExtra("open", false),
                        wide = intent.getBooleanExtra("wide", false),
                        loop = intent.getBooleanExtra("loop", false)
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceSamples() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(AppColors.WindowBg))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Default", "No shadow", "No scrim").forEachIndexed { index, label ->
                LiquidGlassSurface(
                    shape = AppRoundedCornerShape(24.dp), fallbackColor = AppColors.CardBg, backdrop = backdrop,
                    contentScrimColor = if (index == 2) Color.Transparent else AppColors.CardBg.copy(alpha = 0.62f),
                    decorationModifier = if (index == 1) Modifier else null,
                    modifier = Modifier.fillMaxWidth().height(128.dp)
                ) { Text(label, color = AppColors.TextPrimary) }
            }
        }
    }
}

/** Reader page with a selection and the selection bar, for recording the bar and its menu. */
@Composable
private fun SelectionMenuPreview(dark: Boolean) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val paper = if (dark) Color(0xFF1E1E1E) else Color(0xFFEFE0C4)
    val ink = if (dark) Color(0xFFD6D6D6) else Color(0xFF3B3226)
    var reappear by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("") }
    val paragraph = "视历史经验的倾向。归根到底，书本知识只是一种间接的知识，来源于千千万万劳动群众实践经验的积累总结。" +
        "瞧不起历史经验，就是瞧不起群众。脱离群众，孤家寡人，你就一事无成。从发展的观点看，人的知识也有一个不断充实的过程。"
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().layerBackdrop(backdrop).background(paper)
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            repeat(4) { Text(paragraph, color = ink, fontSize = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp)) }
            Text(log, color = Color.Red)
        }
        val top = with(density) { 150.dp.toPx() }
        val state = com.huangder.lumibooks.ui.reader.SelectionState(
            0, 0, 0, 2, "间接", 0f, 0f,
            selTopY = top, selBottomY = top + with(density) { 30.dp.toPx() },
            selStartX = with(density) { 150.dp.toPx() }, selEndX = with(density) { 200.dp.toPx() }
        )
        com.huangder.lumibooks.ui.reader.SelectionMenuOverlay(
            state = state, readerTheme = if (dark) "night" else "sepia", glassBackdrop = backdrop,
            isDragging = false, reappearKey = reappear,
            onDismiss = { log = "dismissed"; reappear++ },
            onHighlight = { log = "highlight" }, onNote = { log = "note" }, onExcerpt = { log = "excerpt" },
            onSearch = { log = "search" }, onWebSearch = { log = "web" }, onDictionary = { log = "dictionary" },
            onDictionaryAppSelected = {}, onCopy = { log = "copy" }, onViewNote = {}
        )
    }
}

/** A wide 158x36 selector capsule, like the reader settings pickers. */
@Composable
private fun WideCapsuleTrigger(sourceId: Any, backdrop: com.kyant.backdrop.Backdrop, onClick: () -> Unit, modifier: Modifier) {
    LiquidGlassSurface(
        shape = AppRoundedCornerShape(18.dp),
        fallbackColor = AppColors.CardBg,
        backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.4f),
        onClick = onClick,
        modifier = modifier.liquidGlassMenuAnchor(sourceId, cornerRadius = 18.dp).size(158.dp, 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Theme", color = AppColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Paper  ▾", color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun PreviewMenus(
    solid: Boolean,
    transparentBackdrop: Boolean,
    demo: Boolean,
    open: Boolean,
    wide: Boolean = false,
    loop: Boolean = false
) {
    val backdrop = rememberLayerBackdrop()
    var selected by remember { mutableStateOf("Ready") }
    var actions by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    var timer by remember { mutableStateOf<Long?>(null) }
    val sourceId = remember { Any() }
    LiquidGlassMenuHost(Modifier.fillMaxSize(), backdrop) {
        val background = if (transparentBackdrop) {
            Modifier.background(AppColors.WindowBg).layerBackdrop(backdrop)
        } else Modifier.layerBackdrop(backdrop).background(AppColors.WindowBg)
        Box(Modifier.fillMaxSize().then(background)) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 180.dp, end = 20.dp)
                .size(240.dp, 360.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFFD72544), Color(0xFF743CA6))),
                    AppRoundedCornerShape(16.dp)
                ))
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Menu motion QA", color = AppColors.TextPrimary)
                Text(selected + " / actions=" + actions, color = AppColors.TextPrimary)
                repeat(12) { Text("Reading sample " + it, color = AppColors.TextSecondary) }
            }
        }
        val host = LocalLiquidGlassMenuHost.current!!
        val menu = LiquidGlassMenuSpec(
            Rect.Zero, 220.dp,
            items = (1..12).map { index ->
                LiquidGlassMenuItem(
                    "Option " + index,
                    icon = if (index <= 3) AppIcons.Bookmark.regular else null,
                    selected = index == 2,
                    groupTitle = if (index == 4) "Layout" else null,
                    dividerBefore = index == 9
                ) { selected = "Option " + index; actions++ }
            },
            sourceId = sourceId,
            forceSolid = solid
        )
        LaunchedEffect(demo) {
            if (demo) {
                delay(1200)
                host.show(menu)
                delay(1500)
                host.dismiss()
                delay(800)
                host.show(menu)
                delay(160)
                host.dismiss()
                delay(100)
                host.show(menu)
                delay(1200)
                host.dismiss()
            }
        }
        LaunchedEffect(open) {
            if (open) {
                delay(600)
                host.show(menu)
            }
        }
        LaunchedEffect(loop) {
            // Recording loop: open, return into the trigger, open, vanish.
            while (loop) {
                delay(900)
                host.show(menu)
                delay(1300)
                host.dismiss(LiquidMenuCloseStyle.ReturnToSource)
                delay(900)
                host.show(menu)
                delay(1300)
                host.dismiss(LiquidMenuCloseStyle.Vanish)
            }
        }
        if (wide) {
            WideCapsuleTrigger(
                sourceId = sourceId, backdrop = backdrop, onClick = { host.toggle(menu) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp)
            )
        } else LiquidGlassIconButton(
            imageVector = AppIcons.DotsThreeVertical,
            contentDescription = "Open menu",
            size = 44.dp,
            onClick = {
                host.toggle(menu)
            },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp)
                .liquidGlassMenuAnchor(sourceId)
        )
        TtsPlayerPanel(
            playbackState = if (playing) TtsPlaybackState.PLAYING else TtsPlaybackState.PAUSED,
            speechRate = rate, speechRateMode = TtsProsodyMode.OVERRIDE,
            pitch = pitch, pitchMode = TtsProsodyMode.OVERRIDE, usesAndroidTts = true,
            sleepTimerRemainingMs = timer,
            onPlayPause = { playing = !playing; actions++ }, onStop = { selected = "Stop"; actions++ },
            onSkipForward = { selected = "Next"; actions++ }, onSkipBackward = { selected = "Previous"; actions++ },
            onRateChange = { rate = it; selected = "Rate " + it; actions++ }, onRateModeChange = { selected = "Rate mode " + it; actions++ },
            onPitchChange = { pitch = it; selected = "Pitch " + it; actions++ }, onPitchModeChange = { selected = "Pitch mode " + it; actions++ },
            onSetSleepTimer = { timer = it * 60_000L; selected = "Timer " + it; actions++ },
            onCancelSleepTimer = { timer = null; actions++ },
            readerBackgroundColor = AppColors.CardBg, readerContentColor = AppColors.TextPrimary,
            forceSolidSurface = solid,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp)
        )
    }
}
