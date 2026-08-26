package com.huangder.lumibooks.tts

/** A playback-session-owned source of stable, paginated text. */
interface TtsPageSource {
    suspend fun getPage(chapterIndex: Int, pageIndex: Int): TtsPageContent?

    fun close()
}
