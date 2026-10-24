package com.huangder.lumibooks.service

import android.media.AudioManager
import com.huangder.lumibooks.tts.TtsPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsAudioFocusTest {
    @Test
    fun permanentAndExclusiveTransientLossPausePlayback() {
        assertEquals(
            TtsAudioFocusAction.PAUSE_FOR_PERMANENT_LOSS,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_LOSS, pausedForLoss = false)
        )
        assertEquals(
            TtsAudioFocusAction.PAUSE_FOR_TRANSIENT_LOSS,
            ttsAudioFocusActionForChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, pausedForLoss = false)
        )
    }

    @Test
    fun duckableFocusLossDoesNotPausePlayback() {
        assertEquals(
            TtsAudioFocusAction.IGNORE,
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

    @Test
    fun focusRemainsHeldWhilePlaybackIsPausedForTransientLoss() {
        assertEquals(
            true,
            shouldHoldTtsAudioFocus(
                playbackState = TtsPlaybackState.PAUSED,
                usesSystemEngine = true,
                pausedForLoss = true
            )
        )
        assertEquals(
            false,
            shouldHoldTtsAudioFocus(
                playbackState = TtsPlaybackState.PAUSED,
                usesSystemEngine = true,
                pausedForLoss = false
            )
        )
        assertEquals(
            false,
            shouldHoldTtsAudioFocus(
                playbackState = TtsPlaybackState.PLAYING,
                usesSystemEngine = false,
                pausedForLoss = true
            )
        )
    }
}
