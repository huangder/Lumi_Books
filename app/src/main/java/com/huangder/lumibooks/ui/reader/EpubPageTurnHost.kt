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
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.util.ReaderBackgroundBlurTransformation
import com.huangder.lumibooks.ui.reader.engine.CurlFrameSource
import com.huangder.lumibooks.ui.reader.engine.CurlPageAnim
import com.huangder.lumibooks.ui.reader.engine.CurlTurnInput
import com.huangder.lumibooks.ui.reader.engine.BookmarkPullGestureTracker
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface
import com.huangder.lumibooks.ui.reader.engine.PageBitmapSource
import com.huangder.lumibooks.ui.reader.engine.RenderResourceLease
import com.huangder.lumibooks.ui.reader.engine.RenderResourcePool
import com.huangder.lumibooks.ui.reader.engine.ReaderCurlTurnSequencer
import com.huangder.lumibooks.ui.reader.engine.SlidePageAnim
import com.huangder.lumibooks.ui.reader.engine.ScrollPageAnim
import com.huangder.lumibooks.ui.reader.engine.horizontalPageFrame
import com.huangder.lumibooks.ui.reader.engine.verticalPageFrame
import com.huangder.lumibooks.ui.reader.engine.isCurlSwipeIntent
import com.huangder.lumibooks.ui.reader.engine.isSystemBackGestureStart
import com.huangder.lumibooks.ui.reader.engine.isSystemBackGestureSwipe
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.util.performance.ReaderPageTurnPerformance
import coil.load
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BUSY_CURL_TAP_MOVE_LIMIT_PX = 12f

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

