package com.huangder.lumibooks.pdfconversion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.domain.repository.BookRepository
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@HiltWorker
class PdfConversionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val bookRepository: BookRepository,
    private val database: AppDatabase,
    private val textExtractor: PdfTextExtractor
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val sourceBookId = inputData.getString(PdfConversionContract.KEY_SOURCE_BOOK_ID)
            ?: return failure(PdfConversionContract.ERROR_FILE_MISSING)
        val replaceExisting = inputData.getBoolean(PdfConversionContract.KEY_REPLACE_EXISTING, false)
        val sourceBook = bookRepository.getBookById(sourceBookId)
            ?: return failAndNotify(sourceBookId, PdfConversionContract.ERROR_FILE_MISSING)
        val sourceFile = File(sourceBook.filePath)
        if (!sourceFile.isFile) {
            return failAndNotify(sourceBookId, PdfConversionContract.ERROR_FILE_MISSING)
        }

        val convertedBookId = PdfConversionContract.convertedBookId(sourceBookId)
        val existingBook = bookRepository.getBookById(convertedBookId)
        if (existingBook != null && !replaceExisting) {
            return Result.success(
                workDataOf(
                    PdfConversionContract.KEY_CONVERTED_BOOK_ID to existingBook.id,
                    PdfConversionContract.KEY_TEXT_PAGES to 0,
                    PdfConversionContract.KEY_TOTAL_PAGES to 0
                )
            )
        }

        val temporaryFile = PdfConversionContract.temporaryFile(applicationContext, sourceBookId)
        val outputFile = PdfConversionContract.outputFile(applicationContext, sourceBookId)
        val backupFile = PdfConversionContract.backupFile(applicationContext, sourceBookId)
        temporaryFile.delete()
        if (backupFile.exists()) {
            if (outputFile.exists()) backupFile.delete() else moveReplacing(backupFile, outputFile)
        }

        return try {
            setForeground(createForegroundInfo(sourceBookId, 0, 0, 0))
            var lastNotifiedProgress = -1
            val extraction = textExtractor.extract(sourceFile, temporaryFile) { page, total, progress ->
                setProgress(
                    workDataOf(
                        PdfConversionContract.KEY_CURRENT_PAGE to page,
                        PdfConversionContract.KEY_TOTAL_PAGES to total,
                        PdfConversionContract.KEY_PROGRESS to progress
                    )
                )
                if (progress != lastNotifiedProgress) {
                    setForeground(createForegroundInfo(sourceBookId, page, total, progress))
                    lastNotifiedProgress = progress
                }
            }

            installOutputFile(temporaryFile, outputFile, backupFile)
            try {
                val now = System.currentTimeMillis()
                database.withTransaction {
                    if (existingBook != null) {
                        database.bookmarkDao().deleteAllBookmarksByBookId(convertedBookId)
                        database.noteDao().deleteAllNotesByBookId(convertedBookId)
                    }
                    database.bookDao().insertBook(
                        BookEntity(
                            id = convertedBookId,
                            title = sourceBook.title + "（解析后）",
                            author = sourceBook.author,
                            filePath = outputFile.absolutePath,
                            coverPath = existingBook?.coverPath ?: sourceBook.coverPath,
                            format = "TXT",
                            lastReadTime = now,
                            readingProgress = 0f,
                            createdAt = existingBook?.createdAt ?: now,
                            isFavorite = existingBook?.isFavorite ?: sourceBook.isFavorite
                        )
                    )
                }
            } catch (error: Throwable) {
                restorePreviousOutput(outputFile, backupFile)
                throw error
            }
            backupFile.delete()
            existingBook?.filePath?.let { previousPath ->
                val previousFile = File(previousPath)
                if (previousFile.absolutePath != outputFile.absolutePath &&
                    PdfConversionContract.isManagedConvertedFile(applicationContext, previousFile)
                ) {
                    previousFile.delete()
                }
            }

            notifyCompleted(
                sourceBookId = sourceBookId,
                convertedBookId = convertedBookId,
                bookTitle = sourceBook.title + "（解析后）",
                textPages = extraction.textPageCount,
                totalPages = extraction.totalPageCount
            )
            Result.success(
                workDataOf(
                    PdfConversionContract.KEY_CONVERTED_BOOK_ID to convertedBookId,
                    PdfConversionContract.KEY_TEXT_PAGES to extraction.textPageCount,
                    PdfConversionContract.KEY_TOTAL_PAGES to extraction.totalPageCount
                )
            )
        } catch (error: CancellationException) {
            temporaryFile.delete()
            if (backupFile.exists()) restorePreviousOutput(outputFile, backupFile)
            throw error
        } catch (_: NoPdfTextException) {
            temporaryFile.delete()
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_NO_TEXT)
        } catch (_: InvalidPasswordException) {
            temporaryFile.delete()
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_ENCRYPTED)
        } catch (_: FileNotFoundException) {
            temporaryFile.delete()
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_FILE_MISSING)
        } catch (_: IOException) {
            temporaryFile.delete()
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_STORAGE)
        } catch (_: Throwable) {
            temporaryFile.delete()
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_UNKNOWN)
        }
    }

    private fun createForegroundInfo(
        sourceBookId: String,
        currentPage: Int,
        totalPages: Int,
        progress: Int
    ): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.pdf_convert_notification_title))
            .setContentText(
                if (totalPages > 0) {
                    applicationContext.getString(
                        R.string.pdf_convert_notification_progress,
                        currentPage,
                        totalPages
                    )
                } else {
                    applicationContext.getString(R.string.pdf_convert_preparing)
                }
            )
            .setProgress(100, progress.coerceIn(0, 100), totalPages <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                applicationContext.getString(R.string.pdf_convert_cancel_action),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()
        val notificationId = PdfConversionContract.progressNotificationId(sourceBookId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notifyCompleted(
        sourceBookId: String,
        convertedBookId: String,
        bookTitle: String,
        textPages: Int,
        totalPages: Int
    ) {
        createNotificationChannel()
        val openBookIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, convertedBookId)
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            PdfConversionContract.resultNotificationId(sourceBookId),
            openBookIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(bookTitle)
            .setContentText(
                applicationContext.getString(
                    R.string.pdf_convert_notification_complete,
                    textPages,
                    totalPages
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(PdfConversionContract.resultNotificationId(sourceBookId), notification)
    }

    private fun failAndNotify(sourceBookId: String, errorCode: String): Result {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.pdf_convert_failed_title))
            .setContentText(applicationContext.getString(errorMessageResource(errorCode)))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(PdfConversionContract.resultNotificationId(sourceBookId), notification)
        return failure(errorCode)
    }

    private fun failure(errorCode: String): Result {
        return Result.failure(workDataOf(PdfConversionContract.KEY_ERROR_CODE to errorCode))
    }

    private fun errorMessageResource(errorCode: String): Int {
        return when (errorCode) {
            PdfConversionContract.ERROR_NO_TEXT -> R.string.pdf_convert_error_no_text
            PdfConversionContract.ERROR_ENCRYPTED -> R.string.pdf_convert_error_encrypted
            PdfConversionContract.ERROR_FILE_MISSING -> R.string.pdf_convert_error_file_missing
            PdfConversionContract.ERROR_STORAGE -> R.string.pdf_convert_error_storage
            else -> R.string.pdf_convert_error_unknown
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.pdf_convert_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.pdf_convert_notification_channel_description)
            setSound(null, null)
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun installOutputFile(temporaryFile: File, outputFile: File, backupFile: File) {
        if (outputFile.exists()) moveReplacing(outputFile, backupFile)
        try {
            moveReplacing(temporaryFile, outputFile)
        } catch (error: Throwable) {
            if (backupFile.exists()) moveReplacing(backupFile, outputFile)
            throw error
        }
    }

    private fun restorePreviousOutput(outputFile: File, backupFile: File) {
        outputFile.delete()
        if (backupFile.exists()) moveReplacing(backupFile, outputFile)
    }

    private fun moveReplacing(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val CHANNEL_ID = "pdf_conversion"
    }
}
