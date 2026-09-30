package com.huangder.lumibooks.ui.animation

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookReaderHandoffTest {
    private val book = Book("a", "A", "Author", "a.txt", null, BookFormat.TXT, 0, 0f, createdAt = 0)
    private val source = Rect(100f, 300f, 300f, 600f)

    private fun TestScope.transition(): BookReaderTransitionState = BookReaderTransitionState(
        CoroutineScope(coroutineContext + object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000L)
            }
        })
    ).also { it.registerCoverAnchor("cover", book.id, source, 8f, null) }

    @Test fun `early first page overlaps the final window frames`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f)
        state.markReaderReady()
        advanceTimeBy(460)
        assertEquals(BookReaderTransitionPhase.Opening, state.phase)
        advanceTimeBy(100)
        assertEquals(BookReaderTransitionPhase.Ready, state.phase)
        assertTrue(state.sizeSnapshot.value < 1f)
        assertTrue(state.shellAlphaSnapshot.value in 0.01f..0.99f)
        advanceTimeBy(140)
        assertEquals(BookReaderTransitionPhase.Reader, state.phase)
        assertEquals(0f, state.shellAlphaSnapshot.value, 0f)
        advanceUntilIdle()
    }

    @Test fun `late page or error surface reveals without replaying the window`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f)
        advanceTimeBy(720)
        assertEquals(BookReaderTransitionPhase.ReaderLoading, state.phase)
        assertEquals(1f, state.sizeSnapshot.value, 0f)
        state.markReaderReady()
        advanceTimeBy(220)
        assertEquals(BookReaderTransitionPhase.Reader, state.phase)
        advanceUntilIdle()
    }

    @Test fun `back during reveal cancels the opening completion and freezes return geometry`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f)
        state.markReaderReady()
        advanceTimeBy(530)
        assertTrue(state.startClose())
        val restored = Rect(110f, 310f, 310f, 610f)
        state.registerCoverAnchor("cover", book.id, restored, 8f, null)
        advanceTimeBy(80)
        assertEquals(restored, state.sourceBounds)
        state.registerCoverAnchor("cover", book.id, Rect(110f, 350f, 310f, 650f), 8f, null)
        assertEquals(restored, state.sourceBounds)
        state.markReaderReady()
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Library, state.phase)
        assertNull(state.activeBookId)
    }

    @Test fun `stale callback cannot reveal a later opening of the same book`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f)
        val firstSession = state.sessionId
        state.fallbackToLibrary()
        state.startOpen(book, null, source, 8f)
        state.markReaderReady(firstSession)
        advanceTimeBy(720)
        assertFalse(state.readerReady)
        assertEquals(BookReaderTransitionPhase.ReaderLoading, state.phase)
        state.markReaderReady(state.sessionId)
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Reader, state.phase)
    }

    @Test fun `back before first content never exposes the empty reader`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f)
        advanceTimeBy(240)
        assertTrue(state.startClose())
        runCurrent()
        assertEquals(0f, state.readerExitAlphaSnapshot.value, 0f)
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Library, state.phase)
    }

    @Test fun `reader clip follows the cover window endpoints`() {
        val target = Rect(0f, 0f, 1080f, 2400f)
        assertEquals(source, BookReaderMotion.windowBounds(source, target, 0f, 0f, 28f))
        assertEquals(target, BookReaderMotion.windowBounds(source, target, 1f, 1f, 28f))
        val intermediate = BookReaderMotion.windowBounds(source, target, 0.7f, 0.7f, 28f)
        assertTrue(intermediate.width > source.width && intermediate.width < target.width)
        assertTrue(intermediate.height > source.height && intermediate.height < target.height)
    }

    @Test fun `return waits for the lazy layout to settle and preserves the original anchor`() = runTest {
        val state = transition()
        state.registerCoverAnchor("duplicate", book.id, Rect(10f, 10f, 90f, 100f), 8f, null)
        state.startOpen(book, null, source, 8f)
        state.markReaderReady()
        advanceUntilIdle()
        state.startClose()
        advanceTimeBy(20)
        val restored = Rect(140f, 520f, 340f, 820f)
        state.registerCoverAnchor("cover", book.id, restored, 8f, null)
        advanceTimeBy(80)
        assertEquals(restored, state.sourceBounds)
        assertEquals("cover", state.closeAnchorKey)
        state.registerCoverAnchor("cover", book.id, source, 8f, null)
        assertEquals(restored, state.sourceBounds)
        advanceUntilIdle()
    }

    @Test fun `cover flow return geometry stops changing once reverse motion starts`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 8f, BookReaderPresentation.CoverFlow)
        state.markReaderReady()
        advanceUntilIdle()
        state.startClose()
        val restored = source.translate(androidx.compose.ui.geometry.Offset(40f, 70f))
        state.updateCoverFlowReturnSource(book.id, restored)
        advanceTimeBy(40)
        state.updateCoverFlowReturnSource(book.id, source)
        assertEquals(restored, state.sourceBounds)
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Library, state.phase)
    }
}
