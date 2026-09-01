package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSpreadPlannerTest {
    @Test
    fun firstPageStartsOnTheLeft() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(0, 0),
            anchorChapter = 0,
            pageCounts = listOf(2)
        )

        assertEquals(SpreadTarget(PageLocation(0, 0), PageLocation(0, 1)), spread)
    }

    @Test
    fun oddChapterTailIsFilledByNextChapterFirstPage() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(0, 2),
            anchorChapter = 0,
            pageCounts = listOf(3, 2)
        )

        assertEquals(
            SpreadTarget(PageLocation(0, 2), PageLocation(1, 0)),
            spread
        )
    }

    @Test
    fun evenChapterTailStartsNextChapterOnTheLeft() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(1, 0),
            anchorChapter = 0,
            pageCounts = listOf(2, 2)
        )

        assertEquals(
            SpreadTarget(PageLocation(1, 0), PageLocation(1, 1)),
            spread
        )
    }

    @Test
    fun nextAndPreviousSpreadsRoundTripAcrossChapterBoundary() {
        val counts = listOf(3, 3)
        val tail = ReaderSpreadPlanner.spreadFor(PageLocation(0, 2), 0, counts)!!
        val next = ReaderSpreadPlanner.next(tail, 0, counts)!!
        val previous = ReaderSpreadPlanner.previous(next, 0, counts)

        assertEquals(SpreadTarget(PageLocation(1, 1), PageLocation(1, 2)), next)
        assertEquals(tail, previous)
    }

    @Test
    fun directChapterJumpUsesThatChapterAsANewLeftAnchor() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(2, 0),
            anchorChapter = 2,
            pageCounts = listOf(0, 0, 2)
        )

        assertEquals(SpreadTarget(PageLocation(2, 0), PageLocation(2, 1)), spread)
    }

    @Test
    fun emptyOrOutOfRangeTargetsHaveNoSpread() {
        assertNull(ReaderSpreadPlanner.spreadFor(PageLocation(0, 0), 0, listOf(0)))
        assertNull(ReaderSpreadPlanner.spreadFor(PageLocation(2, 0), 0, listOf(1)))
    }

    @Test
    fun severalShortChaptersKeepGlobalOrderWithoutDuplicates() {
        val counts = listOf(1, 1, 1, 1)
        val pages = buildList {
            var spread = ReaderSpreadPlanner.spreadFor(PageLocation(0, 0), 0, counts)
            while (spread != null) {
                addAll(listOfNotNull(spread.left, spread.right))
                spread = ReaderSpreadPlanner.next(spread, 0, counts)
            }
        }

        assertEquals(
            listOf(
                PageLocation(0, 0), PageLocation(1, 0),
                PageLocation(2, 0), PageLocation(3, 0)
            ),
            pages
        )
    }

    @Test
    fun oddTargetResolvesToItsContainingSpread() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(0, 3),
            anchorChapter = 0,
            pageCounts = listOf(4)
        )

        assertEquals(
            SpreadTarget(PageLocation(0, 2), PageLocation(0, 3)),
            spread
        )
    }

    @Test
    fun bookBoundariesDoNotCreateDuplicateSpreads() {
        val counts = listOf(2, 1)
        val first = ReaderSpreadPlanner.spreadFor(PageLocation(0, 0), 0, counts)!!
        val last = ReaderSpreadPlanner.next(first, 0, counts)!!

        assertNull(ReaderSpreadPlanner.previous(first, 0, counts))
        assertNull(ReaderSpreadPlanner.next(last, 0, counts))
    }

    @Test
    fun directChapterJumpBridgesToPreviousChapterTailAndReturns() {
        val counts = listOf(3, 2)
        val jumped = ReaderSpreadPlanner.spreadFor(PageLocation(1, 0), 1, counts)!!
        val previous = ReaderSpreadPlanner.previous(jumped, 1, counts)!!

        assertEquals(
            SpreadTarget(PageLocation(0, 1), PageLocation(0, 2)),
            previous
        )
        assertEquals(jumped, ReaderSpreadPlanner.next(previous, 1, counts))
        assertEquals(
            SpreadTarget(PageLocation(0, 0), null),
            ReaderSpreadPlanner.previous(previous, 1, counts)
        )
    }
}
