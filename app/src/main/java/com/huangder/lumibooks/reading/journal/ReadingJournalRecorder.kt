package com.huangder.lumibooks.reading.journal

import android.util.Log
import com.huangder.lumibooks.data.local.dao.BookDao
import com.huangder.lumibooks.data.local.dao.ReadingJournalDao
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the opt-in journal. Nothing is recorded until the user enables it; recording
 * itself does not depend on the edition, so history survives a later unlock.
 */
@Singleton
class ReadingJournalRecorder @Inject constructor(
    private val dao: ReadingJournalDao,
    private val bookDao: BookDao,
    private val settings: ReadingJournalSettings,
    private val identity: SyncIdentityStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val openSessions = mutableMapOf<Boolean, ReadingJournalEventEntity>()
    private val started = AtomicBoolean(false)
    private var deviceId: String? = null

    @Volatile
    private var state = ReadingJournalState()

    init {
        scope.launch { settings.state.collect { state = it } }
    }

    /** Starts milestone detection and the daily retention cleanup. Safe to call repeatedly. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { runCatching { cleanupIfDue() }.onFailure { Log.w(TAG, "Journal cleanup failed", it) } }
        scope.launch {
            val detector = JournalMilestoneDetector()
            settings.state.map { it.enabled to it.enabledAt }.distinctUntilChanged()
                .flatMapLatest { (enabled, enabledAt) ->
                    detector.reset()
                    if (enabled) bookDao.observeVisibleBooks().map { it to enabledAt } else emptyFlow()
                }
                .collect { (books, enabledAt) ->
                    val events = detector.update(books, System.currentTimeMillis(), deviceId(), enabledAt)
                    if (events.isNotEmpty()) runCatching { dao.upsertAll(events) }
                        .onFailure { Log.w(TAG, "Unable to save journal milestone", it) }
                }
        }
    }

    /** Called by the activity tracker after a time increment was committed. */
    fun recordActivity(bookId: String, readingMs: Long, listeningMs: Long, startTime: Long, endTime: Long) {
        if (!state.enabled) return
        scope.launch {
            runCatching {
                mutex.withLock {
                    if (readingMs > 0) appendSession(bookId, false, startTime, endTime, readingMs)
                    if (listeningMs > 0) appendSession(bookId, true, startTime, endTime, listeningMs)
                }
            }.onFailure { Log.w(TAG, "Unable to save journal session", it) }
        }
    }

    private suspend fun appendSession(bookId: String, listening: Boolean, start: Long, end: Long, duration: Long) {
        // Re-read so a cleanup, restore or "delete all" since the last write starts a fresh row.
        val open = openSessions[listening]?.let { dao.getById(it.id) }
        val next = JournalSessionMerger.extend(
            open, bookId, listening, start, end, duration, deviceId(), { UUID.randomUUID().toString() }
        )
        dao.upsert(next)
        openSessions[listening] = next
    }

    suspend fun cleanupNow(now: Long = System.currentTimeMillis()): Int {
        val cutoff = ReadingJournalRetentionPolicy.cutoff(settings.current().retention, now)
        val removed = if (cutoff == null) 0 else dao.deleteEndedBefore(cutoff)
        settings.markCleanup(now)
        return removed
    }

    suspend fun clearAll() = mutex.withLock {
        dao.deleteAll()
        openSessions.clear()
    }

    private suspend fun cleanupIfDue() {
        val now = System.currentTimeMillis()
        if (ReadingJournalRetentionPolicy.cleanupDue(settings.current().lastCleanupAt, now)) cleanupNow(now)
    }

    private suspend fun deviceId(): String = deviceId ?: identity.deviceId().also { deviceId = it }

    private companion object {
        const val TAG = "ReadingJournal"
    }
}
