package com.huangder.lumibooks.tts

data class TtsPageLocation(
    val chapterIndex: Int,
    val pageIndex: Int
)

data class TtsPageContent(
    val location: TtsPageLocation,
    val text: String,
    val previous: TtsPageLocation?,
    val next: TtsPageLocation?
)

data class TtsPageTurnRequest(
    val bookId: String,
    val location: TtsPageLocation
)
