package com.huangder.lumibooks.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun continueReadingUsesLatestLastReadTime() = runBlocking {
        database.bookDao().insertBook(book(id = "older", lastReadTime = 100L))
        database.bookDao().insertBook(book(id = "newer", lastReadTime = 200L))

        val result = database.bookDao().getContinueReadingWidgetData()

        assertEquals("newer", result?.bookId)
    }

    @Test
    fun randomQuoteExcludesBlankAndOrphanedRows() = runBlocking {
        database.bookDao().insertBook(book(id = "book", lastReadTime = 100L))
        database.noteDao().insertNote(note(id = 1L, bookId = "book", selectedText = ""))
        database.noteDao().insertNote(
            note(id = 2L, bookId = "missing", selectedText = "orphan")
        )
        database.noteDao().insertNote(
            note(id = 3L, bookId = "book", selectedText = "visible", noteText = "comment")
        )

        val result = database.noteDao().getRandomWidgetQuote()

        assertEquals(3L, result?.noteId)
        assertEquals("book", result?.bookId)
        assertEquals("visible", result?.selectedText)
        assertTrue(result?.isNote == true)
    }

    @Test
    fun pureHighlightIsNotClassifiedAsNoteAndBookDeletionRemovesIt() = runBlocking {
        val book = book(id = "book", lastReadTime = 100L)
        database.bookDao().insertBook(book)
        database.noteDao().insertNote(
            note(id = 4L, bookId = "book", selectedText = "highlight")
        )

        val result = database.noteDao().getRandomWidgetQuote()

        assertFalse(result?.isNote ?: true)
        database.bookDao().deleteBookWithRelatedData(book.id)
        assertNull(database.noteDao().getRandomWidgetQuote())
    }

    private fun book(id: String, lastReadTime: Long): BookEntity {
        return BookEntity(
            id = id,
            title = "Title $id",
            author = "Author",
            filePath = "/books/$id.epub",
            coverPath = null,
            format = "EPUB",
            lastReadTime = lastReadTime,
            readingProgress = 0.5f,
            locatorJson = null,
            createdAt = 1L,
            isFavorite = false
        )
    }

    private fun note(
        id: Long,
        bookId: String,
        selectedText: String,
        noteText: String = ""
    ): NoteEntity {
        return NoteEntity(
            id = id,
            bookId = bookId,
            chapterIndex = 0,
            startPosition = 0,
            endPosition = selectedText.length,
            selectedText = selectedText,
            note = noteText,
            color = "#FFF59D",
            createdAt = id
        )
    }
}
