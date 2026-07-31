package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CurlTerminalGeometryTest {
    @Test
    fun completionDistanceClearsTheWholePageDiagonal() {
        val width = 1080f
        val height = 2340f
        val distance = CurlTerminalGeometry.completionDistance(width, height)

        assertEquals(
            width + 2f * hypot(width.toDouble(), height.toDouble()).toFloat(),
            distance,
            0.01f
        )
        assertTrue(distance > width * 2f)
    }

    @Test
    fun invalidViewportSizesProduceFiniteDistance() {
        val distance = CurlTerminalGeometry.completionDistance(0f, 0f)

        assertTrue(distance.isFinite())
        assertTrue(distance > 0f)
    }
}
