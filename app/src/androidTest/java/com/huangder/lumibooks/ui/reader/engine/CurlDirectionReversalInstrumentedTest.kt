package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurlDirectionReversalInstrumentedTest {
    @Test
    fun reversingFromPreviousToNextRebasesPageRolesWithoutCompleting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val previous = View(context)
            val current = View(context)
            val next = View(context)
            val width = 1080
            val height = 1920
            root.layout(0, 0, width, height)
            previous.layout(0, 0, width, height)
            current.layout(0, 0, width, height)
            next.layout(0, 0, width, height)

            val drawnPages = mutableListOf<View>()
            val surface = PageAnimationSurface(
                root = root,
                prevPageView = previous,
                curPageView = current,
                nextPageView = next,
                backgroundColorProvider = { Color.WHITE },
                directPageRenderer = { canvas: Canvas, page: View ->
                    drawnPages += page
                    canvas.drawColor(
                        when (page) {
                            previous -> Color.BLUE
                            current -> Color.RED
                            next -> Color.GREEN
                            else -> Color.WHITE
                        }
                    )
                    true
                }
            )
            val controller = CurlPageAnim(surface)
            var completionCount = 0
            controller.onCanFlip = { true }
            controller.onAnimationComplete = { completionCount++ }

            dispatch(controller, MotionEvent.ACTION_DOWN, 540f, 400f)
            dispatch(controller, MotionEvent.ACTION_MOVE, 760f, 400f)
            assertEquals(PageAnimationController.Direction.PREV, controller.currentDirection)
            assertTrue(controller.isDragging)

            drawnPages.clear()
            dispatch(controller, MotionEvent.ACTION_MOVE, 260f, 400f)
            assertEquals(PageAnimationController.Direction.NEXT, controller.currentDirection)
            assertTrue(controller.isDragging)
            assertEquals(0, completionCount)

            val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                controller.onDraw(Canvas(frame))
            } finally {
                frame.recycle()
            }
            assertTrue(drawnPages.contains(current))
            assertTrue(drawnPages.contains(next))
            assertFalse(drawnPages.contains(previous))

            dispatch(controller, MotionEvent.ACTION_CANCEL, 260f, 400f)
            controller.destroy()
        }
    }

    @Test
    fun reversalWithUnavailableTargetLeavesCurlInactiveUntilTargetIsReady() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val previous = View(context)
            val current = View(context)
            val next = View(context)
            val width = 1080
            val height = 1920
            root.layout(0, 0, width, height)
            previous.layout(0, 0, width, height)
            current.layout(0, 0, width, height)
            next.layout(0, 0, width, height)

            val surface = PageAnimationSurface(
                root = root,
                prevPageView = previous,
                curPageView = current,
                nextPageView = next,
                backgroundColorProvider = { Color.WHITE },
                directPageRenderer = { _, _ -> true }
            )
            val controller = CurlPageAnim(surface)
            var canFlipNext = false
            controller.onCanFlip = { direction ->
                direction != PageAnimationController.Direction.NEXT || canFlipNext
            }

            dispatch(controller, MotionEvent.ACTION_DOWN, 540f, 960f)
            dispatch(controller, MotionEvent.ACTION_MOVE, 760f, 960f)
            assertTrue(controller.isDragging)

            dispatch(controller, MotionEvent.ACTION_MOVE, 260f, 960f)
            assertEquals(PageAnimationController.Direction.NEXT, controller.currentDirection)
            assertFalse(controller.isDragging)

            canFlipNext = true
            dispatch(controller, MotionEvent.ACTION_MOVE, 220f, 960f)
            assertTrue(controller.isDragging)
            assertEquals(PageAnimationController.Direction.NEXT, controller.currentDirection)

            dispatch(controller, MotionEvent.ACTION_CANCEL, 220f, 960f)
            controller.destroy()
        }
    }

    private fun dispatch(controller: CurlPageAnim, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        try {
            controller.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
