package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderOpeningSnapshotTest {
    private fun page() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test fun `settled page is handed over while the reader still shows it`() {
        val sink = ReaderOpeningSnapshotSink()
        sink.currentIdentity = listOf(3, 7)
        val bitmap = page()
        sink.offer(listOf(3, 7), bitmap)
        assertSame(bitmap, sink.takeMatching())
        assertTrue(!bitmap.isRecycled)
    }

    @Test fun `page is dropped once the reader moved away from it`() {
        val sink = ReaderOpeningSnapshotSink()
        sink.currentIdentity = listOf(3, 7)
        val bitmap = page()
        sink.offer(listOf(3, 7), bitmap)
        sink.currentIdentity = listOf(3, 8)
        assertNull(sink.takeMatching())
        assertTrue(bitmap.isRecycled)
    }

    @Test fun `copies that finish late or after leaving are discarded`() {
        val sink = ReaderOpeningSnapshotSink()
        sink.currentIdentity = listOf(3, 8)
        val stale = page()
        sink.offer(listOf(3, 7), stale)
        assertTrue(stale.isRecycled)
        assertNull(sink.takeMatching())
        val afterExit = page()
        sink.offer(listOf(3, 8), afterExit)
        assertTrue(afterExit.isRecycled)
    }

    @Test fun `key changes whenever the opening would show a different page`() {
        val book = Book("b", "B", "Author", "/books/b.epub", null, BookFormat.EPUB, 0, 0.25f,
            locatorJson = "{\"chapter\":3}", createdAt = 0, sourceLastModified = 10)
        val display = ReaderOpeningDisplay(1080, 2400, 3f, 1f, "zh-CN", false, "system")
        val key = readerOpeningSnapshotKey(book, "prefs", display, 1L)
        assertEquals(key, readerOpeningSnapshotKey(book.copy(title = "Renamed"), "prefs", display, 1L))
        assertEquals(64, key.length)
        listOf(
            readerOpeningSnapshotKey(book.copy(readingProgress = 0.26f), "prefs", display, 1L),
            readerOpeningSnapshotKey(book.copy(locatorJson = "{\"chapter\":4}"), "prefs", display, 1L),
            readerOpeningSnapshotKey(book.copy(sourceLastModified = 11), "prefs", display, 1L),
            readerOpeningSnapshotKey(book, "prefs-larger-font", display, 1L),
            readerOpeningSnapshotKey(book, "prefs", display.copy(width = 1200), 1L),
            readerOpeningSnapshotKey(book, "prefs", display.copy(appIsDark = true), 1L),
            readerOpeningSnapshotKey(book, "prefs", display, 2L)
        ).forEach { assertNotEquals(key, it) }
    }
}
