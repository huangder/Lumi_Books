package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.FolderRepository
import com.huangder.lumibooks.util.AuthorizedFolderDirectorySnapshot
import com.huangder.lumibooks.util.authorizedSnapshotIdentity

/** Stable across different tree-qualified URIs that point at the same SAF document. */
internal fun authorizedDocumentIdentity(documentKey: String?, uri: String): String =
    authorizedSnapshotIdentity(documentKey, uri)

/**
 * Rebuilds the physical storage binding chain (root first, then every nested directory) for each
 * remembered directory path. Matches what a live scan used to produce for the same folder.
 */
internal fun authorizedStorageBindingsByPath(
    treeUri: String,
    directories: List<AuthorizedFolderDirectorySnapshot>
): Map<String, List<FolderRepository.StorageBinding>> {
    val root = directories.firstOrNull { it.relativePath == null } ?: return emptyMap()
    val rootBinding = root.toStorageBinding(treeUri)
    val byPath = directories.associateBy { it.relativePath.orEmpty() }
    return directories.associate { directory ->
        val path = directory.relativePath.orEmpty()
        val childBindings = path
            .split('/')
            .filter(String::isNotBlank)
            .runningFold("") { prefix, segment ->
                if (prefix.isBlank()) segment else "$prefix/$segment"
            }
            .drop(1)
            .mapNotNull { byPath[it] }
            .map { it.toStorageBinding(treeUri) }
        path to (listOf(rootBinding) + childBindings)
    }
}

private fun AuthorizedFolderDirectorySnapshot.toStorageBinding(
    treeUri: String
): FolderRepository.StorageBinding = FolderRepository.StorageBinding(
    name = name,
    treeUri = treeUri,
    documentUri = documentUri,
    parentUri = parentDocumentUri
)

/**
 * Matches in the required order. Hash matching is deliberately limited to a single record so
 * an existing duplicate set is never merged into an arbitrary book.
 */
internal fun findMatchingAuthorizedBook(
    documentKey: String?,
    uri: String,
    sha256: String?,
    books: List<Book>,
    claimedBookIds: Set<String> = emptySet()
): Book? {
    val available = books.filterNot { it.id in claimedBookIds }
    return documentKey
        ?.takeIf(String::isNotBlank)
        ?.let { key -> available.firstOrNull { it.sourceDocumentKey == key } }
        ?: available.firstOrNull { it.sourceUri == uri }
        ?: sha256
            ?.takeIf(String::isNotBlank)
            ?.let { hash -> available.filter { it.sourceSha256 == hash }.singleOrNull() }
}

/**
 * A matched SAF document can reuse its stored hash when the provider reports the same reliable
 * modification time. Providers that do not expose a timestamp (zero) are always revalidated.
 */
internal fun shouldRefreshAuthorizedHash(book: Book, documentLastModified: Long): Boolean =
    book.sourceSha256.isNullOrBlank() ||
        documentLastModified <= 0L ||
        book.sourceLastModified != documentLastModified

/** One-time lazy repair for books created before local source hashes were introduced. */
internal suspend fun backfillMissingSourceHashes(
    books: List<Book>,
    hashLocation: (String) -> String?,
    persist: suspend (bookId: String, sha256: String) -> Unit
): List<Book> = books.map { book ->
    if (!book.sourceSha256.isNullOrBlank() || book.isCloudOnly || book.filePath.isBlank()) {
        return@map book
    }
    val hash = hashLocation(book.filePath)?.takeIf(String::isNotBlank) ?: return@map book
    val updated = book.copy(sourceSha256 = hash)
    persist(book.id, hash)
    updated
}
