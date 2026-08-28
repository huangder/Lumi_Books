package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlGestureHelpersTest {
    @Test
    fun horizontalDominanceClassifierRejectsVerticalGestures() {
        assertTrue(isCurlSwipeIntent(deltaX = 90f, deltaY = 90f))
        assertTrue(isCurlSwipeIntent(deltaX = 70f, deltaY = 110f))
        assertFalse(isCurlSwipeIntent(deltaX = 40f, deltaY = 100f))
    }

    @Test
    fun bottomFifthScreenEdgesAreReservedForSystemBack() {
        assertTrue(isSystemBackGestureStart(1080f, 2340f, 40f, 2000f, 3f))
        assertTrue(isSystemBackGestureStart(1080f, 2340f, 1040f, 2000f, 3f))
        assertFalse(isSystemBackGestureStart(1080f, 2340f, 540f, 2000f, 3f))
        assertFalse(isSystemBackGestureStart(1080f, 2340f, 40f, 1800f, 3f))
    }

    @Test
    fun systemBackReservationRequiresHorizontalMovement() {
        assertTrue(isSystemBackGestureSwipe(80f, 20f))
        assertTrue(isSystemBackGestureSwipe(-80f, 20f))
        assertFalse(isSystemBackGestureSwipe(6f, 1f))
        assertFalse(isSystemBackGestureSwipe(20f, 80f))
    }

    @Test
    fun horizontalDragLocksToVerticalEdgeCurlAtClassificationDistance() {
        val lock = CurlGestureModeLock()
        lock.begin(1000f, 40f)

        val mode = lock.lock(
            width = 1080f,
            height = 2340f,
            physicalTurnSign = -1f,
            deltaX = -160f,
            deltaY = 24f
        )

        assertTrue(lock.isLocked)
        assertTrue(mode == CurlGestureMode.EDGE_VERTICAL)
    }

    @Test
    fun movementBelowClassificationDistanceDoesNotLockOrRenderAMode() {
        val lock = CurlGestureModeLock()
        lock.begin(1000f, 40f)

        assertTrue(
            lock.lock(1080f, 2340f, -1f, -40f, 30f) ==
                CurlGestureMode.EDGE_VERTICAL
        )
        assertFalse(lock.isLocked)
    }

    @Test
    fun diagonalDragFromTurnEdgeSelectsStartingCorner() {
        val topLock = CurlGestureModeLock()
        topLock.begin(1000f, 120f)
        assertTrue(
            topLock.lock(1080f, 2340f, -1f, -180f, 400f) ==
                CurlGestureMode.CORNER_TOP
        )

        val bottomLock = CurlGestureModeLock()
        bottomLock.begin(1000f, 2200f)
        assertTrue(
            bottomLock.lock(1080f, 2340f, -1f, -180f, -400f) ==
                CurlGestureMode.CORNER_BOTTOM
        )
    }

    @Test
    fun selectedVerticalModeDoesNotChangeMidGesture() {
        val lock = CurlGestureModeLock()
        lock.begin(1000f, 120f)
        assertTrue(
            lock.lock(1080f, 2340f, -1f, -180f, 10f) ==
                CurlGestureMode.EDGE_VERTICAL
        )
        assertTrue(
            lock.lock(1080f, 2340f, -1f, -240f, 500f) ==
                CurlGestureMode.EDGE_VERTICAL
        )
        assertTrue(lock.isLocked)
    }

    @Test
    fun diagonalDragAwayFromEdgeStillSelectsStartingCorner() {
        val lock = CurlGestureModeLock()
        lock.begin(540f, 1200f)
        assertTrue(
            lock.lock(1080f, 2340f, -1f, -220f, 620f) ==
                CurlGestureMode.CORNER_BOTTOM
        )
    }
}
