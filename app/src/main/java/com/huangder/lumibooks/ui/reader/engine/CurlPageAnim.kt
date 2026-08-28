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
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * GPLv3-derived page-curl animation adapted from legado-E's SimulationPageDelegate.
 *
 * Source: https://github.com/Luoyacheng/legado-E/blob/main/app/src/main/java/io/legado/app/ui/book/read/page/delegate/SimulationPageDelegate.kt
 * The geometry and layer order are retained; Lumi adapts the lifecycle to
 * PageAnimationController and uses lighter shadow colors.
 *
 * The curl is recalculated from the live finger position on every frame. The
 * current/previous page, folded back, underlying page and their shadows are
 * separate clipped layers instead of a translated translucent strip.
 */
class CurlPageAnim(
    readView: PageAnimationSurface,
    @Suppress("UNUSED_PARAMETER") private val trackCornerTouchDirectly: Boolean = false,
    private var baseDurationMs: Int = 800
) : PageAnimationController(readView) {

    enum class MotionState { IDLE, DRAGGING, SETTLING, DESTROYED }

    /** Observed by ReadView to coordinate page-slot loading and input gating. */
    var onMotionStateChanged: ((MotionState) -> Unit)? = null

    companion object {
        private const val CSS_SETTLE_CORNER_DISTANCE_FACTOR = 0.18f
        private const val COMMIT_PROGRESS = 0.14f
        private const val FLING_VELOCITY_DP_PER_SECOND = 450f
    }

    override val drawsDirectlyOnCanvas: Boolean = true

    private val density = readView.resources.displayMetrics.density
    private val pagePaint = Paint(Paint.DITHER_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val pageSourceRect = Rect()
    private val pageDestinationRect = Rect()
    private val backTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foldTextureMatrix = Matrix()

    // 纸背画笔：镜像当前页并整体压暗，模拟油墨透过纸背的观感（与 EPUB 卷曲路径一致）。
    // 纹理用 BitmapShader + CLAMP 铺满整个背面区域，折痕附近不再露出打底色形成浅色边。
    private val foldTexturePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG
    ).apply {
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
    private var foldBackShader: BitmapShader? = null
    private var foldBackShaderBitmap: Bitmap? = null
    private val folderShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.TRANSPARENT,
            SimulationCurlShadowStyle.fold(
                SimulationCurlShadowStyle.FOLD_SHADOW_MAX_ALPHA
            )
        )
    )
    private val folderShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(
            Color.TRANSPARENT,
            SimulationCurlShadowStyle.fold(
                SimulationCurlShadowStyle.FOLD_SHADOW_MAX_ALPHA
            )
        )
    )
    private val backShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            SimulationCurlShadowStyle.backPage(
                SimulationCurlShadowStyle.BACK_PAGE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )
    private val backShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(
            SimulationCurlShadowStyle.backPage(
                SimulationCurlShadowStyle.BACK_PAGE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )
    private val frontShadowDrawableVLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            SimulationCurlShadowStyle.crease(
                SimulationCurlShadowStyle.CREASE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )
    private val frontShadowDrawableVRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(
            SimulationCurlShadowStyle.crease(
                SimulationCurlShadowStyle.CREASE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )
    private val frontShadowDrawableHTB = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            SimulationCurlShadowStyle.crease(
                SimulationCurlShadowStyle.CREASE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )
    private val frontShadowDrawableHBT = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(
            SimulationCurlShadowStyle.crease(
                SimulationCurlShadowStyle.CREASE_SHADOW_MAX_ALPHA
            ),
            Color.TRANSPARENT
        )
    )

    private val path0 = Path()
    private val path1 = Path()
    private val shadowClipPath = Path()
    private val viewportRegion = Region()
    private val curledRegion = Region()
    private val visibleFrontRegion = Region()
    private val visibleFoldRegion = Region()
    private val bezierStart1 = PointF()
    private val bezierControl1 = PointF()
    private val bezierVertex1 = PointF()
    private val bezierEnd1 = PointF()
    private val bezierStart2 = PointF()
    private val bezierControl2 = PointF()
    private val bezierVertex2 = PointF()
    private val bezierEnd2 = PointF()
    private val simulationFrame = SimulationCurlFrame()
    private val reflectionFrame = CurlReflectionFrame()
    private val gestureModeLock = CurlGestureModeLock()
    private var gestureMode = CurlGestureMode.EDGE_VERTICAL

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

    private var turningBitmap: Bitmap? = null
    private var underBitmap: Bitmap? = null
    private var ownedTurningBitmap: Bitmap? = null
    private var ownedUnderBitmap: Bitmap? = null
    private var turningLease: RenderResourceLease<Bitmap>? = null
    private var underLease: RenderResourceLease<Bitmap>? = null
    private var turningPageView: View? = null
    private var underPageView: View? = null
    private var snapshotsReady = false

    private var gestureStarted = false
    private var settleCompletesPage = false
    private var settleTargetY = Float.NaN
    private var settleExpedited = false
    private var capturedBaseDurationMs = baseDurationMs.coerceIn(300, 1200)
    private var finalFramePending = false
    private var velocityTracker: VelocityTracker? = null
    private var destroyed = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (destroyed) return false
        if (!readView.isPageProgressReversed) return handleTouchEvent(event)
        val mirroredEvent = MotionEvent.obtain(event)
        mirroredEvent.setLocation(readView.width - event.x, event.y)
        return try {
            handleTouchEvent(mirroredEvent)
        } finally {
            mirroredEvent.recycle()
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                capturedBaseDurationMs = baseDurationMs
                onMotionStateChanged?.invoke(MotionState.DRAGGING)
                gestureStarted = true
                startX = event.x
                startY = event.y
                touchX = event.x
                touchY = event.y
                lastX = event.x
                gestureMode = CurlGestureMode.EDGE_VERTICAL
                gestureModeLock.begin(event.x, event.y)
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ensureGestureStarted(event)
                velocityTracker?.addMovement(event)

                val dx = event.x - startX
                val newDirection = when {
                    dx < 0f -> Direction.NEXT
                    dx > 0f -> Direction.PREV
                    else -> Direction.NONE
                }

                if (newDirection != Direction.NONE && newDirection != direction) {
                    direction = newDirection
                    gestureModeLock.begin(startX, startY)
                    curlDragOriginX = startX
                }

                if (!snapshotsReady && direction != Direction.NONE) {
                    // Do not render a provisional EDGE_VERTICAL frame. Wait
                    // through the short classification dead zone, then lock
                    // one mode for the complete gesture before capturing any
                    // page frames.
                    gestureMode = gestureModeLock.lock(
                        width = readView.width.toFloat(),
                        height = readView.height.toFloat(),
                        physicalTurnSign = if (direction == Direction.NEXT) -1f else 1f,
                        deltaX = dx,
                        deltaY = event.y - startY
                    )
                    if (gestureModeLock.isLocked) {
                        configureCorner(startY)
                        snapshotsReady = capturePages(direction)
                        isDragging = snapshotsReady
                        if (snapshotsReady) onMotionStateChanged?.invoke(MotionState.DRAGGING)
                    }
                }

                if (snapshotsReady && direction != Direction.NONE) {
                    // NEXT 从右向左递减；PREV 从真正的屏外平铺态 -width
                    // 向右递增。两者都以实际拖动位移起步，避免方向刚确定
                    // 时目标页卷曲突然占据一部分屏幕。
                    val width = readView.width.toFloat()
                    touchX = SimulationCurlGeometry.canonicalTouchX(
                        width,
                        curlDragOriginX,
                        event.x,
                        if (direction == Direction.NEXT) {
                            SimulationCurlTurnDirection.NEXT
                        } else {
                            SimulationCurlTurnDirection.PREVIOUS
                        }
                    )
                    touchY = gestureTouchY(event.y)
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

                touchY = gestureTouchY(event.y)
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
                    onMotionStateChanged?.invoke(MotionState.SETTLING)
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
        // PREV 自身使用从左向右的 legado-E 坐标，不再镜像 NEXT 几何。
        // 这里只处理竖排/反向阅读布局的物理轴镜像。
        val mirror = readView.isPageProgressReversed
        if (!mirror) {
            drawCurl(canvas)
            return
        }
        val saveCount = canvas.save()
        canvas.scale(-1f, 1f, readView.width * 0.5f, 0f)
        drawCurl(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawCurl(canvas: Canvas) {
        if (!snapshotsReady || direction == Direction.NONE) return
        val width = readView.width.toFloat()
        val height = readView.height.toFloat()
        if (width <= 0f || height <= 0f) return

        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        drawPage(canvas, underBitmap, underPageView)

        if (calculateCurlPoints()) {
            // The turning sheet is outside path0 in both legado-E branches:
            // current over next for NEXT, previous over current for PREV.
            drawCurrentPageArea(canvas, turningBitmap, turningPageView)
            drawNextPageShadow(canvas)
            drawCurrentPageShadow(canvas)
            drawFoldedBack(canvas)
        } else {
            val showTurningPage = simulationDirection()?.let {
                SimulationCurlTurnMotion.fallbackShowsTurningPage(touchX, width, it)
            } ?: false
            val fallbackBitmap = if (showTurningPage) turningBitmap else underBitmap
            val fallbackView = if (showTurningPage) turningPageView else underPageView
            drawPage(canvas, fallbackBitmap, fallbackView)
        }
        canvas.restore()
        if (finalFramePending) {
            finalFramePending = false
            readView.post {
                if (isRunning && !isDragging) finishSettle()
            }
        }
    }

    override fun startAnim(fromDrag: Boolean) {
        if (!snapshotsReady || direction == Direction.NONE) return
        isFlipAnim = true
        onMotionStateChanged?.invoke(MotionState.SETTLING)
        settleToPage()
    }

    fun startFromTap(dir: Direction) {
        if (destroyed || isRunning || isDragging || dir == Direction.NONE) return
        if (onCanFlip?.invoke(dir) != true) return

        abortAnim()
        capturedBaseDurationMs = baseDurationMs
        direction = dir
        startY = readView.height * 0.82f
        gestureMode = CurlGestureMode.EDGE_VERTICAL
        gestureModeLock.reset()
        configureCorner(startY)
        // PREV 从屏外平铺态起步，NEXT 保持右侧平铺态。
        startX = flatTouchX()
        touchX = startX
        touchY = nearCornerY()
        snapshotsReady = capturePages(dir)
        if (!snapshotsReady) {
            resetToIdle()
            return
        }

        isFlipAnim = true
        onMotionStateChanged?.invoke(MotionState.SETTLING)
        settleToPage(capturedBaseDurationMs)
    }

    override fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            touchX = scroller.currX.toFloat()
            touchY = scroller.currY.toFloat()
            if (scroller.currX == scroller.finalX && scroller.currY == scroller.finalY) {
                if (readView.hasDirectPageRenderer) {
                    // Both commit and bounce-back keep the overlay alive until
                    // their terminal frame has actually passed through onDraw.
                    finalFramePending = true
                    readView.postInvalidateOnAnimation()
                } else {
                    finishSettle()
                }
            } else {
                readView.postInvalidateOnAnimation()
            }
            return true
        }

        if (isRunning) {
            if (readView.hasDirectPageRenderer) {
                finalFramePending = true
                readView.postInvalidateOnAnimation()
            } else {
                finishSettle()
            }
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
        onMotionStateChanged?.invoke(MotionState.SETTLING)
        startScrollTo(flatTouchX(), nearCornerY())
    }

    override fun abortAnim() {
        if (!scroller.isFinished) scroller.abortAnimation()
        recycleVelocityTracker()
        releaseBorrowedFrames()
        gestureStarted = false
        settleCompletesPage = false
        settleTargetY = Float.NaN
        settleExpedited = false
        finalFramePending = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
        snapshotsReady = false
        gestureMode = CurlGestureMode.EDGE_VERTICAL
        gestureModeLock.reset()
        resetChildViews()
        readView.invalidate()
        direction = Direction.NONE
        if (!destroyed) onMotionStateChanged?.invoke(MotionState.IDLE)
    }

    override fun completeRunningFlipForNewInput(): Boolean {
        val committedDirection = if (
            isFlipAnim && isRunning && settleCompletesPage && direction != Direction.NONE
        ) direction else Direction.NONE
        if (committedDirection == Direction.NONE) return false

        if (readView.hasDirectPageRenderer) {
            if (!settleExpedited) {
                if (!scroller.isFinished) scroller.abortAnimation()
                settleExpedited = true
                startScrollTo(
                    completionTargetX(),
                    resolvedSettleTargetY(),
                    curlExpeditedDurationMs(capturedBaseDurationMs)
                )
            }
            // Keep the current curl visible until its paper edge has actually
            // left the screen. The host can retain the new intent meanwhile.
            return false
        }

        if (!scroller.isFinished) scroller.abortAnimation()
        recycleVelocityTracker()
        gestureStarted = false
        settleCompletesPage = false
        isRunning = false
        isDragging = false
        isFlipAnim = false
        snapshotsReady = false
        gestureMode = CurlGestureMode.EDGE_VERTICAL
        gestureModeLock.reset()
        resetChildViews()
        direction = committedDirection
        onAnimationComplete?.invoke()
        direction = Direction.NONE
        readView.invalidate()
        onMotionStateChanged?.invoke(MotionState.IDLE)
        return true
    }

    override fun getOffsetX(): Float = when (direction) {
        Direction.NEXT -> readView.width - touchX
        Direction.PREV -> touchX
        Direction.NONE -> 0f
    }

    fun setBaseDuration(durationMs: Int) {
        baseDurationMs = durationMs.coerceIn(300, 1200)
    }

    fun drawOverlay(@Suppress("UNUSED_PARAMETER") canvas: Canvas) = Unit

    fun destroy() {
        if (destroyed) return
        abortAnim()
        releaseBorrowedFrames()
        destroyed = true
        ownedTurningBitmap?.takeUnless { it.isRecycled }?.recycle()
        ownedUnderBitmap?.takeUnless { it.isRecycled }?.recycle()
        turningBitmap = null
        underBitmap = null
        ownedTurningBitmap = null
        ownedUnderBitmap = null
        foldTexturePaint.shader = null
        foldBackShader = null
        foldBackShaderBitmap = null
        onMotionStateChanged?.invoke(MotionState.DESTROYED)
    }

    private fun settleToPage(fixedDurationMs: Int? = null) {
        settleCompletesPage = true
        settleExpedited = false
        finalFramePending = false
        settleTargetY = resolvedSettleTargetY()
        if (hasReachedCompletionTarget()) {
            finishSettle()
            return
        }
        startScrollTo(completionTargetX(), settleTargetY, fixedDurationMs)
    }

    private fun hasReachedCompletionTarget(): Boolean {
        val simulationDirection = simulationDirection() ?: return false
        return SimulationCurlTurnMotion.hasReachedCompletion(
            touchX,
            completionTargetX(),
            simulationDirection
        )
    }

    private fun completionTargetX(): Float {
        val width = readView.width.coerceAtLeast(1).toFloat()
        val simulationDirection = simulationDirection() ?: return touchX
        return SimulationCurlTurnMotion.completionTouchX(
            width,
            readView.height.toFloat(),
            simulationDirection,
            readView.hasDirectPageRenderer
        )
    }

    private fun resolvedSettleTargetY(): Float {
        if (gestureMode == CurlGestureMode.EDGE_VERTICAL) return nearCornerY()
        if (!readView.hasDirectPageRenderer) return nearCornerY()
        if (settleTargetY.isFinite()) return settleTargetY
        return cssSettleTargetY()
    }

    private fun cssSettleTargetY(): Float {
        val height = readView.height.coerceAtLeast(1).toFloat()
        val cornerDistance = height * CSS_SETTLE_CORNER_DISTANCE_FACTOR
        return if (cornerY == 0f) cornerDistance else height - cornerDistance
    }

    private fun startScrollTo(targetX: Float, targetY: Float, fixedDurationMs: Int? = null) {
        val flatX = flatTouchX()
        val flatY = nearCornerY()
        val completionX = completionTargetX()
        val completionY = resolvedSettleTargetY()
        val fullDistance = hypot(completionX - flatX, completionY - flatY)
        val remainingDistance = hypot(targetX - touchX, targetY - touchY)
        val duration = fixedDurationMs ?: curlSettleDurationMs(
            capturedBaseDurationMs,
            remainingDistance,
            fullDistance
        )

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
        releaseBorrowedFrames()
        settleCompletesPage = false
        settleTargetY = Float.NaN
        settleExpedited = false
        finalFramePending = false
        snapshotsReady = false
        gestureMode = CurlGestureMode.EDGE_VERTICAL
        gestureModeLock.reset()
        direction = Direction.NONE
        isRunning = false
        isDragging = false
        resetChildViews()
        readView.invalidate()
        onMotionStateChanged?.invoke(MotionState.IDLE)
    }

    private fun ensureGestureStarted(event: MotionEvent) {
        if (gestureStarted) return
        gestureStarted = true
        startX = event.x
        startY = event.y
        touchX = event.x
        touchY = event.y
        gestureMode = CurlGestureMode.EDGE_VERTICAL
        gestureModeLock.begin(event.x, event.y)
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun configureCorner(initialY: Float) {
        cornerX = readView.width.toFloat()
        cornerY = when (gestureMode) {
            CurlGestureMode.CORNER_TOP -> 0f
            CurlGestureMode.CORNER_BOTTOM -> readView.height.toFloat()
            CurlGestureMode.EDGE_VERTICAL -> when (
                SimulationCurlGeometry.cornerForTouchY(
                    readView.height.toFloat(),
                    initialY
                )
            ) {
                SimulationCurlCorner.TOP -> 0f
                SimulationCurlCorner.BOTTOM -> readView.height.toFloat()
                null -> 0f
            }
        }
    }

    private fun simulationDirection(): SimulationCurlTurnDirection? = when (direction) {
        Direction.NEXT -> SimulationCurlTurnDirection.NEXT
        Direction.PREV -> SimulationCurlTurnDirection.PREVIOUS
        Direction.NONE -> null
    }

    private fun flatTouchX(): Float {
        val simulationDirection = simulationDirection() ?: return touchX
        return SimulationCurlTurnMotion.flatTouchX(
            readView.width.toFloat(),
            simulationDirection
        )
    }

    private fun gestureTouchY(pointerY: Float): Float =
        if (gestureMode == CurlGestureMode.EDGE_VERTICAL) nearCornerY() else pointerY

    private fun nearCornerY(): Float {
        return if (cornerY == 0f) 1f else readView.height - 1f
    }

    private fun calculateCurlPoints(): Boolean {
        val width = readView.width.toFloat()
        val height = readView.height.toFloat()
        if (width <= 0f || height <= 0f) return false

        val horizontalLimit = if (readView.hasDirectPageRenderer) {
            CurlTerminalGeometry.completionDistance(width, height)
        } else {
            width * 1.2f
        }
        val corner = if (cornerY == 0f) {
            SimulationCurlCorner.TOP
        } else {
            SimulationCurlCorner.BOTTOM
        }
        if (!SimulationCurlGeometry.evaluate(
                width,
                height,
                touchX,
                touchY,
                corner,
                simulationFrame,
                horizontalLimit
            )
        ) return false

        renderTouchX = simulationFrame.touchX
        renderTouchY = simulationFrame.touchY
        middleX = simulationFrame.middleX
        middleY = simulationFrame.middleY
        touchToCornerDistance = simulationFrame.touchToCornerDistance
        bezierStart1.set(simulationFrame.start1X, simulationFrame.start1Y)
        bezierControl1.set(simulationFrame.control1X, simulationFrame.control1Y)
        bezierVertex1.set(simulationFrame.vertex1X, simulationFrame.vertex1Y)
        bezierEnd1.set(simulationFrame.end1X, simulationFrame.end1Y)
        bezierStart2.set(simulationFrame.start2X, simulationFrame.start2Y)
        bezierControl2.set(simulationFrame.control2X, simulationFrame.control2Y)
        bezierVertex2.set(simulationFrame.vertex2X, simulationFrame.vertex2Y)
        bezierEnd2.set(simulationFrame.end2X, simulationFrame.end2Y)
        return true
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

    /** Shadow cast by the lifted page onto the revealed page. */
    private fun drawNextPageShadow(canvas: Canvas) {
        shadowClipPath.reset()
        shadowClipPath.moveTo(bezierStart1.x, bezierStart1.y)
        shadowClipPath.lineTo(bezierVertex1.x, bezierVertex1.y)
        shadowClipPath.lineTo(bezierVertex2.x, bezierVertex2.y)
        shadowClipPath.lineTo(bezierStart2.x, bezierStart2.y)
        shadowClipPath.lineTo(cornerX, cornerY)
        shadowClipPath.close()

        val shadowExtent = touchToCornerDistance * 0.25f
        if (!shadowExtent.isFinite() || shadowExtent <= 0f) return
        val upperCorner = cornerY == 0f
        val left: Int
        val right: Int
        val drawable: GradientDrawable
        if (upperCorner) {
            left = bezierStart1.x.toInt()
            right = (bezierStart1.x + shadowExtent).toInt().coerceAtLeast(left + 1)
            drawable = backShadowDrawableLR
        } else {
            right = bezierStart1.x.toInt()
            left = (bezierStart1.x - shadowExtent).toInt().coerceAtMost(right - 1)
            drawable = backShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(shadowClipPath)
        canvas.rotate(foldRotationDegrees(), bezierStart1.x, bezierStart1.y)
        drawable.setBounds(
            left,
            bezierStart1.y.toInt(),
            right,
            (bezierStart1.y + pageDiagonal()).toInt()
        )
        drawable.draw(canvas)
        canvas.restore()
    }

    /** Two narrow directional gradients on the still-flat current page. */
    private fun drawCurrentPageShadow(canvas: Canvas) {
        val shadowWidth = SimulationCurlShadowStyle.widthPx(density)
        val upperCorner = cornerY == 0f
        val angle = if (upperCorner) {
            Math.PI / 4.0 - atan2(
                (bezierControl1.y - renderTouchY).toDouble(),
                (renderTouchX - bezierControl1.x).toDouble()
            )
        } else {
            Math.PI / 4.0 - atan2(
                (renderTouchY - bezierControl1.y).toDouble(),
                (renderTouchX - bezierControl1.x).toDouble()
            )
        }
        val projected = shadowWidth * 1.41421356f
        val outerX = renderTouchX + (projected * cos(angle)).toFloat()
        val verticalOffset = (projected * sin(angle)).toFloat()
        val outerY = if (upperCorner) {
            renderTouchY + verticalOffset
        } else {
            renderTouchY - verticalOffset
        }

        shadowClipPath.reset()
        shadowClipPath.moveTo(outerX, outerY)
        shadowClipPath.lineTo(renderTouchX, renderTouchY)
        shadowClipPath.lineTo(bezierControl1.x, bezierControl1.y)
        shadowClipPath.lineTo(bezierStart1.x, bezierStart1.y)
        shadowClipPath.close()

        var left: Int
        var right: Int
        var drawable: GradientDrawable
        if (upperCorner) {
            left = bezierControl1.x.toInt()
            right = (bezierControl1.x + shadowWidth).toInt().coerceAtLeast(left + 1)
            drawable = frontShadowDrawableVLR
        } else {
            right = (bezierControl1.x + 1f).toInt()
            left = (bezierControl1.x - shadowWidth).toInt().coerceAtMost(right - 1)
            drawable = frontShadowDrawableVRL
        }
        var rotation = Math.toDegrees(
            atan2(
                (renderTouchX - bezierControl1.x).toDouble(),
                (bezierControl1.y - renderTouchY).toDouble()
            )
        ).toFloat()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(shadowClipPath)
        canvas.rotate(rotation, bezierControl1.x, bezierControl1.y)
        drawable.setBounds(
            left,
            (bezierControl1.y - pageDiagonal()).toInt(),
            right,
            bezierControl1.y.toInt()
        )
        drawable.draw(canvas)
        canvas.restore()

        shadowClipPath.reset()
        shadowClipPath.moveTo(outerX, outerY)
        shadowClipPath.lineTo(renderTouchX, renderTouchY)
        shadowClipPath.lineTo(bezierControl2.x, bezierControl2.y)
        shadowClipPath.lineTo(bezierStart2.x, bezierStart2.y)
        shadowClipPath.close()

        if (upperCorner) {
            left = bezierControl2.y.toInt()
            right = (bezierControl2.y + shadowWidth).toInt().coerceAtLeast(left + 1)
            drawable = frontShadowDrawableHTB
        } else {
            right = (bezierControl2.y + 1f).toInt()
            left = (bezierControl2.y - shadowWidth).toInt().coerceAtMost(right - 1)
            drawable = frontShadowDrawableHBT
        }
        rotation = Math.toDegrees(
            atan2(
                (bezierControl2.y - renderTouchY).toDouble(),
                (bezierControl2.x - renderTouchX).toDouble()
            )
        ).toFloat()
        val adjustedControlY = if (bezierControl2.y < 0f) {
            bezierControl2.y - readView.height
        } else {
            bezierControl2.y
        }
        val projectedLength = hypot(
            bezierControl2.x.toDouble(),
            adjustedControlY.toDouble()
        ).toFloat()
        val diagonal = pageDiagonal()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(shadowClipPath)
        canvas.rotate(rotation, bezierControl2.x, bezierControl2.y)
        if (projectedLength > diagonal) {
            drawable.setBounds(
                (bezierControl2.x - shadowWidth - projectedLength).toInt(),
                left,
                (bezierControl2.x + diagonal - projectedLength).toInt(),
                right
            )
        } else {
            drawable.setBounds(
                (bezierControl2.x - diagonal).toInt(),
                left,
                bezierControl2.x.toInt(),
                right
            )
        }
        drawable.draw(canvas)
        canvas.restore()
    }

    private fun foldRotationDegrees(): Float = Math.toDegrees(
        atan2(
            (bezierControl1.x - cornerX).toDouble(),
            (bezierControl2.y - cornerY).toDouble()
        )
    ).toFloat()

    private fun pageDiagonal(): Float = hypot(
        readView.width.toDouble(),
        readView.height.toDouble()
    ).toFloat()

    private fun isCommittedFrameFullyOffscreen(): Boolean {
        if (!readView.hasDirectPageRenderer || !settleCompletesPage ||
            !isRunning || isDragging
        ) return false
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0) return false

        viewportRegion.set(0, 0, width, height)
        curledRegion.setEmpty()
        curledRegion.setPath(path0, viewportRegion)
        visibleFrontRegion.set(viewportRegion)
        visibleFrontRegion.op(curledRegion, Region.Op.DIFFERENCE)
        visibleFoldRegion.setEmpty()
        visibleFoldRegion.setPath(path1, viewportRegion)
        visibleFoldRegion.op(curledRegion, Region.Op.INTERSECT)
        return when (direction) {
            Direction.NEXT ->
                isNegligible(visibleFrontRegion) && isNegligible(visibleFoldRegion)
            Direction.PREV ->
                isNegligible(curledRegion) && isNegligible(visibleFoldRegion)
            Direction.NONE -> false
        }
    }

    private fun isNegligible(region: Region): Boolean {
        if (region.isEmpty) return true
        val bounds = region.bounds
        return bounds.width() <= 2 || bounds.height() <= 2
    }

    private fun drawFoldedBack(canvas: Canvas) {
        path1.reset()
        path1.moveTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierEnd1.x, bezierEnd1.y)
        path1.lineTo(renderTouchX, renderTouchY)
        path1.lineTo(bezierEnd2.x, bezierEnd2.y)
        path1.close()

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(path1)
        canvas.drawColor(readView.bgColor)
        drawFoldBackTexture(canvas)
        val background = readView.bgColor
        backTintPaint.color = Color.argb(
            SimulationCurlShadowStyle.BACK_SURFACE_TINT_ALPHA,
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
        drawFoldBackShadow(canvas)
        canvas.restore()
    }

    /** Directional shading across the reflected paper back, matching legado-E. */
    private fun drawFoldBackShadow(canvas: Canvas) {
        val horizontalExtent = abs(
            (bezierStart1.x + bezierControl1.x) * 0.5f - bezierControl1.x
        )
        val verticalExtent = abs(
            (bezierStart2.y + bezierControl2.y) * 0.5f - bezierControl2.y
        )
        val shadowExtent = minOf(horizontalExtent, verticalExtent)
        if (!shadowExtent.isFinite() || shadowExtent <= 0.5f) return

        val upperCorner = cornerY == 0f
        val left: Int
        val right: Int
        val drawable: GradientDrawable
        if (upperCorner) {
            left = (bezierStart1.x - 1f).toInt()
            right = (bezierStart1.x + shadowExtent + 1f).toInt().coerceAtLeast(left + 1)
            drawable = folderShadowDrawableLR
        } else {
            right = (bezierStart1.x + 1f).toInt()
            left = (bezierStart1.x - shadowExtent - 1f).toInt().coerceAtMost(right - 1)
            drawable = folderShadowDrawableRL
        }

        canvas.save()
        canvas.rotate(foldRotationDegrees(), bezierStart1.x, bezierStart1.y)
        drawable.setBounds(
            left,
            bezierStart1.y.toInt(),
            right,
            (bezierStart1.y + pageDiagonal()).toInt()
        )
        drawable.draw(canvas)
        canvas.restore()
    }

    private fun drawFoldBackTexture(canvas: Canvas): Boolean {
        if (!CurlReflectionGeometry.evaluate(
                cornerX = cornerX,
                cornerY = cornerY,
                touchX = renderTouchX,
                touchY = renderTouchY,
                out = reflectionFrame
            )
        ) return false

        // The back belongs to the same physical sheet as the visible front:
        // current page for NEXT, previous page for PREV.
        val bitmap = turningBitmap?.takeUnless { it.isRecycled } ?: return false
        val shader = foldBackShader?.takeIf { foldBackShaderBitmap === bitmap }
            ?: BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                foldBackShader = it
                foldBackShaderBitmap = bitmap
            }
        // CLAMP 采样让镜像纹理铺满整个背面裁剪区（含折痕附近几何近似
        // 未覆盖的条带），不再露出 drawColor 的底色形成浅色白边。
        foldTextureMatrix.reset()
        foldTextureMatrix.setValues(reflectionFrame.matrixValues)
        foldTextureMatrix.postScale(
            readView.width.toFloat() / bitmap.width.coerceAtLeast(1),
            readView.height.toFloat() / bitmap.height.coerceAtLeast(1)
        )
        shader.setLocalMatrix(foldTextureMatrix)
        foldTexturePaint.shader = shader
        canvas.drawRect(
            0f,
            0f,
            readView.width.toFloat(),
            readView.height.toFloat(),
            foldTexturePaint
        )
        return true
    }

    private fun capturePages(dir: Direction): Boolean {
        if (readView.width <= 0 || readView.height <= 0) return false
        if (onCanFlip?.invoke(dir) != true) return false
        releaseBorrowedFrames()

        val turningView: View
        val underView: View
        when (dir) {
            Direction.NEXT -> {
                turningView = readView.curPageView
                underView = readView.nextPageView
            }
            Direction.PREV -> {
                // PREV：上一页从左侧卷入并覆盖留在底层的当前页。
                turningView = readView.prevPageView
                underView = readView.curPageView
            }
            Direction.NONE -> return false
        }

        turningPageView = turningView
        underPageView = underView
        val sourceTurningLease = (turningView as? CurlFrameSource)?.acquireCurlFrame()
        val sourceUnderLease = (underView as? CurlFrameSource)?.acquireCurlFrame()
        if (sourceTurningLease != null && sourceUnderLease != null) {
            turningLease = sourceTurningLease
            underLease = sourceUnderLease
            turningBitmap = sourceTurningLease.resource
            underBitmap = sourceUnderLease.resource
            return true
        }
        sourceTurningLease?.close()
        sourceUnderLease?.close()
        if (readView.hasDirectPageRenderer) {
            ownedTurningBitmap?.takeUnless { it.isRecycled }?.recycle()
            ownedTurningBitmap = null
            // Main page content remains live, but the reflected paper back uses
            // the stable cached texture supplied by the placeholder page.
            turningBitmap = frozenPage(turningView)
            ownedUnderBitmap?.takeUnless { it.isRecycled }?.recycle()
            ownedUnderBitmap = null
            underBitmap = null
            return true
        }

        // EpubSnapshotPageView already owns stable, page-sized bitmaps. Borrow
        // them directly so starting a curl never copies two full screens on the
        // UI thread. Canvas reader pages still use the reusable owned buffers.
        turningBitmap = frozenPage(turningView) ?: snapshot(turningView, ownedTurningBitmap).also {
            ownedTurningBitmap = it
        }
        underBitmap = frozenPage(underView) ?: snapshot(underView, ownedUnderBitmap).also {
            ownedUnderBitmap = it
        }
        return turningBitmap != null && underBitmap != null
    }

    private fun releaseBorrowedFrames() {
        turningLease?.close()
        underLease?.close()
        turningLease = null
        underLease = null
        if (turningBitmap !== ownedTurningBitmap) turningBitmap = ownedTurningBitmap
        if (underBitmap !== ownedUnderBitmap) underBitmap = ownedUnderBitmap
    }

    private fun frozenPage(view: View): Bitmap? =
        (view as? PageBitmapSource)?.pageBitmap?.takeUnless { it.isRecycled }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        val width = readView.width
        val height = readView.height
        if (width <= 0 || height <= 0 || view.width <= 0 || view.height <= 0) return null

        // A prefetched slot is marked loaded immediately after its text is assigned.
        // During very fast consecutive turns Android may not have run the following
        // layout pass yet, so drawing it at once can capture only the page background.
        // Resolve that pending layout synchronously before freezing the curl frames.
        if (view.isLayoutRequested) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, width, height)
        }

        val bitmap = if (
            reusable == null || reusable.isRecycled ||
            reusable.width != width || reusable.height != height
        ) {
            reusable?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            reusable
        }

        bitmap.density = view.resources.displayMetrics.densityDpi
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


    private fun drawPage(canvas: Canvas, bitmap: Bitmap?, pageView: View?) {
        if (!drawPageContent(canvas, bitmap, pageView)) {
            canvas.drawColor(readView.bgColor)
        }
    }

    private fun drawPageContent(canvas: Canvas, bitmap: Bitmap?, pageView: View?): Boolean {
        // 直接渲染的子 View 内容处于物理坐标；仅抵消布局反向镜像。
        val saveCount = if (readView.isPageProgressReversed) {
            canvas.save().also {
                canvas.scale(-1f, 1f, readView.width * 0.5f, 0f)
            }
        } else {
            -1
        }
        try {
            if (pageView != null && readView.drawPageDirectly(canvas, pageView)) return true
            if (bitmap == null || bitmap.isRecycled) return false
            pageSourceRect.set(0, 0, bitmap.width, bitmap.height)
            if (readView.curlBackTextureMode == CurlBackTextureMode.FADED_MIRROR) {
                // CSS snapshots are surface-sized. Equal source/destination pixel
                // bounds guarantee a 1:1 raster copy and bypass density conversion.
                pageDestinationRect.set(0, 0, bitmap.width, bitmap.height)
            } else {
                pageDestinationRect.set(0, 0, readView.width, readView.height)
            }
            canvas.drawBitmap(bitmap, pageSourceRect, pageDestinationRect, pagePaint)
            return true
        } finally {
            if (saveCount >= 0) canvas.restoreToCount(saveCount)
        }
    }

    private fun resetChildViews() {
        if (readView.animatePageViewsDirectly) return
        val width = readView.width.toFloat()
        readView.curPageView.translationX = 0f
        readView.curPageView.translationY = 0f
        readView.curPageView.alpha = 1f
        readView.curPageView.translationZ = 2f

        readView.prevPageView.translationX = idleTranslationX(Direction.PREV, width)
        readView.prevPageView.translationY = 0f
        readView.prevPageView.alpha = 0f
        readView.prevPageView.translationZ = 0f

        readView.nextPageView.translationX = idleTranslationX(Direction.NEXT, width)
        readView.nextPageView.translationY = 0f
        readView.nextPageView.alpha = 0f
        readView.nextPageView.translationZ = 0f
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
