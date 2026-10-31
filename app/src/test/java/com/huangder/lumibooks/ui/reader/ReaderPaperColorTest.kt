package com.huangder.lumibooks.ui.reader

import android.app.Application
import kotlinx.coroutines.flow.first
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.huangder.lumibooks.util.epub.EpubRenderMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderPaperColorTest {
    @Test fun `opening resolves saved night and book scoped paper before reader creation`() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = com.huangder.lumibooks.data.local.DataStoreManager(context)
            .readerPreferences("opening-test").first()
        val book = Book("opening-test", "title", "author", "book.txt", null, BookFormat.TXT, 0L, 0f, createdAt = 0L)
        val sepia = ReaderThemeSuite("sepia", settings = ReaderThemeSettings(backgroundSelection = "sepia"))
        val snapshot = preferences.copy(
            readerDisplayMode = "night",
            readerThemeSuiteState = com.huangder.lumibooks.domain.model.ReaderThemeSuiteState(listOf(sepia), "sepia"),
            readerThemeSuiteBookScoped = true,
            readerThemeSuiteBookActiveId = "sepia"
        )
        assertEquals(0xFF2B2118.toInt(), snapshot.resolveOpeningPaperColor(book, false))
        assertEquals(0xFFF5E6D3.toInt(), snapshot.copy(readerDisplayMode = "day").resolveOpeningPaperColor(book, true))
    }

    @Test fun `white opening paper is softened while dark paper stays unchanged`() {
        assertEquals(0xFFF0F0F0.toInt(), readerTransitionPaperColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFF0F0F0.toInt(), readerTransitionPaperColor(0xFFFBFBFC.toInt()))
        assertEquals(0xFF111111.toInt(), readerTransitionPaperColor(0xFF111111.toInt()))
    }

    @Test fun `preset paper is available before document and renderer load`() {
        val state = ReaderUiState(activeReaderLayoutThemeSuiteId = "sepia", readerDisplayMode = "day")
        assertNull(state.book)
        assertTrue(state.isLoading)
        assertEquals(0xFFF5E6D3.toInt(), state.resolveReaderPaperColor(false))
        assertEquals(0xFF2B2118.toInt(), state.copy(readerDisplayMode = "night").resolveReaderPaperColor(false))
    }

    @Test fun `publisher suite uses dark fallback paper in night mode`() {
        val state = ReaderUiState(
            book = Book("book", "title", "author", "book.epub", null, BookFormat.EPUB, 0L, 0f, createdAt = 0L),
            renderMode = EpubRenderMode.BOOK_LAYOUT,
            readerDisplayMode = "night"
        )
        assertTrue(state.keepsPublisherPaint())
        assertEquals(0xFF111111.toInt(), state.resolveReaderPaperColor(false))
        assertEquals(0xFFFBFBFC.toInt(), state.copy(readerDisplayMode = "day").resolveReaderPaperColor(false))
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
        val image = ReaderBackgroundPreset(
            "image",
            ReaderBackgroundType.IMAGE,
            "missing.jpg",
            dominantColor = 0xFFB58A62.toInt()
        )
        val state = ReaderUiState(activeReaderLayoutThemeSuiteId = "image-suite",
            readerThemeSuites = listOf(ReaderThemeSuite("image-suite", settings = ReaderThemeSettings(
                backgroundSelection = image.selectionKey, backgroundColorSelection = "green"))),
            customReaderBackgrounds = listOf(image), readerDisplayMode = "day")
        assertEquals(0xFFB58A62.toInt(), state.resolveReaderPaperColor(false))
    }
}
