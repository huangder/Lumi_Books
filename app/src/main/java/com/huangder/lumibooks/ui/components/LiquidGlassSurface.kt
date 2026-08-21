package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassHdrHighlightEnabled
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
/** Visible glass depth. The first surface is level 1; level 4 degrades to a
 * normal surface so a dialog can never become an unreadable stack of glass. */
val LocalLiquidGlassLayer = staticCompositionLocalOf { 0 }

private fun tonalGlassHighlight(baseColor: Color): Color {
    val opaqueBase = baseColor.copy(alpha = 1f)
    val lightenFraction = when {
        opaqueBase.luminance() >= 0.92f -> 0f
        opaqueBase.luminance() <= 0.08f -> 0.28f
        else -> 0.40f
    }
    return lerp(opaqueBase, Color.White, lightenFraction)
}

/**
 * Creates a highlight in scRGB so only the pressed spot can use luminance above
 * the SDR white point. Keeping every gradient stop in the same color space is
 * important: mixed sRGB/scRGB stops can produce banding on some HDR pipelines.
 */
private fun hdrHighlightColor(
    source: Color,
    luminanceScale: Float,
    alpha: Float
): Color {
    val extended = source.convert(ColorSpaces.ExtendedSrgb)
    return Color(
        red = (extended.red * luminanceScale).coerceIn(-0.5f, 7.5f),
        green = (extended.green * luminanceScale).coerceIn(-0.5f, 7.5f),
        blue = (extended.blue * luminanceScale).coerceIn(-0.5f, 7.5f),
        alpha = alpha.coerceIn(0f, 1f),
        colorSpace = ColorSpaces.ExtendedSrgb
    )
}

@Composable
fun ProvideLiquidGlassBackdrop(
    backdrop: Backdrop?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop, content = content)
}

internal fun Modifier.liquidGlassBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    lensShape: Shape = shape,
    isDark: Boolean,
    transparency: Float,
    contentScrimColor: Color = Color.Transparent,
    tintColor: Color? = null,
    pressProgress: Float = 0f,
    scaleOnPress: Boolean = true,
    buttonInteractionState: LiquidGlassButtonInteractionState? = null,
    buttonInteraction: Boolean = false,
    motionEnabled: Boolean = true,
    outlineWidth: Dp = 0.8.dp,
    highlightAlpha: Float = 0.18f,
    highlightColor: Color = Color.White,
    shadowRadius: Dp = 24.dp,
    shadowAlpha: Float = 0.16f,
    pressedShadowAlpha: Float = 0.08f
): Modifier {
    val lensSupported = supportsLiquidGlassLens(lensShape)
    val tonalHighlight = tonalGlassHighlight(highlightColor)
    val surfaceColor = if (isDark) {
        Color(0xFF101012).copy(alpha = 0.34f - transparency * 0.24f)
    } else {
        Color.White.copy(alpha = 0.32f - transparency * 0.28f)
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                tonalHighlight.copy(alpha = 0.46f + pressProgress * 0.14f),
                tonalHighlight.copy(alpha = 0.14f + pressProgress * 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                tonalHighlight.copy(alpha = 0.82f + pressProgress * 0.14f),
                tonalHighlight.copy(alpha = 0.22f + pressProgress * 0.12f)
            )
        )
    }
    val glassModifier = drawBackdrop(
        backdrop = backdrop,
        // The lens library only accepts rounded rectangular/corner-based shapes. A G2
        // superellipse is clipped and outlined with its real path outside this effect;
        // the same-radius rounded rectangle is only the SDF proxy used by the lens.
        shape = { lensShape },
        effects = {
            vibrancy()
            if (transparency < 1f) {
                blur((6.dp * (1f - transparency)).toPx())
            }
            if (lensSupported) {
                if (buttonInteraction) {
                    lens(12.dp.toPx(), 24.dp.toPx())
                } else {
                    lens(
                        (12.dp + 4.dp * pressProgress).toPx(),
                        (24.dp + 4.dp * pressProgress).toPx(),
                        chromaticAberration = pressProgress > 0.05f
                    )
                }
            }
        },
        layerBlock = {
            if (buttonInteraction) {
                val interactionProgress = buttonInteractionState?.pressProgress ?: pressProgress
                val transform = liquidGlassButtonLayerTransform(
                    width = size.width,
                    height = size.height,
                    pressProgress = interactionProgress,
                    dragOffset = buttonInteractionState?.offset ?: Offset.Zero,
                    expansionPx = 4.dp.toPx(),
                    motionEnabled = motionEnabled
                )
                translationX = transform.translationX
                translationY = transform.translationY
                scaleX = transform.scaleX
                scaleY = transform.scaleY
            } else {
                val scale = if (scaleOnPress) 1f + 0.045f * pressProgress else 1f
                scaleX = scale
                scaleY = scale
            }
        },
        highlight = {
            Highlight.Default.copy(
                alpha = if (buttonInteraction) 0f else highlightAlpha + pressProgress * 0.46f
            )
        },
        shadow = {
            Shadow(
                radius = shadowRadius + 4.dp * pressProgress,
                alpha = if (buttonInteraction) 0f else {
                    shadowAlpha + pressProgress * pressedShadowAlpha
                }
            )
        },
        innerShadow = {
            InnerShadow(
                radius = 4.dp + 2.dp * pressProgress,
                alpha = if (buttonInteraction) 0f else 0.04f + pressProgress * 0.08f
            )
        },
        onDrawSurface = {
            drawRect(surfaceColor)
            if (tintColor != null) {
                drawRect(tintColor, blendMode = BlendMode.Hue)
                drawRect(tintColor.copy(alpha = 0.75f))
            } else if (contentScrimColor.alpha > 0f) {
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
    )
    return if (outlineWidth > 0.dp && !buttonInteraction) {
        glassModifier.border(outlineWidth, borderBrush, shape)
    } else {
        glassModifier
    }
}

internal fun supportsLiquidGlassLens(shape: Shape): Boolean =
    shape is CornerBasedShape

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
fun LiquidGlassSheetContainer(
    fallbackColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val parentBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    val containerBackdrop = rememberLayerBackdrop()
    val contentShape = if (isLiquidGlass) RoundedCornerShape(28.dp) else shape

    // 弹层保持手机端宽度（平板不拉长，避免内容排版被拉伸）。
    // widthIn 必须位于调用方 fillMaxWidth 之前（外层）才能生效，
    // 因此用外层 Box 居中 + 内层限宽容器实现。
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (isLiquidGlass) Modifier.layerBackdrop(containerBackdrop) else Modifier
                    )
                    .liquidGlassSheetSurface(
                        fallbackColor = fallbackColor,
                        shape = shape,
                        backdrop = parentBackdrop
                    )
            )
            ProvideLiquidGlassBackdrop(containerBackdrop.takeIf { isLiquidGlass }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLiquidGlass) {
                                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                            } else {
                                Modifier
                            }
                        )
                        // Match the inset glass surface. Caller padding stays inside this
                        // boundary so edge buttons retain room for their pressed stretch.
                        .clip(contentShape)
                        .clipToBounds()
                        .then(contentModifier),
                    contentAlignment = contentAlignment,
                    content = content
                )
            }
        }
    }
}

