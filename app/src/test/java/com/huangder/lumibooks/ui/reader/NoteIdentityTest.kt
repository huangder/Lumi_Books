package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.bookshelf.BookNotesUiState
import org.junit.Assert.*
import org.junit.Test

class NoteIdentityTest {
    private fun annotation() = Note(
        bookId = "book", chapterIndex = 2, startPosition = 7, endPosition = 11,
        selectedText = "text", note = "", color = "#ffee00", createdAt = 1
    )

    @Test fun emptyNoteAndHighlightRemainDistinct() {
        val highlight = annotation()
        val note = highlight.copy(id = 2, isNote = true)
        val state = BookNotesUiState(notes = listOf(highlight, note))
        assertEquals(listOf(highlight), state.highlights)
        assertEquals(listOf(note), state.noteItems)
    }

    @Test fun clearingLegacyNoteRetainsIntentAndUnderlineStyle() {
        val note = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 1,
            selectedText = "a", note = "comment", color = "#000000", createdAt = 1, type = "underline")
        assertTrue(note.copy(note = "").isNoteEntry)
        assertEquals("underline", note.copy(note = "").type)
    }

    @Test fun emptyBodyDoesNotChangeSelectedTextAnchor() {
        val note = annotation().copy(isNote = true, startPosition = 0, endPosition = 4)
        val resolved = requireNotNull(resolveReaderNote(note, "prefix text suffix"))
        assertEquals(7, resolved.startPosition)
        assertEquals(11, resolved.endPosition)
        assertTrue(resolved.isNoteEntry)
    }

    @Test fun positionOnlyNoteUsesSavedCharacterOffset() {
        val note = annotation().copy(isNote = true, selectedText = "", endPosition = 7)
        assertEquals(7, requireNotNull(resolveReaderNote(note, "prefix text suffix")).startPosition)
        assertNull(resolveReaderNote(note.copy(isNote = false), "prefix text suffix"))
    }

    @Test fun navigationDoesNotRequireAnAlreadyResolvedHighlight() {
        val note = annotation().copy(startLocatorJson = """{"version":2,"textPosition":9,"exact":"old quote"}""")
        assertNull(resolveReaderNote(note, "prefix edited chapter"))
        assertEquals(9, resolveReaderNoteNavigation(note, "prefix edited chapter").startPosition)
        assertEquals(2, resolveReaderNoteNavigation(note, null).chapterIndex)
        assertEquals(9, resolveReaderNoteNavigation(note, null).startPosition)
        assertEquals(7, resolveReaderNoteNavigation(annotation(), null).startPosition)
    }
}
