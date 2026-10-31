package com.huangder.lumibooks.tts

/** Detects rendering, independent of synthesis and buffer writes. Reports transitions only. */
internal class RenderedAudioActivity(
    private val monotonicMs: () -> Long,
    initialFrame: Long = 0
) {
    private var lastFrame = initialFrame
    private var lastAdvance: Long? = null
    private var active = false

    @Synchronized
    fun update(frame: Long, paused: Boolean): Boolean? {
        val now = monotonicMs()
        if (paused) lastAdvance = null
        else if (frame > lastFrame) lastAdvance = now
        lastFrame = frame
        // AudioTrack updates its playback head in batches. Tolerate that granularity,
        // but stop counting a starved buffer rather than waiting for a playback error.
        val rendering = !paused && lastAdvance?.let { now - it < 50L } == true
        return transition(rendering)
    }

    @Synchronized
    fun pause(): Boolean? {
        lastAdvance = null
        return transition(false)
    }

    private fun transition(rendering: Boolean): Boolean? {
        if (active == rendering) return null
        active = rendering
        return rendering
    }
}
