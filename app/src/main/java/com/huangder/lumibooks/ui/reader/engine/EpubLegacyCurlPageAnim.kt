package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Bezier page-curl animation adapted to LumiBooks' snapshot and slot model.
 *
 * The curl is recalculated from the live finger position on every frame. The
 * current/previous page, folded back, underlying page and their shadows are
 * separate clipped layers instead of a translated translucent strip.
 */
internal class EpubLegacyCurlPageAnim(readView: PageAnimationSurface) : PageAnimationController(readView) {

    companion object {
        private const val TAP_DURATION_MS = 700
        private const val MIN_SETTLE_DURATION_MS = 300
        private const val MAX_SETTLE_DURATION_MS = 700
        private const val COMMIT_PROGRESS = 0.14f
        private const val FLING_VELOCITY_DP_PER_SECOND = 450f
        private const val GEOMETRY_EPSILON = 0.1f
    }

    override val drawsDirectlyOnCanvas: Boolean = true

    private val density = readView.resources.displayMetrics.density
    private val pagePaint = Paint(Paint.DITHER_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val pageSourceRect = Rect()
    private val pageDestinationRect = Rect()
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
    // 纸背镜像纹理用 BitmapShader + CLAMP 铺满整个背面区域，
    // 折痕附近不再露出 drawColor 底色形成浅色白边。
    private var backShader: BitmapShader? = null
    private var backShaderBitmap: Bitmap? = null

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
    private var touchToCornerDistance = 0f

    // 🔥 curl 拖拽锚点：direction 首次确定时记录手指位置，
    // 使 curl 初始偏移为 0，消除松开前纸张突变。
    private var curlDragOriginX = 0f

    private val reflectionMatrix = Matrix()
    private val reflectionValues = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var turningPageView: View? = null
    private var underPageView: View? = null
    private var snapshotsReady = false

    // 🔥 RadialGradient 缓存：避免每帧 new shader，减少 GC 压力。
    // 索引 0 = 手指落点 shadow，索引 1 = bezierVertex1 shadow。
    private val radialCx = floatArrayOf(Float.NaN, Float.NaN)
    private val radialCy = floatArrayOf(Float.NaN, Float.NaN)
    private val radialR  = floatArrayOf(0f, 0f)
    private val radialColors = intArrayOf(0, 0)
    private val radialShaders = arrayOfNulls<RadialGradient>(2)

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
                    // 🔥 记录 curl 起点锚点：以此为 0 offset 计算 touchX，
                    // 避免手势识别阈值（16px）造成纸张初始突变。
                    // Keep the distance already travelled while the host was
                    // deciding whether this is a paging gesture.
                    curlDragOriginX = startX
                }

                if (snapshotsReady && direction != Direction.NONE) {
                    val width = readView.width.toFloat()
                    // NEXT/PREV 都从角落（内部坐标 = 屏幕右角）平铺起步：
                    // PREV 通过水平镜像输出渲染，手指向右拖折算为内部 touch 向左，
                    // 卷曲从 0 连续开始，不再跳到已卷曲状态。
                    val curlDx = event.x - curlDragOriginX
                    val inwardDrag = when (direction) {
                        Direction.NEXT -> min(curlDx, 0f)
                        Direction.PREV -> -max(curlDx, 0f)
                        Direction.NONE -> 0f
                    }
                    touchX = (width + inwardDrag).coerceIn(-width, width - 1f)
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

        val saveCount = canvas.save()
        // PREV 采用"左掀角"模型：内部几何与 NEXT 相同（从右角向左卷），整帧水平镜像输出
        if (direction == Direction.PREV) {
            canvas.scale(-1f, 1f, width * 0.5f, 0f)
        }
        canvas.clipRect(0f, 0f, width, height)
        drawPage(canvas, underBitmap, underPageView)

        if (calculateCurlPoints()) {
            drawCurrentPageArea(canvas, turningBitmap, turningPageView)
            buildFoldEdgePath()
            drawCurlAmbientShadow(canvas)
            drawFoldedBack(canvas, turningBitmap)
            drawFoldEdgeShading(canvas)
        } else {
            // 几何退化（贴角 / 极端收尾）时整页兜底：未过半显示被掀起的页面，过半显示下层页面
            val targetSideReached = touchX <= 0f
            val fallbackBitmap = if (targetSideReached) underBitmap else turningBitmap
            val fallbackView = if (targetSideReached) underPageView else turningPageView
            drawPage(canvas, fallbackBitmap, fallbackView)
        }
        canvas.restoreToCount(saveCount)
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
        // NEXT/PREV 都从角落平铺起步（PREV 帧随后镜像输出），点击翻页同样从 0 卷曲
        startX = readView.width - 1f
        touchX = startX
        touchY = nearCornerY()
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
        // NEXT/PREV 的平铺态都在角落（内部坐标 = 右角），回弹一律退回右角
        startScrollTo(readView.width - 1f, nearCornerY())
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
        backPaint.shader = null
        backShader = null
        backShaderBitmap = null
    }

