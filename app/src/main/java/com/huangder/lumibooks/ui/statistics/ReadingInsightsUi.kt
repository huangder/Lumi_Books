package com.huangder.lumibooks.ui.statistics

import com.huangder.lumibooks.ui.icons.ControlIcon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.domain.repository.*
import com.huangder.lumibooks.ui.bookshelf.BookDetailsActivity
import com.huangder.lumibooks.ui.bookshare.BookShareActivity
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.animation.VisualMotion
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

// ---------------------------------------------------------------------------------------------
// Cards and labels
// ---------------------------------------------------------------------------------------------

/** Content card (Content surface role — never refracts the captured scroll layer it lives in). */
@Composable internal fun InsightsCard(
    title: String?,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = AppColors.Accent,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().insightsCardSurface().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) InsightsCardHeader(title, icon, tint, action)
        content()
    }
}

@Composable internal fun InsightLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = AppColors.TextSecondary, fontSize = AppType.Caption, modifier = modifier)
}

@Composable internal fun InsightMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(styledFigure(value, 22.sp, 12.sp), maxLines = 1)
        InsightLabel(label)
    }
}

// ---------------------------------------------------------------------------------------------
// Period selection
// ---------------------------------------------------------------------------------------------

/** [edgeBleed]: list padding the overflowing selector may scroll through to reach the screen edge. */
@Composable internal fun ScopeSelector(scope: StatisticsScope, onSelect: (StatisticsScope) -> Unit, includeAll: Boolean = true,
    edgeBleed: Dp = 0.dp) {
    val scopes = StatisticsScope.entries.filter { includeAll || it != StatisticsScope.ALL }
    val labels = scopes.map { item ->
        stringResource(when (item) {
            StatisticsScope.ALL -> R.string.insights_all
            StatisticsScope.WEEK -> R.string.tab_week
            StatisticsScope.MONTH -> R.string.tab_month
            StatisticsScope.YEAR -> R.string.tab_year
        })
    }
    val selectedIndex = scopes.indexOf(scope).coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = LocalDensity.current
        val labelWidth = labels.maxOf { label ->
            with(density) {
                textMeasurer.measure(label, androidx.compose.ui.text.TextStyle(fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false).size.width.toDp()
            }
        }
        // Each segment needs its label plus a small breathing margin; only overflow when it truly can't fit.
        val needed = (labelWidth + 12.dp) * scopes.size + 4.dp
        val select: (Int) -> Unit = { onSelect(scopes[it]) }
        // A scroll container clips its content (and the pill's shadow), so only use one when the
        // labels genuinely do not fit, e.g. very large fonts.
        if (needed <= maxWidth) InsightSegmented(labels, selectedIndex, select)
        else Row(Modifier.horizontalBleed(edgeBleed).horizontalScroll(rememberScrollState())
            .padding(horizontal = edgeBleed, vertical = 4.dp)) {
            // Scrolling: give every segment comfortable room so no label is ellipsized.
            InsightSegmented(labels, selectedIndex, select, Modifier.width((labelWidth + 28.dp) * scopes.size + 4.dp))
        }
    }
}

internal fun periodLabel(period: StatisticsPeriod): String {
    val today = LocalDate.now()
    return if (period.start.year == period.endInclusive.year && period.start.dayOfYear == 1 && period.endInclusive.dayOfYear >= 365)
        period.start.year.toString()
    else if (period.start.dayOfMonth == 1 && period.endInclusive == YearMonth.from(period.start).atEndOfMonth())
        insightsDate(period.start, "yMMMM")
    else if (period.start == period.endInclusive) shortDate(period.start, today)
    else {
        val sameYear = period.start.year == period.endInclusive.year && period.start.year == today.year
        if (sameYear) shortDate(period.start, today) + " – " + shortDate(period.endInclusive, today)
        else insightsDate(period.start, "yMMMd") + " – " + insightsDate(period.endInclusive, "yMMMd")
    }
}

@Composable internal fun PeriodNavigation(label: String, canNext: Boolean, previous: () -> Unit, next: () -> Unit,
    subtitle: String? = null, glass: Boolean = true) {
    InsightsPeriodHeader(label, subtitle, canPrevious = true, canNext = canNext,
        previousDescription = stringResource(R.string.insights_previous_period),
        nextDescription = stringResource(R.string.insights_next_period),
        previous = previous, next = next, glass = glass)
}

