package com.huangder.lumibooks.ui.reader

/** Keep a busy renderer working on the newest settings instead of a drag backlog. */
internal class EpubConfigurationUpdates(
    private val evaluate: (String, (String) -> Unit) -> Unit
) {
    private data class Update(
        val script: String,
        val replaceable: Boolean,
        val callback: (String) -> Unit
    )
    private var document = 0L
    private var busy = false
    private val pending = ArrayDeque<Update>()

    fun reset() {
        document++
        busy = false
        pending.clear()
    }

    fun submit(script: String, replaceable: Boolean, callback: (String) -> Unit) {
        val update = Update(script, replaceable, callback)
        if (busy) {
            // Navigation is a barrier: only replace the last preview when no
            // command depends on the configuration that precedes it.
            if (replaceable && pending.lastOrNull()?.replaceable == true) pending.removeLast()
            pending.addLast(update)
        } else {
            dispatch(update)
        }
    }

    private fun dispatch(update: Update) {
        busy = true
        val targetDocument = document
        evaluate(update.script) { result ->
            if (document == targetDocument) {
                try {
                    update.callback(result)
                } finally {
                    // Completion can itself load another document.
                    if (document == targetDocument) {
                        busy = false
                        val next = pending.removeFirstOrNull()
                        if (next != null) dispatch(next)
                    }
                }
            }
        }
    }
}
