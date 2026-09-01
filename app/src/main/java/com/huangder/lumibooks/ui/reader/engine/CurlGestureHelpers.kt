package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs

/** Horizontal-dominance classifier shared by the native curl hosts. */
internal fun isCurlSwipeIntent(deltaX: Float, deltaY: Float): Boolean =
    abs(deltaX) > abs(deltaY) * 0.55f

/**
 * Returns true once a gesture has enough horizontal travel to become a curl
 * page turn. Curl gestures are allowed to begin after the long-press window;
 * the caller still uses the movement threshold to keep text selection intact.
 */
internal fun isCurlPageSwipeIntent(
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float
): Boolean {
    if (!deltaX.isFinite() || !deltaY.isFinite() ||
        !touchSlop.isFinite() || touchSlop < 0f
    ) return false
    return abs(deltaX) > touchSlop && isCurlSwipeIntent(deltaX, deltaY)
}

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

private const val CURL_CORNER_BAND_FRACTION = 0.3f

/** Classifies a NEXT gesture from its original finger-down vertical position. */
internal fun curlGestureModeForStartY(
    height: Float,
    downY: Float,
    physicalTurnSign: Float
): CurlGestureMode {
    if (!height.isFinite() || height <= 0f || !downY.isFinite() ||
        !physicalTurnSign.isFinite() || physicalTurnSign >= 0f
    ) return CurlGestureMode.EDGE_VERTICAL

    return when {
        downY <= height * CURL_CORNER_BAND_FRACTION -> CurlGestureMode.CORNER_TOP
        downY >= height * (1f - CURL_CORNER_BAND_FRACTION) -> CurlGestureMode.CORNER_BOTTOM
        else -> CurlGestureMode.EDGE_VERTICAL
    }
}

/**
 * Locks a curl mode once the horizontal paging intent is far enough along to
 * classify without exposing a provisional first frame.
 *
 * PREVIOUS is deliberately always an edge curl. NEXT uses the finger-down
 * vertical band: the top and bottom 30% select the corresponding right corner,
 * while the middle 40% selects the full-height edge curl. The movement slope
 * is intentionally ignored here; it is only used by the outer paging-intent
 * recognizers to decide whether a gesture is horizontal at all.
 */
internal class CurlGestureModeLock {
    companion object {
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
            !width.isFinite() || !height.isFinite() || !physicalTurnSign.isFinite() ||
            !deltaX.isFinite() || !downY.isFinite()
        ) return mode
        if (abs(deltaX) < width * CLASSIFICATION_X_FRACTION) return mode

        isLocked = true
        mode = curlGestureModeForStartY(height, downY, physicalTurnSign)
        return mode
    }

    fun reset() {
        mode = CurlGestureMode.EDGE_VERTICAL
        isLocked = false
    }
}