internal fun slideLookaheadFromDestination(
    destination: EpubPageTarget,
    destinationPageCount: Int,
    direction: Int
): EpubPageTarget? {
    if (direction == 0 || destinationPageCount <= 0) return null
    val lookaheadPage = destination.pageIndex.toLong() + direction.toLong()
    return lookaheadPage.takeIf { it in 0 until destinationPageCount.toLong() }
        ?.let { EpubPageTarget(destination.chapterIndex, it.toInt()) }
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

internal fun requiresEpubPreloadBitmap(transition: String, backgroundSnapshot: Boolean = false): Boolean =
    transition == "curl" || backgroundSnapshot

internal fun isLiveEpubPageTransition(transition: String): Boolean =
    transition == "slide" || transition == "scroll"

internal fun epubPageDirectionForHorizontalDelta(
    deltaX: Float,
    reverseAxis: Boolean,
    threshold: Float = 0f
): PageAnimationController.Direction {
    val physicalNext = when {
        deltaX < -threshold -> true
        deltaX > threshold -> false
        else -> return PageAnimationController.Direction.NONE
    }
    val logicalNext = if (reverseAxis) !physicalNext else physicalNext
    return if (logicalNext) {
        PageAnimationController.Direction.NEXT
    } else {
        PageAnimationController.Direction.PREV
    }
}

internal fun deferEpubPageNotification(animationActive: Boolean, waitingForTarget: Boolean): Boolean =
    animationActive && !waitingForTarget

internal fun supportsEpubPageRolePromotion(transition: String): Boolean =
    isLiveEpubPageTransition(transition) || transition == "curl"

internal fun isCrossChapterPageTurn(
    current: EpubPageTarget,
    target: EpubPageTarget
): Boolean = current.chapterIndex != target.chapterIndex

internal fun hasPotentialEpubPageTurn(
    current: EpubPageTarget,
    currentPageCount: Int,
    chapterCount: Int,
    direction: Int
): Boolean = when {
    current.chapterIndex !in 0 until chapterCount -> false
    direction > 0 -> current.pageIndex + 1 < currentPageCount ||
        current.chapterIndex + 1 < chapterCount
    direction < 0 -> current.pageIndex > 0 || current.chapterIndex > 0
    else -> false
}

internal fun epubPageNotificationMatchesTarget(
    target: EpubPageTarget,
    chapterIndex: Int,
    pageIndex: Int
): Boolean = target == EpubPageTarget(chapterIndex, pageIndex.coerceAtLeast(0))

internal fun curlTurnDirectionIsCompatible(
    inFlight: PageAnimationController.Direction,
    requested: PageAnimationController.Direction
): Boolean = inFlight == PageAnimationController.Direction.NONE ||
    requested == PageAnimationController.Direction.NONE ||
    inFlight == requested

internal fun isCapturedBusyCurlCenterTap(
    gestureClaimed: Boolean,
    direction: PageAnimationController.Direction,
    elapsedMs: Long,
    deltaX: Float,
    deltaY: Float
): Boolean = !gestureClaimed &&
    direction == PageAnimationController.Direction.NONE &&
    elapsedMs < 300L &&
    abs(deltaX) <= BUSY_CURL_TAP_MOVE_LIMIT_PX &&
    abs(deltaY) < 50f

internal fun shouldCaptureNativeMediaCenterTap(
    mediaOnlyNativePaging: Boolean,
    direction: PageAnimationController.Direction,
    elapsedMs: Long,
    deltaX: Float,
    deltaY: Float
): Boolean = mediaOnlyNativePaging && isCapturedBusyCurlCenterTap(
    gestureClaimed = false,
    direction = direction,
    elapsedMs = elapsedMs,
    deltaX = deltaX,
    deltaY = deltaY
)

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
    private var bitmapLease: RenderResourceLease<Bitmap>? = null
    override val pageBitmap: Bitmap?
        get() = bitmapLease?.resource

    fun setSnapshotBitmap(bitmap: Bitmap?) {
        if (pageBitmap === bitmap) return
        bitmapLease?.close()
        bitmapLease = leases.acquire(bitmap)
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
 * The three WebViews remain independent layout sources. Slide turns move the
 * prepared WebViews directly so publisher CSS and text rendering stay exact;
 * curl turns consume leased snapshots. Adjacent WebViews finish a visual-state
 * callback and a real draw behind an opaque mask before becoming turn targets.
 */
internal class EpubPageTurnHost(context: Context) : FrameLayout(context) {
    companion object {
        private const val BUSY_TAP_MOVE_LIMIT_PX = 12f
        private const val BUSY_CURL_COMMIT_FRACTION = 0.14f
        private const val BUSY_CURL_FLING_DP_PER_SECOND = 450f
        private const val PERFORMANCE_LOG_TAG = "EpubPageTurnHost"
        /** 卷曲目标页迟迟没准备好时的兜底时长：超过就退化成直接翻页。 */
        private const val CURL_TURN_FALLBACK_DELAY_MS = 260L
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
    /**
     * 预加载遮罩：翻页时临时盖住还没准备好的页面。
     * 它停留在 active WebView 下方且翻页后仍保持可见，所以必须画出与页面相同的底色与背景图，
     * 否则会把下方的阅读背景整块盖住（表现为"翻完页背景只剩纯色"）。
     */
    private val preloadMask = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
    }
    private val backgroundImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }
    private val bitmapLeases = RenderResourcePool<Bitmap> { bitmap ->
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    private val previousPage = EpubSnapshotPageView(context, bitmapLeases)
    private val currentPage = EpubSnapshotPageView(context, bitmapLeases)
    private val nextPage = EpubSnapshotPageView(context, bitmapLeases)
    private var mediaOnlyNativePaging = false

    private val snapshotSurface = PageAnimationSurface(
        root = this,
        prevPageView = previousPage,
        curPageView = currentPage,
        nextPageView = nextPage,
        backgroundColorProvider = { pageBackgroundColor },
        reversePageProgressProvider = { frozenReverseAxis ?: reverseAxis },
        snapTranslationsToPixels = true,
        animatePageViewsDirectly = false
    )
    private val liveSlideSurface = PageAnimationSurface(
        root = this,
        prevPageView = previousWebView,
        curPageView = activeWebView,
        nextPageView = nextWebView,
        backgroundColorProvider = { pageBackgroundColor },
        reversePageProgressProvider = { frozenReverseAxis ?: reverseAxis },
        snapTranslationsToPixels = true,
        animatePageViewsDirectly = true
    )
    private var controller: PageAnimationController = SlidePageAnim(liveSlideSurface)
    private var transition = "slide"
    private var transitionDurationMs = ReaderPageAnimationSettings.SLIDE_DEFAULT_MS
    /**
     * 有自定义背景图时翻页必须走位图快照：此时页面是透明的（为了露出下层背景图），
     * 直接滑动两个 WebView 会让上一页和下一页的文字叠在一起。
     */
    private var snapshotPagesForBackgroundImage = false
    private var nativePagingEnabled = true
    private var nativeTouchPagingEnabled = true
    private var bookmarkPullEnabled = false
    private val bookmarkPullTracker = BookmarkPullGestureTracker()
    private var reverseAxis = false
    // Both animation surfaces use the same axis until the turn has settled.
    private var frozenReverseAxis: Boolean? = null
    private var turnSerial = 0L
    private var turnContext: EpubTurnContext<EpubContentWebView>? = null
    private var turnFrameCount = 0
    private val preparedPages = mutableMapOf<EpubContentWebView, EpubTurnPage<EpubContentWebView>>()
    private var waitingGestureEvent: MotionEvent? = null
    private var controllerUsesSnapshots = false
    private val usesSnapshots: Boolean
        get() = requiresEpubPreloadBitmap(transition, snapshotPagesForBackgroundImage)

    /** All appearance callbacks must yield to the turn that owns these sheets. */
    fun ownsPage(view: EpubContentWebView): Boolean =
        turnContext?.owns(view) == true && (overlayActive || pagingGesture || waitingForTarget != null)
    private var pagingGesture = false
    private var suppressTouchStream = false
    private var waitingGestureDirection = PageAnimationController.Direction.NONE
    private var slideTerminalFramePending = false
    private var slideTerminalHandoffPosted = false
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
    private var systemBackGestureCandidate = false
    private var systemBackGestureSuppressed = false
    private var systemBackGestureStartX = 0f
    private var systemBackGestureStartY = 0f
    private var overlayActive = false
    private var waitingForTarget: EpubPageTarget? = null
    private var waitingForPreparedActivePage = false
    private var waitingCurlDirection = PageAnimationController.Direction.NONE
    private var busyTouchStream = false
    private var busyCurlGestureClaimed = false
    private var busyCurlGestureDirection = PageAnimationController.Direction.NONE
    private var busyCurlLatestEventTime = 0L
    private var busyCurlLatestX = 0f
    private var busyCurlLatestY = 0f
    private var busyCurlLatestMetaState = 0
    private var busyCurlVelocityTracker: VelocityTracker? = null
    private var busySlideTouchStream = false
    private var currentTarget = EpubPageTarget(-1, 0)
    private var currentPageCount = 1
    private var pendingSlideVisualDirection = PageAnimationController.Direction.NONE
    private var queuedSlideTurnDirection = PageAnimationController.Direction.NONE
    private val curlTurnSequencer = ReaderCurlTurnSequencer()
    private var curlFallbackToken = 0
    /** 预加载链路最近一次有进展的时间：只要还在动，卷曲翻页就继续等目标页。 */
    private var lastPreloadActivityAtMs = 0L
    private var curlPageGeneration = 0L
    private var slideVisualPositionDirty = false
    private var lastSlideVisualDirection = PageAnimationController.Direction.NONE
    private var previousTarget: EpubPageTarget? = null
    private var nextTarget: EpubPageTarget? = null
    private var previousGeneration = 0
    private var nextGeneration = 0
    private var previousReady = false
    private var nextReady = false
    private var previousPageCount = 1
    private var nextPageCount = 1
    private var frozenPreviousTarget: EpubPageTarget? = null
    private var frozenNextTarget: EpubPageTarget? = null
    private var pageBackgroundColor = Color.WHITE
    private var backgroundImagePath: String? = null
    private var backgroundImageOpacity = 1f
    private var backgroundImageBlurDp = 0f

    private var previousPreparedBitmap: Bitmap? = null
    private var nextPreparedBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var currentBitmapTarget: EpubPageTarget? = null

    var onPageCommit: ((direction: Int, target: EpubPageTarget, pageCount: Int) -> Unit)? = null
    var onBookmarkPullStart: (() -> Unit)? = null
    var onBookmarkPullProgress: ((distancePx: Float, armed: Boolean) -> Unit)? = null
    var onBookmarkPullFinished: ((commit: Boolean) -> Unit)? = null
    var onCapturedTapDirection: ((x: Float) -> Int)? = null
    var onCapturedCenterTap: (() -> Unit)? = null
    var onHasPotentialTurn: ((direction: Int, current: EpubPageTarget, pageCount: Int) -> Boolean)? = null
    var onSlideLookaheadRequested: ((PreloadSlot, EpubPageTarget) -> Unit)? = null
    var onSlideVisualPageAdvanced: ((EpubPageTarget, Int) -> Unit)? = null
    var onInvalidatePreloads: (() -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        setWillNotDraw(false)

        // Reader background sits below every page so publisher paint (an image
        // background in the book itself) always wins where it exists.
        addView(backgroundImageView, matchParentParams())
        addView(previousWebView, matchParentParams())
        addView(nextWebView, matchParentParams())
        addView(preloadMask, matchParentParams())
        addView(activeWebView, matchParentParams())
        addView(previousPage, matchParentParams())
        addView(currentPage, matchParentParams())
        addView(nextPage, matchParentParams())

        previousWebView.alpha = 1f
        previousWebView.visibility = View.VISIBLE
        nextWebView.alpha = 1f
        nextWebView.visibility = View.VISIBLE
        preloadMask.visibility = View.INVISIBLE
        hideAnimationPages()
        bindControllerCallbacks()
    }

    fun setPageBackgroundColor(color: Int) {
        pageBackgroundColor = color
        setBackgroundColor(color)
        preloadMask.setBackgroundColor(color)
        applyWebViewBackgroundColor()
    }

    /**
     * Reader background under the pages. The book's own image/gradient background
     * still paints above this, so it is only visible where the book has none.
     */
    fun setReaderBackgroundImage(path: String?, opacity: Float, blurDp: Float) {
        val resolvedPath = path?.takeIf { it.isNotBlank() && File(it).isFile }
        setReaderBackgroundUsesSnapshotPages(resolvedPath != null)
        val normalizedOpacity = opacity.coerceIn(0f, 1f)
        val normalizedBlur = blurDp.coerceIn(0f, 40f)
        if (resolvedPath == backgroundImagePath &&
            kotlin.math.abs(normalizedOpacity - backgroundImageOpacity) < 0.001f &&
            kotlin.math.abs(normalizedBlur - backgroundImageBlurDp) < 0.001f
        ) {
            applyWebViewBackgroundColor()
            return
        }
        backgroundImagePath = resolvedPath
        backgroundImageOpacity = normalizedOpacity
        backgroundImageBlurDp = normalizedBlur
        backgroundImageView.alpha = normalizedOpacity
        if (resolvedPath == null) {
            backgroundImageView.setImageDrawable(null)
            backgroundImageView.visibility = View.GONE
            preloadMask.setImageDrawable(null)
        } else {
            backgroundImageView.visibility = View.VISIBLE
            val radiusPx = normalizedBlur * resources.displayMetrics.density
            backgroundImageView.load(File(resolvedPath)) {
                allowHardware(false)
                crossfade(false)
                if (radiusPx >= 0.5f) {
                    transformations(ReaderBackgroundBlurTransformation(radiusPx.roundToInt()))
                }
            }
            // 遮罩层同样铺这张图，避免它把阅读背景盖成纯色。
            preloadMask.load(File(resolvedPath)) {
                allowHardware(false)
                crossfade(false)
                if (radiusPx >= 0.5f) {
                    transformations(ReaderBackgroundBlurTransformation(radiusPx.roundToInt()))
                }
            }
        }
        applyWebViewBackgroundColor()
    }

    /**
     * WebViews stay transparent while a reader background image is active so the
     * image layer shows through; pages still paint their own backgrounds on top.
     */
    private fun applyWebViewBackgroundColor() {
        val color = if (backgroundImagePath != null) Color.TRANSPARENT else pageBackgroundColor
        previousWebView.setBackgroundColor(color)
        activeWebView.setBackgroundColor(color)
        nextWebView.setBackgroundColor(color)
    }

    private fun setReaderBackgroundUsesSnapshotPages(enabled: Boolean) {
        if (snapshotPagesForBackgroundImage == enabled) return
        snapshotPagesForBackgroundImage = enabled
        if (transition != "curl") {
            setTransition(transition, transitionDurationMs)
        }
    }

    /** Draws the flat reader color plus the background image into a page snapshot. */
    private fun drawReaderBackground(canvas: Canvas) {
        if (backgroundImagePath == null || backgroundImageView.visibility != View.VISIBLE) return
        backgroundImageView.draw(canvas)
    }

    fun setReverseAxis(reverse: Boolean) {
        reverseAxis = reverse
    }

    fun setMediaOnlyNativePaging(enabled: Boolean) {
        if (mediaOnlyNativePaging == enabled) return
        mediaOnlyNativePaging = enabled
        if (!overlayActive && !pagingGesture && waitingForTarget == null) {
            resetLivePageViews()
        }
    }

    fun setNativePagingEnabled(enabled: Boolean) {
        nativePagingEnabled = enabled
        if (!enabled) {
            pendingSlideVisualDirection = PageAnimationController.Direction.NONE
            queuedSlideTurnDirection = PageAnimationController.Direction.NONE
            curlTurnSequencer.clear()
            slideVisualPositionDirty = false
            busyTouchStream = false
            clearBusyCurlGesture()
            busySlideTouchStream = false
            clearPendingSlideInput()
        }
        if (!enabled && (overlayActive || pagingGesture)) {
            controller.abortAnim()
            resetAnimationOverlay()
        }
    }

    fun setNativeTouchPagingEnabled(enabled: Boolean) {
        nativeTouchPagingEnabled = enabled
        if (!enabled) {
            busyTouchStream = false
            clearBusyCurlGesture()
        }
    }

    fun setBookmarkPullEnabled(enabled: Boolean) {
        if (bookmarkPullEnabled == enabled) return
        bookmarkPullEnabled = enabled
        if (!enabled) {
            val finish = bookmarkPullTracker.finish(cancelled = true)
            if (finish.wasActive) onBookmarkPullFinished?.invoke(false)
        }
    }

    fun setTransition(
        mode: String,
        durationMs: Int = ReaderPageAnimationSettings.defaultFor(mode)
    ) {
        val normalized = when (mode) {
            "curl" -> "curl"
            "scroll" -> "scroll"
            else -> "slide"
        }
        val sanitizedDuration = ReaderPageAnimationSettings.sanitizeDuration(
            normalized,
            durationMs
        )
        val needsSnapshots = requiresEpubPreloadBitmap(normalized, snapshotPagesForBackgroundImage)
        if (normalized == transition && controllerUsesSnapshots == needsSnapshots) {
            transitionDurationMs = sanitizedDuration
            when (val current = controller) {
                is SlidePageAnim -> current.setBaseDuration(sanitizedDuration)
                is ScrollPageAnim -> current.setBaseDuration(sanitizedDuration)
                is CurlPageAnim -> current.setBaseDuration(sanitizedDuration)
            }
            return
        }
        val previousTargetBeforeChange = previousTarget
        val nextTargetBeforeChange = nextTarget
        controller.abortAnim()
        (controller as? CurlPageAnim)?.destroy()
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        queuedSlideTurnDirection = PageAnimationController.Direction.NONE
        curlTurnSequencer.clear()
        slideVisualPositionDirty = false
        busyTouchStream = false
        clearBusyCurlGesture()
        busySlideTouchStream = false
        waitingGestureDirection = PageAnimationController.Direction.NONE
        clearPendingSlideInput()
        transition = normalized
        transitionDurationMs = sanitizedDuration
        controllerUsesSnapshots = needsSnapshots
        val slideSurface = if (snapshotPagesForBackgroundImage) snapshotSurface else liveSlideSurface
        controller = when (normalized) {
            "curl" -> CurlPageAnim(
                snapshotSurface,
                trackCornerTouchDirectly = true,
                baseDurationMs = transitionDurationMs
            )
            "scroll" -> ScrollPageAnim(slideSurface, transitionDurationMs)
            else -> SlidePageAnim(slideSurface, transitionDurationMs)
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
        // ready/page can arrive again while the same document is being animated.
        // The frozen turn owns its views until commit/cancel; a DOM callback must
        // not move the upper sheet back to x=0 or put it behind its neighbour.
        if (deferEpubPageNotification(overlayActive || pagingGesture, waiting != null)) return

        if (currentTarget != incomingTarget) curlPageGeneration++
        currentTarget = incomingTarget
        currentPageCount = pageCount.coerceAtLeast(1)
        preparedPages[activeWebView] = pageIdentity(activeWebView, incomingTarget, 0)
        val promotedSnapshotReady = waitingForPreparedActivePage &&
            currentBitmapTarget == incomingTarget && currentBitmap != null
        if (!promotedSnapshotReady && usesSnapshots) {
            refreshCurrentSnapshot(incomingTarget)
        }
        if (waiting == null) {
            if (!overlayActive && !pagingGesture) {
                pendingSlideVisualDirection = PageAnimationController.Direction.NONE
                slideVisualPositionDirty = false
                lastSlideVisualDirection = PageAnimationController.Direction.NONE
            }
            resetLivePageViews()
            turnContext = null
            onSettled?.invoke()
            post(::resumeBusyCurlGestureIfReady)
            post(::startQueuedTurnIfReady)
            return
        }

        activeWebView.animate().cancel()
        activeWebView.translationX = 0f
        activeWebView.translationY = 0f
        activeWebView.scaleX = 1f
        activeWebView.scaleY = 1f
        activeWebView.translationZ = 5f
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE
        activeWebView.bringToFront()
        waitingForTarget = null
        waitingForPreparedActivePage = false
        waitingCurlDirection = PageAnimationController.Direction.NONE
        turnContext = null
        resetLivePageViews()
        onSettled?.invoke()
        post(::resumeBusyCurlGestureIfReady)
        post(::startQueuedTurnIfReady)
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

    fun promotePreparedPage(
        slot: PreloadSlot,
        target: EpubPageTarget,
        pageCount: Int
    ): Boolean {
        val slotTarget = preloadTarget(slot)
        if (!isPreloadReady(slot) || slotTarget != target) return false

        controller.abortAnim()
        resetAnimationOverlay()
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        queuedSlideTurnDirection = PageAnimationController.Direction.NONE
        curlTurnSequencer.clear()
        slideVisualPositionDirty = false
        lastSlideVisualDirection = PageAnimationController.Direction.NONE
        waitingForTarget = null
        waitingForPreparedActivePage = false
        waitingCurlDirection = PageAnimationController.Direction.NONE
        pagingGesture = false
        busyTouchStream = false
        clearBusyCurlGesture()
        busySlideTouchStream = false
        clearPendingSlideInput()

        val direction = if (slot == PreloadSlot.NEXT) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
        if (!advanceSlideRoles(direction, target, notifyLookahead = false)) return false
        currentPageCount = pageCount.coerceAtLeast(1)
        slideVisualPositionDirty = false
        lastSlideVisualDirection = PageAnimationController.Direction.NONE
        resetLivePageViews()
        invalidate()
        return true
    }

    fun currentPageTarget(): EpubPageTarget = currentTarget

    fun currentPageCount(): Int = currentPageCount

    fun markPreloadLoading(
        slot: PreloadSlot,
        target: EpubPageTarget?,
        generation: Int
    ): Boolean {
        preparedPages.remove(if (slot == PreloadSlot.PREVIOUS) previousWebView else nextWebView)
        lastPreloadActivityAtMs = android.os.SystemClock.uptimeMillis()
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                previousTarget = target
                previousGeneration = generation
                previousReady = false
                previousPageCount = 1
                bitmapLeases.retire(previousPreparedBitmap)
                previousPreparedBitmap = null
                placeAdjacentWebViewAtIdle(previousWebView, PreloadSlot.PREVIOUS)
                previousWebView.visibility = View.VISIBLE
            }
            PreloadSlot.NEXT -> {
                nextTarget = target
                nextGeneration = generation
                nextReady = false
                nextPageCount = 1
                bitmapLeases.retire(nextPreparedBitmap)
                nextPreparedBitmap = null
                placeAdjacentWebViewAtIdle(nextWebView, PreloadSlot.NEXT)
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
        actualPageCount: Int,
        sourceView: View
    ) {
        lastPreloadActivityAtMs = android.os.SystemClock.uptimeMillis()
        val contentView = sourceView as? EpubContentWebView ?: return
        if (ownsPage(contentView)) return
        if (preloadSlotOf(contentView) != slot) return
        val actual = EpubPageTarget(requested.chapterIndex, actualPageIndex.coerceAtLeast(0))
        when (slot) {
            PreloadSlot.PREVIOUS -> {
                if (previousTarget != requested || previousGeneration != generation) return
                if (sourceView.width <= 0 || sourceView.height <= 0) return
                if (previousReady && previousTarget == actual && previousPreparedBitmap != null) return
                previousTarget = actual
                previousPageCount = actualPageCount.coerceAtLeast(1)
                if (usesSnapshots) {
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
                if (nextReady && nextTarget == actual && nextPreparedBitmap != null) return
                nextTarget = actual
                nextPageCount = actualPageCount.coerceAtLeast(1)
                if (usesSnapshots) {
                    nextPreparedBitmap = snapshot(sourceView, nextPreparedBitmap)
                    nextReady = nextPreparedBitmap != null
                } else {
                    bitmapLeases.retire(nextPreparedBitmap)
                    nextPreparedBitmap = null
                    nextReady = true
                }
            }
        }
        preparedPages[contentView] = pageIdentity(contentView, actual, generation)
        post(::resumeWaitingGestureIfReady)
        Log.d(
            "EpubCurlCommit",
            "preloadReady slot=$slot requested=$requested actual=$actual " +
                "previousReady=$previousReady nextReady=$nextReady"
        )
        postInvalidateOnAnimation()
        post(::resumeBusyCurlGestureIfReady)
        post(::startQueuedTurnIfReady)
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
        if (!nativePagingEnabled || direction == 0 ||
            transition !in setOf("slide", "scroll", "curl")
        ) return false
        val controllerDirection = if (direction > 0) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
        val requestedTarget = targetFor(controllerDirection)
        if (!overlayActive && waitingForTarget == null && requestedTarget != null) {
            ReaderPageTurnPerformance.beginIntent(
                preloaded = canFlip(controllerDirection),
                crossChapter = requestedTarget.chapterIndex != currentTarget.chapterIndex
            )
        }
        if (isLiveEpubPageTransition(transition) && overlayActive) {
            queueSlideTurn(controllerDirection)
            return true
        }
        if (isLiveEpubPageTransition(transition) && waitingForTarget != null) {
            queueSlideTurn(controllerDirection)
            return true
        }
        if (transition == "curl") {
            val inFlightDirection = when {
                waitingForTarget != null -> waitingCurlDirection
                controller.isRunning || controller.isDragging -> controller.currentDirection
                else -> PageAnimationController.Direction.NONE
            }
            if (!curlTurnDirectionIsCompatible(inFlightDirection, controllerDirection)) {
                return true
            }
            return when (
                epubCurlTurnDisposition(
                    idle = !overlayActive && waitingForTarget == null &&
                        !pagingGesture && !controller.isRunning && !controller.isDragging,
                    targetExists = hasFlipTarget(controllerDirection) ||
                        hasPotentialTurn(controllerDirection),
                    targetReady = canFlip(controllerDirection)
                )
                ) {
                EpubCurlTurnDisposition.ACCEPT -> startTurnFromTap(controllerDirection)
                EpubCurlTurnDisposition.QUEUE -> {
                    val expediteRunning = controller.isRunning
                    queueCurlTurn(
                        direction = controllerDirection,
                        expedited = expediteRunning || curlTurnSequencer.pendingSteps != 0
                    )
                    if (expediteRunning) controller.completeRunningFlipForNewInput()
                    post(::startQueuedCurlTurnIfReady)
                    scheduleCurlTurnFallback(controllerDirection)
                    true
                }
                EpubCurlTurnDisposition.PASS_BOUNDARY -> false
            }
        }
        if (overlayActive || waitingForTarget != null) return false
        if (startTurnFromTap(controllerDirection)) return true
        if (isLiveEpubPageTransition(transition) && hasFlipTarget(controllerDirection)) {
            queueSlideTurn(controllerDirection)
            post(::commitDirtySlideVisualPosition)
            return true
        }
        return false
    }

    fun requestTurn(direction: Int): Boolean = turnFromTap(direction)

    internal fun activeCurlGestureOffsetPx(): Float? {
        val curl = controller as? CurlPageAnim ?: return null
        return curl.getOffsetX().takeIf { pagingGesture && curl.isDragging }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            systemBackGestureStartX = event.x
            systemBackGestureStartY = event.y
            systemBackGestureCandidate = isSystemBackGestureStart(
                width.toFloat(),
                height.toFloat(),
                event.x,
                event.y,
                resources.displayMetrics.density
            )
            systemBackGestureSuppressed = false
        } else if (systemBackGestureSuppressed) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                systemBackGestureSuppressed = false
                systemBackGestureCandidate = false
            }
            return super.dispatchTouchEvent(event)
        } else if (systemBackGestureCandidate &&
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            val deltaX = event.x - systemBackGestureStartX
            val deltaY = event.y - systemBackGestureStartY
            if (isSystemBackGestureSwipe(deltaX, deltaY)) {
                // Reserve the complete edge stream for Android navigation.
                // In particular, do not queue turns while another curl is busy.
                systemBackGestureCandidate = false
                systemBackGestureSuppressed = true
                busyTouchStream = false
                clearBusyCurlGesture()
                busySlideTouchStream = false
                pagingGesture = false
                waitingGestureDirection = PageAnimationController.Direction.NONE
                capturedSlideTouchStream = false
                val bookmarkFinish = bookmarkPullTracker.finish(cancelled = true)
                if (bookmarkFinish.wasActive) {
                    onBookmarkPullFinished?.invoke(false)
                }
                return super.dispatchTouchEvent(event)
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> bookmarkPullTracker.start(
                x = event.rawX,
                y = event.rawY,
                density = resources.displayMetrics.density,
                enabled = bookmarkPullEnabled &&
                    transition != "scroll" &&
                    !overlayActive &&
                    waitingForTarget == null &&
                    !controller.isRunning &&
                    !controller.isDragging &&
                    !pagingGesture &&
                    !busyTouchStream &&
                    !busySlideTouchStream,
                startRegionY = event.y,
                gestureRegionHeight = height.toFloat()
            )
            MotionEvent.ACTION_MOVE -> {
                bookmarkPullTracker.move(event.rawX, event.rawY)?.let { update ->
                    if (update.justClaimed) {
                        cancelChildTouch(event)
                        activeWebView.clearTextSelection()
                        onBookmarkPullStart?.invoke()
                    }
                    if (update.crossedThreshold) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onBookmarkPullProgress?.invoke(update.distancePx, update.armed)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finish = bookmarkPullTracker.finish(
                    cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                )
                if (finish.wasActive) {
                    onBookmarkPullFinished?.invoke(finish.commit)
                    return true
                }
            }
        }
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
        if (isLiveEpubPageTransition(transition) && slideTerminalFramePending) {
            capturePendingSlideSwipe(event)
            return true
        }
        if (isLiveEpubPageTransition(transition) && (busySlideTouchStream ||
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                (overlayActive || waitingForTarget != null ||
                    controller.isRunning || controller.isDragging))
        ) {
            return handleBusySlideTouch(event)
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
        if (!nativePagingEnabled || transition !in setOf("slide", "scroll", "curl")) {
            return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchDownTime = event.eventTime
                pagingGesture = false
                waitingGestureDirection = PageAnimationController.Direction.NONE
                clearWaitingGestureEvent()
                capturedSlideTouchStream = false
                return super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pagingGesture) {
                    dispatchMappedEvent(event)
                    return true
                }
                if (waitingGestureDirection != PageAnimationController.Direction.NONE) {
                    rememberWaitingGesture(event)
                    resumeWaitingGestureIfReady()
                    return true
                }
                if (tryBeginSlideGesture(event)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (waitingGestureDirection != PageAnimationController.Direction.NONE) {
                    val direction = waitingGestureDirection
                    waitingGestureDirection = PageAnimationController.Direction.NONE
                    clearWaitingGestureEvent()
                    val extent = if (transition == "scroll") height else width
                    val distance = if (transition == "scroll") event.y - touchStartY else event.x - touchStartX
                    if (event.actionMasked == MotionEvent.ACTION_UP &&
                        abs(distance) >= extent * PageAnimationController.FLIP_THRESHOLD
                    ) {
                        queueSlideTurn(direction)
                        post(::startQueuedTurnIfReady)
                    }
                    capturedSlideTouchStream = false
                    post(::commitDirtySlideVisualPosition)
                    return true
                }
                if (pagingGesture) {
                    pagingGesture = false
                    dispatchMappedEvent(event)
                    if (supportsEpubPageRolePromotion(transition) && controller.isCompletingTurn && controller.isRunning &&
                        controller.currentDirection != PageAnimationController.Direction.NONE
                    ) {
                        recordPreparedTurn(controller.currentDirection)
                    }
                    return true
                }
                if (capturedSlideTouchStream) {
                    capturedSlideTouchStream = false
                    post(::commitDirtySlideVisualPosition)
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val direction = capturedTapDirection(event.x)
                    if (shouldCaptureNativeMediaCenterTap(
                            mediaOnlyNativePaging = mediaOnlyNativePaging,
                            direction = direction,
                            elapsedMs = event.eventTime - touchDownTime,
                            deltaX = event.x - touchStartX,
                            deltaY = event.y - touchStartY
                        )
                    ) {
                        // Full-page images can consume touchend/click inside WebView before
                        // the injected bridge sees it. Own the center tap at the host layer.
                        cancelChildTouch(event)
                        onCapturedCenterTap?.invoke()
                        return true
                    }
                    // Once the page-turn host is idle, the child WebView must see ACTION_UP.
                    // Its DOM handler distinguishes center-menu taps from links (including
                    // footnote markers). Capture the center tap only while a native animation
                    // or handoff is actually owning the gesture stream.
                    if ((overlayActive || waitingForTarget != null ||
                        controller.isRunning || controller.isDragging) &&
                        isCapturedBusyCurlCenterTap(
                            gestureClaimed = false,
                            direction = direction,
                            elapsedMs = event.eventTime - touchDownTime,
                            deltaX = event.x - touchStartX,
                            deltaY = event.y - touchStartY
                        )
                    ) {
                        cancelChildTouch(event)
                        onCapturedCenterTap?.invoke()
                        return true
                    }
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (isLiveEpubPageTransition(transition) && slideVisualPositionDirty && !overlayActive &&
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
            post(::resumeBusyCurlGestureIfReady)
            post(::startQueuedCurlTurnIfReady)
        }
        if (needsFrame) postInvalidateOnAnimation()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val turn = turnContext
        if (overlayActive && turn != null && listOf(turn.current, turn.target).any {
                !turn.matches(it.view, it.view.documentLifecycle.configurationGeneration,
                    it.view.documentLifecycle.layoutRevision)
            }
        ) {
            controller.abortAnim()
            resetAnimationOverlay()
            onInvalidatePreloads?.invoke()
            super.dispatchDraw(canvas)
            return
        }
        if (overlayActive && turn != null && isLiveEpubPageTransition(transition)) {
            drawTurnPages(canvas, turn)
            if (slideTerminalFramePending && !slideTerminalHandoffPosted) {
                slideTerminalHandoffPosted = true
                postOnAnimation(::finishSlideTerminalSwipeHandoff)
            }
            ReaderPageTurnPerformance.markFirstFrame()
            return
        }
        if (overlayActive && controller.drawsDirectlyOnCanvas &&
            (controller.isRunning || controller.isDragging)
        ) {
            // Record an unused preload's display list beneath the opaque curl.
            allWebViews().filter { turn?.owns(it) != true }.forEach {
                drawChild(canvas, it, drawingTime)
            }
            controller.onDraw(canvas)
            ReaderPageTurnPerformance.markFirstFrame()
            return
        }
        if (overlayActive) controller.onDraw(canvas)
        super.dispatchDraw(canvas)
        if (overlayActive) {
            when (val current = controller) {
                is SlidePageAnim -> current.drawOverlay(canvas)
                is ScrollPageAnim -> current.drawOverlay(canvas)
                is CurlPageAnim -> current.drawOverlay(canvas)
            }
        }
        if (slideTerminalFramePending && !slideTerminalHandoffPosted) {
            slideTerminalHandoffPosted = true
            postOnAnimation(::finishSlideTerminalSwipeHandoff)
        }
        ReaderPageTurnPerformance.markFirstFrame()
    }

    /** Explicit composition, independent of Android's child Z reordering. */
    private fun drawTurnPages(canvas: Canvas, turn: EpubTurnContext<EpubContentWebView>) {
        allWebViews().filterNot(turn::owns).forEach { drawChild(canvas, it, drawingTime) }
        canvas.drawColor(pageBackgroundColor)
        val offset = controller.getOffsetX()
        val horizontal = horizontalPageFrame(turn.direction, offset, width.toFloat(), turn.reverseAxis)
        val vertical = verticalPageFrame(turn.direction, offset, height.toFloat())
        fun position(page: EpubTurnPage<EpubContentWebView>): Pair<Float, Float> {
            val current = page === turn.current
            return if (transition == "scroll") {
                0f to if (current) vertical.currentY else if (turn.direction == PageAnimationController.Direction.NEXT) {
                    vertical.nextY
                } else vertical.previousY
            } else {
                (if (current) horizontal.currentX else if (turn.direction == PageAnimationController.Direction.NEXT) {
                    horizontal.nextX
                } else horizontal.previousX).roundToInt().toFloat() to 0f
            }
        }
        val (upperX, upperY) = position(turn.upper)
        if (BuildConfig.DEBUG && turnFrameCount++ < 10) Log.d(PERFORMANCE_LOG_TAG,
            "frame turn=${turn.serial} n=$turnFrameCount upper=${turn.upper.target} " +
                "lower=${turn.lower.target} offset=$offset upperX=$upperX upperY=$upperY")
        fun draw(page: EpubTurnPage<EpubContentWebView>, underneath: Boolean) {
            val (x, y) = position(page)
            val save = canvas.save()
            canvas.clipRect(0, 0, width, height)
            if (underneath) canvas.clipOutRect(upperX, upperY, upperX + width, upperY + height)
            canvas.translate(x, y)
            canvas.clipRect(0, 0, width, height)
            canvas.drawColor(pageBackgroundColor)
            if (usesSnapshots) {
                val bitmap = when {
                    page === turn.current -> currentPage.pageBitmap
                    turn.direction == PageAnimationController.Direction.NEXT -> nextPage.pageBitmap
                    else -> previousPage.pageBitmap
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
                }
            } else {
                drawChild(canvas, page.view, drawingTime)
            }
            canvas.restoreToCount(save)
        }
        draw(turn.lower, underneath = true)
        draw(turn.upper, underneath = false)
        (controller as? SlidePageAnim)?.drawOverlay(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw <= 0 || oldh <= 0 || (w == oldw && h == oldh)) return
        controller.abortAnim()
        busyTouchStream = false
        clearBusyCurlGesture()
        resetAnimationOverlay()
        recyclePageBitmaps()
        previousReady = false
        nextReady = false
        onInvalidatePreloads?.invoke()
    }

    override fun onDetachedFromWindow() {
        ReaderPageTurnPerformance.cancel()
        bookmarkPullTracker.reset()
        controller.abortAnim()
        (controller as? CurlPageAnim)?.destroy()
        pendingSlideVisualDirection = PageAnimationController.Direction.NONE
        queuedSlideTurnDirection = PageAnimationController.Direction.NONE
        curlTurnSequencer.destroy()
        slideVisualPositionDirty = false
        busyTouchStream = false
        clearBusyCurlGesture()
        busySlideTouchStream = false
        clearPendingSlideInput()
        recyclePageBitmaps()
        bitmapLeases.destroy()
        preparedPages.clear()
        clearWaitingGestureEvent()
        super.onDetachedFromWindow()
    }

    private fun bindControllerCallbacks() {
        controller.onCanFlip = ::canFlip
        controller.onAnimationComplete = complete@{
            val turn = turnContext ?: return@complete
            if (!turn.finishOnce()) return@complete
            val direction = turn.direction
            curlTurnSequencer.idle()
            val target = turn.target.target
            Log.d(
                "EpubCurlCommit",
                "complete dir=$direction current=$currentTarget target=$target " +
                    "previousReady=$previousReady nextReady=$nextReady"
            )
            pendingSlideVisualDirection = PageAnimationController.Direction.NONE
            val promotedPreparedTarget = supportsEpubPageRolePromotion(transition) &&
                advanceSlideRoles(direction, target)

            waitingForTarget = target
            waitingForPreparedActivePage = promotedPreparedTarget
            waitingCurlDirection = direction.takeIf { transition == "curl" }
                ?: PageAnimationController.Direction.NONE
            overlayActive = false
            hideAnimationPages()

            val targetView = if (promotedPreparedTarget) {
                activeWebView
            } else when (direction) {
                PageAnimationController.Direction.NEXT -> nextWebView
                PageAnimationController.Direction.PREV -> previousWebView
                PageAnimationController.Direction.NONE -> null
            }
            if (targetView == null) {
                resetAnimationOverlay()
                post(::startQueuedTurnIfReady)
                return@complete
            }

            activeWebView.translationX = 0f
            activeWebView.translationY = 0f
            activeWebView.scaleX = 1f
            activeWebView.scaleY = 1f
            activeWebView.translationZ = 2f
            activeWebView.alpha = 1f
            activeWebView.visibility = View.VISIBLE

            targetView.animate().cancel()
            targetView.translationX = 0f
            targetView.translationY = 0f
            targetView.scaleX = 1f
            targetView.scaleY = 1f
            targetView.translationZ = 4f
            targetView.alpha = 1f
            targetView.visibility = View.VISIBLE
            targetView.bringToFront()

            dispatchPromotedPageCommit(direction, target, targetView)
            slideVisualPositionDirty = false
            lastSlideVisualDirection = PageAnimationController.Direction.NONE
            invalidate()
        }
    }

    private fun canFlip(direction: PageAnimationController.Direction): Boolean = when (direction) {
        PageAnimationController.Direction.NEXT -> nextReady && preparedPageMatches(nextWebView, nextTarget)
        PageAnimationController.Direction.PREV -> previousReady && preparedPageMatches(previousWebView, previousTarget)
        PageAnimationController.Direction.NONE -> false
    }

    private fun pageIdentity(view: EpubContentWebView, target: EpubPageTarget, request: Int) =
        EpubTurnPage(view, target, view.documentLifecycle.configurationGeneration,
            view.documentLifecycle.layoutRevision, request)

    private fun preparedPageMatches(view: EpubContentWebView, target: EpubPageTarget?): Boolean {
        val prepared = preparedPages[view] ?: return false
        return prepared.target == target && prepared.generation == view.documentLifecycle.configurationGeneration &&
            prepared.revision == view.documentLifecycle.layoutRevision
    }

    private fun targetFor(direction: PageAnimationController.Direction): EpubPageTarget? =
        when (direction) {
            PageAnimationController.Direction.NEXT -> nextTarget
            PageAnimationController.Direction.PREV -> previousTarget
            PageAnimationController.Direction.NONE -> null
        }

    private fun hasFlipTarget(direction: PageAnimationController.Direction): Boolean = when (direction) {
        PageAnimationController.Direction.NEXT -> nextTarget != null
        PageAnimationController.Direction.PREV -> previousTarget != null
        PageAnimationController.Direction.NONE -> false
    }

    private fun hasPotentialTurn(direction: PageAnimationController.Direction): Boolean {
        val delta = direction.toTurnDelta()
        return delta != 0 && onHasPotentialTurn?.invoke(
            delta,
            currentTarget,
            currentPageCount
        ) == true
    }

    private fun tryBeginSlideGesture(event: MotionEvent): Boolean {
        val dx = abs(event.x - touchStartX)
        val dy = abs(event.y - touchStartY)
        val elapsed = event.eventTime - touchDownTime
        // 4px 阈值会把轻点链接时的正常手指抖动误判为翻页手势并取消 WebView 点击。
        // Keep link-tap jitter below 12px. Curl additionally requires horizontal dominance.
        val pageIntent = when (transition) {
            "curl" -> isCurlSwipeIntent(event.x - touchStartX, event.y - touchStartY)
            "scroll" -> dy > dx
            else -> dx > dy * 0.3f
        }
        val axisDistance = if (transition == "scroll") dy else dx
        if (axisDistance <= BUSY_TAP_MOVE_LIMIT_PX || !pageIntent ||
            elapsed >= 500L && activeWebView.hasSelectionUi()
        ) {
            return capturedSlideTouchStream
        }
        val direction = if (transition == "scroll") {
            if (event.y < touchStartY) {
                PageAnimationController.Direction.NEXT
            } else {
                PageAnimationController.Direction.PREV
            }
        } else {
            epubPageDirectionForHorizontalDelta(
                deltaX = event.x - touchStartX,
                reverseAxis = reverseAxis
            )
        }
        if (!canFlip(direction)) {
            if (transition == "curl" &&
                (hasFlipTarget(direction) || hasPotentialTurn(direction))
            ) {
                beginBusyCurlGesture(event, direction)
                cancelChildTouch(event)
                return true
            }
            if (hasFlipTarget(direction)) {
                waitingGestureDirection = direction
                rememberWaitingGesture(event)
                capturedSlideTouchStream = false
                cancelChildTouch(event)
                return true
            }
            return capturedSlideTouchStream
        }
        if (!prepareAnimationPages(direction = direction)) return true
        capturedSlideTouchStream = false
        beginPagingGesture(event)
        return true
    }

    private fun rememberWaitingGesture(event: MotionEvent) {
        clearWaitingGestureEvent()
        waitingGestureEvent = MotionEvent.obtain(event)
    }

    private fun clearWaitingGestureEvent() {
        waitingGestureEvent?.recycle()
        waitingGestureEvent = null
    }

    private fun resumeWaitingGestureIfReady() {
        val event = waitingGestureEvent ?: return
        val direction = waitingGestureDirection
        if (direction == PageAnimationController.Direction.NONE || !canFlip(direction)) return
        if (!prepareAnimationPages(direction)) return
        waitingGestureDirection = PageAnimationController.Direction.NONE
        beginPagingGesture(event)
        clearWaitingGestureEvent()
        postInvalidateOnAnimation()
    }

    private fun holdSlideTerminalFrameForSwipe(event: MotionEvent): Boolean {
        val pendingTarget = when (pendingSlideVisualDirection) {
            PageAnimationController.Direction.NEXT -> frozenNextTarget
            PageAnimationController.Direction.PREV -> frozenPreviousTarget
            PageAnimationController.Direction.NONE -> null
        } ?: return false
        if (!controller.holdRunningFlipAtEnd()) return false

        slideTerminalFramePending = true
        slideTerminalHandoffPosted = false
        pendingSlideTouchActive = true
        pendingSlideEventTime = event.eventTime
        pendingSlideEventX = event.x
        pendingSlideEventY = event.y
        pendingSlideMetaState = event.metaState
        busySlideTouchStream = false
        postInvalidateOnAnimation()
        return true
    }

    private fun capturePendingSlideSwipe(event: MotionEvent) {
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

    private fun finishSlideTerminalSwipeHandoff() {
        if (!slideTerminalFramePending) return
        val direction = pendingSlideVisualDirection
        val target = when (direction) {
            PageAnimationController.Direction.NEXT -> frozenNextTarget
            PageAnimationController.Direction.PREV -> frozenPreviousTarget
            PageAnimationController.Direction.NONE -> null
        }
        val continueTouch = pendingSlideTouchActive
        val downTime = pendingSlideDownTime
        val downX = pendingSlideDownX
        val downY = pendingSlideDownY
        val eventTime = pendingSlideEventTime
        val eventX = pendingSlideEventX
        val eventY = pendingSlideEventY
        val metaState = pendingSlideMetaState
        val crossesChapter = target != null && isCrossChapterPageTurn(currentTarget, target)

        val promoted = advancePendingSlideVisualTurn()
        controller.abortAnim()
        clearSlideTerminalState()
        resetAnimationOverlay()
        if (!promoted) return
        if (crossesChapter) commitDirtySlideVisualPosition()
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

    private fun isCapturedSlideTap(event: MotionEvent): Boolean =
        event.eventTime - pendingSlideDownTime < 300L &&
            abs(event.x - pendingSlideDownX) <= BUSY_TAP_MOVE_LIMIT_PX &&
            abs(event.y - pendingSlideDownY) < 50f

    private fun capturedTapDirection(x: Float): PageAnimationController.Direction =
        when (onCapturedTapDirection?.invoke(x) ?: 0) {
            1 -> PageAnimationController.Direction.NEXT
            -1 -> PageAnimationController.Direction.PREV
            else -> PageAnimationController.Direction.NONE
        }

    private fun clearPendingSlideInput() {
        clearSlideTerminalState()
        busySlideTouchStream = false
        capturedSlideTouchStream = false
    }

    private fun clearSlideTerminalState() {
        slideTerminalFramePending = false
        slideTerminalHandoffPosted = false
        pendingSlideTouchActive = false
    }

    private fun cancelChildTouch(source: MotionEvent) {
        val cancel = MotionEvent.obtain(source).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    private fun beginPagingGesture(event: MotionEvent) {
        bookmarkPullTracker.reset()
        pagingGesture = true
        overlayActive = true
        cancelChildTouch(event)
        val down = MotionEvent.obtain(
            touchDownTime,
            touchDownTime,
            MotionEvent.ACTION_DOWN,
            touchStartX,
            touchStartY,
            event.metaState
        )
        controller.onTouchEvent(down)
        down.recycle()
        controller.confirmPageGesture()
        dispatchMappedEvent(event)
    }

    private fun startTurnFromTap(
        direction: PageAnimationController.Direction,
        reuseCurrentCurlSnapshot: Boolean = false,
        gestureStartY: Float? = null,
        expedited: Boolean = false
    ): Boolean {
        if (!canFlip(direction) ||
            !prepareAnimationPages(direction, reuseCurrentCurlSnapshot)
        ) return false
        targetFor(direction)?.let { target ->
            ReaderPageTurnPerformance.beginIntent(
                preloaded = true,
                crossChapter = target.chapterIndex != currentTarget.chapterIndex
            )
            ReaderPageTurnPerformance.markVisualStarted()
        }
        if (supportsEpubPageRolePromotion(transition)) recordPreparedTurn(direction)
        overlayActive = true
        when (val current = controller) {
            is CurlPageAnim -> current.startFromTap(direction, gestureStartY, expedited)
            is SlidePageAnim -> current.startFromTap(direction)
            is ScrollPageAnim -> current.startFromTap(direction)
        }
        if (controller is CurlPageAnim) curlTurnSequencer.settling()
        invalidate()
        return true
    }

    private fun recordPreparedTurn(direction: PageAnimationController.Direction) {
        pendingSlideVisualDirection = direction
        val destination = when (direction) {
            PageAnimationController.Direction.NEXT -> frozenNextTarget
            PageAnimationController.Direction.PREV -> frozenPreviousTarget
            PageAnimationController.Direction.NONE -> null
        } ?: return
        val destinationPageCount = when (direction) {
            PageAnimationController.Direction.NEXT -> nextPageCount
            PageAnimationController.Direction.PREV -> previousPageCount
            PageAnimationController.Direction.NONE -> 0
        }
        val lookahead = slideLookaheadFromDestination(
            destination = destination,
            destinationPageCount = destinationPageCount,
            direction = direction.toTurnDelta()
        ) ?: return
        val stagingSlot = if (direction == PageAnimationController.Direction.NEXT) {
            PreloadSlot.PREVIOUS
        } else {
            PreloadSlot.NEXT
        }
        post {
            if (pendingSlideVisualDirection == direction) {
                onSlideLookaheadRequested?.invoke(stagingSlot, lookahead)
            }
        }
    }

    private fun queueSlideTurn(direction: PageAnimationController.Direction) {
        if (direction == PageAnimationController.Direction.NONE) return
        queuedSlideTurnDirection = direction
    }

    private fun queueCurlTurn(
        direction: PageAnimationController.Direction,
        gestureStartY: Float? = null,
        input: CurlTurnInput = CurlTurnInput.TAP,
        expedited: Boolean = true
    ) {
        curlTurnSequencer.offer(
            direction = direction,
            gestureStartY = gestureStartY,
            input = input,
            expedited = expedited,
            pageGeneration = curlPageGeneration
        )
    }

    private fun startQueuedTurnIfReady() {
        startQueuedSlideTurnIfReady()
        startQueuedCurlTurnIfReady()
    }

    private fun startQueuedSlideTurnIfReady() {
        if (!isLiveEpubPageTransition(transition) || overlayActive || slideTerminalFramePending ||
            waitingForTarget != null ||
            pagingGesture || controller.isRunning ||
            controller.isDragging
        ) return
        val direction = queuedSlideTurnDirection
        if (direction == PageAnimationController.Direction.NONE) return
        if (!hasFlipTarget(direction)) {
            queuedSlideTurnDirection = PageAnimationController.Direction.NONE
            return
        }
        if (!canFlip(direction)) return
        queuedSlideTurnDirection = PageAnimationController.Direction.NONE
        startTurnFromTap(direction)
    }

    private fun startQueuedCurlTurnIfReady() {
        if (transition != "curl" || overlayActive || waitingForTarget != null ||
            pagingGesture || busyTouchStream || controller.isRunning || controller.isDragging
        ) return
        val direction = when {
            curlTurnSequencer.pendingSteps > 0 -> PageAnimationController.Direction.NEXT
            curlTurnSequencer.pendingSteps < 0 -> PageAnimationController.Direction.PREV
            else -> PageAnimationController.Direction.NONE
        }
        if (direction == PageAnimationController.Direction.NONE) return
        if (!hasFlipTarget(direction)) {
            if (hasPotentialTurn(direction)) {
                curlTurnSequencer.waitingForTarget()
                return
            }
            curlTurnSequencer.clear()
            return
        }
        if (!canFlip(direction)) return
        val turn = curlTurnSequencer.pollTurn()
        val expedited = turn.expedited || curlTurnSequencer.pendingSteps != 0
        if (!startTurnFromTap(
                direction = turn.direction,
                reuseCurrentCurlSnapshot = true,
                gestureStartY = turn.gestureStartY,
                expedited = expedited
            )
        ) {
            curlTurnSequencer.restore(turn)
        }
    }

    /**
     * 卷曲翻页等不到目标页时的兜底：延迟一小段时间后若仍未准备好，
     * 直接让 WebView 翻页，避免这一下操作石沉大海。
     */
    private fun scheduleCurlTurnFallback(direction: PageAnimationController.Direction) {
        if (direction == PageAnimationController.Direction.NONE) return
        val token = ++curlFallbackToken
        postDelayed({
            if (token != curlFallbackToken) return@postDelayed
            val idle = !overlayActive && waitingForTarget == null && !pagingGesture &&
                !controller.isRunning && !controller.isDragging
            // 预加载还在持续推进时就继续等，别和正常的"目标页准备好"抢时间。
            val sincePreloadActivity =
                android.os.SystemClock.uptimeMillis() - lastPreloadActivityAtMs
            if (sincePreloadActivity < CURL_TURN_FALLBACK_DELAY_MS) {
                scheduleCurlTurnFallback(direction)
                return@postDelayed
            }
            if (!shouldFallBackToDirectCurlTurn(
                    turnPending = curlTurnSequencer.pendingSteps != 0,
                    idle = idle,
                    waitingForTarget = waitingForTarget != null,
                    targetReady = canFlip(direction),
                    potentialTurn = hasPotentialTurn(direction)
                )
            ) return@postDelayed
            val view = activeWebView
            android.util.Log.w(
                "EpubPageTurnHost",
                "curl target never became ready, turning directly dir=$direction"
            )
            curlTurnSequencer.clear()
            view.evaluateJavascript(
                "window.LumiReader&&window.LumiReader.turnPage(" +
                    direction.toTurnDelta() + ");",
                null
            )
        }, CURL_TURN_FALLBACK_DELAY_MS)
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
        target: EpubPageTarget,
        notifyLookahead: Boolean = true
    ): Boolean {
        val oldPreviousView = previousRoleView
        val oldActiveView = activeRoleView
        val oldNextView = nextRoleView
        val oldCurrentTarget = currentTarget
        val oldCurrentPageCount = currentPageCount
        var promotedPageCount = currentPageCount

        when (direction) {
            PageAnimationController.Direction.NEXT -> {
                val stagedTarget = previousTarget
                val stagedReady = previousReady
                val stagedGeneration = previousGeneration
                val stagedBitmap = previousPreparedBitmap
                val stagedPageCount = previousPageCount
                val promotedBitmap = nextPreparedBitmap
                promotedPageCount = nextPageCount

                previousRoleView = oldActiveView
                activeRoleView = oldNextView
                nextRoleView = oldPreviousView
                previousTarget = oldCurrentTarget
                previousReady = true
                previousPageCount = oldCurrentPageCount
                previousGeneration = nextGeneration
                previousPreparedBitmap = currentBitmap
                currentBitmap = promotedBitmap
                currentBitmapTarget = target

                val expectedNext = target.pageIndex + 1
                val stagingMatches = expectedNext < promotedPageCount &&
                    stagedTarget == EpubPageTarget(target.chapterIndex, expectedNext)
                nextTarget = stagedTarget.takeIf { stagingMatches }
                nextReady = stagedReady && stagingMatches
                nextPageCount = stagedPageCount.takeIf { stagingMatches } ?: 1
                nextGeneration = stagedGeneration
                nextPreparedBitmap = stagedBitmap.takeIf { stagingMatches }
                if (!stagingMatches) bitmapLeases.retire(stagedBitmap)
            }
            PageAnimationController.Direction.PREV -> {
                val stagedTarget = nextTarget
                val stagedReady = nextReady
                val stagedGeneration = nextGeneration
                val stagedBitmap = nextPreparedBitmap
                val stagedPageCount = nextPageCount
                val promotedBitmap = previousPreparedBitmap
                promotedPageCount = previousPageCount

                previousRoleView = oldNextView
                activeRoleView = oldPreviousView
                nextRoleView = oldActiveView
                nextTarget = oldCurrentTarget
                nextReady = true
                nextPageCount = oldCurrentPageCount
                nextGeneration = previousGeneration
                nextPreparedBitmap = currentBitmap
                currentBitmap = promotedBitmap
                currentBitmapTarget = target

                val expectedPrevious = target.pageIndex - 1
                val stagingMatches = expectedPrevious >= 0 &&
                    stagedTarget == EpubPageTarget(target.chapterIndex, expectedPrevious)
                previousTarget = stagedTarget.takeIf { stagingMatches }
                previousReady = stagedReady && stagingMatches
                previousPageCount = stagedPageCount.takeIf { stagingMatches } ?: 1
                previousGeneration = stagedGeneration
                previousPreparedBitmap = stagedBitmap.takeIf { stagingMatches }
                if (!stagingMatches) bitmapLeases.retire(stagedBitmap)
            }
            PageAnimationController.Direction.NONE -> return false
        }

        liveSlideSurface.prevPageView = previousWebView
        liveSlideSurface.curPageView = activeWebView
        liveSlideSurface.nextPageView = nextWebView
        if (currentTarget != target) curlPageGeneration++
        currentTarget = target
        currentPageCount = promotedPageCount.coerceAtLeast(1)
        slideVisualPositionDirty = true
        lastSlideVisualDirection = direction
        if (notifyLookahead) {
            onSlideVisualPageAdvanced?.invoke(currentTarget, currentPageCount)
        }
        return true
    }

    private fun commitDirtySlideVisualPosition() {
        if (!slideVisualPositionDirty || overlayActive || waitingForTarget != null) return
        val direction = lastSlideVisualDirection
        if (direction == PageAnimationController.Direction.NONE) return
        waitingForTarget = currentTarget
        waitingForPreparedActivePage = true
        dispatchPromotedPageCommit(direction, currentTarget, activeWebView)
        slideVisualPositionDirty = false
        lastSlideVisualDirection = PageAnimationController.Direction.NONE
    }

    private fun dispatchPromotedPageCommit(
        direction: PageAnimationController.Direction,
        target: EpubPageTarget,
        targetView: EpubContentWebView
    ) {
        val delta = direction.toTurnDelta()
        if (!mediaOnlyNativePaging || !isLiveEpubPageTransition(transition)) {
            onPageCommit?.invoke(delta, target, currentPageCount)
            return
        }

        var dispatched = false
        val pageCountAtPromotion = currentPageCount
        val commitOnce = {
            if (!dispatched && waitingForTarget == target && activeWebView === targetView) {
                dispatched = true
                onPageCommit?.invoke(delta, target, pageCountAtPromotion)
            }
        }
        val requestId = System.nanoTime()
        targetView.postVisualStateCallback(
            requestId,
            object : android.webkit.WebView.VisualStateCallback() {
                override fun onComplete(completedRequestId: Long) {
                    if (completedRequestId == requestId) {
                        targetView.runAfterNextDraw(commitOnce)
                    }
                }
            }
        )
        targetView.postDelayed(commitOnce, 180L)
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
                busyCurlGestureClaimed = false
                busyCurlGestureDirection = PageAnimationController.Direction.NONE
                busyCurlLatestEventTime = event.eventTime
                busyCurlLatestX = event.x
                busyCurlLatestY = event.y
                busyCurlLatestMetaState = event.metaState
                busyCurlVelocityTracker?.recycle()
                busyCurlVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                busyCurlVelocityTracker?.addMovement(event)
                busyCurlLatestEventTime = event.eventTime
                busyCurlLatestX = event.x
                busyCurlLatestY = event.y
                busyCurlLatestMetaState = event.metaState
                if (!busyCurlGestureClaimed) {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    if (abs(deltaX) > BUSY_TAP_MOVE_LIMIT_PX &&
                        isCurlSwipeIntent(deltaX, deltaY)
                    ) {
                        busyCurlGestureDirection = curlDirectionForDelta(deltaX)
                        busyCurlGestureClaimed =
                            busyCurlGestureDirection != PageAnimationController.Direction.NONE &&
                                (hasFlipTarget(busyCurlGestureDirection) ||
                                    hasPotentialTurn(busyCurlGestureDirection))
                        if (busyCurlGestureClaimed) {
                            // Direct manipulation supersedes queued automatic
                            // turns and must acquire the next page immediately.
                            curlTurnSequencer.clear()
                            if (controller.isRunning) {
                                (controller as? CurlPageAnim)
                                    ?.completeRunningFlipForGestureHandoff()
                            }
                        }
                    }
                } else {
                    val updatedDirection = curlDirectionForDelta(event.x - touchStartX)
                    if (updatedDirection != PageAnimationController.Direction.NONE &&
                        (hasFlipTarget(updatedDirection) || hasPotentialTurn(updatedDirection))
                    ) {
                        busyCurlGestureDirection = updatedDirection
                    }
                }
                if (busyCurlGestureClaimed) resumeBusyCurlGestureIfReady()
            }
            MotionEvent.ACTION_UP -> {
                busyCurlVelocityTracker?.addMovement(event)
                val elapsed = event.eventTime - touchDownTime
                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY
                if (busyCurlGestureClaimed && resumeBusyCurlGestureIfReady()) {
                    pagingGesture = false
                    dispatchMappedEvent(event)
                    if (controller.isRunning &&
                        controller.currentDirection != PageAnimationController.Direction.NONE
                    ) {
                        recordPreparedTurn(controller.currentDirection)
                    }
                    clearBusyCurlGesture()
                    return true
                }

                val direction = if (busyCurlGestureClaimed) {
                    busyCurlGestureDirection
                } else if (elapsed < 300L &&
                    abs(deltaX) <= BUSY_TAP_MOVE_LIMIT_PX && abs(deltaY) < 50f
                ) {
                    capturedTapDirection(event.x)
                } else {
                    PageAnimationController.Direction.NONE
                }
                val capturedCenterTap = isCapturedBusyCurlCenterTap(
                    gestureClaimed = busyCurlGestureClaimed,
                    direction = direction,
                    elapsedMs = elapsed,
                    deltaX = deltaX,
                    deltaY = deltaY
                )
                val input = if (busyCurlGestureClaimed) CurlTurnInput.SWIPE else CurlTurnInput.TAP
                val shouldQueue = if (input == CurlTurnInput.SWIPE) {
                    busyCurlVelocityTracker?.computeCurrentVelocity(1000)
                    val physicalVelocity = busyCurlVelocityTracker?.xVelocity ?: 0f
                    val directionalVelocity = when (direction) {
                        PageAnimationController.Direction.NEXT ->
                            physicalVelocity * if (reverseAxis) 1f else -1f
                        PageAnimationController.Direction.PREV ->
                            physicalVelocity * if (reverseAxis) -1f else 1f
                        PageAnimationController.Direction.NONE -> 0f
                    }
                    abs(deltaX) / width.coerceAtLeast(1).toFloat() >=
                        BUSY_CURL_COMMIT_FRACTION ||
                        directionalVelocity >= BUSY_CURL_FLING_DP_PER_SECOND *
                        resources.displayMetrics.density
                } else {
                    direction != PageAnimationController.Direction.NONE
                }
                val inFlightDirection = when {
                    waitingForTarget != null -> waitingCurlDirection
                    controller.isRunning || controller.isDragging -> controller.currentDirection
                    else -> PageAnimationController.Direction.NONE
                }
                if (shouldQueue && curlTurnDirectionIsCompatible(inFlightDirection, direction)) {
                    queueCurlTurn(
                        direction = direction,
                        gestureStartY = touchStartY.takeIf { input == CurlTurnInput.SWIPE },
                        input = input,
                        expedited = true
                    )
                }
                if (capturedCenterTap) onCapturedCenterTap?.invoke()
                if (controller.isRunning) controller.completeRunningFlipForNewInput()
                busyTouchStream = false
                clearBusyCurlGesture()
                post(::startQueuedCurlTurnIfReady)
            }
            MotionEvent.ACTION_CANCEL -> {
                busyTouchStream = false
                clearBusyCurlGesture()
            }
        }
        return true
    }

    private fun curlDirectionForDelta(deltaX: Float): PageAnimationController.Direction {
        if (deltaX == 0f) return PageAnimationController.Direction.NONE
        val physicalNext = deltaX < 0f
        val logicalNext = if (reverseAxis) !physicalNext else physicalNext
        return if (logicalNext) PageAnimationController.Direction.NEXT
        else PageAnimationController.Direction.PREV
    }

    private fun beginBusyCurlGesture(
        event: MotionEvent,
        direction: PageAnimationController.Direction
    ) {
        busyTouchStream = true
        busyCurlGestureClaimed = true
        busyCurlGestureDirection = direction
        busyCurlLatestEventTime = event.eventTime
        busyCurlLatestX = event.x
        busyCurlLatestY = event.y
        busyCurlLatestMetaState = event.metaState
        busyCurlVelocityTracker?.recycle()
        busyCurlVelocityTracker = VelocityTracker.obtain().also { tracker ->
            val down = MotionEvent.obtain(
                touchDownTime,
                touchDownTime,
                MotionEvent.ACTION_DOWN,
                touchStartX,
                touchStartY,
                event.metaState
            )
            try {
                tracker.addMovement(down)
                tracker.addMovement(event)
            } finally {
                down.recycle()
            }
        }
    }

    private fun resumeBusyCurlGestureIfReady(): Boolean {
        if (!busyTouchStream || !busyCurlGestureClaimed || transition != "curl" ||
            waitingForTarget != null || controller.isRunning || controller.isDragging ||
            overlayActive || pagingGesture
        ) return false
        val direction = busyCurlGestureDirection
        // Role promotion already moved the prepared destination snapshot into
        // currentBitmap. Re-capturing ACTIVE here blocks the same MOVE for
        // tens of milliseconds and can overload WebView during rapid turns.
        if (!canFlip(direction) ||
            !prepareAnimationPages(direction, reuseCurrentCurlSnapshot = true)
        ) return false

        val move = MotionEvent.obtain(
            touchDownTime,
            busyCurlLatestEventTime.coerceAtLeast(touchDownTime),
            MotionEvent.ACTION_MOVE,
            busyCurlLatestX,
            busyCurlLatestY,
            busyCurlLatestMetaState
        )
        try {
            beginPagingGesture(move)
        } finally {
            move.recycle()
        }
        busyTouchStream = false
        busyCurlGestureClaimed = false
        busyCurlGestureDirection = PageAnimationController.Direction.NONE
        busyCurlVelocityTracker?.recycle()
        busyCurlVelocityTracker = null
        curlTurnSequencer.dragging()
        return true
    }

    private fun clearBusyCurlGesture() {
        busyCurlGestureClaimed = false
        busyCurlGestureDirection = PageAnimationController.Direction.NONE
        busyCurlVelocityTracker?.recycle()
        busyCurlVelocityTracker = null
    }

    private fun handleBusySlideTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                busySlideTouchStream = true
                pendingSlideDownTime = event.downTime
                pendingSlideDownX = event.x
                pendingSlideDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - pendingSlideDownX
                val deltaY = event.y - pendingSlideDownY
                val pageIntent = if (transition == "scroll") {
                    abs(deltaY) > BUSY_TAP_MOVE_LIMIT_PX && abs(deltaY) > abs(deltaX)
                } else {
                    abs(deltaX) > BUSY_TAP_MOVE_LIMIT_PX && abs(deltaX) > abs(deltaY) * 0.3f
                }
                if (event.eventTime - pendingSlideDownTime < 500L &&
                    pageIntent &&
                    holdSlideTerminalFrameForSwipe(event)
                ) return true
            }
            MotionEvent.ACTION_UP -> {
                val direction = if (isCapturedSlideTap(event)) {
                    capturedTapDirection(event.x)
                } else {
                    val deltaX = event.x - pendingSlideDownX
                    val deltaY = event.y - pendingSlideDownY
                    val pageIntent = if (transition == "scroll") {
                        abs(deltaY) > BUSY_TAP_MOVE_LIMIT_PX && abs(deltaY) > abs(deltaX)
                    } else {
                        abs(deltaX) > BUSY_TAP_MOVE_LIMIT_PX && abs(deltaX) > abs(deltaY) * 0.3f
                    }
                    if (pageIntent) {
                        if (transition == "scroll") {
                            if (deltaY < 0f) PageAnimationController.Direction.NEXT
                            else PageAnimationController.Direction.PREV
                        } else {
                            epubPageDirectionForHorizontalDelta(deltaX, reverseAxis)
                        }
                    } else {
                        PageAnimationController.Direction.NONE
                    }
                }
                busySlideTouchStream = false
                queueSlideTurn(direction)
                post(::startQueuedSlideTurnIfReady)
            }
            MotionEvent.ACTION_CANCEL -> busySlideTouchStream = false
        }
        return true
    }

    private fun prepareAnimationPages(
        direction: PageAnimationController.Direction,
        reuseCurrentCurlSnapshot: Boolean = false
    ): Boolean {
        if (width <= 0 || height <= 0 || activeWebView.width <= 0 || activeWebView.height <= 0) return false
        val targetView = when (direction) {
            PageAnimationController.Direction.NEXT -> nextWebView
            PageAnimationController.Direction.PREV -> previousWebView
            else -> return false
        }
        val target = targetFor(direction) ?: return false
        fun page(view: EpubContentWebView, pageTarget: EpubPageTarget, request: Int) = EpubTurnPage(
            view, pageTarget, view.documentLifecycle.configurationGeneration,
            view.documentLifecycle.layoutRevision, request
        )
        val preparedTurn = EpubTurnContext(
            ++turnSerial, direction, reverseAxis,
            page(activeWebView, currentTarget, 0),
            page(targetView, target, if (direction == PageAnimationController.Direction.NEXT) nextGeneration else previousGeneration)
        )
        frozenReverseAxis = reverseAxis
        frozenPreviousTarget = previousTarget.takeIf { previousReady }
        frozenNextTarget = nextTarget.takeIf { nextReady }

        // Views stay at their measured geometry. Only the host canvas moves sheets.
        allWebViews().forEach { view ->
            view.animate().cancel()
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            view.elevation = 0f
            view.translationZ = 0f
            view.alpha = 1f
            view.visibility = View.VISIBLE
        }
        fun publishTurn() {
            turnContext = preparedTurn
            turnFrameCount = 0
            activeWebView.documentLifecycle.markPresented()
            targetView.documentLifecycle.markPresented()
            if (BuildConfig.DEBUG) Log.d(PERFORMANCE_LOG_TAG,
                "turn=$turnSerial dir=$direction rtl=$reverseAxis from=$currentTarget to=$target backend=" +
                    if (usesSnapshots) "snapshot" else "live")
        }

        if (!usesSnapshots) {
            publishTurn()
            hideAnimationPages()
            postInvalidateOnAnimation()
            return true
        }

        if (!reuseCurrentCurlSnapshot || currentBitmapTarget != currentTarget ||
            currentBitmap == null || currentBitmap?.isRecycled == true
        ) {
            refreshCurrentSnapshot(currentTarget)
        }
        if (currentBitmapTarget != currentTarget ||
            currentBitmap == null || currentBitmap?.isRecycled == true
        ) return false
        if (direction == PageAnimationController.Direction.PREV && previousPreparedBitmap == null) return false
        if (direction == PageAnimationController.Direction.NEXT && nextPreparedBitmap == null) return false

        previousPage.setSnapshotBitmap(previousPreparedBitmap)
        currentPage.setSnapshotBitmap(currentBitmap)
        nextPage.setSnapshotBitmap(nextPreparedBitmap)
        previousPage.alpha = 1f
        currentPage.alpha = 1f
        nextPage.alpha = 1f
        publishTurn()
        return true
    }

    private fun refreshCurrentSnapshot(target: EpubPageTarget) {
        val replacement = snapshot(activeWebView, currentBitmap) ?: return
        currentBitmap = replacement
        currentBitmapTarget = target
    }

    private fun snapshot(view: View, reusable: Bitmap?): Bitmap? {
        val startedNanos = SystemClock.elapsedRealtimeNanos()
        return try {
            snapshotNow(view, reusable)
        } finally {
            if (BuildConfig.DEBUG) {
                val durationMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000f
                Log.d(
                    PERFORMANCE_LOG_TAG,
                    "snapshot role=${(view as? EpubContentWebView)?.let(::roleOf)} " +
                        "transition=$transition durationMs=$durationMs duringAnimation=" +
                        (overlayActive || pagingGesture || controller.isRunning)
                )
            }
        }
    }

    private fun snapshotNow(view: View, reusable: Bitmap?): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || width <= 0 || height <= 0) return null
        if (transition == "curl" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        drawReaderBackground(bitmapCanvas)
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
                drawReaderBackground(recordingCanvas)
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
        if (source.actionMasked == MotionEvent.ACTION_MOVE && transition != "scroll") {
            val desired = epubPageDirectionForHorizontalDelta(
                source.x - touchStartX, frozenReverseAxis ?: reverseAxis, BUSY_TAP_MOVE_LIMIT_PX
            )
            val old = turnContext
            if (old != null && desired != PageAnimationController.Direction.NONE && desired != old.direction) {
                // Reversal starts a new sheet context at the flat origin.
                if (!canFlip(desired)) return
                old.finishOnce()
                if (!prepareAnimationPages(desired, reuseCurrentCurlSnapshot = true)) return
            }
        }
        // Controllers own physical-axis conversion, for both fixed and reflowable EPUBs.
        controller.onTouchEvent(source)
    }

    private fun resetLivePageViews() {
        activeWebView.translationX = 0f
        activeWebView.translationY = 0f
        activeWebView.scaleX = 1f
        activeWebView.scaleY = 1f
        activeWebView.translationZ = 0f
        activeWebView.alpha = 1f
        activeWebView.visibility = View.VISIBLE

        placeAdjacentWebViewAtIdle(previousWebView, PreloadSlot.PREVIOUS)
        previousWebView.translationZ = 0f
        previousWebView.visibility = View.VISIBLE

        placeAdjacentWebViewAtIdle(nextWebView, PreloadSlot.NEXT)
        nextWebView.translationZ = 0f
        nextWebView.visibility = View.VISIBLE

        preloadMask.visibility = View.VISIBLE
        preloadMask.bringToFront()
        activeWebView.bringToFront()
    }

    private fun placeAdjacentWebViewAtIdle(view: View, slot: PreloadSlot) {
        view.animate().cancel()
        // Pre-raster at the final viewport, hidden by the mask/current sheet.
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.elevation = 0f
        view.translationZ = 0f
        view.alpha = 1f
    }

    private fun resetAnimationOverlay() {
        clearWaitingGestureEvent()
        turnContext?.finishOnce()
        turnContext = null
        frozenReverseAxis = null
        overlayActive = false
        pagingGesture = false
        waitingGestureDirection = PageAnimationController.Direction.NONE
        waitingForTarget = null
        waitingForPreparedActivePage = false
        waitingCurlDirection = PageAnimationController.Direction.NONE
        frozenPreviousTarget = null
        frozenNextTarget = null
        resetLivePageViews()
        hideAnimationPages()
        invalidate()
    }

    private fun hideAnimationPages() {
        previousPage.setSnapshotBitmap(null)
        currentPage.setSnapshotBitmap(null)
        nextPage.setSnapshotBitmap(null)
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
        currentBitmapTarget = null
    }

    private fun matchParentParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