@Composable
fun LiquidGlassColumnSheetContainer(
    fallbackColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassSheetContainer(
        fallbackColor = fallbackColor,
        shape = shape,
        modifier = modifier,
        backdrop = backdrop
    ) {
        Column(
            modifier = contentModifier.fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

@Composable
fun LiquidGlassSurface(
    shape: Shape,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    contentScrimColor: Color = Color.Transparent,
    tintColor: Color? = null,
    transparencyOverride: Float? = null,
    forceFallback: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactive: Boolean = onClick != null,
    effectPadding: Dp = 0.dp,
    outlineWidth: Dp = 0.8.dp,
    highlightColor: Color = fallbackColor,
    highlightAlpha: Float = 0.18f,
    decorationModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val glassLayer = LocalLiquidGlassLayer.current + 1
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" &&
        LocalLiquidGlassCapability.current.supported &&
        !forceFallback && glassLayer <= 3
    val isDark = LocalIsDarkTheme.current
    val transparency = (transparencyOverride ?: LocalLiquidGlassTransparency.current)
        .coerceIn(0f, 1f)
    val hdrHighlightEnabled = LocalLiquidGlassHdrHighlightEnabled.current
    val motionEnabled = LocalMotionEnabled.current
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    val density = LocalDensity.current
    val lensShape = remember(shape, density) {
        if (shape is G2ContinuousCornerShape) {
            RoundedCornerShape(with(density) { shape.cornerRadius.toDp() })
        } else {
            shape
        }
    }
    val interactionState = if (interactive) {
        val animationScope = rememberCoroutineScope()
        remember(animationScope) { LiquidGlassButtonInteractionState(animationScope) }
    } else {
        null
    }
    val latestOnClick = rememberUpdatedState(onClick)
    val handlesButtonGesture = isLiquidGlass && enabled && interactionState != null &&
        supportsLiquidGlassLens(lensShape)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = null,
            indication = if (handlesButtonGesture) null else LocalIndication.current,
            enabled = enabled,
            role = Role.Button
        ) {
            latestOnClick.value?.invoke()
        }
    } else {
        Modifier
    }
    val interactionModifier = if (interactionState != null) {
        Modifier.liquidGlassButtonGesture(
            state = interactionState,
            enabled = handlesButtonGesture
        )
    } else {
        Modifier
    }
    val surfaceModifier = if (isLiquidGlass && activeBackdrop != null) {
        Modifier.liquidGlassBackdrop(
            backdrop = activeBackdrop,
            shape = shape,
            lensShape = lensShape,
            isDark = isDark,
            transparency = transparency,
            contentScrimColor = contentScrimColor,
            tintColor = tintColor,
            scaleOnPress = false,
            buttonInteractionState = interactionState,
            buttonInteraction = handlesButtonGesture,
            motionEnabled = motionEnabled,
            outlineWidth = outlineWidth,
            highlightColor = highlightColor,
            highlightAlpha = highlightAlpha,
            shadowRadius = 24.dp,
            shadowAlpha = 0f,
            pressedShadowAlpha = 0.02f
        )
    } else if (isLiquidGlass) {
        val fallbackScrim = if (tintColor != null) {
            tintColor.copy(alpha = 0.75f)
        } else if (contentScrimColor.alpha > 0f) {
            contentScrimColor
        } else {
            fallbackColor.copy(alpha = 0.42f)
        }
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        fallbackScrim.copy(alpha = (fallbackScrim.alpha * 1.20f).coerceAtMost(0.88f)),
                        fallbackScrim.copy(alpha = fallbackScrim.alpha * 0.76f)
                    )
                )
            )
            .then(
                if (outlineWidth > 0.dp && !interactive) {
                    Modifier.border(
                        outlineWidth,
                        Brush.verticalGradient(
                            listOf(
                                tonalGlassHighlight(fallbackColor).copy(alpha = 0.68f),
                                tonalGlassHighlight(fallbackColor).copy(alpha = 0.26f),
                                tonalGlassHighlight(fallbackColor).copy(alpha = 0.08f)
                            )
                        ),
                        shape
                    )
                } else {
                    Modifier
                }
            )
    } else {
        Modifier.clip(shape).background(fallbackColor)
    }
    val activeInteractionState = interactionState.takeIf { handlesButtonGesture }
    val contentTransformModifier = if (activeInteractionState != null) {
        Modifier.graphicsLayer {
            val surfaceTransform = liquidGlassButtonLayerTransform(
                width = size.width,
                height = size.height,
                pressProgress = activeInteractionState.pressProgress,
                dragOffset = activeInteractionState.offset,
                expansionPx = 4.dp.toPx(),
                motionEnabled = motionEnabled
            )
            val contentTransform = liquidGlassButtonContentTransform(surfaceTransform)
            translationX = contentTransform.translationX
            translationY = contentTransform.translationY
            scaleX = contentTransform.scaleX
            scaleY = contentTransform.scaleY
            clip = false
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(decorationModifier)
            .then(clickModifier)
            .then(interactionModifier),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(effectPadding)
                .then(surfaceModifier)
                .clip(shape)
                .drawWithContent {
                val pressProgress = interactionState?.pressProgress ?: 0f
                val highlightPosition = interactionState?.position
                if (handlesButtonGesture && highlightPosition != null && pressProgress > 0.001f) {
                    val highlightCenter = Offset(
                        x = highlightPosition.x.coerceIn(0f, size.width),
                        y = highlightPosition.y.coerceIn(0f, size.height)
                    )
                    val radius = size.minDimension * 1.5f
                    val tonalHighlight = tonalGlassHighlight(fallbackColor)
                    val highlightColors = if (hdrHighlightEnabled) {
                        listOf(
                            hdrHighlightColor(
                                // The specular core is neutral white so it can cross the
                                // SDR white point even on a dark glass surface.
                                source = Color.White,
                                luminanceScale = 1.30f,
                                alpha = 0.58f * pressProgress
                            ),
                            hdrHighlightColor(
                                source = tonalHighlight,
                                luminanceScale = 1.06f,
                                alpha = 0.18f * pressProgress
                            ),
                            hdrHighlightColor(
                                source = tonalHighlight,
                                luminanceScale = 1f,
                                alpha = 0f
                            )
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.15f * pressProgress),
                            tonalHighlight.copy(alpha = 0.08f * pressProgress),
                            tonalHighlight.copy(alpha = 0f)
                        )
                    }
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f * pressProgress),
                        blendMode = BlendMode.Plus
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = highlightColors,
                            center = highlightCenter,
                            radius = radius
                        ),
                        radius = radius,
                        center = highlightCenter,
                        blendMode = BlendMode.Plus
                    )
                }
                drawContent()
            }
        )
        val surfaceScope = this
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides activeBackdrop,
            LocalLiquidGlassLayer provides glassLayer
        ) {
            Box(
                modifier = Modifier.then(contentTransformModifier),
                contentAlignment = contentAlignment
            ) {
                with(surfaceScope) { content() }
            }
        }
    }
}
