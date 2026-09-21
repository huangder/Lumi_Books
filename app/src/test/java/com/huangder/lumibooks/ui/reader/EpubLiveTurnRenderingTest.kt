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

    @After fun tearDown() { activity.pause().stop().destroy() }

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
}
