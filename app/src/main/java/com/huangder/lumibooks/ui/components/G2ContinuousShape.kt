package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A continuous corner whose curvature reaches zero where it joins each edge.
 */
class G2ContinuousCornerShape(internal val cornerRadius: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val radius = cornerRadius.coerceIn(0f, minOf(width, height) / 2f)
        if (radius == 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, width, height))

        return Outline.Generic(Path().apply {
            moveTo(radius, 0f)
            lineTo(width - radius, 0f)
            addSuperellipseCorner(width - radius, radius, radius, -PI / 2.0, 0.0)
            lineTo(width, height - radius)
            addSuperellipseCorner(width - radius, height - radius, radius, 0.0, PI / 2.0)
            lineTo(radius, height)
            addSuperellipseCorner(radius, height - radius, radius, PI / 2.0, PI)
            lineTo(0f, radius)
            addSuperellipseCorner(radius, radius, radius, PI, PI * 1.5)
            close()
        })
    }
}

private fun Path.addSuperellipseCorner(
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Double,
    endAngle: Double
) {
    repeat(96) { index ->
        val angle = startAngle + (endAngle - startAngle) * (index + 1) / 96.0
        val cosine = cos(angle)
        val sine = sin(angle)
        lineTo(
            centerX + radius * (sign(cosine) * sqrt(abs(cosine))).toFloat(),
            centerY + radius * (sign(sine) * sqrt(abs(sine))).toFloat()
        )
    }
}
