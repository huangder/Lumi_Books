package com.huangder.lumibooks.domain.model

data class ReadingStatistics(
    val todayReadingTime: Long,
    val monthlyReadingTime: Long,
    val dailyGoal: Int,
    val goalProgress: Float
)
