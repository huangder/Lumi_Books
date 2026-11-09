package com.huangder.lumibooks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry of the opt-in reading journal. Sessions keep the exact time span that
 * the daily totals in [ReadingRecordEntity] merge away; milestone rows mark moments
 * such as finishing a book. Highlights and imports are read from their own tables.
 */
@Entity(
    tableName = "reading_journal_events",
    indices = [Index(value = ["startedAt"]), Index(value = ["bookId"])]
)
data class ReadingJournalEventEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val listening: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    val sourceDeviceId: String = "",
    val updatedAt: Long = endedAt
) {
    companion object {
        const val TYPE_SESSION = "session"
        const val TYPE_FINISHED = "finished"
        const val TYPE_WANT_TO_READ = "want_to_read"
    }
}
