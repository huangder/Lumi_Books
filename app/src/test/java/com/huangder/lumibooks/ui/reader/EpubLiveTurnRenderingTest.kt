package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.ui.reader.engine.PageBitmapSource
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubLiveTurnRenderingTest {
    private val activity = Robolectric.buildActivity(Activity::class.java)
    private lateinit var host: EpubPageTurnHost
    private var down = 0L

    @Before fun setUp() {
        val screen = activity.setup().get()
        host = EpubPageTurnHost(screen)
        screen.setContentView(host, android.view.ViewGroup.LayoutParams(400, 800))
        host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 400, 800)
        host.allWebViews().forEach {
            it.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 400, 800)
            // Robolectric's WebView provider does not implement setFrame or paint.
            // Give the real host measured sheets with visible page identities.
            it.right = 400
            it.bottom = 800
        }
        host.setBookmarkPullEnabled(false)
        host.setCurrentPage(0, 1, 3)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 0), 1)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 2)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 0), 1, 0, 3, host.previousWebView)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 2, 2, 3, host.nextWebView)
        host.activeWebView.background = ColorDrawable(Color.RED)
        host.previousWebView.background = ColorDrawable(Color.GREEN)
        host.nextWebView.background = ColorDrawable(Color.BLUE)
        assertTrue(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
        down = SystemClock.uptimeMillis()
    }

    @After fun tearDown() { host.release(); activity.pause().stop().destroy() }

    @Test fun warmChapterMovesIntoNeighbourWithoutReplacingActiveOrReloading() {
        assertTrue(host.ensureWarmPage())
        val warm = host.warmWebView!!
        warm.right = 400
        warm.bottom = 800
        val active = host.activeWebView
        val oldNext = host.nextWebView
        val target = EpubPageTarget(1, 0)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.WARM, target, 42)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.WARM, target, 42, 0, 4, warm)
        assertTrue(host.adoptWarmPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        assertSame(active, host.activeWebView)
        assertSame(warm, host.nextWebView)
        assertSame(oldNext, host.warmWebView)
        assertTrue(host.hasPreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        host.discardWarmPage()
        assertEquals(3, host.allWebViews().size)
        assertFalse(warm.released)
        assertTrue(oldNext.released)
    }

    @Test fun loadingWarmChapterKeepsItsRequestWhenItBecomesAdjacent() {
        assertTrue(host.ensureWarmPage())
        val warm = host.warmWebView!!
        warm.right = 400
        warm.bottom = 800
        val target = EpubPageTarget(1, 0)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.WARM, target, 42)
        assertTrue(host.adoptWarmPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        assertFalse(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, target, 41, 0, 4, warm)
        assertFalse(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, target, 42, 0, 4, warm)
        assertTrue(host.hasPreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
    }

    @Test fun warmChapterWithChangedConfigurationCannotBePromoted() {
        assertTrue(host.ensureWarmPage())
        val warm = host.warmWebView!!
        warm.right = 400
        warm.bottom = 800
        val target = EpubPageTarget(1, 0)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.WARM, target, 42)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.WARM, target, 42, 0, 4, warm)
        warm.documentLifecycle.configure("new-font")
        assertFalse(host.adoptWarmPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        assertEquals(4, host.allWebViews().size)
        host.discardWarmPage()
        assertEquals(3, host.allWebViews().size)
    }

    @Test fun closingDetachesImmediatelyButDestroysAndNavigatesOnlyAfterFrameFence() {
        var frameCommitted: (() -> Unit)? = null
        val closingHost = EpubPageTurnHost(activity.get()) { _, callback -> frameCommitted = callback }
        val views = closingHost.allWebViews().toList()
        var navigated = 0
        closingHost.release { navigated++ }
        closingHost.release()
        assertNotNull(frameCommitted)
        assertEquals(0, closingHost.childCount)
        assertEquals(0, navigated)
        views.forEach { assertTrue(it.released); assertFalse(it.destroyed); assertNull(it.parent) }
        val oldTarget = closingHost.currentPageTarget()
        closingHost.setCurrentPage(8, 3, 4)
        assertEquals(oldTarget, closingHost.currentPageTarget())
        frameCommitted!!.invoke()
        assertEquals(1, navigated)
        views.forEach { assertTrue(it.destroyed) }
        closingHost.release()
        assertEquals(1, navigated)
    }

    @Test fun scrollingPreloadCanPromoteWithoutNativePaging() {
        host.setNativePagingEnabled(false)
        host.setNativeTouchPagingEnabled(false)
        host.setCurrentPage(1, 0, 1)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(2, 0), 8)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(2, 0), 8, 0, 1, host.nextWebView)
        assertTrue(host.promotePreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(2, 0), 1))
        assertEquals(EpubPageTarget(2, 0), host.currentPageTarget())
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(1, Int.MAX_VALUE), 9)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(1, Int.MAX_VALUE), 9, 0, 1, host.previousWebView)
        assertTrue(host.promotePreparedPage(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(1, 0), 1))
        assertEquals(EpubPageTarget(1, 0), host.currentPageTarget())
    }

    @Test fun releaseWhileTurningDetachesBeforeDestroyAndDropsCallbacks() {
        var callbacks = 0
        val views = host.allWebViews().toList()
        host.onPageCommit = { _, _, _ -> callbacks++ }
        views.forEach { it.runAfterNextDraw { callbacks++ } }
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 40f, 120)
        event(MotionEvent.ACTION_UP, 40f, 170)
        host.release()
        host.release()
        views.forEach {
            assertTrue(it.released)
            assertNull(it.parent)
            it.loadUrl("https://should-not-load.invalid")
            it.evaluateJavascript("true") { callbacks++ }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        host.computeScroll()
        frame().recycle()
        assertEquals(0, callbacks)
        assertEquals(0, host.childCount)
    }

    @Test fun repeatedBookEndSwipeThenPreviousDoesNotQueueAReturn() {
        host.onHasPotentialTurn = { direction, current, count ->
            if (direction > 0) current.pageIndex + 1 < count else current.pageIndex > 0
        }
        host.setCurrentPage(0, 2, 3)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, null, 3)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 1), 4)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 1), 4, 1, 3, host.previousWebView)
        repeat(3) { index ->
            event(MotionEvent.ACTION_DOWN, 350f, index * 300L)
            event(MotionEvent.ACTION_MOVE, 40f, index * 300L + 100)
            event(MotionEvent.ACTION_UP, 40f, index * 300L + 150)
        }
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        event(MotionEvent.ACTION_DOWN, 70f, 1100)
        event(MotionEvent.ACTION_MOVE, 360f, 1250)
        event(MotionEvent.ACTION_UP, 360f, 1280)
        repeat(8) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            host.computeScroll(); frame().recycle()
        }
        assertEquals(1, commits)
        assertEquals(EpubPageTarget(0, 1), host.currentPageTarget())
    }

    @Test fun swipesWhileTheNextPageLoadsEachTurnOnePage() {
        host.onHasPotentialTurn = { direction, current, count ->
            if (direction > 0) current.pageIndex + 1 < count else current.pageIndex > 0
        }
        host.setCurrentPage(0, 1, 10)
        var generation = 10
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), ++generation)
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
            // Stand in for the reader: the following page is prepared after each commit.
            val next = EpubPageTarget(0, target.pageIndex + 1)
            host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, next, ++generation)
            host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, next, generation, next.pageIndex, 10, host.nextWebView)
        }
        // Three quick swipes while page 2 is still loading: none may replace another.
        repeat(3) { index ->
            event(MotionEvent.ACTION_DOWN, 350f, index * 200L)
            event(MotionEvent.ACTION_MOVE, 300f, index * 200L + 30)
            event(MotionEvent.ACTION_MOVE, 60f, index * 200L + 80)
            event(MotionEvent.ACTION_UP, 60f, index * 200L + 100)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(0, commits)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), generation, 2, 10, host.nextWebView)
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            host.computeScroll(); frame().recycle()
        }
        assertEquals(3, commits)
        assertEquals(EpubPageTarget(0, 4), host.currentPageTarget())
    }

    @Test fun pressDuringATurnThatEndsBeforeTheMoveFollowsTheFinger() {
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        // A tap turn is still animating when the finger lands.
        assertTrue(host.requestTurn(1))
        event(MotionEvent.ACTION_DOWN, 100f, 0)
        // That turn ends before the first move arrives.
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            host.computeScroll(); frame().recycle()
        }
        assertEquals(1, commits)
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        // Dragging back toward page 1 must move the sheets with the finger.
        event(MotionEvent.ACTION_MOVE, 220f, 650)
        assertTrue(host.ownsPage(host.activeWebView))
        event(MotionEvent.ACTION_MOVE, 330f, 700)
        event(MotionEvent.ACTION_UP, 330f, 720)
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            host.computeScroll(); frame().recycle()
        }
        assertEquals(2, commits)
        assertEquals(EpubPageTarget(0, 1), host.currentPageTarget())
    }

    @Test fun warmPageMayPrepareDuringAnAutomaticTurnAndFollowsReadingOrder() {
        // setUp: page 0/1 of 3, with pages 0/0 and 0/2 ready on either side.
        assertEquals(EpubPageTarget(1, 0), host.pageBeyond(EpubPageTurnHost.PreloadSlot.NEXT, chapterCount = 3))
        assertNull(host.pageBeyond(EpubPageTurnHost.PreloadSlot.NEXT, chapterCount = 1))
        assertNull(host.pageBeyond(EpubPageTurnHost.PreloadSlot.PREVIOUS, chapterCount = 3))
        assertTrue(host.requestTurn(1))
        assertTrue(host.canPrepareWarmPage())
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.PREVIOUS, EpubPageTarget(0, 0), 9)
        assertNull(host.pageBeyond(EpubPageTurnHost.PreloadSlot.PREVIOUS, chapterCount = 3))
        assertFalse(host.canPrepareWarmPage())
        assertTrue(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.NEXT))
        assertFalse(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.PREVIOUS))
        assertFalse(host.canPrepareAdjacentPage())
    }

    @Test fun directionalWarmPreparationRejectsAStaleFontLayout() {
        host.nextWebView.documentLifecycle.configure("changed-font")
        assertFalse(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.NEXT))
        assertTrue(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.PREVIOUS))
        host.invalidateLayout("font-size")
        assertFalse(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.PREVIOUS))
    }

    @Test fun curlCanPlanPastAnInFlightNeighbourOnlyWithinKnownPagination() {
        val next = EpubPageTurnHost.PreloadSlot.NEXT
        val previous = EpubPageTurnHost.PreloadSlot.PREVIOUS
        host.setCurrentPage(1, 1, 3)
        host.markPreloadLoading(next, EpubPageTarget(1, 2), 9)
        host.markPreloadLoading(previous, EpubPageTarget(1, 0), 10)
        assertNull(host.pageBeyond(next, chapterCount = 3))
        assertTrue(host.canPrepareWarmPage(next, allowUnreadyAhead = true))
        assertEquals(EpubPageTarget(2, 0), host.pageBeyond(next, chapterCount = 3, useCurrentLayout = true))
        assertEquals(EpubPageTarget(0, Int.MAX_VALUE),
            host.pageBeyond(previous, chapterCount = 3, useCurrentLayout = true))

        host.markPreloadLoading(next, EpubPageTarget(1, 1), 11)
        assertNull(host.pageBeyond(next, chapterCount = 3, useCurrentLayout = true))
        host.setCurrentPage(1, 2, 3)
        host.markPreloadLoading(next, EpubPageTarget(2, 0), 12)
        assertNull(host.pageBeyond(next, chapterCount = 3, useCurrentLayout = true))
    }

    @Test fun warmLookaheadUsesActualPageCountAfterCrossingInEitherDirection() {
        val next = EpubPageTurnHost.PreloadSlot.NEXT
        val previous = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val firstOfNextChapter = EpubPageTarget(2, 0)
        val lastOfPreviousChapter = EpubPageTarget(0, Int.MAX_VALUE)
        host.setCurrentPage(1, 0, 1)
        host.markPreloadLoading(next, firstOfNextChapter, 20)
        host.markPreloadReady(next, firstOfNextChapter, 20, 0, 4, host.nextWebView)
        host.markPreloadLoading(previous, lastOfPreviousChapter, 21)
        host.markPreloadReady(previous, lastOfPreviousChapter, 21, 2, 3, host.previousWebView)

        assertEquals(EpubPageTarget(2, 1), host.pageBeyond(next, chapterCount = 3))
        assertEquals(EpubPageTarget(0, 1), host.pageBeyond(previous, chapterCount = 3))
    }

    @Test fun curlPlansPastUnpaintedChapterEdgesUsingTheirReportedPagination() {
        val next = EpubPageTurnHost.PreloadSlot.NEXT
        val previous = EpubPageTurnHost.PreloadSlot.PREVIOUS
        host.setCurrentPage(2, 0, 1)
        host.markPreloadLoading(next, EpubPageTarget(3, 0), 20)
        host.markPreloadLoading(previous, EpubPageTarget(1, Int.MAX_VALUE), 21)

        assertNull(host.pageBeyond(next, chapterCount = 5, useCurrentLayout = true))
        assertNull(host.pageBeyond(previous, chapterCount = 5, useCurrentLayout = true))
        assertEquals(EpubPageTarget(3, 1), host.pageBeyond(next, chapterCount = 5, reportedPageCount = 4))
        assertEquals(EpubPageTarget(1, 2), host.pageBeyond(previous, chapterCount = 5, reportedPageCount = 4))
        assertEquals(EpubPageTarget(4, 0), host.pageBeyond(next, chapterCount = 5, reportedPageCount = 1))
        assertEquals(EpubPageTarget(0, Int.MAX_VALUE),
            host.pageBeyond(previous, chapterCount = 5, reportedPageCount = 1))
        assertFalse(host.isPreloadReady(next))
        assertFalse(host.isPreloadReady(previous))
    }

    @Test fun directionalWarmPreparationPausesDuringFingerDrag() {
        assertTrue(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.NEXT))
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 100f, 120)
        assertFalse(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.NEXT))
        assertFalse(host.canPrepareWarmPage(EpubPageTurnHost.PreloadSlot.PREVIOUS))
        event(MotionEvent.ACTION_UP, 100f, 160)
    }

    @Test fun consecutiveOnePageChaptersUseWarmPagesWithoutColdNavigation() = checkShortChapterChain(1)

    @Test fun consecutiveOnePageChaptersUseWarmPagesWhenTurningBackward() = checkShortChapterChain(-1)

    private fun checkShortChapterChain(direction: Int) {
        val ahead = if (direction > 0) EpubPageTurnHost.PreloadSlot.NEXT else EpubPageTurnHost.PreloadSlot.PREVIOUS
        val behind = if (direction > 0) EpubPageTurnHost.PreloadSlot.PREVIOUS else EpubPageTurnHost.PreloadSlot.NEXT
        val start = if (direction > 0) 0 else 10
        fun edgeTarget(chapter: Int) = EpubPageTarget(chapter, if (direction > 0) 0 else Int.MAX_VALUE)
        host.setCurrentPage(start, 0, 1)
        host.markPreloadLoading(behind, null, 10)
        val first = edgeTarget(start + direction)
        host.markPreloadLoading(ahead, first, 11)
        host.markPreloadReady(ahead, first, 11, 0, 1, host.preloadView(ahead)!!)
        var generation = 12
        var coldTurns = 0
        var commits = 0
        host.onUnpreparedChapterTurn = { coldTurns++ }
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
            assertTrue(host.adoptWarmPage(ahead, edgeTarget(target.chapterIndex + direction)))
        }
        repeat(8) { index ->
            assertTrue(host.hasPreparedPage(ahead, edgeTarget(start + direction * (index + 1))))
            assertTrue(host.requestTurn(direction))
            // The fourth view prepares the chapter after the animated destination.
            assertTrue(host.canPrepareWarmPage(ahead))
            assertTrue(host.ensureWarmPage())
            val warm = host.warmWebView!!
            warm.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            warm.layout(0, 0, 400, 800)
            warm.right = 400
            warm.bottom = 800
            // Match the explicit drawable used for the original three sheets:
            // Robolectric's WebView provider does not paint its background.
            warm.background = ColorDrawable(Color.MAGENTA)
            val beyond = host.pageBeyond(ahead, chapterCount = 12)!!
            assertEquals(edgeTarget(start + direction * (index + 2)), beyond)
            host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.WARM, beyond, ++generation)
            host.markPreloadReady(EpubPageTurnHost.PreloadSlot.WARM, beyond, generation, 0, 1, warm)
            repeat(4) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                host.computeScroll(); frame().recycle()
            }
            // Cross-chapter commit is posted after the destination's draw.
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EpubPageTarget(start + direction * (index + 1), 0), host.currentPageTarget())
            assertEquals("chapter $index, active attached=${host.activeWebView.isAttachedToWindow}, " +
                "handoff=${host.hasPendingPageHandoff()}", index + 1, commits)
        }
        assertEquals(0, coldTurns)
    }

    @Test fun stillMenuTapDropsQueuedTurns() {
        host.onHasPotentialTurn = { direction, current, count ->
            if (direction > 0) current.pageIndex + 1 < count else current.pageIndex > 0
        }
        host.setCurrentPage(0, 1, 10)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 11)
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 300f, 30)
        event(MotionEvent.ACTION_MOVE, 60f, 80)
        event(MotionEvent.ACTION_UP, 60f, 100)
        // A still tap in the middle means "stop": the queued turn must not play later.
        event(MotionEvent.ACTION_DOWN, 200f, 300)
        event(MotionEvent.ACTION_UP, 200f, 360)
        shadowOf(Looper.getMainLooper()).idle()
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 11, 2, 10, host.nextWebView)
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            host.computeScroll(); frame().recycle()
        }
        assertEquals(0, commits)
        assertEquals(EpubPageTarget(0, 1), host.currentPageTarget())
    }

    private fun event(action: Int, x: Float, elapsed: Long, y: Float = 400f) {
        val event = MotionEvent.obtain(down, down + elapsed, action, x, y, 0)
        host.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun frame(): Bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).also {
        assertEquals(400, host.width)
        assertEquals(800, host.height)
        val canvas = Canvas(it)
        canvas.drawColor(Color.YELLOW)
        assertEquals(Color.YELLOW, it.getPixel(0, 0))
        host.draw(canvas)
    }

    @Test fun forwardCurrentSheetCoversNextThroughFirstTenSlowFrames() {
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        for (index in 1..10) {
            val distance = 12 + index * 4
            event(MotionEvent.ACTION_MOVE, 200f - distance, index * 110L)
            val bitmap = frame()
            assertEquals("current at frame $index", Color.RED, bitmap.getPixel(120, 400))
            assertEquals("next exposed at frame $index", Color.BLUE, bitmap.getPixel(399, 400))
            assertEquals("sheet edge follows the finger at frame $index",
                Color.RED, bitmap.getPixel(400 - distance - 1, 400))
            assertEquals(1f, host.nextWebView.alpha, 0f)
            assertEquals(0f, host.nextWebView.translationX, 0f)
            bitmap.recycle()
        }
        event(MotionEvent.ACTION_UP, 148f, 1200)
    }

    @Test fun previousSheetCoversCurrentAndStaleReadyCannotRestackIt() {
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 300f, 180)
        host.setCurrentPage(0, 1, 3)
        val bitmap = frame()
        assertEquals(Color.GREEN, bitmap.getPixel(30, 400))
        assertEquals(Color.RED, bitmap.getPixel(300, 400))
        assertTrue(host.ownsPage(host.previousWebView))
        bitmap.recycle()
    }

    @Test fun rtlMirrorsMotionWithoutExchangingSheetOwnership() {
        host.setReverseAxis(true)
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 300f, 180)
        var bitmap = frame()
        assertEquals(Color.BLUE, bitmap.getPixel(2, 400))
        assertEquals(Color.RED, bitmap.getPixel(300, 400))
        bitmap.recycle()
        event(MotionEvent.ACTION_MOVE, 100f, 240)
        bitmap = frame()
        assertEquals(Color.GREEN, bitmap.getPixel(398, 400))
        assertEquals(Color.RED, bitmap.getPixel(100, 400))
        bitmap.recycle()
    }

    @Test fun cancellationKeepsSheetsDuringBounceAndNeverCommits() {
        var commits = 0
        host.onPageCommit = { _, _, _ -> commits++ }
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 150f, 250)
        event(MotionEvent.ACTION_CANCEL, 150f, 300)
        val bitmap = frame()
        assertEquals(Color.BLUE, bitmap.getPixel(399, 400))
        bitmap.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        host.computeScroll()
        host.computeScroll()
        assertEquals(0, commits)
        assertEquals(EpubPageTarget(0, 1), host.currentPageTarget())
    }

    @Test fun requestsHighRefreshOnlyWhilePageTurnIsActive() {
        assertTrue(host.requestedFrameRate.isNaN())
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        assertTrue(host.requestedFrameRate.isNaN())
        event(MotionEvent.ACTION_MOVE, 40f, 120)
        assertEquals(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH, host.requestedFrameRate, 0f)
        host.cancelPendingInput()
        assertTrue(host.requestedFrameRate.isNaN())
    }

    @Test fun oneTurnCommitsOnceEvenWithDuplicateReadyAndRepeatedFrames() {
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 40f, 120)
        event(MotionEvent.ACTION_UP, 40f, 170)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        repeat(4) { host.computeScroll(); frame().recycle() }
        assertEquals(1, commits)
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        assertTrue(host.requestedFrameRate.isNaN())
    }

    @Test fun lateTargetResumesAtLatestPointerWithoutExposingAnUnreadySheet() {
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 3)
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 150f, 100)
        assertFalse(host.ownsPage(host.activeWebView))
        event(MotionEvent.ACTION_MOVE, 130f, 200)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(0, 2), 3, 2, 3, host.nextWebView)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.ownsPage(host.activeWebView))
        val bitmap = frame()
        assertEquals(Color.RED, bitmap.getPixel(320, 400))
        assertEquals(Color.BLUE, bitmap.getPixel(399, 400))
        bitmap.recycle()
    }

    @Test fun preparedPageFromOldDocumentCannotStartATurn() {
        host.nextWebView.documentLifecycle.beginDocument()
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 130f, 100)
        assertFalse(host.ownsPage(host.activeWebView))
        assertEquals(EpubPageTarget(0, 1), host.currentPageTarget())
    }

    @Test fun crossChapterTurnKeepsLogicalRoles() {
        prepareCrossChapterTurn()
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 120f, 180)
        val bitmap = frame()
        assertEquals(Color.RED, bitmap.getPixel(120, 400))
        assertEquals(Color.BLUE, bitmap.getPixel(399, 400))
        bitmap.recycle()
    }

    @Test fun crossChapterCommitWaitsForTargetDrawAndDoesNotStartLookaheadInAnimation() {
        prepareCrossChapterTurn()
        var commits = 0
        var lookahead = 0
        host.onSlideLookaheadRequested = { _, _ -> lookahead++ }
        host.onSlideVisualPageAdvanced = { _, _ -> lookahead++ }
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }

        finishCrossChapterAnimation()
        assertEquals(EpubPageTarget(1, 0), host.currentPageTarget())
        assertEquals(0, lookahead)
        assertEquals(0, commits)
        frame().recycle()
        assertEquals("chapter work must run outside drawing", 0, commits)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, commits)
        assertFalse(host.hasPendingPageHandoff())
        repeat(3) { host.computeScroll(); frame().recycle() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, commits)
    }

    @Test fun crossChapterDrawCommitCannotOutliveItsDocument() {
        prepareCrossChapterTurn()
        var commits = 0
        host.onPageCommit = { _, _, _ -> commits++ }
        finishCrossChapterAnimation()
        frame().recycle()
        host.activeWebView.documentLifecycle.beginDocument()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, commits)
    }

    @Test fun crossChapterReflowBeforeFirstDrawStillFinishesHandoffOnce() {
        prepareCrossChapterTurn()
        var commits = 0
        host.onPageCommit = { _, target, count ->
            commits++
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        finishCrossChapterAnimation()
        val lifecycle = host.activeWebView.documentLifecycle
        lifecycle.observeLayout(lifecycle.configurationGeneration, 2L, stable = false)
        lifecycle.observeLayout(lifecycle.configurationGeneration, 3L, stable = true)
        frame().recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, commits)
        assertEquals(EpubPageTarget(1, 0), host.currentPageTarget())
        assertFalse(host.hasPendingPageHandoff())
    }

    @Test fun lateFontReflowDuringHandoffAcceptsAChangedPageAddress() {
        verifyLateFontReflowDuringHandoff(pageIndex = 2)
    }

    @Test fun lateFontReflowDuringHandoffAlsoReleasesAnUnchangedPageAddress() {
        verifyLateFontReflowDuringHandoff(pageIndex = 0)
    }

    private fun verifyLateFontReflowDuringHandoff(pageIndex: Int) {
        prepareCrossChapterTurn()
        var staleCommits = 0
        host.onPageCommit = { _, _, _ -> staleCommits++ }
        finishCrossChapterAnimation()
        assertTrue(host.hasPendingPageHandoff())
        val view = host.activeWebView
        val generation = view.documentLifecycle.configurationGeneration
        assertTrue(host.observeDocumentLayout(view, generation, 2, stable = false, pending = true))
        assertFalse("late font reflow must release the old page handoff", host.hasPendingPageHandoff())
        assertTrue(host.hasLayoutPreview())
        assertTrue(host.observeDocumentLayout(view, generation, 3, stable = true, pending = false))
        host.setCurrentPage(1, pageIndex, 6, onSettled = host::endLayoutPreview)
        frame().recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(EpubPageTarget(1, pageIndex), host.currentPageTarget())
        assertFalse(host.hasLayoutPreview())
        assertEquals(0, staleCommits)

        val next = EpubPageTarget(1, pageIndex + 1)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, next, 10)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, next, 10, next.pageIndex, 6, host.nextWebView)
        host.onPageCommit = { _, target, count -> host.setCurrentPage(target.chapterIndex, target.pageIndex, count) }
        assertTrue(host.requestTurn(1))
        SystemClock.sleep(500)
        repeat(3) { host.computeScroll(); frame().recycle() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(next, host.currentPageTarget())
    }

    @Test fun staleDuplicateAndNeighbourLayoutEventsDoNotCancelTheActiveTurn() {
        val active = host.activeWebView
        val generation = active.documentLifecycle.configurationGeneration
        var invalidations = 0
        host.onInvalidatePreloads = { invalidations++ }
        assertTrue(host.observeDocumentLayout(active, generation, 2, stable = false, pending = true))
        assertEquals(1, invalidations)
        assertTrue(host.observeDocumentLayout(active, generation, 2, stable = false, pending = true))
        assertFalse(host.observeDocumentLayout(active, generation - 1, 9, stable = false, pending = true))
        assertFalse(host.observeDocumentLayout(active, generation, 1, stable = false, pending = true))
        val neighbour = host.nextWebView
        assertTrue(host.observeDocumentLayout(neighbour, neighbour.documentLifecycle.configurationGeneration,
            9, stable = false, pending = true))
        assertEquals(1, invalidations)
    }

    @Test fun marginChangeDuringChapterHandoffAcceptsTheRepaginatedPosition() {
        prepareCrossChapterTurn()
        var staleCommits = 0
        host.onPageCommit = { _, _, _ -> staleCommits++ }
        finishCrossChapterAnimation()
        assertTrue(host.hasPendingPageHandoff())

        host.activeWebView.documentLifecycle.configure("new-margins")
        host.invalidateLayout()
        host.setCurrentPage(1, 2, 6)
        frame().recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertFalse("old page address must not keep the handoff busy", host.hasPendingPageHandoff())
        assertEquals(EpubPageTarget(1, 2), host.currentPageTarget())
        assertEquals(6, host.currentPageCount())
        assertEquals(0, staleCommits)

        val next = EpubPageTarget(1, 3)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, next, 10)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, next, 10, 3, 6, host.nextWebView)
        host.onPageCommit = { _, target, count -> host.setCurrentPage(target.chapterIndex, target.pageIndex, count) }
        assertTrue(host.requestTurn(1))
        SystemClock.sleep(500)
        repeat(3) { host.computeScroll(); frame().recycle() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(next, host.currentPageTarget())
    }

    @Test fun marginChangeDuringSwipeReleasesBothOldSheetsBeforeInvalidatingPreloads() {
        var invalidations = 0
        host.onInvalidatePreloads = {
            invalidations++
            assertFalse(host.ownsPage(host.activeWebView))
            assertFalse(host.ownsPage(host.nextWebView))
        }
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 130f, 100)
        assertTrue(host.ownsPage(host.activeWebView))

        host.activeWebView.documentLifecycle.configure("new-margins")
        host.invalidateLayout()
        host.setCurrentPage(0, 4, 8)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, invalidations)
        assertEquals(EpubPageTarget(0, 4), host.currentPageTarget())
        assertFalse(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
        assertFalse(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.PREVIOUS))
    }

    @Test fun turnDuringFontResizeUsesTheNewPageCount() {
        host.setCurrentPage(0, 2, 3)
        host.onHasPotentialTurn = { direction, target, pageCount ->
            hasPotentialEpubPageTurn(target, pageCount, chapterCount = 1, direction = direction)
        }
        var bookEnds = 0
        host.onBookEnd = { bookEnds++ }
        host.activeWebView.documentLifecycle.configure("larger-font")
        host.invalidateLayout()

        assertTrue(host.requestTurn(1))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, bookEnds)

        val next = EpubPageTarget(0, 5)
        host.setCurrentPage(0, 4, 8) {
            host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, next, 9)
            host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, next, 9, 5, 8, host.nextWebView)
        }
        host.onPageCommit = { _, target, count -> host.setCurrentPage(target.chapterIndex, target.pageIndex, count) }
        shadowOf(Looper.getMainLooper()).idle()
        SystemClock.sleep(500)
        repeat(3) { host.computeScroll(); frame().recycle() }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(next, host.currentPageTarget())
        assertEquals(0, bookEnds)
    }

    private fun finishFontRepagination(page: Int = 4, count: Int = 8, mode: String = "slide", chapter: Int = 0) {
        host.setTransition(mode)
        host.onHasPotentialTurn = { direction, target, pageCount ->
            hasPotentialEpubPageTurn(target, pageCount, chapterCount = 3, direction = direction)
        }
        val lifecycle = host.activeWebView.documentLifecycle
        lifecycle.configure("font-resize-$mode")
        host.invalidateLayout("reconfigure")
        lifecycle.observeLayout(lifecycle.configurationGeneration, 1, stable = true)
        host.setCurrentPage(chapter, page, count)
    }

    @Test fun repaginatedChapterTurnsWithoutWaitingForDuplicateWebViews() {
        finishFontRepagination()
        val active = host.activeWebView
        assertTrue(host.requestTurn(1))
        assertTrue(shadowOf(active).lastEvaluatedJavascript.contains("goToPage(5)"))
        assertTrue(host.isAwaitingPage(0, 5))
        assertEquals("position changes only on the stable visual callback", EpubPageTarget(0, 4), host.currentPageTarget())
        host.setCurrentPage(0, 5, 8)
        assertFalse(host.hasPendingPageHandoff())
        assertSame(active, host.activeWebView)
        assertTrue(host.requestTurn(-1))
        assertTrue(shadowOf(active).lastEvaluatedJavascript.contains("goToPage(4)"))
    }

    @Test fun tapDuringReflowReplaysImmediatelyWhenTheCurrentChapterIsStable() {
        host.onHasPotentialTurn = { _, _, _ -> true }
        val lifecycle = host.activeWebView.documentLifecycle
        lifecycle.configure("font-resize")
        host.invalidateLayout()
        assertTrue(host.requestTurn(1))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.hasPendingPageHandoff())
        lifecycle.observeLayout(lifecycle.configurationGeneration, 1, stable = true)
        host.setCurrentPage(0, 4, 8)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.isAwaitingPage(0, 5))
        assertTrue(shadowOf(host.activeWebView).lastEvaluatedJavascript.contains("goToPage(5)"))
    }

    @Test fun swipeAfterReflowTurnsOnReleaseWhileDuplicatePagesAreLoading() {
        finishFontRepagination()
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 70f, 80)
        assertFalse("moving a finger must not commit the page", host.hasPendingPageHandoff())
        event(MotionEvent.ACTION_UP, 50f, 120)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.isAwaitingPage(0, 5))
    }

    @Test fun repeatedTypographyChangesRetainTheTurnUntilTheLatestPaginationFinishes() {
        host.onHasPotentialTurn = { _, _, _ -> true }
        val lifecycle = host.activeWebView.documentLifecycle
        repeat(12) { step ->
            lifecycle.configure("font-$step")
            host.invalidateLayout("reconfigure")
            if (step == 0) assertTrue(host.requestTurn(1))
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.hasPendingPageHandoff())
        lifecycle.observeLayout(lifecycle.configurationGeneration, 1, stable = true)
        host.setCurrentPage(0, 4, 8)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.isAwaitingPage(0, 5))
    }

    @Test fun rapidTapsDuringDirectHandoffLeaveOnlyOnePendingTurn() {
        finishFontRepagination()
        host.onCapturedTapDirection = { 1 }
        assertTrue(host.requestTurn(1))
        repeat(4) { index ->
            event(MotionEvent.ACTION_DOWN, 350f, index * 40L)
            event(MotionEvent.ACTION_UP, 350f, index * 40L + 20)
        }
        repeat(3) { assertTrue(host.requestTurn(1)) }
        for (page in 5..6) {
            assertTrue("queued turn must target page $page", host.isAwaitingPage(0, page))
            host.setCurrentPage(0, page, 8)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertFalse(host.hasPendingPageHandoff())
        assertEquals(EpubPageTarget(0, 6), host.currentPageTarget())

        assertTrue(host.requestTurn(1))
        assertTrue(host.isAwaitingPage(0, 7))
        host.setCurrentPage(0, 7, 8)
        assertEquals(EpubPageTarget(0, 7), host.currentPageTarget())
    }

    // Robolectric's API 35 ImageReader cannot finish a hardware snapshot. API 28
    // exercises the same curl input/queue using the software snapshot fallback.
    @Test @Config(sdk = [28]) fun isolatedCurlWaitsForTheCrossChapterSnapshot() {
        finishFontRepagination(page = 5, count = 6, mode = "curl")
        val commits = mutableListOf<EpubPageTarget>()
        host.onPageCommit = { _, target, count ->
            commits += target
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        assertTrue(host.requestTurn(1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(EpubPageTarget(0, 5), host.currentPageTarget())
        assertTrue(commits.isEmpty())
        val next = EpubPageTarget(1, 0)
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        host.markPreloadLoading(slot, next, 99)
        host.markPreloadReady(slot, next, 99, 0, 6, host.nextWebView)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.ownsPage(host.nextWebView))
        assertTrue(commits.isEmpty())
        renderCurlFrames()
        assertEquals(listOf(next), commits)
    }

    @Test @Config(sdk = [28]) fun crossChapterCurlKeepsItsTerminalPageThroughTheFirstLiveDraw() =
        verifyCrossChapterCurlHandoff(waitForFallback = false)

    @Test @Config(sdk = [28]) fun snapshotCurlDrawsReadyHiddenPageOnlyIfItHasADrawCallback() {
        finishFontRepagination(mode = "curl")
        val previousSlot = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val nextSlot = EpubPageTurnHost.PreloadSlot.NEXT
        val previous = EpubPageTarget(0, 3)
        val next = EpubPageTarget(0, 5)
        val hidden = host.previousWebView
        var hiddenDraws = 0
        hidden.background = object : ColorDrawable(Color.GREEN) {
            override fun draw(canvas: Canvas) {
                hiddenDraws++
                super.draw(canvas)
            }
        }
        host.markPreloadLoading(previousSlot, previous, 98)
        host.markPreloadReady(previousSlot, previous, 98, 3, 8, hidden)
        host.markPreloadLoading(nextSlot, next, 99)
        host.markPreloadReady(nextSlot, next, 99, 5, 8, host.nextWebView)
        assertTrue(host.hasPreparedPage(previousSlot, previous))
        assertTrue(host.requestTurn(1))
        assertTrue(host.ownsPage(host.nextWebView))

        val before = hiddenDraws
        frame().recycle()
        assertEquals(before, hiddenDraws)

        var callbackRan = false
        hidden.runAfterNextDraw { callbackRan = true }
        frame().recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(hiddenDraws > before)
        assertTrue(callbackRan)
    }

    @Test @Config(sdk = [28]) fun crossChapterCurlCommitFallbackKeepsTheTerminalPageUntilDraw() =
        verifyCrossChapterCurlHandoff(waitForFallback = true)

    @Test @Config(sdk = [28]) fun curlStagesNextSheetOnlyAfterAFingerTurnConfirms() =
        verifyCurlStaging(direction = 1)

    @Test @Config(sdk = [28]) fun backwardCurlStagesPreviousSheetOnlyAfterAFingerTurnConfirms() =
        verifyCurlStaging(direction = -1)

    private fun verifyCurlStaging(direction: Int) {
        finishFontRepagination(mode = "curl")
        val ahead = if (direction > 0) EpubPageTurnHost.PreloadSlot.NEXT else EpubPageTurnHost.PreloadSlot.PREVIOUS
        val behind = if (direction > 0) EpubPageTurnHost.PreloadSlot.PREVIOUS else EpubPageTurnHost.PreloadSlot.NEXT
        val start = host.currentPageTarget()
        val destination = start.copy(pageIndex = start.pageIndex + direction)
        val lookahead = start.copy(pageIndex = start.pageIndex + 2 * direction)
        val source = host.activeWebView
        val incoming = checkNotNull(host.preloadView(ahead))
        val staging = checkNotNull(host.preloadView(behind))
        host.markPreloadLoading(ahead, destination, 98)
        host.markPreloadReady(ahead, destination, 98, destination.pageIndex, 8, incoming)
        host.onPageCommit = { _, target, count -> host.setCurrentPage(target.chapterIndex, target.pageIndex, count) }
        var preparations = 0
        host.onCurlFollowupRequested = { slot, target ->
            assertEquals(behind, slot)
            assertEquals(lookahead, target)
            assertTrue(host.ownsPage(source))
            assertTrue(host.ownsPage(incoming))
            assertFalse(host.ownsPage(staging))
            host.markPreloadLoading(slot, target, 99)
            preparations++
        }

        event(MotionEvent.ACTION_DOWN, 200f + direction * 150f, 0, y = 600f)
        event(MotionEvent.ACTION_MOVE, 200f - direction * 100f, 120, y = 460f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, preparations)
        event(MotionEvent.ACTION_UP, 200f - direction * 150f, 180, y = 400f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, preparations)
        renderCurlFrames()

        assertSame(incoming, host.activeWebView)
        assertSame(source, host.preloadView(behind))
        assertTrue(host.hasPreparedPage(behind, start))
        assertSame(staging, host.preloadView(ahead))
        assertEquals(lookahead, host.preloadTarget(ahead))
        assertFalse(host.hasPreparedPage(ahead, lookahead))
        host.markPreloadReady(ahead, lookahead, 99, lookahead.pageIndex, 8, staging)
        assertTrue(host.hasPreparedPage(ahead, lookahead))
    }

    @Test @Config(sdk = [28]) fun curlDefersWarmSnapshotButKeepsItWhenThePageBecomesAdjacent() {
        finishFontRepagination(mode = "curl")
        val nextSlot = EpubPageTurnHost.PreloadSlot.NEXT
        val warmSlot = EpubPageTurnHost.PreloadSlot.WARM
        val next = EpubPageTarget(0, 5)
        val lookahead = EpubPageTarget(0, 6)
        host.markPreloadLoading(nextSlot, next, 98)
        host.markPreloadReady(nextSlot, next, 98, 5, 8, host.nextWebView)
        assertTrue(host.ensureWarmPage())
        val warm = host.warmWebView!!
        warm.right = 400
        warm.bottom = 800
        var snapshots = 0
        warm.background = object : ColorDrawable(Color.GREEN) {
            override fun draw(canvas: Canvas) {
                snapshots++
                super.draw(canvas)
            }
        }
        host.markPreloadLoading(warmSlot, lookahead, 99)
        host.onPageCommit = { _, target, count ->
            assertEquals(next, target)
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
            assertTrue(host.adoptWarmPage(nextSlot, lookahead))
        }

        assertTrue(host.requestTurn(1))
        val before = snapshots
        host.markPreloadReady(warmSlot, lookahead, 99, 6, 8, warm)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(before, snapshots)
        assertFalse(host.isPreloadReady(warmSlot))
        assertTrue(host.hasDeferredWarmSnapshot(warm, lookahead, 99))
        assertFalse(host.hasDeferredWarmSnapshot(warm, lookahead, 100))

        renderCurlFrames()
        assertSame(warm, host.nextWebView)
        assertTrue(host.hasPreparedPage(nextSlot, lookahead))
        assertFalse(host.hasDeferredWarmSnapshot(warm, lookahead, 99))
    }

    @Test @Config(sdk = [28]) fun curlDiscardsDeferredWarmSnapshotAfterLayoutChanges() {
        finishFontRepagination(mode = "curl")
        val nextSlot = EpubPageTurnHost.PreloadSlot.NEXT
        val warmSlot = EpubPageTurnHost.PreloadSlot.WARM
        val next = EpubPageTarget(0, 5)
        val lookahead = EpubPageTarget(0, 6)
        host.markPreloadLoading(nextSlot, next, 98)
        host.markPreloadReady(nextSlot, next, 98, 5, 8, host.nextWebView)
        assertTrue(host.ensureWarmPage())
        val warm = host.warmWebView!!
        warm.right = 400
        warm.bottom = 800
        host.markPreloadLoading(warmSlot, lookahead, 99)

        assertTrue(host.requestTurn(1))
        host.markPreloadReady(warmSlot, lookahead, 99, 6, 8, warm)
        warm.documentLifecycle.configure("new-font")
        host.cancelPendingInput()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertFalse(host.isPreloadReady(warmSlot))
    }

    private fun verifyCrossChapterCurlHandoff(waitForFallback: Boolean) {
        finishFontRepagination(page = 5, count = 6, mode = "curl")
        val target = EpubPageTarget(1, 0)
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        val incoming = host.nextWebView
        incoming.background = ColorDrawable(Color.BLUE)
        host.markPreloadLoading(slot, target, 99)
        host.markPreloadReady(slot, target, 99, 0, 6, incoming)
        var commits = 0
        host.onPageCommit = { _, page, count ->
            commits++
            host.setCurrentPage(page.chapterIndex, page.pageIndex, count)
        }
        assertTrue(host.requestTurn(1))
        val main = shadowOf(Looper.getMainLooper())
        repeat(30) {
            if (!host.hasPendingPageHandoff()) {
                main.idleFor(Duration.ofMillis(16))
                host.computeScroll()
                if (!host.hasPendingPageHandoff()) frame().recycle()
                main.idle()
            }
        }
        assertSame(incoming, host.activeWebView)
        assertTrue(host.hasPendingPageHandoff())
        assertEquals(0, commits)
        if (waitForFallback) {
            main.idleFor(Duration.ofSeconds(1))
            assertEquals(1, commits)
            assertFalse(host.hasPendingPageHandoff())
        }
        incoming.background = ColorDrawable(Color.GREEN)
        val firstLiveFrame = frame()
        try {
            assertEquals(Color.BLUE, firstLiveFrame.getPixel(200, 400))
        } finally { firstLiveFrame.recycle() }
        main.idleFor(Duration.ofSeconds(1))
        assertEquals(1, commits)
        assertFalse(host.hasPendingPageHandoff())
        val settledFrame = frame()
        try {
            assertEquals(Color.GREEN, settledFrame.getPixel(200, 400))
        } finally { settledFrame.recycle() }
    }

    @Test @Config(sdk = [28]) fun curlAfterReflowAnimatesBothTapAndQueuedSwipe() {
        finishFontRepagination(mode = "curl")
        val commits = mutableListOf<EpubPageTarget>()
        host.onPageCommit = { _, target, count ->
            commits += target
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        val next = EpubPageTarget(0, 5)
        val afterNext = EpubPageTarget(0, 6)
        host.markPreloadLoading(slot, next, 98)
        host.markPreloadReady(slot, next, 98, 5, 8, host.nextWebView)
        assertTrue(host.requestTurn(1))
        event(MotionEvent.ACTION_DOWN, 350f, 0, y = 600f)
        event(MotionEvent.ACTION_MOVE, 70f, 80, y = 365f)
        assertFalse(host.hasPendingPageHandoff())
        event(MotionEvent.ACTION_UP, 50f, 120, y = 350f)
        renderCurlFrames()
        assertEquals(listOf(next), commits)
        host.markPreloadLoading(slot, afterNext, 99)
        host.markPreloadReady(slot, afterNext, 99, 6, 8, host.nextWebView)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.ownsPage(host.nextWebView))
        renderCurlFrames()
        assertEquals(listOf(next, afterNext), commits)
        assertTrue(ShadowLog.getLogsForTag("EpubTurnTrace").any {
            "ev=queued_curl_fold_drawn" in it.msg && "ny=-" !in it.msg
        })
    }

    @Test @Config(sdk = [28]) fun readyFollowupSheetLetsHeldSwipeTakeOverWithoutWaitingForAFrame() {
        finishFontRepagination(mode = "curl")
        val nextSlot = EpubPageTurnHost.PreloadSlot.NEXT
        val behindSlot = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val next = EpubPageTarget(0, 5)
        val followup = EpubPageTarget(0, 6)
        val commits = mutableListOf<EpubPageTarget>()
        var lookaheads = 0
        host.onCurlLookahead = { lookaheads++ }
        host.onPageCommit = { _, target, count ->
            commits += target
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        host.markPreloadLoading(nextSlot, next, 98)
        host.markPreloadReady(nextSlot, next, 98, 5, 8, host.nextWebView)
        assertTrue(host.requestTurn(1))
        host.markPreloadLoading(behindSlot, followup, 99)
        host.markPreloadReady(behindSlot, followup, 99, 6, 8, host.previousWebView)
        val automaticLookaheads = lookaheads

        event(MotionEvent.ACTION_DOWN, 350f, 0, y = 600f)
        event(MotionEvent.ACTION_MOVE, 150f, 64, y = 480f)

        assertEquals(listOf(next), commits)
        assertTrue(host.ownsPage(host.nextWebView))
        assertTrue(lookaheads > automaticLookaheads)
        assertTrue(host.canPrepareWarmPage(nextSlot, allowUnreadyAhead = true))
        assertFalse(host.canPrepareWarmPage(nextSlot))
        event(MotionEvent.ACTION_UP, 50f, 110, y = 350f)
        renderCurlFrames()
        assertEquals(listOf(next, followup), commits)
    }

    @Test @Config(sdk = [28]) fun followupBecomingReadyTakesOverTheAlreadyHeldSwipe() {
        finishFontRepagination(mode = "curl")
        val nextSlot = EpubPageTurnHost.PreloadSlot.NEXT
        val stagingSlot = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val next = EpubPageTarget(0, 5)
        val followup = EpubPageTarget(0, 6)
        val commits = mutableListOf<EpubPageTarget>()
        host.onPageCommit = { _, target, count ->
            commits += target
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        host.markPreloadLoading(nextSlot, next, 98)
        host.markPreloadReady(nextSlot, next, 98, next.pageIndex, 8, host.nextWebView)
        assertTrue(host.requestTurn(1))
        host.markPreloadLoading(stagingSlot, followup, 99)

        event(MotionEvent.ACTION_DOWN, 350f, 0, y = 600f)
        event(MotionEvent.ACTION_MOVE, 150f, 64, y = 480f)
        assertTrue(commits.isEmpty())

        val staged = host.previousWebView
        host.markPreloadReady(stagingSlot, followup, 99, followup.pageIndex, 8, staged)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(next), commits)
        assertSame(staged, host.nextWebView)
        assertTrue(host.ownsPage(staged))
        assertTrue(ShadowLog.getLogsForTag("EpubTurnTrace").any { "ev=turn_start src=swipe " in it.msg })
        event(MotionEvent.ACTION_UP, 50f, 110, y = 350f)
        renderCurlFrames()
        assertEquals(listOf(next, followup), commits)
    }

    @Test @Config(sdk = [28]) fun sameDirectionHeldSwipeDoesNotBlockQueuedTurnThenTakesOver() {
        finishFontRepagination(mode = "curl")
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        val next = EpubPageTarget(0, 5)
        val afterNext = EpubPageTarget(0, 6)
        val commits = mutableListOf<EpubPageTarget>()
        host.onPageCommit = { _, target, count ->
            commits += target
            host.setCurrentPage(target.chapterIndex, target.pageIndex, count)
        }
        host.markPreloadLoading(slot, next, 98)
        event(MotionEvent.ACTION_DOWN, 350f, 0, y = 600f)
        event(MotionEvent.ACTION_MOVE, 70f, 80, y = 365f)
        event(MotionEvent.ACTION_UP, 50f, 120, y = 350f)
        event(MotionEvent.ACTION_DOWN, 350f, 150, y = 600f)
        event(MotionEvent.ACTION_MOVE, 270f, 200, y = 515f)
        host.markPreloadReady(slot, next, 98, 5, 8, host.nextWebView)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.ownsPage(host.nextWebView))
        renderCurlFrames()
        assertEquals(listOf(next), commits)

        host.markPreloadLoading(slot, afterNext, 99)
        host.markPreloadReady(slot, afterNext, 99, 6, 8, host.nextWebView)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(host.ownsPage(host.nextWebView))
        assertTrue(ShadowLog.getLogsForTag("EpubTurnTrace").any { "ev=turn_start src=swipe " in it.msg })
        event(MotionEvent.ACTION_MOVE, 70f, 710, y = 365f)
        event(MotionEvent.ACTION_UP, 50f, 735, y = 350f)
        renderCurlFrames()
        assertEquals(listOf(next, afterNext), commits)
    }

    private fun renderCurlFrames() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            host.computeScroll()
            frame().recycle()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun preparedNeighbourStillUsesTheSelectedAnimationAfterReflow() {
        finishFontRepagination()
        val target = EpubPageTarget(0, 5)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, target, 99)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, target, 99, 5, 8, host.nextWebView)
        host.onPageCommit = { _, page, count -> host.setCurrentPage(page.chapterIndex, page.pageIndex, count) }
        assertTrue(host.requestTurn(1))
        assertFalse(host.hasPendingPageHandoff())
        SystemClock.sleep(500)
        repeat(3) { host.computeScroll(); frame().recycle() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(target, host.currentPageTarget())
    }

    @Test fun staleNeighbourCannotTurnBackToAnObsoletePageWhileItsReplacementIsDelayed() {
        finishFontRepagination()
        val stale = EpubPageTarget(0, 2)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, stale, 99)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, stale, 99, 2, 8, host.nextWebView)
        assertTrue(host.requestTurn(1))
        assertTrue(host.isAwaitingPage(0, 5))
    }

    @Test fun currentChapterFastTurnNeverCrossesTheChapterBoundary() {
        finishFontRepagination(page = 7)
        val requested = mutableListOf<EpubPageTarget>()
        host.onSlideLookaheadRequested = { _, target -> requested.add(target) }
        assertTrue(host.requestTurn(1))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.hasPendingPageHandoff())
        assertTrue(requested.contains(EpubPageTarget(1, 0)))
        assertFalse(shadowOf(host.activeWebView).lastEvaluatedJavascript?.contains("goToPage") == true)
    }

    @Test fun repaginationKeepsThePreviewAboveTheWebViewUntilTheNewFrame() {
        fun visibleColor() = frame().let { bitmap ->
            bitmap.getPixel(200, 400).also { bitmap.recycle() }
        }

        host.beginLayoutPreview()
        val preview = (host.getChildAt(host.childCount - 1) as PageBitmapSource).pageBitmap!!
        // Resetting the page slots must not expose a partially drawn new page.
        host.activeWebView.background = ColorDrawable(Color.MAGENTA)
        host.invalidateLayout()
        assertEquals(Color.RED, visibleColor())
        assertFalse(preview.isRecycled)

        host.setCurrentPage(0, 2, 4, onSettled = host::endLayoutPreview)
        assertEquals(Color.MAGENTA, visibleColor())
        assertTrue(preview.isRecycled)
    }

    @Test fun swipeRequestsStaleNeighbourAgainAndResumesAfterReprepare() {
        val target = EpubPageTarget(0, 2)
        val incoming = host.nextWebView
        incoming.documentLifecycle.observeLayout(
            incoming.documentLifecycle.configurationGeneration, 3L, stable = true
        )
        assertFalse(host.hasPreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        var preparations = 0
        host.onSlideLookaheadRequested = { slot, requested ->
            assertEquals(EpubPageTurnHost.PreloadSlot.NEXT, slot)
            assertEquals(target, requested)
            preparations++
            host.markPreloadLoading(slot, requested, 4)
            host.markPreloadReady(slot, requested, 4, 2, 3, incoming)
        }
        event(MotionEvent.ACTION_DOWN, 200f, 0)
        event(MotionEvent.ACTION_MOVE, 130f, 100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, preparations)
        assertTrue(host.hasPreparedPage(EpubPageTurnHost.PreloadSlot.NEXT, target))
        assertTrue(host.ownsPage(host.activeWebView))
        val bitmap = frame()
        assertEquals(Color.BLUE, bitmap.getPixel(399, 400))
        bitmap.recycle()
    }

    @Test fun preparedLastPageMatchesSentinelWithoutPreparingAgain() {
        val slot = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val lastPage = EpubPageTarget(0, Int.MAX_VALUE)
        host.markPreloadLoading(slot, lastPage, 6)
        host.markPreloadReady(slot, lastPage, 6, 2, 3, host.previousWebView)
        assertTrue(host.hasPreparedPage(slot, lastPage))
        assertTrue(host.hasPreparedPage(slot, EpubPageTarget(0, 2)))
        assertFalse(host.hasPreparedPage(slot, EpubPageTarget(0, 1)))
        host.previousWebView.documentLifecycle.beginDocument()
        assertFalse(host.hasPreparedPage(slot, lastPage))
    }

    @Test fun scrollingChapterFadeDrawsMovingSheetsBeforeCommitting() {
        host.setNativePagingEnabled(false)
        prepareCrossChapterTurn()
        host.setPageBackgroundColor(Color.WHITE)
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        val target = EpubPageTarget(1, 0)
        val outgoing = host.activeWebView
        val incoming = host.nextWebView
        var commits = 0
        assertTrue(host.fadePreparedChapter(slot, target, onComplete = {
            assertTrue(host.promotePreparedPage(slot, target, 1))
            commits++
        }, onInvalidated = { fail("stable documents must not cancel") }))
        assertTrue(host.ownsPage(outgoing))
        assertTrue(host.ownsPage(incoming))
        host.setCurrentPage(0, 1, 3) { fail("late scroll callback must not commit during the fade") }
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        fun pixel() = frame().let { bitmap -> bitmap.getPixel(200, 400).also { bitmap.recycle() } }
        assertEquals(Color.RED, pixel())
        SystemClock.sleep(90)
        assertEquals(Color.RED, pixel())
        assertEquals(0, commits)
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        SystemClock.sleep(180)
        assertEquals(Color.BLUE, pixel())
        SystemClock.sleep(90)
        assertEquals(Color.BLUE, pixel())
        assertEquals(0, commits)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, commits)
        assertEquals(target, host.currentPageTarget())
        assertFalse(host.ownsPage(outgoing))
        assertFalse(host.ownsPage(incoming))
        assertEquals(Color.BLUE, pixel())
    }

    @Test fun paginatedChapterFadeUsesConfiguredDurationWithoutMovingSheets() {
        host.setNativePagingEnabled(false)
        prepareCrossChapterTurn()
        host.setPageBackgroundColor(Color.WHITE)
        val slot = EpubPageTurnHost.PreloadSlot.NEXT
        val target = EpubPageTarget(1, 0)
        var commits = 0
        assertTrue(host.fadePreparedChapter(slot, target,
            onComplete = {
                assertTrue(host.promotePreparedPage(slot, target, 1))
                commits++
            },
            onInvalidated = { fail("stable documents must not cancel") },
            verticalMotion = false, durationMs = 800))

        fun uniformPixel(): Int {
            val bitmap = frame()
            val top = bitmap.getPixel(200, 100)
            assertEquals("fade must not slide either sheet", top, bitmap.getPixel(200, 700))
            bitmap.recycle()
            return top
        }
        assertEquals(Color.RED, uniformPixel())
        SystemClock.sleep(200)
        val early = uniformPixel()
        assertTrue(Color.red(early) > Color.blue(early))
        assertTrue(Color.blue(early) in 1..254)
        SystemClock.sleep(200)
        val midpoint = uniformPixel()
        // The 8-bit paint alpha and destination blending each round a channel.
        assertEquals(Color.red(midpoint).toFloat(), Color.blue(midpoint).toFloat(), 2f)
        assertEquals(0, Color.green(midpoint))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("duration must not use the scrolling default", 0, commits)
        SystemClock.sleep(200)
        val late = uniformPixel()
        assertTrue(Color.blue(late) > Color.red(late))
        assertTrue(Color.red(late) in 1..254)
        SystemClock.sleep(200)
        assertEquals(Color.BLUE, uniformPixel())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, commits)
        assertEquals(target, host.currentPageTarget())
    }

    @Test fun scrollingChapterFadeMovesBothSheetsThroughTheViewport() {
        host.setNativePagingEnabled(false)
        prepareCrossChapterTurn()
        host.setPageBackgroundColor(Color.WHITE)
        assertTrue(host.fadePreparedChapter(
            EpubPageTurnHost.PreloadSlot.NEXT,
            EpubPageTarget(1, 0),
            onComplete = {},
            onInvalidated = { fail("stable documents must not cancel") }
        ))

        frame().recycle()
        SystemClock.sleep(180)
        val bitmap = frame()
        val top = bitmap.getPixel(200, 100)
        val bottom = bitmap.getPixel(200, 700)
        bitmap.recycle()

        assertTrue("outgoing sheet should remain visible near the top: $top", Color.red(top) > Color.blue(top))
        assertTrue("incoming sheet should rise from the bottom: $bottom", Color.blue(bottom) > Color.red(bottom))
    }

    @Test fun cancelledScrollingFadeLeavesOriginalPageAndDropsFinalFrameCallback() {
        prepareCrossChapterTurn()
        var commits = 0
        assertTrue(host.fadePreparedChapter(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(1, 0),
            onComplete = { commits++ }, onInvalidated = { commits++ }))
        frame().recycle()
        SystemClock.sleep(400)
        frame().recycle()
        host.cancelPendingInput()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, commits)
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        val bitmap = frame()
        assertEquals(Color.RED, bitmap.getPixel(200, 400))
        bitmap.recycle()
    }

    @Test fun previousChapterFadeRejectsALateLayoutChange() {
        val slot = EpubPageTurnHost.PreloadSlot.PREVIOUS
        val target = EpubPageTarget(0, 2)
        host.setCurrentPage(1, 0, 1)
        host.markPreloadLoading(slot, target, 9)
        host.markPreloadReady(slot, target, 9, 2, 3, host.previousWebView)
        var commits = 0
        var cancelled = 0
        assertTrue(host.fadePreparedChapter(slot, target,
            onComplete = { commits++ }, onInvalidated = { cancelled++ }))
        frame().recycle()
        SystemClock.sleep(400)
        frame().recycle()
        host.previousWebView.documentLifecycle.beginDocument()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, commits)
        assertEquals(1, cancelled)
        assertEquals(EpubPageTarget(1, 0), host.currentPageTarget())
    }

    @Test fun closingDuringScrollingFadeDropsCommitAndReleasesBothPages() {
        prepareCrossChapterTurn()
        var commits = 0
        assertTrue(host.fadePreparedChapter(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(1, 0),
            onComplete = { commits++ }, onInvalidated = { commits++ }))
        frame().recycle()
        SystemClock.sleep(400)
        frame().recycle()
        host.release()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(0, commits)
        assertEquals(0, host.childCount)
        host.allWebViews().forEach { assertTrue(it.released) }
    }

    private fun prepareCrossChapterTurn() {
        host.setCurrentPage(0, 2, 3)
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(1, 0), 3)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT,
            EpubPageTarget(1, 0), 3, 0, 1, host.nextWebView)
    }

    private fun finishCrossChapterAnimation() {
        event(MotionEvent.ACTION_DOWN, 350f, 0)
        event(MotionEvent.ACTION_MOVE, 40f, 120)
        event(MotionEvent.ACTION_UP, 40f, 170)
        // Advance the animation without dispatching queued frame callbacks yet.
        SystemClock.sleep(500)
        host.computeScroll()
        host.computeScroll()
    }
}
