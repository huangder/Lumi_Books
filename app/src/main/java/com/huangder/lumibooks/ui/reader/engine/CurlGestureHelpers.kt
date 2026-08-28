package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs

/** Horizontal-dominance classifier shared by the native curl hosts. */
internal fun isCurlSwipeIntent(deltaX: Float, deltaY: Float): Boolean =
    abs(deltaX) > abs(deltaY) * 0.55f

/**
 * Bottom-edge horizontal gestures are reserved for Android system navigation.
 * Keep a small dp tolerance beyond the platform gesture inset so page paging
 * does not race the back gesture on devices with different navigation bars.
 */
internal fun isSystemBackGestureStart(
    width: Float,
    height: Float,
    x: Float,
    y: Float,
    density: Float
): Boolean {
    if (!width.isFinite() || !height.isFinite() || !x.isFinite() || !y.isFinite() ||
        !density.isFinite() || width <= 0f || height <= 0f || density <= 0f
    ) return false
    val edgePx = 32f * density
    return y >= height * 0.8f && (x <= edgePx || x >= width - edgePx)
}

internal fun isSystemBackGestureSwipe(deltaX: Float, deltaY: Float): Boolean =
    abs(deltaX) > 8f && isCurlSwipeIntent(deltaX, deltaY)

internal enum class CurlGestureMode {
    EDGE_VERTICAL,
    CORNER_TOP,
    CORNER_BOTTOM
}

/** Locks horizontal drags to an edge curl and diagonal drags to a corner curl. */
internal class CurlGestureModeLock {
    companion object {
        private const val DIAGONAL_SLOPE = 0.42f
        private const val CLASSIFICATION_X_FRACTION = 0.045f
    }

    var mode: CurlGestureMode = CurlGestureMode.EDGE_VERTICAL
        private set
    var isLocked: Boolean = false
        private set

    private var downX = 0f
    private var downY = 0f

    fun begin(x: Float, y: Float) {
        downX = x
        downY = y
        mode = CurlGestureMode.EDGE_VERTICAL
        isLocked = false
    }

    fun lock(
        width: Float,
        height: Float,
        physicalTurnSign: Float,
        deltaX: Float,
        deltaY: Float
    ): CurlGestureMode {
        if (isLocked) return mode
        if (width <= 0f || height <= 0f || physicalTurnSign == 0f ||
            !width.isFinite() || !height.isFinite()
        ) return mode
        if (abs(deltaX) < width * CLASSIFICATION_X_FRACTION) return mode

        val slope = abs(deltaY) / abs(deltaX).coerceAtLeast(1f)
        isLocked = true
        mode = if (slope < DIAGONAL_SLOPE) {
            CurlGestureMode.EDGE_VERTICAL
        } else if (downY < height * 0.5f) {
            CurlGestureMode.CORNER_TOP
        } else {
            CurlGestureMode.CORNER_BOTTOM
        }
        return mode
    }

    fun reset() {
        mode = CurlGestureMode.EDGE_VERTICAL
        isLocked = false
    }
}
