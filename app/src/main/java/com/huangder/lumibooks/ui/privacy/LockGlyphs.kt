package com.huangder.lumibooks.ui.privacy

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.icons.AppIcons

/**
 * The app's own lock icon (Fluent, as everywhere else). Opening cross-fades the closed and
 * open lock with a small bounce. The icon fills [iconSize] inside a larger canvas, so the
 * bounce never clips. Tint and progress are read in the draw phase only.
 */
@Composable
internal fun LockIcon(
    iconSize: Dp,
    tint: () -> Color,
    modifier: Modifier = Modifier,
    openProgress: () -> Float = { 0f }
) {
    val closed = rememberVectorPainter(AppIcons.Lock)
    val opened = rememberVectorPainter(AppIcons.LockOpen)
    Canvas(modifier) {
        val side = iconSize.toPx()
        val open = openProgress().coerceIn(0f, 1f)
        val bounce = 1f + 0.08f * (1f - kotlin.math.abs(open * 2f - 1f))
        val filter = ColorFilter.tint(tint())
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(bounce, pivot = Offset(side / 2f, side / 2f)) {
                if (open < 1f) with(closed) { draw(Size(side, side), alpha = 1f - open, colorFilter = filter) }
                if (open > 0f) with(opened) { draw(Size(side, side), alpha = open, colorFilter = filter) }
            }
        }
    }
}

/** Progress ring around the pull indicator's lock. */
internal fun DrawScope.drawPullRing(progress: Float, color: Color, track: Color) {
    val stroke = size.minDimension * 0.06f
    val diameter = size.minDimension - stroke
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)
    if (track.alpha > 0f) drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
    if (progress > 0f) {
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
internal fun BackspaceGlyph(color: Color, modifier: Modifier = Modifier) {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.graphicsLayer { if (mirrored) scaleX = -1f }) {
        val w = size.width
        val h = size.height
        val strokeWidth = minOf(w, h) * 0.075f
        val top = h * 0.24f
        val bottom = h * 0.76f
        val notch = w * 0.32f
        val outline = Path().apply {
            moveTo(w * 0.10f, h / 2f)
            lineTo(notch, top)
            lineTo(w * 0.90f, top)
            lineTo(w * 0.90f, bottom)
            lineTo(notch, bottom)
            close()
        }
        drawPath(outline, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val cx = (notch + w * 0.90f) / 2f + w * 0.02f
        val d = h * 0.11f
        drawLine(color, Offset(cx - d, h / 2f - d), Offset(cx + d, h / 2f + d), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(cx + d, h / 2f - d), Offset(cx - d, h / 2f + d), strokeWidth, StrokeCap.Round)
    }
}
