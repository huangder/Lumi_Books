package com.huangder.lumibooks.domain.model

data class ReadingRecord(
    val id: Long = 0,
    val bookId: String,
    val date: String,
    val duration: Long,
    val startTime: Long,
    val endTime: Long,
    val sourceDeviceId: String = "",
    val updatedAt: Long = endTime,
    val listeningDuration: Long = 0
)

data class DailyTotal(
    val date: String,
    val totalDuration: Long
)
