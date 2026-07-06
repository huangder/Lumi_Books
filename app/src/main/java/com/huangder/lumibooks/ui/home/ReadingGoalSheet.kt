package com.huangder.lumibooks.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.KaiTi
import kotlinx.coroutines.launch

// 设计规范颜色 - 浅色模式
private val AccentColor = Color(0xFFE85D5D)
private val LightTextSecondary = Color(0xFF6E6E73)
private val LightBgGray = Color(0xFFF2F2F7)
private val LightBackground = Color(0xFFFBFBFC)
private val LightCardBg = Color.White
private val LightDivider = Color(0xFFE5E5EA)

// 深色模式颜色
private val DarkTextSecondary = Color(0xFF98989D)
private val DarkBgGray = Color(0xFF2C2C2E)
private val DarkBackground = Color(0xFF000000)
private val DarkCardBg = Color(0xFF1C1C1E)
private val DarkDivider = Color(0xFF38383A)

// 打卡颜色
private val StreakBlue = Color(0xFF4FC3F7)

@Composable
fun ReadingGoalSheet(
    visible: Boolean,
    todayReadingTime: Long,
    dailyGoal: Int,
    currentBook: Book?,
    weeklyData: List<DailyReading> = emptyList(),
    streakDays: Int = 0,
    onDismiss: () -> Unit,
    onSaveGoal: (Int) -> Unit,
    onTabBarVisibleChange: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var showGoalPicker by remember { mutableStateOf(false) }

    // 根据深浅模式动态获取颜色
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray
    val cardBg = if (isDark) DarkCardBg else LightCardBg
    val dividerColor = if (isDark) DarkDivider else LightDivider

    // 容器滑入动画
    val containerOffsetY = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            onTabBarVisibleChange(false)
            // 滑入动画
            containerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        } else {
            // 滑出动画
            containerOffsetY.animateTo(
                targetValue = 1f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
            onTabBarVisibleChange(true)
        }
    }

    // 处理返回键
    BackHandler(enabled = visible) {
        onDismiss()
    }

    if (!visible && containerOffsetY.value >= 1f) return

    // 计算阅读数据
    val totalMinutes = (todayReadingTime / 1000 / 60).toInt()
    val goalMs = dailyGoal * 60 * 1000L
    val progress = (todayReadingTime.toFloat() / goalMs).coerceIn(0f, 1f)
    val remaining = ((goalMs - todayReadingTime) / 1000 / 60).coerceAtLeast(0).toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩层（由外层 AnimatedVisibility 控制渐显/渐隐）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.4f else 0.1f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )

        // 容器层（滑入动画）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = containerOffsetY.value * size.height
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .background(cardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(bgGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showGoalPicker) {
                // 目标选择器
                GoalPicker(
                    currentMinutes = dailyGoal,
                    isDark = isDark,
                    onConfirm = { minutes ->
                        onSaveGoal(minutes)
                        showGoalPicker = false
                    },
                    onCancel = { showGoalPicker = false }
                )
            } else {
                // 今日阅读内容
                TodayReadingContent(
                    totalMinutes = totalMinutes,
                    dailyGoal = dailyGoal,
                    progress = progress,
                    remaining = remaining,
                    currentBook = currentBook,
                    weeklyData = weeklyData,
                    streakDays = streakDays,
                    isDark = isDark,
                    onChangeGoal = { showGoalPicker = true }
                )
            }
        }
    }
}

@Composable
private fun TodayReadingContent(
    totalMinutes: Int,
    dailyGoal: Int,
    progress: Float,
    remaining: Int,
    currentBook: Book?,
    weeklyData: List<DailyReading>,
    streakDays: Int,
    isDark: Boolean,
    onChangeGoal: () -> Unit
) {
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray
    val dividerColor = if (isDark) DarkDivider else LightDivider

    // 标题
    Text(
        text = "今日阅读",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KaiTi,
        color = textPrimary
    )

    Spacer(modifier = Modifier.height(32.dp))

    // 阅读时间数字
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$totalMinutes",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "分钟",
            fontSize = 16.sp,
            color = textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 目标提示
    Text(
        text = "目标 $dailyGoal 分钟",
        fontSize = 14.sp,
        color = textSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    // 进度条
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = AccentColor,
        trackColor = dividerColor
    )

    Spacer(modifier = Modifier.height(8.dp))

    // 剩余时间提示
    Text(
        text = if (remaining > 0) "还剩 $remaining 分钟达标" else "🎉 已达标！",
        fontSize = 12.sp,
        color = if (remaining > 0) textSecondary else AccentColor,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    // 正在阅读卡片
    if (currentBook != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgGray)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("正在阅读", fontSize = 12.sp, color = textSecondary)
                Text(
                    text = currentBook.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    maxLines = 1
                )
            }
            Text(
                text = "${(currentBook.readingProgress * 100).toInt()}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 连续阅读打卡区域
    val goalMs = dailyGoal * 60 * 1000L
    // weeklyData 始终是 7 项，index 6 = 今天（最近一天）
    val todayIndex = if (weeklyData.isNotEmpty()) weeklyData.size - 1 else 6

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weeklyData.forEachIndexed { index, data ->
            val isPast = index < todayIndex
            val isToday = index == todayIndex
            val goalMet = data.duration >= goalMs
            val achieved = goalMet || isToday

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (achieved) {
                            Modifier.background(StreakBlue, CircleShape)
                        } else {
                            Modifier.border(1.dp, dividerColor, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.dayLabel,
                    fontSize = 10.sp,
                    color = if (achieved) Color.White else textSecondary,
                    fontWeight = if (achieved) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (index < weeklyData.size - 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 连胜天数
        Text(
            text = "连胜 $streakDays 天",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = StreakBlue
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 底部按钮区域
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bgGray)
            .clickable(onClick = onChangeGoal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "修改每日目标",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
    }

    // 底部额外间距
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun GoalPicker(
    currentMinutes: Int,
    isDark: Boolean,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray

    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes) }

    Text(
        text = "设置每日阅读目标",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KaiTi,
        color = textPrimary
    )

    Spacer(modifier = Modifier.height(32.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "$selectedMinutes",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = AccentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "分钟",
            fontSize = 16.sp,
            color = textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    WheelPicker(
        items = presets,
        initialItem = presets.indexOf(currentMinutes).coerceAtLeast(0),
        isDark = isDark,
        onItemSelected = { selectedMinutes = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(bgGray)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Text("取消", fontSize = 16.sp, color = textSecondary)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(AccentColor)
                .clickable { onConfirm(selectedMinutes) },
            contentAlignment = Alignment.Center
        ) {
            Text("确定", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun WheelPicker(
    items: List<Int>,
    initialItem: Int,
    isDark: Boolean,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgGray = if (isDark) DarkBgGray else LightBgGray

    val itemHeight = 48.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)

    val centerIndex = listState.firstVisibleItemIndex
    LaunchedEffect(centerIndex) {
        if (centerIndex in items.indices) onItemSelected(items[centerIndex])
    }

    Box(modifier = modifier) {
        // 选中项背景
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(bgGray)
        )

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(itemHeight)) }
            items.forEach { value ->
                item {
                    val isSelected = value == items.getOrNull(listState.firstVisibleItemIndex)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$value 分钟",
                            fontSize = if (isSelected) 22.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) textPrimary else textSecondary
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(itemHeight)) }
        }
    }
}
