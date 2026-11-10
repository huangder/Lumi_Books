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

    @Test
    fun `lumi 2 dark variant enables only its own aliases`() {
        assertOnlyEnabled("lumi2_dark", splashEnabled = true, LauncherComponentNames.LUMI_2_DARK_SPLASH)
        assertOnlyEnabled("lumi2_dark", splashEnabled = false, LauncherComponentNames.LUMI_2_DARK_DIRECT)
    }

    @Test
    fun `lumi 2 auto variant keeps its own aliases`() {
        assertOnlyEnabled("lumi2_auto", splashEnabled = true, LauncherComponentNames.LUMI_2_AUTO_SPLASH)
        assertOnlyEnabled("lumi2_auto", splashEnabled = false, LauncherComponentNames.LUMI_2_AUTO_DIRECT)
    }

    @Test
    fun `manifest defaults cover every launcher component`() {
        assertEquals(launcherComponentStates(null, true).keys, LauncherComponentNames.manifestDefaults.keys)
        assertEquals(listOf(LauncherComponentNames.LUMI_2_SPLASH),
            LauncherComponentNames.manifestDefaults.filterValues { it }.keys.toList())
    }

    private fun assertOnlyEnabled(style: String, splashEnabled: Boolean, expected: String) {
        val states = launcherComponentStates(style, splashEnabled)
        assertEquals(10, states.size)
        assertEquals(listOf(expected), states.filterValues { it }.keys.toList())
    }

    @Test
    fun `lumi chan is blocked until the egg is unlocked`() {
        assertOnlyEnabled("lumi_chan", true, LauncherComponentNames.LUMI_2_SPLASH)
    }

    @Test
    fun `lumi chan selects its own launcher pair after unlock`() {
        val states = launcherComponentStates("lumi_chan", true, easterEggUnlocked = true)
        assertEquals(LauncherComponentNames.LUMI_CHAN_SPLASH, states.filterValues { it }.keys.single())
    }
    @Test
    fun `every icon splash and unlock combination enables exactly one alias`() {
        for (style in listOf(null, "unknown", "lumi2", "lumi2_dark", "lumi2_auto", "classic", "lumi_chan")) {
            for (splash in listOf(false, true)) for (unlocked in listOf(false, true)) {
                val enabled = launcherComponentStates(style, splash, unlocked).filterValues { it }.keys
                assertEquals(1, enabled.size)
                if (style == "lumi_chan" && unlocked) {
                    assertEquals(if (splash) LauncherComponentNames.LUMI_CHAN_SPLASH else LauncherComponentNames.LUMI_CHAN_DIRECT, enabled.single())
                }
            }
        }
    }
}
