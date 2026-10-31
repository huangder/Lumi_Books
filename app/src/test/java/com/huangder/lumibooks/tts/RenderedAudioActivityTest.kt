package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderedAudioActivityTest {
    @Test fun synthesisAndInitialBufferingAreExcludedAndStarvationEndsActivity() {
        var now = 0L
        val activity = RenderedAudioActivity({ now }, initialFrame = 240_000)
        assertNull(activity.update(240_000, false))
        now += 30_000 // Downloading and synthesizing audio must never count.
        assertNull(activity.update(240_000, false))
        assertEquals(true, activity.update(240_100, false))
        now += 20
        assertNull(activity.update(240_100, false))
        now += 40
        assertEquals(false, activity.update(240_100, false))
        assertNull(activity.update(240_100, false))
        assertEquals(true, activity.update(240_200, false))
    }

    @Test fun evenImmediatePauseAndResumeWaitsForNewRenderedFrames() {
        var now = 0L
        val activity = RenderedAudioActivity({ now })
        assertEquals(true, activity.update(100, false))
        assertEquals(false, activity.pause())
        assertNull(activity.pause())
        assertNull(activity.update(100, false))
        now += 10
        assertEquals(true, activity.update(200, false))
        assertEquals(false, activity.update(200, true))
    }
}
