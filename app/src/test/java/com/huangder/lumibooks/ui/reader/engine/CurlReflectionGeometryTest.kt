package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlReflectionGeometryTest {

    @Test
    fun reflectionMapsCornerToTouch() {
        val transform = assertNotNullTransform(
            CurlReflectionGeometry.between(
                cornerX = 1200f,
                cornerY = 2670f,
                touchX = 340f,
                touchY = 1510f
            )
        )

        assertEquals(340f, transform.mapX(1200f, 2670f), 0.01f)
        assertEquals(1510f, transform.mapY(1200f, 2670f), 0.01f)
    }

    @Test
    fun reflectionBasisIsOrthonormal() {
        val transform = assertNotNullTransform(
            CurlReflectionGeometry.between(1200f, 2670f, 410f, 1840f)
        )

        val firstLength = hypot(transform.m00.toDouble(), transform.m10.toDouble())
        val secondLength = hypot(transform.m01.toDouble(), transform.m11.toDouble())
        val dot = transform.m00 * transform.m01 + transform.m10 * transform.m11
        val determinant = transform.m00 * transform.m11 - transform.m01 * transform.m10

        assertEquals(1.0, firstLength, 0.0001)
        assertEquals(1.0, secondLength, 0.0001)
        assertEquals(0f, dot, 0.0001f)
        assertEquals(-1f, determinant, 0.0001f)
    }

    @Test
    fun reflectionPreservesDistance() {
        val transform = assertNotNullTransform(
            CurlReflectionGeometry.between(1200f, 0f, 250f, 870f)
        )
        val firstX = 170f
        val firstY = 320f
        val secondX = 930f
        val secondY = 2210f
        val originalDistance = hypot(
            (secondX - firstX).toDouble(),
            (secondY - firstY).toDouble()
        )
        val mappedDistance = hypot(
            (transform.mapX(secondX, secondY) - transform.mapX(firstX, firstY)).toDouble(),
            (transform.mapY(secondX, secondY) - transform.mapY(firstX, firstY)).toDouble()
        )

        assertEquals(originalDistance, mappedDistance, 0.01)
    }

    @Test
    fun edgeCoordinatesRemainFinite() {
        val samples = listOf(
            floatArrayOf(1200f, 2670f, -1200f, 1f),
            floatArrayOf(0f, 2670f, 1199f, 2669f),
            floatArrayOf(1200f, 0f, 1f, 2669f),
            floatArrayOf(0f, 0f, -0.1f, 0.1f)
        )

        samples.forEach { sample ->
            val transform = assertNotNullTransform(
                CurlReflectionGeometry.between(sample[0], sample[1], sample[2], sample[3])
            )
            assertTrue(transform.matrixValues().all { it.isFinite() })
            assertTrue(abs(transform.mapX(sample[0], sample[1])) < 10_000f)
            assertTrue(abs(transform.mapY(sample[0], sample[1])) < 10_000f)
        }
    }

    @Test
    fun coincidentCornerAndTouchHaveNoReflection() {
        assertNull(CurlReflectionGeometry.between(1200f, 2670f, 1200f, 2670f))
    }

    private fun assertNotNullTransform(
        transform: CurlReflectionTransform?
    ): CurlReflectionTransform {
        assertNotNull(transform)
        return requireNotNull(transform)
    }
}
