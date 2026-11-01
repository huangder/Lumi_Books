package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.*
import org.junit.Test

class NotesExportSourceTest {
    private fun book(id: String) = Book(id, id, "Author", "", null, BookFormat.EPUB, 0, 0f, createdAt = 0)
    private fun note(id: Long, bookId: String) = Note(id, bookId, 0, 0, 5,
        selectedText = "source", note = "", color = "red", createdAt = id)

    @Test fun snapshotKeepsFirstSeenBookOrderAndDoesNotAddOtherMarks() {
        val first = book("a")
        val second = book("b")
        val bookmark = Bookmark(id = 3, bookId = "b", chapterIndex = 1, position = 0f,
            title = "mark", createdAt = 3)
        val result = notesExportBooks(listOf(
            AnnotationFeedEntry.BookmarkEntry(second, bookmark),
            AnnotationFeedEntry.NoteEntry(first, note(1, "a")),
            AnnotationFeedEntry.NoteEntry(second, note(2, "b"))
        ))
        assertEquals(listOf("b", "a"), result.map { it.book.id })
        assertEquals(listOf(2L), result.first().notes.map { it.id })
        assertEquals(listOf(bookmark), result.first().bookmarks)
        assertTrue(result.last().bookmarks.isEmpty())
    }

    @Test fun excludesHiddenBooksAndBothKindsOfInkEvenForHandBuiltInputs() {
        val visible = book("a")
        val hidden = book("hidden").copy(isHidden = true)
        val result = notesExportBooks(listOf(
            AnnotationFeedEntry.NoteEntry(hidden, note(1, "hidden")),
            AnnotationFeedEntry.NoteEntry(visible, note(2, "a").copy(type = "pdf_ink_pen")),
            AnnotationFeedEntry.NoteEntry(visible, note(3, "a").copy(type = "epub_comic_ink_pen")),
            AnnotationFeedEntry.NoteEntry(visible, note(4, "a"))
        ))
        assertEquals(listOf("a"), result.map { it.book.id })
        assertEquals(listOf(4L), result.single().notes.map { it.id })
    }

    @Test fun exportUsesExactlyTheEntriesSurvivingEveryFeedFilter() {
        val book = book("a")
        val selected = note(1, "a").copy(type = "underline", tags = listOf("keep"))
        val rule = selected.copy(id = 2, origin = Note.ORIGIN_HIGHLIGHT_RULE,
            sourceRuleId = "r", sourceMatchKey = "m")
        val entries = listOf(selected, rule, selected.copy(id = 3, color = "blue"),
            selected.copy(id = 4, tags = emptyList()), selected.copy(id = 5, selectedText = "other"))
            .map { AnnotationFeedEntry.NoteEntry(book, it) }
        val query = AnnotationFeedQuery(type = AnnotationFeedType.UNDERLINES,
            filter = ReadingMarkFilter(tag = "keep", color = "red", underlineMode = 3),
            searchText = "source")
        assertEquals(listOf(1L), notesExportBooks(entries.matching(query)).single().notes.map { it.id })
        assertEquals(listOf(1L, 2L), notesExportBooks(entries.matching(query.copy(includeRuleHighlights = true)))
            .single().notes.map { it.id })
    }
}
