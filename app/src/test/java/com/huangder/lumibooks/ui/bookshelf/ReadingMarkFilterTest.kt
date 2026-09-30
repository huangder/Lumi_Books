package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.*
import org.junit.Test

class ReadingMarkFilterTest {
    private val underline = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 4,
        selectedText = "text", note = "", color = "red", createdAt = 1, type = "underline", tags = listOf("Study", "Quote"))

    @Test fun combinesTagColorAndAllFourLineStyles() {
        for (mode in 1..4) {
            val item = underline.copy(styleSnapshotJson = """{"underlineMode":$mode}""")
            assertTrue(ReadingMarkFilter("Study", "red", mode).matches(item))
            assertFalse(ReadingMarkFilter("Missing", "red", mode).matches(item))
            assertFalse(ReadingMarkFilter("Quote", "blue", mode).matches(item))
            assertFalse(ReadingMarkFilter("Quote", "red", mode % 4 + 1).matches(item))
        }
        assertTrue(ReadingMarkFilter(underlineMode = 3).matches(underline))
        assertTrue(ReadingMarkFilter(underlineMode = 3).matches(underline.copy(styleSnapshotJson = "{}")))
    }

    @Test fun supportsUntaggedAndDoesNotApplyStyleFiltersToNotesOrBookmarks() {
        val untagged = underline.copy(tags = emptyList())
        assertTrue(ReadingMarkFilter("").matches(untagged))
        assertFalse(ReadingMarkFilter("").matches(underline))
        assertTrue(ReadingMarkFilter().matches(underline))
        assertTrue(ReadingMarkFilter("Study", "blue", 1).matches(underline.copy(isNote = true)))
        val bookmark = Bookmark(bookId = "book", chapterIndex = 0, position = 0f, title = "Mark", createdAt = 1, tags = listOf("Study"))
        assertTrue(ReadingMarkFilter("Study", "blue", 4).matches(bookmark))
        assertFalse(ReadingMarkFilter("").matches(bookmark))
        assertTrue(ReadingMarkFilter("").matches(bookmark.copy(tags = emptyList())))
        assertTrue(ReadingMarkFilter("Study", "red", 4).matches(underline.copy(type = "highlight")))
    }
}
