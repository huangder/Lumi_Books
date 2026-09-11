package com.huangder.lumibooks.util.parser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.LinkedHashMap

/** Shares expensive semantic TXT scans across reader instances in this app process. */
object TxtIndexCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = mutableMapOf<TxtIndexKey, Deferred<TxtIndexSnapshot>>()
    private val sourceGenerations = mutableMapOf<String, Long>()
    private var generationSequence = 0L
    private val completedIndexes = object : LinkedHashMap<TxtIndexKey, TxtIndexSnapshot>(4, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TxtIndexKey, TxtIndexSnapshot>?
        ): Boolean = size > MAX_COMPLETED_INDEXES
    }

    @Synchronized
    fun getOrBuild(
        key: TxtIndexKey,
        builder: () -> TxtIndexSnapshot
    ): Deferred<TxtIndexSnapshot> {
        completedIndexes[key]?.let { snapshot ->
            return scope.async { snapshot }
        }
        inFlight[key]?.let { return it }
        val generation = sourceGenerations.getOrPut(key.sourceIdentity) { ++generationSequence }
        return scope.async {
            builder().also { snapshot ->
                synchronized(this@TxtIndexCoordinator) {
                    if (sourceGenerations[key.sourceIdentity] == generation) {
                        completedIndexes[key] = snapshot
                    }
                }
            }
        }.also { task ->
            inFlight[key] = task
            task.invokeOnCompletion {
                synchronized(this) {
                    inFlight.remove(key, task)
                }
            }
        }
    }

    @Synchronized
    fun completed(key: TxtIndexKey): TxtIndexSnapshot? = completedIndexes[key]

    @Synchronized
    fun invalidate(sourceIdentity: String) {
        sourceGenerations[sourceIdentity] = ++generationSequence
        completedIndexes.keys.removeAll { it.sourceIdentity == sourceIdentity }
        inFlight.filterKeys { it.sourceIdentity == sourceIdentity }
            .values
            .toList()
            .forEach { it.cancel() }
        inFlight.keys.removeAll { it.sourceIdentity == sourceIdentity }
    }

    @Synchronized
    internal fun clearForTesting() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        completedIndexes.clear()
        sourceGenerations.clear()
        generationSequence++
    }

    private const val MAX_COMPLETED_INDEXES = 4
}
