package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Selection
import android.text.TextPaint
import android.text.style.LocaleSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ReaderLineGeometry
import com.huangder.lumibooks.ui.reader.engine.readerGlyphPaint
import com.huangder.lumibooks.ui.reader.engine.readerSerifTypeface
import com.huangder.lumibooks.ui.reader.engine.applyReaderPunctuationCompression
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousEnglishHyphenationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sample = "Complaints of their inattention to what was going forward. " +
        "As all conversation was thereby at an end, Elizabeth soon afterwards left the room. " +
        "“Eliza Bennet,” said Miss Bingley, when the door was closed on her."

    @Test fun formattingKeepsOriginalOffsetsAndPublisherLocale() {
        val source = SpannableStringBuilder(sample).apply {
            setSpan(LocaleSpan(Locale.UK), 0, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val result = prepareReaderEnglishHyphenation(applyReaderPunctuationCompression(source)) as Spanned
        assertEquals(sample, result.toString())
        assertTrue(usesReaderEnglishHyphenation(result))
        assertSame(result, prepareReaderEnglishHyphenation(result))
        assertFalse(usesReaderEnglishHyphenation(source))
        val paint = TextPaint().apply { textLocale = Locale.CHINA }
        result.getSpans(0, 10, LocaleSpan::class.java).forEach { it.updateMeasureState(paint) }
        assertEquals(Locale.UK, paint.textLocale)
        result.getSpans(11, 12, LocaleSpan::class.java).forEach { it.updateMeasureState(paint) }
        assertEquals(Locale.ENGLISH, paint.textLocale)
        for (text in listOf("中文 English 混排。", "日本語と English", "", "1234")) {
            assertSame(text, prepareReaderEnglishHyphenation(text))
        }
    }

    @Test fun discretionaryHyphensUseNativeInkAndSelectionAcrossStylesAndWidths() {
        var hyphenatedLines = 0
        for (font in listOf(Typeface.DEFAULT, ResourcesCompat.getFont(context, R.font.lxgw_wenkai)!!,
            com.huangder.lumibooks.ui.reader.engine.readerSerifTypeface(context, 550))) {
            for (bionic in listOf(false, true)) for (alignment in listOf(ReaderTextAlignment.LEFT, ReaderTextAlignment.JUSTIFY)) {
                for (width in listOf(360, 540)) {
                    val view = view(sample, font, bionic, alignment, width)
                    val sl = view.layout
                    // Test-only access to Android's line-break result. Production
                    // rendering uses Layout.draw and does not call hidden APIs.
                    val edit = sl.javaClass.getMethod("getEndHyphenEdit", Int::class.javaPrimitiveType)
                    val geometry = ReaderLineGeometry(sl, view.text, view.readerJustificationMode)
                    for (line in 0 until sl.lineCount - 1) {
                        if (edit.invoke(sl, line) as Int == 0) continue
                        hyphenatedLines++
                        val end = sl.getLineEnd(line)
                        assertTrue("An automatic hyphen must split a word", sample[end - 1].isLetter() && sample[end].isLetter())
                        val range = geometry.horizontalRange(line, end - 1, end)!!
                        assertTrue("Hyphen must fit inside the right margin", range.right <= width + 1f)
                        assertEquals("The trailing selection handle must stay on the hyphenated row",
                            range.right, geometry.horizontalPosition(end, trailing = true)!!, 1f)
                        val selected = view.text as android.text.Spannable
                        Selection.setSelection(selected, 0, end)
                        assertEquals(sample.substring(0, end), selected.subSequence(0, end).toString())
                        Selection.removeSelection(selected)
                    }
                    val actual = bitmap(view.width, view.height).also { view.draw(Canvas(it)) }
                    val expected = bitmap(view.width, view.height).also { sl.draw(Canvas(it)) }
                    if (alignment == ReaderTextAlignment.LEFT) {
                        assertTrue("Hyphens, styled words and quotes must match native drawing", expected.sameAs(actual))
                    } else {
                        for (line in 0 until sl.lineCount - 1) {
                            val content = sample.substring(sl.getLineStart(line), sl.getLineEnd(line)).trimEnd()
                            if (' ' in content) assertEquals("English word spacing must fill the line: font=$font bionic=$bionic width=$width line=$line text=$content native=${geometry.nativeLine(line)?.positions?.joinToString()}",
                                width.toFloat(), geometry.lineRange(line)!!.right, 1f)
                        }
                    }
                    val wordStart = sample.indexOf("inattention")
                    val wordEnd = wordStart + "inattention".length
                    for (line in sl.getLineForOffset(wordStart)..sl.getLineForOffset(wordEnd - 1)) {
                        val redXs = (0 until width).filter { x ->
                            (sl.getLineTop(line) until sl.getLineBottom(line)).any { y ->
                                val pixel = actual.getPixel(x, y)
                                Color.alpha(pixel) > 128 && Color.red(pixel) > 200 && Color.green(pixel) < 50
                            }
                        }
                        assertTrue("A split word must remain visible", redXs.isNotEmpty())
                        val x = (redXs.first() + redXs.last()) / 2f
                        val y = (sl.getLineTop(line) + sl.getLineBottom(line)) / 2f
                        assertTrue("Touching either half must still hit the source word",
                            view.getOffsetForPosition(x, y) in wordStart until wordEnd)
                        val wordRange = geometry.horizontalRange(line,
                            maxOf(wordStart, sl.getLineStart(line)), minOf(wordEnd, sl.getLineEnd(line)))!!
                        assertTrue("Word ink must fit its selection geometry: bionic=$bionic alignment=$alignment width=$width line=$line ink=${redXs.first()}..${redXs.last()} range=$wordRange",
                            redXs.first() >= wordRange.left - 2f && redXs.last() <= wordRange.right + 2f)
                    }
                    actual.recycle(); expected.recycle()
                }
            }
        }
        assertTrue("Fixture must actually exercise automatic hyphenation", hyphenatedLines > 0)
    }

    @Test fun bionicTextKeepsAutomaticHyphenationAtReadingSize() {
        val prose = ("The extraordinary conversation about their inattention and misunderstanding " +
            "continued throughout the afternoon. ").repeat(8)
        for (bionic in listOf(false, true)) {
            val text = SpannableStringBuilder(BionicReadingFormatter.format(
                prepareReaderEnglishHyphenation(prose), bionic))
            val view = ContinuousSelectableTextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(480, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 40f)
                breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                hyphenationFrequency = readerHyphenationFrequency(text)
                setReaderText(text)
                measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                layout(0, 0, measuredWidth, measuredHeight)
            }
            val sl = view.layout
            val edit = sl.javaClass.getMethod("getEndHyphenEdit", Int::class.javaPrimitiveType)
            val count = (0 until sl.lineCount).count { edit.invoke(sl, it) as Int != 0 }
            assertTrue("No automatic hyphens with bionic=$bionic", count > 0)
        }
    }

    @Test fun bionicEmphasisAddsToTheSelectedVariableWeight() {
        val text = BionicReadingFormatter.format(prepareReaderEnglishHyphenation("inattention"), true) as Spanned
        assertTrue(text.getSpans(0, 1, BionicFixationSpan::class.java).isNotEmpty())
        for (weight in listOf(250, 550, 850)) {
            val base = TextPaint().apply {
                typeface = readerSerifTypeface(context, weight)
                textSize = 40f
            }
            val prefix = readerGlyphPaint(base, text, 0, 1)
            val suffix = readerGlyphPaint(base, text, 8, 9)
            assertEquals(weight, prefix.typeface.weight)
            assertTrue(prefix.isFakeBoldText)
            assertEquals(weight, suffix.typeface.weight)
            assertFalse(suffix.isFakeBoldText)
        }
    }

    private fun view(raw: String, font: Typeface, bionic: Boolean, alignment: ReaderTextAlignment, width: Int): ContinuousSelectableTextView {
        val text = SpannableStringBuilder(BionicReadingFormatter.format(
            prepareReaderEnglishHyphenation(applyReaderPunctuationCompression(raw)), bionic))
        val wordStart = raw.indexOf("inattention")
        text.setSpan(ForegroundColorSpan(Color.RED), wordStart, wordStart + "inattention".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        protectContinuousImageHeights(text, 1.5f)
        return ContinuousSelectableTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(Color.BLACK)
            typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 48f)
            breakStrategy = alignment.readerBreakStrategyForText(text)
            hyphenationFrequency = readerHyphenationFrequency(text)
            justificationMode = alignment.readerJustificationForText(text)
            readerJustificationMode = justificationMode
            setReaderText(text)
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun bitmap(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}
