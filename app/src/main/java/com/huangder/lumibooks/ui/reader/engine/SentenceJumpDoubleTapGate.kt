package com.huangder.lumibooks.ui.reader.engine

/**
 * Decides whether a short tap starts a double tap (sentence jump) or stands alone.
 *
 * The reader must delay a standalone tap by the double-tap timeout while listening, otherwise the
 * first tap of a double tap would already toggle the menu or turn a page. Keeping the decision in a
 * plain class makes the timing rules testable without touching the platform gesture pipeline.
 */
internal class SentenceJumpDoubleTapGate(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val slopPx: Float = DEFAULT_SLOP_PX
) {
    enum class TapDecision {
        SINGLE,
        DOUBLE
    }

    private var lastTapTimeMs: Long? = null
    private var lastTapX = 0f
    private var lastTapY = 0f

    /** Milliseconds a standalone tap must be delayed so a possible second tap can be observed. */
    val timeout: Long get() = timeoutMs

    fun classify(nowMs: Long, x: Float, y: Float): TapDecision {
        val previousTime = lastTapTimeMs
        val withinTimeout = previousTime != null && nowMs - previousTime <= timeoutMs
        val withinSlop = withinTimeout &&
            kotlin.math.abs(x - lastTapX) <= slopPx &&
            kotlin.math.abs(y - lastTapY) <= slopPx
        if (withinSlop) {
            reset()
            return TapDecision.DOUBLE
        }
        lastTapTimeMs = nowMs
        lastTapX = x
        lastTapY = y
        return TapDecision.SINGLE
    }

    fun reset() {
        lastTapTimeMs = null
        lastTapX = 0f
        lastTapY = 0f
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 300L
        const val DEFAULT_SLOP_PX = 100f
    }
}
