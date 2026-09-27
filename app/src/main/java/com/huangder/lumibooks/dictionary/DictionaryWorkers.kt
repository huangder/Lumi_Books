package com.huangder.lumibooks.dictionary

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DictionaryInstallWorker @AssistedInject constructor(@Assisted context: Context,
    @Assisted params: WorkerParameters, private val repository: DictionaryRepository) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("dictionaryId") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        return if (repository.install(id, token)) Result.success() else Result.failure()
    }
}

@HiltWorker
class DictionaryReconcileWorker @AssistedInject constructor(@Assisted context: Context,
    @Assisted params: WorkerParameters, private val repository: DictionaryRepository) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (repository.reconcileDownloads()) Result.retry() else Result.success()
}

class DictionaryDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        // Only reconciles our persisted DownloadManager IDs. No paths/URLs are accepted from the broadcast.
        WorkManager.getInstance(context).enqueueUniqueWork("dictionary-reconcile", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DictionaryReconcileWorker>().build())
    }
}
