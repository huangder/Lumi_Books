package com.huangder.lumibooks.widget

import kotlin.math.floor
import kotlin.math.sqrt

internal data class BitmapDimensions(val width: Int, val height: Int) {
    val pixelCount: Long get() = width.toLong() * height.toLong()
}

/** Keeps each RemoteViews bitmap below one screen of ARGB pixels (Android allows 1.5 screens). */
internal object WidgetBitmapBudget {
    fun maxBitmapPixels(screenWidthPx: Int, screenHeightPx: Int): Long =
        screenWidthPx.coerceAtLeast(1).toLong() * screenHeightPx.coerceAtLeast(1).toLong()

    fun constrain(width: Int, height: Int, maxPixels: Long): BitmapDimensions {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val safeMaxPixels = maxPixels.coerceAtLeast(1L)
        val pixels = safeWidth.toLong() * safeHeight.toLong()
        if (pixels <= safeMaxPixels) return BitmapDimensions(safeWidth, safeHeight)

        val scale = sqrt(safeMaxPixels.toDouble() / pixels.toDouble())
        var scaledWidth = floor(safeWidth * scale).toInt().coerceAtLeast(1)
        var scaledHeight = floor(safeHeight * scale).toInt().coerceAtLeast(1)
        while (scaledWidth.toLong() * scaledHeight > safeMaxPixels) {
            if (scaledWidth >= scaledHeight && scaledWidth > 1) scaledWidth-- else scaledHeight--
        }
        return BitmapDimensions(scaledWidth, scaledHeight)
    }
}
