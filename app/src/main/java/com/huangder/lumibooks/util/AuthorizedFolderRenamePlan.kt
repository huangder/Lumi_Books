package com.huangder.lumibooks.util

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder

/** Match a renamed subtree by its unchanged relative paths, never by opaque document IDs. */
class AuthorizedFolderRenamePlan(
    before: AuthorizedStorageManager.ScanResult,
    after: AuthorizedStorageManager.ScanResult
) {
    private val directories = after.directories.associateBy { it.relativePath.orEmpty() }.let { current ->
        before.directories.associate { old ->
            old.uri.toString() to requireNotNull(current[old.relativePath.orEmpty()]) {
                "Directory missing after rename: ${old.relativePath}"
            }
        }
    }
    private val documents = after.documents.associateBy { it.relativeDirectory.orEmpty() to it.name }.let { current ->
        before.documents.associate { old ->
            old.uri.toString() to requireNotNull(current[old.relativeDirectory.orEmpty() to old.name]) {
                "Book missing after rename: ${old.name}"
            }
        }
    }
    private val documentKeys = before.documents.mapNotNull { old ->
        old.documentKey?.let { it to documents.getValue(old.uri.toString()) }
    }.toMap()

    fun rebind(folder: LibraryFolder): LibraryFolder {
        val directory = directories[folder.storageDocumentUri] ?: return folder
        // The scanned subtree root has no scanned parent, but keeps its physical parent.
        val parentUri = directory.parentUri?.toString()
            ?: directories[folder.storageParentUri]?.uri?.toString() ?: folder.storageParentUri
        return folder.copy(
            name = directory.name,
            storageDocumentUri = directory.uri.toString(),
            storageParentUri = parentUri,
            storageMissing = false
        )
    }

    fun rebind(book: Book): Book {
        val file = documents[book.filePath]
        val source = documents[book.sourceUri]
        val document = file ?: source ?: documentKeys[book.sourceDocumentKey] ?: return book
        return book.copy(
            // Local copies and generated Markdown EPUBs keep their app-managed file paths.
            filePath = file?.uri?.toString() ?: book.filePath,
            sourceUri = source?.uri?.toString() ?: book.sourceUri,
            sourceDocumentKey = document.documentKey,
            sourceParentUri = document.parentUri.toString(),
            isMissing = if (file != null) false else book.isMissing
        )
    }
}
