package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkPullGestureTrackerTest {
    private val tracker = BookmarkPullGestureTracker()

    @Test
    fun startOutsideTopRegionDoesNotClaim() {
        tracker.start(x = 100f, y = 161f, density = 1f, enabled = true)

        assertNull(tracker.move(x = 100f, y = 260f))
        assertFalse(tracker.finish(cancelled = false).wasActive)
    }

    @Test
    fun middleOfReaderCanClaimPull() {
        tracker.start(
            x = 100f,
            y = 500f,
            density = 1f,
            enabled = true,
            startRegionY = 500f,
            gestureRegionHeight = 1_000f
        )

        assertTrue(tracker.move(x = 100f, y = 570f)!!.armed)
    }

    @Test
    fun bottomRegionDoesNotClaimPull() {
        tracker.start(
            x = 100f,
            y = 850f,
            density = 1f,
            enabled = true,
            startRegionY = 850f,
            gestureRegionHeight = 1_000f
        )

        assertNull(tracker.move(x = 100f, y = 950f))
    }

    @Test
    fun horizontalDominantMoveDoesNotClaim() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)

        assertNull(tracker.move(x = 160f, y = 80f))
        assertFalse(tracker.finish(cancelled = false).wasActive)
    }

    @Test
    fun pullBelowThresholdReturnsWithoutCommit() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)

        val update = tracker.move(x = 102f, y = 100f)!!

        assertTrue(update.justClaimed)
        assertFalse(update.armed)
        assertFalse(tracker.finish(cancelled = false).commit)
    }

    @Test
    fun retreatBelowThresholdCancelsCommit() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)

        assertTrue(tracker.move(x = 100f, y = 120f)!!.armed)
        assertFalse(tracker.move(x = 100f, y = 105f)!!.armed)
        assertFalse(tracker.finish(cancelled = false).commit)
    }

    @Test
    fun releasePastThresholdCommitsAndClampsDistance() {
        tracker.start(x = 100f, y = 50f, density = 2f, enabled = true)

        val update = tracker.move(x = 100f, y = 350f)!!

        assertEquals(240f, update.distancePx, 0f)
        assertTrue(update.armed)
        assertTrue(tracker.finish(cancelled = false).commit)
    }

    @Test
    fun cancelNeverCommits() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)
        tracker.move(x = 100f, y = 120f)

        val finish = tracker.finish(cancelled = true)

        assertTrue(finish.wasActive)
        assertFalse(finish.commit)
    }

    @Test
    fun thresholdFeedbackIsEmittedOnlyOncePerGesture() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)

        assertTrue(tracker.move(x = 100f, y = 120f)!!.crossedThreshold)
        assertFalse(tracker.move(x = 100f, y = 105f)!!.crossedThreshold)
        assertFalse(tracker.move(x = 100f, y = 125f)!!.crossedThreshold)
    }

    @Test
    fun returningToOriginDoesNotClaimGestureTwice() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = true)

        assertTrue(tracker.move(x = 100f, y = 80f)!!.justClaimed)
        assertFalse(tracker.move(x = 100f, y = 50f)!!.justClaimed)
        assertFalse(tracker.move(x = 100f, y = 80f)!!.justClaimed)
    }

    @Test
    fun disabledTrackerDoesNotClaim() {
        tracker.start(x = 100f, y = 50f, density = 1f, enabled = false)

        assertNull(tracker.move(x = 100f, y = 150f))
    }
}
