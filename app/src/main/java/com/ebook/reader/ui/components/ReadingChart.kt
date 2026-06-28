package com.ebook.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ebook.reader.ui.statistics.DailyReading
import com.ebook.reader.util.TimeUtils

@Composable
fun ReadingChart(
    data: List<DailyReading>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    if (data.isEmpty()) return

    val maxDuration = data.maxOf { it.duration }.toFloat()
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barWidth = width / (data.size * 2 + 1)
        val chartHeight = height - 40.dp.toPx()

        // 绘制背景
        drawRect(
            color = backgroundColor,
            size = Size(width, chartHeight)
        )

        // 绘制柱状图
        data.forEachIndexed { index, dailyReading ->
            val barHeight = if (maxDuration > 0) {
                (dailyReading.duration / maxDuration) * chartHeight
            } else 0f

            val x = barWidth * (index * 2 + 1)
            val y = chartHeight - barHeight

            // 绘制柱子
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )

            // 绘制日期标签
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = textColor.toArgb()
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(
                    dailyReading.dayOfWeek,
                    x + barWidth / 2,
                    height - 10.dp.toPx(),
                    paint
                )
            }

            // 绘制时长标签
            if (dailyReading.duration > 0) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = textColor.toArgb()
                        textSize = 8.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(
                        TimeUtils.formatDurationShort(dailyReading.duration),
                        x + barWidth / 2,
                        y - 4.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}
