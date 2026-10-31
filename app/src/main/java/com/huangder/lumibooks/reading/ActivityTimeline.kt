package com.huangder.lumibooks.reading

import java.time.Instant
import java.time.ZoneId

internal data class ActivityIncrement(
    val bookId: String,
    val date: String,
    val readingDuration: Long,
    val listeningDuration: Long,
    val startTime: Long,
    val endTime: Long
)

/** Called on the main thread. Monotonic time measures activity; wall time only assigns dates. */
internal class ActivityTimeline(
    private val monotonicMs: () -> Long,
    private val wallMs: () -> Long,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private var readingBook: String? = null
    private var listeningBook: String? = null
    private var lastTick = monotonicMs()
    private var lastWall = wallMs()

    fun setReadingBook(bookId: String?): List<ActivityIncrement> = checkpoint().also {
        readingBook = bookId?.takeIf { it.isNotBlank() }
    }

    fun setListeningBook(bookId: String?): List<ActivityIncrement> = checkpoint().also {
        listeningBook = bookId?.takeIf { it.isNotBlank() }
    }

    fun checkpoint(): List<ActivityIncrement> {
        val now = monotonicMs()
        val elapsed = (now - lastTick).coerceAtLeast(0)
        val start = lastWall
        lastTick = now
        lastWall = wallMs()
        val bookId = listeningBook ?: readingBook ?: return emptyList()
        if (elapsed == 0L) return emptyList()
        val result = mutableListOf<ActivityIncrement>()
        var cursor = start
        val end = start + elapsed
        val currentZone = zone()
        while (cursor < end) {
            val day = Instant.ofEpochMilli(cursor).atZone(currentZone).toLocalDate()
            val boundary = day.plusDays(1).atStartOfDay(currentZone).toInstant().toEpochMilli()
            val until = minOf(end, boundary)
            val duration = until - cursor
            result += ActivityIncrement(bookId, day.toString(),
                if (listeningBook == null) duration else 0,
                if (listeningBook != null) duration else 0, cursor, until)
            cursor = until
        }
        return result
    }
}
