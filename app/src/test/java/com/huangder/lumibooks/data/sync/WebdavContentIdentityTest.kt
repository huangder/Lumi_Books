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

    @Test fun changedBytesInvalidateRememberedAliasAndSameLocalUuid() {
        val changed = identity.copy(sha256 = "b".repeat(64))
        val remote = SyncManifest(bookIdentities = mapOf("local" to identity, wire to identity))
        val result = resolve(book("local"), remote, mapOf("local" to wire), changed)
        assertEquals("sha256-${changed.sha256}", result.wireIds["local"])
        assertEquals(identity, result.identities[wire])
        assertEquals(identity, result.identities["local"])
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
