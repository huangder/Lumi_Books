package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableAsset
import com.huangder.lumibooks.data.backup.PortableBook
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotMerger
import com.huangder.lumibooks.data.local.entity.*
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.*
import org.junit.Test

class WebdavBookIdentityTest {
    private val hash = "a".repeat(64)
    private val library = "library"
    private val remote = mapOf("wire" to SyncFileEntry("wire.epub", hash, 42, 10))

    @Test fun identicalBytesMatchAcrossDeviceLocalIds() {
        assertEquals(mapOf("phone-b" to "wire"), matchWebdavBookIds(
            listOf(book("phone-b")), remote, mapOf("phone-b" to hash.uppercase()), library
        ))
    }

    @Test fun openingAtDefaultPositionBeforeFirstLinkDoesNotResetCloudProgressButLaterRewindDoes() {
        val openedCopy = book("phone-b").copy(createdAt = 100, lastReadTime = 200, locatorJson = "start")
        val linked = openedCopy.withWebdavAssociation(remote.getValue("wire"), library)
        assertEquals(0L, linked.lastReadTime)
        assertNull(linked.locatorJson)
        assertEquals("phone-b", linked.id)
        val rewind = linked.copy(lastReadTime = 300, locatorJson = "start")
        assertEquals(rewind, rewind.withWebdavAssociation(remote.getValue("wire"), library))
        val partlyRead = openedCopy.copy(readingProgress = .4f)
            .withWebdavAssociation(remote.getValue("wire"), library)
        assertEquals(200L, partlyRead.lastReadTime)
        assertEquals(.4f, partlyRead.readingProgress, 0f)
    }

    @Test fun ambiguousLocalOrRemoteCopiesAndExistingTargetAreNotCollapsed() {
        assertTrue(matchWebdavBookIds(listOf(book("one"), book("two")), remote,
            mapOf("one" to hash, "two" to hash), library).isEmpty())
        assertTrue(matchWebdavBookIds(listOf(book("one")), remote + ("other" to remote.getValue("wire").copy(fileName = "other.epub")),
            mapOf("one" to hash), library).isEmpty())
        assertTrue(matchWebdavBookIds(listOf(book("one"), book("wire")), remote,
            mapOf("one" to hash), library).isEmpty())
    }

    @Test fun mismatchedFormatMarkdownInvalidHashAndOtherLibraryAreExcluded() {
        for (candidate in listOf(book("local").copy(format = BookFormat.PDF),
            book("local").copy(format = BookFormat.MARKDOWN), book("local").copy(remoteLibraryKey = "other"))) {
            assertTrue(matchWebdavBookIds(listOf(candidate), remote, mapOf("local" to hash), library).isEmpty())
        }
        assertTrue(matchWebdavBookIds(listOf(book("local")), remote, mapOf("local" to "invalid"), library).isEmpty())
    }

    @Test fun provenAssociationSurvivesChangedLocalBytesButNeverCrossesLibraries() {
        val associated = book("local").copy(remoteLibraryKey = library, remoteFileName = "wire.epub")
        assertEquals("wire", associatedWebdavBookId(associated, remote, library))
        assertEquals(mapOf("local" to "wire"), matchWebdavBookIds(listOf(associated), remote,
            mapOf("local" to "b".repeat(64)), library))
        assertNull(associatedWebdavBookId(associated, remote, "different"))
        assertNull(associatedWebdavBookId(associated, remote + ("second" to remote.getValue("wire")), library))
    }

