package com.huangder.lumibooks.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeShockwavePolicyTest {
    @Test
    fun `Galaxy A series gets progressively lighter defaults`() {
        assertEquals(WelcomeShockwaveTier.FULL, welcomeShockwaveTier("SM-A176B"))
        assertEquals(WelcomeShockwaveTier.FULL, welcomeShockwaveTier("SM-A166B"))
        assertEquals(WelcomeShockwaveTier.BALANCED, welcomeShockwaveTier("SM-A156B"))
        assertEquals(WelcomeShockwaveTier.LIGHT, welcomeShockwaveTier("SM-A135F"))
        assertEquals(WelcomeShockwaveTier.HALO_ONLY, welcomeShockwaveTier("SM-A125F"))
    }

    @Test
    fun `unrecognized models keep adaptive full default`() {
        assertEquals(WelcomeShockwaveTier.FULL, welcomeShockwaveTier("Pixel 9"))
        assertEquals(WelcomeShockwaveTier.FULL, welcomeShockwaveTier("SM-S938B"))
    }
}
