package com.huangder.lumibooks.ui.reader

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
}
