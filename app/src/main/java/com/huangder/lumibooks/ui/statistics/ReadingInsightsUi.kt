package com.huangder.lumibooks.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.*
import com.huangder.lumibooks.ui.bookshelf.BookDetailsActivity
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@Composable internal fun InsightsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    LiquidGlassSurface(AppRoundedCornerShape(AppRadius.lg), AppColors.CardBg, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpace.md), verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
            Text(title, color = AppColors.TextPrimary, fontSize = AppType.Section, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable internal fun InsightLabel(text: String) {
    Text(text, color = AppColors.TextSecondary, fontSize = AppType.Caption)
}

@Composable internal fun InsightMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = AppColors.TextPrimary, fontSize = AppType.Section, fontWeight = FontWeight.SemiBold)
        InsightLabel(label)
    }
}

@Composable internal fun ScopeSelector(scope: StatisticsScope, onSelect: (StatisticsScope) -> Unit, includeAll: Boolean = true) {
    val scopes = StatisticsScope.entries.filter { includeAll || it != StatisticsScope.ALL }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        scopes.forEach { item ->
            val label = stringResource(when (item) {
                StatisticsScope.ALL -> R.string.insights_all
                StatisticsScope.WEEK -> R.string.tab_week
                StatisticsScope.MONTH -> R.string.tab_month
                StatisticsScope.YEAR -> R.string.tab_year
            })
            TextButton(onClick = { onSelect(item) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .clip(AppRoundedCornerShape(AppRadius.sm))
                .background(if (scope == item) AppColors.Accent.copy(alpha = .12f) else AppColors.BgGray)) {
                Text(label, color = if (scope == item) AppColors.Accent else AppColors.TextSecondary)
            }
        }
    }
}

internal fun periodLabel(period: StatisticsPeriod): String =
    if (period.start.year == period.endInclusive.year && period.start.dayOfYear == 1 && period.endInclusive.dayOfYear >= 365)
        period.start.year.toString()
    else if (period.start.dayOfMonth == 1 && period.endInclusive == YearMonth.from(period.start).atEndOfMonth())
        period.start.format(DateTimeFormatter.ofPattern("yyyy MMMM", Locale.getDefault()))
    else period.start.toString() + " – " + period.endInclusive.toString()

@Composable internal fun PeriodNavigation(label: String, canNext: Boolean, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = previous) { Text("‹", color = AppColors.TextPrimary) }
        Text(label, color = AppColors.TextPrimary, fontSize = AppType.Body, modifier = Modifier.weight(1f))
        TextButton(onClick = next, enabled = canNext) { Text("›", color = if (canNext) AppColors.TextPrimary else AppColors.TextSecondary) }
    }
}

@Composable internal fun InsightsSummary(data: ReadingInsights) {
    InsightsCard(stringResource(R.string.insights_overview)) {
        Row(Modifier.fillMaxWidth()) {
            InsightMetric(activityDurationText(data.activity.totalDuration), stringResource(R.string.statistics_reading_time), Modifier.weight(1f))
            InsightMetric(data.books.size.toString(), stringResource(R.string.insights_books), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            InsightMetric(data.readingDays.toString(), stringResource(R.string.statistics_active_days), Modifier.weight(1f))
            InsightMetric(activityDurationText(data.dailyAverage), stringResource(R.string.insights_daily_average), Modifier.weight(1f))
        }
    }
}

@Composable internal fun ActivityBreakdown(data: ReadingInsights) {
    InsightsCard(stringResource(R.string.insights_habits)) {
        Text(stringResource(R.string.activity_duration_breakdown, activityDurationText(data.activity.readingDuration),
            activityDurationText(data.activity.listeningDuration)), color = AppColors.TextPrimary, fontSize = AppType.BodySmall)
        if (data.activity.totalDuration > 0) LinearProgressIndicator(
            progress = { data.activity.readingDuration.toFloat() / data.activity.totalDuration },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(AppRoundedCornerShape(4.dp)),
            color = AppColors.Accent, trackColor = AppColors.Accent.copy(alpha = .20f))
        Row(Modifier.fillMaxWidth()) {
            InsightMetric(activityDurationText(data.averageForWeekend(false)), stringResource(R.string.insights_weekday), Modifier.weight(1f))
            InsightMetric(activityDurationText(data.averageForWeekend(true)), stringResource(R.string.insights_weekend), Modifier.weight(1f))
        }
    }
}

@Composable internal fun ReadingHighlights(data: ReadingInsights, showCurrent: Boolean = false) {
    InsightsCard(stringResource(R.string.insights_highlights)) {
        if (showCurrent) InsightLabel(stringResource(R.string.insights_current_streak, data.currentStreak))
        InsightLabel(stringResource(R.string.insights_longest_streak, data.longestStreak))
        data.bestDay?.let { InsightLabel(stringResource(R.string.insights_best_day, it.key.toString(), activityDurationText(it.value.totalDuration))) }
        InsightLabel(stringResource(R.string.insights_goal_days, data.goalDays))
        InsightLabel(if (data.goalMinutes > 0) stringResource(R.string.statistics_streak_goal_hint, data.goalMinutes)
            else stringResource(R.string.statistics_streak_any_hint))
    }
}

@Composable internal fun AnnotationInsights(summary: AnnotationPeriodSummary, onOpen: (() -> Unit)? = null) {
    InsightsCard(stringResource(R.string.statistics_annotations_title)) {
        val values = listOf(summary.highlights to R.string.tab_highlights, summary.underlines to R.string.tab_underline,
            summary.notes to R.string.tab_notes, summary.bookmarks to R.string.tab_bookmarks)
        values.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) { row.forEach { (count, label) ->
                InsightMetric(count.toString(), stringResource(label), Modifier.weight(1f))
            } }
        }
        if (summary.topTags.isNotEmpty()) InsightLabel(summary.topTags.joinToString(" · ") { "${it.first} ${it.second}" })
        if (onOpen != null) TextButton(onClick = onOpen) { Text(stringResource(R.string.statistics_annotations_view_all), color = AppColors.Accent) }
    }
}