// ---------------------------------------------------------------------------------------------
// Hero, metrics and summaries
// ---------------------------------------------------------------------------------------------

/**
 * Hero card: the period's total time in large type, followed by the change versus the
 * previous period and an optional chart (bars grow in underneath).
 */
@Composable internal fun InsightsSummary(data: ReadingInsights, comparison: PeriodComparison? = null,
    allTime: Boolean = false, chart: (@Composable ColumnScope.() -> Unit)? = null) {
    InsightsCard(stringResource(R.string.insights_overview), icon = AppIcons.Timer, tint = AppColors.Accent) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InsightLabel(stringResource(R.string.statistics_reading_time))
            Text(styledFigure(insightsDuration(data.activity.totalDuration), 40.sp, 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (comparison != null) ComparisonRow(comparison)
        else if (allTime && data.daily.isNotEmpty()) InsightLabel(stringResource(R.string.insights_since, shortDate(data.period.start)))
        chart?.invoke(this)
    }
}

@Composable private fun ComparisonRow(comparison: PeriodComparison) {
    val difference = comparison.currentMs - comparison.previousMs
    val (text, color, icon) = when {
        comparison.previousMs == 0L && comparison.currentMs > 0 ->
            Triple(stringResource(R.string.insights_new_reading), InsightsHue.Green, AppIcons.Lightning)
        comparison.previousMs == 0L -> Triple("—", AppColors.TextSecondary, null)
        else -> {
            val percent = difference * 100.0 / comparison.previousMs
            Triple(String.format(Locale.getDefault(), "%+.0f%%", percent),
                if (difference >= 0) InsightsHue.Green else InsightsHue.Red,
                if (difference >= 0) AppIcons.CaretUp else AppIcons.CaretDown)
        }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        InsightsPill(text, color, icon = icon)
        Text(stringResource(R.string.insights_vs_previous, periodLabel(comparison.previous)) +
            if (comparison.previousMs > 0) " · " + stringResource(if (difference >= 0) R.string.insights_more_time else R.string.insights_less_time,
                insightsDuration(abs(difference))) else "",
            color = AppColors.TextSecondary, fontSize = AppType.Caption)
    }
}

/** 2×2 (4×1 on wide layouts) tile grid: books, active days, daily average, streak. */
@Composable internal fun InsightsMetrics(data: ReadingInsights, showCurrentStreak: Boolean) {
    val books = data.books.size
    val days = data.readingDays
    val average = data.dailyAverage
    val streak = if (showCurrentStreak) data.currentStreak else data.longestStreak
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { MetricTile(AppIcons.Books, InsightsHue.Orange, stringResource(R.string.insights_books),
            stringResource(R.string.insights_books_value, books), it) },
        { MetricTile(AppIcons.CheckCircle, InsightsHue.Green, stringResource(R.string.statistics_active_days),
            stringResource(R.string.insights_days_value, days), it) },
        { MetricTile(AppIcons.Timer, InsightsHue.Blue, stringResource(R.string.insights_daily_average), insightsDuration(average), it) },
        { MetricTile(AppIcons.Lightning, InsightsHue.Pink,
            stringResource(if (showCurrentStreak) R.string.statistics_streak_current else R.string.statistics_streak_longest),
            stringResource(R.string.insights_days_value, streak), it) }
    )
    MetricGrid(tiles)
}

@Composable internal fun ActivityBreakdown(data: ReadingInsights) {
    InsightsCard(stringResource(R.string.insights_habits), icon = AppIcons.Headphones, tint = InsightsHue.Indigo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReadListenRing(data.activity.readingDuration, data.activity.listeningDuration)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendValue(hue(AppColors.Accent), stringResource(R.string.insights_reading), insightsDuration(data.activity.readingDuration))
                LegendValue(listenColor(), stringResource(R.string.insights_listening), insightsDuration(data.activity.listeningDuration))
            }
        }
        InsightsDivider(0.dp)
        val weekday = data.averageForWeekend(false)
        val weekend = data.averageForWeekend(true)
        val max = maxOf(weekday, weekend, 1L).toFloat()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ComparisonBar(stringResource(R.string.insights_weekday), weekday, weekday / max, InsightsHue.Teal, "weekday")
            ComparisonBar(stringResource(R.string.insights_weekend), weekend, weekend / max, InsightsHue.Orange, "weekend")
        }
    }
}

