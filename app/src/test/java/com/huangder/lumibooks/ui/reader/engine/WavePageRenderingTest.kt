package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WavePageRenderingTest {
    private class Page(context: Context, override val pageBitmap: Bitmap) : View(context), PageBitmapSource

    @Test fun sweepFeatherLeavesBothScreenEdgesBeforeTheTerminalFrame() {
        val context = RuntimeEnvironment.getApplication()
        for ((width, height) in listOf(400 to 800, 160 to 1600)) {
            for (reverse in listOf(false, true)) {
                for (direction in listOf(-1, 1)) {
                    val root = FrameLayout(context)
                    val sources = listOf(Color.BLUE, Color.RED, Color.BLUE).map { color ->
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                    }
                    val pages = sources.map { Page(context, it) }
                    pages.forEach { root.addView(it, FrameLayout.LayoutParams(width, height)) }
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    val wave = WavePageAnim(PageAnimationSurface(root, pages[0], pages[1], pages[2],
                        { Color.WHITE }, { reverse }))
                    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val sign = direction * if (reverse) 1f else -1f
                    try {
                        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, width / 2f, height / 2f, 0)
                        wave.onTouchEvent(down)
                        down.recycle()
                        for (progress in listOf(0.5f, 0.998f)) {
                            val move = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE,
                                width / 2f + sign * width * progress, height / 2f, 0)
                            wave.onTouchEvent(move)
                            move.recycle()
                            wave.onDraw(Canvas(output))
                            if (progress == 0.5f) {
                                assertTrue(Color.red(output.getPixel(width / 2, height / 4)) in 1..254)
                            } else {
                                for (y in 0 until height) {
                                    for (x in listOf(0, width / 2, width - 1)) {
                                        val residual = Color.red(output.getPixel(x, y))
                                        assertTrue("residual=$residual at $x/$y, progress=$progress, sign=$sign",
                                            residual <= 4)
                                    }
                                }
                            }
                        }
                    } finally {
                        wave.abortAnim()
                        sources.forEach { it.recycle() }
                        output.recycle()
                    }
                }
            }
        }
    }

    @Test fun sweepKeepsTopAndBottomLandmarksAtTheirPixelCoordinatesAcrossCanvasDensities() {
        for (reverse in listOf(false, true)) {
            for (direction in listOf(-1, 1)) {
                val context = RuntimeEnvironment.getApplication()
                val root = FrameLayout(context)
                val bitmaps = listOf(Color.BLUE, Color.RED, Color.BLUE).map { color ->
                    Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
                        density = 480
                        eraseColor(Color.WHITE)
                        val paint = Paint().apply { this.color = color }
                        Canvas(this).apply {
                            drawRect(0f, 80f, 400f, 100f, paint)
                            drawRect(0f, 700f, 400f, 720f, paint)
                        }
                    }
                }
                val pages = bitmaps.map { Page(context, it) }
                pages.forEach { root.addView(it, FrameLayout.LayoutParams(400, 800)) }
                root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 400, 800)
                val wave = WavePageAnim(PageAnimationSurface(root, pages[0], pages[1], pages[2],
                    { Color.WHITE }, { reverse })).apply { onCanFlip = { true } }
                val output = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
                    // The destination canvas may have a different density from intermediate bitmaps.
                    density *= 2
                }
                try {
                    val sign = direction * if (reverse) 1f else -1f
                    fun move(action: Int, distance: Float, time: Long) {
                        MotionEvent.obtain(0, time, action, 200f + sign * distance, 400f, 0).also {
                            assertTrue(wave.onTouchEvent(it))
                            it.recycle()
                        }
                    }
                    move(MotionEvent.ACTION_DOWN, 0f, 0)
                    for (distance in listOf(40f, 220f, 400f)) {
                        move(MotionEvent.ACTION_MOVE, distance, 100)
                        output.eraseColor(Color.MAGENTA)
                        wave.onDraw(Canvas(output))
                        val incomingX = if (sign < 0) 399 else 0
                        assertLandmarks(output, incomingX)
                        if (distance < 400f) {
                            assertLandmarks(output, 399 - incomingX)
                        } else {
                            assertEquals(Color.BLUE, output.getPixel(incomingX, 80))
                            assertEquals(Color.BLUE, output.getPixel(incomingX, 700))
                        }
                    }
                } finally {
                    wave.abortAnim()
                    bitmaps.forEach {
                        assertFalse("animation must not recycle source pages", it.isRecycled)
                        it.recycle()
                    }
                    output.recycle()
                }
            }
        }
    }

    private fun assertLandmarks(pixels: Bitmap, x: Int) {
        // Early sweep frames blend the pages, but must not shift or resize the rows.
        for (y in listOf(79, 100, 699, 720)) assertEquals("paper at $x/$y", Color.WHITE, pixels.getPixel(x, y))
        for (y in listOf(80, 99, 700, 719)) {
            val pixel = pixels.getPixel(x, y)
            assertEquals("unscaled landmark at $x/$y", 0, Color.green(pixel))
        }
    }
}
