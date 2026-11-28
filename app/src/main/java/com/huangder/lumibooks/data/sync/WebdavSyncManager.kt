package com.huangder.lumibooks.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.data.local.WebdavTokenStore
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.domain.model.AnnotationTags
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun webdavProbeErrorKind(
    statusCode: Int,
    htmlResponse: Boolean
): WebdavErrorKind {
    val statusKind = WebdavFailureClassifier.kindForStatus(statusCode)
    return when {
        statusKind != WebdavErrorKind.UNKNOWN -> statusKind
        WebdavFailureClassifier.isSuccessStatus(statusCode) && htmlResponse ->
            WebdavErrorKind.INVALID_RESPONSE
        else -> WebdavErrorKind.UNKNOWN
    }
}

@Singleton
class WebdavSyncManager @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val webdavClient: WebdavClient,
    private val tokenStore: WebdavTokenStore,
    private val dataStoreManager: DataStoreManager,
    private val portableStateSync: WebdavPortableStateSync,
    private val localNetworkAccessManager: LocalNetworkAccessManager,
    private val bookIdentityStore: WebdavBookIdentityStore,
    private val syncLog: WebdavSyncLogStore,
    private val syncChanges: WebdavSyncChanges,
    private val localBookState: WebdavLocalBookState,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressSyncJob: Job? = null
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private val _downloadStates = MutableStateFlow<Map<String, BookDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, BookDownloadState>> = _downloadStates.asStateFlow()
    private val _status = MutableStateFlow<WebdavSyncStatus>(WebdavSyncStatus.Idle)
    /** Live progress of the current (or last) full sync for the bookshelf banner. */
    val status: StateFlow<WebdavSyncStatus> = _status.asStateFlow()
    @Volatile private var runStartedAt = 0L

    private fun reportPhase(
        phase: WebdavSyncPhase,
        bookTitle: String? = null,
        done: Int = 0,
        total: Int = 0
    ) {
        val previous = _status.value as? WebdavSyncStatus.Running
        if (previous?.phase != phase) syncLog.info("Phase: ${phase.name}")
        _status.value = WebdavSyncStatus.Running(phase, bookTitle, done, total, runStartedAt)
    }

    // ── Full sync ───────────────────────────────────────────────────

    /**
     * Full bidirectional sync: compares local vs remote manifest,
     * uploads newer local files, downloads newer remote files.
     * Returns a result description string.
     */
    suspend fun fullSync(
        checkRemoteBookFiles: Boolean = true,
        trigger: WebdavSyncTrigger = WebdavSyncTrigger.MANUAL
    ): SyncResult = fullSync(checkRemoteBookFiles, trigger, tokenStore::read)

    internal suspend fun fullSync(
        checkRemoteBookFiles: Boolean = true,
        trigger: WebdavSyncTrigger = WebdavSyncTrigger.MANUAL,
        passwordReader: () -> String?
    ): SyncResult {
        if (!syncMutex.tryLock()) return SyncResult(context.getString(R.string.webdav_syncing), false)
        _isSyncing.value = true
        runStartedAt = System.currentTimeMillis()
        syncLog.beginRun(trigger, runStartedAt)
        syncLog.info("Sync started (${trigger.name.lowercase()})")
        reportPhase(WebdavSyncPhase.PREPARING)
        var result: SyncResult? = null
        return try {
            withContext(Dispatchers.IO) {
                repeat(3) { attempt ->
                    try {
                        return@withContext runFullSync(checkRemoteBookFiles, trigger, passwordReader)
                    } catch (error: ManifestConflict) {
                        if (attempt == 2) return@withContext SyncResult(
                            context.getString(R.string.webdav_sync_failed_detail, error.message), false
                        )
                        syncLog.info("Remote manifest changed; retrying against the latest version")
                    }
                }
                error("Unreachable")
            }.also { result = it }
        } finally {
            val finished = result
            val now = System.currentTimeMillis()
            if (finished == null) {
                syncLog.warn("Sync cancelled")
                syncLog.endRun(false, now)
                _status.value = WebdavSyncStatus.Idle
            } else {
                finished.message.lines().filter { it.isNotBlank() }.forEach { line ->
                    if (finished.success) syncLog.info(line) else syncLog.error(line)
                }
                syncLog.info("Finished in ${now - runStartedAt} ms")
                syncLog.endRun(finished.success, now)
                _status.value = if (finished.success) {
                    WebdavSyncStatus.Succeeded(finished.message, now)
                } else {
                    WebdavSyncStatus.Failed(finished.message, now)
                }
            }
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun runFullSync(
        checkRemoteBookFiles: Boolean, trigger: WebdavSyncTrigger, passwordReader: () -> String?
    ): SyncResult {
        val config = dataStoreManager.webdavConfig.first()
        if (!config.enabled) return SyncResult(context.getString(R.string.webdav_disabled), false)

        val password = passwordReader()
        if (password.isNullOrBlank()) return SyncResult(context.getString(R.string.webdav_password_missing), false)

        val normalized = config.normalized()
        if (!normalized.hasSelectedContent) {
            return SyncResult(context.getString(R.string.webdav_select_content_required), false)
        }
        localNetworkPermissionFailure(normalized.serverUrl)?.let { return it }
        val serverUrl = normalized.serverUrl
        val username = normalized.username
        val syncPath = normalized.syncPath
        syncLog.info(
            "Server: ${runCatching { WebdavUrl.host(serverUrl) }.getOrNull().orEmpty()}  path: $syncPath  " +
                "books=${normalized.syncBookFiles} data=${normalized.syncsBookData} " +
                "checkRemoteFiles=$checkRemoteBookFiles"
        )

        return try {
            val libraryKey = remoteLibraryKey(normalized)
            val changeKey = syncChanges.key(normalized)
            // Capture BEFORE network work: an edit made during sync must remain pending.
            val localFingerprint = syncFingerprint(
                portableStateSync.localFingerprint(normalized) +
                    bookIdentityStore.readPendingDeletes(libraryKey).toSortedMap().toString() +
                    bookIdentityStore.readPendingReuploads(libraryKey).sorted().toString() +
                    if (normalized.syncBookFiles) bookRepository.getAllBooks().first().sortedBy { it.id }.joinToString {
                        val file = File(it.filePath)
                        val cover = it.coverPath?.let(::File)
                        "${it.id}:${it.toSyncMetadata()}:${it.isCloudOnly}:" +
                            "${it.filePath}:${file.length()}:${file.lastModified()}:" +
                            "${it.coverPath}:${cover?.length()}:${cover?.lastModified()}"
                    } else ""
            )
            if (trigger == WebdavSyncTrigger.AUTO &&
                syncChanges.unchanged(changeKey, localFingerprint, normalized, password)
            ) {
                syncLog.info("No local or remote changes; skipped data synchronization")
                return SyncResult(context.getString(R.string.webdav_sync_success), true)
            }
            webdavClient.ensureDirectory(serverUrl, username, password, syncPath)
            if (normalized.syncBookFiles) {
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/books")
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/covers")
            }
            if (normalized.syncsBookData) {
                webdavClient.ensureDirectory(serverUrl, username, password, "$syncPath/data")
            }

            reportPhase(WebdavSyncPhase.MANIFEST)
            val existingManifest = downloadManifestVersion(serverUrl, username, password, syncPath)
            val downloadedManifest = existingManifest?.manifest ?: SyncManifest()
            syncLog.info(
                "Remote manifest: ${downloadedManifest.books.size} books, " +
                    "${downloadedManifest.data.size} data entries, ${downloadedManifest.deletedBooks.size} deletions"
            )
            val pendingDeletes = bookIdentityStore.readPendingDeletes(libraryKey)
            val rememberedIds = bookIdentityStore.read(libraryKey)
            val pendingWireIds = downloadedManifest.resolveBookDeletionIds(pendingDeletes, rememberedIds)
            // An explicit re-upload lifts the cloud deletion that would otherwise block the book.
            val pendingReuploads = bookIdentityStore.readPendingReuploads(libraryKey)
            val revivedWireIds = pendingReuploads.flatMapTo(mutableSetOf()) { listOfNotNull(rememberedIds[it], it) }
            val remoteManifest = downloadedManifest.withBookDeletions(
                pendingDeletes, pendingWireIds, System.currentTimeMillis()
            ).let { it.copy(deletedBooks = it.deletedBooks - revivedWireIds) }
            val confirmedBooks = remoteManifest.books.toMutableMap()
            val dataEntries = remoteManifest.data.toMutableMap()
            val deletedBooks = remoteManifest.deletedBooks.toMutableMap()
            val booksUrl = WebdavUrl.append(serverUrl, syncPath, "books")
            val bodyPresence = mutableMapOf<String, Boolean>()
            suspend fun bodyExists(fileName: String): Boolean = bodyPresence.getOrPut(fileName) {
                !webdavClient.isFileMissing(WebdavUrl.append(booksUrl, fileName), username, password)
            }
            val missingBodies = if (normalized.syncBookFiles && checkRemoteBookFiles) {
                confirmedBooks.filterValues { it.fileName.isSafeWebdavFileName() && !bodyExists(it.fileName) }.keys
            } else emptySet()
            val initialLocalBooks = bookRepository.getBooksForWebdav()
            val contentResolution = resolveContentIdentities(initialLocalBooks, remoteManifest, libraryKey, missingBodies)
            val wireBookIds = contentResolution.wireIds.filterKeys { id -> initialLocalBooks.any { it.id == id } }
            val initialLocalIds = initialLocalBooks.mapTo(mutableSetOf()) { it.id }
            val localBookIds = wireBookIds.filterKeys { it in initialLocalIds }
                .entries.associate { (local, wire) -> wire to local }
            val duplicateRows = contentResolution.duplicateRows
            val duplicateWires = contentResolution.duplicateWireIds
            val archiveOwners = (duplicateRows.values + duplicateWires.values).toSet()
            localBookState.foldDuplicates(duplicateRows)
            for ((localId, wireId) in wireBookIds) {
                val book = bookRepository.getBookById(localId) ?: continue
                val prepared = if (normalized.syncReadingRecords) {
                    book.prepareFirstContentLink(rememberedIds[localId] == wireId ||
                        (localId == wireId && localId in remoteManifest.data))
                } else book
                val entry = confirmedBooks[wireId]
                // A placeholder must keep its old path until the replacement is checked.
                val linked = if (entry != null && (!book.isCloudOnly || book.filePath.isNotBlank())) {
                    prepared.withWebdavAssociation(entry, libraryKey, normalizeUnread = false)
                } else prepared
                if (linked != book) bookRepository.updateBook(linked)
            }

            reportPhase(WebdavSyncPhase.REMOTE_CHANGES)
            for (bookId in deletedBooks.keys) {
                val affected = initialLocalBooks.filter { book ->
                    book.remoteLibraryKey == libraryKey &&
                        (book.id == bookId || rememberedIds[book.id] == bookId)
                }
                for (local in affected) {
                    if (local.isCloudOnly && local.filePath.isBlank()) {
                        localBookState.setSuppressed(local.id, true)
                    } else {
                        bookRepository.clearRemoteAssociation(local.id)
                    }
                }
                confirmedBooks.remove(bookId)
                dataEntries.remove(bookId)
            }

            var cloudBooksDiscovered = 0
            var coversComplete = true
            for (book in initialLocalBooks) {
                if (!book.isCloudOnly || book.filePath.isNotBlank() || book.id in duplicateRows ||
                    (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey)) continue
                val wire = wireBookIds[book.id] ?: book.id
                if (wire !in confirmedBooks) localBookState.setSuppressed(book.id, true)
            }
            if (normalized.syncBookFiles) {
                val remoteTotal = confirmedBooks.size
                var remoteDone = 0
                for ((bookId, remoteEntry) in confirmedBooks) {
                    remoteDone++
                    reportPhase(
                        WebdavSyncPhase.REMOTE_CHANGES,
                        remoteEntry.metadata?.title?.takeIf { it.isNotBlank() }
                            ?: cloudFileTitle(remoteEntry.fileName),
                        remoteDone,
                        remoteTotal
                    )
                    if (bookId in deletedBooks || bookId in duplicateWires || !remoteEntry.fileName.isSafeWebdavFileName()) continue
                    val localId = localBookIds[bookId] ?: bookId
                    val existing = bookRepository.getBookById(localId)
                    if (existing != null && wireBookIds[existing.id]?.let { it != bookId } == true) continue
                    if (existing?.isCloudOnly == true && existing.remoteLibraryKey != null &&
                        existing.remoteLibraryKey != libraryKey) continue
                    val needsProbe = checkRemoteBookFiles || existing == null || localBookState.isSuppressed(localId) ||
                        (existing.isCloudOnly && existing.remoteFileName != remoteEntry.fileName)
                    if (needsProbe && !bodyExists(remoteEntry.fileName)) {
                        if (existing?.isCloudOnly == true && existing.filePath.isBlank()) {
                            localBookState.setSuppressed(localId, true)
                        }
                        continue
                    }
                    val localCover = existing?.coverPath?.let(::File)
                    val remoteCoverPath = if (remoteEntry.cover != null && shouldDownloadWebdavCover(
                            hasLocalCover = localCover?.isFile == true && localCover.length() > 0L,
                            localMetadataUpdatedAt = existing?.metadataUpdatedAt,
                            remoteMetadata = remoteEntry.metadata
                        )) {
                        downloadCoverThumbnail(
                            bookId = bookId,
                            entry = remoteEntry.cover,
                            serverUrl = serverUrl,
                            username = username,
                            password = password,
                            syncPath = syncPath
                        ).also { if (it == null) coversComplete = false }
                    } else {
                        existing?.coverPath
                    }
                    val merged = mergeRemoteBook(
                        bookId = localId,
                        remoteEntry = remoteEntry,
                        existing = existing,
                        coverPath = remoteCoverPath,
                        libraryKey = libraryKey
                    ).let { if (existing != null && localId in archiveOwners) it.copy(isArchived = existing.isArchived) else it }
                    if (existing == null) {
                        bookRepository.insertBook(merged)
                        syncLog.info("New cloud book: ${merged.title}")
                        cloudBooksDiscovered++
                    } else if (merged != existing) {
                        bookRepository.updateBook(merged)
                    }
                    localBookState.setSuppressed(localId, false)
                }
            }

            // The cloud copy was replaced by an edit elsewhere while this copy stayed untouched.
            val appliedBaselines = mutableMapOf<String, String>()
            // A deferred update must run again even if nothing else changes in the meantime.
            var staleBodyDeferred = false
            if (normalized.syncBookFiles) {
                for (localId in contentResolution.staleBodies) {
                    val book = bookRepository.getBookById(localId) ?: continue
                    val entry = confirmedBooks[wireBookIds[localId] ?: continue] ?: continue
                    val expected = contentResolution.syncedHashes[localId] ?: continue
                    if (!canReplaceStaleBody(book, entry)) {
                        syncLog.info("Cloud copy of 《${book.title}》 is newer; the local file was kept")
                        continue
                    }
                    if (WebdavOpenBooks.isBusy(book.id)) {
                        staleBodyDeferred = true
                        syncLog.info("《${book.title}》 is open; its cloud update waits for the next sync")
                        continue
                    }
                    try {
                        if (replaceStaleBody(book, entry, expected, booksUrl, username, password)) {
                            appliedBaselines[localId] = entry.sha256.lowercase()
                            syncLog.info("Updated 《${book.title}》 from the edited cloud copy")
                        } else staleBodyDeferred = true
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        staleBodyDeferred = true
                        syncLog.warn("Could not update 《${book.title}》 from the cloud: ${describeForLog(error)}")
                    }
                }
            }

            val books = bookRepository.getAllBooks().first()
            val appliedIdentities = mutableMapOf<String, SyncBookIdentity>()
            val associations = mutableMapOf<String, SyncFileEntry>()
            val blockedUploads = mutableSetOf<String>()
            var booksUploaded = 0
            var booksAlreadyPresent = 0
            var booksFailed = 0
            val failedBookTitles = mutableListOf<String>()
            var quotaError: WebdavException? = null

            if (normalized.syncBookFiles) {
                var uploadDone = 0
                for (book in books) {
                    uploadDone++
                    reportPhase(WebdavSyncPhase.UPLOADING_BOOKS, book.title, uploadDone, books.size)
                    if ((wireBookIds[book.id] ?: book.id) in deletedBooks) {
                        if (!book.isCloudOnly && book.filePath.isNotBlank()) blockedUploads += book.id
                        continue
                    }
                    val wireId = wireBookIds[book.id] ?: book.id
                    val changed = contentResolution.changedBodies[book.id]
                    // A copy the cloud has moved past must never be published, not even to fill a missing body.
                    val stale = book.id in contentResolution.staleBodies
                    val localSize = BookFileAccess.size(context, book.filePath)
                    val manifestEntry = confirmedBooks[wireId]?.takeIf { it.fileName.isSafeWebdavFileName() }
                    if (wireId in duplicateWires || book.id in duplicateRows ||
                        (book.isCloudOnly && book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey)) continue

                    var confirmedEntry = manifestEntry
                    if (book.isCloudOnly) {
                        // A placeholder has no body to upload, but locally edited metadata and
                        // cover changes still need to reach the remote manifest.
                        if (confirmedEntry == null) continue
                        booksAlreadyPresent++
                    } else if (manifestEntry == null && stale) {
                        continue
                    } else if (manifestEntry != null && (wireId !in missingBodies || stale) && changed == null) {
                        booksAlreadyPresent++
                    } else {
                        val availableBytes = quotaError?.availableBytes
                        if (availableBytes != null && localSize > availableBytes) {
                            syncLog.warn("Skipped 《${book.title}》: remote quota exhausted")
                            booksFailed++
                            failedBookTitles.add(book.title)
                            continue
                        }
                        try {
                            // Fill a missing legacy path in place only when its expected bytes
                            // match. Otherwise write a separate file, never overwrite shared data.
                            // An edited body gets a new name: the old file stays intact and
                            // referenced until the manifest that replaces it is committed.
                            val identity = changed ?: contentResolution.identities[wireId]
                            val fileName = manifestEntry?.fileName?.takeIf { name ->
                                book.format != BookFormat.MARKDOWN && identity != null &&
                                    confirmedBooks.values.filter { it.fileName == name }.all {
                                        it.sizeBytes == identity.sizeBytes && it.sha256.equals(identity.sha256, true)
                                    }
                            } ?: webdavBookFileName(book, wireId, identity?.sha256)
                            syncLog.info("Uploading 《${book.title}》 (${formatBytes(localSize)})")
                            confirmedEntry = uploadBookFile(book, booksUrl, username, password, fileName)
                            if (confirmedEntry != null) {
                                bodyPresence[fileName] = true
                                booksUploaded++
                                if (changed != null) {
                                    appliedIdentities[wireId] = changed
                                    manifestEntry?.fileName?.takeIf { it != fileName }?.let { old ->
                                        bookIdentityStore.saveSupersededBodies(
                                            libraryKey, bookIdentityStore.readSupersededBodies(libraryKey) + old
                                        )
                                    }
                                }
                                identity?.let { appliedBaselines[book.id] = it.sha256.lowercase() }
                            } else {
                                syncLog.warn("Upload skipped 《${book.title}》: local file unreadable")
                                booksFailed++
                                failedBookTitles.add(book.title)
                                continue
                            }
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            Log.e("WebDAV", "Upload failed book=${book.id} file=${book.filePath}: ${error.message}", error)
                            syncLog.error("Upload failed 《${book.title}》: ${describeForLog(error)}")
                            if (error is WebdavException && error.serverCode == "TrafficRateExhausted") {
                                quotaError = error
                            } else if (error is WebdavException) {
                                // Transport and HTTP failures affect the whole endpoint. Stop this
                                // batch so we do not keep issuing requests or publish an incomplete
                                // manifest after a 503, disconnect, authentication failure, etc.
                                throw error
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
                    // Devices may retain different local renditions of the same cover revision.
                    val cover = if (metadataResolution.localWins &&
                        shouldPublishWebdavCover(book.metadataUpdatedAt, manifestEntry)
                    ) {
                        val coverUpload = runCatching {
                            uploadCoverThumbnailIfNeeded(
                                book = book,
                                wireId = wireId,
                                remoteCover = manifestEntry?.cover,
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                syncPath = syncPath
                            )
                        }.onFailure { error ->
                            coversComplete = false
                            Log.w("WebDAV", "Cover upload failed book=${book.id}: ${error.message}")
                            syncLog.warn("Cover upload failed 《${book.title}》: ${describeForLog(error)}")
                        }
                        coverUpload.exceptionOrNull()?.let { error ->
                            if (error is CancellationException) throw error
                            if (error is WebdavException) throw error
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
                    confirmedBooks[wireId] = finalEntry
                    // Linked after the commit: a row must never point at an unpublished entry.
                    associations[book.id] = finalEntry
                }
            } else {
                // Without book files only the identity moves, and only when no body would contradict it.
                for ((localId, identity) in contentResolution.changedBodies) {
                    val wireId = wireBookIds[localId] ?: continue
                    if (wireId in confirmedBooks) continue
                    appliedIdentities[wireId] = identity
                    appliedBaselines[localId] = identity.sha256.lowercase()
                }
            }

            var dataSynced = 0
            var dataDownloaded = 0
            var dataFailed = 0
            if (normalized.syncsBookData) {
                for ((alias, owner) in duplicateWires) {
                    val remoteData = dataEntries[alias] ?: continue
                    try {
                        if (downloadBookData(owner, serverUrl, username, password, syncPath, normalized,
                                remoteData, preserveArchive = true, wireId = alias)) dataDownloaded++
                        else dataFailed++
                    } catch (error: Exception) {
                        if (error is CancellationException || error is WebdavException) throw error
                        dataFailed++
                        syncLog.warn("Could not merge legacy reading data: ${describeForLog(error)}")
                    }
                }
                val dataBooks = bookRepository.getAllBooks().first()
                var dataDone = 0
                for (book in dataBooks) {
                    dataDone++
                    reportPhase(WebdavSyncPhase.BOOK_DATA, book.title, dataDone, dataBooks.size)
                    if ((wireBookIds[book.id] ?: book.id) in deletedBooks) continue
                    val wireId = wireBookIds[book.id] ?: book.id
                    val remoteData = dataEntries[wireId]
                    val remoteBook = confirmedBooks[wireId]
                    val syncAnnotations = normalized.syncBookmarks || normalized.syncNotes
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
                                (!syncAnnotations && remoteData.lastModified > book.localReadingDataModifiedAt()) ||
                                needsMetadataBackfill)
                        ) {
                            if (downloadBookData(book.id, serverUrl, username, password, syncPath, normalized, remoteData,
                                    preserveArchive = book.id in archiveOwners, wireId = wireId)) {
                                dataDownloaded++
                            } else {
                                dataFailed++
                            }
                        } else if (book.id in initialLocalIds &&
                            (remoteData == null || syncAnnotations ||
                                book.localReadingDataModifiedAt() > remoteData.lastModified)
                        ) {
                            val usedNames = dataEntries.filterKeys { it != wireId }.values.mapTo(mutableSetOf()) { it.fileName }
                            val remoteFileName = remoteData?.fileName?.takeIf { name ->
                                name.isSafeWebdavFileName() && name !in usedNames
                            } ?: "$wireId.json".takeUnless { it in usedNames } ?: "$wireId-${UUID.randomUUID()}.json"
                            uploadBookData(book.id, serverUrl, username, password, syncPath, normalized, wireId,
                                remoteFileName, remoteData?.fileName, book.id in archiveOwners)
                            val latest = bookRepository.getBookById(book.id) ?: book
                            dataEntries[wireId] = buildBookDataManifestEntry(latest).copy(fileName = remoteFileName)
                            dataSynced++
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException || error is ManifestConflict) throw error
                        if (error is WebdavException) throw error
                        dataFailed++
                        Log.e("WebDAV", "Reading data sync failed book=${book.id}: ${error.message}", error)
                        syncLog.error("Reading data failed 《${book.title}》: ${describeForLog(error)}")
                    }
                }
            }

            reportPhase(WebdavSyncPhase.COMMIT)
            syncLog.info(
                "Books: uploaded $booksUploaded, already present $booksAlreadyPresent, " +
                    "new from cloud $cloudBooksDiscovered, failed $booksFailed; " +
                    "data: uploaded $dataSynced, downloaded $dataDownloaded, failed $dataFailed"
            )
            val committedManifest = SyncManifest(
                    books = confirmedBooks,
                    data = dataEntries,
                    deletedBooks = deletedBooks,
                    bookIdentities = (contentResolution.identities + appliedIdentities).filterKeys { it !in deletedBooks }
                )
            if (existingManifest == null || committedManifest != downloadedManifest) commitManifest(
                manifest = committedManifest,
                serverUrl = serverUrl,
                username = username,
                password = password,
                syncPath = syncPath,
                expected = existingManifest
            )
            if (pendingDeletes.isNotEmpty()) {
                bookIdentityStore.savePendingDeletes(libraryKey, emptyMap())
            }
            if (pendingReuploads.isNotEmpty()) bookIdentityStore.savePendingReuploads(libraryKey, emptySet())
            for ((localId, entry) in associations) {
                val current = bookRepository.getBookById(localId) ?: continue
                val associated = current.copy(
                    remoteLibraryKey = libraryKey,
                    remoteFileName = entry.fileName,
                    remoteFileSize = entry.sizeBytes,
                    remoteFileSha256 = entry.sha256.ifBlank { null }
                )
                if (associated != current) bookRepository.updateBook(associated)
            }
            bookIdentityStore.saveSyncedHashes(libraryKey, contentResolution.syncedHashes + appliedBaselines)
            if (normalized.syncBookFiles) bookIdentityStore.saveBlockedUploads(libraryKey, blockedUploads)
            cleanupDeletedRemoteFiles(committedManifest, serverUrl, username, password, syncPath, libraryKey)

            val portableResult = if (
                normalized.syncProfileAndSettings ||
                normalized.syncLibraryOrganization ||
                normalized.syncReadingData
            ) portableStateSync.also { reportPhase(WebdavSyncPhase.PORTABLE_STATE) }.sync(
                normalized, password, wireBookIds, deletedBooks.keys, duplicateRows.keys, duplicateWires, archiveOwners
            ) else null

            if (booksFailed == 0 && dataFailed == 0 && coversComplete && !staleBodyDeferred &&
                portableResult?.readerThemeDeferred != true) {
                syncChanges.record(changeKey, localFingerprint, committedManifest, portableResult?.remoteStateFingerprint)
            }

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
            if (portableResult?.readerThemeDeferred == true) {
                summaries += context.getString(R.string.webdav_sync_reader_theme_deferred)
            } else if (normalized.syncProfileAndSettings) {
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
                success = booksFailed == 0 && dataFailed == 0,
                failureCategory = quotaError?.let { WebdavFailureCategory.QUOTA }
            )
        } catch (error: ManifestConflict) {
            throw error
        } catch (error: WebdavException) {
            logWebdavFailure("FULL_SYNC", normalized.serverUrl, error)
            syncLog.error("Sync aborted: ${describeForLog(error)}")
            SyncResult(
                message = context.getString(R.string.webdav_sync_failed_detail, userFacingWebdavError(error)),
                success = false,
                failureCategory = webdavFailureCategory(error)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Full sync failed: ${error.message}", error)
            syncLog.error("Sync aborted: ${describeForLog(error)}")
            SyncResult(
                message = context.getString(R.string.webdav_sync_failed_detail, error.message.orEmpty()),
                success = false
            )
        }
    }

    /** One-line, credential-free description of a failure for the user-visible sync log. */
    private fun describeForLog(error: Throwable): String {
        if (error !is WebdavException) {
            return "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        }
        return buildList {
            add("kind=${error.kind.name}")
            error.statusCode?.let { add("HTTP $it") }
            error.serverCode?.let { add("serverCode=$it") }
            error.server?.let { add("server=$it") }
            error.finalUrl?.let { runCatching { WebdavUrl.host(it) }.getOrNull() }?.let { add("host=$it") }
            if (error.redirected) add("redirects=${error.redirectCount}")
            error.contentType?.let { add("contentType=$it") }
            if (error.htmlResponse) add("htmlResponse")
            error.serverDetail?.takeIf { it.isNotBlank() }?.let { add("detail=${it.take(200)}") }
            add(userFacingWebdavError(error))
            error.message?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("  ·  ")
    }

    // Reading progress sync (debounced)

    /** Coalesce progress saves, then use the same change check as other automatic syncs. */
    @Suppress("UNUSED_PARAMETER")
    fun scheduleReadingProgressSync(bookId: String) {
        progressSyncJob?.cancel()
        progressSyncJob = scope.launch {
            delay(60_000)
            val config = dataStoreManager.webdavConfig.first()
            if (!config.enabled || config.syncMode != "auto" || !config.syncsBookData) return@launch
            if (localNetworkAccessManager.stateFor(config.serverUrl) ==
                LocalNetworkAccessState.PERMISSION_REQUIRED
            ) return@launch
            fullSync(checkRemoteBookFiles = false, trigger = WebdavSyncTrigger.AUTO)
        }
    }

    /**
     * Compares the shelf with the remote books directory. Failure messages are user-facing.
     * Hidden books are included, matching what full sync uploads.
     */
    suspend fun libraryOverview(): Result<WebdavLibraryOverview> = withContext(Dispatchers.IO) {
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled) {
            return@withContext Result.failure(IllegalStateException(context.getString(R.string.webdav_disabled)))
        }
        localNetworkPermissionFailure(config.serverUrl)?.let {
            return@withContext Result.failure(IllegalStateException(it.message))
        }
        val password = tokenStore.read()
        if (password.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException(context.getString(R.string.webdav_password_missing))
            )
        }
        try {
            val libraryKey = remoteLibraryKey(config)
            val listed = webdavClient.listBookFileNames(
                WebdavUrl.append(config.serverUrl, config.syncPath, "books"),
                config.username,
                password
            )
            var localOnly = 0
            var remoteOnly = 0
            var both = 0
            var missing = 0
            val remoteOnlyIds = mutableListOf<String>()
            for (book in bookRepository.getAllBooks().first()) {
                val hasLocal = !book.isCloudOnly &&
                    runCatching { BookFileAccess.size(context, book.filePath) }.getOrDefault(0L) > 0L
                val remoteName = book.remoteFileName
                val hasRemote = remoteName != null &&
                    (book.remoteLibraryKey == null || book.remoteLibraryKey == libraryKey) &&
                    remoteName in listed
                when (webdavBookPlacement(hasLocal, hasRemote)) {
                    WebdavBookPlacement.BOTH -> both++
                    WebdavBookPlacement.LOCAL_ONLY -> localOnly++
                    WebdavBookPlacement.REMOTE_ONLY -> {
                        remoteOnly++
                        if (book.isCloudOnly) remoteOnlyIds += book.id
                    }
                    WebdavBookPlacement.MISSING -> missing++
                }
            }
            Result.success(WebdavLibraryOverview(localOnly, remoteOnly, both, missing, remoteOnlyIds))
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebdavException) {
            logWebdavFailure("LIBRARY_OVERVIEW", config.serverUrl, error)
            Result.failure(IllegalStateException(userFacingWebdavError(error)))
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: error.javaClass.simpleName))
        }
    }

    suspend fun downloadBook(bookId: String): CloudBookDownloadResult = downloadBook(bookId, tokenStore::read)

    internal suspend fun downloadBook(bookId: String, passwordReader: () -> String?): CloudBookDownloadResult {
        if (!syncMutex.tryLock()) {
            return CloudBookDownloadResult(
                null,
                context.getString(R.string.webdav_operation_in_progress),
                false
            )
        }
        return try {
            withContext(Dispatchers.IO) { runBookDownload(bookId, passwordReader) }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun runBookDownload(bookId: String, passwordReader: () -> String?): CloudBookDownloadResult {
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
        if (localBookState.isSuppressed(bookId)) {
            return downloadFailure(bookId, context.getString(R.string.webdav_book_unavailable))
        }

        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled) return downloadFailure(bookId, context.getString(R.string.webdav_disabled))
        if (localNetworkAccessManager.stateFor(config.serverUrl) ==
            LocalNetworkAccessState.PERMISSION_REQUIRED
        ) {
            return downloadFailure(bookId, context.getString(R.string.webdav_error_local_network_permission))
        }
        val password = passwordReader()
            ?: return downloadFailure(bookId, context.getString(R.string.webdav_password_missing))
        val libraryKey = remoteLibraryKey(config)
        if (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey) {
            return downloadFailure(bookId, context.getString(R.string.webdav_different_library))
        }

        var partial: File? = null
        var installed: File? = null
        var installedSnapshot: File? = null
        var registered = false
        return try {
            val manifest = downloadManifestOrNull(
                config.serverUrl,
                config.username,
                password,
                config.syncPath
            ) ?: return downloadFailure(bookId, context.getString(R.string.webdav_manifest_missing))
            val wireBookIds = resolveBookIdentities(listOf(book), manifest.books, libraryKey)
            val wireId = wireBookIds[bookId] ?: bookId
            val entry = manifest.books[wireId]
                ?: return downloadFailure(bookId, context.getString(R.string.webdav_book_unavailable))
            if (wireId in manifest.deletedBooks) {
                return downloadFailure(bookId, context.getString(R.string.webdav_book_deleted))
            }
            require(entry.fileName.isSafeWebdavFileName()) { "Invalid WebDAV book filename" }

            val extension = entry.fileName.substringAfterLast('.', "book")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
                ?: "book"
            val booksDirectory = FileUtils.getBooksDirectory(context)
            val partialFile = File.createTempFile("webdav-", ".part", context.cacheDir).also { partial = it }
            val destination = File(booksDirectory, "${UUID.randomUUID()}.$extension")
            _downloadStates.value = _downloadStates.value + (
                bookId to BookDownloadState.Downloading(0L, entry.sizeBytes)
            )
            val result = try {
                webdavClient.downloadToFile(
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
            } catch (error: WebdavException) {
                if (error.statusCode in setOf(404, 410) && book.filePath.isBlank()) {
                    localBookState.setSuppressed(bookId, true)
                }
                throw error
            }
            check(result.bytesWritten > 0L && (entry.sizeBytes <= 0L || result.bytesWritten == entry.sizeBytes)) {
                context.getString(R.string.book_download_size_mismatch)
            }
            check(entry.sha256.isBlank() || result.sha256.equals(entry.sha256, ignoreCase = true)) {
                context.getString(R.string.book_download_checksum_mismatch)
            }
            Files.move(partialFile.toPath(), destination.toPath())
            installed = destination
            val readingFile = if (extension == "mdpack") {
                MarkdownSnapshotStore.unpack(context, bookId, destination, isolated = true)
            } else if (book.format == BookFormat.MARKDOWN || extension in setOf("md", "markdown")) {
                MarkdownSnapshotStore.createWithMetadata(context, bookId, destination.absolutePath, null, isolated = true).file
            } else destination
            if (readingFile != destination) installedSnapshot = readingFile.parentFile
            withContext(NonCancellable) {
                localBookState.transaction {
                    val current = bookRepository.getBookById(bookId)
                    check(current?.isCloudOnly == true && current.filePath == book.filePath) {
                        context.getString(R.string.book_download_save_failed)
                    }
                    bookRepository.markBookDownloaded(bookId, readingFile.absolutePath)
                    localBookState.setSuppressed(bookId, false)
                }
                registered = true
                if (readingFile != destination) destination.delete()
            }
            val updated = enrichDownloadedBook(readingFile, bookId, entry)
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = if (error is WebdavException) {
                logWebdavFailure("DOWNLOAD_BOOK", config.serverUrl, error)
                if (error.statusCode in setOf(404, 410)) context.getString(R.string.webdav_book_unavailable)
                else userFacingWebdavError(error)
            } else {
                error.message ?: context.getString(R.string.book_download_failed)
            }
            downloadFailure(bookId, message)
        } finally {
            partial?.delete()
            // Never scan by book ID: an older client may have flagged an original as cloud-only.
            if (!registered) {
                installed?.delete()
                installedSnapshot?.deleteRecursively()
            }
        }
    }

    /** [detachLocal] removes only the cloud copy: local rows and files stay, unlinked. */
    suspend fun deleteRemoteBooks(
        books: List<Book>,
        detachLocal: Boolean = false,
        deleteLocallyFirst: (suspend () -> Unit)? = null
    ): SyncResult = deleteRemoteBooks(books, detachLocal, tokenStore::read, deleteLocallyFirst)

    internal suspend fun deleteRemoteBooks(
        books: List<Book>,
        detachLocal: Boolean,
        passwordReader: () -> String?,
        deleteLocallyFirst: (suspend () -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val config = dataStoreManager.webdavConfig.first().normalized()
                val libraryKey = config.takeIf { it.serverUrl.isNotBlank() }?.let(::remoteLibraryKey)
                val activeBooks = books.filter { it.remoteLibraryKey == null || it.remoteLibraryKey == libraryKey }
                fun fileNamesOf(items: List<Book>) = items.associate { book ->
                    book.id to (book.remoteFileName ?: "${book.id}.${book.webdavFileExtension()}")
                }
                val fileNames = fileNamesOf(activeBooks)

                if (deleteLocallyFirst != null) {
                    // Persist the delete before removing the row so a later sync can retry it.
                    val linkedBooks = books.filter { it.remoteFileName != null }
                    for ((key, libraryBooks) in linkedBooks.groupBy { it.remoteLibraryKey ?: libraryKey }) {
                        if (key == null) continue
                        val pending = bookIdentityStore.readPendingDeletes(key)
                        bookIdentityStore.savePendingDeletes(key, pending + fileNamesOf(libraryBooks))
                    }
                    deleteLocallyFirst()
                }

                if (activeBooks.size != books.size && deleteLocallyFirst == null) {
                    return@withLock SyncResult(context.getString(R.string.webdav_different_library), false)
                }
                if (fileNames.isEmpty()) return@withLock SyncResult(context.getString(R.string.no_books_selected), true)
                if (!config.enabled) return@withLock SyncResult(context.getString(R.string.webdav_disabled), false)
                localNetworkPermissionFailure(config.serverUrl)?.let { return@withLock it }
                val password = passwordReader()
                    ?: return@withLock SyncResult(context.getString(R.string.webdav_password_missing), false)
                checkNotNull(libraryKey)
                var aliases: Map<String, String> = emptyMap()
                var wireIds: Map<String, String> = emptyMap()
                var updated = SyncManifest()
                for (attempt in 0..2) {
                    val version = downloadManifestVersion(config.serverUrl, config.username, password, config.syncPath)
                    val manifest = version?.manifest ?: SyncManifest()
                    aliases = bookIdentityStore.read(libraryKey) + resolveBookIdentities(
                        bookRepository.getBooksForWebdav(), manifest.books, libraryKey
                    )
                    wireIds = manifest.resolveBookDeletionIds(fileNames, aliases)
                    updated = manifest.withBookDeletions(fileNames, wireIds, System.currentTimeMillis())
                    try {
                        if (updated != manifest) commitManifest(updated, config.serverUrl, config.username, password, config.syncPath, version)
                        break
                    } catch (error: ManifestConflict) {
                        if (attempt == 2) throw error
                    }
                }
                if (deleteLocallyFirst != null) {
                    val pending = bookIdentityStore.readPendingDeletes(libraryKey)
                    bookIdentityStore.savePendingDeletes(libraryKey, pending - fileNames.keys)
                }
                if (detachLocal) {
                    // Remember which deletion covers each kept book so later syncs do not upload it again.
                    val kept = activeBooks.filter { !it.isCloudOnly && it.filePath.isNotBlank() }
                    bookIdentityStore.save(libraryKey, bookIdentityStore.read(libraryKey) +
                        kept.mapNotNull { book -> wireIds[book.id]?.let { book.id to it } })
                    bookIdentityStore.saveBlockedUploads(
                        libraryKey, bookIdentityStore.readBlockedUploads(libraryKey) + kept.map { it.id }
                    )
                    for (book in kept) bookRepository.clearRemoteAssociation(book.id)
                }
                cleanupDeletedRemoteFiles(updated, config.serverUrl, config.username, password, config.syncPath, libraryKey)
                dataStoreManager.saveWebdavSyncedBookIds(updated.activeRemoteBooks().keys)
                val portableResult = if (deleteLocallyFirst != null && (
                        config.syncProfileAndSettings || config.syncLibraryOrganization || config.syncReadingData
                    )
                ) {
                    portableStateSync.sync(config, password, aliases + wireIds, updated.deletedBooks.keys)
                } else null
                val message = context.getString(R.string.webdav_books_deleted, fileNames.size)
                val themeHint = if (portableResult?.readerThemeDeferred == true) {
                    "\n" + context.getString(R.string.webdav_sync_reader_theme_deferred)
                } else ""
                SyncResult(message + themeHint, true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: WebdavException) {
                logWebdavFailure("DELETE_BOOKS", null, error)
                SyncResult(
                    context.getString(R.string.webdav_delete_failed, userFacingWebdavError(error)),
                    false,
                    webdavFailureCategory(error)
                )
            } catch (error: Exception) {
                SyncResult(context.getString(R.string.webdav_delete_failed, error.message.orEmpty()), false)
            }
        }
    }

    /** Cloud-only copies an earlier edit left behind, each mapped to the local book that replaced it. */
    suspend fun supersededCloudCopies(): Map<String, String> {
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled || config.serverUrl.isBlank()) return emptyMap()
        val libraryKey = remoteLibraryKey(config)
        return supersededCloudCopies(bookRepository.getAllBooks().first(), bookIdentityStore.read(libraryKey), libraryKey)
    }

    /** Moves anything only the old copies hold onto the surviving books before they are deleted. */
    suspend fun mergeSupersededCloudCopies(owners: Map<String, String>) {
        syncMutex.withLock { localBookState.foldDuplicates(owners) }
    }

    /** Local books whose upload is held back because their cloud copy was deleted. */
    suspend fun blockedUploadIds(): Set<String> {
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (!config.enabled || config.serverUrl.isBlank() || !config.syncBookFiles) return emptySet()
        return bookIdentityStore.readBlockedUploads(remoteLibraryKey(config))
    }

    /** Lifts the cloud deletion of these books; the next sync uploads them again. */
    suspend fun requestReupload(bookIds: Collection<String>) {
        val config = dataStoreManager.webdavConfig.first().normalized()
        if (config.serverUrl.isBlank() || bookIds.isEmpty()) return
        val libraryKey = remoteLibraryKey(config)
        syncMutex.withLock {
            bookIdentityStore.savePendingReuploads(
                libraryKey, bookIdentityStore.readPendingReuploads(libraryKey) + bookIds
            )
        }
    }

    suspend fun detachLibrary(config: WebdavConfig) {
        val normalized = config.normalized()
        if (normalized.serverUrl.isBlank()) return
        syncMutex.withLock {
            val libraryKey = remoteLibraryKey(normalized)
            val books = bookRepository.getBooksForWebdav()
            for (book in books) {
                if (book.remoteLibraryKey != libraryKey) continue
                if (book.isCloudOnly && book.filePath.isBlank()) {
                    localBookState.setSuppressed(book.id, true)
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
        localNetworkPermissionFailure(serverUrl)?.let { return it }
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
                        success = false,
                        failureCategory = webdavFailureCategory(error)
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
                success = false,
                failureCategory = webdavFailureCategory(error)
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
        val kind = webdavProbeErrorKind(probe.statusCode, probe.htmlResponse)
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

    private suspend fun localNetworkPermissionFailure(serverUrl: String): SyncResult? {
        if (localNetworkAccessManager.stateFor(serverUrl) !=
            LocalNetworkAccessState.PERMISSION_REQUIRED
        ) {
            return null
        }
        return SyncResult(
            message = context.getString(R.string.webdav_error_local_network_permission),
            success = false,
            failureCategory = WebdavFailureCategory.LOCAL_NETWORK_PERMISSION
        )
    }

    private fun userFacingWebdavError(error: WebdavException): String {
        val category = webdavFailureCategory(error)
        return when (category) {
            WebdavFailureCategory.LOCAL_NETWORK_PERMISSION ->
                context.getString(R.string.webdav_error_local_network_permission)
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
            WebdavFailureCategory.SERVICE_UNAVAILABLE ->
                context.getString(R.string.webdav_error_service_unavailable)
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

    private fun webdavFailureCategory(error: WebdavException): WebdavFailureCategory =
        WebdavFailureClassifier.classify(
            kind = error.kind,
            statusCode = error.statusCode,
            serverCode = error.serverCode
        )

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
        updatedAt = metadataUpdatedAt,
        isPinned = isPinned,
        isHidden = isHidden,
        isArchived = isArchived,
        wantToReadAt = wantToReadAt
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
            lastReadTime = 0L,
            readingProgress = 0f,
            createdAt = metadata?.createdAt ?: remoteEntry.lastModified,
            isFavorite = metadata?.isFavorite ?: false,
            isPinned = metadata?.isPinned ?: false,
            isHidden = metadata?.isHidden ?: false,
            isArchived = metadata?.isArchived ?: false,
            wantToReadAt = metadata?.wantToReadAt ?: 0L,
            isCloudOnly = true,
            metadataUpdatedAt = metadata?.updatedAt ?: 0L
        )
        return base.copy(
            lastReadTime = readingPositionTimestamp(base.lastReadTime, base.createdAt, base.readingProgress, base.locatorJson, base.isManuallyMarkedRead),
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
            isPinned = if (remoteMetadataWins) metadata?.isPinned ?: base.isPinned else base.isPinned,
            isHidden = if (remoteMetadataWins) metadata?.isHidden ?: base.isHidden else base.isHidden,
            isArchived = if (remoteMetadataWins) metadata?.isArchived ?: base.isArchived else base.isArchived,
            wantToReadAt = if (remoteMetadataWins) metadata?.wantToReadAt ?: base.wantToReadAt else base.wantToReadAt,
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

    private fun bookFormatFromName(fileName: String): BookFormat =
        BookFormat.fromExtension(fileName.substringAfterLast('.', "")) ?: BookFormat.TXT

    private suspend fun uploadCoverThumbnailIfNeeded(
        book: Book,
        wireId: String,
        remoteCover: SyncFileEntry?,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String
    ): SyncFileEntry? {
        val source = book.coverPath?.let(::File)?.takeIf { it.isFile } ?: return null
        // A downloaded thumbnail may be the only local cover. Re-encoding it changes its hash.
        if (remoteCover?.matchesCoverContent(source.length()) { source.sha256() } == true) {
            return remoteCover
        }

        val bytes = createCoverThumbnail(source) ?: return null
        val sha256 = sha256Bytes(bytes)
        if (remoteCover?.matchesCoverContent(bytes.size.toLong()) { sha256 } == true) {
            return remoteCover
        }
        val fileName = remoteCover?.fileName ?: "$wireId.jpg"
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

    private fun createCoverThumbnail(source: File): ByteArray? {
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
        return try {
            val output = ByteArrayOutputStream()
            check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                "Unable to encode cover thumbnail"
            }
            output.toByteArray()
        } finally {
            thumbnail.recycle()
        }
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebdavException) {
            if (error.statusCode != 404) throw error
            Log.w("WebDAV", "Cover is missing for book=$bookId")
            null
        } catch (error: Exception) {
            Log.w("WebDAV", "Cover download failed book=$bookId: ${error.message}")
            null
        }
    }

    private suspend fun cleanupDeletedRemoteFiles(
        manifest: SyncManifest,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        libraryKey: String
    ) {
        val superseded = bookIdentityStore.readSupersededBodies(libraryKey)
        if (manifest.deletedBooks.isEmpty() && superseded.isEmpty()) return
        // Re-read after the commit: another device may have added an active reference.
        val current = try {
            downloadManifestOrNull(serverUrl, username, password, syncPath) ?: return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            syncLog.warn("Deferred remote file cleanup: ${describeForLog(error)}")
            return
        }
        val replaced = current.unreferencedBookFiles(superseded)
        val failed = mutableSetOf<String>()
        val targets = current.unreferencedDeletedFiles() + replaced.map { "books" to it }
        for ((directory, fileName) in targets) {
            try {
                webdavClient.delete(
                    WebdavUrl.append(serverUrl, syncPath, directory, fileName),
                    username,
                    password
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (directory == "books" && fileName in replaced &&
                    (error as? WebdavException)?.statusCode !in setOf(404, 410)) failed += fileName
                Log.w("WebDAV", "Deferred cleanup failed for $directory/$fileName: ${error.message}")
            }
        }
        // Names still referenced were never superseded after all; only failed deletes are retried.
        if (superseded.isNotEmpty()) bookIdentityStore.saveSupersededBodies(libraryKey, failed)
    }

    private fun canReplaceStaleBody(book: Book, entry: SyncFileEntry): Boolean =
        book.format == BookFormat.TXT && !book.isCloudOnly &&
            entry.fileName.isSafeWebdavFileName() && entry.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            FileUtils.isAppManagedBookLocation(context, book.filePath)

    /** Swap in the edited cloud body, but only over the exact bytes it supersedes. */
    private suspend fun replaceStaleBody(
        book: Book, entry: SyncFileEntry, expectedSha256: String,
        booksUrl: String, username: String, password: String
    ): Boolean {
        val target = File(book.filePath)
        val partial = File(target.parentFile, "${target.name}.webdav.part")
        try {
            val result = webdavClient.downloadToFile(
                url = WebdavUrl.append(booksUrl, entry.fileName), destination = partial,
                username = username, password = password, expectedSize = entry.sizeBytes
            )
            check(result.bytesWritten > 0L && result.bytesWritten == entry.sizeBytes &&
                result.sha256.equals(entry.sha256, ignoreCase = true)) {
                context.getString(R.string.book_download_checksum_mismatch)
            }
            // The user may have opened or edited the book while it downloaded.
            if (WebdavOpenBooks.isBusy(book.id) || !target.isFile ||
                !fileSha256(target).equals(expectedSha256, ignoreCase = true)) return false
            try {
                Files.move(partial.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            ReaderCacheStore.get(context).invalidate(book.filePath)
            FileUtils.deleteTxtIndexCache(context, book.filePath)
            return true
        } finally {
            partial.delete()
        }
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadFailure(bookId: String, message: String): CloudBookDownloadResult {
        _downloadStates.value = _downloadStates.value + (bookId to BookDownloadState.Failed(message))
        return CloudBookDownloadResult(null, message, false)
    }

    private suspend fun resolveContentIdentities(
        books: List<Book>, manifest: SyncManifest, libraryKey: String, missingBodies: Set<String>
    ): WebdavContentIdentityResolution {
        val verified = mutableMapOf<String, SyncBookIdentity>()
        for (book in books) {
            if (book.filePath.isBlank()) continue
            try {
                verified[book.id] = BookFileAccess.openInputStream(context, book.filePath).use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        size += count
                    }
                    SyncBookIdentity(digest.digest().joinToString("") { "%02x".format(it) }, book.format.name, size)
                }
                val size = verified.getValue(book.id).sizeBytes
                if (cloudBookNeedsLocalRepair(book, size)) bookRepository.markBookDownloaded(book.id, book.filePath)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Cannot verify book identity: ${book.id}")
            }
        }
        val resolution = resolveWebdavContentIdentities(
            books, manifest, verified, bookIdentityStore.read(libraryKey), libraryKey, missingBodies,
            bookIdentityStore.readSyncedHashes(libraryKey)
        )
        bookIdentityStore.save(libraryKey, resolution.wireIds)
        // Saved before any row is re-linked, so a failed or retried run classifies edits the same way.
        bookIdentityStore.saveSyncedHashes(libraryKey, resolution.syncedHashes)
        return resolution
    }

    private suspend fun resolveBookIdentities(
        books: List<Book>, remote: Map<String, SyncFileEntry>, libraryKey: String,
        establishNew: Boolean = true
    ): Map<String, String> {
        val localIds = books.mapTo(mutableSetOf()) { it.id }
        val remembered = bookIdentityStore.read(libraryKey).filter { (local, wire) ->
            local !in remote && wire !in localIds
        }
        val hashes = mutableMapOf<String, String>()
        for (book in books) {
            if (!establishNew || remote.isEmpty() || book.id in remembered || book.id in remote || associatedWebdavBookId(book, remote, libraryKey) != null ||
                book.format == BookFormat.MARKDOWN) continue
            if (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey) continue
            if (book.isCloudOnly) {
                book.remoteFileSha256?.let { hashes[book.id] = it }
                continue
            }
            val size = BookFileAccess.size(context, book.filePath)
            if (remote.values.none { it.sizeBytes <= 0L || size <= 0L || it.sizeBytes == size }) continue
            try {
                hashes[book.id] = BookFileAccess.openInputStream(context, book.filePath).use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Cannot verify book identity: ${book.id}")
            }
        }
        val resolved = remembered + matchWebdavBookIds(books, remote, hashes, libraryKey)
        // A partial view must not erase the aliases of books it did not look at.
        if (establishNew) bookIdentityStore.save(libraryKey, bookIdentityStore.read(libraryKey) + resolved)
        return resolved
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
            if (book.format == BookFormat.MARKDOWN) {
                val pack = com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.packageFile(File(book.filePath))
                return@runCatching SyncFileEntry("${book.id}.mdpack", sha256Bytes(pack.readBytes()), pack.length(), File(book.filePath).lastModified())
            }
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
            BookFormat.AZW -> ".azw"
            BookFormat.AZW3 -> ".azw3"
            BookFormat.DOCX -> ".docx"
            BookFormat.DOC -> ".doc"
            com.huangder.lumibooks.domain.model.BookFormat.CBZ -> ".cbz"
            com.huangder.lumibooks.domain.model.BookFormat.MARKDOWN -> ".mdpack"
            else -> ".txt"
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
        booksUrl: String,
        username: String,
        password: String,
        fileName: String
    ): SyncFileEntry? {
        val url = WebdavUrl.append(booksUrl, fileName)
        val expectedSize: Long
        val lastModified: Long

        if (book.format == BookFormat.MARKDOWN) {
            val document = File(book.filePath)
            val pack = com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.packageFile(document)
            val result = webdavClient.uploadStream(
                url = url, contentLength = pack.length(),
                inputStreamProvider = { pack.inputStream().buffered() }, username = username, password = password)
            return SyncFileEntry(fileName, result.sha256, result.bytesWritten, document.lastModified())
        }
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
                url,
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
                url = url,
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
                url,
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
    ): SyncManifest? = downloadManifestVersion(serverUrl, username, password, syncPath)?.manifest

    private suspend fun downloadManifestVersion(
        serverUrl: String, username: String, password: String, syncPath: String
    ): ManifestVersion? = try {
            val version = webdavClient.downloadVersioned(
                WebdavUrl.append(serverUrl, syncPath, "manifest.json"),
                username,
                password
            )
            val json = version.data.toString(Charsets.UTF_8)
            require(json.isNotBlank()) { "Empty WebDAV manifest" }
            ManifestVersion(SyncManifest.fromJson(json), syncJsonFingerprint(json), version.etag?.takeUnless { it.startsWith("W/") })
        } catch (error: WebdavException) {
            if (error.statusCode in setOf(404, 410)) null else throw error
        }

    private suspend fun uploadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        config: WebdavConfig,
        remoteBookId: String,
        remoteFileName: String,
        sourceFileName: String?,
        preserveArchive: Boolean
    ) {
        require(remoteFileName.isSafeWebdavFileName()) { "Invalid WebDAV reading data filename" }
        val target = WebdavUrl.append(serverUrl, syncPath, "data", remoteFileName)
        val expected = downloadVersionedOrNull(target, username, password)
        val source = if (sourceFileName != null && sourceFileName != remoteFileName) {
            require(sourceFileName.isSafeWebdavFileName()) { "Invalid WebDAV reading data filename" }
            downloadVersionedOrNull(WebdavUrl.append(serverUrl, syncPath, "data", sourceFileName), username, password)
        } else null
        val existingJson = localBookState.transaction {
            val inputs = listOfNotNull(source, expected).map {
                JSONObject(it.data.toString(Charsets.UTF_8)).also { json ->
                    require(json.length() > 0) { "Empty WebDAV reading data" }
                    applyBookDataJson(bookId, json, config, preserveArchive, remoteBookId)
                }
            }
            inputs.lastOrNull()
        }
        val json = JSONObject(buildBookDataJson(bookId, config, existingJson))
            .put("bookId", remoteBookId).toString()
        if (expected != null && syncJsonFingerprint(json) == syncJsonFingerprint(expected.data.toString(Charsets.UTF_8))) return
        commitVersionedJson(target, json.toByteArray(Charsets.UTF_8), username, password, expected?.etag,
            expected?.let { syncJsonFingerprint(it.data.toString(Charsets.UTF_8)) })
    }

    private suspend fun downloadBookData(
        bookId: String,
        serverUrl: String,
        username: String,
        password: String,
        syncPath: String,
        config: WebdavConfig,
        entry: SyncFileEntry,
        preserveArchive: Boolean,
        wireId: String
    ): Boolean {
        require(entry.fileName.isSafeWebdavFileName()) { "Invalid WebDAV reading data filename" }
        return try {
            val data = webdavClient.download(
                WebdavUrl.append(serverUrl, syncPath, "data", entry.fileName),
                username, password
            )
            require(data.isNotEmpty() && (entry.sizeBytes <= 0 || entry.sizeBytes == data.size.toLong()) &&
                (entry.sha256.isBlank() || sha256Bytes(data).equals(entry.sha256, true))) { "Invalid WebDAV reading data" }
            val json = JSONObject(data.toString(Charsets.UTF_8))
            require(json.length() > 0) { "Empty WebDAV reading data" }
            localBookState.transaction { applyBookDataJson(bookId, json, config, preserveArchive, wireId) }
            true
        } catch (error: WebdavException) {
            if (error.statusCode !in setOf(404, 410)) throw error
            false
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
                put("isManuallyMarkedRead", book.isManuallyMarkedRead)
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
                            put("remark", b.remark)
                            put("tags", JSONArray(b.tags))
                            put("isStale", b.isStale)
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
                            put("origin", n.origin)
                            put("tags", JSONArray(n.tags))
                            put("isStale", n.isStale)
                            n.sourceRuleId?.let { put("sourceRuleId", it) }
                            n.sourceMatchKey?.let { put("sourceMatchKey", it) }
                            n.styleSnapshotJson?.let { put("styleSnapshotJson", it) }
                        })
                    }
                })
            }
        }.toString(2)
    }

    private suspend fun applyBookDataJson(
        bookId: String, root: JSONObject, config: WebdavConfig, preserveArchive: Boolean, wireId: String
    ) {
        val book = bookRepository.getBookById(bookId)
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
                    lastReadTime = readingPositionTimestamp(localBook.lastReadTime, localBook.createdAt, localBook.readingProgress, localBook.locatorJson, localBook.isManuallyMarkedRead),
                    title = appliedMetadata.title.takeIf { it.isNotBlank() } ?: localBook.title,
                    author = appliedMetadata.author.takeIf { it.isNotBlank() } ?: localBook.author,
                    format = appliedMetadata.format.toBookFormatOrNull() ?: localBook.format,
                    createdAt = appliedMetadata.createdAt.takeIf { it > 0L } ?: localBook.createdAt,
                    isFavorite = appliedMetadata.isFavorite,
                    isPinned = appliedMetadata.isPinned,
                    isHidden = appliedMetadata.isHidden ?: localBook.isHidden,
                    isArchived = if (preserveArchive) localBook.isArchived else appliedMetadata.isArchived ?: localBook.isArchived,
                    wantToReadAt = appliedMetadata.wantToReadAt ?: localBook.wantToReadAt,
                    metadataUpdatedAt = appliedMetadata.updatedAt
                )
                bookRepository.updateBook(updatedBook)
            }
        }
        if (config.syncReadingRecords) applySyncedReadingPosition(bookRepository, bookId, root)

        // Legacy arrays have no deletion markers. Apply explicit updates only; the portable
        // snapshot carries deletions. A parse error rolls back the whole Room transaction.
        val deletedAnnotations = localBookState.annotationDeletions()
        if (config.syncBookmarks && root.has("bookmarks")) {
            val bookmarksArr = root.getJSONArray("bookmarks")
            for (i in 0 until bookmarksArr.length()) {
                val b = bookmarksArr.getJSONObject(i)
                val syncId = b.optString("syncId").ifBlank {
                    legacyAnnotationSyncId(
                        "bookmark",
                        wireId,
                        b.optInt("chapterIndex").toString(),
                        b.optDouble("position").toString(),
                        b.optLong("createdAt").toString()
                    )
                }
                val updatedAt = b.optLong("updatedAt", b.getLong("createdAt"))
                val local = localBookState.bookmark(syncId)
                if ((local != null && (local.bookId != bookId || updatedAt <= local.updatedAt)) ||
                    updatedAt <= (deletedAnnotations["bookmark" to syncId] ?: Long.MIN_VALUE)) continue
                localBookState.saveBookmark(BookmarkEntity(
                    id = local?.id ?: 0,
                    bookId = bookId,
                    chapterIndex = b.getInt("chapterIndex"),
                    position = b.getDouble("position").toFloat(),
                    locatorJson = b.optString("locatorJson", null),
                    title = b.getString("title"),
                    remark = b.optString("remark"),
                    tagsJson = AnnotationTags.encode(resolveSyncedAnnotationTags(
                        hasRemoteTags = b.has("tags"),
                        remoteTagsJson = b.optJSONArray("tags")?.toString(),
                        localTags = AnnotationTags.decode(local?.tagsJson)
                    )),
                    isStale = b.optBoolean("isStale", false),
                    createdAt = b.getLong("createdAt"),
                    syncId = syncId,
                    updatedAt = updatedAt
                ))
            }
        }

        if (config.syncNotes && root.has("notes")) {
            val notesArr = root.getJSONArray("notes")
            for (i in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(i)
                val syncId = n.optString("syncId").ifBlank {
                    legacyAnnotationSyncId(
                        "note",
                        wireId,
                        n.optInt("chapterIndex").toString(),
                        n.optInt("startPosition").toString(),
                        n.optInt("endPosition").toString(),
                        n.optLong("createdAt").toString()
                    )
                }
                val updatedAt = n.optLong("updatedAt", n.getLong("createdAt"))
                val local = localBookState.note(syncId)
                if ((local != null && (local.bookId != bookId || updatedAt <= local.updatedAt)) ||
                    updatedAt <= (deletedAnnotations["note" to syncId] ?: Long.MIN_VALUE)) continue
                localBookState.saveNote(NoteEntity(
                    id = local?.id ?: 0,
                    bookId = bookId,
                    chapterIndex = n.getInt("chapterIndex"),
                    startPosition = n.getInt("startPosition"),
                    endPosition = n.getInt("endPosition"),
                    startLocatorJson = n.optString("startLocatorJson", null),
                    endLocatorJson = n.optString("endLocatorJson", null),
                    selectedText = n.getString("selectedText"),
                    isStale = n.optBoolean("isStale", false),
                    note = n.getString("note"),
                    color = n.getString("color"),
                    createdAt = n.getLong("createdAt"),
                    type = n.optString("type", "highlight"),
                    isNote = n.optBoolean("isNote", n.optString("note").isNotBlank() || n.optString("type") == "note"),
                    syncId = syncId,
                    updatedAt = updatedAt,
                    origin = n.optString("origin", Note.ORIGIN_MANUAL),
                    sourceRuleId = n.optString("sourceRuleId", null),
                    sourceMatchKey = n.optString("sourceMatchKey", null),
                    styleSnapshotJson = n.optString("styleSnapshotJson", null),
                    bookProgress = local?.bookProgress,
                    tagsJson = AnnotationTags.encode(resolveSyncedAnnotationTags(
                        hasRemoteTags = n.has("tags"),
                        remoteTagsJson = n.optJSONArray("tags")?.toString(),
                        localTags = AnnotationTags.decode(local?.tagsJson)
                    ))
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
        expected: ManifestVersion?
    ) {
        commitVersionedJson(WebdavUrl.append(serverUrl, syncPath, "manifest.json"),
            manifest.copy(version = SyncManifest.CURRENT_VERSION).toJson().toByteArray(Charsets.UTF_8),
            username, password, expected?.etag, expected?.fingerprint)
    }

    private suspend fun downloadVersionedOrNull(url: String, username: String, password: String): WebdavVersionedData? =
        try {
            webdavClient.downloadVersioned(url, username, password)
        } catch (error: WebdavException) {
            if (error.statusCode in setOf(404, 410)) null else throw error
        }

    private suspend fun commitVersionedJson(
        url: String, payload: ByteArray, username: String, password: String,
        etag: String?, expectedFingerprint: String?
    ) {
        val strongEtag = etag?.takeUnless { it.startsWith("W/") }
        if (expectedFingerprint == null || strongEtag != null) {
            try {
                webdavClient.uploadConditional(url, payload, username, password, strongEtag, "application/json")
                return
            } catch (error: WebdavException) {
                if (error.statusCode == 412) throw ManifestConflict()
                if (error.statusCode !in setOf(400, 405, 501)) throw error
            }
        }
        // Older servers may not provide a strong validator. Refuse a stale write when
        // detectable, but keep those servers usable (this is not a compare-and-swap).
        val current = downloadVersionedOrNull(url, username, password)
        if (current?.let { syncJsonFingerprint(it.data.toString(Charsets.UTF_8)) } != expectedFingerprint) throw ManifestConflict()
        webdavClient.upload(url, payload, username, password, "application/json")
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
        val (title, author) = if (format.supportsEpubFeatures ||
            format == BookFormat.CBZ || format == BookFormat.MARKDOWN
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
    }
}

internal fun isMoveFallbackStatus(statusCode: Int?): Boolean =
    statusCode in setOf(403, 405, 500, 501)

data class SyncResult(
    val message: String,
    val success: Boolean,
    val failureCategory: WebdavFailureCategory? = null
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
