package com.huangder.lumibooks.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.cardPressEffect
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.SansSerif
import java.util.Calendar

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        OverscrollBounce(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Spacer(Modifier.height(AppSpace.md))
                    Text(
                        text = stringResource(R.string.stats_title),
                        fontSize = AppType.Display,
                        fontWeight = FontWeight.Bold,
                        fontFamily = KaiTi,
                        letterSpacing = (-0.02).sp,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                    )
                    Spacer(Modifier.height(AppSpace.xl))
                }

                item(key = "tabs") {
                    val tabs = listOf(
                        stringResource(R.string.tab_week),
                        stringResource(R.string.tab_month),
                        stringResource(R.string.tab_year)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg)
                    ) {
                        tabs.forEachIndexed { index, label ->
                            val isSelected = index == uiState.selectedTab
                            Text(
                                text = label,
                                fontSize = AppType.Body,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                                modifier = Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { viewModel.selectTab(index) }
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                }

                item(key = "overview_${uiState.selectedTab}") {
                    when (uiState.selectedTab) {
                        0 -> WeeklyOverview(uiState, viewModel)
                        1 -> MonthlyHeatmap(uiState, viewModel)
                        2 -> YearlyHeatmap(uiState, viewModel)
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                }

                item(key = "most_read") {
                    MostReadBooks(uiState.mostReadBooks)
                    Spacer(Modifier.height(AppSpace.lg))
                }

                item(key = "completion") {
                    CompletionProgress(uiState)
                    Spacer(Modifier.height(120.dp))
                }
            }
        } // OverscrollBounce 结束

        StatusGradientOverlay()
    }
}

