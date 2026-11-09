package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.*
import org.junit.Assert.*
import org.junit.Test

class ListeningTextTest {
    @Test fun subtitlesHaveClauseRowsAndWordProgressMovesFocusWithinOneUtterance() {
        val chapter = TtsChapterSnapshot(0, 1, "第一小句，第二小句，句子结束。")
        val page = TtsPageContent(TtsPageLocation(0, 0), chapter.text, null, null)
        val utterance = TtsTextSegment(chapter.text, 0, chapter.text.length)
        val text = listeningText(chapter, page, utterance)
        assertEquals(listOf("第一小句，", "第二小句，", "句子结束。"), text.sentences.map { it.segment.text })
        val progressed = text.withRangeProgress(TtsListeningFrame(chapter, page, utterance), TtsTextSegment("二", 6, 7))
        assertEquals(1, progressed.focus)
        assertSame(text.sentences, progressed.sentences)
    }

    @Test fun wordProgressAdvancesWithoutChangingSentenceIdentityOrFocus() {
        val chapter = TtsChapterSnapshot(0, 1, "First sentence. A sentence across pages. Last sentence.")
        val page = TtsPageContent(TtsPageLocation(0, 0), chapter.text, null, null)
        val utterance = TtsTextSegment("A sentence across pages.", 16, 40)
        val frame = TtsListeningFrame(chapter, page, utterance)
        val text = listeningText(chapter, page, utterance)
        val progressed = text.withRangeProgress(frame, TtsTextSegment("across", 27, 33))
        assertEquals(text.focus, progressed.focus)
        assertSame(text.sentences, progressed.sentences)
        assertTrue(progressed.progress > text.progress)
        assertEquals(27f / chapter.text.length, progressed.progress, .0001f)
        assertEquals(text, text.withRangeProgress(frame, TtsTextSegment("stale", 0, 5)))
    }

    @Test fun crossPageFragmentsResolveToTheSameWholeSentence() {
        val chapter = TtsChapterSnapshot(2, 5, "First sentence. A sentence across pages. Last sentence.")
        fun at(offset: Int, pageIndex: Int): ListeningText {
            val page = TtsPageContent(TtsPageLocation(2, pageIndex), chapter.text.substring(offset), null, null, startCharacterOffset = offset)
            return listeningText(chapter, page, TtsTextSegment("fragment", offset, offset + 3))
        }
        val first = at(16, 0)
        val continuation = at(27, 1)
        assertEquals(first.sentences[first.focus].id, continuation.sentences[continuation.focus].id)
        assertEquals("A sentence across pages.", continuation.sentences[continuation.focus].segment.text)
        assertEquals(3, continuation.sentences.size)
    }

    @Test fun dismissalRequiresDistanceOrBothFlingDistanceAndDownwardVelocity() {
        assertFalse(shouldDismissListening(180f, 1000f, 0f, 2f))
        assertTrue(shouldDismissListening(181f, 1000f, 0f, 2f))
        assertFalse(shouldDismissListening(95f, 1000f, 4000f, 2f))
        assertTrue(shouldDismissListening(97f, 1000f, 2000f, 2f))
        assertFalse(shouldDismissListening(97f, 1000f, -3000f, 2f))
    }
}
