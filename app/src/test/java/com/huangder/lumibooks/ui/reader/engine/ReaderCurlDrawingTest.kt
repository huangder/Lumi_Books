package com.huangder.lumibooks.ui.reader.engine

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderCurlDrawingTest {
    @Test fun hiddenPageRecordsItsInkAgainAfterHardwareDisplayListsWereCached() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val reader = ReadView(activity.get())
        reader.slotManager.backgroundPreparationEnabled = false
        activity.get().setContentView(reader)
        val ink = object : View(activity.get()) {
            var draws = 0
            override fun onDraw(canvas: Canvas) {
                draws++
                canvas.drawColor(Color.BLUE)
            }
        }
        reader.nextPageView.addView(ink, FrameLayout.LayoutParams(80, 80))
        val frame = CurlPageFrame()
        try {
            reader.setPageTransition("curl")
            layout(reader, 400, 800)
            val attachInfo = ReflectionHelpers.getField<Any>(ink, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
            val spread = reader.nextPageView.parent as View
            spread.translationX = 400f
            // Simulate the ordinary idle interval: child display lists already exist.
            record { spread.draw(it) }
            assertTrue(ink.draws > 0)
            val cachedDraws = ink.draws
            frame.capture(spread)
            val surface = surface(reader)
            val directRenderer = ReflectionHelpers.getField<Any>(surface, "directPageRenderer")
            ReflectionHelpers.setField(surface, "directPageRenderer", null)
            try {
                record { frame.draw(surface, it, Paint(), Rect(0, 0, 400, 800)) }
                assertEquals("ordinary hardware draw reuses the live child's display list", cachedDraws, ink.draws)
            } finally {
                ReflectionHelpers.setField(surface, "directPageRenderer", directRenderer)
            }
            record { frame.draw(surface, it, Paint(), Rect(0, 0, 400, 800)) }
            assertTrue("curl must own ink commands rather than reference a cached hidden child", ink.draws > cachedDraws)
            assertEquals(0f, spread.alpha, 0f)
            assertEquals(400f, spread.translationX, 0f)
        } finally {
            frame.clear()
            reader.slotManager.destroy()
            activity.pause().stop().destroy()
        }
    }

    @Test fun bothHiddenNeighboursRetainTextInSingleAndTwoPageCurlFrames() {
        for (twoPage in listOf(false, true)) {
            val reader = ReadView(RuntimeEnvironment.getApplication())
            reader.slotManager.backgroundPreparationEnabled = false
            val width = if (twoPage) 1200 else 400
            val output = Bitmap.createBitmap(width, 800, Bitmap.Config.ARGB_8888)
            val frame = CurlPageFrame()
            try {
                reader.configure(24f, "day", 1, 0, 0, twoPageSpread = twoPage)
                reader.setPageTransition("curl")
                layout(reader, width, 800)
                for (slot in listOf(reader.slotManager.getPrevSlot(), reader.slotManager.getNextSlot())) {
                    val text = "The next page must be visible during a slow page turn. ".repeat(8)
                    slot.contentView.setPageContent(text, 0, text.length)
                    if (twoPage) slot.rightContentView!!.setPageContent(text, 0, text.length)
                    val spread = slot.contentView.parent as View
                    spread.translationX = if (slot.contentView === reader.nextPageView) width.toFloat() else -width.toFloat()
                    // Capture before the new text's requested layout pass as well.
                    frame.capture(spread)
                    output.eraseColor(Color.MAGENTA)
                    frame.draw(surface(reader), Canvas(output), Paint(), Rect(0, 0, width, 800))
                    fun inkIn(left: Int, right: Int) = (left until right).any { x ->
                        (0 until 800).any { y ->
                            val color = output.getPixel(x, y)
                            Color.red(color) < 100 && Color.green(color) < 100 && Color.blue(color) < 100
                        }
                    }
                    assertTrue("hidden neighbour has readable ink, twoPage=$twoPage", inkIn(0, if (twoPage) width / 2 else width))
                    if (twoPage) assertTrue("right page has readable ink", inkIn(width / 2, width))
                    assertEquals(0f, spread.alpha, 0f)
                    frame.clear()
                }
            } finally {
                frame.clear()
                output.recycle()
                reader.slotManager.destroy()
            }
        }
    }

    @Test fun directRendererReceivesLaidOutChildrenOnFirstCapture() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val page = FrameLayout(context)
        val child = View(context)
        page.addView(child, FrameLayout.LayoutParams(100, 200))
        root.addView(page, FrameLayout.LayoutParams(400, 800))
        // The root is sized, but the newly supplied page has never been laid out.
        layout(root, 400, 800)
        page.addView(View(context), FrameLayout.LayoutParams(20, 30))
        var drew = false
        val surface = PageAnimationSurface(root, page, page, page, { Color.WHITE },
            directPageRenderer = { _, target ->
                assertFalse(target.isLayoutRequested)
                assertEquals(100, child.width)
                assertEquals(30, page.getChildAt(1).height)
                drew = true
                true
            })
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val frame = CurlPageFrame()
        try {
            frame.capture(page)
            frame.draw(surface, Canvas(bitmap), Paint(), Rect(0, 0, 400, 800))
            assertTrue(drew)
        } finally {
            frame.clear()
            bitmap.recycle()
        }
    }

    private fun surface(reader: ReadView): PageAnimationSurface {
        val field = ReadView::class.java.getDeclaredField("animationSurface\$delegate").apply { isAccessible = true }
        return (field.get(reader) as Lazy<*>).value as PageAnimationSurface
    }

    private fun record(draw: (Canvas) -> Unit) {
        val node = RenderNode("curl test").apply { setPosition(0, 0, 400, 800) }
        val canvas = node.beginRecording(400, 800)
        try { draw(canvas) } finally { node.endRecording(); node.discardDisplayList() }
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
