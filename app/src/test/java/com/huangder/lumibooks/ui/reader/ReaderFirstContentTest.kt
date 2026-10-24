package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.*
import org.junit.Test

class ReaderFirstContentTest {
    private fun ready(format: BookFormat) = ReaderUiState(
        book = Book("book", "Title", "", "document.md", null, format, 0, 0f, createdAt = 0),
        isLoading = false, pageReady = true
    )

    @Test fun markdownOpensAfterNativeViewportIsReadyWithoutCanvasDrawCallback() {
        val state = ready(BookFormat.MARKDOWN)
        assertTrue(readerFirstContentReady(state, continuous = true, continuousContentDrawn = false))
        assertFalse(readerFirstContentReady(state.copy(isLoading = true), true, false))
        assertFalse(readerFirstContentReady(state.copy(pageReady = false), true, false))
        assertFalse(readerFirstContentReady(state.copy(pendingPageFraction = 0.5f), true, false))
    }

    @Test fun continuousTextStillWaitsForItsDrawCallback() {
        val state = ready(BookFormat.TXT)
        assertFalse(readerFirstContentReady(state, true, false))
        assertTrue(readerFirstContentReady(state, true, true))
        assertTrue(readerFirstContentReady(state, false, false))
    }

    @Test fun failuresRevealErrorSurfaceWithoutWaitingForContentOrRestore() {
        val state = ready(BookFormat.MARKDOWN).copy(
            pageReady = false, error = "Render failed", pendingPageFraction = 0.5f
        )
        assertTrue(readerFirstContentReady(state, true, false))
    }
}
