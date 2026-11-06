package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArchivedBookSyncTest {
    private fun book(archived: Boolean? = true, updated: Long = 10) = PortableBook(
        "book", "Title", "Author", "EPUB", 4, .4f, "position", 1, true,
        false, updated, "body", null, "library", "book.epub", 12, "sha",
        isPinned = true, isArchived = archived)
    private fun snapshot(book: PortableBook) = PortableSnapshot(
        createdAt = 1, sourceDeviceId = "device", preferences = emptyList(), books = listOf(book),
        folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
        readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(), tombstones = emptyList(), assets = emptyList())

    @Test fun backupRoundTripAndLegacyRestoreKeepTheCorrectArchiveState() {
        val restored = PortableSnapshot.fromJson(snapshot(book()).toJson()).books.single()
        assertEquals(true, restored.isArchived)
        assertTrue(restored.toEntity("book.epub", null).isArchived)
        val legacy = JSONObject(snapshot(book()).toJson()).apply { getJSONArray("books").getJSONObject(0).remove("isArchived") }
        val oldBook = PortableSnapshot.fromJson(legacy.toString()).books.single()
        assertNull(oldBook.isArchived)
        assertFalse(oldBook.toEntity("book.epub", null).isArchived)
        assertTrue(oldBook.toEntity("book.epub", null, existingIsArchived = true).isArchived)
    }

    @Test fun webdavDistinguishesExplicitRestoresFromOlderSenders() {
        for (archived in listOf(true, false, null)) {
            val metadata = SyncBookMetadata("Title", "Author", "EPUB", 1, true, 2, isArchived = archived)
            val json = metadata.toJson()
            assertEquals(archived != null, json.has("isArchived"))
            assertEquals(archived, SyncBookMetadata.fromJson(json).isArchived)
        }
    }

    @Test fun newerRestoreWinsWhileReadingProgressMergesIndependently() {
        val archived = snapshot(book(true, 10).copy(lastReadTime = 30, readingProgress = .8f))
        val restored = snapshot(book(false, 20))
        val merged = PortableSnapshotMerger.merge(archived, restored)
        assertEquals(false, merged.books.single().isArchived)
        assertEquals(.8f, merged.books.single().readingProgress, 0f)
        assertEquals(30L, merged.books.single().lastReadTime)
        assertEquals(merged.books, PortableSnapshotMerger.merge(merged, archived).books)
    }

    @Test fun newerLegacyMetadataDoesNotUnarchiveOnEitherMergeSide() {
        val local = snapshot(book(true, 10))
        val legacy = snapshot(book(null, 20))
        assertEquals(true, PortableSnapshotMerger.merge(local, legacy).books.single().isArchived)
        assertEquals(true, PortableSnapshotMerger.merge(legacy, local).books.single().isArchived)
    }
}
