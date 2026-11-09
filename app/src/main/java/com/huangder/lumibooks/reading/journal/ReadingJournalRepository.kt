package com.huangder.lumibooks.reading.journal

import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dao.ReadingJournalDao
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Read and settings access for journal screens, shared by both editions. */
@Singleton
class ReadingJournalRepository @Inject constructor(
    private val dao: ReadingJournalDao,
    private val bookDao: BookDao,
    private val recorder: ReadingJournalRecorder,
    val settings: ReadingJournalSettings
) {
    fun observeEvents(startInclusive: Long, endExclusive: Long): Flow<List<ReadingJournalEventEntity>> =
        dao.observeBetween(startInclusive, endExclusive)

    fun observeBookEvents(bookId: String): Flow<List<ReadingJournalEventEntity>> = dao.observeForBook(bookId)

    fun observeEventCount(): Flow<Int> = dao.observeCount()

    fun observeEarliestStart(): Flow<Long?> = dao.observeEarliestStart()

    /** Adds to or removes from the to-read list; the journal picks the change up on its own. */
    suspend fun setWantToRead(bookId: String, wantToRead: Boolean, now: Long = System.currentTimeMillis()) {
        bookDao.setWantToRead(bookId, if (wantToRead) now else 0L, now)
    }

    suspend fun enable(retention: JournalRetention) {
        settings.enable(ReadingJournalSettings.CONSENT_VERSION_CURRENT, retention)
        recorder.start()
    }

    /** Stops recording; existing entries are kept unless [deleteHistory] is set. */
    suspend fun disable(deleteHistory: Boolean) {
        settings.disable()
        if (deleteHistory) recorder.clearAll()
    }

    /**
     * Saves the retention period. Entries are removed right away only when the new period is
     * shorter than the old one; an unchanged or longer period deletes nothing. Returns the count.
     */
    suspend fun setRetention(retention: JournalRetention, now: Long = System.currentTimeMillis()): Int {
        val previous = settings.current().retention
        settings.setRetention(retention)
        return if (ReadingJournalRetentionPolicy.isShorter(retention, previous, now)) recorder.cleanupNow(now) else 0
    }

    suspend fun cleanupNow(): Int = recorder.cleanupNow()
}
