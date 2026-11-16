package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.util.parser.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPageBottomAlignmentTest {
    private val paint = TextPaint().apply { textSize = 40f }

    private fun metrics(height: Int, mult: Float, extra: Float) = ReaderPageTextMetrics(
        paint = paint, width = 800, height = height,
        lineSpacingExtra = extra, lineSpacingMultiplier = mult,
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE,
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE,
        justificationMode = Layout.JUSTIFICATION_MODE_NONE
    )

    private fun inkBottom(text: Spannable, m: ReaderPageTextMetrics): Int {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, m.paint, m.width)
            .setLineSpacing(m.lineSpacingExtra, m.lineSpacingMultiplier)
            .setIncludePad(false)
            .setUseLineSpacingFromFallbacks(false)
            .build()
        var last = layout.lineCount - 1
        while (last > 0 && text.subSequence(layout.getLineStart(last), layout.getLineEnd(last)).isBlank()) last--
        return layout.getLineBaseline(last) + paint.fontMetricsInt.descent
    }

    private fun page(): SpannableStringBuilder {
        val text = SpannableStringBuilder()
        repeat(4) {
            text.append("每一页正文都应当在同一条下边线上结束，而不是忽高忽低。".repeat(2)).append('\n')
            val spacer = text.length
            text.append('\n')
            text.setSpan(EpubParser.ParagraphLineHeightSpan(18), spacer, spacer + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    @Test
    fun fullPageEndsOnBottomMarginForDifferentLineSpacing() {
        for ((mult, extra) in listOf(1f to 8f, 1.5f to 0f, 1.8f to 4f)) {
            val probe = page()
            val natural = inkBottom(probe, metrics(10_000, mult, extra))
            val lineHeight = (paint.fontMetricsInt.let { it.descent - it.ascent } * mult + extra).toInt()
            for (slack in listOf(lineHeight / 3, lineHeight - 2)) {
                val m = metrics(natural + 2 + slack, mult, extra)
                val text = page()
                applyReaderPageBottomAlignment(text, m)
                val bottom = inkBottom(text, m)
                val target = m.height - 2
                assertTrue("mult=$mult slack=$slack overflowed: $bottom > $target", bottom <= target)
                assertTrue("mult=$mult slack=$slack left ${target - bottom}px", target - bottom <= 3)
            }
        }
    }

    @Test
    fun largeGapBeforeMovedImageOrHeadingIsLeftNatural() {
        val probe = page()
        val m0 = metrics(10_000, 1.5f, 0f)
        val natural = inkBottom(probe, m0)
        val m = metrics(natural + 600, 1.5f, 0f)
        val text = page()
        applyReaderPageBottomAlignment(text, m)
        assertEquals(0, text.getSpans(0, text.length, ReaderPageBottomAlignSpan::class.java).size)
        assertEquals(natural, inkBottom(text, m))
    }
}
