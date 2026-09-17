package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubHighlightRoutingTest {
    @Test
    fun routesOnlyNotesForTheRenderedChapterAndKeepsOrder() {
        val notes = listOf(
            note(id = 1, chapterIndex = 3, text = "first"),
            note(id = 2, chapterIndex = 1, text = "other"),
            note(id = 3, chapterIndex = 3, text = "second")
        )

        assertEquals(
            listOf(1L, 3L),
            epubNotesForChapter(notes, chapterIndex = 3).map { it.id }
        )
    }

    @Test
    fun missingChapterHasNoNotes() {
        assertEquals(emptyList<Note>(), epubNotesForChapter(listOf(note()), chapterIndex = 9))
    }

    private fun note(
        id: Long = 1,
        chapterIndex: Int = 0,
        text: String = "text"
    ) = Note(
        id = id,
        bookId = "book",
        chapterIndex = chapterIndex,
        startPosition = 0,
        endPosition = text.length,
        selectedText = text,
        note = "",
        color = "#FFEB3B",
        createdAt = 1
    )
}
