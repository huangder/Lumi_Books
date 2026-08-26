package com.huangder.lumibooks.ui.reader.engine

import java.util.IdentityHashMap
import java.util.WeakHashMap

internal class RenderResourceLease<T : Any>(
    val resource: T,
    private var releaseAction: (() -> Unit)?
) : AutoCloseable {
    override fun close() {
        releaseAction?.invoke()
        releaseAction = null
    }
}

/** Keeps retired render resources alive until the last animation releases them. */
internal class RenderResourcePool<T : Any>(
    private val dispose: (T) -> Unit
) {
    private class Entry(var leases: Int = 0, var retired: Boolean = false)

    private val entries = IdentityHashMap<T, Entry>()
    private val disposed = WeakHashMap<T, Boolean>()
    private var destroyed = false

    fun track(resource: T): T {
        check(!destroyed) { "Registry is destroyed" }
        entries.getOrPut(resource) { Entry() }
        return resource
    }

    fun acquire(resource: T?): RenderResourceLease<T>? {
        if (resource == null || destroyed) return null
        val entry = entries[resource] ?: return null
        if (entry.retired) return null
        entry.leases++
        return RenderResourceLease(resource) { release(resource, entry) }
    }

    fun canReuse(resource: T?): Boolean {
        if (resource == null || destroyed) return false
        val entry = entries[resource] ?: return false
        return !entry.retired && entry.leases == 0
    }

    fun retire(resource: T?) {
        if (resource == null) return
        val entry = entries[resource]
        if (entry == null) {
            disposeOnce(resource)
            return
        }
        entry.retired = true
        disposeIfUnused(resource, entry)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        val snapshot = entries.entries.toList()
        snapshot.forEach { (resource, entry) ->
            entry.retired = true
            disposeIfUnused(resource, entry)
        }
    }

    internal fun activeLeaseCount(resource: T): Int = entries[resource]?.leases ?: 0

    private fun release(resource: T, entry: Entry) {
        if (entry.leases > 0) entry.leases--
        disposeIfUnused(resource, entry)
    }

    private fun disposeIfUnused(resource: T, entry: Entry) {
        if (!entry.retired || entry.leases != 0) return
        if (entries.remove(resource) != null) disposeOnce(resource)
    }

    private fun disposeOnce(resource: T) {
        if (disposed.put(resource, true) == null) dispose(resource)
    }
}
