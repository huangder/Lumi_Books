package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.reader.engine.PageAnimationController.Direction

internal data class EpubTurnPage<T>(
    val view: T,
    val target: EpubPageTarget,
    val generation: Long,
    val revision: Long,
    val requestGeneration: Int
)

/** The sheets belong to one turn, even while preloading rotates other view roles. */
internal class EpubTurnContext<T>(
    val serial: Long,
    val direction: Direction,
    val reverseAxis: Boolean,
    val current: EpubTurnPage<T>,
    val target: EpubTurnPage<T>
) {
    val upper get() = if (direction == Direction.PREV) target else current
    val lower get() = if (direction == Direction.PREV) current else target
    private var finished = false

    fun owns(view: T): Boolean = view === current.view || view === target.view

    fun matches(view: T, generation: Long, revision: Long): Boolean {
        val page = when {
            view === current.view -> current
            view === target.view -> target
            else -> return false
        }
        return page.generation == generation && page.revision == revision
    }

    fun finishOnce(): Boolean {
        if (finished) return false
        finished = true
        return true
    }
}
