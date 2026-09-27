package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.util.parser.EpubParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderImagePagingStabilityTest {
    @Test fun coverImageViewDoesNotResizeTheLayoutSpan() {
        val bitmap = Bitmap.createBitmap(200, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            setHasAlpha(false)
        }
        val source = BitmapDrawable(null, bitmap).apply { setBounds(0, 0, 100, 160) }
        val text = SpannableString("\uFFFC").apply {
            setSpan(ImageSpan(source, "cover.jpg"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(EpubParser.CoverPageSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val page = PageContentView(ApplicationProvider.getApplicationContext<Application>())
        repeat(4) {
            page.setPageContent(text, 0, text.length)
            val span = page.getJustifiedText()!!.getSpans(0, 1, ImageSpan::class.java).single()
            drawPage(page).recycle()
            assertEquals(Rect(0, 0, 100, 160), span.drawable.bounds)
            assertEquals(Rect(0, 0, 100, 160), source.bounds)
            page.clear()
        }
        bitmap.recycle()
    }

    @Test fun imagePageKeepsGeometryAndPixelsWhenSlotsRotateForwardAndBack() {
        // Like LazyEpubImageDrawable: not a BitmapDrawable, no ConstantState,
        // and the same source instance is retained in the chapter cache.
        val source = object : Drawable() {
            private val paint = Paint().apply { color = Color.BLUE }
            override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)
            override fun getIntrinsicWidth() = 280
            override fun getIntrinsicHeight() = 180
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = PixelFormat.OPAQUE
        }.apply { setBounds(0, 0, 280, 180) }
        val chapter = SpannableString("Before image\nSecond line\nThird line\n\uFFFC\nAfter image\n" +
            "Following page text.\n".repeat(70)).apply {
            val index = indexOf('\uFFFC')
            setSpan(ImageSpan(source, "illustration.png"), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        val views = List(6) { PageContentView(context).apply {
            configure(24f, Color.BLACK, lineHeightMult = 1f, marginLeftPx = 20f,
                marginRightPx = 20f, marginTopPx = 20f, marginBottomPx = 20f,
                textAlignment = ReaderTextAlignment.LEFT)
            setBackgroundColor(Color.WHITE)
            measurePage(this)
        } }
        val engine = PageLayoutEngine().apply {
            configure(400, 800, 24f, lineSpacingPx = 0f, lineSpacingMult = 1f,
                marginLeftPx = 20f, marginRightPx = 20f, marginTopPx = 20f, marginBottomPx = 20f,
                chapterCount = 1, textAlignment = ReaderTextAlignment.LEFT)
        }
        val pages = runBlocking { engine.layout(0, chapter) }
        assertTrue(pages.totalPages > 1)
        val manager = PageSlotManager(engine, views[0], views[1], views[2], views[3], views[4], views[5])
        manager.setChapterCount(1)
        manager.contentProvider = { chapter }
        try {
            manager.initialize(0, 0)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!manager.getNextSlot().isLoaded && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("Current slot loaded", manager.getCurSlot().isLoaded)
            assertTrue("Next slot loaded", manager.getNextSlot().isLoaded)
            val current = views[1]
            val firstFrame = drawPage(current)
            val text = current.textView.text.toString()
            val firstLayout = current.textView.layout
            val baselines = (0 until firstLayout.lineCount).map(firstLayout::getLineBaseline)
            repeat(4) { turn ->
                manager.shiftForward()
                drawPage(current).recycle()
                manager.shiftBackward()
                val frame = drawPage(current)
                assertEquals("page after round trip $turn", 0, manager.getCurSlot().pageIndex)
                assertEquals(text, current.textView.text.toString())
                val layout = current.textView.layout
                assertEquals("line baselines after round trip $turn", baselines,
                    (0 until layout.lineCount).map(layout::getLineBaseline))
                assertTrue("visible pixels after round trip $turn", firstFrame.sameAs(frame))
                assertEquals(Rect(0, 0, 280, 180), source.bounds)
                frame.recycle()
            }
            firstFrame.recycle()
        } finally {
            manager.destroy()
        }
    }

    private fun measurePage(page: PageContentView) {
        page.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, 400, 800)
    }

    private fun drawPage(page: PageContentView): Bitmap {
        measurePage(page)
        return Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).also { page.draw(Canvas(it)) }
    }
}
