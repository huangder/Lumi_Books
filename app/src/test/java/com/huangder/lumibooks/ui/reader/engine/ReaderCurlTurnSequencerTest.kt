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
    fun waitingBurstKeepsOnlyOneLatestDirection() {
        val sequencer = ReaderCurlTurnSequencer()
        repeat(20) {
            sequencer.offerWhileWaiting(PageAnimationController.Direction.NEXT)
        }

        assertEquals(1, sequencer.pendingSteps)
        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)

        sequencer.offerWhileWaiting(PageAnimationController.Direction.PREV)
        assertEquals(0, sequencer.pendingSteps)
    }

    @Test
    fun queuedAnimationBurstCollapsesWhenTargetStartsWaiting() {
        val sequencer = ReaderCurlTurnSequencer()
        sequencer.settling()
        repeat(5) { sequencer.offer(PageAnimationController.Direction.NEXT) }

        val blockedTurn = sequencer.poll()
        sequencer.restore(blockedTurn)

        assertEquals(ReaderCurlTurnSequencer.State.WAITING_FOR_TARGET, sequencer.state)
        assertEquals(1, sequencer.pendingSteps)
        assertEquals(PageAnimationController.Direction.NEXT, sequencer.poll())
    }
}
