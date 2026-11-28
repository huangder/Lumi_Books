package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.WebdavConfig
import org.junit.Assert.*
import org.junit.Test

class WebdavContentIdentityTest {
    private val identity = SyncBookIdentity("a".repeat(64), "EPUB", 42)
    private val wire = "sha256-${identity.sha256}"
    private fun book(id: String) = Book(id, "Title", "Author", "/$id.epub", null,
        BookFormat.EPUB, 300, 0f, createdAt = 300)
    private fun resolve(book: Book, remote: SyncManifest = SyncManifest(),
        remembered: Map<String, String> = emptyMap(), fingerprint: SyncBookIdentity = identity) =
        resolveWebdavContentIdentities(listOf(book), remote, mapOf(book.id to fingerprint), remembered, "library")

    @Test fun independentImportsShareIdentityWithoutAnyUploadedBook() {
        val first = resolve(book("phone-a"))
        val manifest = SyncManifest(bookIdentities = first.identities)
        val second = resolve(book("phone-b").copy(title = "Renamed", filePath = "/other/path.epub"), manifest)
        assertEquals(wire, first.wireIds["phone-a"])
        assertEquals(wire, second.wireIds["phone-b"])
        assertTrue(manifest.books.isEmpty())
        assertEquals(manifest, SyncManifest.fromJson(manifest.toJson()))
    }

    @Test fun oldProgressOnlyUuidIsBackfilledAndRecognizedOnAnotherDevice() {
        val legacy = SyncManifest(data = mapOf("old-local" to SyncFileEntry("old-local.json", "", 0, 100)))
        val upgraded = resolve(book("old-local"), legacy)
        assertEquals("old-local", upgraded.wireIds["old-local"])
        val other = resolve(book("other-local"), legacy.copy(bookIdentities = upgraded.identities))
        assertEquals("old-local", other.wireIds["other-local"])
        assertEquals(legacy.data, legacy.copy(bookIdentities = upgraded.identities).data)
        assertTrue(SyncManifest.fromJson("{\"version\":1}").bookIdentities.isEmpty())
    }

    @Test fun oldUploadedFileKeepsItsCloudUuid() {
        val legacy = SyncManifest(books = mapOf("cloud" to SyncFileEntry("cloud.epub", identity.sha256, 42, 100)))
        assertEquals("cloud", resolve(book("phone-b"), legacy).wireIds["phone-b"])
    }

    @Test fun legacyMarkdownPackHashIsNotMistakenForAChangedSourceDocument() {
        val legacy = SyncManifest(books = mapOf("old-local" to SyncFileEntry("old-local.mdpack", "b".repeat(64), 100, 1)))
        val result = resolve(book("old-local").copy(format = BookFormat.MARKDOWN), legacy,
            fingerprint = identity.copy(format = "MARKDOWN"))
        assertEquals("old-local", result.wireIds["old-local"])
    }

    @Test fun changedBytesWithoutASyncedBaselineInvalidateRememberedAliasAndSameLocalUuid() {
        val changed = identity.copy(sha256 = "b".repeat(64))
        val remote = SyncManifest(bookIdentities = mapOf("local" to identity, wire to identity))
        val result = resolve(book("local"), remote, mapOf("local" to wire), changed)
        assertEquals("sha256-${changed.sha256}", result.wireIds["local"])
        assertEquals(identity, result.identities[wire])
        assertEquals(identity, result.identities["local"])
    }

