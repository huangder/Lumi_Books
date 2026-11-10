package com.huangder.lumibooks.ui.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.bookshelf.CoverFlowCover
import com.huangder.lumibooks.ui.reader.readerTransitionPaperColor
import com.huangder.lumibooks.ui.theme.AppColors
import kotlin.math.roundToInt

/** One progress controls side departure, cover dissolve and the incoming text page. */
internal object CoverFlowReaderMotion {
    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun sideExit(progress: Float): Float = smooth(progress / 0.34f)
    fun coverAlpha(progress: Float): Float = 1f - smooth((progress - 0.34f) / 0.56f)
    fun coverScale(progress: Float): Float = 1f + 0.22f * progress.coerceIn(0f, 1f)
    fun readerAlpha(progress: Float): Float = smooth((progress - 0.34f) / 0.66f)
    fun readerScale(progress: Float): Float = 0.94f + 0.06f * readerAlpha(progress)

    fun windowBounds(source: androidx.compose.ui.geometry.Rect, target: androidx.compose.ui.geometry.Rect, progress: Float): androidx.compose.ui.geometry.Rect {
        val fraction = smooth(progress)
        return androidx.compose.ui.geometry.Rect(
            left = source.left + (target.left - source.left) * fraction,
            top = source.top + (target.top - source.top) * fraction,
            right = source.right + (target.right - source.right) * fraction,
            bottom = source.bottom + (target.bottom - source.bottom) * fraction
        )
    }

    fun windowCornerRadius(sourceRadiusDp: Float, progress: Float): Float =
        sourceRadiusDp * (1f - smooth(progress))
}

@Composable
internal fun CoverFlowReaderOverlay(transition: BookReaderTransitionState, modifier: Modifier) {
    if (transition.phase == BookReaderTransitionPhase.Library || transition.phase == BookReaderTransitionPhase.Reader) return
    val source = transition.sourceBounds ?: return
    val book = transition.coverBook ?: return
    val density = LocalDensity.current
    val page = transition.readerPaperColorArgb?.let { Color(readerTransitionPaperColor(it)) }
        ?: AppColors.PageBg
    BoxWithConstraints(modifier.fillMaxSize()) {
        val target = androidx.compose.ui.geometry.Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val p = transition.coverFlowProgressSnapshot.value
        val window = readerWindowPixelBounds(CoverFlowReaderMotion.windowBounds(source, target, p))
        val windowShape = AppRoundedCornerShape(
            CoverFlowReaderMotion.windowCornerRadius(transition.sourceCornerRadiusDp, p).dp
        )
        Box(
            Modifier.offset { IntOffset(window.left.roundToInt(), window.top.roundToInt()) }
                .size(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
                .clip(windowShape)
                .graphicsLayer {
                    // Reveal the live page continuously instead of dropping an opaque
                    // paper layer in a single frame when Ready becomes Reader.
                    alpha = 1f - transition.readerRevealSnapshot.value *
                        (if (transition.phase == BookReaderTransitionPhase.Closing)
                            transition.readerExitAlphaSnapshot.value else 1f)
                }
                .background(page)
        )
        CoverFlowCover(book,
            Modifier.offset { IntOffset(window.left.roundToInt(), window.top.roundToInt()) }
                .size(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
                .clip(windowShape)
                .graphicsLayer { alpha = CoverFlowReaderMotion.coverAlpha(p) })
        if (rememberBookLoadingIndicatorVisible(transition)) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(20.dp).graphicsLayer {
                    alpha = 1f - transition.readerRevealSnapshot.value.coerceIn(0f, 1f)
                },
                color = AppColors.Accent,
                strokeWidth = 2.dp
            )
        }
    }
}
