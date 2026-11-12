package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningFrame
import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPageLocation
import com.huangder.lumibooks.tts.TtsTextExtractor
import com.huangder.lumibooks.tts.TtsTextSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsHighlightTest {
    @Test fun longSpokenSentenceKeepsEverySubtitleClauseInTheReaderHighlight() {
        val text = "这是会在正文中换行的一段长句，".repeat(6) + "到这里才结束。"
        val sentence = TtsTextSegment(text, 120, 120 + text.length)
        val frame = frame(sentence)
        val clauses = TtsTextExtractor().splitIntoClauses(sentence)
        assertTrue(clauses.size > 3)
        val position = frame.readerHighlightPosition(fullscreen = false)!!
        assertEquals(TtsSentencePosition(3, 120, 120 + text.length), position)
        clauses.forEach { clause ->
            assertTrue(clause.startCharacterOffset >= position.startOffset)
            assertTrue(clause.endCharacterOffset <= position.endOffset)
        }
    }

    @Test fun shortSentencesAndPageTailRangesKeepOriginalSourceOffsets() {
        val sentence = TtsTextSegment("好。", 450, 452)
        // A spoken sentence can extend beyond the visible page after a cross-page merge.
        val frame = frame(sentence).copy(page = frame(sentence).page!!.copy(endCharacterOffset = 451))
        assertEquals(TtsSentencePosition(3, 450, 452), frame.readerHighlightPosition(false))
    }

    @Test fun fullscreenAndIncompletePlaybackSnapshotsClearTheReaderHighlight() {
        val frame = frame(TtsTextSegment("句子。", 0, 3))
        assertNull(frame.readerHighlightPosition(true))
        assertNull(frame.copy(utterance = null).readerHighlightPosition(false))
        assertNull(frame.copy(page = null).readerHighlightPosition(false))
        assertNull(TtsListeningFrame().readerHighlightPosition(false))
    }

    private fun frame(sentence: TtsTextSegment) = TtsListeningFrame(
        page = TtsPageContent(TtsPageLocation(3, 2), sentence.text, null, null,
            sentence.startCharacterOffset, sentence.endCharacterOffset),
        utterance = sentence
    )
}
