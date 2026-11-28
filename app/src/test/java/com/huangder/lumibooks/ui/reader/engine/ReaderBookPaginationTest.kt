package com.huangder.lumibooks.ui.reader.engine

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.huangder.lumibooks.ui.reader.prepareReaderEnglishHyphenation
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

    @Test fun typographyWindowUsesChapterOffsetsWithoutPublishingProvisionalCounts() = runBlocking {
        val engine = engine()
        val chapter = text.repeat(15)
        val window = typographyWindow(chapter, 3_000, 250)
        val sample = engine.layoutWindow(0, chapter, window.first, window.last + 1)
        assertTrue(window.first > 0)
        assertTrue(window.last < chapter.lastIndex)
        assertTrue(sample.pages.any { 3_000 in it.startCharOffset until it.endCharOffset })
        assertTrue(sample.pages.all { it.startCharOffset >= window.first && it.endCharOffset <= window.last + 1 })
        assertNull(engine.getChapterLayout(0))
        assertNull(engine.bookPages.value.chapterCounts[0])
    }

    @Test fun typographyHandoffKeepsVisibleRangeAndEveryChapterCharacter() = runBlocking {
        val engine = engine()
        val chapter = text.repeat(10)
        val exact = engine.layout(0, chapter)
        val page = exact.pages[4]
        val visible = page.copy(startCharOffset = page.startCharOffset + 3, endCharOffset = page.endCharOffset + 5)
        val combined = retainTypographyPages(exact, listOf(visible))
        assertTrue(combined.pages.any { it.startCharOffset == visible.startCharOffset && it.endCharOffset == visible.endCharOffset })
        assertEquals(0, combined.pages.first().startCharOffset)
        assertEquals(chapter.length, combined.pages.last().endCharOffset)
        assertTrue(combined.pages.zipWithNext().all { (left, right) -> left.endCharOffset == right.startCharOffset })
        assertSame(exact, engine.getChapterLayout(0))
        assertEquals(exact.totalPages, engine.bookPages.value.chapterCounts[0])
    }

    @Test fun typographyAnchorSkipsInsertedAndRemovedParagraphSpacers() {
        val adjusted = "First\n\nSecond\n\nThird"
        val base = "First\nSecond\nThird"
        assertEquals(adjusted.indexOf("Third"), typographyOffset(base, adjusted, base.indexOf("Third")))
        assertEquals(base.indexOf("Second"), typographyOffset(adjusted, base, adjusted.indexOf("Second")))
    }

    @Test fun backgroundIndexMatchesForegroundPageBoundaries() = runBlocking {
        val styled = SpannableString("第一章\n" + "測試文字排版。\n".repeat(100)).apply {
            setSpan(RelativeSizeSpan(1.8f), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val english = prepareReaderEnglishHyphenation("Characteristically sophisticated typography ".repeat(40))
        for ((mode, chapter) in listOf(
            ReaderWritingMode.HORIZONTAL to styled,
            ReaderWritingMode.HORIZONTAL to english,
            ReaderWritingMode.VERTICAL_RL to styled
        )) {
            val foreground = PageLayoutEngine().apply { configure(280, 360, 24f, chapterCount = 1, writingMode = mode) }
            val background = PageLayoutEngine().apply { configure(280, 360, 24f, chapterCount = 1, writingMode = mode) }
            val expected = foreground.layout(0, chapter)
            background.measurePageCount(0, chapter, background.bookPages.value.generation)
            assertEquals(expected.totalPages, background.bookPages.value.totalPages)
            assertEquals(expected.pages.map { it.startCharOffset }, background.bookPages.value.chapterStarts[0])
            assertNull(background.getChapterLayout(0))
        }
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
