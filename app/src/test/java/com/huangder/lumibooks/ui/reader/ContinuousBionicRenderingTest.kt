package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderNode
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Selection
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousBionicRenderingTest {
    private val paragraph = "The office, suddenly silent, felt different. Don't forget the coffee: " +
        "a fine afternoon awaits. An efficient reader can select every word.\n"

    @Test fun hardwareRecordingOnlyPaintsTheScrollingViewport() {
        val text = SpannableStringBuilder(BionicReadingFormatter.format(paragraph.repeat(120), true))
        val distantInk = CountingInk()
        text.setSpan(distantInk, paragraph.length * 110, paragraph.length * 111,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val view = view(text)
        val viewport = Rect(0, 0, view.width, 480)
        // Negative control: a real RecordingCanvas sees the full chapter even
        // though the Compose parent only displays one screen of this View.
        distantInk.calls = 0
        record(view)
        assertTrue("Fixture must expose full-chapter recording", distantInk.calls > 0)
        view.onSelectionViewport = { out -> out.set(viewport); true }
        distantInk.calls = 0
        record(view)
        assertEquals("Offscreen paragraph was painted while recording the first screen", 0, distantInk.calls)

        val line = view.layout.getLineForOffset(paragraph.length * 110)
        viewport.offsetTo(0, view.layout.getLineTop(line))
        record(view)
        assertTrue("Scrolling to the distant paragraph must reveal its text", distantInk.calls > 0)
    }

    @Test fun unchangedMeasureKeepsTheEnglishBionicDrawingCache() {
        val text = SpannableStringBuilder(BionicReadingFormatter.format(
            prepareReaderEnglishHyphenation(paragraph.repeat(30)), true))
        val ink = CountingInk()
        text.setSpan(ink, 0, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val view = view(text)
        view.onSelectionViewport = { out -> out.set(0, 0, 480, 480); true }
        record(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val callsAfterMeasure = ink.calls
        record(view)
        assertEquals("An unchanged parent measure re-shaped visible bionic text", callsAfterMeasure, ink.calls)
    }

    @Test fun actualTextViewScrollingCostReport() {
        val report = StringBuilder()
        for (repeats in listOf(30, 300)) for (legacyBuffer in listOf(true, false))
            for (english in listOf(false, true)) {
            val source = paragraph.repeat(repeats)
            val text = BionicReadingFormatter.format(
                if (english) prepareReaderEnglishHyphenation(source) else source, true)
            val view = view(text, legacyBuffer)
            val bitmap = Bitmap.createBitmap(view.width, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val times = List(5) { i ->
                val saved = canvas.save()
                canvas.translate(0f, -view.layout.getLineTop(20 + i).toFloat())
                val elapsed = measureNanoTime { view.draw(canvas) } / 1_000_000.0
                canvas.restoreToCount(saved)
                elapsed
            }
            report.appendLine("chars=${text.length} english=$english buffer=${view.text.javaClass.simpleName} " +
                "drawMedianMs=${times.drop(2).sorted()[1]}")
            bitmap.recycle()
        }
        File("build/reports/bionic/continuous-${android.os.Build.VERSION.SDK_INT}.txt").apply {
            parentFile!!.mkdirs()
            writeText(report.toString())
        }
    }

    @Test fun indexedLiveBufferPreservesPixelsAndDoesNotMutateCachedChapter() {
        val source = SpannableStringBuilder(BionicReadingFormatter.format(paragraph, true))
        val indexed = view(source)
        val legacy = view(source, legacyBuffer = true)
        val expected = Bitmap.createBitmap(480, indexed.height, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(480, indexed.height, Bitmap.Config.ARGB_8888)
        legacy.draw(Canvas(expected))
        indexed.draw(Canvas(actual))
        assertTrue("Changing the span index changed the text", expected.sameAs(actual))
        assertTrue(indexed.text is SpannableStringBuilder)
        assertNotSame(source, indexed.text)
        val live = indexed.text as Spannable
        Selection.setSelection(live, 4, 10)
        val annotation = ForegroundColorSpan(Color.RED)
        live.setSpan(annotation, 4, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertEquals(-1, source.getSpanStart(annotation))
        assertEquals(-1, Selection.getSelectionStart(source))
        assertEquals(4, Selection.getSelectionStart(live))
        assertEquals(10, Selection.getSelectionEnd(live))
        expected.recycle()
        actual.recycle()
    }

    @Test fun drawingWindowReusesNearbyContentAndRefreshesAfterJumpsAndResize() {
        val window = ContinuousReaderDrawingWindow()
        assertTrue(window.update(Rect(0, 1000, 480, 1480), 480, 10000))
        assertTrue(window.bounds.contains(Rect(0, 1000, 480, 1480)))
        assertFalse(window.update(Rect(0, 1100, 480, 1580), 480, 10000))
        assertTrue(window.update(Rect(0, 7000, 480, 7480), 480, 10000))
        assertTrue(window.bounds.contains(Rect(0, 7000, 480, 7480)))
        assertTrue(window.bounds.height() <= 960)
        assertTrue(window.update(Rect(0, 7000, 480, 7100), 480, 10000))
        assertTrue(window.bounds.height() <= 200)
        assertTrue(window.update(Rect(), 480, 10000))
        assertTrue(window.bounds.isEmpty)
        assertTrue(window.update(Rect(0, 9000, 600, 11000), 600, 10000))
        assertEquals(Rect(0, 8500, 600, 10000), window.bounds)
    }

    @Test fun cachedViewportKeepsPixelsAfterLiveStyleChangesAndFontReflow() {
        val source = BionicReadingFormatter.format(paragraph.repeat(5), true)
        val cached = view(source)
        fun pixels(target: ContinuousSelectableTextView): Bitmap =
            Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).also { target.draw(Canvas(it)) }
        pixels(cached).recycle() // Fill the cache before changing its source.
        val live = cached.text as Spannable
        val style = android.text.style.RelativeSizeSpan(1.2f)
        live.setSpan(style, 4, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        fun assertFreshPixels() {
            val fresh = view(live).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, cached.textSize)
                measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                layout(0, 0, measuredWidth, measuredHeight)
            }
            val expected = pixels(fresh)
            val actual = pixels(cached)
            assertTrue("Cached geometry survived a metric change", expected.sameAs(actual))
            expected.recycle(); actual.recycle()
        }
        assertFreshPixels()
        live.removeSpan(style)
        assertFreshPixels()
        cached.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 32f)
        cached.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        cached.layout(0, 0, cached.measuredWidth, cached.measuredHeight)
        assertFreshPixels()
    }

    @Test fun customScrollingPainterUsesUpdatedBaseColorButKeepsRuleColor() {
        val source = SpannableStringBuilder("ordinary text with a marked word")
        source.setSpan(ForegroundColorSpan(Color.RED), 20, 26, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val view = view(source)
        view.setTextColor(Color.WHITE)
        val bitmap = Bitmap.createBitmap(480, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        var white = 0
        var red = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 128 && Color.red(pixel) > 220 &&
                Color.green(pixel) > 220 && Color.blue(pixel) > 220) white++
            if (Color.alpha(pixel) > 128 && Color.red(pixel) > 180 &&
                Color.green(pixel) < 80 && Color.blue(pixel) < 80) red++
        }
        assertTrue("Normal scrolling text kept the old theme color", white > 20)
        assertTrue("Rule foreground color was overwritten by the base color", red > 5)
        bitmap.recycle()
    }

    private fun view(text: CharSequence, legacyBuffer: Boolean = false) = ContinuousSelectableTextView(RuntimeEnvironment.getApplication()).apply {
        layoutParams = ViewGroup.LayoutParams(480, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (legacyBuffer) setSpannableFactory(Spannable.Factory.getInstance())
        setTextColor(Color.BLACK)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f)
        setReaderText(text)
        measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun record(view: View) {
        val node = RenderNode("scrolling chapter")
        node.setPosition(0, 0, view.width, view.height)
        val canvas = node.beginRecording(view.width, view.height)
        try {
            assertTrue(canvas.isHardwareAccelerated)
            view.draw(canvas)
        } finally {
            node.endRecording()
            node.discardDisplayList()
        }
    }

    private class CountingInk : CharacterStyle() {
        var calls = 0
        override fun updateDrawState(tp: TextPaint) { calls++ }
    }
}
