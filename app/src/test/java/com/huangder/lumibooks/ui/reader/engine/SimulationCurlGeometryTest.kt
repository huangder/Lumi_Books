package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCurlGeometryTest {
    private fun assertFinite(frame: SimulationCurlFrame) {
        val values = floatArrayOf(
            frame.cornerX, frame.cornerY, frame.touchX, frame.touchY,
            frame.middleX, frame.middleY, frame.touchToCornerDistance,
            frame.start1X, frame.start1Y, frame.control1X, frame.control1Y,
            frame.vertex1X, frame.vertex1Y, frame.end1X, frame.end1Y,
            frame.start2X, frame.start2Y, frame.control2X, frame.control2Y,
            frame.vertex2X, frame.vertex2Y, frame.end2X, frame.end2Y
        )
        assertTrue(values.all { it.isFinite() })
    }

    @Test
    fun topAndBottomCornersProduceFiniteBezierFrames() {
        val top = SimulationCurlFrame()
        val bottom = SimulationCurlFrame()

        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 340f, 420f, SimulationCurlCorner.TOP, top
            )
        )
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 340f, 1920f, SimulationCurlCorner.BOTTOM, bottom
            )
        )
        assertEquals(0f, top.cornerY, 0f)
        assertEquals(2340f, bottom.cornerY, 0f)
        assertFinite(top)
        assertFinite(bottom)
    }

    @Test
    fun nearCornerTouchYProducesFullHeightVerticalCurl() {
        val top = SimulationCurlFrame()
        val bottom = SimulationCurlFrame()

        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 720f, 1f, SimulationCurlCorner.TOP, top
            )
        )
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 720f, 2339f, SimulationCurlCorner.BOTTOM, bottom
            )
        )
        assertTrue(top.start2Y > 2340f)
        assertTrue(bottom.start2Y < 0f)
        assertFinite(top)
        assertFinite(bottom)
    }

    @Test
    fun sameTouchCoordinatesAlwaysProduceTheSameFiniteGeometry() {
        val next = SimulationCurlFrame()
        val previous = SimulationCurlFrame()
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 420f, 1510f, SimulationCurlCorner.TOP, next
            )
        )
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 420f, 1510f, SimulationCurlCorner.TOP, previous
            )
        )
        assertEquals(next.touchX, previous.touchX, 0f)
        assertEquals(next.vertex1X, previous.vertex1X, 0f)
        assertEquals(next.vertex2Y, previous.vertex2Y, 0f)
    }

    @Test
    fun extremeAndDegenerateInputsNeverProduceNaN() {
        val frame = SimulationCurlFrame()
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, -5000f, Float.POSITIVE_INFINITY,
                SimulationCurlCorner.BOTTOM, frame
            ).not()
        )
        assertFalse(
            SimulationCurlGeometry.evaluate(
                0f, 2340f, 0f, 100f, SimulationCurlCorner.TOP, frame
            )
        )
        assertFalse(
            SimulationCurlGeometry.evaluate(
                1080f, 1f, 0f, 0.5f, SimulationCurlCorner.TOP, frame
            )
        )
    }

    @Test
    fun terminalDistanceAndCornerSelectionStayFinite() {
        val distance = CurlTerminalGeometry.completionDistance(1080f, 2340f)
        assertTrue(distance.isFinite())
        assertTrue(distance > 1080f)
        assertTrue(CurlTerminalGeometry.completionDistance(Float.MAX_VALUE, Float.MAX_VALUE).isFinite())
        assertEquals(SimulationCurlCorner.TOP, SimulationCurlGeometry.cornerForTouchY(1000f, 200f))
        assertEquals(SimulationCurlCorner.BOTTOM, SimulationCurlGeometry.cornerForTouchY(1000f, 800f))
    }

    @Test
    fun reboundAndTerminalCoordinatesRemainSafe() {
        val frame = SimulationCurlFrame()
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, 1079f, 1170f, SimulationCurlCorner.BOTTOM, frame
            )
        )
        assertFinite(frame)
        assertTrue(
            SimulationCurlGeometry.evaluate(
                1080f, 2340f, -1080f, 1170f, SimulationCurlCorner.BOTTOM, frame,
                horizontalLimit = 5000f
            )
        )
        assertFinite(frame)
    }

    @Test
    fun nextDecreasesWhilePreviousIncreasesFromOffscreenFlatState() {
        assertEquals(
            780f,
            SimulationCurlGeometry.canonicalTouchX(
                1080f, 900f, 600f, SimulationCurlTurnDirection.NEXT
            ),
            0f
        )
        assertEquals(
            -480f,
            SimulationCurlGeometry.canonicalTouchX(
                1080f, 300f, 600f, SimulationCurlTurnDirection.PREVIOUS
            ),
            0f
        )
        assertEquals(
            -1080f,
            SimulationCurlGeometry.canonicalTouchX(
                1080f, 300f, 300f, SimulationCurlTurnDirection.PREVIOUS
            ),
            0f
        )
        assertEquals(
            1080f,
            SimulationCurlGeometry.canonicalTouchX(
                1080f, 0f, 2000f, SimulationCurlTurnDirection.PREVIOUS
            ),
            0f
        )
        assertTrue(
            SimulationCurlGeometry.canonicalTouchX(
                0.5f, 0.25f, 0.1f, SimulationCurlTurnDirection.NEXT
            ).isFinite()
        )
    }

    @Test
    fun cornerTouchXFollowsTheActualPointerCoordinate() {
        assertEquals(
            180f,
            SimulationCurlGeometry.cornerTouchX(1080f, 180f),
            0f
        )
        assertEquals(
            -40f,
            SimulationCurlGeometry.cornerTouchX(1080f, -40f),
            0f
        )
        assertEquals(
            -1080f,
            SimulationCurlGeometry.cornerTouchX(1080f, -2000f),
            0f
        )
        assertTrue(
            SimulationCurlGeometry.cornerTouchX(
                Float.POSITIVE_INFINITY,
                100f
            ).isFinite()
        )
    }
}
