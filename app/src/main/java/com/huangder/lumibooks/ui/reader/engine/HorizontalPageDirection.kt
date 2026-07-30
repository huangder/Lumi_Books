package com.huangder.lumibooks.ui.reader.engine

internal enum class HorizontalPageDirection {
    NONE,
    NEXT,
    PREVIOUS
}

internal fun horizontalPageDirectionForDelta(
    deltaX: Float,
    threshold: Float,
    reversePageProgress: Boolean
): HorizontalPageDirection {
    val normalDirection = when {
        deltaX > threshold -> HorizontalPageDirection.PREVIOUS
        deltaX < -threshold -> HorizontalPageDirection.NEXT
        else -> HorizontalPageDirection.NONE
    }
    if (!reversePageProgress) return normalDirection
    return when (normalDirection) {
        HorizontalPageDirection.NEXT -> HorizontalPageDirection.PREVIOUS
        HorizontalPageDirection.PREVIOUS -> HorizontalPageDirection.NEXT
        HorizontalPageDirection.NONE -> HorizontalPageDirection.NONE
    }
}

internal fun horizontalPageTurnSign(
    direction: HorizontalPageDirection,
    reversePageProgress: Boolean
): Float {
    val normalSign = when (direction) {
        HorizontalPageDirection.NEXT -> -1f
        HorizontalPageDirection.PREVIOUS -> 1f
        HorizontalPageDirection.NONE -> 0f
    }
    return if (reversePageProgress) -normalSign else normalSign
}
