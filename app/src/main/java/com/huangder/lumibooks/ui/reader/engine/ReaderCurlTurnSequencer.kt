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

internal data class QueuedCurlTurn(
    val direction: PageAnimationController.Direction,
    val gestureStartY: Float? = null,
    val input: CurlTurnInput = CurlTurnInput.TAP,
    val expedited: Boolean = false,
    val pageGeneration: Long = 0L
)

internal enum class CurlTurnInput { TAP, SWIPE }

/**
 * Ordered pending curl intents shared by the reflowed and WebView readers.
 *
 * Same-direction input is lossless. An opposite input only cancels the newest
 * turn that has not started yet, so changing direction cannot create a delayed
 * back-and-forth animation through pages the user has not seen.
 */
internal class ReaderCurlTurnSequencer {
    enum class State { IDLE, DRAGGING, SETTLING, WAITING_FOR_TARGET, DESTROYED }

    var state: State = State.IDLE
        private set
    private val pendingTurns = ArrayDeque<QueuedCurlTurn>()

    val pendingSteps: Int
        get() {
            val direction = pendingTurns.firstOrNull()?.direction ?: return 0
            return if (direction == PageAnimationController.Direction.NEXT) {
                pendingTurns.size
            } else {
                -pendingTurns.size
            }
        }

    fun offer(
        direction: PageAnimationController.Direction,
        gestureStartY: Float? = null,
        input: CurlTurnInput = CurlTurnInput.TAP,
        expedited: Boolean = false,
        pageGeneration: Long = 0L
    ) {
        if (state == State.DESTROYED) return
        if (direction == PageAnimationController.Direction.NONE) return
        val newest = pendingTurns.lastOrNull()
        if (newest != null && newest.direction != direction) {
            pendingTurns.removeLast()
            return
        }
        pendingTurns.addLast(
            QueuedCurlTurn(
                direction = direction,
                gestureStartY = gestureStartY?.takeIf { it.isFinite() },
                input = input,
                expedited = expedited,
                pageGeneration = pageGeneration
            )
        )
    }

    /**
     * Waiting changes lifecycle state only. It must never collapse accepted
     * input because the missing target may simply be a cross-chapter preload.
     */
    fun offerWhileWaiting(
        direction: PageAnimationController.Direction,
        gestureStartY: Float? = null,
        input: CurlTurnInput = CurlTurnInput.TAP,
        expedited: Boolean = false,
        pageGeneration: Long = 0L
    ) {
        offer(direction, gestureStartY, input, expedited, pageGeneration)
        state = State.WAITING_FOR_TARGET
    }

    fun poll(): PageAnimationController.Direction {
        return pollTurn().direction
    }

    fun pollTurn(): QueuedCurlTurn {
        if (state == State.DESTROYED || pendingTurns.isEmpty()) {
            return QueuedCurlTurn(PageAnimationController.Direction.NONE)
        }
        return pendingTurns.removeFirst()
    }

    fun restore(direction: PageAnimationController.Direction) {
        restore(QueuedCurlTurn(direction))
    }

    fun restore(turn: QueuedCurlTurn) {
        if (state == State.DESTROYED || turn.direction == PageAnimationController.Direction.NONE) return
        pendingTurns.addFirst(turn)
        state = State.WAITING_FOR_TARGET
    }

    fun cancelNewest(direction: PageAnimationController.Direction): Boolean {
        if (state == State.DESTROYED || pendingTurns.lastOrNull()?.direction != direction) {
            return false
        }
        pendingTurns.removeLast()
        return true
    }

    fun dragging() = moveTo(State.DRAGGING)

    fun settling() = moveTo(State.SETTLING)

    fun waitingForTarget() = moveTo(State.WAITING_FOR_TARGET)

    fun idle() = moveTo(State.IDLE)

    fun clear() {
        if (state == State.DESTROYED) return
        pendingTurns.clear()
        state = State.IDLE
    }

    fun destroy() {
        pendingTurns.clear()
        state = State.DESTROYED
    }

    private fun moveTo(next: State) {
        if (state != State.DESTROYED) state = next
    }
}
