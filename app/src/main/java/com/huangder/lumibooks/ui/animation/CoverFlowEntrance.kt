package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

internal object CoverFlowEntranceMotion {
    private val floating = CubicBezierEasing(0.16f, 0.82f, 0.22f, 1f)
    private fun section(progress: Float, start: Float, end: Float): Float =
        floating.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

    fun button(progress: Float, order: Int): Float = section(progress, order * 0.045f, 0.32f + order * 0.045f)
    fun cover(progress: Float, distance: Float): Float {
        val rank = abs(distance).coerceAtMost(5f)
        return if (rank < 0.5f) section(progress, 0.02f, 0.94f)
        else section(progress, 0.12f + rank * 0.045f, 0.73f + rank * 0.045f)
    }
}

@Stable
internal class CoverFlowEntranceState(private val scope: CoroutineScope) {
    val maskAlpha = Animatable(0f)
    val titleAlpha = Animatable(0f)
    val lightProgress = Animatable(0f)
    val sceneProgress = Animatable(1f)
    var active by mutableStateOf(false)
        private set
    var motionEnabled = true
        private set

    fun enter(motionEnabled: Boolean, onCovered: () -> Unit) {
        if (active) return
        active = true
        this.motionEnabled = motionEnabled
        scope.launch {
            try {
                maskAlpha.snapTo(0f)
                titleAlpha.snapTo(0f)
                lightProgress.snapTo(0f)
                coroutineScope {
                    launch { maskAlpha.animateTo(1f, tween(if (motionEnabled) 240 else 60)) }
                    launch {
                        if (motionEnabled) delay(120)
                        titleAlpha.animateTo(1f, tween(if (motionEnabled) 320 else 60))
                    }
                }
                sceneProgress.snapTo(0f)
                onCovered()
                withFrameNanos { }
                withFrameNanos { }
                if (motionEnabled) lightProgress.animateTo(0.35f, tween(320, easing = LinearEasing))
                coroutineScope {
                    launch { maskAlpha.animateTo(0f, tween(if (motionEnabled) 440 else 100)) }
                    launch { titleAlpha.animateTo(0f, tween(if (motionEnabled) 300 else 100)) }
                    launch { lightProgress.animateTo(1f, tween(if (motionEnabled) 850 else 100, easing = LinearEasing)) }
                    launch { sceneProgress.animateTo(1f, tween(if (motionEnabled) 1500 else 100, easing = LinearEasing)) }
                }
            } finally {
                active = false
            }
        }
    }
}

internal val LocalCoverFlowEntrance = staticCompositionLocalOf<CoverFlowEntranceState?> { null }

@Composable
internal fun rememberCoverFlowEntranceState(): CoverFlowEntranceState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverFlowEntranceState(scope) }
}

@Composable
internal fun Modifier.coverFlowEntranceItem(order: Int): Modifier {
    val entrance = LocalCoverFlowEntrance.current ?: return this
    return graphicsLayer {
        val p = CoverFlowEntranceMotion.button(entrance.sceneProgress.value, order)
        alpha = p
        translationY = if (entrance.motionEnabled) (1f - p) * 12.dp.toPx() else 0f
        scaleX = if (entrance.motionEnabled) 0.96f + 0.04f * p else 1f
        scaleY = scaleX
    }
}

@Composable
internal fun CoverFlowEntranceOverlay(state: CoverFlowEntranceState) {
    if (!state.active) return
    val dark = LocalIsDarkTheme.current
    val veil = if (dark) Color(0xFF0C0D10) else Color.White
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(20f)
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
    ) {
        val titleSize = if (maxWidth < 360.dp) 40.sp else if (maxWidth < 520.dp) 48.sp else 64.sp
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = state.maskAlpha.value }.background(veil))
        Column(Modifier.align(Alignment.Center).graphicsLayer {
            alpha = state.titleAlpha.value
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cover Flow", color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                fontSize = titleSize, letterSpacing = 0.sp,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val light = Brush.linearGradient(
                            if (dark) listOf(Color(0xFFA8BFEF), Color(0xFFD8B2DC), Color(0xFFF1BC97),
                                Color(0xFFA4D5D9), Color(0xFFA8BFEF))
                            else listOf(Color(0xFF637AAE), Color(0xFFAC83AF), Color(0xFFD69370),
                                Color(0xFF76A5AA), Color(0xFF637AAE)),
                            start = Offset.Zero, end = Offset(size.width * 2f, size.height * 1.4f)
                        )
                        onDrawWithContent {
                            drawContent()
                            translate(left = -size.width * state.lightProgress.value) {
                                drawRect(light, size = Size(size.width * 3f, size.height), blendMode = BlendMode.SrcIn)
                            }
                        }
                    })
            Text("Beta", color = (if (dark) Color(0xFFB9B7C2) else Color(0xFF787780)).copy(alpha = 0.48f),
                fontFamily = FontFamily.Serif, fontSize = 14.sp, letterSpacing = 0.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
