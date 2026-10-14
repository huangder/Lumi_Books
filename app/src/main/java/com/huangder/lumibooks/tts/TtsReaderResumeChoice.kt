package com.huangder.lumibooks.tts

/** Controls the reader viewport when a book opens, independently of starting audio. */
enum class TtsReaderResumeChoice(val storedValue: String) {
    KEEP_READING("keep_reading"),
    LISTENING_POSITION("listening_position");

    companion object {
        fun fromStoredValue(value: String?): TtsReaderResumeChoice? =
            entries.firstOrNull { it.storedValue == value }
    }
}
