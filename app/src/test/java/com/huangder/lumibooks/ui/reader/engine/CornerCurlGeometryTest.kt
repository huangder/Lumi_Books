package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerCurlGeometryTest {
    @Test
    fun cornerShadowFadesSmoothlyAwayFromScreenIntersection() {
        assertEquals(0f, cornerShadowEndpointOpacity(1080f, 500f, 1080f, 2340f, 48f), 0f)
        assertEquals(0.5f, cornerShadowEndpointOpacity(1056f, 500f, 1080f, 2340f, 48f), 0.001f)
        assertEquals(1f, cornerShadowEndpointOpacity(1032f, 500f, 1080f, 2340f, 48f), 0f)
    }

    @Test
    fun cornerShadowAlsoFadesAtTopAndBottomIntersections() {
        assertEquals(0f, cornerShadowEndpointOpacity(600f, 0f, 1080f, 2340f, 48f), 0f)
        assertEquals(0f, cornerShadowEndpointOpacity(600f, 2340f, 1080f, 2340f, 48f), 0f)
    }

    @Test
    fun diagonalSwipeReachesCurlClassifierWhileVerticalGestureDoesNot() {
        assertTrue(isCurlSwipeIntent(deltaX = 90f, deltaY = 90f))
        assertTrue(isCurlSwipeIntent(deltaX = 70f, deltaY = 110f))
        assertFalse(isCurlSwipeIntent(deltaX = 40f, deltaY = 100f))
    }

    @Test
    fun directCornerTrackingMapsBothTurnEdgesToCanonicalGeometry() {
        assertEquals(720f, directCornerTouchX(1080f, 720f, -1f), 0f)
        assertEquals(720f, directCornerTouchX(1080f, 360f, 1f), 0f)
    }

    @Test
    fun directCornerTrackingClampsPointerOutsidePage() {
        assertEquals(-1080f, directCornerTouchX(1080f, -1400f, -1f), 0f)
        assertEquals(-1080f, directCornerTouchX(1080f, 2480f, 1f), 0f)
    }

    @Test
    fun centerStartLocksVerticalMode() {
        val lock = CurlGestureModeLock()
        lock.begin(1000f, 1170f)

        assertEquals(
            CurlGeometryMode.EDGE_VERTICAL,
            lock.lock(1080f, 2340f, -1f, deltaX = -80f, deltaY = 3f)
        )
    }

    @Test
    fun topAndBottomPageCornersSelectCornerCurl() {
        val lock = CurlGestureModeLock()
        lock.begin(1030f, 90f)
        assertEquals(
            CurlGeometryMode.CORNER_TOP,
            lock.lock(1080f, 2340f, -1f, deltaX = -80f, deltaY = 2f)
        )

        lock.begin(40f, 2250f)
        assertEquals(
            CurlGeometryMode.CORNER_BOTTOM,
            lock.lock(1080f, 2340f, 1f, deltaX = 80f, deltaY = -2f)
        )
    }

    @Test
    fun selectedModeDoesNotChangeAfterDirectionIsLocked() {
        val lock = CurlGestureModeLock()
        lock.begin(1030f, 2250f)
        assertEquals(
            CurlGeometryMode.CORNER_BOTTOM,
            lock.lock(1080f, 2340f, -1f, deltaX = -80f, deltaY = -40f)
        )

        assertEquals(
            CurlGeometryMode.CORNER_BOTTOM,
            lock.lock(1080f, 2340f, 1f, deltaX = 80f, deltaY = 0f)
        )
        assertTrue(lock.isLocked)
    }

    @Test
    fun diagonalGestureFromTurnEdgeSelectsCornerBeyondLiteralCornerZone() {
        val lock = CurlGestureModeLock()
        lock.begin(760f, 1540f)

        assertEquals(
            CurlGeometryMode.CORNER_BOTTOM,
            lock.lock(1080f, 2340f, -1f, deltaX = -90f, deltaY = -82f)
        )
    }

    @Test
    fun horizontalGestureFromSamePositionStaysVertical() {
        val lock = CurlGestureModeLock()
        lock.begin(760f, 1540f)

        assertEquals(
            CurlGeometryMode.EDGE_VERTICAL,
            lock.lock(1080f, 2340f, -1f, deltaX = -90f, deltaY = -8f)
        )
    }

    @Test
    fun earlyDiagonalJitterDoesNotLockHorizontalGestureToCorner() {
        val lock = CurlGestureModeLock()
        lock.begin(760f, 1540f)

        assertEquals(
            CurlGeometryMode.EDGE_VERTICAL,
            lock.lock(1080f, 2340f, -1f, deltaX = -18f, deltaY = -14f)
        )
        assertFalse(lock.isLocked)

        assertEquals(
            CurlGeometryMode.EDGE_VERTICAL,
            lock.lock(1080f, 2340f, -1f, deltaX = -90f, deltaY = -15f)
        )
        assertTrue(lock.isLocked)
    }

    @Test
    fun shallowDiagonalGestureSelectsCornerAfterStableClassification() {
        val lock = CurlGestureModeLock()
        lock.begin(800f, 1540f)

        assertEquals(
            CurlGeometryMode.CORNER_BOTTOM,
            lock.lock(1080f, 2340f, -1f, deltaX = -100f, deltaY = -55f)
        )
    }

    @Test
    fun horizontallyDominantGestureStaysVertical() {
        val lock = CurlGestureModeLock()
        lock.begin(800f, 1540f)

        assertEquals(
            CurlGeometryMode.EDGE_VERTICAL,
            lock.lock(1080f, 2340f, -1f, deltaX = -100f, deltaY = -30f)
        )
    }

    @Test
    fun cornerBezierPointsStayFinite() {
        val frame = CornerCurlFrame()

        assertTrue(
            CornerCurlGeometry.evaluate(
                1080f,
                2340f,
                520f,
                1820f,
                fromBottom = true,
                frame
            )
        )
        assertTrue(
            floatArrayOf(
                frame.start1X,
                frame.control1X,
                frame.end1X,
                frame.touchX,
                frame.touchY,
                frame.end2Y,
                frame.control2Y,
                frame.start2Y
            ).all { it.isFinite() }
        )
        assertFalse(
            CornerCurlGeometry.evaluate(0f, 2340f, 0f, 0f, true, frame)
        )
    }

    @Test
    fun foldedBackLayerAlwaysUsesTurningPage() {
        val turning = Any()
        val under = Any()

        assertSame(turning, CurlLayerPolicy.foldedBack(turning, under))
    }
}
