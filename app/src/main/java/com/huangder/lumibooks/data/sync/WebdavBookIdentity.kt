package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.json.JSONArray

/** Wire IDs may differ from installation-local UUIDs. Never rename local rows or preferences. */
internal fun matchWebdavBookIds(
    books: List<Book>,
    remote: Map<String, SyncFileEntry>,
    verifiedHashes: Map<String, String>,
    libraryKey: String
): Map<String, String> {
    val localIds = books.mapTo(mutableSetOf()) { it.id }
    val candidates = books.filter { it.id !in remote }
        .mapNotNull { book ->
            if (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey) return@mapNotNull null
            val associated = associatedWebdavBookId(book, remote, libraryKey)
            if (associated != null) return@mapNotNull book.id to associated
            if (book.format == BookFormat.MARKDOWN) return@mapNotNull null
            val hash = verifiedHashes[book.id]?.lowercase()
                ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: return@mapNotNull null
            val matches = remote.filterValues {
                it.sha256.equals(hash, ignoreCase = true) &&
                    (it.metadata?.format ?: it.fileName.substringAfterLast('.')).equals(book.format.name, ignoreCase = true)
            }.keys
            matches.singleOrNull()?.takeIf { it !in localIds }?.let { book.id to it }
        }
    // Ambiguous duplicate files must not silently collapse independent local records.
    return candidates.groupBy { it.second }.values.filter { it.size == 1 }
        .flatten().filter { it.second !in localIds }.toMap()
}

internal fun associatedWebdavBookId(book: Book, remote: Map<String, SyncFileEntry>, libraryKey: String): String? =
    if (book.remoteLibraryKey == libraryKey && book.remoteFileName != null) {
        remote.filterValues { it.fileName == book.remoteFileName }.keys.singleOrNull()
    } else null

internal fun Book.withWebdavAssociation(
    entry: SyncFileEntry, libraryKey: String, normalizeUnread: Boolean = true
): Book {
    // Opening an independently imported copy at its default position before the first sync
    // must not turn its newer open/import time into a request to reset the cloud position.
    val firstUnreadLink = normalizeUnread && remoteLibraryKey == null && readingProgress == 0f && !isManuallyMarkedRead
    return copy(
        lastReadTime = if (firstUnreadLink) 0L else lastReadTime,
        locatorJson = if (firstUnreadLink) null else locatorJson,
        remoteLibraryKey = libraryKey,
        remoteFileName = entry.fileName,
        remoteFileSize = entry.sizeBytes,
        remoteFileSha256 = entry.sha256
    )
}

internal fun PortableSnapshot.remapWebdavBookIds(ids: Map<String, String>): PortableSnapshot {
    if (ids.isEmpty()) return this
    fun bookId(id: String) = ids[id] ?: id
    return copy(
        preferences = preferences.map { preference ->
            val alias = ids.entries.firstOrNull { preference.key.endsWith("_${it.key}") }
            if (alias == null) preference else preference.copy(
                key = preference.key.removeSuffix(alias.key) + alias.value
            )
        },
        books = books.map { it.copy(id = bookId(it.id)) },
        folders = folders.map { folder -> folder.copy(previewBookIds = folder.previewBookIds?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                JSONArray((0 until array.length()).map { bookId(array.getString(it)) }).toString()
            }.getOrDefault(raw)
        }) },
        bookFolderLinks = bookFolderLinks.map { it.copy(bookId = bookId(it.bookId)) },
        bookTagLinks = bookTagLinks.map { it.copy(bookId = bookId(it.bookId)) },
        readingRecords = readingRecords.map { it.copy(bookId = bookId(it.bookId)) },
        bookmarks = bookmarks.map { it.copy(bookId = bookId(it.bookId)) },
        notes = notes.map { it.copy(bookId = bookId(it.bookId)) },
        journalEvents = journalEvents.map { it.copy(bookId = bookId(it.bookId)) },
        bookHighlightRuleStates = bookHighlightRuleStates.map { it.copy(bookId = bookId(it.bookId)) },
        bookHighlightSettings = bookHighlightSettings.map { it.copy(bookId = bookId(it.bookId)) },
        highlightRuleExclusions = highlightRuleExclusions.map { it.copy(bookId = bookId(it.bookId)) },
        tombstones = tombstones.map { tombstone ->
            val id = when (tombstone.namespace) {
                "book", "book_folder" -> bookId(tombstone.itemId)
                "book_tag" -> ids.entries.firstOrNull { tombstone.itemId.startsWith("${it.key}:") }
                    ?.let { it.value + tombstone.itemId.removePrefix(it.key) } ?: tombstone.itemId
                else -> tombstone.itemId
            }
            tombstone.copy(itemId = id)
        },
        assets = assets.map { asset ->
            if (asset.kind in setOf("book", "book_cover")) asset.copy(ownerId = bookId(asset.ownerId)) else asset
        }
    )
}

/** Old cloud rows must not overwrite a local row now mapped to a different content ID. */
internal fun PortableSnapshot.withoutWebdavBookIds(excluded: Set<String>): PortableSnapshot {
    if (excluded.isEmpty()) return this
    return copy(
        books = books.filter { it.id !in excluded },
        preferences = preferences.filter { preference -> excluded.none { preference.key.endsWith("_$it") } },
        folders = folders.map { folder -> folder.copy(previewBookIds = folder.previewBookIds?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                JSONArray((0 until array.length()).map { array.getString(it) }.filter { it !in excluded }).toString()
            }.getOrDefault(raw)
        }) },
        bookFolderLinks = bookFolderLinks.filter { it.bookId !in excluded },
        bookTagLinks = bookTagLinks.filter { it.bookId !in excluded },
        readingRecords = readingRecords.filter { it.bookId !in excluded },
        bookmarks = bookmarks.filter { it.bookId !in excluded },
        notes = notes.filter { it.bookId !in excluded },
        journalEvents = journalEvents.filter { it.bookId !in excluded },
        bookHighlightRuleStates = bookHighlightRuleStates.filter { it.bookId !in excluded },
        bookHighlightSettings = bookHighlightSettings.filter { it.bookId !in excluded },
        highlightRuleExclusions = highlightRuleExclusions.filter { it.bookId !in excluded },
        tombstones = tombstones.filter { tombstone -> when (tombstone.namespace) {
            "book", "book_folder" -> tombstone.itemId !in excluded
            "book_tag" -> excluded.none { tombstone.itemId.startsWith("$it:") }
            else -> true
        } },
        assets = assets.filter { it.kind !in setOf("book", "book_cover") || it.ownerId !in excluded }
    )
}
