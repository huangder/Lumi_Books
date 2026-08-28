package com.huangder.lumibooks.util.parser

import kotlin.math.roundToInt

internal data class ReaderImageBounds(
    val width: Int,
    val height: Int
)

/** Shared intrinsic-size policy for images embedded in reader-layout content. */
internal object ReaderImageSizing {
    fun bounds(originalWidth: Int, originalHeight: Int, contentWidth: Int): ReaderImageBounds? {
        if (originalWidth <= 0 || originalHeight <= 0 || contentWidth <= 0) return null
        val width = minOf(originalWidth, contentWidth).coerceAtLeast(1)
        val scale = width.toDouble() / originalWidth.toDouble()
        val height = (originalHeight.toDouble() * scale)
            .roundToInt()
            .coerceAtLeast(1)
        return ReaderImageBounds(width, height)
    }

    /** Keep enough decoded width for the final display bounds without upscaling low-res input. */
    fun decodeSampleSize(originalWidth: Int, originalHeight: Int, contentWidth: Int): Int {
        val target = bounds(originalWidth, originalHeight, contentWidth) ?: return 1
        return (originalWidth / target.width).coerceAtLeast(1)
    }
}
