package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalReaderOpeningComplete = staticCompositionLocalOf { true }

/** Main-thread, per-reader gate for work that must not compete with the opening frames. */
internal class ReaderOpenWorkGate {
    private val pending = linkedMapOf<String, () -> Unit>()
    var released = false
        private set
    private var cancelled = false

    fun schedule(key: String, work: () -> Unit) {
        if (cancelled) return
        if (released) work() else pending[key] = work
    }

    fun complete() {
        if (cancelled || released) return
        released = true
        val work = pending.values.toList()
        pending.clear()
        work.forEach { if (!cancelled) it() }
    }

    fun cancel() {
        cancelled = true
        pending.clear()
    }
}
