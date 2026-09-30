package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.ui.reader.engine.applyReaderPunctuationCompression
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousTxtTitleRenderingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val title = "第十二章：“新的开始。”夜色中的漫长旅程与重逢"

    /** Dip-sized TXT headings must reserve exactly the space their actual ink uses. */
    @Test fun dipTitleMatchesEquivalentPixelTitleAcrossFontsAndWrapping() {
        val fonts = listOf(Typeface.DEFAULT, ResourcesCompat.getFont(context, R.font.lxgw_wenkai)!!)
        for (font in fonts) for (width in listOf(360, 720)) for (size in listOf(16f, 28f)) {
            for (alignment in listOf(ReaderTextAlignment.LEFT, ReaderTextAlignment.CENTER,
                ReaderTextAlignment.RIGHT, ReaderTextAlignment.JUSTIFY)) {
                val actual = view(title, true, width, size, font, alignment)
                val expected = view(title, false, width, size, font, alignment)
                assertEquals("Title wrapping changed", expected.layout.lineCount, actual.layout.lineCount)
                for (line in 0 until expected.layout.lineCount) {
                    assertEquals("$width/$size/$alignment: title baseline on line $line",
                        expected.layout.getLineBaseline(line), actual.layout.getLineBaseline(line))
                    assertEquals("$width/$size/$alignment: line bottom $line",
                        expected.layout.getLineBottom(line), actual.layout.getLineBottom(line))
                }
                val reference = render(expected)
                val result = render(actual)
                assertTrue("Dip and pixel headings must have identical, unclipped ink", reference.sameAs(result))
                reference.recycle()
                result.recycle()
            }
        }
    }

    /** At 1x spacing a styled title must keep the framework's native font metrics. */
    @Test fun titleLineHeightMatchesNativeLayoutAndDoesNotClipTopOrOverlapRows() {
        for (heading in listOf(title, "Chapter Twelve: A Long Journey, An Unexpected Return")) {
            val actual = view(heading, true, 420, 28f, Typeface.DEFAULT,
                ReaderTextAlignment.LEFT, 1f)
            val native = view(heading, true, 420, 28f, Typeface.DEFAULT,
                ReaderTextAlignment.LEFT, null)
            assertTrue("Fixture must wrap the title", actual.layout.getLineForOffset(heading.length) > 0)
            val lastTitleLine = native.layout.getLineForOffset(heading.length - 1)
            for (line in 0..lastTitleLine) {
                assertEquals("Styled title ascent must not shrink on line $line",
                    native.layout.getLineAscent(line), actual.layout.getLineAscent(line))
                assertEquals("Styled title baseline must not overlap the preceding row on line $line",
                    native.layout.getLineBaseline(line), actual.layout.getLineBaseline(line))
            }
            // Body fallback metrics may differ by Android version; compare the
            // heading's complete ink area, including its first and last rows.
            val titleBottom = maxOf(native.layout.getLineBottom(lastTitleLine),
                actual.layout.getLineBottom(lastTitleLine))
            val reference = render(native, titleBottom)
            val result = render(actual, titleBottom)
            assertTrue("1x line-height protection must preserve all title pixels: $heading", reference.sameAs(result))
            reference.recycle()
            result.recycle()
        }
    }

    private fun view(heading: String, dip: Boolean, width: Int, bodySize: Float,
        font: Typeface, alignment: ReaderTextAlignment, spacing: Float? = 1.5f): ContinuousSelectableTextView {
        val titleSize = txtChapterTitleFontSize(bodySize)
        val source = SpannableStringBuilder("$heading\n\n正文从这里开始。The story begins here.\n").apply {
            setSpan(AbsoluteSizeSpan(if (dip) titleSize else
                (titleSize * context.resources.displayMetrics.density).toInt(), dip),
                0, heading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, heading.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val formatted = ReaderParagraphFormatter.applyFirstLineIndent(source, 2f,
            bodySize * context.resources.displayMetrics.scaledDensity, 12f,
            skipFirstNonEmptyParagraph = true)
        val text = SpannableStringBuilder(applyReaderPunctuationCompression(
            applyReaderTextAlignment(formatted, alignment, heading.length + 1)))
        if (spacing != null) protectContinuousImageHeights(text, spacing)
        return ContinuousSelectableTextView(context).apply {
            typeface = font
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
            setLineSpacing(0f, 1f)
            breakStrategy = alignment.readerBreakStrategy()
            justificationMode = alignment.readerJustificationMode()
            setReaderText(text)
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun render(view: View, height: Int = view.height): Bitmap =
        Bitmap.createBitmap(view.width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}
