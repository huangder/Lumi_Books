package com.huangder.lumibooks.ui.home

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookHighlightRuleStateEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.repository.HighlightRuleRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.util.AuthorizedStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalBookImportTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun externalLocationAndHashReuseExistingBookWithItsSwitches() = withRepositories { db, books, rules ->
        val original = files.newFile("older book.txt").apply { writeText("Same text") }
        val alternate = files.newFile("shared book.txt").apply { writeText("Same text") }
        val originalUri = Uri.parse(original.toURI().toASCIIString())
        val alternateUri = Uri.parse(alternate.toURI().toASCIIString())
        val saved = book("legacy").copy(filePath = original.absolutePath, readingProgress = .3f)
        books.insertBook(saved)
        db.noteDao().insertNote(NoteEntity(
            bookId = saved.id,
            chapterIndex = 0,
            startPosition = 0,
            endPosition = 4,
            selectedText = "Same",
            note = "Retained note",
            color = "yellow",
            createdAt = 1
        ))
        rules.saveRule(HighlightRule("rule", "Text", "Same"))
        rules.setRuleEnabled(saved.id, "rule", false)

        val savedContent = "content://books/document/legacy"
        assertEquals(saved, findBookAtExternalLocation(originalUri, null, listOf(saved)))
        val contentBook = saved.copy(filePath = savedContent)
        assertEquals(contentBook, findBookAtExternalLocation(Uri.parse(savedContent), null, listOf(contentBook)))
        assertNull(findBookAtExternalLocation(alternateUri, null, listOf(saved)))

        val context = RuntimeEnvironment.getApplication()
        val storage = AuthorizedStorageManager()
        val hash = storage.sha256(context, alternateUri.toString())
        assertEquals(storage.sha256(context, original.absolutePath), hash)
        val checked = backfillMissingSourceHashes(
            books = listOf(saved),
            hashLocation = { storage.sha256(context, it) },
            persist = books::updateSourceSha256
        )
        val reopened = checkNotNull(findMatchingAuthorizedBook(null, alternateUri.toString(), hash, checked))
        assertEquals(saved.id, reopened.id)
        assertEquals(saved.readingProgress, reopened.readingProgress)
        assertFalse(rules.getRulesForBook(reopened.id).single().enabled)
        assertEquals("Retained note", db.noteDao().getNotesByBookId(reopened.id).first().single().note)
        assertEquals(1, db.bookDao().getAllBooksSnapshot().size)
    }

    @Test
    fun externalBookInheritsLatestEligibleSwitchesWithDefaultOrderAndCanChangeIndependently() =
        withRepositories { db, books, rules ->
            rules.saveRules(listOf(
                HighlightRule("off", "Turn off", "off", position = 0),
                HighlightRule("on", "Turn on", "on", enabled = false, position = 1)
            ))
            val older = book("older", lastReadTime = 6)
            val source = book("source", lastReadTime = 7)
            val hidden = book("hidden", lastReadTime = 8).copy(isHidden = true)
            val archived = book("archived", lastReadTime = 9).copy(isArchived = true)
            val deletedRuleOnly = book("deleted-rule-only", lastReadTime = 10)
            val noSwitches = book("no-switches", lastReadTime = 11)
            listOf(older, source, hidden, archived, deletedRuleOnly, noSwitches).forEach { books.insertBook(it) }
            listOf(older, hidden, archived).forEach { rules.setRuleEnabled(it.id, "off", true) }
            rules.setRuleEnabled(source.id, "off", false)
            rules.setRuleEnabled(source.id, "on", true)
            rules.reorderRules(source.id, listOf("on", "off"))
            rules.setMaterializeNotes(source.id, true)
            db.highlightRuleDao().upsertBookState(BookHighlightRuleStateEntity(source.id, "missing", false, 3))
            db.highlightRuleDao().upsertBookState(BookHighlightRuleStateEntity(deletedRuleOnly.id, "missing", false, 0))

            val imported = book("imported", lastReadTime = 12).copy(format = BookFormat.EPUB)
            books.insertExternalBook(imported)
            assertEquals(imported, books.getBookById(imported.id))
            assertEquals(
                listOf("off" to false, "on" to true),
                rules.getRulesForBook(imported.id).map { it.id to it.enabled }
            )
            assertEquals(setOf("off", "on"), db.highlightRuleDao().getBookStates(imported.id).map { it.ruleId }.toSet())
            assertNull(db.highlightRuleDao().getSettings(imported.id))
            rules.setRuleEnabled(imported.id, "off", true)
            assertFalse(rules.getRulesForBook(source.id).single { it.id == "off" }.enabled)

            val internal = book("internal", lastReadTime = 13)
            val pdf = book("pdf", lastReadTime = 14).copy(format = BookFormat.PDF)
            books.insertBook(internal)
            books.insertExternalBook(pdf)
            assertTrue(db.highlightRuleDao().getBookStates(internal.id).isEmpty())
            assertTrue(db.highlightRuleDao().getBookStates(pdf.id).isEmpty())
        }

    @Test
    fun firstExternalBookUsesRuleLibraryDefaults() = withRepositories { db, books, rules ->
        rules.saveRule(HighlightRule("rule", "Text", "text", enabled = false))
        val first = book("first")
        books.insertExternalBook(first)
        assertTrue(db.highlightRuleDao().getBookStates(first.id).isEmpty())
        assertFalse(rules.getRulesForBook(first.id).single().enabled)
    }

    private fun book(id: String, lastReadTime: Long = 0): Book = Book(
        id = id,
        title = id,
        author = "",
        filePath = "/books/$id.txt",
        coverPath = null,
        format = BookFormat.TXT,
        lastReadTime = lastReadTime,
        readingProgress = 0f,
        createdAt = 0
    )

    private fun withRepositories(
        test: suspend (AppDatabase, BookRepositoryImpl, HighlightRuleRepositoryImpl) -> Unit
    ) = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val identity = SyncIdentityStore(db.syncStateDao())
            test(
                db,
                BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identity),
                HighlightRuleRepositoryImpl(db.highlightRuleDao(), db.noteDao(), db, identity)
            )
        } finally {
            db.close()
        }
    }
}