    @Test fun remappingRoundTripsEveryBookReferenceWithoutRenamingSourceAssetIds() {
        val original = snapshot(portable("local")).copy(
            folders = listOf(FolderEntity("folder", "Folder", "folder", null, 1, previewBookIds = "[\"local\",\"unmapped\"]")),
            bookFolderLinks = listOf(BookFolderCrossRefEntity("local", "folder", 1)),
            bookTagLinks = listOf(BookTagCrossRefEntity("local", "tag", 1)),
            readingRecords = listOf(ReadingRecordEntity(bookId = "local", date = "2026-11-08", duration = 1, startTime = 1, endTime = 2)),
            bookmarks = listOf(BookmarkEntity(bookId = "local", chapterIndex = 1, position = .5f, title = "Bookmark", createdAt = 1, syncId = "bookmark")),
            notes = listOf(NoteEntity(bookId = "local", chapterIndex = 1, startPosition = 0, endPosition = 1, selectedText = "text", note = "note", color = "red", createdAt = 1, syncId = "note")),
            journalEvents = listOf(ReadingJournalEventEntity("event", "session", "local", 1, 2)),
            bookHighlightRuleStates = listOf(BookHighlightRuleStateEntity("local", "rule", true, 0)),
            bookHighlightSettings = listOf(BookHighlightSettingsEntity("local", true, 1)),
            highlightRuleExclusions = listOf(HighlightRuleExclusionEntity("local", "rule", "match", 1)),
            tombstones = listOf("book" to "local", "book_folder" to "local", "book_tag" to "local:tag", "note" to "note").map {
                SyncTombstoneEntity(it.first, it.second, 1, "device")
            },
            assets = listOf(PortableAsset("body-asset", "book", "local", "body.epub", hash, 42),
                PortableAsset("cover-asset", "book_cover", "local", "cover.jpg", hash, 42),
                PortableAsset("folder-asset", "folder_cover", "folder", "folder.jpg", hash, 42))
        )
        val wire = original.remapWebdavBookIds(mapOf("local" to "wire"))
        assertEquals("wire", wire.books.single().id)
        assertEquals(List(9) { "wire" }, listOf(
            wire.bookFolderLinks.single().bookId, wire.bookTagLinks.single().bookId,
            wire.readingRecords.single().bookId, wire.bookmarks.single().bookId,
            wire.notes.single().bookId, wire.journalEvents.single().bookId,
            wire.bookHighlightRuleStates.single().bookId, wire.bookHighlightSettings.single().bookId,
            wire.highlightRuleExclusions.single().bookId
        ))
        assertEquals("[\"wire\",\"unmapped\"]", wire.folders.single().previewBookIds)
        assertEquals(listOf("wire", "wire", "wire:tag", "note"), wire.tombstones.map { it.itemId })
        assertEquals(listOf("wire", "wire", "folder"), wire.assets.map { it.ownerId })
        assertEquals(original.assets.map { it.id }, wire.assets.map { it.id })
        assertEquals(original.assets.map { it.fileName }, wire.assets.map { it.fileName })
        assertEquals(original, wire.remapWebdavBookIds(mapOf("wire" to "local")))
    }

    @Test fun independentlyImportedUnreadCopyReceivesProgressAndReverseSyncRetainsLocalIds() {
        val phoneB = snapshot(portable("phone-b").copy(createdAt = 100, lastReadTime = 100))
        val phoneA = snapshot(portable("wire").copy(createdAt = 1, lastReadTime = 50, readingProgress = .6f, locatorJson = "chapter-6"))
        val merged = PortableSnapshotMerger.merge(phoneB.remapWebdavBookIds(mapOf("phone-b" to "wire")), phoneA)
        val restoredB = merged.remapWebdavBookIds(mapOf("wire" to "phone-b"))
        assertEquals("phone-b", restoredB.books.single().id)
        assertEquals(.6f, restoredB.books.single().readingProgress, 0f)
        assertEquals("chapter-6", restoredB.books.single().locatorJson)
        val laterB = restoredB.copy(books = restoredB.books.map { it.copy(lastReadTime = 200, readingProgress = .8f, locatorJson = "chapter-8") })
        val restoredA = PortableSnapshotMerger.merge(phoneA, laterB.remapWebdavBookIds(mapOf("phone-b" to "wire")))
        assertEquals("wire", restoredA.books.single().id)
        assertEquals(.8f, restoredA.books.single().readingProgress, 0f)
        assertEquals("chapter-8", restoredA.books.single().locatorJson)
    }

    @Test fun changedContentDoesNotLetLegacyRemoteUuidOverwriteNewContentOnReverseMapping() {
        val local = snapshot(portable("local").copy(readingProgress = .2f, lastReadTime = 300))
            .remapWebdavBookIds(mapOf("local" to "new-content"))
        val staleRemote = snapshot(portable("local").copy(readingProgress = .9f, lastReadTime = 500))
        val merged = PortableSnapshotMerger.merge(local, staleRemote)
        val payload = merged.withoutWebdavBookIds(setOf("local"))
            .remapWebdavBookIds(mapOf("new-content" to "local"))
        assertEquals("local", payload.books.single().id)
        assertEquals(.2f, payload.books.single().readingProgress, 0f)
        assertEquals(.9f, staleRemote.books.single().readingProgress, 0f)
    }

    private fun book(id: String) = Book(id, "Title", "Author", "/$id.epub", null, BookFormat.EPUB, 1, 0f, createdAt = 1)
    private fun portable(id: String) = PortableBook(id, "Title", "Author", "EPUB", 1, 0f, null, 1, false, false, 1,
        null, null, null, null, 0, null)
    private fun snapshot(book: PortableBook) = PortableSnapshot(createdAt = 1, sourceDeviceId = "device", preferences = emptyList(),
        books = listOf(book), folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
        readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(), tombstones = emptyList(), assets = emptyList())
}
