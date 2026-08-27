package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnimationSettingsTest {
    @Test
    fun defaultsMatchProductValues() {
        val settings = ReaderPageAnimationSettings()
        assertEquals(260, settings.slideDurationMs)
        assertEquals(400, settings.fadeDurationMs)
        assertEquals(800, settings.curlDurationMs)
    }

    @Test
    fun durationsClampAndSnapToModeStep() {
        assertEquals(100, ReaderPageAnimationSettings.sanitizeDuration("slide", 1))
        assertEquals(270, ReaderPageAnimationSettings.sanitizeDuration("slide", 266))
        assertEquals(300, ReaderPageAnimationSettings.sanitizeDuration("curl", 1))
        assertEquals(825, ReaderPageAnimationSettings.sanitizeDuration("curl", 813))
        assertEquals(1200, ReaderPageAnimationSettings.sanitizeDuration("curl", 9999))
    }
}