    private fun settleToPage(fixedDurationMs: Int? = null) {
        settleCompletesPage = true
        // PREV 与 NEXT 内部几何一致：touch 向左越过屏幕宽度即完成
        startScrollTo(-readView.width.toFloat(), nearCornerY(), fixedDurationMs)
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

    private fun drawCurrentPageArea(canvas: Canvas, bitmap: Bitmap?, pageView: View?) {
        if (bitmap == null && pageView == null) return
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
        drawPageContent(canvas, bitmap, pageView)
        canvas.restore()
    }

    private fun buildFoldEdgePath() {
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
    }

    /**
     * 沿完整折痕曲线的三段式羽化阴影：每个区域用多 pass 宽度递减、
     * 透明度小幅递增的描边叠加，形成由折痕向外大范围平滑衰减的渐变
     * （单步 ≤3% 避免色阶断层；折痕边缘处峰值约 20% 黑）。
     */
    private fun drawFoldEdgeShading(canvas: Canvas) {
        canvas.save()
        // 卷起的纸在下方页面上的投影（path0 内、path1 外），峰值约 12% 黑
        canvas.save()
        canvas.clipPath(path0)
        canvas.clipOutPath(path1)
        strokeFoldEdge(canvas, 40f * density, 0x03000000)
        strokeFoldEdge(canvas, 28f * density, 0x04000000)
        strokeFoldEdge(canvas, 18f * density, 0x06000000)
        strokeFoldEdge(canvas, 10f * density, 0x08000000)
        strokeFoldEdge(canvas, 4f * density, 0x0B000000)
        canvas.restore()
        // 纸背沿折痕的压暗（越贴折痕越深，峰值约 20% 黑）
        canvas.save()
        canvas.clipPath(path1)
        strokeFoldEdge(canvas, 36f * density, 0x03000000)
        strokeFoldEdge(canvas, 26f * density, 0x05000000)
        strokeFoldEdge(canvas, 17f * density, 0x07000000)
        strokeFoldEdge(canvas, 10f * density, 0x0A000000)
        strokeFoldEdge(canvas, 5f * density, 0x0D000000)
        strokeFoldEdge(canvas, max(1.5f, density * 1.5f), 0x12000000)
        canvas.restore()
        // 折痕前方平铺页面的轻微弯曲暗示，峰值约 8% 黑
        canvas.save()
        canvas.clipOutPath(path0)
        strokeFoldEdge(canvas, 30f * density, 0x03000000)
        strokeFoldEdge(canvas, 20f * density, 0x04000000)
        strokeFoldEdge(canvas, 11f * density, 0x06000000)
        strokeFoldEdge(canvas, 4.5f * density, 0x0A000000)
        canvas.restore()
        canvas.restore()
    }

    private fun strokeFoldEdge(canvas: Canvas, width: Float, color: Int) {
        edgeFeatherPaint.strokeWidth = width
        edgeFeatherPaint.color = color
        canvas.drawPath(foldEdgePath, edgeFeatherPaint)
    }

    private fun drawCurlAmbientShadow(canvas: Canvas) {
        val maxRadius = 105f * density
        val radius = max(40f * density, min(touchToCornerDistance * 0.25f, maxRadius))

        canvas.save()
        canvas.clipPath(path0)
        // 🔥 cacheIdx 0/1 — 避免每帧重建 RadialGradient
        drawRadialShadow(canvas, 0, renderTouchX, renderTouchY, radius, 0x16000000)
        drawRadialShadow(canvas, 1, bezierVertex1.x, bezierVertex1.y, radius * 0.74f, 0x10000000)
        canvas.restore()
        ambientShadowPaint.shader = null
    }

    private fun drawRadialShadow(
        canvas: Canvas,
        cacheIdx: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        centerColor: Int
    ) {
        if (radius <= 1f || !centerX.isFinite() || !centerY.isFinite()) return
        // 🔥 位移或半径变化超过 1.5px 才重建 shader，其余帧复用缓存对象
        if (abs(centerX - radialCx[cacheIdx]) > 1.5f ||
            abs(centerY - radialCy[cacheIdx]) > 1.5f ||
            abs(radius   - radialR[cacheIdx])  > 1.5f ||
            centerColor != radialColors[cacheIdx]
        ) {
            radialCx[cacheIdx]     = centerX
            radialCy[cacheIdx]     = centerY
            radialR[cacheIdx]      = radius
            radialColors[cacheIdx] = centerColor
            radialShaders[cacheIdx] = RadialGradient(
                centerX, centerY, radius,
                intArrayOf(centerColor, 0x06000000, 0x00000000),
                floatArrayOf(0f, 0.40f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        ambientShadowPaint.shader = radialShaders[cacheIdx]
        canvas.drawCircle(centerX, centerY, radius, ambientShadowPaint)
    }

    private fun drawFoldedBack(canvas: Canvas, bitmap: Bitmap?) {
        path1.reset()
        path1.moveTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierEnd1.x, bezierEnd1.y)
        path1.lineTo(renderTouchX, renderTouchY)
        path1.lineTo(bezierEnd2.x, bezierEnd2.y)
        path1.close()

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
        drawFoldBackTexture(canvas, bitmap)
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
        canvas.restore()
    }

    private fun drawFoldBackTexture(canvas: Canvas, bitmap: Bitmap?): Boolean {
        if (bitmap != null && !bitmap.isRecycled) {
            val shader = backShader?.takeIf { backShaderBitmap === bitmap }
                ?: BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                    backShader = it
                    backShaderBitmap = bitmap
                }
            // CLAMP 采样让镜像纹理铺满整个背面裁剪区（含折痕附近几何近似
            // 未覆盖的条带），不再露出 drawColor 的底色形成浅色白边。
            reflectionMatrix.postScale(
                readView.width.toFloat() / bitmap.width.coerceAtLeast(1),
                readView.height.toFloat() / bitmap.height.coerceAtLeast(1)
            )
            shader.setLocalMatrix(reflectionMatrix)
            backPaint.shader = shader
            canvas.drawRect(
                0f,
                0f,
                readView.width.toFloat(),
                readView.height.toFloat(),
                backPaint
            )
            return true
        }
        val directPage = turningPageView ?: return false
        val reflectionSave = canvas.save()
        canvas.concat(reflectionMatrix)
        val drewDirectPage = readView.drawPageDirectly(canvas, directPage)
        canvas.restoreToCount(reflectionSave)
        return drewDirectPage
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
                // PREV：当前页从左边缘掀起（帧镜像输出），露出上一页
                turningView = readView.curPageView
                underView = readView.prevPageView
            }
            Direction.NONE -> return false
        }

        turningPageView = turningView
        underPageView = underView
        if (readView.hasDirectPageRenderer) {
            turningBitmap?.recycle()
            turningBitmap = null
            underBitmap?.recycle()
            underBitmap = null
            return true
        }

        turningBitmap = snapshot(turningView, turningBitmap)
        underBitmap = snapshot(underView, underBitmap)
        return turningBitmap != null && underBitmap != null
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        val frozenPage = (view as? PageBitmapSource)?.pageBitmap
        if (frozenPage != null && !frozenPage.isRecycled) {
            return copyFrozenPage(frozenPage, reusable)
        }

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

        bitmap.density = readView.resources.displayMetrics.densityDpi
        bitmap.eraseColor(readView.bgColor)
        val bitmapCanvas = Canvas(bitmap)
        val savedTranslationX = view.translationX
        val savedTranslationY = view.translationY
        val savedAlpha = view.alpha
        try {
            view.translationX = 0f
            view.translationY = 0f
            view.alpha = 1f
            view.draw(bitmapCanvas)
        } finally {
            view.translationX = savedTranslationX
            view.translationY = savedTranslationY
            view.alpha = savedAlpha
        }
        return bitmap
    }


