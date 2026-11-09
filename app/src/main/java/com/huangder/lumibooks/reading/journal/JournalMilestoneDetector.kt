package com.huangder.lumibooks.reading.journal

import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Compares consecutive library snapshots and reports books that were just finished
 * or added to the to-read list. Ids are deterministic so the same milestone synced
 * from two devices collapses into one entry.
 */
internal class JournalMilestoneDetector(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private data class Mark(val finished: Boolean, val wantToReadAt: Long)

    private var previous: Map<String, Mark>? = null

    fun reset() { previous = null }

    fun update(books: List<BookEntity>, now: Long, deviceId: String, enabledAt: Long): List<ReadingJournalEventEntity> {
        val current = books.associate { it.id to Mark(it.isFinished(), it.wantToReadAt) }
        val before = previous
        previous = current
        if (before == null) return emptyList()
        val events = mutableListOf<ReadingJournalEventEntity>()
        for ((id, mark) in current) {
            val old = before[id] ?: continue
            if (mark.finished && !old.finished) {
                val day = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
                events += milestone("finished:$id:$day", ReadingJournalEventEntity.TYPE_FINISHED, id, now, deviceId)
            }
            if (mark.wantToReadAt > 0L && mark.wantToReadAt != old.wantToReadAt && mark.wantToReadAt >= enabledAt) {
                events += milestone(
                    "want:$id:${mark.wantToReadAt}", ReadingJournalEventEntity.TYPE_WANT_TO_READ,
                    id, mark.wantToReadAt, deviceId
                )
            }
        }
        return events
    }

    private fun milestone(id: String, type: String, bookId: String, at: Long, deviceId: String) =
        ReadingJournalEventEntity(
            id = id, type = type, bookId = bookId, startedAt = at, endedAt = at,
            sourceDeviceId = deviceId, updatedAt = at
        )

    private fun BookEntity.isFinished() =
        isManuallyMarkedRead || (readingProgress.isFinite() && readingProgress >= 1f)
}
