package com.huangder.lumibooks.ui.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Session-owned work: cancelling a consumer removes queued work, never interrupts native decode. */
internal class RasterWorkScheduler(
    private val scope: CoroutineScope,
    private val parallelism: Int,
    private val onEvent: (String) -> Unit = {}
) {
    private class Work(
        val key: Any,
        var priority: Int,
        var background: Boolean,
        val large: Boolean,
        val block: suspend () -> Any?
    ) {
        val result = CompletableDeferred<Any?>()
        var consumers = 0
        var running = false
    }

    private val lock = Any()
    private val work = LinkedHashMap<Any, Work>()
    private var closed = false
    private val drained = CompletableDeferred<Unit>()

    suspend fun <T> execute(
        key: Any,
        priority: Int,
        background: Boolean = false,
        large: Boolean = false,
        block: suspend () -> T
    ): T {
        currentCoroutineContext().ensureActive()
        val task = synchronized(lock) {
            if (closed) throw CancellationException("Raster session closed")
            if (work.containsKey(key)) onEvent("dedup key=$key")
            val item = work.getOrPut(key) { Work(key, priority, background, large, block) }
            item.consumers++
            item.priority = minOf(item.priority, priority)
            // A visible request promotes speculative work, including work already in progress.
            item.background = item.background && background
            dispatchLocked()
            item
        }
        try {
            @Suppress("UNCHECKED_CAST")
            return task.result.await() as T
        } finally {
            synchronized(lock) {
                task.consumers--
                if (task.consumers == 0 && !task.running && work[task.key] === task) {
                    work.remove(task.key)
                    onEvent("drop_queued key=${task.key}")
                    task.result.cancel()
                }
                dispatchLocked()
            }
        }
    }

    private fun dispatchLocked() {
        if (closed) return
        while (work.values.count { it.running } < parallelism) {
            val running = work.values.filter { it.running }
            val next = work.values.filter {
                !it.running && (!it.background || running.none { job -> job.background }) &&
                    (!it.large || running.none { job -> job.large })
            }.minByOrNull { it.priority } ?: break
            next.running = true
            scope.launch {
                try {
                    next.result.complete(next.block())
                } catch (error: Throwable) {
                    next.result.completeExceptionally(error)
                } finally {
                    synchronized(lock) {
                        if (next.consumers == 0) onEvent("finished_without_consumer key=${next.key}")
                        work.remove(next.key)
                        if (closed && work.isEmpty()) drained.complete(Unit)
                        dispatchLocked()
                    }
                }
            }
        }
    }

    fun close() = synchronized(lock) {
        closed = true
        val queued = work.values.filter { !it.running }
        queued.forEach { work.remove(it.key); it.result.cancel() }
        if (work.isEmpty()) drained.complete(Unit)
    }

    suspend fun awaitClosed() = drained.await()
}
