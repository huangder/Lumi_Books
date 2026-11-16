package com.huangder.lumibooks.ui.bookshelf.spine

import com.huangder.lumibooks.ui.bookshelf.pinnedGroupRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpineReorderTest {
    private val rows = listOf(
        SpineRowModel(listOf("a", "b", "c"), explicit = false),
        SpineRowModel(listOf("d", "e"), explicit = false),
        SpineRowModel(emptyList(), explicit = false),
        SpineRowModel(emptyList(), explicit = false)
    )
    private val noFolders: (String) -> Boolean = { false }
    private val oneGroup: (String) -> Int = { 0 }

    @Test
    fun droppingOnAnEmptyRowKeepsTheBookThereAndFreezesTheRowsAbove() {
        val arranged = arrangeSpineRows(rows, "c", targetRow = 3, insertAt = 0, noFolders, oneGroup)!!

        assertEquals(listOf("a", "b", "d", "e", "c"), arranged.order)
        // d keeps its own row though c left room above; c skips the empty row 2.
        assertEquals(mapOf("d" to 1, "c" to 2), arranged.breaks)
    }

    @Test
    fun fullRowsAfterTheTargetKeepFlowingSoOverflowMovesOn() {
        val arranged = arrangeSpineRows(rows, "e", targetRow = 0, insertAt = 1, noFolders, oneGroup)!!

        assertEquals(listOf("a", "e", "b", "c", "d"), arranged.order)
        assertEquals(emptyMap<String, Int>(), arranged.breaks)
    }

    @Test
    fun explicitRowsAfterTheTargetKeepTheirBreak() {
        val explicitRows = rows.toMutableList().apply { this[1] = SpineRowModel(listOf("d", "e"), explicit = true) }
        val arranged = arrangeSpineRows(explicitRows, "a", targetRow = 0, insertAt = 3, noFolders, oneGroup)!!

        assertEquals(listOf("b", "c", "a", "d", "e"), arranged.order)
        assertEquals(mapOf("d" to 1), arranged.breaks)
    }

    @Test
    fun pinnedBooksStayAheadOfTheRest() {
        val pinned = setOf("a", "b")
        val group: (String) -> Int = { if (it in pinned) 0 else 1 }

        assertNull(arrangeSpineRows(rows, "d", targetRow = 0, insertAt = 0, noFolders, group))
        assertEquals(
            listOf("a", "b", "d", "c", "e"),
            arrangeSpineRows(rows, "d", targetRow = 0, insertAt = 2, noFolders, group)!!.order
        )
    }

    @Test
    fun foldersStayInFrontOfTheirRow() {
        val withFolder = listOf(SpineRowModel(listOf("folder:x", "a", "b"), explicit = false))
        val arranged = arrangeSpineRows(withFolder, "b", targetRow = 0, insertAt = 0, { it.startsWith("folder:") }, oneGroup)!!

        assertEquals(listOf("b", "a"), arranged.order)
        assertEquals(emptyMap<String, Int>(), arranged.breaks)
    }

    @Test
    fun rowBreaksStartRowsAndLeaveEmptyRows() {
        val pages = SpineLayout.paginate(
            listOf(SpineEntry("a"), SpineEntry("b", breakBefore = 2), SpineEntry("c")),
            seed = 7L, shelfWidth = 340f, maxBookHeight = 150f, rows = 4
        )
        val page = pages.single()

        assertEquals(listOf("a"), page.rows[0].items.map { it.id })
        assertEquals(emptyList<String>(), page.rows[1].items.map { it.id })
        assertEquals(listOf("b", "c"), page.rows[2].items.map { it.id })
        assertEquals(listOf(false, true, true, false), page.rows.map { it.explicit })
    }

    @Test
    fun pinnedGroupIsTheRunSharingThePinnedState() {
        val pinned = listOf(true, true, false, false, false)
        assertEquals(0..1, pinnedGroupRange(pinned, 1) { it })
        assertEquals(2..4, pinnedGroupRange(pinned, 3) { it })
        assertEquals(IntRange.EMPTY, pinnedGroupRange(pinned, 9) { it })
    }
}
