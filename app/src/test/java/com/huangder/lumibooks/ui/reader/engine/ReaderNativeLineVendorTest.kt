package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import com.huangder.lumibooks.ui.reader.prepareReaderEnglishHyphenation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderNativeLineVendorTest {
    private fun layout(): StaticLayout {
        val prose = ("The extraordinary conversation about their inattention and misunderstanding " +
            "continued throughout the afternoon. ").repeat(8) + "\n"
        val text = SpannableStringBuilder(BionicReadingFormatter.format(
            prepareReaderEnglishHyphenation(prose), true))
        return StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }, 480)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            .build()
    }

    @Test fun lineLocalVendorCallbacksPreserveHyphensInkAndSelection() {
        val original = layout()
        val vendor = VendorLayout(original)
        var hyphens = 0
        for (line in 1 until original.lineCount) {
            if (original.getLineStart(line) == original.text.length) continue // Final explicit newline.
            val expected = ReaderNativeLine.measure(original, line, Layout.JUSTIFICATION_MODE_NONE)
            assertNotNull("AOSP capture failed on line $line", expected)
            expected!!
            val actual = ReaderNativeLine.measure(vendor, line, Layout.JUSTIFICATION_MODE_NONE)
            assertNotNull("Vendor capture failed on nonzero-offset line $line", actual)
            actual!!
            assertArrayEquals(expected.positions, actual.positions, 0.01f)
            assertEquals(expected.endHyphenEdit, actual.endHyphenEdit)
            if (actual.endHyphenEdit != 0) hyphens++
            for (offset in actual.start..actual.end) {
                assertEquals(expected.offset(expected.position(offset)), actual.offset(actual.position(offset)))
            }
            val a = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val b = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            expected.draw(Canvas(a)); actual.draw(Canvas(b))
            assertTrue("Vendor callback changed glyphs on line $line", a.sameAs(b))
            a.recycle(); b.recycle()
        }
        assertTrue("Fixture must include automatic hyphens after the first line", hyphens > 0)
    }

    @Test fun unsupportedCaptureIsOnlyAttemptedOncePerCachedLine() {
        val vendor = VendorLayout(layout(), omitText = true)
        val geometry = ReaderLineGeometry(vendor, vendor.text, Layout.JUSTIFICATION_MODE_NONE)
        repeat(40) { assertNull(geometry.nativeLine(2)) }
        assertEquals("A failed capture was retried for every character", 1, vendor.drawCalls)
        geometry.retainLines(2..4)
        assertNull(geometry.nativeLine(2))
        assertEquals(1, vendor.drawCalls)
        geometry.retainLines(4..6)
        assertNull(geometry.nativeLine(2))
        assertEquals("Evicted lines must be measured again", 2, vendor.drawCalls)
    }

    /** Replays real Android shaping through Xiaomi's line-local Canvas contract. */
    private class VendorLayout(private val source: Layout, private val omitText: Boolean = false) :
        Layout(source.text, source.paint, source.width, source.alignment, 1f, 0f) {
        var drawCalls = 0
        override fun draw(canvas: Canvas) {
            drawCalls++
            if (omitText) return
            source.draw(object : Canvas() {
                override fun getClipBounds(bounds: Rect) = canvas.getClipBounds(bounds)
                override fun drawTextRun(text: CharSequence, start: Int, end: Int,
                    contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean, paint: Paint) {
                    val line = source.getLineForVertical(y.toInt() - 1)
                    val base = source.getLineStart(line)
                    val local = text.subSequence(base, source.getLineVisibleEnd(line))
                    canvas.drawTextRun(local, start - base, end - base,
                        contextStart - base, contextEnd - base, x, y, rtl, paint)
                }
                override fun drawTextRun(text: CharArray, index: Int, count: Int,
                    contextIndex: Int, contextCount: Int, x: Float, y: Float, rtl: Boolean, paint: Paint) {
                    val line = source.getLineForVertical(y.toInt() - 1)
                    val base = source.getLineStart(line)
                    val local = source.text.subSequence(base, source.getLineVisibleEnd(line))
                    canvas.drawTextRun(local, index, index + count,
                        contextIndex, contextIndex + contextCount, x, y, rtl, paint)
                }
                override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            })
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
