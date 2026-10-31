package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PageAnimationCompletionTest {
    private fun surface(): PageAnimationSurface {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context).apply { layout(0, 0, 400, 800) }
        return PageAnimationSurface(root, View(context), View(context), View(context), { 0 })
    }

    private fun event(controller: PageAnimationController, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(0L, time, action, x, y, 0)
        try { controller.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun finish(controller: PageAnimationController) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        repeat(3) { controller.computeScroll() }
    }

    @Test fun tapCallbackCanStartSlideWithoutLosingDirection() {
        val controller = SlidePageAnim(surface())
        assertTapCallbackPreservesTurn(controller) { controller.startFromTap(it) }
    }

    @Test fun tapCallbackCanStartScrollWithoutLosingDirection() {
        val controller = ScrollPageAnim(surface())
        assertTapCallbackPreservesTurn(controller) { controller.startFromTap(it) }
    }

    private fun assertTapCallbackPreservesTurn(
        controller: PageAnimationController,
        start: (PageAnimationController.Direction) -> Unit
    ) {
        var completedDirection = PageAnimationController.Direction.NONE
        controller.onTapRight = { start(PageAnimationController.Direction.NEXT) }
        controller.onAnimationComplete = { completedDirection = controller.currentDirection }
        event(controller, MotionEvent.ACTION_DOWN, 380f, 400f, 0L)
        event(controller, MotionEvent.ACTION_UP, 380f, 400f, 40L)
        assertTrue(controller.isRunning)
        assertEquals(PageAnimationController.Direction.NEXT, controller.currentDirection)
        finish(controller)
        assertEquals(PageAnimationController.Direction.NEXT, completedDirection)
        assertFalse(controller.isCompletingTurn)
    }

    @Test fun fullyDraggedSlideStillCommitsExactlyOnce() {
        assertFullyDraggedTurn(SlidePageAnim(surface()), 400f, 400f, 0f, 400f)
    }

    @Test fun fullyDraggedScrollStillCommitsExactlyOnce() {
        assertFullyDraggedTurn(ScrollPageAnim(surface()), 200f, 800f, 200f, 0f)
    }

    private fun assertFullyDraggedTurn(
        controller: PageAnimationController, startX: Float, startY: Float, endX: Float, endY: Float
    ) {
        var completed = 0
        controller.onCanFlip = { true }
        controller.onAnimationComplete = {
            assertEquals(PageAnimationController.Direction.NEXT, controller.currentDirection)
            completed++
        }
        event(controller, MotionEvent.ACTION_DOWN, startX, startY, 0L)
        event(controller, MotionEvent.ACTION_MOVE, endX, endY, 40L)
        event(controller, MotionEvent.ACTION_UP, endX, endY, 80L)
        finish(controller)
        assertEquals(1, completed)
        assertFalse(controller.isRunning)
        assertFalse(controller.isCompletingTurn)
    }
}
