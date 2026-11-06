package com.huangder.lumibooks.ui.bookshelf.spine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SpineLayoutTest {
    private val entries = (0 until 240).map { SpineEntry("book-$it") }

    private fun layout(seed: Long, list: List<SpineEntry> = entries) =
        SpineLayout.paginate(list, seed, shelfWidth = 340f, maxBookHeight = 150f, rows = 4)

    @Test fun `same seed gives the same shelf`() {
        assertEquals(layout(42L), layout(42L))
    }

    @Test fun `a new process seed re-rolls the poses`() {
        val first = layout(1L).flatMap { page -> page.rows.flatMap { it.items } }.map { it.pose to it.width }
        val second = layout(2L).flatMap { page -> page.rows.flatMap { it.items } }.map { it.pose to it.width }
        assertNotEquals(first, second)
    }

    @Test fun `every book appears exactly once in order`() {
        val ids = layout(7L).flatMap { page -> page.rows.flatMap { it.items } }.map { it.id }
        assertEquals(entries.map { it.id }, ids)
    }

    @Test fun `every page has the requested rows and items stay on the shelf`() {
        val pages = layout(9L)
        assertTrue(pages.size > 1)
        pages.forEach { page ->
            assertEquals(4, page.rows.size)
            page.rows.flatMap { it.items }.forEach { item ->
                assertTrue(item.x >= -0.01f)
                assertTrue(SpineLayout.rightEdge(item) <= 340.01f)
                assertTrue(item.height + item.lift <= 150.01f)
            }
        }
    }

    @Test fun `poses are mostly upright with some leaning and flat books`() {
        val items = layout(11L).flatMap { page -> page.rows.flatMap { it.items } }
        val upright = items.count { it.pose == SpinePose.Upright }
        assertTrue(upright > items.size * 0.65f)
        assertTrue(items.any { it.pose == SpinePose.Leaning })
        assertTrue(items.any { it.pose == SpinePose.Flat })
    }

    @Test fun `a leaning book rests on an upright neighbour or the shelf wall`() {
        repeat(20) { seed ->
            layout(seed.toLong()).forEach { page ->
                page.rows.forEach { row ->
                    row.items.forEachIndexed { index, item ->
                        if (item.pose != SpinePose.Leaning) return@forEachIndexed
                        assertTrue(item.angle < 0f)
                        val previous = row.items.getOrNull(index - 1) ?: return@forEachIndexed
                        assertEquals(SpinePose.Upright, previous.pose)
                        assertTrue(previous.height >= item.height * SpineLayout.LEAN_SUPPORT)
                    }
                }
            }
        }
    }

    @Test fun `a leaning book stays inside its row and never dips below the board`() {
        repeat(30) { seed ->
            layout(seed.toLong()).flatMap { page -> page.rows.flatMap { it.items } }
                .filter { it.pose == SpinePose.Leaning }
                .forEach { item ->
                    // Compose rotationZ is clockwise in y-down screen space, about the bottom-left corner.
                    val radians = Math.toRadians(item.angle.toDouble())
                    val corners = listOf(0f to 0f, item.width to 0f, 0f to -item.height, item.width to -item.height)
                        .map { (x, y) ->
                            (x * cos(radians) - y * sin(radians)).toFloat() + item.x to
                                (x * sin(radians) + y * cos(radians)).toFloat()
                        }
                    corners.forEach { (x, y) ->
                        assertTrue("x=$x", x >= -0.01f && x <= 340.01f)
                        assertTrue("y=$y", y <= 0.01f)
                        assertTrue(-y <= 150.01f)
                    }
                }
        }
    }

    @Test fun `full rows reach the end of the shelf so bays meet without a hole`() {
        repeat(20) { seed ->
            val rows = layout(seed.toLong()).flatMap { it.rows }.filter { it.items.isNotEmpty() }
            rows.dropLast(1).forEach { row ->
                val extent = row.items.maxOf { SpineLayout.rightEdge(it) }
                val groups = row.items.count { it.lift == 0f && it.pose != SpinePose.Leaning }
                if (groups >= 6) assertTrue("extent=$extent", extent >= 340f - 0.5f)
                assertTrue(extent <= 340.01f)
            }
        }
    }

    @Test fun `spreading a row keeps the order and the lean contact`() {
        layout(5L).flatMap { it.rows }.forEach { row ->
            row.items.zipWithNext().forEach { (a, b) -> assertTrue(b.x >= a.x - 0.01f || b.lift > 0f || a.lift > 0f) }
        }
    }

    @Test fun `flat stacks are at most three high`() {
        repeat(20) { seed ->
            layout(seed.toLong()).forEach { page ->
                page.rows.forEach { row ->
                    var stack = 0
                    row.items.forEach { item ->
                        stack = if (item.pose == SpinePose.Flat) {
                            if (item.lift > 0f) stack + 1 else 1
                        } else 0
                        assertTrue(stack <= SpineLayout.MAX_STACK)
                    }
                }
            }
        }
    }

    @Test fun `folders stand upright`() {
        val list = (0 until 30).map { SpineEntry("folder:$it", isFolder = true) }
        layout(3L, list).flatMap { page -> page.rows.flatMap { it.items } }.forEach {
            assertEquals(SpinePose.Upright, it.pose)
            assertEquals(SpineLayout.FOLDER_THICKNESS, it.width, 0f)
        }
    }

    @Test fun `an empty library still has one page of empty shelves`() {
        val pages = layout(1L, emptyList())
        assertEquals(1, pages.size)
        assertTrue(pages.single().rows.all { it.items.isEmpty() })
    }

    @Test fun `four rows only when each row can be tall enough`() {
        assertEquals(3, SpineLayout.rowCount(500f))
        assertEquals(4, SpineLayout.rowCount(640f))
    }
}
