package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.*
import org.junit.Test

class DeferredCurlGestureTest {
    @Test fun reversingAWaitingSwipeDoesNotReplayItsOldDirection() {
        for (reversed in listOf(false, true)) {
            for (direction in listOf(PageAnimationController.Direction.NEXT, PageAnimationController.Direction.PREV)) {
                val sign = if ((direction == PageAnimationController.Direction.NEXT) != reversed) -1f else 1f
                assertTrue(commit(sign * 300f, direction, reversed))
                assertFalse(commit(sign * -300f, direction, reversed))
                assertFalse(commit(0f, direction, reversed))
            }
        }
    }

    @Test fun shortFastSwipeStillCommitsInItsQueuedDirection() {
        assertTrue(shouldCommitDeferredCurlGesture(-40f, 40L, 1000, 1f,
            PageAnimationController.Direction.NEXT, false))
        assertFalse(shouldCommitDeferredCurlGesture(40f, 40L, 1000, 1f,
            PageAnimationController.Direction.NEXT, false))
    }

    private fun commit(delta: Float, direction: PageAnimationController.Direction, reversed: Boolean) =
        shouldCommitDeferredCurlGesture(delta, 1000L, 1000, 1f, direction, reversed)
}
