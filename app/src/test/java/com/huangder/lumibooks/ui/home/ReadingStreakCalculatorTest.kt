package com.huangder.lumibooks.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReadingStreakCalculatorTest {
    private val goalDurationMs = 30 * 60 * 1000L

    @Test
    fun keepsStreakAcrossCalendarWeekWhenTodayIsIncomplete() {
        val dailyDurations = mapOf(
            "2026-07-16" to goalDurationMs,
            "2026-07-17" to goalDurationMs,
            "2026-07-18" to goalDurationMs
        )

        val streak = ReadingStreakCalculator.calculate(
            dailyDurations = dailyDurations,
            today = LocalDate.of(2026, 7, 19),
            goalDurationMs = goalDurationMs
        )

        assertEquals(3, streak)
    }

    @Test
    fun includesTodayWhenTodayMeetsGoal() {
        val dailyDurations = mapOf(
            "2026-07-16" to goalDurationMs,
            "2026-07-17" to goalDurationMs,
            "2026-07-18" to goalDurationMs,
            "2026-07-19" to goalDurationMs
        )

        val streak = ReadingStreakCalculator.calculate(
            dailyDurations = dailyDurations,
            today = LocalDate.of(2026, 7, 19),
            goalDurationMs = goalDurationMs
        )

        assertEquals(4, streak)
    }

    @Test
    fun stopsAtFirstMissedHistoricalDay() {
        val dailyDurations = mapOf(
            "2026-07-15" to goalDurationMs,
            "2026-07-17" to goalDurationMs,
            "2026-07-18" to goalDurationMs
        )

        val streak = ReadingStreakCalculator.calculate(
            dailyDurations = dailyDurations,
            today = LocalDate.of(2026, 7, 19),
            goalDurationMs = goalDurationMs
        )

        assertEquals(2, streak)
    }
}
