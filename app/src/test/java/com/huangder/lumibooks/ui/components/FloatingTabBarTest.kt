package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTabBarTest {
    @Test
    fun panelOffsetIsSymmetricAndBounded() {
        val left = dampedTabPanelOffset(-320f, panelWidthPx = 640f, maxOffsetPx = 8f)
        val right = dampedTabPanelOffset(320f, panelWidthPx = 640f, maxOffsetPx = 8f)

        assertEquals(-left, right, 0.0001f)
        assertEquals(8f, dampedTabPanelOffset(5000f, 640f, 8f), 0.0001f)
        assertEquals(-8f, dampedTabPanelOffset(-5000f, 640f, 8f), 0.0001f)
    }

}
