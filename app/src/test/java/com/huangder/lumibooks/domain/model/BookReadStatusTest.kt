package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookReadStatusTest {
    @Test
    fun manualReadMarkSurvivesProgressUpdatesUntilExplicitlyToggledOff() {
        val original = book(progress = 0.35f)

        val marked = original.toggleReadStatus()
        val reread = marked.copy(readingProgress = 0.6f)

        assertTrue(marked.isReadingFinished)
        assertEquals(0.35f, marked.readingProgress)
        assertTrue(reread.isReadingFinished)

        val unmarked = reread.toggleReadStatus()
        assertFalse(unmarked.isReadingFinished)
        assertEquals(0f, unmarked.readingProgress)
        assertFalse(unmarked.isManuallyMarkedRead)
    }

    private fun book(progress: Float) = Book(
        id = "book",
        title = "Book",
        author = "Author",
        filePath = "book.epub",
        coverPath = null,
        format = BookFormat.EPUB,
        lastReadTime = 0L,
        readingProgress = progress,
        createdAt = 0L
    )
}
