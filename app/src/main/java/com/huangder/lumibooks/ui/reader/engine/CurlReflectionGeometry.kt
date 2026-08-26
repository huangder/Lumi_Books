package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.sqrt

internal class CurlReflectionFrame {
    val matrixValues = FloatArray(9).apply { this[8] = 1f }

    fun mapX(x: Float, y: Float): Float =
        matrixValues[0] * x + matrixValues[1] * y + matrixValues[2]

    fun mapY(x: Float, y: Float): Float =
        matrixValues[3] * x + matrixValues[4] * y + matrixValues[5]
}

/** Reflection across the fold line halfway between the original and dragged page edges. */
internal object CurlReflectionGeometry {
    private const val MIN_DISTANCE_SQUARED = 0.01f

    fun evaluate(
        cornerX: Float,
        cornerY: Float,
        touchX: Float,
        touchY: Float,
        out: CurlReflectionFrame
    ): Boolean {
        val deltaX = cornerX - touchX
        val deltaY = cornerY - touchY
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (!distanceSquared.isFinite() || distanceSquared < MIN_DISTANCE_SQUARED) return false

        val inverseDistance = 1f / sqrt(distanceSquared)
        val normalX = deltaX * inverseDistance
        val normalY = deltaY * inverseDistance
        val midpointX = (cornerX + touchX) * 0.5f
        val midpointY = (cornerY + touchY) * 0.5f
        val values = out.matrixValues
        values[0] = 1f - 2f * normalX * normalX
        values[1] = -2f * normalX * normalY
        values[3] = values[1]
        values[4] = 1f - 2f * normalY * normalY
        values[2] = midpointX - values[0] * midpointX - values[1] * midpointY
        values[5] = midpointY - values[3] * midpointX - values[4] * midpointY
        values[6] = 0f
        values[7] = 0f
        values[8] = 1f
        return values.all { it.isFinite() }
    }
}

internal object CurlLayerPolicy {
    fun <T> foldedBack(turningPage: T?, @Suppress("UNUSED_PARAMETER") underPage: T?): T? =
        turningPage
}

internal object ColorMatrixPaperTone {
    fun dim(color: Int): Int {
        val alpha = (((color ushr 24) and 0xFF) * 0.96f).toInt().coerceIn(0, 255)
        val red = (((color ushr 16) and 0xFF) * 0.91f + 8f).toInt().coerceIn(0, 255)
        val green = (((color ushr 8) and 0xFF) * 0.91f + 8f).toInt().coerceIn(0, 255)
        val blue = ((color and 0xFF) * 0.91f + 8f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
