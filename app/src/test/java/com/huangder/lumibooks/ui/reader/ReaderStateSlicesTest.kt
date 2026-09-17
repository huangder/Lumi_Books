package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.util.epub.EpubRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderStateSlicesTest {
    @Test
    fun positionChangesDoNotChangeDocumentRenderOrControlsSlices() {
        val initial = ReaderUiState(isLoading = false, pageReady = true)
        val nextPage = initial.copy(currentPageIndex = 4, globalPageIndex = 12)

        assertEquals(initial.toDocumentState(), nextPage.toDocumentState())
        assertEquals(initial.toRenderSettingsState(), nextPage.toRenderSettingsState())
        assertEquals(initial.toControlsState(), nextPage.toControlsState())
        assertNotEquals(initial.toPositionState(), nextPage.toPositionState())
    }

    @Test
    fun renderChangesDoNotChangeDocumentPositionOrControlsSlices() {
        val initial = ReaderUiState(isLoading = false, pageReady = true)
        val largerText = initial.copy(fontSize = 22f, lineHeight = 1.8f)

        assertEquals(initial.toDocumentState(), largerText.toDocumentState())
        assertEquals(initial.toPositionState(), largerText.toPositionState())
        assertEquals(initial.toControlsState(), largerText.toControlsState())
        assertNotEquals(initial.toRenderSettingsState(), largerText.toRenderSettingsState())
    }

    @Test
    fun onlyEpubLikeBooksInBookLayoutUseTheBookLayoutSettings() {
        assertEquals(
            ReaderLayoutTarget.BOOK_LAYOUT,
            readerLayoutTargetFor("EPUB", EpubRenderMode.BOOK_LAYOUT)
        )
        assertEquals(
            ReaderLayoutTarget.BOOK_LAYOUT,
            readerLayoutTargetFor("MOBI", EpubRenderMode.BOOK_LAYOUT)
        )
        assertEquals(
            ReaderLayoutTarget.READER_LAYOUT,
            readerLayoutTargetFor("EPUB", EpubRenderMode.READER_LAYOUT)
        )
        assertEquals(
            ReaderLayoutTarget.READER_LAYOUT,
            readerLayoutTargetFor("TXT", EpubRenderMode.BOOK_LAYOUT)
        )
        assertEquals(
            ReaderLayoutTarget.READER_LAYOUT,
            readerLayoutTargetFor(null, EpubRenderMode.BOOK_LAYOUT)
        )
    }
}
