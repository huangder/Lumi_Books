package com.huangder.lumibooks.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.data.local.WebdavTokenStore
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavSyncManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val webdavClient: WebdavClient,
    private val tokenStore: WebdavTokenStore,
    private val dataStoreManager: DataStoreManager,
    private val portableStateSync: WebdavPortableStateSync,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressSyncJob: Job? = null
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private val _downloadStates = MutableStateFlow<Map<String, BookDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, BookDownloadState>> = _downloadStates.asStateFlow()

    // ── Full sync ───────────────────────────────────────────────────

    /**
     * Full bidirectional sync: compares local vs remote manifest,
     * uploads newer local files, downloads newer remote files.
     * Returns a result description string.
     */
    suspend fun fullSync(): SyncResult {
        if (!syncMutex.tryLock()) return SyncResult(context.getString(R.string.webdav_syncing), false)
        _isSyncing.value = true
        return try {
            withContext(Dispatchers.IO) { runFullSync() }
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun runFullSync(): SyncResult {
        cleanupStalePartialDownloads()
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled) return SyncResult(context.getString(R.string.webdav_disabled), false)

        val password = tokenStore.read()
        if (password.isNullOrBlank()) return SyncResult(context.getString(R.string.webdav_password_missing), false)

        val normalized = config.normalized()
        if (!normalized.hasSelectedContent) {
            return SyncResult(context.getString(R.string.webdav_select_content_required), false)
        }
        val serverUrl = normalized.serverUrl
        val username = normalized.username
        val syncPath = normalized.syncPath

        return try {
            webdavClient.ensureDirectory(serverUrl, username, password, syncPath)
            if (normalized.syncBookFiles) {
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/books")
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/covers")
            }
            if (normalized.syncsBookData) {
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/data")
            }

            val remoteManifest = downloadManifestOrNull(serverUrl, username, password, syncPath)
                ?: SyncManifest()
            val libraryKey = remoteLibraryKey(normalized)
            val confirmedBooks = remoteManifest.books.toMutableMap()
            val dataEntries = remoteManifest.data.toMutableMap()
            val deletedBooks = remoteManifest.deletedBooks.toMutableMap()
            val initialLocalBooks = bookRepository.getAllBooks().first()
            val initialLocalIds = initialLocalBooks.mapTo(mutableSetOf()) { it.id }

            for ((bookId, deleted) in deletedBooks) {
                val local = bookRepository.getBookById(bookId)
                if (local != null && local.remoteLibraryKey == libraryKey) {
                    if (local.isCloudOnly) {
                        bookRepository.deleteBook(local)
                        deleteLocalCloudCover(local.coverPath)
                    } else {
                        bookRepository.clearRemoteAssociation(bookId)
                    }
                }
                cleanupDeletedRemoteFiles(deleted, serverUrl, username, password, syncPath)
                confirmedBooks.remove(bookId)
                dataEntries.remove(bookId)
            }

            var cloudBooksDiscovered = 0
            if (normalized.syncBookFiles) {
                for ((bookId, remoteEntry) in confirmedBooks) {
                    if (bookId in deletedBooks) continue
                    val existing = bookRepository.getBookById(bookId)
                    val remoteCoverPath = if (remoteEntry.cover != null &&
                        (existing?.coverPath.isNullOrBlank() ||
                            (remoteEntry.metadata?.updatedAt ?: 0L) >= (existing?.metadataUpdatedAt ?: 0L))
                    ) {
                        downloadCoverThumbnail(
                            bookId = bookId,
                            entry = remoteEntry.cover,
                            serverUrl = serverUrl,
                            username = username,
                            password = password,
                            syncPath = syncPath
                        )
                    } else {
                        existing?.coverPath
                    }
                    val merged = mergeRemoteBook(
                        bookId = bookId,
                        remoteEntry = remoteEntry,
                        existing = existing,
                        coverPath = remoteCoverPath,
                        libraryKey = libraryKey
                    )
                    if (existing == null) {
                        bookRepository.insertBook(merged)
                        cloudBooksDiscovered++
                    } else if (merged != existing) {
                        bookRepository.updateBook(merged)
                    }
                }
            }

            val books = bookRepository.getAllBooks().first()
            var booksUploaded = 0
            var booksAlreadyPresent = 0
            var booksFailed = 0
            val failedBookTitles = mutableListOf<String>()
            var quotaError: WebdavException? = null

            if (normalized.syncBookFiles) {
                for (book in books) {
                    if (book.id in deletedBooks) continue
                    val remoteFileName = remoteBookFileName(book)
                    val localSize = BookFileAccess.size(context, book.filePath)
                    val manifestEntry = confirmedBooks[book.id]
                    val manifestConfirmsFile = manifestEntry != null &&
                        manifestEntry.fileName == remoteFileName &&
                        (localSize <= 0L || manifestEntry.sizeBytes <= 0L || manifestEntry.sizeBytes == localSize)

                    var confirmedEntry = manifestEntry
                    if (book.isCloudOnly) {
                        // A placeholder has no body to upload, but locally edited metadata and
                        // cover changes still need to reach the remote manifest.
                        if (confirmedEntry == null) continue
                        booksAlreadyPresent++
                    } else if (manifestConfirmsFile) {
                        booksAlreadyPresent++
                    } else {
                        val availableBytes = quotaError?.availableBytes
                        if (availableBytes != null && localSize > availableBytes) {
                            booksFailed++
                            failedBookTitles.add(book.title)
                            continue
                        }
                        try {
                            confirmedEntry = uploadBookFile(book, serverUrl, username, password, syncPath)
                            if (confirmedEntry != null) {
                                booksUploaded++
                            } else {
                                booksFailed++
                                failedBookTitles.add(book.title)
                                continue
                            }
                        } catch (error: Exception) {
                            Log.e("WebDAV", "Upload failed book=${book.id} file=${book.filePath}: ${error.message}", error)
                            if (error is WebdavException && error.serverCode == "TrafficRateExhausted") {
                                quotaError = error
                            }
                            booksFailed++
                            failedBookTitles.add(book.title)
                            continue
                        }
                    }

                    val confirmed = requireNotNull(confirmedEntry)
                    val metadataResolution = resolveMetadataForSync(
                        localMetadata = book.toSyncMetadata(),
                        localIsCloudOnly = book.isCloudOnly,
                        generatedCloudTitle = cloudFallbackTitle(book.id, confirmed.fileName),
                        remoteMetadata = manifestEntry?.metadata,
                        legacyCloudFileTitle = cloudFileTitle(confirmed.fileName)
                    )
                    val cover = if (metadataResolution.localWins) {
                        val coverUpload = runCatching {
                            uploadCoverThumbnailIfNeeded(
                                book = book,
                                remoteCover = manifestEntry?.cover,
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                syncPath = syncPath
                            )
                        }.onFailure { error ->
                            Log.w("WebDAV", "Cover upload failed book=${book.id}: ${error.message}")
                        }
                        when {
                            coverUpload.isFailure -> manifestEntry?.cover
                            book.coverPath.isNullOrBlank() -> null
                            else -> coverUpload.getOrNull() ?: manifestEntry?.cover
                        }
                    } else {
                        manifestEntry?.cover
                    }
                    val finalEntry = confirmed.copy(
                        metadata = metadataResolution.metadata,
                        cover = cover
                    )
                    confirmedBooks[book.id] = finalEntry
                    val associatedBook = book.copy(
                        remoteLibraryKey = libraryKey,
                        remoteFileName = finalEntry.fileName,
                        remoteFileSize = finalEntry.sizeBytes,
                        remoteFileSha256 = finalEntry.sha256.ifBlank { null }
                    )
                    if (associatedBook != book) bookRepository.updateBook(associatedBook)
                }
            }

            var dataSynced = 0
            var dataDownloaded = 0
            var dataFailed = 0
            if (normalized.syncsBookData) {
                for (book in bookRepository.getAllBooks().first()) {
                    if (book.id in deletedBooks) continue
                    val remoteData = dataEntries[book.id]
                    val remoteBook = confirmedBooks[book.id]
                    val needsMetadataBackfill = remoteData != null &&
                        book.isCloudOnly &&
                        remoteBook != null &&
                        remoteBook.metadata == null &&
                        isGeneratedCloudTitle(
                            book.title,
                            cloudFallbackTitle(book.id, remoteBook.fileName),
                            cloudFileTitle(remoteBook.fileName)
                        )
                    try {
                        if (remoteData != null &&
                            (book.id !in initialLocalIds ||
                                remoteData.lastModified > book.localReadingDataModifiedAt() ||
                                needsMetadataBackfill)
                        ) {
                            downloadBookData(book.id, serverUrl, username, password, syncPath, normalized)
                            dataDownloaded++
                        } else if (book.id in initialLocalIds &&
                            (remoteData == null ||
                                book.localReadingDataModifiedAt() > remoteData.lastModified)
                        ) {
                            uploadBookData(book.id, serverUrl, username, password, syncPath, normalized)
                            dataEntries[book.id] = buildBookDataManifestEntry(book)
                            dataSynced++
                        }
                    } catch (error: Exception) {
                        dataFailed++
                        Log.e("WebDAV", "Reading data sync failed book=${book.id}: ${error.message}", error)
                    }
                }
            }

            commitManifest(
                manifest = SyncManifest(
                    books = confirmedBooks,
                    data = dataEntries,
                    deletedBooks = deletedBooks
                ),
                serverUrl = serverUrl,
                username = username,
                password = password,
                syncPath = syncPath,
                atomic = false
            )

            val portableResult = if (
                normalized.syncProfileAndSettings ||
                normalized.syncLibraryOrganization ||
                normalized.syncReadingData
            ) portableStateSync.sync(normalized, password) else null

            val now = System.currentTimeMillis()
            dataStoreManager.updateWebdavLastSyncTime(now)
            if (normalized.syncBookFiles) {
                dataStoreManager.saveWebdavSyncedBookIds(confirmedBooks.keys - deletedBooks.keys)
            }
            dataStoreManager.saveWebdavConfig(normalized.copy(lastSyncTime = now))

            val failedNames = failedBookTitles.take(2).joinToString(
                separator = context.getString(R.string.list_separator)
            )
            val summaries = mutableListOf<String>()
            if (normalized.syncBookFiles) {
                val failureHint = if (booksFailed > 0) {
                    context.getString(R.string.webdav_sync_failure_suffix, booksFailed, failedNames)
                } else ""
                summaries += context.getString(
                    R.string.webdav_sync_books_summary,
                    confirmedBooks.size,
                    booksUploaded,
                    booksAlreadyPresent,
                    cloudBooksDiscovered,
                    failureHint
                )
            }
            if (normalized.syncsBookData) {
                val selectedData = buildList {
                    if (normalized.syncReadingRecords) add(context.getString(R.string.webdav_sync_content_reading_records))
                    if (normalized.syncBookmarks) add(context.getString(R.string.webdav_sync_content_bookmarks))
                    if (normalized.syncNotes) add(context.getString(R.string.webdav_sync_content_notes))
                }.joinToString(context.getString(R.string.list_separator))
                val dataFailureHint = if (dataFailed > 0) {
                    context.getString(R.string.webdav_sync_failure_count_suffix, dataFailed)
                } else ""
                summaries += context.getString(
                    R.string.webdav_sync_data_summary,
                    selectedData,
                    dataSynced,
                    dataDownloaded,
                    dataFailureHint
                )
            }
            if (normalized.syncProfileAndSettings) {
                summaries += context.getString(R.string.webdav_sync_category_result,
                    context.getString(R.string.webdav_sync_content_profile_settings))
            }
            if (normalized.syncLibraryOrganization) {
                summaries += context.getString(R.string.webdav_sync_category_result,
                    context.getString(R.string.webdav_sync_content_library))
            }
            if (portableResult != null) {
                summaries += context.getString(
                    R.string.webdav_sync_assets_result,
                    portableResult.uploadedAssets,
                    portableResult.downloadedAssets
                )
            }
            val quotaHint = quotaError?.let { "\n" + userFacingWebdavError(it) }.orEmpty()
            SyncResult(
                message = summaries.joinToString("\n") + quotaHint,
                success = booksFailed == 0 && dataFailed == 0
            )
        } catch (error: WebdavException) {
            logWebdavFailure("FULL_SYNC", normalized.serverUrl, error)
            SyncResult(
                message = context.getString(R.string.webdav_sync_failed_detail, userFacingWebdavError(error)),
                success = false
            )
        } catch (error: Exception) {
            SyncResult(
                message = context.getString(R.string.webdav_sync_failed_detail, error.message.orEmpty()),
                success = false
            )
        }
    }

    // Reading progress sync (debounced)

    /** Debounced sync for a single book's reading data. Call after progress changes. */
    fun scheduleReadingProgressSync(bookId: String) {
        progressSyncJob?.cancel()
        progressSyncJob = scope.launch {
            delay(5_000) // 5 second debounce
            val config = dataStoreManager.webdavConfig.first()
            if (!config.enabled || config.syncMode != "auto" || !config.syncsBookData) return@launch
            val password = tokenStore.read() ?: return@launch
            if (!syncMutex.tryLock()) return@launch
            val n = config.normalized()
            try {
                uploadBookData(bookId, n.serverUrl, n.username, password, n.syncPath, n)
                // Update only the reading-data entry in the remote manifest.
                // Do not rebuild the local book manifest here because that hashes every book file
                // and makes background progress sync feel stuck on large SAF libraries.
                val book = bookRepository.getBookById(bookId)
                val remoteManifest = downloadManifestOrNull(n.serverUrl, n.username, password, n.syncPath)
                if (book != null && remoteManifest != null) {
                    val newManifest = remoteManifest.copy(
                        data = remoteManifest.data + (bookId to buildBookDataManifestEntry(book))
                    )
                    commitManifest(
                        manifest = newManifest,
                        serverUrl = n.serverUrl,
                        username = n.username,
                        password = password,
                        syncPath = n.syncPath,
                        atomic = false
                    )
                }
                portableStateSync.sync(n, password)
            } catch (_: Exception) {
                // Silent fail for background sync
            } finally {
                syncMutex.unlock()
            }
        }
    }

    suspend fun downloadBook(bookId: String): CloudBookDownloadResult {
        if (!syncMutex.tryLock()) {
            return CloudBookDownloadResult(
                null,
                context.getString(R.string.webdav_operation_in_progress),
                false
            )
        }
        return try {
            withContext(Dispatchers.IO) { runBookDownload(bookId) }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun runBookDownload(bookId: String): CloudBookDownloadResult {
        cleanupStalePartialDownloads()
        val book = bookRepository.getBookById(bookId)
            ?: return CloudBookDownloadResult(null, context.getString(R.string.book_not_found), false)
        val localFileSize = runCatching { BookFileAccess.size(context, book.filePath) }
            .getOrDefault(0L)
        // 记录仍标记为仅云端但本地已有完整文件（例如曾被 bodyless 状态快照误翻转过）时，
        // 直接修复记录并视为已下载，避免无谓地重新下载同一文件。
        val repairedBook = if (cloudBookNeedsLocalRepair(book, localFileSize)) {
            bookRepository.markBookDownloaded(bookId, book.filePath)
            bookRepository.getBookById(bookId) ?: book.copy(isCloudOnly = false)
        } else {
            null
        }
        if (!book.isCloudOnly || repairedBook != null) {
            return CloudBookDownloadResult(
                repairedBook ?: book,
                context.getString(R.string.book_already_downloaded),
                true
            )
        }

        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled) return downloadFailure(bookId, context.getString(R.string.webdav_disabled))
        val password = tokenStore.read()
            ?: return downloadFailure(bookId, context.getString(R.string.webdav_password_missing))
        val libraryKey = remoteLibraryKey(config)
        if (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey) {
            return downloadFailure(bookId, context.getString(R.string.webdav_different_library))
        }

        return try {
            val manifest = downloadManifestOrNull(
                config.serverUrl,
                config.username,
                password,
                config.syncPath
            ) ?: return downloadFailure(bookId, context.getString(R.string.webdav_manifest_missing))
            val entry = manifest.books[bookId]
                ?: return downloadFailure(bookId, context.getString(R.string.webdav_book_unavailable))
            if (bookId in manifest.deletedBooks) {
                return downloadFailure(bookId, context.getString(R.string.webdav_book_deleted))
            }

            val extension = entry.fileName.substringAfterLast('.', "book")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
                ?: "book"
            val booksDirectory = FileUtils.getBooksDirectory(context)
            booksDirectory.listFiles()
                ?.filter { it.name.startsWith("$bookId.") && it.name.endsWith(".part") }
                ?.forEach { it.delete() }
            val partialFile = File(booksDirectory, "$bookId.$extension.part")
            val destination = File(booksDirectory, "$bookId.$extension")
            _downloadStates.value = _downloadStates.value + (
                bookId to BookDownloadState.Downloading(0L, entry.sizeBytes)
            )
            val result = webdavClient.downloadToFile(
                url = WebdavUrl.append(config.serverUrl, config.syncPath, "books", entry.fileName),
                destination = partialFile,
                username = config.username,
                password = password,
                expectedSize = entry.sizeBytes
            ) { bytesRead, totalBytes ->
                _downloadStates.value = _downloadStates.value + (
                    bookId to BookDownloadState.Downloading(bytesRead, totalBytes)
                )
            }
            check(entry.sizeBytes <= 0L || result.bytesWritten == entry.sizeBytes) {
                context.getString(R.string.book_download_size_mismatch)
            }
            check(entry.sha256.isBlank() || result.sha256.equals(entry.sha256, ignoreCase = true)) {
                context.getString(R.string.book_download_checksum_mismatch)
            }
            runCatching {
                Files.move(
                    partialFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.recoverCatching {
                Files.move(
                    partialFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrThrow()
            bookRepository.markBookDownloaded(bookId, destination.absolutePath)
            val updated = enrichDownloadedBook(destination, bookId, entry)
                ?: bookRepository.getBookById(bookId)
                ?: error(context.getString(R.string.book_download_save_failed))
            _downloadStates.value = _downloadStates.value + (bookId to BookDownloadState.Completed)
            scope.launch {
                delay(1_500)
                if (_downloadStates.value[bookId] == BookDownloadState.Completed) {
                    _downloadStates.value = _downloadStates.value - bookId
                }
            }
            CloudBookDownloadResult(updated, context.getString(R.string.book_download_completed), true)
        } catch (error: Exception) {
            FileUtils.getBooksDirectory(context).listFiles()
                ?.filter { it.name.startsWith("$bookId.") && it.name.endsWith(".part") }
                ?.forEach { it.delete() }
            val current = bookRepository.getBookById(bookId)
            if (current?.isCloudOnly == true) {
                FileUtils.getBooksDirectory(context).listFiles()
                    ?.filter { it.name.startsWith("$bookId.") && !it.name.endsWith(".part") }
                    ?.forEach { it.delete() }
            }
            downloadFailure(bookId, error.message ?: context.getString(R.string.book_download_failed))
        }
    }

    suspend fun deleteRemoteBooks(
        bookIds: Set<String>,
        remoteFileNames: Map<String, String> = emptyMap(),
        publishPortableState: Boolean = false
    ): SyncResult {
        if (bookIds.isEmpty()) return SyncResult(context.getString(R.string.no_books_selected), true)
        if (!syncMutex.tryLock()) {
            return SyncResult(context.getString(R.string.webdav_operation_in_progress), false)
        }
        return try {
            withContext(Dispatchers.IO) {
                runRemoteDelete(bookIds, remoteFileNames, publishPortableState)
            }
        } catch (error: WebdavException) {
            logWebdavFailure("DELETE_BOOKS", null, error)
            SyncResult(
                context.getString(R.string.webdav_delete_failed, userFacingWebdavError(error)),
                false
            )
        } catch (error: Exception) {
            SyncResult(
                context.getString(R.string.webdav_delete_failed, error.message.orEmpty()),
                false
            )
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun runRemoteDelete(
        bookIds: Set<String>,
        remoteFileNames: Map<String, String>,
        publishPortableState: Boolean
    ): SyncResult {
        cleanupStalePartialDownloads()
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled) return SyncResult(context.getString(R.string.webdav_disabled), false)
        val password = tokenStore.read()
            ?: return SyncResult(context.getString(R.string.webdav_password_missing), false)
        return try {
            val manifest = downloadManifestOrNull(
                config.serverUrl,
                config.username,
                password,
                config.syncPath
            ) ?: SyncManifest()
            val books = manifest.books.toMutableMap()
            val data = manifest.data.toMutableMap()
            val deleted = manifest.deletedBooks.toMutableMap()
            val now = System.currentTimeMillis()
            for (bookId in bookIds) {
                val localBook = bookRepository.getBookById(bookId)
                val remoteBook = books.remove(bookId)
                val remoteData = data.remove(bookId)
                deleted[bookId] = DeletedBookEntry(
                    deletedAt = now,
                    fileName = remoteBook?.fileName
                        ?: localBook?.remoteFileName
                        ?: remoteFileNames[bookId],
                    coverFileName = remoteBook?.cover?.fileName ?: "$bookId.jpg",
                    dataFileName = remoteData?.fileName ?: "$bookId.json"
                )
            }
            val updatedManifest = manifest.copy(
                version = SyncManifest.CURRENT_VERSION,
                books = books,
                data = data,
                deletedBooks = deleted
            )
            commitManifest(
                manifest = updatedManifest,
                serverUrl = config.serverUrl,
                username = config.username,
                password = password,
                syncPath = config.syncPath,
                atomic = true
            )
            bookIds.mapNotNull(deleted::get).forEach { entry ->
                cleanupDeletedRemoteFiles(
                    entry,
                    config.serverUrl,
                    config.username,
                    password,
                    config.syncPath
                )
            }
            dataStoreManager.saveWebdavSyncedBookIds(books.keys)
            if (publishPortableState && (
                    config.syncProfileAndSettings ||
                        config.syncLibraryOrganization ||
                        config.syncReadingData
                )
            ) {
                portableStateSync.sync(config, password)
            }
            SyncResult(context.getString(R.string.webdav_books_deleted, bookIds.size), true)
        } catch (error: WebdavException) {
            logWebdavFailure("DELETE_BOOKS", null, error)
            SyncResult(
                context.getString(R.string.webdav_delete_failed, userFacingWebdavError(error)),
                false
            )
        } catch (error: Exception) {
            SyncResult(
                context.getString(R.string.webdav_delete_failed, error.message.orEmpty()),
                false
            )
        }
    }

    suspend fun detachLibrary(config: WebdavConfig) {
        val normalized = config.normalized()
        if (normalized.serverUrl.isBlank()) return
        syncMutex.withLock {
            val libraryKey = remoteLibraryKey(normalized)
            val books = bookRepository.getAllBooks().first()
            for (book in books) {
                if (book.remoteLibraryKey != libraryKey) continue
                if (book.isCloudOnly) {
                    bookRepository.deleteBook(book)
                    deleteLocalCloudCover(book.coverPath)
                } else {
                    bookRepository.clearRemoteAssociation(book.id)
                }
            }
            dataStoreManager.saveWebdavSyncedBookIds(emptySet())
        }
    }

    // ── Test connection ─────────────────────────────────────────────

    /**
     * Read-only connection test.
     *
     * Probes with `PROPFIND Depth: 0` (no GET, no MKCOL):
     * - `{serverUrl}` — is the configured address a WebDAV collection and are the credentials OK?
     * - `{serverUrl}/{syncPath}` — can the app reach the directory it will actually sync into?
     *
     * Verdict: reachable sync directory → success; missing sync directory on a working address →
     * success with a note (the first sync creates it); anything else reports the failing probe.
     * Nothing is created or modified on the server.
     */
    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncResult {
        return try {
            val rootProbe = webdavClient.probeCollection(serverUrl, username, password)
            val syncDirUrl = WebdavUrl.append(serverUrl, syncPath)
            val dirProbe = webdavClient.probeCollection(syncDirUrl, username, password)
            when {
                // The directory the app actually syncs into is reachable — that is what matters,
                // even if the server refuses to describe the collection root itself.
                isDavProbeSuccess(dirProbe) -> SyncResult(
                    message = context.getString(R.string.webdav_test_success),
                    success = true
                )
                // The sync directory does not exist yet — the first sync creates it with MKCOL.
                dirProbe.statusCode == 404 && isDavProbeSuccess(rootProbe) -> SyncResult(
                    message = context.getString(R.string.webdav_test_success_dir_pending),
                    success = true
                )
                else -> {
                    // A missing sync directory is only conclusive when the root probe also worked;
                    // otherwise the root failure is the real diagnosis (wrong address, no write
                    // permission, authentication, ...).
                    val failedProbe = if (dirProbe.statusCode == 404) rootProbe else dirProbe
                    val failedUrl = if (dirProbe.statusCode == 404) serverUrl else syncDirUrl
                    val error = probeFailure("PROPFIND", failedProbe)
                    logWebdavFailure("TEST_CONNECTION", failedUrl, error)
                    SyncResult(
                        message = context.getString(
                            R.string.webdav_test_failed_detail,
                            userFacingWebdavError(error)
                        ),
                        success = false
                    )
                }
            }
        } catch (error: WebdavException) {
            logWebdavFailure("TEST_CONNECTION", serverUrl, error)
            SyncResult(
                message = context.getString(
                    R.string.webdav_test_failed_detail,
                    userFacingWebdavError(error)
                ),
                success = false
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val wrapped = WebdavException(
                message = "Unexpected WebDAV probe failure",
                kind = WebdavErrorKind.INVALID_RESPONSE,
                cause = error
            )
            logWebdavFailure("TEST_CONNECTION", serverUrl, wrapped)
            SyncResult(
                message = context.getString(
                    R.string.webdav_test_failed_detail,
                    userFacingWebdavError(wrapped)
                ),
                success = false
            )
        }
    }

    private fun isDavProbeSuccess(probe: WebdavProbeResult): Boolean =
        WebdavFailureClassifier.isSuccessStatus(probe.statusCode) && !probe.htmlResponse

    private fun probeFailure(operation: String, probe: WebdavProbeResult): WebdavException {
        val kind = when {
            probe.htmlResponse -> WebdavErrorKind.INVALID_RESPONSE
            else -> WebdavFailureClassifier.kindForStatus(probe.statusCode)
        }
        return WebdavException(
            message = "$operation failed — HTTP ${probe.statusCode}",
            statusCode = probe.statusCode,
            kind = kind,
            initialUrl = probe.initialUrl,
            finalUrl = probe.finalUrl,
            redirected = probe.redirected,
            redirectCount = probe.redirectCount,
            server = probe.server,
            requestId = probe.requestId,
            responseSummary = probe.responseSummary,
            contentType = probe.contentType,
            davHeader = probe.davHeader,
            htmlResponse = probe.htmlResponse
        )
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun userFacingWebdavError(error: WebdavException): String {
        val category = WebdavFailureClassifier.classify(
            kind = error.kind,
            statusCode = error.statusCode,
            serverCode = error.serverCode
        )
        return when (category) {
            WebdavFailureCategory.QUOTA -> {
                val unknown = context.getString(R.string.webdav_unknown_value)
                val remaining = error.availableBytes?.let(::formatBytes) ?: unknown
                val required = error.requiredBytes?.let(::formatBytes) ?: unknown
                context.getString(R.string.webdav_upload_quota_exhausted, remaining, required)
            }
            WebdavFailureCategory.AUTH -> context.getString(R.string.webdav_error_auth)
            WebdavFailureCategory.FORBIDDEN -> context.getString(R.string.webdav_error_forbidden)
            WebdavFailureCategory.NOT_FOUND -> context.getString(R.string.webdav_error_not_found)
            WebdavFailureCategory.CONFLICT -> context.getString(R.string.webdav_error_conflict)
            WebdavFailureCategory.NOT_SUPPORTED -> context.getString(
                R.string.webdav_error_not_supported,
                error.statusCode ?: 0
            )
            WebdavFailureCategory.NETWORK -> context.getString(R.string.webdav_error_network)
            WebdavFailureCategory.TIMEOUT -> context.getString(R.string.webdav_error_timeout)
            WebdavFailureCategory.TLS -> context.getString(R.string.webdav_error_tls)
            WebdavFailureCategory.INVALID_URL -> context.getString(R.string.webdav_error_invalid_url)
            WebdavFailureCategory.REDIRECT -> context.getString(
                R.string.webdav_error_redirect,
                error.finalUrl?.let(WebdavUrl::host).orEmpty().ifBlank { context.getString(R.string.webdav_unknown_value) }
            )
            WebdavFailureCategory.INVALID_RESPONSE -> context.getString(
                R.string.webdav_error_invalid_response,
                error.statusCode?.toString() ?: context.getString(R.string.webdav_unknown_value)
            )
            WebdavFailureCategory.SERVER_ERROR -> context.getString(
                R.string.webdav_request_failed_http,
                error.statusCode ?: 0
            )
            WebdavFailureCategory.UNKNOWN -> error.statusCode?.let {
                context.getString(R.string.webdav_request_failed_http, it)
            } ?: context.getString(R.string.webdav_request_failed)
        }
    }

    /** Records a WebDAV failure for the exportable diagnostic package.
     *  Only the host is kept — never the username, password or full URL. */
    private fun logWebdavFailure(operation: String, url: String?, error: WebdavException) {
        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        Log.e(
            TAG,
            "$operation failed: kind=${error.kind}, statusCode=${error.statusCode}, " +
                "host=${host.orEmpty()}, message=${error.message.orEmpty()}"
        )
        DiagnosticLoggerRegistry.logger?.log(
            category = "sync",
            event = "webdav_request_failed",
            level = DiagnosticLevel.ERROR,
            attributes = mapOf(
                "operation" to operation,
                "kind" to error.kind.name,
                "statusCode" to error.statusCode,
                "host" to host,
                "serverCode" to error.serverCode,
                "server" to error.server,
                "requestId" to error.requestId,
                "finalHost" to error.finalUrl?.let(WebdavUrl::host),
                "redirected" to error.redirected,
                "redirectCount" to error.redirectCount,
                "contentType" to error.contentType,
                "davHeader" to error.davHeader,
                "htmlResponse" to error.htmlResponse,
                "responseBodyPresent" to (
                    !error.serverDetail.isNullOrBlank() ||
                        !error.responseSummary.isNullOrBlank()
                    )
            ),
            throwable = error
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun remoteLibraryKey(config: WebdavConfig): String {
        val normalized = config.normalized()
        return sha256Bytes(
            "${normalized.serverUrl}\n${normalized.username}\n${normalized.syncPath}"
                .toByteArray(Charsets.UTF_8)
        ).take(24)
    }

    private fun Book.toSyncMetadata(): SyncBookMetadata = SyncBookMetadata(
        title = title,
        author = author,
        format = format.name,
        createdAt = createdAt,
        isFavorite = isFavorite,
        updatedAt = metadataUpdatedAt
    )

    private fun mergeRemoteBook(
        bookId: String,
        remoteEntry: SyncFileEntry,
        existing: Book?,
        coverPath: String?,
        libraryKey: String
    ): Book {
        val fallbackTitle = cloudFallbackTitle(bookId, remoteEntry.fileName)
        val legacyFileTitle = cloudFileTitle(remoteEntry.fileName)
        val metadata = remoteEntry.metadata?.takeUnless {
            isGeneratedCloudTitle(it.title, fallbackTitle, legacyFileTitle)
        }
        val existingIsGeneratedPlaceholder = existing?.isCloudOnly == true &&
            isGeneratedCloudTitle(existing.title, fallbackTitle, legacyFileTitle)
        val remoteMetadataWins = metadata != null &&
            (existing == null || existingIsGeneratedPlaceholder ||
                shouldApplyRemoteMetadata(existing.metadataUpdatedAt, metadata))
        val fallbackFormat = bookFormatFromName(remoteEntry.fileName)
        val base = existing ?: Book(
            id = bookId,
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            author = metadata?.author?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.book_author_unknown),
            filePath = "",
            coverPath = coverPath,
            format = metadata?.format?.toBookFormatOrNull() ?: fallbackFormat,
            lastReadTime = metadata?.createdAt ?: remoteEntry.lastModified,
            readingProgress = 0f,
            createdAt = metadata?.createdAt ?: remoteEntry.lastModified,
            isFavorite = metadata?.isFavorite ?: false,
            isCloudOnly = true,
            metadataUpdatedAt = metadata?.updatedAt ?: 0L
        )
        return base.copy(
            title = when {
                remoteMetadataWins -> metadata?.title?.takeIf { it.isNotBlank() } ?: base.title
                existingIsGeneratedPlaceholder -> fallbackTitle
                else -> base.title
            },
            author = if (remoteMetadataWins) metadata?.author?.takeIf { it.isNotBlank() } ?: base.author else base.author,
            coverPath = coverPath ?: base.coverPath,
            format = if (remoteMetadataWins) metadata?.format?.toBookFormatOrNull() ?: base.format else base.format,
            createdAt = if (remoteMetadataWins) metadata?.createdAt ?: base.createdAt else base.createdAt,
            isFavorite = if (remoteMetadataWins) metadata?.isFavorite ?: base.isFavorite else base.isFavorite,
            metadataUpdatedAt = if (remoteMetadataWins) metadata?.updatedAt ?: base.metadataUpdatedAt else base.metadataUpdatedAt,
            remoteLibraryKey = libraryKey,
            remoteFileName = remoteEntry.fileName,
            remoteFileSize = remoteEntry.sizeBytes,
            remoteFileSha256 = remoteEntry.sha256.ifBlank { null }
        )
    }

    private fun cloudFallbackTitle(bookId: String, remoteFileName: String): String =
        context.getString(R.string.webdav_cloud_book_fallback, bookId.take(8))

    private fun cloudFileTitle(remoteFileName: String): String =
        remoteFileName.substringBeforeLast('.', remoteFileName)

    private fun String.toBookFormatOrNull(): BookFormat? =
        runCatching { BookFormat.valueOf(uppercase()) }.getOrNull()

    private fun bookFormatFromName(fileName: String): BookFormat = when (fileName.substringAfterLast('.').lowercase()) {
        "epub" -> BookFormat.EPUB
        "pdf" -> BookFormat.PDF
        "mobi" -> BookFormat.MOBI
        "cbz" -> BookFormat.CBZ
        else -> BookFormat.TXT
    }

    private suspend fun uploadCoverThumbnailIfNeeded(
        book: Book,
        remoteCover: SyncFileEntry?,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncFileEntry? {
        val thumbnail = createCoverThumbnail(book) ?: return null
        val bytes = thumbnail.readBytes()
        val sha256 = sha256Bytes(bytes)
        if (remoteCover != null &&
            remoteCover.sha256.equals(sha256, ignoreCase = true) &&
            remoteCover.sizeBytes == bytes.size.toLong()
        ) {
            return remoteCover
        }
        val fileName = "${book.id}.jpg"
        webdavClient.upload(
            WebdavUrl.append(serverUrl, syncPath, "covers", fileName),
            bytes,
            username,
            password,
            "image/jpeg"
        )
        return SyncFileEntry(
            fileName = fileName,
            sha256 = sha256,
            sizeBytes = bytes.size.toLong(),
            lastModified = book.metadataUpdatedAt
        )
    }

    private fun createCoverThumbnail(book: Book): File? {
        val sourcePath = book.coverPath?.takeIf { it.isNotBlank() } ?: return null
        val source = File(sourcePath)
        if (!source.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 960 || bounds.outHeight / sampleSize > 1280) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        val scale = minOf(1f, 480f / decoded.width, 640f / decoded.height)
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val thumbnail = if (width == decoded.width && height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        }
        val directory = File(context.cacheDir, "webdav_cover_upload").apply { mkdirs() }
        val output = File(directory, "${book.id}.jpg")
        FileOutputStream(output).use { stream ->
            check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                "Unable to encode cover thumbnail"
            }
        }
        thumbnail.recycle()
        return output
    }

    private suspend fun downloadCoverThumbnail(
        bookId: String,
        entry: SyncFileEntry,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): String? {
        return try {
            val hash = entry.sha256.take(12).ifBlank { entry.lastModified.toString() }
            val coversDirectory = FileUtils.getCoversDirectory(context)
            val destination = File(coversDirectory, "cloud_${bookId}_$hash.jpg")
            if (!destination.exists()) {
                val bytes = webdavClient.download(
                    WebdavUrl.append(serverUrl, syncPath, "covers", entry.fileName),
                    username,
                    password
                )
                check(entry.sizeBytes <= 0L || bytes.size.toLong() == entry.sizeBytes)
                check(entry.sha256.isBlank() || sha256Bytes(bytes).equals(entry.sha256, ignoreCase = true))
                destination.writeBytes(bytes)
            }
            coversDirectory.listFiles()
                ?.filter { it.name.startsWith("cloud_${bookId}_") && it != destination }
                ?.forEach { it.delete() }
            destination.absolutePath
        } catch (error: Exception) {
            Log.w("WebDAV", "Cover download failed book=$bookId: ${error.message}")
            null
        }
    }

    private fun deleteLocalCloudCover(coverPath: String?) {
        val file = coverPath?.let(::File) ?: return
        if (file.name.startsWith("cloud_") && file.parentFile == FileUtils.getCoversDirectory(context)) {
            file.delete()
        }
    }

    private fun cleanupStalePartialDownloads() {
        FileUtils.getBooksDirectory(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { partial ->
                if (!partial.delete()) {
                    Log.w("WebDAV", "Unable to remove stale download ${partial.absolutePath}")
                }
            }
    }

    private suspend fun cleanupDeletedRemoteFiles(
        entry: DeletedBookEntry,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ) {
        val targets = buildList {
            entry.fileName?.let { add("books" to it) }
            entry.coverFileName?.let { add("covers" to it) }
            entry.dataFileName?.let { add("data" to it) }
        }
        for ((directory, fileName) in targets) {
            try {
                webdavClient.delete(
                    WebdavUrl.append(serverUrl, syncPath, directory, fileName),
                    username,
                    password
                )
            } catch (error: Exception) {
                Log.w("WebDAV", "Deferred cleanup failed for $directory/$fileName: ${error.message}")
            }
        }
    }

    private fun downloadFailure(bookId: String, message: String): CloudBookDownloadResult {
        _downloadStates.value = _downloadStates.value + (bookId to BookDownloadState.Failed(message))
        return CloudBookDownloadResult(null, message, false)
    }

    private suspend fun buildLocalManifest(): SyncManifest {
        val books = bookRepository.getAllBooks().first()
        val booksMap = mutableMapOf<String, SyncFileEntry>()
        val dataMap = mutableMapOf<String, SyncFileEntry>()

        for (book in books) {
            buildBookManifestEntry(book)?.let { entry ->
                booksMap[book.id] = entry
            }

            // Build data entry using the book's lastReadTime as proxy for data freshness
            dataMap[book.id] = buildBookDataManifestEntry(book)
        }
        return SyncManifest(books = booksMap, data = dataMap)
    }

    private fun buildBookManifestEntry(book: Book): SyncFileEntry? {
        return runCatching {
            if (BookFileAccess.isContentUri(book.filePath)) {
                val data = readBookData(book.filePath)
                SyncFileEntry(
                    fileName = contentBookFileName(book),
                    sha256 = sha256Bytes(data),
                    sizeBytes = data.size.toLong(),
                    lastModified = book.lastReadTime // SAF providers often omit reliable file timestamps.
                )
            } else {
                val file = File(book.filePath)
                if (!file.exists()) return null
                val data = file.readBytes()
                SyncFileEntry(
                    fileName = file.name,
                    sha256 = sha256Bytes(data),
                    sizeBytes = data.size.toLong(),
                    lastModified = file.lastModified()
                )
            }
        }.onFailure { error ->
            Log.e("WebDAV", "Manifest skip: cannot read book id=${book.id} file=${book.filePath}: ${error.message}", error)
        }.getOrNull()
    }

    private fun contentBookFileName(book: Book): String {
        val displayName = BookFileAccess.displayName(context, book.filePath)
            ?.takeIf { it.isNotBlank() }
        if (displayName != null) return displayName
        return book.title + when (book.format) {
            com.huangder.lumibooks.domain.model.BookFormat.EPUB -> ".epub"
            com.huangder.lumibooks.domain.model.BookFormat.PDF -> ".pdf"
            com.huangder.lumibooks.domain.model.BookFormat.MOBI -> ".mobi"
            com.huangder.lumibooks.domain.model.BookFormat.CBZ -> ".cbz"
            else -> ".txt"
        }
    }

    private fun remoteBookFileName(book: Book): String = "${book.id}.${bookFileExtension(book)}"

    private fun bookFileExtension(book: Book): String {
        val fromName = BookFileAccess.displayName(context, book.filePath)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        if (fromName != null) return fromName
        return when (book.format) {
            com.huangder.lumibooks.domain.model.BookFormat.EPUB -> "epub"
            com.huangder.lumibooks.domain.model.BookFormat.PDF -> "pdf"
            com.huangder.lumibooks.domain.model.BookFormat.MOBI -> "mobi"
            com.huangder.lumibooks.domain.model.BookFormat.CBZ -> "cbz"
            else -> "txt"
        }
    }

    private fun buildBookDataManifestEntry(book: Book): SyncFileEntry = SyncFileEntry(
        fileName = "${book.id}.json",
        sha256 = "",
        sizeBytes = 0,
        lastModified = book.localReadingDataModifiedAt()
    )

    private fun Book.localReadingDataModifiedAt(): Long = maxOf(lastReadTime, metadataUpdatedAt)

    private suspend fun uploadBookFile(
        book: Book,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncFileEntry? {
        val fileName: String
        val expectedSize: Long
        val lastModified: Long

        fileName = remoteBookFileName(book)
        if (BookFileAccess.isContentUri(book.filePath)) {
            expectedSize = BookFileAccess.size(context, book.filePath).let { if (it > 0L) it else -1L }
            lastModified = book.lastReadTime // SAF providers often omit reliable file timestamps.
        } else {
            val file = File(book.filePath)
            if (!file.exists()) return null
            expectedSize = file.length()
            lastModified = file.lastModified()
        }

        if (expectedSize < 0L) {
            // Some SAF providers do not expose a size. In that case use one in-memory read so
            // the WebDAV PUT still has a Content-Length instead of relying on chunked upload,
            // which not every WebDAV server accepts.
            val data = readBookData(book.filePath)
            webdavClient.upload(
                WebdavUrl.append(serverUrl, syncPath, "books", fileName),
                data,
                username,
                password
            )
            return SyncFileEntry(
                fileName = fileName,
                sha256 = sha256Bytes(data),
                sizeBytes = data.size.toLong(),
                lastModified = lastModified
            )
        }

        return try {
            val uploadResult = webdavClient.uploadStream(
                url = WebdavUrl.append(serverUrl, syncPath, "books", fileName),
                contentLength = expectedSize,
                inputStreamProvider = { BookFileAccess.openInputStream(context, book.filePath) },
                username = username,
                password = password
            )

            SyncFileEntry(
                fileName = fileName,
                sha256 = uploadResult.sha256,
                sizeBytes = uploadResult.bytesWritten,
                lastModified = lastModified
            )
        } catch (serverError: WebdavException) {
            // A definitive HTTP rejection will not be fixed by sending the same bytes twice.
            throw serverError
        } catch (streamError: Exception) {
            // Only fall back when streaming failed before the server returned an HTTP response.
            Log.w("WebDAV", "Stream upload failed; retrying fixed-length upload book=${book.id}: ${streamError.message}")
            val data = readBookData(book.filePath)
            webdavClient.upload(
                WebdavUrl.append(serverUrl, syncPath, "books", fileName),
                data,
                username,
                password
            )
            SyncFileEntry(
                fileName = fileName,
                sha256 = sha256Bytes(data),
                sizeBytes = data.size.toLong(),
                lastModified = lastModified
            )
        }
    }

    private suspend fun downloadManifestOrNull(
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncManifest? {
        return try {
            val data = webdavClient.download(
                WebdavUrl.append(serverUrl, syncPath, "manifest.json"),
                username,
                password
            )
            SyncManifest.fromJson(data.toString(Charsets.UTF_8))
        } catch (error: WebdavException) {
            if (error.statusCode == 404) null else throw error
        }
    }

    private suspend fun uploadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        config: WebdavConfig
    ) {
        val existingJson = if (
            config.syncReadingRecords && config.syncBookmarks && config.syncNotes
        ) {
            null
        } else {
            try {
                val remoteData = webdavClient.download(
                    WebdavUrl.append(serverUrl, syncPath, "data", "$bookId.json"),
                    username,
                    password
                )
                JSONObject(remoteData.toString(Charsets.UTF_8))
            } catch (error: WebdavException) {
                if (error.statusCode == 404) null else throw error
            }
        }
        val json = buildBookDataJson(bookId, config, existingJson)
        val data = json.toByteArray(Charsets.UTF_8)
        webdavClient.upload(
            WebdavUrl.append(serverUrl, syncPath, "data", "$bookId.json"),
            data, username, password,
            "application/json"
        )
    }

    private suspend fun downloadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        config: WebdavConfig
    ) {
        try {
            val data = webdavClient.download(
                WebdavUrl.append(serverUrl, syncPath, "data", "$bookId.json"),
                username, password
            )
            val json = data.toString(Charsets.UTF_8)
            applyBookDataJson(bookId, json, config)
        } catch (error: WebdavException) {
            if (error.statusCode != 404) throw error
        }
    }

    private suspend fun buildBookDataJson(
        bookId: String,
        config: WebdavConfig,
        existingJson: JSONObject? = null
    ): String {
        val localBook = bookRepository.getBookById(bookId)
        val book = localBook.takeIf { config.syncReadingRecords }
        val bookmarks = if (config.syncBookmarks) {
            readingRepository.getBookmarksByBookId(bookId).first()
        } else {
            emptyList()
        }
        val notes = if (config.syncNotes) {
            readingRepository.getNotesByBookId(bookId).first()
        } else {
            emptyList()
        }

        return (existingJson ?: JSONObject()).apply {
            put("bookId", bookId)
            if (localBook != null) {
                val fallback = cloudFallbackTitle(
                    localBook.id,
                    localBook.remoteFileName ?: localBook.id
                )
                val legacyFileTitle = localBook.remoteFileName?.let(::cloudFileTitle)
                if (!localBook.isCloudOnly ||
                    !isGeneratedCloudTitle(localBook.title, fallback, legacyFileTitle)
                ) {
                    put("metadata", localBook.toSyncMetadata().toJson())
                }
            }
            if (config.syncReadingRecords && book != null) {
                put("readingProgress", book.readingProgress.toDouble())
                if (book.locatorJson != null) put("locatorJson", book.locatorJson) else remove("locatorJson")
                put("lastReadTime", book.lastReadTime)
            }
            if (config.syncBookmarks) {
                put("bookmarks", JSONArray().apply {
                    for (b in bookmarks) {
                        put(JSONObject().apply {
                            put("id", b.id)
                            put("chapterIndex", b.chapterIndex)
                            put("position", b.position.toDouble())
                            b.locatorJson?.let { put("locatorJson", it) }
                            put("title", b.title)
                            put("createdAt", b.createdAt)
                            put("syncId", b.syncId)
                            put("updatedAt", b.updatedAt)
                        })
                    }
                })
            }
            if (config.syncNotes) {
                put("notes", JSONArray().apply {
                    for (n in notes) {
                        put(JSONObject().apply {
                            put("id", n.id)
                            put("chapterIndex", n.chapterIndex)
                            put("startPosition", n.startPosition)
                            put("endPosition", n.endPosition)
                            n.startLocatorJson?.let { put("startLocatorJson", it) }
                            n.endLocatorJson?.let { put("endLocatorJson", it) }
                            put("selectedText", n.selectedText)
                            put("note", n.note)
                            put("isNote", n.isNoteEntry)
                            put("color", n.color)
                            put("createdAt", n.createdAt)
                            put("type", n.type)
                            put("syncId", n.syncId)
                            put("updatedAt", n.updatedAt)
                        })
                    }
                })
            }
        }.toString(2)
    }

    private suspend fun applyBookDataJson(bookId: String, json: String, config: WebdavConfig) {
        val root = JSONObject(json)
        var book = bookRepository.getBookById(bookId)
        val remoteMetadata = syncMetadataFromReadingData(root)
        val localBook = book
        if (localBook != null && remoteMetadata != null) {
            val fallback = cloudFallbackTitle(bookId, localBook.remoteFileName ?: bookId)
            val legacyFileTitle = localBook.remoteFileName?.let(::cloudFileTitle)
            val trustedMetadata = remoteMetadata.takeUnless {
                isGeneratedCloudTitle(it.title, fallback, legacyFileTitle)
            }
            val localIsPlaceholder = localBook.isCloudOnly &&
                isGeneratedCloudTitle(localBook.title, fallback, legacyFileTitle)
            if (trustedMetadata != null && shouldApplyReadingDataMetadata(
                    localIsCloudOnly = localBook.isCloudOnly,
                    localIsPlaceholder = localIsPlaceholder,
                    localUpdatedAt = localBook.metadataUpdatedAt,
                    remoteMetadata = trustedMetadata
                )
            ) {
                val appliedMetadata = requireNotNull(trustedMetadata)
                val updatedBook = localBook.copy(
                    title = appliedMetadata.title.takeIf { it.isNotBlank() } ?: localBook.title,
                    author = appliedMetadata.author.takeIf { it.isNotBlank() } ?: localBook.author,
                    format = appliedMetadata.format.toBookFormatOrNull() ?: localBook.format,
                    createdAt = appliedMetadata.createdAt.takeIf { it > 0L } ?: localBook.createdAt,
                    isFavorite = appliedMetadata.isFavorite,
                    metadataUpdatedAt = appliedMetadata.updatedAt
                )
                bookRepository.updateBook(updatedBook)
                book = updatedBook
            }
        }
        if (book != null && config.syncReadingRecords && root.has("readingProgress")) {
            val progress = root.optDouble("readingProgress", book.readingProgress.toDouble()).toFloat()
            val locator = root.optString("locatorJson", null)
            val lastReadTime = root.optLong("lastReadTime", book.lastReadTime)
            bookRepository.updateReadingProgress(bookId, progress, locator)
            if (lastReadTime > book.lastReadTime) {
                bookRepository.updateLastReadTime(bookId, lastReadTime)
            }
        }

        // Sync bookmarks: clear local, re-insert from remote
        if (config.syncBookmarks && root.has("bookmarks")) {
            val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
            readingRepository.deleteAllBookmarksByBookId(bookId)
            for (i in 0 until bookmarksArr.length()) {
                val b = bookmarksArr.getJSONObject(i)
                readingRepository.insertBookmark(Bookmark(
                    id = 0,
                    bookId = bookId,
                    chapterIndex = b.getInt("chapterIndex"),
                    position = b.getDouble("position").toFloat(),
                    locatorJson = b.optString("locatorJson", null),
                    title = b.getString("title"),
                    createdAt = b.getLong("createdAt"),
                    syncId = b.optString("syncId").ifBlank {
                        legacyAnnotationSyncId(
                            "bookmark",
                            bookId,
                            b.optInt("chapterIndex").toString(),
                            b.optDouble("position").toString(),
                            b.optLong("createdAt").toString()
                        )
                    },
                    updatedAt = b.optLong("updatedAt", b.optLong("createdAt"))
                ))
            }
        }

        if (config.syncNotes && root.has("notes")) {
            val notesArr = root.optJSONArray("notes") ?: JSONArray()
            readingRepository.deleteAllNotesByBookId(bookId)
            for (i in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(i)
                readingRepository.insertNote(Note(
                    id = 0,
                    bookId = bookId,
                    chapterIndex = n.getInt("chapterIndex"),
                    startPosition = n.getInt("startPosition"),
                    endPosition = n.getInt("endPosition"),
                    startLocatorJson = n.optString("startLocatorJson", null),
                    endLocatorJson = n.optString("endLocatorJson", null),
                    selectedText = n.getString("selectedText"),
                    note = n.getString("note"),
                    color = n.getString("color"),
                    createdAt = n.getLong("createdAt"),
                    type = n.optString("type", "highlight"),
                    isNote = n.optBoolean("isNote", n.optString("note").isNotBlank() || n.optString("type") == "note"),
                    syncId = n.optString("syncId").ifBlank {
                        legacyAnnotationSyncId(
                            "note",
                            bookId,
                            n.optInt("chapterIndex").toString(),
                            n.optInt("startPosition").toString(),
                            n.optInt("endPosition").toString(),
                            n.optLong("createdAt").toString()
                        )
                    },
                    updatedAt = n.optLong("updatedAt", n.optLong("createdAt"))
                ))
            }
        }
    }

    private fun legacyAnnotationSyncId(prefix: String, vararg parts: String): String {
        val raw = buildString {
            append(prefix)
            parts.forEach { append('|').append(it) }
        }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun commitManifest(
        manifest: SyncManifest,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        atomic: Boolean = false
    ) {
        val finalUrl = WebdavUrl.append(serverUrl, syncPath, "manifest.json")
        val payload = manifest.copy(version = SyncManifest.CURRENT_VERSION)
            .toJson()
            .toByteArray(Charsets.UTF_8)

        if (!atomic) {
            // PUT is supported by WebDAV providers that do not implement MOVE (for example,
            // some hosted services). Ordinary sync can safely replace the manifest directly;
            // destructive remote deletion uses the atomic branch below.
            webdavClient.upload(finalUrl, payload, username, password, "application/json")
            return
        }

        val temporaryUrl = WebdavUrl.append(serverUrl, syncPath, "manifest.json.next")
        webdavClient.upload(temporaryUrl, payload, username, password, "application/json")
        try {
            webdavClient.move(
                sourceUrl = temporaryUrl,
                destinationUrl = finalUrl,
                username = username,
                password = password,
                overwrite = true
            )
        } catch (error: WebdavException) {
            if (error.statusCode in MOVE_UNSUPPORTED_STATUS_CODES) {
                // A number of hosted WebDAV services expose PUT/DELETE but reject MOVE. Keep
                // deletion usable there while still preferring the atomic operation whenever
                // the server supports it. The local record is changed only after this PUT
                // succeeds, just like the normal sync path.
                Log.w("WebDAV", "MOVE is unavailable (HTTP ${error.statusCode}); falling back to PUT")
                try {
                    webdavClient.upload(finalUrl, payload, username, password, "application/json")
                    cleanupTemporaryManifest(temporaryUrl, username, password)
                    return
                } catch (fallbackError: Exception) {
                    cleanupTemporaryManifest(temporaryUrl, username, password)
                    throw fallbackError
                }
            }
            cleanupTemporaryManifest(temporaryUrl, username, password)
            throw error
        } catch (error: Exception) {
            cleanupTemporaryManifest(temporaryUrl, username, password)
            throw error
        }
    }

    private suspend fun cleanupTemporaryManifest(
        temporaryUrl: String,
        username: String,
        password: String
    ) {
        runCatching {
            webdavClient.delete(temporaryUrl, username, password)
        }.onFailure { cleanupError ->
            Log.w("WebDAV", "Unable to clean temporary manifest: ${cleanupError.message}")
        }
    }

    private suspend fun enrichDownloadedBook(
        file: File,
        bookId: String,
        entry: SyncFileEntry
    ): Book? {
        val existing = bookRepository.getBookById(bookId) ?: return null
        val generatedTitle = cloudFallbackTitle(bookId, entry.fileName)
        val hasTrustedRemoteTitle = entry.metadata?.title
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.equals(generatedTitle.trim(), ignoreCase = true) == false
        if (hasTrustedRemoteTitle) return existing
        val format = bookFormatFromName(file.name)

        // Extract cover and metadata from EPUB
        val coverPath = try {
            val parser = BookParserFactory.createParser(format, context)
            try {
                parser.extractCoverPath(file.absolutePath)
            } finally {
                runCatching { parser.close() }
            }
        } catch (_: Exception) { null }

        // EPUB/MOBI carry OPF metadata, CBZ may carry ComicInfo.xml.
        val (title, author) = if (format == BookFormat.EPUB || format == BookFormat.MOBI ||
            format == BookFormat.CBZ
        ) {
            try {
                val metadataParser = com.huangder.lumibooks.util.parser.BookParserFactory.createParser(format, context)
                try {
                    val content = metadataParser.parse(file.absolutePath)
                    val t = content.title.takeIf { it.isNotBlank() && it != file.nameWithoutExtension }
                        ?: existing.title
                    val unknownAuthor = context.getString(R.string.book_author_unknown)
                    val a = content.author.takeIf { it.isNotBlank() && it != unknownAuthor }
                        ?: existing.author
                    t to a
                } finally {
                    runCatching { metadataParser.close() }
                }
            } catch (_: Exception) {
                existing.title to existing.author
            }
        } else {
            existing.title to existing.author
        }

        val book = existing.copy(
            title = title,
            author = author,
            filePath = file.absolutePath,
            coverPath = coverPath ?: existing.coverPath,
            format = format,
            isCloudOnly = false,
            metadataUpdatedAt = System.currentTimeMillis()
        )
        bookRepository.updateBook(book)
        return book
    }

    /** Read all bytes from a book regardless of storage type (app-internal file or SAF content URI). */
    private fun readBookData(location: String): ByteArray = BookFileAccess.readBytes(context, location)

    private fun sha256Bytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "WebdavSync"
        val MOVE_UNSUPPORTED_STATUS_CODES = setOf(403, 405, 501)
    }
}

data class SyncResult(
    val message: String,
    val success: Boolean
)

sealed interface BookDownloadState {
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long
    ) : BookDownloadState {
        val progress: Float
            get() = if (totalBytes > 0L) {
                (bytesRead.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
    }

    data object Completed : BookDownloadState
    data class Failed(val message: String) : BookDownloadState
}

data class CloudBookDownloadResult(
    val book: Book?,
    val message: String,
    val success: Boolean
)

/**
 * 判断一本仍标记为“仅云端”的书是否其实已有完整本地文件，可以只修复记录而不重新下载。
 * 云端占位记录正常时 filePath 为空；只有被 bodyless 状态快照误翻转过（或状态损坏）时，
 * 才会出现 isCloudOnly=true 且 filePath 指向现存文件的情况。
 */
internal fun cloudBookNeedsLocalRepair(book: Book, localFileSize: Long): Boolean =
    book.isCloudOnly &&
        book.filePath.isNotBlank() &&
        localFileSize > 0L &&
        (book.remoteFileSize <= 0L || localFileSize == book.remoteFileSize)
