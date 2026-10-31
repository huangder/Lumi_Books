package com.huangder.lumibooks.data.local.dao

import com.huangder.lumibooks.domain.model.ActivityDuration
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.domain.model.DailyTotal
import kotlinx.coroutines.flow.Flow

/** Reading time of hidden books stays out of every statistic, daily goal and streak. */
private const val NOT_HIDDEN_BOOK = "bookId NOT IN (SELECT id FROM books WHERE isHidden = 1)"

@Dao
interface ReadingRecordDao {
    @Query("SELECT COALESCE(SUM(duration), 0) AS readingDuration, COALESCE(SUM(listeningDuration), 0) AS listeningDuration FROM reading_records WHERE bookId = :bookId")
    fun getActivityDurationByBookId(bookId: String): Flow<ActivityDuration>

    @Query("SELECT COALESCE(SUM(duration), 0) AS readingDuration, COALESCE(SUM(listeningDuration), 0) AS listeningDuration FROM reading_records WHERE date BETWEEN :startDate AND :endDate AND $NOT_HIDDEN_BOOK")
    fun getActivityDurationBetweenDates(startDate: String, endDate: String): Flow<ActivityDuration>

    @Query("UPDATE reading_records SET duration = duration + :readingDuration, listeningDuration = listeningDuration + :listeningDuration, startTime = MIN(startTime, :startTime), endTime = MAX(endTime, :endTime), updatedAt = MAX(updatedAt, :endTime) WHERE id = :recordId")
    suspend fun appendActivityDuration(recordId: Long, readingDuration: Long, listeningDuration: Long, startTime: Long, endTime: Long)

    @Query("SELECT * FROM reading_records WHERE date = :date AND $NOT_HIDDEN_BOOK")
    fun getRecordsByDate(date: String): Flow<List<ReadingRecordEntity>>

    @Query("SELECT * FROM reading_records WHERE date BETWEEN :startDate AND :endDate AND $NOT_HIDDEN_BOOK")
    fun getRecordsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingRecordEntity>>

    @Query("SELECT SUM(duration + listeningDuration) FROM reading_records WHERE date = :date AND $NOT_HIDDEN_BOOK")
    fun getTotalDurationByDate(date: String): Flow<Long?>

    @Query("SELECT SUM(duration + listeningDuration) FROM reading_records WHERE date BETWEEN :startDate AND :endDate AND $NOT_HIDDEN_BOOK")
    fun getTotalDurationBetweenDates(startDate: String, endDate: String): Flow<Long?>

    /** All-time reading plus listening, the Premium reading unlock's measure. */
    @Query("SELECT COALESCE(SUM(duration + listeningDuration), 0) FROM reading_records WHERE $NOT_HIDDEN_BOOK")
    fun observeTotalActivityDuration(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ReadingRecordEntity)

    @Query(
        "SELECT * FROM reading_records WHERE bookId = :bookId AND date = :date " +
            "AND sourceDeviceId = :sourceDeviceId LIMIT 1"
    )
    suspend fun getRecordByBookAndDate(
        bookId: String,
        date: String,
        sourceDeviceId: String
    ): ReadingRecordEntity?

    @Query(
        "UPDATE reading_records SET duration = duration + :additionalDuration, " +
            "endTime = :endTime, updatedAt = :endTime WHERE id = :recordId"
    )
    suspend fun updateRecordDuration(recordId: Long, additionalDuration: Long, endTime: Long)

    @Query(
        "SELECT bookId, SUM(duration + listeningDuration) as totalDuration FROM reading_records " +
            "WHERE date BETWEEN :startDate AND :endDate AND $NOT_HIDDEN_BOOK GROUP BY bookId " +
            "HAVING totalDuration > 0 ORDER BY totalDuration DESC LIMIT :limit"
    )
    fun getMostReadBooksBetween(startDate: String, endDate: String, limit: Int = 5): Flow<List<BookDuration>>

    @Query(
        "SELECT date, SUM(duration + listeningDuration) as totalDuration FROM reading_records " +
            "WHERE date BETWEEN :startDate AND :endDate AND $NOT_HIDDEN_BOOK GROUP BY date"
    )
    fun getDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>>

    @Query("SELECT SUM(duration + listeningDuration) FROM reading_records WHERE bookId = :bookId")
    fun getTotalDurationByBookId(bookId: String): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT date) FROM reading_records WHERE bookId = :bookId AND (duration + listeningDuration) > 0")
    fun getActiveDaysByBookId(bookId: String): Flow<Int>
}

data class BookDuration(
    val bookId: String,
    val totalDuration: Long
)
