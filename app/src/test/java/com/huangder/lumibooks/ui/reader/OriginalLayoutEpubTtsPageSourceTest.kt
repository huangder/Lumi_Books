package com.huangder.lumibooks.ui.reader

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalLayoutEpubTtsPageSourceTest {
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
