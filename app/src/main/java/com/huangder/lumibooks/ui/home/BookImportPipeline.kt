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
import com.huangder.lumibooks.util.parser.BookParserFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
        val fallbackCoverPath: String? = null
    )

    enum class Status { IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

    data class Outcome(val document: Document, val status: Status, val bookId: String? = null)

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
        groupByAuthorizedSource: Boolean
    ): Result {
        val outcomes = mutableListOf<Outcome>()
        var knownBooks = bookRepository.getAllBooks().first()
        var missingHashesChecked = false
        documents.forEach { document ->
            val extension = FileUtils.getFileExtension(document.name)
            if (extension !in SUPPORTED_BOOK_EXTENSIONS) {
                outcomes += Outcome(document, Status.UNSUPPORTED)
                return@forEach
            }
            val incomingKey = document.sourceDocumentKey
                ?: authorizedStorageManager.documentKey(document.uri)
            val incomingUri = document.uri.toString()
            val readableLocation = document.localFile?.absolutePath ?: incomingUri
            val incomingHash = runCatching {
                authorizedStorageManager.sha256(context, readableLocation)
            }.getOrNull()
            val directDuplicate = findMatchingAuthorizedBook(
                documentKey = incomingKey,
                uri = incomingUri,
                sha256 = null,
                books = knownBooks
            )
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
            if (duplicate != null) {
                outcomes += Outcome(document, Status.DUPLICATE, duplicate.id)
                return@forEach
            }
            var importedLocation: String? = null
            var extractedCoverPath: String? = null
            var insertedBook: Book? = null
            runCatching {
                val bookId = FileUtils.generateBookId()
                val format = extension.toBookFormat()
                if (format == BookFormat.MARKDOWN && document.localFile == null) {
                    runCatching { context.contentResolver.takePersistableUriPermission(document.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                val location = if (format == BookFormat.MARKDOWN) {
                    com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.create(context, bookId, readableLocation, document.sourceDirectoryDocumentUri).absolutePath
                } else if (copyIntoApp && document.localFile != null) {
                    FileUtils.moveFileToInternal(context, document.localFile, document.name)?.absolutePath
                        ?: error("Unable to move ${document.name}")
                } else if (copyIntoApp) {
                    FileUtils.copyFileToInternal(context, document.uri, document.name)?.absolutePath
                        ?: error("Unable to copy ${document.name}")
                } else {
                    document.uri.toString()
                }
                importedLocation = location
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
                val markdownMetadata = if (format == BookFormat.MARKDOWN) com.huangder.lumibooks.util.markdown.MarkdownSnapshotStore.manifest(File(location)) else null
                val book = Book(
                        id = bookId,
                        title = markdownMetadata?.optString("title")?.takeIf { it.isNotBlank() }
                            ?: document.titleOverride?.takeIf { it.isNotBlank() }
                            ?: document.name.substringBeforeLast('.'),
                        author = markdownMetadata?.optString("author")?.takeIf { it.isNotBlank() }
                            ?: document.authorOverride?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.book_author_unknown),
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
                bookRepository.insertBook(book)
                insertedBook = book
                val destinationFolderId = if (groupByAuthorizedSource) {
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
                insertedBook?.let { knownBooks = knownBooks + it }
            }.onFailure {
                outcomes += Outcome(document, Status.FAILED)
                insertedBook?.let { book -> runCatching { bookRepository.deleteBook(book) } }
                if (copyIntoApp || extension in setOf("md", "markdown")) {
                    importedLocation?.let { FileUtils.deleteAppManagedBookFile(context, it) }
                }
                FileUtils.deleteAppOwnedFile(context, extractedCoverPath)
            }
        }
        return Result(outcomes)
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
        val SUPPORTED_BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "mobi", "cbz", "md", "markdown")
        private const val NO_URI = "lumibooks:no-uri"

        fun String.toBookFormat(): BookFormat = when (this) {
            "epub" -> BookFormat.EPUB
            "pdf" -> BookFormat.PDF
            "mobi" -> BookFormat.MOBI
            "cbz" -> BookFormat.CBZ
            "md", "markdown", "mdpack" -> BookFormat.MARKDOWN
            else -> BookFormat.TXT
        }
    }
}
