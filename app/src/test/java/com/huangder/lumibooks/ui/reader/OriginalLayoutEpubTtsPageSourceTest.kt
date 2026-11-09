package com.huangder.lumibooks.ui.reader

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalLayoutEpubTtsPageSourceTest {
    @Test fun hiddenReaderSkipsSleepingWebViewAndCanUseItAgainOnReturn() = runTest {
        var visible = false
        var webRequests = 0
        val source = OriginalLayoutEpubTtsPageSource(2, { chapter, page ->
            webRequests++
            if (!visible) awaitCancellation()
            EpubPageText(chapter, page, 1, "Rendered.", "Rendered.", 0, 9)
        }, { "Background chapter." }, webPageTimeoutMs = 60_000L,
            prefetchDispatcher = Dispatchers.Unconfined)
        try {
            source.setReaderVisible(false)
            assertEquals("Background chapter.", source.getPage(0, 0)?.text)
            assertEquals("Background chapter.", source.getChapter(0)?.text)
            assertEquals(0, webRequests)
            assertEquals(0L, testScheduler.currentTime)
            visible = true
            source.setReaderVisible(true)
            assertEquals("Rendered.", source.getPage(1, 0)?.text)
            assertTrue(webRequests > 0)
        } finally { source.close() }
    }

    @Test fun offsetSeekUsesRendererCoordinatesAndCanSeekAfterReaderDetach() = runBlocking {
        val rendered = "First. Second. Third."
        val starts = listOf(0, 7, 15, rendered.length)
        val source = OriginalLayoutEpubTtsPageSource(1, { chapter, page ->
            if (page in 0..2) EpubPageText(chapter, page, 3,
                rendered.substring(starts[page], starts[page + 1]), rendered, starts[page], starts[page + 1]) else null
        }, { "Parser whitespace differs." }, prefetchDispatcher = Dispatchers.Unconfined)
        try {
            assertEquals(1, source.locatePage(0, 9)?.location?.pageIndex)
            assertEquals(rendered, source.getChapter(0)?.text)
            source.detachReader()
            val whole = source.locatePage(0, 17)!!
            assertEquals(rendered, whole.text)
            assertEquals(0, whole.startCharacterOffset)
        } finally { source.close() }
    }

    @Test fun leavingReaderStopsWebRequestsAndReadsFollowingChapters() = runBlocking {
        var webRequests = 0
        val source = OriginalLayoutEpubTtsPageSource(
            chapterCount = 2,
            webPageProvider = { _, _ -> webRequests++; null },
            chapterTextProvider = { if (it == 0) "First chapter." else "Second chapter." },
            prefetchDispatcher = Dispatchers.Unconfined
        )
        source.detachReader()
        val first = source.getPage(0, 0)!!
        val second = source.getPage(first.next!!.chapterIndex, first.next!!.pageIndex)!!
        assertEquals("First chapter.", first.text)
        assertEquals("Second chapter.", second.text)
        assertEquals(0, webRequests)
        source.close()
    }

    @Test
    fun webFailureContinuesFromLastUnreadCharacter() = runBlocking {
        val chapter = "First.Second.Third."
        val source = OriginalLayoutEpubTtsPageSource(
            chapterCount = 1,
            webPageProvider = { _, pageIndex ->
                if (pageIndex == 0) {
                    EpubPageText(
                        chapterIndex = 0,
                        pageIndex = 0,
                        pageCount = 3,
                        text = "First.",
                        chapterText = chapter,
                        startCharacterOffset = 0,
                        endCharacterOffset = 6
                    )
                } else {
                    null
                }
            },
            chapterTextProvider = { chapter },
            webPageTimeoutMs = 50L,
            prefetchDispatcher = Dispatchers.Unconfined
        )

        val first = source.getPage(0, 0)!!
        val fallback = source.getPage(0, 1)!!

        assertEquals("First.", first.text)
        assertEquals("Second.Third.", fallback.text)
        assertEquals(6, fallback.startCharacterOffset)
        assertEquals(chapter.length, fallback.endCharacterOffset)
        assertNull(fallback.next)
        source.close()
    }

    @Test
    fun webTimeoutFallsBackWithoutReturningToChapterStart() = runBlocking {
        val chapter = "Already read.Remaining text."
        val source = OriginalLayoutEpubTtsPageSource(
            chapterCount = 1,
            webPageProvider = { _, pageIndex ->
                if (pageIndex == 0) {
                    EpubPageText(
                        chapterIndex = 0,
                        pageIndex = 0,
                        pageCount = 2,
                        text = "Already read.",
                        chapterText = chapter,
                        startCharacterOffset = 0,
                        endCharacterOffset = 13
                    )
                } else {
                    delay(1_000L)
                    null
                }
            },
            chapterTextProvider = { chapter },
            webPageTimeoutMs = 20L,
            prefetchDispatcher = Dispatchers.Unconfined
        )

        source.getPage(0, 0)
        val fallback = source.getPage(0, 1)!!

        assertEquals("Remaining text.", fallback.text)
        assertEquals(13, fallback.startCharacterOffset)
        assertEquals(chapter.length, fallback.endCharacterOffset)
        source.close()
    }
}
