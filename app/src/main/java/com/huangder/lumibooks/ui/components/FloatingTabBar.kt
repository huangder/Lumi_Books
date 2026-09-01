package com.huangder.lumibooks.ui.components

/*
 * Portions of the liquid-glass tab implementation are adapted and modified
 * from AndroidLiquidGlass' LiquidBottomTabs sample.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Lumi changes include layout, colors, sizing, navigation integration,
 * click motion, drag settling, backdrop composition, finger-tracking
 * highlight, whole-bar press scaling, and accessibility.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 */

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class TabItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val titleRes: Int,
    val testTag: String
)

val tabs = listOf(
    TabItem(Icons.Rounded.Home, Icons.Rounded.Home, R.string.home_title, "home_tab"),
    TabItem(Icons.Rounded.AutoStories, Icons.Rounded.AutoStories, R.string.bookshelf_title, "bookshelf_tab"),
    TabItem(Icons.Rounded.Leaderboard, Icons.Rounded.Leaderboard, R.string.statistics_title, "statistics_tab")
)

@Composable
fun Material3BottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = NavigationBarDefaults.Elevation,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                modifier = Modifier.testTag(tab.testTag),
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.titleRes)
                    )
                },
                label = { Text(stringResource(tab.titleRes), maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
fun FloatingTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    liquidGlassBackdrop: Backdrop? = null,
    reserveImportButtonSpace: Boolean = false
) {
    val isDark = LocalIsDarkTheme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val transparency = LocalLiquidGlassTransparency.current
    val motionEnabled = LocalMotionEnabled.current
    val density = LocalDensity.current
    val barHeight = if (isLiquidGlass) 72.dp else 56.dp
    val accent = AppColors.Accent
    // Automatic backdrop sampling is intentionally disabled. PixelCopy and draw
    // observation both add work to every animated frame on affected devices.
    val adaptiveBackgroundIsDark = isDark
    val toneProgress by animateFloatAsState(
        targetValue = if (adaptiveBackgroundIsDark) 1f else 0f,
        animationSpec = if (motionEnabled) tween(180) else snap(),
        label = "adaptiveTabBarTone"
    )
    val tabContentColor = if (isLiquidGlass) {
        lerp(Color(0xFF17171A), Color.White.copy(alpha = 0.92f), toneProgress)
    } else {
        AppColors.TextSecondary
    }
    val tabMaskColor = lerp(Color.White, Color.Black, toneProgress)
    val selectedTabContentColor = if (isLiquidGlass) accent.copy(alpha = 0.88f) else AppColors.TextPrimary
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val animationScope = rememberCoroutineScope()
    // Compensate the light glass' neutral overlay so the sampled result matches Lumi pink.
    val glassShape = CircleShape
    val horizontalPadding = if (isLiquidGlass) 24.dp else 80.dp
    val endPadding = if (isLiquidGlass && reserveImportButtonSpace) 108.dp else horizontalPadding
    val glassBrush = if (isLiquidGlass && isDark) {
        val alpha = 0.42f - transparency * 0.24f
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2C2C2E).copy(alpha = alpha + 0.10f),
                Color(0xFF111113).copy(alpha = alpha)
            )
        )
    } else if (isLiquidGlass) {
        val alpha = 0.42f - transparency * 0.24f
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha + 0.12f),
                Color(0xFFEAF6FF).copy(alpha = alpha)
            )
        )
    } else if (isDark) {
        // The default bar uses a uniform translucent surface; the liquid glass
        // theme above owns the directional shading and refraction effects.
        SolidColor(Color(0xFF242426).copy(alpha = 0.76f))
    } else {
        SolidColor(Color.White.copy(alpha = 0.80f))
    }
    val borderBrush = if (isLiquidGlass && isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.68f - transparency * 0.18f),
                Color.White.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else if (isLiquidGlass) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color.White.copy(alpha = 0.58f + transparency * 0.10f),
                Color.White.copy(alpha = 0.18f + transparency * 0.08f)
            )
        )
    } else if (isDark) {
        SolidColor(Color.White.copy(alpha = 0.22f))
    } else {
        SolidColor(Color.White.copy(alpha = 0.58f))
    }
    val hazeModifier = hazeState?.let { state ->
        Modifier.hazeChild(state) {
            backgroundColor = if (isDark) Color(0xFF1C1C1E) else Color.White
            tints = listOf(
                HazeTint(
                    if (isLiquidGlass) {
                        if (isDark) Color(0x441C1C1E) else Color.White.copy(alpha = 0.12f)
                    } else if (isDark) Color(0x521C1C1E) else Color(0x5CFFFFFF)
                )
            )
            blurRadius = if (isLiquidGlass) {
                10.dp * (1f - transparency)
            } else {
                36.dp
            }
            noiseFactor = 0.08f
            fallbackTint = HazeTint(
                if (isDark) Color(0xC81C1C1E) else Color(0xCCFFFFFF)
            )
        }
    } ?: Modifier
    val liquidSurfaceColor = if (isDark) {
        Color(0xFF121214).copy(alpha = 0.34f - transparency * 0.16f)
    } else {
        Color.White.copy(alpha = 0.38f - transparency * 0.20f)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedTabsBackdrop = if (isLiquidGlass && liquidGlassBackdrop != null) {
        rememberCombinedBackdrop(liquidGlassBackdrop, tabsBackdrop)
    } else {
        null
    }
    val outerGlassModifier = if (isLiquidGlass && liquidGlassBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = liquidGlassBackdrop,
            shape = { glassShape },
            effects = {
                vibrancy()
                if (transparency < 1f) {
                    blur((6.dp * (1f - transparency)).toPx())
                }
                lens(16.dp.toPx(), 28.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 0.26f)
            },
            onDrawSurface = { drawRect(liquidSurfaceColor) }
        )
    } else {
        Modifier
            .then(hazeModifier)
            .background(glassBrush)
    }
    val shadowColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                top = if (isLiquidGlass) 10.dp else 14.dp,
                bottom = if (isLiquidGlass) 10.dp else 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (isLiquidGlass) 480.dp else 430.dp)
                .padding(
                    start = horizontalPadding,
                    end = endPadding
                )
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            // Keep the original three-column rhythm, with only a subtle inset.
            val contentPadding = if (isLiquidGlass) 6.dp else 0.dp
            val contentPaddingPx = with(density) { contentPadding.toPx() }
            val contentWidth = maxWidth - contentPadding * 2
            val contentWidthPx = with(density) { contentWidth.toPx() }
            val indicatorWidth = contentWidth / tabs.size
            val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
            val indicatorExtraWidth = contentPadding * 2
            val indicatorExtraWidthPx = with(density) { indicatorExtraWidth.toPx() }
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            var currentIndex by remember { mutableIntStateOf(selectedIndex) }
            var pointerPosition by remember { mutableStateOf(Offset.Zero) }
            val dragState = remember(animationScope, indicatorWidthPx, motionEnabled) {
                LiquidGlassDampedMotionState(
                    animationScope = animationScope,
                    initialValue = selectedIndex.toFloat(),
                    valueRange = 0f..tabs.lastIndex.toFloat(),
                    motionEnabled = motionEnabled,
                    pressedScale = 78f / 56f
                )
            }
            var panelDragDistancePx by remember { mutableFloatStateOf(0f) }
            var panelOffsetTargetPx by remember { mutableFloatStateOf(0f) }
            val panelOffsetPx by animateFloatAsState(
                targetValue = panelOffsetTargetPx,
                animationSpec = if (dragState.isInteracting) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 300f
                    )
                },
                label = "liquidTabPanelOffset"
            )

            LaunchedEffect(selectedIndex, dragState) {
                if (selectedIndex != currentIndex) {
                    currentIndex = selectedIndex
                    if (abs(dragState.targetValue - selectedIndex.toFloat()) > 0.001f) {
                        dragState.syncToValue(selectedIndex.toFloat())
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass) {
                            Modifier.liquidGlassTabDrag(
                                state = dragState,
                                onDragStart = { position ->
                                    pointerPosition = position
                                    panelDragDistancePx = 0f
                                    panelOffsetTargetPx = 0f
                                },
                                onPointerMove = { position ->
                                    pointerPosition = position
                                },
                                onDrag = { dragAmount ->
                                    val direction = if (isLtr) 1f else -1f
                                    dragState.dragTo(
                                        dragState.targetValue +
                                            dragAmount.x / indicatorWidthPx * direction
                                    )
                                    panelDragDistancePx += dragAmount.x
                                    panelOffsetTargetPx = dampedTabPanelOffset(
                                        dragDistancePx = panelDragDistancePx,
                                        panelWidthPx = contentWidthPx,
                                        maxOffsetPx = with(density) { 4.dp.toPx() }
                                    )
                                },
                                onDragEnd = { dragged ->
                                    val target = if (dragged) {
                                        projectedTabTarget(
                                            currentValue = dragState.targetValue,
                                            velocity = dragState.velocity,
                                            lastIndex = tabs.lastIndex
                                        )
                                    } else {
                                        currentIndex
                                    }
                                    dragState.settleTo(target.toFloat())
                                    panelOffsetTargetPx = 0f
                                    if (target != currentIndex) {
                                        currentIndex = target
                                        currentOnTabSelected(target)
                                    }
                                },
                                onDragCancel = {
                                    dragState.cancelInteraction(currentIndex.toFloat())
                                    panelOffsetTargetPx = 0f
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = if (motionEnabled) {
                            1f + 0.014f * dragState.pressProgress
                        } else {
                            1f
                        }
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val shadowRadius = 28.dp.toPx()
                        val cornerRadius = size.height / 2f
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.White.copy(alpha = 0.01f).toArgb()
                            setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRoundRect(
                                RectF(0f, 0f, size.width, size.height),
                                cornerRadius,
                                cornerRadius,
                                paint
                            )
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass && liquidGlassBackdrop != null) Modifier
                        else Modifier.clip(glassShape)
                    )
                    .then(outerGlassModifier)
                    .border(width = if (isLiquidGlass) 1.dp else 0.8.dp, brush = borderBrush, shape = glassShape)
            )

            if (isLiquidGlass && liquidGlassBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(glassShape)
                        .drawBehind {
                            val progress = dragState.pressProgress
                            if (progress > 0f) {
                                val barScale = if (motionEnabled) {
                                    1f + 0.014f * progress
                                } else {
                                    1f
                                }
                                // Counter the bar's visual scale so the rendered glow
                                // remains exactly beneath the physical pointer.
                                val center = Offset(
                                    x = size.width / 2f +
                                        (pointerPosition.x - size.width / 2f) / barScale,
                                    y = size.height / 2f +
                                        (pointerPosition.y - size.height / 2f) / barScale
                                )
                                val radius = size.minDimension * 1.8f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.28f * progress),
                                            Color.White.copy(alpha = 0.09f * progress),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center,
                                    blendMode = BlendMode.Plus
                                )
                            }
                        }
                )
            }

            // A translucent tonal veil keeps the bar readable over changing content;
            // the prism remains a separate, brighter layer above it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(glassShape)
                    .background(
                        tabMaskColor.copy(
                            alpha = (
                                (if (isLiquidGlass) 0.30f else 0.20f) * (1f - toneProgress) +
                                    (if (isLiquidGlass) 0.24f else 0.16f) * toneProgress
                                )
                        )
                    )
            )

            FixedTabItems(
                currentIndex = currentIndex,
                contentPadding = contentPadding,
                contentColor = tabContentColor,
                selectedContentColor = selectedTabContentColor,
                onTabSelected = { index ->
                    if (index != currentIndex) {
                        currentIndex = index
                        dragState.animateToValue(index.toFloat())
                        currentOnTabSelected(index)
                    }
                },
                liquidGlass = isLiquidGlass,
                interactive = true
            )

            if (isLiquidGlass && liquidGlassBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { glassShape },
                            effects = {
                                val progress = dragState.pressProgress
                                vibrancy()
                                if (transparency < 1f) {
                                    blur((6.dp * (1f - transparency)).toPx())
                                }
                                lens(
                                    16.dp.toPx() * progress,
                                    20.dp.toPx() * progress
                                )
                            },
                            onDrawSurface = { drawRect(liquidSurfaceColor) }
                        )
                ) {
                    FixedTabItems(
                        currentIndex = currentIndex,
                        contentPadding = contentPadding,
                        contentColor = tabContentColor,
                        selectedContentColor = selectedTabContentColor,
                        onTabSelected = {},
                        liquidGlass = true,
                        interactive = false,
                        itemScale = {
                            if (motionEnabled) {
                                1f + 0.2f * dragState.pressProgress
                            } else {
                                1f
                            }
                        }
                    )
                }
            }

            if (isLiquidGlass) {
                val prismGestureModifier = Modifier
                    .width(indicatorWidth + indicatorExtraWidth)
                    .fillMaxHeight()
                    .offset {
                        val baseTranslation = if (isLtr) {
                            contentPaddingPx - indicatorExtraWidthPx / 2f +
                                dragState.value * indicatorWidthPx
                        } else {
                            contentPaddingPx + contentWidthPx -
                                (dragState.value + 1f) * indicatorWidthPx -
                                indicatorExtraWidthPx / 2f
                        }
                        IntOffset((baseTranslation + panelOffsetPx).roundToInt(), 0)
                    }
                    .padding(3.dp)
                val prismVisualModifier = if (combinedTabsBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = combinedTabsBackdrop,
                        shape = { CircleShape },
                        effects = {
                            val opticalProgress =
                                if (motionEnabled) dragState.pressProgress else 0f
                            lens(
                                14.dp.toPx() * opticalProgress,
                                18.dp.toPx() * opticalProgress,
                                chromaticAberration = opticalProgress > 0.05f
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dragState.pressProgress)
                        },
                        shadow = {
                            Shadow(alpha = dragState.pressProgress)
                        },
                        innerShadow = {
                            val progress = dragState.pressProgress
                            InnerShadow(radius = 8.dp * progress, alpha = progress)
                        },
                        layerBlock = {
                            scaleY = dragState.scale
                            scaleX = equalEdgePrismScaleX(
                                scaleY = scaleY,
                                widthPx = size.width,
                                heightPx = size.height
                            )
                        },
                        onDrawSurface = {
                            val progress = dragState.pressProgress
                            drawRect(
                                color = if (isDark) Color.White else Color.Black,
                                alpha = (if (isDark) 0.08f else 0.06f) * (1f - progress)
                            )
                            drawRect(Color.Black, alpha = 0.03f * progress)
                        }
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            (if (isDark) Color.White else Color.Black).copy(
                                alpha = if (isDark) 0.08f else 0.06f
                            )
                        )
                }
                Box(
                    modifier = prismGestureModifier.then(prismVisualModifier)
                )
            }

            }
            }
            }
    }
    }
}

