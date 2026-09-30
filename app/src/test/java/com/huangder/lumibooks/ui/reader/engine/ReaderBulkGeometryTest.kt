package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.LeadingMarginSpan
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderBulkGeometryTest {
    private val sample = "The office, suddenly silent, felt different. “Hello!” Don't forget the coffee: " +
        "an efficient reader can select every word near the right edge.\n"

    @Test fun bionicDrawingAndCaretsMatchThePerCharacterFallback() {
        for (spacing in listOf(0f, 0.08f, -0.03f)) {
            for (justification in listOf(Layout.JUSTIFICATION_MODE_NONE,
                Layout.JUSTIFICATION_MODE_INTER_WORD, Layout.JUSTIFICATION_MODE_INTER_CHARACTER)) {
                val text = fixture(sample.repeat(3))
                val native = layout(text, spacing, justification)
                val bulk = CountingLayout(native)
                val fallback = CountingLayout(native, rejectBulk = true)
                val actualGeometry = ReaderLineGeometry(bulk, text, justification)
                val expectedGeometry = ReaderLineGeometry(fallback, text, justification)
                for (line in 0 until native.lineCount) {
                    val start = native.getLineStart(line)
                    val end = readerLineContentEnd(text, start, native.getLineEnd(line))
                    for (offset in start until end) {
                        val expected = expectedGeometry.horizontalRange(line, offset, offset + 1)!!
                        val actual = actualGeometry.horizontalRange(line, offset, offset + 1)!!
                        assertEquals("left: $spacing/$justification/$offset", expected.left, actual.left, 0.05f)
                        assertEquals("right: $spacing/$justification/$offset", expected.right, actual.right, 0.05f)
                    }
                }
                val expected = Bitmap.createBitmap(native.width, native.height, Bitmap.Config.ARGB_8888)
                val actual = Bitmap.createBitmap(native.width, native.height, Bitmap.Config.ARGB_8888)
                ReaderTextPainter().draw(Canvas(expected), fallback, text, expectedGeometry)
                ReaderTextPainter().draw(Canvas(actual), bulk, text, actualGeometry)
                assertTrue("Pixels changed at $spacing/$justification", expected.sameAs(actual))
                if (spacing == 0f) {
                    assertTrue("Bulk path still reshaped every character: ${bulk.caretQueries}/${fallback.caretQueries}",
                        bulk.caretQueries < fallback.caretQueries / 3)
                }
                expected.recycle()
                actual.recycle()
            }
        }
    }

    @Test fun vendorExpandedBoundsDoNotReplaceUnexpandedCarets() {
        val text = fixture(sample)
        val native = layout(text, 0f, Layout.JUSTIFICATION_MODE_NONE)
        val vendor = CountingLayout(native, boundsShift = 12f)
        val geometry = ReaderLineGeometry(vendor, text, Layout.JUSTIFICATION_MODE_NONE)
        val start = native.getLineStart(0)
        val end = readerLineContentEnd(text, start, native.getLineEnd(0))
        for (offset in start until end - 1) {
            assertEquals(native.getPrimaryHorizontal(offset),
                geometry.horizontalRange(0, offset, offset + 1)!!.left, 0.05f)
        }
        assertTrue(vendor.caretQueries >= end - start)
    }

    @Test fun mixedDirectionAndCombiningTextKeepNativeCaretFallback() {
        for (raw in listOf("Cafe\u0301 nai\u0308ve office", "English العربية together", "Book 📖 office")) {
            val text = fixture(raw)
            val native = layout(text, 0f, Layout.JUSTIFICATION_MODE_NONE)
            val bulk = CountingLayout(native)
            val fallback = CountingLayout(native, rejectBulk = true)
            val actual = ReaderLineGeometry(bulk, text, 0)
            val expected = ReaderLineGeometry(fallback, text, 0)
            for (offset in 0 until text.length) {
                assertEquals(expected.horizontalPosition(offset)!!, actual.horizontalPosition(offset)!!, 0.05f)
            }
        }
    }

    private fun fixture(raw: String): Spanned = SpannableStringBuilder(
        applyReaderPunctuationCompression(BionicReadingFormatter.format(raw, true))
    ).apply {
        setSpan(LeadingMarginSpan.Standard(32, 0), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(34), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun layout(text: Spanned, spacing: Float, justification: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f; color = Color.BLACK; letterSpacing = spacing
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, 480)
            .setIncludePad(false).setJustificationMode(justification).build()
    }

    private class CountingLayout(
        private val source: Layout,
        private val rejectBulk: Boolean = false,
        private val boundsShift: Float = 0f
    ) : Layout(source.text, source.paint, source.width, source.alignment, 1f, 0f) {
        var caretQueries = 0
        override fun getPrimaryHorizontal(offset: Int): Float {
            caretQueries++
            return source.getPrimaryHorizontal(offset)
        }
        override fun fillCharacterBounds(start: Int, end: Int, bounds: FloatArray, boundsStart: Int) {
            if (rejectBulk) throw IllegalArgumentException("OEM does not provide bounds")
            source.fillCharacterBounds(start, end, bounds, boundsStart)
            for (i in start until end) {
                bounds[boundsStart + (i - start) * 4] += boundsShift
                bounds[boundsStart + (i - start) * 4 + 2] += boundsShift
            }
        }
        override fun getLineCount() = source.lineCount
        override fun getLineTop(line: Int) = source.getLineTop(line)
        override fun getLineDescent(line: Int) = source.getLineDescent(line)
        override fun getLineStart(line: Int) = source.getLineStart(line)
        override fun getParagraphDirection(line: Int) = source.getParagraphDirection(line)
        override fun getLineContainsTab(line: Int) = source.getLineContainsTab(line)
        override fun getLineDirections(line: Int) = source.getLineDirections(line)
        override fun getTopPadding() = source.topPadding
        override fun getBottomPadding() = source.bottomPadding
        override fun getEllipsisStart(line: Int) = source.getEllipsisStart(line)
        override fun getEllipsisCount(line: Int) = source.getEllipsisCount(line)
    }
}
