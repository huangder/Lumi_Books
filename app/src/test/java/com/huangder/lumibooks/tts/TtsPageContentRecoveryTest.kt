package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsPageContentRecoveryTest {
    @Test
    fun recoversMissingPageTextFromTheMatchingChapterRange() {
        val page = TtsPageContent(
            location = TtsPageLocation(4, 12),
            text = "",
            previous = TtsPageLocation(4, 11),
            next = TtsPageLocation(4, 13),
            startCharacterOffset = 2,
            endCharacterOffset = 6
        )

        val recovered = recoverTtsPageText(page, TtsChapterSnapshot(4, 8, "甲乙丙丁戊己庚辛"))

        assertEquals("丙丁戊己", recovered?.text)
        assertEquals(page.location, recovered?.location)
        assertEquals(page.next, recovered?.next)
        assertEquals(2, recovered?.startCharacterOffset)
        assertEquals(6, recovered?.endCharacterOffset)
    }

    @Test
    fun refusesDifferentChapterAndEmptyRanges() {
        val page = TtsPageContent(
            location = TtsPageLocation(4, 12),
            text = "",
            previous = null,
            next = null,
            startCharacterOffset = 6,
            endCharacterOffset = 6
        )

        assertNull(recoverTtsPageText(page, TtsChapterSnapshot(5, 8, "章节文本")))
        assertNull(recoverTtsPageText(page, TtsChapterSnapshot(4, 8, "章节文本")))
    }
}
