package com.huangder.lumibooks.reading.journal

import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import java.time.Instant
import java.time.ZoneId

/** Turns the tracker's 15-second increments into continuous sessions. */
internal object JournalSessionMerger {
    /** A pause up to this long (switching apps briefly, a dimmed screen) continues the session. */
    const val MAX_GAP_MS = 2L * 60 * 1000

    fun extend(
        open: ReadingJournalEventEntity?,
        bookId: String,
        listening: Boolean,
        startTime: Long,
        endTime: Long,
        durationMs: Long,
        deviceId: String,
        newId: () -> String,
        zone: ZoneId = ZoneId.systemDefault()
    ): ReadingJournalEventEntity {
        val continues = open != null &&
            open.type == ReadingJournalEventEntity.TYPE_SESSION &&
            open.bookId == bookId &&
            open.listening == listening &&
            startTime - open.endedAt in -1_000L..MAX_GAP_MS &&
            sameDay(open.startedAt, startTime, zone)
        return if (continues) {
            requireNotNull(open).copy(
                endedAt = maxOf(open.endedAt, endTime),
                durationMs = open.durationMs + durationMs,
                updatedAt = maxOf(open.updatedAt, endTime)
            )
        } else {
            ReadingJournalEventEntity(
                id = newId(),
                type = ReadingJournalEventEntity.TYPE_SESSION,
                bookId = bookId,
                startedAt = startTime,
                endedAt = endTime,
                durationMs = durationMs,
                listening = listening,
                sourceDeviceId = deviceId,
                updatedAt = endTime
            )
        }
    }

    private fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}
