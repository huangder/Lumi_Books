package com.huangder.lumibooks.tts

import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35, 36], manifest = Config.NONE)
class NativeTtsMediaRoutingTest {
    @Test fun routingPlaysAsLumiMediaAndReleasesBeforeRestart() {
        val tracks = mutableListOf<AudioTrack>()
        val routing = NativeTtsMediaRouting { NativeTtsMediaRouting.createRoutingTrack().also(tracks::add) }
        try {
            routing.start()
            routing.start()
            assertEquals(1, tracks.size)
            if (Build.VERSION.SDK_INT >= 29) {
                assertEquals(AudioAttributes.USAGE_MEDIA, tracks.single().audioAttributes.usage)
            }
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, tracks.single().playState)
            routing.stop()
            assertEquals(AudioTrack.STATE_UNINITIALIZED, tracks.single().state)
            routing.stop()
            routing.start()
            assertEquals(2, tracks.size)
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, tracks.last().playState)
        } finally { routing.stop() }
        assertEquals(AudioTrack.STATE_UNINITIALIZED, tracks.last().state)
    }

    @Test fun unavailableAudioOutputDoesNotThrowAndCanBeRetried() {
        var attempts = 0
        val routing = NativeTtsMediaRouting {
            if (++attempts == 1) throw IllegalStateException("No output available")
            NativeTtsMediaRouting.createRoutingTrack()
        }
        try {
            routing.start()
            routing.start()
            assertEquals(2, attempts)
        } finally { routing.stop() }
    }
}
