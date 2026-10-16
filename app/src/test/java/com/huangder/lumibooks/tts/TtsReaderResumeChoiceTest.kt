package com.huangder.lumibooks.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsReaderResumeChoiceTest {
    private val listening = TtsListeningPosition("book", 5, 2, 450, 123L)
    private val furthest = TtsReaderResumeChoice.FURTHEST_PROGRESS

    @Test fun laterChapterWinsRegardlessOfPageAndCharacterNumbers() {
        assertTrue(furthest.shouldUseListeningPosition(listening, 4, 99, 9_999))
        assertFalse(furthest.shouldUseListeningPosition(listening, 6, 0, 0))
    }

    @Test fun characterAnchorsWinOverPageNumbersFromAnOlderLayout() {
        assertTrue(furthest.shouldUseListeningPosition(listening, 5, 20, 400))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, 0, 500))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, 0, 450))
    }

    @Test fun pageOnlyAnchorsUseTheLaterPageAndTiesKeepReading() {
        assertTrue(furthest.shouldUseListeningPosition(listening, 5, 1))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, 3))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, 2))
    }

    @Test fun continuousAndRendererAnchorsWorkWithoutComparablePageNumbers() {
        assertTrue(furthest.shouldUseListeningPosition(listening, 5, null, 400))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, null, 500))
        assertFalse(furthest.shouldUseListeningPosition(listening, 5, null))
    }

    @Test fun physicalPageBooksCompareTheirPageAsTheChapterAnchor() {
        val pdfListening = listening.copy(chapterIndex = 12, pageIndex = 0, characterOffset = 0)
        assertTrue(furthest.shouldUseListeningPosition(pdfListening, 11, 0))
        assertFalse(furthest.shouldUseListeningPosition(pdfListening, 13, 0))
        assertFalse(furthest.shouldUseListeningPosition(pdfListening, 12, 0))
    }

    @Test fun explicitChoicesKeepTheirBehaviorRegardlessOfWhichPositionIsAhead() {
        assertTrue(TtsReaderResumeChoice.LISTENING_POSITION.shouldUseListeningPosition(listening, 6, 0, 0))
        assertFalse(TtsReaderResumeChoice.KEEP_READING.shouldUseListeningPosition(listening, 4, 0, 0))
    }
}
