package com.huangder.lumibooks.ui.home

import java.time.LocalDate

internal object ReadingStreakCalculator {
    fun calculate(
        dailyDurations: Map<String, Long>,
        today: LocalDate,
        goalDurationMs: Long
    ): Int {
        fun isGoalMet(date: LocalDate): Boolean =
            isGoalMet(dailyDurations[date.toString()] ?: 0L, goalDurationMs)

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

    /** Longest run of consecutive qualifying days anywhere in the history. */
    fun longest(dailyDurations: Map<String, Long>, goalDurationMs: Long): Int {
        val days = dailyDurations
            .filterValues { isGoalMet(it, goalDurationMs) }
            .keys
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .sorted()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        return longest
    }

    private fun isGoalMet(duration: Long, goalDurationMs: Long): Boolean =
        if (goalDurationMs > 0) duration >= goalDurationMs else duration > 0L
}
