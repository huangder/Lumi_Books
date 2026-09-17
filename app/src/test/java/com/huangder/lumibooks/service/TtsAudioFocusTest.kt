package com.huangder.lumibooks.service

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsAudioFocusTest {
    @Test
    fun everyFocusLossPausesPlayback() {
        assertEquals(
            TtsAudioFocusAction.PAUSE_FOR_LOSS,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_LOSS, pausedForLoss = false)
        )
        assertEquals(
            TtsAudioFocusAction.PAUSE_FOR_LOSS,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, pausedForLoss = false)
        )
        assertEquals(
            TtsAudioFocusAction.PAUSE_FOR_LOSS,
            ttsAudioFocusActionForChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                pausedForLoss = false
            )
        )
    }

    @Test
    fun gainingFocusResumesOnlyAfterOurOwnAutoPause() {
        assertEquals(
            TtsAudioFocusAction.RESUME_AFTER_LOSS,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_GAIN, pausedForLoss = true)
        )
        // A pause the user chose must never be undone by a focus gain.
        assertEquals(
            TtsAudioFocusAction.IGNORE,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_GAIN, pausedForLoss = false)
        )
    }

    @Test
    fun unknownFocusChangesAreIgnored() {
        assertEquals(
            TtsAudioFocusAction.IGNORE,
            ttsAudioFocusActionForChange(change = 99, pausedForLoss = true)
        )
    }
}
