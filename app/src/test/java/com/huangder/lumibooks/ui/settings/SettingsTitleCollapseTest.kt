package com.huangder.lumibooks.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTitleCollapseTest {

    @Test
    fun titleFullyVisibleHasNoCollapsedAlpha() {
        assertEquals(0f, detailTitleCollapseFraction(titleTop = 120f, titleHeight = 64f, viewportTop = 100f), 0.0001f)
    }

    @Test
    fun titlePartiallyLeavingViewportUsesVisibleProgress() {
        val alpha = detailTitleCollapseFraction(titleTop = 68f, titleHeight = 64f, viewportTop = 100f)
        assertEquals(0.5f, alpha, 0.0001f)
        assertTrue(alpha in 0f..1f)
    }

    @Test
    fun titleFullyOutsideViewportHasFullCollapsedAlpha() {
        assertEquals(1f, detailTitleCollapseFraction(titleTop = 20f, titleHeight = 64f, viewportTop = 100f), 0.0001f)
    }

    @Test
    fun invalidTitleHeightFallsBackToHidden() {
        assertEquals(0f, detailTitleCollapseFraction(titleTop = 20f, titleHeight = 0f, viewportTop = 100f), 0.0001f)
    }
}
