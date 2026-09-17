package com.huangder.lumibooks.ui.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.bookshelf.CoverFlowCover
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
    fun coverScale(progress: Float): Float = 1f + 0.7f * progress.coerceIn(0f, 1f)
    fun readerAlpha(progress: Float): Float = smooth((progress - 0.34f) / 0.66f)
    fun readerScale(progress: Float): Float = 0.94f + 0.06f * readerAlpha(progress)
}

@Composable
internal fun CoverFlowReaderOverlay(transition: BookReaderTransitionState, modifier: Modifier) {
    if (transition.phase == BookReaderTransitionPhase.Library || transition.phase == BookReaderTransitionPhase.Reader) return
    val source = transition.sourceBounds ?: return
    val book = transition.coverBook ?: return
    val density = LocalDensity.current
    val bg = AppColors.WindowBg
    Box(modifier.fillMaxSize()) {
        // A light veil blends the blurred library into the reading surface. It also keeps slow
        // imports coherent after Navigation has disposed the outgoing library destination.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = transition.coverFlowProgressSnapshot.value
            alpha = (CoverFlowReaderMotion.sideExit(p) * (1f - CoverFlowReaderMotion.readerAlpha(p)) * 0.65f)
        }.background(bg))
        CoverFlowCover(book, transition.coverDownloadState,
            Modifier.offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                .size(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                .graphicsLayer {
                    val p = transition.coverFlowProgressSnapshot.value
                    transformOrigin = TransformOrigin.Center
                    scaleX = CoverFlowReaderMotion.coverScale(p)
                    scaleY = scaleX
                    alpha = CoverFlowReaderMotion.coverAlpha(p)
                })
        if (transition.phase == BookReaderTransitionPhase.ReaderLoading && !transition.readerReady) {
            CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).size(18.dp),
                color = AppColors.Accent, strokeWidth = 2.dp)
        }
    }
}
