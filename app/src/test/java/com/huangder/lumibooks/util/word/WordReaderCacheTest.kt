package com.huangder.lumibooks.util.word

import android.app.Application
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.parser.BookParserFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class WordReaderCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `clearing the derived EPUB preserves a live reader and rebuilds the same locators`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val original = folder.newFile("sample.docx")
        checkNotNull(javaClass.getResourceAsStream("/word/reading.docx")).use { input ->
            original.outputStream().use { input.copyTo(it) }
        }
        val before = original.readBytes()
        val lease = WordEpubCache.acquire(context, original.absolutePath, "docx")
        val cache = lease.file
        try {
            WordEpubCache.clear(context)
            assertTrue(cache.isFile)
        } finally {
            lease.close()
        }
        assertFalse(cache.exists())
        val parser = BookParserFactory.createParser(BookFormat.DOCX, context)
        try {
            val book = parser.parse(original.absolutePath)
            assertTrue(book.tocEntries.any { it.title.contains("第二章") })
            val search = parser as BookSearchSource
            val first = search.searchBook("可以搜索", 10).single().locator
            parser.close()
            WordEpubCache.clear(context)
            parser.parse(original.absolutePath)
            assertEquals(first, search.searchBook("可以搜索", 10).single().locator)
            assertTrue(before.contentEquals(original.readBytes()))
        } finally {
            parser.close()
            WordEpubCache.clear(context)
        }
    }
}
