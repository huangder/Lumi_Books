package com.huangder.lumibooks.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.DingliSong
import com.huangder.lumibooks.ui.theme.SansSerif
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReadingGoalSheet(
    visible: Boolean,
    todayReadingTime: Long,
    dailyGoal: Int,
    currentBook: Book?,
    onDismiss: () -> Unit,
    onSaveGoal: (Int) -> Unit,
    onTabBarVisibleChange: (Boolean) -> Unit
) {
    // 动画状态
    val sheetOffset = remember { Animatable(1f) }    // 0=显示, 1=隐藏
    val scrimAlpha = remember { Animatable(0f) }     // 背景遮罩透明度
    val contentScale = remember { Animatable(1f) }   // 内容缩放
    val contentAlpha = remember { Animatable(0f) }   // 内容透明度
    val isClosing = remember { mutableStateOf(false) }
    var showGoalPicker by remember { mutableStateOf(false) }

    // 打开动画
    LaunchedEffect(visible) {
        if (visible) {
            isClosing.value = false
            showGoalPicker = false
            onTabBarVisibleChange(false)
            // 背景模糊压暗
            launch { scrimAlpha.animateTo(1f, tween(300)) }
            // 内容缩小 5%
            launch { contentScale.animateTo(0.95f, tween(300, easing = FastOutSlowInEasing)) }
            // 弹窗滑入
            sheetOffset.animateTo(0f, tween(400, easing = AppEasing.Smooth))
            // 内容淡入
            contentAlpha.animateTo(1f, tween(200))
        }
    }

    // 关闭动画
    LaunchedEffect(isClosing.value) {
        if (isClosing.value) {
            // 内容淡出
            contentAlpha.animateTo(0f, tween(150))
            // 弹窗滑出
            launch { sheetOffset.animateTo(1f, tween(300, easing = AppEasing.Accelerate)) }
            // 背景恢复
            launch { scrimAlpha.animateTo(0f, tween(300)) }
            // 内容恢复大小
            contentScale.animateTo(1f, tween(300))
            delay(100)
            onTabBarVisibleChange(true)
            isClosing.value = false
            onDismiss()
        }
    }

    if (!visible && !isClosing.value) return

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景遮罩（模糊 + 压暗）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value)
                .background(Color.Black.copy(alpha = 0.5f * scrimAlpha.value))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { if (!isClosing.value) isClosing.value = true }
        )

        // 弹窗卡片（带动画）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = sheetOffset.value * size.height
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .shadow(
                    24.dp,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ambientColor = Color(0x18000000),
                    spotColor = Color(0x18000000)
                )
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpace.lg)
                    .alpha(contentAlpha.value)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { if (!isClosing.value) isClosing.value = true }
                    )
                }

                Spacer(Modifier.height(AppSpace.md))

                if (showGoalPicker) {
                    GoalPicker(
                        currentMinutes = dailyGoal,
                        onConfirm = { minutes -> onSaveGoal(minutes); showGoalPicker = false },
                        onCancel = { showGoalPicker = false }
                    )
                } else {
                    TodayReadingContent(
                        todayReadingTime = todayReadingTime,
                        dailyGoal = dailyGoal,
                        currentBook = currentBook,
                        onChangeGoal = { showGoalPicker = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayReadingContent(
    todayReadingTime: Long,
    dailyGoal: Int,
    currentBook: Book?,
    onChangeGoal: () -> Unit
) {
    val totalMinutes = (todayReadingTime / 1000 / 60).toInt()
    val goalMs = dailyGoal * 60 * 1000L
    val progress = (todayReadingTime.toFloat() / goalMs).coerceIn(0f, 1f)
    val remaining = ((goalMs - todayReadingTime) / 1000 / 60).coerceAtLeast(0).toInt()

    Text(
        text = "今日阅读",
        fontSize = AppType.Section,
        fontWeight = FontWeight.Bold,
        fontFamily = DingliSong,
        color = AppColors.TextPrimary
    )

    Spacer(Modifier.height(AppSpace.xl))

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$totalMinutes",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansSerif,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "分钟",
            fontSize = AppType.Body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    Spacer(Modifier.height(AppSpace.sm))

    Text(
        text = "目标 $dailyGoal 分钟",
        fontSize = AppType.BodySmall,
        color = AppColors.TextSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(AppSpace.lg))

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = AppColors.Accent,
        trackColor = AppColors.Divider
    )

    Spacer(Modifier.height(AppSpace.sm))

    Text(
        text = if (remaining > 0) "还差 $remaining 分钟达标" else "🎉 已达标！",
        fontSize = AppType.Caption,
        color = if (remaining > 0) AppColors.TextSecondary else AppColors.Accent,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(AppSpace.lg))

    if (currentBook != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.md))
                .background(AppColors.BgGray)
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("正在阅读", fontSize = AppType.Caption, color = AppColors.TextSecondary)
                Text(
                    text = currentBook.title,
                    fontSize = AppType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )
            }
            Text(
                text = "${(currentBook.readingProgress * 100).toInt()}%",
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.Accent
            )
        }
        Spacer(Modifier.height(AppSpace.lg))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(AppRadius.capsule))
            .background(AppColors.BgGray)
            .clickable(onClick = onChangeGoal),
        contentAlignment = Alignment.Center
    ) {
        Text("修改每日目标", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}

@Composable
private fun GoalPicker(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes) }

    Text(
        text = "设置每日阅读目标",
        fontSize = AppType.Section,
        fontWeight = FontWeight.Bold,
        fontFamily = DingliSong,
        color = AppColors.TextPrimary
    )

    Spacer(Modifier.height(AppSpace.xl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "$selectedMinutes",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansSerif,
            color = AppColors.Accent
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "分钟",
            fontSize = AppType.Body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    Spacer(Modifier.height(AppSpace.lg))

    WheelPicker(
        items = presets,
        initialItem = presets.indexOf(currentMinutes).coerceAtLeast(0),
        onItemSelected = { selectedMinutes = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    )

    Spacer(Modifier.height(AppSpace.xl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(AppRadius.capsule))
                .background(AppColors.BgGray)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) { Text("取消", fontSize = AppType.Body, color = AppColors.TextSecondary) }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(AppRadius.capsule))
                .background(AppColors.Accent)
                .clickable { onConfirm(selectedMinutes) },
            contentAlignment = Alignment.Center
        ) { Text("确定", fontSize = AppType.Body, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

@Composable
private fun WheelPicker(
    items: List<Int>,
    initialItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 48.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex = listState.firstVisibleItemIndex
    LaunchedEffect(centerIndex) {
        if (centerIndex in items.indices) onItemSelected(items[centerIndex])
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.BgGray)
        )
        LazyColumn(state = listState, flingBehavior = flingBehavior, modifier = Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(itemHeight)) }
            items.forEach { value ->
                item {
                    val isSelected = value == items.getOrNull(listState.firstVisibleItemIndex)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$value 分钟",
                            fontSize = if (isSelected) 22.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(itemHeight)) }
        }
    }
}
