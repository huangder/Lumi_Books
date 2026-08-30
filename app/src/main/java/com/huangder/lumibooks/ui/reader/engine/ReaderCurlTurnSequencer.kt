package com.huangder.lumibooks.ui.reader.engine

internal enum class CurlRunningInputDisposition {
    QUEUE,
    REEVALUATE,
    ABORT_AND_REEVALUATE
}

internal fun curlRunningInputDisposition(
    handoff: PageAnimationController.RunningFlipHandoff
): CurlRunningInputDisposition = when (handoff) {
    PageAnimationController.RunningFlipHandoff.COMPLETING_ASYNCHRONOUSLY ->
        CurlRunningInputDisposition.QUEUE
    PageAnimationController.RunningFlipHandoff.COMPLETED_SYNCHRONOUSLY ->
        CurlRunningInputDisposition.REEVALUATE
    PageAnimationController.RunningFlipHandoff.NOT_COMMITTED ->
        CurlRunningInputDisposition.ABORT_AND_REEVALUATE
}

/** Pending intent for reflowed Curl turns. It stores a signed balance, not pages. */
internal class ReaderCurlTurnSequencer {
    enum class State { IDLE, DRAGGING, SETTLING, WAITING_FOR_TARGET, DESTROYED }

    var state: State = State.IDLE
        private set
    var pendingSteps: Int = 0
        private set

    fun offer(direction: PageAnimationController.Direction) {
        if (state == State.DESTROYED) return
        when (direction) {
            PageAnimationController.Direction.NEXT -> pendingSteps++
            PageAnimationController.Direction.PREV -> pendingSteps--
            PageAnimationController.Direction.NONE -> return
        }
    }

    /**
     * While no target frame exists, retain one latest intent instead of replaying
     * a burst after the finger has already been released.
     */
    fun offerWhileWaiting(direction: PageAnimationController.Direction) {
        if (state == State.DESTROYED || direction == PageAnimationController.Direction.NONE) return
        val incoming = if (direction == PageAnimationController.Direction.NEXT) 1 else -1
        pendingSteps = when {
            pendingSteps == 0 -> incoming
            pendingSteps.sign() == incoming -> incoming
            else -> 0
        }
        state = State.WAITING_FOR_TARGET
    }

    fun poll(): PageAnimationController.Direction {
        if (state == State.DESTROYED || pendingSteps == 0) {
            return PageAnimationController.Direction.NONE
        }
        return if (pendingSteps > 0) {
            pendingSteps--
            PageAnimationController.Direction.NEXT
        } else {
            pendingSteps++
            PageAnimationController.Direction.PREV
        }
    }

    fun restore(direction: PageAnimationController.Direction) {
        offerWhileWaiting(direction)
    }

    fun dragging() = moveTo(State.DRAGGING)

    fun settling() = moveTo(State.SETTLING)

    fun waitingForTarget() = moveTo(State.WAITING_FOR_TARGET)

    fun idle() = moveTo(State.IDLE)

    fun clear() {
        if (state == State.DESTROYED) return
        pendingSteps = 0
        state = State.IDLE
    }

    fun destroy() {
        pendingSteps = 0
        state = State.DESTROYED
    }

    private fun moveTo(next: State) {
        if (state != State.DESTROYED) state = next
    }

    private fun Int.sign(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }
}
