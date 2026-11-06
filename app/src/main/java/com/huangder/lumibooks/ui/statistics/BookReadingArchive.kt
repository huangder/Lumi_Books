package com.huangder.lumibooks.ui.statistics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.components.DisclosureChevron
import com.huangder.lumibooks.ui.components.activityDurationText
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookArchiveState())
    fun load(id: String) { book.value = id }
}

@Composable internal fun BookReadingArchive(bookId: String, model: BookArchiveViewModel = hiltViewModel(key = "book-archive-$bookId")) {
    LaunchedEffect(bookId) { model.load(bookId) }
    val state by model.state.collectAsStateWithLifecycle()
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
    val motion = insightsMotion()
    ProvideInsightsMotion("archive-$bookId") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InsightsCard(stringResource(R.string.insights_book_archive), icon = AppIcons.Archive, tint = InsightsHue.Indigo,
            action = { InsightsTextAction(stringResource(R.string.insights_share_card)) { BookShareActivity.start(context, bookId) } }) {
            val first = state.daily.keys.minOrNull()
            val last = state.daily.keys.maxOrNull()
            val best = state.daily.entries.filter { it.value.totalDuration > 0 }.maxByOrNull { it.value.totalDuration }
            val total = state.daily.values.sumOf { it.totalDuration }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InsightLabel(stringResource(R.string.statistics_reading_time))
                Text(styledFigure(insightsDuration(total), 32.sp, 15.sp), maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArchiveFact(stringResource(R.string.insights_first_read_label), first?.let { shortDate(it) } ?: "—", Modifier.weight(1f))
                ArchiveFact(stringResource(R.string.insights_last_read_label), last?.let { shortDate(it) } ?: "—", Modifier.weight(1f))
                ArchiveFact(stringResource(R.string.insights_best_day_label), best?.let { insightsDuration(it.value.totalDuration) } ?: "—",
                    Modifier.weight(1f), best?.let { shortDate(it.key) })
            }
            InsightLabel(stringResource(R.string.insights_records_hint))
        }
        InsightsCard(stringResource(R.string.insights_book_records), icon = AppIcons.ChartBar, tint = InsightsHue.Green) {
            var direction by remember { mutableIntStateOf(0) }
            PeriodNavigation(insightsDate(month.atDay(1), "yMMMM"), month < YearMonth.now(),
                { direction = -1; month = month.minusMonths(1) },
                { direction = 1; if (month < YearMonth.now()) month = month.plusMonths(1) }, glass = false)
            InsightSegmented(listOf(stringResource(R.string.insights_calendar), stringResource(R.string.insights_day_list)),
                if (list) 1 else 0, { list = it == 1 }, glass = false)
            val body: @Composable (Pair<YearMonth, Boolean>) -> Unit = { (shownMonth, shownList) ->
                if (!shownList) Box(Modifier.fillMaxWidth()) {
                    ActivityCalendar(StatisticsPeriod(shownMonth.atDay(1), shownMonth.atEndOfMonth()), state.daily,
                        state.annotations, firstDayOfWeek = DayOfWeek.MONDAY) { selected = it }
                } else ArchiveDayList(shownMonth, state) { selected = it }
            }
            if (!motion) body(month to list)
            else AnimatedContent(month to list, transitionSpec = {
                val d = if (initialState.second != targetState.second) (if (targetState.second) 1 else -1) else if (direction == 0) 1 else direction
                (slideInHorizontally(VisualMotion.spring(dampingRatio = .86f, stiffness = 340f)) { it / 4 * d } + fadeIn(VisualMotion.tween(220)))
                    .togetherWith(slideOutHorizontally(VisualMotion.tween(200)) { -it / 4 * d } + fadeOut(VisualMotion.tween(150)))
            }, label = "archiveMonth") { body(it) }
        }
    } }
    selected?.let { ReadingDaySheet(it, bookId) { selected = null } }
}

@Composable private fun ArchiveFact(label: String, value: String, modifier: Modifier, caption: String? = null) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(styledFigure(value, 16.sp, 12.sp, unitColor = AppColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) Text(caption, color = AppColors.TextSecondary, fontSize = AppType.Caption2, maxLines = 1)
    }
}

@Composable private fun ArchiveDayList(month: YearMonth, state: BookArchiveState, onDay: (LocalDate) -> Unit) {
    val dates = (state.daily.keys + state.annotations.keys).filter { YearMonth.from(it) == month }.sortedDescending()
    Column {
        if (dates.isEmpty()) InsightLabel(stringResource(R.string.no_reading_records), Modifier.padding(vertical = 12.dp))
        dates.forEachIndexed { index, day ->
            if (index > 0) InsightsDivider(0.dp)
            val interaction = remember(day) { MutableInteractionSource() }
            val activity = state.daily[day] ?: ActivityDuration()
            Row(Modifier.fillMaxWidth().lumiPressable(interaction, pressedScale = .98f)
                .clickable(interaction, indication = null, role = Role.Button) { onDay(day) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(dayTitle(day), color = AppColors.TextPrimary, fontSize = AppType.Subhead, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.activity_duration_breakdown, insightsDuration(activity.readingDuration),
                        insightsDuration(activity.listeningDuration)), color = AppColors.TextSecondary, fontSize = AppType.Caption)
                }
                val marks = state.annotations[day] ?: 0
                if (marks > 0) { InsightsPill(marks.toString(), InsightsHue.Yellow, icon = AppIcons.PencilSimple); Spacer(Modifier.width(8.dp)) }
                Text(styledFigure(insightsDuration(activity.totalDuration), 15.sp, 11.sp), maxLines = 1)
                Spacer(Modifier.width(4.dp))
                DisclosureChevron()
            }
        }
    }
}
