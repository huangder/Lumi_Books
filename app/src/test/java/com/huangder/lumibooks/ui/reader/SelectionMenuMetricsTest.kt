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
}
