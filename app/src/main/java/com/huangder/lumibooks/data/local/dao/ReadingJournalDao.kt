package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import kotlinx.coroutines.flow.Flow

private const val VISIBLE_BOOK = "bookId NOT IN (SELECT id FROM books WHERE isHidden = 1)"

@Dao
interface ReadingJournalDao {
    /** Events overlapping [startInclusive, endExclusive), hidden books excluded. */
    @Query(
        "SELECT * FROM reading_journal_events WHERE startedAt < :endExclusive AND endedAt >= :startInclusive " +
            "AND $VISIBLE_BOOK ORDER BY startedAt, id"
    )
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<ReadingJournalEventEntity>>

    @Query("SELECT * FROM reading_journal_events WHERE bookId = :bookId ORDER BY startedAt, id")
    fun observeForBook(bookId: String): Flow<List<ReadingJournalEventEntity>>

    @Query("SELECT * FROM reading_journal_events ORDER BY startedAt, id")
    suspend fun getAll(): List<ReadingJournalEventEntity>

    @Query("SELECT MIN(startedAt) FROM reading_journal_events WHERE $VISIBLE_BOOK")
    fun observeEarliestStart(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM reading_journal_events")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM reading_journal_events WHERE id = :id")
    suspend fun getById(id: String): ReadingJournalEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ReadingJournalEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<ReadingJournalEventEntity>)

    @Query("DELETE FROM reading_journal_events WHERE endedAt < :cutoff")
    suspend fun deleteEndedBefore(cutoff: Long): Int

    @Query("DELETE FROM reading_journal_events")
    suspend fun deleteAll()

    @Query("DELETE FROM reading_journal_events WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
