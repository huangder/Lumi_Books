package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "sw800dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPageMarginsTest {
    @Test
    fun oldSelectionLayoutCannotOverrideNewPortraitWidthOrRightMargin() {
        val context = RuntimeEnvironment.getApplication()
        val text = SpannableStringBuilder("平板竖屏阅读时正文应当始终保留左右边距。".repeat(12))
        val oldLayout = StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint().apply { textSize = 36f }, 1448).setIncludePad(false).build()
        fun view() = JustifiedTextView(context).apply {
            setTextSize(36f)
            readerJustificationMode = Layout.JUSTIFICATION_MODE_NONE
            this.text = text
        }
        val reference = view()
        val reused = view().apply { setSourceLayoutProvider { oldLayout } }
        for ((width, rightMargin) in listOf(1200 to 76, 1600 to 240)) {
            fun render(view: JustifiedTextView): Bitmap {
                view.setPadding(76, 32, rightMargin, 32)
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, 1800)
                return Bitmap.createBitmap(width, 1800, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }
            }
            val expected = render(reference)
            val actual = render(reused)
            try {
                assertTrue("Old selection width overrode $width px page with $rightMargin px right margin",
                    expected.sameAs(actual))
            } finally {
                expected.recycle()
                actual.recycle()
            }
        }
    }

    @Test
    fun tabletPageKeepsRightMarginAfterResizingAndChangingInsets() {
        val page = PageContentView(RuntimeEnvironment.getApplication())
        val text = applyReaderPunctuationCompression("平板竖屏阅读时正文应当始终保留左右边距。".repeat(100))
        for ((width, height) in listOf(1600 to 2400, 2400 to 1600, 1600 to 2400)) {
            for (rightMargin in listOf(76, 240, 40)) {
                page.configure(36f, Color.BLACK, marginLeftPx = 76f,
                    marginRightPx = rightMargin.toFloat(), textAlignment = ReaderTextAlignment.JUSTIFY)
                page.setPageContent(text, 0, 200)
                page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                page.layout(0, 0, width, height)
                assertEquals(width - 76 - rightMargin, page.textView.layout.width)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    page.draw(Canvas(bitmap))
                    var lastInk = -1
                    for (y in 0 until minOf(height, 400)) {
                        for (x in 0 until width) {
                            if (Color.alpha(bitmap.getPixel(x, y)) > 64) lastInk = maxOf(lastInk, x)
                        }
                    }
                    assertTrue("Expected body text", lastInk >= 76)
                    assertTrue("$width x $height: ink at $lastInk entered right margin $rightMargin",
                        lastInk < width - rightMargin)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
