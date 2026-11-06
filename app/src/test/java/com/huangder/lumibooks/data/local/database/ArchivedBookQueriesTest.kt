package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.huangder.lumibooks.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArchivedBookQueriesTest {
    private lateinit var db: AppDatabase
    private val date = "2026-11-04"
    private fun book(id: String, time: Long, archived: Boolean = false, hidden: Boolean = false) = BookEntity(
        id, id, "Author", "/books/$id.epub", "cover.jpg", "EPUB", time, .4f,
        locatorJson = "saved-position", createdAt = 1, isFavorite = true, isPinned = true,
        sourceDocumentKey = id, isHidden = hidden, isArchived = archived)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        listOf(book("visible", 1), book("archived", 2, archived = true), book("secret", 3, hidden = true))
            .forEach { db.bookDao().insertBook(it) }
        listOf("visible", "archived", "secret").forEach { id ->
            db.readingRecordDao().insertRecord(ReadingRecordEntity(bookId = id, date = date,
                duration = 60_000, startTime = 0, endTime = 60_000))
            db.noteDao().insertNote(NoteEntity(bookId = id, chapterIndex = 0, startPosition = 0, endPosition = 4,
                selectedText = "quote", note = "note", color = "yellow", createdAt = 100, syncId = "note-$id"))
            db.bookmarkDao().insertBookmark(BookmarkEntity(bookId = id, chapterIndex = 0,
                position = .4f, title = id, createdAt = 100, syncId = "bookmark-$id"))
        }
    }
    @After fun cleanup() { db.close() }

    @Test fun shelfAndWidgetsExcludeArchivedBooksButCompleteLibraryKeepsThem() = runBlocking {
        assertEquals(listOf("visible"), db.bookDao().observeVisibleBooks().first().map { it.id })
        assertNull(db.bookDao().getVisibleBookById("archived"))
        assertEquals("visible", db.bookDao().getContinueReadingWidgetData()?.bookId)
        assertEquals(listOf("visible"), db.noteDao().observeWidgetQuotes().first().map { it.bookId })
        repeat(5) { assertEquals("visible", db.noteDao().getRandomWidgetQuote()?.bookId) }
        assertEquals(3, db.bookDao().getAllBooksSnapshot().size)
        assertTrue(db.bookDao().getBookBySourceDocumentKey("archived")!!.isArchived)
    }

    @Test fun archiveKeepsHistoricalStatisticsAndAllNotesButHiddenRemainsPrivate() = runBlocking {
        assertEquals(120_000L, db.readingRecordDao().getTotalDurationByDate(date).first())
        assertEquals(setOf("visible", "archived"), db.readingRecordDao().getMostReadBooksBetween(date, date).first().map { it.bookId }.toSet())
        assertEquals(setOf("visible", "archived"), db.noteDao().observeTextNotes().first().map { it.bookId }.toSet())
        assertEquals(setOf("visible", "archived"), db.bookmarkDao().observeAllBookmarks().first().map { it.bookId }.toSet())
        assertEquals(1, db.noteDao().getNotesByBookId("archived").first().size)
    }

    @Test fun restorePreservesMetadataProgressAndFolderMembership() = runBlocking {
        val original = db.bookDao().getBookById("visible")!!
        db.folderDao().insertFolder(FolderEntity("folder", "Folder", "folder", null, 1))
        db.openHelper.writableDatabase.execSQL("INSERT INTO book_folder_cross_refs (bookId, folderId, updatedAt) VALUES ('visible', 'folder', 1)")
        db.bookDao().setBooksArchived(listOf("visible"), true, 900)
        db.bookDao().updateReadingProgress("visible", .7f, "new-position")
        db.bookDao().setBooksArchived(listOf("visible"), false, 901)
        val restored = db.bookDao().getBookById("visible")!!
        assertEquals(original.copy(readingProgress = .7f, locatorJson = "new-position", metadataUpdatedAt = 901), restored)
        assertEquals("folder", db.folderDao().getAllBookFolderLinks().first().single().folderId)
        assertEquals(1, db.noteDao().getNotesByBookId("visible").first().size)
    }

    @Test fun staleWidgetWritesCannotChangeAnArchivedBook() = runBlocking {
        val changed = db.bookDao().compareAndSetVisibleReadingProgress("archived", .4f, "saved-position", .8f, "stale-widget", 999)
        assertEquals(0, changed)
        assertEquals(.4f, db.bookDao().getBookById("archived")!!.readingProgress, 0f)
    }

    @Test fun migrationFrom26DefaultsToUnarchivedWithoutChangingOtherColumns() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, readingProgress REAL NOT NULL, isHidden INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val sqlite = helper.writableDatabase
            sqlite.execSQL("INSERT INTO books VALUES ('a', 'Book', 0.5, 1)")
            DatabaseMigrations.MIGRATION_26_27.migrate(sqlite)
            sqlite.query("SELECT title, readingProgress, isHidden, isArchived FROM books").use {
                assertTrue(it.moveToFirst()); assertEquals("Book", it.getString(0))
                assertEquals(.5f, it.getFloat(1), 0f); assertEquals(1, it.getInt(2)); assertEquals(0, it.getInt(3))
            }
        }
    }
}
