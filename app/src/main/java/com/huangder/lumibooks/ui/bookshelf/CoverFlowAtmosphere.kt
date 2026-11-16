package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.huangder.lumibooks.ui.components.shiftHue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

/**
 * The stage's light field and its "pedestal of light": a softly polished slab the books stand on.
 * Colours come from the focused cover (picked only once the flow settles) and crossfade slowly;
 * every brush and path is cached, so a fling replays the same display list.
 */
@Composable
internal fun CoverFlowAtmosphere(
    floorY: Float,
    coverWidth: Float,
    pedestalDepth: Float,
    pedestalFront: Float,
    colors: List<Color>,
    modifier: Modifier,
    opacity: () -> Float
) {
    val dark = LocalIsDarkTheme.current
    val accent = AppColors.Accent
    val card = AppColors.CardBg
    val spec = tween<Color>(700)
    // States are read only inside the draw cache: the colour fade never recomposes the stage.
    val key = animateColorAsState(colors.getOrElse(0) { accent }, spec, label = "coverFlowKeyLight")
    val rimLight = animateColorAsState(colors.getOrElse(1) { accent.shiftHue(32f) }, spec, label = "coverFlowRimLight")
    val fill = animateColorAsState(colors.getOrElse(2) { accent.shiftHue(-28f) }, spec, label = "coverFlowFillLight")
    Box(modifier.graphicsLayer { alpha = opacity() }.drawWithCache {
        val w = size.width
        val keyColor = key.value
        // Key light: a tall, soft column above the centre book.
        val haloCenter = Offset(w * 0.5f, floorY * 0.5f)
        val haloRadius = w * 0.62f
        val halo = Brush.radialGradient(
            0f to keyColor.copy(alpha = if (dark) 0.28f else 0.16f),
            0.55f to keyColor.copy(alpha = if (dark) 0.09f else 0.05f),
            1f to Color.Transparent,
            center = haloCenter, radius = haloRadius
        )
        val rimCenter = Offset(w * 0.88f, floorY * 0.66f)
        val rimRadius = w * 0.5f
        val rim = Brush.radialGradient(
            listOf(rimLight.value.copy(alpha = if (dark) 0.13f else 0.09f), Color.Transparent),
            center = rimCenter, radius = rimRadius
        )
        val fillCenter = Offset(w * 0.12f, floorY * 0.6f)
        val fillRadius = w * 0.42f
        val fillLight = Brush.radialGradient(
            listOf(fill.value.copy(alpha = if (dark) 0.10f else 0.07f), Color.Transparent),
            center = fillCenter, radius = fillRadius
        )
        // Keep every ellipse fully inside the stage: no rectangular gradient boundary
        // underneath the folder row, including on a light background.
        val haloHeight = minOf(haloCenter.y, size.height - haloCenter.y) * 0.96f
        val rimHeight = minOf(rimCenter.y, size.height - rimCenter.y) * 0.96f
        val fillHeight = minOf(fillCenter.y, size.height - fillCenter.y) * 0.96f

        // Pedestal in gentle perspective: narrower back edge, wider front edge, then a thin face.
        val backY = floorY - coverWidth * 0.14f
        val frontY = floorY + pedestalDepth * 0.86f
        val backHalf = w * 0.40f
        val frontHalf = w * 0.47f
        val corner = 10.dp.toPx()
        val cx = w / 2f
        val top = Path().apply {
            moveTo(cx - backHalf + corner, backY)
            lineTo(cx + backHalf - corner, backY)
            quadraticTo(cx + backHalf, backY, cx + backHalf + corner * 0.25f, backY + corner * 0.5f)
            lineTo(cx + frontHalf, frontY - corner * 0.4f)
            quadraticTo(cx + frontHalf + corner * 0.1f, frontY, cx + frontHalf - corner, frontY)
            lineTo(cx - frontHalf + corner, frontY)
            quadraticTo(cx - frontHalf - corner * 0.1f, frontY, cx - frontHalf, frontY - corner * 0.4f)
            lineTo(cx - backHalf - corner * 0.25f, backY + corner * 0.5f)
            quadraticTo(cx - backHalf, backY, cx - backHalf + corner, backY)
            close()
        }
        val faceCorner = minOf(corner, pedestalFront)
        val face = Path().apply {
            moveTo(cx - frontHalf + corner, frontY)
            lineTo(cx + frontHalf - corner, frontY)
            quadraticTo(cx + frontHalf, frontY, cx + frontHalf, frontY + faceCorner * 0.3f)
            lineTo(cx + frontHalf - faceCorner * 0.2f, frontY + pedestalFront - faceCorner * 0.3f)
            quadraticTo(cx + frontHalf - faceCorner * 0.3f, frontY + pedestalFront,
                cx + frontHalf - corner, frontY + pedestalFront)
            lineTo(cx - frontHalf + corner, frontY + pedestalFront)
            quadraticTo(cx - frontHalf + faceCorner * 0.3f, frontY + pedestalFront,
                cx - frontHalf + faceCorner * 0.2f, frontY + pedestalFront - faceCorner * 0.3f)
            lineTo(cx - frontHalf, frontY + faceCorner * 0.3f)
            quadraticTo(cx - frontHalf, frontY, cx - frontHalf + corner, frontY)
            close()
        }
        // Frosted paper/acrylic, faintly tinted by the key light; never wood or metal.
        val surfaceTop = if (dark) lerp(Color.White, keyColor, 0.25f).copy(alpha = 0.075f)
            else lerp(lerp(card, Color(0xFFF1ECE3), 0.7f), keyColor, 0.06f).copy(alpha = 0.92f)
        val surfaceFront = if (dark) Color.White.copy(alpha = 0.035f)
            else lerp(card, Color(0xFFE6E0D5), 0.8f).copy(alpha = 0.9f)
        val surface = Brush.verticalGradient(listOf(surfaceTop, surfaceFront), startY = backY, endY = frontY)
        val faceBrush = Brush.verticalGradient(
            if (dark) listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.25f))
            else listOf(Color(0xFFDCD5C9).copy(alpha = 0.95f), Color(0xFFCDC5B7).copy(alpha = 0.9f)),
            startY = frontY, endY = frontY + pedestalFront
        )
        val backRim = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.25f to Color.White.copy(alpha = if (dark) 0.22f else 0.95f),
            0.75f to Color.White.copy(alpha = if (dark) 0.22f else 0.95f),
            1f to Color.Transparent,
            startX = cx - backHalf, endX = cx + backHalf
        )
        val frontLip = Color.White.copy(alpha = if (dark) 0.10f else 0.75f)
        val groundCenter = Offset(cx, frontY + pedestalFront)
        val groundRadius = frontHalf * 1.05f
        val ground = Brush.radialGradient(
            listOf(Color.Black.copy(alpha = if (dark) 0.45f else 0.10f), Color.Transparent),
            center = groundCenter, radius = groundRadius
        )
        // Pool of light where the key light lands, tinted by the cover.
        val poolCenter = Offset(cx, floorY + pedestalDepth * 0.15f)
        val poolRadius = coverWidth * 0.95f
        val pool = Brush.radialGradient(
            0f to keyColor.copy(alpha = if (dark) 0.30f else 0.20f),
            0.5f to keyColor.copy(alpha = if (dark) 0.10f else 0.06f),
            1f to Color.Transparent,
            center = poolCenter, radius = poolRadius
        )
        val poolScale = ((frontY - backY) / poolRadius).coerceIn(0.05f, 0.4f)
        val hairline = 1.dp.toPx()
        onDrawBehind {
            scale(1f, haloHeight / haloRadius, haloCenter) { drawCircle(halo, haloRadius, haloCenter) }
            scale(1f, rimHeight / rimRadius, rimCenter) { drawCircle(rim, rimRadius, rimCenter) }
            scale(1f, fillHeight / fillRadius, fillCenter) { drawCircle(fillLight, fillRadius, fillCenter) }
            scale(1f, 0.08f, groundCenter) { drawCircle(ground, groundRadius, groundCenter) }
            drawPath(face, faceBrush)
            drawPath(top, surface)
            scale(1f, poolScale, poolCenter) { drawCircle(pool, poolRadius, poolCenter) }
            drawLine(backRim, Offset(cx - backHalf + corner, backY + hairline / 2f),
                Offset(cx + backHalf - corner, backY + hairline / 2f), hairline)
            drawLine(frontLip, Offset(cx - frontHalf + corner, frontY),
                Offset(cx + frontHalf - corner, frontY), hairline * 0.75f)
        }
    })
}

