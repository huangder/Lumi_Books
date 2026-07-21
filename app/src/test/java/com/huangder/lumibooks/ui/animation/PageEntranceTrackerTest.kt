package com.huangder.lumibooks.ui.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEntranceTrackerTest {
    @Test
    fun firstEntryPlaysAndShortReturnDoesNotReplay() {
        val tracker = PageEntranceTracker(cooldownMillis = 1_000L)

        assertTrue(tracker.shouldPlay("home", nowMillis = 10_000L))
        assertFalse(tracker.shouldPlay("home", nowMillis = 10_999L))
    }

    @Test
    fun pageReplaysAfterCooldown() {
        val tracker = PageEntranceTracker(cooldownMillis = 1_000L)

        assertTrue(tracker.shouldPlay("statistics", nowMillis = 20_000L))
        assertTrue(tracker.shouldPlay("statistics", nowMillis = 21_000L))
    }

    @Test
    fun cooldownIsTrackedPerPage() {
        val tracker = PageEntranceTracker(cooldownMillis = 1_000L)

        assertTrue(tracker.shouldPlay("home", nowMillis = 30_000L))
        assertTrue(tracker.shouldPlay("bookshelf", nowMillis = 30_100L))
    }

    @Test
    fun defaultCooldownIsTenSeconds() {
        val tracker = PageEntranceTracker()

        assertTrue(tracker.shouldPlay("home", nowMillis = 100_000L))
        assertFalse(tracker.shouldPlay("home", nowMillis = 109_999L))
        assertTrue(tracker.shouldPlay("home", nowMillis = 110_000L))
    }
}