@Composable private fun LegendValue(color: Color, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = AppColors.TextSecondary, fontSize = AppType.Footnote)
        }
        Text(styledFigure(value, 20.sp, 12.sp), maxLines = 1, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable private fun ComparisonBar(label: String, value: Long, fraction: Float, color: Color, key: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, color = AppColors.TextSecondary, fontSize = AppType.Footnote, modifier = Modifier.weight(1f))
            Text(styledFigure(insightsDuration(value), 15.sp, 11.sp), maxLines = 1)
        }
        InsightsProgress(fraction, color, height = 8.dp, key = key)
    }
}

@Composable internal fun ReadingHighlights(data: ReadingInsights, showCurrent: Boolean = false) {
    InsightsCard(stringResource(R.string.insights_highlights), icon = AppIcons.Lightning, tint = InsightsHue.Orange) {
        Column {
            if (showCurrent) {
                InsightsIconRow(AppIcons.Lightning, InsightsHue.Pink, stringResource(R.string.statistics_streak_current),
                    stringResource(R.string.insights_days_value, data.currentStreak))
                InsightsDivider()
            }
            InsightsIconRow(AppIcons.Pulse, InsightsHue.Purple, stringResource(R.string.statistics_streak_longest),
                stringResource(R.string.insights_days_value, data.longestStreak))
            data.bestDay?.let {
                InsightsDivider()
                InsightsIconRow(AppIcons.Sun, InsightsHue.Orange, stringResource(R.string.insights_best_day_label),
                    insightsDuration(it.value.totalDuration), subtitle = dayTitle(it.key))
            }
            InsightsDivider()
            InsightsIconRow(AppIcons.CheckCircle, InsightsHue.Green, stringResource(R.string.insights_goal_days_label),
                stringResource(R.string.insights_days_value, data.goalDays),
                subtitle = if (data.goalMinutes > 0) stringResource(R.string.statistics_streak_goal_hint, data.goalMinutes)
                    else stringResource(R.string.statistics_streak_any_hint))
        }
    }
}

@Composable internal fun AnnotationInsights(summary: AnnotationPeriodSummary, onOpen: (() -> Unit)? = null) {
    InsightsCard(stringResource(R.string.statistics_annotations_title), icon = AppIcons.PencilSimple, tint = InsightsHue.Yellow,
        action = onOpen?.let { open -> { InsightsTextAction(stringResource(R.string.statistics_annotations_view_all), open) } }) {
        val values = listOf(
            Triple(summary.highlights, R.string.tab_highlights, InsightsHue.Yellow),
            Triple(summary.underlines, R.string.tab_underline, InsightsHue.Blue),
            Triple(summary.notes, R.string.tab_notes, InsightsHue.Green),
            Triple(summary.bookmarks, R.string.tab_bookmarks, InsightsHue.Red))
        Row(Modifier.fillMaxWidth()) {
            values.forEach { (count, label, color) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(styledFigure(count.toString(), 24.sp), maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(hue(color)))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(label), color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 1)
                    }
                }
            }
        }
        if (summary.topTags.isNotEmpty()) {
            InsightsDivider(0.dp)
            InsightLabel(stringResource(R.string.insights_top_tags))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.topTags.forEach { (tag, count) -> InsightsPill("#$tag  $count", AppColors.Accent) }
            }
        }
    }
}

/** Ranked list of the most-read books in the period. */
@Composable internal fun TopBooksCard(data: ReadingInsights, limit: Int) {
    InsightsCard(stringResource(R.string.most_read_books), icon = AppIcons.Books, tint = InsightsHue.Indigo) {
        if (data.books.isEmpty()) InsightLabel(stringResource(R.string.no_reading_records))
        data.books.take(limit).forEachIndexed { index, item ->
            if (index > 0) InsightsDivider(68.dp)
            InsightsBookRow(item, data.activity.totalDuration, rank = index + 1)
        }
    }
}

