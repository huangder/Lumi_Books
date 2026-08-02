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
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.mineru.MineruApiClient
import com.huangder.lumibooks.mineru.MineruApiException
import com.huangder.lumibooks.mineru.MineruConfig
import com.huangder.lumibooks.mineru.MineruEpubBuilder
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@HiltWorker
class MineruPdfConversionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val bookRepository: BookRepository,
    private val database: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val apiClient: MineruApiClient,
    private val epubBuilder: MineruEpubBuilder,
    private val tokenStore: MineruTokenStore
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val sourceBookId = inputData.getString(PdfConversionContract.KEY_SOURCE_BOOK_ID)
            ?: return failure(PdfConversionContract.ERROR_FILE_MISSING)
        val replaceExisting = inputData.getBoolean(PdfConversionContract.KEY_REPLACE_EXISTING, false)
        val mode = MineruMode.fromKey(inputData.getString(PdfConversionContract.KEY_MINERU_MODE))
        val configuredMode = MineruMode.fromKey(dataStoreManager.mineruMode.first())
        val consentVersion = dataStoreManager.mineruConsentVersion.first()
        val preciseToken = if (mode == MineruMode.PRECISE) tokenStore.read() else null
        if (mode == MineruMode.DISABLED ||
            configuredMode != mode ||
            consentVersion < MineruConfig.CONSENT_VERSION ||
            (mode == MineruMode.PRECISE && preciseToken == null)
        ) {
            return failAndNotify(sourceBookId, PdfConversionContract.ERROR_MINERU_NOT_CONFIGURED)
        }

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

        val outputFile = PdfConversionContract.mineruOutputFile(applicationContext, sourceBookId)
        val temporaryFile = PdfConversionContract.mineruTemporaryFile(applicationContext, sourceBookId)
        val backupFile = PdfConversionContract.mineruBackupFile(applicationContext, sourceBookId)
        val workDirectory = File(applicationContext.cacheDir, "mineru/${safePart(sourceBookId)}")
        temporaryFile.delete()
        workDirectory.deleteRecursively()
        workDirectory.mkdirs()
        if (backupFile.exists()) {
            if (outputFile.exists()) backupFile.delete() else moveReplacing(backupFile, outputFile)
        }

        return try {
            setForeground(createForegroundInfo(sourceBookId, 0))
            val pageCount = readAndValidatePdf(sourceFile, mode)
            updateProgress(sourceBookId, 2, pageCount)

            val result = apiClient.parse(
                source = sourceFile,
                mode = mode,
                token = preciseToken,
                workDirectory = workDirectory
            ) { progress ->
                updateProgress(sourceBookId, progress, pageCount)
            }
            updateProgress(sourceBookId, 94, pageCount)

            epubBuilder.build(
                remoteResult = result,
                outputFile = temporaryFile,
                workDirectory = workDirectory,
                title = sourceBook.title,
                author = sourceBook.author
            )
            updateProgress(sourceBookId, 98, pageCount)

            installOutputFile(temporaryFile, outputFile, backupFile)
            val convertedTitle = applicationContext.getString(
                R.string.pdf_convert_mineru_book_title,
                sourceBook.title
            )
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
                            title = convertedTitle,
                            author = sourceBook.author,
                            filePath = outputFile.absolutePath,
                            coverPath = existingBook?.coverPath ?: sourceBook.coverPath,
                            format = "EPUB",
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
            deletePreviousConvertedFile(existingBook?.filePath, outputFile)
            updateProgress(sourceBookId, 100, pageCount)

            notifyCompleted(sourceBookId, convertedBookId, convertedTitle, pageCount)
            Result.success(
                workDataOf(
                    PdfConversionContract.KEY_CONVERTED_BOOK_ID to convertedBookId,
                    PdfConversionContract.KEY_TEXT_PAGES to pageCount,
                    PdfConversionContract.KEY_TOTAL_PAGES to pageCount
                )
            )
        } catch (error: CancellationException) {
            temporaryFile.delete()
            if (backupFile.exists()) restorePreviousOutput(outputFile, backupFile)
            throw error
        } catch (_: InvalidPasswordException) {
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_ENCRYPTED)
        } catch (_: FileNotFoundException) {
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_FILE_MISSING)
        } catch (error: MineruApiException) {
            failAndNotify(sourceBookId, error.toErrorCode())
        } catch (_: IOException) {
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_STORAGE)
        } catch (_: Throwable) {
            failAndNotify(sourceBookId, PdfConversionContract.ERROR_UNKNOWN)
        } finally {
            temporaryFile.delete()
            workDirectory.deleteRecursively()
        }
    }

    private fun readAndValidatePdf(source: File, mode: MineruMode): Int {
        val maxBytes = if (mode == MineruMode.AGENT) AGENT_MAX_BYTES else PRECISE_MAX_BYTES
        if (source.length() > maxBytes) {
            throw MineruApiException(MineruApiException.Kind.FILE_LIMIT, "PDF exceeds MinerU file limit")
        }
        val pages = PDDocument.load(source).use { it.numberOfPages }
        val maxPages = if (mode == MineruMode.AGENT) AGENT_MAX_PAGES else PRECISE_MAX_PAGES
        if (pages > maxPages) {
            throw MineruApiException(MineruApiException.Kind.PAGE_LIMIT, "PDF exceeds MinerU page limit")
        }
        return pages
    }

    private suspend fun updateProgress(sourceBookId: String, progress: Int, totalPages: Int) {
        val normalized = progress.coerceIn(0, 100)
        setProgress(
            workDataOf(
                PdfConversionContract.KEY_CURRENT_PAGE to 0,
                PdfConversionContract.KEY_TOTAL_PAGES to totalPages,
                PdfConversionContract.KEY_PROGRESS to normalized
            )
        )
        setForeground(createForegroundInfo(sourceBookId, normalized))
    }

    private fun createForegroundInfo(sourceBookId: String, progress: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.pdf_convert_mineru_notification_title))
            .setContentText(applicationContext.getString(R.string.pdf_convert_mineru_notification_progress, progress))
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
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
        pageCount: Int
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
            .setContentText(applicationContext.getString(R.string.pdf_convert_mineru_notification_complete, pageCount))
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

    private fun MineruApiException.toErrorCode(): String = when (kind) {
        MineruApiException.Kind.AUTH -> PdfConversionContract.ERROR_MINERU_AUTH
        MineruApiException.Kind.RATE_LIMIT -> PdfConversionContract.ERROR_MINERU_RATE_LIMIT
        MineruApiException.Kind.FILE_LIMIT -> PdfConversionContract.ERROR_MINERU_FILE_LIMIT
        MineruApiException.Kind.PAGE_LIMIT -> PdfConversionContract.ERROR_MINERU_PAGE_LIMIT
        MineruApiException.Kind.NETWORK -> PdfConversionContract.ERROR_MINERU_NETWORK
        MineruApiException.Kind.UPLOAD -> PdfConversionContract.ERROR_MINERU_UPLOAD
        MineruApiException.Kind.INVALID_RESULT -> PdfConversionContract.ERROR_MINERU_RESULT
        MineruApiException.Kind.SERVICE -> PdfConversionContract.ERROR_MINERU_SERVICE
    }

    private fun errorMessageResource(errorCode: String): Int = when (errorCode) {
        PdfConversionContract.ERROR_ENCRYPTED -> R.string.pdf_convert_error_encrypted
        PdfConversionContract.ERROR_FILE_MISSING -> R.string.pdf_convert_error_file_missing
        PdfConversionContract.ERROR_MINERU_NOT_CONFIGURED -> R.string.pdf_convert_error_mineru_not_configured
        PdfConversionContract.ERROR_MINERU_FILE_LIMIT -> R.string.pdf_convert_error_mineru_file_limit
        PdfConversionContract.ERROR_MINERU_PAGE_LIMIT -> R.string.pdf_convert_error_mineru_page_limit
        PdfConversionContract.ERROR_MINERU_AUTH -> R.string.pdf_convert_error_mineru_auth
        PdfConversionContract.ERROR_MINERU_RATE_LIMIT -> R.string.pdf_convert_error_mineru_rate_limit
        PdfConversionContract.ERROR_MINERU_NETWORK -> R.string.pdf_convert_error_mineru_network
        PdfConversionContract.ERROR_MINERU_UPLOAD -> R.string.pdf_convert_error_mineru_upload
        PdfConversionContract.ERROR_MINERU_SERVICE -> R.string.pdf_convert_error_mineru_service
        PdfConversionContract.ERROR_MINERU_RESULT -> R.string.pdf_convert_error_mineru_result
        PdfConversionContract.ERROR_STORAGE -> R.string.pdf_convert_error_storage
        else -> R.string.pdf_convert_error_unknown
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

    private fun deletePreviousConvertedFile(previousPath: String?, installedFile: File) {
        if (previousPath.isNullOrBlank()) return
        val previous = File(previousPath)
        if (previous.absolutePath != installedFile.absolutePath &&
            PdfConversionContract.isManagedConvertedFile(applicationContext, previous)
        ) {
            previous.delete()
        }
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

    private fun safePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val CHANNEL_ID = "pdf_conversion"
        const val AGENT_MAX_BYTES = 10L * 1_024L * 1_024L
        const val PRECISE_MAX_BYTES = 200L * 1_024L * 1_024L
        const val AGENT_MAX_PAGES = 20
        const val PRECISE_MAX_PAGES = 200
    }
}
