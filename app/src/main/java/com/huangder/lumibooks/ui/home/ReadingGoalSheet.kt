package com.huangder.lumibooks.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun ReadingGoalSheet(
    visible: Boolean,
    todayReadingTime: Long,
    dailyGoal: Int,
    currentBook: Book?,
    weeklyData: List<DailyReading> = emptyList(),
    streakDays: Int = 0,
    onDismiss: () -> Unit,
    onSaveGoal: (Int) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val cardBg = AppColors.CardBg
    val containerOffsetY = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    val sheetScrollState = rememberScrollState()
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.84f).dp
    var showGoalPicker by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            showGoalPicker = false
            containerOffsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        } else {
            containerOffsetY.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        }
    }

    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(
        enabled = visible || containerOffsetY.value < 1f
    ) { onDismiss() }

    if (!visible && containerOffsetY.value >= 1f) return

    val totalMinutes = (todayReadingTime / 1000 / 60).toInt()
    val hasGoal = dailyGoal > 0
    val goalMs = if (hasGoal) dailyGoal * 60 * 1000L else 0L
    val progress = if (hasGoal) (todayReadingTime.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    val remaining = if (hasGoal) ((goalMs - todayReadingTime) / 1000 / 60).coerceAtLeast(0).toInt() else 0

    fun showContainerContent(goalPicker: Boolean) {
        if (showGoalPicker == goalPicker) return
        coroutineScope.launch {
            containerOffsetY.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            showGoalPicker = goalPicker
            sheetScrollState.scrollTo(0)
            containerOffsetY.snapTo(1f)
            containerOffsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    fun saveGoalAndReturn(minutes: Int) {
        coroutineScope.launch {
            containerOffsetY.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            onSaveGoal(minutes)
            showGoalPicker = false
            sheetScrollState.scrollTo(0)
            containerOffsetY.snapTo(1f)
            containerOffsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = if (isDark) 0.4f else 0.1f)
                )
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .heightIn(max = maxSheetHeight)
                .materialBottomSheetMotion(containerOffsetY.value, predictiveBackProgress)
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(cardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(sheetScrollState)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(bgGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showGoalPicker) {
                GoalPicker(
                    currentMinutes = dailyGoal,
                    onConfirm = { minutes -> saveGoalAndReturn(minutes) },
                    onCancel = { showContainerContent(false) }
                )
            } else {
                TodayReadingContent(
                    totalMinutes = totalMinutes,
                    dailyGoal = dailyGoal,
                    progress = progress,
                    remaining = remaining,
                    currentBook = currentBook,
                    weeklyData = weeklyData,
                    streakDays = streakDays,
                    onChangeGoal = { showContainerContent(true) }
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
    onChangeGoal: () -> Unit
) {
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val dividerColor = AppColors.Divider
    val hasGoal = dailyGoal > 0
    val hasReadToday = totalMinutes > 0
    val todayStatusText = if (hasReadToday) "今日已阅读" else "今日未阅读"
    val todayEmoji = if (hasReadToday) "📖 ✅ 🌿" else "🌙 ☕ 📚"

    Text(
        text = if (hasGoal) stringResource(R.string.today_reading_label) else todayStatusText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KaiTi,
        color = textPrimary
    )

    Spacer(modifier = Modifier.height(32.dp))

    if (hasGoal) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$totalMinutes",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Accent
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.minutes_label),
                fontSize = 16.sp,
                color = textSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.goal_minutes_label, dailyGoal),
            fontSize = 14.sp,
            color = textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AppColors.Accent,
            trackColor = bgGray
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (remaining > 0) {
                stringResource(R.string.remaining_minutes, remaining)
            } else {
                stringResource(R.string.goal_reached)
            },
            fontSize = 12.sp,
            color = textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = todayEmoji,
            fontSize = 40.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = todayStatusText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasReadToday) "今天已经和书页碰面了" else "今天还没有留下阅读记录",
            fontSize = 13.sp,
            color = textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (currentBook != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgGray)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentBook.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(currentBook.readingProgress * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    WeeklyCheckIn(weeklyData, dailyGoal, dividerColor, streakDays)

    Spacer(modifier = Modifier.height(16.dp))

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
            text = stringResource(R.string.edit_daily_goal),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun WeeklyCheckIn(
    weeklyData: List<DailyReading>,
    dailyGoal: Int,
    dividerColor: Color,
    streakDays: Int
) {
    val textSecondary = AppColors.TextSecondary
    val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    val goalMs = dailyGoal * 60 * 1000L

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weeklyData.forEachIndexed { index, data ->
            val goalMet = if (dailyGoal > 0) data.duration >= goalMs else data.duration > 0L
            val achieved = if (dailyGoal > 0) goalMet || index == todayIndex else goalMet
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (achieved) AppColors.Accent else Color.Transparent)
                        .border(1.dp, if (achieved) AppColors.Accent else dividerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (achieved) {
                        Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = data.dayLabel, fontSize = 11.sp, color = textSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.streak_days, streakDays),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = textSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End
    )
}

@Composable
private fun GoalPicker(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val dividerColor = AppColors.Divider
    var customMode by remember { mutableStateOf(false) }
    var customMinutesText by remember(currentMinutes) {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "")
    }
    val customMinutes = customMinutesText.toIntOrNull()
    val isCustomValid = customMinutes != null && customMinutes in 1..1439

    Text(
        text = stringResource(R.string.set_daily_goal),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KaiTi,
        color = textPrimary
    )

    Spacer(modifier = Modifier.height(32.dp))

    GoalOptionButton(
        title = "不设置目标",
        subtitle = "首页只显示今日是否阅读",
        onClick = { onConfirm(0) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (customMode) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(bgGray)
                .border(1.dp, dividerColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "自定义目标",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = customMinutesText,
                    onValueChange = { raw ->
                        customMinutesText = raw.filter { it.isDigit() }.take(4)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isCustomValid) onConfirm(customMinutes!!) }
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (customMinutesText.isBlank()) {
                                Text("输入分钟数", fontSize = 16.sp, color = textSecondary)
                            }
                            innerTextField()
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.minutes_label),
                    fontSize = 14.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isCustomValid) AppColors.Accent else textSecondary.copy(alpha = 0.22f))
                        .clickable(enabled = isCustomValid) { onConfirm(customMinutes!!) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCustomValid) AppColors.OnAccent else textSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "请输入 1-1439 分钟",
                fontSize = 12.sp,
                color = if (customMinutesText.isBlank() || isCustomValid) textSecondary else AppColors.Accent
            )
        }
    } else {
        GoalOptionButton(
            title = "自定义目标",
            subtitle = if (currentMinutes > 0) {
                "当前 $currentMinutes 分钟，可改为 1-1439 分钟"
            } else {
                "输入 1-1439 分钟"
            },
            onClick = { customMode = true }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bgGray)
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.cancel), fontSize = 16.sp, color = textSecondary)
    }
}

@Composable
private fun GoalOptionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val dividerColor = AppColors.Divider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgGray)
            .border(1.dp, dividerColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = textSecondary
        )
    }
}
