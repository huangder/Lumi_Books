package com.huangder.lumibooks.tts

enum class TtsProsodyMode(val storedValue: String) {
    FOLLOW_ENGINE("follow_engine"),
    OVERRIDE("override");

    companion object {
        fun fromStoredValue(value: String?): TtsProsodyMode? =
            entries.firstOrNull { it.storedValue == value }

        fun resolve(storedMode: String?, hasLegacyValue: Boolean): TtsProsodyMode =
            fromStoredValue(storedMode)
                ?: if (hasLegacyValue) OVERRIDE else FOLLOW_ENGINE
    }
}

data class TtsProsodySettings(
    val speechRate: Float = 1f,
    val speechRateMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE,
    val pitch: Float = 1f,
    val pitchMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE
) {
    fun normalized(): TtsProsodySettings = copy(
        speechRate = speechRate.coerceIn(0.5f, 5f),
        pitch = pitch.coerceIn(0.5f, 2f)
    )
}
