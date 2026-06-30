package com.huangder.lumibooks.domain.model

data class ReadingRecord(
    val id: Long = 0,
    val bookId: String,
    val date: String,
    val duration: Long,
    val startTime: Long,
    val endTime: Long
)
