package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningPosition
import org.junit.Assert.*
import org.junit.Test

class ListeningReaderSessionTest {
    private val target = TtsListeningPosition("book", 2, 3, 450, 123L)

    private fun openedSession() = ListeningReaderSession().apply {
        val opening = beginCheck()!!
        assertTrue(isCurrent(opening))
        offer(target)
    }

    @Test fun repeatedPageTurnsKeepTheHeadphoneAnchorAndItsBookPage() {
        val session = openedSession()
        val pages = ReaderBookPages(chapterCounts = listOf(10, 20, 6),
            chapterStarts = mapOf(2 to listOf(0, 100, 250, 400, 600, 800)))
        repeat(5) {
            session.pageTurn()
            val hint = session.prompt.value!!
            assertEquals(target, hint)
            assertEquals(34, pages.pageNumber(hint.chapterIndex,
                pages.pageAtCharacter(hint.chapterIndex, hint.characterOffset)!!))
            assertNull(session.jumpRequest.value)
            assertFalse(session.followEnabled.value)
        }
        assertNull(session.beginCheck())
    }

    @Test fun clickingAfterBrowsingStillJumpsToSavedListeningAnchor() {
        val session = openedSession()
        repeat(4) { session.pageTurn() }
        session.requestJump(session.prompt.value!!)
        assertNull(session.prompt.value)
        assertEquals(target, session.jumpRequest.value)
        assertFalse(session.completeJump(target.copy(characterOffset = 999)))
        assertTrue(session.completeJump(target))
        assertTrue(session.followEnabled.value)
        assertNull(session.jumpRequest.value)
        session.navigate()
        assertFalse(session.followEnabled.value)
        assertNull(session.beginCheck())
    }

    @Test fun deliberateJumpEndsOpeningHintAndDoesNotReofferIt() {
        val session = openedSession()
        session.navigate()
        assertNull(session.prompt.value)
        session.pageTurn()
        assertNull(session.prompt.value)
        assertNull(session.beginCheck())
    }

    @Test fun expiryAndLeavingReaderClearTheHintWhileReopeningAllowsANewCheck() {
        val session = openedSession()
        session.keepReading()
        assertNull(session.prompt.value)
        assertNull(session.beginCheck())
        session.hide()
        assertNotNull(session.beginCheck())
    }

    @Test fun pageTurnDetachesAudioFollowAndCancelsPendingJump() {
        val session = openedSession()
        session.requestJump(target)
        session.pageTurn()
        assertFalse(session.completeJump(target))
        assertNull(session.jumpRequest.value)
        session.follow()
        assertTrue(session.followEnabled.value)
        session.pageTurn()
        assertFalse(session.followEnabled.value)
    }

    @Test fun browsingInvalidatesAnOpeningCheckThatHasNotFinishedLoading() {
        val session = ListeningReaderSession()
        val opening = session.beginCheck()!!
        session.pageTurn()
        assertFalse(session.isCurrent(opening))
        assertNull(session.beginCheck())
        assertNull(session.prompt.value)
    }
}
