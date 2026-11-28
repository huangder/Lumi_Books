package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat

/**
 * Older clients published every edit of a text book as a new content ID and left the previous
 * one behind as a cloud-only copy. Nothing recorded which book it came from, so this only
 * proposes candidates (cloud-only copy to the local book that replaced it) for the user to confirm.
 */
internal fun supersededCloudCopies(
    books: List<Book>,
    wireIds: Map<String, String>,
    libraryKey: String
): Map<String, String> {
    val contentId = Regex("sha256-[0-9a-f]{64}")
    fun wire(book: Book) = wireIds[book.id] ?: book.id
    fun label(book: Book) = (book.title.trim().lowercase() to book.author.trim().lowercase())
        .takeIf { it.first.isNotEmpty() }
    // Uploaded names are "<stem> - <16 hex>.<ext>"; the stem survives every edit.
    fun stem(book: Book) = book.remoteFileName?.substringBeforeLast('.')
        ?.replace(Regex(" - [0-9a-f]{16}$"), "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    fun inLibrary(book: Book) = book.format == BookFormat.TXT && book.remoteFileName != null &&
        book.remoteLibraryKey == libraryKey
    val current = books.filter { inLibrary(it) && !it.isCloudOnly && it.filePath.isNotBlank() }
    return books.filter { inLibrary(it) && it.isCloudOnly && it.filePath.isBlank() && wire(it).matches(contentId) }
        .mapNotNull { copy ->
            val owner = current.singleOrNull { book ->
                wire(book) != wire(copy) &&
                    ((label(book) != null && label(book) == label(copy)) || (stem(book) != null && stem(book) == stem(copy)))
            }
            owner?.let { copy.id to it.id }
        }.toMap()
}
