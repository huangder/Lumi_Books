package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader

/**
 * 垂直滚动翻页动画。
 *
 * 页面上下平移实现翻页效果：
 * - NEXT：当前页上滑出，下一页从底部滑入
 * - PREV：当前页下滑出，上一页从顶部滑入
 * - 触摸追踪 Y 轴偏移
 */
class ScrollPageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        private const val SHADOW_HEIGHT_PX = 120
    }

    private val density: Float get() = readView.resources.displayMetrics.density
    private val shadowHeight: Float get() = SHADOW_HEIGHT_PX * density.coerceAtLeast(1f)

    override fun onDraw(canvas: Canvas) {
        val vw = readView.width.toFloat()
        val vh = readView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        // 垂直偏移（覆写父类的水平偏移逻辑）
        val oy = touchY - startY

        when {
            direction == Direction.NEXT -> {
                // 当前页上滑，下一页从底部滑入
                readView.curPageView.translationY = oy
                readView.nextPageView.translationY = vh + oy
                readView.prevPageView.translationY = -vh
                readView.curPageView.translationX = 0f
                readView.nextPageView.translationX = 0f
                readView.prevPageView.translationX = 0f
                readView.curPageView.translationZ = 2f
                readView.nextPageView.translationZ = 1f
                readView.prevPageView.translationZ = 0f
            }
            direction == Direction.PREV -> {
                // 当前页下滑，上一页从顶部滑入
                readView.curPageView.translationY = oy
                readView.prevPageView.translationY = -vh + oy
                readView.nextPageView.translationY = vh
                readView.curPageView.translationX = 0f
                readView.prevPageView.translationX = 0f
                readView.nextPageView.translationX = 0f
                readView.prevPageView.translationZ = 2f
                readView.curPageView.translationZ = 1f
                readView.nextPageView.translationZ = 0f
            }
            else -> {
                readView.curPageView.translationX = 0f
                readView.curPageView.translationY = 0f
                readView.prevPageView.translationX = 0f
                readView.prevPageView.translationY = -vh
                readView.nextPageView.translationX = 0f
                readView.nextPageView.translationY = vh
                readView.curPageView.translationZ = 2f
                readView.prevPageView.translationZ = 0f
                readView.nextPageView.translationZ = 0f
            }
        }
    }

    fun drawOverlay(canvas: Canvas) {
        // 垂直滚动模式不需要额外阴影
    }

    override fun startAnim(fromDrag: Boolean) {
        val vh = readView.height.toFloat()
        val fromY: Float; val toY: Float
        when {
            direction == Direction.NEXT -> {
                fromY = if (fromDrag) touchY else startY
                toY = startY - vh
            }
            direction == Direction.PREV -> {
                fromY = if (fromDrag) touchY else startY
                toY = startY + vh
            }
            else -> return
        }
        val dy = (toY - fromY).toInt()
        if (dy == 0) { direction = Direction.NONE; return }
        isRunning = true
        scroller.startScroll(0, fromY.toInt(), 0, dy, ANIM_DURATION)
        readView.postInvalidateOnAnimation()
    }

    fun startFromTap(dir: Direction) {
        direction = dir; touchY = startY; isFlipAnim = true; startAnim(fromDrag = false)
    }

    // 覆写回弹：垂直方向
    private fun startBounceBackVertical() {
        direction = Direction.NONE
        isFlipAnim = false
        val fromY = touchY.toInt()
        val toY = startY.toInt()
        val dy = toY - fromY
        if (dy == 0) { readView.invalidate(); return }
        isRunning = true
        scroller.startScroll(0, fromY, 0, dy, BOUNCE_DURATION)
        readView.postInvalidateOnAnimation()
    }

    // 覆写 computeScroll：Scroller 驱动 Y 轴
    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            touchY = scroller.currY.toFloat()
            readView.invalidate()
            return true
        }
        if (isRunning) {
            isRunning = false
            if (isFlipAnim) {
                isFlipAnim = false
                onAnimationComplete?.invoke()
            }
            direction = Direction.NONE
            touchY = startY
            readView.invalidate()
        }
        return false
    }

    override fun getOffsetX(): Float = touchY - startY

    // 覆写触摸处理：追踪垂直方向拖拽
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                abortAnim()
                startX = event.x
                startY = event.y
                touchX = startX
                touchY = startY
                lastX = startY  // 复用 lastX 追踪 Y
                hasMoved = false
                downTime = System.currentTimeMillis()
                direction = Direction.NONE
                isDragging = true
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    startY = event.y
                    lastX = event.y
                    touchY = event.y
                    hasMoved = true
                    isDragging = true
                    downTime = System.currentTimeMillis() - 500
                    return true
                }

                touchY = event.y
                touchX = event.x

                if (!hasMoved && Math.abs(event.y - startY) > 12f) {
                    hasMoved = true
                }

                if (hasMoved) {
                    val cumulativeDy = event.y - startY
                    direction = when {
                        cumulativeDy < -12f -> Direction.NEXT  // 上滑 = 下一页
                        cumulativeDy > 12f -> Direction.PREV   // 下滑 = 上一页
                        else -> Direction.NONE
                    }
                    readView.invalidate()
                }
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false

                val dy = event.y - startY
                val dt = System.currentTimeMillis() - downTime

                if (!hasMoved && dt < 300L) {
                    // 点击
                    val viewWidth = readView.width.toFloat()
                    val relX = event.x / viewWidth
                    when {
                        relX < 0.3f -> onTapLeft?.invoke()
                        relX > 0.7f -> onTapRight?.invoke()
                        else -> onTapCenter?.invoke()
                    }
                    direction = Direction.NONE
                    readView.invalidate()
                    return true
                }

                if (hasMoved) {
                    val viewHeight = readView.height.toFloat()
                    val fraction = Math.abs(dy) / viewHeight

                    direction = when {
                        dy < -12f -> Direction.NEXT
                        dy > 12f -> Direction.PREV
                        else -> Direction.NONE
                    }

                    if (fraction >= FLIP_THRESHOLD && direction != Direction.NONE) {
                        if (onCanFlip?.invoke(direction) == true) {
                            isFlipAnim = true
                            startAnim(fromDrag = true)
                        } else {
                            startBounceBackVertical()
                        }
                    } else {
                        startBounceBackVertical()
                    }
                } else {
                    direction = Direction.NONE
                    readView.invalidate()
                }
                return true
            }
        }
        return false
    }
}
