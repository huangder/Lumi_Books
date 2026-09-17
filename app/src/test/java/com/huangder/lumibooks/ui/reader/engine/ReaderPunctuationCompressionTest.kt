package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPunctuationCompressionTest {
    @Test
    fun compressesFullWidthSentenceMarksAndBrackets() {
        listOf('。', '，', '、', '；', '：', '！', '？', '」', '』', '（', '「', '“', '’')
            .forEach { assertTrue("$it 应该参与挤压", isReaderCompressiblePunctuation(it)) }
    }

    @Test
    fun keepsHanziHalfWidthPunctuationAndDashesUntouched() {
        listOf('永', 'A', '.', ',', '!', ' ', '\n', '…', '—', '・')
            .forEach { assertFalse("$it 不应参与挤压", isReaderCompressiblePunctuation(it)) }
    }

    @Test
    fun compressionKeepsHalfWidthSlot() {
        assertEquals(0.5f, READER_PUNCTUATION_COMPRESSION)
    }

    /**
     * 全角标点墨迹窄于半字宽时仍取半字宽；墨迹更宽时（自定义字体的比例标点、
     * 满框字形）槽位必须跟到墨迹宽度，否则框架在行末按槽位裁切，标点会被切掉半截。
     */
    @Test
    fun compressedSlotNeverNarrowerThanInk() {
        assertEquals(28f, readerCompressedSlotWidth(naturalAdvance = 56f, inkWidth = 25f), 0.001f)
        assertEquals(19f, readerCompressedSlotWidth(naturalAdvance = 26f, inkWidth = 19f), 0.001f)
        assertEquals(48f, readerCompressedSlotWidth(naturalAdvance = 56f, inkWidth = 48f), 0.001f)
        assertEquals(0f, readerCompressedSlotWidth(naturalAdvance = Float.NaN, inkWidth = 0f), 0.001f)
    }

    /**
     * 自定义字体的比例引号（实测霞鹜文楷 “ ：推进量 ≈0.35em、墨迹 ≈0.25em）是本次
     * 用户反馈的典型场景：旧的“固定半字宽”会让 0.175em 的槽位装不下 0.25em 的墨迹，
     * 行末引号被裁掉近一半。
     */
    @Test
    fun proportionalQuoteFromCustomFontWidensItsSlot() {
        val slot = readerCompressedSlotWidth(naturalAdvance = 35f, inkWidth = 25f)
        assertEquals(25f, slot, 0.001f)

        val inkLeft = 6.5f
        val inkWidth = 25f
        val shift = readerPunctuationDrawShift(inkLeft, inkWidth, slot)
        val inkRight = shift + inkLeft + inkWidth
        assertTrue("墨迹右缘 $inkRight 不应超过槽位右缘 $slot", inkRight <= slot + 0.001f)

        // 旧规则（固定半字宽）会越界：这是被修复的裁切来源。
        val legacySlot = 35f * READER_PUNCTUATION_COMPRESSION
        val legacyInkRight =
            readerPunctuationDrawShift(inkLeft, inkWidth, legacySlot) + inkLeft + inkWidth
        assertTrue("旧半字宽槽位应当越界", legacyInkRight > legacySlot + 1f)
    }

    /** 居中位移必须让墨迹完整落在槽位内，行末才不会越过正文列右边缘。 */
    @Test
    fun centeredGlyphFitsInsideItsSlot() {
        val samples = listOf(
            Triple(4f, 25f, 28f), // 常规全角标点：半字宽槽位
            Triple(4f, 19f, 19f), // 比例标点：槽位被墨迹撑满
            Triple(0.5f, 48f, 48f),
            Triple(3f, 48f, 50f)
        )
        samples.forEach { (inkLeft, inkWidth, slotWidth) ->
            val shift = readerPunctuationDrawShift(inkLeft, inkWidth, slotWidth)
            val inkRight = shift + inkLeft + inkWidth
            assertTrue(
                "墨迹右缘 $inkRight 不应超过槽位右缘 $slotWidth",
                inkRight <= slotWidth + 0.001f
            )
            assertTrue("墨迹左缘不应越过槽位左缘", shift + inkLeft >= -0.001f)
        }
    }

    /** 无墨迹信息时不做位移，保持既有回退行为。 */
    @Test
    fun drawShiftFallsBackToZeroWithoutInk() {
        assertEquals(0f, readerPunctuationDrawShift(0f, 0f, 28f), 0.001f)
        assertEquals(0f, readerPunctuationDrawShift(4f, 25f, 0f), 0.001f)
    }
}
