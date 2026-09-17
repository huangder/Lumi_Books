package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Paint
import android.text.Spanned
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 标点挤压的两套 span 不能混用：分页模式自己逐字绘制（只改测量值），
 * 上下滚动模式交给原生 TextView 绘制（ReplacementSpan 把字形居中画进半宽槽位）。
 * 混用会让框架按整字宽落笔而排版只给它半个字宽，行尾越过正文列右边缘被平台裁掉半截。
 */
@RunWith(AndroidJUnit4::class)
class ReaderPunctuationCompressionInstrumentedTest {

    private val sample = "他说：“你好，天麟。”随后离开"

    @Test
    fun frameworkDrawnVariantCarriesOnlyReplacementSpans() {
        val text = applyReaderPunctuationCompression(sample, frameworkDrawsText = true)
        assertTrue(text is Spanned)
        val spanned = text as Spanned

        assertEquals(0, spanned.getSpans(0, spanned.length, ReaderPunctuationCompressionSpan::class.java).size)
        assertTrue(
            spanned.getSpans(0, spanned.length, ReaderPunctuationReplacementSpan::class.java).isNotEmpty()
        )
        for (index in sample.indices) {
            if (!isReaderCompressiblePunctuation(sample[index])) continue
            assertTrue(
                "索引 $index 的标点必须是框架绘制变体",
                readerIsCompressedPunctuation(
                    spanned,
                    index,
                    ReaderPunctuationReplacementSpan::class.java
                )
            )
            assertFalse(
                readerIsCompressedPunctuation(
                    spanned,
                    index,
                    ReaderPunctuationCompressionSpan::class.java
                )
            )
        }
    }

    @Test
    fun readerDrawnVariantCarriesOnlyMeasureSpans() {
        val text = applyReaderPunctuationCompression(sample, frameworkDrawsText = false)
        val spanned = text as Spanned

        assertEquals(0, spanned.getSpans(0, spanned.length, ReaderPunctuationReplacementSpan::class.java).size)
        assertTrue(
            spanned.getSpans(0, spanned.length, ReaderPunctuationCompressionSpan::class.java).isNotEmpty()
        )
        for (index in sample.indices) {
            if (!isReaderCompressiblePunctuation(sample[index])) continue
            assertTrue(
                readerIsCompressedPunctuation(
                    spanned,
                    index,
                    ReaderPunctuationCompressionSpan::class.java
                )
            )
        }
    }

    @Test
    fun reApplyingTheSameVariantKeepsOnlyOneSpanPerPunctuation() {
        val once = applyReaderPunctuationCompression(sample, frameworkDrawsText = true)
        val twice = applyReaderPunctuationCompression(once, frameworkDrawsText = true)
        val punctuationCount = sample.count { isReaderCompressiblePunctuation(it) }

        assertEquals(
            punctuationCount,
            (twice as Spanned)
                .getSpans(0, twice.length, ReaderPunctuationReplacementSpan::class.java)
                .size
        )
    }

    /** 切换绘制通道时必须清掉另一种变体，否则会留下「半字宽测量 + 整字宽绘制」的混用状态。 */
    @Test
    fun switchingVariantsClearsTheOtherVariant() {
        val measureOnly = applyReaderPunctuationCompression(sample, frameworkDrawsText = false)
        val asFramework = applyReaderPunctuationCompression(measureOnly, frameworkDrawsText = true)
        val frameworkSpanned = asFramework as Spanned
        assertEquals(
            0,
            frameworkSpanned.getSpans(0, asFramework.length, ReaderPunctuationCompressionSpan::class.java).size
        )
        assertTrue(
            frameworkSpanned.getSpans(0, asFramework.length, ReaderPunctuationReplacementSpan::class.java)
                .isNotEmpty()
        )

        val backToReader = applyReaderPunctuationCompression(asFramework, frameworkDrawsText = false)
        val readerSpanned = backToReader as Spanned
        assertEquals(
            0,
            readerSpanned.getSpans(0, backToReader.length, ReaderPunctuationReplacementSpan::class.java).size
        )
        assertTrue(
            readerSpanned.getSpans(0, backToReader.length, ReaderPunctuationCompressionSpan::class.java)
                .isNotEmpty()
        )
    }

    /** 真实字体下，槽位（框架预留的推进量）必须覆盖字形墨迹，行末才不会越界被裁。 */
    @Test
    fun replacementSlotCoversGlyphInkForRealFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                18f,
                context.resources.displayMetrics
            )
        }

        READER_COMPRESSIBLE_PUNCTUATION.forEach { char ->
            val glyph = char.toString()
            val slot = readerPunctuationSlotWidth(paint, glyph, 0, glyph.length)
            val ink = readerGlyphInk(paint, glyph)
            assertTrue("标点 $char 的槽位 $slot 不应窄于墨迹 ${ink.width}", slot + 0.001f >= ink.width)

            val shift = readerPunctuationDrawShift(ink.left, ink.width, slot)
            val inkRight = shift + ink.left + ink.width
            assertTrue(
                "标点 $char 的墨迹右缘 $inkRight 不应超过槽位右缘 $slot",
                inkRight <= slot + 0.001f
            )
            assertTrue("标点 $char 的墨迹左缘不应越过槽位左缘", shift + ink.left >= -0.001f)
        }
    }
}
