package com.huangder.lumibooks.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class WebdavSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val syncManager: WebdavSyncManager,
    private val dataStoreManager: DataStoreManager
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled || config.syncMode != "auto") return Result.success()
        val result = syncManager.fullSync()
        return when {
            result.success -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 4
    }
}

@Singleton
@OptIn(FlowPreview::class)
class WebdavAutoSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val database: AppDatabase,
    private val dataStoreManager: DataStoreManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            enqueue(immediate = true)
            database.invalidationTracker.createFlow(
                "books", "folders", "book_folder_cross_refs", "tags", "book_tag_cross_refs",
                "reading_records", "bookmarks", "notes", "sync_tombstones"
            ).debounce(CHANGE_DEBOUNCE_MS).collectLatest { enqueue(immediate = false) }
        }
        scope.launch {
            dataStoreManager.portablePreferenceChanges
                .debounce(CHANGE_DEBOUNCE_MS)
                .collectLatest { enqueue(immediate = false) }
        }
    }

    fun onAppBackgrounded() {
        scope.launch { enqueue(immediate = true) }
    }

    fun onReaderExited() {
        scope.launch { enqueue(immediate = true) }
    }

    private suspend fun enqueue(immediate: Boolean) {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled || config.syncMode != "auto") return
        val request = OneTimeWorkRequestBuilder<WebdavSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (!immediate) setInitialDelay(CHANGE_DEBOUNCE_MS, TimeUnit.MILLISECONDS) }
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "lumi_webdav_auto_sync"
        private const val CHANGE_DEBOUNCE_MS = 5_000L
    }
}
