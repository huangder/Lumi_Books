package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import kotlin.math.abs
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
    step: Float = 0.1f,
    trackHeight: androidx.compose.ui.unit.Dp = 28.dp,
    activeColor: Color = AppColors.TextPrimary,
    inactiveColor: Color = AppColors.BgGray
) {
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val fraction = remember(value, valueRange) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture
                    val gestureStartX = down.position.x
                    val gestureStartY = down.position.y
                    val gestureStartValue = latestValue.coerceIn(
                        valueRange.start,
                        valueRange.endInclusive
                    )
                    val rangeLength = valueRange.endInclusive - valueRange.start
                    val dragThresholdPx = 20.dp.toPx()
                    var dragStarted = false
                    var dragAnchorX = gestureStartX
                    var lastEmittedValue = gestureStartValue

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        if (!dragStarted) {
                            if (change.isConsumed) break
                            val totalDx = change.position.x - gestureStartX
                            val totalDy = change.position.y - gestureStartY
                            val absDx = abs(totalDx)
                            val absDy = abs(totalDy)

                            if (absDy >= dragThresholdPx && absDy >= absDx) break
                            if (absDx < dragThresholdPx || absDx < absDy * 1.35f) continue

                            dragStarted = true
                            dragAnchorX = change.position.x
                            change.consume()
                            continue
                        }

                        val relativeFraction = (change.position.x - dragAnchorX) / width
                        val dragValue = (gestureStartValue + relativeFraction * rangeLength)
                            .coerceIn(valueRange.start, valueRange.endInclusive)
                        val steppedValue = if (step > 0f) {
                            valueRange.start +
                                ((dragValue - valueRange.start) / step).roundToInt() * step
                        } else {
                            dragValue
                        }
                        val roundedValue = ((steppedValue * 1000f).roundToInt() / 1000f)
                            .coerceIn(valueRange.start, valueRange.endInclusive)
                        val crossedHysteresis = step <= 0f ||
                            abs(dragValue - lastEmittedValue) >= step * 0.65f
                        if (roundedValue != lastEmittedValue && crossedHysteresis) {
                            lastEmittedValue = roundedValue
                            latestOnValueChange(roundedValue)
                        }
                        change.consume()
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2

        // 底槽（灰色）—— 全圆角
        drawRoundRect(
            color = inactiveColor,
            cornerRadius = CornerRadius(r, r),
            size = Size(w, h)
        )

        // 填充部分（黑色）—— 左侧圆角、右侧直角
        // 用 clipRect 截掉右侧圆角，留下直角
        val activeW = w * fraction
        if (activeW > 0f) {
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, activeW, h)
            drawRoundRect(
                color = activeColor,
                cornerRadius = CornerRadius(r, r),
                size = Size(w, h)
            )
            drawContext.canvas.restore()
        }
    }
}
