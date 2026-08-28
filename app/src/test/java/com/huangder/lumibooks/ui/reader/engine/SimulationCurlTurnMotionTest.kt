package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCurlTurnMotionTest {
    @Test
    fun previousStartsFullyOffscreenAndCompletesAtRight() {
        assertEquals(
            -1080f,
            SimulationCurlTurnMotion.flatTouchX(
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            ),
            0f
        )
        assertEquals(
            1080f,
            SimulationCurlTurnMotion.completionTouchX(
                1080f,
                2340f,
                SimulationCurlTurnDirection.PREVIOUS,
                extendedNextTerminal = true
            ),
            0f
        )
    }

    @Test
    fun previousFallbackKeepsCurrentUntilTerminalFrame() {
        assertFalse(
            SimulationCurlTurnMotion.fallbackShowsTurningPage(
                0f,
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            )
        )
        assertFalse(
            SimulationCurlTurnMotion.fallbackShowsTurningPage(
                1079f,
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            )
        )
        assertTrue(
            SimulationCurlTurnMotion.fallbackShowsTurningPage(
                1080f,
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            )
        )
    }

    @Test
    fun completionComparisonIsDirectionAware() {
        assertTrue(
            SimulationCurlTurnMotion.hasReachedCompletion(
                -1080f,
                -1080f,
                SimulationCurlTurnDirection.NEXT
            )
        )
        assertFalse(
            SimulationCurlTurnMotion.hasReachedCompletion(
                0f,
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            )
        )
        assertTrue(
            SimulationCurlTurnMotion.hasReachedCompletion(
                1080f,
                1080f,
                SimulationCurlTurnDirection.PREVIOUS
            )
        )
    }

    @Test
    fun nextRetainsItsExtendedTerminalDistance() {
        val terminal = SimulationCurlTurnMotion.completionTouchX(
            1080f,
            2340f,
            SimulationCurlTurnDirection.NEXT,
            extendedNextTerminal = true
        )
        assertTrue(terminal < -1080f)
        assertTrue(terminal.isFinite())
    }
}
