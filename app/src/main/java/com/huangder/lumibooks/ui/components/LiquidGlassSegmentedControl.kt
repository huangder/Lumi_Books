package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SegmentSpringDamping = 0.68f
private const val SegmentSpringStiffness = 520f
private const val SegmentOvershootFraction = 0.08f
private val SegmentMaximumOvershoot = 6.dp
private val SegmentMinimumTouchHeight = 44.dp

internal data class LiquidGlassSegmentGeometry(
    val startsPx: List<Float>,
    val widthsPx: List<Float>,
    val totalWidthPx: Float
) {
    val size: Int get() = widthsPx.size
    val centersPx: List<Float>
        get() = startsPx.indices.map { startsPx[it] + widthsPx[it] / 2f }
}

internal fun equalLiquidGlassSegmentGeometry(
    totalWidthPx: Float,
    count: Int,
    spacingPx: Float = 0f
): LiquidGlassSegmentGeometry {
    if (count <= 0) return LiquidGlassSegmentGeometry(emptyList(), emptyList(), 0f)
    val safeWidth = totalWidthPx.coerceAtLeast(0f)
    val safeSpacing = spacingPx.coerceAtLeast(0f)
    val itemWidth = ((safeWidth - safeSpacing * (count - 1)) / count).coerceAtLeast(0f)
    return variableLiquidGlassSegmentGeometry(
        widthsPx = List(count) { itemWidth },
        spacingPx = safeSpacing
    )
}

internal fun variableLiquidGlassSegmentGeometry(
    widthsPx: List<Float>,
    spacingPx: Float = 0f
): LiquidGlassSegmentGeometry {
    if (widthsPx.isEmpty()) return LiquidGlassSegmentGeometry(emptyList(), emptyList(), 0f)
    val safeWidths = widthsPx.map { it.coerceAtLeast(0f) }
    val safeSpacing = spacingPx.coerceAtLeast(0f)
    var cursor = 0f
    val starts = safeWidths.map { width ->
        val start = cursor
        cursor += width + safeSpacing
        start
    }
    return LiquidGlassSegmentGeometry(
        startsPx = starts,
        widthsPx = safeWidths,
        totalWidthPx = (cursor - safeSpacing).coerceAtLeast(0f)
    )
}

internal fun nearestEnabledLiquidGlassSegment(
    value: Float,
    enabled: List<Boolean>
): Int {
    if (enabled.isEmpty()) return -1
    return enabled.indices
        .filter { enabled[it] }
        .minByOrNull { abs(value - it) }
        ?: -1
}

internal fun liquidGlassSegmentValueForCenter(
    physicalCenterPx: Float,
    geometry: LiquidGlassSegmentGeometry,
    isLtr: Boolean
): Float {
    if (geometry.size <= 1) return 0f
    val logicalCenter = if (isLtr) physicalCenterPx else geometry.totalWidthPx - physicalCenterPx
    val centers = geometry.centersPx
    if (logicalCenter <= centers.first()) return 0f
    if (logicalCenter >= centers.last()) return geometry.size - 1f
    val lower = centers.indices.first { centers[it + 1] >= logicalCenter }
    val distance = centers[lower + 1] - centers[lower]
    val fraction = if (distance <= 0f) 0f else (logicalCenter - centers[lower]) / distance
    return lower + fraction
}

internal fun cappedLiquidGlassSegmentValue(
    value: Float,
    origin: Float,
    target: Int,
    geometry: LiquidGlassSegmentGeometry,
    maximumOvershootPx: Float
): Float {
    if (geometry.size <= 1) return 0f
    val safeTarget = target.coerceIn(0, geometry.size - 1)
    val centers = geometry.centersPx
    val arrivingFromLeft = origin < safeTarget
    val arrivingFromRight = origin > safeTarget
    val adjacentTravel = when {
        arrivingFromLeft && safeTarget > 0 -> centers[safeTarget] - centers[safeTarget - 1]
        arrivingFromRight && safeTarget < centers.lastIndex -> centers[safeTarget + 1] - centers[safeTarget]
        safeTarget == 0 -> centers[1] - centers[0]
        else -> centers.last() - centers[centers.lastIndex - 1]
    }.coerceAtLeast(1f)
    val valueLimit = minOf(
        SegmentOvershootFraction,
        maximumOvershootPx.coerceAtLeast(0f) / adjacentTravel
    )
    return when {
        arrivingFromLeft && value > safeTarget -> value.coerceAtMost(safeTarget + valueLimit)
        arrivingFromRight && value < safeTarget -> value.coerceAtLeast(safeTarget - valueLimit)
        else -> value
    }
}

