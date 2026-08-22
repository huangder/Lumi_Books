package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import kotlin.math.abs
import kotlin.math.max

/**
 * 封面裁剪覆盖层：在浏览器内容区域上方显示固定比例（默认 3:4）的可拖动裁剪框。
 * 框可整体拖动，四角手柄拖拽等比缩放；框外半透明遮罩（圆角镂空），框内三分网格。
 * 框状态由父级持有（容器坐标 px），本组件只负责展示与手势。
 */
@Composable
fun CoverCropOverlay(
    visible: Boolean,
    frame: Rect?,
    containerSize: Size,
    onFrameChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.75f,
    decorationsHidden: Boolean = false
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val motionEnabled = LocalMotionEnabled.current

    val minFrameWidth = with(density) { 96.dp.toPx() }
    val cornerTouchRadius = with(density) { 26.dp.toPx() }
    val borderStroke = with(density) { 2.5f.dp.toPx() }
    val gridStroke = with(density) { 0.75f.dp.toPx() }
    val frameCornerRadius = with(density) { 16.dp.toPx() }

    // 0=左上 1=右上 2=右下 3=左下；-1=整体拖动；null=未命中
    var dragMode by remember { mutableStateOf<Int?>(null) }

    // pointerInput 块仅在 keys 变化时重建，必须经 rememberUpdatedState 读取最新值，
    // 否则闭包持有旧 frame（初始居中框），拖动时会"恢复原大小、钉死屏幕中间"
    val latestFrame by rememberUpdatedState(frame)
    val latestOnFrameChange by rememberUpdatedState(onFrameChange)

    val appearScale by animateFloatAsState(
        targetValue = if (visible) 1f else 1.14f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
        } else {
            tween(0)
        },
        label = "coverCropAppearScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val currentFrame = frame ?: return@graphicsLayer
                    scaleX = appearScale
                    scaleY = appearScale
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (containerSize.width > 0f) {
                            currentFrame.center.x / containerSize.width
                        } else 0.5f,
                        pivotFractionY = if (containerSize.height > 0f) {
                            currentFrame.center.y / containerSize.height
                        } else 0.5f
                    )
                }
                .pointerInput(containerSize, aspectRatio) {
                    detectDragGestures(
                        onDragStart = { position ->
                            val currentFrame = latestFrame ?: return@detectDragGestures
                            dragMode = resolveDragMode(
                                position, currentFrame, cornerTouchRadius
                            )
                            if (dragMode != null && dragMode != -1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDrag = { change, _ ->
                            val mode = dragMode ?: return@detectDragGestures
                            val currentFrame = latestFrame ?: return@detectDragGestures
                            val nextFrame = if (mode == -1) {
                                currentFrame.translateWithin(
                                    delta = change.position - change.previousPosition,
                                    bounds = containerSize
                                )
                            } else {
                                resizeFrame(
                                    anchorCorner = (mode + 2) % 4,
                                    pointer = change.position,
                                    frame = currentFrame,
                                    aspectRatio = aspectRatio,
                                    minWidth = minFrameWidth,
                                    bounds = containerSize
                                )
                            }
                            latestOnFrameChange(nextFrame)
                            change.consume()
                        },
                        onDragEnd = { dragMode = null },
                        onDragCancel = { dragMode = null }
                    )
                }
        ) {
            val currentFrame = frame ?: return@Canvas

            // 截取瞬间整体隐藏（含圆角遮罩，避免圆角外的暗角被截进封面）
            if (decorationsHidden) return@Canvas

            // 遮罩镂空：外矩形减去圆角框
            val outer = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val hole = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = currentFrame.left,
                        top = currentFrame.top,
                        right = currentFrame.right,
                        bottom = currentFrame.bottom,
                        cornerRadius = CornerRadius(frameCornerRadius, frameCornerRadius)
                    )
                )
            }
            val scrimPath = Path.combine(PathOperation.Difference, outer, hole)
            drawPath(scrimPath, color = Color.Black.copy(alpha = 0.45f))

            // 框边框
            drawRoundRect(
                color = Color.White.copy(alpha = 0.95f),
                topLeft = Offset(currentFrame.left, currentFrame.top),
                size = Size(currentFrame.width, currentFrame.height),
                cornerRadius = CornerRadius(frameCornerRadius, frameCornerRadius),
                style = Stroke(width = borderStroke)
            )

            // 三分网格
            val gridColor = Color.White.copy(alpha = 0.40f)
            for (index in 1..2) {
                val x = currentFrame.left + currentFrame.width * index / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(x, currentFrame.top),
                    end = Offset(x, currentFrame.bottom),
                    strokeWidth = gridStroke
                )
                val y = currentFrame.top + currentFrame.height * index / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(currentFrame.left, y),
                    end = Offset(currentFrame.right, y),
                    strokeWidth = gridStroke
                )
            }

            // 四角圆点手柄（贴合圆角边框）
            val cornerDots = listOf(
                Offset(currentFrame.left, currentFrame.top),
                Offset(currentFrame.right, currentFrame.top),
                Offset(currentFrame.right, currentFrame.bottom),
                Offset(currentFrame.left, currentFrame.bottom)
            )
            val dotRadius = with(density) { 6.dp.toPx() }
            cornerDots.forEach { corner ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.22f),
                    radius = dotRadius + borderStroke,
                    center = corner
                )
                drawCircle(
                    color = Color.White,
                    radius = dotRadius,
                    center = corner
                )
            }
        }
    }
}

