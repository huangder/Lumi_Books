package com.huangder.lumibooks.highlight

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HighlightRuleScanState {
    data object Idle : HighlightRuleScanState
    data class Running(val current: Int, val total: Int) : HighlightRuleScanState
    data class Succeeded(val noteCount: Int) : HighlightRuleScanState
    data class Failed(val message: String) : HighlightRuleScanState
    data object Cancelled : HighlightRuleScanState
}

object HighlightRuleScanContract {
    const val BOOK_ID = "book_id"
    const val CURRENT = "current"
    const val TOTAL = "total"
    const val NOTE_COUNT = "note_count"
    const val ERROR = "error"
    fun workName(bookId: String) = "lumi-highlight-rules-$bookId"
}

@HiltWorker
class HighlightRuleScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val materializer: HighlightRuleMaterializer
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(HighlightRuleScanContract.BOOK_ID) ?: return Result.failure()
        return runCatching {
            val count = materializer.scanBook(bookId) { current, total ->
                setProgress(workDataOf(HighlightRuleScanContract.CURRENT to current, HighlightRuleScanContract.TOTAL to total))
            }
            Result.success(workDataOf(HighlightRuleScanContract.NOTE_COUNT to count))
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Result.failure(workDataOf(HighlightRuleScanContract.ERROR to (error.message ?: "规则扫描失败")))
        }
    }
}

@Singleton
class HighlightRuleScanManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(bookId: String) {
        val request = OneTimeWorkRequestBuilder<HighlightRuleScanWorker>()
            .setInputData(workDataOf(HighlightRuleScanContract.BOOK_ID to bookId))
            .addTag(HighlightRuleScanContract.workName(bookId))
            .build()
        workManager.enqueueUniqueWork(
            HighlightRuleScanContract.workName(bookId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(bookId: String) = workManager.cancelUniqueWork(HighlightRuleScanContract.workName(bookId))

    fun observe(bookId: String): Flow<HighlightRuleScanState> = workManager
        .getWorkInfosForUniqueWorkFlow(HighlightRuleScanContract.workName(bookId))
        .map { infos ->
            val info = infos.firstOrNull() ?: return@map HighlightRuleScanState.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                    HighlightRuleScanState.Running(
                        info.progress.getInt(HighlightRuleScanContract.CURRENT, 0),
                        info.progress.getInt(HighlightRuleScanContract.TOTAL, 0)
                    )
                WorkInfo.State.SUCCEEDED -> HighlightRuleScanState.Succeeded(
                    info.outputData.getInt(HighlightRuleScanContract.NOTE_COUNT, 0)
                )
                WorkInfo.State.FAILED -> HighlightRuleScanState.Failed(
                    info.outputData.getString(HighlightRuleScanContract.ERROR) ?: "规则扫描失败"
                )
                WorkInfo.State.CANCELLED -> HighlightRuleScanState.Cancelled
            }
        }
}