/**
 * Reflect only the already recorded cover stage. This sibling never belongs to [covers], so the
 * render graph stays acyclic. No duplicate Coil requests, cover composition, or bitmap readback.
 */
@Composable
internal fun CoverFlowReflection(
    covers: GraphicsLayer,
    floorY: Float,
    blurEnabled: Boolean,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val dark = LocalIsDarkTheme.current
    val allowBlur = blurEnabled && !LocalReduceTransparency.current
    val blur = remember(density, allowBlur) {
        if (Build.VERSION.SDK_INT >= 31 && allowBlur) {
            val radius = with(density) { 2.2.dp.toPx() }
            android.graphics.RenderEffect.createBlurEffect(radius, radius,
                android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect()
        } else null
    }
    Box(modifier
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            clip = true
            renderEffect = blur
        }
        .drawWithCache {
            // Fades out just before the pedestal's front lip, so the reflection stays on its top.
            val mask = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.22f else 0.15f),
                0.22f to Color.White.copy(alpha = if (dark) 0.10f else 0.07f),
                0.62f to Color.White.copy(alpha = 0.02f),
                0.8f to Color.Transparent,
                endY = size.height
            )
            onDrawBehind {
                // Compress the reflection slightly, like a softly polished shelf surface.
                translate(top = floorY * 0.72f) {
                    scale(1f, -0.72f, Offset.Zero) { drawLayer(covers) }
                }
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        })
}
