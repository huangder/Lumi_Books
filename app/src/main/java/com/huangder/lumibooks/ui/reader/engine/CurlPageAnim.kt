package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Scroller

/** Lightweight MIT page curl shared by reflowed pages and fixed-layout EPUB. */
class CurlPageAnim(
    readView: PageAnimationSurface,
    private val trackCornerTouchDirectly: Boolean = false
) : PageAnimationController(readView) {
    companion object {
        internal const val TAP_DURATION_MS = 620
        private const val MIN_SETTLE_DURATION_MS = 180
        private const val MAX_SETTLE_DURATION_MS = 360
        private const val MIN_BOUNCE_DURATION_MS = 160
        private const val MAX_BOUNCE_DURATION_MS = 240
        private const val COMMIT_PROGRESS = 0.14f
        private const val FLING_VELOCITY_DP_PER_SECOND = 450f
        private const val SHADOW_SAMPLE_COUNT = 96
        private const val SHADOW_CENTER_COLOR = 0x1A000000
        private const val CORNER_SHADOW_CENTER_ALPHA = 0x28
        private const val CORNER_SHADOW_ENDPOINT_FADE_DP = 48f
    }

    internal enum class MotionState { IDLE, DRAGGING, SETTLING, DESTROYED }

    override val drawsDirectlyOnCanvas: Boolean = true

    private val density = readView.resources.displayMetrics.density
    private val curlScroller = Scroller(readView.context, AccelerateDecelerateInterpolator())
    private val verticalFrame = VerticalCurlFrame()
    private val cornerFrame = CornerCurlFrame()
    private val reflectionFrame = CurlReflectionFrame()
    private val gestureModeLock = CurlGestureModeLock()
    private val curledArea = Path()
    private val foldBand = Path()
    private val creasePath = Path()
    private val verticalOuterPath = Path()
    private val cornerCreasePath = Path()
    private val visibleCornerFold = Path()
    private val foldMatrix = Matrix()
    private val paperBackShadeMatrix = Matrix()
    private val paperBackShadeMatrixValues = FloatArray(9).apply { this[8] = 1f }
    private val pageSource = Rect()
    private val pageDestination = Rect()
    private val pagePaint = Paint(Paint.DITHER_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val backPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG
    ).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.91f, 0f, 0f, 0f, 8f,
                    0f, 0.91f, 0f, 0f, 8f,
                    0f, 0f, 0.91f, 0f, 8f,
                    0f, 0f, 0f, 0.96f, 0f
                )
            )
        )
    }
    private val backFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperBackShadeShader = LinearGradient(
        0f,
        0f,
        1f,
        0f,
        intArrayOf(0x18000000, 0x0A000000, 0x04000000),
        floatArrayOf(0f, 0.56f, 1f),
        Shader.TileMode.CLAMP
    )
    private val paperBackShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = paperBackShadeShader
    }
    private val foldShadowMeasure = PathMeasure()
    private val foldShadowPosition = FloatArray(2)
    private val foldShadowPathX = FloatArray(SHADOW_SAMPLE_COUNT)
    private val foldShadowPathY = FloatArray(SHADOW_SAMPLE_COUNT)
    private val foldShadowPositiveVertices = FloatArray((SHADOW_SAMPLE_COUNT + 1) * 4)
    private val foldShadowPositiveColors = IntArray((SHADOW_SAMPLE_COUNT + 1) * 2)
    private val cornerShadowColors = IntArray((SHADOW_SAMPLE_COUNT + 1) * 2)
    private val foldShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2C000000
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
    }

    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var ownedTurningBitmap: Bitmap? = null
    private var ownedUnderBitmap: Bitmap? = null
    private var turningLease: RenderResourceLease<Bitmap>? = null
    private var underLease: RenderResourceLease<Bitmap>? = null
    private var turningPageView: View? = null
    private var underPageView: View? = null
    private var framesReady = false

    private var gestureStarted = false
    private var dragOriginX = 0f
    private var settleCompletesPage = false
    private var velocityTracker: VelocityTracker? = null
    private var destroyed = false
    private var geometryMode = CurlGeometryMode.EDGE_VERTICAL
    private var gestureGeometryReady = true

    init {
        for (sample in 0..SHADOW_SAMPLE_COUNT) {
            val colorOffset = sample * 2
            foldShadowPositiveColors[colorOffset] = 0x00000000
            foldShadowPositiveColors[colorOffset + 1] = SHADOW_CENTER_COLOR
            cornerShadowColors[colorOffset] = 0x00000000
            cornerShadowColors[colorOffset + 1] = CORNER_SHADOW_CENTER_ALPHA shl 24
        }
    }

    internal var onMotionStateChanged: ((MotionState) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (destroyed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                gestureStarted = true
                startX = event.x
                startY = event.y
                touchX = readView.width.toFloat()
                touchY = readView.height * 0.5f
                dragOriginX = event.x
                direction = Direction.NONE
                geometryMode = CurlGeometryMode.EDGE_VERTICAL
                gestureGeometryReady = !trackCornerTouchDirectly
                gestureModeLock.begin(event.x, event.y)
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ensureGestureStarted(event)
                velocityTracker?.addMovement(event)
                val deltaX = event.x - startX
                val nextDirection = directionForHorizontalDelta(deltaX, 12f)
                if (direction == Direction.NONE && nextDirection != Direction.NONE) {
                    direction = nextDirection
                    dragOriginX = startX
                    framesReady = capturePages(direction)
                    if (framesReady) {
                        isDragging = true
                        onMotionStateChanged?.invoke(MotionState.DRAGGING)
                    }
                }
                if (framesReady && direction != Direction.NONE) {
                    val width = readView.width.toFloat()
                    val turnSign = horizontalTurnSign(direction)
                    geometryMode = gestureModeLock.lock(
                        width,
                        readView.height.toFloat(),
                        turnSign,
                        deltaX,
                        event.y - startY
                    )
                    gestureGeometryReady = !trackCornerTouchDirectly || gestureModeLock.isLocked
                    touchX = if (
                        trackCornerTouchDirectly && geometryMode != CurlGeometryMode.EDGE_VERTICAL
                    ) {
                        directCornerTouchX(width, event.x, turnSign)
                    } else {
                        val projectedDistance = (event.x - dragOriginX) * turnSign
                        (width - projectedDistance).coerceIn(-width, width)
                    }
                    touchY = if (geometryMode == CurlGeometryMode.EDGE_VERTICAL) {
                        readView.height * 0.5f
                    } else {
                        event.y.coerceIn(1f, readView.height - 1f)
                    }
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

                if (!framesReady || direction == Direction.NONE) {
                    resetToIdle()
                    return true
                }

                val width = readView.width.coerceAtLeast(1).toFloat()
                val progress = (1f - touchX / width).coerceIn(0f, 1f)
                val projectedVelocity = xVelocity * horizontalTurnSign(direction)
                val completes = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    onCanFlip?.invoke(direction) == true &&
                    (progress >= COMMIT_PROGRESS ||
                        projectedVelocity >= FLING_VELOCITY_DP_PER_SECOND * density)
                if (completes) {
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
        if (!framesReady || direction == Direction.NONE) return
        val width = readView.width.toFloat()
        val height = readView.height.toFloat()
        if (width <= 0f || height <= 0f) return
        if (trackCornerTouchDirectly && isDragging && !gestureGeometryReady) {
            drawUprightPage(canvas, turningBitmap, turningPageView)
            return
        }
        val mirrorGeometry = horizontalTurnSign(direction) > 0f

        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        drawUprightPage(canvas, underBitmap, underPageView)
        when (geometryMode) {
            CurlGeometryMode.EDGE_VERTICAL ->
                drawVerticalCurl(canvas, width, height, mirrorGeometry)
            CurlGeometryMode.CORNER_TOP, CurlGeometryMode.CORNER_BOTTOM ->
                drawCornerCurl(canvas, width, height, mirrorGeometry)
        }
        canvas.restoreToCount(save)
    }

    override fun startAnim(fromDrag: Boolean) {
        if (!framesReady || direction == Direction.NONE) return
        isFlipAnim = true
        settleToPage()
    }

    fun startFromTap(dir: Direction) {
        if (destroyed || isRunning || isDragging || dir == Direction.NONE) return
        if (onCanFlip?.invoke(dir) != true) return

        abortAnim()
        direction = dir
        startX = readView.width.toFloat()
        startY = readView.height * 0.5f
        touchX = readView.width.toFloat()
        touchY = readView.height * 0.5f
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = true
        gestureModeLock.reset()
        framesReady = capturePages(dir)
        if (!framesReady) {
            resetToIdle()
            return
        }
        isFlipAnim = true
        settleToPage(TAP_DURATION_MS)
    }

    override fun computeScroll(): Boolean {
        if (curlScroller.computeScrollOffset()) {
            touchX = curlScroller.currX.toFloat()
            touchY = curlScroller.currY.toFloat()
            if (curlScroller.currX == curlScroller.finalX &&
                curlScroller.currY == curlScroller.finalY
            ) {
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
        if (!framesReady || direction == Direction.NONE) {
            resetToIdle()
            return
        }
        isFlipAnim = false
        settleCompletesPage = false
        val width = readView.width.coerceAtLeast(1).toFloat()
        val progress = (1f - touchX / width).coerceIn(0f, 1f)
        val duration = (MIN_BOUNCE_DURATION_MS +
            (MAX_BOUNCE_DURATION_MS - MIN_BOUNCE_DURATION_MS) * progress).toInt()
        startScrollTo(width, settledY(), duration)
    }

    override fun abortAnim() {
        if (!curlScroller.isFinished) curlScroller.abortAnimation()
        recycleVelocityTracker()
        gestureStarted = false
        settleCompletesPage = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
        framesReady = false
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = true
        gestureModeLock.reset()
        releaseBorrowedFrames()
        resetChildViews()
        direction = Direction.NONE
        if (!destroyed) onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
    }

    override fun completeRunningFlipForNewInput(): Boolean {
        val completedDirection = if (
            isRunning && isFlipAnim && settleCompletesPage && direction != Direction.NONE
        ) direction else Direction.NONE
        if (completedDirection == Direction.NONE) return false

        if (!curlScroller.isFinished) curlScroller.abortAnimation()
        isRunning = false
        isDragging = false
        isFlipAnim = false
        settleCompletesPage = false
        framesReady = false
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = true
        gestureModeLock.reset()
        releaseBorrowedFrames()
        resetChildViews()
        direction = completedDirection
        onAnimationComplete?.invoke()
        direction = Direction.NONE
        onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
        return true
    }

    override fun getOffsetX(): Float = readView.width - touchX

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    fun destroy() {
        if (destroyed) return
        abortAnim()
        destroyed = true
        releaseBorrowedFrames()
        ownedTurningBitmap?.takeUnless { it.isRecycled }?.recycle()
        ownedUnderBitmap?.takeUnless { it.isRecycled }?.recycle()
        ownedTurningBitmap = null
        ownedUnderBitmap = null
        turningBitmap = null
        underBitmap = null
        turningPageView = null
        underPageView = null
        onMotionStateChanged?.invoke(MotionState.DESTROYED)
        onMotionStateChanged = null
    }

    private fun settleToPage(fixedDuration: Int? = null) {
        settleCompletesPage = true
        val width = readView.width.coerceAtLeast(1).toFloat()
        val targetX = -width
        val remaining = ((touchX - targetX) / (2f * width)).coerceIn(0f, 1f)
        val duration = fixedDuration ?: (MIN_SETTLE_DURATION_MS +
            (MAX_SETTLE_DURATION_MS - MIN_SETTLE_DURATION_MS) * remaining).toInt()
        startScrollTo(targetX, settledY(), duration)
    }

    private fun startScrollTo(targetX: Float, targetY: Float, duration: Int) {
        val delta = (targetX - touchX).toInt()
        val deltaY = (targetY - touchY).toInt()
        if (delta == 0 && deltaY == 0) {
            finishSettle()
            return
        }
        isRunning = true
        onMotionStateChanged?.invoke(MotionState.SETTLING)
        curlScroller.startScroll(touchX.toInt(), touchY.toInt(), delta, deltaY, duration)
        readView.postInvalidateOnAnimation()
    }

    private fun finishSettle() {
        val completedDirection = direction
        val commit = settleCompletesPage && isFlipAnim && completedDirection != Direction.NONE
        isRunning = false
        isDragging = false
        isFlipAnim = false
        settleCompletesPage = false
        framesReady = false
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = true
        gestureModeLock.reset()
        releaseBorrowedFrames()
        resetChildViews()
        if (commit) {
            direction = completedDirection
            onAnimationComplete?.invoke()
        }
        direction = Direction.NONE
        onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
    }

    private fun resetToIdle() {
        isRunning = false
        isDragging = false
        isFlipAnim = false
        settleCompletesPage = false
        framesReady = false
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = true
        gestureModeLock.reset()
        releaseBorrowedFrames()
        resetChildViews()
        direction = Direction.NONE
        onMotionStateChanged?.invoke(MotionState.IDLE)
        readView.invalidate()
    }

    private fun ensureGestureStarted(event: MotionEvent) {
        if (gestureStarted) return
        gestureStarted = true
        startX = event.x
        startY = event.y
        dragOriginX = event.x
        touchX = readView.width.toFloat()
        touchY = readView.height * 0.5f
        geometryMode = CurlGeometryMode.EDGE_VERTICAL
        gestureGeometryReady = !trackCornerTouchDirectly
        gestureModeLock.begin(event.x, event.y)
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun settledY(): Float = when (geometryMode) {
        CurlGeometryMode.CORNER_TOP -> 1f
        CurlGeometryMode.CORNER_BOTTOM -> readView.height - 1f
        CurlGeometryMode.EDGE_VERTICAL -> readView.height * 0.5f
    }

    private fun drawVerticalCurl(
        canvas: Canvas,
        width: Float,
        height: Float,
        mirrorGeometry: Boolean
    ) {
        if (!VerticalCurlGeometry.evaluate(width, height, touchX, verticalFrame)) {
            drawFallback(canvas, width)
            return
        }
        if (verticalFrame.terminal) return

        buildVerticalPaths(width, height, mirrorGeometry)
        val frontSave = canvas.save()
        canvas.clipOutPath(curledArea)
        drawUprightPage(canvas, turningBitmap, turningPageView)
        canvas.restoreToCount(frontSave)

        drawVerticalFoldOutlineShadow(canvas, width, mirrorGeometry)
        val cornerX = if (mirrorGeometry) 0f else width
        val foldedEdgeX = physicalX(verticalFrame.foldedEdgeX, width, mirrorGeometry)
        drawReflectedFold(
            canvas,
            cornerX,
            height * 0.5f,
            foldedEdgeX,
            height * 0.5f,
            foldBand,
            addPaperDepth = true
        )
        canvas.drawPath(creasePath, creasePaint)
    }

    private fun drawCornerCurl(
        canvas: Canvas,
        width: Float,
        height: Float,
        mirrorGeometry: Boolean
    ) {
        val fromBottom = geometryMode == CurlGeometryMode.CORNER_BOTTOM
        if (!CornerCurlGeometry.evaluate(
                width,
                height,
                touchX,
                touchY,
                fromBottom,
                cornerFrame
            )
        ) {
            drawFallback(canvas, width)
            return
        }

        buildCornerPaths(width, mirrorGeometry)
        visibleCornerFold.set(foldBand)
        if (!visibleCornerFold.op(curledArea, Path.Op.INTERSECT)) {
            visibleCornerFold.set(foldBand)
        }
        val frontSave = canvas.save()
        canvas.clipOutPath(curledArea)
        drawUprightPage(canvas, turningBitmap, turningPageView)
        canvas.restoreToCount(frontSave)

        drawCornerFoldOutlineShadow(canvas, width, height)

        drawReflectedFold(
            canvas,
            physicalX(cornerFrame.cornerX, width, mirrorGeometry),
            cornerFrame.cornerY,
            physicalX(cornerFrame.touchX, width, mirrorGeometry),
            cornerFrame.touchY,
            visibleCornerFold,
            addPaperDepth = true
        )
        canvas.drawPath(cornerCreasePath, creasePaint)
    }

    private fun buildVerticalPaths(width: Float, height: Float, mirrorGeometry: Boolean) {
        val crease = physicalX(verticalFrame.creaseX, width, mirrorGeometry)
        val outer = physicalX(verticalFrame.foldedEdgeX, width, mirrorGeometry)
        val bendSign = if (mirrorGeometry) 1f else -1f
        val inset = verticalFrame.curveInset * bendSign
        val mid = height * 0.5f

        creasePath.reset()
        creasePath.moveTo(crease, 0f)
        creasePath.cubicTo(
            crease + inset * 0.2f, height * 0.22f,
            crease + inset, height * 0.34f,
            crease + inset, mid
        )
        creasePath.cubicTo(
            crease + inset, height * 0.66f,
            crease + inset * 0.2f, height * 0.78f,
            crease, height
        )

        verticalOuterPath.reset()
        verticalOuterPath.moveTo(outer, 0f)
        verticalOuterPath.cubicTo(
            outer - inset * 0.1f, height * 0.22f,
            outer - inset * 0.45f, height * 0.34f,
            outer - inset * 0.45f, mid
        )
        verticalOuterPath.cubicTo(
            outer - inset * 0.45f, height * 0.66f,
            outer - inset * 0.1f, height * 0.78f,
            outer, height
        )

        curledArea.reset()
        curledArea.addPath(creasePath)
        val originalEdge = if (mirrorGeometry) 0f else width
        curledArea.lineTo(originalEdge, height)
        curledArea.lineTo(originalEdge, 0f)
        curledArea.close()

        foldBand.reset()
        foldBand.addPath(creasePath)
        foldBand.lineTo(outer, height)
        foldBand.cubicTo(
            outer - inset * 0.1f, height * 0.78f,
            outer - inset * 0.45f, height * 0.66f,
            outer - inset * 0.45f, mid
        )
        foldBand.cubicTo(
            outer - inset * 0.45f, height * 0.34f,
            outer - inset * 0.1f, height * 0.22f,
            outer, 0f
        )
        foldBand.close()
    }

    private fun buildCornerPaths(width: Float, mirrorGeometry: Boolean) {
        val start1X = physicalX(cornerFrame.start1X, width, mirrorGeometry)
        val control1X = physicalX(cornerFrame.control1X, width, mirrorGeometry)
        val end1X = physicalX(cornerFrame.end1X, width, mirrorGeometry)
        val end2X = physicalX(cornerFrame.end2X, width, mirrorGeometry)
        val control2X = physicalX(cornerFrame.control2X, width, mirrorGeometry)
        val start2X = physicalX(cornerFrame.start2X, width, mirrorGeometry)

        creasePath.reset()
        creasePath.moveTo(start1X, cornerFrame.start1Y)
        creasePath.quadTo(
            control1X,
            cornerFrame.control1Y,
            end1X,
            cornerFrame.end1Y
        )
        creasePath.lineTo(
            physicalX(cornerFrame.touchX, width, mirrorGeometry),
            cornerFrame.touchY
        )
        creasePath.lineTo(
            end2X,
            cornerFrame.end2Y
        )
        creasePath.quadTo(
            control2X,
            cornerFrame.control2Y,
            start2X,
            cornerFrame.start2Y
        )

        cornerCreasePath.reset()
        cornerCreasePath.moveTo(start1X, cornerFrame.start1Y)
        cornerCreasePath.quadTo(
            control1X,
            cornerFrame.control1Y,
            end1X,
            cornerFrame.end1Y
        )
        cornerCreasePath.moveTo(end2X, cornerFrame.end2Y)
        cornerCreasePath.quadTo(
            control2X,
            cornerFrame.control2Y,
            start2X,
            cornerFrame.start2Y
        )

        curledArea.reset()
        curledArea.addPath(creasePath)
        curledArea.lineTo(
            physicalX(cornerFrame.cornerX, width, mirrorGeometry),
            cornerFrame.cornerY
        )
        curledArea.close()

        foldBand.reset()
        foldBand.moveTo(
            physicalX(cornerFrame.vertex2X, width, mirrorGeometry),
            cornerFrame.vertex2Y
        )
        foldBand.lineTo(
            physicalX(cornerFrame.vertex1X, width, mirrorGeometry),
            cornerFrame.vertex1Y
        )
        foldBand.lineTo(
            physicalX(cornerFrame.end1X, width, mirrorGeometry),
            cornerFrame.end1Y
        )
        foldBand.lineTo(
            physicalX(cornerFrame.touchX, width, mirrorGeometry),
            cornerFrame.touchY
        )
        foldBand.lineTo(
            physicalX(cornerFrame.end2X, width, mirrorGeometry),
            cornerFrame.end2Y
        )
        foldBand.close()
    }

    private fun sampleFoldOutline(path: Path): Boolean {
        foldShadowMeasure.setPath(path, true)
        val length = foldShadowMeasure.length
        if (!length.isFinite() || length <= 1f) return false
        for (sample in 0 until SHADOW_SAMPLE_COUNT) {
            val distance = length * sample / SHADOW_SAMPLE_COUNT.toFloat()
            if (!foldShadowMeasure.getPosTan(
                    distance,
                    foldShadowPosition,
                    null
                )
            ) return false
            foldShadowPathX[sample] = foldShadowPosition[0]
            foldShadowPathY[sample] = foldShadowPosition[1]
        }
        return true
    }

    private fun sampleOpenFoldEdge(path: Path): Boolean {
        foldShadowMeasure.setPath(path, false)
        val length = foldShadowMeasure.length
        if (!length.isFinite() || length <= 1f) return false
        val finalSample = SHADOW_SAMPLE_COUNT - 1
        for (sample in 0 until SHADOW_SAMPLE_COUNT) {
            val distance = length * sample / finalSample.toFloat()
            if (!foldShadowMeasure.getPosTan(distance, foldShadowPosition, null)) return false
            foldShadowPathX[sample] = foldShadowPosition[0]
            foldShadowPathY[sample] = foldShadowPosition[1]
        }
        return true
    }

    private fun drawVerticalFoldOutlineShadow(
        canvas: Canvas,
        width: Float,
        mirrorGeometry: Boolean
    ) {
        val creaseX = physicalX(verticalFrame.creaseX, width, mirrorGeometry)
        val outerX = physicalX(verticalFrame.foldedEdgeX, width, mirrorGeometry)
        val paperDirection = if (creaseX >= outerX) 1f else -1f
        drawOpenFoldEdgeShadow(canvas, verticalOuterPath, -paperDirection)
        drawOpenFoldEdgeShadow(canvas, creasePath, paperDirection)
    }

    private fun drawOpenFoldEdgeShadow(canvas: Canvas, edge: Path, shadowSideX: Float) {
        if (!sampleOpenFoldEdge(edge)) return
        val radius = 25f * density

        for (sample in 0 until SHADOW_SAMPLE_COUNT) {
            val previous = (sample - 1).coerceAtLeast(0)
            val next = (sample + 1).coerceAtMost(SHADOW_SAMPLE_COUNT - 1)
            val x = foldShadowPathX[sample]
            val y = foldShadowPathY[sample]
            var tangentX = foldShadowPathX[next] - foldShadowPathX[previous]
            var tangentY = foldShadowPathY[next] - foldShadowPathY[previous]
            val tangentLength = kotlin.math.hypot(
                tangentX.toDouble(),
                tangentY.toDouble()
            ).toFloat().coerceAtLeast(0.001f)
            tangentX /= tangentLength
            tangentY /= tangentLength
            var normalX = -tangentY
            var normalY = tangentX
            if (normalX * shadowSideX < 0f) {
                normalX = -normalX
                normalY = -normalY
            }
            val vertexOffset = sample * 4

            foldShadowPositiveVertices[vertexOffset] = x + normalX * radius
            foldShadowPositiveVertices[vertexOffset + 1] = y + normalY * radius
            foldShadowPositiveVertices[vertexOffset + 2] = x
            foldShadowPositiveVertices[vertexOffset + 3] = y
        }

        val vertexValueCount = SHADOW_SAMPLE_COUNT * 4
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLE_STRIP,
            vertexValueCount,
            foldShadowPositiveVertices,
            0,
            null,
            0,
            foldShadowPositiveColors,
            0,
            null,
            0,
            0,
            foldShadowPaint
        )
    }

    private fun drawCornerFoldOutlineShadow(canvas: Canvas, width: Float, height: Float) {
        if (!sampleFoldOutline(visibleCornerFold)) return

        var signedAreaTwice = 0f
        for (point in 0 until SHADOW_SAMPLE_COUNT) {
            val next = (point + 1) % SHADOW_SAMPLE_COUNT
            signedAreaTwice += foldShadowPathX[point] * foldShadowPathY[next] -
                foldShadowPathX[next] * foldShadowPathY[point]
        }
        if (!signedAreaTwice.isFinite() || kotlin.math.abs(signedAreaTwice) < 1f) return

        val outwardSign = if (signedAreaTwice > 0f) -1f else 1f
        val radius = 25f * density
        for (sample in 0..SHADOW_SAMPLE_COUNT) {
            val point = sample % SHADOW_SAMPLE_COUNT
            val previous = (point - 1 + SHADOW_SAMPLE_COUNT) % SHADOW_SAMPLE_COUNT
            val next = (point + 1) % SHADOW_SAMPLE_COUNT
            val x = foldShadowPathX[point]
            val y = foldShadowPathY[point]

            var incomingX = x - foldShadowPathX[previous]
            var incomingY = y - foldShadowPathY[previous]
            val incomingLength = kotlin.math.hypot(
                incomingX.toDouble(),
                incomingY.toDouble()
            ).toFloat().coerceAtLeast(0.001f)
            incomingX /= incomingLength
            incomingY /= incomingLength

            var outgoingX = foldShadowPathX[next] - x
            var outgoingY = foldShadowPathY[next] - y
            val outgoingLength = kotlin.math.hypot(
                outgoingX.toDouble(),
                outgoingY.toDouble()
            ).toFloat().coerceAtLeast(0.001f)
            outgoingX /= outgoingLength
            outgoingY /= outgoingLength

            val incomingNormalX = -incomingY * outwardSign
            val incomingNormalY = incomingX * outwardSign
            val outgoingNormalX = -outgoingY * outwardSign
            val outgoingNormalY = outgoingX * outwardSign
            var miterX = incomingNormalX + outgoingNormalX
            var miterY = incomingNormalY + outgoingNormalY
            val miterLength = kotlin.math.hypot(
                miterX.toDouble(),
                miterY.toDouble()
            ).toFloat()
            if (miterLength <= 0.001f) {
                miterX = outgoingNormalX
                miterY = outgoingNormalY
            } else {
                miterX /= miterLength
                miterY /= miterLength
            }
            val projection = kotlin.math.abs(
                miterX * outgoingNormalX + miterY * outgoingNormalY
            ).coerceAtLeast(0.5f)
            val miterDistance = (radius / projection).coerceAtMost(radius * 1.35f)
            val vertexOffset = sample * 4
            val colorOffset = sample * 2
            val opacity = cornerShadowEndpointOpacity(
                x,
                y,
                width,
                height,
                CORNER_SHADOW_ENDPOINT_FADE_DP * density
            )
            val centerAlpha = (CORNER_SHADOW_CENTER_ALPHA * opacity).toInt()
                .coerceIn(0, CORNER_SHADOW_CENTER_ALPHA)

            foldShadowPositiveVertices[vertexOffset] = x + miterX * miterDistance
            foldShadowPositiveVertices[vertexOffset + 1] = y + miterY * miterDistance
            foldShadowPositiveVertices[vertexOffset + 2] = x
            foldShadowPositiveVertices[vertexOffset + 3] = y
            cornerShadowColors[colorOffset] = 0x00000000
            cornerShadowColors[colorOffset + 1] = centerAlpha shl 24
        }

        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLE_STRIP,
            (SHADOW_SAMPLE_COUNT + 1) * 4,
            foldShadowPositiveVertices,
            0,
            null,
            0,
            cornerShadowColors,
            0,
            null,
            0,
            0,
            foldShadowPaint
        )
    }

    private fun drawReflectedFold(
        canvas: Canvas,
        cornerX: Float,
        cornerY: Float,
        foldedEdgeX: Float,
        foldedEdgeY: Float,
        foldClip: Path,
        addPaperDepth: Boolean
    ) {
        val foldedBitmap = CurlLayerPolicy.foldedBack(turningBitmap, underBitmap)
            ?.takeUnless { it.isRecycled } ?: return
        if (!CurlReflectionGeometry.evaluate(
                cornerX,
                cornerY,
                foldedEdgeX,
                foldedEdgeY,
                reflectionFrame
            )
        ) return

        val save = canvas.save()
        canvas.clipPath(foldClip)
        backFillPaint.color = ColorMatrixPaperTone.dim(readView.bgColor)
        canvas.drawPaint(backFillPaint)
        val reflectedSave = canvas.save()
        foldMatrix.setValues(reflectionFrame.matrixValues)
        canvas.concat(foldMatrix)
        drawUprightPage(canvas, foldedBitmap, null, applyBackFilter = true)
        canvas.restoreToCount(reflectedSave)
        if (addPaperDepth) {
            drawPaperBackDepth(canvas, cornerX, cornerY, foldedEdgeX, foldedEdgeY)
        }
        canvas.restoreToCount(save)
    }

    private fun drawPaperBackDepth(
        canvas: Canvas,
        cornerX: Float,
        cornerY: Float,
        foldedEdgeX: Float,
        foldedEdgeY: Float
    ) {
        val creaseX = (cornerX + foldedEdgeX) * 0.5f
        val creaseY = (cornerY + foldedEdgeY) * 0.5f
        val axisX = foldedEdgeX - creaseX
        val axisY = foldedEdgeY - creaseY
        if (axisX * axisX + axisY * axisY < 1f) return

        val values = paperBackShadeMatrixValues
        values[0] = axisX
        values[1] = -axisY
        values[2] = creaseX
        values[3] = axisY
        values[4] = axisX
        values[5] = creaseY
        values[6] = 0f
        values[7] = 0f
        values[8] = 1f
        paperBackShadeMatrix.setValues(values)
        paperBackShadeShader.setLocalMatrix(paperBackShadeMatrix)
        canvas.drawPaint(paperBackShadePaint)
    }

    private fun drawFallback(canvas: Canvas, width: Float) {
        if (touchX > -width * 0.9f) {
            drawUprightPage(canvas, turningBitmap, turningPageView)
        }
    }

    private fun physicalX(internalX: Float, width: Float, mirrorGeometry: Boolean): Float =
        if (mirrorGeometry) width - internalX else internalX

    private fun capturePages(dir: Direction): Boolean {
        if (readView.width <= 0 || readView.height <= 0 || onCanFlip?.invoke(dir) != true) {
            return false
        }
        releaseBorrowedFrames()
        val turningView = readView.curPageView
        val targetView = when (dir) {
            Direction.NEXT -> readView.nextPageView
            Direction.PREV -> readView.prevPageView
            Direction.NONE -> return false
        }
        turningPageView = turningView
        underPageView = targetView

        val sourceTurningLease = (turningView as? CurlFrameSource)?.acquireCurlFrame()
        val sourceUnderLease = (targetView as? CurlFrameSource)?.acquireCurlFrame()
        if (sourceTurningLease != null && sourceUnderLease != null) {
            turningLease = sourceTurningLease
            underLease = sourceUnderLease
            turningBitmap = sourceTurningLease.resource
            underBitmap = sourceUnderLease.resource
            return true
        }
        sourceTurningLease?.close()
        sourceUnderLease?.close()

        ownedTurningBitmap = snapshot(turningView, ownedTurningBitmap)
        ownedUnderBitmap = snapshot(targetView, ownedUnderBitmap)
        turningBitmap = ownedTurningBitmap
        underBitmap = ownedUnderBitmap
        return turningBitmap != null && underBitmap != null
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0 || view.width <= 0 || view.height <= 0) return null
        val bitmap = if (reusable == null || reusable.isRecycled ||
            reusable.width != width || reusable.height != height
        ) {
            reusable?.takeUnless { it.isRecycled }?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else reusable

        bitmap.density = readView.resources.displayMetrics.densityDpi
        bitmap.eraseColor(readView.bgColor)
        val bitmapCanvas = Canvas(bitmap)
        val oldX = view.translationX
        val oldY = view.translationY
        val oldAlpha = view.alpha
        try {
            view.translationX = 0f
            view.translationY = 0f
            view.alpha = 1f
            view.draw(bitmapCanvas)
        } finally {
            view.translationX = oldX
            view.translationY = oldY
            view.alpha = oldAlpha
        }
        return bitmap
    }

    private fun drawUprightPage(
        canvas: Canvas,
        bitmap: Bitmap?,
        pageView: View?,
        applyBackFilter: Boolean = false
    ) {
        if (pageView != null && readView.drawPageDirectly(canvas, pageView)) return
        if (bitmap == null || bitmap.isRecycled) {
            canvas.drawColor(readView.bgColor)
            return
        }
        pageSource.set(0, 0, bitmap.width, bitmap.height)
        pageDestination.set(0, 0, readView.width, readView.height)
        canvas.drawBitmap(
            bitmap,
            pageSource,
            pageDestination,
            if (applyBackFilter) backPaint else pagePaint
        )
    }

    private fun releaseBorrowedFrames() {
        turningLease?.close()
        underLease?.close()
        turningLease = null
        underLease = null
        if (turningBitmap !== ownedTurningBitmap) turningBitmap = ownedTurningBitmap
        if (underBitmap !== ownedUnderBitmap) underBitmap = ownedUnderBitmap
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
