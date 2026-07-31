package com.huangder.lumibooks.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderThemeSuiteCodecInstrumentedTest {
    @Test
    fun codec_roundTripsBuiltInAndCustomSuites() {
        val custom = ReaderThemeSuites.newCustom("focus", "Focus").copy(
            settings = ReaderThemeSettings(
                backgroundSelection = "custom:paper",
                textColor = 0xFF223344.toInt(),
                fontSize = 20f,
                letterSpacing = 4f
            )
        )
        val source = listOf(custom) + ReaderThemeSuites.defaults()

        val decoded = ReaderThemeSuites.normalized(
            ReaderThemeSuiteCodec.decode(ReaderThemeSuiteCodec.encode(source))
        )

        assertEquals(source, decoded)
    }

    @Test
    fun codec_invalidJsonFallsBackWithoutCrashing() {
        assertTrue(ReaderThemeSuiteCodec.decode("not-json").isEmpty())
    }
}
