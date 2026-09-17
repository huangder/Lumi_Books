package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.CbzReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzSpreadPlannerTest {
    @Test
    fun `pairs pages without a cover offset`() {
        assertEquals(3, CbzSpreadPlanner.spreadCount(pageCount = 5, aloneFirstPage = false))
        assertEquals(CbzSpread(0, 1), CbzSpreadPlanner.spreadFor(0, 5, false))
        assertEquals(CbzSpread(2, 3), CbzSpreadPlanner.spreadFor(1, 5, false))
        assertEquals(CbzSpread(4, null), CbzSpreadPlanner.spreadFor(2, 5, false))
    }

    @Test
    fun `cover offset puts the first page alone`() {
        assertEquals(3, CbzSpreadPlanner.spreadCount(pageCount = 5, aloneFirstPage = true))
        assertEquals(CbzSpread(0, null), CbzSpreadPlanner.spreadFor(0, 5, true))
        assertEquals(CbzSpread(1, 2), CbzSpreadPlanner.spreadFor(1, 5, true))
        assertEquals(CbzSpread(3, 4), CbzSpreadPlanner.spreadFor(2, 5, true))
        assertNull(CbzSpreadPlanner.spreadFor(3, 5, true))
    }

    @Test
    fun `empty and out of range spreads are null`() {
        assertEquals(0, CbzSpreadPlanner.spreadCount(pageCount = 0, aloneFirstPage = true))
        assertNull(CbzSpreadPlanner.spreadFor(0, 0, true))
        assertNull(CbzSpreadPlanner.spreadFor(-1, 4, false))
    }

    @Test
    fun `page index maps back to its spread`() {
        assertEquals(0, CbzSpreadPlanner.spreadIndexOfPage(0, 9, aloneFirstPage = true))
        assertEquals(1, CbzSpreadPlanner.spreadIndexOfPage(1, 9, true))
        assertEquals(1, CbzSpreadPlanner.spreadIndexOfPage(2, 9, true))
        assertEquals(2, CbzSpreadPlanner.spreadIndexOfPage(3, 9, true))
        assertEquals(4, CbzSpreadPlanner.spreadIndexOfPage(9, 9, false))
        assertEquals(4, CbzSpreadPlanner.spreadIndexOfPage(99, 9, false))
    }

    @Test
    fun `right page is the earlier page when reading right to left`() {
        val spread = CbzSpreadPlanner.spreadFor(1, 5, aloneFirstPage = true)!!
        val direction = CbzReadingDirection.RIGHT_TO_LEFT
        val rightHandPage = if (direction.isRightToLeft) spread.firstPage else spread.secondPage
        val leftHandPage = if (direction.isRightToLeft) spread.secondPage else spread.firstPage

        assertEquals(1, rightHandPage)
        assertEquals(2, leftHandPage)
    }

    @Test
    fun `reading direction resolves from storage then comic info then default`() {
        assertEquals(
            CbzReadingDirection.LEFT_TO_RIGHT,
            CbzReadingDirection.resolve(null, prefersRightToLeft = false)
        )
        assertEquals(
            CbzReadingDirection.RIGHT_TO_LEFT,
            CbzReadingDirection.resolve(null, prefersRightToLeft = true)
        )
        assertEquals(
            CbzReadingDirection.LEFT_TO_RIGHT,
            CbzReadingDirection.resolve("ltr", prefersRightToLeft = true)
        )
        assertEquals(
            CbzReadingDirection.RIGHT_TO_LEFT,
            CbzReadingDirection.resolve("rtl", prefersRightToLeft = false)
        )
        assertTrue(CbzReadingDirection.fromKey("unknown") == null)
        assertEquals(CbzReadingDirection.RIGHT_TO_LEFT, CbzReadingDirection.LEFT_TO_RIGHT.next())
    }
}
