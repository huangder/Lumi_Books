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

/** Both reading modes retain punctuation characters for Android line breaking. */
@RunWith(AndroidJUnit4::class)
class ReaderPunctuationCompressionInstrumentedTest {

    private val sample = "他说：“你好，天麟。”随后离开"

    @Test
    fun bothModesKeepRealPunctuationAndOneMeasureSpanPerCharacter() {
        var text: CharSequence = sample
        repeat(4) { pass ->
            text = applyReaderPunctuationCompression(text, frameworkDrawsText = pass % 2 == 0)
            val spanned = text as Spanned
            assertEquals(sample, text.toString())
            assertEquals(0, spanned.getSpans(0, text.length, android.text.style.ReplacementSpan::class.java).size)
            assertEquals(sample.count { isReaderCompressiblePunctuation(it) },
                spanned.getSpans(0, text.length, ReaderPunctuationCompressionSpan::class.java).size)
        }
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
