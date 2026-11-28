package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book

internal data class WebdavContentIdentityResolution(
    val wireIds: Map<String, String>,
    val identities: Map<String, SyncBookIdentity>,
    val duplicateRows: Map<String, String> = emptyMap(),
    val duplicateWireIds: Map<String, String> = emptyMap(),
    /** Local books edited since their last sync: the new body replaces the same wire ID. */
    val changedBodies: Map<String, SyncBookIdentity> = emptyMap(),
    /** Local books whose unedited body was replaced in the cloud by another device. */
    val staleBodies: Set<String> = emptySet(),
    /** Local book ID to the hash last confirmed equal to its cloud copy. */
    val syncedHashes: Map<String, String> = emptyMap()
)

/** Keep legacy cloud UUIDs when verified; new books get a device-independent wire ID. */
internal fun resolveWebdavContentIdentities(
    books: List<Book>,
    remote: SyncManifest,
    verified: Map<String, SyncBookIdentity>,
    remembered: Map<String, String>,
    libraryKey: String,
    missingBodies: Set<String> = emptySet(),
    syncedHashes: Map<String, String> = emptyMap()
): WebdavContentIdentityResolution {
    val known = remote.bookIdentities + remote.books.mapNotNull { (id, entry) ->
        // Markdown downloads are resource packs, not the source document being identified.
        if (entry.metadata?.format.equals("MARKDOWN", true) || entry.fileName.endsWith(".mdpack", true)) {
            return@mapNotNull null
        }
        entry.sha256.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.let {
            id to SyncBookIdentity(it.lowercase(), entry.metadata?.format
                ?: entry.fileName.substringAfterLast('.').uppercase(), entry.sizeBytes)
        }
    }.toMap()
    val identities = known.toMutableMap()
    val result = mutableMapOf<String, String>()
    val localIds = books.mapTo(mutableSetOf()) { it.id }
    val localBooks = books.associateBy { it.id }
    val localBodyIds = books.filter { !it.isCloudOnly || it.filePath.isNotBlank() }.mapTo(mutableSetOf()) { it.id }
    val duplicates = verified.values.groupingBy { it.copy(sha256 = it.sha256.lowercase(), format = it.format.uppercase()) }.eachCount()
    fun SyncBookIdentity.matches(other: SyncBookIdentity): Boolean =
        sizeBytes > 0 && sizeBytes == other.sizeBytes &&
            sha256.equals(other.sha256, true) && format.equals(other.format, true)
    val reserved = remembered.filterKeys { it in localBodyIds && it !in verified }
    val changed = mutableMapOf<String, SyncBookIdentity>()
    val stale = mutableSetOf<String>()
    val baselines = syncedHashes.filterKeys { it in localBodyIds && it !in verified }.toMutableMap()

    for (book in books.sortedWith(compareBy<Book> { it.id !in verified }.thenBy { it.id })) {
        val identity = verified[book.id]
        if (identity == null) {
            continue
        }
        val previous = remembered[book.id]
        if (previous != null && previous in remote.deletedBooks) {
            // A cloud deletion stays in force even after the bytes change; never re-upload
            // the book under a fresh ID behind the user's back.
            result[book.id] = previous
            continue
        }
        val canonical = "sha256-${identity.sha256.lowercase()}"
        fun available(id: String): Boolean = (id == book.id || id !in localIds ||
            localBooks[id]?.let { it.isCloudOnly && it.filePath.isBlank() && it.format == book.format &&
                (it.remoteLibraryKey == null || it.remoteLibraryKey == libraryKey) } == true) &&
            (reserved + result).none { (local, wire) -> local != book.id && local in localBodyIds && wire == id } &&
            remembered.none { (local, wire) -> local != book.id && local in localBodyIds && wire == id }
        val matches = known.filterValues { it.matches(identity) }.keys
            .filter { it !in remote.deletedBooks && available(it) }
        val legacyLocal = book.id.takeIf {
            it !in remote.deletedBooks && known[it] == null && (it in remote.data || it in remote.books)
        }
        // An edit keeps the wire ID. Compare the local bytes and the cloud identity with the
        // hash both last agreed on: exactly one side moving is an edit, both moving is a fork.
        val anchor = (previous ?: associatedWebdavBookId(book, remote.books, libraryKey) ?: book.id)
            .takeIf { it !in remote.deletedBooks && available(it) }
        val cloud = anchor?.let(known::get)
        // Before the first recorded baseline, trust the row only while it still names the
        // very file the anchor publishes; anything looser could tie two different books together.
        val base = syncedHashes[book.id] ?: book.remoteFileSha256?.takeIf {
            it.isNotBlank() && book.remoteLibraryKey == libraryKey && book.format.name != "MARKDOWN" &&
                anchor != null && remote.books[anchor]?.fileName == book.remoteFileName
        }
        val diverged = cloud != null && base != null && identity.sizeBytes > 0 &&
            !cloud.sha256.equals(identity.sha256, true) && cloud.format.equals(identity.format, true)
        val localEdit = diverged && cloud!!.sha256.equals(base, true)
        val remoteEdit = diverged && !localEdit && identity.sha256.equals(base, true)
        val wireId = when {
            localEdit || remoteEdit -> anchor!!
            duplicates[identity.copy(sha256 = identity.sha256.lowercase(), format = identity.format.uppercase())] != 1 ->
                previous?.takeIf { it in matches } ?: book.id
            previous in matches -> previous!!
            book.id in matches -> book.id
            canonical in matches -> canonical
            matches.isNotEmpty() -> matches.minOrNull()!!
            legacyLocal != null -> legacyLocal
            available(canonical) && (known[canonical] == null || canonical in matches) -> canonical
            else -> book.id
        }
        result[book.id] = wireId
        when {
            // The cloud identity only moves once the replacement body is published.
            localEdit -> { changed[book.id] = identity; baselines[book.id] = base!! }
            remoteEdit -> { stale += book.id; baselines[book.id] = base!! }
            else -> {
                if (known[wireId]?.matches(identity) == true) baselines[book.id] = identity.sha256.lowercase()
                identities[wireId] = identity
            }
        }
    }
    val bodyOwners = result.entries.associate { (local, wire) -> wire to local }
    fun matchingBody(signature: SyncBookIdentity?): String? = signature?.let { candidate ->
        verified.filterValues { it.matches(candidate) }.keys.singleOrNull()
    }
    val duplicateRows = mutableMapOf<String, String>()
    val duplicateWires = mutableMapOf<String, String>()
    fun canFoldWire(id: String) = id !in remote.books || id in missingBodies
    for ((id, signature) in known) {
        val rowLibrary = localBooks[id]?.remoteLibraryKey
        if (id in localBodyIds || id in bodyOwners || id in remote.deletedBooks ||
            id in reserved.values || !canFoldWire(id) || (rowLibrary != null && rowLibrary != libraryKey)) continue
        matchingBody(signature)?.let { owner -> duplicateWires[id] = owner }
    }
    for (book in books) {
        if (book.id in verified || (book.remoteLibraryKey != null && book.remoteLibraryKey != libraryKey)) continue
        val associated = associatedWebdavBookId(book, remote.books, libraryKey)
        val alias = remembered[book.id] ?: associated ?: book.id
        if (book.isCloudOnly && book.filePath.isBlank()) {
            val signature = book.remoteFileSha256?.takeIf { book.format.name != "MARKDOWN" }
                ?.let { SyncBookIdentity(it, book.format.name, book.remoteFileSize) }
            val owner = bodyOwners[alias] ?: duplicateWires[alias] ?: matchingBody(signature)?.takeIf {
                canFoldWire(alias) && alias !in remote.deletedBooks && alias !in reserved.values &&
                    (known[alias] == null || known.getValue(alias).matches(requireNotNull(signature)))
            }
            if (owner != null && owner != book.id) {
                duplicateRows[book.id] = owner
                if (alias !in bodyOwners && alias !in localBodyIds) duplicateWires[alias] = owner
                continue
            }
        }
        if (alias !in bodyOwners && alias !in result.values && (alias == book.id || alias !in localIds)) {
            result[book.id] = alias
        }
    }
    return WebdavContentIdentityResolution(remembered.filterKeys { it !in localIds } + result,
        identities, duplicateRows, duplicateWires, changed, stale, baselines)
}

/** A first default-position open is not an intentional rewind of a previously linked book. */
internal fun Book.prepareFirstContentLink(alreadyLinked: Boolean): Book =
    if (!alreadyLinked && remoteLibraryKey == null && readingProgress == 0f && !isManuallyMarkedRead) {
        copy(lastReadTime = 0L, locatorJson = null)
    } else this