private fun segmentMetricAtValue(
    values: List<Float>,
    value: Float
): Float {
    if (values.isEmpty()) return 0f
    if (values.size == 1) return values.first()
    val lower = value.toInt().coerceIn(0, values.lastIndex - 1)
    val fraction = value - lower
    return values[lower] + (values[lower + 1] - values[lower]) * fraction
}

private fun liquidGlassSegmentStartPx(
    geometry: LiquidGlassSegmentGeometry,
    value: Float,
    isLtr: Boolean
): Float {
    val logicalStart = segmentMetricAtValue(geometry.startsPx, value)
    val width = segmentMetricAtValue(geometry.widthsPx, value)
    return if (isLtr) logicalStart else geometry.totalWidthPx - logicalStart - width
}

private fun liquidGlassSegmentCenterPx(
    geometry: LiquidGlassSegmentGeometry,
    value: Float,
    isLtr: Boolean
): Float {
    val logicalCenter = segmentMetricAtValue(geometry.centersPx, value)
    return if (isLtr) logicalCenter else geometry.totalWidthPx - logicalCenter
}

private fun liquidGlassSegmentAtPosition(
    xPx: Float,
    geometry: LiquidGlassSegmentGeometry,
    isLtr: Boolean
): Int = liquidGlassSegmentValueForCenter(xPx, geometry, isLtr)
    .roundToInt()
    .coerceIn(0, (geometry.size - 1).coerceAtLeast(0))

/**
 * Shared Liquid Glass segmented selector. The moving neutral prism deliberately has no border,
 * highlight, or inner edge treatment; depth comes only from tint, refraction, and a soft shadow.
 */
