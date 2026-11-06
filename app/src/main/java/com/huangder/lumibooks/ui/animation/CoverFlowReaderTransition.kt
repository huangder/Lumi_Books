package com.huangder.lumibooks.ui.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
    fun readerAlpha(progress: Float, zoom: Boolean = false): Float =
        if (zoom) smooth((progress - 0.18f) / 0.72f) else smooth((progress - 0.34f) / 0.66f)
    /** Zoom-only opening grows the page from further back, so the hand-off reads as a zoom. */
    fun readerScale(progress: Float, zoom: Boolean = false): Float =
        if (zoom) 0.84f + 0.16f * smooth(progress) else 0.94f + 0.06f * readerAlpha(progress)
    /** Zoom-only opening: the shelf grows towards the reader and is gone before the text settles. */
    fun libraryZoomScale(progress: Float): Float = 1f + 0.12f * smooth(progress)
    fun libraryZoomFade(progress: Float): Float = smooth(progress / 0.55f)
}

@Composable
internal fun CoverFlowReaderOverlay(transition: BookReaderTransitionState, modifier: Modifier) {
    if (transition.phase == BookReaderTransitionPhase.Library || transition.phase == BookReaderTransitionPhase.Reader) return
    val source = transition.sourceBounds ?: return
    val book = transition.coverBook ?: return
    val density = LocalDensity.current
    val bg = AppColors.WindowBg
    val zoom = transition.zoomOnly
    // A zoom grows the reader's own loading page (its paper colour) with the text still hidden.
    val page = if (zoom) transition.readerPaperColorArgb?.let { Color(readerTransitionPaperColor(it)) } ?: AppColors.PageBg
        else bg
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = transition.coverFlowProgressSnapshot.value
            alpha = CoverFlowReaderMotion.readerAlpha(p, zoom) *
                (1f - transition.readerRevealSnapshot.value.coerceIn(0f, 1f))
            if (zoom) {
                scaleX = CoverFlowReaderMotion.readerScale(p, zoom = true)
                scaleY = scaleX
            }
        }.background(page))
        // A light veil blends the blurred library into the reading surface. It also keeps slow
        // imports coherent after Navigation has disposed the outgoing library destination.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = transition.coverFlowProgressSnapshot.value
            // A zoom carries the shelf itself out of view; the veil would hide that motion.
            alpha = if (transition.zoomOnly) 0f
                else CoverFlowReaderMotion.sideExit(p) * (1f - CoverFlowReaderMotion.readerAlpha(p)) * 0.65f
        }.background(bg))
        if (!transition.zoomOnly) CoverFlowCover(book,
            Modifier.offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                .size(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                .graphicsLayer {
                    val p = transition.coverFlowProgressSnapshot.value
                    transformOrigin = TransformOrigin.Center
                    scaleX = CoverFlowReaderMotion.coverScale(p)
                    scaleY = scaleX
                    alpha = CoverFlowReaderMotion.coverAlpha(p)
                })
        if (zoom || rememberBookLoadingIndicatorVisible(transition)) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(20.dp).graphicsLayer {
                    val p = transition.coverFlowProgressSnapshot.value
                    alpha = (1f - transition.readerRevealSnapshot.value.coerceIn(0f, 1f)) *
                        (if (zoom) CoverFlowReaderMotion.readerAlpha(p, zoom = true) else 1f)
                },
                color = AppColors.Accent,
                strokeWidth = 2.dp
            )
        }
    }
}
