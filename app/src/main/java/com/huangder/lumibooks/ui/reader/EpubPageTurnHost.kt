package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.media.ImageReader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.huangder.lumibooks.ui.reader.engine.CurlFrameSource
import com.huangder.lumibooks.ui.reader.engine.CurlPageAnim
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface
import com.huangder.lumibooks.ui.reader.engine.PageBitmapSource
import com.huangder.lumibooks.ui.reader.engine.RenderResourceLease
import com.huangder.lumibooks.ui.reader.engine.RenderResourcePool
import com.huangder.lumibooks.ui.reader.engine.SlidePageAnim
import com.huangder.lumibooks.ui.reader.engine.isCurlSwipeIntent
import kotlin.math.abs

internal data class EpubPageTarget(
    val chapterIndex: Int,
    val pageIndex: Int
)

internal fun slideLookaheadTarget(
    current: EpubPageTarget,
    currentPageCount: Int,
    direction: Int
): EpubPageTarget? {
    if (direction == 0 || currentPageCount <= 0) return null
    val lookaheadPage = current.pageIndex.toLong() + direction.toLong() * 2L
    return lookaheadPage.takeIf { it in 0 until currentPageCount.toLong() }
        ?.let { EpubPageTarget(current.chapterIndex, it.toInt()) }
}

internal fun resolvedPreloadSlot(
    role: EpubPageTurnHost.WebViewRole?,
    callbackGeneration: Int,
    currentGeneration: Int?
): EpubPageTurnHost.PreloadSlot? {
    if (currentGeneration != callbackGeneration) return null
    return when (role) {
        EpubPageTurnHost.WebViewRole.PREVIOUS -> EpubPageTurnHost.PreloadSlot.PREVIOUS
        EpubPageTurnHost.WebViewRole.NEXT -> EpubPageTurnHost.PreloadSlot.NEXT
        EpubPageTurnHost.WebViewRole.ACTIVE, null -> null
    }
}

internal fun requiresEpubPreloadBitmap(transition: String): Boolean = transition == "curl"

private class EpubSnapshotPageView(
    context: Context,
    private val leases: RenderResourcePool<Bitmap>
) : View(context), PageBitmapSource, CurlFrameSource {
    private val bitmapPaint = Paint(Paint.DITHER_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val bitmapSourceRect = Rect()
    private val bitmapDestinationRect = Rect()
    override var pageBitmap: Bitmap? = null
        private set

    fun setSnapshotBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        invalidate()
    }

    override fun acquireCurlFrame(): RenderResourceLease<Bitmap>? = leases.acquire(pageBitmap)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageBitmap?.takeUnless { it.isRecycled }?.let { bitmap ->
            bitmapSourceRect.set(0, 0, bitmap.width, bitmap.height)
            bitmapDestinationRect.set(0, 0, width, height)
            canvas.drawBitmap(bitmap, bitmapSourceRect, bitmapDestinationRect, bitmapPaint)
        }
    }
}

/**
 * Native page-turn surface for BOOK_LAYOUT EPUBs.
 *
 * The three WebViews remain independent, attached render sources. Adjacent
 * pages finish a real WebView draw behind an opaque mask before they can be
 * turned into view, and the destination remains visible until the active
 * WebView has drawn the same page. No CSS column strip is translated directly.
 */
internal class EpubPageTurnHost(context: Context) : FrameLayout(context) {
    companion object {
        private const val BUSY_TAP_MOVE_LIMIT_PX = 12f
    }

    enum class PreloadSlot { PREVIOUS, NEXT }
    enum class WebViewRole { PREVIOUS, ACTIVE, NEXT }

    private val firstWebView = EpubContentWebView(context)
    private val secondWebView = EpubContentWebView(context)
    private val thirdWebView = EpubContentWebView(context)
    private var previousRoleView = firstWebView
    private var activeRoleView = secondWebView
    private var nextRoleView = thirdWebView

    val previousWebView: EpubContentWebView get() = previousRoleView
    val activeWebView: EpubContentWebView get() = activeRoleView
    val nextWebView: EpubContentWebView get() = nextRoleView

    fun allWebViews(): List<EpubContentWebView> =
        listOf(firstWebView, secondWebView, thirdWebView)

    fun roleOf(view: EpubContentWebView): WebViewRole? = when (view) {
        previousWebView -> WebViewRole.PREVIOUS
        activeWebView -> WebViewRole.ACTIVE
        nextWebView -> WebViewRole.NEXT
        else -> null
    }

