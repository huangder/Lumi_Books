package com.huangder.lumibooks.ui.reader.engine

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PaperCurlLifecycleTest {
    private fun fixture(test: (CurlPageAnim, List<Bitmap>, RenderResourcePool<Bitmap>) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pool = RenderResourcePool<Bitmap> { it.recycle() }
        val bitmaps = List(3) { pool.track(Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)) }
        val root = FrameLayout(activity)
        val pages = bitmaps.map { bitmap ->
            object : View(activity), PageBitmapSource, CurlFrameSource {
                override val pageBitmap = bitmap
                override fun acquireCurlFrame() = pool.acquire(bitmap)
            }.also { root.addView(it, FrameLayout.LayoutParams(40, 80)) }
        }
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 40, 80)
        val surface = PageAnimationSurface(root, pages[0], pages[1], pages[2], { Color.WHITE }).apply { gpuRenderingEnabled = false }
        val curl = CurlPageAnim(surface)
        curl.onCanFlip = { true }
        assertEquals(40, root.width)
        assertEquals(80, root.height)
        try { test(curl, bitmaps, pool) } finally { curl.destroy(); pool.destroy(); activity.finish() }
    }

    @Test fun terminalMustBeDrawnBeforeCommittingAndCannotCommitTwice() = fixture { curl, _, _ ->
        var commits = 0
        curl.onAnimationComplete = { commits++; assertEquals(PageAnimationController.Direction.NEXT, curl.currentDirection) }
        assertTrue(curl.startFromTap(PageAnimationController.Direction.NEXT))
        assertTrue(curl.holdRunningFlipAtEnd())
        assertEquals(0, commits)
        val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        curl.onDraw(Canvas(bitmap)); curl.onDraw(Canvas(bitmap))
        assertEquals(0, commits)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, commits)
        assertFalse(curl.isRunning)
        bitmap.recycle()
    }

    @Test fun abortInvalidatesPostedCompletionAndReleasesRetiredFrames() = fixture { curl, images, pool ->
        var commits = 0
        curl.onAnimationComplete = { commits++ }
        curl.startFromTap(PageAnimationController.Direction.NEXT)
        pool.retire(images[1]); pool.retire(images[2])
        assertFalse(images[1].isRecycled)
        curl.holdRunningFlipAtEnd()
        val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        curl.onDraw(Canvas(bitmap))
        curl.abortAnim()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, commits)
        assertTrue(images[1].isRecycled)
        assertTrue(images[2].isRecycled)
        bitmap.recycle()
    }

    @Test fun missingTargetDoesNotLeaseFramesOrAdvanceAndBusyInputWaits() = fixture { curl, images, pool ->
        curl.onCanFlip = { false }
        assertFalse(curl.startFromTap(PageAnimationController.Direction.NEXT))
        assertTrue(images.all(pool::canReuse))
        curl.onCanFlip = { true }
        var commits = 0
        curl.onAnimationComplete = { commits++ }
        assertTrue(curl.startFromTap(PageAnimationController.Direction.PREV))
        // A running tap turn refuses another tap until it is handed over.
        assertFalse(curl.startFromTap(PageAnimationController.Direction.NEXT))
        assertEquals(PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY,
            curl.completeRunningFlipForGestureHandoff())
        assertEquals(1, commits)
        assertFalse(curl.isRunning)
        assertTrue(images.all(pool::canReuse))
        assertTrue(curl.startFromTap(PageAnimationController.Direction.NEXT))
    }

    @Test fun tapThatStartsATurnKeepsItsDirectionUntilTheTurnCompletes() = fixture { curl, _, _ ->
        var completed: PageAnimationController.Direction? = null
        curl.onTapRight = { curl.startFromTap(PageAnimationController.Direction.NEXT) }
        curl.onAnimationComplete = { completed = curl.currentDirection }
        for ((action, time) in listOf(android.view.MotionEvent.ACTION_DOWN to 0L, android.view.MotionEvent.ACTION_UP to 60L)) {
            val event = android.view.MotionEvent.obtain(0L, time, action, 36f, 40f, 0)
            try { curl.onTouchEvent(event) } finally { event.recycle() }
        }
        assertEquals(PageAnimationController.Direction.NEXT, curl.currentDirection)
        assertTrue(curl.holdRunningFlipAtEnd())
        val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        curl.onDraw(Canvas(bitmap))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PageAnimationController.Direction.NEXT, completed)
        bitmap.recycle()
    }

    @Test fun queuedTapExpeditesTheRunningTurnInsteadOfJumping() = fixture { curl, _, _ ->
        var commits = 0
        curl.onAnimationComplete = { commits++ }
        assertTrue(curl.startFromTap(PageAnimationController.Direction.NEXT))
        assertEquals(PageAnimationController.RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY,
            curl.completeRunningFlipForNewInput())
        assertTrue(curl.isRunning)
        assertEquals(0, commits)
        // Tapping again while it catches up hands the page over now, so turns never lag behind.
        assertEquals(PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY,
            curl.completeRunningFlipForNewInput())
        assertEquals(1, commits)
        assertFalse(curl.isRunning)
    }
}
