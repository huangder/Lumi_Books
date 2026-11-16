package com.huangder.lumibooks.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.FolderNameValidator
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.FolderRepository
import com.huangder.lumibooks.util.AuthorizedStorageManager
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore
import com.huangder.lumibooks.util.parser.BookFilenameMetadata
import com.huangder.lumibooks.util.parser.BookNameMetadata
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.parser.readEmbeddedBookMetadata
import com.huangder.lumibooks.util.word.WordEpubCache
import com.huangder.lumibooks.util.word.WordException
import com.huangder.lumibooks.util.word.userMessage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place that turns documents into library books: duplicate detection, copying or
 * referencing the file, cover extraction, the database insert and folder placement.
 * Callers run it off the main thread.
 */
@Singleton
class BookImportPipeline @Inject constructor(
    private val bookRepository: BookRepository,
    private val folderRepository: FolderRepository,
    private val authorizedStorageManager: AuthorizedStorageManager
) {
    private val importMutex = Mutex()
    data class Document(
        val uri: Uri,
        val name: String,
        val sourceDirectoryUri: String? = null,
        val sourceDirectoryName: String? = null,
        val sourceRelativeDirectory: String? = null,
        val sourceDirectoryDocumentUri: String? = null,
        val sourceDocumentKey: String? = null,
        val sourceLastModified: Long = 0L,
        val sourceSize: Long = 0L,
        val sourceDirectoryBindings: List<FolderRepository.StorageBinding> = emptyList(),
        /** A downloaded file in app-private storage; moved instead of copied into the library. */
        val localFile: File? = null,
        /** False for transient sources (remote downloads) whose URI must not be kept. */
        val persistSourceUri: Boolean = true,
        val titleOverride: String? = null,
        val authorOverride: String? = null,
        /** App-private cover snapshot used when the file itself has no extractable cover. */
        val fallbackCoverPath: String? = null,
        /** The original download name when a destination adds a collision suffix. */
        val metadataFileName: String = name
    )

    enum class Status { IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

    data class Outcome(val document: Document, val status: Status, val bookId: String? = null, val failureMessage: String? = null)

    data class Result(val outcomes: List<Outcome>) {
        val imported: Int get() = outcomes.count { it.status == Status.IMPORTED }
        val skipped: Int get() = outcomes.count { it.status == Status.DUPLICATE || it.status == Status.UNSUPPORTED }
        val failed: Int get() = outcomes.count { it.status == Status.FAILED }
    }

    suspend fun import(
        context: Context,
        documents: List<Document>,
        copyIntoApp: Boolean,
        targetFolderId: String?,
        groupByAuthorizedSource: Boolean,
        inheritHighlightSwitches: Boolean = false
    ): Result = importMutex.withLock {
        val outcomes = mutableListOf<Outcome>()
        var knownBooks = bookRepository.getAllBooks().first()
        var missingHashesChecked = false
        documents.forEach { document ->
            currentCoroutineContext().ensureActive()
            val extension = FileUtils.getFileExtension(document.name)
            val format = BookFormat.fromExtension(extension)?.takeIf { extension in BookFormat.importExtensions }
            if (format == null) {
                outcomes += Outcome(document, Status.UNSUPPORTED)
                return@forEach
            }
            val incomingKey = document.sourceDocumentKey
                ?: authorizedStorageManager.documentKey(document.uri)
            val incomingUri = document.uri.toString()
            val readableLocation = document.localFile?.absolutePath ?: incomingUri
            val directDuplicate = findMatchingAuthorizedBook(
                documentKey = incomingKey,
                uri = incomingUri,
                sha256 = null,
                books = knownBooks
            )
            if (format.isWord && directDuplicate != null && !directDuplicate.isCloudOnly) {
                outcomes += Outcome(document, Status.DUPLICATE, directDuplicate.id)
                return@forEach
            }
            val incomingHash = if (format.isWord) {
                try {
                    runInterruptible { WordEpubCache.sourceHash(context, readableLocation) }
                } catch (error: WordException) {
                    outcomes += Outcome(document, Status.FAILED, failureMessage = error.userMessage(context))
                    return@forEach
                }
            } else {
                runCatching { authorizedStorageManager.sha256(context, readableLocation) }.getOrNull()
            }
            if (directDuplicate == null && incomingHash != null && !missingHashesChecked) {
                knownBooks = repairMissingSourceHashes(context, knownBooks)
                missingHashesChecked = true
            }
            val duplicate = directDuplicate ?: findMatchingAuthorizedBook(
                documentKey = incomingKey,
                uri = incomingUri,
                sha256 = incomingHash,
                books = knownBooks
            )
            val cloudBook = if (duplicate == null || duplicate.isCloudOnly) {
                findCloudBookForImport(incomingHash, format, knownBooks)
                    ?.takeIf { duplicate == null || it.id == duplicate.id }
            } else null
            if (duplicate != null && cloudBook == null) {
                outcomes += Outcome(document, Status.DUPLICATE, duplicate.id)
                return@forEach
            }
            var importedLocation: String? = null
            var extractedCoverPath: String? = null
            var insertedBook: Book? = null
            runCatching {
                val bookId = cloudBook?.id ?: FileUtils.generateBookId()
                if (format == BookFormat.MARKDOWN && document.localFile == null) {
                    runCatching { context.contentResolver.takePersistableUriPermission(document.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                val markdownSnapshot = if (format == BookFormat.MARKDOWN) {
                    MarkdownSnapshotStore.createWithMetadata(context, bookId, readableLocation, document.sourceDirectoryDocumentUri)
                } else null
                val location = when {
                    markdownSnapshot != null -> markdownSnapshot.file.absolutePath
                    copyIntoApp && document.localFile != null ->
                        FileUtils.moveFileToInternal(context, document.localFile, document.name)?.absolutePath
                        ?: error("Unable to move ${document.name}")
                    copyIntoApp -> FileUtils.copyFileToInternal(context, document.uri, document.name)?.absolutePath
                        ?: error("Unable to copy ${document.name}")
                    else -> incomingUri
                }
                importedLocation = location
                val embeddedMetadata = when {
                    markdownSnapshot != null -> markdownSnapshot.metadata
                    format.isWord -> runInterruptible {
                        val word = WordEpubCache.metadata(context, location, extension, checkNotNull(incomingHash))
                        BookNameMetadata(word.title, word.author)
                    }
                    format == BookFormat.TXT -> BookNameMetadata()
                    else -> readEmbeddedBookMetadata(context, location, format) ?: BookNameMetadata()
                }
                val metadata = embeddedMetadata
                    .withFallback(BookNameMetadata(document.titleOverride, document.authorOverride))
                    .withFallback(BookFilenameMetadata.read(document.metadataFileName))
                val parser = BookParserFactory.createParser(format, context)
                val coverPath = try {
                    parser.extractCoverPath(location)
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { parser.close() }
                } ?: document.fallbackCoverPath?.let { snapshotCover(context, it) }
                extractedCoverPath = coverPath
                val now = System.currentTimeMillis()
                val markdownMetadata = markdownSnapshot?.file?.let(MarkdownSnapshotStore::manifest)
                val importedBook = Book(
                        id = bookId,
                        title = metadata.title ?: BookFilenameMetadata.stem(document.name),
                        author = metadata.author ?: context.getString(R.string.book_author_unknown),
                        filePath = location,
                        coverPath = coverPath,
                        format = format,
                        lastReadTime = now,
                        readingProgress = 0f,
                        createdAt = now,
                        sourceUri = incomingUri.takeIf { document.persistSourceUri },
                        sourceDocumentKey = incomingKey,
                        sourceParentUri = document.sourceDirectoryDocumentUri,
                        sourceSha256 = markdownMetadata?.optString("sourceSha256")?.takeIf { it.isNotBlank() } ?: incomingHash,
                        sourceDisplayName = document.name,
                        sourceLastModified = document.sourceLastModified,
                        isMissing = false
                    )
                val book = cloudBook?.let { attachImportedCloudBook(it, importedBook) } ?: importedBook
                insertedBook = book
                when {
                    cloudBook != null -> bookRepository.updateBook(book)
                    inheritHighlightSwitches -> bookRepository.insertExternalBook(book)
                    else -> bookRepository.insertBook(book)
                }
                val destinationFolderId = if (cloudBook != null) {
                    null // Keep existing cloud organization when attaching its local body.
                } else if (groupByAuthorizedSource) {
                    document.sourceDirectoryName
                        ?.let(FolderNameValidator::clean)
                        ?.take(FolderNameValidator.MAX_LENGTH)
                        ?.takeIf(FolderNameValidator::isValid)
                        ?.let {
                            folderRepository.getOrCreateFolderPath(
                                rootName = it,
                                relativeDirectory = document.sourceRelativeDirectory,
                                storageBindings = document.sourceDirectoryBindings
                            ).id
                        }
                } else {
                    targetFolderId
                }
                if (destinationFolderId != null) {
                    folderRepository.moveBooks(setOf(bookId), destinationFolderId)
                }
            }.onSuccess {
                outcomes += Outcome(document, Status.IMPORTED, insertedBook?.id)
                insertedBook?.let { updated -> knownBooks = knownBooks.filterNot { it.id == updated.id } + updated }
            }.onFailure { error ->
                withContext(NonCancellable) {
                    insertedBook?.let { book -> runCatching {
                        if (cloudBook != null) bookRepository.updateBook(cloudBook) else bookRepository.deleteBook(book)
                    } }
                    if (copyIntoApp || format == BookFormat.MARKDOWN) {
                        importedLocation?.let { FileUtils.deleteAppManagedBookFile(context, it) }
                    }
                    FileUtils.deleteAppOwnedFile(context, extractedCoverPath)
                }
                if (error is CancellationException) throw error
                outcomes += Outcome(document, Status.FAILED, failureMessage = (error as? WordException)?.userMessage(context))
            }
        }
        Result(outcomes)
    }

    /**
     * The library book a download would duplicate: by the remembered remote import, the document
     * key, or (after the one-time hash backfill) identical content.
     */
    suspend fun findExisting(
        context: Context,
        rememberedBookId: String?,
        documentKey: String?,
        sha256: String?
    ): Book? {
        rememberedBookId?.let { id -> bookRepository.getBookById(id)?.let { return it } }
        var books = bookRepository.getAllBooks().first()
        findMatchingAuthorizedBook(documentKey, NO_URI, sha256 = null, books = books)?.let { return it }
        if (sha256.isNullOrBlank()) return null
        books = repairMissingSourceHashes(context, books)
        return findMatchingAuthorizedBook(documentKey, NO_URI, sha256 = sha256, books = books)
    }

    suspend fun repairMissingSourceHashes(
        context: Context,
        books: List<Book>
    ): List<Book> = backfillMissingSourceHashes(
        books = books,
        hashLocation = { location ->
            runCatching { authorizedStorageManager.sha256(context, location) }.getOrNull()
        },
        persist = bookRepository::updateSourceSha256
    )

    /** Copies a cache cover into the persistent covers directory so it survives cache clears. */
    private fun snapshotCover(context: Context, source: String): String? = runCatching {
        val file = File(source)
        if (!file.isFile || file.length() <= 0L) return null
        val target = File(FileUtils.getCoversDirectory(context), "remote_${FileUtils.generateBookId()}.jpg")
        file.copyTo(target, overwrite = true)
        target.absolutePath
    }.getOrNull()

    companion object {
        private const val NO_URI = "lumibooks:no-uri"
    }
}
