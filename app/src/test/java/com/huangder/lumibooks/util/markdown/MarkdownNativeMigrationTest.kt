package com.huangder.lumibooks.util.markdown

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownNativeMigrationTest {
    @Test fun upgradeIsIdempotentPreservesUnmatchedNotesAndMigratesLaterSyncRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val legacy = JSONObject().put("version", 2).put("exact", "target").put("textPosition", 100).toString()
            val book = Book("md", "Title", "", "document.md", null, BookFormat.MARKDOWN, 0, 0f, legacy, 0)
            db.bookDao().insertBook(BookEntity("md", "Title", "", "document.md", null, "MARKDOWN", 0, 0f, legacy, 0))
            fun note(id: Long, quote: String) = NoteEntity(id, "md", 0, 100, 106, legacy,
                selectedText = quote, note = "keep my note", color = "#FFFF00", createdAt = 1, syncId = "note-$id")
            db.noteDao().insertNotes(listOf(note(1, "target"), note(2, "removed")))
            db.bookmarkDao().insertBookmark(BookmarkEntity(1, "md", 0, 0f, legacy, "Bookmark", 1, "bookmark"))
            val manager = MarkdownUpdateManager(context, db)
            val updated = manager.migrateNative(book, "prefix target suffix")
            val notes = db.noteDao().getNotesByBookId("md").first().associateBy { it.id }
            assertEquals(7, notes.getValue(1).startPosition)
            assertFalse(notes.getValue(1).isStale)
            assertTrue(notes.getValue(2).isStale)
            assertEquals("keep my note", notes.getValue(2).note)
            assertEquals(3, JSONObject(updated.locatorJson!!).getInt("version"))
            assertFalse(db.bookmarkDao().getBookmarksByBookId("md").first().single().isStale)
            manager.migrateNative(updated, "prefix target suffix")
            assertEquals(notes, db.noteDao().getNotesByBookId("md").first().associateBy { it.id })
            db.noteDao().insertNote(note(3, "target"))
            manager.migrateNative(updated, "prefix target suffix")
            assertEquals(7, db.noteDao().getNotesByBookId("md").first().first { it.id == 3L }.startPosition)
        } finally { db.close() }
    }
}
