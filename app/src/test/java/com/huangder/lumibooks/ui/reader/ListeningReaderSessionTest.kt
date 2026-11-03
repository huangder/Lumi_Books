package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningPosition
import org.junit.Assert.*
import org.junit.Test

class ListeningReaderSessionTest {
    private val target = TtsListeningPosition("book", 2, 3, 450, 123L)

    @Test fun repeatedBackgroundEventsPreserveFollowIntentAndRestoreOnlyOnce() {
        val session = ListeningReaderSession()
        session.follow()
        repeat(4) { session.suspendForBackground(42) }
        assertFalse(session.followEnabled.value)
        assertEquals(true, session.returnFromBackground(42))
        val live = target.copy(chapterIndex = 5, characterOffset = 900)
        session.requestJump(live, 42)
        assertNull(session.returnFromBackground(42))
        assertFalse(session.isJumpSessionCurrent(43))
        assertTrue(session.isJumpSessionCurrent(42))
        assertTrue(session.completeJump(live))
        assertTrue(session.followEnabled.value)
    }

    @Test fun backgroundReturnAfterManualBrowsingTracksTheLiveSessionWithoutAnotherOpeningPrompt() {
        val session = ListeningReaderSession()
        session.follow()
        session.pageTurn()
        repeat(3) { session.suspendForBackground(8) }
        assertEquals(true, session.returnFromBackground(8))
        assertNull(session.returnFromBackground(8))
        session.requestJump(target, 8)
        assertEquals(target, session.jumpRequest.value)
        assertTrue(session.completeJump(target))
        assertTrue(session.followEnabled.value)
    }

    @Test fun backgroundReturnFromAnExplicitReadingChoiceAlsoTracksTheLiveSession() {
        val session = openedSession()
        session.keepReading()
        session.suspendForBackground(9)
        assertEquals(true, session.returnFromBackground(9))
        assertFalse(session.followEnabled.value)
        session.requestJump(target, 9)
        assertTrue(session.completeJump(target))
        assertTrue(session.followEnabled.value)
    }

    @Test fun changedOrStoppedAudioSessionUsesExistingOpeningPolicy() {
        for (nextSession in listOf(2L, null)) {
            val session = ListeningReaderSession()
            session.follow()
            session.suspendForBackground(1)
            assertNull(session.returnFromBackground(nextSession))
            assertNotNull(session.beginCheck())
            assertFalse(session.followEnabled.value)
        }
    }

    @Test fun leavingReaderClearsTemporaryFollowIntent() {
        val session = ListeningReaderSession()
        session.follow()
        session.suspendForBackground(1)
        session.hide()
        assertNull(session.returnFromBackground(1))
        assertNotNull(session.beginCheck())
    }

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
