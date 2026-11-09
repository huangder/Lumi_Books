package com.huangder.lumibooks.ui.statistics
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ActivityDuration
import com.huangder.lumibooks.edition.EditionReadingJournalCard
import com.huangder.lumibooks.edition.EditionReadingReviewEntry
import com.huangder.lumibooks.edition.editionHasReadingJournal
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.bookshelf.AnnotationFeedActivity
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.statistics.report.*
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.time.LocalDate

internal fun StatisticsSelection.reportPeriod(today: LocalDate): ReadingReportPeriod = when (scope) {
    StatisticsScope.ALL -> ReadingReportPeriod(ReadingReportKind.MONTH, today.year, today.monthValue - 1)
    StatisticsScope.WEEK -> range(today).start.let { ReadingReportPeriod(ReadingReportKind.WEEK, it.year, it.monthValue - 1, it.dayOfMonth) }
    StatisticsScope.MONTH -> ReadingReportPeriod(ReadingReportKind.MONTH, year, month0)
    StatisticsScope.YEAR -> ReadingReportPeriod(ReadingReportKind.YEAR, year)
}

/** Monthly totals for a year, keyed by the first day of each month. */
internal fun monthlyTotals(year: Int, daily: Map<LocalDate, ActivityDuration>): Pair<List<LocalDate>, Map<LocalDate, ActivityDuration>> {
    val months = (1..12).map { LocalDate.of(year, it, 1) }
    return months to months.associateWith { month ->
        val values = daily.filterKeys { it.year == year && it.monthValue == month.monthValue }.values
        ActivityDuration(values.sumOf { it.readingDuration }, values.sumOf { it.listeningDuration })
    }
}

