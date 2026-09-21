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
class CoverFlowReaderStateTest {
    private val book = Book("a", "A", "Author", "a.txt", null, BookFormat.TXT, 0, 0f, createdAt = 0)
    private val source = Rect(100f, 300f, 300f, 600f)
    private fun TestScope.transition() = BookReaderTransitionState(
        CoroutineScope(coroutineContext + object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000L)
            }
        })
    )

    @Test fun `opening after focus change does not require a layout callback`() = runTest {
        val state = transition()
        assertTrue(state.startOpen(book, null, source, 0f, BookReaderPresentation.CoverFlow))
        assertEquals(BookReaderPresentation.CoverFlow, state.presentation)
        assertEquals(source, state.sourceBounds)
        assertEquals(0f, state.sourceCoverAlpha(book.id, "late-anchor"), 0f)
        state.markReaderReady()
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Reader, state.phase)
        assertEquals(1f, state.coverFlowProgressSnapshot.value, 0f)
    }

    @Test fun `slow reader retains sharp cover and reverse uses restored focus geometry`() = runTest {
        val state = transition()
        state.startOpen(book, null, source, 0f, BookReaderPresentation.CoverFlow)
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.ReaderLoading, state.phase)
        assertEquals(1f, CoverFlowReaderMotion.coverAlpha(state.coverFlowProgressSnapshot.value), 0f)
        state.markReaderReady()
        advanceUntilIdle()
        assertTrue(state.startClose())
        val restored = Rect(110f, 250f, 310f, 550f)
        state.updateCoverFlowReturnSource(book.id, restored)
        advanceTimeBy(80)
        runCurrent()
        assertEquals(restored, state.sourceBounds)
        assertTrue(state.coverFlowProgressSnapshot.value < 1f)
        advanceUntilIdle()
        assertEquals(BookReaderTransitionPhase.Library, state.phase)
        // The outgoing navigation destination must stay invisible until it is disposed.
        assertEquals(0f, CoverFlowReaderMotion.readerAlpha(state.coverFlowProgressSnapshot.value), 0f)
    }

    @Test fun `window transition inherits hidden progress from its source cover`() = runTest {
        val state = transition()
        state.registerCoverAnchor(
            anchorKey = "home-continue",
            bookId = book.id,
            bounds = source,
            cornerRadiusDp = 8f,
            titleStyle = null,
            showReadingProgress = false
        )

        assertTrue(state.startOpen(book, null, source, 8f))
        assertFalse(state.coverShowsReadingProgress)
        state.markReaderReady()
        advanceUntilIdle()
    }

    @Test fun `entering uses staggered nonlinear motion with finite endpoints`() {
        for (rank in 0..5) {
            assertEquals(0f, CoverFlowEntranceMotion.cover(0f, rank.toFloat()), 0f)
            assertEquals(1f, CoverFlowEntranceMotion.cover(1f, rank.toFloat()), 0f)
            assertEquals(CoverFlowEntranceMotion.cover(0.5f, rank.toFloat()),
                CoverFlowEntranceMotion.cover(0.5f, -rank.toFloat()), 0f)
        }
        assertTrue(CoverFlowEntranceMotion.cover(0.25f, 0f) > CoverFlowEntranceMotion.cover(0.25f, 3f))
        assertTrue(CoverFlowEntranceMotion.button(0.2f, 0) > CoverFlowEntranceMotion.button(0.2f, 4))
    }
}
