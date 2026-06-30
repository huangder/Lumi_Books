package com.ebook.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Compose Canvas 翻页组件 — EPUB 风格视差滑动覆盖。
 * 所有状态由外部以 Compose State 传入，本组件只负责绘制、手势、动画。
 */
@Composable
fun PageCanvas(
    curPage: PageData,
    prevPage: PageData?,
    nextPage: PageData?,
    bgColor: ComposeColor,
    onPageChanged: (chapterIndex: Int, pageIndex: Int, chapterTotal: Int) -> Unit,
    onMenuToggle: () -> Unit,
    onShiftForward: () -> Unit,
    onShiftBackward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }
    val slideAnim = remember { Animatable(0f) }

    val renderOffset by remember { derivedStateOf { if (isDragging) dragOffset else slideAnim.value } }

    // ── 回弹 ──
    fun animateBounce(fromOffset: Float) {
        scope.launch {
            slideAnim.snapTo(fromOffset); isDragging = false
            slideAnim.animateTo(0f, tween(350, easing = Easing { 1f - (1f - it) * (1f - it) * (1f - it) * (1f - it) }))
        }
    }

    // ── 翻页动画 ──
    fun startAnimateTurn(direction: Int, fromOffset: Float) {
        val w = canvasSize.width.toFloat()
        val target = if (direction > 0) -w else w
        val targetPage = if (direction > 0) nextPage else prevPage
        if (targetPage == null) { animateBounce(fromOffset); return }

        isAnimating = true
        scope.launch {
            slideAnim.snapTo(fromOffset); isDragging = false
            slideAnim.animateTo(target, tween(450, easing = Easing { 1f - (1f - it) * (1f - it) * (1f - it) }))
            slideAnim.snapTo(0f)
            if (direction > 0) onShiftForward() else onShiftBackward()
            onPageChanged(curPage.chapterIndex, curPage.pageIndex, curPage.chapterTotal)
            isAnimating = false
        }
    }

    // ── 拖拽结束 ──
    fun handleDragEnd() {
        val last = dragOffset; val w = canvasSize.width.toFloat()
        if (abs(last) / w > 0.25f) startAnimateTurn(if (last < 0) 1 else -1, last)
        else animateBounce(last)
    }

    Box(modifier = modifier.fillMaxSize().background(bgColor).onSizeChanged { canvasSize = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        if (downEvent.type != PointerEventType.Press) continue
                        val down = downEvent.changes.firstOrNull() ?: continue
                        if (isAnimating) continue
                        val startX = down.position.x
                        val startY = down.position.y
                        var moved = false
                        down.consume()

                        // 追踪 move 直到 up
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            if (ev.type == PointerEventType.Release) {
                                ev.changes.firstOrNull()?.consume()
                                break
                            }
                            if (ev.type != PointerEventType.Move) continue
                            val ch = ev.changes.firstOrNull() ?: break
                            val dx = ch.position.x - startX
                            val dy = abs(ch.position.y - startY)
                            if (!moved && abs(dx) > 8f && abs(dx) > dy) {
                                moved = true; isDragging = true; dragOffset = 0f
                            }
                            if (moved) {
                                dragOffset = dx.coerceIn(-canvasSize.width * 0.9f, canvasSize.width * 0.9f)
                                ch.consume()
                            } else if (dy > 16f) break // vertical scroll, abort
                        }

                        if (moved) {
                            handleDragEnd()
                        } else {
                            val wf = canvasSize.width.toFloat()
                            when {
                                startX < wf * 0.30f -> startAnimateTurn(-1, 0f)
                                startX > wf * 0.70f -> startAnimateTurn(1, 0f)
                                else -> onMenuToggle()
                            }
                        }
                    }
                }
            }
        ) {
            val w = size.width; val h = size.height
            if (w <= 0 || h <= 0) return@Canvas
            val offset = renderOffset

            drawRect(bgColor, Offset.Zero, Size(w, h))

            if (offset < 0) {
                // 前向：底层 nextPage (视差 0.3x, 起始 70%)，顶层 curPage (1:1)
                nextPage?.let {
                    drawPageBitmap(it.bitmap, this, Offset(w * 0.7f + offset * 0.3f, 0f), Size(w, h))
                }
                drawPageBitmap(curPage.bitmap, this, Offset(offset, 0f), Size(w, h))
                drawShadow(this, w, h, w + offset, rightSide = true)
            } else if (offset > 0) {
                // 后向：底层 curPage (0.2x)，顶层 prevPage (1:1 从 -w 滑入)
                drawPageBitmap(curPage.bitmap, this, Offset(offset * 0.2f, 0f), Size(w, h))
                prevPage?.let {
                    drawPageBitmap(it.bitmap, this, Offset(-w + offset, 0f), Size(w, h))
                }
                drawShadow(this, w, h, offset, rightSide = false)
            } else {
                drawPageBitmap(curPage.bitmap, this, Offset.Zero, Size(w, h))
            }
        }
    }
}

private fun drawShadow(scope: DrawScope, w: Float, h: Float, edgeX: Float, rightSide: Boolean) {
    val sw = 100f
    val shadowPaint = Paint().apply {
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        color = android.graphics.Color.argb(50, 0, 0, 0); isAntiAlias = true
    }
    val edgePaint = Paint().apply { color = android.graphics.Color.argb(20, 0, 0, 0); isAntiAlias = true }
    val nativeCanvas = scope.drawContext.canvas.nativeCanvas

    if (rightSide && edgeX in 0f..w) {
        nativeCanvas.drawRect(edgeX - sw, 0f, edgeX, h, shadowPaint)
        nativeCanvas.drawRect(edgeX - 3f, 0f, edgeX, h, edgePaint)
    } else if (!rightSide && edgeX in 0f..w) {
        nativeCanvas.drawRect(edgeX, 0f, edgeX + sw, h, shadowPaint)
        nativeCanvas.drawRect(edgeX, 0f, edgeX + 3f, h, edgePaint)
    }
}

private fun drawPageBitmap(bitmap: Bitmap, scope: DrawScope, offset: Offset, canvasSize: Size) {
    val sw = bitmap.width.toFloat(); val sh = bitmap.height.toFloat()
    if (sw <= 0 || sh <= 0) return
    val sx = canvasSize.width / sw; val sy = canvasSize.height / sh
    scope.drawContext.canvas.nativeCanvas.apply {
        save(); translate(offset.x, offset.y); scale(sx, sy); drawBitmap(bitmap, 0f, 0f, null); restore()
    }
}
