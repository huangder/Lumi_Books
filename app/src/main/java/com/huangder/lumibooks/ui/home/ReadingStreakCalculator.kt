package com.huangder.lumibooks.ui.home

import java.time.LocalDate

internal object ReadingStreakCalculator {
    fun calculate(
        dailyDurations: Map<String, Long>,
        today: LocalDate,
        goalDurationMs: Long
    ): Int {
        if (goalDurationMs <= 0) return 0

        var date = today
        if ((dailyDurations[date.toString()] ?: 0L) < goalDurationMs) {
            // An unfinished current day does not end the streak earned through yesterday.
            date = date.minusDays(1)
        }

        var streakDays = 0
        while ((dailyDurations[date.toString()] ?: 0L) >= goalDurationMs) {
            streakDays++
            date = date.minusDays(1)
        }
        return streakDays
    }
}
