package com.huangder.lumibooks.ui.reader.engine

/** A waiting swipe must still point towards its queued page when released. */
internal fun shouldCommitDeferredCurlGesture(
    deltaX: Float,
    elapsedMillis: Long,
    widthPx: Int,
    density: Float,
    direction: PageAnimationController.Direction,
    reversed: Boolean
): Boolean {
    val physicalSign = when (direction) {
        PageAnimationController.Direction.NEXT -> if (reversed) 1f else -1f
        PageAnimationController.Direction.PREV -> if (reversed) -1f else 1f
        PageAnimationController.Direction.NONE -> return false
    }
    val travel = deltaX * physicalSign
    val distanceCommit = travel / widthPx.coerceAtLeast(1) >= 0.14f
    val velocityCommit = travel * 1_000f / elapsedMillis.coerceAtLeast(1L) >= 450f * density
    return distanceCommit || velocityCommit
}
