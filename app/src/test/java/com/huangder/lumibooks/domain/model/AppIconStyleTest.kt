package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconStyleTest {
    @Test
    fun `missing and invalid values use lumi 2`() {
        assertEquals(AppIconStyle.LUMI_2, AppIconStyle.fromStoredValue(null))
        assertEquals(AppIconStyle.LUMI_2, AppIconStyle.fromStoredValue(""))
        assertEquals("lumi2", AppIconStyle.normalize("unknown"))
    }

    @Test
    fun `valid values remain unchanged`() {
        assertEquals(AppIconStyle.LUMI_2, AppIconStyle.fromStoredValue("lumi2"))
        assertEquals(AppIconStyle.CLASSIC, AppIconStyle.fromStoredValue("classic"))
        assertEquals("classic", AppIconStyle.normalize("classic"))
    }

    @Test
    fun `lumi chan requires the unlocked flag`() {
        assertEquals(AppIconStyle.LUMI_2, AppIconStyle.fromStoredValue("lumi_chan", false))
        assertEquals(AppIconStyle.LUMI_CHAN, AppIconStyle.fromStoredValue("lumi_chan", true))
    }

    @Test
    fun `lumi 2 appearance variants round trip and form one family`() {
        assertEquals(AppIconStyle.LUMI_2_DARK, AppIconStyle.fromStoredValue("lumi2_dark"))
        assertEquals(AppIconStyle.LUMI_2_AUTO, AppIconStyle.fromStoredValue("lumi2_auto"))
        assertEquals("lumi2_auto", AppIconStyle.normalize("lumi2_auto", easterEggUnlocked = false))
        assertEquals(
            setOf(AppIconStyle.LUMI_2, AppIconStyle.LUMI_2_DARK, AppIconStyle.LUMI_2_AUTO),
            AppIconStyle.entries.filter { it.isLumi2Family }.toSet()
        )
    }
}
