package com.huangder.lumibooks.ui.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TxtEditorSearchTest {
    private val chapters = listOf(
        "alpha first",
        "nothing here",
        "alpha second alpha"
    )

    @Test
    fun scansWholeBookInChapterOrder() = runBlocking {
        val result = scanTxtEditorMatches(
            chapterCount = chapters.size,
            activeChapter = 1,
            scope = TxtSearchScope.BOOK,
            query = "alpha",
            ignoreCase = false,
            direction = TxtEditorSearchDirection.NEXT,
            anchorChapter = 1,
            anchorOffset = 0,
            readChapter = chapters::get
        )

        assertEquals(3, result.total)
        assertEquals(2, result.ordinal)
        assertEquals(TxtEditorMatch(2, 0, 5), result.match)
    }

    @Test
    fun previousWrapsToLastBookMatch() = runBlocking {
        val result = scanTxtEditorMatches(
            chapterCount = chapters.size,
            activeChapter = 0,
            scope = TxtSearchScope.BOOK,
            query = "alpha",
            ignoreCase = false,
            direction = TxtEditorSearchDirection.PREVIOUS,
            anchorChapter = 0,
            anchorOffset = 0,
            readChapter = chapters::get
        )

        assertEquals(3, result.total)
        assertEquals(3, result.ordinal)
        assertEquals(TxtEditorMatch(2, 13, 18), result.match)
    }

    @Test
    fun chapterScopeReadsOnlyActiveChapter() = runBlocking {
        val reads = mutableListOf<Int>()
        val result = scanTxtEditorMatches(
            chapterCount = chapters.size,
            activeChapter = 2,
            scope = TxtSearchScope.CHAPTER,
            query = "alpha",
            ignoreCase = false,
            direction = TxtEditorSearchDirection.NEXT,
            anchorChapter = 2,
            anchorOffset = 0,
            readChapter = { chapterIndex ->
                reads += chapterIndex
                chapters[chapterIndex]
            }
        )

        assertEquals(listOf(2), reads)
        assertEquals(2, result.total)
        assertEquals(1, result.ordinal)
    }

    @Test
    fun returnsEmptyResultWhenNoChapterMatches() = runBlocking {
        val result = scanTxtEditorMatches(
            chapterCount = chapters.size,
            activeChapter = 0,
            scope = TxtSearchScope.BOOK,
            query = "missing",
            ignoreCase = false,
            direction = TxtEditorSearchDirection.NEXT,
            anchorChapter = 0,
            anchorOffset = 0,
            readChapter = chapters::get
        )

        assertEquals(0, result.total)
        assertEquals(0, result.ordinal)
        assertEquals(null, result.match)
    }

    @Test
    fun chapterReadFailureIsNotConvertedToNoMatches() = runBlocking {
        try {
            scanTxtEditorMatches(
                chapterCount = chapters.size,
                activeChapter = 0,
                scope = TxtSearchScope.BOOK,
                query = "alpha",
                ignoreCase = false,
                direction = TxtEditorSearchDirection.NEXT,
                anchorChapter = 0,
                anchorOffset = 0,
                readChapter = { chapterIndex ->
                    if (chapterIndex == 1) error("read failed")
                    chapters[chapterIndex]
                }
            )
            fail("Expected chapter read failure")
        } catch (error: IllegalStateException) {
            assertEquals("read failed", error.message)
        }
    }

    @Test
    fun cancellationStopsWholeBookScan() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            scanTxtEditorMatches(
                chapterCount = 100,
                activeChapter = 0,
                scope = TxtSearchScope.BOOK,
                query = "alpha",
                ignoreCase = false,
                direction = TxtEditorSearchDirection.NEXT,
                anchorChapter = 0,
                anchorOffset = 0,
                readChapter = {
                    started.complete(Unit)
                    delay(100)
                    "alpha"
                }
            )
        }

        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
