package com.huangder.lumibooks.ui.reader

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubBackgroundTurnRenderingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun backgroundImageKeepsLivePageGeometryInBothDirectionsAndAxes() {
        for (transition in listOf("slide", "scroll")) {
            for (reverse in if (transition == "slide") listOf(false, true) else listOf(false)) {
                for (direction in listOf(-1, 1)) {
                    withHost(transition) { host, image ->
                        host.setReverseAxis(reverse)
                        host.setReaderBackgroundImage(image.path, 0.5f, 0f)
                        // Bypass asynchronous image loading; exercise the real ImageView composition.
                        val background = (0 until host.childCount).map(host::getChildAt)
                            .filterIsInstance<ImageView>().single { it.alpha == 0.5f }
                        background.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                        background.layout(0, 0, 400, 800)
                        background.setImageDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
                        val vertical = transition == "scroll"
                        val current = Landmarks(Color.RED, vertical)
                        val destination = Landmarks(Color.BLUE, vertical, secondary = true)
                        host.activeWebView.background = current
                        val incoming = if (direction > 0) host.nextWebView else host.previousWebView
                        incoming.background = destination
                        preparePages(host)

                        val down = SystemClock.uptimeMillis()
                        fun touch(action: Int, x: Float, y: Float, elapsed: Long) {
                            MotionEvent.obtain(down, down + elapsed, action, x, y, 0).also {
                                host.dispatchTouchEvent(it)
                                it.recycle()
                            }
                        }
                        val delta = direction * if (reverse && !vertical) 100f else -100f
                        touch(MotionEvent.ACTION_DOWN, 200f, 400f, 0)
                        touch(MotionEvent.ACTION_MOVE,
                            if (vertical) 200f else 200f + delta,
                            if (vertical) 400f + delta else 400f, 200)
                        assertTrue("turn started: $transition/$direction/$reverse", host.ownsPage(incoming))
                        val beforeDraw = destination.draws
                        val pixels = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
                        host.draw(Canvas(pixels))
                        assertTrue("destination must paint live during the turn", destination.draws > beforeDraw)
                        if (vertical) {
                            val incomingY = if (direction > 0) 799 else 0
                            assertBlue(pixels.getPixel(40, incomingY))
                            assertBlue(pixels.getPixel(59, incomingY))
                            assertFalse(isBlue(pixels.getPixel(39, incomingY)))
                            assertFalse(isBlue(pixels.getPixel(60, incomingY)))
                            assertFalse("next page cannot show through the current sheet",
                                isBlue(pixels.getPixel(310, 400)))
                        } else {
                            val incomingX = if (delta < 0) 399 else 0
                            assertBlue(pixels.getPixel(incomingX, 100))
                            assertBlue(pixels.getPixel(incomingX, 119))
                            assertFalse(isBlue(pixels.getPixel(incomingX, 99)))
                            assertFalse(isBlue(pixels.getPixel(incomingX, 120)))
                            assertFalse("next page cannot show through the current sheet",
                                isBlue(pixels.getPixel(200, 610)))
                        }
                        // Transparent parts show the same half-opacity image on both sheets.
                        val backgroundPixel = pixels.getPixel(if (delta < 0) 0 else 399, 400)
                        assertEquals(128f, Color.red(backgroundPixel).toFloat(), 1f)
                        assertEquals(Color.red(backgroundPixel), Color.green(backgroundPixel))
                        assertEquals(Color.red(backgroundPixel), Color.blue(backgroundPixel))
                        pixels.recycle()
                    }
                }
            }
        }
    }

    @Test fun changingBackgroundDoesNotDiscardPreparedPagesOrRestartTheTransition() {
        withHost("slide") { host, image ->
            preparePages(host)
            var invalidations = 0
            host.onInvalidatePreloads = { invalidations++ }
            host.setReaderBackgroundImage(image.path, 1f, 0f)
            assertTrue(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.NEXT))
            assertTrue(host.isPreloadReady(EpubPageTurnHost.PreloadSlot.PREVIOUS))
            host.setReaderBackgroundImage(null, 1f, 0f)
            assertEquals(0, invalidations)
        }
    }

    private fun withHost(transition: String, block: (EpubPageTurnHost, java.io.File) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = EpubPageTurnHost(activity.get(), Color.BLACK)
        val image = temporaryFolder.newFile("background-${System.nanoTime()}.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            activity.get().setContentView(host, android.view.ViewGroup.LayoutParams(400, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 400, 800)
            // Robolectric's WebView does not implement setFrame.
            host.allWebViews().forEach { it.right = 400; it.bottom = 800 }
            host.setTransition(transition)
            host.setBookmarkPullEnabled(false)
            block(host, image)
        } finally {
            host.release()
            activity.pause().stop().destroy()
        }
    }

    private fun preparePages(host: EpubPageTurnHost) {
        host.setCurrentPage(0, 1, 3)
        for ((slot, page, generation) in listOf(
            Triple(EpubPageTurnHost.PreloadSlot.PREVIOUS, 0, 1),
            Triple(EpubPageTurnHost.PreloadSlot.NEXT, 2, 2)
        )) {
            val target = EpubPageTarget(0, page)
            host.markPreloadLoading(slot, target, generation)
            host.markPreloadReady(slot, target, generation, page, 3, host.preloadView(slot)!!)
        }
    }

    private fun isBlue(color: Int): Boolean = Color.blue(color) > Color.red(color) + 100
    private fun assertBlue(color: Int) = assertTrue("blue landmark: ${color.toUInt().toString(16)}", isBlue(color))

    private class Landmarks(color: Int, private val vertical: Boolean, private val secondary: Boolean = false) : Drawable() {
        private val paint = Paint().apply { this.color = color }
        var draws = 0
        override fun draw(canvas: Canvas) {
            draws++
            if (vertical) canvas.drawRect(40f, 0f, 60f, 800f, paint)
            else canvas.drawRect(0f, 100f, 400f, 120f, paint)
            if (secondary) {
                if (vertical) canvas.drawRect(300f, 0f, 320f, 800f, paint)
                else canvas.drawRect(0f, 600f, 400f, 620f, paint)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
