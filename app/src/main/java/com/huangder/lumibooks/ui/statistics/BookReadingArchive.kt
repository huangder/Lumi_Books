package com.huangder.lumibooks.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.components.activityDurationText
import com.huangder.lumibooks.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

internal data class BookArchiveState(val daily: Map<LocalDate, ActivityDuration> = emptyMap(), val annotations: Map<LocalDate, Int> = emptyMap())
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class BookArchiveViewModel @Inject constructor(private val repository: ReadingRepository) : ViewModel() {
    private val book = MutableStateFlow<String?>(null)
    val state = book.filterNotNull().flatMapLatest { id ->
        combine(repository.getBookDailyActivity(id), repository.getNotesByBookId(id), repository.getBookmarksByBookId(id)) { days, notes, bookmarks ->
            val zone = ZoneId.systemDefault()
            val manual = notes.filterNot { it.isInkStroke || (it.isGeneratedByHighlightRule && !it.isNoteEntry) }
            BookArchiveState(days.associate { readingLocalDate(it.date) to ActivityDuration(it.readingDuration, it.listeningDuration) },
                (manual.map { it.createdAt } + bookmarks.map { it.createdAt }).groupingBy {
                    java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }.eachCount())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookArchiveState())
    fun load(id: String) { book.value = id }
}

@Composable internal fun BookReadingArchive(bookId: String) {
    val model: BookArchiveViewModel = hiltViewModel(key = "book-archive-$bookId")
    LaunchedEffect(bookId) { model.load(bookId) }
    val state by model.state.collectAsState()
    var month by remember(bookId) { mutableStateOf(YearMonth.now()) }
    var located by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(state.daily) {
        if (!located && state.daily.isNotEmpty()) {
            month = YearMonth.from(state.daily.keys.maxOrNull()!!)
            located = true
        }
    }
    var list by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
        InsightsCard(stringResource(R.string.insights_book_archive)) {
            val first = state.daily.keys.minOrNull()
            val last = state.daily.keys.maxOrNull()
            InsightLabel(stringResource(R.string.insights_first_read, first?.toString() ?: "—"))
            InsightLabel(stringResource(R.string.insights_last_read, last?.toString() ?: "—"))
            state.daily.entries.maxByOrNull { it.value.totalDuration }?.let {
                InsightLabel(stringResource(R.string.insights_best_day, it.key.toString(), activityDurationText(it.value.totalDuration)))
            }
            InsightLabel(stringResource(R.string.insights_records_hint))
            TextButton(onClick = { BookShareActivity.start(context, bookId) }) {
                Text(stringResource(R.string.insights_share_card), color = AppColors.Accent)
            }
        }
        InsightsCard(stringResource(R.string.insights_book_records)) {
            PeriodNavigation(month.toString(), month < YearMonth.now(), { month = month.minusMonths(1) },
                { if (month < YearMonth.now()) month = month.plusMonths(1) })
            Row {
                TextButton(onClick = { list = false }) { Text(stringResource(R.string.insights_calendar), color = if (!list) AppColors.Accent else AppColors.TextSecondary) }
                TextButton(onClick = { list = true }) { Text(stringResource(R.string.insights_day_list), color = if (list) AppColors.Accent else AppColors.TextSecondary) }
            }
            if (!list) ActivityCalendar(StatisticsPeriod(month.atDay(1), month.atEndOfMonth()), state.daily) { selected = it }
            else {
                val dates = (state.daily.keys + state.annotations.keys).filter { YearMonth.from(it) == month }.sortedDescending()
                if (dates.isEmpty()) InsightLabel(stringResource(R.string.no_reading_records))
                dates.forEach { day ->
                    TextButton(onClick = { selected = day }) {
                        Column {
                            Text(day.toString(), color = AppColors.TextPrimary)
                            val activity = state.daily[day] ?: ActivityDuration()
                            InsightLabel(stringResource(R.string.activity_duration_breakdown, activityDurationText(activity.readingDuration), activityDurationText(activity.listeningDuration)))
                            InsightLabel(stringResource(R.string.insights_marks_count, state.annotations[day] ?: 0))
                        }
                    }
                }
            }
        }
    }
    selected?.let { ReadingDaySheet(it, bookId) { selected = null } }
}
