package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.StoredNotesExportTemplate
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.pdfconversion.PdfTextExtractor
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
class NotesExportStorageTest {
    private fun book(format: BookFormat) = Book("a", "Book", "Author", "/missing/book", null, format, 0, 0f, createdAt = 0)

    @Test fun templateSurvivesPortablePreferenceRoundTripWithoutEditionCode() = runBlocking {
        val manager = DataStoreManager(RuntimeEnvironment.getApplication())
        val saved = StoredNotesExportTemplate("# {{book}}", "{{text}}\n{{unknown}}", "txt")
        manager.saveNotesExportTemplate(saved)
        val portable = manager.exportPortablePreferences("test").filter { it.key.startsWith("notes_export_") }
        assertEquals(3, portable.size)
        manager.saveNotesExportTemplate(StoredNotesExportTemplate("changed", "changed", "md"))
        // Restore is allowed to replace locally changed preferences.
        manager.replacePortablePreferences(portable, "test")
        assertEquals(saved, manager.notesExportTemplate.first())
    }

    @Test fun rasterFormatsUsePageLabelsAndTxtStaysOnTheOriginalFormatter() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val builder = BookNotesExportBuilder(context, DataStoreManager(context), PdfTextExtractor())
        val mark = Bookmark(id = 1, bookId = "a", chapterIndex = 2, position = 0f,
            title = "fallback", createdAt = 0)
        for (format in listOf(BookFormat.PDF, BookFormat.CBZ)) {
            val content = builder.prepare(NotesExportBook(book(format), emptyList(), listOf(mark)))
            assertEquals(content.chapterTitles[2], content.bookmarks.single().chapterTitle)
            assertFalse(content.bookmarks.single().chapterTitle.isBlank())
            assertEquals(content.asTxt(), builder.build(book(format), listOf(mark), emptyList()))
        }
    }

    @Test fun missingBookFallsBackToNumberedChaptersWithoutDroppingText() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val builder = BookNotesExportBuilder(context, DataStoreManager(context), PdfTextExtractor())
        val note = Note(bookId = "a", chapterIndex = 7, startPosition = 0, endPosition = 4,
            selectedText = "kept source", note = "", color = "red", createdAt = 0)
        val content = builder.prepare(NotesExportBook(book(BookFormat.EPUB), listOf(note), emptyList()))
        assertTrue(content.chapterTitles.isEmpty())
        assertTrue(content.asTxt().text.contains(content.labels.chapterNumber(8)))
        assertTrue(content.asTxt().text.contains("kept source"))
    }
}
