package com.huangder.lumibooks.tts

import java.security.MessageDigest

data class TtsPageLocation(
    val chapterIndex: Int,
    val pageIndex: Int
)

data class TtsPageContent(
    val location: TtsPageLocation,
    val text: String,
    val previous: TtsPageLocation?,
    val next: TtsPageLocation?,
    /** Offset in the text source used to build the current layout where [text] begins. */
    val startCharacterOffset: Int = 0
) {
    val resumeFingerprint: String
        get() = buildTtsPageFingerprint(location, startCharacterOffset, text)
}
data class TtsTextSegment(
    val text: String,
    val startCharacterOffset: Int,
    val endCharacterOffset: Int,
    val canContinueAcrossPage: Boolean = true
)

data class TtsPageTurnRequest(
    val bookId: String,
    val location: TtsPageLocation
)


internal fun buildTtsPageFingerprint(
    location: TtsPageLocation,
    startCharacterOffset: Int,
    text: String
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(
        location.chapterIndex.toString(),
        location.pageIndex.toString(),
        startCharacterOffset.toString(),
        text
    ).forEach { value ->
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}