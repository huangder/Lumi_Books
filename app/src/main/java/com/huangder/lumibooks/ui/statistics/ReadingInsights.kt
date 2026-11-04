package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.home.ReadingStreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

internal enum class StatisticsScope { ALL, WEEK, MONTH, YEAR }

internal data class StatisticsSelection(
    val scope: StatisticsScope = StatisticsScope.ALL,
    val year: Int = LocalDate.now().year,
    val month0: Int = LocalDate.now().monthValue - 1,
    val weekOffset: Int = 0
) {
    fun range(today: LocalDate): StatisticsPeriod = when (scope) {
        StatisticsScope.ALL -> StatisticsPeriod(LocalDate.of(1970, 1, 1), today)
        StatisticsScope.WEEK -> StatisticsPeriod.week(today, weekOffset)
        StatisticsScope.MONTH -> StatisticsPeriod.month(year, month0)
        StatisticsScope.YEAR -> StatisticsPeriod.year(year)
    }
    fun shift(delta: Int, today: LocalDate): StatisticsSelection {
        val shifted = when (scope) {
            StatisticsScope.ALL -> this
            StatisticsScope.WEEK -> copy(weekOffset = (weekOffset + delta).coerceAtMost(0))
            StatisticsScope.MONTH -> LocalDate.of(year, month0 + 1, 1).plusMonths(delta.toLong()).let {
                copy(year = it.year, month0 = it.monthValue - 1)
            }
            StatisticsScope.YEAR -> copy(year = (year + delta).coerceAtMost(today.year),
                month0 = if (year + delta >= today.year) minOf(month0, today.monthValue - 1) else month0)
        }
        return shifted.takeIf { it.year >= 1970 && it.range(today).start <= today } ?: this
    }
}

internal fun LocalDate.readingDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    .format(Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant()))

internal fun readingLocalDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrElse {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
        .parse(value)!!.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}

internal data class InsightsBook(val book: Book, val activity: ReadingBookActivity)
internal data class ReadingInsights(
    val period: StatisticsPeriod,
    val through: LocalDate,
    val daily: Map<LocalDate, ActivityDuration> = emptyMap(),
    val books: List<InsightsBook> = emptyList(),
    val annotations: AnnotationPeriodSummary = AnnotationPeriodSummary(),
    val goalMinutes: Int = 0
) {
    val activity = ActivityDuration(daily.values.sumOf { it.readingDuration }, daily.values.sumOf { it.listeningDuration })
    val readingDays = daily.count { it.value.totalDuration > 0 }
    val elapsedDays = (ChronoUnit.DAYS.between(period.start, through) + 1).coerceAtLeast(1)
    val dailyAverage = activity.totalDuration / elapsedDays
    val bestDay = daily.entries.filter { it.value.totalDuration > 0 }.maxByOrNull { it.value.totalDuration }
    val longestStreak = ReadingStreakCalculator.longest(daily.mapKeys { it.key.toString() }.mapValues { it.value.totalDuration }, goalMinutes * 60_000L)
    val currentStreak = ReadingStreakCalculator.calculate(daily.mapKeys { it.key.toString() }.mapValues { it.value.totalDuration }, through, goalMinutes * 60_000L)
    val goalDays = daily.count { if (goalMinutes > 0) it.value.totalDuration >= goalMinutes * 60_000L else it.value.totalDuration > 0 }
    fun averageForWeekend(weekend: Boolean): Long {
        val days = generateSequence(period.start) { it.plusDays(1) }.takeWhile { it <= through }
            .filter { (it.dayOfWeek.value >= 6) == weekend }.toList()
        return if (days.isEmpty()) 0 else days.sumOf { daily[it]?.totalDuration ?: 0 } / days.size
    }
}

internal fun observeReadingInsights(
    repository: ReadingRepository, books: BookRepository, period: StatisticsPeriod,
    today: LocalDate, goalMinutes: Int, allTime: Boolean = false
): Flow<ReadingInsights> {
    val through = minOf(period.endInclusive, today)
    val zone = ZoneId.systemDefault()
    val end = StatisticsPeriod(period.start, through)
    return combine(
        repository.getDailyActivity(period.start.readingDate(), through.readingDate()),
        repository.getBookActivity(period.start.readingDate(), through.readingDate()),
        books.getAllBooks(),
        repository.observeTextNotesCreatedBetween(end.startMillis(zone), end.endMillis(zone)),
        repository.observeBookmarksCreatedBetween(end.startMillis(zone), end.endMillis(zone))
    ) { days, activity, library, notes, bookmarks ->
        val visible = library.filterNot { it.isHidden }.associateBy { it.id }
        val daily = days.associate { readingLocalDate(it.date) to ActivityDuration(it.readingDuration, it.listeningDuration) }
            .filterKeys { it >= period.start && it <= through }
        val effective = if (allTime) StatisticsPeriod(daily.keys.minOrNull() ?: through, through) else period
        ReadingInsights(effective, through, daily,
            activity.mapNotNull { row -> visible[row.bookId]?.let { InsightsBook(it, row) } },
            summarizeAnnotations(notes.filter { it.bookId in visible }, bookmarks.filter { it.bookId in visible }), goalMinutes)
    }
}

internal data class PeriodComparison(val current: StatisticsPeriod, val previous: StatisticsPeriod, val currentMs: Long, val previousMs: Long)

internal fun comparisonPeriods(selection: StatisticsSelection, today: LocalDate): Pair<StatisticsPeriod, StatisticsPeriod>? {
    if (selection.scope == StatisticsScope.ALL) return null
    val range = selection.range(today)
    val previous = when (selection.scope) {
        StatisticsScope.WEEK -> StatisticsPeriod(range.start.minusWeeks(1), range.endInclusive.minusWeeks(1))
        StatisticsScope.MONTH -> range.start.minusMonths(1).let { StatisticsPeriod.month(it.year, it.monthValue - 1) }
        StatisticsScope.YEAR -> StatisticsPeriod.year(range.start.year - 1)
        else -> return null
    }
    if (range.endInclusive < today) return range to previous
    val count = minOf(ChronoUnit.DAYS.between(range.start, today), previous.days.size.toLong())
    if (count <= 0) return null
    return StatisticsPeriod(range.start, range.start.plusDays(count - 1)) to
        StatisticsPeriod(previous.start, previous.start.plusDays(count - 1))
}

internal fun annualBooksByMonth(books: List<InsightsBook>): Map<Int, List<InsightsBook>> = books
    .sortedWith(compareBy<InsightsBook> { readingLocalDate(it.activity.firstDate) }.thenBy { it.book.title }.thenBy { it.book.id })
    .groupBy { readingLocalDate(it.activity.firstDate).monthValue }.toSortedMap()
