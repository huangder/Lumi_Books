package com.huangder.lumibooks.ui.components

import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
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
import kotlin.math.roundToInt

data class TabItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val titleRes: Int
)

val tabs = listOf(
    TabItem(Icons.Rounded.Home, Icons.Rounded.Home, R.string.home_title),
    TabItem(Icons.Rounded.AutoStories, Icons.Rounded.AutoStories, R.string.bookshelf_title),
    TabItem(Icons.Rounded.Leaderboard, Icons.Rounded.Leaderboard, R.string.statistics_title)
)

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
    val density = LocalDensity.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val animationScope = rememberCoroutineScope()
    // Compensate the light glass' neutral overlay so the sampled result matches Lumi pink.
    val accent = if (isDark) AppColors.Accent else Color(0xFFFF6868)
    val glassShape = CircleShape
    val barHeight = if (isLiquidGlass) 72.dp else 56.dp
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
        Brush.verticalGradient(
            colors = listOf(
                Color(0xCC2C2C2E),
                Color(0xB01C1C1E)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xDFFFFFFF),
                Color(0xB8FFFFFF)
            )
        )
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
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                Color.White.copy(alpha = 0.30f)
            )
        )
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
                4.dp + 6.dp * (1f - transparency)
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
    val tintedTabsBackdrop = rememberLayerBackdrop()
    val selectedBackdrop = liquidGlassBackdrop?.let { rootBackdrop ->
        rememberCombinedBackdrop(rootBackdrop, tintedTabsBackdrop)
    }
    val outerGlassModifier = if (isLiquidGlass && liquidGlassBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = liquidGlassBackdrop,
            shape = { glassShape },
            effects = {
                vibrancy()
                blur((2.dp + 4.dp * (1f - transparency)).toPx())
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
                start = horizontalPadding,
                end = endPadding,
                top = if (isLiquidGlass) 10.dp else 14.dp,
                bottom = if (isLiquidGlass) 10.dp else 14.dp
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
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
            val indicatorExtraWidth = 12.dp
            val indicatorExtraWidthPx = with(density) { indicatorExtraWidth.toPx() }
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            var currentIndex by remember { mutableIntStateOf(selectedIndex) }
            val dragState = remember(animationScope, indicatorWidthPx) {
                LiquidGlassTabDragState(
                    animationScope = animationScope,
                    initialValue = selectedIndex.toFloat(),
                    valueRange = 0f..tabs.lastIndex.toFloat(),
                    onDragStopped = {
                        val target = targetValue.roundToInt().coerceIn(0, tabs.lastIndex)
                        currentIndex = target
                        animateToValue(target.toFloat())
                        currentOnTabSelected(target)
                    },
                    onDrag = { _, dragAmount ->
                        val direction = if (isLtr) 1f else -1f
                        updateValue(
                            (targetValue + dragAmount.x / indicatorWidthPx * direction)
                                .coerceIn(0f, tabs.lastIndex.toFloat())
                        )
                    }
                )
            }

            LaunchedEffect(selectedIndex, dragState) {
                if (selectedIndex != currentIndex) {
                    currentIndex = selectedIndex
                    dragState.animateToValue(selectedIndex.toFloat())
                }
            }

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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = contentPadding),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabItemView(
                        tab = tab,
                        isSelected = index == currentIndex,
                        liquidGlass = isLiquidGlass,
                        onClick = {
                            currentIndex = index
                            dragState.animateToValue(index.toFloat(), pressDuringAnimation = true)
                            currentOnTabSelected(index)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isLiquidGlass && liquidGlassBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tintedTabsBackdrop)
                        .drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { glassShape },
                            effects = {
                                val progress = dragState.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    24.dp.toPx() * progress,
                                    24.dp.toPx() * progress
                                )
                            },
                            onDrawSurface = { drawRect(liquidSurfaceColor) }
                        )
                        .graphicsLayer(colorFilter = ColorFilter.tint(accent))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = contentPadding),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            TabItemView(
                                tab = tab,
                                isSelected = index == currentIndex,
                                liquidGlass = true,
                                interactive = false,
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (isLiquidGlass) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth + indicatorExtraWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = if (isLtr) {
                                contentPaddingPx - indicatorExtraWidthPx / 2f +
                                    dragState.value * indicatorWidthPx
                            } else {
                                contentPaddingPx + contentWidthPx -
                                    (dragState.value + 1f) * indicatorWidthPx -
                                    indicatorExtraWidthPx / 2f
                            }
                            scaleX = dragState.scaleX
                            scaleY = dragState.scaleY
                            val velocity = dragState.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        }
                        .then(dragState.modifier)
                        .padding(3.dp)
                        .then(
                            if (selectedBackdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = selectedBackdrop,
                                    shape = { CircleShape },
                                    effects = {
                                        val progress = dragState.pressProgress
                                        lens(
                                            10.dp.toPx() * progress,
                                            14.dp.toPx() * progress,
                                            chromaticAberration = true
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
                                        InnerShadow(
                                            radius = 8.dp * progress,
                                            alpha = progress
                                        )
                                    },
                                    onDrawSurface = {
                                        val progress = dragState.pressProgress
                                        drawRect(
                                            if (isDark) Color.White.copy(alpha = 0.10f)
                                            else Color.Black.copy(alpha = 0.10f),
                                            alpha = 1f - progress
                                        )
                                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                    }
                                )
                            } else {
                                Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) Color.White.copy(alpha = 0.13f)
                                        else Color.Black.copy(alpha = 0.10f)
                                    )
                            }
                        )
                )
            }
        }
    }
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
        modifier = modifier.size(72.dp),
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.import_books),
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun TabItemView(
    tab: TabItem,
    isSelected: Boolean,
    liquidGlass: Boolean,
    interactive: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (liquidGlass) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 5.dp)
                .clip(CircleShape)
                .then(
                    if (interactive) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onClick() }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = stringResource(tab.titleRes),
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(
                text = stringResource(tab.titleRes),
                color = AppColors.TextPrimary,
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
                tint = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
