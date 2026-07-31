package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageTurnHostTest {
    @Test
    fun nextTurnPreparesPageAfterItsVisualDestination() {
        assertEquals(
            EpubPageTarget(4, 7),
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = 1
            )
        )
    }

    @Test
    fun previousTurnPreparesPageBeforeItsVisualDestination() {
        assertEquals(
            EpubPageTarget(4, 3),
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = -1
            )
        )
    }

    @Test
    fun lookaheadStopsAtChapterBoundary() {
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 18),
                currentPageCount = 20,
                direction = 1
            )
        )
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 1),
                currentPageCount = 20,
                direction = -1
            )
        )
    }

    @Test
    fun zeroDirectionDoesNotPrepareAnotherPage() {
        assertNull(
            slideLookaheadTarget(
                current = EpubPageTarget(4, 5),
                currentPageCount = 20,
                direction = 0
            )
        )
    }

    @Test
    fun preparedCallbackUsesRoleAtCompletionTime() {
        assertEquals(
            EpubPageTurnHost.PreloadSlot.NEXT,
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.NEXT,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
        assertEquals(
            EpubPageTurnHost.PreloadSlot.PREVIOUS,
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.PREVIOUS,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
    }

    @Test
    fun preparedCallbackRejectsPromotedAndReplacedRequests() {
        assertNull(
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.ACTIVE,
                callbackGeneration = 14,
                currentGeneration = 14
            )
        )
        assertNull(
            resolvedPreloadSlot(
                role = EpubPageTurnHost.WebViewRole.NEXT,
                callbackGeneration = 14,
                currentGeneration = 15
            )
        )
    }

    @Test
    fun onlyCurlPreloadsBitmaps() {
        assertTrue(requiresEpubPreloadBitmap("curl"))
        assertFalse(requiresEpubPreloadBitmap("slide"))
    }
}
