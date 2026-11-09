package com.huangder.lumibooks.tts

enum class ListeningDisplayStyle(val storedValue: String) {
    COVER("cover"), SUBTITLES("subtitles");

    companion object {
        fun fromStoredValue(value: String?) = entries.firstOrNull { it.storedValue == value } ?: COVER
    }
}

enum class SubtitleSeekGesture(val storedValue: String) {
    DOUBLE_TAP("double_tap"), SINGLE_TAP("single_tap");

    companion object {
        fun fromStoredValue(value: String?) = entries.firstOrNull { it.storedValue == value } ?: DOUBLE_TAP
    }
}

data class ListeningPageSettings(
    val defaultDisplayStyle: ListeningDisplayStyle = ListeningDisplayStyle.COVER,
    val seekGesture: SubtitleSeekGesture = SubtitleSeekGesture.DOUBLE_TAP
)
