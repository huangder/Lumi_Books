package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class AutoReadingGestureGateTest {
    @Test fun waitsForTouchAndFlingThenResumes() {
        val gate = AutoReadingGestureGate()
        assertTrue(gate.canAdvance(0, false))
        assertFalse(gate.canAdvance(100, true))
        assertFalse(gate.canAdvance(1000, true))
        assertFalse(gate.canAdvance(1699, false))
        assertTrue(gate.canAdvance(1700, false))
    }

    @Test fun anotherTouchRestartsTheGracePeriod() {
        val gate = AutoReadingGestureGate()
        gate.onInteraction(100)
        assertFalse(gate.canAdvance(799, false))
        gate.onInteraction(750)
        assertFalse(gate.canAdvance(800, false))
        assertTrue(gate.canAdvance(1450, false))
    }
}
