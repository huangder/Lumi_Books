package com.ebook.reader.ui.reader.engine

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * 水平视差滑动翻页动画。
 *
 * - PREV：上一页在上层从左侧全速滑入，覆盖下层当前页
 * - NEXT：当前页在上层全速左滑出，揭示下层下一页
 * - 下层：恒定速度比视差（上层速度 * PARALLAX_RATIO），从偏移位出发到达终点
 *   - NEXT: nextX = (vw + ox) * ratio，start=0.3*vw(右侧) → end=0(中央)
 *   - PREV: curX = ox * ratio，start=0(中央) → end=0.3*vw(右侧)
 * - 上层：R 角裁剪（saveLayer 离屏层） + 外阴影
 */
class SlidePageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        private const val SHADOW_WIDTH_PX = 250
        private const val CORNER_RADIUS_PX = 80f
        /** 下层页面速度比 = 上层速度 * PARALLAX_RATIO */
        private const val PARALLAX_RATIO = 0.3f
    }

    private val density: Float get() = readView.resources.displayMetrics.density
    private val shadowWidth: Float get() = SHADOW_WIDTH_PX * density.coerceAtLeast(1f)
    private val cornerRadius: Float get() = CORNER_RADIUS_PX * density.coerceAtLeast(1f)

    override fun onDraw(canvas: Canvas) {
        val vw = readView.width.toFloat()
        val vh = readView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        val ox = touchX - startX
        val cur = readView.getCurBitmap()

        when {
            // NEXT：上层 cur 全速左滑，下层 next 从右侧偏移位慢速滑入
            direction == Direction.NEXT -> {
                val next = readView.getNextBitmap()
                // 恒定速度比：nextX = (vw + ox) * ratio，start=0.3*vw(右侧) → end=0(中央)
                val lowerX = (vw + ox) * PARALLAX_RATIO
                drawLowerPage(canvas, next, lowerX, 0f, vw, vh)
                drawUpperPage(canvas, cur, ox, 0f, vw, vh)
            }
            // PREV：上层 prev 从左侧全速滑入，下层 cur 向右慢速偏移
            direction == Direction.PREV -> {
                val prev = readView.getPrevBitmap()
                // 恒定速度比：curX = ox * ratio，start=0(中央) → end=0.3*vw(右侧)
                val lowerX = ox * PARALLAX_RATIO
                drawLowerPage(canvas, cur, lowerX, 0f, vw, vh)
                drawUpperPage(canvas, prev, -vw + ox, 0f, vw, vh)
            }
            // 空闲
            else -> drawPageBitmap(canvas, cur, 0f, 0f, vw, vh)
        }
    }

    /** 上层：右侧 R 角（BitmapShader 方案）+ 外阴影 */
    private fun drawUpperPage(
        canvas: Canvas, bitmap: android.graphics.Bitmap?,
        x: Float, y: Float, vw: Float, vh: Float
    ) {
        if (bitmap == null || bitmap.isRecycled) return
        val r = cornerRadius

        // BitmapShader：用页面 bitmap 作为纹理源，偏移到 (x, y)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            matrix.postTranslate(x, y)
            shader?.setLocalMatrix(matrix)
        }

        // 1. 绘制四个角都圆角的矩形（GPU 原生支持 drawRoundRect）
        canvas.drawRoundRect(x, y, x + vw, y + vh, r, r, paint)
        // 2. 覆盖左侧圆角恢复直角（着色器从相同位图采样，无缝拼接）
        canvas.drawRect(x, y, x + r, y + r, paint)           // 左上角
        canvas.drawRect(x, y + vh - r, x + r, y + vh, paint) // 左下角

        // 3. 外阴影：从上层右边缘向右延伸，在下层页面上可见
        val right = x + vw
        val shStart = right
        val shEnd = (right + shadowWidth).coerceAtMost(readView.width.toFloat())
        if (shStart < readView.width.toFloat() && shEnd > shStart + 2f) {
            canvas.save()
            canvas.clipRect(shStart, y, readView.width.toFloat(), y + vh)

            val colors = intArrayOf(
                0x26000000.toInt(),
                0x18000000.toInt(),
                0x08000000.toInt(),
                0x02000000.toInt(),
                0x00000000
            )
            val stops = floatArrayOf(0.0f, 0.2f, 0.5f, 0.75f, 1.0f)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(shStart, 0f, shEnd, 0f, colors, stops, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(shStart, y, shEnd, y + vh, shadowPaint)
            canvas.restore()
        }
    }

    private fun drawLowerPage(
        canvas: Canvas, bitmap: android.graphics.Bitmap?,
        x: Float, y: Float, vw: Float, vh: Float
    ) {
        drawPageBitmap(canvas, bitmap, x, y, vw, vh)
    }

    private fun drawPageBitmap(
        canvas: Canvas, bitmap: android.graphics.Bitmap?,
        x: Float, y: Float, vw: Float, vh: Float
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.save()
            canvas.clipRect(0f, 0f, vw, vh)
            canvas.drawBitmap(bitmap, x, y, null)
            canvas.restore()
        }
    }

    // ── Scroller ──

    override fun startAnim(fromDrag: Boolean) {
        val vw = readView.width.toFloat()
        val fromX: Float; val toX: Float
        when {
            direction == Direction.NEXT -> { fromX = if (fromDrag) touchX else startX; toX = startX - vw }
            direction == Direction.PREV -> { fromX = if (fromDrag) touchX else startX; toX = startX + vw }
            else -> return
        }
        val dx = (toX - fromX).toInt()
        if (dx == 0) { direction = Direction.NONE; return }
        isRunning = true
        scroller.startScroll(fromX.toInt(), 0, dx, 0, ANIM_DURATION)
        readView.postInvalidateOnAnimation()
    }

    fun startFromTap(dir: Direction) {
        direction = dir; touchX = startX; startAnim(fromDrag = false)
    }
}
