package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.tts.ReaderTtsDock
import com.huangder.lumibooks.tts.ReaderTtsPosition
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

internal data class ReaderTtsPlacement(
    val capsuleX: Dp,
    val capsuleWidth: Dp,
    val expandLeft: Boolean,
    val dock: ReaderTtsDock,
    val dockProgress: Float,
    val collapseKey: Int,
    val dragging: Boolean,
    val horizontalInset: Dp,
    val dragModifier: Modifier,
    val onAnchorChanged: (Dp) -> Unit,
    val onRestore: () -> Unit
)

/** Geometry uses the actual footer bounds and live window size, rather than a fixed bottom offset. */
internal data class ReaderTtsSafeArea(
    val width: Float,
    val top: Float,
    val bottom: Float,
    val inset: Float,
    val scale: Float,
    val compact: Boolean = false
) {
    val capsuleWidth get() = (if (compact) 40f else 128f) * scale
    val maxX get() = (width - capsuleWidth).coerceAtLeast(0f)
    val maxY get() = (bottom - 48f * scale).coerceAtLeast(top)
    val minY get() = (top + if (compact) 0f else 58f * scale).coerceAtMost(maxY)
    fun point(position: ReaderTtsPosition): Offset {
        val value = position.normalized()
        return Offset(
            if (value.saved) maxX * value.xFraction else inset.coerceAtMost(maxX),
            minY + (maxY - minY) * value.yFraction
        )
    }
    fun constrain(point: Offset) = Offset(point.x.coerceIn(0f, maxX), point.y.coerceIn(minY, maxY))
    fun clearRails(point: Offset): Offset {
        val margin = inset.coerceAtMost(maxX / 2f)
        return constrain(point).copy(x = point.x.coerceIn(margin, maxX - margin))
    }
    fun save(point: Offset, dock: ReaderTtsDock) = ReaderTtsPosition(
        if (maxX > 0f) point.x / maxX else 0f,
        if (maxY > minY) (point.y - minY) / (maxY - minY) else 1f,
        dock, true
    ).normalized()
    fun dockAt(point: Offset): ReaderTtsDock = when {
        point.x <= 24f * scale -> ReaderTtsDock.LEFT
        maxX - point.x <= 24f * scale -> ReaderTtsDock.RIGHT
        else -> ReaderTtsDock.NONE
    }
    fun restored(point: Offset, side: ReaderTtsDock) = constrain(point.copy(x = when (side) {
        ReaderTtsDock.LEFT -> if (inset > 24f * scale) inset else 12f * scale
        ReaderTtsDock.RIGHT -> maxX - if (inset > 24f * scale) inset else 12f * scale
        ReaderTtsDock.NONE -> point.x
    }))

    fun aboveCatalogIfNeeded(point: Offset, bounds: Rect, windowOrigin: Offset): Offset {
        val expanded = copy(compact = false)
        val current = expanded.constrain(point)
        val catalogTop = bounds.top - windowOrigin.y
        if (current.y + 48f * scale <= catalogTop) return current
        return expanded.constrain(Offset(bounds.left - windowOrigin.x, catalogTop - 60f * scale))
    }

    fun restoreHandle(point: Offset): Offset {
        val expanded = copy(compact = false)
        return when (val side = dockAt(point)) {
            ReaderTtsDock.NONE -> expanded.constrain(point.copy(x = point.x - 44f * scale))
            else -> expanded.restored(point, side)
        }
    }
}

/** Short release glide: slow drags stop in place, faster ones coast at most 48dp. */
internal fun readerTtsCoastTarget(point: Offset, velocity: Offset, area: ReaderTtsSafeArea): Offset {
    val speed = hypot(velocity.x, velocity.y)
    if (!speed.isFinite() || speed < 120f * area.scale) return area.constrain(point)
    val distance = (speed * .075f).coerceAtMost(48f * area.scale)
    return area.constrain(point + velocity * (distance / speed))
}

