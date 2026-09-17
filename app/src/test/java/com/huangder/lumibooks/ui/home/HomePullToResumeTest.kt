package com.huangder.lumibooks.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePullToResumeTest {
    private val thresholdPx = PULL_TO_RESUME_THRESHOLD_DP

    @Test
    fun progressIsZeroWhenListIsAtRest() {
        assertEquals(0f, pullToResumeProgress(offsetPx = 0f, thresholdPx = thresholdPx), 0.0001f)
    }

    @Test
    fun progressIsZeroForBottomOverscroll() {
        assertEquals(0f, pullToResumeProgress(offsetPx = -48f, thresholdPx = thresholdPx), 0.0001f)
    }

    @Test
    fun progressFollowsPullDistance() {
        assertEquals(
            0.5f,
            pullToResumeProgress(offsetPx = thresholdPx / 2f, thresholdPx = thresholdPx),
            0.0001f
        )
    }

    @Test
    fun progressClampsBeyondThreshold() {
        assertEquals(1f, pullToResumeProgress(offsetPx = 200f, thresholdPx = thresholdPx), 0.0001f)
        assertEquals(1f, pullToResumeProgress(offsetPx = thresholdPx, thresholdPx = thresholdPx), 0.0001f)
    }

    @Test
    fun progressIsZeroWhenThresholdIsNotUsable() {
        assertEquals(0f, pullToResumeProgress(offsetPx = 40f, thresholdPx = 0f), 0.0001f)
        assertEquals(0f, pullToResumeProgress(offsetPx = 40f, thresholdPx = -10f), 0.0001f)
    }

    @Test
    fun releaseBelowThresholdDoesNotOpenBook() {
        assertFalse(
            shouldOpenRecentBookOnRelease(releasedOffsetPx = thresholdPx - 1f, thresholdPx = thresholdPx)
        )
    }

    @Test
    fun releaseAtThresholdOpensBook() {
        assertTrue(shouldOpenRecentBookOnRelease(releasedOffsetPx = thresholdPx, thresholdPx = thresholdPx))
        assertTrue(shouldOpenRecentBookOnRelease(releasedOffsetPx = 120f, thresholdPx = thresholdPx))
    }

    @Test
    fun releaseAfterDraggingBackDoesNotOpenBook() {
        // 用户拉出后原路推回：位移回落，松手时判定为不触发。
        assertFalse(shouldOpenRecentBookOnRelease(releasedOffsetPx = 0f, thresholdPx = thresholdPx))
    }

    @Test
    fun releaseWithNegativeOffsetNeverOpensBook() {
        assertFalse(shouldOpenRecentBookOnRelease(releasedOffsetPx = -80f, thresholdPx = thresholdPx))
    }
}
