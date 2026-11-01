package com.huangder.lumibooks.data.local.dao

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VisibleReadingProgressTest {
    @Test fun externalProgressRequiresVisibleUnchangedSnapshot() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity(id = "book", title = "Book", author = "", filePath = "book.txt",
                coverPath = null, format = "TXT", readingProgress = 0f, lastReadTime = 0, createdAt = 0))
            assertEquals(1, dao.observeVisibleBooks().first().size)
            assertEquals(1, dao.compareAndSetVisibleReadingProgress("book", 0f, null, .2f, "first", 10))
            assertEquals(0, dao.compareAndSetVisibleReadingProgress("book", 0f, null, .3f, "stale", 20))
            assertEquals(0, dao.compareAndSetVisibleReadingProgress("book", .2f, "different", .3f, "stale", 20))
            assertEquals(1, dao.compareAndSetVisibleReadingProgress("book", .2f, "first", .4f, "second", 30))
            dao.setBooksHidden(listOf("book"), true, 40)
            assertNull(dao.getVisibleBookById("book"))
            assertTrue(dao.observeVisibleBooks().first().isEmpty())
            assertEquals(0, dao.compareAndSetVisibleReadingProgress("book", .4f, "second", .5f, "hidden", 50))
            val saved = dao.getBookById("book")!!
            assertEquals(.4f, saved.readingProgress)
            assertEquals("second", saved.locatorJson)
            assertEquals(30L, saved.lastReadTime)
        } finally { database.close() }
    }
}