@Composable
internal fun ReaderTtsFloatingHost(
    visible: Boolean,
    readerMenuVisible: Boolean,
    position: ReaderTtsPosition,
    onPositionChange: (ReaderTtsPosition) -> Unit,
    footerTopInWindow: Float? = null,
    catalogBoundsInWindow: Rect? = null,
    revealKey: Int = 0,
    horizontalInset: Dp = 24.dp,
    modifier: Modifier = Modifier,
    content: @Composable (ReaderTtsPlacement) -> Unit
) {
    val density = LocalDensity.current
    val eInk = LocalEInkMode.current
    val motion = LocalMotionEnabled.current && !eInk
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    var localPosition by remember { mutableStateOf(position) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var collapseKey by remember { mutableIntStateOf(0) }
    val save by rememberUpdatedState(onPositionChange)
    val scope = rememberCoroutineScope()
    var coastJob by remember { mutableStateOf<Job?>(null) }
    var renderedX by remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(position) { if (dragPoint == null) localPosition = position }
    LaunchedEffect(readerMenuVisible) {
        if (readerMenuVisible) {
            coastJob?.cancel()
            dragPoint = null
            collapseKey++
        }
    }
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned {
        windowOrigin = it.boundsInWindow().topLeft
    }) {
        val area = with(density) {
            ReaderTtsSafeArea(
                maxWidth.toPx(),
                maxOf(12.dp.toPx(), statusTop - windowOrigin.y),
                minOf(maxHeight.toPx() - navBottom,
                    footerTopInWindow?.minus(windowOrigin.y) ?: Float.MAX_VALUE) - 12.dp.toPx(),
                horizontalInset.toPx(), density.density,
                compact = localPosition.dock != ReaderTtsDock.NONE
            )
        }
        val storedPoint = area.constrain(area.point(localPosition))
        val savedPoint = dragPoint?.let(area::constrain) ?: if (localPosition.dock == ReaderTtsDock.NONE && area.inset > 24f * area.scale)
            area.clearRails(storedPoint) else storedPoint
        val point = if (readerMenuVisible) {
            catalogBoundsInWindow?.let { area.aboveCatalogIfNeeded(savedPoint, it, windowOrigin) }
                ?: area.copy(compact = false).constrain(savedPoint)
        } else savedPoint
        val dock = if (readerMenuVisible) ReaderTtsDock.NONE else localPosition.dock
        val progress by animateFloatAsState(
            if (dock == ReaderTtsDock.NONE) 0f else 1f,
            tween(if (eInk) 0 else if (motion) 240 else 100), label = "readerTtsDock")
        // Keep the capsule full width when restoring. Only the handle's layout box shrinks;
        // the buttons never squeeze together or grow out of the left edge.
        val coreWidth = with(density) { if (dock == ReaderTtsDock.NONE) 128.dp.toPx() else 40.dp.toPx() }
        val positionSpec = tween<Offset>(
            durationMillis = if (eInk || dragPoint != null) 0 else if (motion) 240 else 100,
            easing = if (motion && !eInk && dragPoint == null) LinearEasing else LinearOutSlowInEasing
        )
        // Animate the complete position as one vector. This keeps menu handoff and
        // handle restoration on one straight path instead of composing separate moves.
        val animatedPosition by animateOffsetAsState(
            targetValue = point,
            animationSpec = positionSpec,
            label = "readerTtsPosition"
        )
        // The capsule width changes as soon as a docked handle is restored.  The
        // position animation still starts at the handle's old point, so clamp the
        // transient frame to the current width's rails. Docked handles need their
        // own 40dp rails so the right handle can reach the screen edge; restoring
        // switches to the full capsule's rails immediately to keep it visible.
        val animatedX = animatedPosition.x.coerceIn(
            0f,
            area.copy(compact = dock != ReaderTtsDock.NONE).maxX
        )
        val animatedY = animatedPosition.y
        val currentArea by rememberUpdatedState(area)
        val currentPoint by rememberUpdatedState(point)
        val currentDock by rememberUpdatedState(dock)
        val currentReaderMenu by rememberUpdatedState(readerMenuVisible)
        val currentRenderedY by rememberUpdatedState(animatedY)
        val currentMotion by rememberUpdatedState(motion)
        val currentEInk by rememberUpdatedState(eInk)
        var dragOriginDock by remember { mutableStateOf(ReaderTtsDock.NONE) }
        fun persist(pointToSave: Offset, dockToSave: ReaderTtsDock) {
            val targetArea = currentArea.copy(compact = dockToSave != ReaderTtsDock.NONE)
            localPosition = targetArea.save(targetArea.constrain(pointToSave), dockToSave)
            save(localPosition)
        }
        LaunchedEffect(revealKey) {
            if (revealKey > 0) {
                coastJob?.cancel()
                dragPoint = null
                if (localPosition.dock != ReaderTtsDock.NONE) {
                    persist(area.restoreHandle(storedPoint), ReaderTtsDock.NONE)
                }
                collapseKey++
            }
        }
        val dragModifier = Modifier.pointerInput(Unit) {
            val velocityTracker = VelocityTracker()
            detectDragGestures(
                onDragStart = {
                    coastJob?.cancel()
                    velocityTracker.resetTracking()
                    collapseKey++
                    dragOriginDock = currentDock
                    dragPoint = currentArea.constrain(Offset(
                        renderedX.takeIf { it.isFinite() } ?: currentPoint.x, currentRenderedY))
                },
                onDrag = { change, amount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, (dragPoint ?: currentPoint) + change.position)
                    dragPoint = currentArea.constrain((dragPoint ?: currentPoint) + amount)
                },
                onDragEnd = {
                    val released = currentArea.constrain(dragPoint ?: currentPoint)
                    val velocity = velocityTracker.calculateVelocity()
                    val destination = readerTtsCoastTarget(released, Offset(velocity.x, velocity.y), currentArea)
                    coastJob = scope.launch {
                        if (!currentEInk && destination != released) {
                            Animatable(released, Offset.VectorConverter).animateTo(destination,
                                tween(if (currentMotion) 220 else 100, easing = LinearOutSlowInEasing)) {
                                dragPoint = currentArea.constrain(value)
                            }
                        }
                        val final = currentArea.constrain(destination)
                        val side = if (currentArea.compact) {
                            if (final.x > currentArea.maxX / 2f) ReaderTtsDock.RIGHT else ReaderTtsDock.LEFT
                        } else currentArea.dockAt(final)
                        val savedPoint = when {
                            !currentArea.compact && side != ReaderTtsDock.NONE -> final.copy(
                                x = if (side == ReaderTtsDock.LEFT) 0f else currentArea.copy(compact = true).maxX)
                            side == ReaderTtsDock.NONE && currentArea.inset > 24f * currentArea.scale -> currentArea.clearRails(final)
                            else -> final
                        }
                        persist(savedPoint, side)
                        dragPoint = null
                    }
                },
                onDragCancel = {
                    coastJob?.cancel()
                    if (!currentReaderMenu) persist(dragPoint ?: currentPoint, dragOriginDock)
                    dragPoint = null
                }
            )
        }
        val placement = ReaderTtsPlacement(
            with(density) { animatedX.toDp() },
            with(density) { coreWidth.toDp() },
            // Three 48dp circles match the 48dp capsule height.
            point.x > area.maxX / 2f,
            dock, progress, collapseKey, dragPoint != null, horizontalInset,
            if (readerMenuVisible) Modifier else dragModifier,
            onAnchorChanged = { renderedX = with(density) { it.toPx() } },
            onRestore = {
                persist(area.restoreHandle(storedPoint), ReaderTtsDock.NONE)
                collapseKey++
            }
        )
        Layout(content = {
            TtsPlayerOverlay(visible) { content(placement) }
        }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val child = measurables.singleOrNull()?.measure(Constraints(
                maxWidth = constraints.maxWidth,
                maxHeight = (area.bottom - area.top).roundToInt().coerceAtLeast(48)))
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Expanding adds height above the capsule, keeping the play button stationary.
                child?.place(0, (animatedY + 48f * density.density - child.height).roundToInt())
            }
        }
    }
}
