package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.home.ReadingStreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
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
    // Book metadata changes must use the latest complete Book, without repeating history work.
    // The observer prepares this cache on Default before publishing it to Compose.
    @Volatile private var prepared: ReadingInsightsTotals? = null
    private val totals: ReadingInsightsTotals
        get() = prepared ?: ReadingInsightsTotals(period, through, daily, goalMinutes).also { prepared = it }
    val activity get() = totals.activity
    val readingDays get() = totals.readingDays
    val elapsedDays get() = totals.elapsedDays
    val dailyAverage get() = totals.dailyAverage
    val bestDay get() = totals.bestDay
    val longestStreak get() = totals.longestStreak
    val currentStreak get() = totals.currentStreak
    val goalDays get() = totals.goalDays
    fun averageForWeekend(weekend: Boolean): Long = if (weekend) totals.weekendAverage else totals.weekdayAverage

    internal fun withBooks(latestBooks: List<InsightsBook>): ReadingInsights = copy(books = latestBooks).also {
        it.prepared = totals
    }
}

private class ReadingInsightsTotals(
    private val period: StatisticsPeriod,
    private val through: LocalDate,
    private val daily: Map<LocalDate, ActivityDuration>,
    private val goalMinutes: Int
) {
    val activity = ActivityDuration(daily.values.sumOf { it.readingDuration }, daily.values.sumOf { it.listeningDuration })
    val readingDays = daily.count { it.value.totalDuration > 0 }
    val elapsedDays = (ChronoUnit.DAYS.between(period.start, through) + 1).coerceAtLeast(1)
    val dailyAverage = activity.totalDuration / elapsedDays
    val bestDay = daily.entries.filter { it.value.totalDuration > 0 }.maxByOrNull { it.value.totalDuration }
    private val durations = daily.entries.associate { it.key.toString() to it.value.totalDuration }
    val longestStreak = ReadingStreakCalculator.longest(durations, goalMinutes * 60_000L)
    val currentStreak = ReadingStreakCalculator.calculate(durations, through, goalMinutes * 60_000L)
    val goalDays = daily.count { if (goalMinutes > 0) it.value.totalDuration >= goalMinutes * 60_000L else it.value.totalDuration > 0 }
    private fun average(weekend: Boolean): Long {
        var date = period.start
        var count = 0L
        var duration = 0L
        while (date <= through) {
            if ((date.dayOfWeek.value >= 6) == weekend) {
                count++
                duration += daily[date]?.totalDuration ?: 0L
            }
            date = date.plusDays(1)
        }
        return if (count == 0L) 0L else duration / count
    }
    val weekdayAverage = average(false)
    val weekendAverage = average(true)
}

private data class InsightsInputs(
    val days: List<ReadingDayActivity>, val activity: List<ReadingBookActivity>, val library: List<Book>,
    val notes: List<Note>, val bookmarks: List<Bookmark>
)

private data class InsightsAggregationKey(
    val days: List<ReadingDayActivity>, val activity: List<ReadingBookActivity>,
    val notes: List<Note>, val bookmarks: List<Bookmark>, val visibleIds: Set<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeReadingInsights(
    repository: ReadingRepository, books: BookRepository, period: StatisticsPeriod,
    today: LocalDate, goalMinutes: Int, allTime: Boolean = false
): Flow<ReadingInsights> {
    val through = minOf(period.endInclusive, today)
    val zone = ZoneId.systemDefault()
    val end = StatisticsPeriod(period.start, through)
    var previousKey: InsightsAggregationKey? = null
    var previousInsights: ReadingInsights? = null
    return combine(
        repository.getDailyActivity(period.start.readingDate(), through.readingDate()),
        repository.getBookActivity(period.start.readingDate(), through.readingDate()),
        books.getAllBooks(),
        repository.observeTextNotesCreatedBetween(end.startMillis(zone), end.endMillis(zone)),
        repository.observeBookmarksCreatedBetween(end.startMillis(zone), end.endMillis(zone))
        ) { days, activity, library, notes, bookmarks -> InsightsInputs(days, activity, library, notes, bookmarks) }
            .mapLatest { input ->
                val context = currentCoroutineContext()
                context.ensureActive()
                val visible = input.library.filterNot { it.isHidden }.associateBy { it.id }
                val key = InsightsAggregationKey(input.days, input.activity, input.notes, input.bookmarks, visible.keys)
                val aggregated = previousInsights?.takeIf { key == previousKey } ?: run {
                    val daily = input.days.mapIndexedNotNull { index, day ->
                        if (index % 128 == 0) context.ensureActive()
                        val date = readingLocalDate(day.date)
                        if (date < period.start || date > through) null
                        else date to ActivityDuration(day.readingDuration, day.listeningDuration)
                    }.toMap()
                    val notes = input.notes.filterIndexed { index, note ->
                        if (index % 128 == 0) context.ensureActive()
                        note.bookId in visible
                    }
                    val bookmarks = input.bookmarks.filterIndexed { index, bookmark ->
                        if (index % 128 == 0) context.ensureActive()
                        bookmark.bookId in visible
                    }
                    val effective = if (allTime) StatisticsPeriod(daily.keys.minOrNull() ?: through, through) else period
                    ReadingInsights(effective, through, daily, annotations = summarizeAnnotations(notes, bookmarks), goalMinutes = goalMinutes)
                }
                val latestBooks = input.activity.mapIndexedNotNull { index, row ->
                    if (index % 128 == 0) context.ensureActive()
                    visible[row.bookId]?.let { InsightsBook(it, row) }
                }
                val result = aggregated.withBooks(latestBooks)
                previousKey = key
                previousInsights = aggregated
                result
            }
        .flowOn(Dispatchers.Default)
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
    val completedThrough = today.minusDays(1)
    if (completedThrough < range.start) return null
    val previousThrough = when (selection.scope) {
        StatisticsScope.YEAR -> completedThrough.minusYears(1)
        else -> minOf(previous.endInclusive, previous.start.plusDays(ChronoUnit.DAYS.between(range.start, completedThrough)))
    }
    return StatisticsPeriod(range.start, completedThrough) to StatisticsPeriod(previous.start, previousThrough)
}

internal fun annualBooksByMonth(books: List<InsightsBook>): Map<Int, List<InsightsBook>> = books
    .sortedWith(compareBy<InsightsBook> { readingLocalDate(it.activity.firstDate) }.thenBy { it.book.title }.thenBy { it.book.id })
    .groupBy { readingLocalDate(it.activity.firstDate).monthValue }.toSortedMap()
