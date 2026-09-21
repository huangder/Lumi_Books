package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.ui.reader.engine.PageAnimationController.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageTurnQueueTest {
    @Test
    fun rapidSameDirectionInputIsNotCollapsed() {
        val queue = ReaderPageTurnQueue()
        repeat(5) { queue.offer(Direction.NEXT) }

        assertEquals(5, queue.size)
        repeat(5) { assertEquals(Direction.NEXT, queue.poll()) }
        assertEquals(Direction.NONE, queue.poll())
    }

    @Test
    fun oppositeInputCancelsOnlyNewestPendingStep() {
        val queue = ReaderPageTurnQueue()
        repeat(3) { queue.offer(Direction.NEXT) }
        queue.offer(Direction.PREV)

        assertEquals(2, queue.size)
        assertEquals(Direction.NEXT, queue.poll())
        assertEquals(Direction.NEXT, queue.poll())
    }

}
