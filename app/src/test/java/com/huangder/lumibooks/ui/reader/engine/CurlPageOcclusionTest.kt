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
class CurlPageOcclusionTest {
    private class SnapshotPage(context: Context, override val pageBitmap: Bitmap) :
        View(context), PageBitmapSource

    @Test
    fun nextIllustrationOnlyAppearsInRevealedAreaEvenWithTransparentCurrentPage() {
        for (reversed in listOf(false, true)) {
            withCurl(reversed, currentColor = null, nextColor = Color.BLUE) { curl ->
                drag(curl, reversed, 60f)
                draw(curl) { pixels ->
                    val coveredX = if (reversed) 360 else 40
                    val revealedX = if (reversed) 1 else 398
                    assertEquals("flat paper hides next illustration", Color.WHITE,
                        pixels.getPixel(coveredX, 200))
                    assertEquals("next illustration appears after fold passes", Color.BLUE,
                        pixels.getPixel(revealedX, 200))
                }
            }
        }
    }

    @Test
    fun transparentPreviousPageCoversCurrentIllustrationDuringBackwardTurn() {
        for (reversed in listOf(false, true)) {
            withCurl(reversed, currentColor = Color.BLUE, nextColor = null) { curl ->
                drag(curl, reversed, -160f)
                draw(curl) { pixels ->
                    val coveredX = if (reversed) 398 else 1
                    val revealedX = if (reversed) 40 else 360
                    assertEquals("previous paper hides current illustration", Color.WHITE,
                        pixels.getPixel(coveredX, 200))
                    assertEquals("current remains visible beyond previous sheet", Color.BLUE,
                        pixels.getPixel(revealedX, 200))
                }
            }
        }
    }

    @Test
    fun flatAndCurledTransparentPagesHideUnrelatedPreloadBehindAnimation() {
        withCurl(false, currentColor = null, nextColor = null) { curl ->
            assertTrue(curl.startFromTap(PageAnimationController.Direction.NEXT))
            draw(curl) { pixels ->
                assertEquals("flat fallback has paper background", Color.WHITE,
                    pixels.getPixel(40, 200))
            }
            curl.abortAnim()
            drag(curl, false, 60f)
            draw(curl) { pixels ->
                assertEquals(Color.WHITE, pixels.getPixel(40, 200))
                assertEquals(Color.WHITE, pixels.getPixel(398, 200))
            }
        }
    }

    private fun withCurl(
        reversed: Boolean,
        currentColor: Int?,
        nextColor: Int?,
        verify: (CurlPageAnim) -> Unit
    ) {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val bitmaps = listOf(null, currentColor, nextColor).map { color ->
            Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.TRANSPARENT)
                // Leave paper transparent, just like a WebView with a reader background.
                if (color != null) Canvas(this).drawRect(0f, 100f, 400f, 300f,
                    Paint().apply { this.color = color })
            }
        }
        val pages = bitmaps.map { SnapshotPage(context, it) }
        pages.forEach { root.addView(it, FrameLayout.LayoutParams(400, 800)) }
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
        val surface = PageAnimationSurface(root, pages[0], pages[1], pages[2],
            backgroundColorProvider = { Color.WHITE },
            reversePageProgressProvider = { reversed })
        val curl = CurlPageAnim(surface).apply { onCanFlip = { true } }
        try {
            verify(curl)
        } finally {
            curl.destroy()
            bitmaps.forEach(Bitmap::recycle)
        }
    }

    private fun drag(curl: CurlPageAnim, reversed: Boolean, distance: Float) {
        val x = 200f
        val target = x + if (reversed) distance else -distance
        for ((time, action, position) in listOf(
            Triple(0L, MotionEvent.ACTION_DOWN, x),
            Triple(100L, MotionEvent.ACTION_MOVE, target)
        )) {
            val event = MotionEvent.obtain(0L, time, action, position, 400f, 0)
            try { assertTrue(curl.onTouchEvent(event)) } finally { event.recycle() }
        }
        assertTrue(curl.isDragging)
    }

    private fun draw(curl: CurlPageAnim, verify: (Bitmap) -> Unit) {
        val pixels = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        try {
            pixels.eraseColor(Color.MAGENTA)
            curl.onDraw(Canvas(pixels))
            verify(pixels)
        } finally {
            pixels.recycle()
        }
    }
}
