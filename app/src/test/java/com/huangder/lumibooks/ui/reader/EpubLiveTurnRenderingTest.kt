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

    private fun event(action: Int, x: Float, elapsed: Long) {
        val event = MotionEvent.obtain(down, down + elapsed, action, x, 400f, 0)
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
        host.markPreloadLoading(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(1, 0), 3)
        host.markPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT, EpubPageTarget(1, 0), 3, 0, 1, host.nextWebView)
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

    @Test fun scrollingChapterFadeDrawsIntermediateOpacityBeforeCommitting() {
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
        val fadingOut = pixel()
        assertEquals(255, Color.red(fadingOut))
        assertTrue(Color.green(fadingOut) in 1..254)
        assertEquals(Color.green(fadingOut), Color.blue(fadingOut))
        assertEquals(0, commits)
        assertEquals(EpubPageTarget(0, 2), host.currentPageTarget())
        SystemClock.sleep(180)
        val fadingIn = pixel()
        assertEquals(255, Color.blue(fadingIn))
        assertTrue(Color.red(fadingIn) in 1..254)
        assertEquals(Color.red(fadingIn), Color.green(fadingIn))
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
