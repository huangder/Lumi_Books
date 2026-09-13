package com.huangder.lumibooks.tts

/** A playback-session-owned source of stable, paginated text. */
interface TtsPageSource {
    suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent?

    /**
     * Resolves the page that contains [characterOffset] inside [chapterIndex].
     * Sources that cannot map chapter offsets return null and callers fall back to page indexing.
     */
    suspend fun locatePage(chapterIndex: Int, characterOffset: Int): TtsPageContent? = null

    fun close()
}
