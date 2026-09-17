package com.huangder.lumibooks.ui.bookshelf

import android.os.Build
import androidx.compose.foundation.layout.Box
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

/** Cached gradients illuminate the stage without another bitmap or background blur capture. */
@Composable
internal fun CoverFlowAtmosphere(
    floorY: Float,
    modifier: Modifier,
    opacity: () -> Float
) {
    val dark = LocalIsDarkTheme.current
    val accent = AppColors.Accent
    Box(modifier.graphicsLayer { alpha = opacity() }.drawWithCache {
        val haloCenter = Offset(size.width * 0.43f, floorY * 0.48f)
        val haloRadius = size.width * 0.76f
        val halo = Brush.radialGradient(
            listOf(Color(0xFFACA4CE).copy(alpha = if (dark) 0.19f else 0.13f), Color.Transparent),
            center = haloCenter, radius = haloRadius
        )
        val rimCenter = Offset(size.width * 0.88f, floorY * 0.68f)
        val rimRadius = size.width * 0.53f
        val rim = Brush.radialGradient(
            listOf(Color(0xFF8AAAC9).copy(alpha = if (dark) 0.12f else 0.09f), Color.Transparent),
            center = rimCenter, radius = rimRadius
        )
        val floorCenter = Offset(size.width * 0.5f, floorY)
        val floor = Brush.radialGradient(
            listOf(accent.copy(alpha = if (dark) 0.23f else 0.15f),
                accent.copy(alpha = if (dark) 0.07f else 0.04f), Color.Transparent),
            center = floorCenter, radius = size.width * 0.65f
        )
        val haloHeight = minOf(haloCenter.y, size.height - haloCenter.y) * 0.96f
        val rimHeight = minOf(rimCenter.y, size.height - rimCenter.y) * 0.96f
        onDrawBehind {
            // Keep the ellipse fully inside the stage: no rectangular gradient boundary
            // underneath the folder row, including on a light background.
            scale(1f, haloHeight / haloRadius, haloCenter) { drawCircle(halo, haloRadius, haloCenter) }
            scale(1f, rimHeight / rimRadius, rimCenter) { drawCircle(rim, rimRadius, rimCenter) }
            scale(1f, 0.20f, floorCenter) { drawCircle(floor, size.width * 0.65f, floorCenter) }
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
    val blur = remember(density, blurEnabled) {
        if (Build.VERSION.SDK_INT >= 31 && blurEnabled) {
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
            val mask = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.25f else 0.17f),
                0.22f to Color.White.copy(alpha = if (dark) 0.12f else 0.08f),
                0.7f to Color.White.copy(alpha = 0.025f),
                1f to Color.Transparent,
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