    fun preloadSlotOf(view: EpubContentWebView): PreloadSlot? = when (roleOf(view)) {
        WebViewRole.PREVIOUS -> PreloadSlot.PREVIOUS
        WebViewRole.NEXT -> PreloadSlot.NEXT
        WebViewRole.ACTIVE, null -> null
    }
    private val preloadMask = View(context)
    private val bitmapLeases = RenderResourcePool<Bitmap> { bitmap ->
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    private val previousPage = EpubSnapshotPageView(context, bitmapLeases)
    private val currentPage = EpubSnapshotPageView(context, bitmapLeases)
    private val nextPage = EpubSnapshotPageView(context, bitmapLeases)

    private val snapshotSurface = PageAnimationSurface(
        root = this,
        prevPageView = previousPage,
        curPageView = currentPage,
        nextPageView = nextPage,
        backgroundColorProvider = { pageBackgroundColor },
        snapTranslationsToPixels = true,
        animatePageViewsDirectly = false
    )
    private val liveSlideSurface = PageAnimationSurface(
        root = this,
        prevPageView = previousWebView,
        curPageView = activeWebView,
        nextPageView = nextWebView,
        backgroundColorProvider = { pageBackgroundColor },
        snapTranslationsToPixels = true,
        animatePageViewsDirectly = true
    )
    private var controller: PageAnimationController = SlidePageAnim(liveSlideSurface)
    private var transition = "slide"
    private var nativePagingEnabled = true
    private var nativeTouchPagingEnabled = true
    private var reverseAxis = false
    private var pagingGesture = false
    private var suppressTouchStream = false
    private var waitingGestureDirection = PageAnimationController.Direction.NONE
    private var slideTerminalFramePending = false
    private var slideTerminalHandoffPosted = false
    private var pendingSlideTapDirection = PageAnimationController.Direction.NONE
    private var pendingSlideTouchActive = false
    private var capturedSlideTouchStream = false
    private var pendingSlideDownTime = 0L
    private var pendingSlideDownX = 0f
    private var pendingSlideDownY = 0f
    private var pendingSlideEventTime = 0L
    private var pendingSlideEventX = 0f
    private var pendingSlideEventY = 0f
    private var pendingSlideMetaState = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchDownTime = 0L
    private var overlayActive = false
    private var waitingForTarget: EpubPageTarget? = null
    private var waitingForPreparedActivePage = false
    private var busyTouchStream = false
    private var currentTarget = EpubPageTarget(-1, 0)
    private var currentPageCount = 1
    private var pendingSlideVisualDirection = PageAnimationController.Direction.NONE
    private var slideVisualPositionDirty = false
    private var lastSlideVisualDirection = PageAnimationController.Direction.NONE
    private var previousTarget: EpubPageTarget? = null
    private var nextTarget: EpubPageTarget? = null
    private var previousGeneration = 0
    private var nextGeneration = 0
    private var previousReady = false
    private var nextReady = false
    private var frozenPreviousTarget: EpubPageTarget? = null
    private var frozenNextTarget: EpubPageTarget? = null
    private var pageBackgroundColor = Color.WHITE

    private var previousPreparedBitmap: Bitmap? = null
    private var nextPreparedBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null

    var onPageCommit: ((direction: Int, target: EpubPageTarget) -> Unit)? = null
    var onSlideLookaheadRequested: ((PreloadSlot, EpubPageTarget) -> Unit)? = null
    var onSlideVisualPageAdvanced: ((EpubPageTarget, Int) -> Unit)? = null
    var onInvalidatePreloads: (() -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        setWillNotDraw(false)

        addView(previousWebView, matchParentParams())
        addView(nextWebView, matchParentParams())
        addView(preloadMask, matchParentParams())
        addView(activeWebView, matchParentParams())
        addView(previousPage, matchParentParams())
        addView(currentPage, matchParentParams())
        addView(nextPage, matchParentParams())

        previousWebView.alpha = 0f
        previousWebView.visibility = View.VISIBLE
        nextWebView.alpha = 0f
        nextWebView.visibility = View.VISIBLE
        preloadMask.visibility = View.INVISIBLE
        hideAnimationPages()
        bindControllerCallbacks()
    }

    fun setPageBackgroundColor(color: Int) {
        pageBackgroundColor = color
        setBackgroundColor(color)
        preloadMask.setBackgroundColor(color)
        previousWebView.setBackgroundColor(color)
        activeWebView.setBackgroundColor(color)
        nextWebView.setBackgroundColor(color)
    }

    fun setReverseAxis(reverse: Boolean) {
        reverseAxis = reverse
    }

    fun setNativePagingEnabled(enabled: Boolean) {
        nativePagingEnabled = enabled
        if (!enabled) {
            pendingSlideVisualDirection = PageAnimationController.Direction.NONE
            slideVisualPositionDirty = false
            busyTouchStream = false
            clearPendingSlideInput()
        }
        if (!enabled && (overlayActive || pagingGesture)) {
            controller.abortAnim()
            resetAnimationOverlay()
        }
    }

    fun setNativeTouchPagingEnabled(enabled: Boolean) {
        nativeTouchPagingEnabled = enabled
    }

    fun setTransition(mode: String) {
        val normalized = if (mode == "curl") "curl" else "slide"
        if (normalized == transition) return
        val previousTargetBeforeChange = previousTarget
        val nextTargetBeforeChange = nextTarget
        controller.abortAnim()
        (controller as? CurlPageAnim)?.destroy()
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        slideVisualPositionDirty = false
        busyTouchStream = false
        waitingGestureDirection = PageAnimationController.Direction.NONE
        clearPendingSlideInput()
        transition = normalized
        controller = if (normalized == "curl") {
            CurlPageAnim(snapshotSurface, trackCornerTouchDirectly = true)
        } else {
            SlidePageAnim(liveSlideSurface)
        }
        bindControllerCallbacks()
        resetAnimationOverlay()
        recyclePageBitmaps()
        previousReady = false
        nextReady = false
        onInvalidatePreloads?.invoke()
        post {
            previousTargetBeforeChange?.let {
                onSlideLookaheadRequested?.invoke(PreloadSlot.PREVIOUS, it)
            }
            nextTargetBeforeChange?.let {
                onSlideLookaheadRequested?.invoke(PreloadSlot.NEXT, it)
            }
        }
    }

    fun setCurrentPage(
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
        onSettled: (() -> Unit)? = null
    ) {
        val incomingTarget = EpubPageTarget(chapterIndex, pageIndex.coerceAtLeast(0))
        val waiting = waitingForTarget
        if (waiting != null && waiting != incomingTarget) return

        currentTarget = incomingTarget
        currentPageCount = pageCount.coerceAtLeast(1)
        if (waiting == null) {
            if (!overlayActive && !pagingGesture) {
                pendingSlideVisualDirection = PageAnimationController.Direction.NONE
                slideVisualPositionDirty = false
                lastSlideVisualDirection = PageAnimationController.Direction.NONE
            }
            resetLivePageViews()
            onSettled?.invoke()
            return
        }

        activeWebView.animate().cancel()
        activeWebView.translationX = 0f
        activeWebView.translationY = 0f
        activeWebView.translationZ = 5f
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE
        activeWebView.bringToFront()
        waitingForTarget = null
        waitingForPreparedActivePage = false
        resetLivePageViews()
        onSettled?.invoke()
        invalidate()
    }

    fun isAwaitingPage(chapterIndex: Int, pageIndex: Int): Boolean =
        waitingForTarget == EpubPageTarget(chapterIndex, pageIndex.coerceAtLeast(0))

    fun isAwaitingPreparedActivePage(chapterIndex: Int, pageIndex: Int): Boolean =
        waitingForPreparedActivePage && isAwaitingPage(chapterIndex, pageIndex)

    fun hasPendingPageHandoff(): Boolean = waitingForTarget != null

    fun invalidatePreloads() {
        onInvalidatePreloads?.invoke()
    }

    fun keepActiveWebViewCoveredForHandoff() {
        activeWebView.animate().cancel()
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE
    }

    fun preloadTarget(slot: PreloadSlot): EpubPageTarget? = when (slot) {
        PreloadSlot.PREVIOUS -> previousTarget
        PreloadSlot.NEXT -> nextTarget
    }

    fun isPreloadReady(slot: PreloadSlot): Boolean = when (slot) {
        PreloadSlot.PREVIOUS -> previousReady
        PreloadSlot.NEXT -> nextReady
    }

    fun markPreloadLoading(
        slot: PreloadSlot,
        target: EpubPageTarget?,
        generation: Int
    ): Boolean {
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                previousTarget = target
                previousGeneration = generation
                previousReady = false
                bitmapLeases.retire(previousPreparedBitmap)
                previousPreparedBitmap = null
                previousWebView.translationX = 0f
                previousWebView.alpha = 1f
                previousWebView.visibility = View.VISIBLE
            }
            PreloadSlot.NEXT -> {
                nextTarget = target
                nextGeneration = generation
                nextReady = false
                bitmapLeases.retire(nextPreparedBitmap)
                nextPreparedBitmap = null
                nextWebView.translationX = 0f
                nextWebView.alpha = 1f
                nextWebView.visibility = View.VISIBLE
            }
        }
        return false
    }

    fun markPreloadReady(
        slot: PreloadSlot,
        requested: EpubPageTarget,
        generation: Int,
        actualPageIndex: Int,
        sourceView: View
    ) {
        val contentView = sourceView as? EpubContentWebView ?: return
        if (preloadSlotOf(contentView) != slot) return
        val actual = EpubPageTarget(requested.chapterIndex, actualPageIndex.coerceAtLeast(0))
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                if (previousTarget != requested || previousGeneration != generation) return
                if (sourceView.width <= 0 || sourceView.height <= 0) return
                previousTarget = actual
                if (requiresEpubPreloadBitmap(transition)) {
                    previousPreparedBitmap = snapshot(sourceView, previousPreparedBitmap)
                    previousReady = previousPreparedBitmap != null
                } else {
                    bitmapLeases.retire(previousPreparedBitmap)
                    previousPreparedBitmap = null
                    previousReady = true
                }
            }
            PreloadSlot.NEXT -> {
                if (nextTarget != requested || nextGeneration != generation) return
                if (sourceView.width <= 0 || sourceView.height <= 0) return
                nextTarget = actual
                if (requiresEpubPreloadBitmap(transition)) {
                    nextPreparedBitmap = snapshot(sourceView, nextPreparedBitmap)
                    nextReady = nextPreparedBitmap != null
                } else {
                    bitmapLeases.retire(nextPreparedBitmap)
                    nextPreparedBitmap = null
                    nextReady = true
                }
            }
        }
        postInvalidateOnAnimation()
    }

    fun markPreloadFailed(
        slot: PreloadSlot,
        requested: EpubPageTarget,
        generation: Int
    ) {
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                if (previousTarget != requested || previousGeneration != generation) return
                previousReady = false
                bitmapLeases.retire(previousPreparedBitmap)
                previousPreparedBitmap = null
            }
            PreloadSlot.NEXT -> {
                if (nextTarget != requested || nextGeneration != generation) return
                nextReady = false
                bitmapLeases.retire(nextPreparedBitmap)
                nextPreparedBitmap = null
            }
        }
    }

    fun turnFromTap(direction: Int): Boolean {
        if (!nativePagingEnabled || direction == 0 || transition !in setOf("slide", "curl")) return false
        val controllerDirection = if (direction > 0) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
        if (transition == "slide" && slideTerminalFramePending) return true
        if (transition == "slide" && overlayActive) {
            if (holdSlideTerminalFrameForNewInput(controllerDirection, null)) return true
            advancePendingSlideVisualTurn()
            controller.abortAnim()
            resetAnimationOverlay()
            if (startTurnFromTap(controllerDirection)) return true
            post(::commitDirtySlideVisualPosition)
            return true
        }
        if (transition == "slide" && waitingForTarget != null) {
            advanceWaitingSlideTarget(controllerDirection)
            return true
        }
        if (transition == "curl") {
            return when (
                epubCurlTurnDisposition(
                    idle = !overlayActive && waitingForTarget == null &&
                        !pagingGesture && !controller.isRunning && !controller.isDragging,
                    targetExists = hasFlipTarget(controllerDirection),
                    targetReady = canFlip(controllerDirection)
                )
            ) {
                EpubCurlTurnDisposition.ACCEPT -> startTurnFromTap(controllerDirection)
                EpubCurlTurnDisposition.DROP -> true
                EpubCurlTurnDisposition.PASS_BOUNDARY -> false
            }
        }
        if (overlayActive || waitingForTarget != null) return false
        return startTurnFromTap(controllerDirection)
    }

    fun requestTurn(direction: Int): Boolean = turnFromTap(direction)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!nativeTouchPagingEnabled && !overlayActive && waitingForTarget == null &&
            !busyTouchStream
        ) {
            return super.dispatchTouchEvent(event)
        }
        if (transition == "curl" && (busyTouchStream ||
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                (overlayActive || waitingForTarget != null ||
                    controller.isRunning || controller.isDragging))
        ) {
            return handleBusyCurlTouch(event)
        }
        if (transition == "slide" && slideTerminalFramePending) {
            capturePendingSlideTouch(event)
            return true
        }
        if (suppressTouchStream) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                suppressTouchStream = false
            }
            return true
        }
        if (waitingForTarget != null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) suppressTouchStream = true
            return true
        }
        if (!nativePagingEnabled || transition !in setOf("slide", "curl")) {
            return super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && overlayActive) {
            if (transition == "slide" &&
                holdSlideTerminalFrameForNewInput(
                    PageAnimationController.Direction.NONE,
                    event
                )
            ) return true
            if (transition == "slide") advancePendingSlideVisualTurn()
            controller.abortAnim()
            if (waitingForTarget != null) {
                suppressTouchStream = true
                return true
            }
            resetAnimationOverlay()
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchDownTime = event.eventTime
                pagingGesture = false
                waitingGestureDirection = PageAnimationController.Direction.NONE
                capturedSlideTouchStream = false
                return super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pagingGesture) {
                    dispatchMappedEvent(event)
                    return true
                }
                if (waitingGestureDirection != PageAnimationController.Direction.NONE) {
                    if (canFlip(waitingGestureDirection) && prepareAnimationPages()) {
                        beginPagingGesture(event)
                        waitingGestureDirection = PageAnimationController.Direction.NONE
                    }
                    return true
                }
                if (tryBeginSlideGesture(event)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (waitingGestureDirection != PageAnimationController.Direction.NONE) {
                    waitingGestureDirection = PageAnimationController.Direction.NONE
                    capturedSlideTouchStream = false
                    post(::commitDirtySlideVisualPosition)
                    return true
                }
                if (pagingGesture) {
                    pagingGesture = false
                    dispatchMappedEvent(event)
                    if (transition == "slide" && controller.isRunning &&
                        controller.currentDirection != PageAnimationController.Direction.NONE
                    ) {
                        recordSlideTurn(controller.currentDirection)
                    }
                    return true
                }
                if (capturedSlideTouchStream) {
                    capturedSlideTouchStream = false
                    post(::commitDirtySlideVisualPosition)
                    return true
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (transition == "slide" && slideVisualPositionDirty && !overlayActive &&
            (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL)
        ) {
            postDelayed(::commitDirtySlideVisualPosition, 80L)
        }
        return handled
    }

    override fun computeScroll() {
        val needsFrame = controller.computeScroll()
        if (overlayActive && !controller.isRunning && !controller.isDragging &&
            controller.currentDirection == PageAnimationController.Direction.NONE &&
            waitingForTarget == null
        ) {
            resetAnimationOverlay()
        }
        if (needsFrame) postInvalidateOnAnimation()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (overlayActive && controller.drawsDirectlyOnCanvas &&
            (controller.isRunning || controller.isDragging)
        ) {
            controller.onDraw(canvas)
            return
        }
        if (overlayActive) controller.onDraw(canvas)
        super.dispatchDraw(canvas)
        if (overlayActive) {
            when (val current = controller) {
                is SlidePageAnim -> current.drawOverlay(canvas)
                is CurlPageAnim -> current.drawOverlay(canvas)
            }
        }
        if (slideTerminalFramePending && !slideTerminalHandoffPosted) {
            slideTerminalHandoffPosted = true
            postOnAnimation(::finishSlideTerminalHandoff)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (transition != "curl" || oldw <= 0 || oldh <= 0 || (w == oldw && h == oldh)) return
        controller.abortAnim()
        busyTouchStream = false
        resetAnimationOverlay()
        recyclePageBitmaps()
        previousReady = false
        nextReady = false
        onInvalidatePreloads?.invoke()
    }

    override fun onDetachedFromWindow() {
        controller.abortAnim()
        (controller as? CurlPageAnim)?.destroy()
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        slideVisualPositionDirty = false
        clearPendingSlideInput()
        recyclePageBitmaps()
        bitmapLeases.destroy()
        super.onDetachedFromWindow()
    }

    private fun bindControllerCallbacks() {
        controller.onCanFlip = ::canFlip
        controller.onAnimationComplete = complete@{
            val direction = controller.currentDirection
            val target = when (direction) {
                PageAnimationController.Direction.NEXT -> frozenNextTarget
                PageAnimationController.Direction.PREV -> frozenPreviousTarget
                PageAnimationController.Direction.NONE -> null
            }
            pendingSlideVisualDirection = PageAnimationController.Direction.NONE
            if (target == null) {
                resetAnimationOverlay()
                return@complete
            }

            val promotedSlideTarget = transition == "slide" &&
                advanceSlideRoles(direction, target)

            waitingForTarget = target
            waitingForPreparedActivePage = promotedSlideTarget
            overlayActive = false
            hideAnimationPages()

            val targetView = if (promotedSlideTarget) {
                activeWebView
            } else when (direction) {
                PageAnimationController.Direction.NEXT -> nextWebView
                PageAnimationController.Direction.PREV -> previousWebView
                PageAnimationController.Direction.NONE -> null
            }
            if (targetView == null) {
                resetAnimationOverlay()
                return@complete
            }

            activeWebView.translationX = 0f
            activeWebView.translationY = 0f
            activeWebView.translationZ = 2f
            activeWebView.alpha = 1f
            activeWebView.visibility = View.VISIBLE

            targetView.animate().cancel()
            targetView.translationX = 0f
            targetView.translationY = 0f
            targetView.translationZ = 4f
            targetView.alpha = 1f
            targetView.visibility = View.VISIBLE
            targetView.bringToFront()

            onPageCommit?.invoke(
                if (direction == PageAnimationController.Direction.NEXT) 1 else -1,
                target
            )
            slideVisualPositionDirty = false
            lastSlideVisualDirection = PageAnimationController.Direction.NONE
            invalidate()
        }
    }

    private fun canFlip(direction: PageAnimationController.Direction): Boolean = when (direction) {
        PageAnimationController.Direction.NEXT -> nextReady && nextTarget != null
        PageAnimationController.Direction.PREV -> previousReady && previousTarget != null
        PageAnimationController.Direction.NONE -> false
    }

    private fun hasFlipTarget(direction: PageAnimationController.Direction): Boolean = when (direction) {
        PageAnimationController.Direction.NEXT -> nextTarget != null
        PageAnimationController.Direction.PREV -> previousTarget != null
        PageAnimationController.Direction.NONE -> false
    }

    private fun tryBeginSlideGesture(event: MotionEvent): Boolean {
        val dx = abs(event.x - touchStartX)
        val dy = abs(event.y - touchStartY)
        val elapsed = event.eventTime - touchDownTime
        // 4px 阈值会把轻点链接时的正常手指抖动误判为翻页手势并取消 WebView 点击。
        // Keep link-tap jitter below 12px. Curl additionally requires horizontal dominance.
        val horizontalPageIntent = if (transition == "curl") {
            isCurlSwipeIntent(event.x - touchStartX, event.y - touchStartY)
        } else {
            dx > dy * 0.3f
        }
        if (elapsed >= 500L || dx <= BUSY_TAP_MOVE_LIMIT_PX || !horizontalPageIntent) {
            return capturedSlideTouchStream
        }
        val physicalNext = event.x < touchStartX
        val logicalNext = if (reverseAxis) !physicalNext else physicalNext
        val direction = if (logicalNext) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
        if (!canFlip(direction)) {
            if (transition == "curl" && hasFlipTarget(direction)) {
                busyTouchStream = true
                cancelChildTouch(event)
                return true
            }
            if (hasFlipTarget(direction)) {
                waitingGestureDirection = direction
                capturedSlideTouchStream = false
                cancelChildTouch(event)
                return true
            }
            return capturedSlideTouchStream
        }
        if (!prepareAnimationPages()) return true
        capturedSlideTouchStream = false
        beginPagingGesture(event)
        return true
    }

    private fun holdSlideTerminalFrameForNewInput(
        tapDirection: PageAnimationController.Direction,
        touchDown: MotionEvent?
    ): Boolean {
        val pendingTarget = when (pendingSlideVisualDirection) {
            PageAnimationController.Direction.NEXT -> frozenNextTarget
            PageAnimationController.Direction.PREV -> frozenPreviousTarget
            PageAnimationController.Direction.NONE -> null
        } ?: return false
        if (pendingTarget.chapterIndex != currentTarget.chapterIndex) return false
        if (!controller.holdRunningFlipAtEnd()) return false

        slideTerminalFramePending = true
        slideTerminalHandoffPosted = false
        pendingSlideTapDirection = tapDirection
        pendingSlideTouchActive = touchDown != null
        touchDown?.let { event ->
            pendingSlideDownTime = event.downTime
            pendingSlideDownX = event.x
            pendingSlideDownY = event.y
            pendingSlideEventTime = event.eventTime
            pendingSlideEventX = event.x
            pendingSlideEventY = event.y
            pendingSlideMetaState = event.metaState
        }
        postInvalidateOnAnimation()
        return true
    }

    private fun capturePendingSlideTouch(event: MotionEvent) {
        pendingSlideEventTime = event.eventTime
        pendingSlideEventX = event.x
        pendingSlideEventY = event.y
        pendingSlideMetaState = event.metaState
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            pendingSlideTouchActive = false
        }
    }

    private fun finishSlideTerminalHandoff() {
        if (!slideTerminalFramePending) return
        val continueTouch = pendingSlideTouchActive
        val tapDirection = pendingSlideTapDirection
        val downTime = pendingSlideDownTime
        val downX = pendingSlideDownX
        val downY = pendingSlideDownY
        val eventTime = pendingSlideEventTime
        val eventX = pendingSlideEventX
        val eventY = pendingSlideEventY
        val metaState = pendingSlideMetaState

        val promoted = advancePendingSlideVisualTurn()
        controller.abortAnim()
        clearPendingSlideInput()
        resetAnimationOverlay()
        if (!promoted) return

        if (tapDirection != PageAnimationController.Direction.NONE) {
            if (!startTurnFromTap(tapDirection)) post(::commitDirtySlideVisualPosition)
            return
        }
        if (!continueTouch) {
            post(::commitDirtySlideVisualPosition)
            return
        }

        touchStartX = downX
        touchStartY = downY
        touchDownTime = downTime
        pagingGesture = false
        waitingGestureDirection = PageAnimationController.Direction.NONE
        capturedSlideTouchStream = true

        if (eventTime > downTime) {
            val move = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_MOVE,
                eventX,
                eventY,
                metaState
            )
            try {
                tryBeginSlideGesture(move)
            } finally {
                move.recycle()
            }
        }
    }

    private fun clearPendingSlideInput() {
        slideTerminalFramePending = false
        slideTerminalHandoffPosted = false
        pendingSlideTapDirection = PageAnimationController.Direction.NONE
        pendingSlideTouchActive = false
        capturedSlideTouchStream = false
    }

    private fun cancelChildTouch(source: MotionEvent) {
        val cancel = MotionEvent.obtain(source).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun beginPagingGesture(event: MotionEvent) {
        pagingGesture = true
        overlayActive = true
        cancelChildTouch(event)
        val mappedStartX = if (reverseAxis) width - touchStartX else touchStartX
        val down = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            MotionEvent.ACTION_DOWN,
            mappedStartX,
            touchStartY,
            event.metaState
        )
        controller.onTouchEvent(down)
        down.recycle()
        dispatchMappedEvent(event)
    }

    private fun startTurnFromTap(direction: PageAnimationController.Direction): Boolean {
        if (!canFlip(direction) || !prepareAnimationPages()) return false
        if (transition == "slide") recordSlideTurn(direction)
        overlayActive = true
        when (val current = controller) {
            is CurlPageAnim -> current.startFromTap(direction)
            is SlidePageAnim -> current.startFromTap(direction)
        }
        invalidate()
        return true
    }

    private fun recordSlideTurn(direction: PageAnimationController.Direction) {
        pendingSlideVisualDirection = direction
        val lookahead = slideLookaheadTarget(
            current = currentTarget,
            currentPageCount = currentPageCount,
            direction = direction.toTurnDelta()
        ) ?: return
        val stagingSlot = if (direction == PageAnimationController.Direction.NEXT) {
            PreloadSlot.PREVIOUS
        } else {
            PreloadSlot.NEXT
        }
        onSlideLookaheadRequested?.invoke(stagingSlot, lookahead)
    }

    private fun advanceWaitingSlideTarget(direction: PageAnimationController.Direction) {
        val waiting = waitingForTarget ?: return
        if (waiting.chapterIndex != currentTarget.chapterIndex) return
        val targetPage = waiting.pageIndex + direction.toTurnDelta()
        if (targetPage !in 0 until currentPageCount) return
        val target = EpubPageTarget(waiting.chapterIndex, targetPage)
        if (target == waiting) return
        waitingForTarget = target
        onPageCommit?.invoke(direction.toTurnDelta(), target)
    }

    private fun advancePendingSlideVisualTurn(): Boolean {
        val direction = pendingSlideVisualDirection
        val target = when (direction) {
            PageAnimationController.Direction.NEXT -> frozenNextTarget
            PageAnimationController.Direction.PREV -> frozenPreviousTarget
            PageAnimationController.Direction.NONE -> null
        } ?: return false
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        return advanceSlideRoles(direction, target)
    }

    private fun advanceSlideRoles(
        direction: PageAnimationController.Direction,
        target: EpubPageTarget
    ): Boolean {
        if (target.chapterIndex != currentTarget.chapterIndex) return false
        val oldPreviousView = previousRoleView
        val oldActiveView = activeRoleView
        val oldNextView = nextRoleView
        val oldCurrentTarget = currentTarget

        when (direction) {
            PageAnimationController.Direction.NEXT -> {
                val stagedTarget = previousTarget
                val stagedReady = previousReady
                val stagedGeneration = previousGeneration
                val stagedBitmap = previousPreparedBitmap
                val promotedBitmap = nextPreparedBitmap

                previousRoleView = oldActiveView
                activeRoleView = oldNextView
                nextRoleView = oldPreviousView
                previousTarget = oldCurrentTarget
                previousReady = true
                previousGeneration = nextGeneration
                previousPreparedBitmap = null

                val expectedNext = target.pageIndex + 1
                val stagingMatches = expectedNext < currentPageCount &&
                    stagedTarget == EpubPageTarget(target.chapterIndex, expectedNext)
                nextTarget = stagedTarget.takeIf { stagingMatches }
                nextReady = stagedReady && stagingMatches
                nextGeneration = stagedGeneration
                nextPreparedBitmap = stagedBitmap.takeIf { stagingMatches }
                if (!stagingMatches) bitmapLeases.retire(stagedBitmap)
                bitmapLeases.retire(promotedBitmap)
            }
            PageAnimationController.Direction.PREV -> {
                val stagedTarget = nextTarget
                val stagedReady = nextReady
                val stagedGeneration = nextGeneration
                val stagedBitmap = nextPreparedBitmap
                val promotedBitmap = previousPreparedBitmap

                previousRoleView = oldNextView
                activeRoleView = oldPreviousView
                nextRoleView = oldActiveView
                nextTarget = oldCurrentTarget
                nextReady = true
                nextGeneration = previousGeneration
                nextPreparedBitmap = null

                val expectedPrevious = target.pageIndex - 1
                val stagingMatches = expectedPrevious >= 0 &&
                    stagedTarget == EpubPageTarget(target.chapterIndex, expectedPrevious)
                previousTarget = stagedTarget.takeIf { stagingMatches }
                previousReady = stagedReady && stagingMatches
                previousGeneration = stagedGeneration
                previousPreparedBitmap = stagedBitmap.takeIf { stagingMatches }
                if (!stagingMatches) bitmapLeases.retire(stagedBitmap)
                bitmapLeases.retire(promotedBitmap)
            }
            PageAnimationController.Direction.NONE -> return false
        }

        liveSlideSurface.prevPageView = previousWebView
        liveSlideSurface.curPageView = activeWebView
        liveSlideSurface.nextPageView = nextWebView
        currentTarget = target
        slideVisualPositionDirty = true
        lastSlideVisualDirection = direction
        onSlideVisualPageAdvanced?.invoke(currentTarget, currentPageCount)
        return true
    }

    private fun commitDirtySlideVisualPosition() {
        if (!slideVisualPositionDirty || overlayActive || waitingForTarget != null) return
        val direction = lastSlideVisualDirection
        if (direction == PageAnimationController.Direction.NONE) return
        waitingForTarget = currentTarget
        waitingForPreparedActivePage = true
        onPageCommit?.invoke(direction.toTurnDelta(), currentTarget)
        slideVisualPositionDirty = false
        lastSlideVisualDirection = PageAnimationController.Direction.NONE
    }

    private fun PageAnimationController.Direction.toTurnDelta(): Int = when (this) {
        PageAnimationController.Direction.NEXT -> 1
        PageAnimationController.Direction.PREV -> -1
        PageAnimationController.Direction.NONE -> 0
    }

    private fun handleBusyCurlTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchDownTime = event.eventTime
                busyTouchStream = true
            }
            MotionEvent.ACTION_UP -> {
                busyTouchStream = false
            }
            MotionEvent.ACTION_CANCEL -> {
                busyTouchStream = false
            }
        }
        return true
    }

    private fun prepareAnimationPages(): Boolean {
        if (width <= 0 || height <= 0 || activeWebView.width <= 0 || activeWebView.height <= 0) return false
        frozenPreviousTarget = previousTarget.takeIf { previousReady }
        frozenNextTarget = nextTarget.takeIf { nextReady }

        if (transition == "slide") {
            activeWebView.animate().cancel()
            previousWebView.animate().cancel()
            nextWebView.animate().cancel()
            activeWebView.translationX = 0f
            activeWebView.translationY = 0f
            activeWebView.translationZ = 2f
            activeWebView.alpha = 1f
            activeWebView.visibility = View.VISIBLE
            previousWebView.translationX = -width.toFloat()
            previousWebView.translationY = 0f
            previousWebView.translationZ = 0f
            previousWebView.alpha = 0f
            previousWebView.visibility = View.VISIBLE
            nextWebView.translationX = width.toFloat()
            nextWebView.translationY = 0f
            nextWebView.translationZ = 0f
            nextWebView.alpha = 0f
            nextWebView.visibility = View.VISIBLE
            hideAnimationPages()
            return true
        }

        currentBitmap = snapshot(activeWebView, currentBitmap)
        if (currentBitmap == null) return false
        if (frozenPreviousTarget != null && previousPreparedBitmap == null) return false
        if (frozenNextTarget != null && nextPreparedBitmap == null) return false

        previousPage.setSnapshotBitmap(previousPreparedBitmap)
        currentPage.setSnapshotBitmap(currentBitmap)
        nextPage.setSnapshotBitmap(nextPreparedBitmap)
        previousPage.alpha = 1f
        currentPage.alpha = 1f
        nextPage.alpha = 1f
        return true
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || width <= 0 || height <= 0) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hardwareSnapshot(view)?.let { hardwareSnapshot ->
                bitmapLeases.retire(reusable?.takeUnless { it === hardwareSnapshot })
                return bitmapLeases.track(hardwareSnapshot)
            }
        }

        val bitmap = if (reusable == null || reusable.isRecycled ||
            !bitmapLeases.canReuse(reusable) ||
            reusable.width != width || reusable.height != height
        ) {
            bitmapLeases.retire(reusable)
            bitmapLeases.track(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        } else reusable
        bitmap.density = resources.displayMetrics.densityDpi
        bitmap.eraseColor(pageBackgroundColor)
        val bitmapCanvas = Canvas(bitmap)
        val savedAlpha = view.alpha
        val savedTranslationX = view.translationX
        val savedTranslationY = view.translationY
        try {
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.draw(bitmapCanvas)
        } finally {
            view.alpha = savedAlpha
            view.translationX = savedTranslationX
            view.translationY = savedTranslationY
        }
        return bitmap
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun hardwareSnapshot(view: View): Bitmap? = runCatching {
        val node = RenderNode("epub-page-snapshot")
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val renderer = HardwareRenderer()
        try {
            node.setPosition(0, 0, width, height)
            val recordingCanvas = node.beginRecording(width, height)
            val savedAlpha = view.alpha
            val savedTranslationX = view.translationX
            val savedTranslationY = view.translationY
            try {
                recordingCanvas.drawColor(pageBackgroundColor)
                view.alpha = 1f
                view.translationX = 0f
                view.translationY = 0f
                view.draw(recordingCanvas)
            } finally {
                view.alpha = savedAlpha
                view.translationX = savedTranslationX
                view.translationY = savedTranslationY
                node.endRecording()
            }

            renderer.setSurface(imageReader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = imageReader.acquireLatestImage() ?: return@runCatching null
            image.use {
                val plane = it.planes.firstOrNull() ?: return@use null
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                if (pixelStride <= 0 || rowStride <= 0) return@use null
                val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
                val paddedBitmap = Bitmap.createBitmap(
                    paddedWidth.coerceAtLeast(width),
                    height,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(plane.buffer)
                val result = if (paddedBitmap.width == width) {
                    paddedBitmap
                } else {
                    Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                        paddedBitmap.recycle()
                    }
                }
                result.density = resources.displayMetrics.densityDpi
                result
            }
        } finally {
            renderer.setSurface(null)
            renderer.destroy()
            imageReader.close()
            node.discardDisplayList()
        }
    }.getOrNull()

    private fun dispatchMappedEvent(source: MotionEvent) {
        if (!reverseAxis) {
            controller.onTouchEvent(source)
            return
        }
        val mapped = MotionEvent.obtain(source)
        try {
            mapped.setLocation(width - source.x, source.y)
            controller.onTouchEvent(mapped)
        } finally {
            mapped.recycle()
        }
    }

    private fun resetLivePageViews() {
        activeWebView.translationX = 0f
        activeWebView.translationY = 0f
        activeWebView.translationZ = 2f
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE

        previousWebView.translationX = 0f
        previousWebView.translationY = 0f
        previousWebView.translationZ = 0f
        previousWebView.alpha = 1f
        previousWebView.visibility = View.VISIBLE

        nextWebView.translationX = 0f
        nextWebView.translationY = 0f
        nextWebView.translationZ = 0f
        nextWebView.alpha = 1f
        nextWebView.visibility = View.VISIBLE

        preloadMask.visibility = View.VISIBLE
        preloadMask.bringToFront()
        activeWebView.bringToFront()
    }

    private fun resetAnimationOverlay() {
        overlayActive = false
        pagingGesture = false
        waitingGestureDirection = PageAnimationController.Direction.NONE
        waitingForTarget = null
        waitingForPreparedActivePage = false
        frozenPreviousTarget = null
        frozenNextTarget = null
        resetLivePageViews()
        hideAnimationPages()
        invalidate()
    }

    private fun hideAnimationPages() {
        previousPage.alpha = 0f
        currentPage.alpha = 0f
        nextPage.alpha = 0f
        previousPage.translationX = -width.toFloat()
        currentPage.translationX = 0f
        nextPage.translationX = width.toFloat()
        previousPage.translationY = 0f
        currentPage.translationY = 0f
        nextPage.translationY = 0f
    }

    private fun recyclePageBitmaps() {
        previousPage.setSnapshotBitmap(null)
        currentPage.setSnapshotBitmap(null)
        nextPage.setSnapshotBitmap(null)
        bitmapLeases.retire(previousPreparedBitmap)
        bitmapLeases.retire(nextPreparedBitmap)
        bitmapLeases.retire(currentBitmap)
        previousPreparedBitmap = null
        nextPreparedBitmap = null
        currentBitmap = null
    }

    private fun matchParentParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
