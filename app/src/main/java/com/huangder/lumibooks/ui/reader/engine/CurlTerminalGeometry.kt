package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.hypot

internal object CurlTerminalGeometry {
    fun completionDistance(width: Float, height: Float): Float {
        val safeWidth = width.coerceAtLeast(1f)
        val safeHeight = height.coerceAtLeast(1f)
        return safeWidth + 2f * hypot(safeWidth.toDouble(), safeHeight.toDouble()).toFloat()
    }
}
