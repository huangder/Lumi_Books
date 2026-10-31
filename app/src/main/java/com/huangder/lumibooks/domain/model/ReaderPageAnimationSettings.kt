package com.huangder.lumibooks.domain.model

data class ReaderPageAnimationSettings(
    val slideDurationMs: Int = SLIDE_DEFAULT_MS,
    val scrollDurationMs: Int = SCROLL_DEFAULT_MS,
    val fadeDurationMs: Int = FADE_DEFAULT_MS,
    val curlDurationMs: Int = CURL_DEFAULT_MS,
    val waveDurationMs: Int = WAVE_DEFAULT_MS
) {
    fun durationFor(mode: String): Int = when (mode) {
        MODE_SCROLL -> scrollDurationMs
        MODE_FADE -> fadeDurationMs
        MODE_CURL -> curlDurationMs
        MODE_WAVE -> waveDurationMs
        else -> slideDurationMs
    }

    fun withDuration(mode: String, durationMs: Int): ReaderPageAnimationSettings = when (mode) {
        MODE_SCROLL -> copy(scrollDurationMs = sanitizeDuration(mode, durationMs))
        MODE_FADE -> copy(fadeDurationMs = sanitizeDuration(mode, durationMs))
        MODE_CURL -> copy(curlDurationMs = sanitizeDuration(mode, durationMs))
        MODE_WAVE -> copy(waveDurationMs = sanitizeDuration(mode, durationMs))
        else -> copy(slideDurationMs = sanitizeDuration(MODE_SLIDE, durationMs))
    }

    companion object {
        const val MODE_SLIDE = "slide"
        const val MODE_SCROLL = "scroll"
        const val MODE_FADE = "fade"
        const val MODE_CURL = "curl"
        const val MODE_WAVE = "wave"

        const val SLIDE_DEFAULT_MS = 260
        const val SCROLL_DEFAULT_MS = 260
        const val FADE_DEFAULT_MS = 400
        const val CURL_DEFAULT_MS = 800
        const val WAVE_DEFAULT_MS = 400

        fun rangeFor(mode: String): IntRange = when (mode) {
            MODE_CURL -> 300..1200
            else -> 100..1000
        }

        fun stepFor(mode: String): Int = if (mode == MODE_CURL) 25 else 10

        fun defaultFor(mode: String): Int = when (mode) {
            MODE_FADE -> FADE_DEFAULT_MS
            MODE_CURL -> CURL_DEFAULT_MS
            MODE_WAVE -> WAVE_DEFAULT_MS
            else -> SLIDE_DEFAULT_MS
        }

        fun sanitizeDuration(mode: String, value: Int): Int {
            val range = rangeFor(mode)
            val step = stepFor(mode)
            val clamped = value.coerceIn(range)
            return (range.first + ((clamped - range.first + step / 2) / step) * step)
                .coerceIn(range)
        }

        // Runtime values have already been speed-adjusted; preserve their millisecond precision.
        fun sanitizeRuntimeDuration(mode: String, value: Int): Int =
            value.coerceIn(1, rangeFor(mode).last)
    }
}
