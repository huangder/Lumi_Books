package com.huangder.lumibooks.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import java.util.Calendar
import kotlinx.coroutines.coroutineScope
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
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val summaryOffsetY = remember { Animatable(1f) }
    val pickerOffsetY = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val summaryScrollState = rememberScrollState()
    val pickerScrollState = rememberScrollState()
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.84f).dp
    var showGoalPicker by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            showGoalPicker = false
            pickerOffsetY.snapTo(1f)
            summaryOffsetY.snapTo(1f)
            summaryOffsetY.animateBottomSheetIn()
        } else {
            coroutineScope {
                launch { summaryOffsetY.animateBottomSheetOut() }
                launch { pickerOffsetY.animateBottomSheetOut() }
            }
            showGoalPicker = false
        }
    }

    fun openGoalPicker() {
        if (showGoalPicker) return
        showGoalPicker = true
        scope.launch {
            pickerScrollState.scrollTo(0)
            pickerOffsetY.snapTo(1f)
            if (isLiquidGlass) {
                coroutineScope {
                    launch { summaryOffsetY.animateBottomSheetOut() }
                    launch { pickerOffsetY.animateBottomSheetIn() }
                }
            } else {
                pickerOffsetY.animateBottomSheetIn()
            }
        }
    }

    fun closeGoalPicker(minutesToSave: Int? = null) {
        if (!showGoalPicker) return
        scope.launch {
            minutesToSave?.let(onSaveGoal)
            summaryScrollState.scrollTo(0)
            if (isLiquidGlass) {
                summaryOffsetY.snapTo(1f)
                coroutineScope {
                    launch { pickerOffsetY.animateBottomSheetOut() }
                    launch { summaryOffsetY.animateBottomSheetIn() }
                }
            } else {
                pickerOffsetY.animateBottomSheetOut()
            }
            showGoalPicker = false
        }
    }

    fun closeTopContainer() {
        if (showGoalPicker) closeGoalPicker() else onDismiss()
    }

    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(
        enabled = visible || summaryOffsetY.value < 1f || pickerOffsetY.value < 1f
    ) { closeTopContainer() }

    if (!visible && summaryOffsetY.value >= 1f && pickerOffsetY.value >= 1f) return

    val totalMinutes = (todayReadingTime / 1000 / 60).toInt()
    val hasGoal = dailyGoal > 0
    val goalMs = if (hasGoal) dailyGoal * 60 * 1000L else 0L
    val progress = if (hasGoal) (todayReadingTime.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    val remaining = if (hasGoal) ((goalMs - todayReadingTime) / 1000 / 60).coerceAtLeast(0).toInt() else 0
    val scrimProgress = (
        2f - summaryOffsetY.value.coerceIn(0f, 1f) - pickerOffsetY.value.coerceIn(0f, 1f)
    ).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = (if (isDark) 0.28f else 0.18f) * scrimProgress)
                )
                .pointerInput(showGoalPicker, visible) {
                    detectTapGestures { closeTopContainer() }
                }
        )

        if (!isLiquidGlass || !showGoalPicker || summaryOffsetY.value < 1f) {
            ReadingGoalContainer(
                entryOffset = summaryOffsetY.value,
                predictiveBackProgress = if (showGoalPicker) 0f else predictiveBackProgress,
                maxSheetHeight = maxSheetHeight,
                scrollState = summaryScrollState,
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                TodayReadingContent(
                    totalMinutes = totalMinutes,
                    dailyGoal = dailyGoal,
                    progress = progress,
                    remaining = remaining,
                    currentBook = currentBook,
                    weeklyData = weeklyData,
                    streakDays = streakDays,
                    onChangeGoal = ::openGoalPicker
                )
            }
        }

        if (showGoalPicker || pickerOffsetY.value < 1f) {
            ReadingGoalContainer(
                entryOffset = pickerOffsetY.value,
                predictiveBackProgress = if (showGoalPicker) predictiveBackProgress else 0f,
                maxSheetHeight = maxSheetHeight,
                scrollState = pickerScrollState,
                onClose = { closeGoalPicker() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GoalPicker(
                    currentMinutes = dailyGoal,
                    onConfirm = { minutes -> closeGoalPicker(minutes) },
                    onCancel = { closeGoalPicker() }
                )
            }
        }
    }
}

@Composable
private fun ReadingGoalContainer(
    entryOffset: Float,
    predictiveBackProgress: Float,
    maxSheetHeight: Dp,
    scrollState: ScrollState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    LiquidGlassColumnSheetContainer(
        fallbackColor = AppColors.CardBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .materialBottomSheetMotion(entryOffset, predictiveBackProgress),
        contentModifier = Modifier
            .then(
                if (isLiquidGlass) {
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                } else {
                    Modifier
                }
            )
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            LiquidGlassIconButton(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.close),
                onClick = onClose,
                size = 44.dp,
                iconSize = 20.dp,
                contentColor = AppColors.TextPrimary,
                normalContainerColor = AppColors.BgGray
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        content()
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
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
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

    LiquidGlassButton(
        onClick = onChangeGoal,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(28.dp),
        tintedColor = bgGray.takeUnless { isLiquidGlass },
        contentColor = textPrimary
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
        CustomGoalInputPanel(
            value = customMinutesText,
            onValueChange = { raw ->
                customMinutesText = raw.filter { it.isDigit() }.take(4)
            },
            isValid = isCustomValid,
            onConfirm = { customMinutes?.let(onConfirm) }
        )
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

    LiquidGlassButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(28.dp),
        tintedColor = bgGray.takeUnless { LocalAppTheme.current == "liquid_glass" },
        contentColor = textSecondary
    ) {
        Text(stringResource(R.string.cancel), fontSize = 16.sp, color = textSecondary)
    }
}

@Composable
private fun CustomGoalInputPanel(
    value: String,
    onValueChange: (String) -> Unit,
    isValid: Boolean,
    onConfirm: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val shape = RoundedCornerShape(18.dp)
    val panelContent: @Composable ColumnScope.() -> Unit = {
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
                value = value,
                onValueChange = onValueChange,
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
                    onDone = { if (isValid) onConfirm() }
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isBlank()) {
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
            LiquidGlassButton(
                onClick = onConfirm,
                enabled = isValid,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                tintedColor = if (isValid) AppColors.Accent else bgGray,
                contentColor = if (isValid) AppColors.OnAccent else textSecondary
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isValid) AppColors.OnAccent else textSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "请输入 1-1439 分钟",
            fontSize = 12.sp,
            color = if (value.isBlank() || isValid) textSecondary else AppColors.Accent
        )
    }

    if (isLiquidGlass) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = bgGray,
            contentScrimColor = bgGray.copy(alpha = 0.34f),
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                content = panelContent
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgGray)
                .border(1.dp, AppColors.Divider, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = panelContent
        )
    }
}

@Composable
private fun GoalOptionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val textPrimary = AppColors.TextPrimary
    val textSecondary = AppColors.TextSecondary
    val bgGray = AppColors.BgGray
    val dividerColor = AppColors.Divider

    val shape = RoundedCornerShape(18.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
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

    if (isLiquidGlass) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = bgGray,
            contentScrimColor = bgGray.copy(alpha = 0.34f),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgGray)
                .border(1.dp, dividerColor, shape)
                .clickable(onClick = onClick)
        ) {
            content()
        }
    }
}
