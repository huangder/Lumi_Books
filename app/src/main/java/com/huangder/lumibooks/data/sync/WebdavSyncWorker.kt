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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        return when (webdavWorkerDecision(result, runAttemptCount, MAX_RETRIES)) {
            WebdavWorkerDecision.SUCCESS -> Result.success()
            WebdavWorkerDecision.RETRY -> Result.retry()
            WebdavWorkerDecision.FAILURE -> Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 4
    }
}

@Singleton
class WebdavAutoSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStoreManager: DataStoreManager,
    private val localNetworkAccessManager: LocalNetworkAccessManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { enqueue() }
    }

    fun onAppBackgrounded() {
        scope.launch { enqueue() }
    }

    fun onReaderExited() {
        scope.launch { enqueue() }
    }

    fun onLocalNetworkPermissionGranted() {
        scheduleImmediate()
    }

    fun scheduleImmediate() {
        scope.launch { enqueue() }
    }

    private suspend fun enqueue() {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled || config.syncMode != "auto") return
        if (localNetworkAccessManager.stateFor(config.serverUrl) ==
            LocalNetworkAccessState.PERMISSION_REQUIRED
        ) return
        val request = OneTimeWorkRequestBuilder<WebdavSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "lumi_webdav_auto_sync"
    }
}

internal enum class WebdavWorkerDecision { SUCCESS, RETRY, FAILURE }

internal fun webdavWorkerDecision(
    result: SyncResult,
    runAttemptCount: Int,
    maxRetries: Int
): WebdavWorkerDecision = when {
    result.success || result.failureCategory == WebdavFailureCategory.LOCAL_NETWORK_PERMISSION ->
        WebdavWorkerDecision.SUCCESS
    result.failureCategory in NON_RETRYABLE_FAILURES -> WebdavWorkerDecision.FAILURE
    runAttemptCount < maxRetries -> WebdavWorkerDecision.RETRY
    else -> WebdavWorkerDecision.FAILURE
}

private val NON_RETRYABLE_FAILURES = setOf(
    WebdavFailureCategory.AUTH,
    WebdavFailureCategory.FORBIDDEN,
    WebdavFailureCategory.NOT_FOUND,
    WebdavFailureCategory.CONFLICT,
    WebdavFailureCategory.NOT_SUPPORTED,
    WebdavFailureCategory.TLS,
    WebdavFailureCategory.QUOTA,
    WebdavFailureCategory.INVALID_URL,
    WebdavFailureCategory.REDIRECT,
    WebdavFailureCategory.INVALID_RESPONSE
)
