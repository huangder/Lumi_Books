package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderCornerMargins
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.util.epub.EpubRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderStateSlicesTest {
    @Test
    fun displayUsesTheActiveSuitesCornerMarginsForTheActualMode() {
        val light = ReaderCornerMargins(leftDp = 0f, topDp = 40f)
        val dark = ReaderCornerMargins(rightDp = 15f, bottomDp = 60f)
        val suite = ReaderThemeSuite(
            id = "day", settings = ReaderThemeSettings(),
            lightSettings = ReaderThemeSettings(cornerMargins = light),
            darkSettings = ReaderThemeSettings(cornerMargins = dark)
        )
        val state = ReaderUiState(readerThemeSuites = listOf(suite), readerCornerMargins = dark)
        assertEquals(light, state.withActiveThemeSettingsForMode(false).readerCornerMargins)
        assertEquals(dark, state.withActiveThemeSettingsForMode(true).readerCornerMargins)
        val fallback = state.copy(readerThemeSuites = emptyList())
        assertEquals(dark, fallback.withActiveThemeSettingsForMode(false).readerCornerMargins)
    }

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
        assertEquals(ReaderLayoutTarget.READER_LAYOUT,
            readerLayoutTargetFor("MARKDOWN", EpubRenderMode.BOOK_LAYOUT))
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
