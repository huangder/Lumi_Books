package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Implicit "there is more" cue for scrolling menus: the edge that still has content behind it
 * softly fades out, and the fade eases away as that edge is reached. No arrows or labels.
 *
 * Apply it to the scroll viewport (before `verticalScroll`, or on the `LazyColumn` itself).
 */
internal fun Modifier.scrollEdgeFade(state: ScrollState, fadeLength: Dp = DefaultFadeLength): Modifier =
    edgeFade(fadeLength) { fadePx ->
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) {
            0f to 0f
        } else {
            (state.value / fadePx).coerceIn(0f, 1f) to ((max - state.value) / fadePx).coerceIn(0f, 1f)
        }
    }

internal fun Modifier.scrollEdgeFade(state: LazyListState, fadeLength: Dp = DefaultFadeLength): Modifier =
    edgeFade(fadeLength) { fadePx ->
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@edgeFade 0f to 0f
        val top = if (state.firstVisibleItemIndex > 0) 1f
        else (state.firstVisibleItemScrollOffset / fadePx).coerceIn(0f, 1f)
        val last = visible.last()
        val bottom = if (last.index < info.totalItemsCount - 1) 1f
        else ((last.offset + last.size + info.afterContentPadding - info.viewportEndOffset) / fadePx).coerceIn(0f, 1f)
        top to bottom
    }

private val DefaultFadeLength = 28.dp

/** The very edge keeps some opacity so a peeking row stays readable. */
private const val EdgeMinAlpha = 0.18f

private inline fun Modifier.edgeFade(
    fadeLength: Dp,
    crossinline amounts: (fadePx: Float) -> Pair<Float, Float>
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = fadeLength.toPx().coerceAtMost(size.height / 3f).coerceAtLeast(1f)
        val (top, bottom) = amounts(fadePx)
        if (top > 0f) fadeEdge(fadePx, top, atTop = true)
        if (bottom > 0f) fadeEdge(fadePx, bottom, atTop = false)
    }

private fun ContentDrawScope.fadeEdge(fadePx: Float, amount: Float, atTop: Boolean) {
    val edgeAlpha = 1f - (1f - EdgeMinAlpha) * amount
    val edge = Color.Black.copy(alpha = edgeAlpha)
    val startY = if (atTop) 0f else size.height - fadePx
    val brush = if (atTop) {
        Brush.verticalGradient(listOf(edge, Color.Black), startY = 0f, endY = fadePx)
    } else {
        Brush.verticalGradient(listOf(Color.Black, edge), startY = startY, endY = size.height)
    }
    drawRect(
        brush = brush,
        topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
        size = androidx.compose.ui.geometry.Size(size.width, fadePx),
        blendMode = BlendMode.DstIn
    )
}
