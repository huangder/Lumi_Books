package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPositionLocatorTest {
    @Test
    fun switchingToScrollCapturesPageStartBeforeCountersAreReset() {
        val position = positionForReaderFlowChange(
            chapterIndex = 4, pageIndex = 8, pageCount = 20,
            pendingPosition = null, pendingFraction = 0f, characterOffset = 812,
            destination = ReaderPositionFlow.CONTINUOUS
        )
        assertEquals(4, position.chapterIndex)
        assertEquals(0.4f, position.chapterFraction, 0.0001f)
        assertEquals(812, position.characterOffset)
    }

    @Test
    fun switchingAgainBeforeRestoreKeepsAnchorInsteadOfTemporaryPageZero() {
        val pending = ReaderPositionLocator(4, 0.4f, ReaderPositionFlow.CONTINUOUS, 812)
        val position = positionForReaderFlowChange(
            chapterIndex = 4, pageIndex = 0, pageCount = 0,
            pendingPosition = pending, pendingFraction = 0.4f, characterOffset = 0,
            destination = ReaderPositionFlow.PAGED
        )
        assertEquals(pending.copy(flow = ReaderPositionFlow.PAGED), position)
    }

    @Test
    fun legacyPendingFractionSurvivesMissingPagination() {
        val position = positionForReaderFlowChange(
            chapterIndex = 2, pageIndex = 0, pageCount = 0,
            pendingPosition = null, pendingFraction = 0.6f, characterOffset = null,
            destination = ReaderPositionFlow.CONTINUOUS
        )
        assertEquals(0.6f, position.chapterFraction, 0f)
        assertNull(position.characterOffset)
    }

    @Test
    fun continuousTtsPageFractionsMapBackToEveryRequestedPage() {
        val totalPages = 31

        repeat(totalPages) { pageIndex ->
            val fraction = requireNotNull(continuousTtsPageFraction(pageIndex, totalPages))
            assertEquals(pageIndex, (fraction * totalPages).toInt())
        }
        assertNull(continuousTtsPageFraction(-1, totalPages))
        assertNull(continuousTtsPageFraction(totalPages, totalPages))
        assertNull(continuousTtsPageFraction(0, 0))
    }

    @Test
    fun continuousStartOffsetFollowsTheChapterFraction() {
        assertEquals(0, continuousStartCharacterOffset(0f, 1_000))
        assertEquals(500, continuousStartCharacterOffset(0.5f, 1_000))
        assertEquals(999, continuousStartCharacterOffset(0.9999f, 1_000))
        // Out-of-range fractions clamp so the offset always addresses a real character.
        assertEquals(0, continuousStartCharacterOffset(-1f, 1_000))
        assertEquals(999, continuousStartCharacterOffset(2f, 1_000))
        assertNull(continuousStartCharacterOffset(0.5f, 0))
        assertNull(continuousStartCharacterOffset(0.5f, -10))
    }

    @Test
    fun `round trip preserves paged character anchor`() {
        val locator = ReaderPositionLocator(
            chapterIndex = 4,
            chapterFraction = 0.25f,
            flow = ReaderPositionFlow.PAGED,
            characterOffset = 812,
            sourceByteOffset = 98_765L
        )

        assertEquals(locator, ReaderPositionLocator.fromJson(locator.toJson()))
    }

    @Test
    fun `version one locator remains readable without byte anchor`() {
        val json = """{"type":"lumi_reader_position","version":1,"chapterIndex":2,"chapterFraction":0.5,"flow":"paged","characterOffset":42}"""

        assertEquals(
            ReaderPositionLocator(
                chapterIndex = 2,
                chapterFraction = 0.5f,
                flow = ReaderPositionFlow.PAGED,
                characterOffset = 42,
                sourceByteOffset = null
            ),
            ReaderPositionLocator.fromJson(json)
        )
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
