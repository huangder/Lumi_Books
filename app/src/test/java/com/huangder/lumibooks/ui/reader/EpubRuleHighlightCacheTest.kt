package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.mutableStateOf
import com.huangder.lumibooks.domain.model.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpubRuleHighlightCacheTest {
    @Test
    fun adjacentPreloadAndColdNavigationShareResultsAndWaitForRuleFonts() = runTest {
        val fonts = CompletableDeferred<Map<String, String>>()
        val requested = mutableListOf<Int>()
        val cache = EpubRuleHighlightCache(this, { fonts.await() }) { chapter ->
            requested.add(chapter)
            if (chapter == 4) listOf(ruleNote(chapter)) else emptyList()
        }
        val previous = cache.prepare(2)
        val next = cache.prepare(4)
        assertSame(next, cache.prepare(4))
        val latest = mutableStateOf<EpubRuleHighlightCache?>(cache)
        val navigation = async { latest.awaitChapter(4) }
        yield()
        assertNull(cache.notes(4))
        assertFalse(navigation.isCompleted)

        fonts.complete(mapOf("serif" to "https://reader.local/serif.ttf"))
        previous?.await()
        navigation.await()

        assertEquals(listOf(2, 4), requested)
        assertEquals(emptyList<Note>(), cache.notes(2))
        assertEquals(listOf(ruleNote(4)), epubNotesWithRules(listOf(ruleNote(2)), cache, 4))
        assertEquals(mapOf("serif" to "https://reader.local/serif.ttf"), cache.fontUrls)
        assertNull(cache.prepare(2))
        assertNull(cache.prepare(4))
    }

    @Test
    fun waitingForAChapterUsesLatestRulesIfRulesChangeDuringPreload() = runTest {
        val oldMatch = CompletableDeferred<List<Note>>()
        val oldCache = EpubRuleHighlightCache(this, { emptyMap() }) { oldMatch.await() }
        val latest = mutableStateOf<EpubRuleHighlightCache?>(oldCache)
        val waiting = async { latest.awaitChapter(4) }
        yield()

        val newCache = EpubRuleHighlightCache(this, { emptyMap() }) { listOf(ruleNote(it, "new")) }
        latest.value = newCache
        oldMatch.complete(listOf(ruleNote(4, "old")))
        waiting.await()

        assertEquals(listOf(ruleNote(4, "new")), epubNotesWithRules(emptyList(), latest.value, 4))
    }

    private fun ruleNote(chapter: Int, text: String = "match") = Note(
        bookId = "book",
        chapterIndex = chapter,
        startPosition = 0,
        endPosition = text.length,
        selectedText = text,
        note = "",
        color = "#FFEB3B",
        createdAt = 0L,
        origin = Note.ORIGIN_HIGHLIGHT_RULE,
        sourceRuleId = "rule",
        sourceMatchKey = "$chapter:0:${text.length}"
    )
}
