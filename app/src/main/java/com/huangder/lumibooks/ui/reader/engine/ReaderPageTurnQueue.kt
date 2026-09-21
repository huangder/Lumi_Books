package com.huangder.lumibooks.ui.reader.engine

/**
 * Lossless queue for page-turn input received while the next layout slot is loading.
 * Opposite input cancels the newest pending step, matching direct reading gestures.
 */
internal class ReaderPageTurnQueue(
    private val capacity: Int = 16
) {
    private val turns = ArrayDeque<PageAnimationController.Direction>()

    val size: Int get() = turns.size

    fun peek(): PageAnimationController.Direction =
        turns.firstOrNull() ?: PageAnimationController.Direction.NONE

    fun offer(direction: PageAnimationController.Direction) {
        if (direction == PageAnimationController.Direction.NONE) return
        val newest = turns.lastOrNull()
        if (newest != null && newest != direction) {
            turns.removeLast()
            return
        }
        if (turns.size < capacity.coerceAtLeast(1)) turns.addLast(direction)
    }

    fun poll(): PageAnimationController.Direction =
        if (turns.isEmpty()) PageAnimationController.Direction.NONE else turns.removeFirst()

    fun clear() = turns.clear()
}
