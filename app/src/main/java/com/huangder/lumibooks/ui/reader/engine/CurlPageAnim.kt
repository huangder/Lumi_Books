package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bezier page-curl animation adapted to LumiBooks' snapshot and slot model.
 *
 * The curl is recalculated from the live finger position on every frame. The
 * current/previous page, folded back, underlying page and their shadows are
 * separate clipped layers instead of a translated translucent strip.
 */
class CurlPageAnim(readView: ReadView) : PageAnimationController(readView) {

    companion object {
        private const val TAP_DURATION_MS = 460
        private const val MIN_SETTLE_DURATION_MS = 180
        private const val MAX_SETTLE_DURATION_MS = 460
        private const val COMMIT_PROGRESS = 0.22f
        private const val FLING_VELOCITY_DP_PER_SECOND = 900f
        private const val GEOMETRY_EPSILON = 0.1f
    }

    override val drawsDirectlyOnCanvas: Boolean = true

    private val density = readView.resources.displayMetrics.density
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.94f, 0f, 0f, 0f, 6f,
                    0f, 0.94f, 0f, 0f, 6f,
                    0f, 0f, 0.94f, 0f, 6f,
                    0f, 0f, 0f, 0.96f, 0f
                )
            )
        )
    }
    private val backTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ambientShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeFeatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path0 = Path()
    private val path1 = Path()
    private val foldEdgePath = Path()
    private val bezierStart1 = PointF()
    private val bezierControl1 = PointF()
    private val bezierVertex1 = PointF()
    private val bezierEnd1 = PointF()
    private val bezierStart2 = PointF()
    private val bezierControl2 = PointF()
    private val bezierVertex2 = PointF()
    private val bezierEnd2 = PointF()

    private var renderTouchX = 0.1f
    private var renderTouchY = 0.1f
    private var cornerX = 0f
    private var cornerY = 0f
    private var middleX = 0f
    private var middleY = 0f
    private var degrees = 0f
    private var touchToCornerDistance = 0f
    private var maxLength = 0f
    private var isRightTopOrLeftBottom = false

    private val reflectionMatrix = Matrix()
    private val reflectionValues = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    private val folderShadowLR = gradient(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x00000000, 0x08000000, 0x14000000)
    )
    private val folderShadowRL = gradient(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x00000000, 0x08000000, 0x14000000)
    )
    private val backShadowLR = gradient(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )
    private val backShadowRL = gradient(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )
    private val frontShadowVLR = gradient(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )
    private val frontShadowVRL = gradient(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )
    private val frontShadowHTB = gradient(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )
    private val frontShadowHBT = gradient(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(0x1C000000, 0x0A000000, 0x02000000, 0x00000000)
    )

    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var snapshotsReady = false

    private var gestureStarted = false
    private var settleCompletesPage = false
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
                lastX = event.x
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ensureGestureStarted(event)
                velocityTracker?.addMovement(event)

                touchY = event.y
                val dx = event.x - startX
                val newDirection = when {
                    dx < 0f -> Direction.NEXT
                    dx > 0f -> Direction.PREV
                    else -> Direction.NONE
                }

                if (newDirection != Direction.NONE && newDirection != direction) {
                    direction = newDirection
                    configureCorner(startY)
                    snapshotsReady = capturePages(newDirection)
                    isDragging = snapshotsReady
                }

                if (snapshotsReady && direction != Direction.NONE) {
                    val width = readView.width.toFloat()
                    touchX = when (direction) {
                        Direction.NEXT -> width + 2f * min(dx, 0f)
                        Direction.PREV -> -width + 2f * max(dx, 0f)
                        Direction.NONE -> event.x
                    }.coerceIn(-width, width - 1f)
                    isDragging = true
                    lastX = event.x
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

                touchY = event.y
                val progress = abs(event.x - startX) /
                    readView.width.coerceAtLeast(1).toFloat()
                val directionalVelocity = when (direction) {
                    Direction.NEXT -> -xVelocity
                    Direction.PREV -> xVelocity
                    Direction.NONE -> 0f
                }
                val canComplete = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    onCanFlip?.invoke(direction) == true &&
                    (progress >= COMMIT_PROGRESS ||
                        directionalVelocity >= FLING_VELOCITY_DP_PER_SECOND * density)

                if (canComplete) {
                    isFlipAnim = true
                    settleToPage()
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

        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        drawPage(canvas, underBitmap)

        if (calculateCurlPoints()) {
            drawCurrentPageArea(canvas, turningBitmap)
            drawUnderlyingPageAndShadow(canvas, underBitmap)
            drawCurrentPageShadow(canvas)
            drawCurlAmbientShadow(canvas)
            drawFoldedBack(canvas, turningBitmap)
            drawFeatheredFoldEdge(canvas)
        } else {
            val fallback = if (direction == Direction.NEXT) turningBitmap else underBitmap
            drawPage(canvas, fallback)
        }
        canvas.restore()
    }

    override fun startAnim(fromDrag: Boolean) {
        if (!snapshotsReady || direction == Direction.NONE) return
        isFlipAnim = true
        settleToPage()
    }

    fun startFromTap(dir: Direction) {
        if (isRunning || isDragging || dir == Direction.NONE) return
        if (onCanFlip?.invoke(dir) != true) return

        abortAnim()
        direction = dir
        startY = readView.height * 0.82f
        configureCorner(startY)
        val nearCornerY = nearCornerY()
        if (dir == Direction.NEXT) {
            startX = readView.width - 1f
            touchX = startX
        } else {
            startX = 1f
            touchX = -readView.width.toFloat()
        }
        touchY = nearCornerY
        snapshotsReady = capturePages(dir)
        if (!snapshotsReady) {
            resetToIdle()
            return
        }

        isFlipAnim = true
        settleToPage(TAP_DURATION_MS)
    }

    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            touchX = scroller.currX.toFloat()
            touchY = scroller.currY.toFloat()
            if (scroller.currX == scroller.finalX && scroller.currY == scroller.finalY) {
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
        if (!snapshotsReady || direction == Direction.NONE) {
            resetToIdle()
            return
        }
        isFlipAnim = false
        settleCompletesPage = false
        val targetX = if (direction == Direction.NEXT) {
            readView.width - 1f
        } else {
            -readView.width.toFloat()
        }
        startScrollTo(targetX, nearCornerY())
    }

    override fun abortAnim() {
        if (!scroller.isFinished) scroller.abortAnimation()
        recycleVelocityTracker()
        gestureStarted = false
        settleCompletesPage = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
        snapshotsReady = false
        direction = Direction.NONE
        resetChildViews()
        readView.invalidate()
    }

    override fun getOffsetX(): Float = touchX - startX

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    fun destroy() {
        abortAnim()
        turningBitmap?.recycle()
        underBitmap?.recycle()
        turningBitmap = null
        underBitmap = null
    }

    private fun settleToPage(fixedDurationMs: Int? = null) {
        settleCompletesPage = true
        val targetX = if (direction == Direction.NEXT) {
            -readView.width.toFloat()
        } else {
            readView.width - 1f
        }
        startScrollTo(targetX, nearCornerY(), fixedDurationMs)
    }

    private fun startScrollTo(targetX: Float, targetY: Float, fixedDurationMs: Int? = null) {
        val width = readView.width.coerceAtLeast(1).toFloat()
        val remaining = (abs(targetX - touchX) / width).coerceIn(0f, 1.5f)
        val duration = fixedDurationMs ?: (
            MIN_SETTLE_DURATION_MS +
                (MAX_SETTLE_DURATION_MS - MIN_SETTLE_DURATION_MS) * min(remaining, 1f)
            ).toInt().coerceIn(MIN_SETTLE_DURATION_MS, MAX_SETTLE_DURATION_MS)

        val dx = (targetX - touchX).toInt()
        val dy = (targetY - touchY).toInt()
        if (dx == 0 && dy == 0) {
            finishSettle()
            return
        }

        isRunning = true
        scroller.startScroll(touchX.toInt(), touchY.toInt(), dx, dy, duration)
        readView.postInvalidateOnAnimation()
    }

    private fun finishSettle() {
        val completedDirection = direction
        val completed = settleCompletesPage && isFlipAnim
        isRunning = false
        isDragging = false

        if (completed && completedDirection != Direction.NONE) {
            onAnimationComplete?.invoke()
        }

        isFlipAnim = false
        resetToIdle()
    }

    private fun resetToIdle() {
        settleCompletesPage = false
        snapshotsReady = false
        direction = Direction.NONE
        isRunning = false
        isDragging = false
        resetChildViews()
        readView.invalidate()
    }

    private fun ensureGestureStarted(event: MotionEvent) {
        if (gestureStarted) return
        gestureStarted = true
        startX = event.x
        startY = event.y
        touchX = event.x
        touchY = event.y
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun configureCorner(initialY: Float) {
        cornerX = readView.width.toFloat()
        cornerY = if (initialY <= readView.height * 0.5f) 0f else readView.height.toFloat()
        isRightTopOrLeftBottom = cornerY == 0f
        maxLength = hypot(readView.width.toDouble(), readView.height.toDouble()).toFloat()
    }

    private fun nearCornerY(): Float {
        return if (cornerY == 0f) 1f else readView.height - 1f
    }

    private fun calculateCurlPoints(): Boolean {
        val width = readView.width.toFloat()
        val height = readView.height.toFloat()
        if (width <= 0f || height <= 0f) return false

        renderTouchX = touchX.coerceIn(-width * 1.2f, width * 1.2f)
        renderTouchY = touchY.coerceIn(1f, height - 1f)
        if (abs(renderTouchX - cornerX) < GEOMETRY_EPSILON) {
            renderTouchX = cornerX - GEOMETRY_EPSILON
        }
        if (abs(renderTouchY - cornerY) < GEOMETRY_EPSILON) {
            renderTouchY = cornerY + if (cornerY == 0f) GEOMETRY_EPSILON else -GEOMETRY_EPSILON
        }

        if (!calculateControlPoints()) return false

        // Keep the horizontal Bezier start on screen without freezing vertical finger input.
        if (renderTouchX > 0f && renderTouchX < width &&
            (bezierStart1.x < 0f || bezierStart1.x > width)
        ) {
            val originalStartX = bezierStart1.x
            val horizontalDistance = abs(cornerX - renderTouchX).coerceAtLeast(GEOMETRY_EPSILON)
            val normalizedStartX = if (originalStartX < 0f) {
                width - originalStartX
            } else {
                originalStartX
            }.coerceAtLeast(GEOMETRY_EPSILON)
            val correctedDistance = width * horizontalDistance / normalizedStartX
            val correctedX = abs(cornerX - correctedDistance)
            val correctedY = abs(
                cornerY - abs(cornerX - correctedX) * abs(cornerY - renderTouchY) /
                    horizontalDistance
            )
            renderTouchX = correctedX
            renderTouchY = correctedY.coerceIn(1f, height - 1f)
            if (!calculateControlPoints()) return false
        }

        bezierStart2.x = cornerX
        bezierStart2.y = bezierControl2.y - (cornerY - bezierControl2.y) / 2f
        touchToCornerDistance = hypot(
            (renderTouchX - cornerX).toDouble(),
            (renderTouchY - cornerY).toDouble()
        ).toFloat()

        val cross1 = lineIntersection(
            PointF(renderTouchX, renderTouchY), bezierControl1,
            bezierStart1, bezierStart2
        ) ?: return false
        val cross2 = lineIntersection(
            PointF(renderTouchX, renderTouchY), bezierControl2,
            bezierStart1, bezierStart2
        ) ?: return false
        bezierEnd1.set(cross1.x, cross1.y)
        bezierEnd2.set(cross2.x, cross2.y)

        bezierVertex1.x = (bezierStart1.x + 2f * bezierControl1.x + bezierEnd1.x) / 4f
        bezierVertex1.y = (2f * bezierControl1.y + bezierStart1.y + bezierEnd1.y) / 4f
        bezierVertex2.x = (bezierStart2.x + 2f * bezierControl2.x + bezierEnd2.x) / 4f
        bezierVertex2.y = (2f * bezierControl2.y + bezierStart2.y + bezierEnd2.y) / 4f

        return listOf(
            bezierStart1.x, bezierStart1.y, bezierStart2.x, bezierStart2.y,
            bezierControl1.x, bezierControl1.y, bezierControl2.x, bezierControl2.y,
            bezierEnd1.x, bezierEnd1.y, bezierEnd2.x, bezierEnd2.y
        ).all { it.isFinite() }
    }

    private fun calculateControlPoints(): Boolean {
        middleX = (renderTouchX + cornerX) / 2f
        middleY = (renderTouchY + cornerY) / 2f
        val denominatorX = cornerX - middleX
        val denominatorY = cornerY - middleY
        if (abs(denominatorX) < GEOMETRY_EPSILON || abs(denominatorY) < GEOMETRY_EPSILON) {
            return false
        }

        bezierControl1.x = middleX -
            (cornerY - middleY) * (cornerY - middleY) / denominatorX
        bezierControl1.y = cornerY
        bezierControl2.x = cornerX
        bezierControl2.y = middleY -
            (cornerX - middleX) * (cornerX - middleX) / denominatorY
        bezierStart1.x = bezierControl1.x - (cornerX - bezierControl1.x) / 2f
        bezierStart1.y = cornerY
        return bezierControl1.x.isFinite() && bezierControl2.y.isFinite()
    }

    private fun lineIntersection(
        p1: PointF,
        p2: PointF,
        p3: PointF,
        p4: PointF
    ): PointF? {
        val denominator = (p1.x - p2.x) * (p3.y - p4.y) -
            (p1.y - p2.y) * (p3.x - p4.x)
        if (abs(denominator) < 0.001f) return null

        val determinant1 = p1.x * p2.y - p1.y * p2.x
        val determinant2 = p3.x * p4.y - p3.y * p4.x
        val x = (determinant1 * (p3.x - p4.x) -
            (p1.x - p2.x) * determinant2) / denominator
        val y = (determinant1 * (p3.y - p4.y) -
            (p1.y - p2.y) * determinant2) / denominator
        return if (x.isFinite() && y.isFinite()) PointF(x, y) else null
    }

    private fun drawCurrentPageArea(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        path0.reset()
        path0.moveTo(bezierStart1.x, bezierStart1.y)
        path0.quadTo(bezierControl1.x, bezierControl1.y, bezierEnd1.x, bezierEnd1.y)
        path0.lineTo(renderTouchX, renderTouchY)
        path0.lineTo(bezierEnd2.x, bezierEnd2.y)
        path0.quadTo(bezierControl2.x, bezierControl2.y, bezierStart2.x, bezierStart2.y)
        path0.lineTo(cornerX, cornerY)
        path0.close()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.drawBitmap(bitmap, 0f, 0f, pagePaint)
        canvas.restore()
    }

    private fun drawUnderlyingPageAndShadow(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        path1.reset()
        path1.moveTo(bezierStart1.x, bezierStart1.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierStart2.x, bezierStart2.y)
        path1.lineTo(cornerX, cornerY)
        path1.close()

        degrees = Math.toDegrees(
            atan2(
                (bezierControl1.x - cornerX).toDouble(),
                bezierControl2.y - cornerY.toDouble()
            )
        ).toFloat()

        val left: Int
        val right: Int
        val shadow: GradientDrawable
        if (isRightTopOrLeftBottom) {
            left = bezierStart1.x.toInt()
            right = (bezierStart1.x + touchToCornerDistance / 3.4f).toInt()
            shadow = backShadowLR
        } else {
            left = (bezierStart1.x - touchToCornerDistance / 3.4f).toInt()
            right = bezierStart1.x.toInt()
            shadow = backShadowRL
        }

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(path1)
        canvas.drawBitmap(bitmap, 0f, 0f, pagePaint)
        canvas.rotate(degrees, bezierStart1.x, bezierStart1.y)
        shadow.setBounds(
            min(left, right),
            bezierStart1.y.toInt(),
            max(left, right),
            (bezierStart1.y + maxLength).toInt()
        )
        shadow.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentPageShadow(canvas: Canvas) {
        val angle = if (isRightTopOrLeftBottom) {
            PI / 4f - atan2(
                (bezierControl1.y - renderTouchY).toDouble(),
                (renderTouchX - bezierControl1.x).toDouble()
            ).toFloat()
        } else {
            PI / 4f - atan2(
                (renderTouchY - bezierControl1.y).toDouble(),
                (renderTouchX - bezierControl1.x).toDouble()
            ).toFloat()
        }
        val shadowWidth = max(22f * density, 28f)
        val offsetX = (shadowWidth * 1.414f * cos(angle)).toFloat()
        val offsetY = (shadowWidth * 1.414f * sin(angle)).toFloat()
        val shadowTipX = renderTouchX + offsetX
        val shadowTipY = if (isRightTopOrLeftBottom) {
            renderTouchY + offsetY
        } else {
            renderTouchY - offsetY
        }

        path1.reset()
        path1.moveTo(shadowTipX, shadowTipY)
        path1.lineTo(renderTouchX, renderTouchY)
        path1.lineTo(bezierControl1.x, bezierControl1.y)
        path1.lineTo(bezierStart1.x, bezierStart1.y)
        path1.close()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(path1)
        val verticalShadow = if (isRightTopOrLeftBottom) frontShadowVLR else frontShadowVRL
        val left = if (isRightTopOrLeftBottom) {
            bezierControl1.x.toInt()
        } else {
            (bezierControl1.x - shadowWidth).toInt()
        }
        val right = if (isRightTopOrLeftBottom) {
            (bezierControl1.x + shadowWidth).toInt()
        } else {
            (bezierControl1.x + 1f).toInt()
        }
        val rotation = Math.toDegrees(
            atan2(
                (renderTouchX - bezierControl1.x).toDouble(),
                (bezierControl1.y - renderTouchY).toDouble()
            )
        ).toFloat()
        canvas.rotate(rotation, bezierControl1.x, bezierControl1.y)
        verticalShadow.setBounds(
            min(left, right),
            (bezierControl1.y - maxLength).toInt(),
            max(left, right),
            bezierControl1.y.toInt()
        )
        verticalShadow.draw(canvas)
        canvas.restore()

        path1.reset()
        path1.moveTo(shadowTipX, shadowTipY)
        path1.lineTo(renderTouchX, renderTouchY)
        path1.lineTo(bezierControl2.x, bezierControl2.y)
        path1.lineTo(bezierStart2.x, bezierStart2.y)
        path1.close()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(path1)
        val horizontalShadow = if (isRightTopOrLeftBottom) frontShadowHTB else frontShadowHBT
        val top = if (isRightTopOrLeftBottom) {
            bezierControl2.y.toInt()
        } else {
            (bezierControl2.y - shadowWidth).toInt()
        }
        val bottom = if (isRightTopOrLeftBottom) {
            (bezierControl2.y + shadowWidth).toInt()
        } else {
            (bezierControl2.y + 1f).toInt()
        }
        val horizontalRotation = Math.toDegrees(
            atan2(
                (bezierControl2.y - renderTouchY).toDouble(),
                (bezierControl2.x - renderTouchX).toDouble()
            )
        ).toFloat()
        canvas.rotate(horizontalRotation, bezierControl2.x, bezierControl2.y)
        val referenceY = if (bezierControl2.y < 0f) {
            bezierControl2.y - readView.height
        } else {
            bezierControl2.y
        }
        val diagonalLength = hypot(bezierControl2.x.toDouble(), referenceY.toDouble()).toFloat()
        val shadowLeft = if (diagonalLength > maxLength) {
            bezierControl2.x - shadowWidth - diagonalLength
        } else {
            bezierControl2.x - maxLength
        }
        val shadowRight = if (diagonalLength > maxLength) {
            bezierControl2.x + maxLength - diagonalLength
        } else {
            bezierControl2.x
        }
        horizontalShadow.setBounds(
            min(shadowLeft, shadowRight).toInt(),
            min(top, bottom),
            max(shadowLeft, shadowRight).toInt(),
            max(top, bottom)
        )
        horizontalShadow.draw(canvas)
        canvas.restore()
    }

    private fun drawCurlAmbientShadow(canvas: Canvas) {
        val maxRadius = 105f * density
        val radius = max(40f * density, min(touchToCornerDistance * 0.25f, maxRadius))

        canvas.save()
        canvas.clipPath(path0)
        drawRadialShadow(canvas, renderTouchX, renderTouchY, radius, 0x16000000)
        drawRadialShadow(
            canvas,
            bezierVertex1.x,
            bezierVertex1.y,
            radius * 0.74f,
            0x10000000
        )
        canvas.restore()
        ambientShadowPaint.shader = null
    }

    private fun drawRadialShadow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        centerColor: Int
    ) {
        if (radius <= 1f || !centerX.isFinite() || !centerY.isFinite()) return
        ambientShadowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(centerColor, 0x06000000, 0x00000000),
            floatArrayOf(0f, 0.40f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, ambientShadowPaint)
    }

    private fun drawFeatheredFoldEdge(canvas: Canvas) {
        foldEdgePath.reset()
        foldEdgePath.moveTo(bezierStart1.x, bezierStart1.y)
        foldEdgePath.quadTo(
            bezierControl1.x,
            bezierControl1.y,
            bezierEnd1.x,
            bezierEnd1.y
        )
        foldEdgePath.lineTo(renderTouchX, renderTouchY)
        foldEdgePath.lineTo(bezierEnd2.x, bezierEnd2.y)
        foldEdgePath.quadTo(
            bezierControl2.x,
            bezierControl2.y,
            bezierStart2.x,
            bezierStart2.y
        )

        drawEdgeStroke(canvas, 30f * density, 0x01000000)
        drawEdgeStroke(canvas, 22f * density, 0x02000000)
        drawEdgeStroke(canvas, 14f * density, 0x03000000)
        drawEdgeStroke(canvas, 7f * density, 0x05000000)
        drawEdgeStroke(canvas, max(1.5f, density * 1.5f), 0x09000000)
    }

    private fun drawEdgeStroke(canvas: Canvas, width: Float, color: Int) {
        edgeFeatherPaint.strokeWidth = width
        edgeFeatherPaint.color = color
        canvas.drawPath(foldEdgePath, edgeFeatherPaint)
    }

    private fun drawFoldedBack(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        val horizontalFold = abs((bezierStart1.x + bezierControl1.x) / 2f - bezierControl1.x)
        val verticalFold = abs((bezierStart2.y + bezierControl2.y) / 2f - bezierControl2.y)
        val foldShadowWidth = min(horizontalFold, verticalFold)

        path1.reset()
        path1.moveTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierEnd1.x, bezierEnd1.y)
        path1.lineTo(renderTouchX, renderTouchY)
        path1.lineTo(bezierEnd2.x, bezierEnd2.y)
        path1.close()

        val folderShadow: GradientDrawable
        val left: Int
        val right: Int
        if (isRightTopOrLeftBottom) {
            left = (bezierStart1.x - 1f).toInt()
            right = (bezierStart1.x + foldShadowWidth + 1f).toInt()
            folderShadow = folderShadowLR
        } else {
            left = (bezierStart1.x - foldShadowWidth - 1f).toInt()
            right = (bezierStart1.x + 1f).toInt()
            folderShadow = folderShadowRL
        }

        val distance = hypot(
            (cornerX - bezierControl1.x).toDouble(),
            (bezierControl2.y - cornerY).toDouble()
        ).toFloat()
        if (distance < GEOMETRY_EPSILON) return
        val xRatio = (cornerX - bezierControl1.x) / distance
        val yRatio = (bezierControl2.y - cornerY) / distance
        reflectionValues[0] = 1f - 2f * yRatio * yRatio
        reflectionValues[1] = 2f * xRatio * yRatio
        reflectionValues[3] = reflectionValues[1]
        reflectionValues[4] = 1f - 2f * xRatio * xRatio
        reflectionMatrix.reset()
        reflectionMatrix.setValues(reflectionValues)
        reflectionMatrix.preTranslate(-bezierControl1.x, -bezierControl1.y)
        reflectionMatrix.postTranslate(bezierControl1.x, bezierControl1.y)

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(path1)
        canvas.drawColor(readView.bgColor)
        canvas.drawBitmap(bitmap, reflectionMatrix, backPaint)
        val background = readView.bgColor
        backTintPaint.color = Color.argb(
            42,
            Color.red(background),
            Color.green(background),
            Color.blue(background)
        )
        canvas.drawRect(
            0f,
            0f,
            readView.width.toFloat(),
            readView.height.toFloat(),
            backTintPaint
        )
        canvas.rotate(degrees, bezierStart1.x, bezierStart1.y)
        folderShadow.setBounds(
            min(left, right),
            bezierStart1.y.toInt(),
            max(left, right),
            (bezierStart1.y + maxLength).toInt()
        )
        folderShadow.draw(canvas)
        canvas.restore()
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
        try {
            view.translationX = 0f
            view.translationY = 0f
            view.alpha = 1f
            view.draw(Canvas(bitmap))
        } finally {
            view.translationX = savedTranslationX
            view.translationY = savedTranslationY
            view.alpha = savedAlpha
        }
        return bitmap
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            canvas.drawColor(readView.bgColor)
        } else {
            canvas.drawBitmap(bitmap, 0f, 0f, pagePaint)
        }
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

    private fun gradient(
        orientation: GradientDrawable.Orientation,
        colors: IntArray
    ): GradientDrawable {
        return GradientDrawable(orientation, colors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }
}
