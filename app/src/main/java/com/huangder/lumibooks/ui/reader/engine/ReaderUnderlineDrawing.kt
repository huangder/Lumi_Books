package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.huangder.lumibooks.domain.model.HighlightRule
import kotlin.math.sin

internal fun drawReaderUnderline(
    canvas: Canvas,
    paint: Paint,
    mode: Int,
    start: Float,
    end: Float,
    cross: Float,
    vertical: Boolean,
    density: Float
) {
    if (end <= start) return
    val oldEffect = paint.pathEffect
    fun line(offset: Float) {
        if (vertical) canvas.drawLine(cross + offset, start, cross + offset, end, paint)
        else canvas.drawLine(start, cross + offset, end, cross + offset, paint)
    }
    when (mode) {
        HighlightRule.UNDERLINE_STRAIGHT -> line(0f)
        HighlightRule.UNDERLINE_DOUBLE -> {
            line(-1.3f * density)
            line(1.3f * density)
        }
        HighlightRule.UNDERLINE_DASHED -> {
            paint.pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
            line(0f)
        }
        else -> {
            val path = Path()
            var position = start
            while (position < end) {
                val deviation = 0.8f * density * sin((position - start) / (11f * density) * 2.0 * Math.PI).toFloat()
                if (position == start) {
                    if (vertical) path.moveTo(cross, position) else path.moveTo(position, cross)
                } else if (vertical) path.lineTo(cross + deviation, position)
                else path.lineTo(position, cross + deviation)
                position = (position + density.coerceAtLeast(1f)).coerceAtMost(end)
            }
            if (vertical) path.lineTo(cross, end) else path.lineTo(end, cross)
            canvas.drawPath(path, paint)
        }
    }
    paint.pathEffect = oldEffect
}
