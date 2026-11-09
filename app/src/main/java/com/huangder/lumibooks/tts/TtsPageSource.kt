package com.huangder.lumibooks.tts

/** A playback-session-owned source of stable, paginated text. */
interface TtsPageSource {
    suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent?

    /** Same character coordinate space as getPage; PDF pages are chapters here. */
    suspend fun getChapter(chapterIndex: Int): TtsChapterSnapshot? = null

    /**
     * Resolves the page that contains [characterOffset] inside [chapterIndex].
     * Sources that cannot map chapter offsets return null and callers fall back to page indexing.
     */
    suspend fun locatePage(chapterIndex: Int, characterOffset: Int): TtsPageContent? = null

    /** Temporarily avoid view-backed text providers while visual following is unavailable. */
    fun setReaderVisible(visible: Boolean) {}

    /** Release reader-only providers before its views are destroyed. */
    fun detachReader() {}

    fun close()
}

data class TtsChapterSnapshot(val index: Int, val count: Int, val text: String)

/** Published atomically so chapter changes never pair a new page with an old sentence. */
data class TtsListeningFrame(
    val chapter: TtsChapterSnapshot? = null, val page: TtsPageContent? = null, val utterance: TtsTextSegment? = null
)
