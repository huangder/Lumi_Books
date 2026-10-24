package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsPeriodTest {
    @Test
    fun weekRunsSundayToSaturdayAndShiftsByOffset() {
        val wednesday = LocalDate.of(2026, 7, 15)
        assertEquals(
            StatisticsPeriod(LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 18)),
            StatisticsPeriod.week(wednesday, 0)
        )
        assertEquals(LocalDate.of(2026, 7, 5), StatisticsPeriod.week(wednesday, -1).start)
        // A Sunday starts its own week.
        assertEquals(LocalDate.of(2026, 7, 19), StatisticsPeriod.week(LocalDate.of(2026, 7, 19), 0).start)
        assertEquals(7, StatisticsPeriod.week(wednesday, 0).days.size)
    }

    @Test
    fun monthAndYearCoverWholeCalendarRanges() {
        assertEquals(LocalDate.of(2028, 2, 29), StatisticsPeriod.month(2028, 1).endInclusive)
        assertEquals(LocalDate.of(2027, 2, 28), StatisticsPeriod.month(2027, 1).endInclusive)
        assertEquals(
            StatisticsPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            StatisticsPeriod.year(2026)
        )
    }

    @Test
    fun millisEndIsExclusiveStartOfNextDay() {
        val day = StatisticsPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        assertEquals(24 * 60 * 60 * 1000L, day.endMillis(ZoneOffset.UTC) - day.startMillis(ZoneOffset.UTC))
    }

    @Test
    fun summaryCountsHandMadeMarksOnlyAndRanksTags() {
        fun note(type: String = "highlight", note: String = "", tags: List<String> = emptyList()) = Note(
            bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 1, selectedText = "text",
            note = note, color = "red", createdAt = 1, type = type, tags = tags
        )
        val notes = listOf(
            note(tags = listOf("Study")),
            note(type = "underline", tags = listOf("Quote")),
            note(note = "thought", tags = listOf("Study")),
            note(type = "pdf_ink_pen", tags = listOf("Ink")),
            note(tags = listOf("Rule")).copy(origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "r", sourceMatchKey = "k")
        )
        val bookmarks = listOf(
            Bookmark(bookId = "book", chapterIndex = 0, position = 0f, title = "Mark", createdAt = 1, tags = listOf("Study", "Quote"))
        )
        val summary = summarizeAnnotations(notes, bookmarks)
        assertEquals(1, summary.highlights)
        assertEquals(1, summary.underlines)
        assertEquals(1, summary.notes)
        assertEquals(1, summary.bookmarks)
        assertEquals(listOf("Study" to 3, "Quote" to 2), summary.topTags)
    }
}
