package com.huangder.lumibooks.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.ColorSpace as AndroidColorSpace
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.Shader as AndroidShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassHdrHighlightEnabled
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
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
import kotlin.math.abs
import kotlin.math.max

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val ExtendedSrgbColorSpace: AndroidColorSpace by lazy {
    AndroidColorSpace.get(AndroidColorSpace.Named.EXTENDED_SRGB)
}

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
            if (transparency < 1f) {
                blur((6.dp * (1f - transparency)).toPx())
            }
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
        val sheetTransparency = if (transparency >= 1f) {
            1f
        } else {
            (transparency - 0.10f).coerceIn(0f, 0.90f)
        }
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

    Box(modifier = modifier) {
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
                    .then(contentModifier)
                    .clip(contentShape),
                contentAlignment = contentAlignment,
                content = content
            )
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
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactive: Boolean = onClick != null,
    effectPadding: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val hdrHighlightEnabled = LocalLiquidGlassHdrHighlightEnabled.current
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    val density = LocalDensity.current
    val latestOnClick by rememberUpdatedState(onClick)
    var gestureActive by remember { mutableStateOf(false) }
    var edgeDragTargetX by remember { mutableFloatStateOf(0f) }
    var edgeDragTargetY by remember { mutableFloatStateOf(0f) }
    var highlightCenter by remember { mutableStateOf(Offset.Zero) }
    var highlightSequence by remember { mutableIntStateOf(0) }
    val highlightSpread = remember { Animatable(0f) }
    val hdrHighlightPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val pressProgress by animateFloatAsState(
        targetValue = if (gestureActive && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "liquidGlassSurfacePress"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (gestureActive && enabled) 1f else 0f,
        animationSpec = tween(if (gestureActive) 90 else 220),
        label = "liquidGlassTouchHighlightAlpha"
    )
    val edgeDragX by animateFloatAsState(
        targetValue = edgeDragTargetX,
        animationSpec = if (gestureActive) snap() else spring(dampingRatio = 0.70f, stiffness = 360f),
        label = "liquidGlassEdgeDragX"
    )
    val edgeDragY by animateFloatAsState(
        targetValue = edgeDragTargetY,
        animationSpec = if (gestureActive) snap() else spring(dampingRatio = 0.70f, stiffness = 360f),
        label = "liquidGlassEdgeDragY"
    )

    LaunchedEffect(highlightSequence) {
        if (highlightSequence > 0) {
            highlightSpread.snapTo(0f)
            highlightSpread.animateTo(
                targetValue = 1f,
                animationSpec = tween(460, easing = FastOutSlowInEasing)
            )
        }
    }

    val handlesGestures = enabled && interactive
    val semanticsModifier = if (enabled && onClick != null) {
        Modifier.semantics {
                role = Role.Button
                onClick {
                    latestOnClick?.invoke()
                    true
                }
            }
    } else if (!enabled && onClick != null) {
        Modifier.semantics {
            role = Role.Button
            disabled()
        }
    } else {
        Modifier
    }
    val interactionModifier = if (handlesGestures) {
        Modifier.pointerInput(enabled, interactive) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val start = down.position
                    highlightCenter = start
                    highlightSequence++
                    gestureActive = true
                    var totalDrag = Offset.Zero
                    var dragged = false
                    var released = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            totalDrag += delta
                            if (!dragged && totalDrag.getDistance() >= viewConfiguration.touchSlop) {
                                dragged = true
                            }

                            val normalizedX = ((change.position.x - width / 2f) / (width / 2f))
                                .coerceIn(-1f, 1f)
                            val normalizedY = ((change.position.y - height / 2f) / (height / 2f))
                                .coerceIn(-1f, 1f)
                            val edgeProximity = max(abs(normalizedX), abs(normalizedY))
                            val edgeFactor = ((edgeProximity - 0.52f) / 0.48f).coerceIn(0f, 1f)
                            edgeDragTargetX = (totalDrag.x / width).coerceIn(-0.32f, 0.32f) * edgeFactor
                            edgeDragTargetY = (totalDrag.y / height).coerceIn(-0.32f, 0.32f) * edgeFactor

                            if (change.changedToUpIgnoreConsumed()) {
                                released = true
                                break
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        gestureActive = false
                        edgeDragTargetX = 0f
                        edgeDragTargetY = 0f
                    }

                    if (released && !dragged) latestOnClick?.invoke()
                }
            }
    } else {
        Modifier
    }

    val surfaceModifier = if (isLiquidGlass && activeBackdrop != null) {
        Modifier.liquidGlassBackdrop(
            backdrop = activeBackdrop,
            shape = shape,
            isDark = isDark,
            transparency = transparency,
            contentScrimColor = contentScrimColor,
            pressProgress = pressProgress,
            scaleOnPress = false
        )
    } else if (isLiquidGlass) {
        val fallbackScrim = if (contentScrimColor.alpha > 0f) {
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
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.16f))
                ),
                shape
            )
    } else {
        Modifier.clip(shape).background(fallbackColor)
    }

    val baseScale = 1f + 0.045f * pressProgress
    val densityScale = density.density
    val horizontalStretch = abs(edgeDragX) * 0.18f
    val verticalStretch = abs(edgeDragY) * 0.18f
    val transformOriginX = when {
        edgeDragX > 0.01f -> 0f
        edgeDragX < -0.01f -> 1f
        else -> 0.5f
    }
    val transformOriginY = when {
        edgeDragY > 0.01f -> 0f
        edgeDragY < -0.01f -> 1f
        else -> 0.5f
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = baseScale + horizontalStretch
                scaleY = baseScale + verticalStretch
                translationX = edgeDragX * 5f * densityScale
                translationY = edgeDragY * 5f * densityScale
                transformOrigin = TransformOrigin(transformOriginX, transformOriginY)
            }
            .clip(shape)
            .then(semanticsModifier)
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
                if (highlightAlpha > 0.001f && highlightSpread.value > 0.001f) {
                    val radius = max(size.width, size.height) *
                        (0.10f + 1.05f * highlightSpread.value)
                    if (hdrHighlightEnabled) {
                        hdrHighlightPaint.shader = AndroidRadialGradient(
                            highlightCenter.x,
                            highlightCenter.y,
                            radius,
                            longArrayOf(
                                AndroidColor.valueOf(
                                    1.35f,
                                    1.35f,
                                    1.35f,
                                    0.42f * highlightAlpha,
                                    ExtendedSrgbColorSpace
                                ).pack(),
                                AndroidColor.valueOf(
                                    1.12f,
                                    1.12f,
                                    1.12f,
                                    0.16f * highlightAlpha,
                                    ExtendedSrgbColorSpace
                                ).pack(),
                                AndroidColor.valueOf(
                                    1f,
                                    1f,
                                    1f,
                                    0f,
                                    ExtendedSrgbColorSpace
                                ).pack()
                            ),
                            floatArrayOf(0f, 0.48f, 1f),
                            AndroidShader.TileMode.CLAMP
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawCircle(
                                highlightCenter.x,
                                highlightCenter.y,
                                radius,
                                hdrHighlightPaint
                            )
                        }
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.38f * highlightAlpha),
                                    Color.White.copy(alpha = 0.14f * highlightAlpha),
                                    Color.Transparent
                                ),
                                center = highlightCenter,
                                radius = radius
                            ),
                            radius = radius,
                            center = highlightCenter
                        )
                    }
                }
                drawContent()
            }
        )
        val surfaceScope = this
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides activeBackdrop
        ) {
            with(surfaceScope) { content() }
        }
    }
}
