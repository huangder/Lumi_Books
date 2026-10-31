package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnimationSettingsTest {
    @Test
    fun defaultsMatchProductValues() {
        val settings = ReaderPageAnimationSettings()
        assertEquals(260, settings.slideDurationMs)
        assertEquals(260, settings.scrollDurationMs)
        assertEquals(400, settings.fadeDurationMs)
        assertEquals(800, settings.curlDurationMs)
        assertEquals(400, settings.waveDurationMs)
    }

    @Test
    fun durationsClampAndSnapToModeStep() {
        assertEquals(100, ReaderPageAnimationSettings.sanitizeDuration("slide", 1))
        assertEquals(270, ReaderPageAnimationSettings.sanitizeDuration("slide", 266))
        assertEquals(270, ReaderPageAnimationSettings.sanitizeDuration("scroll", 266))
        assertEquals(300, ReaderPageAnimationSettings.sanitizeDuration("curl", 1))
        assertEquals(825, ReaderPageAnimationSettings.sanitizeDuration("curl", 813))
        assertEquals(1200, ReaderPageAnimationSettings.sanitizeDuration("curl", 9999))
        assertEquals(100, ReaderPageAnimationSettings.sanitizeDuration("wave", 1))
        assertEquals(410, ReaderPageAnimationSettings.sanitizeDuration("wave", 414))
        assertEquals(1000, ReaderPageAnimationSettings.sanitizeDuration("wave", 9999))
    }

    @Test
    fun scrollDurationIsIndependentFromHorizontalSlide() {
        val settings = ReaderPageAnimationSettings().withDuration("scroll", 410)

        assertEquals(260, settings.durationFor("slide"))
        assertEquals(410, settings.durationFor("scroll"))
    }

    @Test
    fun waveDurationIsIndependentFromOtherTransitions() {
        val settings = ReaderPageAnimationSettings().withDuration("wave", 540)

        assertEquals(540, settings.durationFor("wave"))
        assertEquals(260, settings.durationFor("slide"))
        assertEquals(800, settings.durationFor("curl"))
    }
}
