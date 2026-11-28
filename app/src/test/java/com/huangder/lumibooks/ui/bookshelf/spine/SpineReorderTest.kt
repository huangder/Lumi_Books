package com.huangder.lumibooks.ui.bookshelf.spine

import com.huangder.lumibooks.ui.home.mergeCustomOrder
import org.junit.Assert.*
import org.junit.Test

class SpineReorderTest {
    @Test fun `a book dropped on the right of an empty second or third row stays there at the left`() {
        val source = SpineArrangement(listOf("book"), emptyMap())
        for (row in listOf(1, 2)) {
            val moved = move(source, "book", bay = 0, row = row, x = 300f)!!
            assertEquals(source.order, moved.order)
            val reloaded = SpineArrangement(moved.order, moved.rowsByItem.toMap())
            val pages = reloaded.layout(320f, 160f, 3)
            val item = pages.single().rows[row].items.single()
            assertEquals("book", item.id)
            val left = if (item.pose == SpinePose.Leaning) {
                item.x + item.height * kotlin.math.sin(Math.toRadians(item.angle.toDouble())).toFloat()
            } else item.x
            assertEquals(0f, left, 0.01f)
            assertTrue(pages.single().rows[0].items.isEmpty())
        }
    }

    @Test fun `moving onto a row in the right bay fills that row on the left before spilling right`() {
        val ids = (1..8).map { "folder:$it" }
        val source = SpineArrangement(ids, emptyMap())
        val moved = move(source, ids.first(), bay = 1, row = 1, x = 99f, width = 100f)!!
        val pages = moved.layout(100f, 160f, 3)
        assertEquals(listOf(ids[2], ids[3]), pages[0].rows[1].items.map { it.id })
        assertEquals(listOf(ids.first()), pages[1].rows[1].items.map { it.id })
        assertEquals(0f, pages[1].rows[1].items.single().x, 0.01f)

        val shortened = moved.copy(order = moved.order.filterNot { it == ids[2] })
        val compact = shortened.layout(100f, 160f, 3)
        assertEquals(listOf(ids[3], ids.first()), compact.first().rows[1].items.map { it.id })
        assertTrue(compact.drop(1).all { it.rows[1].items.isEmpty() })
    }

    @Test fun `a full row spills into the same row in the next bay`() {
        val ids = (1..5).map { "folder:$it" }
        val source = SpineArrangement(ids, emptyMap())
        val moved = move(source, ids.last(), bay = 0, row = 0, x = 99f, width = 100f)!!
        val pages = moved.layout(100f, 160f, 3)
        assertEquals(listOf(ids[0], ids[1]), pages[0].rows[0].items.map { it.id })
        assertEquals(listOf(ids.last()), pages[1].rows[0].items.map { it.id })
        assertEquals(listOf(ids[2], ids[3]), pages[0].rows[1].items.map { it.id })
    }

    @Test fun `folder can move between books and retain its position after reload`() {
        val books = listOf("a", "b", "c")
        val folder = "folder:one"
        val original = spineShelfOrder(books, listOf(folder), savedOrder = emptyList())
        val source = SpineArrangement(original, emptyMap())
        val reordered = arrangeSpineRows(source, source.layout(320f, 160f, 3), folder,
            SpineDropTarget(0, 0, 2)) { false }!!
        val saved = mergeCustomOrder(listOf("outside") + books, reordered.order)
        assertEquals(listOf("a", "b", folder, "c"), spineShelfOrder(books, listOf(folder), saved))
        assertTrue("outside" in saved)
    }

    @Test fun `pinning still controls book order while folders can move past pinned books`() {
        val folder = "folder:one"
        val original = listOf("pinned", folder, "ordinary")
        val source = SpineArrangement(original, emptyMap())
        val pages = source.layout(320f, 160f, 3)
        assertNull(arrangeSpineRows(source, pages, "ordinary", SpineDropTarget(0, 0, 0)) { it == "pinned" })
        val moved = arrangeSpineRows(source, pages, folder, SpineDropTarget(0, 0, 2)) { it == "pinned" }
        assertEquals(listOf("pinned", "ordinary", folder), moved?.order)
        assertEquals(original, spineShelfOrder(listOf("pinned", "ordinary"), listOf(folder), listOf("ordinary", folder, "pinned")))
    }

    @Test fun `new items and stale ids preserve the saved slots of existing folders`() {
        val folder = "folder:one"
        val newFolder = "folder:new"
        assertEquals(
            listOf(newFolder, "new", "a", folder, "b"),
            spineShelfOrder(listOf("new", "a", "b"), listOf(folder, newFolder), listOf("a", folder, "gone", "b"))
        )
    }

    @Test fun `vertical drag near an edge does not start horizontal scrolling`() {
        assertFalse(isSpineDragHorizontal(17f, 100f, 12f))
        assertFalse(isSpineDragHorizontal(8f, 0f, 12f))
        assertTrue(isSpineDragHorizontal(-90f, 22f, 12f))
    }

    private fun move(source: SpineArrangement, item: String, bay: Int, row: Int, x: Float, width: Float = 320f): SpineArrangement? {
        val pages = source.layout(width, 160f, 3)
        val target = spineDropTarget(pages, bay, row, x, item)!!
        return arrangeSpineRows(source, pages, item, target) { false }
    }
}
