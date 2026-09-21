package com.huangder.lumibooks.ui.reader.engine

internal enum class PageTurnRole { PREVIOUS, CURRENT, NEXT }

/** Sheet ownership follows reading order, independently of the physical X axis. */
internal data class PageTurnLayers(val upper: PageTurnRole, val lower: PageTurnRole)

internal fun pageTurnLayers(direction: PageAnimationController.Direction): PageTurnLayers =
    when (direction) {
        PageAnimationController.Direction.PREV ->
            PageTurnLayers(PageTurnRole.PREVIOUS, PageTurnRole.CURRENT)
        else -> PageTurnLayers(PageTurnRole.CURRENT, PageTurnRole.NEXT)
    }

/** Child drawing order for a live page turn, from back to front. */
internal fun pageTurnDrawingOrder(
    direction: PageAnimationController.Direction
): List<PageTurnRole> {
    val layers = pageTurnLayers(direction)
    return listOf(layers.lower, layers.upper)
}

internal data class HorizontalPageFrame(
    val previousX: Float,
    val currentX: Float,
    val nextX: Float,
    val layers: PageTurnLayers
)

internal fun horizontalPageFrame(
    direction: PageAnimationController.Direction,
    offset: Float,
    width: Float,
    reversed: Boolean
): HorizontalPageFrame {
    val extent = width.coerceAtLeast(0f)
    val nextSign = if (reversed) 1f else -1f
    val sign = if (direction == PageAnimationController.Direction.PREV) -nextSign else nextSign
    // A reversal can leave one frame's pointer offset on the other side of the origin.
    val distance = (offset * sign).coerceIn(0f, extent) * sign
    return when (direction) {
        PageAnimationController.Direction.NEXT -> HorizontalPageFrame(
            nextSign * extent, distance, (-sign * extent + distance) * 0.3f,
            pageTurnLayers(direction)
        )
        PageAnimationController.Direction.PREV -> HorizontalPageFrame(
            -sign * extent + distance, distance * 0.3f, -nextSign * extent,
            pageTurnLayers(direction)
        )
        PageAnimationController.Direction.NONE -> HorizontalPageFrame(
            nextSign * extent, 0f, -nextSign * extent, pageTurnLayers(direction)
        )
    }
}
