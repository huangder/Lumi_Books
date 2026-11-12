package com.huangder.lumibooks.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.ui.components.GlassBitmapBackdrop
import com.huangder.lumibooks.ui.reader.engine.BookmarkPullGestureTracker
import com.huangder.lumibooks.ui.reader.engine.CurlFrameSource
import com.huangder.lumibooks.ui.reader.engine.CurlPageAnim
import com.huangder.lumibooks.ui.reader.engine.PageAnimationController
import com.huangder.lumibooks.ui.reader.engine.PageAnimationSurface
import com.huangder.lumibooks.ui.reader.engine.PageBitmapSource
import com.huangder.lumibooks.ui.reader.engine.RenderResourceLease
import com.huangder.lumibooks.ui.reader.engine.RenderResourcePool
import com.huangder.lumibooks.ui.reader.engine.ScrollPageAnim
import com.huangder.lumibooks.ui.reader.engine.SlidePageAnim
import com.huangder.lumibooks.ui.reader.engine.WavePageAnim
import com.huangder.lumibooks.ui.reader.engine.isSystemBackGestureStart
import com.huangder.lumibooks.ui.reader.engine.isSystemBackGestureSwipe
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.EpubPageSide
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import org.json.JSONObject
import kotlin.math.abs

/** One independently paginated leaf. The token changes for every positioning command. */
internal data class EpubSpreadLeafRequest(
    val target: EpubPageTarget,
    val destination: EpubNavigationDestination,
    val documentGeneration: Int
)

internal data class EpubSpreadState(
    val target: EpubSpreadTarget,
    val first: EpubSpreadPage,
    val second: EpubSpreadPage?,
    val reverseAxis: Boolean,
    val focused: EpubSpreadPage = first
)

/**
 * Three bounded spread slots, each containing at most two real WebViews. A slot
 * becomes presentable only after every leaf has crossed its own compositor and
 * draw fence. Navigation never exposes half of a newly loaded spread.
 */
