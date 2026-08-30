package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherComponentSelectionTest {
    @Test
    fun `lumi 2 with splash enables only lumi 2 splash alias`() {
        assertOnlyEnabled("lumi2", splashEnabled = true, LauncherComponentNames.LUMI_2_SPLASH)
    }

    @Test
    fun `lumi 2 without splash enables only lumi 2 direct alias`() {
        assertOnlyEnabled("lumi2", splashEnabled = false, LauncherComponentNames.LUMI_2_DIRECT)
    }

    @Test
    fun `classic with splash enables only classic splash alias`() {
        assertOnlyEnabled("classic", splashEnabled = true, LauncherComponentNames.CLASSIC_SPLASH)
    }

    @Test
    fun `classic without splash enables only classic direct alias`() {
        assertOnlyEnabled("classic", splashEnabled = false, LauncherComponentNames.CLASSIC_DIRECT)
    }

    private fun assertOnlyEnabled(style: String, splashEnabled: Boolean, expected: String) {
        val states = launcherComponentStates(style, splashEnabled)
        assertEquals(4, states.size)
        assertEquals(listOf(expected), states.filterValues { it }.keys.toList())
    }
}
