package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BionicReaderRenderingTest {
    private val sample = "The office, suddenly silent, felt different. Don't forget the coffee: " +
        "a fine afternoon awaits. An efficient reader can select every word.\n"

    @Test fun bionicPrefixesKeepPlatformShapingAndExistingStyles() {
        for (raw in listOf(sample, "office affinity AV Cafe\u0301 nai\u0308ve")) {
            val source = SpannableStringBuilder(raw).apply {
                setSpan(StyleSpan(Typeface.ITALIC), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.RED), 7, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(38), 7, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val text = BionicReadingFormatter.format(source, true) as Spanned
            assertEquals(raw, text.toString())
            val layout = layout(text)
            val expected = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            layout.draw(Canvas(expected))
            ReaderTextPainter().draw(Canvas(actual), layout, text, ReaderLineGeometry(layout, text, 0))
            assertTrue("Bionic style transitions changed glyphs: $raw", expected.sameAs(actual))
            expected.recycle()
            actual.recycle()
        }
    }

    @Test fun longBionicChapterDrawsOnlyVisibleLines() {
        val report = StringBuilder()
        for (enabled in listOf(false, true)) {
            val raw = sample.repeat(300)
            lateinit var text: Spanned
            val formatNanos = measureNanoTime {
                text = SpannableStringBuilder(BionicReadingFormatter.format(raw, enabled))
            }
            lateinit var layout: StaticLayout
            val layoutNanos = measureNanoTime { layout = layout(text) }
            val bitmap = Bitmap.createBitmap(layout.width, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(0f, -layout.getLineTop(500).toFloat())
            val visible = readerVisibleLines(canvas, layout)
            assertEquals(500, visible.first)
            assertTrue(visible.count() < 20)
            val painter = ReaderTextPainter()
            val samples = List(7) {
                measureNanoTime {
                    painter.draw(canvas, layout, text, ReaderLineGeometry(layout, text, 0))
                } / 1_000_000.0
            }
            assertTrue((0 until bitmap.width).any { x ->
                (0 until bitmap.height).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
            })
            report.appendLine("bionic=$enabled chars=${text.length} formatMs=${formatNanos / 1_000_000.0} " +
                "layoutMs=${layoutNanos / 1_000_000.0} drawMedianMs=${samples.drop(2).sorted()[2]}")
            bitmap.recycle()
        }
        File("build/reports/bionic/timings-${android.os.Build.VERSION.SDK_INT}.txt").apply {
            parentFile!!.mkdirs()
            writeText(report.toString())
        }
    }

    @Test fun drawingBionicWordsKeepsSpanQueriesProportionalToVisibleText() {
        val source = BionicReadingFormatter.format(sample.repeat(30), true) as Spanned
        val text = SpanQueryCountingText(source)
        val layout = layout(source)
        val bitmap = Bitmap.createBitmap(layout.width, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val visible = readerVisibleLines(canvas, layout)
        val visibleCharacters = layout.getLineEnd(visible.last) - layout.getLineStart(visible.first)
        ReaderTextPainter().draw(canvas, layout, text, ReaderLineGeometry(layout, text, 0))
        // A long line with many bold prefixes must not rescan the complete line
        // for each prefix/suffix. Use a work budget instead of a flaky time limit.
        assertTrue("${text.queries} span queries for $visibleCharacters visible characters",
            text.queries <= visibleCharacters * 6)
        bitmap.recycle()
    }

    private class SpanQueryCountingText(private val source: Spanned) : Spanned by source {
        var queries = 0
        override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
            queries++
            return source.getSpans(start, end, type)
        }
        override fun toString(): String = source.toString()
    }

    private fun layout(text: CharSequence): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.BLACK }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, 480)
            .setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build()
    }
}
