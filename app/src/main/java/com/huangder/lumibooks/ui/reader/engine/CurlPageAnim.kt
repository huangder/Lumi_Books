package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stable simulated page fold driven by a single normalized progress value.
 *
 * The turning and underlying pages are snapshotted once when a gesture starts.
 * While the fold is active, [ReadView] draws only these snapshots, so live child
 * views cannot leak through the clipped regions or change during slot rotation.
 */
class CurlPageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        private const val PROGRESS_SCALE = 10_000
        private const val TAP_DURATION_MS = 420
        private const val MIN_SETTLE_DURATION_MS = 160
        private const val MAX_SETTLE_DURATION_MS = 420
        private const val COMMIT_PROGRESS = 0.22f
        private const val FLING_VELOCITY_DP_PER_SECOND = 900f
    }

    override val drawsDirectlyOnCanvas: Boolean = true

    private val density = readView.resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.84f, 0f, 0f, 0f, 12f,
                    0f, 0.84f, 0f, 0f, 12f,
                    0f, 0f, 0.84f, 0f, 12f,
                    0f, 0f, 0f, 0.94f, 0f
                )
            )
        )
    }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density * 0.7f)
    }

    private val frontPath = Path()
    private val foldPath = Path()
    private val edgePath = Path()
    private val mirrorMatrix = Matrix()
    private val pageRect = RectF()

    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var snapshotsReady = false

    private var gestureStarted = false
    private var progress = 0f
    private var foldY = 0f
    private var settleTarget = 0f
    private var velocityTracker: VelocityTracker? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                gestureStarted = true
                startX = event.x
                startY = event.y
                touchX = event.x
                touchY = event.y
                foldY = event.y
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ensureGestureStarted(event)
                velocityTracker?.addMovement(event)

                touchX = event.x
                touchY = event.y
                foldY = event.y.coerceIn(0f, readView.height.toFloat())

                val dx = event.x - startX
                val newDirection = when {
                    dx < 0f -> Direction.NEXT
                    dx > 0f -> Direction.PREV
                    else -> Direction.NONE
                }

                if (newDirection != Direction.NONE && newDirection != direction) {
                    direction = newDirection
                    snapshotsReady = capturePages(newDirection)
                    isDragging = snapshotsReady
                    progress = 0f
                }

                if (snapshotsReady && direction != Direction.NONE) {
                    progress = (abs(dx) / readView.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    isDragging = true
                    readView.postInvalidateOnAnimation()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val xVelocity = velocityTracker?.xVelocity ?: 0f
                recycleVelocityTracker()
                gestureStarted = false
                isDragging = false

                if (!snapshotsReady || direction == Direction.NONE) {
                    resetToIdle()
                    return true
                }

                val directionalVelocity = when (direction) {
                    Direction.NEXT -> -xVelocity
                    Direction.PREV -> xVelocity
                    Direction.NONE -> 0f
                }
                val flingThreshold = FLING_VELOCITY_DP_PER_SECOND * density
                val canComplete = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    onCanFlip?.invoke(direction) == true &&
                    (progress >= COMMIT_PROGRESS || directionalVelocity >= flingThreshold)

                if (canComplete) {
                    isFlipAnim = true
                    settleTo(1f)
                } else {
                    startBounceBack()
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        if (!snapshotsReady || direction == Direction.NONE) return

        val width = readView.width.toFloat()
        val height = readView.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val p = progress.coerceIn(0f, 1f)
        val foldAmount = sin(PI.toFloat() * p).coerceAtLeast(0f)
        val edgeX = when (direction) {
            Direction.NEXT -> width * (1f - p)
            Direction.PREV -> width * p
            Direction.NONE -> return
        }
        val foldWidth = max(1f, min(width * 0.16f, width * 0.13f * foldAmount))
        val bend = min(width * 0.075f, foldWidth * 0.72f)
        val bendDirection = if (direction == Direction.NEXT) -1f else 1f
        val curve = FoldCurve(
            topX = edgeX,
            control1X = edgeX + bendDirection * bend,
            control1Y = (foldY * 0.72f).coerceIn(0f, height),
            control2X = edgeX + bendDirection * bend,
            control2Y = (foldY + (height - foldY) * 0.28f).coerceIn(0f, height),
            bottomX = edgeX
        )

        buildPaths(curve, foldWidth, height)
        pageRect.set(0f, 0f, width, height)

        canvas.save()
        canvas.clipRect(pageRect)
        drawPage(canvas, underBitmap)

        canvas.save()
        canvas.clipPath(frontPath)
        drawPage(canvas, turningBitmap)
        canvas.restore()

        drawUnderPageShadow(canvas, curve, foldWidth, width, height)
        drawFoldedBack(canvas, curve, foldWidth, width, height)
        drawFrontCrease(canvas, curve, foldWidth, width, height)
        canvas.restore()
    }

    override fun startAnim(fromDrag: Boolean) {
        if (!snapshotsReady || direction == Direction.NONE) return
        isFlipAnim = true
        settleTo(1f)
    }

    fun startFromTap(dir: Direction) {
        if (isRunning || isDragging || dir == Direction.NONE) return
        if (onCanFlip?.invoke(dir) != true) return

        abortAnim()
        direction = dir
        startX = if (dir == Direction.NEXT) readView.width.toFloat() else 0f
        startY = readView.height * 0.82f
        touchX = startX
        touchY = startY
        foldY = startY
        progress = 0f
        snapshotsReady = capturePages(dir)
        if (!snapshotsReady) {
            resetToIdle()
            return
        }

        isFlipAnim = true
        settleTo(1f, TAP_DURATION_MS)
    }

    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            progress = (scroller.currX.toFloat() / PROGRESS_SCALE).coerceIn(0f, 1f)
            if (scroller.currX == scroller.finalX) {
                finishSettle()
            } else {
                readView.postInvalidateOnAnimation()
            }
            return true
        }

        if (isRunning) {
            finishSettle()
            return true
        }
        return false
    }

    override fun startBounceBack() {
        isFlipAnim = false
        settleTo(0f)
    }

    override fun abortAnim() {
        if (!scroller.isFinished) scroller.abortAnimation()
        recycleVelocityTracker()
        gestureStarted = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
        settleTarget = 0f
        progress = 0f
        snapshotsReady = false
        direction = Direction.NONE
        resetChildViews()
        readView.invalidate()
    }

    override fun getOffsetX(): Float {
        val width = readView.width.toFloat()
        return when (direction) {
            Direction.NEXT -> -progress * width
            Direction.PREV -> progress * width
            Direction.NONE -> 0f
        }
    }

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    fun destroy() {
        abortAnim()
        turningBitmap?.recycle()
        underBitmap?.recycle()
        turningBitmap = null
        underBitmap = null
    }

    private fun ensureGestureStarted(event: MotionEvent) {
        if (gestureStarted) return
        gestureStarted = true
        startX = event.x
        startY = event.y
        touchX = event.x
        touchY = event.y
        foldY = event.y
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun settleTo(target: Float, fixedDurationMs: Int? = null) {
        settleTarget = target.coerceIn(0f, 1f)
        val start = (progress * PROGRESS_SCALE).toInt()
        val end = (settleTarget * PROGRESS_SCALE).toInt()
        val delta = end - start
        if (delta == 0) {
            finishSettle()
            return
        }

        val duration = fixedDurationMs ?: (
            MIN_SETTLE_DURATION_MS +
                (MAX_SETTLE_DURATION_MS - MIN_SETTLE_DURATION_MS) * abs(settleTarget - progress)
            ).toInt().coerceIn(MIN_SETTLE_DURATION_MS, MAX_SETTLE_DURATION_MS)

        isRunning = true
        scroller.startScroll(start, 0, delta, 0, duration)
        readView.postInvalidateOnAnimation()
    }

    private fun finishSettle() {
        if (!isRunning && settleTarget == 0f) {
            resetToIdle()
            return
        }

        val completedDirection = direction
        val completed = isFlipAnim && settleTarget >= 1f
        isRunning = false
        isDragging = false
        progress = settleTarget

        if (completed && completedDirection != Direction.NONE) {
            onAnimationComplete?.invoke()
        }

        isFlipAnim = false
        resetToIdle()
    }

    private fun resetToIdle() {
        progress = 0f
        settleTarget = 0f
        snapshotsReady = false
        direction = Direction.NONE
        isRunning = false
        isDragging = false
        resetChildViews()
        readView.invalidate()
    }

    private fun capturePages(dir: Direction): Boolean {
        if (readView.width <= 0 || readView.height <= 0) return false
        if (onCanFlip?.invoke(dir) != true) return false

        val turningView: View
        val underView: View
        when (dir) {
            Direction.NEXT -> {
                turningView = readView.curPageView
                underView = readView.nextPageView
            }
            Direction.PREV -> {
                turningView = readView.prevPageView
                underView = readView.curPageView
            }
            Direction.NONE -> return false
        }

        turningBitmap = snapshot(turningView, turningBitmap)
        underBitmap = snapshot(underView, underBitmap)
        return turningBitmap != null && underBitmap != null
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0 || view.width <= 0 || view.height <= 0) return null

        val bitmap = if (
            reusable == null || reusable.isRecycled ||
            reusable.width != width || reusable.height != height
        ) {
            reusable?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            reusable
        }

        bitmap.eraseColor(readView.bgColor)
        val savedTranslationX = view.translationX
        val savedTranslationY = view.translationY
        val savedAlpha = view.alpha
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
        view.draw(Canvas(bitmap))
        view.translationX = savedTranslationX
        view.translationY = savedTranslationY
        view.alpha = savedAlpha
        return bitmap
    }

    private fun buildPaths(curve: FoldCurve, foldWidth: Float, height: Float) {
        frontPath.reset()
        frontPath.moveTo(0f, 0f)
        frontPath.lineTo(curve.topX, 0f)
        frontPath.cubicTo(
            curve.control1X, curve.control1Y,
            curve.control2X, curve.control2Y,
            curve.bottomX, height
        )
        frontPath.lineTo(0f, height)
        frontPath.close()

        foldPath.reset()
        foldPath.moveTo(curve.topX, 0f)
        foldPath.lineTo(curve.topX + foldWidth, 0f)
        foldPath.cubicTo(
            curve.control1X + foldWidth, curve.control1Y,
            curve.control2X + foldWidth, curve.control2Y,
            curve.bottomX + foldWidth, height
        )
        foldPath.lineTo(curve.bottomX, height)
        foldPath.cubicTo(
            curve.control2X, curve.control2Y,
            curve.control1X, curve.control1Y,
            curve.topX, 0f
        )
        foldPath.close()

        edgePath.reset()
        edgePath.moveTo(curve.topX, 0f)
        edgePath.cubicTo(
            curve.control1X, curve.control1Y,
            curve.control2X, curve.control2Y,
            curve.bottomX, height
        )
    }

    private fun drawUnderPageShadow(
        canvas: Canvas,
        curve: FoldCurve,
        foldWidth: Float,
        width: Float,
        height: Float
    ) {
        val left = min(min(curve.topX, curve.bottomX), min(curve.control1X, curve.control2X))
        val shadowWidth = max(16f * density, foldWidth * 1.15f)
        val right = min(width, left + shadowWidth)
        if (right <= left) return

        shadePaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(0x38000000, 0x18000000, 0x00000000),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipOutPath(frontPath)
        canvas.drawRect(left, 0f, right, height, shadePaint)
        canvas.restore()
        shadePaint.shader = null
    }

    private fun drawFoldedBack(
        canvas: Canvas,
        curve: FoldCurve,
        foldWidth: Float,
        width: Float,
        height: Float
    ) {
        val bitmap = turningBitmap ?: return
        val edgeX = (curve.topX + curve.bottomX) * 0.5f

        mirrorMatrix.reset()
        mirrorMatrix.setScale(-1f, 1f)
        mirrorMatrix.postTranslate(edgeX * 2f, 0f)

        canvas.save()
        canvas.clipPath(foldPath)
        canvas.drawBitmap(bitmap, mirrorMatrix, backPaint)

        val background = readView.bgColor
        paperTintPaint.color = Color.argb(
            92,
            Color.red(background),
            Color.green(background),
            Color.blue(background)
        )
        canvas.drawRect(0f, 0f, width, height, paperTintPaint)

        val left = min(curve.topX, curve.bottomX)
        val right = min(width, max(curve.topX, curve.bottomX) + foldWidth)
        shadePaint.shader = LinearGradient(
            left, 0f, right.coerceAtLeast(left + 1f), 0f,
            intArrayOf(0x12000000, 0x44FFFFFF, 0x22000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, 0f, right, height, shadePaint)
        shadePaint.shader = null
        canvas.restore()
    }

    private fun drawFrontCrease(
        canvas: Canvas,
        curve: FoldCurve,
        foldWidth: Float,
        width: Float,
        height: Float
    ) {
        val edgeX = min(min(curve.topX, curve.bottomX), min(curve.control1X, curve.control2X))
        val shadowWidth = max(10f * density, foldWidth * 0.55f)
        val left = max(0f, edgeX - shadowWidth)
        val right = min(width, edgeX + 1f)
        if (right > left) {
            shadePaint.shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(0x00000000, 0x14000000, 0x30000000),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipPath(frontPath)
            canvas.drawRect(left, 0f, right, height, shadePaint)
            canvas.restore()
            shadePaint.shader = null
        }

        val background = readView.bgColor
        val isDark = Color.red(background) + Color.green(background) + Color.blue(background) < 300
        edgePaint.color = if (isDark) 0x44FFFFFF else 0x55FFFFFF
        canvas.drawPath(edgePath, edgePaint)
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            canvas.drawColor(readView.bgColor)
            return
        }
        canvas.drawBitmap(bitmap, null, pageRect, bitmapPaint)
    }

    private fun resetChildViews() {
        val width = readView.width.toFloat()
        readView.curPageView.translationX = 0f
        readView.curPageView.translationY = 0f
        readView.curPageView.alpha = 1f
        readView.curPageView.translationZ = 2f

        readView.prevPageView.translationX = -width
        readView.prevPageView.translationY = 0f
        readView.prevPageView.alpha = 0f
        readView.prevPageView.translationZ = 0f

        readView.nextPageView.translationX = width
        readView.nextPageView.translationY = 0f
        readView.nextPageView.alpha = 0f
        readView.nextPageView.translationZ = 0f
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private data class FoldCurve(
        val topX: Float,
        val control1X: Float,
        val control1Y: Float,
        val control2X: Float,
        val control2Y: Float,
        val bottomX: Float
    )
}
