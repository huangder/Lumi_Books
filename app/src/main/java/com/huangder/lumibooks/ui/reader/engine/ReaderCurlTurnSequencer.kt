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
    val pageGeneration: Long = 0L,
    val expiresAtMs: Long = Long.MAX_VALUE,
    val deferredSwipe: DeferredCurlSwipe? = null
)

internal enum class CurlTurnInput { TAP, SWIPE }

/**
 * Ordered pending curl intents shared by the reflowed and WebView readers.
 *
 * Same-direction input is lossless. An opposite input only cancels the newest
 * turn that has not started yet, so changing direction cannot create a delayed
 * back-and-forth animation through pages the user has not seen.
 */
internal class ReaderCurlTurnSequencer(private val boundTapBacklog: Boolean = false) {
    enum class State { IDLE, DRAGGING, SETTLING, WAITING_FOR_TARGET, DESTROYED }

    var state: State = State.IDLE
        private set
    private val pendingTurns = ArrayDeque<QueuedCurlTurn>()
    private var lastTapAtMs: Long? = null

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
        pageGeneration: Long = 0L,
        expiresAtMs: Long = Long.MAX_VALUE,
        nowMs: Long = 0L,
        deferredSwipe: DeferredCurlSwipe? = null
    ) {
        if (state == State.DESTROYED) return
        if (direction == PageAnimationController.Direction.NONE) return
        discardStaleBurstTaps(nowMs)
        val boundTap = boundTapBacklog && input == CurlTurnInput.TAP
        val inTapBurst = boundTap && lastTapAtMs?.let { nowMs - it <= BURST_TAP_MAX_WAIT_MS } == true
        if (boundTap) recordTap(nowMs)
        val newest = pendingTurns.lastOrNull()
        if (newest != null && newest.direction != direction) {
            pendingTurns.removeLast()
            return
        }
        val replacedTap = boundTap &&
            pendingTurns.removeAll { it.input == CurlTurnInput.TAP }
        pendingTurns.addLast(
            QueuedCurlTurn(
                direction = direction,
                gestureStartY = gestureStartY?.takeIf { it.isFinite() },
                input = input,
                expedited = expedited,
                pageGeneration = pageGeneration,
                expiresAtMs = if (replacedTap || inTapBurst) nowMs + BURST_TAP_MAX_WAIT_MS else expiresAtMs,
                deferredSwipe = deferredSwipe
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

    fun peekTurn(): QueuedCurlTurn? = pendingTurns.firstOrNull()

    fun recordTap(nowMs: Long) {
        if (boundTapBacklog) lastTapAtMs = nowMs
    }

    fun discardStaleBurstTaps(nowMs: Long) {
        if (boundTapBacklog) pendingTurns.removeAll { it.input == CurlTurnInput.TAP && it.expiresAtMs <= nowMs }
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
        lastTapAtMs = null
        state = State.IDLE
    }

    fun destroy() {
        pendingTurns.clear()
        state = State.DESTROYED
    }

    private fun moveTo(next: State) {
        if (state != State.DESTROYED) state = next
    }

    private companion object {
        const val BURST_TAP_MAX_WAIT_MS = 350L
    }
}
