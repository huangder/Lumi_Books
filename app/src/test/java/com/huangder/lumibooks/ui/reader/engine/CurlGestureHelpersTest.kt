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
    fun curlPageIntentUsesTouchSlopAndAllowsCornerDiagonalTravel() {
        assertFalse(isCurlPageSwipeIntent(18f, 4f, touchSlop = 24f))
        assertTrue(isCurlPageSwipeIntent(70f, 110f, touchSlop = 24f))
        assertFalse(isCurlPageSwipeIntent(40f, 100f, touchSlop = 24f))
    }

    @Test
    fun curlPageIntentHasNoElapsedTimeDependency() {
        // The gesture gate only receives deltas, so a qualifying move remains
        // valid after the text-selection long-press window has elapsed.
        assertTrue(isCurlPageSwipeIntent(-240f, 12f, touchSlop = 24f))
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

}
