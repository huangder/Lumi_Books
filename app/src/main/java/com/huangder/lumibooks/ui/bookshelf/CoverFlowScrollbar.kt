package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun CoverFlowScrollbar(
    state: CoverFlowState,
    bookCount: Int,
    enabled: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (bookCount <= 1) return
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val minThumbWidth = with(LocalDensity.current) { 28.dp.toPx() }
    val color = AppColors.TextSecondary
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val available = enabled && state.menuBookId == null
    val active = state.isMoving || dragging
    LaunchedEffect(active, available) {
        if (!available) {
            visible = false
        } else if (active) {
            visible = true
        } else {
            // Leave time to catch the thumb after swiping a cover.
            delay(650)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible && available) 1f else 0f,
        animationSpec = tween(if (!motionEnabled) 0 else if (visible) 150 else 300),
        label = "coverFlowScrollbarAlpha"
    )
    fun thumbWidth(width: Float) = (width / bookCount).coerceAtLeast(minThumbWidth).coerceAtMost(width * 0.45f)
    fun fraction() = (state.position / (bookCount - 1)).coerceIn(0f, 1f)
    val interaction = if (available && (visible || alpha > 0.01f)) {
        Modifier.pointerInput(state, bookCount, rtl, minThumbWidth, motionEnabled) {
            var grabOffset = 0f
            fun dragTo(x: Float) {
                val travel = size.width - thumbWidth(size.width.toFloat())
                if (travel <= 0f) return
                val progress = ((x - grabOffset) / travel).coerceIn(0f, 1f)
                state.dragTo((if (rtl) 1f - progress else progress) * (bookCount - 1))
            }
            try {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val width = thumbWidth(size.width.toFloat())
                        val start = (size.width - width) * (if (rtl) 1f - fraction() else fraction())
                        grabOffset = if (offset.x in start..(start + width)) offset.x - start else width / 2f
                        dragging = true
                        state.beginDrag()
                        dragTo(offset.x)
                    },
                    onDragEnd = {
                        dragging = false
                        state.settle(scope, state.focusedIndex, motionEnabled)
                    },
                    onDragCancel = {
                        dragging = false
                        state.settle(scope, state.focusedIndex, motionEnabled)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragTo(change.position.x)
                    }
                )
            } finally {
                dragging = false
            }
        }.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(state.position.coerceIn(0f, (bookCount - 1).toFloat()),
                0f..(bookCount - 1).toFloat(), (bookCount - 2).coerceAtLeast(0))
            setProgress { target ->
                state.settle(scope, target.roundToInt(), motionEnabled)
                true
            }
        }
    } else Modifier

    // Keep the touch area and layout stable; animate only the drawing's opacity.
    Canvas(modifier.height(40.dp).testTag("cover_flow_scrollbar")
        .graphicsLayer { this.alpha = alpha }.then(interaction)) {
        val y = size.height / 2f
        val trackHeight = 3.dp.toPx()
        drawLine(color.copy(alpha = 0.12f), Offset(trackHeight / 2f, y),
            Offset(size.width - trackHeight / 2f, y), trackHeight, StrokeCap.Round)
        val width = thumbWidth(size.width)
        val x = (size.width - width) * (if (rtl) 1f - fraction() else fraction())
        val height = 5.dp.toPx()
        drawRoundRect(color.copy(alpha = 0.36f), Offset(x, y - height / 2f),
            Size(width, height), CornerRadius(height / 2f))
    }
}
