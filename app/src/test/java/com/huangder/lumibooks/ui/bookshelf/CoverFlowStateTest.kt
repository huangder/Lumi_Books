package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoverFlowStateTest {
    private fun TestScope.motionScope() = CoroutineScope(coroutineContext + object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(testScheduler.currentTime * 1_000_000L)
        }
    })

    @Test fun `focus observed before books load follows drag beyond the initial window`() {
        val state = CoverFlowState()
        val center = derivedStateOf { state.focusedIndex }
        assertEquals(0, center.value)

        state.updateBooks((0..6).map { "book-$it" })
        assertEquals(0, center.value)
        state.beginDrag()
        state.dragBy(6f)

        assertEquals(6, center.value)
        assertEquals("book-6", state.focusedId)
        assertTrue(6 in CoverFlowPhysics.visibleRange(center.value.toFloat(), 7))
    }

    @Test fun `loading books invalidates restored focus without a position change`() {
        val state = CoverFlowState(6f, "book-6")
        val center = derivedStateOf { state.focusedIndex }
        assertEquals(0, center.value)

        state.updateBooks((0..6).map { "book-$it" })

        assertEquals(6f, state.position, 0f)
        assertEquals(6, center.value)
    }

    @Test fun `drag interrupts spring at its current position and cancels pending menu`() = runTest {
        val state = CoverFlowState()
        state.updateBooks(listOf("a", "b", "c", "d"))
        state.settle(motionScope(), 3, true, showMenuAfter = true)
        advanceTimeBy(80)
        runCurrent()
        assertTrue(state.position > 0f && state.position < 3f)
        val interrupted = state.position
        state.beginDrag()
        assertEquals(interrupted, state.position, 0f)
        state.dragBy(0.2f)
        val dragged = state.position
        advanceUntilIdle()
        assertEquals(dragged, state.position, 0f)
        assertNull(state.menuBookId)
    }

    @Test fun `side long press opens menu only after centering`() = runTest {
        val state = CoverFlowState()
        state.updateBooks(listOf("a", "b", "c"))
        state.settle(motionScope(), 2, true, showMenuAfter = true)
        assertNull(state.menuBookId)
        advanceTimeBy(80)
        assertNull(state.menuBookId)
        advanceUntilIdle()
        assertEquals(2f, state.position, 0f)
        assertEquals("c", state.menuBookId)
        assertFalse(state.isMoving)
    }

    @Test fun `removing target prevents delayed action and settles to surviving identity`() = runTest {
        val state = CoverFlowState()
        state.updateBooks(listOf("a", "b", "c"))
        state.settle(motionScope(), 2, true, showMenuAfter = true)
        advanceTimeBy(32)
        state.updateBooks(listOf("a", "b"))
        advanceUntilIdle()
        assertNull(state.menuBookId)
        assertTrue(state.position in 0f..1f)
        assertFalse(state.isMoving)
    }

    @Test fun `saved focus reorders by identity and direct drag is fractional`() {
        val state = CoverFlowState(2f, "c")
        state.updateBooks(listOf("c", "a", "b"))
        assertEquals(0f, state.position, 0f)
        state.beginDrag()
        state.dragBy(0.37f)
        assertEquals(0.37f, state.position, 0f)
        state.restoreBook("b")
        assertEquals(2f, state.position, 0f)
        assertEquals("b", state.focusedId)
    }

    @Test fun `fast fling traverses a large shelf and can be caught mid flight`() = runTest {
        val state = CoverFlowState(20f, "book-20")
        state.updateBooks((0..99).map { "book-$it" })
        state.release(motionScope(), 20f, true)
        advanceUntilIdle()
        assertEquals(30f, state.position, 0f)
        assertEquals("book-30", state.focusedId)
        state.release(motionScope(), -30f, true)
        advanceTimeBy(160)
        runCurrent()
        assertTrue(state.position in 15f..30f)
        state.beginDrag()
        val stopped = state.position
        advanceUntilIdle()
        assertEquals(stopped, state.position, 0f)
    }
}
