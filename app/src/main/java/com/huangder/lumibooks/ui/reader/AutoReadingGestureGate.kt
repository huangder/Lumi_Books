package com.huangder.lumibooks.ui.reader

/** Wait for touch and inertial scrolling to settle before resuming automatic motion. */
internal class AutoReadingGestureGate {
    private var resumeAfter = 0L

    fun onInteraction(nowMillis: Long) {
        resumeAfter = nowMillis + 700L
    }

    fun canAdvance(nowMillis: Long, interacting: Boolean): Boolean {
        if (interacting) onInteraction(nowMillis)
        return !interacting && nowMillis >= resumeAfter
    }
}
