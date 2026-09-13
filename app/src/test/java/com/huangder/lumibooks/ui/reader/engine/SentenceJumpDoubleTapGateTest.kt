package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceJumpDoubleTapGateTest {
    private val gate = SentenceJumpDoubleTapGate(timeoutMs = 300L, slopPx = 40f)

    @Test
    fun twoNearbyTapsInsideTheTimeoutAreADoubleTap() {
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_000L, x = 100f, y = 200f)
        )
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.DOUBLE,
            gate.classify(nowMs = 1_250L, x = 120f, y = 210f)
        )
    }

    @Test
    fun aTapAfterTheTimeoutStandsAlone() {
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_000L, x = 100f, y = 200f)
        )
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_400L, x = 100f, y = 200f)
        )
    }

    @Test
    fun aDistantTapInsideTheTimeoutIsNotADoubleTap() {
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_000L, x = 100f, y = 200f)
        )
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_100L, x = 300f, y = 200f)
        )
        // The distant tap replaced the anchor, so an immediate repeat is now the first tap again.
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_150L, x = 100f, y = 200f)
        )
    }

    @Test
    fun consumingADoubleTapResetsTheSequence() {
        gate.classify(nowMs = 1_000L, x = 10f, y = 10f)
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.DOUBLE,
            gate.classify(nowMs = 1_100L, x = 12f, y = 12f)
        )
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_150L, x = 12f, y = 12f)
        )
    }

    @Test
    fun resetForgetsThePreviousTap() {
        gate.classify(nowMs = 1_000L, x = 10f, y = 10f)
        gate.reset()
        assertEquals(
            SentenceJumpDoubleTapGate.TapDecision.SINGLE,
            gate.classify(nowMs = 1_100L, x = 10f, y = 10f)
        )
    }
}
