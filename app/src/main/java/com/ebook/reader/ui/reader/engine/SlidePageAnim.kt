package com.ebook.reader.ui.reader.engine

import android.graphics.Canvas

/**
 * 水平视差滑动翻页动画。
 *
 * 复现当前 WebView 的翻页动画效果：
 * - 跟手拖拽：两个 Bitmap 跟随手指滑动
 * - 松手翻页：300ms Scroller 动画完成
 * - 松手回弹：200ms Scroller 动画回弹
 * - 阴影：页面边缘渐变阴影
 */
class SlidePageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        /** 阴影宽度 dp（px 值在绘制时根据密度计算） */
        private const val SHADOW_WIDTH = 30
    }

    override fun onDraw(canvas: Canvas) {
        val viewWidth = readView.width.toFloat()
        val viewHeight = readView.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val offsetX = touchX - startX
        val curBitmap = readView.getCurBitmap()

        when {
            // ── 正向翻页（下一页） ──
            direction == Direction.NEXT -> {
                val targetBitmap = readView.getNextBitmap()
                // progress: 0（起始）→ 1（完成）
                val progress = (-offsetX / viewWidth).coerceIn(0f, 1f)
                // 目标页用 quadratic ease-out：先快后慢，营造"从底层滑入"的层级感
                val eased = 1f - (1f - progress) * (1f - progress)
                val nextX = viewWidth * (1f - eased)

                // 目标页（底层，轻微移动）
                drawPageBitmap(canvas, targetBitmap, nextX, 0f, viewWidth, viewHeight)
                // 当前页（顶层，全速左移）
                drawPageBitmap(canvas, curBitmap, offsetX, 0f, viewWidth, viewHeight)

                // 阴影：当前页右边缘，模拟"页面抬起"的光影
                val shadowX = viewWidth + offsetX
                if (shadowX > 0 && shadowX < viewWidth) {
                    canvas.drawRect(
                        shadowX, 0f,
                        (shadowX + SHADOW_WIDTH).coerceAtMost(viewWidth), viewHeight,
                        createShadowPaint(SHADOW_WIDTH, fromLeft = false)
                    )
                }
            }

            // ── 反向翻页（上一页） ──
            direction == Direction.PREV -> {
                val targetBitmap = readView.getPrevBitmap()
                val progress = (offsetX / viewWidth).coerceIn(0f, 1f)
                val eased = 1f - (1f - progress) * (1f - progress)
                val prevX = -viewWidth * (1f - eased)

                // 目标页（底层，轻微移动）
                drawPageBitmap(canvas, targetBitmap, prevX, 0f, viewWidth, viewHeight)
                // 当前页（顶层，全速右移）
                drawPageBitmap(canvas, curBitmap, offsetX, 0f, viewWidth, viewHeight)

                // 阴影：当前页左边缘
                val shadowX = offsetX
                if (shadowX > 0 && shadowX < viewWidth) {
                    canvas.drawRect(
                        (shadowX - SHADOW_WIDTH).coerceAtLeast(0f), 0f,
                        shadowX, viewHeight,
                        createShadowPaint(SHADOW_WIDTH, fromLeft = true)
                    )
                }
            }

            // ── 空闲 ──
            else -> {
                drawPageBitmap(canvas, curBitmap, 0f, 0f, viewWidth, viewHeight)
            }
        }
    }

    override fun startAnim(fromDrag: Boolean) {
        val viewWidth = readView.width.toFloat()
        val fromX: Float
        val toX: Float
        val dx: Int

        when {
            direction == Direction.NEXT -> {
                fromX = if (fromDrag) touchX else startX
                toX = startX - viewWidth
                dx = (toX - fromX).toInt()
            }
            direction == Direction.PREV -> {
                fromX = if (fromDrag) touchX else startX
                toX = startX + viewWidth
                dx = (toX - fromX).toInt()
            }
            else -> {
                // 点击翻页（没有拖拽方向，需要根据调用上下文确定）
                // 由外部设置 direction 后调用
                return
            }
        }

        isRunning = true
        scroller.startScroll(fromX.toInt(), 0, dx, 0, ANIM_DURATION)
        readView.invalidate()
    }

    /**
     * 从点击触发翻页（无拖拽，从 0 开始动画）。
     */
    fun startFromTap(dir: Direction) {
        direction = dir
        touchX = startX
        startAnim(fromDrag = false)
    }

    /**
     * 绘制单页 Bitmap，剪裁到视图范围。
     */
    private fun drawPageBitmap(
        canvas: Canvas,
        bitmap: android.graphics.Bitmap?,
        x: Float,
        y: Float,
        viewWidth: Float,
        viewHeight: Float
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.save()
            // 剪裁到屏幕范围
            canvas.clipRect(0f, 0f, viewWidth, viewHeight)
            canvas.drawBitmap(bitmap, x, y, null)
            canvas.restore()
        }
        // bitmap 为 null 时不做任何事（背景色由 Activity/Compose 提供）
    }
}
