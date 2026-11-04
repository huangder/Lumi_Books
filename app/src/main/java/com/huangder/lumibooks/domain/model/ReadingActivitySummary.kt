package com.huangder.lumibooks.domain.model

data class ReadingDayActivity(val date: String, val readingDuration: Long, val listeningDuration: Long) {
    val totalDuration: Long get() = readingDuration + listeningDuration
}

data class ReadingBookActivity(
    val bookId: String, val firstDate: String, val lastDate: String,
    val readingDuration: Long, val listeningDuration: Long, val activeDays: Int
) {
    val totalDuration: Long get() = readingDuration + listeningDuration
}

fun aggregateReadingDays(records: List<ReadingRecord>): List<ReadingDayActivity> = records
    .groupBy { it.date }.map { (date, rows) ->
        ReadingDayActivity(date, rows.sumOf { it.duration }, rows.sumOf { it.listeningDuration })
    }.filter { it.totalDuration > 0 }.sortedBy { it.date }

fun aggregateReadingBooks(records: List<ReadingRecord>): List<ReadingBookActivity> = records
    .filter { it.duration + it.listeningDuration > 0 }.groupBy { it.bookId }.map { (id, rows) ->
        ReadingBookActivity(id, rows.minOf { it.date }, rows.maxOf { it.date },
            rows.sumOf { it.duration }, rows.sumOf { it.listeningDuration }, rows.map { it.date }.distinct().size)
    }.sortedWith(compareByDescending<ReadingBookActivity> { it.totalDuration }.thenBy { it.bookId })