/** Subtitle under the period title: "Through Nov 4" while the period is still running. */
@Composable internal fun periodSubtitle(period: StatisticsPeriod, today: LocalDate): String? =
    if (today in period.start..period.endInclusive)
        stringResource(R.string.insights_through, shortDate(today, today)) else null

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun StatisticsScreen(
    playEntranceAnimation: Boolean = false,
    onMessage: (String) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigation = LocalMainNavigationInsets.current
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.error) { state.error?.let(onMessage) }
    val today = LocalDate.now()
    // Keep the previous figures on screen while a new period loads instead of flashing a spinner.
    // Figures switch directly to the loaded values; bars retain their transition.
    val lastLoaded = remember { arrayOfNulls<StatisticsUiState>(1) }
    if (state.insights != null) lastLoaded[0] = state
    val shown = if (state.insights != null) state else lastLoaded[0].takeIf { state.error == null }
    val data = shown?.insights
    val stale by animateFloatAsState(if (shown != null && shown !== state) .55f else 1f,
        VisualMotion.tween(180), label = "statisticsStale")
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val controlsBackdrop = rememberLayerBackdrop()
    ProvideInsightsMotion { Box(Modifier.fillMaxSize().background(AppColors.PageBg)) {
        // Navigation captures the whole page. Controls must sample this sibling
        // background instead, otherwise their RenderNodes reference themselves.
        // The atmosphere glow is part of this background sibling: glass tabs may refract it, and it is
        // never inside a control's own source layer.
        Box(Modifier.fillMaxSize()
            .then(if (isLiquidGlass) Modifier.layerBackdrop(controlsBackdrop) else Modifier)
            .background(AppColors.PageBg)) {
            val accent = AppColors.Accent
            AmbientGlow(remember(accent) { listOf(accent, InsightsHue.Indigo, InsightsHue.Teal) }, intensity = .8f,
                scrollOffset = { if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat() })
        }
        ProvideLiquidGlassBackdrop(controlsBackdrop.takeIf { isLiquidGlass }) {
            OverscrollBounce(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.padding(start = navigation.start).widthIn(max = 840.dp).fillMaxWidth().align(Alignment.TopCenter),
                    state = listState,
                    contentPadding = PaddingValues(start = AppSpace.lg, end = AppSpace.lg,
                        bottom = if (navigation.bottomBar) 140.dp else 64.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item("header") {
                        PageEntranceItem(play = playEntranceAnimation, index = 0) {
                            // Shared main-tab header: titles on Home, Library and Statistics sit at one height.
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                // If title + labelled review button don't fit (large fonts), the button turns icon-only.
                                val title = stringResource(R.string.stats_title)
                                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val titleFont = resolveAppFontFamily(KaiTi)
                                val titleWidth = with(density) { measurer.measure(title, androidx.compose.ui.text.TextStyle(
                                    fontSize = AppType.Display, fontWeight = FontWeight.Bold, fontFamily = titleFont)).size.width.toDp() }
                                val buttonWidth = with(density) { measurer.measure(stringResource(R.string.insights_review),
                                    androidx.compose.ui.text.TextStyle(fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)).size.width.toDp() } + 64.dp
                                val compact = titleWidth + buttonWidth + 16.dp > maxWidth
                                MainTabHeader(eyebrow = insightsDate(today, "MMMMdEEEE"), horizontalPadding = 0.dp,
                                    modifier = Modifier.padding(bottom = AppSpace.sm),
                                    trailing = { EditionReadingReviewEntry(state.selection.reportPeriod(today), compact) }) {
                                    MainTabTitle(title)
                                }
                            }
                        }
                    }
                    item("tabs") { PageEntranceItem(play = playEntranceAnimation, index = 1) { ScopeSelector(state.selection.scope, viewModel::selectScope) } }
                    if (state.selection.scope != StatisticsScope.ALL) item("period") {
                        val range = state.selection.range(today)
                        PeriodNavigation(periodLabel(range), state.selection.shift(1, today) != state.selection,
                            { viewModel.shift(-1) }, { viewModel.shift(1) }, subtitle = periodSubtitle(range, today))
                    }
                    if (shown == null || data == null) item("placeholder") {
                        if (state.error == null) InsightsSkeleton()
                        else EmptyState(stringResource(R.string.insights_load_failed), Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            icon = AppIcons.ChartBar)
                    } else {
                        val scope = shown.selection.scope
                        val dim = Modifier.graphicsLayer { alpha = stale }
                        item("summary") {
                            InsightsAppear(0, dim) {
                                InsightsSummary(data, shown.comparison, allTime = scope == StatisticsScope.ALL) {
                                    when (scope) {
                                        StatisticsScope.ALL -> {
                                            InsightLabel(stringResource(R.string.insights_recent_thirty))
                                            ActivityBars(StatisticsPeriod(today.minusDays(29), today).days, data.daily) { selectedDay = it }
                                        }
                                        StatisticsScope.YEAR -> {
                                            InsightLabel(stringResource(R.string.insights_monthly_activity))
                                            val (months, totals) = monthlyTotals(data.period.start.year, data.daily)
                                            ActivityBars(months, totals, monthly = true)
                                        }
                                        else -> ActivityBars(shown.selection.range(today).days, data.daily) { selectedDay = it }
                                    }
                                }
                            }
                        }
                        item("metrics") { InsightsAppear(1, dim) { InsightsMetrics(data, scope == StatisticsScope.ALL) } }
                        if (editionHasReadingJournal) item("journal") { InsightsAppear(2, dim) { EditionReadingJournalCard() } }
                        if (scope == StatisticsScope.MONTH || scope == StatisticsScope.YEAR) item("calendar") {
                            InsightsAppear(2, dim) {
                                InsightsCard(stringResource(R.string.insights_distribution), icon = AppIcons.ChartBar, tint = InsightsHue.Green) {
                                    ActivityCalendar(shown.selection.range(today), data.daily) { selectedDay = it }
                                }
                            }
                        }
                        item("habits") { InsightsAppear(3, dim) { ActivityBreakdown(data) } }
                        item("annotations") { InsightsAppear(4, dim) { AnnotationInsights(data.annotations) { AnnotationFeedActivity.start(context) } } }
                        item("books") { InsightsAppear(5, dim) { TopBooksCard(data, 5) } }
                        item("streak") { InsightsAppear(6, dim) { ReadingHighlights(data, scope == StatisticsScope.ALL) } }
                    }
                }
            }
            selectedDay?.let { ReadingDaySheet(it, onDismiss = { selectedDay = null }) }
        }
    } }
}
