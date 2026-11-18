package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderSlideVisibilityTest {
    @Test fun waveSelectedBeforeLayoutRoutesLongPressToVisibleCurrentPage() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.slotManager.backgroundPreparationEnabled = false
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val currentActions = mutableListOf<Int>()
        val nextActions = mutableListOf<Int>()
        try {
            reader.curPageView.setPageContent("Current TXT page", 0, 16)
            reader.nextPageView.setPageContent("Next TXT page", 0, 13)
            reader.curPageView.textView.setOnTouchListener { _, event ->
                currentActions += event.actionMasked
                true
            }
            reader.nextPageView.textView.setOnTouchListener { _, event ->
                nextActions += event.actionMasked
                true
            }
            // AndroidView can apply the saved animation before the first layout.
            reader.setPageTransition("wave")
            reader.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            reader.layout(0, 0, 400, 800)
            reader.draw(Canvas(bitmap))
            for ((action, time) in listOf(MotionEvent.ACTION_DOWN to 100L,
                MotionEvent.ACTION_UP to 700L)) {
                val event = MotionEvent.obtain(100L, time, action, 200f, 400f, 0)
                try { assertTrue(reader.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            assertTrue("the invisible preload must not receive the long press: $nextActions", nextActions.isEmpty())
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), currentActions)
        } finally {
            reader.animationController.abortAnim()
            reader.slotManager.destroy()
            bitmap.recycle()
        }
    }

    @Test fun pressDuringFadeDoesNotReachOverlappingPageText() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.slotManager.backgroundPreparationEnabled = false
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val currentActions = mutableListOf<Int>()
        val nextActions = mutableListOf<Int>()
        val width = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        try {
            reader.curPageView.setPageContent("Current page", 0, 12)
            reader.nextPageView.setPageContent("Next page", 0, 9)
            reader.curPageView.textView.setOnTouchListener { _, event ->
                currentActions += event.actionMasked
                true
            }
            reader.nextPageView.textView.setOnTouchListener { _, event ->
                nextActions += event.actionMasked
                true
            }
            reader.measure(width, height)
            reader.layout(0, 0, 400, 800)
            reader.setPageTransition("fade")
            val fade = reader.animationController as FadePageAnim
            fade.startFromTap(PageAnimationController.Direction.NEXT)
            reader.draw(canvas)
            assertEquals(0f, (reader.nextPageView.parent as View).translationX, 0f)

            fun touch(action: Int, down: Long, elapsed: Long) {
                val event = MotionEvent.obtain(down, down + elapsed, action, 200f, 400f, 0)
                try { assertTrue(reader.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 100L, 0L)
            fade.abortAnim()
            reader.draw(canvas)
            touch(MotionEvent.ACTION_UP, 100L, 600L)
            assertTrue(nextActions.isEmpty())
            assertTrue(currentActions.isEmpty())

            touch(MotionEvent.ACTION_DOWN, 800L, 0L)
            touch(MotionEvent.ACTION_UP, 800L, 600L)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), currentActions)
            assertTrue(nextActions.isEmpty())
        } finally {
            reader.animationController.abortAnim()
            reader.slotManager.destroy()
            bitmap.recycle()
        }
    }

    @Test fun switchingFromInstantToSlideRestoresAdjacentSpreadsForEveryDrag() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.slotManager.backgroundPreparationEnabled = false
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val specWidth = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val specHeight = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        try {
            reader.measure(specWidth, specHeight)
            reader.layout(0, 0, 400, 800)
            for (instant in listOf("none", "instant", "no_animation")) {
                reader.setPageTransition(instant)
                reader.draw(canvas)
                assertEquals(View.INVISIBLE, (reader.nextPageView.parent as View).visibility)
                reader.setPageTransition("slide")
                for (delta in listOf(-160f, 160f, -240f)) {
                    val controller = reader.animationController
                    for ((action, x) in listOf(MotionEvent.ACTION_DOWN to 200f,
                        MotionEvent.ACTION_MOVE to 200f + delta)) {
                        val event = MotionEvent.obtain(0, 100, action, x, 400f, 0)
                        try { assertTrue(controller.onTouchEvent(event)) } finally { event.recycle() }
                    }
                    reader.draw(canvas)
                    val incoming = if (delta < 0) reader.nextPageView else reader.prevPageView
                    val spread = incoming.parent as View
                    assertEquals("incoming spread after $instant, delta=$delta", View.VISIBLE, spread.visibility)
                    assertEquals(1f, spread.alpha, 0f)
                    controller.abortAnim()
                }
            }
        } finally {
            reader.animationController.abortAnim()
            reader.slotManager.destroy()
            bitmap.recycle()
        }
    }
}
