package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.DropShadowPainter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** A centered, diffuse halo that stays outside even a translucent menu surface. */
internal fun Modifier.selectionMenuOuterShadow(shape: Shape, dark: Boolean): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        val radius = 12.dp
        val spread = 0.dp
        val painter = DropShadowPainter(
            shape = shape,
            shadow = Shadow(
                radius = radius,
                spread = spread,
                color = Color.Black.copy(alpha = if (dark) 0.18f else 0.10f)
            )
        )
        // Finish the blur before removing the caster. Rasterizing this small cached
        // mask makes its interior truly transparent, independent of GPU clip order.
        val padding = ceil((radius * 3 + spread).toPx()).toInt()
        val shadowSize = Size(size.width + padding * 2, size.height + padding * 2)
        val bitmap = ImageBitmap(ceil(shadowSize.width).toInt(), ceil(shadowSize.height).toInt())
        val menuSize = size
        CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), shadowSize) {
            translate(padding.toFloat(), padding.toFloat()) {
                with(painter) { draw(menuSize) }
                drawPath(path, Color.Transparent, blendMode = BlendMode.Clear)
            }
        }
        onDrawWithContent {
            // Resolve the glass first. The shadow must never become an input to
            // its translucent/blurred surface, including the antialiased edge.
            drawContent()
            clipPath(path, ClipOp.Difference) {
                drawImage(bitmap, topLeft = Offset(-padding.toFloat(), -padding.toFloat()))
            }
        }
    }
