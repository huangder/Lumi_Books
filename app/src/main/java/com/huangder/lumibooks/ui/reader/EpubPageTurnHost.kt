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
import com.huangder.lumibooks.ui.reader.engine.EpubLegacyCurlPageAnim
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface
import com.huangder.lumibooks.ui.reader.engine.PageBitmapSource
import com.huangder.lumibooks.ui.reader.engine.SlidePageAnim
import java.util.ArrayDeque
import kotlin.math.abs

internal data class EpubPageTarget(
    val chapterIndex: Int,
    val pageIndex: Int
)

private class EpubSnapshotPageView(context: Context) : View(context), PageBitmapSource {
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

    val previousWebView = EpubContentWebView(context)
    val activeWebView = EpubContentWebView(context)
    val nextWebView = EpubContentWebView(context)

    fun allWebViews(): List<EpubContentWebView> =
        listOf(previousWebView, activeWebView, nextWebView)

    fun roleOf(view: EpubContentWebView): WebViewRole? = when (view) {
        previousWebView -> WebViewRole.PREVIOUS
        activeWebView -> WebViewRole.ACTIVE
        nextWebView -> WebViewRole.NEXT
        else -> null
    }
    private val preloadMask = View(context)

    private val previousPage = EpubSnapshotPageView(context)
    private val currentPage = EpubSnapshotPageView(context)
    private val nextPage = EpubSnapshotPageView(context)

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
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchDownTime = 0L
    private var overlayActive = false
    private var waitingForTarget: EpubPageTarget? = null
    private val queuedTurns = ArrayDeque<PageAnimationController.Direction>()
    private var busyTouchStream = false
    private var busyTurnQueuedForGesture = false
    private var currentTarget = EpubPageTarget(-1, 0)
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
    private var previousBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null

