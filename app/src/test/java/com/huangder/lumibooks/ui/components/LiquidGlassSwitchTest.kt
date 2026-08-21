package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassSwitchTest {
    @Test
    fun tapTogglesCurrentValue() {
        assertTrue(resolveSwitchTarget(currentChecked = false, hasDragged = false, dragValue = 0f))
        assertFalse(resolveSwitchTarget(currentChecked = true, hasDragged = false, dragValue = 1f))
    }

    @Test
    fun dragSettlesFromThumbPosition() {
        assertFalse(resolveSwitchTarget(currentChecked = true, hasDragged = true, dragValue = 0.49f))
        assertTrue(resolveSwitchTarget(currentChecked = false, hasDragged = true, dragValue = 0.5f))
    }
}
