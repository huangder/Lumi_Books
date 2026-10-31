package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Hidden books stay out of statistics, cross-book feeds and widgets, but keep their own data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HiddenBookQueriesTest {
    private lateinit var db: AppDatabase
    private val date = "2026-10-25"

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val books = db.bookDao()
        books.insertBook(book("visible", lastReadTime = 1, hidden = false))
        books.insertBook(book("secret", lastReadTime = 2, hidden = true))
        listOf("visible" to 60_000L, "secret" to 600_000L).forEach { (id, duration) ->
            db.readingRecordDao().insertRecord(ReadingRecordEntity(bookId = id, date = date, duration = duration, startTime = 0, endTime = duration))
            db.noteDao().insertNote(note(id))
            db.bookmarkDao().insertBookmark(
                BookmarkEntity(bookId = id, chapterIndex = 0, position = 0f, title = id, createdAt = 50, syncId = "bookmark-$id")
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun readingStatisticsLeaveHiddenBooksOut() = runBlocking {
        val records = db.readingRecordDao()
        assertEquals(60_000L, records.getTotalDurationByDate(date).first())
        assertEquals(listOf(60_000L), records.getDailyTotalsBetween(date, date).first().map { it.totalDuration })
        assertEquals(listOf("visible"), records.getMostReadBooksBetween(date, date).first().map { it.bookId })
        // The reader still accumulates time for the hidden book itself.
        assertNotNull(records.getRecordByBookAndDate("secret", date, ""))
        assertEquals(600_000L, records.getTotalDurationByBookId("secret").first())
    }

    @Test
    fun crossBookFeedsLeaveHiddenBooksOut() = runBlocking {
        assertEquals(listOf("visible"), db.noteDao().observeTextNotes().first().map { it.bookId })
        assertEquals(listOf("visible"), db.noteDao().observeTextNotesCreatedBetween(0, 1_000).first().map { it.bookId })
        assertEquals(listOf("visible"), db.bookmarkDao().observeAllBookmarks().first().map { it.bookId })
        assertEquals(listOf("visible"), db.bookmarkDao().observeBookmarksCreatedBetween(0, 1_000).first().map { it.bookId })
        assertEquals(1, db.noteDao().getNotesByBookId("secret").first().size)
    }

    @Test
    fun widgetsNeverShowHiddenBooks() = runBlocking {
        // The hidden book was read last, yet the widget continues the visible one.
        assertEquals("visible", db.bookDao().getContinueReadingWidgetData()?.bookId)
        assertEquals(listOf("visible"), db.noteDao().observeWidgetQuotes().first().map { it.bookId })
        repeat(10) { assertEquals("visible", db.noteDao().getRandomWidgetQuote()?.bookId) }
    }

    @Test
    fun hidingUpdatesTheFlagAndTheSyncTimestamp() = runBlocking {
        db.bookDao().setBooksHidden(listOf("visible"), hidden = true, updatedAt = 999)
        val book = db.bookDao().getBookById("visible")!!
        assertTrue(book.isHidden)
        assertEquals(999L, book.metadataUpdatedAt)
        assertEquals(null, db.readingRecordDao().getTotalDurationByDate(date).first())
    }

    @Test
    fun upgradeFromVersion20KeepsBooksVisible() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, readingProgress REAL NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val sqlite = helper.writableDatabase
            sqlite.execSQL("INSERT INTO books VALUES ('a', 'Book', 0.5)")
            DatabaseMigrations.MIGRATION_20_21.migrate(sqlite)
            DatabaseMigrations.MIGRATION_21_22.migrate(sqlite)
            sqlite.query("SELECT title, isHidden FROM books").use {
                it.moveToFirst()
                assertEquals("Book", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
        }
    }

    private fun book(id: String, lastReadTime: Long, hidden: Boolean) = BookEntity(
        id = id, title = id, author = "Author", filePath = "/books/$id.epub", coverPath = null,
        format = "EPUB", lastReadTime = lastReadTime, readingProgress = 0.2f, createdAt = 1, isHidden = hidden
    )

    private fun note(bookId: String) = NoteEntity(
        bookId = bookId, chapterIndex = 0, startPosition = 0, endPosition = 4,
        selectedText = "摘录 $bookId", note = "", color = "yellow", createdAt = 100, syncId = "note-$bookId"
    )
}