    var onPageCommit: ((direction: Int, target: EpubPageTarget) -> Unit)? = null
    var onBusyEdgeTapDirection: ((isLeftEdge: Boolean) -> Int)? = null

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
        if (!enabled) queuedTurns.clear()
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
        controller.abortAnim()
        (controller as? EpubLegacyCurlPageAnim)?.destroy()
        queuedTurns.clear()
        busyTouchStream = false
        transition = normalized
        controller = if (normalized == "curl") {
            EpubLegacyCurlPageAnim(snapshotSurface)
        } else {
            SlidePageAnim(liveSlideSurface)
        }
        bindControllerCallbacks()
        resetAnimationOverlay()
    }

    fun setCurrentPage(
        chapterIndex: Int,
        pageIndex: Int,
        onSettled: (() -> Unit)? = null
    ) {
        val incomingTarget = EpubPageTarget(chapterIndex, pageIndex.coerceAtLeast(0))
        val waiting = waitingForTarget
        if (waiting != null && waiting != incomingTarget) return

        currentTarget = incomingTarget
        if (waiting == null) {
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
        resetLivePageViews()
        onSettled?.invoke()
        post(::drainQueuedTurns)
        invalidate()
    }

    fun isAwaitingPage(chapterIndex: Int, pageIndex: Int): Boolean =
        waitingForTarget == EpubPageTarget(chapterIndex, pageIndex.coerceAtLeast(0))

    fun hasPendingPageHandoff(): Boolean = waitingForTarget != null

    fun keepActiveWebViewCoveredForHandoff() {
        activeWebView.animate().cancel()
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE
    }

    fun preloadTarget(slot: PreloadSlot): EpubPageTarget? = when (slot) {
        PreloadSlot.PREVIOUS -> previousTarget
        PreloadSlot.NEXT -> nextTarget
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
                previousPreparedBitmap?.recycle()
                previousPreparedBitmap = null
                previousWebView.translationX = 0f
                previousWebView.alpha = 1f
                previousWebView.visibility = View.VISIBLE
            }
            PreloadSlot.NEXT -> {
                nextTarget = target
                nextGeneration = generation
                nextReady = false
                nextPreparedBitmap?.recycle()
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
        val actual = EpubPageTarget(requested.chapterIndex, actualPageIndex.coerceAtLeast(0))
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                if (previousTarget != requested || previousGeneration != generation) return
                if (sourceView.width <= 0 || sourceView.height <= 0) return
                previousPreparedBitmap = snapshot(sourceView, previousPreparedBitmap)
                previousTarget = actual
                previousReady = previousPreparedBitmap != null
            }
            PreloadSlot.NEXT -> {
                if (nextTarget != requested || nextGeneration != generation) return
                if (sourceView.width <= 0 || sourceView.height <= 0) return
                nextPreparedBitmap = snapshot(sourceView, nextPreparedBitmap)
                nextTarget = actual
                nextReady = nextPreparedBitmap != null
            }
        }
        post(::drainQueuedTurns)
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
                previousPreparedBitmap?.recycle()
                previousPreparedBitmap = null
            }
            PreloadSlot.NEXT -> {
                if (nextTarget != requested || nextGeneration != generation) return
                nextReady = false
                nextPreparedBitmap?.recycle()
                nextPreparedBitmap = null
            }
        }
        post(::drainQueuedTurns)
    }

    fun turnFromTap(direction: Int): Boolean {
        if (!nativePagingEnabled || transition !in setOf("slide", "curl")) return false
        val controllerDirection = if (direction > 0) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
        if (transition == "curl" && (overlayActive || waitingForTarget != null)) {
            enqueueTurn(controllerDirection)
            return true
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
                (overlayActive || waitingForTarget != null))
        ) {
            return handleBusyCurlTouch(event)
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
                return super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pagingGesture) {
                    dispatchMappedEvent(event)
                    return true
                }
                val dx = abs(event.x - touchStartX)
                val dy = abs(event.y - touchStartY)
                val elapsed = event.eventTime - touchDownTime
                if (elapsed < 500L && dx > 4f && dx > dy * 0.3f) {
                    val physicalNext = event.x < touchStartX
                    val logicalNext = if (reverseAxis) !physicalNext else physicalNext
                    val direction = if (logicalNext) {
                        PageAnimationController.Direction.NEXT
                    } else {
                        PageAnimationController.Direction.PREV
                    }
                    if (!canFlip(direction) || !prepareAnimationPages()) {
                        return super.dispatchTouchEvent(event)
                    }
                    pagingGesture = true
                    overlayActive = true

                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()

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
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pagingGesture) {
                    pagingGesture = false
                    dispatchMappedEvent(event)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
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
                is EpubLegacyCurlPageAnim -> current.drawOverlay(canvas)
            }
        }
    }

    override fun onDetachedFromWindow() {
        controller.abortAnim()
        (controller as? EpubLegacyCurlPageAnim)?.destroy()
        queuedTurns.clear()
        recyclePageBitmaps()
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
            if (target == null) {
                resetAnimationOverlay()
                return@complete
            }

            waitingForTarget = target
            overlayActive = false
            hideAnimationPages()

            val targetView = when (direction) {
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
            invalidate()
        }
    }

    private fun canFlip(direction: PageAnimationController.Direction): Boolean = when (direction) {
        PageAnimationController.Direction.NEXT -> nextReady && nextTarget != null
        PageAnimationController.Direction.PREV -> previousReady && previousTarget != null
        PageAnimationController.Direction.NONE -> false
    }

    private fun startTurnFromTap(direction: PageAnimationController.Direction): Boolean {
        if (!canFlip(direction) || !prepareAnimationPages()) return false
        overlayActive = true
        when (val current = controller) {
            is EpubLegacyCurlPageAnim -> current.startFromTap(direction)
            is SlidePageAnim -> current.startFromTap(direction)
        }
        invalidate()
        return true
    }

    private fun enqueueTurn(direction: PageAnimationController.Direction) {
        if (direction == PageAnimationController.Direction.NONE) return
        // Busy input represents the user's latest intent, not a playback list.
        // Keeping a backlog makes pages continue turning after the finger lifts.
        queuedTurns.clear()
        queuedTurns.addLast(direction)
        post(::drainQueuedTurns)
    }

    private fun drainQueuedTurns() {
        if (!nativePagingEnabled || transition != "curl" || overlayActive ||
            waitingForTarget != null || pagingGesture || busyTouchStream
        ) return

        while (queuedTurns.isNotEmpty()) {
            val direction = queuedTurns.peekFirst()
            val targetExists = when (direction) {
                PageAnimationController.Direction.NEXT -> nextTarget != null
                PageAnimationController.Direction.PREV -> previousTarget != null
                PageAnimationController.Direction.NONE -> false
            }
            if (!targetExists) {
                queuedTurns.removeFirst()
                continue
            }
            if (!canFlip(direction)) return
            if (startTurnFromTap(direction)) queuedTurns.removeFirst()
            return
        }
    }

    private fun handleBusyCurlTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchDownTime = event.eventTime
                busyTouchStream = true
                busyTurnQueuedForGesture = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!busyTurnQueuedForGesture) {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)
                    if (dx > 4f && dx > dy * 0.3f) {
                        enqueueTurn(directionForHorizontalMove(event.x))
                        busyTurnQueuedForGesture = true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!busyTurnQueuedForGesture) {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)
                    val elapsed = event.eventTime - touchDownTime
                    if (elapsed < 500L && dx < BUSY_TAP_MOVE_LIMIT_PX && dy < BUSY_TAP_MOVE_LIMIT_PX) {
                        val edgeDirection = when {
                            event.x < width * 0.3f -> onBusyEdgeTapDirection?.invoke(true)
                            event.x > width * 0.7f -> onBusyEdgeTapDirection?.invoke(false)
                            else -> null
                        }
                        edgeDirection?.let {
                            enqueueTurn(
                                if (it > 0) PageAnimationController.Direction.NEXT
                                else PageAnimationController.Direction.PREV
                            )
                        }
                    }
                }
                busyTouchStream = false
                busyTurnQueuedForGesture = false
                post(::drainQueuedTurns)
            }
            MotionEvent.ACTION_CANCEL -> {
                busyTouchStream = false
                busyTurnQueuedForGesture = false
                queuedTurns.clear()
            }
        }
        return true
    }

    private fun directionForHorizontalMove(currentX: Float): PageAnimationController.Direction {
        val physicalNext = currentX < touchStartX
        val logicalNext = if (reverseAxis) !physicalNext else physicalNext
        return if (logicalNext) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
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
        previousBitmap = if (frozenPreviousTarget != null) {
            copyBitmap(previousPreparedBitmap, previousBitmap)
        } else null
        nextBitmap = if (frozenNextTarget != null) {
            copyBitmap(nextPreparedBitmap, nextBitmap)
        } else null
        if (currentBitmap == null) return false
        if (frozenPreviousTarget != null && previousBitmap == null) return false
        if (frozenNextTarget != null && nextBitmap == null) return false

        previousPage.setSnapshotBitmap(previousBitmap)
        currentPage.setSnapshotBitmap(currentBitmap)
        nextPage.setSnapshotBitmap(nextBitmap)
        previousPage.alpha = 1f
        currentPage.alpha = 1f
        nextPage.alpha = 1f
        return true
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || width <= 0 || height <= 0) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hardwareSnapshot(view)?.let { hardwareSnapshot ->
                reusable?.takeUnless { it === hardwareSnapshot || it.isRecycled }?.recycle()
                return hardwareSnapshot
            }
        }

        val bitmap = if (reusable == null || reusable.isRecycled ||
            reusable.width != width || reusable.height != height
        ) {
            reusable?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

    private fun copyBitmap(source: Bitmap?, reusable: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null
        val bitmap = if (reusable == null || reusable.isRecycled ||
            reusable.width != source.width || reusable.height != source.height
        ) {
            reusable?.recycle()
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        } else reusable
        bitmap.density = source.density.takeIf { it != Bitmap.DENSITY_NONE }
            ?: resources.displayMetrics.densityDpi
        bitmap.eraseColor(pageBackgroundColor)
        Canvas(bitmap).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(0, 0, bitmap.width, bitmap.height),
            null
        )
        return bitmap
    }

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
        waitingForTarget = null
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
        previousPreparedBitmap?.recycle()
        nextPreparedBitmap?.recycle()
        previousBitmap?.recycle()
        currentBitmap?.recycle()
        nextBitmap?.recycle()
        previousPreparedBitmap = null
        nextPreparedBitmap = null
        previousBitmap = null
        currentBitmap = null
        nextBitmap = null
    }

    private fun matchParentParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
