package com.huangder.lumibooks.ui.reader

/** Shares the WebView renderer with foreground pagination instead of flooding it with neighbours. */
internal class EpubPreloadScheduler(
    private val post: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val foregroundReady: () -> Boolean,
    private val prepared: (EpubPageTurnHost.PreloadSlot, EpubPageTarget) -> Boolean,
    private val prepare: (EpubPageTurnHost.PreloadSlot, EpubPageTarget) -> Unit,
    private val onAheadReady: () -> Unit,
    private val onIdle: () -> Unit
) {
    private data class Task(val slot: EpubPageTurnHost.PreloadSlot, val target: EpubPageTarget)
    private var source: EpubPageTarget? = null
    private var plan = emptyList<Task>()
    private var generation = 0
    private var scheduled: Runnable? = null
    private var activeIndex = 0
    private var polls = 0
    private var started = false

    fun update(current: EpubPageTarget, previous: EpubPageTarget?, next: EpubPageTarget?, direction: Int) {
        val ahead = if (direction < 0) EpubPageTurnHost.PreloadSlot.PREVIOUS else EpubPageTurnHost.PreloadSlot.NEXT
        val tasks = listOfNotNull(
            previous?.let { Task(EpubPageTurnHost.PreloadSlot.PREVIOUS, it) },
            next?.let { Task(EpubPageTurnHost.PreloadSlot.NEXT, it) }
        ).sortedBy { it.slot != ahead }
        if (source == current && plan == tasks) {
            if (scheduled == null) onIdle()
            return
        }
        cancel()
        source = current
        plan = tasks
        start(0)
    }

    /** Advance on the actual draw fence, without another timer or an idle animation frame. */
    fun onPrepared() {
        val task = plan.getOrNull(activeIndex) ?: return
        if (!prepared(task.slot, task.target)) return
        scheduled?.let(remove)
        scheduled = null
        complete()
    }

    fun cancel() {
        generation++
        scheduled?.let(remove)
        scheduled = null
        source = null
        plan = emptyList()
        activeIndex = 0
    }

    private fun schedule(delay: Long, action: () -> Unit) {
        val expected = generation
        val work = object : Runnable {
            override fun run() {
                if (expected == generation && scheduled === this) {
                    scheduled = null
                    action()
                }
            }
        }
        scheduled = work
        post(work, delay)
    }

    private fun start(index: Int) {
        activeIndex = index
        polls = 0
        started = false
        val task = plan.getOrNull(index) ?: return onIdle()
        if (prepared(task.slot, task.target)) return complete()
        // The current document is already stable. In short chapters the next
        // chapter is the next page, so it must not pay a background cooldown.
        schedule(if (index == 0) 0L else 50L, ::run)
    }

    private fun complete() {
        val expected = generation
        val index = activeIndex
        if (activeIndex == 0) onAheadReady()
        if (expected == generation && index == activeIndex) start(index + 1)
    }

    private fun run() {
        val task = plan.getOrNull(activeIndex) ?: return
        if (prepared(task.slot, task.target)) return complete()
        if (!foregroundReady()) {
            schedule(50L, ::run)
            return
        }
        if (polls >= 60) {
            start(activeIndex + 1)
            return
        }
        if (!started) {
            val expected = generation
            val index = activeIndex
            started = true
            prepare(task.slot, task.target)
            if (expected != generation || index != activeIndex) return
            if (prepared(task.slot, task.target)) return complete()
        }
        polls++
        schedule(50L, ::run)
    }
}
