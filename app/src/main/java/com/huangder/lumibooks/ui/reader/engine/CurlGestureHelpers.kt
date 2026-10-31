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
