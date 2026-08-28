package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
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
    fun `queued curl turn can be consumed after jump generation resolves`() {
        val gate = JumpGenerationGate()
        val sequencer = ReaderCurlTurnSequencer()
        val generation = gate.begin()

        sequencer.offerWhileWaiting(PageAnimationController.Direction.NEXT)
        assertTrue(gate.isSettling)
        assertTrue(gate.resolve(generation))

        // This models ReadView's post(resolve) drain: the queued intent must
        // remain available until the jump lock is released.
        assertEquals(
            PageAnimationController.Direction.NEXT,
            sequencer.poll()
        )
    }

    @Test
    fun `rapid swipes during jump retain one latest turn`() {
        val gate = JumpGenerationGate()
        val sequencer = ReaderCurlTurnSequencer()
        val generation = gate.begin()

        repeat(10) {
            sequencer.offerWhileWaiting(PageAnimationController.Direction.NEXT)
        }

        assertTrue(gate.resolve(generation))
        assertEquals(1, sequencer.pendingSteps)
        assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
        assertEquals(PageAnimationController.Direction.NONE, sequencer.poll())
    }

    @Test
    fun `jump result resumes queued curl only with a usable current page`() {
        assertTrue(
            shouldResumeQueuedCurlAfterJump(
                PageSlotManager.CurrentSlotLoadResult.LOADED,
                currentSlotReady = true
            )
        )
        assertTrue(
            shouldResumeQueuedCurlAfterJump(
                result = null,
                currentSlotReady = true
            )
        )
        assertFalse(
            shouldResumeQueuedCurlAfterJump(
                PageSlotManager.CurrentSlotLoadResult.LOADED,
                currentSlotReady = false
            )
        )
        assertFalse(
            shouldResumeQueuedCurlAfterJump(
                PageSlotManager.CurrentSlotLoadResult.EMPTY,
                currentSlotReady = false
            )
        )
        assertFalse(
            shouldResumeQueuedCurlAfterJump(
                PageSlotManager.CurrentSlotLoadResult.FAILED,
                currentSlotReady = false
            )
        )
        assertFalse(
            shouldResumeQueuedCurlAfterJump(
                PageSlotManager.CurrentSlotLoadResult.CANCELLED,
                currentSlotReady = true
            )
        )
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
