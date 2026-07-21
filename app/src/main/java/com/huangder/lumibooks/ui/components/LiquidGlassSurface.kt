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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun LiquidGlassSurface(
    shape: Shape,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
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
    val scale = 1f + 0.045f * pressProgress

    val surfaceModifier = if (isLiquidGlass && activeBackdrop != null) {
        val surfaceColor = if (isDark) {
            Color(0xFF101012).copy(alpha = 0.34f - transparency * 0.24f)
        } else {
            Color.White.copy(alpha = 0.32f - transparency * 0.28f)
        }
        val borderColor = if (isDark) {
            Color.White.copy(alpha = 0.16f + pressProgress * 0.12f)
        } else {
            Color.White.copy(alpha = 0.48f + pressProgress * 0.20f)
        }
        Modifier
            .drawBackdrop(
                backdrop = activeBackdrop,
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
                    Highlight.Default.copy(alpha = 0.18f + pressProgress * 0.46f)
                },
                shadow = {
                    Shadow(
                        radius = 12.dp + 4.dp * pressProgress,
                        alpha = 0.18f + pressProgress * 0.10f
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 5.dp + 3.dp * pressProgress,
                        alpha = 0.14f + pressProgress * 0.32f
                    )
                },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .border(0.7.dp, borderColor, shape)
    } else {
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
