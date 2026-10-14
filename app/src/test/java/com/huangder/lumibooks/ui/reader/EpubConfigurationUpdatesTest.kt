package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubConfigurationUpdatesTest {
    private val scripts = mutableListOf<String>()
    private val completions = ArrayDeque<(String) -> Unit>()
    private val results = mutableListOf<String>()
    private val updates = EpubConfigurationUpdates { script, callback ->
        scripts += script
        completions.addLast(callback)
    }

    private fun submit(script: String, replaceable: Boolean = true) =
        updates.submit(script, replaceable) { results += script }

    private fun complete() = completions.removeFirst()("ok")

    @Test fun busyRendererSkipsIntermediatePreviewsAndAppliesFinalValue() {
        submit("left=20")
        submit("left=30")
        submit("left=40")
        submit("left=50")
        assertEquals(listOf("left=20"), scripts)
        complete()
        assertEquals(listOf("left=20", "left=50"), scripts)
        complete()
        assertEquals(listOf("left=20", "left=50"), results)
        submit("top=60")
        assertEquals("top=60", scripts.last())
    }

    @Test fun navigationPreservesConfigurationOrderAndIsNeverReplaced() {
        submit("left=20")
        submit("left=30")
        submit("goToPage=4", replaceable = false)
        submit("left=40")
        submit("left=50")
        repeat(4) { complete() }
        assertEquals(listOf("left=20", "left=30", "goToPage=4", "left=50"), scripts)
        assertEquals(scripts, results)
    }

    @Test fun oldDocumentCompletionCannotDispatchOrCompleteNewDocumentUpdates() {
        submit("old running")
        submit("old pending")
        updates.reset()
        submit("new running", replaceable = false)
        submit("new pending")
        complete()
        assertEquals(emptyList<String>(), results)
        assertEquals(listOf("old running", "new running"), scripts)
        complete()
        complete()
        assertEquals(listOf("new running", "new pending"), results)
    }

    @Test fun loadingNewDocumentInCallbackKeepsNewQueueBusy() {
        updates.submit("old", replaceable = false) {
            updates.reset()
            submit("new running")
            submit("new pending")
        }
        complete()
        assertEquals(listOf("old", "new running"), scripts)
        submit("new final")
        complete()
        assertEquals(listOf("old", "new running", "new final"), scripts)
        complete()
        assertEquals(listOf("new running", "new final"), results)
    }
}
