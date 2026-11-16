package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuMetricsTest {

    private val verticalPaddings =
        (SELECTION_MENU_CHIP_VERTICAL_PADDING_DP + SELECTION_MENU_ROW_VERTICAL_PADDING_DP) * 2f

    @Test
    fun defaultFontSizeKeepsLegacyRowHeight() {
        // 13sp 文字在默认字体下的实测行高约 16~18dp，行高应保持既有 52dp
        assertEquals(SELECTION_MENU_MIN_ROW_HEIGHT_DP, selectionMenuRowHeightDp(17f), 0.001f)
    }

    @Test
    fun largeFontScaleGrowsRowHeightSoTextFits() {
        // fontScale≈2 时 13sp 中文行高约 34dp，旧的固定 52dp 会把文字下半截裁掉
        val chipLineHeight = 34f
        assertTrue(
            "旧固定行高不足以容纳大字体文字",
            chipLineHeight + verticalPaddings > SELECTION_MENU_MIN_ROW_HEIGHT_DP
        )

        val rowHeight = selectionMenuRowHeightDp(chipLineHeight)

        assertTrue("行高必须随字体放大", rowHeight > SELECTION_MENU_MIN_ROW_HEIGHT_DP)
        assertTrue(
            "文字 + 两层上下内边距必须完整放进胶囊",
            rowHeight >= chipLineHeight + verticalPaddings
        )
    }

    @Test
    fun rowHeightNeverShrinksBelowLegacyHeight() {
        assertEquals(SELECTION_MENU_MIN_ROW_HEIGHT_DP, selectionMenuRowHeightDp(0f), 0.001f)
        assertEquals(SELECTION_MENU_MIN_ROW_HEIGHT_DP, selectionMenuRowHeightDp(-40f), 0.001f)
    }

    @Test
    fun rowHeightGrowsMonotonicallyWithFontScale() {
        val heights = listOf(16f, 24f, 32f, 40f).map(::selectionMenuRowHeightDp)
        heights.zipWithNext().forEach { (smaller, larger) ->
            assertTrue("$heights 必须单调不减", larger >= smaller)
        }
    }

    @Test
    fun allActionsFitOnTheBarWithoutTheMoreButton() {
        val layout = selectionMenuInlineLayout(listOf(60f, 60f, 50f), dividerDp = 0.5f, availableDp = 300f)
        assertEquals(3, layout.inlineCount)
        assertTrue(!layout.showsMore)
        assertEquals(171f, layout.contentWidthDp, 0.001f)
    }

    @Test
    fun overflowKeepsUserOrderAndReservesTheMoreButton() {
        val chips = listOf(70f, 70f, 50f, 50f, 70f, 50f)
        val layout = selectionMenuInlineLayout(chips, dividerDp = 0.5f, availableDp = 270f)
        assertTrue(layout.showsMore)
        // 70 + 0.5 + 70 + 0.5 + 50 = 191；再放一个 50 会让 241.5 加上「>」与间距超出 270
        assertEquals(3, layout.inlineCount)
        assertTrue(layout.contentWidthDp <= 270f)
        assertEquals(191f + SELECTION_MENU_MORE_GAP_DP + SELECTION_MENU_MORE_BUTTON_DP, layout.contentWidthDp, 0.001f)
    }

    @Test
    fun narrowScreensStillShowAtLeastOneAction() {
        val layout = selectionMenuInlineLayout(listOf(120f, 80f), dividerDp = 0.5f, availableDp = 100f)
        assertEquals(1, layout.inlineCount)
        assertTrue(layout.showsMore)
    }

    @Test
    fun widerScreensAndLargerFontsChangeHowManyActionsFit() {
        val chips = List(8) { 56f }
        val phone = selectionMenuInlineLayout(chips, 0.5f, availableDp = 300f)
        val tablet = selectionMenuInlineLayout(chips, 0.5f, availableDp = 600f)
        val largeFont = selectionMenuInlineLayout(chips.map { it * 1.5f }, 0.5f, availableDp = 300f)
        assertTrue(tablet.inlineCount > phone.inlineCount)
        assertTrue(largeFont.inlineCount < phone.inlineCount)
        assertTrue(!tablet.showsMore)
    }

    @Test
    fun trailingGearCountsAgainstTheAvailableWidth() {
        val chips = listOf(90f, 90f, 90f)
        assertTrue(!selectionMenuInlineLayout(chips, 0.5f, availableDp = 280f).showsMore)
        assertTrue(selectionMenuInlineLayout(chips, 0.5f, availableDp = 280f, trailingDp = 32.5f).showsMore)
    }
}
