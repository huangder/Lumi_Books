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
}
