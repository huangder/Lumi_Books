package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpGenerationGateTest {
    @Test
    fun `stale generation cannot unlock a newer jump`() {
        val gate = JumpGenerationGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.resolve(first))
        assertTrue(gate.isSettling)
        assertTrue(gate.resolve(second))
        assertFalse(gate.isSettling)
    }

    @Test
    fun `current generation resolves for success failure or timeout`() {
        val gate = JumpGenerationGate()

        repeat(3) {
            val generation = gate.begin()
            assertTrue(gate.isSettling)
            assertTrue(gate.resolve(generation))
            assertFalse(gate.isSettling)
        }
    }

    @Test
    fun `unchanged compose position is not reapplied over internal paging`() {
        val tracker = ReaderPositionRequestTracker()

        assertTrue(tracker.observe(chapterIndex = 4, pageIndex = 0))
        assertFalse(tracker.observe(chapterIndex = 4, pageIndex = 0))
        assertFalse(tracker.observe(chapterIndex = 4, pageIndex = 0))
        assertTrue(tracker.observe(chapterIndex = 4, pageIndex = 1))
        assertFalse(tracker.observe(chapterIndex = 4, pageIndex = 1))
    }
}
