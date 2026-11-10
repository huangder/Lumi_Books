package com.huangder.lumibooks.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

/**
 * Native TextToSpeech.speak() renders under the engine's UID, while Lumi owns the media
 * session. Android selects the media-button session from UIDs that actually played audio;
 * audio focus and MediaSession.isActive alone do not put Lumi in that list.
 *
 * Play one 250 ms silence clip under Lumi's UID when native speech starts or resumes.
 * Completion releases the player without rearming it, so sentence/page callbacks never
 * keep an extra audio output alive or repeatedly play silence. Explicit stop/pause rearms
 * the next attempt. Do not mute the volume: muted players may be excluded from monitoring.
 * All calls and MediaPlayer callbacks run on the engine's main thread.
 */
internal class NativeTtsMediaRouting(
    private val createPlayer: () -> MediaPlayer?
) {
    constructor(context: Context) : this({
        MediaPlayer.create(
            context.applicationContext,
            R.raw.tts_media_button_pulse,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    })

    private var player: MediaPlayer? = null
    private var attempted = false

    fun start() {
        if (attempted) return
        // Failure also consumes this attempt; do not retry on each subsequent sentence.
        attempted = true
        var created: MediaPlayer? = null
        try {
            val output = checkNotNull(createPlayer()) { "Unable to prepare TTS media routing clip" }
                .also { created = it }
            output.isLooping = false
            output.setOnCompletionListener { finish(it, "native_media_routing_completed") }
            output.setOnErrorListener { failed, what, extra ->
                finish(failed, "native_media_routing_failed", mapOf("what" to what, "extra" to extra))
                true
            }
            player = output
            output.start()
            DiagnosticLoggerRegistry.logger?.log("tts", "native_media_routing_started")
        } catch (error: Exception) {
            if (player === created) player = null
            created?.let(::release)
            // A routing failure must not stop otherwise working native speech.
            Log.w("NativeTtsMediaRouting", "Unable to register native TTS audio playback", error)
            DiagnosticLoggerRegistry.logger?.log("tts", "native_media_routing_failed", throwable = error)
        }
    }

    fun stop() {
        attempted = false
        player?.let { finish(it, "native_media_routing_stopped") }
    }

    private fun finish(output: MediaPlayer, event: String, attributes: Map<String, Any?> = emptyMap()) {
        // A late completion/error from a cancelled player must not release the current pulse.
        if (player !== output) return
        player = null
        release(output)
        DiagnosticLoggerRegistry.logger?.log("tts", event, attributes = attributes)
    }

    private fun release(output: MediaPlayer) {
        runCatching { output.setOnCompletionListener(null) }
        runCatching { output.setOnErrorListener(null) }
        runCatching { output.release() }
    }
}
