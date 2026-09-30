package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderOpenWorkGateTest {
    @Test fun `only latest adjacent chapter request runs after handoff`() {
        val gate = ReaderOpenWorkGate()
        val work = mutableListOf<String>()
        gate.schedule("preload") { work += "old chapter" }
        gate.schedule("preload") { work += "current chapter" }
        gate.schedule("metadata") { work += "metadata" }
        assertTrue(work.isEmpty())
        gate.complete()
        gate.complete()
        assertEquals(listOf("current chapter", "metadata"), work)
        gate.schedule("preload") { work += "next chapter" }
        assertEquals("next chapter", work.last())
    }

    @Test fun `leaving the reader drops pending work and late completions`() {
        val first = ReaderOpenWorkGate()
        val second = ReaderOpenWorkGate()
        val work = mutableListOf<String>()
        first.schedule("scan") { work += "old" }
        first.cancel()
        first.complete()
        first.schedule("preload") { work += "late" }
        second.schedule("scan") { work += "new" }
        second.complete()
        assertEquals(listOf("new"), work)
        assertFalse(first.released)
    }
}
