package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas

/**
 * 渐变切换翻页动画。
 *
 * 滑动时不响应手势（页面不跟随手指移动），
 * 松手后通过 alpha 淡入淡出切换到下一页。
 *
 * 点击翻页仍然正常工作（左/右侧点击触发渐变动画）。
 */
class FadePageAnim(readView: ReadView) : PageAnimationController(readView) {

    /** 动画进度 0f..1f（0=当前页完全显示，1=下一页完全显示） */
    private var fadeProgress: Float = 0f

    override fun onDraw(canvas: Canvas) {
        val vw = readView.width.toFloat()
        val vh = readView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        when {
            // 动画进行中：当前页淡出，目标页淡入
            isRunning && direction == Direction.NEXT -> {
                readView.curPageView.alpha = 1f - fadeProgress
                readView.nextPageView.alpha = fadeProgress
                readView.prevPageView.alpha = 0f
                // 位置固定
                readView.curPageView.translationX = 0f
                readView.curPageView.translationY = 0f
                readView.nextPageView.translationX = 0f
                readView.nextPageView.translationY = 0f
                readView.prevPageView.translationX = -vw
                readView.prevPageView.translationY = 0f
                // z-order
                readView.nextPageView.translationZ = 2f
                readView.curPageView.translationZ = 1f
                readView.prevPageView.translationZ = 0f
            }
            isRunning && direction == Direction.PREV -> {
                readView.curPageView.alpha = 1f - fadeProgress
                readView.prevPageView.alpha = fadeProgress
                readView.nextPageView.alpha = 0f
                readView.curPageView.translationX = 0f
                readView.curPageView.translationY = 0f
                readView.prevPageView.translationX = 0f
                readView.prevPageView.translationY = 0f
                readView.nextPageView.translationX = vw
                readView.nextPageView.translationY = 0f
                readView.prevPageView.translationZ = 2f
                readView.curPageView.translationZ = 1f
                readView.nextPageView.translationZ = 0f
            }
            else -> {
                // 空闲状态：当前页完全显示，其他页隐藏
                readView.curPageView.alpha = 1f
                readView.prevPageView.alpha = 0f
                readView.nextPageView.alpha = 0f
                readView.curPageView.translationX = 0f
                readView.curPageView.translationY = 0f
                readView.prevPageView.translationX = -vw
                readView.prevPageView.translationY = 0f
                readView.nextPageView.translationX = vw
                readView.nextPageView.translationY = 0f
                readView.curPageView.translationZ = 2f
                readView.prevPageView.translationZ = 0f
                readView.nextPageView.translationZ = 0f
            }
        }
    }

    fun drawOverlay(canvas: Canvas) {
        // 渐变模式不需要阴影
    }

    // ── 触摸处理覆写 ──
    // 渐变模式下，滑动不响应，只在松手时触发动画

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                abortAnim()
                startX = event.x
                startY = event.y
                touchX = startX
                touchY = startY
                hasMoved = false
                downTime = System.currentTimeMillis()
                direction = Direction.NONE
                isDragging = true
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                // 渐变模式：滑动时不响应，只检测是否移动过
                if (Math.abs(event.x - startX) > 12f || Math.abs(event.y - startY) > 12f) {
                    hasMoved = true
                }
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false

                val dx = event.x - startX
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
                    return true
                }

                if (hasMoved) {
                    // 滑动手势：根据方向决定翻页
                    direction = when {
                        dx > 20f -> Direction.PREV
                        dx < -20f -> Direction.NEXT
                        else -> Direction.NONE
                    }

                    if (direction != Direction.NONE) {
                        // 渐变模式：不检查 onCanFlip，直接翻页
                        // （渐变效果下，即使目标页未加载也能平滑过渡）
                        isFlipAnim = true
                        startAnim(fromDrag = false)
                    }
                }
                return true
            }
        }
        return false
    }

    override fun startAnim(fromDrag: Boolean) {
        if (direction == Direction.NONE) return
        isRunning = true
        fadeProgress = 0f
        // 使用 Scroller 驱动 fadeProgress 从 0 到 1
        scroller.startScroll(0, 0, 1000, 0, ANIM_DURATION)
        readView.postInvalidateOnAnimation()
    }

    fun startFromTap(dir: Direction) {
        direction = dir
        isFlipAnim = true
        startAnim(fromDrag = false)
    }

    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            // 将 Scroller 的 X 值（0→1000）映射为 fadeProgress（0→1）
            fadeProgress = scroller.currX / 1000f
            readView.invalidate()
            return true
        }
        if (isRunning) {
            isRunning = false
            val wasFlip = isFlipAnim
            if (isFlipAnim) {
                isFlipAnim = false
                // 确保最终状态
                fadeProgress = 1f
                onAnimationComplete?.invoke()
            }
            direction = Direction.NONE
            readView.invalidate()
        }
        return false
    }
}
