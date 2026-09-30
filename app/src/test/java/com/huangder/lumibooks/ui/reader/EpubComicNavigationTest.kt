package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.epub.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubComicNavigationTest {
    private val pages = listOf(EpubComicPage("a.xhtml", "a.jpg", 0, 0),
        EpubComicPage("a.xhtml", "a.jpg", 1, 0), EpubComicPage("c.xhtml", "c.jpg", 0, 2))
    private val index = EpubComicIndex(pages, listOf("A", "Text", "C"), false, false)

    @Test fun missingChapterMapsForwardThenBackAtEnd() {
        assertEquals(2, index.pageForChapter(1))
        assertEquals(2, index.pageForChapter(4))
    }

    @Test fun scrollPositionSurvivesRestartAndTextRoundTrip() {
        val position = EpubComicPosition.fromPage(pages[1], 0.72f)
        val saved = EpubComicPosition.decode(position.encode())!!
        assertEquals(1, index.restore(saved))
        assertEquals(0.72f, saved.scrollFraction, 0.0001f)
        assertEquals(position, comicPositionFromLocator(epubTextLocator(position, EpubRenderMode.BOOK_LAYOUT)))
        val native = ReaderPositionLocator.fromJson(epubTextLocator(position, EpubRenderMode.READER_LAYOUT))!!
        assertEquals(0, native.chapterIndex)
        assertEquals(0f, native.chapterFraction)
        assertEquals((1f + 0.72f) / 2f / 3f, index.textProgress(1, 0.72f), 0.0001f)
    }

    @Test fun inkIdentityRemapsAfterInsertedPagesWithoutChangingCoordinates() {
        val stroke = PdfInkStroke(page = 1, points = listOf(PdfInkPoint(0.2f, 0.6f)),
            tool = PdfInkTool.PEN, color = "#ff0000", width = 0.01f)
        val encoded = encodeComicInk(stroke, EpubComicPosition.fromPage(pages[1]))
        val changed = index.copy(pages = listOf(EpubComicPage("cover.xhtml", "cover.jpg", 0, 0)) + pages)
        assertEquals(2, changed.find(comicPositionFromLocator(encoded)!!))
        assertEquals(stroke.points, PdfInkStrokeLocatorV1.decode(encoded)!!.points)
        assertTrue(isEpubComicInk(EpubComicInkPenType))
        assertFalse(isEpubComicInk("highlight"))
        assertFalse(isEpubComicInk(PdfInkPenType))
    }
}
