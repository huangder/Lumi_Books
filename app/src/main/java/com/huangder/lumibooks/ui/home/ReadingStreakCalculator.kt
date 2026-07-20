package com.huangder.lumibooks.ui.home

import java.time.LocalDate

internal object ReadingStreakCalculator {
    fun calculate(
        dailyDurations: Map<String, Long>,
        today: LocalDate,
        goalDurationMs: Long
    ): Int {
        fun isGoalMet(date: LocalDate): Boolean {
            val duration = dailyDurations[date.toString()] ?: 0L
            return if (goalDurationMs > 0) duration >= goalDurationMs else duration > 0L
        }

        var date = today
        if (!isGoalMet(date)) {
            // An unfinished current day does not end the streak earned through yesterday.
            date = date.minusDays(1)
        }

        var streakDays = 0
        while (isGoalMet(date)) {
            streakDays++
            date = date.minusDays(1)
        }
        return streakDays
    }
}
