package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Reader did not finish the requested page", condition())
    }
}
