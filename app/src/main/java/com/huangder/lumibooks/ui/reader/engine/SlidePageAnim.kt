package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import kotlin.math.roundToInt

/**
 * 水平视差滑动翻页动画。
 *
 * 通过 translationX 平移 PageContentView 实现翻页效果。
 * - PREV：上一页从左侧全速滑入，当前页 30% 视差右移
 * - NEXT：当前页全速左滑，下一页 30% 视差滑入
 * - 阴影在 dispatchDraw 中绘制
 */
class SlidePageAnim(
    readView: PageAnimationSurface,
    private var baseDurationMs: Int = ANIM_DURATION
) : PageAnimationController(readView) {

    fun setBaseDuration(durationMs: Int) {
        baseDurationMs = durationMs.coerceIn(100, 1000)
    }

    companion object {
        private const val SHADOW_WIDTH_PX = 250
        private const val PARALLAX_RATIO = 0.3f
    }

    private val density: Float get() = readView.resources.displayMetrics.density
    private val shadowWidth: Float get() = SHADOW_WIDTH_PX * density.coerceAtLeast(1f)

    // 🔥 复用 Paint 对象，避免每帧在 drawShadow 里 new Paint() + new LinearGradient()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pagePaint = Paint(Paint.DITHER_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val pageSourceRect = Rect()
    private val pageDestinationRect = Rect()

    override val drawsDirectlyOnCanvas: Boolean
        get() = readView.snapTranslationsToPixels && !readView.animatePageViewsDirectly

    override fun onDraw(canvas: Canvas) {
        if (drawsDirectlyOnCanvas) {
            drawSnapshotPages(canvas)
            drawOverlay(canvas)
            return
        }
        val vw = readView.width.toFloat()
        if (vw <= 0) return

        val ox = snapTranslation(touchX - startX)

        when {
            direction == Direction.NEXT -> {
                val turnSign = horizontalTurnSign(direction)
                if (readView.animatePageViewsDirectly) {
                    readView.curPageView.visibility = View.VISIBLE
                    readView.nextPageView.visibility = View.VISIBLE
                    readView.prevPageView.visibility = View.INVISIBLE
                }
                // cur 在上层全速左滑，next 在下层 30% 视差滑入
                readView.nextPageView.translationX = snapTranslation((-turnSign * vw + ox) * PARALLAX_RATIO)
                readView.curPageView.translationX = ox
                readView.prevPageView.translationX = idleTranslationX(Direction.PREV, vw)
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
                val turnSign = horizontalTurnSign(direction)
                if (readView.animatePageViewsDirectly) {
                    readView.curPageView.visibility = View.VISIBLE
                    readView.prevPageView.visibility = View.VISIBLE
                    readView.nextPageView.visibility = View.INVISIBLE
                }
                // prev 在上层全速滑入，cur 在下层 30% 视差右移
                readView.curPageView.translationX = snapTranslation(ox * PARALLAX_RATIO)
                readView.prevPageView.translationX = snapTranslation(-turnSign * vw + ox)
                readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, vw)
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
                if (readView.animatePageViewsDirectly) {
                    readView.curPageView.visibility = View.VISIBLE
                    readView.prevPageView.visibility = View.INVISIBLE
                    readView.nextPageView.visibility = View.INVISIBLE
                }
                readView.curPageView.translationX = 0f
                readView.prevPageView.translationX = idleTranslationX(Direction.PREV, vw)
                readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, vw)
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

    private fun drawSnapshotPages(canvas: Canvas) {
        val width = readView.width.toFloat()
        if (width <= 0f) return
        val offset = snapTranslation(touchX - startX)
        canvas.drawColor(readView.bgColor)
        when (direction) {
            Direction.NEXT -> {
                val turnSign = horizontalTurnSign(direction)
                drawPageBitmap(
                    canvas,
                    readView.nextPageView,
                    snapTranslation((-turnSign * width + offset) * PARALLAX_RATIO)
                )
                drawPageBitmap(canvas, readView.curPageView, offset)
            }
            Direction.PREV -> {
                val turnSign = horizontalTurnSign(direction)
                drawPageBitmap(canvas, readView.curPageView, snapTranslation(offset * PARALLAX_RATIO))
                drawPageBitmap(canvas, readView.prevPageView, snapTranslation(-turnSign * width + offset))
            }
            Direction.NONE -> drawPageBitmap(canvas, readView.curPageView, 0f)
        }
    }

    private fun drawPageBitmap(canvas: Canvas, view: android.view.View, left: Float) {
        canvas.save()
        canvas.translate(left, 0f)
        if (!readView.drawPageDirectly(canvas, view)) {
            val bitmap = (view as? PageBitmapSource)?.pageBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                pageSourceRect.set(0, 0, bitmap.width, bitmap.height)
                pageDestinationRect.set(0, 0, readView.width, readView.height)
                canvas.drawBitmap(bitmap, pageSourceRect, pageDestinationRect, pagePaint)
            }
        }
        canvas.restore()
    }

    /** 在 dispatchDraw 的 super 之后调用，绘制阴影叠加层 */
    private fun snapTranslation(value: Float): Float =
        if (readView.snapTranslationsToPixels) value.roundToInt().toFloat() else value

    fun drawOverlay(canvas: Canvas) {
        val vw = readView.width.toFloat()
        val vh = readView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        val ox = touchX - startX

        when {
            direction == Direction.NEXT -> {
                // 阴影随翻页进度渐隐：翻完时刚好消失，消除闪烁
                val turnSign = horizontalTurnSign(direction)
                val progress = (kotlin.math.abs(ox) / vw).coerceIn(0f, 1f)
                val edgeX = if (turnSign < 0f) ox + vw else ox
                drawShadow(
                    canvas = canvas,
                    edgeX = edgeX,
                    vw = vw,
                    vh = vh,
                    extendsRight = turnSign < 0f,
                    shadowAlpha = 1f - progress
                )
            }
            direction == Direction.PREV -> {
                // 阴影随拖动进度渐显：不会突然出现
                val turnSign = horizontalTurnSign(direction)
                val progress = (kotlin.math.abs(ox) / vw).coerceIn(0f, 1f)
                val edgeX = if (turnSign > 0f) ox else vw + ox
                drawShadow(
                    canvas = canvas,
                    edgeX = edgeX,
                    vw = vw,
                    vh = vh,
                    extendsRight = turnSign > 0f,
                    shadowAlpha = progress
                )
            }
            else -> {
                // 翻页完成后阴影已随进度自然消失，无需额外渐隐
            }
        }
    }

    /** 翻页时的阴影。shadowAlpha: 0f=不可见, 1f=满强度 */
    private fun drawShadow(
        canvas: Canvas,
        edgeX: Float,
        vw: Float,
        vh: Float,
        extendsRight: Boolean,
        shadowAlpha: Float = 1f
    ) {
        val shStart = if (extendsRight) edgeX.coerceIn(0f, vw)
            else (edgeX - shadowWidth).coerceAtLeast(0f)
        val shEnd = if (extendsRight) (edgeX + shadowWidth).coerceAtMost(vw)
            else edgeX.coerceIn(0f, vw)
        if (shEnd <= shStart + 2f) return

        canvas.save()
        canvas.clipRect(shStart, 0f, shEnd, vh)
        val a = shadowAlpha.coerceIn(0f, 1f)
        val baseColors = intArrayOf(
            ((0x26 * a).toInt() shl 24),
            ((0x18 * a).toInt() shl 24),
            ((0x08 * a).toInt() shl 24),
            ((0x02 * a).toInt() shl 24),
            0x00000000
        )
        val colors = if (extendsRight) baseColors else baseColors.reversedArray()
        val stops = floatArrayOf(0.0f, 0.2f, 0.5f, 0.75f, 1.0f)
        // 🔥 复用成员 shadowPaint，仅更新 shader，避免每帧 new Paint() + new LinearGradient()
        shadowPaint.shader = LinearGradient(shStart, 0f, shEnd, 0f, colors, stops, Shader.TileMode.CLAMP)
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
            direction == Direction.NEXT || direction == Direction.PREV -> {
                fromX = if (fromDrag) touchX else startX
                toX = startX + horizontalTurnSign(direction) * vw
            }
            else -> return
        }
        val dx = (toX - fromX).toInt()
        if (dx == 0) { direction = Direction.NONE; return }
        isRunning = true
        val capturedDurationMs = baseDurationMs
        scroller.startScroll(fromX.toInt(), 0, dx, 0, capturedDurationMs)
        readView.postInvalidateOnAnimation()
    }

    fun startFromTap(dir: Direction) {
        direction = dir; touchX = startX; isFlipAnim = true; startAnim(fromDrag = false)
    }
}
