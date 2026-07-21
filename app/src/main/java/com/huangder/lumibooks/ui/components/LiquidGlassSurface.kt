package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun ProvideLiquidGlassBackdrop(
    backdrop: Backdrop?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop, content = content)
}

fun Modifier.liquidGlassBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    isDark: Boolean,
    transparency: Float,
    contentScrimColor: Color = Color.Transparent,
    pressProgress: Float = 0f,
    scaleOnPress: Boolean = true,
    outlineWidth: Dp = 0.8.dp,
    highlightAlpha: Float = 0.18f
): Modifier {
    val surfaceColor = if (isDark) {
        Color(0xFF101012).copy(alpha = 0.34f - transparency * 0.24f)
    } else {
        Color.White.copy(alpha = 0.32f - transparency * 0.28f)
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f + pressProgress * 0.14f),
                Color.White.copy(alpha = 0.10f + pressProgress * 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.82f + pressProgress * 0.14f),
                Color.White.copy(alpha = 0.22f + pressProgress * 0.12f)
            )
        )
    }
    val scale = if (scaleOnPress) 1f + 0.045f * pressProgress else 1f

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((1.dp + 5.dp * (1f - transparency)).toPx())
            lens(
                (12.dp + 4.dp * pressProgress).toPx(),
                (24.dp + 4.dp * pressProgress).toPx(),
                chromaticAberration = pressProgress > 0.05f
            )
        },
        layerBlock = {
            scaleX = scale
            scaleY = scale
        },
        highlight = {
            Highlight.Default.copy(alpha = highlightAlpha + pressProgress * 0.46f)
        },
        shadow = {
            Shadow(
                radius = 14.dp + 4.dp * pressProgress,
                alpha = 0.24f + pressProgress * 0.10f
            )
        },
        innerShadow = {
            InnerShadow(
                radius = 5.dp + 3.dp * pressProgress,
                alpha = 0.14f + pressProgress * 0.32f
            )
        },
        onDrawSurface = {
            drawRect(surfaceColor)
            if (contentScrimColor.alpha > 0f) {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            contentScrimColor.copy(
                                alpha = (contentScrimColor.alpha * 1.28f).coerceAtMost(1f)
                            ),
                            contentScrimColor.copy(alpha = contentScrimColor.alpha * 0.72f)
                        )
                    )
                )
            }
        }
    ).border(outlineWidth, borderBrush, shape)
}

@Composable
fun Modifier.liquidGlassSheetSurface(
    fallbackColor: Color,
    shape: Shape,
    backdrop: Backdrop? = null
): Modifier {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current

    return if (isLiquidGlass) {
        val floatingShape = RoundedCornerShape(28.dp)
        val sheetTransparency = (transparency - 0.10f).coerceIn(0f, 0.90f)
        val scrimAlpha = (0.81f - sheetTransparency * 0.25f).coerceIn(0.58f, 0.81f)
        val floatingSurface = padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .shadow(
                elevation = 22.dp,
                shape = floatingShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.40f else 0.30f)
            )

        if (activeBackdrop != null) {
            floatingSurface.liquidGlassBackdrop(
                backdrop = activeBackdrop,
                shape = floatingShape,
                isDark = isDark,
                transparency = sheetTransparency,
                contentScrimColor = fallbackColor.copy(alpha = scrimAlpha),
                scaleOnPress = false,
                outlineWidth = 1.1.dp,
                highlightAlpha = 0.30f
            )
        } else {
            floatingSurface
                .clip(floatingShape)
                .background(fallbackColor)
                .border(
                    width = 1.1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.34f else 0.88f),
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.22f)
                        )
                    ),
                    shape = floatingShape
                )
        }
    } else {
        shadow(
            elevation = 24.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.16f)
        )
            .clip(shape)
            .background(fallbackColor)
    }
}

@Composable
fun LiquidGlassSurface(
    shape: Shape,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    contentScrimColor: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "liquidGlassSurfacePress"
    )

    val surfaceModifier = if (isLiquidGlass && activeBackdrop != null) {
        Modifier.liquidGlassBackdrop(
            backdrop = activeBackdrop,
            shape = shape,
            isDark = isDark,
            transparency = transparency,
            contentScrimColor = contentScrimColor,
            pressProgress = pressProgress
        )
    } else {
        val scale = 1f + 0.045f * pressProgress
        Modifier
            .clip(shape)
            .background(fallbackColor)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            indication = null,
            interactionSource = interactionSource,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(surfaceModifier)
            .clip(shape)
            .then(clickModifier),
        contentAlignment = contentAlignment,
        content = content
    )
}
