package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCurlTurnSequencerTest {
    @Test
    fun asynchronousHandoffQueuesWithoutAbortingCurrentTurn() {
        assertEquals(
            CurlRunningInputDisposition.QUEUE,
            curlRunningInputDisposition(
                PageAnimationController.RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY
            )
        )
        assertEquals(
            CurlRunningInputDisposition.REEVALUATE,
            curlRunningInputDisposition(
                PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY
            )
        )
        assertEquals(
            CurlRunningInputDisposition.ABORT_AND_REEVALUATE,
            curlRunningInputDisposition(
                PageAnimationController.RunningFlipHandoff.NOT_COMMITTED
            )
        )
    }

    @Test
    fun twentyRapidNextInputsProduceTwentySteps() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        repeat(20) { sequencer.offer(PageAnimationController.Direction.NEXT) }

        var turns = 0
        while (sequencer.poll() == PageAnimationController.Direction.NEXT) turns++

        assertEquals(20, turns)
        assertEquals(0, sequencer.pendingSteps)
    }

    @Test
    fun reverseInputsCancelPendingOppositeSteps() {
        val sequencer = ReaderCurlTurnSequencer()
        repeat(10) { sequencer.offer(PageAnimationController.Direction.NEXT) }
        repeat(3) { sequencer.offer(PageAnimationController.Direction.PREV) }

        assertEquals(7, sequencer.pendingSteps)
    }

    @Test
    fun waitingStepCanBeRestoredAndResumed() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.offer(PageAnimationController.Direction.NEXT)
        val waiting = sequencer.poll()
        sequencer.restore(waiting)

        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)
        assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
    }

    @Test
    fun lifecycleClearAndDestroyDiscardIntent() {
        val sequencer = ReaderCurlTurnSequencer()
        repeat(4) { sequencer.offer(PageAnimationController.Direction.NEXT) }
        sequencer.clear()
        assertEquals(0, sequencer.pendingSteps)
        assertEquals(ReaderCurlTurnSequencer.State.IDLE, sequencer.state)

        sequencer.offer(PageAnimationController.Direction.PREV)
        sequencer.destroy()
        sequencer.offer(PageAnimationController.Direction.NEXT)
        assertEquals(0, sequencer.pendingSteps)
        assertEquals(ReaderCurlTurnSequencer.State.DESTROYED, sequencer.state)
    }

    @Test
    fun inputRecordedDuringSettleOrBounceRemainsAvailable() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        sequencer.offer(PageAnimationController.Direction.NEXT)
        sequencer.idle()

        assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
    }

    @Test
    fun waitingBurstPreservesEveryAcceptedTurn() {
        val sequencer = ReaderCurlTurnSequencer()
        repeat(20) {
            sequencer.offerWhileWaiting(PageAnimationController.Direction.NEXT)
        }

        assertEquals(20, sequencer.pendingSteps)
        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)

        sequencer.offerWhileWaiting(PageAnimationController.Direction.PREV)
        assertEquals(19, sequencer.pendingSteps)
    }

    @Test
    fun inFlightOppositeInputCancelsOnlyTheNewestPendingStep() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        repeat(8) { sequencer.offer(PageAnimationController.Direction.NEXT) }
        sequencer.offer(PageAnimationController.Direction.PREV)

        assertEquals(7, sequencer.pendingSteps)
        repeat(7) {
            assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
        }
        assertEquals(PageAnimationController.Direction.NONE, sequencer.poll())
    }

    @Test
    fun queuedAnimationBurstRemainsOrderedWhenTargetStartsWaiting() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        repeat(5) { sequencer.offer(PageAnimationController.Direction.NEXT) }

        val blockedTurn = sequencer.poll()
        sequencer.restore(blockedTurn)

        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)
        assertEquals(5, sequencer.pendingSteps)
        repeat(5) {
            assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
        }
    }

    @Test
    fun rapidQueuedSwipesKeepTheirOwnCornerStartPositions() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        sequencer.offer(PageAnimationController.Direction.NEXT, gestureStartY = 1810f)
        sequencer.offer(PageAnimationController.Direction.NEXT, gestureStartY = 1940f)

        val first = sequencer.pollTurn()
        val second = sequencer.pollTurn()

        assertEquals(PageAnimationController.Direction.NEXT, first.direction)
        assertEquals(1810f, first.gestureStartY ?: Float.NaN, 0f)
        assertEquals(PageAnimationController.Direction.NEXT, second.direction)
        assertEquals(1940f, second.gestureStartY ?: Float.NaN, 0f)
    }

    @Test
    fun crossChapterWaitRestoresTheBlockedSwipesCornerPosition() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.offer(PageAnimationController.Direction.NEXT, gestureStartY = 1900f)
        val blockedTurn = sequencer.pollTurn()

        sequencer.restore(blockedTurn)
        val resumedTurn = sequencer.pollTurn()

        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)
        assertEquals(PageAnimationController.Direction.NEXT, resumedTurn.direction)
        assertEquals(1900f, resumedTurn.gestureStartY ?: Float.NaN, 0f)
    }

    @Test
    fun queuedTurnPreservesInputTypeAccelerationAndOriginGeneration() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.offer(
            direction = PageAnimationController.Direction.NEXT,
            gestureStartY = 1780f,
            input = CurlTurnInput.SWIPE,
            expedited = true,
            pageGeneration = 42L
        )

        val turn = sequencer.pollTurn()

        assertEquals(PageAnimationController.Direction.NEXT, turn.direction)
        assertEquals(1780f, turn.gestureStartY ?: Float.NaN, 0f)
        assertEquals(CurlTurnInput.SWIPE, turn.input)
        assertEquals(true, turn.expedited)
        assertEquals(42L, turn.pageGeneration)
    }
}
