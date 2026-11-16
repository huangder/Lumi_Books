package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Light skeuomorphic materials for the CoverFlow stage. Every brush and path is built once per
 * size/colour in drawWithCache; nothing here reads the scroll position while drawing, so a fling
 * only changes layer properties and never re-records these display lists.
 */

/** Paper block on the fore-edge plus a narrow contact shadow. Drawn behind the cover, unclipped. */
internal fun Modifier.coverFlowBookBody(dark: Boolean): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val edge = 2.5.dp.toPx()
    val radius = CornerRadius(2.dp.toPx())
    val rtl = layoutDirection == LayoutDirection.Rtl
    val paper = if (dark) Color(0xFFA7A199) else Color(0xFFEDE8DE)
    val paperShade = if (dark) Color(0xFF7D7871) else Color(0xFFD9D2C5)
    val leaf = Color.Black.copy(alpha = if (dark) 0.22f else 0.10f)
    val contactCenter = Offset(w / 2f, h + 1.5.dp.toPx())
    val contactRadius = w * 0.48f
    val contact = Brush.radialGradient(
        0f to Color.Black.copy(alpha = if (dark) 0.42f else 0.20f),
        0.6f to Color.Black.copy(alpha = if (dark) 0.12f else 0.06f),
        1f to Color.Transparent,
        center = contactCenter, radius = contactRadius
    )
    // The block sits inside the boards' height; only its fore-edge strip shows.
    val blockLeft = if (rtl) -edge else w * 0.5f
    val blockSize = Size(w * 0.5f + edge, h - 3.dp.toPx())
    val blockBrush = Brush.horizontalGradient(
        if (rtl) listOf(paperShade, paper) else listOf(paper, paperShade),
        startX = blockLeft, endX = blockLeft + blockSize.width
    )
    val leafXs = (1..2).map { i -> if (rtl) -edge * i / 3f else w + edge * i / 3f }
    onDrawBehind {
        scale(1f, 0.11f, contactCenter) { drawCircle(contact, contactRadius, contactCenter) }
        drawRoundRect(blockBrush, Offset(blockLeft, 1.5.dp.toPx()), blockSize, radius)
        leafXs.forEach { x ->
            drawLine(leaf, Offset(x, 3.dp.toPx()), Offset(x, h - 3.dp.toPx()), 0.6.dp.toPx())
        }
    }
}

/** Hinge crease near the spine and a hairline rim, over the cover artwork. */
internal fun Modifier.coverFlowBookSurface(dark: Boolean): Modifier = drawWithCache {
    val w = size.width
    val rtl = layoutDirection == LayoutDirection.Rtl
    val band = w * 0.09f
    val left = if (rtl) w - band else 0f
    val stops = arrayOf(
        0f to Color.Black.copy(alpha = if (dark) 0.26f else 0.16f),
        0.32f to Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
        0.5f to Color.Black.copy(alpha = if (dark) 0.16f else 0.10f),
        0.72f to Color.White.copy(alpha = if (dark) 0.03f else 0.06f),
        1f to Color.Transparent
    )
    // The gradient always starts at the spine edge; in RTL that is the right side.
    val crease = Brush.horizontalGradient(*stops,
        startX = if (rtl) w else 0f, endX = if (rtl) w - band else band)
    val rim = Color.White.copy(alpha = if (dark) 0.08f else 0.18f)
    val topLight = Color.White.copy(alpha = if (dark) 0.14f else 0.32f)
    val stroke = Stroke(1f)
    onDrawWithContent {
        drawContent()
        drawRect(crease, Offset(left, 0f), Size(band, size.height))
        drawRect(rim, style = stroke)
        drawLine(topLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
    }
}

/**
 * A soft diagonal gloss. The gradient is recorded once; [tilt] only moves the layer, so the cover
 * catches the light as it turns without any per-frame drawing.
 */
@Composable
internal fun BoxScope.CoverFlowSheen(dark: Boolean, tilt: (() -> Float)?) {
    Box(Modifier.matchParentSize()
        .graphicsLayer { if (tilt != null) translationX = -tilt() * size.width * 0.45f }
        .drawWithCache {
            val peak = if (dark) 0.06f else 0.10f
            val brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.38f to Color.White.copy(alpha = peak * 0.35f),
                0.5f to Color.White.copy(alpha = peak),
                0.62f to Color.White.copy(alpha = peak * 0.3f),
                1f to Color.Transparent,
                start = Offset(-size.width * 0.3f, 0f),
                end = Offset(size.width * 0.9f, size.height * 0.75f)
            )
            onDrawBehind { drawRect(brush) }
        })
}

/** Warm paper tone for the info label, derived from the theme card colour. */
internal fun coverFlowPaperColor(card: Color, dark: Boolean): Color =
    lerp(card, if (dark) Color(0xFF2B2825) else Color(0xFFF8F2E7), if (dark) 0.55f else 0.6f)

/** Inner light of a paper label: brighter top edge and a faint fall-off, cached. */
internal fun Modifier.coverFlowPaperSheen(dark: Boolean): Modifier = drawWithCache {
    val fall = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.05f else 0.45f),
        0.55f to Color.Transparent
    )
    val edge = Color.White.copy(alpha = if (dark) 0.10f else 0.85f)
    onDrawBehind {
        drawRect(fall)
        drawLine(edge, Offset(size.height * 0.08f, 0.75f), Offset(size.width - size.height * 0.08f, 0.75f), 1.5f)
    }
}

/** Swallow-tailed bookmark ribbon path in a w×h box. */
internal fun ribbonPath(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h)
    lineTo(w / 2f, h - w * 0.55f)
    lineTo(0f, h)
    close()
}
