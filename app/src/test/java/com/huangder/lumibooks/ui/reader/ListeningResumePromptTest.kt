package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.tts.TtsListeningPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningResumePromptTest {
    private val position = TtsListeningPosition("book", 9, 2, 450, 123L)

    @Test
    fun chapterSixReadingOffersChapterTenListeningAndIgnoresOtherBooksAndInvalidChapters() {
        assertTrue(shouldOfferListeningResume(position, "book", 12, 5, 0))
        assertFalse(shouldOfferListeningResume(position, "other", 12, 5, 0))
        assertFalse(shouldOfferListeningResume(position, "book", 9, 5, 0))
        assertFalse(shouldOfferListeningResume(null, "book", 12, 5, 0))
    }

    @Test
    fun changedPaginationComparesCharacterRangeAndSameVisibleTextDoesNotPrompt() {
        assertFalse(shouldOfferListeningResume(position, "book", 12, 9, 5, 400, 500))
        assertTrue(shouldOfferListeningResume(position, "book", 12, 9, 2, 0, 400))
        assertTrue(shouldOfferListeningResume(position.copy(characterOffset = 500),
            "book", 12, 9, 2, 400, 500))
        assertFalse(shouldOfferListeningResume(position, "book", 12, 9, 2))
        assertTrue(shouldOfferListeningResume(position, "book", 12, 9, 2, allowPageFallback = false))
    }

    @Test
    fun spreadAndContinuousVisibleRangesSuppressListeningJumpOnAnyVisiblePage() {
        val visible = listOf(ReaderVisibleCharacters(8, 200, 500), ReaderVisibleCharacters(9, 0, 500))
        assertFalse(shouldOfferListeningResume(position, "book", 12, 8, 3,
            allowPageFallback = false, visibleCharacters = visible))
        assertTrue(shouldOfferListeningResume(position.copy(characterOffset = 500), "book", 12, 8, 3,
            allowPageFallback = false, visibleCharacters = visible))
        assertFalse(shouldOfferListeningResume(position, "book", 12, 8, 0,
            visibleChapters = setOf(8, 9)))
        assertTrue(shouldOfferListeningResume(position, "book", 12, 8, 0,
            visibleChapters = setOf(8)))
    }

    @Test
    fun dialogInkFitsLightDarkAndInkScreenThemes() {
        assertEquals(Color.Black, listeningDialogContentColor(Color.White, Color.White, false))
        assertEquals(Color.White, listeningDialogContentColor(Color.Black, Color.Black, false))
        assertEquals(Color(0xFF3E2723), listeningDialogContentColor(
            Color(0xFFF5E6D3), Color(0xFF3E2723), false))
        assertEquals(Color.Black, listeningDialogContentColor(Color.Black, Color.White, true))
    }
}
