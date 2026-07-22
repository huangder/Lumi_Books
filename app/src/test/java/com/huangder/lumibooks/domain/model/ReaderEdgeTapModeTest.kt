package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
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

    private fun assertActions(
        mode: ReaderEdgeTapMode,
        left: ReaderEdgeTapAction,
        right: ReaderEdgeTapAction
    ) {
        assertEquals(left, mode.leftAction)
        assertEquals(right, mode.rightAction)
    }
}
