package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSpreadPlannerTest {
    @Test
    fun firstPageStartsOnTheLeft() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(0, 0),
            anchor = PageLocation(0, 0),
            pageCounts = listOf(2)
        )

        assertEquals(SpreadTarget(PageLocation(0, 0), PageLocation(0, 1)), spread)
    }

    @Test
    fun oddChapterTailIsFilledByNextChapterFirstPage() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(0, 2),
            anchor = PageLocation(0, 0),
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
            anchor = PageLocation(0, 0),
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
        val anchor = PageLocation(0, 0)
        val tail = ReaderSpreadPlanner.spreadFor(PageLocation(0, 2), anchor, counts)!!
        val next = ReaderSpreadPlanner.next(tail, anchor, counts)!!
        val previous = ReaderSpreadPlanner.previous(next, anchor, counts)

        assertEquals(SpreadTarget(PageLocation(1, 1), PageLocation(1, 2)), next)
        assertEquals(tail, previous)
    }

    @Test
    fun directChapterJumpUsesThatChapterAsANewLeftAnchor() {
        val spread = ReaderSpreadPlanner.spreadFor(
            PageLocation(2, 0),
            anchor = PageLocation(2, 0),
            pageCounts = listOf(0, 0, 2)
        )

        assertEquals(SpreadTarget(PageLocation(2, 0), PageLocation(2, 1)), spread)
    }

    @Test
    fun emptyOrOutOfRangeTargetsHaveNoSpread() {
        assertNull(
            ReaderSpreadPlanner.spreadFor(
                PageLocation(0, 0),
                PageLocation(0, 0),
                listOf(0)
            )
        )
        assertNull(
            ReaderSpreadPlanner.spreadFor(
                PageLocation(2, 0),
                PageLocation(0, 0),
                listOf(1)
            )
        )
    }

    @Test
    fun severalShortChaptersKeepGlobalOrderWithoutDuplicates() {
        val counts = listOf(1, 1, 1, 1)
        val pages = buildList {
            val anchor = PageLocation(0, 0)
            var spread = ReaderSpreadPlanner.spreadFor(PageLocation(0, 0), anchor, counts)
            while (spread != null) {
                addAll(listOfNotNull(spread.left, spread.right))
                spread = ReaderSpreadPlanner.next(spread, anchor, counts)
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
            anchor = PageLocation(0, 0),
            pageCounts = listOf(4)
        )

        assertEquals(
            SpreadTarget(PageLocation(0, 2), PageLocation(0, 3)),
            spread
        )
    }

    @Test
    fun restoredOddPageStartsANewSpreadOnTheLeft() {
        val restored = PageLocation(0, 3)
        val spread = ReaderSpreadPlanner.spreadFor(
            target = restored,
            anchor = restored,
            pageCounts = listOf(6)
        )

        assertEquals(
            SpreadTarget(PageLocation(0, 3), PageLocation(0, 4)),
            spread
        )
    }

    @Test
    fun bookBoundariesDoNotCreateDuplicateSpreads() {
        val counts = listOf(2, 1)
        val anchor = PageLocation(0, 0)
        val first = ReaderSpreadPlanner.spreadFor(PageLocation(0, 0), anchor, counts)!!
        val last = ReaderSpreadPlanner.next(first, anchor, counts)!!

        assertNull(ReaderSpreadPlanner.previous(first, anchor, counts))
        assertNull(ReaderSpreadPlanner.next(last, anchor, counts))
    }

    @Test
    fun directChapterJumpBridgesToPreviousChapterTailAndReturns() {
        val counts = listOf(3, 2)
        val anchor = PageLocation(1, 0)
        val jumped = ReaderSpreadPlanner.spreadFor(PageLocation(1, 0), anchor, counts)!!
        val previous = ReaderSpreadPlanner.previous(jumped, anchor, counts)!!

        assertEquals(
            SpreadTarget(PageLocation(0, 1), PageLocation(0, 2)),
            previous
        )
        assertEquals(jumped, ReaderSpreadPlanner.next(previous, anchor, counts))
        assertEquals(
            SpreadTarget(PageLocation(0, 0), null),
            ReaderSpreadPlanner.previous(previous, anchor, counts)
        )
    }

    @Test
    fun restoredLeftPageKeepsParityAcrossChapterBoundaries() {
        val counts = listOf(3, 4)
        val anchor = PageLocation(1, 1)
        val restored = ReaderSpreadPlanner.spreadFor(anchor, anchor, counts)!!
        val previous = ReaderSpreadPlanner.previous(restored, anchor, counts)!!
        val next = ReaderSpreadPlanner.next(restored, anchor, counts)!!

        assertEquals(
            SpreadTarget(PageLocation(1, 1), PageLocation(1, 2)),
            restored
        )
        assertEquals(
            SpreadTarget(PageLocation(0, 2), PageLocation(1, 0)),
            previous
        )
        assertEquals(
            SpreadTarget(PageLocation(1, 3), null),
            next
        )
        assertEquals(restored, ReaderSpreadPlanner.next(previous, anchor, counts))
        assertEquals(restored, ReaderSpreadPlanner.previous(next, anchor, counts))
    }
}
