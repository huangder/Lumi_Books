package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Inclusive calendar range shown by one statistics tab. */
internal data class StatisticsPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end, so timestamps such as annotation createdAt use `>= start AND < end`. */
    fun endMillis(zone: ZoneId): Long = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= endInclusive }.toList()

    companion object {
        /** Weeks run Sunday to Saturday, matching the weekly chart. */
        fun week(today: LocalDate, offset: Int): StatisticsPeriod {
            val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong()).plusWeeks(offset.toLong())
            return StatisticsPeriod(sunday, sunday.plusDays(6))
        }

        fun month(year: Int, month0: Int): StatisticsPeriod {
            val yearMonth = YearMonth.of(year, month0 + 1)
            return StatisticsPeriod(yearMonth.atDay(1), yearMonth.atEndOfMonth())
        }

        fun year(year: Int): StatisticsPeriod =
            StatisticsPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }
}

data class AnnotationPeriodSummary(
    val highlights: Int = 0,
    val underlines: Int = 0,
    val notes: Int = 0,
    val bookmarks: Int = 0,
    val topTags: List<Pair<String, Int>> = emptyList()
)

/** Counts marks the reader made by hand; ink strokes and rule-generated highlights are excluded. */
internal fun summarizeAnnotations(
    notes: List<Note>,
    bookmarks: List<Bookmark>,
    topTagCount: Int = 3
): AnnotationPeriodSummary {
    val marks = notes.filterNot { it.isInkStroke || (it.isGeneratedByHighlightRule && !it.isNoteEntry) }
    val noteEntries = marks.count { it.isNoteEntry }
    val underlines = marks.count { !it.isNoteEntry && it.type == "underline" }
    val topTags = (marks.flatMap { it.tags } + bookmarks.flatMap { it.tags })
        .groupingBy { it }.eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(topTagCount)
        .map { it.key to it.value }
    return AnnotationPeriodSummary(
        highlights = marks.size - noteEntries - underlines,
        underlines = underlines,
        notes = noteEntries,
        bookmarks = bookmarks.size,
        topTags = topTags
    )
}
