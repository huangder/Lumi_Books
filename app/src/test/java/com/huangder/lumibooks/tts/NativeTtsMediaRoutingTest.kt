package com.huangder.lumibooks.tts

import android.media.MediaPlayer
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.State
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35, 36], manifest = Config.NONE)
class NativeTtsMediaRoutingTest {
    @Test fun completionReleasesTheNonLoopingPlayerWithoutRearmingNextSentence() {
        val players = mutableListOf<MediaPlayer>()
        val routing = NativeTtsMediaRouting { preparedRoutingPlayer().also(players::add) }
        try {
            routing.start()
            routing.start()
            assertEquals(1, players.size)
            assertFalse(players.single().isLooping)
            assertTrue(players.single().isPlaying)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            assertEquals(State.END, shadowOf(players.single()).state)
            routing.start()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            assertEquals(1, players.size)
        } finally { routing.stop() }
    }

    @Test fun stopCancelsEarlyAndRearmsTheNextPlaybackEvenAfterNaturalCompletion() {
        val players = mutableListOf<MediaPlayer>()
        val routing = NativeTtsMediaRouting { preparedRoutingPlayer().also(players::add) }
        try {
            routing.start()
            routing.stop()
            assertEquals(State.END, shadowOf(players.single()).state)
            routing.stop()
            routing.start()
            assertEquals(2, players.size)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            routing.stop() // Rearm even though completion already released the player.
            routing.start()
            assertEquals(3, players.size)
        } finally { routing.stop() }
        assertEquals(State.END, shadowOf(players.last()).state)
    }

    @Test fun lateCompletionFromCancelledPlayerCannotReleaseItsReplacement() {
        val players = mutableListOf<MediaPlayer>()
        val routing = NativeTtsMediaRouting { preparedRoutingPlayer().also(players::add) }
        try {
            routing.start()
            val oldPlayer = players.single()
            val oldCompletion = requireNotNull(shadowOf(oldPlayer).onCompletionListener)
            routing.stop()
            routing.start()
            oldCompletion.onCompletion(oldPlayer)
            assertTrue(players.last().isPlaying)
            assertEquals(State.END, shadowOf(oldPlayer).state)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            assertEquals(State.END, shadowOf(players.last()).state)
        } finally { routing.stop() }
    }

    @Test fun playbackErrorReleasesWithoutRetryUntilNextPlayback() {
        val players = mutableListOf<MediaPlayer>()
        val routing = NativeTtsMediaRouting { preparedRoutingPlayer().also(players::add) }
        try {
            routing.start()
            shadowOf(players.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
            assertEquals(State.END, shadowOf(players.single()).state)
            routing.start()
            assertEquals(1, players.size)
            routing.stop()
            routing.start()
            assertEquals(2, players.size)
        } finally { routing.stop() }
    }

    @Test fun creationFailureIsNonFatalAndRetriedOnlyAfterStop() {
        var attempts = 0
        val routing = NativeTtsMediaRouting {
            if (++attempts == 1) throw IllegalStateException("No output available")
            preparedRoutingPlayer()
        }
        try {
            routing.start()
            routing.start()
            assertEquals(1, attempts)
            routing.stop()
            routing.start()
            assertEquals(2, attempts)
        } finally { routing.stop() }
    }

    @Test fun nullPlayerConsumesOnlyTheCurrentAttempt() {
        var attempts = 0
        val routing = NativeTtsMediaRouting { attempts++; null }
        routing.start()
        routing.start()
        assertEquals(1, attempts)
        routing.stop()
        routing.start()
        assertEquals(2, attempts)
        routing.stop()
    }
}

internal fun preparedRoutingPlayer(): MediaPlayer {
    val source = "native-tts-media-routing-test.wav"
    ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(source), ShadowMediaPlayer.MediaInfo(250, 0))
    return MediaPlayer().apply {
        setDataSource(source)
        prepare()
    }
}
