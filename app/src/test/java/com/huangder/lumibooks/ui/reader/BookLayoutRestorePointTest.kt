package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookLayoutRestorePointTest {
    @Test
    fun progressMapsBackToTheSamePageIndex() {
        // 第 3 章第 2 页（0 基）/ 共 5 页 → 保存为 (2 + 1) / 5 = 0.6。
        val point = bookLayoutRestorePoint(
            readingProgress = (3 + 0.6f) / 20f,
            chapterCount = 20
        )

        assertEquals(3, point.chapterIndex)
        assertEquals(0.6f, point.chapterFraction, 0.0001f)
        assertEquals(
            2,
            restoredPagedPageIndex(point.chapterFraction, 5, ReaderPageFractionSemantics.INCLUSIVE_PAGE_END)
        )
    }

    @Test
    fun lastPageOfAChapterStaysInThatChapter() {
        // 第 7 章最后一页：progress * chapterCount = 7 + 1.0 → 必须回到第 7 章页尾。
        val point = bookLayoutRestorePoint(
            readingProgress = 8f / 20f,
            chapterCount = 20
        )

        assertEquals(7, point.chapterIndex)
        assertEquals(1f, point.chapterFraction, 0.0001f)
        assertEquals(
            4,
            restoredPagedPageIndex(point.chapterFraction, 5, ReaderPageFractionSemantics.INCLUSIVE_PAGE_END)
        )
    }

    @Test
    fun firstChapterKeepsZeroFraction() {
        val point = bookLayoutRestorePoint(readingProgress = 0f, chapterCount = 20)

        assertEquals(0, point.chapterIndex)
        assertEquals(0f, point.chapterFraction, 0.0001f)
    }

    @Test
    fun retainedLocatorOnlySurvivesSamePageReports() {
        val locator = """{"href":"a.xhtml"}"""

        // 同一页重复上报：保留旧锚点。
        assertEquals(
            locator,
            retainedEpubLocator(
                previous = locator,
                incoming = null,
                chapterChanged = false,
                pageChanged = false
            )
        )
        // 翻页但没有新锚点：丢弃，避免把上一页位置存进进度。
        assertNull(
            retainedEpubLocator(
                previous = locator,
                incoming = null,
                chapterChanged = false,
                pageChanged = true
            )
        )
        // 换章但没有新锚点：同样丢弃。
        assertNull(
            retainedEpubLocator(
                previous = locator,
                incoming = null,
                chapterChanged = true,
                pageChanged = false
            )
        )
        // 有新锚点：总是用新的。
        assertEquals(
            "new",
            retainedEpubLocator(
                previous = locator,
                incoming = "new",
                chapterChanged = true,
                pageChanged = true
            )
        )
    }
}
