package com.huangder.lumibooks.ui.reader

import android.app.Application
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPageIndexCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var source: File
    private lateinit var book: Book
    private val text = "Text flows across the chapter and pages. ".repeat(60)
    private fun engine() = PageLayoutEngine().apply { configure(280, 360, 24f, chapterCount = 12) }
    @Before fun prepare() {
        ReaderCacheStore.get(context).clear()
        source = File.createTempFile("pagination", ".txt", context.filesDir).apply { writeText(text) }
        book = Book("pages", "Title", "", source.path, null, BookFormat.TXT, 0, 0f, createdAt = 0)
    }
    @After fun cleanup() { source.delete(); ReaderCacheStore.get(context).clear() }

    @Test fun completeIndexRestoresIntoNewEngineWithoutAnyStatisticsCalls() = runBlocking {
        val first = engine()
        val cache = ReaderPageIndexCache.open(context, book, first.paginationSignature(), 12)
        first.indexMissingPages(first.bookPages.value.generation) { text }
        cache.submit(first.bookPages.value); cache.close(); cache.awaitWrites()
        // A new cache object and engine have no shared reading-session state.
        val reopened = engine()
        val disk = ReaderPageIndexCache.open(context, book, reopened.paginationSignature(), 12)
        assertTrue(reopened.restoreBookPages(disk.restored!!, reopened.bookPages.value.generation))
        var reads = 0
        reopened.indexMissingPages(reopened.bookPages.value.generation) { reads++; text }
        assertEquals(0, reads)
        assertEquals(first.bookPages.value.totalPages, reopened.bookPages.value.totalPages)
        assertEquals(first.bookPages.value.chapterStarts, reopened.bookPages.value.chapterStarts)
        assertEquals(readerBookPageLabel(first.bookPages.value, 7, first.getChapterPageCount(7) - 1, 8, 0),
            readerBookPageLabel(reopened.bookPages.value, 7, reopened.getChapterPageCount(7) - 1, 8, 0))
        disk.close(); disk.awaitWrites()
    }

    @Test fun closeDuringCountingCommitsPartialAndOnlyMissingChaptersAreRead() = runBlocking {
        val first = engine()
        val cache = ReaderPageIndexCache.open(context, book, first.paginationSignature(), 12)
        (0..8).forEach { first.layout(it, text); cache.submit(first.bookPages.value) }
        assertNull(first.getChapterLayout(0)) // LRU eviction must not affect the disk index.
        cache.close(); cache.awaitWrites()
        val second = engine()
        val disk = ReaderPageIndexCache.open(context, book, second.paginationSignature(), 12)
        second.restoreBookPages(disk.restored!!, second.bookPages.value.generation)
        val reads = mutableListOf<Int>()
        second.indexMissingPages(second.bookPages.value.generation) { chapter -> reads += chapter; text }
        assertEquals(listOf(9, 10, 11), reads)
        assertNotNull(second.bookPages.value.totalPages)
        disk.close(); disk.awaitWrites()
    }

    @Test fun modifiedContentAndLayoutRejectSavedIndex() = runBlocking {
        val first = engine()
        val cache = ReaderPageIndexCache.open(context, book, first.paginationSignature(), 12)
        first.layout(0, text); cache.submit(first.bookPages.value); cache.close(); cache.awaitWrites()
        val reflow = engine().apply { configure(400, 360, 24f, chapterCount = 12) }
        val miss = ReaderPageIndexCache.open(context, book, reflow.paginationSignature(), 12)
        assertNull(miss.restored); miss.close(); miss.awaitWrites()
        source.appendText("content changed")
        val contentMiss = ReaderPageIndexCache.open(context, book, first.paginationSignature(), 12)
        assertNull(contentMiss.restored); contentMiss.close(); contentMiss.awaitWrites()
    }

    @Test fun colorsAndLayoutRefreshPreserveIndexButMetricContentChangesInvalidate() = runBlocking {
        val engine = engine()
        engine.layout(0, text)
        val signature = engine.paginationSignature()
        val pages = engine.bookPages.value
        engine.configure(280, 360, 24f, textColor = 0xffabcdef.toInt(), chapterCount = 12)
        engine.refreshLayouts()
        assertEquals(signature, engine.paginationSignature())
        assertEquals(pages.chapterCounts, engine.bookPages.value.chapterCounts)
        assertEquals(pages.chapterStarts, engine.bookPages.value.chapterStarts)
        engine.setPaginationIdentity("changed geometry from styled content")
        assertTrue(engine.bookPages.value.chapterCounts.all { it == null })
        assertNotEquals(signature, engine.paginationSignature())
    }

    @Test fun pureDisplaySettingsDoNotChangeContentIdentity() {
        val state = ReaderUiState(book = book, chapterCount = 12)
        assertEquals(state.paginationContentKey(), state.copy(readerTextColor = 123,
            readerTheme = "night", contentRevision = 99, currentChapterIndex = 8,
            currentPageIndex = 5).paginationContentKey())
        assertNotEquals(state.paginationContentKey(), state.copy(paragraphSpacing = 19f).paginationContentKey())
        assertNotEquals(state.paginationContentKey(), state.copy(chineseMode = "simplified").paginationContentKey())
    }

    @Test fun fontFileChangesHaveStableIndependentIdentity() {
        val font = File.createTempFile("font", ".ttf", context.filesDir)
        try {
            font.writeText("original font")
            val key = readerPaginationFontKey(context, "custom:1", font.path, 400)
            assertEquals(key, readerPaginationFontKey(context, "custom:1", font.path, 400))
            font.appendText(" replacement")
            assertNotEquals(key, readerPaginationFontKey(context, "custom:1", font.path, 400))
            assertNotEquals(key, readerPaginationFontKey(context, "custom:1", font.path, 700))
        } finally { font.delete() }
    }

    @Test fun cacheClearRejectsOutstandingWriterAndCorruptionIsAMiss() = runBlocking {
        val pages = ReaderBookPages(chapterCounts = listOf(2), chapterStarts = mapOf(0 to listOf(0, 100)))
        val cache = ReaderPageIndexCache.open(context, book, "corruption", 1)
        cache.submit(pages); cache.close(); cache.awaitWrites()
        val root = File(context.cacheDir, "reader_cache")
        root.listFiles()!!.first { it.name.startsWith("page_index_v") }.writeText("broken")
        val corrupt = ReaderPageIndexCache.open(context, book, "corruption", 1)
        assertNull(corrupt.restored)
        ReaderCacheStore.get(context).clear()
        corrupt.submit(pages); corrupt.close(); corrupt.awaitWrites()
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("page_index_v") })
    }

    @Test fun originalCountsAndIncompleteMarkdownBoundariesRoundTripIndependently() = runBlocking {
        val original = ReaderPageIndexCache.open(context, book, "original", 3)
        original.submit(ReaderBookPages(chapterCounts = listOf(3, null, 7)))
        original.close(); original.awaitWrites()
        val markdown = ReaderPageIndexCache.open(context, book, "markdown", 1)
        val partial = ReaderBookPages(chapterCounts = listOf(null), chapterStarts = mapOf(0 to listOf(0, 120, 240)))
        markdown.submit(partial); markdown.close(); markdown.awaitWrites()
        val restoredOriginal = ReaderPageIndexCache.open(context, book, "original", 3)
        val restoredMarkdown = ReaderPageIndexCache.open(context, book, "markdown", 1)
        assertEquals(listOf(3, null, 7), restoredOriginal.restored!!.chapterCounts)
        assertEquals(partial, restoredMarkdown.restored)
        restoredOriginal.close(); restoredMarkdown.close()
        restoredOriginal.awaitWrites(); restoredMarkdown.awaitWrites()
    }

    @Test fun invalidCharacterBoundariesAndCountsAreRejected() {
        fun payload(count: Any, starts: List<Int>) = JSONObject()
            .put("counts", JSONArray().put(count))
            .put("starts", JSONObject().put("0", JSONArray(starts)))
        assertNull(ReaderPageIndexCache.decode(payload(2, listOf(0, 0)), 1))
        assertNull(ReaderPageIndexCache.decode(payload(2, listOf(0, 10, 20)), 1))
        assertNull(ReaderPageIndexCache.decode(payload(-1, emptyList()), 1))
        assertNull(ReaderPageIndexCache.decode(payload(2, listOf(1, 10)), 1))
    }

    @Test fun delayedPartialMarkdownWriteCannotReplaceACompleteIndexFromAReopenedReader() = runBlocking {
        val old = ReaderPageIndexCache.open(context, book, "overlapping", 1)
        val reopened = ReaderPageIndexCache.open(context, book, "overlapping", 1)
        val complete = ReaderBookPages(chapterCounts = listOf(3), chapterStarts = mapOf(0 to listOf(0, 120, 240)))
        reopened.submit(complete); reopened.close(); reopened.awaitWrites()
        old.submit(ReaderBookPages(chapterCounts = listOf(null), chapterStarts = mapOf(0 to listOf(0, 120))))
        old.close(); old.awaitWrites()
        val disk = ReaderPageIndexCache.open(context, book, "overlapping", 1)
        assertEquals(complete, disk.restored)
        disk.close(); disk.awaitWrites()
    }

    @Test fun staleRestorationDoesNotOverrideReflowOrFreshForegroundMeasurements() = runBlocking {
        val engine = engine()
        engine.layout(0, text)
        val generation = engine.bookPages.value.generation
        val cached = engine.bookPages.value
        engine.configure(280, 240, 32f, chapterCount = 12)
        assertFalse(engine.restoreBookPages(cached, generation))
        engine.layout(0, text.repeat(3))
        assertFalse(engine.restoreBookPages(cached, engine.bookPages.value.generation))
    }
}
