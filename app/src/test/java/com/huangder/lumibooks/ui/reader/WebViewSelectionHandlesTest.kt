package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebViewSelectionHandlesTest {
    @Test fun adjustedSelectionPaintsBothEndpointsAndDragsInCssCoordinates() {
        val host = View(ApplicationProvider.getApplicationContext())
        val moves = mutableListOf<Triple<Boolean, Float, Float>>()
        val handles = WebViewSelectionHandles(host) { start, x, y -> moves += Triple(start, x, y) }
        handles.update(JSONObject("""{"start":{"x":20,"top":30,"bottom":40},"end":{"x":120,"top":30,"bottom":40}}"""), 2f)
        assertTrue(handles.isVisible)
        val radius = 8f * host.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)
        handles.draw(Canvas(bitmap))
        for ((start, x) in listOf(true to 40f, false to 240f)) {
            assertEquals(Color.rgb(68, 138, 255), bitmap.getPixel(x.toInt(), (80f + radius).toInt()))
            fun touch(action: Int, px: Float, py: Float): Boolean =
                MotionEvent.obtain(0, 1, action, px, py, 0).let {
                    try { handles.onTouch(it) } finally { it.recycle() }
                }
            assertTrue(touch(MotionEvent.ACTION_DOWN, x, 80f + radius))
            assertTrue(touch(MotionEvent.ACTION_MOVE, x + 10f, 90f + radius))
            assertEquals(Triple(start, (x + 10f) / 2f, 40f), moves.last())
            assertTrue(touch(MotionEvent.ACTION_UP, x + 10f, 90f + radius))
        }
        handles.clear()
        assertFalse(handles.isVisible)
        bitmap.recycle()
    }
}
