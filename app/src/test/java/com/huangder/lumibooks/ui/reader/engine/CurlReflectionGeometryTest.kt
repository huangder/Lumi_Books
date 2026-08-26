package com.huangder.lumibooks.ui.reader.engine

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlReflectionGeometryTest {
    @Test
    fun paperBackgroundUsesSameDimToneAsFoldedBitmap() {
        assertEquals(
            0xF4F0E0C8.toInt(),
            ColorMatrixPaperTone.dim(0xFFFFEED4.toInt())
        )
    }

    @Test
    fun reflectionMapsOriginalCornerToDraggedEdge() {
        val frame = CurlReflectionFrame()

        assertTrue(CurlReflectionGeometry.evaluate(1080f, 2340f, 340f, 1510f, frame))
        assertEquals(340f, frame.mapX(1080f, 2340f), 0.01f)
        assertEquals(1510f, frame.mapY(1080f, 2340f), 0.01f)
    }

    @Test
    fun reflectionBasisIsOrthonormal() {
        val frame = CurlReflectionFrame()
        assertTrue(CurlReflectionGeometry.evaluate(1080f, 2340f, 410f, 1840f, frame))
        val values = frame.matrixValues

        assertEquals(1.0, hypot(values[0].toDouble(), values[3].toDouble()), 0.0001)
        assertEquals(1.0, hypot(values[1].toDouble(), values[4].toDouble()), 0.0001)
        assertEquals(0f, values[0] * values[1] + values[3] * values[4], 0.0001f)
        assertEquals(-1f, values[0] * values[4] - values[1] * values[3], 0.0001f)
    }

    @Test
    fun coincidentCornerAndTouchAreRejected() {
        assertFalse(
            CurlReflectionGeometry.evaluate(
                1080f,
                2340f,
                1080f,
                2340f,
                CurlReflectionFrame()
            )
        )
    }
}