    @Test fun localEditKeepsTheWireIdAndWaitsForTheReplacementBody() {
        val changed = identity.copy(sha256 = "b".repeat(64), sizeBytes = 43)
        val remote = SyncManifest(bookIdentities = mapOf(wire to identity))
        val result = resolveWebdavContentIdentities(listOf(book("local")), remote, mapOf("local" to changed),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256))
        assertEquals(wire, result.wireIds["local"])
        assertEquals(mapOf("local" to changed), result.changedBodies)
        assertEquals(identity, result.identities[wire])
        assertEquals(mapOf("local" to identity.sha256), result.syncedHashes)
        assertTrue(result.staleBodies.isEmpty())
    }

    @Test fun cloudEditKeepsTheWireIdAndNeverPublishesTheStaleBody() {
        val replaced = identity.copy(sha256 = "b".repeat(64), sizeBytes = 43)
        val remote = SyncManifest(bookIdentities = mapOf(wire to replaced))
        val result = resolveWebdavContentIdentities(listOf(book("local")), remote, mapOf("local" to identity),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256))
        assertEquals(wire, result.wireIds["local"])
        assertEquals(setOf("local"), result.staleBodies)
        assertTrue(result.changedBodies.isEmpty())
        assertEquals(replaced, result.identities[wire])
        assertEquals(mapOf("local" to identity.sha256), result.syncedHashes)
    }

    @Test fun editsOnBothSidesForkInsteadOfOverwritingEitherCopy() {
        val mine = identity.copy(sha256 = "b".repeat(64))
        val theirs = identity.copy(sha256 = "c".repeat(64))
        val remote = SyncManifest(bookIdentities = mapOf(wire to theirs))
        val result = resolveWebdavContentIdentities(listOf(book("local")), remote, mapOf("local" to mine),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256))
        assertEquals("sha256-${mine.sha256}", result.wireIds["local"])
        assertTrue(result.changedBodies.isEmpty() && result.staleBodies.isEmpty())
        assertEquals(theirs, result.identities[wire])
    }

    @Test fun linkedRowSuppliesTheFirstBaselineOnlyForItsOwnLibraryAndNeverForMarkdown() {
        val changed = identity.copy(sha256 = "b".repeat(64))
        val remote = SyncManifest(books = mapOf(wire to SyncFileEntry("cloud.epub", identity.sha256, 42, 100)))
        val linked = book("local").copy(remoteLibraryKey = "library", remoteFileName = "cloud.epub",
            remoteFileSha256 = identity.sha256)
        assertEquals(wire, resolve(linked, remote, fingerprint = changed).wireIds["local"])
        assertEquals("sha256-${changed.sha256}",
            resolve(linked.copy(remoteLibraryKey = "other"), remote, fingerprint = changed).wireIds["local"])
        val markdown = SyncManifest(bookIdentities = mapOf(wire to identity.copy(format = "MARKDOWN")))
        val document = linked.copy(format = BookFormat.MARKDOWN)
        val edited = changed.copy(format = "MARKDOWN")
        assertEquals("sha256-${changed.sha256}", resolveWebdavContentIdentities(listOf(document), markdown,
            mapOf("local" to edited), mapOf("local" to wire), "library").wireIds["local"])
        assertEquals(wire, resolveWebdavContentIdentities(listOf(document), markdown, mapOf("local" to edited),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256)).wireIds["local"])
    }

    @Test fun unchangedBookSeedsItsBaselineAndAnEditNeverMergesIntoAnotherEntry() {
        val remote = SyncManifest(bookIdentities = mapOf(wire to identity))
        assertEquals(mapOf("local" to identity.sha256), resolve(book("local"), remote).syncedHashes)
        val elsewhere = identity.copy(sha256 = "b".repeat(64))
        val both = SyncManifest(bookIdentities = mapOf(wire to identity, "other" to elsewhere))
        val result = resolveWebdavContentIdentities(listOf(book("local")), both, mapOf("local" to elsewhere),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256))
        assertEquals(wire, result.wireIds["local"])
        assertEquals(mapOf("local" to elsewhere), result.changedBodies)
    }

    @Test fun deletedCloudCopyStaysDeletedAfterTheLocalBytesChange() {
        val changed = identity.copy(sha256 = "b".repeat(64))
        val remote = SyncManifest(deletedBooks = mapOf(wire to DeletedBookEntry(10, null, null, null)))
        val result = resolveWebdavContentIdentities(listOf(book("local")), remote, mapOf("local" to changed),
            mapOf("local" to wire), "library", syncedHashes = mapOf("local" to identity.sha256))
        assertEquals(wire, result.wireIds["local"])
        assertTrue(result.changedBodies.isEmpty())
        assertFalse(wire in result.identities)
    }

    @Test fun onlyUnambiguousTextCopiesAreProposedAsSuperseded() {
        fun text(id: String, name: String?) = Book(id, "Title", "Author", "/$id.txt", null, BookFormat.TXT, 0, 0f,
            createdAt = 1, remoteLibraryKey = "library", remoteFileName = name)
        fun copy(hex: Char, name: String) = text("sha256-" + hex.toString().repeat(64), name).copy(filePath = "", isCloudOnly = true)
        val owner = text("local", "Title - 0123456789abcdef.txt")
        val old = copy('a', "Title - fedcba9876543210.txt")
        val renamed = copy('b', "Title - 1111111111111111.txt").copy(title = "Renamed", author = "")
        val unrelated = copy('c', "Other - 2222222222222222.txt").copy(title = "Other")
        val legacy = old.copy(id = "legacy-uuid")
        val foreign = copy('d', "Title - 3333333333333333.txt").copy(remoteLibraryKey = "other")
        val epub = copy('e', "Title - 4444444444444444.epub").copy(format = BookFormat.EPUB)
        val books = listOf(owner, old, renamed, unrelated, legacy, foreign, epub)
        val wires = mapOf("local" to "sha256-" + "f".repeat(64))
        assertEquals(mapOf(old.id to "local", renamed.id to "local"), supersededCloudCopies(books, wires, "library"))
        // Two local books with the same name: never guess which one a copy belongs to.
        assertTrue(supersededCloudCopies(books + text("twin", "Title - 5555555555555555.txt"), wires, "library").isEmpty())
        // The copy the local book is itself linked to is its current version, not an old one.
        assertTrue(supersededCloudCopies(listOf(owner, old), mapOf("local" to old.id), "library").isEmpty())
    }

    @Test fun verifiedBodyCanUseAnExistingLegacyCloudPlaceholderWithoutCollapsingPhysicalCopies() {
        val placeholder = book("old-cloud").copy(isCloudOnly = true, filePath = "")
        val physical = book("imported")
        val remote = SyncManifest(bookIdentities = mapOf(placeholder.id to identity))
        val result = resolveWebdavContentIdentities(listOf(placeholder, physical), remote,
            mapOf(physical.id to identity), mapOf(placeholder.id to placeholder.id), "library")
        assertEquals(mapOf(physical.id to placeholder.id), result.wireIds)
        val otherLibrary = placeholder.copy(remoteLibraryKey = "other-library")
        val isolated = resolveWebdavContentIdentities(listOf(otherLibrary, physical), remote,
            mapOf(physical.id to identity), emptyMap(), "library")
        assertEquals(wire, isolated.wireIds[physical.id])
    }

    @Test fun duplicateLocalCopiesArePreservedAndDeletedAliasesSurviveMissingRows() {
        val books = listOf(book("one"), book("two"))
        val result = resolveWebdavContentIdentities(books, SyncManifest(),
            books.associate { it.id to identity }, mapOf("deleted-local" to "deleted-wire"), "library")
        assertEquals(mapOf("one" to "one", "two" to "two", "deleted-local" to "deleted-wire"), result.wireIds)
        val remote = SyncManifest(bookIdentities = mapOf(wire to identity))
        val matched = resolveWebdavContentIdentities(books, remote, books.associate { it.id to identity }, emptyMap(), "library")
        assertEquals(mapOf("one" to "one", "two" to "two"), matched.wireIds)
        val background = resolveWebdavContentIdentities(books, remote, mapOf("two" to identity), mapOf("one" to wire), "library")
        assertEquals(mapOf("one" to wire, "two" to "two"), background.wireIds)
    }

    @Test fun firstDefaultOpenDoesNotResetProgressButLaterRewindRemainsAnEvent() {
        val first = book("local").copy(locatorJson = "start").prepareFirstContentLink(false)
        assertEquals(0L, first.lastReadTime)
        assertNull(first.locatorJson)
        assertEquals("local", first.id)
        val rewind = first.copy(lastReadTime = 500, locatorJson = "start")
        assertEquals(rewind, rewind.prepareFirstContentLink(true))
        val fileLinked = rewind.prepareFirstContentLink(true).withWebdavAssociation(
            SyncFileEntry("cloud.epub", identity.sha256, 42, 100), "library", normalizeUnread = false
        )
        assertEquals(500L, fileLinked.lastReadTime)
        assertEquals("start", fileLinked.locatorJson)
    }

    @Test fun progressOnlyIsOptInAndDoesNotEnableBookmarksOrNotes() {
        val existing = WebdavConfig()
        assertTrue(existing.syncBookmarks)
        val progressOnly = existing.copy(syncReadingProgressOnly = true)
        assertTrue(progressOnly.syncReadingRecords)
        assertFalse(progressOnly.syncReadingDetails)
        assertFalse(progressOnly.syncBookmarks)
        assertFalse(progressOnly.syncNotes)
    }
}
