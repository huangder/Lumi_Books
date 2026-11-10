package com.huangder.lumibooks.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

/**
 * Native TextToSpeech.speak() renders under the engine's UID, while Lumi owns the media
 * session. Android selects the media-button session from UIDs that actually played audio;
 * audio focus and MediaSession.isActive alone do not put Lumi in that list.
 *
 * Keep a small, looping PCM-silence track under Lumi's UID only during native speech. Do
 * not mute its volume: muted players can be excluded from Android's playback monitoring.
 * This preserves MultiTTS's native queue/background music without duplicating its speech.
 * Call on the engine's main thread, and release on pause, completion, stop or shutdown.
 */
internal class NativeTtsMediaRouting(
    private val createTrack: () -> AudioTrack = ::createRoutingTrack
) {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        var created: AudioTrack? = null
        try {
            val output = createTrack().also { created = it }
            // MODE_STATIC becomes STATE_INITIALIZED only after its first successful write.
            check(output.state != AudioTrack.STATE_UNINITIALIZED)
            val silence = ByteArray(FRAMES * 2)
            check(output.write(silence, 0, silence.size) == silence.size)
            check(output.state == AudioTrack.STATE_INITIALIZED)
            check(output.setLoopPoints(0, FRAMES, -1) == AudioTrack.SUCCESS)
            output.play()
            track = output
            DiagnosticLoggerRegistry.logger?.log("tts", "native_media_routing_started")
        } catch (error: Exception) {
            created?.let { runCatching { it.release() } }
            // A routing failure must not stop otherwise working native speech.
            Log.w("NativeTtsMediaRouting", "Unable to register native TTS audio playback", error)
            DiagnosticLoggerRegistry.logger?.log("tts", "native_media_routing_failed", throwable = error)
        }
    }

    fun stop() {
        val output = track ?: return
        track = null
        runCatching { output.stop() }
        runCatching { output.release() }
        DiagnosticLoggerRegistry.logger?.log("tts", "native_media_routing_stopped")
    }

    companion object {
        private const val SAMPLE_RATE = 8_000
        private const val FRAMES = SAMPLE_RATE // One second of mono PCM16, looped by AudioTrack.

        internal fun createRoutingTrack() = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            FRAMES * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }
}
