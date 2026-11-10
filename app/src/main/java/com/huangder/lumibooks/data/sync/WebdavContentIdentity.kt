package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book

internal data class WebdavContentIdentityResolution(
    val wireIds: Map<String, String>,
    val identities: Map<String, SyncBookIdentity>
)

/** Keep legacy cloud UUIDs when verified; new books get a device-independent wire ID. */
internal fun resolveWebdavContentIdentities(
    books: List<Book>,
    remote: SyncManifest,
    verified: Map<String, SyncBookIdentity>,
    remembered: Map<String, String>,
    libraryKey: String
): WebdavContentIdentityResolution {
    val known = remote.books.mapNotNull { (id, entry) ->
        // Markdown downloads are resource packs, not the source document being identified.
        if (entry.metadata?.format.equals("MARKDOWN", true) || entry.fileName.endsWith(".mdpack", true)) {
            return@mapNotNull null
        }
        entry.sha256.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.let {
            id to SyncBookIdentity(it.lowercase(), entry.metadata?.format
                ?: entry.fileName.substringAfterLast('.').uppercase(), entry.sizeBytes)
        }
    }.toMap() + remote.bookIdentities
    val identities = known.toMutableMap()
    val result = remembered.toMutableMap()
    val localIds = books.mapTo(mutableSetOf()) { it.id }
    val localBooks = books.associateBy { it.id }
    val localBodyIds = books.filterNot { it.isCloudOnly }.mapTo(mutableSetOf()) { it.id }
    val duplicates = verified.values.groupingBy { it.sha256.lowercase() }.eachCount()
    fun SyncBookIdentity.matches(other: SyncBookIdentity): Boolean =
        sha256.equals(other.sha256, true) && format.equals(other.format, true)

    for (book in books) {
        val identity = verified[book.id]
        if (identity == null) {
            // Missing/cloud-only bodies may retain a previously established association.
            val alias = remembered[book.id] ?: associatedWebdavBookId(book, remote.books, libraryKey)
            if (alias != null && (alias == book.id || alias !in localIds)) result[book.id] = alias
            continue
        }
        val previous = remembered[book.id]
        val canonical = "sha256-${identity.sha256.lowercase()}"
        fun available(id: String): Boolean = (id == book.id || id !in localIds ||
            localBooks[id]?.let { it.isCloudOnly && it.format == book.format &&
                (it.remoteLibraryKey == null || it.remoteLibraryKey == libraryKey) } == true) &&
            result.none { (local, wire) -> local != book.id && local in localBodyIds && wire == id }
        val matches = known.filterValues { it.matches(identity) }.keys
            .filter { it !in remote.deletedBooks && available(it) }
        val legacyLocal = book.id.takeIf {
            it !in remote.deletedBooks && known[it] == null && (it in remote.data || it in remote.books)
        }
        val wireId = when {
            duplicates[identity.sha256.lowercase()] != 1 ->
                previous?.takeIf { it in matches } ?: book.id
            previous in matches -> previous!!
            book.id in matches -> book.id
            canonical in matches -> canonical
            matches.size == 1 -> matches.single()
            legacyLocal != null -> legacyLocal
            available(canonical) -> canonical
            else -> book.id
        }
        result[book.id] = wireId
        identities[wireId] = identity
    }
    // A verified local body owns the alias; a pre-existing cloud placeholder keeps its row.
    val bodyTargets = result.filterKeys { it in localBodyIds }.values.toSet()
    result.keys.removeAll { it !in localBodyIds && it in localIds && it in bodyTargets }
    return WebdavContentIdentityResolution(result, identities)
}

/** A first default-position open is not an intentional rewind of a previously linked book. */
internal fun Book.prepareFirstContentLink(alreadyLinked: Boolean): Book =
    if (!alreadyLinked && remoteLibraryKey == null && readingProgress == 0f && !isManuallyMarkedRead) {
        copy(lastReadTime = 0L, locatorJson = null)
    } else this