@Composable internal fun InsightsBookRow(item: InsightsBook, totalDuration: Long, trailing: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { BookDetailsActivity.start(context, item.book.id) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(item.book.coverPath, item.book.title, Modifier.size(46.dp, 66.dp).clip(AppRoundedCornerShape(5.dp)), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f).padding(horizontal = AppSpace.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.book.title, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = AppType.BodySmall)
            InsightLabel(activityDurationText(item.activity.totalDuration))
            if (totalDuration > 0) {
                val percent = (item.activity.totalDuration * 100L / totalDuration).toInt()
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Accent, trackColor = AppColors.Divider)
                InsightLabel("$percent%")
            }
        }
        trailing?.invoke()
    }
}

@Composable internal fun ActivityCalendar(period: StatisticsPeriod, daily: Map<LocalDate, ActivityDuration>, onDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val maximum = daily.values.maxOfOrNull { it.totalDuration }?.coerceAtLeast(1) ?: 1L
    val leading = period.start.dayOfWeek.value % 7
    val days = List<LocalDate?>(leading) { null } + period.days
    if (period.days.size > 62) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row { (1..12).forEach { month ->
                Text(java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = AppColors.TextSecondary,
                    fontSize = AppType.Caption, modifier = Modifier.width(70.dp))
            } }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                days.chunked(7).forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val duration = daily[day]?.totalDuration ?: 0L
                            Box(Modifier.size(13.dp).clip(AppRoundedCornerShape(2.dp))
                                .background(if (duration > 0) AppColors.Accent.copy(alpha = .25f + .75f * duration.toFloat() / maximum) else AppColors.BgGray)
                                .then(if (day != null && day <= today) Modifier.semantics { contentDescription = day.toString() }.clickable { onDay(day) } else Modifier))
                        }
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) { (0..6).forEach { day ->
                Text(java.time.DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = AppColors.TextSecondary, modifier = Modifier.weight(1f), fontSize = AppType.Caption)
            } }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (week + List(7 - week.size) { null }).forEach { day ->
                        val duration = daily[day]?.totalDuration ?: 0L
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(AppRoundedCornerShape(5.dp))
                            .background(if (duration > 0) AppColors.Accent.copy(alpha = .10f + .25f * duration.toFloat() / maximum) else AppColors.BgGray)
                            .then(if (day != null && day <= today) Modifier.semantics { contentDescription = day.toString() }.clickable { onDay(day) } else Modifier)
                            .padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day?.dayOfMonth?.toString() ?: "", color = AppColors.TextPrimary, fontSize = AppType.Caption)
                            if (duration > 0) Text((duration / 60_000).toString(), color = AppColors.Accent, fontSize = AppType.Caption)
                        }
                    }
                }
            }
            InsightLabel(stringResource(R.string.insights_calendar_hint))
        }
    }
}

