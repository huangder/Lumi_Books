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

    @Test
    fun bookLayoutSettingsRoundTripIndependently() {
        val suites = listOf(
            ReaderThemeSuite(
                id = "day",
                settings = ReaderThemeSettings(marginLeft = 38f, fontSize = 16f),
                bookLayoutSettings = ReaderThemeSettings(marginLeft = 12f, fontSize = 22f)
            )
        )

        val decoded = ReaderThemeSuiteCodec.decode(ReaderThemeSuiteCodec.encode(suites))
            .first { it.id == "day" }

        assertEquals(38f, decoded.settings.marginLeft)
        assertEquals(16f, decoded.settings.fontSize)
        assertEquals(12f, decoded.bookLayoutSettings.marginLeft)
        assertEquals(22f, decoded.bookLayoutSettings.fontSize)
    }

    @Test
    fun legacyPayloadCopiesSingleSettingsToBothLayouts() {
        val raw =
            """[{"id":"day","settings":{"background":"sepia","fontSize":19,"marginLeft":24}}]"""

        val suite = ReaderThemeSuiteCodec.decode(raw).single()

        assertEquals(19f, suite.settings.fontSize)
        assertEquals(19f, suite.bookLayoutSettings.fontSize)
        assertEquals(24f, suite.bookLayoutSettings.marginLeft)
        assertEquals(suite.settings, suite.bookLayoutSettings)
    }

    @Test
    fun normalizationClampsBothLayouts() {
        val suite = ReaderThemeSuite(
            id = "custom",
            customName = "Custom",
            settings = ReaderThemeSettings(marginLeft = 200f),
            bookLayoutSettings = ReaderThemeSettings(marginTop = 500f, fontSize = 2f)
        )

        val normalized = ReaderThemeSuites.normalized(listOf(suite)).first { it.id == "custom" }

        assertEquals(80f, normalized.settings.marginLeft)
        assertEquals(120f, normalized.bookLayoutSettings.marginTop)
        assertEquals(12f, normalized.bookLayoutSettings.fontSize)
    }

    @Test
    fun settingsForAndWithSettingsTargetTheRequestedLayout() {
        val suite = ReaderThemeSuites.defaults().first { it.id == ReaderThemeSuites.SEPIA_ID }
        val bookSettings = suite.settingsFor(ReaderLayoutTarget.BOOK_LAYOUT).copy(marginLeft = 8f)

        val updated = suite.withSettings(ReaderLayoutTarget.BOOK_LAYOUT, bookSettings)

        assertEquals("serif", suite.settingsFor(ReaderLayoutTarget.READER_LAYOUT).fontType)
        assertEquals(8f, updated.bookLayoutSettings.marginLeft)
        assertEquals(suite.settings, updated.settings)
    }
}