internal class EpubSpreadTurnHost(
    context: Context,
    private val afterDetachedFrame: (View, () -> Unit) -> Unit = ::afterEpubDetachedFrame
) : FrameLayout(context) {
    var deferChapterConfiguration: (EpubContentWebView, Int, () -> Unit) -> Boolean = { _, _, _ -> false }
    var configureReader: (EpubContentWebView, EpubSpreadLeafRequest) -> Unit = { _, _ -> }
    var onInteraction: (EpubContentWebView, Int, String, JSONObject) -> Unit = { _, _, _, _ -> }
    var onSpreadChanged: (EpubSpreadState) -> Unit = {}
    var onFocusedPageChanged: (EpubSpreadPage) -> Unit = {}
    var onNavigationResult: (EpubNavigationResult) -> Unit = {}
    var onNavigationStage: (EpubNavigationRequest, EpubNavigationStage) -> Unit = { _, _ -> }
    var onFailure: (EpubRenderFailure) -> Unit = {}
    var onPageCountConfirmed: (Int, Int) -> Unit = { _, _ -> }
    var onViewportChanged: (Int, Int) -> Unit = { _, _ -> }
    var onCenterTap: () -> Unit = {}
    var onManualTurnStarted: () -> Unit = {}
    /** A forward turn was requested while the final spread is already visible. */
    var onBookEnd: () -> Unit = {}
    var onBookEndPull: (Float) -> Unit = {}
    var onBookEndPullEnd: (cancelled: Boolean) -> Unit = {}
    private var bookEndPulling = false
    var selectionMenuVisible = false
    var bookmarkPullEnabled = false
    var onBookmarkPullStart: (() -> Unit)? = null
    var onBookmarkPullProgress: ((Float, Boolean) -> Unit)? = null
    var onBookmarkPullFinished: ((Boolean) -> Unit)? = null
    var released = false
        private set
    private var releaseCompleted = false
    private val releaseCallbacks = mutableListOf<() -> Unit>()

    private inner class Leaf(var view: EpubContentWebView) {
        var request: EpubSpreadLeafRequest? = null
        var documentUrl: String? = null
        var configurationIdentity: Any? = null
        var configured = false
        var documentReady = false
        var semanticApplied = false
        var semanticInFlight = false
        var page: EpubSpreadPage? = null
        var retryCount = 0
        var fence = 0L
        var timeout: Runnable? = null
        var ready: (() -> Unit)? = null
    }

    private inner class SpreadSlot : FrameLayout(context), PageBitmapSource, CurlFrameSource {
        private var leafStorage: List<Leaf>? = null
        val leaves: List<Leaf> get() = leafStorage ?: List(2) { Leaf(EpubContentWebView(context)).also { leaf ->
                addView(leaf.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                attach(leaf)
            } }.also { leafStorage = it }
        val existingLeaves get() = leafStorage.orEmpty()
        fun releaseLeaves() {
            val old = existingLeaves.map { it.view }
            leafStorage = null
            val window = rootView
            old.forEach { view ->
                view.beginRelease()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "lumiNative")
                removeView(view)
            }
            if (old.isNotEmpty()) afterDetachedFrame(window) { old.forEach(EpubContentWebView::destroy) }
        }
        var target: EpubSpreadTarget? = null
        var snapshot: Bitmap? = null
        override val pageBitmap get() = snapshot
        override fun acquireCurlFrame(): RenderResourceLease<Bitmap>? = bitmapPool.acquire(snapshot)

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            if (fade == null || !spreadBackground.hasImage) spreadBackground.draw(canvas, width, height)
        }

        fun arrange(spread: EpubSpreadTarget) {
            target = spread
            leaves.forEachIndexed { index, leaf ->
                val needed = index == 0 || spread.second != null
                leaf.view.visibility = if (needed) VISIBLE else GONE
                val side = if (index == 0) spread.firstSide else when (spread.firstSide) {
                    EpubPageSide.RIGHT -> EpubPageSide.LEFT
                    else -> EpubPageSide.RIGHT
                }
                leaf.view.layoutParams = LayoutParams(
                    if (spread.fullWidth) LayoutParams.MATCH_PARENT else
                        ((this@EpubSpreadTurnHost.width - (16f * resources.displayMetrics.density).toInt()) / 2).coerceAtLeast(1),
                    LayoutParams.MATCH_PARENT
                ).apply { gravity = if (side == EpubPageSide.RIGHT) android.view.Gravity.RIGHT else android.view.Gravity.LEFT }
            }
        }

        fun isReady(): Boolean = target?.pages?.withIndex()?.all { (index, target) ->
            leaves[index].page?.target == target
        } == true

        fun invalidateSnapshot() { bitmapPool.retire(snapshot); snapshot = null }
    }

    private data class Preparation(
        val id: Long,
        val direction: Int,
        val request: EpubNavigationRequest?,
        val source: EpubSpreadTarget?,
        var anchor: EpubPageTarget,
        var destinationResolved: Boolean
    ) {
        var resolvedLocatorJson: String? = null
    }

    private val bitmapPool = RenderResourcePool<Bitmap> { if (!it.isRecycled) it.recycle() }
    private var pageBackgroundColor = Color.WHITE
    private val spreadBackground = ReaderSpreadBackground(context) {
        listOf(previous, current, next).forEach { it.invalidateSnapshot(); it.invalidate() }
        invalidate()
    }

    fun setReaderBackground(color: Int, path: String?, opacity: Float, blurDp: Float) {
        spreadBackground.configure(color, path, opacity, blurDp)
        allWebViews().forEach { it.setBackgroundColor(if (path != null) Color.TRANSPARENT else color) }
    }
    private var previous = SpreadSlot()
    private var current = SpreadSlot()
    private var next = SpreadSlot()
    private var session: BookRenderSession? = null
    private var planner: EpubSpreadPlanner? = null
    private var configurationIdentity: Any? = null
    private var reverseAxis = false
    private var requestedReverseAxis = false
    private var transition = "none"
    private var durationMillis = 260
    private var edgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    private var state: EpubSpreadState? = null
    private var sequence = 0
    private var preparationSequence = 0L
    private var preparation: Preparation? = null
    private var preparationSlot: SpreadSlot? = null
    private var lastOperationId: Long? = null
    private var pendingTurn = 0
    private val preloadDirections = ArrayDeque<Int>()
    var backgroundPreparationEnabled = true
        set(value) {
            if (field == value) return
            field = value
            if (value) post { drainPreloads() }
            else {
                // A retained alternate direction owns just its current spread.
                cancelPreparation(replaced = false)
                abortAnimation()
                listOf(previous, next).forEach { clearSlot(it); it.releaseLeaves() }
            }
        }
    private var controller: PageAnimationController? = null
    private var animationCommit: (() -> Unit)? = null
    private var fade: ValueAnimator? = null
    private var leafCapture: Bitmap? = null
    private val bookmarkPull = BookmarkPullGestureTracker()
    private var pullingBookmark = false
    private var footnoteTouchStream = false
    private var outsideFootnoteOwner: EpubContentWebView? = null
    private var touchX = 0f
    private var touchY = 0f
    private var lastTouchX = 0f
    private var gestureClaimed = false
    private var waitingGesture: MotionEvent? = null
    private var gestureStartedAt = 0L
    private var systemBackCandidate = false
    private var systemBackSuppressed = false

    private fun trace(event: String, detail: String) {
        if (BuildConfig.DEBUG) Log.d("EpubSpread", "$event $detail")
    }

    init {
        clipChildren = true
        setWillNotDraw(false)
        listOf(previous, next, current).forEach { addView(it, LayoutParams(-1, -1)) }
        resetSlots()
    }

    fun allWebViews(): List<EpubContentWebView> =
        if (released) emptyList() else listOf(previous, current, next).flatMap { it.existingLeaves.map(Leaf::view) }

    fun visibleWebViews(): List<EpubContentWebView> = if (released) emptyList() else
        current.target?.pages?.indices?.map { current.leaves[it].view }.orEmpty()

    fun readyView(target: EpubPageTarget): EpubContentWebView? =
        if (released) null else listOf(current, previous, next).asSequence().flatMap { it.existingLeaves.asSequence() }
            .firstOrNull { it.page?.target == target }?.view

    fun evaluateVisible(script: String) { visibleWebViews().forEach { it.evaluateJavascript(script, null) } }

    fun update(
        session: BookRenderSession,
        configurationIdentity: Any,
        request: EpubNavigationRequest?,
        transition: String,
        durationMillis: Int,
        reverseAxis: Boolean,
        edgeTapMode: ReaderEdgeTapMode,
        backgroundColor: Int
    ) {
        if (released) return
        val sessionChanged = this.session !== session
        val layoutChanged = sessionChanged || this.configurationIdentity != configurationIdentity || requestedReverseAxis != reverseAxis
        val pendingRequest = preparation?.request
        val restartingRequest = if (layoutChanged && (state == null || pendingRequest != null)) {
            request ?: pendingRequest
        } else null
        this.transition = transition
        this.durationMillis = durationMillis.coerceIn(1, 1000)
        this.edgeTapMode = edgeTapMode
        this.pageBackgroundColor = backgroundColor
        setBackgroundColor(backgroundColor)
        if (sessionChanged || requestedReverseAxis != reverseAxis) this.reverseAxis = reverseAxis
        requestedReverseAxis = reverseAxis
        this.session = session
        this.configurationIdentity = configurationIdentity
        if (layoutChanged) {
            // A calibrated viewport replaces host work, not the navigation itself.
            // Reporting cancellation for this same ID would clear the caller's request.
            cancelPreparation(replaced = true,
                notifyCancellation = pendingRequest?.operationId != restartingRequest?.operationId)
            abortAnimation()
            planner = EpubSpreadPlanner(session.chapterCount, session::pagePresentation, this.reverseAxis, width >= height)
            listOf(previous, next).forEach(::clearSlot)
            if (sessionChanged) {
                clearSlot(current)
                state = null
                lastOperationId = null
            }
        }
        if (request != null && request.operationId != lastOperationId) {
            lastOperationId = request.operationId
            jump(request)
        } else if (restartingRequest != null) {
            jump(restartingRequest)
        } else if (layoutChanged && session.chapterCount > 0) {
            val focused = state?.focused
            jump(EpubNavigationRequest(
                operationId = -(++preparationSequence), origin = EpubNavigationOrigin.PROGRESS,
                sourceChapterIndex = focused?.target?.chapterIndex ?: 0,
                sourcePageIndex = focused?.target?.pageIndex ?: 0,
                targetChapterIndex = focused?.target?.chapterIndex ?: 0,
                destination = focused?.locatorJson?.let(EpubNavigationDestination::Locator)
                    ?: EpubNavigationDestination.Page(focused?.target?.pageIndex ?: 0),
                showLoadingPage = false
            ))
        }
        invalidate()
    }

    fun jump(request: EpubNavigationRequest) {
        val book = session ?: return
        if (released) return
        if (request.targetChapterIndex !in 0 until book.chapterCount) {
            onNavigationResult(EpubNavigationResult.Failed(request.operationId, EpubNavigationFailureReason.INVALID_TARGET))
            return
        }
        cancelPreparation(replaced = true)
        abortAnimation()
        preloadDirections.clear()
        pendingTurn = 0
        val destination = request.destination
        trace("jump", "op=${request.operationId} chapter=${request.targetChapterIndex} destination=${destination.javaClass.simpleName} size=${width}x$height")
        val visiblePage = (destination as? EpubNavigationDestination.Page)?.takeIf { it.chapterFraction == null }
            ?.let { page -> listOfNotNull(state?.first, state?.second).firstOrNull {
                it.target == EpubPageTarget(request.targetChapterIndex, page.index)
            } }
        if (visiblePage != null && currentConfigurationValid()) {
            commitVisibleFocus(request, visiblePage)
            return
        }
        val job = Preparation(++preparationSequence, 0, request, state?.target,
            EpubPageTarget(request.targetChapterIndex, destination.requestedPageIndex()),
            destination is EpubNavigationDestination.ChapterStart ||
                destination is EpubNavigationDestination.Page && destination.chapterFraction == null)
        preparation = job
        preparationSlot = next
        onNavigationStage(request, EpubNavigationStage.STARTING)
        advance(job)
    }

    /** A queued input is one spread, never one leaf. */
    fun turn(direction: Int): Boolean {
        val spread = state
        if (released || direction == 0 || spread == null || selectionMenuVisible) return false
        val sign = if (direction > 0) 1 else -1
        val last = spread.second ?: spread.first
        if (sign < 0 && spread.first.target == EpubPageTarget(0, 0) ||
            sign > 0 && last.target.chapterIndex == (session?.chapterCount ?: 0) - 1 && last.target.pageIndex + 1 >= last.pageCount) {
            if (sign > 0 && animationCommit == null) onBookEnd()
            return false
        }
        if (animationCommit != null) { pendingTurn = sign; return true }
        val slot = if (sign > 0) next else previous
        if (slot.isReady()) {
            cancelPreparation(replaced = false)
            animateCommit(slot, sign, null)
            return true
        }
        pendingTurn = sign
        if (preparation?.direction == sign) return true
        cancelPreparation(replaced = false)
        prepareAdjacent(sign)
        return true
    }

    fun focus(view: EpubContentWebView, locatorJson: String? = null) {
        val old = state ?: return
        val page = current.leaves.firstOrNull { it.view === view }?.page ?: return
        val focused = if (locatorJson != null) page.copy(locatorJson = locatorJson) else page
        state = old.copy(focused = focused)
        if (old.focused != focused) onFocusedPageChanged(focused)
    }

    private fun prepareAdjacent(direction: Int) {
        val active = state ?: return
        val slot = if (direction > 0) next else previous
        val job = Preparation(++preparationSequence, direction, null, active.target, active.target.first, true)
        preparation = job
        preparationSlot = slot
        advance(job)
    }

    private fun advance(job: Preparation) {
        if (released || preparation !== job) return
        val plan = planner ?: return
        val slot = preparationSlot ?: return
        val book = session ?: return
        // Resolve semantic jumps in one spare leaf before asking the planner to pair it.
        if (!job.destinationResolved) {
            val destination = job.request?.destination ?: return
            prepareProbe(slot, job.anchor, destination) { page ->
                if (preparation !== job) return@prepareProbe
                job.anchor = page.target
                job.destinationResolved = true
                job.resolvedLocatorJson = (destination as? EpubNavigationDestination.Locator)?.json ?: page.locatorJson
                val visible = listOfNotNull(state?.first, state?.second).firstOrNull { it.target == page.target }
                if (visible != null && job.request != null && currentConfigurationValid()) {
                    clearSlot(slot)
                    preparation = null
                    preparationSlot = null
                    commitVisibleFocus(job.request, visible.copy(locatorJson = job.resolvedLocatorJson ?: page.locatorJson ?: visible.locatorJson))
                    return@prepareProbe
                }
                advance(job)
            }
            return
        }
        val result = when {
            job.direction > 0 -> plan.planNext(job.source ?: return)
            job.direction < 0 -> plan.planPrevious(job.source ?: return)
            else -> plan.planAt(job.anchor)
        }
        when (result) {
            EpubSpreadPlan.Boundary -> {
                preparation = null
                preparationSlot = null
                if (pendingTurn == job.direction) pendingTurn = 0
                drainPreloads()
            }
            is EpubSpreadPlan.NeedsPageCount -> prepareProbe(slot,
                EpubPageTarget(result.chapterIndex, 0), EpubNavigationDestination.Page(0)) {
                if (preparation === job) advance(job)
            }
            is EpubSpreadPlan.Ready -> {
                val target = result.spread
                slot.arrange(target)
                slot.invalidateSnapshot()
                var remaining = target.pages.size
                target.pages.forEachIndexed { index, page ->
                    prepareLeaf(slot.leaves[index], page, EpubNavigationDestination.Page(page.pageIndex)) {
                        if (preparation !== job || slot.target != target) return@prepareLeaf
                        if (--remaining == 0) {
                            if (!slot.isReady()) {
                                // Fonts/media may change a count between planning and the
                                // final draw. Replan with the confirmed counts before commit.
                                post { if (preparation === job) advance(job) }
                                return@prepareLeaf
                            }
                            preparation = null
                            preparationSlot = null
                            if (job.request != null || pendingTurn == job.direction) {
                                if (pendingTurn == job.direction) pendingTurn = 0
                                animateCommit(slot, job.direction, job.request, job.anchor, job.resolvedLocatorJson)
                            } else if (gestureClaimed && waitingGesture != null) {
                                resumeWaitingGesture()
                            } else drainPreloads()
                        }
                    }
                }
            }
        }
    }

    private fun commitVisibleFocus(request: EpubNavigationRequest, page: EpubSpreadPage) {
        val old = state ?: return
        state = old.copy(focused = page)
        if (old.focused != page) onFocusedPageChanged(page)
        onNavigationResult(EpubNavigationResult.Committed(request.operationId,
            page.target.chapterIndex, page.target.pageIndex, page.pageCount, page.locatorJson))
        preloadDirections.clear()
        preloadDirections.add(-1)
        preloadDirections.add(1)
        drainPreloads()
    }

    private fun currentConfigurationValid(): Boolean = current.target?.pages?.indices?.all {
        current.leaves[it].configurationIdentity == configurationIdentity
    } == true

    private fun prepareProbe(
        slot: SpreadSlot, target: EpubPageTarget, destination: EpubNavigationDestination,
        complete: (EpubSpreadPage) -> Unit
    ) {
        val info = session?.pagePresentation(target.chapterIndex) ?: return
        val full = info.isCover && target.pageIndex == 0 || info.renditionLayout == EpubRenditionLayout.PRE_PAGINATED &&
            (!info.renditionSpread.allowsSpread(width >= height) || info.preferredSide == EpubPageSide.CENTER ||
                info.preferredSide == null && info.viewportWidth != null && info.viewportHeight != null && info.viewportWidth > info.viewportHeight)
        slot.arrange(EpubSpreadTarget(target, fullWidth = full))
        prepareLeaf(slot.leaves[0], target, destination) { slot.leaves[0].page?.let(complete) }
    }

    private fun prepareLeaf(leaf: Leaf, target: EpubPageTarget, destination: EpubNavigationDestination, ready: () -> Unit) {
        val book = session ?: return
        leaf.timeout?.let(::removeCallbacks)
        leaf.fence++
        leaf.page = null
        leaf.ready = ready
        leaf.retryCount = 0
        leaf.request = EpubSpreadLeafRequest(target, destination, ++sequence)
        leaf.configured = false
        leaf.semanticApplied = false
        leaf.semanticInFlight = false
        val url = book.chapterUrl(target.chapterIndex)
        val reload = leaf.documentUrl != url || leaf.configurationIdentity != configurationIdentity
        trace("leaf", "wv=${leaf.view.traceId} target=${target.traceLabel()} reload=$reload size=${leaf.view.width}x${leaf.view.height}")
        leaf.documentUrl = url
        leaf.configurationIdentity = configurationIdentity
        armTimeout(leaf)
        if (reload) {
            leaf.documentReady = false
            preparation?.request?.let { onNavigationStage(it, EpubNavigationStage.LOADING_DOCUMENT) }
            leaf.view.loadUrl(url)
        } else if (leaf.documentReady) leaf.view.post { configure(leaf) }
        resetSlots()
    }

    private fun configure(leaf: Leaf) {
        val request = leaf.request ?: return
        if (released || leaf.configured || !leaf.documentReady || leaf.view.released || leaf.view.width <= 0 || leaf.view.height <= 0) return
        val expectedWidth = leaf.view.layoutParams.width.let { if (it == LayoutParams.MATCH_PARENT) width else it }
        if (leaf.view.width != expectedWidth || leaf.view.height != height) return
        val fence = leaf.fence
        if (deferChapterConfiguration(leaf.view, request.target.chapterIndex) {
                if (leaf.request === request && leaf.fence == fence) configure(leaf)
            }
        ) return
        leaf.configured = true
        preparation?.request?.let { onNavigationStage(it, EpubNavigationStage.CONFIGURING) }
        configureReader(leaf.view, request)
    }

    private fun armTimeout(leaf: Leaf) {
        val request = leaf.request ?: return
        val timeout = Runnable {
            if (!released && leaf.request === request && leaf.page == null) failLeaf(leaf, EpubInitialLoadFailureReason.PAGE_READY_TIMEOUT)
        }
        leaf.timeout = timeout
        postDelayed(timeout, EPUB_NAVIGATION_TIMEOUT_MS)
    }

    private fun failLeaf(leaf: Leaf, reason: EpubInitialLoadFailureReason) {
        if (released) return
        trace("failure", "wv=${leaf.view.traceId} target=${leaf.request?.target.traceLabel()} reason=$reason retry=${leaf.retryCount} active=${slotOf(leaf.view) === current}")
        if (reason == EpubInitialLoadFailureReason.RENDERER_GONE) {
            val active = slotOf(leaf.view) === current
            val job = preparation
            val preparing = preparationSlot?.leaves?.contains(leaf) == true
            val pendingReady = leaf.ready
            val pendingRequest = leaf.request
            val pendingUrl = leaf.documentUrl
            val position = state?.focused?.let { EpubReadingPosition(it.target.chapterIndex, it.target.pageIndex, it.pageCount, it.locatorJson) }
            if (active) {
                cancelPreparation(replaced = false)
                abortAnimation()
                pendingTurn = 0
            }
            leaf.fence++
            leaf.ready = null
            leaf.page = null
            leaf.timeout?.let(::removeCallbacks)
            leaf.timeout = null
            replaceDeadView(leaf)
            if (!active && preparing && pendingReady != null && pendingRequest != null && pendingUrl != null && leaf.retryCount++ == 0) {
                leaf.ready = pendingReady
                leaf.documentUrl = pendingUrl
                leaf.configurationIdentity = configurationIdentity
                leaf.view.loadUrl(pendingUrl)
                armTimeout(leaf)
                return
            }
            if (active) onFailure(EpubRenderFailure(reason, leaf.request?.target?.chapterIndex ?: 0,
                position, hasVisiblePage = false))
            else if (preparing) {
                val requested = job?.request != null || pendingTurn != 0
                cancelPreparation(replaced = false, notifyCancellation = false)
                if (requested) {
                    pendingTurn = 0
                    job?.request?.let { onNavigationResult(EpubNavigationResult.Failed(it.operationId, EpubNavigationFailureReason.RENDERER_GONE)) }
                    onFailure(EpubRenderFailure(reason, leaf.request?.target?.chapterIndex ?: 0, position, state != null))
                }
            }
            return
        }
        if (leaf.ready == null) {
            if (slotOf(leaf.view) === current) {
                val position = state?.focused?.let { EpubReadingPosition(it.target.chapterIndex, it.target.pageIndex, it.pageCount, it.locatorJson) }
                onFailure(EpubRenderFailure(reason, leaf.request?.target?.chapterIndex ?: 0, position, state != null))
            }
            return
        }
        leaf.timeout?.let(::removeCallbacks)
        leaf.timeout = null
        if (leaf.retryCount++ == 0 && reason != EpubInitialLoadFailureReason.RENDERER_GONE) {
            leaf.fence++
            leaf.configured = false
            leaf.documentReady = false
            leaf.semanticApplied = false
            leaf.semanticInFlight = false
            leaf.view.loadUrl(leaf.documentUrl ?: return)
            armTimeout(leaf)
            return
        }
        val job = preparation
        val requested = job?.request != null || pendingTurn == job?.direction
        cancelPreparation(replaced = false, notifyCancellation = false)
        pendingTurn = 0
        if (requested || state == null) {
            job?.request?.let { onNavigationResult(EpubNavigationResult.Failed(it.operationId,
                if (reason == EpubInitialLoadFailureReason.PAGE_READY_TIMEOUT) EpubNavigationFailureReason.TIMEOUT else EpubNavigationFailureReason.DOCUMENT_ERROR)) }
            val position = state?.focused?.let { EpubReadingPosition(it.target.chapterIndex, it.target.pageIndex, it.pageCount, it.locatorJson) }
            onFailure(EpubRenderFailure(reason, leaf.request?.target?.chapterIndex ?: 0, position, state != null))
        }
    }

    private fun attach(leaf: Leaf) {
        val view = leaf.view
        configureEpubWebViewSettings(view)
        view.onAppearanceChanged = { slotOf(view)?.invalidateSnapshot() }
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!leaf.configured && leaf.documentReady && leaf.view === view) view.post { configure(leaf) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, "lumiNative", setOf("https://${BookRenderSession.ASSET_DOMAIN}"),
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(sourceView: WebView, message: WebMessageCompat, sourceOrigin: Uri,
                        isMainFrame: Boolean, replyProxy: JavaScriptReplyProxy) {
                        if (released || sourceView !== view || leaf.view !== view || !isMainFrame || sourceOrigin.scheme != "https" ||
                            sourceOrigin.host != BookRenderSession.ASSET_DOMAIN || sourceOrigin.port != -1) return
                        val root = runCatching { JSONObject(message.data ?: return) }.getOrNull() ?: return
                        val document = root.optString("documentHref")
                        if (!epubDocumentMessageMatches(document, leaf.documentUrl ?: return)) return
                        val chapter = session?.chapterIndexForUrl(document.substringBefore('#')) ?: return
                        if (chapter != leaf.request?.target?.chapterIndex) return
                        handleMessage(leaf, chapter, root.optString("type"), root.optJSONObject("payload") ?: JSONObject())
                    }
                })
        } else view.settings.javaScriptEnabled = false
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (released || leaf.view !== view || !epubDocumentMessageMatches(url, leaf.documentUrl ?: return)) return
                leaf.view.documentLifecycle.beginDocument()
                leaf.configured = false
                leaf.documentReady = false
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (released || leaf.view !== view || !epubDocumentMessageMatches(url, leaf.documentUrl ?: return)) return
                leaf.documentReady = true
                configure(leaf)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                session?.assetLoader?.shouldInterceptRequest(request.url) ?: blockedResponse()

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (leaf.view !== view) return true
                val page = leaf.page
                if (!released && slotOf(leaf.view) === current && page != null && request.isForMainFrame) {
                    focus(leaf.view)
                    onInteraction(leaf.view, page.target.chapterIndex, "link", JSONObject().put("href", request.url.toString()))
                }
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (leaf.view !== view) return
                if (request.isForMainFrame && epubDocumentMessageMatches(request.url.toString(), leaf.documentUrl ?: return))
                    failLeaf(leaf, EpubInitialLoadFailureReason.MAIN_FRAME_ERROR)
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (leaf.view !== view) return
                if (request.isForMainFrame && epubDocumentMessageMatches(request.url.toString(), leaf.documentUrl ?: return))
                    failLeaf(leaf, EpubInitialLoadFailureReason.HTTP_ERROR)
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (leaf.view !== view || released) return true
                failLeaf(leaf, EpubInitialLoadFailureReason.RENDERER_GONE)
                return true
            }
        }
        if (!view.settings.javaScriptEnabled) view.post {
            if (!released && leaf.view === view && leaf.ready != null) failLeaf(leaf, EpubInitialLoadFailureReason.READER_SCRIPT_MISSING)
        }
    }

    private fun replaceDeadView(leaf: Leaf) {
        val dead = leaf.view
        val parent = dead.parent as? FrameLayout
        val params = dead.layoutParams
        val childIndex = parent?.indexOfChild(dead) ?: -1
        dead.beginRelease()
        parent?.removeView(dead)
        leaf.view = EpubContentWebView(context)
        leaf.documentReady = false
        leaf.documentUrl = null
        leaf.configured = false
        leaf.configurationIdentity = null
        attach(leaf)
        parent?.addView(leaf.view, childIndex.coerceAtLeast(0), params)
        afterDetachedFrame(rootView) { dead.destroy() }
    }

    private fun handleMessage(leaf: Leaf, chapter: Int, type: String, payload: JSONObject) {
        if (type == "footnotePopover") {
            leaf.view.footnotePopoverVisible = payload.optBoolean("visible")
            return
        }
        if (type == "ready" && !leaf.configured) {
            leaf.documentReady = true
            configure(leaf)
            return
        }
        if (type in setOf("ready", "page", "layoutStable", "layoutPending", "pagePrepared")) {
            if (leaf.configurationIdentity != configurationIdentity) return
            val lifecycle = leaf.view.documentLifecycle
            val generation = payload.optLong("configurationGeneration", -1)
            val revision = payload.optLong("layoutRevision", -1)
            if (!lifecycle.observeLayout(generation, revision, type == "layoutStable")) return
            if (type == "layoutPending") {
                leaf.fence++
                leaf.page = null
                slotOf(leaf.view)?.invalidateSnapshot()
                return
            }
            val request = leaf.request ?: return
            val count = payload.optInt("pageCount", 0)
            val page = payload.optInt("pageIndex", -1)
            if (count <= 0 || page !in 0 until count || !leaf.configured || !lifecycle.isStable(generation, revision)) return
            val semantic = request.destination is EpubNavigationDestination.Locator || request.destination is EpubNavigationDestination.Fragment ||
                (request.destination as? EpubNavigationDestination.Page)?.chapterFraction != null
            if (leaf.semanticInFlight) return
            if (semantic && !leaf.semanticApplied) {
                leaf.semanticApplied = true
                leaf.semanticInFlight = true
                leaf.fence++
                val command = when (val destination = request.destination) {
                    is EpubNavigationDestination.Locator -> {
                        val locator = runCatching { JSONObject(destination.json) }.getOrNull()
                        if (locator == null) {
                            leaf.semanticInFlight = false
                            failLeaf(leaf, EpubInitialLoadFailureReason.SCRIPT_EXECUTION_ERROR)
                            return
                        }
                        "restore($locator)"
                    }
                    is EpubNavigationDestination.Fragment -> "goToFragment(${JSONObject.quote(destination.value)})"
                    is EpubNavigationDestination.Page -> "goToProgression(${destination.chapterFraction?.coerceIn(0f, 1f) ?: 0f})"
                    else -> return
                }
                leaf.view.evaluateJavascript("(function(){if(!window.LumiReader)return false;var found=window.LumiReader.$command;return found!==false;})()") {
                    if (leaf.request === request) {
                        leaf.semanticInFlight = false
                        if (it == "false") failLeaf(leaf, EpubInitialLoadFailureReason.SCRIPT_EXECUTION_ERROR)
                        else leaf.view.evaluateJavascript("window.LumiReader&&window.LumiReader.reportLayoutStatus();", null)
                    }
                }
                return
            }
            if (!semantic && !epubPageTargetMatchesPayload(request.target, page, count)) return
            val locator = payload.optJSONObject("locator")?.apply { put("href", session?.chapterHref(chapter)) }?.toString()
            val readyPage = EpubSpreadPage(EpubPageTarget(chapter, page), count, locator)
            val fence = ++leaf.fence
            leaf.view.postVisualStateCallback(fence, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!acceptsFence(leaf, request, fence, generation, revision)) return
                    leaf.view.runAfterNextDraw {
                        if (!acceptsFence(leaf, request, fence, generation, revision)) return@runAfterNextDraw
                        leaf.page = readyPage
                        leaf.timeout?.let(::removeCallbacks)
                        leaf.timeout = null
                        planner?.updatePage(readyPage)
                        if (payload.has("reverseAxis") && (state == null || slotOf(leaf.view) === current || preparation?.direction == 0)) {
                            val resolvedAxis = payload.optBoolean("reverseAxis", reverseAxis)
                            if (resolvedAxis != reverseAxis) {
                                reverseAxis = resolvedAxis
                                planner?.setReverse(resolvedAxis)
                            }
                        }
                        onPageCountConfirmed(chapter, count)
                        val complete = leaf.ready
                        leaf.ready = null
                        if (complete != null) complete() else if (slotOf(leaf.view) === current && current.isReady()) {
                            val active = state
                            if (active != null) {
                                val first = current.leaves[0].page!!
                                val second = current.target?.second?.let { current.leaves[1].page!! }
                                val sameFocus = listOfNotNull(first, second).firstOrNull { it.target == active.focused.target } ?: first
                                val focused = sameFocus.copy(locatorJson = active.focused.locatorJson ?: sameFocus.locatorJson)
                                val refreshed = active.copy(first = first, second = second, reverseAxis = reverseAxis, focused = focused)
                                if (refreshed != active) {
                                    state = refreshed
                                    if (first.pageCount != active.first.pageCount || second?.pageCount != active.second?.pageCount) {
                                        cancelPreparation(replaced = false)
                                        clearSlot(previous)
                                        clearSlot(next)
                                        preloadDirections.clear()
                                        preloadDirections.add(-1)
                                        preloadDirections.add(1)
                                        post { drainPreloads() }
                                    }
                                    onSpreadChanged(refreshed)
                                }
                            }
                        }
                    }
                    leaf.view.invalidate()
                }
            })
            preparation?.request?.let { onNavigationStage(it, EpubNavigationStage.WAITING_FOR_FRAME) }
            return
        }
        if (slotOf(leaf.view) !== current || leaf.page == null) return
        when (type) {
            "tap" -> {
                if (selectionMenuVisible) { onCenterTap(); return }
                when {
                    lastTouchX < width * .3f -> tapEdge(edgeTapMode.leftAction)
                    lastTouchX > width * .7f -> tapEdge(edgeTapMode.rightAction)
                    else -> onCenterTap()
                }
            }
            "chapterTurn" -> turn(payload.optInt("direction", 0))
            else -> {
                if (type in setOf("selection", "image", "imageLongPress", "link", "annotationSelection")) focus(leaf.view)
                onInteraction(leaf.view, chapter, type, payload)
            }
        }
    }

    private fun acceptsFence(leaf: Leaf, request: EpubSpreadLeafRequest, fence: Long, generation: Long, revision: Long): Boolean =
        !released && leaf.configurationIdentity == configurationIdentity && leaf.request === request &&
            leaf.fence == fence && leaf.view.documentLifecycle.isStable(generation, revision)

    private fun tapEdge(action: ReaderEdgeTapAction) {
        val direction = if (action == ReaderEdgeTapAction.NEXT_PAGE) 1 else -1
        onManualTurnStarted()
        turn(direction)
    }

    private fun spreadCommit(slot: SpreadSlot, direction: Int, request: EpubNavigationRequest?,
        focused: EpubPageTarget? = null, focusedLocatorJson: String? = null): () -> Unit = {
            abortAnimation()
            val oldCurrent = current
            if (slot === next) {
                current = next; next = previous; previous = oldCurrent
                clearSlot(next)
            } else {
                current = previous; previous = next; next = oldCurrent
                clearSlot(previous)
            }
            // A jump does not turn the old unrelated spread into an adjacent preload.
            if (direction == 0) { clearSlot(previous); clearSlot(next) }
            val target = current.target!!
            val first = current.leaves[0].page!!
            val second = target.second?.let { current.leaves[1].page!! }
            val physicalFocus = listOfNotNull(first, second).firstOrNull { it.target == focused } ?: first
            val focusPage = if (focusedLocatorJson != null) physicalFocus.copy(locatorJson = focusedLocatorJson) else physicalFocus
            state = EpubSpreadState(target, first, second, reverseAxis, focusPage)
            trace("commit", "op=${request?.operationId} first=${first.target.traceLabel()} second=${second?.target.traceLabel()} counts=${first.pageCount}/${second?.pageCount} reverse=$reverseAxis full=${target.fullWidth} size=${width}x$height")
            resetSlots()
            onSpreadChanged(state!!)
            request?.let { onNavigationResult(EpubNavigationResult.Committed(it.operationId,
                focusPage.target.chapterIndex, focusPage.target.pageIndex, focusPage.pageCount, focusPage.locatorJson)) }
            preloadDirections.clear()
            preloadDirections.add(-1)
            preloadDirections.add(1)
            val queued = pendingTurn
            pendingTurn = 0
            if (queued != 0) post { turn(queued) } else post { drainPreloads() }
    }

    private fun animateCommit(slot: SpreadSlot, direction: Int, request: EpubNavigationRequest?, focused: EpubPageTarget? = null,
        focusedLocatorJson: String? = null) {
        if (!slot.isReady()) return
        val commit = spreadCommit(slot, direction, request, focused, focusedLocatorJson)
        if (direction == 0 || transition == "none" || state == null || width <= 0 || height <= 0) { commit(); return }
        animationCommit = commit
        request?.let { onNavigationStage(it, EpubNavigationStage.ANIMATING) }
        if (transition == "fade") {
            listOf(previous, next).filter { it !== slot }.forEach { it.alpha = 0f }
            slot.bringToFront()
            slot.alpha = 0f
            fade = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMillis.toLong()
                addUpdateListener { slot.alpha = it.animatedValue as Float; current.alpha = 1f - slot.alpha }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { animationCommit?.invoke() } })
                start()
            }
            return
        }
        if (transition == "curl" || transition == "wave") {
            if (!captureSlot(current) || !captureSlot(slot)) { commit(); return }
        }
        controller = createAnimation().also { animation ->
            animation.onCanFlip = { slot.isReady() }
            animation.onAnimationComplete = { animationCommit?.invoke() }
            val dir = if (direction > 0) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
            when (animation) {
                is CurlPageAnim -> if (!animation.startFromTap(dir)) commit()
                is WavePageAnim -> animation.startFromTap(dir)
                is ScrollPageAnim -> animation.startFromTap(dir)
                is SlidePageAnim -> animation.startFromTap(dir)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun createAnimation(): PageAnimationController {
        val surface = PageAnimationSurface(this, previous, current, next, { pageBackgroundColor }, { reverseAxis },
            snapTranslationsToPixels = true, animatePageViewsDirectly = true,
            directPageRenderer = { canvas, view ->
                val bitmap = (view as? PageBitmapSource)?.pageBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), null)
                    true
                } else false
            })
        return when (transition) {
            "curl" -> CurlPageAnim(surface, baseDurationMs = durationMillis)
            "wave" -> WavePageAnim(surface, durationMillis)
            "scroll" -> ScrollPageAnim(surface, durationMillis)
            "fade" -> EpubSpreadFadeAnimation(surface, durationMillis)
            else -> SlidePageAnim(surface, durationMillis)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fade != null && spreadBackground.hasImage) spreadBackground.draw(canvas, width, height)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val animation = controller
        if (animation != null && animation.drawsDirectlyOnCanvas && animationCommit != null) {
            canvas.drawColor(pageBackgroundColor)
            animation.onDraw(canvas)
        } else {
            animation?.onDraw(canvas)
            super.dispatchDraw(canvas)
            when (animation) {
                is SlidePageAnim -> animation.drawOverlay(canvas)
                is ScrollPageAnim -> animation.drawOverlay(canvas)
            }
        }
    }

    override fun computeScroll() {
        val animation = controller ?: return
        if (animation.computeScroll()) postInvalidateOnAnimation()
        if (controller === animation && !animation.isRunning && !animation.isDragging &&
            !animation.isCompletingTurn && !gestureClaimed) {
            abortAnimation()
            drainPreloads()
        }
    }

    private fun abortAnimation() {
        gestureClaimed = false
        clearWaitingGesture()
        animationCommit = null
        fade?.removeAllListeners()
        fade?.cancel()
        fade = null
        controller?.onAnimationComplete = null
        controller?.abortAnim()
        (controller as? CurlPageAnim)?.destroy()
        controller = null
        resetSlots()
    }

    private fun drainPreloads() {
        if (!backgroundPreparationEnabled) {
            // Hidden locator updates can commit another chapter without changing
            // the mode flag. Drop the displaced pair after every such commit too.
            if (preparation == null && animationCommit == null && !gestureClaimed) {
                listOf(previous, next).forEach { clearSlot(it); it.releaseLeaves() }
            }
            return
        }
        if (released || preparation != null || animationCommit != null || gestureClaimed || state == null) return
        while (preloadDirections.isNotEmpty()) {
            val direction = preloadDirections.removeFirst()
            val slot = if (direction > 0) next else previous
            if (!slot.isReady()) { prepareAdjacent(direction); return }
        }
    }

    private fun cancelPreparation(replaced: Boolean, notifyCancellation: Boolean = true) {
        val old = preparation
        preparation = null
        preparationSlot = null
        listOf(previous, next).forEach { slot ->
            slot.existingLeaves.forEach { leaf ->
                if (leaf.ready != null) {
                    leaf.fence++
                    leaf.ready = null
                    leaf.timeout?.let(::removeCallbacks)
                    leaf.timeout = null
                    leaf.page = null
                }
            }
        }
        if (notifyCancellation) old?.request?.let { onNavigationResult(EpubNavigationResult.Cancelled(it.operationId, replaced)) }
    }

    private fun clearSlot(slot: SpreadSlot) {
        slot.target = null
        slot.invalidateSnapshot()
        slot.existingLeaves.forEach { leaf ->
            leaf.fence++
            leaf.timeout?.let(::removeCallbacks)
            leaf.timeout = null
            leaf.ready = null
            leaf.page = null
            leaf.request = null
        }
    }

    private fun resetSlots() {
        if (controller != null) return
        listOf(previous, next, current).forEach { slot ->
            slot.visibility = VISIBLE
            slot.alpha = 1f
            slot.translationX = 0f
            slot.translationY = 0f
            slot.translationZ = 0f
        }
        // Prepared WebViews stay drawable behind the current opaque spread.
        current.bringToFront()
        invalidate()
    }

    private fun slotOf(view: EpubContentWebView): SpreadSlot? =
        listOf(previous, current, next).firstOrNull { slot -> slot.existingLeaves.any { it.view === view } }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (released) return true
        lastTouchX = event.x
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            outsideFootnoteOwner = visibleWebViews().firstOrNull { it.footnotePopoverVisible }?.takeUnless { view ->
                val bounds = Rect()
                view.getDrawingRect(bounds)
                offsetDescendantRectToMyCoords(view, bounds)
                event.x >= bounds.left && event.x < bounds.right &&
                    event.y >= bounds.top && event.y < bounds.bottom
            }
        }
        outsideFootnoteOwner?.let { owner ->
            // The other leaf has its own document and would interpret this stroke as reading input.
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    outsideFootnoteOwner = null
                    if (owner.footnotePopoverVisible) {
                        owner.evaluateJavascript("if (window.LumiReader) window.LumiReader.dismissFootnote();", null)
                    }
                }
                MotionEvent.ACTION_CANCEL -> outsideFootnoteOwner = null
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            footnoteTouchStream = visibleWebViews().any { it.footnotePopoverVisible }
        }
        if (footnoteTouchStream) return false
        if (released || selectionMenuVisible || state == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x; touchY = event.y; gestureStartedAt = event.eventTime
                gestureClaimed = false; pullingBookmark = false
                systemBackCandidate = isSystemBackGestureStart(width.toFloat(), height.toFloat(),
                    event.x, event.y, resources.displayMetrics.density)
                systemBackSuppressed = false
                bookmarkPull.start(event.x, event.y, resources.displayMetrics.density,
                    bookmarkPullEnabled && animationCommit == null, gestureRegionHeight = height.toFloat())
            }
            MotionEvent.ACTION_MOVE -> {
                if (systemBackSuppressed) return false
                if (systemBackCandidate && isSystemBackGestureSwipe(event.x - touchX, event.y - touchY)) {
                    systemBackSuppressed = true
                    bookmarkPull.reset()
                    return false
                }
                val pull = bookmarkPull.move(event.x, event.y)
                if (pull?.justClaimed == true) {
                    pullingBookmark = true
                    onBookmarkPullStart?.invoke()
                    onBookmarkPullProgress?.invoke(pull.distancePx, pull.armed)
                    return true
                }
                val dx = abs(event.x - touchX)
                val dy = abs(event.y - touchY)
                val moved = if (transition == "scroll") dy > dx * 1.3f else dx > dy * 1.3f
                if (moved && maxOf(dx, dy) > 18f * resources.displayMetrics.density && event.eventTime - gestureStartedAt < 500) {
                    // A new confirmed stroke can take over a settling animation. Taps and
                    // long presses never prematurely commit the previous turn.
                    if (controller?.isCompletingTurn == true) animationCommit?.invoke()
                    else if (controller != null) abortAnimation()
                    gestureClaimed = true
                    bookmarkPull.reset()
                    onManualTurnStarted()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> bookmarkPull.reset()
        }
        return animationCommit != null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // When DOWN was intercepted during settling, Android sends subsequent
        // events straight here without another onInterceptTouchEvent call.
        if (!gestureClaimed && !pullingBookmark && event.actionMasked == MotionEvent.ACTION_MOVE)
            onInterceptTouchEvent(event)
        if (pullingBookmark) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> bookmarkPull.move(event.x, event.y)?.let { onBookmarkPullProgress?.invoke(it.distancePx, it.armed) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val result = bookmarkPull.finish(event.actionMasked == MotionEvent.ACTION_CANCEL)
                    pullingBookmark = false
                    onBookmarkPullFinished?.invoke(result.commit)
                }
            }
            return true
        }
        if (gestureClaimed) {
            if (trackBookEndPull(event)) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> followGesture(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                    val direction = gestureDirection(event)
                    if (!cancelled && transition != "none") followGesture(event, finishing = true)
                    val animation = controller
                    gestureClaimed = false
                    clearWaitingGesture()
                    if (animation != null) {
                        // If reversal is waiting for a different slot, the old sheet must
                        // return instead of committing the page from the previous direction.
                        val actual = animation.currentDirection.toSpreadDirection()
                        val release = MotionEvent.obtain(event).apply {
                            if (cancelled || direction != 0 && actual != direction) action = MotionEvent.ACTION_CANCEL
                        }
                        animation.onTouchEvent(release)
                        release.recycle()
                        postInvalidateOnAnimation()
                    } else if (!cancelled && gestureCanCommit(event)) {
                        turn(direction)
                    } else {
                        pendingTurn = 0
                        drainPreloads()
                    }
                }
            }
        }
        return true
    }

    private fun isShowingFinalSpread(): Boolean {
        val spread = state ?: return false
        val last = spread.second ?: spread.first
        return last.target.chapterIndex == (session?.chapterCount ?: 0) - 1 &&
            last.target.pageIndex + 1 >= last.pageCount
    }

    /** Forward drag on the final spread drives the finish page instead of a page turn. */
    private fun trackBookEndPull(event: MotionEvent): Boolean {
        if (!bookEndPulling) {
            if (event.actionMasked != MotionEvent.ACTION_MOVE || controller != null ||
                animationCommit != null || gestureDirection(event) <= 0 || !isShowingFinalSpread()
            ) return false
            bookEndPulling = true
            clearWaitingGesture()
            cancelPreparation(replaced = false)
        }
        val forward = if (transition == "scroll") touchY - event.y else {
            val dx = event.x - touchX
            if (reverseAxis) dx else -dx
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onBookEndPull(forward.coerceAtLeast(0f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                bookEndPulling = false
                gestureClaimed = false
                onBookEndPullEnd(event.actionMasked == MotionEvent.ACTION_CANCEL)
            }
        }
        return true
    }

    private fun PageAnimationController.Direction.toSpreadDirection(): Int = when (this) {
        PageAnimationController.Direction.NEXT -> 1
        PageAnimationController.Direction.PREV -> -1
        PageAnimationController.Direction.NONE -> 0
    }

    private fun gestureDirection(event: MotionEvent): Int {
        val delta = if (transition == "scroll") event.y - touchY else event.x - touchX
        if (abs(delta) < 12f) return 0
        val direction = if (delta < 0) 1 else -1
        return if (transition != "scroll" && reverseAxis) -direction else direction
    }

    private fun gestureCanCommit(event: MotionEvent): Boolean {
        val distance = abs(if (transition == "scroll") event.y - touchY else event.x - touchX)
        val size = if (transition == "scroll") height else width
        val speed = distance * 1000f / (event.eventTime - gestureStartedAt).coerceAtLeast(1)
        return gestureDirection(event) != 0 && (distance >= size * PageAnimationController.FLIP_THRESHOLD ||
            speed >= 450f * resources.displayMetrics.density)
    }

    private fun clearWaitingGesture() {
        waitingGesture?.recycle()
        waitingGesture = null
    }

    private fun resumeWaitingGesture() {
        val event = waitingGesture?.let(MotionEvent::obtain) ?: return
        if (gestureClaimed) followGesture(event)
        event.recycle()
    }

    private fun dragSlotReady(direction: Int): Boolean {
        if (direction == 0) return false
        val slot = if (direction > 0) next else previous
        if (!slot.isReady()) return false
        if (transition == "curl" || transition == "wave") {
            if (current.snapshot == null && !captureSlot(current)) return false
            if (slot.snapshot == null && !captureSlot(slot)) return false
        }
        return true
    }

    private fun followGesture(event: MotionEvent, finishing: Boolean = false) {
        if (transition == "none") return
        val direction = gestureDirection(event)
        if (direction != 0 && !dragSlotReady(direction)) {
            clearWaitingGesture()
            if (!finishing) waitingGesture = MotionEvent.obtain(event)
            if (preparation?.direction != direction) {
                cancelPreparation(replaced = false)
                prepareAdjacent(direction)
            }
            return
        }
        clearWaitingGesture()
        if (controller == null) {
            if (direction == 0) return
            cancelPreparation(replaced = false)
            val animation = createAnimation()
            controller = animation
            animation.onCanFlip = { dragSlotReady(it.toSpreadDirection()) }
            animationCommit = {
                val completedDirection = animation.currentDirection.toSpreadDirection()
                val slot = if (completedDirection > 0) next else previous
                if (controller === animation && completedDirection != 0 && slot.isReady())
                    spreadCommit(slot, completedDirection, null)()
            }
            animation.onAnimationComplete = { if (controller === animation) animationCommit?.invoke() }
            if (animation is CurlPageAnim) animation.onMotionStateChanged = { motion ->
                if (motion == CurlPageAnim.MotionState.IDLE) post {
                    if (controller === animation && !gestureClaimed && !animation.isRunning) {
                        abortAnimation()
                        drainPreloads()
                    }
                }
            }
            val down = MotionEvent.obtain(gestureStartedAt, gestureStartedAt, MotionEvent.ACTION_DOWN,
                touchX, touchY, event.metaState)
            animation.onTouchEvent(down)
            down.recycle()
            animation.confirmPageGesture()
            trace("drag", "begin direction=$direction size=${width}x$height")
        }
        val move = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_MOVE }
        controller?.onTouchEvent(move)
        move.recycle()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        listOf(previous, current, next).forEach { slot -> slot.target?.let(slot::arrange); slot.invalidateSnapshot() }
        onViewportChanged(w, h)
        listOf(previous, current, next).flatMap { it.existingLeaves }.forEach { leaf -> if (!leaf.configured) leaf.view.post { configure(leaf) } }
    }

    private fun captureSlot(slot: SpreadSlot): Boolean {
        val old = slot.snapshot
        val snapshot = captureSpread(slot, old?.takeIf(bitmapPool::canReuse), androidx.compose.ui.unit.IntSize(width, height)) ?: return false
        if (snapshot !== old) bitmapPool.retire(old)
        slot.snapshot = bitmapPool.track(snapshot)
        return true
    }

    /** Capture each leaf at its own viewport size, then compose without changing
     * its aspect ratio. A hardware renderer must not move between WebViews: its
     * retained compositor constraints can vertically compress a different leaf. */
    private fun captureSpread(slot: SpreadSlot, reusable: Bitmap?, size: androidx.compose.ui.unit.IntSize): Bitmap? {
        if (width <= 0 || height <= 0 || size.width <= 0 || size.height <= 0) return null
        val views = slot.target?.pages?.indices?.map { slot.leaves[it].view } ?: return null
        if (slot.width != width || slot.height != height || views.any { it.width <= 0 || it.height != height }) return null
        val output = reusable?.takeIf { !it.isRecycled && it.width == size.width && it.height == size.height }
            ?: Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        output.density = resources.displayMetrics.densityDpi
        val canvas = Canvas(output)
        canvas.scale(size.width.toFloat() / width, size.height.toFloat() / height)
        spreadBackground.draw(canvas, width, height)
        for (view in views) {
            val old = leafCapture
            val frame = capture(view, old, androidx.compose.ui.unit.IntSize(view.width, view.height))
            if (frame == null) {
                if (output !== reusable) output.recycle()
                return null
            }
            if (frame !== old) old?.recycle()
            leafCapture = frame
            canvas.drawBitmap(frame, null, android.graphics.Rect(view.left, view.top, view.right, view.bottom), null)
        }
        return output
    }

    private fun capture(view: View, reusable: Bitmap?, size: androidx.compose.ui.unit.IntSize): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || size.width <= 0 || size.height <= 0) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                EpubHardwareSnapshot(size.width, size.height).use { pipeline ->
                    pipeline.capture(view, reusable, resources.displayMetrics.densityDpi,
                        size.width.toFloat() / view.width, size.height.toFloat() / view.height) {
                            it.drawColor(if (spreadBackground.hasImage) Color.TRANSPARENT else pageBackgroundColor,
                                android.graphics.PorterDuff.Mode.SRC)
                        }
                }
            }.onFailure { trace("snapshot", "hardwareFailure=$it") }.getOrNull()?.let { return it }
            // Software WebView.draw can use different viewport geometry too.
            // Do not use a distorted software fallback as a curl/wave frame.
            return null
        }
        val bitmap = reusable?.takeIf { !it.isRecycled && it.width == size.width && it.height == size.height }
            ?: Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        bitmap.density = resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)
        canvas.scale(size.width.toFloat() / view.width, size.height.toFloat() / view.height)
        canvas.drawColor(if (spreadBackground.hasImage) Color.TRANSPARENT else pageBackgroundColor,
            android.graphics.PorterDuff.Mode.SRC)
        view.draw(canvas)
        return bitmap
    }

    fun captureGlassBackdrop(quality: LiquidGlassSamplingQuality): GlassBitmapBackdrop? {
        if (released || state == null || animationCommit != null) return null
        val bitmap = captureSpread(current, null, quality.sampleSize(androidx.compose.ui.unit.IntSize(width, height))) ?: return null
        val origin = IntArray(2).also(this::getLocationOnScreen)
        return GlassBitmapBackdrop(bitmap, androidx.compose.ui.geometry.Offset(origin[0].toFloat(), origin[1].toFloat()),
            androidx.compose.ui.unit.IntSize(width, height))
    }

    fun release(onReleased: () -> Unit = {}) {
        if (releaseCompleted) { onReleased(); return }
        releaseCallbacks += onReleased
        if (released) return
        val views = allWebViews()
        val window = rootView
        released = true
        spreadBackground.release()
        cancelPreparation(replaced = false)
        abortAnimation()
        views.forEach { view ->
            view.beginRelease()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "lumiNative")
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        listOf(previous, current, next).forEach(::clearSlot)
        state = null
        removeAllViews()
        bitmapPool.destroy()
        leafCapture?.recycle()
        leafCapture = null
        bookmarkPull.reset()
        afterDetachedFrame(window) {
            views.forEach(EpubContentWebView::destroy)
            releaseCompleted = true
            val callbacks = releaseCallbacks.toList()
            releaseCallbacks.clear()
            callbacks.forEach { it() }
        }
    }
}
