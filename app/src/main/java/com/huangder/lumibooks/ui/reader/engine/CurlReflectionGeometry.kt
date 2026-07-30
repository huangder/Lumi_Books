package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.sqrt

internal data class CurlReflectionTransform(
    val m00: Float,
    val m01: Float,
    val m02: Float,
    val m10: Float,
    val m11: Float,
    val m12: Float
) {
    fun mapX(x: Float, y: Float): Float = m00 * x + m01 * y + m02

    fun mapY(x: Float, y: Float): Float = m10 * x + m11 * y + m12

    fun matrixValues(): FloatArray = floatArrayOf(
        m00, m01, m02,
        m10, m11, m12,
        0f, 0f, 1f
    )
}

internal object CurlReflectionGeometry {
    private const val MIN_DISTANCE_SQUARED = 0.01f

    fun between(
        cornerX: Float,
        cornerY: Float,
        touchX: Float,
        touchY: Float
    ): CurlReflectionTransform? {
        val deltaX = cornerX - touchX
        val deltaY = cornerY - touchY
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (!distanceSquared.isFinite() || distanceSquared < MIN_DISTANCE_SQUARED) return null

        val inverseDistance = 1f / sqrt(distanceSquared)
        val normalX = deltaX * inverseDistance
        val normalY = deltaY * inverseDistance
        val midpointX = (cornerX + touchX) * 0.5f
        val midpointY = (cornerY + touchY) * 0.5f

        val m00 = 1f - 2f * normalX * normalX
        val m01 = -2f * normalX * normalY
        val m10 = m01
        val m11 = 1f - 2f * normalY * normalY
        val m02 = midpointX - m00 * midpointX - m01 * midpointY
        val m12 = midpointY - m10 * midpointX - m11 * midpointY

        val values = floatArrayOf(m00, m01, m02, m10, m11, m12)
        if (values.any { !it.isFinite() }) return null
        return CurlReflectionTransform(m00, m01, m02, m10, m11, m12)
    }
}
