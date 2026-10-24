package com.huangder.lumibooks.data.remote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.data.sync.LocalNetworkAccessState
import com.huangder.lumibooks.ui.home.BookImportPipeline
import com.huangder.lumibooks.util.AuthorizedStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where downloaded remote books go. */
sealed interface RemoteDestination {
    /** Copied into app storage, optionally placed into a library folder. */
    data class AppStorage(val libraryFolderId: String? = null) : RemoteDestination

    /** Written into an authorized folder (a persisted SAF tree) and read from there. */
    data class AuthorizedFolder(val treeUri: String, val name: String) : RemoteDestination
}

sealed interface RemoteDownloadState {
    data object Queued : RemoteDownloadState
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : RemoteDownloadState {
        val progress: Float? get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    data object Importing : RemoteDownloadState
    data class Completed(val bookId: String, val alreadyInLibrary: Boolean) : RemoteDownloadState
    data class Failed(val kind: RemoteErrorKind) : RemoteDownloadState
}

/**
 * App-scoped queue for remote downloads, so a transfer keeps going after the user leaves the
 * browsing page. Files are staged in the cache, verified, then handed to [BookImportPipeline].
 */
@Singleton
class RemoteDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RemoteLibraryStore,
    private val opdsClient: OpdsClient,
    private val webdavClient: WebdavLibraryClient,
    private val pipeline: BookImportPipeline,
    private val authorizedStorageManager: AuthorizedStorageManager,
    private val localNetworkAccessManager: LocalNetworkAccessManager
) {
    private data class Request(
        val source: RemoteSource,
        val item: RemoteBookItem,
        val format: RemoteFormat,
        val destination: RemoteDestination,
        val prefetched: File?
    )

    data class BatchSummary(val imported: Int, val existing: Int, val failed: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Request>(Channel.UNLIMITED)
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val _states = MutableStateFlow<Map<String, RemoteDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, RemoteDownloadState>> = _states.asStateFlow()
    private val _batchFinished = MutableSharedFlow<BatchSummary>(extraBufferCapacity = 4)
    val batchFinished: SharedFlow<BatchSummary> = _batchFinished.asSharedFlow()
    private var pendingInBatch = 0
    private var batchImported = 0
    private var batchExisting = 0
    private var batchFailed = 0
    private val stagingRoot = File(context.cacheDir, "remote_import")

    init {
        stagingRoot.deleteRecursively()
        scope.launch {
            for (request in queue) process(request)
        }
    }

    /** Queues [items]; a book already queued or downloading is ignored. */
    fun enqueue(
        source: RemoteSource,
        items: List<Pair<RemoteBookItem, RemoteFormat>>,
        destination: RemoteDestination,
        prefetched: Map<String, File> = emptyMap()
    ) {
        val accepted = items.filter { (item, _) ->
            val current = _states.value[item.key]
            current == null || current is RemoteDownloadState.Failed || current is RemoteDownloadState.Completed
        }
        if (accepted.isEmpty()) return
        synchronized(this) { pendingInBatch += accepted.size }
        _states.update { states -> states + accepted.associate { (item, _) -> item.key to RemoteDownloadState.Queued } }
        accepted.forEach { (item, format) ->
            cancelled.remove(item.key)
            // The caller's preview may be deleted when its page closes; queue a private copy.
            val owned = prefetched[item.key]?.takeIf(File::isFile)?.let { preview ->
                runCatching {
                    File(stagingRoot, "prefetch-${UUID.randomUUID()}").also {
                        stagingRoot.mkdirs()
                        preview.copyTo(it)
                    }
                }.getOrNull()
            }
            queue.trySend(Request(source, item, format, destination, owned))
        }
    }

    fun cancel(itemKey: String) {
        val state = _states.value[itemKey]
        if (state is RemoteDownloadState.Queued || state is RemoteDownloadState.Downloading) {
            cancelled.add(itemKey)
        }
    }

    fun clearFinished(itemKey: String) {
        _states.update { states ->
            val state = states[itemKey]
            if (state is RemoteDownloadState.Failed || state is RemoteDownloadState.Completed) states - itemKey else states
        }
    }

    private suspend fun process(request: Request) {
        val key = request.item.key
        val result: RemoteDownloadState? = try {
            if (key in cancelled) null else runDownload(request)
        } catch (_: CancellationException) {
            // Only a user cancellation reaches here; the manager scope itself is never cancelled.
            null
        } catch (error: RemoteException) {
            Log.w(TAG, "Remote download failed kind=${error.kind} status=${error.statusCode}")
            RemoteDownloadState.Failed(error.kind)
        } catch (error: Exception) {
            Log.w(TAG, "Remote download failed", error)
            RemoteDownloadState.Failed(RemoteErrorKind.UNKNOWN)
        }
        // The private preview copy is moved into the batch on success; otherwise drop it here.
        request.prefetched?.delete()
        // A cancelled download simply disappears from the list.
        _states.update { states -> if (result == null) states - key else states + (key to result) }
        cancelled.remove(key)
        finishOne(result)
    }

    private fun finishOne(result: RemoteDownloadState?) {
        val summary = synchronized(this) {
            when (result) {
                is RemoteDownloadState.Completed -> if (result.alreadyInLibrary) batchExisting++ else batchImported++
                is RemoteDownloadState.Failed -> batchFailed++
                else -> Unit
            }
            pendingInBatch = (pendingInBatch - 1).coerceAtLeast(0)
            if (pendingInBatch == 0) {
                BatchSummary(batchImported, batchExisting, batchFailed).also {
                    batchImported = 0
                    batchExisting = 0
                    batchFailed = 0
                }
            } else {
                null
            }
        }
        if (summary != null && (summary.imported + summary.existing + summary.failed) > 0) {
            _batchFinished.tryEmit(summary)
        }
    }

    private suspend fun runDownload(request: Request): RemoteDownloadState {
        val (source, item, format, destination, prefetched) = request
        if (!format.isSupported) throw RemoteException(RemoteErrorKind.UNSUPPORTED_FORMAT)
        if (localNetworkAccessManager.stateFor(source.url) == LocalNetworkAccessState.PERMISSION_REQUIRED) {
            throw RemoteException(RemoteErrorKind.LOCAL_NETWORK_PERMISSION)
        }
        val batch = File(stagingRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val staged: File
            val fileName: String
            if (prefetched != null && prefetched.isFile) {
                staged = File(batch, "download.part")
                if (!prefetched.renameTo(staged)) {
                    prefetched.copyTo(staged, overwrite = true)
                    prefetched.delete()
                }
                fileName = RemoteBookFiles.resolveDownloadFileName(
                    contentDisposition = null,
                    responseMimeType = format.mimeType,
                    url = format.url,
                    title = item.fileName ?: item.title,
                    expectedExtension = format.extension
                )
            } else {
                staged = File(batch, "download.part")
                val password = store.password(source.id)
                _states.update { it + (item.key to RemoteDownloadState.Downloading(0L, format.sizeBytes)) }
                val onProgress: (Long, Long) -> Unit = { read, total ->
                    val known = total.takeIf { it > 0L } ?: format.sizeBytes
                    _states.update { it + (item.key to RemoteDownloadState.Downloading(read, known)) }
                }
                val isCancelled = { item.key in cancelled }
                val downloaded = when (source.type) {
                    RemoteSourceType.OPDS ->
                        opdsClient.download(source, password, format.url, staged, isCancelled, onProgress)
                    RemoteSourceType.WEBDAV ->
                        webdavClient.download(source, password, format.url, staged, isCancelled, onProgress)
                }
                if (format.sizeBytes > 0L && source.type == RemoteSourceType.WEBDAV &&
                    downloaded.bytesWritten != format.sizeBytes
                ) {
                    throw RemoteException(RemoteErrorKind.INVALID_FILE, message = "Size mismatch")
                }
                fileName = RemoteBookFiles.resolveDownloadFileName(
                    contentDisposition = downloaded.contentDisposition,
                    responseMimeType = downloaded.contentType,
                    url = downloaded.finalUrl,
                    title = item.fileName ?: item.title,
                    expectedExtension = format.extension
                )
            }
            if (staged.length() <= 0L) throw RemoteException(RemoteErrorKind.INVALID_FILE)
            val extension = RemoteBookFiles.extensionFromName(fileName)
                ?: throw RemoteException(RemoteErrorKind.UNSUPPORTED_FORMAT)
            if (!RemoteFileSniffer.matches(staged, extension)) {
                throw RemoteException(RemoteErrorKind.INVALID_FILE, message = "Content does not match $extension")
            }
            _states.update { it + (item.key to RemoteDownloadState.Importing) }
            val bookFile = File(batch, fileName).also { target ->
                check(staged.renameTo(target)) { "Unable to stage download" }
            }
            val documentKey = RemoteBookFiles.remoteDocumentKey(source.id, item.key)
            val hash = runCatching { authorizedStorageManager.sha256(context, bookFile.absolutePath) }.getOrNull()
            val remembered = store.load().importedBooks[documentKey]
            pipeline.findExisting(context, remembered, documentKey, hash)?.let { existing ->
                store.recordImported(documentKey, existing.id)
                return RemoteDownloadState.Completed(existing.id, alreadyInLibrary = true)
            }
            // Catalog covers back up files with no embedded cover (PDF/TXT/MOBI, bare EPUBs). The
            // pipeline still prefers the cover inside the file and snapshots this one otherwise.
            val catalogCover = fetchCatalogCover(source, item, batch)
            val outcome = when (destination) {
                is RemoteDestination.AppStorage -> importIntoApp(item, bookFile, documentKey, destination, catalogCover)
                is RemoteDestination.AuthorizedFolder ->
                    importIntoFolder(item, bookFile, documentKey, destination, catalogCover)
            }
            val bookId = outcome.bookId
            return when {
                outcome.status == BookImportPipeline.Status.IMPORTED && bookId != null -> {
                    store.recordImported(documentKey, bookId)
                    RemoteDownloadState.Completed(bookId, alreadyInLibrary = false)
                }
                outcome.status == BookImportPipeline.Status.DUPLICATE && bookId != null -> {
                    store.recordImported(documentKey, bookId)
                    RemoteDownloadState.Completed(bookId, alreadyInLibrary = true)
                }
                outcome.status == BookImportPipeline.Status.UNSUPPORTED ->
                    RemoteDownloadState.Failed(RemoteErrorKind.UNSUPPORTED_FORMAT)
                else -> RemoteDownloadState.Failed(RemoteErrorKind.INVALID_FILE)
            }
        } finally {
            batch.deleteRecursively()
        }
    }

    private suspend fun fetchCatalogCover(source: RemoteSource, item: RemoteBookItem, batch: File): String? {
        val url = item.coverUrl ?: item.thumbnailUrl ?: return null
        if (source.type != RemoteSourceType.OPDS) return null
        return runCatching {
            val target = File(batch, "catalog_cover.img")
            opdsClient.download(source, store.password(source.id), url, target, { false }) { _, _ -> }
            target.takeIf { it.length() in 1..MAX_COVER_BYTES }?.absolutePath
        }.getOrNull()
    }

    private suspend fun importIntoApp(
        item: RemoteBookItem,
        bookFile: File,
        documentKey: String,
        destination: RemoteDestination.AppStorage,
        catalogCover: String?
    ): BookImportPipeline.Outcome {
        val document = BookImportPipeline.Document(
            uri = Uri.fromFile(bookFile),
            name = bookFile.name,
            sourceDocumentKey = documentKey,
            sourceLastModified = item.lastModified,
            sourceSize = bookFile.length(),
            localFile = bookFile,
            persistSourceUri = false,
            titleOverride = item.title.takeIf { item.fileName == null },
            authorOverride = item.authorLabel.takeIf(String::isNotBlank),
            fallbackCoverPath = catalogCover
        )
        return pipeline.import(
            context = context,
            documents = listOf(document),
            copyIntoApp = true,
            targetFolderId = destination.libraryFolderId,
            groupByAuthorizedSource = false
        ).outcomes.single()
    }

    /**
     * Writes the file into the authorized folder root, then imports it exactly like a book found
     * by scanning that folder: read in place and grouped under the folder's library entry.
     */
    private suspend fun importIntoFolder(
        item: RemoteBookItem,
        bookFile: File,
        documentKey: String,
        destination: RemoteDestination.AuthorizedFolder,
        catalogCover: String?
    ): BookImportPipeline.Outcome {
        val treeUri = Uri.parse(destination.treeUri)
        val hasGrant = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!hasGrant) throw RemoteException(RemoteErrorKind.DESTINATION_UNAVAILABLE)
        val rootUri = authorizedStorageManager.treeRootUri(treeUri)
        val fileName = uniqueChildName(treeUri, rootUri, bookFile.name)
        val moved = runCatching {
            authorizedStorageManager.copyThenDelete(
                context = context,
                sourceLocation = bookFile.absolutePath,
                targetParentUri = rootUri,
                fileName = fileName,
                deleteSource = false
            )
        }.getOrElse { error ->
            throw RemoteException(RemoteErrorKind.DESTINATION_UNAVAILABLE, message = "Write failed", cause = error)
        }
        val documentUri = authorizedStorageManager.documentUriUsingTree(treeUri, moved.destinationUri)
        val actualName = authorizedStorageManager.queryDisplayName(context, documentUri) ?: fileName
        val rootBinding = com.huangder.lumibooks.domain.repository.FolderRepository.StorageBinding(
            name = destination.name,
            treeUri = destination.treeUri,
            documentUri = rootUri.toString(),
            parentUri = null
        )
        val outcome = pipeline.import(
            context = context,
            documents = listOf(
                BookImportPipeline.Document(
                    uri = documentUri,
                    name = actualName,
                    sourceDirectoryUri = destination.treeUri,
                    sourceDirectoryName = destination.name,
                    sourceRelativeDirectory = null,
                    sourceDirectoryDocumentUri = rootUri.toString(),
                    sourceDocumentKey = authorizedStorageManager.documentKey(documentUri),
                    sourceLastModified = authorizedStorageManager.queryLastModified(context, documentUri),
                    sourceSize = bookFile.length(),
                    sourceDirectoryBindings = listOf(rootBinding),
                    titleOverride = item.title.takeIf { item.fileName == null },
                    authorOverride = item.authorLabel.takeIf(String::isNotBlank),
                    fallbackCoverPath = catalogCover
                )
            ),
            copyIntoApp = false,
            targetFolderId = null,
            groupByAuthorizedSource = true
        ).outcomes.single()
        if (outcome.status == BookImportPipeline.Status.IMPORTED && outcome.bookId != null) {
            store.recordImported(documentKey, outcome.bookId)
        } else {
            // Do not leave an orphan copy behind when the import itself failed.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri) }
        }
        return outcome
    }

    private fun uniqueChildName(treeUri: Uri, parentUri: Uri, requested: String): String {
        val existing = runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(parentUri)
            )
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0).orEmpty().lowercase()) }
            }.orEmpty()
        }.getOrDefault(emptySet())
        if (requested.lowercase() !in existing) return requested
        val base = requested.substringBeforeLast('.', requested)
        val extension = requested.substringAfterLast('.', "")
        return generateSequence(2) { it + 1 }
            .map { index -> if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension" }
            .first { it.lowercase() !in existing }
    }

    private companion object {
        const val TAG = "RemoteDownload"
        const val MAX_COVER_BYTES = 12L * 1024 * 1024
    }
}