@Composable internal fun ActivityBars(days: List<LocalDate>, daily: Map<LocalDate, ActivityDuration>, monthly: Boolean = false, onDay: (LocalDate) -> Unit) {
    val maximum = days.maxOfOrNull { daily[it]?.totalDuration ?: 0 }?.coerceAtLeast(1) ?: 1L
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val duration = daily[day]?.totalDuration ?: 0L
            Column(Modifier.width(40.dp).semantics { contentDescription = day.toString() }
                .clickable(enabled = day <= LocalDate.now()) { onDay(day) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                InsightLabel((duration / 60_000).toString())
                Box(Modifier.height(100.dp).fillMaxWidth(), Alignment.BottomCenter) {
                    Box(Modifier.width(22.dp).height((duration.toFloat() / maximum * 100).coerceAtLeast(2f).dp)
                        .clip(AppRoundedCornerShape(4.dp)).background(if (duration > 0) AppColors.Accent else AppColors.Divider))
                }
                InsightLabel(if (monthly) day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else day.dayOfMonth.toString())
            }
        }
    }
    InsightLabel(stringResource(R.string.insights_calendar_hint))
}

internal data class DayBook(val book: Book, val activity: ActivityDuration, val annotations: AnnotationPeriodSummary)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ReadingDayViewModel @Inject constructor(private val repository: ReadingRepository, private val books: BookRepository) : ViewModel() {
    private val query = MutableStateFlow<Pair<LocalDate, String?>?>(null)
    val entries = query.filterNotNull().flatMapLatest { (date, bookId) ->
        val zone = ZoneId.systemDefault()
        val period = StatisticsPeriod(date, date)
        if (bookId != null) return@flatMapLatest combine(repository.getBookDailyActivity(bookId), books.getAllBooks(),
            repository.getNotesByBookId(bookId), repository.getBookmarksByBookId(bookId)) { days, library, notes, bookmarks ->
            val book = library.firstOrNull { it.id == bookId }
            val day = days.firstOrNull { readingLocalDate(it.date) == date }
            val dayNotes = notes.filter { java.time.Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == date }
            val dayBookmarks = bookmarks.filter { java.time.Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == date }
            if (book == null || (day == null && dayNotes.isEmpty() && dayBookmarks.isEmpty())) emptyList()
            else listOf(DayBook(book, ActivityDuration(day?.readingDuration ?: 0, day?.listeningDuration ?: 0), summarizeAnnotations(dayNotes, dayBookmarks)))
        }.onStart { emit(emptyList()) }
        combine(repository.getRecordsByDate(date.readingDate()), books.getAllBooks(),
            repository.observeTextNotesCreatedBetween(period.startMillis(zone), period.endMillis(zone)),
            repository.observeBookmarksCreatedBetween(period.startMillis(zone), period.endMillis(zone))) { records, library, notes, bookmarks ->
            val totals = records.filter { bookId == null || it.bookId == bookId }.groupBy { it.bookId }
            library.filter { !it.isHidden && (bookId == null || it.id == bookId) &&
                (it.id in totals || notes.any { note -> note.bookId == it.id } || bookmarks.any { mark -> mark.bookId == it.id }) }
                .map { book -> DayBook(book, ActivityDuration(totals[book.id].orEmpty().sumOf { it.duration },
                    totals[book.id].orEmpty().sumOf { it.listeningDuration }),
                    summarizeAnnotations(notes.filter { it.bookId == book.id }, bookmarks.filter { it.bookId == book.id })) }
                .sortedByDescending { it.activity.totalDuration }
        }.onStart { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun load(date: LocalDate, bookId: String?) { query.value = date to bookId }
}

@Composable internal fun ReadingDaySheet(date: LocalDate, bookId: String? = null, onDismiss: () -> Unit) {
    val model: ReadingDayViewModel = hiltViewModel(key = "reading-day-${bookId.orEmpty()}")
    LaunchedEffect(date, bookId) { model.load(date, bookId) }
    val entries by model.entries.collectAsState()
    val context = LocalContext.current
    AppBottomSheet(onDismiss) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(AppSpace.md), verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
            item { AppSheetHeader(date.toString(), onDismiss) }
            if (entries.isEmpty()) item { InsightLabel(stringResource(R.string.no_reading_records)) }
            items(entries.size) { index ->
                val entry = entries[index]
                InsightsCard(entry.book.title) {
                    InsightLabel(stringResource(R.string.activity_duration_breakdown,
                        activityDurationText(entry.activity.readingDuration), activityDurationText(entry.activity.listeningDuration)))
                    InsightLabel(stringResource(R.string.insights_marks_count, entry.annotations.let { it.highlights + it.underlines + it.notes + it.bookmarks }))
                    Row {
                        TextButton(onClick = { BookDetailsActivity.start(context, entry.book.id) }) { Text(stringResource(R.string.book_details), color = AppColors.Accent) }
                        TextButton(onClick = { BookShareActivity.start(context, entry.book.id, date) }, enabled = entry.activity.totalDuration > 0) {
                            Text(stringResource(R.string.insights_check_in), color = AppColors.Accent)
                        }
                    }
                }
            }
        }
    }
}
