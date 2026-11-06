package com.huangder.lumibooks.ui.bookshare

import com.huangder.lumibooks.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BookShareSnapshotTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val book = Book(id = "book", title = "A book", author = "", filePath = "", coverPath = null,
        format = BookFormat.EPUB, lastReadTime = 0, readingProgress = .7f, createdAt = 0)

    @Test fun historicalCheckInUsesItsOwnActivityAndDoesNotInventHistoricalProgress() {
        val yesterday = today.minusDays(1)
        val days = mapOf(yesterday to ActivityDuration(60_000, 120_000), today to ActivityDuration(600_000, 0))
        val snapshot = createBookShareSnapshot(book, "My review", 4f, days, yesterday, today)
        assertEquals(ActivityDuration(60_000, 120_000), snapshot.activity)
        assertNull(snapshot.currentProgress)
        assertEquals(.7f, createBookShareSnapshot(book, "", 0f, days, today, today).currentProgress!!, 0f)
    }

    @Test fun anEmptyDateCannotExportACheckInAndTheSnapshotKeepsTheOriginalContent() {
        val days = mutableMapOf(today to ActivityDuration(0, 120_000))
        val snapshot = createBookShareSnapshot(book, "Original review", 4f, days, today, today)
        days[today] = ActivityDuration(999_000, 0)
        assertEquals(ActivityDuration(0, 120_000), snapshot.activity)
        assertEquals("Original review", snapshot.review)
        val empty = createBookShareSnapshot(book, "", 0f, days, today.minusDays(2), today)
        assertFalse(shareHasContent(empty, BookShareKind.CHECK_IN))
        assertTrue(shareHasContent(empty, BookShareKind.REVIEW))
    }
}
