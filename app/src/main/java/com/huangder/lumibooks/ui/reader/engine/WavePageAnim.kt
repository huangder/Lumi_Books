package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

/** Reveals the next or previous page through a soft, finger-tracked color sweep. */
class WavePageAnim(
    readView: PageAnimationSurface,
    private var baseDurationMs: Int = 400
) : PageAnimationController(readView) {
    override val drawsDirectlyOnCanvas: Boolean = true

    private val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val pageBounds = Rect()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val waveAmplitude = 0.014f
    private val edgeBandWidth get() = 52f * readView.resources.displayMetrics.density
    private var oldSnapshot: Bitmap? = null
    private var newSnapshot: Bitmap? = null
    private var snapshotDirection = Direction.NONE
    private var snapshotWidth = 0
    private var snapshotHeight = 0

    fun setWaveDuration(durationMs: Int) {
        baseDurationMs = durationMs.coerceIn(1, 1000)
    }

    override fun onDraw(canvas: Canvas) {
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0 || direction == Direction.NONE ||
            (!isRunning && !isDragging && !isFlipAnim)
        ) return
        ensureSnapshots(width, height, direction)
        val oldPage = oldSnapshot ?: return
        val newPage = newSnapshot ?: return
        pageBounds.set(0, 0, width, height)

        canvas.drawColor(readView.bgColor)
        drawSnapshot(canvas, oldPage)
        val progress = ((touchX - startX) * horizontalTurnSign(direction) / width)
            .coerceIn(0f, 1f)
        if (progress >= 1f) {
            drawSnapshot(canvas, newPage)
            return
        }
        val sweepFromRight = horizontalTurnSign(direction) < 0f
        val band = edgeBandWidth.coerceIn(12f, width / 2f)
        // The whole feathered edge, including its wave, must leave the viewport.
        // Clamping its center/gradient to the screen leaves half a band visible
        // until the terminal frame replaces it with the new page.
        val overscan = band + height * waveAmplitude
        val stripHeight = (height / 48f).coerceAtLeast(1f)
        var top = 0f
        while (top < height) {
            val bottom = (top + stripHeight).coerceAtMost(height.toFloat())
            val centerY = (top + bottom) * 0.5f
            val waveOffset = sin(centerY / height * Math.PI * 2.0).toFloat() * height * waveAmplitude
            val edge = if (sweepFromRight) {
                width + overscan - progress * (width + 2f * overscan) + waveOffset
            } else {
                -overscan + progress * (width + 2f * overscan) + waveOffset
            }
            val maskStart = edge - band
            val maskEnd = edge + band
            val layerBounds = if (sweepFromRight) {
                android.graphics.RectF(maskStart.coerceIn(0f, width.toFloat()), top, width.toFloat(), bottom)
            } else {
                android.graphics.RectF(0f, top, maskEnd.coerceIn(0f, width.toFloat()), bottom)
            }
            if (layerBounds.width() > 0f) {
                val save = canvas.saveLayer(layerBounds, null)
                drawSnapshot(canvas, newPage)
                val transparent = 0x00000000
                val opaque = 0xff000000.toInt()
                maskPaint.shader = if (sweepFromRight) {
                    LinearGradient(maskStart, 0f, maskEnd.coerceAtLeast(maskStart + 1f), 0f,
                        transparent, opaque, Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(maskStart, 0f, maskEnd.coerceAtLeast(maskStart + 1f), 0f,
                        opaque, transparent, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(layerBounds, maskPaint)
                canvas.restoreToCount(save)
                maskPaint.shader = null
            }
            if (sweepFromRight && edge + band < width) {
                canvas.save()
                canvas.clipRect((edge + band).coerceAtMost(width.toFloat()), top, width.toFloat(), bottom)
                drawSnapshot(canvas, newPage)
                canvas.restore()
            } else if (!sweepFromRight && edge - band > 0f) {
                canvas.save()
                canvas.clipRect(0f, top, (edge - band).coerceAtLeast(0f), bottom)
                drawSnapshot(canvas, newPage)
                canvas.restore()
            }
            top = bottom
        }
    }

    private fun drawSnapshot(canvas: Canvas, bitmap: Bitmap) {
        // Page snapshots are already in viewport pixels. The position-only
        // overload applies bitmap density scaling again on some canvases.
        canvas.drawBitmap(bitmap, null, pageBounds, pagePaint)
    }

    private fun ensureSnapshots(width: Int, height: Int, pageDirection: Direction) {
        if (snapshotWidth == width && snapshotHeight == height && snapshotDirection == pageDirection &&
            oldSnapshot != null && newSnapshot != null
        ) return
        recycleSnapshots()
        snapshotWidth = width
        snapshotHeight = height
        snapshotDirection = pageDirection
        oldSnapshot = capture(readView.curPageView, width, height)
        val target = if (pageDirection == Direction.NEXT) readView.nextPageView else readView.prevPageView
        newSnapshot = capture(target, width, height)
    }

    private fun capture(view: View, width: Int, height: Int): Bitmap? = runCatching {
        Bitmap.createBitmap(readView.resources.displayMetrics, width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            if (!readView.drawPageDirectly(canvas, view)) {
                val source = (view as? PageBitmapSource)?.pageBitmap
                if (source != null && !source.isRecycled) {
                    canvas.drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), pagePaint)
                } else {
                    view.draw(canvas)
                }
            }
        }
    }.getOrNull()

    private fun recycleSnapshots() {
        listOf(oldSnapshot, newSnapshot)
            .distinct()
            .forEach { bitmap -> if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() }
        oldSnapshot = null
        newSnapshot = null
        snapshotDirection = Direction.NONE
        snapshotWidth = 0
        snapshotHeight = 0
    }

    override fun startAnim(fromDrag: Boolean) {
        val width = readView.width.toFloat()
        if (width <= 0f || direction == Direction.NONE) return
        val sign = horizontalTurnSign(direction)
        val fromX = if (fromDrag) touchX.coerceIn(startX - width, startX + width) else startX
        val toX = startX + sign * width
        isRunning = true
        scroller.startScroll(fromX.toInt(), 0, (toX - fromX).toInt(), 0, baseDurationMs)
        readView.postInvalidateOnAnimation()
    }

    override fun computeScroll(): Boolean {
        val active = super.computeScroll()
        if (!active && !isRunning && !isDragging && !isFlipAnim) recycleSnapshots()
        return active
    }

    override fun completeRunningFlipForNewInput(): RunningFlipHandoff {
        val result = super.completeRunningFlipForNewInput()
        recycleSnapshots()
        return result
    }

    override fun abortAnim() {
        super.abortAnim()
        recycleSnapshots()
    }

    fun startFromTap(dir: Direction) {
        recycleSnapshots()
        direction = dir
        touchX = startX
        isFlipAnim = true
        startAnim(fromDrag = false)
    }
}