@Composable internal fun InsightsBookRow(item: InsightsBook, totalDuration: Long, rank: Int? = null, trailing: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().lumiPressable(interaction, pressedScale = .98f, pressedAlpha = .9f)
        .clickable(interaction, indication = null) { BookDetailsActivity.start(context, item.book.id) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) Text(rank.toString(), color = if (rank == 1) hue(AppColors.Accent) else AppColors.TextSecondary,
            fontSize = AppType.Headline, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
        if (rank != null) Spacer(Modifier.width(6.dp))
        InsightsBookCover(item.book, Modifier.size(40.dp, 58.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.book.title, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontSize = AppType.Subhead, fontWeight = FontWeight.Medium)
            if (totalDuration > 0) {
                val fraction = item.activity.totalDuration.toFloat() / totalDuration
                InsightsProgress(fraction, AppColors.Accent, height = 4.dp, key = "book-${item.book.id}")
                Text(insightsDuration(item.activity.totalDuration) + " · " + "${(fraction * 100).toInt()}%",
                    color = AppColors.TextSecondary, fontSize = AppType.Caption)
            } else InsightLabel(insightsDuration(item.activity.totalDuration))
        }
        trailing?.invoke()
    }
}

/** Cover with soft depth; a missing cover becomes a tinted typographic placeholder. */
@Composable internal fun InsightsBookCover(book: Book, modifier: Modifier = Modifier) {
    val shape = AppRoundedCornerShape(6.dp)
    val eInk = LocalEInkMode.current
    val palette = listOf(InsightsHue.Blue, InsightsHue.Indigo, InsightsHue.Teal, InsightsHue.Orange, InsightsHue.Pink, InsightsHue.Green)
    val base = palette[abs(book.title.hashCode()) % palette.size]
    Box(modifier.then(if (eInk) Modifier else Modifier.shadow(3.dp, shape)).clip(shape)
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier)
        .background(if (eInk) Color.White else AppColors.BgGray), Alignment.Center) {
        if (book.coverPath.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().then(if (eInk) Modifier
                else Modifier.background(Brush.linearGradient(listOf(base.copy(alpha = .85f), base.copy(alpha = .55f))))), Alignment.Center) {
                Text(book.title, modifier = Modifier.padding(5.dp), color = if (eInk) Color.Black else Color.White,
                    fontSize = AppType.Caption2, fontWeight = FontWeight.SemiBold, fontFamily = resolveAppFontFamily(KaiTi),
                    maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        } else AsyncImage(book.coverPath, book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

// ---------------------------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------------------------

/**
 * Month calendar of circular day cells (intensity = time), or a year heatmap. With [annotations]
 * (single-book archive) each cell lists read/listen minutes and new marks instead.
 */
@Composable internal fun ActivityCalendar(period: StatisticsPeriod, daily: Map<LocalDate, ActivityDuration>,
    annotations: Map<LocalDate, Int>? = null, firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY,
    onDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val maximum = daily.values.maxOfOrNull { it.totalDuration }?.coerceAtLeast(1) ?: 1L
    val leading = (period.start.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val days = List<LocalDate?>(leading) { null } + period.days
    // Progress is read only inside layer/draw lambdas, so growth never recomposes the grid.
    val grow = rememberPlayOnce("calendar-${period.start}-${period.days.size}", 900)
    val accent = hue(AppColors.Accent)
    if (period.days.size > 62) {
        YearHeatmap(days, daily, maximum, today, grow, onDay)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { (0..6).forEach { day ->
            Text(firstDayOfWeek.plus(day.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                color = AppColors.TextSecondary, modifier = Modifier.weight(1f), fontSize = AppType.Caption2,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        } }
        days.chunked(7).forEachIndexed { row, week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (week + List(7 - week.size) { null }).forEachIndexed { column, day ->
                    val activity = daily[day] ?: ActivityDuration()
                    val level = activity.totalDuration.toFloat() / maximum
                    val interaction = remember { MutableInteractionSource() }
                    val clickable = day != null && day <= today
                    val cellModifier = Modifier.weight(1f)
                        .graphicsLayer {
                            val wave = ((grow.value * 1.6f) - (row + column) * .06f).coerceIn(0f, 1f)
                            alpha = wave; val s = .7f + .3f * wave; scaleX = s; scaleY = s
                        }
                        .then(if (clickable) Modifier.semantics { contentDescription = day.toString() }
                            .lumiPressable(interaction, pressedScale = .9f).clickable(interaction, indication = null) { onDay(day!!) } else Modifier)
                    if (annotations == null) MonthDayCell(day, activity.totalDuration > 0, level, day == today, accent, cellModifier)
                    else ArchiveDayCell(day, activity, annotations[day] ?: 0, day == today, level, accent, cellModifier)
                }
            }
        }
        InsightLabel(stringResource(if (annotations == null) R.string.insights_tap_hint else R.string.insights_calendar_hint))
    }
}

@Composable private fun MonthDayCell(day: LocalDate?, active: Boolean, level: Float, isToday: Boolean, accent: Color, modifier: Modifier) {
    val eInk = LocalEInkMode.current
    Box(modifier.aspectRatio(1f).padding(2.dp), Alignment.Center) {
        if (day == null) return@Box
        val strong = active && (eInk || level > .5f)
        Box(Modifier.fillMaxSize().clip(CircleShape)
            .then(if (active) Modifier.background(if (eInk) Color.Black.copy(alpha = .25f + .75f * level) else accent.copy(alpha = .2f + .8f * level)) else Modifier)
            .then(if (isToday) Modifier.border(1.5.dp, if (eInk) Color.Black else accent, CircleShape) else Modifier), Alignment.Center) {
            Text(day.dayOfMonth.toString(), fontSize = AppType.Footnote,
                fontWeight = if (isToday || active) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    strong -> Color.White
                    day > LocalDate.now() -> AppColors.TextSecondary.copy(alpha = .45f)
                    active -> AppColors.TextPrimary
                    else -> AppColors.TextSecondary
                })
        }
    }
}

@Composable private fun ArchiveDayCell(day: LocalDate?, activity: ActivityDuration, marks: Int, isToday: Boolean, level: Float,
    accent: Color, modifier: Modifier) {
    val shape = AppRoundedCornerShape(10.dp)
    // Every cell gets the height of the fullest cell (date + read/listen/marks lines) so the
    // grid stays even regardless of which values a day has.
    val cellHeight = with(LocalDensity.current) { AppType.Footnote.toDp() * 1.45f + AppType.Caption2.toDp() * 1.45f * 3 } + 10.dp
    Column(modifier.height(cellHeight).clip(shape)
        .background(if (day != null && activity.totalDuration > 0) accent.copy(alpha = .1f + .22f * level) else AppColors.BgGray.copy(alpha = if (day == null) 0f else 1f))
        .then(if (isToday) Modifier.border(1.5.dp, accent, shape) else Modifier)
        .padding(vertical = 4.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (day == null) return@Column
        Text(day.dayOfMonth.toString(), color = AppColors.TextPrimary, fontSize = AppType.Footnote, fontWeight = FontWeight.SemiBold)
        if (day <= LocalDate.now() && (activity.totalDuration > 0 || marks > 0)) {
            if (activity.readingDuration > 0) Text(stringResource(R.string.insights_read_minutes, activity.readingDuration / 60_000), color = accent,
                fontSize = AppType.Caption2, maxLines = 1)
            if (activity.listeningDuration > 0) Text(stringResource(R.string.insights_listen_minutes, activity.listeningDuration / 60_000),
                color = listenColor(), fontSize = AppType.Caption2, maxLines = 1)
            if (marks > 0) Text(stringResource(R.string.insights_day_marks, marks), color = AppColors.TextSecondary, fontSize = AppType.Caption2, maxLines = 1)
        }
    }
}

@Composable private fun YearHeatmap(days: List<LocalDate?>, daily: Map<LocalDate, ActivityDuration>, maximum: Long, today: LocalDate,
    grow: State<Float>, onDay: (LocalDate) -> Unit) {
    val scroll = rememberScrollState()
    val weeks = days.chunked(7)
    val accent = hue(AppColors.Accent)
    val empty = AppColors.BgGray
    LaunchedEffect(scroll.maxValue) {
        val todayWeek = weeks.indexOfFirst { week -> week.any { it == today } }
        if (todayWeek >= 0 && scroll.maxValue > 0) scroll.scrollTo((scroll.maxValue * todayWeek / weeks.size.coerceAtLeast(1)).coerceIn(0, scroll.maxValue))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            weeks.forEachIndexed { index, week ->
                Column(Modifier.graphicsLayer { alpha = ((grow.value * 1.5f) - index / weeks.size.toFloat() * .5f).coerceIn(0f, 1f) },
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val monthStart = week.firstOrNull { it?.dayOfMonth == 1 }
                    Box(Modifier.width(14.dp).height(16.dp)) {
                        if (monthStart != null) Text(insightsDate(monthStart, "LLL"), color = AppColors.TextSecondary, fontSize = AppType.Caption2,
                            maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                    }
                    week.forEach { day ->
                        val duration = daily[day]?.totalDuration ?: 0L
                        val level = if (duration <= 0) 0 else (1 + (duration * 4 / (maximum + 1)).toInt()).coerceAtMost(4)
                        val color = if (level == 0) empty else accent.copy(alpha = listOf(0f, .3f, .52f, .76f, 1f)[level])
                        Box(Modifier.size(14.dp).clip(AppRoundedCornerShape(3.5.dp)).background(color)
                            .then(if (day == today) Modifier.border(1.dp, AppColors.TextPrimary.copy(alpha = .6f), AppRoundedCornerShape(3.5.dp)) else Modifier)
                            .then(if (day != null && day <= today) Modifier.semantics { contentDescription = day.toString() }.clickable { onDay(day) } else Modifier))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            InsightLabel(stringResource(R.string.insights_tap_hint), Modifier.weight(1f))
            listOf(0f, .3f, .52f, .76f, 1f).forEach { alpha ->
                Box(Modifier.size(10.dp).clip(AppRoundedCornerShape(2.5.dp)).background(if (alpha == 0f) empty else accent.copy(alpha = alpha)))
            }
        }
    }
}

/**
 * Capsule bar chart that fits the card width: bars spring up in a left-to-right wave, a dashed
 * line marks the daily average, today is highlighted and each bar opens that day's details.
 */
@Composable internal fun ActivityBars(days: List<LocalDate>, daily: Map<LocalDate, ActivityDuration>, monthly: Boolean = false,
    chartHeight: Dp = 128.dp, onDay: ((LocalDate) -> Unit)? = null) {
    if (days.isEmpty()) return
    val today = LocalDate.now()
    val values = days.map { daily[it]?.totalDuration ?: 0L }
    val maximum = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val elapsed = days.indices.filter { days[it] <= today }
    val average = if (elapsed.isEmpty()) 0L else elapsed.sumOf { values[it] } / elapsed.size
    val motion = insightsMotion()
    val grow = rememberPlayOnce("bars-${days.first()}-${days.size}", 1000)
    val accent = hue(AppColors.Accent)
    val brush = barBrush(accent)
    val divider = AppColors.Divider
    val averageColor = AppColors.TextSecondary.copy(alpha = .7f)
    val averageFraction = animateFloatAsState((average.toFloat() / maximum).coerceIn(0f, 1f),
        if (motion) VisualMotion.spring(dampingRatio = .85f, stiffness = 200f) else snap(), label = "barsAverage")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val step = maxWidth / days.size
            val barWidth = (step * if (days.size <= 12) .5f else .62f).coerceIn(3.dp, 24.dp)
            Column {
                Box(Modifier.fillMaxWidth().height(chartHeight)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        drawLine(divider.copy(alpha = .6f), Offset(0f, 0f), Offset(size.width, 0f), .5.dp.toPx())
                        if (average > 0) {
                            val y = size.height * (1f - averageFraction.value * grow.value)
                            drawLine(averageColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        days.forEachIndexed { index, day ->
                            val target = values[index].toFloat() / maximum
                            val fraction = animateFloatAsState(target,
                                if (motion) VisualMotion.spring(dampingRatio = .78f, stiffness = 260f) else snap(), label = "bar")
                            val lag = .45f * index / days.size
                            val future = day > today
                            val interaction = remember { MutableInteractionSource() }
                            Box(Modifier.weight(1f).fillMaxHeight()
                                .semantics { contentDescription = day.toString() }
                                .then(if (onDay != null && !future) Modifier.clickable(interaction, indication = null) { onDay(day) } else Modifier),
                                Alignment.BottomCenter) {
                                if (values[index] > 0) {
                                    Box(Modifier.width(barWidth).fillMaxHeight()
                                        .layout { measurable, constraints ->
                                            val fractionHeight = (constraints.maxHeight * fraction.value.coerceIn(.015f, 1f))
                                                .roundToInt().coerceIn(0, constraints.maxHeight)
                                            val placeable = measurable.measure(
                                                constraints.copy(minHeight = fractionHeight, maxHeight = fractionHeight)
                                            )
                                            layout(constraints.maxWidth, constraints.maxHeight) {
                                                placeable.placeRelative(0, constraints.maxHeight - placeable.height)
                                            }
                                        }
                                        .graphicsLayer {
                                            // Grow from the baseline with a small overshoot, in the layer phase only.
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                                            scaleY = if (motion) InsightsOvershoot.transform(((grow.value - lag) / (1f - .45f)).coerceIn(0f, 1f)) else 1f
                                        }
                                        .lumiPressable(interaction, pressedScale = .92f)
                                        .clip(AppRoundedCornerShape(AppRadius.full))
                                        .background(if (day == today || monthly && YearMonth.from(day) == YearMonth.from(today)) Brush.verticalGradient(listOf(accent, accent)) else brush)
                                        .graphicsLayer { alpha = if (day == today) 1f else .92f })
                                } else Box(Modifier.padding(bottom = 1.dp).size(minOf(barWidth, 5.dp)).clip(CircleShape)
                                    .background(if (future) divider.copy(alpha = .5f) else divider))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    days.forEachIndexed { index, day ->
                        val show = days.size <= 12 || (days.size - 1 - index) % 7 == 0
                        val current = if (monthly) YearMonth.from(day) == YearMonth.from(today) else day == today
                        Box(Modifier.weight(1f), Alignment.Center) {
                            if (show) Text(
                                when {
                                    monthly -> insightsDate(day, "LLL")
                                    days.size <= 7 -> day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                                    else -> day.dayOfMonth.toString()
                                }, color = if (current) accent else AppColors.TextSecondary, fontSize = AppType.Caption2,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
                                modifier = Modifier.wrapContentWidth(unbounded = true))
                        }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (average > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp, 2.dp)) {
                    drawLine(averageColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
                }
                Spacer(Modifier.width(6.dp))
                InsightLabel(stringResource(R.string.insights_daily_average) + " " + insightsDuration(average))
            }
            if (onDay != null && !monthly) InsightLabel(stringResource(R.string.insights_tap_hint))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Day details
// ---------------------------------------------------------------------------------------------

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
            val context = currentCoroutineContext()
            context.ensureActive()
            val book = library.firstOrNull { it.id == bookId }
            val day = days.firstOrNull { readingLocalDate(it.date) == date }
            val dayNotes = notes.filterIndexed { index, note ->
                if (index % 128 == 0) context.ensureActive()
                java.time.Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate() == date
            }
            val dayBookmarks = bookmarks.filterIndexed { index, mark ->
                if (index % 128 == 0) context.ensureActive()
                java.time.Instant.ofEpochMilli(mark.createdAt).atZone(zone).toLocalDate() == date
            }
            context.ensureActive()
            if (book == null || (day == null && dayNotes.isEmpty() && dayBookmarks.isEmpty())) emptyList()
            else listOf(DayBook(book, ActivityDuration(day?.readingDuration ?: 0, day?.listeningDuration ?: 0), summarizeAnnotations(dayNotes, dayBookmarks)))
        }.onStart { emit(emptyList()) }
        combine(repository.getRecordsByDate(date.readingDate()), books.getAllBooks(),
            repository.observeTextNotesCreatedBetween(period.startMillis(zone), period.endMillis(zone)),
            repository.observeBookmarksCreatedBetween(period.startMillis(zone), period.endMillis(zone))) { records, library, notes, bookmarks ->
            val context = currentCoroutineContext()
            context.ensureActive()
            val totals = records.groupBy { it.bookId }
            val notesByBook = notes.groupBy { it.bookId }
            val bookmarksByBook = bookmarks.groupBy { it.bookId }
            val result = library.mapIndexedNotNull { index, book ->
                if (index % 128 == 0) context.ensureActive()
                if (book.isHidden || (bookId != null && book.id != bookId)) return@mapIndexedNotNull null
                val bookRecords = totals[book.id].orEmpty()
                val bookNotes = notesByBook[book.id].orEmpty()
                val bookBookmarks = bookmarksByBook[book.id].orEmpty()
                if (bookRecords.isEmpty() && bookNotes.isEmpty() && bookBookmarks.isEmpty()) return@mapIndexedNotNull null
                DayBook(book, ActivityDuration(bookRecords.sumOf { it.duration }, bookRecords.sumOf { it.listeningDuration }),
                    summarizeAnnotations(bookNotes, bookBookmarks))
            }
            context.ensureActive()
            result
                .sortedByDescending { it.activity.totalDuration }
        }.onStart { emit(emptyList()) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun load(date: LocalDate, bookId: String?) { query.value = date to bookId }
}

/** Compact capsule action used inside sheets (no glass: sheets have their own backdrop). */
@Composable internal fun InsightsCapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val eInk = LocalEInkMode.current
    val shape = AppRoundedCornerShape(AppRadius.full)
    val interaction = remember { MutableInteractionSource() }
    val container = when {
        eInk -> if (primary) Color.Black else Color.White
        primary -> AppColors.Accent
        else -> AppColors.BgGray
    }
    val content = when {
        eInk -> if (primary) Color.White else Color.Black
        primary -> AppColors.OnAccent
        else -> AppColors.TextPrimary
    }
    Row(modifier.heightIn(min = 40.dp).lumiPressable(interaction, pressedScale = .95f).clip(shape)
        .then(if (eInk && !primary) Modifier.border(1.dp, Color.Black, shape) else Modifier)
        .background(container).graphicsLayer { alpha = if (enabled) 1f else .45f }
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        if (icon != null) { ControlIcon(icon, null, tint = content, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = content, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun ReadingDaySheet(date: LocalDate, bookId: String? = null,
    model: ReadingDayViewModel = hiltViewModel(key = "reading-day-${bookId.orEmpty()}"), onDismiss: () -> Unit) {
    LaunchedEffect(date, bookId) { model.load(date, bookId) }
    val entries by model.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AppBottomSheet(onDismiss) { ProvideInsightsMotion {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = insightsSheetMaxHeight()),
            contentPadding = insightsSheetPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppSheetHeader(dayTitle(date), onDismiss) }
            item {
                val reading = entries.sumOf { it.activity.readingDuration }
                val listening = entries.sumOf { it.activity.listeningDuration }
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InsightLabel(stringResource(R.string.statistics_reading_time))
                    Text(styledFigure(insightsDuration(reading + listening), 34.sp, 16.sp), maxLines = 1)
                    Text(stringResource(R.string.activity_duration_breakdown, insightsDuration(reading), insightsDuration(listening)),
                        color = AppColors.TextSecondary, fontSize = AppType.Footnote)
                }
            }
            if (entries.isEmpty()) item { InsightLabel(stringResource(R.string.no_reading_records), Modifier.padding(horizontal = 4.dp)) }
            items(entries.size, key = { entries[it].book.id }) { index ->
                val entry = entries[index]
                InsightsAppear(index) {
                    Column(Modifier.fillMaxWidth().insightsCardSurface(18.dp, 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InsightsBookCover(entry.book, Modifier.size(44.dp, 64.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.book.title, color = AppColors.TextPrimary, fontSize = AppType.Subhead, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.activity_duration_breakdown,
                                    insightsDuration(entry.activity.readingDuration), insightsDuration(entry.activity.listeningDuration)),
                                    color = AppColors.TextSecondary, fontSize = AppType.Caption)
                                val marks = entry.annotations.let { it.highlights + it.underlines + it.notes + it.bookmarks }
                                if (marks > 0) InsightsPill(stringResource(R.string.insights_marks_count, marks), InsightsHue.Yellow, icon = AppIcons.PencilSimple)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightsCapsuleButton(stringResource(R.string.book_details), { BookDetailsActivity.start(context, entry.book.id) },
                                Modifier.weight(1f), icon = AppIcons.BookOpen)
                            InsightsCapsuleButton(stringResource(R.string.insights_check_in), { BookShareActivity.start(context, entry.book.id, date) },
                                Modifier.weight(1f), primary = true, enabled = entry.activity.totalDuration > 0, icon = AppIcons.CheckCircle)
                        }
                    }
                }
            }
        }
    } }
}
