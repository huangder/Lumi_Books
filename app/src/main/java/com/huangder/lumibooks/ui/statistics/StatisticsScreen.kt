package com.huangder.lumibooks.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.edition.EditionReadingReviewEntry
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.bookshelf.AnnotationFeedActivity
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.statistics.report.*
import com.huangder.lumibooks.ui.theme.*
import java.time.LocalDate
import java.util.Locale

internal fun StatisticsSelection.reportPeriod(today: LocalDate): ReadingReportPeriod = when (scope) {
    StatisticsScope.ALL -> ReadingReportPeriod(ReadingReportKind.MONTH, today.year, today.monthValue - 1)
    StatisticsScope.WEEK -> range(today).start.let { ReadingReportPeriod(ReadingReportKind.WEEK, it.year, it.monthValue - 1, it.dayOfMonth) }
    StatisticsScope.MONTH -> ReadingReportPeriod(ReadingReportKind.MONTH, year, month0)
    StatisticsScope.YEAR -> ReadingReportPeriod(ReadingReportKind.YEAR, year)
}

@Composable
internal fun StatisticsScreen(
    playEntranceAnimation: Boolean = false,
    onMessage: (String) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val navigation = LocalMainNavigationInsets.current
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.error) { state.error?.let(onMessage) }
    val today = LocalDate.now()
    val data = state.insights
    OverscrollBounce(Modifier.fillMaxSize().background(AppColors.PageBg)) {
        LazyColumn(Modifier.fillMaxWidth().widthIn(max = 840.dp).align(Alignment.TopCenter).padding(start = navigation.start),
            contentPadding = PaddingValues(start = AppSpace.lg, end = AppSpace.lg,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppSpace.md,
                bottom = if (navigation.bottomBar) 140.dp else 64.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
            item("header") {
                PageEntranceItem(play = playEntranceAnimation, index = 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.stats_title), modifier = Modifier.weight(1f), fontSize = AppType.Display,
                            color = AppColors.TextPrimary, fontFamily = resolveAppFontFamily(KaiTi), fontWeight = FontWeight.Bold)
                        EditionReadingReviewEntry(state.selection.reportPeriod(today))
                    }
                }
            }
            item("tabs") { ScopeSelector(state.selection.scope, viewModel::selectScope) }
            if (state.selection.scope != StatisticsScope.ALL) item("period") {
                PeriodNavigation(periodLabel(state.selection.range(today)), state.selection.shift(1, today) != state.selection,
                    { viewModel.shift(-1) }, { viewModel.shift(1) })
            }
            if (data == null) item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                if (state.error == null) CircularProgressIndicator(color = AppColors.Accent)
                else InsightLabel(stringResource(R.string.insights_load_failed))
            } } else {
                item("summary") { InsightsSummary(data) }
                item("calendar") {
                    InsightsCard(stringResource(if (state.selection.scope == StatisticsScope.ALL) R.string.insights_recent_thirty else R.string.insights_distribution)) {
                        val period = if (state.selection.scope == StatisticsScope.ALL) StatisticsPeriod(today.minusDays(29), today) else state.selection.range(today)
                        if (period.days.size <= 31) ActivityBars(period.days, data.daily) { selectedDay = it }
                        if (period.days.size > 31 || state.selection.scope == StatisticsScope.MONTH) ActivityCalendar(period, data.daily) { selectedDay = it }
                    }
                }
                state.comparison?.let { comparison ->
                    item("comparison") {
                        InsightsCard(stringResource(R.string.insights_comparison)) {
                            InsightLabel(periodLabel(comparison.current) + " / " + periodLabel(comparison.previous))
                            val difference = comparison.currentMs - comparison.previousMs
                            val value = when {
                                comparison.previousMs == 0L && comparison.currentMs > 0 -> stringResource(R.string.insights_new_reading)
                                comparison.previousMs == 0L -> stringResource(R.string.no_reading_records)
                                else -> String.format(Locale.getDefault(), "%+.1f%%", difference * 100.0 / comparison.previousMs)
                            }
                            Text(value, color = AppColors.Accent, fontSize = AppType.Section, fontWeight = FontWeight.SemiBold)
                            InsightLabel(stringResource(if (difference >= 0) R.string.insights_more_time else R.string.insights_less_time,
                                activityDurationText(kotlin.math.abs(difference))))
                        }
                    }
                }
                item("habits") { ActivityBreakdown(data) }
                item("annotations") { AnnotationInsights(data.annotations) { AnnotationFeedActivity.start(context) } }
                item("books") { InsightsCard(stringResource(R.string.most_read_books)) {
                    if (data.books.isEmpty()) InsightLabel(stringResource(R.string.no_reading_records))
                    data.books.take(5).forEach { InsightsBookRow(it, data.activity.totalDuration) }
                } }
                item("streak") { ReadingHighlights(data, state.selection.scope == StatisticsScope.ALL) }
            }
        }
    }
    selectedDay?.let { ReadingDaySheet(it, onDismiss = { selectedDay = null }) }
}
