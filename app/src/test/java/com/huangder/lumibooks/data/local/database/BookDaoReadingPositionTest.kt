package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.entity.BookEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BookDaoReadingPositionTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.bookDao().insertBook(
            BookEntity(
                id = "book",
                title = "Book",
                author = "Author",
                filePath = "/books/book.epub",
                coverPath = null,
                format = "EPUB",
                lastReadTime = 1L,
                readingProgress = .8f,
                locatorJson = "chapter-5-position",
                createdAt = 1L
            )
        )
    }

    @After
    fun cleanup() {
        db.close()
    }

    @Test
    fun fullPositionUpdateClearsAnObsoleteChapterLocator() = runBlocking {
        db.bookDao().updateReadingPosition("book", .2f, null)

        val saved = db.bookDao().getBookById("book")!!
        assertEquals(.2f, saved.readingProgress, 0f)
        assertNull(saved.locatorJson)
    }

    @Test
    fun progressOnlyUpdateRetainsAnUnspecifiedLocator() = runBlocking {
        db.bookDao().updateReadingProgress("book", .2f, null)

        assertEquals("chapter-5-position", db.bookDao().getBookById("book")!!.locatorJson)
    }
}
