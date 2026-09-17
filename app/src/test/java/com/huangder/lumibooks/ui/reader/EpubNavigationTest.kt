package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubNavigationTest {
    private fun request(
        operationId: Long,
        sourceChapter: Int = 2,
        targetChapter: Int = 4
    ) = EpubNavigationRequest(
        operationId = operationId,
        origin = EpubNavigationOrigin.TOC,
        sourceChapterIndex = sourceChapter,
        sourcePageIndex = 3,
        targetChapterIndex = targetChapter,
        destination = EpubNavigationDestination.ChapterStart
    )

    @Test
    fun onlyNewestOperationCanCommit() {
        val tracker = EpubNavigationOperationTracker()
        assertNull(tracker.begin(10L))
        assertEquals(10L, tracker.begin(11L))
        assertFalse(tracker.finish(10L))
        assertTrue(tracker.accepts(11L))
        assertTrue(tracker.finish(11L))
        assertNull(tracker.activeOperationId())
    }

    @Test
    fun cancellationRejectsLateCallbacks() {
        val tracker = EpubNavigationOperationTracker()
        tracker.begin(22L)
        assertTrue(tracker.finish(22L))
        assertFalse(tracker.accepts(22L))
    }

    @Test
    fun successfulOperationCanOnlyCommitOnce() {
        val tracker = EpubNavigationOperationTracker()
        tracker.begin(30L)
        assertTrue(tracker.finish(30L))
        assertFalse(tracker.finish(30L))
    }

    @Test
    fun timeoutUsesTenSecondDeadlineAndRejectsLateSuccess() {
        assertEquals(10_000L, EPUB_NAVIGATION_TIMEOUT_MS)
        val tracker = EpubNavigationOperationTracker()
        tracker.begin(31L)
        assertTrue(tracker.finish(31L))
        assertFalse(tracker.accepts(31L))
    }

    @Test
    fun retryStartsAsANewOperation() {
        val tracker = EpubNavigationOperationTracker()
        tracker.begin(40L)
        tracker.finish(40L)
        assertNull(tracker.begin(41L))
        assertTrue(tracker.accepts(41L))
    }

    @Test
    fun sameChapterNavigationStaysInActiveWebView() {
        assertFalse(request(50L, sourceChapter = 3, targetChapter = 3).requiresStaging(3))
        assertTrue(request(51L, sourceChapter = 3, targetChapter = 5).requiresStaging(3))
    }

    @Test
    fun chapterFractionResolvesAgainstPreparedPageCount() {
        val destination = EpubNavigationDestination.Page(index = 0, chapterFraction = 0.5f)
        assertTrue(destination.matchesPage(pageIndex = 5, pageCount = 10))
        assertFalse(destination.matchesPage(pageIndex = 4, pageCount = 10))
    }

    @Test
    fun previousChapterDestinationResolvesToItsLastPage() {
        val destination = EpubNavigationDestination.Page(Int.MAX_VALUE)
        assertTrue(destination.matchesPage(pageIndex = 11, pageCount = 12))
        assertFalse(destination.matchesPage(pageIndex = 10, pageCount = 12))
    }

    @Test
    fun locatorAndFragmentAcceptResolvedPage() {
        assertTrue(EpubNavigationDestination.Fragment("part-2").matchesPage(7, 12))
        assertTrue(EpubNavigationDestination.Locator("{}").matchesPage(3, 8))
    }
}
