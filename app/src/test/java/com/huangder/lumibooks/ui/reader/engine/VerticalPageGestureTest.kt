package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalPageGestureTest {
    @Test
    fun `up and down swipes map to next and previous pages`() {
        assertEquals(1, verticalPageDirectionForDelta(-13f))
        assertEquals(-1, verticalPageDirectionForDelta(13f))
        assertEquals(0, verticalPageDirectionForDelta(12f))
        assertEquals(0, verticalPageDirectionForDelta(Float.NaN))
    }

    @Test
    fun `distance threshold commits a vertical page turn`() {
        assertTrue(
            shouldCommitVerticalPageTurn(
                deltaY = 81f,
                viewportHeight = 1000f,
                elapsedMillis = 1000L,
                density = 1f
            )
        )
        assertFalse(
            shouldCommitVerticalPageTurn(
                deltaY = 79f,
                viewportHeight = 1000f,
                elapsedMillis = 1000L,
                density = 1f
            )
        )
    }

    @Test
    fun `fast short swipe commits while slow short drag cancels`() {
        assertTrue(
            shouldCommitVerticalPageTurn(
                deltaY = -50f,
                viewportHeight = 1000f,
                elapsedMillis = 100L,
                density = 1f
            )
        )
        assertFalse(
            shouldCommitVerticalPageTurn(
                deltaY = -50f,
                viewportHeight = 1000f,
                elapsedMillis = 500L,
                density = 1f
            )
        )
        assertFalse(shouldCommitVerticalPageTurn(50f, 0f, 100L, 1f))
        assertFalse(shouldCommitVerticalPageTurn(Float.NaN, 1000f, 100L, 1f))
    }

    @Test
    fun `next page frame moves current and next together`() {
        val start = verticalPageFrame(
            direction = PageAnimationController.Direction.NEXT,
            offset = 0f,
            viewportHeight = 1000f
        )
        val middle = verticalPageFrame(
            direction = PageAnimationController.Direction.NEXT,
            offset = -320f,
            viewportHeight = 1000f
        )
        val end = verticalPageFrame(
            direction = PageAnimationController.Direction.NEXT,
            offset = -1000f,
            viewportHeight = 1000f
        )

        assertEquals(0f, start.currentY, 0f)
        assertEquals(1000f, start.nextY, 0f)
        assertEquals(-320f, middle.currentY, 0f)
        assertEquals(680f, middle.nextY, 0f)
        assertEquals(-1000f, end.currentY, 0f)
        assertEquals(0f, end.nextY, 0f)
        assertTrue(middle.currentVisible)
        assertTrue(middle.nextVisible)
        assertFalse(middle.previousVisible)
    }

    @Test
    fun `previous page frame moves current and previous together`() {
        val frame = verticalPageFrame(
            direction = PageAnimationController.Direction.PREV,
            offset = 280f,
            viewportHeight = 1000f
        )

        assertEquals(280f, frame.currentY, 0f)
        assertEquals(-720f, frame.previousY, 0f)
        assertEquals(1000f, frame.nextY, 0f)
        assertTrue(frame.currentVisible)
        assertTrue(frame.previousVisible)
        assertFalse(frame.nextVisible)
    }

    @Test
    fun `idle frame hides both adjacent pages`() {
        val frame = verticalPageFrame(
            direction = PageAnimationController.Direction.NONE,
            offset = 240f,
            viewportHeight = 1000f
        )

        assertEquals(0f, frame.currentY, 0f)
        assertEquals(-1000f, frame.previousY, 0f)
        assertEquals(1000f, frame.nextY, 0f)
        assertTrue(frame.currentVisible)
        assertFalse(frame.previousVisible)
        assertFalse(frame.nextVisible)
    }
}