@Composable
private fun WeeklyOverview(uiState: StatisticsUiState, viewModel: StatisticsViewModel) {
    val isCurrentWeek = uiState.displayWeekOffset == 0
    val weekData = uiState.weeklyData
    val totalMinutes = (weekData.sumOf { it.duration } / 1000 / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val activeDays = weekData.count { it.duration > 0 }

    // 日期范围标题
    val titleText = if (weekData.isNotEmpty()) {
        val startParts = weekData.first().date.split("-")
        val endParts = weekData.last().date.split("-")
        stringResource(R.string.month_format, startParts[1].toInt(), startParts[2].toInt(), endParts[1].toInt(), endParts[2].toInt())
    } else ""

    // 柱状图数据
    val todayIndex = if (isCurrentWeek) Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 else -1
    val weeklyMinutes = MutableList(7) { 0f }
    weekData.forEachIndexed { index, daily ->
        weeklyMinutes[index] = (daily.duration / 1000f / 60f)
    }
    if (isCurrentWeek) {
        weeklyMinutes[todayIndex] = maxOf(weeklyMinutes[todayIndex], uiState.todayReadingTime / 1000f / 60f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        // 标题 + 导航箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                titleText,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.previousWeek() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 20.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        enabled = !isCurrentWeek,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.nextWeek() },
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 20.sp, color = if (isCurrentWeek) AppColors.TextSecondary.copy(alpha = 0.3f) else AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        // 汇总
        Text(stringResource(R.string.reading_time_hours, hours, minutes, activeDays), fontSize = AppType.Caption, color = AppColors.TextSecondary)

        Spacer(Modifier.height(AppSpace.md))

        // 柱状图 + 星期标签（Canvas 绘制，保证对齐）
        val labels = listOf(
            stringResource(R.string.day_sunday),
            stringResource(R.string.day_monday),
            stringResource(R.string.day_tuesday),
            stringResource(R.string.day_wednesday),
            stringResource(R.string.day_thursday),
            stringResource(R.string.day_friday),
            stringResource(R.string.day_saturday)
        )
        val accentColor = AppColors.Accent
        val accentDim = accentColor.copy(alpha = 0.35f)
        val textSecColor = AppColors.TextSecondary
        val maxVal = (weeklyMinutes.maxOrNull() ?: 0f).coerceAtLeast(5f)
        val labelSize = 10.sp

        // 预计算 native 颜色
        val accentArgb = android.graphics.Color.argb((accentColor.alpha * 255).toInt(), (accentColor.red * 255).toInt(), (accentColor.green * 255).toInt(), (accentColor.blue * 255).toInt())
        val textSecArgb2 = android.graphics.Color.argb((textSecColor.alpha * 255).toInt(), (textSecColor.red * 255).toInt(), (textSecColor.green * 255).toInt(), (textSecColor.blue * 255).toInt())

        Canvas(modifier = Modifier.fillMaxWidth().height(128.dp)) {
            val labelHeight = labelSize.toPx() + 6.dp.toPx()
            val barAreaHeight = size.height - labelHeight
            val barWidth = size.width / (7 * 2 - 1)
            val cornerRadius = 4.dp.toPx()

            // 柱体
            weeklyMinutes.forEachIndexed { index, value ->
                val normalized = (value / maxVal).coerceIn(0f, 1f)
                val barHeight = barAreaHeight * normalized
                val x = index * barWidth * 2
                val isToday = index == todayIndex
                val visibleHeight = if (value > 0f) maxOf(barHeight, 2.dp.toPx()) else 0f

                drawRoundRect(
                    color = if (isToday) accentColor else accentDim,
                    topLeft = Offset(x, barAreaHeight - visibleHeight),
                    size = Size(barWidth, visibleHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }

            // 星期标签（居中于每个柱体下方）
            val paint = android.graphics.Paint().apply {
                textSize = labelSize.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            labels.forEachIndexed { index, label ->
                val isToday = index == todayIndex
                paint.color = if (isToday) accentArgb else textSecArgb2
                paint.isFakeBoldText = isToday
                val cx = index * barWidth * 2 + barWidth / 2
                drawContext.canvas.nativeCanvas.drawText(
                    label, cx, size.height - 2.dp.toPx(), paint
                )
            }
        }
    }
}

// ─── 月热力图 ──────────────────────────────────────────────────

@Composable
private fun MonthlyHeatmap(uiState: StatisticsUiState, viewModel: StatisticsViewModel) {
    val year = uiState.displayYear
    val month = uiState.displayMonth
    val now = Calendar.getInstance()
    val isCurrentMonth = (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH))
    val today = if (isCurrentMonth) now.get(Calendar.DAY_OF_MONTH) else -1

    val firstDay = Calendar.getInstance().apply { set(year, month, 1) }
    val startDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)

    val totalMinutes = (uiState.monthlyReadingTime / 1000 / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val activeDays = uiState.monthlyDailyData.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        // 月份标题 + 导航箭头
        val monthNames = listOf(
            stringResource(R.string.month_january),
            stringResource(R.string.month_february),
            stringResource(R.string.month_march),
            stringResource(R.string.month_april),
            stringResource(R.string.month_may),
            stringResource(R.string.month_june),
            stringResource(R.string.month_july),
            stringResource(R.string.month_august),
            stringResource(R.string.month_september),
            stringResource(R.string.month_october),
            stringResource(R.string.month_november),
            stringResource(R.string.month_december)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.year_format, year) + monthNames[month],
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            // 左箭头
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.previousMonth() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 20.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            // 右箭头（当前月禁用）
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        enabled = !isCurrentMonth,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.nextMonth() },
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 20.sp, color = if (isCurrentMonth) AppColors.TextSecondary.copy(alpha = 0.3f) else AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        // 汇总信息
        Text(stringResource(R.string.reading_time_hours, hours, minutes, activeDays), fontSize = AppType.Caption, color = AppColors.TextSecondary)

        Spacer(Modifier.height(AppSpace.md))

        // 星期标题
        val weekLabels = listOf(
            stringResource(R.string.day_sunday),
            stringResource(R.string.day_monday),
            stringResource(R.string.day_tuesday),
            stringResource(R.string.day_wednesday),
            stringResource(R.string.day_thursday),
            stringResource(R.string.day_friday),
            stringResource(R.string.day_saturday)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(AppSpace.xs))

        // 日历网格
        val totalCells = startDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - startDayOfWeek + 1
                    if (dayIndex in 1..daysInMonth) {
                        val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayIndex)
                        val duration = uiState.monthlyDailyData[dateStr] ?: 0
                        val minutesForDay = (duration / 1000 / 60).toInt()
                        val isToday = dayIndex == today

                        val color = when {
                            minutesForDay == 0 -> AppColors.BgGray
                            minutesForDay < 15 -> AppColors.Accent.copy(alpha = 0.2f)
                            minutesForDay < 30 -> AppColors.Accent.copy(alpha = 0.4f)
                            minutesForDay < 60 -> AppColors.Accent.copy(alpha = 0.7f)
                            else -> AppColors.Accent
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .then(
                                    if (isToday) Modifier.border(1.5.dp, AppColors.Accent, RoundedCornerShape(4.dp))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayIndex",
                                fontSize = 10.sp,
                                color = if (minutesForDay > 0 && minutesForDay >= 30) Color.White else AppColors.TextSecondary,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── 年热力图（GitHub 贡献图风格，Canvas 渲染）──────────────────

@Composable
private fun YearlyHeatmap(uiState: StatisticsUiState, viewModel: StatisticsViewModel) {
    val displayYear = uiState.displayYear
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val isCurrentYear = displayYear == currentYear

    // 该年1月1日，对齐到周日
    val yearStart = Calendar.getInstance().apply {
        set(displayYear, Calendar.JANUARY, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }
    val startCal = yearStart.clone() as Calendar
    while (startCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        startCal.add(Calendar.DAY_OF_YEAR, -1)
    }

    // 结束日期
    val yearEnd = Calendar.getInstance().apply { set(displayYear, Calendar.DECEMBER, 31) }
    val endCal = if (isCurrentYear) Calendar.getInstance() else yearEnd
    val yearEndStr = String.format("%04d-12-31", displayYear)

    val daysBetween = ((endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    val weeks = (daysBetween + 1 + 6) / 7

    val totalMinutes = (uiState.yearlyDailyData.values.sum() / 1000 / 60).toInt()
    val hours = totalMinutes / 60
    val activeDays = uiState.yearlyDailyData.size

    val accentColor = AppColors.Accent
    val monthLabels = listOf(
        stringResource(R.string.month_label_format, 1),
        stringResource(R.string.month_label_format, 2),
        stringResource(R.string.month_label_format, 3),
        stringResource(R.string.month_label_format, 4),
        stringResource(R.string.month_label_format, 5),
        stringResource(R.string.month_label_format, 6),
        stringResource(R.string.month_label_format, 7),
        stringResource(R.string.month_label_format, 8),
        stringResource(R.string.month_label_format, 9),
        stringResource(R.string.month_label_format, 10),
        stringResource(R.string.month_label_format, 11),
        stringResource(R.string.month_label_format, 12)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        // 标题 + 导航箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.year_format, displayYear),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.previousYear() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 20.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        enabled = !isCurrentYear,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.nextYear() },
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 20.sp, color = if (isCurrentYear) AppColors.TextSecondary.copy(alpha = 0.3f) else AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        // 汇总
        Text(stringResource(R.string.reading_time_hours_short, hours, activeDays), fontSize = AppType.Caption, color = AppColors.TextSecondary)

        Spacer(Modifier.height(AppSpace.md))

        // Canvas 渲染热力图网格 — 固定格子大小，水平滚动
        // 预计算颜色（Canvas 内不能访问 composable 状态）
        val bgGray = AppColors.BgGray
        val c0 = accentColor.copy(alpha = 0.2f)
        val c1 = accentColor.copy(alpha = 0.4f)
        val c2 = accentColor.copy(alpha = 0.7f)
        val c3 = accentColor
        val textSec = AppColors.TextSecondary
        val textSecArgb = android.graphics.Color.argb(
            (textSec.alpha * 255).toInt(),
            (textSec.red * 255).toInt(),
            (textSec.green * 255).toInt(),
            (textSec.blue * 255).toInt()
        )
        val labelHeight = 14.dp
        val todayStr = if (isCurrentYear) String.format("%04d-%02d-%02d", endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH) + 1, endCal.get(Calendar.DAY_OF_MONTH)) else ""
        val yearStartStr = String.format("%04d-01-01", displayYear)

        val cellSizeDp = 12
        val gapDp = 2
        val gridWidth = (weeks * (cellSizeDp + gapDp)).dp
        val gridHeight = (14 + 7 * 12 + 2).dp

        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Canvas(
            modifier = Modifier
                .width(gridWidth)
                .height(gridHeight)
        ) {
            val cellSizePx = cellSizeDp.dp.toPx()
            val gapPx = gapDp.dp.toPx()
            val cellStepPx = cellSizePx + gapPx
            val cornerRadius = 2.dp.toPx()

            // 月份标签
            var lastMonth = -1
            for (week in 0 until weeks) {
                val weekCal = (startCal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, week) }
                val month = weekCal.get(Calendar.MONTH)
                val dayOfMonth = weekCal.get(Calendar.DAY_OF_MONTH)
                if (month != lastMonth && dayOfMonth <= 7) {
                    val paint = android.graphics.Paint().apply {
                        color = textSecArgb
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        monthLabels[month], week * cellStepPx, labelHeight.toPx() - 2.dp.toPx(), paint
                    )
                    lastMonth = month
                }
            }

            // 热力图格子
            val gridTop = labelHeight.toPx()
            for (week in 0 until weeks) {
                for (dayOfWeek in 0..6) {
                    val cellCal = (startCal.clone() as Calendar).apply {
                        add(Calendar.WEEK_OF_YEAR, week)
                        add(Calendar.DAY_OF_WEEK, dayOfWeek)
                    }
                    val cellDateStr = String.format(
                        "%04d-%02d-%02d",
                        cellCal.get(Calendar.YEAR),
                        cellCal.get(Calendar.MONTH) + 1,
                        cellCal.get(Calendar.DAY_OF_MONTH)
                    )

                    val isInYear = cellDateStr >= yearStartStr && cellDateStr <= yearEndStr
                    val isFuture = isCurrentYear && cellDateStr > todayStr

                    val color = when {
                        !isInYear -> bgGray
                        isFuture -> bgGray
                        else -> {
                            val duration = uiState.yearlyDailyData[cellDateStr] ?: 0L
                            val m = (duration / 1000 / 60).toInt()
                            when {
                                m == 0 -> bgGray
                                m < 15 -> c0
                                m < 30 -> c1
                                m < 60 -> c2
                                else -> c3
                            }
                        }
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(week * cellStepPx, gridTop + dayOfWeek * cellStepPx),
                        size = Size(cellSizePx, cellSizePx),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                }
            }
        }
        } // Box horizontalScroll

        Spacer(Modifier.height(AppSpace.sm))

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.heat_low), fontSize = 9.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.width(4.dp))
            listOf(AppColors.BgGray, accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.4f), accentColor.copy(alpha = 0.7f), accentColor).forEach { c ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.heat_high), fontSize = 9.sp, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun MostReadBooks(books: List<MostReadBook>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .padding(AppSpace.md)
    ) {
        Text(stringResource(R.string.most_read_books), fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = KaiTi, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.md))

        if (books.isEmpty()) {
            Text(stringResource(R.string.no_reading_records), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        } else {
            books.forEachIndexed { index, book ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.Divider))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpace.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = book.title,
                        modifier = Modifier
                            .size(40.dp, 53.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.BgGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(AppSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.author, fontSize = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
                    }
                    // 阅读时长
                    val hours = (book.totalDuration / 1000 / 60 / 60).toInt()
                    val minutes = ((book.totalDuration / 1000 / 60) % 60).toInt()
                    Text(
                        text = if (hours > 0) "${hours}h${minutes}m" else "${minutes}m",
                        fontSize = AppType.Caption,
                        color = AppColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionProgress(uiState: StatisticsUiState) {
    val totalMinutes = (uiState.todayReadingTime / 1000 / 60).toInt()
    val goalMinutes = uiState.dailyGoal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .padding(AppSpace.lg)
    ) {
        Text(stringResource(R.string.today_goal), fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.xs))
        Text(
            text = stringResource(R.string.goal_progress_format, totalMinutes, goalMinutes),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(AppSpace.sm))
        LinearProgressIndicator(
            progress = { uiState.goalProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AppColors.Accent,
            trackColor = AppColors.Divider
        )

        Spacer(Modifier.height(AppSpace.lg))

        val monthlyHours = (uiState.monthlyReadingTime / 1000 / 60 / 60).toInt()
        Text(stringResource(R.string.monthly_reading_label), fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.xs))
        Text(
            text = stringResource(R.string.monthly_hours_format, monthlyHours),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
    }
}
