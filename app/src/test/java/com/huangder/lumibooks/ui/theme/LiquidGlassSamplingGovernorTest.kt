package com.huangder.lumibooks.ui.theme

import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassSamplingGovernorTest {
    @Test fun isolatedJankDoesNotChangeTheSelectedQuality() {
        val governor = LiquidGlassSamplingGovernor(LiquidGlassSamplingQuality.ORIGINAL)
        feedWindow(governor, 0L, missedFrames = 1)
        feedWindow(governor, 1_001L, missedFrames = 0)
        feedWindow(governor, 2_002L, missedFrames = 0)

        assertEquals(LiquidGlassSamplingQuality.ORIGINAL, governor.quality)
    }

    @Test fun sustainedJankStepsDownOneTierAtATimeAndStopsAtPerformanceFloor() {
        val governor = LiquidGlassSamplingGovernor(LiquidGlassSamplingQuality.ORIGINAL)
        var start = 0L
        repeat(6) {
            feedWindow(governor, start, missedFrames = 10)
            start += 1_001L
        }

        assertEquals(LiquidGlassSamplingQuality.PERFORMANCE, governor.quality)
    }

    @Test fun stableWindowsRestoreQualityTowardTheUserSelectedCeiling() {
        val governor = LiquidGlassSamplingGovernor(LiquidGlassSamplingQuality.ORIGINAL)
        feedWindow(governor, 0L, missedFrames = 10)
        feedWindow(governor, 1_001L, missedFrames = 10)
        assertEquals(LiquidGlassSamplingQuality.CLEAR, governor.quality)

        feedWindow(governor, 2_002L, missedFrames = 0)
        feedWindow(governor, 3_003L, missedFrames = 0)
        feedWindow(governor, 4_004L, missedFrames = 0)

        assertEquals(LiquidGlassSamplingQuality.ORIGINAL, governor.quality)
    }

    @Test fun userSelectedPerformanceQualityIsTheHardFloorAndCeiling() {
        val governor = LiquidGlassSamplingGovernor(LiquidGlassSamplingQuality.PERFORMANCE)
        repeat(5) { feedWindow(governor, it * 1_001L, missedFrames = 20) }

        assertEquals(LiquidGlassSamplingQuality.PERFORMANCE, governor.quality)
    }

    private fun feedWindow(
        governor: LiquidGlassSamplingGovernor,
        startMs: Long,
        missedFrames: Int
    ) {
        repeat(20) { index ->
            val duration = if (index < missedFrames) 24_000_000L else 4_000_000L
            governor.recordFrame(duration, 16_000_000L, startMs + index * 50L)
        }
        governor.recordFrame(4_000_000L, 16_000_000L, startMs + 1_000L)
    }
}
