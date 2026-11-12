package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningFrame

/** Keep the complete spoken range and its page in the same playback snapshot. */
internal fun TtsListeningFrame.readerHighlightPosition(fullscreen: Boolean): TtsSentencePosition? {
    if (fullscreen) return null
    val sentence = utterance ?: return null
    val sourcePage = page ?: return null
    return TtsSentencePosition(
        chapterIndex = sourcePage.location.chapterIndex,
        startOffset = sentence.startCharacterOffset,
        endOffset = sentence.endCharacterOffset
    )
}