internal fun projectedTabTarget(
    currentValue: Float,
    velocity: Float,
    lastIndex: Int,
    projectionSeconds: Float = 0.18f
): Int = (currentValue + velocity * projectionSeconds)
    .roundToInt()
    .coerceIn(0, lastIndex)

internal fun dampedTabPanelOffset(
    dragDistancePx: Float,
    panelWidthPx: Float,
    maxOffsetPx: Float
): Float {
    if (panelWidthPx <= 0f || maxOffsetPx <= 0f) return 0f
    val fraction = (abs(dragDistancePx) / panelWidthPx).coerceIn(0f, 1f)
    val easedFraction = 1f - (1f - fraction) * (1f - fraction)
    return maxOffsetPx * dragDistancePx.sign * easedFraction
}

internal fun equalEdgePrismScaleX(
    scaleY: Float,
    widthPx: Float,
    heightPx: Float
): Float {
    if (widthPx <= 0f) return 1f
    return 1f + (scaleY - 1f) * (heightPx / widthPx)
}

@Composable
fun LiquidGlassImportButton(
    onClick: () -> Unit,
    liquidGlassBackdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = Color.Black,
        backdrop = liquidGlassBackdrop,
        contentScrimColor = Color.Black.copy(alpha = 0.85f),
        // Keep the action button on the same 72dp baseline as the Liquid Glass tab bar.
        modifier = modifier.size(72.dp),
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.import_books),
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun FixedTabItems(
    currentIndex: Int,
    contentPadding: androidx.compose.ui.unit.Dp,
    contentColor: Color,
    selectedContentColor: Color,
    onTabSelected: (Int) -> Unit,
    liquidGlass: Boolean = true,
    interactive: Boolean = true,
    itemScale: () -> Float = { 1f },
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            TabItemView(
                tab = tab,
                isSelected = index == currentIndex,
                liquidGlass = liquidGlass,
                contentColor = contentColor,
                selectedContentColor = selectedContentColor,
                interactive = interactive,
                itemScale = itemScale,
                onClick = { onTabSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabItemView(
    tab: TabItem,
    isSelected: Boolean,
    liquidGlass: Boolean,
    contentColor: Color,
    selectedContentColor: Color,
    interactive: Boolean = true,
    itemScale: () -> Float = { 1f },
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (liquidGlass) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 5.dp)
                .clip(CircleShape)
                .semantics {
                    role = Role.Tab
                    selected = isSelected
                    if (interactive) testTagsAsResourceId = true
                    onClick {
                        onClick()
                        true
                    }
                }
                .then(if (interactive) Modifier.testTag(tab.testTag) else Modifier)
                .then(
                    if (interactive) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onClick() }
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    val scale = itemScale()
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = stringResource(tab.titleRes),
                tint = if (isSelected) selectedContentColor else contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(
                text = stringResource(tab.titleRes),
                color = if (isSelected) selectedContentColor else contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
                maxLines = 1
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .semantics { testTagsAsResourceId = true }
            .testTag(tab.testTag)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) AppColors.Accent.copy(alpha = 0.16f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = if (isSelected) selectedContentColor else contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
