package com.huangder.lumibooks.pdfconversion

import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.huangder.lumibooks.domain.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfConversionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository
) {
    private val workManager = WorkManager.getInstance(context)

    fun observe(sourceBookId: String): Flow<PdfConversionState> {
        return workManager
            .getWorkInfosForUniqueWorkFlow(PdfConversionContract.uniqueWorkName(sourceBookId))
            .map { workInfos -> workInfos.firstOrNull().toConversionState() }
    }

    fun enqueue(sourceBookId: String, replaceExisting: Boolean) {
        val request = OneTimeWorkRequestBuilder<PdfConversionWorker>()
            .setInputData(
                workDataOf(
                    PdfConversionContract.KEY_SOURCE_BOOK_ID to sourceBookId,
                    PdfConversionContract.KEY_REPLACE_EXISTING to replaceExisting
                )
            )
            .addTag(PdfConversionContract.uniqueWorkName(sourceBookId))
            .build()

        workManager.enqueueUniqueWork(
            PdfConversionContract.uniqueWorkName(sourceBookId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(sourceBookId: String) {
        workManager.cancelUniqueWork(PdfConversionContract.uniqueWorkName(sourceBookId))
    }

    fun dismissResultNotification(sourceBookId: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(PdfConversionContract.resultNotificationId(sourceBookId))
    }

    suspend fun findConvertedBookId(sourceBookId: String): String? {
        val convertedId = PdfConversionContract.convertedBookId(sourceBookId)
        return bookRepository.getBookById(convertedId)?.id
    }

    private fun WorkInfo?.toConversionState(): PdfConversionState {
        if (this == null) return PdfConversionState.Idle
        return when (state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING -> PdfConversionState.Running(
                currentPage = progress.getInt(PdfConversionContract.KEY_CURRENT_PAGE, 0),
                totalPages = progress.getInt(PdfConversionContract.KEY_TOTAL_PAGES, 0),
                progress = progress.getInt(PdfConversionContract.KEY_PROGRESS, 0)
            )
            WorkInfo.State.SUCCEEDED -> PdfConversionState.Succeeded(
                bookId = outputData.getString(PdfConversionContract.KEY_CONVERTED_BOOK_ID).orEmpty(),
                textPages = outputData.getInt(PdfConversionContract.KEY_TEXT_PAGES, 0),
                totalPages = outputData.getInt(PdfConversionContract.KEY_TOTAL_PAGES, 0)
            )
            WorkInfo.State.FAILED -> PdfConversionState.Failed(
                outputData.getString(PdfConversionContract.KEY_ERROR_CODE)
                    ?: PdfConversionContract.ERROR_UNKNOWN
            )
            WorkInfo.State.CANCELLED -> PdfConversionState.Cancelled
        }
    }
}
