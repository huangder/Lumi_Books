package com.huangder.lumibooks.ui.bookshelf.spine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinePlacementTest {
    private fun items(arrangement: SpineArrangement) =
        arrangement.layout(320f, 160f, 3).flatMap { page -> page.rows.flatMap { it.items } }.associateBy { it.id }

    @Test fun `a chosen placement overrides the automatic look`() {
        val ids = (1..30).map { "book-$it" }
        val upright = items(SpineArrangement(ids, emptyMap(), poses = ids.associateWith { SpinePose.Upright }))
        assertTrue(upright.values.all { it.pose == SpinePose.Upright })

        val first = items(SpineArrangement(ids, emptyMap(), poses = mapOf("book-1" to SpinePose.Flat)))
        assertEquals(SpinePose.Flat, first.getValue("book-1").pose)
    }

    @Test fun `books laid flat by hand start their own pile unless stacked`() {
        val ids = listOf("a", "b")
        val flat = ids.associateWith { SpinePose.Flat }
        val apart = items(SpineArrangement(ids, emptyMap(), poses = flat))
        assertEquals(0f, apart.getValue("a").lift, 0.01f)
        assertEquals(0f, apart.getValue("b").lift, 0.01f)

        val piled = items(SpineArrangement(ids, emptyMap(), stacked = setOf("b"), poses = flat))
        assertEquals(0f, piled.getValue("a").lift, 0.01f)
        assertTrue(piled.getValue("b").lift > 0f)
    }

    @Test fun `stacking is ignored for a book that no longer lies flat`() {
        val ids = listOf("a", "b")
        val placed = items(SpineArrangement(ids, emptyMap(), stacked = setOf("b"),
            poses = mapOf("a" to SpinePose.Flat, "b" to SpinePose.Upright)))
        assertEquals(SpinePose.Upright, placed.getValue("b").pose)
        assertEquals(0f, placed.getValue("b").lift, 0.01f)
    }

    @Test fun `a flat book dropped on a flat book goes on top of its pile`() {
        val poses = mapOf("a" to SpinePose.Flat, "b" to SpinePose.Upright, "c" to SpinePose.Flat)
        val source = SpineArrangement(listOf("a", "b", "c"), emptyMap(), poses = poses)
        val pages = source.layout(320f, 160f, 3)
        val base = pages.single().rows[0].items.first { it.id == "a" }

        val target = spineStackTarget(pages, 0, 0, base.x + base.width / 2f, "c")
        assertEquals("a", target?.stackOn)

        val moved = arrangeSpineRows(source, pages, "c", target!!) { false }!!
        assertEquals(listOf("a", "c", "b"), moved.order)
        assertTrue("c" in moved.stacked)
        val laid = items(moved)
        assertTrue(laid.getValue("c").lift > 0f)
        assertEquals(laid.getValue("a").x, laid.getValue("c").x, base.width)
    }

    @Test fun `only the middle of a flat book or pile takes a stacked drop`() {
        val poses = mapOf("a" to SpinePose.Flat, "b" to SpinePose.Upright)
        val pages = SpineArrangement(listOf("a", "b"), emptyMap(), poses = poses).layout(320f, 160f, 3)
        val row = pages.single().rows[0].items
        val flat = row.first { it.id == "a" }
        val upright = row.first { it.id == "b" }
        assertNull(spineStackTarget(pages, 0, 0, flat.x + 1f, "x"))
        assertNull(spineStackTarget(pages, 0, 0, upright.x + upright.width / 2f, "x"))
        assertNotNull(spineStackTarget(pages, 0, 0, flat.x + flat.width / 2f, "x"))
    }

    @Test fun `dropping a stacked book in a gap takes it off the pile`() {
        val poses = mapOf("a" to SpinePose.Flat, "b" to SpinePose.Flat, "c" to SpinePose.Upright)
        val source = SpineArrangement(listOf("a", "b", "c"), emptyMap(), stacked = setOf("b"), poses = poses)
        val pages = source.layout(320f, 160f, 3)
        val moved = arrangeSpineRows(source, pages, "b", SpineDropTarget(0, 0, slot = 2)) { false }!!
        assertEquals(listOf("a", "c", "b"), moved.order)
        assertFalse("b" in moved.stacked)
    }

    @Test fun `placement keys round trip`() {
        SpinePose.entries.forEach { assertEquals(it, SpinePose.fromStorageKey(it.storageKey)) }
        assertNull(SpinePose.fromStorageKey("sideways"))
        assertNull(SpinePose.fromStorageKey(null))
    }
}
