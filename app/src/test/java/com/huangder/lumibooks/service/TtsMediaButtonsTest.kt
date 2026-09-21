package com.huangder.lumibooks.service

import android.view.KeyEvent
import com.huangder.lumibooks.tts.TtsPlaybackState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsMediaButtonsTest {
    @After
    fun tearDown() {
        TtsMediaButtons.resetDedupe()
    }

    @Test
    fun headsetAndBluetoothPlayPauseKeysTogglePlayback() {
        assertEquals(TtsMediaButtons.Command.TOGGLE, TtsMediaButtons.commandFor(KeyEvent.KEYCODE_HEADSETHOOK))
        assertEquals(
            TtsMediaButtons.Command.TOGGLE,
            TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }

    @Test
    fun transportKeysMapToTheirPlaybackCommands() {
        assertEquals(TtsMediaButtons.Command.PLAY, TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(TtsMediaButtons.Command.PAUSE, TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(TtsMediaButtons.Command.STOP, TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_STOP))
        assertEquals(TtsMediaButtons.Command.NEXT, TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(
            TtsMediaButtons.Command.PREVIOUS,
            TtsMediaButtons.commandFor(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        )
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        assertNull(TtsMediaButtons.commandFor(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(TtsMediaButtons.commandFor(KeyEvent.KEYCODE_BACK))
        assertFalse(TtsMediaButtons.isSupportedKey(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun duplicateDeliveryOfOnePressIsHandledOnce() {
        val pressTime = 5_000L

        assertTrue(TtsMediaButtons.shouldProcess(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, pressTime))
        // The same press arriving through the second delivery path must not toggle twice.
        assertFalse(TtsMediaButtons.shouldProcess(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, pressTime + 40))
        // A genuinely new press after the window is handled again.
        assertTrue(TtsMediaButtons.shouldProcess(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, pressTime + 400))
    }

    @Test
    fun differentKeysInsideTheWindowAreNotDeduplicated() {
        assertTrue(TtsMediaButtons.shouldProcess(KeyEvent.KEYCODE_MEDIA_NEXT, 7_000L))
        assertTrue(TtsMediaButtons.shouldProcess(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 7_010L))
    }

    @Test
    fun keyUpAndLongPressRepeatAreNotSupportedEvents() {
        assertFalse(
            TtsMediaButtons.isSupportedEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
        )
        assertFalse(
            TtsMediaButtons.isSupportedEvent(
                KeyEvent(
                    1L,
                    2L,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    1
                )
            )
        )
    }

    @Test
    fun idlePlaybackRejectsTransportCommands() {
        assertEquals(
            TtsMediaButtons.Outcome.STATE_NOT_ALLOWED,
            TtsMediaButtons.outcomeFor(
                TtsMediaButtons.Command.TOGGLE,
                TtsPlaybackState.IDLE,
                duplicate = false
            )
        )
    }

    @Test
    fun activePlaybackDispatchesCommandsAndDuplicateWins() {
        assertEquals(
            TtsMediaButtons.Outcome.DISPATCHED,
            TtsMediaButtons.outcomeFor(
                TtsMediaButtons.Command.PAUSE,
                TtsPlaybackState.PLAYING,
                duplicate = false
            )
        )
        assertEquals(
            TtsMediaButtons.Outcome.DUPLICATE,
            TtsMediaButtons.outcomeFor(
                TtsMediaButtons.Command.PAUSE,
                TtsPlaybackState.PLAYING,
                duplicate = true
            )
        )
    }
}
