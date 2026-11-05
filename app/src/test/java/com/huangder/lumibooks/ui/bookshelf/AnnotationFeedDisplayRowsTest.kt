package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AnnotationFeedDisplayRowsTest {
    private val book = Book(
        id = "book", title = "Book", author = "Author", filePath = "", coverPath = null,
        format = BookFormat.EPUB, lastReadTime = 0, readingProgress = 0f, createdAt = 0
    )
    private val environment = AnnotationFeedDisplayEnvironment(
        locale = Locale.US, zone = ZoneId.of("UTC"), today = LocalDate.of(2026, 11, 5),
        timePattern = "HH:mm", dayPattern = "MMMM d", otherYearPattern = "yyyy MMMM d",
        todayLabel = "Today", yesterdayLabel = "Yesterday"
    )

    private fun note(id: Long, timestamp: String) = AnnotationFeedEntry.NoteEntry(
        book,
        Note(
            id = id, bookId = book.id, chapterIndex = 0, startPosition = 0, endPosition = 4,
            selectedText = "text", note = "", color = "red",
            createdAt = Instant.parse(timestamp).toEpochMilli()
        )
    )

    @Test fun preservesEntriesOrderAndDistinctKeysForMixedMarks() {
        val first = note(1, "2026-11-05T14:05:00Z")
        val second = AnnotationFeedEntry.BookmarkEntry(
            book, Bookmark(
                id = 1, bookId = book.id, chapterIndex = 0, position = 0f, title = "Mark",
                createdAt = Instant.parse("2026-11-05T13:00:00Z").toEpochMilli()
            )
        )
        val rows = listOf(first, second).toDisplayRows(environment)
        assertEquals(listOf("day-2026-11-05", "note-1", "bookmark-1"), rows.map { it.key })
        assertEquals(listOf("day", "note", "bookmark"), rows.map { it.contentType })
        val entries = rows.filterIsInstance<AnnotationFeedRow.Entry>()
        assertSame(first, entries[0].entry)
        assertSame(second, entries[1].entry)
        assertEquals(listOf("14:05", "13:00"), entries.map { it.timeText })
    }

    @Test fun changingZoneRegroupsAcrossMidnightAndFormatsInThatZone() {
        val entries = listOf(
            note(1, "2026-11-05T00:30:00Z"),
            note(2, "2026-11-04T23:30:00Z")
        )
        val utc = entries.toDisplayRows(environment)
        assertEquals(listOf("Today", "Yesterday"), utc.filterIsInstance<AnnotationFeedRow.Day>().map { it.label })
        val local = entries.toDisplayRows(environment.copy(zone = ZoneId.of("Asia/Shanghai")))
        assertEquals(listOf(LocalDate.of(2026, 11, 5)), local.filterIsInstance<AnnotationFeedRow.Day>().map { it.day })
        assertEquals(listOf("08:30", "07:30"), local.filterIsInstance<AnnotationFeedRow.Entry>().map { it.timeText })
    }

    @Test fun advancingTodayExpiresRelativeLabelsWithoutChangingEntries() {
        val entries = listOf(note(1, "2026-11-05T14:05:00Z"))
        fun label(today: LocalDate) = entries.toDisplayRows(environment.copy(today = today))
            .filterIsInstance<AnnotationFeedRow.Day>().single().label
        assertEquals("Today", label(environment.today))
        assertEquals("Yesterday", label(environment.today.plusDays(1)))
        assertEquals("November 5", label(environment.today.plusDays(2)))
        assertEquals("2026 November 5", label(LocalDate.of(2027, 1, 1)))
    }

    @Test fun localeAndClockPatternAreExplicitInputs() {
        val entries = listOf(note(1, "2026-01-05T14:05:00Z"))
        val french = entries.toDisplayRows(environment.copy(locale = Locale.FRANCE))
        assertEquals("janvier 5", french.filterIsInstance<AnnotationFeedRow.Day>().single().label)
        val twelveHour = entries.toDisplayRows(environment.copy(timePattern = "h:mm a"))
        assertEquals("2:05 PM", twelveHour.filterIsInstance<AnnotationFeedRow.Entry>().single().timeText)
        assertEquals("14:05", entries.toDisplayRows(environment).filterIsInstance<AnnotationFeedRow.Entry>().single().timeText)
    }
}
