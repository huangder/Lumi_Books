package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.ui.reader.ReaderPageFractionSemantics
import com.huangder.lumibooks.ui.reader.ReaderPositionFlow
import com.huangder.lumibooks.ui.reader.ReaderPositionLocator
import com.huangder.lumibooks.ui.reader.ReaderUiState
import com.huangder.lumibooks.ui.reader.pendingPagedPageIndex
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderOpeningPreloadTest {
    @Test fun `current page reports ready before adjacent chapters are released`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val views = List(6) { PageContentView(context).apply { configure(24f, Color.BLACK) } }
        val engine = PageLayoutEngine().apply { configure(400, 800, 24f, chapterCount = 3) }
        val requested = ConcurrentHashMap.newKeySet<Int>()
        val manager = PageSlotManager(engine, views[0], views[1], views[2], views[3], views[4], views[5])
        var firstPageReported = false
        manager.backgroundPreparationEnabled = false
        manager.setChapterCount(3)
        manager.contentProvider = { index -> requested.add(index); "Chapter $index\nA short page." }
        manager.onPageChangedCallback = { _, chapter, _, pages ->
            firstPageReported = chapter == 1 && pages > 0
        }
        try {
            manager.initialize(1, 0)
            awaitMain { firstPageReported }
            assertEquals(setOf(1), requested.toSet())
            assertFalse(manager.getPrevSlot().isLoaded)
            assertFalse(manager.getNextSlot().isLoaded)
            manager.backgroundPreparationEnabled = true
            awaitMain { manager.getPrevSlot().isLoaded && manager.getNextSlot().isLoaded }
            assertEquals(setOf(0, 1, 2), requested.toSet())
            assertEquals(1, manager.getCurSlot().chapterIndex)
        } finally {
            manager.destroy()
        }
    }

    @Test fun `first visible single page uses the saved character anchor`() {
        assertFirstPageAtSavedPosition(spread = false)
    }

    @Test fun `first visible spread uses the legacy saved fraction`() {
        assertFirstPageAtSavedPosition(spread = true)
    }

    private fun assertFirstPageAtSavedPosition(spread: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val views = List(6) { PageContentView(context).apply { configure(24f, Color.BLACK) } }
        val engine = PageLayoutEngine().apply { configure(280, 360, 24f, chapterCount = 1) }
        val manager = PageSlotManager(engine, views[0], views[1], views[2], views[3], views[4], views[5],
            spreadEnabled = { spread })
        val text = (1..100).joinToString("\n") { "Paragraph $it begins on a new line in the book." }
        val state = if (spread) {
            ReaderUiState(pendingPageFraction = 0.5f,
                pendingPageFractionSemantics = ReaderPageFractionSemantics.INCLUSIVE_PAGE_END)
        } else {
            ReaderUiState(pendingReaderPosition = ReaderPositionLocator(
                chapterIndex = 0, chapterFraction = 0f, flow = ReaderPositionFlow.PAGED,
                characterOffset = text.indexOf("Paragraph 50 ")
            ))
        }
        val reportedPages = mutableListOf<Int>()
        manager.backgroundPreparationEnabled = false
        manager.setChapterCount(1)
        manager.contentProvider = { text }
        manager.openingPageResolver = { state.pendingPagedPageIndex(it.chapterIndex, it.pages) }
        manager.onPageChangedCallback = { _, _, page, total -> if (total > 0) reportedPages += page }
        try {
            manager.initialize(0, 0)
            awaitMain { reportedPages.isNotEmpty() }
            val pages = checkNotNull(engine.getChapterLayout(0)).pages
            val restoredPage = checkNotNull(state.pendingPagedPageIndex(0, pages))
            assertTrue(restoredPage > 0)
            assertEquals(listOf(restoredPage), reportedPages)
            assertEquals(pages[restoredPage].startCharOffset, manager.getPrimaryContentView().chapterStartOffset)
        } finally {
            manager.destroy()
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Reader did not finish the requested page", condition())
    }
}
