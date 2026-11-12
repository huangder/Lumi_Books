package com.huangder.lumibooks.domain.model

import org.junit.Assert.*
import org.junit.Test

class ReaderBackgroundRemovalTest {
    private val removed = "custom:white"

    @Test fun removingSolidColorRepairsBothLayoutsAndBothModes() {
        val selected = ReaderThemeSettings(backgroundSelection = removed, backgroundColorSelection = removed)
        val suite = ReaderThemeSuite("suite", "Custom", selected,
            bookLayoutSettings = selected.copy(fontSize = 20f),
            lightSettings = selected.copy(lineHeight = 2f),
            darkSettings = selected.copy(textColor = 0xFFEEDDCC.toInt()),
            bookLayoutLightSettings = selected.copy(fontSize = 21f),
            bookLayoutDarkSettings = selected.copy(fontSize = 24f))
        val repaired = suite.withoutReaderBackground(removed)
        assertEquals(suite.id, repaired.id)
        for (settings in listOf(repaired.settings, repaired.bookLayoutSettings,
            repaired.lightSettings!!, repaired.darkSettings!!,
            repaired.bookLayoutLightSettings!!, repaired.bookLayoutDarkSettings!!)) {
            assertEquals("day", settings.backgroundSelection)
            assertEquals("day", settings.backgroundColorSelection)
        }
        assertEquals(20f, repaired.bookLayoutSettings.fontSize, 0f)
        assertEquals(2f, repaired.lightSettings!!.lineHeight, 0f)
        assertEquals(suite.darkSettings!!.textColor, repaired.darkSettings!!.textColor)
        assertEquals(21f, repaired.bookLayoutLightSettings!!.fontSize, 0f)
        assertEquals(24f, repaired.bookLayoutDarkSettings!!.fontSize, 0f)
    }

    @Test fun deletingImageRestoresItsSurvivingBaseColor() {
        val settings = ReaderThemeSettings(backgroundSelection = removed, backgroundColorSelection = "custom:green")
        val repaired = ReaderThemeSuite("suite", settings = settings).withoutReaderBackground(removed)
        assertEquals("custom:green", repaired.settings.backgroundSelection)
        assertEquals("custom:green", repaired.settings.backgroundColorSelection)
        assertNull(repaired.lightSettings)
        assertNull(repaired.darkSettings)
    }

    @Test fun deletingBaseColorKeepsImageAndItsAdjustments() {
        val settings = ReaderThemeSettings(backgroundSelection = "custom:image", backgroundColorSelection = removed,
            backgroundImageOpacity = 0.6f, backgroundImageBlurDp = 8f)
        val repaired = ReaderThemeSuite("suite", settings = settings).withoutReaderBackground(removed)
        assertEquals(settings.copy(backgroundColorSelection = "day"), repaired.settings)
    }

    @Test fun unrelatedSuitesAndPublisherPaintAreUnchanged() {
        for (suite in ReaderThemeSuites.defaults()) {
            assertEquals(suite, suite.withoutReaderBackground(removed))
        }
    }
}
