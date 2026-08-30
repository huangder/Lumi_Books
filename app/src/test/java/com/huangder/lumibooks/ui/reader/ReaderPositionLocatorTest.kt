package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPositionLocatorTest {
    @Test
    fun `round trip preserves paged character anchor`() {
        val locator = ReaderPositionLocator(
            chapterIndex = 4,
            chapterFraction = 0.25f,
            flow = ReaderPositionFlow.PAGED,
            characterOffset = 812
        )

        assertEquals(locator, ReaderPositionLocator.fromJson(locator.toJson()))
    }

    @Test
    fun `epub and malformed locators are ignored`() {
        assertNull(ReaderPositionLocator.fromJson("{\"href\":\"chapter.xhtml\"}"))
        assertNull(ReaderPositionLocator.fromJson("not-json"))
    }

    @Test
    fun `legacy inclusive page end restores the page that was being read`() {
        assertEquals(
            0,
            restoredPagedPageIndex(
                chapterFraction = 1f / 20f,
                totalPages = 20,
                semantics = ReaderPageFractionSemantics.INCLUSIVE_PAGE_END
            )
        )
        assertEquals(
            7,
            restoredPagedPageIndex(
                chapterFraction = 8f / 20f,
                totalPages = 20,
                semantics = ReaderPageFractionSemantics.INCLUSIVE_PAGE_END
            )
        )
    }

    @Test
    fun `start fraction keeps direct navigation semantics`() {
        assertEquals(
            8,
            restoredPagedPageIndex(
                chapterFraction = 8f / 20f,
                totalPages = 20,
                semantics = ReaderPageFractionSemantics.START
            )
        )
    }

    @Test
    fun `pending exact or legacy anchor blocks transient progress writes`() {
        val exact = ReaderPositionLocator(
            chapterIndex = 0,
            chapterFraction = 0f,
            flow = ReaderPositionFlow.PAGED,
            characterOffset = 0
        )

        assertEquals(true, hasPendingReaderRestore(exact, 0f))
        assertEquals(true, hasPendingReaderRestore(null, 0.05f))
        assertEquals(false, hasPendingReaderRestore(null, 0f))
    }
}
