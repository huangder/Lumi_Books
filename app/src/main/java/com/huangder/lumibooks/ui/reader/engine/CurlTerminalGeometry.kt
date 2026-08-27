package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.hypot

/** Terminal distance used by direct (WebView) curl rendering. */
internal object CurlTerminalGeometry {
    fun completionDistance(width: Float, height: Float): Float {
        if (!width.isFinite() || !height.isFinite()) return 1f
        val safeWidth = width.coerceAtLeast(1f)
        val safeHeight = height.coerceAtLeast(1f)
        val result = safeWidth.toDouble() +
            2.0 * hypot(safeWidth.toDouble(), safeHeight.toDouble())
        return result.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }
}