/** 判断触点命中：四角手柄 → 缩放，框内 → 拖动，其余（遮罩）→ 不响应 */
private fun resolveDragMode(position: Offset, frame: Rect, touchRadius: Float): Int? {
    val corners = listOf(
        Offset(frame.left, frame.top),
        Offset(frame.right, frame.top),
        Offset(frame.right, frame.bottom),
        Offset(frame.left, frame.bottom)
    )
    corners.forEachIndexed { index, corner ->
        if (
            position.x in (corner.x - touchRadius)..(corner.x + touchRadius) &&
            position.y in (corner.y - touchRadius)..(corner.y + touchRadius)
        ) {
            return index
        }
    }
    return if (frame.contains(position)) -1 else null
}

/** 整体拖动并限制在容器内 */
private fun Rect.translateWithin(delta: Offset, bounds: Size): Rect {
    val left = (left + delta.x).coerceIn(0f, (bounds.width - width).coerceAtLeast(0f))
    val top = (top + delta.y).coerceIn(0f, (bounds.height - height).coerceAtLeast(0f))
    return Rect(left, top, left + width, top + height)
}

private fun Rect.clampInto(bounds: Size): Rect {
    val width = width.coerceAtMost(bounds.width)
    val height = height.coerceAtMost(bounds.height)
    val left = left.coerceIn(0f, bounds.width - width)
    val top = top.coerceIn(0f, bounds.height - height)
    return Rect(left, top, left + width, top + height)
}

/** 以对角为锚点等比缩放（保持 aspectRatio = 宽/高） */
private fun resizeFrame(
    anchorCorner: Int,
    pointer: Offset,
    frame: Rect,
    aspectRatio: Float,
    minWidth: Float,
    bounds: Size
): Rect {
    val anchor = when (anchorCorner) {
        0 -> Offset(frame.left, frame.top)
        1 -> Offset(frame.right, frame.top)
        2 -> Offset(frame.right, frame.bottom)
        else -> Offset(frame.left, frame.bottom)
    }
    val dirX = if (pointer.x >= anchor.x) 1f else -1f
    val dirY = if (pointer.y >= anchor.y) 1f else -1f

    var width = max(abs(pointer.x - anchor.x), abs(pointer.y - anchor.y) * aspectRatio)
    width = width.coerceIn(minWidth, bounds.width)
    width = width.coerceAtMost(bounds.height * aspectRatio)
    val height = width / aspectRatio

    val left = if (dirX > 0) anchor.x else anchor.x - width
    val top = if (dirY > 0) anchor.y else anchor.y - height
    return Rect(left, top, left + width, top + height).clampInto(bounds)
}