@Composable
fun LiquidGlassSegmentedControl(
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemEnabled: (Int) -> Boolean = { true },
    segmentWidths: List<Dp>? = null,
    spacing: Dp = 0.dp,
    trackHeight: Dp = 40.dp,
    trackPadding: Dp = 2.dp,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    content: @Composable BoxScope.(index: Int, selected: Boolean) -> Unit
) {
    if (itemCount <= 0) {
        Box(modifier = modifier.height(SegmentMinimumTouchHeight))
        return
    }
    require(segmentWidths == null || segmentWidths.size == itemCount) {
        "segmentWidths must have one entry per item"
    }

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val motionEnabled = LocalMotionEnabled.current
    val animationScope = rememberCoroutineScope()
    val safeSelectedIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val enabledItems = List(itemCount) { enabled && itemEnabled(it) }
    val currentOnSelected by rememberUpdatedState(onSelected)
    val motionState = remember(animationScope, motionEnabled, itemCount) {
        LiquidGlassDampedMotionState(
            animationScope = animationScope,
            initialValue = safeSelectedIndex.toFloat(),
            valueRange = 0f..(itemCount - 1).toFloat(),
            motionEnabled = motionEnabled,
            pressedScale = 1.035f
        )
    }
    val movementSpec: AnimationSpec<Float> = remember(motionEnabled) {
        if (motionEnabled) {
            spring(
                dampingRatio = SegmentSpringDamping,
                stiffness = SegmentSpringStiffness,
                visibilityThreshold = 0.001f
            )
        } else {
            tween(durationMillis = 100)
        }
    }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var animationOrigin by remember(itemCount) { mutableFloatStateOf(safeSelectedIndex.toFloat()) }
    var animationTarget by remember(itemCount) { mutableIntStateOf(safeSelectedIndex) }
    val paddingPx = with(density) { trackPadding.toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val requestedWidthsPx = segmentWidths?.map { with(density) { it.toPx() } }
    val contentWidthPx = (trackWidthPx - paddingPx * 2f).coerceAtLeast(0f)
    val geometry = remember(contentWidthPx, requestedWidthsPx, spacingPx, itemCount) {
        if (requestedWidthsPx != null) {
            variableLiquidGlassSegmentGeometry(requestedWidthsPx, spacingPx)
        } else {
            equalLiquidGlassSegmentGeometry(contentWidthPx, itemCount, spacingPx)
        }
    }
    val maximumOvershootPx = with(density) { SegmentMaximumOvershoot.toPx() }

    LaunchedEffect(safeSelectedIndex, motionState) {
        if (!motionState.isInteracting && abs(motionState.targetValue - safeSelectedIndex) > 0.001f) {
            animationOrigin = motionState.value
            animationTarget = safeSelectedIndex
            motionState.syncToValue(safeSelectedIndex.toFloat(), movementSpec)
        }
    }

    fun animateAndCommit(index: Int) {
        if (index !in 0 until itemCount || !enabledItems[index]) return
        animationOrigin = motionState.value
        animationTarget = index
        motionState.animateToValue(index.toFloat(), movementSpec)
        if (index != safeSelectedIndex) currentOnSelected(index)
    }

    val rawValue = motionState.value
    val visualValue = if (motionEnabled && !motionState.isInteracting) {
        cappedLiquidGlassSegmentValue(
            value = rawValue,
            origin = animationOrigin,
            target = animationTarget,
            geometry = geometry,
            maximumOvershootPx = maximumOvershootPx
        )
    } else {
        rawValue.coerceIn(0f, itemCount - 1f)
    }
    val previewIndex = if (motionState.isInteracting) {
        nearestEnabledLiquidGlassSegment(rawValue, enabledItems).takeIf { it >= 0 }
            ?: safeSelectedIndex
    } else {
        safeSelectedIndex
    }
    val indicatorStartPx = liquidGlassSegmentStartPx(geometry, visualValue, isLtr)
    val indicatorWidthPx = segmentMetricAtValue(geometry.widthsPx, visualValue)
    val indicatorHeight = (trackHeight - trackPadding * 2).coerceAtLeast(1.dp)
    val controlHeight = maxOf(trackHeight, SegmentMinimumTouchHeight)
    val trackShape = RoundedCornerShape(trackHeight / 2)
    val indicatorShape = RoundedCornerShape(indicatorHeight / 2)
    val trackBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop, trackBackdrop)
    } else {
        null
    }
    val trackScrim = if (isDark) {
        Color(0xFF161618).copy(alpha = 0.44f - transparency * 0.18f)
    } else {
        Color.White.copy(alpha = 0.48f - transparency * 0.20f)
    }
    val trackVisual = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { trackShape },
            effects = {
                vibrancy()
                if (transparency < 1f) blur((6.dp * (1f - transparency)).toPx())
                lens(10.dp.toPx(), 18.dp.toPx())
            },
            highlight = { liquidGlassHighlight() },
            onDrawSurface = { drawRect(trackScrim) }
        )
    } else {
        Modifier
            .clip(trackShape)
            .background(trackScrim)
            .border(LiquidGlassOutlineWidth, liquidGlassFallbackOutlineBrush(), trackShape)
    }
    val indicatorVisual = if (indicatorBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = indicatorBackdrop,
            shape = { indicatorShape },
            effects = {
                if (transparency < 1f) blur((3.dp * (1f - transparency)).toPx())
                val opticalProgress = if (motionEnabled) motionState.pressProgress else 0f
                lens(
                    2.dp.toPx() * opticalProgress,
                    4.dp.toPx() * opticalProgress,
                    chromaticAberration = false
                )
            },
            highlight = null,
            shadow = {
                Shadow(
                    radius = 3.dp,
                    color = Color.Black.copy(alpha = if (isDark) 0.12f else 0.07f)
                )
            },
            innerShadow = null,
            layerBlock = {
                if (motionEnabled) {
                    val velocity = (motionState.velocity / 7f).coerceIn(-0.12f, 0.12f)
                    scaleX = motionState.scale * (1f + abs(velocity))
                    scaleY = motionState.scale * (1f - abs(velocity) * 0.35f)
                }
            },
            onDrawSurface = {
                drawRect(
                    color = if (isDark) Color.White else Color.Black,
                    alpha = if (isDark) 0.12f else 0.07f
                )
            }
        )
    } else {
        Modifier
            .shadow(
                elevation = 4.dp,
                shape = indicatorShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(indicatorShape)
            .background(
                (if (isDark) Color.White else Color.Black).copy(
                    alpha = if (isDark) 0.12f else 0.07f
                )
            )
    }
    val variableWidth = segmentWidths?.let {
        it.fold(0.dp) { sum, width -> sum + width } +
            spacing * (itemCount - 1) + trackPadding * 2
    }

    Box(
        modifier = modifier
            .then(if (variableWidth != null) Modifier.width(variableWidth) else Modifier.fillMaxWidth())
            .height(controlHeight)
            .pointerInput(
                geometry,
                enabledItems,
                isLtr,
                safeSelectedIndex,
                movementSpec
            ) {
                if (geometry.size == 0 || geometry.totalWidthPx <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x - paddingPx
                    val downValue = if (motionState.isInteracting) motionState.value else visualValue
                    val downStart = liquidGlassSegmentStartPx(geometry, downValue, isLtr)
                    val downWidth = segmentMetricAtValue(geometry.widthsPx, downValue)
                    val startsOnIndicator = enabled && downX in downStart..(downStart + downWidth)
                    val startCenter = liquidGlassSegmentCenterPx(geometry, downValue, isLtr)
                    var totalX = 0f
                    var totalY = 0f
                    var horizontalDrag = false
                    var cancelled = false
                    var released = false

                    if (startsOnIndicator) motionState.beginInteraction()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y

                        if (!horizontalDrag && !cancelled &&
                            (abs(totalX) >= viewConfiguration.touchSlop ||
                                abs(totalY) >= viewConfiguration.touchSlop)
                        ) {
                            if (startsOnIndicator && abs(totalX) > abs(totalY)) {
                                horizontalDrag = true
                            } else {
                                cancelled = true
                            }
                        }

                        if (horizontalDrag) {
                            change.consume()
                            motionState.dragTo(
                                liquidGlassSegmentValueForCenter(
                                    physicalCenterPx = startCenter + totalX,
                                    geometry = geometry,
                                    isLtr = isLtr
                                )
                            )
                        }

                        if (change.changedToUpIgnoreConsumed()) {
                            released = true
                            break
                        }
                        if (!change.pressed || (change.isConsumed && !horizontalDrag)) break
                    }

                    when {
                        horizontalDrag && released -> {
                            val target = nearestEnabledLiquidGlassSegment(
                                motionState.value,
                                enabledItems
                            ).takeIf { it >= 0 } ?: safeSelectedIndex
                            animationOrigin = motionState.value
                            animationTarget = target
                            motionState.settleTo(
                                value = target.toFloat(),
                                animationSpec = movementSpec,
                                initialVelocity = motionState.velocity.coerceIn(-5f, 5f)
                            )
                            if (target != safeSelectedIndex) currentOnSelected(target)
                        }
                        released && !cancelled -> {
                            val target = liquidGlassSegmentAtPosition(
                                xPx = down.position.x - paddingPx,
                                geometry = geometry,
                                isLtr = isLtr
                            )
                            animateAndCommit(target)
                        }
                        startsOnIndicator -> motionState.cancelInteraction(safeSelectedIndex.toFloat())
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged { trackWidthPx = it.width }
                .then(trackVisual)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(trackPadding)
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(trackBackdrop)
            ) {
                LiquidGlassSegmentItems(
                    itemCount = itemCount,
                    segmentWidths = segmentWidths,
                    spacing = spacing,
                    selectedIndex = previewIndex,
                    enabledItems = enabledItems,
                    interactive = false,
                    onSelected = {},
                    content = content
                )
            }

            if (trackWidthPx > 0 && indicatorWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (paddingPx + indicatorStartPx).roundToInt(),
                                y = 0
                            )
                        }
                        .width(with(density) { indicatorWidthPx.toDp() })
                        .height(indicatorHeight)
                        .align(Alignment.CenterStart)
                        .then(indicatorVisual)
                )
            }
        }

        LiquidGlassSegmentItems(
            itemCount = itemCount,
            segmentWidths = segmentWidths,
            spacing = spacing,
            selectedIndex = previewIndex,
            enabledItems = enabledItems,
            interactive = true,
            onSelected = ::animateAndCommit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = trackPadding),
            content = content
        )
    }
}

@Composable
private fun LiquidGlassSegmentItems(
    itemCount: Int,
    segmentWidths: List<Dp>?,
    spacing: Dp,
    selectedIndex: Int,
    enabledItems: List<Boolean>,
    interactive: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(index: Int, selected: Boolean) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(itemCount) { index ->
            val itemModifier = if (segmentWidths != null) {
                Modifier.width(segmentWidths[index])
            } else {
                Modifier.weight(1f)
            }
            Box(
                modifier = itemModifier
                    .fillMaxHeight()
                    .then(
                        if (interactive) {
                            Modifier.semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = index == selectedIndex
                                if (!enabledItems[index]) disabled()
                                if (enabledItems[index]) {
                                    onClick {
                                        onSelected(index)
                                        true
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                content(index, index == selectedIndex)
            }
        }
    }
}
