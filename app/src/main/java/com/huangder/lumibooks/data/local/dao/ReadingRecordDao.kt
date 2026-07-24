package com.huangder.lumibooks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.domain.model.DailyTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingRecordDao {
    @Query("SELECT * FROM reading_records WHERE date = :date")
    fun getRecordsByDate(date: String): Flow<List<ReadingRecordEntity>>

    @Query("SELECT * FROM reading_records WHERE date BETWEEN :startDate AND :endDate")
    fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecordEntity>>

    @Query("SELECT SUM(duration) FROM reading_records WHERE date = :date")
    fun getTotalDurationByDate(date: String): Flow<Long?>

    @Query("SELECT SUM(duration) FROM reading_records WHERE date BETWEEN :startDate AND :endDate")
    fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ReadingRecordEntity)

    @Query("SELECT * FROM reading_records WHERE bookId = :bookId AND date = :date LIMIT 1")
    suspend fun getRecordByBookAndDate(bookId: String, date: String): ReadingRecordEntity?

    @Query("UPDATE reading_records SET duration = duration + :additionalDuration, endTime = :endTime WHERE id = :recordId")
    suspend fun updateRecordDuration(recordId: Long, additionalDuration: Long, endTime: Long)

    @Query("SELECT bookId, SUM(duration) as totalDuration FROM reading_records GROUP BY bookId ORDER BY totalDuration DESC LIMIT :limit")
    fun getMostReadBooks(limit: Int = 5): Flow<List<BookDuration>>

    @Query("SELECT date, SUM(duration) as totalDuration FROM reading_records WHERE date BETWEEN :startDate AND :endDate GROUP BY date")
    fun getDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>>
}

data class BookDuration(
    val bookId: String,
    val totalDuration: Long
)
