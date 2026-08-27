package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderThemeSuiteCodecTest {
    @Test
    fun v1ThemeDecodesWithNewDefaults() {
        val raw = """[{"id":"day","settings":{"background":"day","fontSize":18,"fontType":"serif"}}]"""

        val settings = ReaderThemeSuiteCodec.decode(raw).single().settings

        assertEquals("day", settings.backgroundColorSelection)
        assertEquals(1f, settings.backgroundImageOpacity)
        assertEquals(0f, settings.backgroundImageBlurDp)
        assertEquals(400, settings.bodyFontWeight)
        assertNull(settings.textColor)
    }

    @Test
    fun imageSettingsAndFontWeightRoundTripIndependently() {
        val suites = listOf(
            ReaderThemeSuite(
                id = "custom-a",
                customName = "A",
                settings = ReaderThemeSettings(
                    backgroundSelection = "custom:image",
                    backgroundColorSelection = "sepia",
                    backgroundImageOpacity = 0.35f,
                    backgroundImageBlurDp = 24f,
                    bodyFontWeight = 700
                )
            ),
            ReaderThemeSuite(
                id = "custom-b",
                customName = "B",
                settings = ReaderThemeSettings(bodyFontWeight = 300)
            )
        )

        val decoded = ReaderThemeSuiteCodec.decode(ReaderThemeSuiteCodec.encode(suites))

        assertEquals(0.35f, decoded[0].settings.backgroundImageOpacity)
        assertEquals(24f, decoded[0].settings.backgroundImageBlurDp)
        assertEquals("sepia", decoded[0].settings.backgroundColorSelection)
        assertEquals(700, decoded[0].settings.bodyFontWeight)
        assertEquals(300, decoded[1].settings.bodyFontWeight)
    }

    @Test
    fun normalizationClampsImageParametersAndFontWeight() {
        val suite = ReaderThemeSuite(
            id = "custom",
            customName = "Custom",
            settings = ReaderThemeSettings(
                backgroundImageOpacity = 2f,
                backgroundImageBlurDp = -4f,
                bodyFontWeight = 1200
            )
        )

        val settings = ReaderThemeSuites.normalized(listOf(suite)).first().settings

        assertEquals(1f, settings.backgroundImageOpacity)
        assertEquals(0f, settings.backgroundImageBlurDp)
        assertEquals(900, settings.bodyFontWeight)
    }
}
