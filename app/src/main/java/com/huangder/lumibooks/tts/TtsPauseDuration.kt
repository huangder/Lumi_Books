package com.huangder.lumibooks.tts

enum class TtsPauseDuration(
    val storedValue: String,
    internal val phraseMillis: Long,
    internal val sentenceMillis: Long,
    internal val paragraphMillis: Long
) {
    STANDARD("standard", 0, 0, 0),
    LONG("long", 250, 450, 750),
    SHORT("short", 100, 180, 320),
    VERY_SHORT("very_short", 0, 60, 120);

    companion object {
        fun fromStoredValue(value: String?): TtsPauseDuration =
            entries.firstOrNull { it.storedValue == value } ?: STANDARD
    }
}
