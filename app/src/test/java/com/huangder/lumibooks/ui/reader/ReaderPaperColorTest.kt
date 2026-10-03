package com.huangder.lumibooks.ui.reader

import android.app.Application
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderPaperColorTest {
    @Test fun `preset paper is available before document and renderer load`() {
        val state = ReaderUiState(activeReaderLayoutThemeSuiteId = "sepia", readerDisplayMode = "day")
        assertNull(state.book)
        assertTrue(state.isLoading)
        assertEquals(0xFFF5E6D3.toInt(), state.resolveReaderPaperColor(false))
        assertEquals(0xFF2B2118.toInt(), state.copy(readerDisplayMode = "night").resolveReaderPaperColor(false))
    }

    @Test fun `active suite mode and custom paper are resolved without content`() {
        val color = ReaderBackgroundPreset("paper", ReaderBackgroundType.COLOR, "#C5D6E7")
        val state = ReaderUiState(
            activeReaderLayoutThemeSuiteId = "custom-suite",
            readerThemeSuites = listOf(ReaderThemeSuite("custom-suite",
                settings = ReaderThemeSettings(backgroundSelection = color.selectionKey),
                darkSettings = ReaderThemeSettings(backgroundSelection = "green"))),
            customReaderBackgrounds = listOf(color), readerDisplayMode = "system"
        )
        assertEquals(0xFFC5D6E7.toInt(), state.resolveReaderPaperColor(false))
        assertEquals(0xFF142A1A.toInt(), state.resolveReaderPaperColor(true))
        assertEquals(0xFFFBFBFC.toInt(), state.copy(eInkModeEnabled = true).resolveReaderPaperColor(true))
    }

    @Test fun `image uses its configured base paper before image decode`() {
        val image = ReaderBackgroundPreset("image", ReaderBackgroundType.IMAGE, "missing.jpg")
        val state = ReaderUiState(activeReaderLayoutThemeSuiteId = "image-suite",
            readerThemeSuites = listOf(ReaderThemeSuite("image-suite", settings = ReaderThemeSettings(
                backgroundSelection = image.selectionKey, backgroundColorSelection = "green"))),
            customReaderBackgrounds = listOf(image), readerDisplayMode = "day")
        assertEquals(0xFFE8F5E9.toInt(), state.resolveReaderPaperColor(false))
    }
}
