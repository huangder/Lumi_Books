package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTransitionTimingTest {
    @Test
    fun `newly revealed overlay remains visible for full minimum`() {
        assertEquals(
            300L,
            BookTransitionTiming.remainingOverlayVisibleMillis(
                shownAtMs = 500L,
                nowMs = 500L
            )
        )
    }

    @Test
    fun `overlay hold counts down from its actual reveal time`() {
        assertEquals(
            1L,
            BookTransitionTiming.remainingOverlayVisibleMillis(
                shownAtMs = 500L,
                nowMs = 799L
            )
        )
        assertEquals(
            0L,
            BookTransitionTiming.remainingOverlayVisibleMillis(
                shownAtMs = 500L,
                nowMs = 800L
            )
        )
    }

    @Test
    fun `clock rollback cannot extend overlay beyond minimum`() {
        assertEquals(
            BookTransitionTiming.MIN_OVERLAY_VISIBLE_MS,
            BookTransitionTiming.remainingOverlayVisibleMillis(
                shownAtMs = 500L,
                nowMs = 400L
            )
        )
    }
}
