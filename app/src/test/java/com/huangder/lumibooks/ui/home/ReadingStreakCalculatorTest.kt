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

    @Test
    fun noGoalModeCountsAnyReadingAsCompleted() {
        val dailyDurations = mapOf(
            "2026-07-17" to 1L,
            "2026-07-18" to 5 * 60 * 1000L,
            "2026-07-19" to 1L
        )

        val streak = ReadingStreakCalculator.calculate(
            dailyDurations = dailyDurations,
            today = LocalDate.of(2026, 7, 19),
            goalDurationMs = 0L
        )

        assertEquals(3, streak)
    }

    @Test
    fun noGoalModeKeepsPreviousStreakWhenTodayHasNoReadingYet() {
        val dailyDurations = mapOf(
            "2026-07-17" to 1L,
            "2026-07-18" to 1L
        )

        val streak = ReadingStreakCalculator.calculate(
            dailyDurations = dailyDurations,
            today = LocalDate.of(2026, 7, 19),
            goalDurationMs = 0L
        )

        assertEquals(2, streak)
    }

    @Test
    fun longestFindsBestHistoricalRunAndSkipsDaysBelowGoal() {
        val dailyDurations = mapOf(
            "2026-07-01" to goalDurationMs,
            "2026-07-02" to goalDurationMs,
            "2026-07-10" to goalDurationMs,
            "2026-07-11" to goalDurationMs,
            "2026-07-12" to goalDurationMs,
            "2026-07-13" to goalDurationMs - 1,
            "2026-07-18" to goalDurationMs
        )

        assertEquals(3, ReadingStreakCalculator.longest(dailyDurations, goalDurationMs))
    }

    @Test
    fun longestWithoutGoalCountsAnyReadingAndIgnoresBadKeys() {
        val dailyDurations = mapOf(
            "2026-02-28" to 1L,
            "2026-03-01" to 1L,
            "2026-03-02" to 0L,
            "not-a-date" to 1L
        )

        assertEquals(2, ReadingStreakCalculator.longest(dailyDurations, goalDurationMs = 0L))
        assertEquals(0, ReadingStreakCalculator.longest(emptyMap(), goalDurationMs))
    }
}
