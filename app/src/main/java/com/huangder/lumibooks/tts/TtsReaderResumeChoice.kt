package com.huangder.lumibooks.tts

/** Controls the reader viewport when a book opens, independently of starting audio. */
enum class TtsReaderResumeChoice(val storedValue: String) {
    KEEP_READING("keep_reading"),
    LISTENING_POSITION("listening_position"),
    FURTHEST_PROGRESS("furthest_progress");

    /** Prefer character anchors because saved page numbers can belong to an older layout. */
    fun shouldUseListeningPosition(
        listening: TtsListeningPosition,
        readingChapter: Int,
        readingPage: Int?,
        readingCharacter: Int? = null
    ): Boolean = when (this) {
        KEEP_READING -> false
        LISTENING_POSITION -> true
        FURTHEST_PROGRESS -> when {
            listening.chapterIndex != readingChapter -> listening.chapterIndex > readingChapter
            readingCharacter != null -> listening.characterOffset > readingCharacter
            readingPage != null -> listening.pageIndex > readingPage
            else -> false
        }
    }

    companion object {
        fun fromStoredValue(value: String?): TtsReaderResumeChoice? =
            entries.firstOrNull { it.storedValue == value }
    }
}
