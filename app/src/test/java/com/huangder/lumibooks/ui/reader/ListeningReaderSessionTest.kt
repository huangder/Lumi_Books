package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ListeningReaderSessionTest {
    private val target = TtsListeningPosition("book", 2, 3, 450, 123L)

    @Test fun explicitPageFollowResumesAfterRepeatedFullscreenLifecycleCallbacks() {
        val session = ListeningReaderSession()
        session.follow()
        repeat(3) {
            repeat(4) { session.suspendForBackground(42) }
            assertFalse(session.followEnabled.value)
            assertEquals(true, session.returnFromBackground(42, followUserPageTurns = true))
            assertTrue(session.followEnabled.value)
            assertNull(session.beginCheck())
            assertNull(session.prompt.value)
            assertNull(session.returnFromBackground(42, followUserPageTurns = true))
        }
    }

    @Test fun explicitPageFollowDoesNotBypassOpeningChoiceOrResumeADifferentSession() {
        for (nextSession in listOf(42L, 43L, null)) {
            val opening = ListeningReaderSession()
            opening.suspendForBackground(42)
            assertNull(opening.returnFromBackground(nextSession, followUserPageTurns = true))
            assertFalse(opening.followEnabled.value)
            assertNotNull(opening.beginCheck())
        }
        val session = ListeningReaderSession()
        session.follow()
        session.suspendForBackground(42)
        assertNull(session.returnFromBackground(43, followUserPageTurns = true))
        assertFalse(session.followEnabled.value)
    }

    @Test fun openingBeforeResumeStillAppliesTheSavedPositionPolicy() {
        for (audioSession in listOf(null, 42L)) {
            val session = ListeningReaderSession()
            repeat(3) { session.suspendForBackground(audioSession) }
            assertNull(session.returnFromBackground(audioSession))
            val stamp = session.beginCheck()!!
            assertTrue(session.isCurrent(stamp))
            session.requestJump(target)
            assertTrue(session.completeJump(target))
        }
    }

    @Test fun backgroundDuringPositionLoadRetriesInsteadOfLosingTheOpeningChoice() {
        val session = ListeningReaderSession()
        val first = session.beginCheck()!!
        session.suspendForBackground(42)
        assertFalse(session.isCurrent(first))
        assertNull(session.returnFromBackground(42))
        val retry = session.beginCheck()!!
        // The cancelled old coroutine must not release the newer check.
        session.cancelCheck(first)
        assertNull(session.beginCheck())
        session.requestJump(target)
        assertTrue(session.completeJump(target))
        assertTrue(session.isCurrent(retry).not())
    }

    @Test fun cancelledViewportCheckCanRetryWithoutAnotherBackgroundTransition() {
        val session = ListeningReaderSession()
        val stamp = session.beginCheck()!!
        assertNull(session.beginCheck())
        session.cancelCheck(stamp)
        assertNotNull(session.beginCheck())
        session.keepReading()
        assertNull(session.beginCheck())
    }

    @Test fun backgroundBeforeRendererAcceptsJumpRetriesTheOpeningPolicy() {
        val session = ListeningReaderSession()
        session.beginCheck()
        session.requestJump(target)
        assertNull(session.beginCheck())
        session.suspendForBackground(42)
        assertFalse(session.completeJump(target))
        assertNull(session.returnFromBackground(42))
        assertNotNull(session.beginCheck())
        session.requestJump(target)
        assertTrue(session.completeJump(target))
        session.suspendForBackground(42)
        assertEquals(true, session.returnFromBackground(42))
        assertNull(session.beginCheck())
    }

    @Test fun backgroundReturnDoesNotStartAnotherListeningResumeCheck() {
        val session = ListeningReaderSession()
        session.follow()
        repeat(4) { session.suspendForBackground(42) }
        assertFalse(session.followEnabled.value)
        assertEquals(true, session.returnFromBackground(42))
        assertTrue(session.followEnabled.value)
        assertNull(session.beginCheck())
        val manuallyRequested = target.copy(chapterIndex = 5, characterOffset = 900)
        session.requestJump(manuallyRequested, 42)
        assertFalse(session.isJumpSessionCurrent(43))
        assertTrue(session.isJumpSessionCurrent(42))
        assertTrue(session.completeJump(manuallyRequested))
        assertNull(session.returnFromBackground(42))
    }

    @Test fun backgroundReturnAfterManualBrowsingDoesNotRestartFollowingOrPrompt() {
        val session = ListeningReaderSession()
        session.follow()
        session.pageTurn()
        repeat(3) { session.suspendForBackground(8) }
        assertEquals(true, session.returnFromBackground(8))
        assertNull(session.beginCheck())
        assertNull(session.returnFromBackground(8))
        assertFalse(session.followEnabled.value)
    }

    @Test fun backgroundReturnKeepsManualBrowsingEligibleForTheNextGesture() {
        val session = ListeningReaderSession()
        session.follow()
        session.beginManualBrowse(8)
        session.settleManualBrowse(8, canPrompt = true)
        assertTrue(session.suspendForBackground(8))
        assertFalse(session.suspendForBackground(8))
        assertNull(session.manualBrowse.value)
        assertEquals(true, session.returnFromBackground(8))
        assertFalse(session.followEnabled.value)
        assertFalse(session.manualBrowse.value!!.showPrompt)
        assertFalse(session.manualBrowse.value!!.settled)
        session.beginManualBrowse(8)
        session.settleManualBrowse(8, canPrompt = true)
        assertTrue(session.manualBrowse.value!!.canPrompt)
    }

    @Test fun backgroundReturnFromAnExplicitReadingChoiceKeepsTheReadingPosition() {
        val session = openedSession()
        session.keepReading()
        session.suspendForBackground(9)
        assertEquals(true, session.returnFromBackground(9))
        assertFalse(session.followEnabled.value)
        assertNull(session.beginCheck())
    }

    @Test fun changedOrStoppedAudioSessionStillDoesNotRepeatOpeningPolicy() {
        for (nextSession in listOf(2L, null)) {
            val session = ListeningReaderSession()
            session.follow()
            session.suspendForBackground(1)
            assertNull(session.returnFromBackground(nextSession))
            assertNull(session.beginCheck())
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun manualBrowsingTimesOutOncePerSettledGestureAndResetsOnTheNextGesture() = runTest {
        val session = ListeningReaderSession()
        session.follow()
        session.beginManualBrowse(42)
        assertFalse(session.followEnabled.value)
        session.settleManualBrowse(43, canPrompt = true)
        assertFalse(session.manualBrowse.value!!.settled)
        session.settleManualBrowse(42, canPrompt = true)
        session.showManualBrowsePrompt(this)
        assertTrue(session.manualBrowse.value!!.showPrompt)
        advanceTimeBy(2_000)

        session.beginManualBrowse(42)
        session.settleManualBrowse(42, canPrompt = true)
        session.showManualBrowsePrompt(this)
        advanceTimeBy(2_999)
        assertTrue(session.manualBrowse.value!!.showPrompt)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(session.manualBrowse.value!!.showPrompt)
        session.showManualBrowsePrompt(this)
        assertFalse(session.manualBrowse.value!!.showPrompt)
        session.follow()
        assertNull(session.manualBrowse.value)
        assertTrue(session.followEnabled.value)
    }

    @Test fun browsingCannotOverrideAnExplicitReadingChoiceOrCarryToAnotherSession() {
        val session = ListeningReaderSession()
        session.keepReading()
        session.beginManualBrowse(42)
        assertNull(session.manualBrowse.value)
        session.follow()
        session.beginManualBrowse(42)
        session.beginManualBrowse(43)
        assertNull(session.manualBrowse.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun offscreenDuringDragPromptsImmediatelyAndFingerUpDoesNotRestartTheTimeout() = runTest {
        val session = ListeningReaderSession()
        session.follow()
        session.beginManualBrowse(42)
        fun update(visible: Boolean?) = session.updateManualBrowseVisibility(visible, true, true, this)
        assertFalse(update(true))
        assertFalse(session.manualBrowse.value!!.showPrompt)
        assertFalse(update(null))
        assertFalse(update(false))
        assertTrue(session.manualBrowse.value!!.showPrompt)
        assertFalse(session.manualBrowse.value!!.settled)
        assertFalse(session.followEnabled.value)
        advanceTimeBy(2_000)
        session.settleManualBrowse(42, canPrompt = true)
        assertFalse(update(false))
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(session.manualBrowse.value!!.showPrompt)
        assertFalse(update(false))
        assertFalse(session.manualBrowse.value!!.showPrompt)
        session.beginManualBrowse(42)
        assertFalse(update(false))
        assertTrue(session.manualBrowse.value!!.showPrompt)
        assertFalse(update(true))
        assertFalse(session.manualBrowse.value!!.showPrompt)
        assertFalse(session.followEnabled.value)
        session.settleManualBrowse(42, canPrompt = true)
        assertTrue(update(true))
        assertTrue(session.followEnabled.value)
        assertNull(session.manualBrowse.value)
    }

    @Test fun pageGesturesStillWaitForCommitBeforeOfferingThePrompt() = runTest {
        val session = ListeningReaderSession()
        session.follow()
        session.beginManualBrowse(42)
        session.updateManualBrowseVisibility(false, true, false, this)
        assertFalse(session.manualBrowse.value!!.showPrompt)
        session.settleManualBrowse(42, canPrompt = false)
        session.updateManualBrowseVisibility(false, true, false, this)
        assertFalse(session.manualBrowse.value!!.showPrompt)
    }

    @Test fun speechVisibilityUsesExclusiveOffsetsAndBothPages() {
        val visible = listOf(ReaderVisibleCharacters(0, 100, 200), ReaderVisibleCharacters(1, 0, 120))
        assertEquals(false, visible.containsSpeech(TtsSentencePosition(0, 80, 100)))
        assertEquals(false, visible.containsSpeech(TtsSentencePosition(0, 200, 220)))
        assertEquals(true, visible.containsSpeech(TtsSentencePosition(0, 99, 101)))
        assertEquals(true, visible.containsSpeech(TtsSentencePosition(1, 40, 60)))
        assertEquals(false, visible.containsSpeech(TtsSentencePosition(2, 40, 60)))
        assertNull(emptyList<ReaderVisibleCharacters>().containsSpeech(TtsSentencePosition(0, 0, 1)))
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
