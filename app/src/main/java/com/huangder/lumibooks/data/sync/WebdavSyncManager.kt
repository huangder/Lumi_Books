package com.huangder.lumibooks.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.BookFileAccess
import java.net.URLEncoder
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
        if (!syncMutex.tryLock()) return SyncResult("正在同步中", false)
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
        if (!config.enabled) return SyncResult("WebDAV \u672a\u542f\u7528", false)

        val password = tokenStore.read()
        if (password.isNullOrBlank()) return SyncResult("\u672a\u914d\u7f6e\u5bc6\u7801", false)

        val normalized = config.normalized()
        if (!normalized.hasSelectedContent) {
            return SyncResult("\u8bf7\u81f3\u5c11\u9009\u62e9\u4e00\u9879\u540c\u6b65\u5185\u5bb9", false)
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
                        remoteMetadata = manifestEntry?.metadata
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
                    try {
                        if (remoteData != null &&
                            (book.id !in initialLocalIds || remoteData.lastModified > book.lastReadTime)
                        ) {
                            downloadBookData(book.id, serverUrl, username, password, syncPath, normalized)
                            dataDownloaded++
                        } else if (book.id in initialLocalIds &&
                            (remoteData == null || book.lastReadTime > remoteData.lastModified)
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

            val now = System.currentTimeMillis()
            dataStoreManager.updateWebdavLastSyncTime(now)
            if (normalized.syncBookFiles) {
                dataStoreManager.saveWebdavSyncedBookIds(confirmedBooks.keys - deletedBooks.keys)
            }
            dataStoreManager.saveWebdavConfig(normalized.copy(lastSyncTime = now))

            val failedNames = failedBookTitles.take(2).joinToString("\u3001")
            val summaries = mutableListOf<String>()
            if (normalized.syncBookFiles) {
                val failureHint = if (booksFailed > 0) {
                    "\uff0c\u5931\u8d25 $booksFailed \u672c" +
                        if (failedNames.isNotBlank()) "\uff1a$failedNames" else ""
                } else ""
                summaries += "\u4e66\u672c\u539f\u6587\u4ef6\uff1a\u5df2\u540c\u6b65 ${confirmedBooks.size} \u672c" +
                    "\uff08\u65b0\u4e0a\u4f20 $booksUploaded \u672c\uff0c\u4e91\u7aef\u5df2\u6709 $booksAlreadyPresent \u672c\uff0c\u65b0\u53d1\u73b0 $cloudBooksDiscovered \u672c\uff09" +
                    failureHint
            }
            if (normalized.syncsBookData) {
                val selectedData = buildList {
                    if (normalized.syncReadingRecords) add("\u9605\u8bfb\u8bb0\u5f55")
                    if (normalized.syncBookmarks) add("\u4e66\u7b7e")
                    if (normalized.syncNotes) add("\u7b14\u8bb0")
                }.joinToString("\u3001")
                val dataFailureHint = if (dataFailed > 0) "\uff0c\u5931\u8d25 $dataFailed \u672c" else ""
                summaries += "$selectedData\uff1a\u4e0a\u4f20 $dataSynced \u672c\uff0c\u4e0b\u8f7d $dataDownloaded \u672c$dataFailureHint"
            }
            val quotaHint = quotaError?.let { "\n" + userFacingWebdavError(it) }.orEmpty()
            SyncResult(
                message = summaries.joinToString("\n") + quotaHint,
                success = booksFailed == 0 && dataFailed == 0
            )
        } catch (error: WebdavException) {
            SyncResult(message = "\u540c\u6b65\u5931\u8d25\uff1a${userFacingWebdavError(error)}", success = false)
        } catch (error: Exception) {
            SyncResult(message = "\u540c\u6b65\u5931\u8d25\uff1a${error.message.orEmpty()}", success = false)
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
        if (!book.isCloudOnly) {
            return CloudBookDownloadResult(book, context.getString(R.string.book_already_downloaded), true)
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
                url = "${config.serverUrl}/${config.syncPath}/books/${encodePathSegment(entry.fileName)}",
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

    suspend fun deleteRemoteBooks(bookIds: Set<String>): SyncResult {
        if (bookIds.isEmpty()) return SyncResult(context.getString(R.string.no_books_selected), true)
        if (!syncMutex.tryLock()) {
            return SyncResult(context.getString(R.string.webdav_operation_in_progress), false)
        }
        return try {
            withContext(Dispatchers.IO) { runRemoteDelete(bookIds) }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun runRemoteDelete(bookIds: Set<String>): SyncResult {
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
                    fileName = remoteBook?.fileName ?: localBook?.remoteFileName,
                    coverFileName = remoteBook?.cover?.fileName,
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
            SyncResult(context.getString(R.string.webdav_books_deleted, bookIds.size), true)
        } catch (error: WebdavException) {
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

    suspend fun testConnection(serverUrl: String, username: String, password: String): SyncResult {
        return try {
            webdavClient.testConnection(serverUrl, username, password)
            SyncResult(message = "连接成功", success = true)
        } catch (e: WebdavException) {
            SyncResult(message = e.message ?: "连接失败", success = false)
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /** Encode a file name for safe use in a URL path segment.
     *  Handles Chinese, spaces, and other non-ASCII characters. */
    private fun encodePathSegment(name: String): String =
        URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun userFacingWebdavError(error: WebdavException): String {
        if (error.serverCode == "TrafficRateExhausted") {
            val remaining = error.availableBytes?.let(::formatBytes) ?: "\u672a\u77e5"
            val required = error.requiredBytes?.let(::formatBytes) ?: "\u672a\u77e5"
            return "WebDAV \u670d\u52a1\u7aef\u4e0a\u4f20\u6d41\u91cf\u989d\u5ea6\u4e0d\u8db3" +
                "\uff08\u5269\u4f59 $remaining\uff0c\u5f53\u524d\u6587\u4ef6\u9700\u8981 $required\uff09\u3002" +
                "\u8bf7\u7b49\u5f85\u989d\u5ea6\u6062\u590d\uff0c\u6216\u5728 WebDAV \u670d\u52a1\u5546\u8c03\u6574\u4e0a\u4f20\u989d\u5ea6\u3002"
        }
        return error.message ?: "WebDAV \u8bf7\u6c42\u5931\u8d25"
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
        val metadata = remoteEntry.metadata?.takeUnless {
            it.title.trim().equals(fallbackTitle.trim(), ignoreCase = true)
        }
        val existingIsGeneratedPlaceholder = existing?.isCloudOnly == true &&
            existing.title.trim().equals(fallbackTitle.trim(), ignoreCase = true)
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
            title = if (remoteMetadataWins) metadata?.title?.takeIf { it.isNotBlank() } ?: base.title else base.title,
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
        remoteFileName.substringBeforeLast('.', remoteFileName)
            .takeIf { it.isNotBlank() && it != bookId }
            ?: context.getString(R.string.webdav_cloud_book_fallback, bookId.take(8))

    private fun String.toBookFormatOrNull(): BookFormat? =
        runCatching { BookFormat.valueOf(uppercase()) }.getOrNull()

    private fun bookFormatFromName(fileName: String): BookFormat = when (fileName.substringAfterLast('.').lowercase()) {
        "epub" -> BookFormat.EPUB
        "pdf" -> BookFormat.PDF
        "mobi" -> BookFormat.MOBI
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
            "$serverUrl/$syncPath/covers/${encodePathSegment(fileName)}",
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
                    "$serverUrl/$syncPath/covers/${encodePathSegment(entry.fileName)}",
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
                    "$serverUrl/$syncPath/$directory/${encodePathSegment(fileName)}",
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
            else -> "txt"
        }
    }

    private fun buildBookDataManifestEntry(book: Book): SyncFileEntry = SyncFileEntry(
        fileName = "${book.id}.json",
        sha256 = "",
        sizeBytes = 0,
        lastModified = book.lastReadTime
    )

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
                "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
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
                url = "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
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
                "$serverUrl/$syncPath/books/${encodePathSegment(fileName)}",
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
                "$serverUrl/$syncPath/manifest.json",
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
                    "$serverUrl/$syncPath/data/$bookId.json",
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
            "$serverUrl/$syncPath/data/$bookId.json",
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
                "$serverUrl/$syncPath/data/$bookId.json",
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
        val book = if (config.syncReadingRecords) bookRepository.getBookById(bookId) else null
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
                            put("color", n.color)
                            put("createdAt", n.createdAt)
                            put("type", n.type)
                        })
                    }
                })
            }
        }.toString(2)
    }

    private suspend fun applyBookDataJson(bookId: String, json: String, config: WebdavConfig) {
        val root = JSONObject(json)
        val book = bookRepository.getBookById(bookId)
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
                    createdAt = b.getLong("createdAt")
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
                    type = n.optString("type", "highlight")
                ))
            }
        }
    }

    private suspend fun commitManifest(
        manifest: SyncManifest,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        atomic: Boolean = false
    ) {
        val finalUrl = "$serverUrl/$syncPath/manifest.json"
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

        val temporaryUrl = "$serverUrl/$syncPath/manifest.json.next"
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

        val (title, author) = if (format == BookFormat.EPUB || format == BookFormat.MOBI) {
            try {
                val metadataParser = com.huangder.lumibooks.util.parser.BookParserFactory.createParser(format, context)
                try {
                    val content = metadataParser.parse(file.absolutePath)
                    val t = content.title.takeIf { it.isNotBlank() && it != file.nameWithoutExtension }
                        ?: existing.title
                    val a = content.author.takeIf { it.isNotBlank() && it != "未知作者" }
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
