package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * 胶囊风格滑动条 —— 黑色填充 + 灰色底槽，无拇指圆点
 *
 * @param value     当前值
 * @param onValueChange  值变化回调
 * @param valueRange 值范围
 * @param trackHeight 轨道高度
 * @param activeColor  已选中部分颜色
 * @param inactiveColor 未选中部分颜色
 */
@Composable
fun PillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 40.dp,
    activeColor: Color = AppColors.TextPrimary,
    inactiveColor: Color = AppColors.BgGray
) {
    // 用 remember + mutableFloatStateOf 内部缓冲，只在 onValueChange 时通知外部
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    val fraction = remember(internalValue, valueRange) {
        ((internalValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 处理按下时的位置
                    val width = size.width.toFloat()
                    val downX = down.position.x
                    val newFraction = (downX / width).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                    internalValue = (newValue * 10f).roundToInt() / 10f
                    onValueChange(internalValue)
                    // 处理拖拽
                    horizontalDrag(down.id) { change ->
                        val x = change.position.x
                        val dragFraction = (x / width).coerceIn(0f, 1f)
                        val dragValue = valueRange.start + dragFraction * (valueRange.endInclusive - valueRange.start)
                        internalValue = (dragValue * 10f).roundToInt() / 10f
                        onValueChange(internalValue)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2

        // 底槽（灰色）
        drawRoundRect(
            color = inactiveColor,
            cornerRadius = CornerRadius(r, r),
            size = Size(w, h)
        )

        // 填充部分（黑色）
        val activeW = w * fraction
        if (activeW > 0f) {
            drawRoundRect(
                color = activeColor,
                cornerRadius = CornerRadius(r, r),
                size = Size(activeW, h)
            )
        }
    }
}
