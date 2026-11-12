package com.huangder.lumibooks.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * Lets a horizontally scrolling row extend through its parent's horizontal padding.
 *
 * Padding on the scroll container clips the row before it reaches the screen or card edge.
 * Apply this modifier to the scroll container, passing the parent's horizontal padding. Then
 * put the same padding back inside the scroll content, for example as LazyRow contentPadding
 * or as padding after horizontalScroll. At rest the first item stays aligned with the
 * surrounding content, and while scrolling the items run to the edge.
 */
fun Modifier.horizontalBleed(start: Dp, end: Dp = start): Modifier {
    if (start <= 0.dp && end <= 0.dp) return this
    return layout { measurable, constraints ->
        val startPx = start.roundToPx().coerceAtLeast(0)
        val endPx = end.roundToPx().coerceAtLeast(0)
        val extra = startPx + endPx
        val widened = if (constraints.hasBoundedWidth) {
            constraints.copy(
                minWidth = constraints.minWidth + extra,
                maxWidth = constraints.maxWidth + extra
            )
        } else constraints
        val placeable = measurable.measure(widened)
        val width = constraints.constrainWidth(placeable.width - extra)
        layout(width, placeable.height) {
            placeable.placeRelative(-startPx, 0)
        }
    }
}
