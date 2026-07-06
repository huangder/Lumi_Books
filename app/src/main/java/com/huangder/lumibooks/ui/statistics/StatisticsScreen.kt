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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.cardPressEffect
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
        Spacer(Modifier.height(AppSpace.md))
        Text(
            text = "统计",
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            fontFamily = KaiTi,
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.lg)
        )

        Spacer(Modifier.height(AppSpace.xl))

        // 周/月/年 切换 Tab
        val tabs = listOf("周", "月", "年")
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

        when (uiState.selectedTab) {
            0 -> WeeklyOverview(uiState)
            1 -> MonthlyHeatmap(uiState)
            2 -> YearlyHeatmap(uiState)
        }
        Spacer(Modifier.height(AppSpace.lg))
        MostReadBooks(uiState.mostReadBooks)
        Spacer(Modifier.height(AppSpace.lg))
        CompletionProgress(uiState)
        Spacer(Modifier.height(120.dp))
        } // Column 结束
        } // OverscrollBounce 结束

        StatusGradientOverlay()
    }
}

@Composable
private fun WeeklyOverview(uiState: StatisticsUiState) {
    val totalMinutes = (uiState.todayReadingTime / 1000 / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    // 周平均（排除今天）
    val weekDays = uiState.weeklyData.filter { it.duration > 0 }
    val weekAvgMinutes = if (weekDays.isNotEmpty()) {
        (weekDays.sumOf { it.duration } / 1000 / 60 / weekDays.size).toInt()
    } else 0
    val growthPercent = if (weekAvgMinutes > 0) {
        ((totalMinutes - weekAvgMinutes).toFloat() / weekAvgMinutes * 100).toInt()
    } else if (totalMinutes > 0) 100 else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$hours", fontSize = AppType.Huge, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontFamily = SansSerif)
            Text("小时", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
            Text("$minutes", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontFamily = SansSerif)
            Text("分钟", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(AppSpace.xs))
            // 同比增长
            if (growthPercent != 0) {
                val color = if (growthPercent > 0) Color(0xFF34C759) else Color(0xFFFF3B30)
                val sign = if (growthPercent > 0) "+" else ""
                Text(
                    text = "$sign$growthPercent%",
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
        }

        // 柱状图：真实数据，动态比例尺
        val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        // 构建 7 天数据（分钟），确保今天有值
        val weeklyMinutes = MutableList(7) { 0f }
        uiState.weeklyData.forEach { daily ->
            // daily.dayOfWeek 是 "Mon", "Tue" 等
            val dayMap = mapOf("Sun" to 0, "Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6)
            val idx = dayMap[daily.dayOfWeek] ?: -1
            if (idx in 0..6) {
                weeklyMinutes[idx] = (daily.duration / 1000f / 60f)
            }
        }
        // 今天的数据从 todayReadingTime 补充（可能 weeklyData 还没更新）
        weeklyMinutes[todayIndex] = maxOf(weeklyMinutes[todayIndex], uiState.todayReadingTime / 1000f / 60f)

        WeeklyBarChart(
            data = weeklyMinutes,
            todayIndex = todayIndex,
            modifier = Modifier.weight(1f).height(160.dp)
        )
    }
}

@Composable
private fun WeeklyBarChart(data: List<Float>, todayIndex: Int, modifier: Modifier = Modifier) {
    val labels = listOf("日", "一", "二", "三", "四", "五", "六")
    val accentColor = AppColors.Accent
    // 动态比例尺：最大值向上取整到整分钟，至少 5 分钟
    val maxVal = (data.maxOrNull() ?: 0f).coerceAtLeast(5f)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barWidth = size.width / (data.size * 2 - 1)
            val maxHeight = size.height
            val cornerRadius = 4.dp.toPx()

            data.forEachIndexed { index, value ->
                val normalized = (value / maxVal).coerceIn(0f, 1f)
                val barHeight = maxHeight * normalized
                val x = index * barWidth * 2
                val isToday = index == todayIndex

                // 最小可见高度（有数据时至少显示 2px）
                val visibleHeight = if (value > 0f) maxOf(barHeight, 2.dp.toPx()) else 0f

                drawRoundRect(
                    color = if (isToday) accentColor else accentColor.copy(alpha = 0.35f),
                    topLeft = Offset(x, maxHeight - visibleHeight),
                    size = Size(barWidth, visibleHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                val isToday = index == todayIndex
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) AppColors.Accent else AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── 月热力图 ──────────────────────────────────────────────────

@Composable
private fun MonthlyHeatmap(uiState: StatisticsUiState) {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val today = calendar.get(Calendar.DAY_OF_MONTH)

    val firstDay = Calendar.getInstance().apply {
        set(year, month, 1)
    }
    val startDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK) - 1 // 0=周日
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
            .cardPressEffect()
            .padding(AppSpace.md)
    ) {
        // 月份标题
        val monthNames = listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${year}年${monthNames[month]}", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = KaiTi, color = AppColors.TextPrimary)
            Text("${hours}h${minutes}m · ${activeDays}天", fontSize = AppType.Caption, color = AppColors.TextSecondary)
        }

        Spacer(Modifier.height(AppSpace.md))

        // 星期标题
        val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
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

// ─── 年热力图（GitHub 贡献图风格）───────────────────────────────

@Composable
private fun YearlyHeatmap(uiState: StatisticsUiState) {
    val today = Calendar.getInstance()
    val todayStr = String.format("%04d-%02d-%02d", today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))

    // 从今天往前推，找到最近的周日作为起始
    val startCal = Calendar.getInstance().apply {
        add(Calendar.YEAR, -1)
        add(Calendar.DAY_OF_YEAR, 1)
    }
    // 对齐到周日
    while (startCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        startCal.add(Calendar.DAY_OF_YEAR, -1)
    }

    val startDate = startCal.clone() as Calendar

    // 计算周数
    val daysBetween = ((today.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    val weeks = (daysBetween + 1 + 6) / 7

    val totalMinutes = (uiState.yearlyDailyData.values.sum() / 1000 / 60).toInt()
    val hours = totalMinutes / 60
    val activeDays = uiState.yearlyDailyData.size

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("过去一年", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = KaiTi, color = AppColors.TextPrimary)
            Text("${hours}小时 · ${activeDays}天", fontSize = AppType.Caption, color = AppColors.TextSecondary)
        }

        Spacer(Modifier.height(AppSpace.md))

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // 月份标签行
                Row {
                    val monthLabels = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
                    var lastMonth = -1
                    repeat(weeks) { week ->
                        val weekCal = (startCal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, week) }
                        val firstDayOfWeek = weekCal.get(Calendar.DAY_OF_MONTH)
                        val month = weekCal.get(Calendar.MONTH)
                        // 只在月份的第一周或第一行显示标签
                        if (month != lastMonth && firstDayOfWeek <= 7) {
                            Text(
                                text = monthLabels[month],
                                fontSize = 9.sp,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.width(14.dp)
                            )
                            lastMonth = month
                        } else {
                            Spacer(modifier = Modifier.width(14.dp))
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                // 7行 × N列网格
                repeat(7) { dayOfWeek ->
                    Row {
                        repeat(weeks) { week ->
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

                            val isFuture = cellDateStr > todayStr
                            val duration = if (isFuture) 0L else (uiState.yearlyDailyData[cellDateStr] ?: 0L)
                            val minutesForDay = (duration / 1000 / 60).toInt()

                            val color = when {
                                isFuture -> Color.Transparent
                                minutesForDay == 0 -> AppColors.BgGray
                                minutesForDay < 15 -> AppColors.Accent.copy(alpha = 0.2f)
                                minutesForDay < 30 -> AppColors.Accent.copy(alpha = 0.4f)
                                minutesForDay < 60 -> AppColors.Accent.copy(alpha = 0.7f)
                                else -> AppColors.Accent
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppSpace.sm))

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("少", fontSize = 9.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.width(4.dp))
            val legendColors = listOf(AppColors.BgGray, AppColors.Accent.copy(alpha = 0.2f), AppColors.Accent.copy(alpha = 0.4f), AppColors.Accent.copy(alpha = 0.7f), AppColors.Accent)
            legendColors.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("多", fontSize = 9.sp, color = AppColors.TextSecondary)
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
        Text("最常阅读的书籍", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = KaiTi, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.md))

        if (books.isEmpty()) {
            Text("暂无阅读记录", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
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
        Text("今日目标", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.xs))
        Text(
            text = "${totalMinutes}/${goalMinutes}分钟",
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
        Text("本月阅读", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpace.xs))
        Text(
            text = "${monthlyHours}小时",
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary
        )
    }
}
