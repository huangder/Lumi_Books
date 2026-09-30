package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.TagEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.highlight.HighlightRuleMaterializer
import java.lang.reflect.Proxy
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
class AnnotationTagsRepositoryTest {
    @Test fun ruleRescanKeepsTagsAndStableIdentityAfterStyleChanges() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val identity = SyncIdentityStore(db.syncStateDao())
            val repository = ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(), db.noteDao(), db.syncStateDao(), identity, db)
            val rules = HighlightRuleRepositoryImpl(db.highlightRuleDao(), db.noteDao(), db, identity)
            val unusedBooks = Proxy.newProxyInstance(BookRepository::class.java.classLoader, arrayOf(BookRepository::class.java)) { _, _, _ ->
                error("Chapter materialization must not read a book file")
            } as BookRepository
            val materializer = HighlightRuleMaterializer(context, unusedBooks, rules, db, identity)
            val rule = HighlightRule("r1", "Rule", "人物", underlineMode = 3)
            rules.saveRule(rule)
            rules.setMaterializeNotes("book", true)
            materializer.materializeChapter("book", BookFormat.TXT, 0, "人物出场。")
            val original = repository.getNotesByBookId("book").first().single()
            repository.updateNoteTags(original, listOf("主角"))
            for (mode in listOf(1, 2, 4, 3)) {
                rules.saveRule(rule.copy(underlineMode = mode))
                materializer.materializeChapter("book", BookFormat.TXT, 0, "人物出场。")
                val rescanned = repository.getNotesByBookId("book").first().single()
                assertEquals(original.id, rescanned.id)
                assertEquals(original.syncId, rescanned.syncId)
                assertEquals(listOf("主角"), rescanned.tags)
                assertTrue(rescanned.isGeneratedByHighlightRule)
                assertTrue(rules.getExclusionKeys("book").isEmpty())
            }
        } finally { db.close() }
    }

    @Test fun editsAllFourKindsAndRenamesAcrossBooksWithoutTouchingBookTags() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val repository = ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(), db.noteDao(),
                db.syncStateDao(), SyncIdentityStore(db.syncStateDao()), db)
            db.tagDao().insertTag(TagEntity(id = "book-tag", name = "人物", normalizedName = "人物", createdAt = 1L))
            for ((index, type) in listOf("highlight", "underline", "note").withIndex()) {
                repository.insertNote(Note(bookId = if (index == 1) "other" else "book", chapterIndex = 0,
                    startPosition = 0, endPosition = 2, selectedText = "正文", note = "", color = "#ff0000", createdAt = 1,
                    type = type, syncId = "n$index", isNote = type == "note", origin = Note.ORIGIN_HIGHLIGHT_RULE,
                    sourceRuleId = "rule", sourceMatchKey = "match$index"))
            }
            repository.insertBookmark(Bookmark(bookId = "book", chapterIndex = 0, position = 0f,
                title = "书签", createdAt = 1, syncId = "b"))
            val notes = repository.getNotesByBookId("book").first() + repository.getNotesByBookId("other").first()
            notes.forEach { repository.updateNoteTags(it, listOf(" 人物 ", "复习", "人物")) }
            val bookmark = repository.getBookmarksByBookId("book").first().single()
            repository.updateBookmarkTags(bookmark, listOf("人物"))
            assertEquals(setOf("人物", "复习"), repository.observeAnnotationTags().first().toSet())
            repository.renameAnnotationTag("人物", "主题")
            assertEquals(listOf("主题"), repository.getBookmarksByBookId("book").first().single().tags)
            assertEquals(listOf("主题", "复习"), repository.getNotesByBookId("other").first().single().tags)
            repository.deleteAnnotationTag("主题")
            assertTrue(repository.getBookmarksByBookId("book").first().single().tags.isEmpty())
            val edited = repository.getNotesByBookId("book").first()
            assertTrue(edited.all { it.tags == listOf("复习") && it.isGeneratedByHighlightRule })
            assertEquals("人物", db.tagDao().getAllTags().first().single().name)
        } finally { db.close() }
    }
}
