package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderBookPaginationTest {
    private val text = "Chapter text wraps onto many lines and pages. ".repeat(30)
    private fun engine() = PageLayoutEngine().apply {
        configure(280, 360, 24f, chapterCount = 12)
    }

    @Test fun fullBookCountsAndAnchorsSurviveEightChapterEviction() = runBlocking {
        val engine = engine()
        val counts = (0 until 12).map { engine.layout(it, text).totalPages }
        assertNull(engine.getChapterLayout(0))
        assertEquals(counts.sum(), engine.bookPages.value.totalPages)
        assertEquals(counts.take(10).sum() + 1, engine.bookPages.value.pageNumber(10, 0))
        assertNotNull(engine.bookPages.value.pageAtCharacter(0, 100))
    }

    @Test fun backgroundCountingLeavesForegroundLayoutsResident() = runBlocking {
        val engine = engine()
        val foreground = engine.layout(0, text)
        val generation = engine.bookPages.value.generation
        (1 until 12).forEach { engine.measurePageCount(it, text, generation) }
        assertSame(foreground, engine.getChapterLayout(0))
        assertNull(engine.getChapterLayout(11))
        assertNotNull(engine.bookPages.value.totalPages)
    }

    @Test fun reflowClearsCountsAndRejectsOldGenerationBeforeRecount() = runBlocking {
        val engine = engine()
        val first = engine.layout(0, text).totalPages
        val generation = engine.bookPages.value.generation
        engine.configure(280, 200, 32f, chapterCount = 12)
        assertTrue(engine.bookPages.value.chapterCounts.all { it == null })
        engine.measurePageCount(1, text, generation)
        assertNull(engine.bookPages.value.chapterCounts[1])
        assertTrue(engine.layout(0, text).totalPages > first)
    }

    @Test fun chapterContentInvalidationKeepsOtherConfirmedCounts() = runBlocking {
        val engine = engine()
        engine.layout(0, text); engine.layout(1, text)
        val before = engine.bookPages.value
        engine.invalidateChapter(0)
        assertNull(engine.bookPages.value.chapterCounts[0])
        assertEquals(before.chapterCounts[1], engine.bookPages.value.chapterCounts[1])
        assertNull(engine.bookPages.value.pageNumber(1, 0))
        engine.layout(0, text.repeat(2))
        assertEquals(engine.bookPages.value.chapterCounts[0]!! + 1, engine.bookPages.value.pageNumber(1, 0))
    }
}
