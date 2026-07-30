package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEdgeTapModeTest {

    @Test
    fun `each mode maps both screen edges to the expected action`() {
        assertActions(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            left = ReaderEdgeTapAction.PREVIOUS_PAGE,
            right = ReaderEdgeTapAction.NEXT_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.LEFT_NEXT_RIGHT_PREVIOUS,
            left = ReaderEdgeTapAction.NEXT_PAGE,
            right = ReaderEdgeTapAction.PREVIOUS_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.BOTH_PREVIOUS,
            left = ReaderEdgeTapAction.PREVIOUS_PAGE,
            right = ReaderEdgeTapAction.PREVIOUS_PAGE
        )
        assertActions(
            ReaderEdgeTapMode.BOTH_NEXT,
            left = ReaderEdgeTapAction.NEXT_PAGE,
            right = ReaderEdgeTapAction.NEXT_PAGE
        )
    }

    @Test
    fun `stored keys restore modes and invalid values use the current default`() {
        ReaderEdgeTapMode.entries.forEach { mode ->
            assertEquals(mode, ReaderEdgeTapMode.fromKey(mode.key))
        }
        assertEquals(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            ReaderEdgeTapMode.fromKey("unsupported")
        )
        assertEquals(
            ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            ReaderEdgeTapMode.fromKey(null)
        )
    }

    @Test
    fun `vertical mode reverses every configured edge action without changing the mode`() {
        ReaderEdgeTapMode.entries.forEach { mode ->
            val originalLeft = mode.leftAction
            val originalRight = mode.rightAction

            assertEquals(originalLeft.reversed(), mode.leftAction.reversed())
            assertEquals(originalRight.reversed(), mode.rightAction.reversed())
            assertEquals(originalLeft, mode.leftAction)
            assertEquals(originalRight, mode.rightAction)
        }
        assertEquals(ReaderEdgeTapAction.NEXT_PAGE, ReaderEdgeTapMode.BOTH_PREVIOUS.leftAction.reversed())
        assertEquals(ReaderEdgeTapAction.NEXT_PAGE, ReaderEdgeTapMode.BOTH_PREVIOUS.rightAction.reversed())
        assertEquals(ReaderEdgeTapAction.PREVIOUS_PAGE, ReaderEdgeTapMode.BOTH_NEXT.leftAction.reversed())
        assertEquals(ReaderEdgeTapAction.PREVIOUS_PAGE, ReaderEdgeTapMode.BOTH_NEXT.rightAction.reversed())
    }

    @Test
    fun `stored writing mode keys restore and invalid values default horizontal`() {
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey("horizontal"))
        assertEquals(ReaderWritingMode.VERTICAL_RL, ReaderWritingMode.fromKey("vertical_rl"))
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey(null))
        assertEquals(ReaderWritingMode.HORIZONTAL, ReaderWritingMode.fromKey("unknown"))
        assertFalse(ReaderWritingMode.HORIZONTAL.isVertical)
        assertTrue(ReaderWritingMode.VERTICAL_RL.isVertical)
    }

    @Test
    fun `vertical mode temporarily replaces continuous transition without changing preference`() {
        val preferred = "continuous"

        assertEquals("slide", ReaderWritingMode.VERTICAL_RL.effectivePageTransition(preferred))
        assertEquals("continuous", ReaderWritingMode.HORIZONTAL.effectivePageTransition(preferred))
        assertEquals("curl", ReaderWritingMode.VERTICAL_RL.effectivePageTransition("curl"))
        assertEquals("continuous", preferred)
    }

    private fun assertActions(
        mode: ReaderEdgeTapMode,
        left: ReaderEdgeTapAction,
        right: ReaderEdgeTapAction
    ) {
        assertEquals(left, mode.leftAction)
        assertEquals(right, mode.rightAction)
    }
}