    private fun copyFrozenPage(source: Bitmap, reusable: Bitmap?): Bitmap {
        val bitmap = if (
            reusable == null || reusable.isRecycled ||
            reusable.width != source.width || reusable.height != source.height
        ) {
            reusable?.recycle()
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        } else {
            reusable
        }
        bitmap.density = source.density.takeIf { it != Bitmap.DENSITY_NONE }
            ?: readView.resources.displayMetrics.densityDpi
        bitmap.eraseColor(readView.bgColor)
        pageSourceRect.set(0, 0, source.width, source.height)
        pageDestinationRect.set(0, 0, bitmap.width, bitmap.height)
        Canvas(bitmap).drawBitmap(source, pageSourceRect, pageDestinationRect, pagePaint)
        return bitmap
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap?, pageView: View?) {
        if (!drawPageContent(canvas, bitmap, pageView)) {
            canvas.drawColor(readView.bgColor)
        }
    }

    private fun drawPageContent(canvas: Canvas, bitmap: Bitmap?, pageView: View?): Boolean {
        if (pageView != null && readView.drawPageDirectly(canvas, pageView)) return true
        if (bitmap == null || bitmap.isRecycled) return false
        pageSourceRect.set(0, 0, bitmap.width, bitmap.height)
        pageDestinationRect.set(0, 0, readView.width, readView.height)
        canvas.drawBitmap(bitmap, pageSourceRect, pageDestinationRect, pagePaint)
        return true
    }

    private fun resetChildViews() {
        if (readView.animatePageViewsDirectly) return
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
}

