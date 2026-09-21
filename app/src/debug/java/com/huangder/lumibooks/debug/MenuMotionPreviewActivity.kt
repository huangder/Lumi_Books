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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Debug-only visual regression fixture; never starts audio or changes saved preferences. */
class MenuMotionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EBookReaderTheme(
                appTheme = intent.getStringExtra("theme") ?: "liquid_glass",
                dynamicColor = intent.getStringExtra("theme") == "material3",
                darkTheme = intent.getBooleanExtra("dark", false),
                eInkMode = intent.getBooleanExtra("eink", false),
                motionPreference = if (intent.getBooleanExtra("reduced", false)) MotionPreference.REDUCED else MotionPreference.STANDARD
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (intent.getBooleanExtra("rtl", false)) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, intent.getFloatExtra("fontScale", 1f))
                ) {
                    if (intent.getBooleanExtra("surfaces", false)) SurfaceSamples()
                    else PreviewMenus(
                        intent.getBooleanExtra("solid", false),
                        intent.getBooleanExtra("transparentBackdrop", false)
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
                    shape = RoundedCornerShape(24.dp), fallbackColor = AppColors.CardBg, backdrop = backdrop,
                    contentScrimColor = if (index == 2) Color.Transparent else AppColors.CardBg.copy(alpha = 0.62f),
                    decorationModifier = if (index == 1) Modifier else null,
                    modifier = Modifier.fillMaxWidth().height(128.dp)
                ) { Text(label, color = AppColors.TextPrimary) }
            }
        }
    }
}

@Composable
private fun PreviewMenus(solid: Boolean, transparentBackdrop: Boolean) {
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
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Menu motion QA", color = AppColors.TextPrimary)
                Text(selected + " / actions=" + actions, color = AppColors.TextPrimary)
                repeat(12) { Text("Reading sample " + it, color = AppColors.TextSecondary) }
            }
        }
        val host = LocalLiquidGlassMenuHost.current!!
        LiquidGlassIconButton(
            imageVector = AppIcons.DotsThreeVertical,
            contentDescription = "Open menu",
            size = 44.dp,
            onClick = {
                host.toggle(LiquidGlassMenuSpec(
                    Rect.Zero, 196.dp,
                    items = (1..12).map { index ->
                        LiquidGlassMenuItem("Option " + index, selected = index == 2) { selected = "Option " + index; actions++ }
                    },
                    sourceId = sourceId,
                    forceSolid = solid
                ))
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
