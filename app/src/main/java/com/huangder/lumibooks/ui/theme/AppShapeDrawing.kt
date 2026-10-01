package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate

/** Shares the exact clipping outline with Canvas borders, charts and crop masks. */
internal fun Outline.toAppPath(): Path = when (this) {
    is Outline.Generic -> Path().also { it.addPath(path) }
    is Outline.Rounded -> Path().also { it.addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().also { it.addRect(rect) }
}

internal fun DrawScope.drawAppShape(
    shape: Shape,
    color: Color,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    style: DrawStyle = Fill
) {
    if (size.width <= 0f || size.height <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(topLeft.x, topLeft.y) { drawOutline(outline, color, style = style) }
}

internal fun DrawScope.drawAppShape(
    shape: Shape,
    brush: Brush,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    style: DrawStyle = Fill
) {
    if (size.width <= 0f || size.height <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(topLeft.x, topLeft.y) { drawOutline(outline, brush, style = style) }
}
