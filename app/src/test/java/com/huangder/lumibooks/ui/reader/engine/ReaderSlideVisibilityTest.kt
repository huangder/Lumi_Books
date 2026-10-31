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
