package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/**
 * 水平视差滑动翻页动画。
 *
 * 通过 translationX 平移 PageContentView 实现翻页效果。
 * - PREV：上一页从左侧全速滑入，当前页 30% 视差右移
 * - NEXT：当前页全速左滑，下一页 30% 视差滑入
 * - 阴影在 dispatchDraw 中绘制
 */
class SlidePageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        private const val SHADOW_WIDTH_PX = 250
        private const val PARALLAX_RATIO = 0.3f
    }

    private val density: Float get() = readView.resources.displayMetrics.density
    private val shadowWidth: Float get() = SHADOW_WIDTH_PX * density.coerceAtLeast(1f)

    override fun onDraw(canvas: Canvas) {
        val vw = readView.width.toFloat()
        if (vw <= 0) return

        val ox = touchX - startX

        when {
            direction == Direction.NEXT -> {
                // cur 在上层全速左滑，next 在下层 30% 视差滑入
                readView.nextPageView.translationX = (vw + ox) * PARALLAX_RATIO
                readView.curPageView.translationX = ox
                readView.prevPageView.translationX = -vw
                // 🔥 确保页面可见（setPageTransition 可能把 alpha 设为 0）
                readView.curPageView.alpha = 1f
                readView.nextPageView.alpha = 1f
                readView.prevPageView.alpha = 0f
                // z-order: cur 在上层
                readView.curPageView.translationZ = 2f
                readView.nextPageView.translationZ = 1f
                readView.prevPageView.translationZ = 0f
            }
            direction == Direction.PREV -> {
                // prev 在上层全速滑入，cur 在下层 30% 视差右移
                readView.curPageView.translationX = ox * PARALLAX_RATIO
                readView.prevPageView.translationX = -vw + ox
                readView.nextPageView.translationX = vw
                // 🔥 确保页面可见
                readView.curPageView.alpha = 1f
                readView.prevPageView.alpha = 1f
                readView.nextPageView.alpha = 0f
                // z-order: prev 在上层
                readView.prevPageView.translationZ = 2f
                readView.curPageView.translationZ = 1f
                readView.nextPageView.translationZ = 0f
            }
            else -> {
                readView.curPageView.translationX = 0f
                readView.prevPageView.translationX = -vw
                readView.nextPageView.translationX = vw
                // 🔥 空闲状态：只显示当前页
                readView.curPageView.alpha = 1f
                readView.prevPageView.alpha = 0f
                readView.nextPageView.alpha = 0f
                // z-order: cur 在上层
                readView.curPageView.translationZ = 2f
                readView.prevPageView.translationZ = 0f
                readView.nextPageView.translationZ = 0f
            }
        }
    }

    /** 在 dispatchDraw 的 super 之后调用，绘制阴影叠加层 */
    fun drawOverlay(canvas: Canvas) {
        val vw = readView.width.toFloat()
        val vh = readView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        val ox = touchX - startX

        when {
            direction == Direction.NEXT -> drawShadow(canvas, ox + vw, vw, vh)
            direction == Direction.PREV -> drawShadow(canvas, ox, vw, vh)
            // The transient edge shadow is only valid while a page is moving. Rendering the
            // generic post-animation fade here always places it on the left and flashes on PREV.
            else -> Unit
        }
    }

    /** 翻页时的阴影 */
    private fun drawShadow(canvas: Canvas, edgeX: Float, vw: Float, vh: Float) {
        val shStart = edgeX.coerceIn(0f, vw)
        val shEnd = (edgeX + shadowWidth).coerceAtMost(vw)
        if (shEnd <= shStart + 2f) return

        canvas.save()
        canvas.clipRect(shStart, 0f, shEnd, vh)
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
        canvas.drawRect(shStart, 0f, shEnd, vh, shadowPaint)
        canvas.restore()
    }

    /** 翻页完成后的阴影渐隐 */
    private fun drawFadeOutShadow(canvas: Canvas, vw: Float, vh: Float) {
        val shEnd = shadowWidth.coerceAtMost(vw)
        if (shEnd < 2f) return

        val baseAlpha = (shadowFadeAlpha * 0x26).toInt().coerceIn(0, 0x26)
        val colors = intArrayOf(
            (baseAlpha shl 24),
            ((baseAlpha * 0.6).toInt() shl 24),
            ((baseAlpha * 0.2).toInt() shl 24),
            0
        )
        val stops = floatArrayOf(0.0f, 0.2f, 0.5f, 1.0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, shEnd, 0f, colors, stops, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, shEnd, vh, paint)
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
        direction = dir; touchX = startX; isFlipAnim = true; startAnim(fromDrag = false)
    }
}
