package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book

/** Stable across different tree-qualified URIs that point at the same SAF document. */
internal fun authorizedDocumentIdentity(documentKey: String?, uri: String): String =
    documentKey?.takeIf(String::isNotBlank)?.let { "document:$it" } ?: "uri:$uri"

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
