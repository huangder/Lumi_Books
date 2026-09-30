package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingRecordEntity
import com.huangder.lumibooks.data.local.entity.SyncTombstoneEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightRuleStateEntity
import com.huangder.lumibooks.data.local.entity.BookHighlightSettingsEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleEntity
import com.huangder.lumibooks.data.local.entity.HighlightRuleExclusionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class PortableSnapshotTest {
    @Test
    fun readingTagsRoundTripAndOldBackupsHaveEmptyTags() {
        val tagJson = "[\"人物\",\"复习\"]"
        val original = snapshot(deviceId = "device-a",
            notes = listOf(note("n1").copy(tagsJson = tagJson)),
            bookmarks = listOf(bookmark("b1").copy(tagsJson = tagJson)))
        val restored = PortableSnapshot.fromJson(original.toJson())
        assertEquals(tagJson, restored.notes.single().tagsJson)
        assertEquals(tagJson, restored.bookmarks.single().tagsJson)
        val legacy = JSONObject(original.toJson()).apply {
            getJSONArray("notes").getJSONObject(0).remove("tagsJson")
            getJSONArray("bookmarks").getJSONObject(0).remove("tagsJson")
        }
        val old = PortableSnapshot.fromJson(legacy.toString())
        assertEquals("[]", old.notes.single().tagsJson)
        assertEquals("[]", old.bookmarks.single().tagsJson)
    }

    @Test
    fun emptyNoteIdentitySurvivesBackupRoundTrip() {
        val emptyNote = note("empty").copy(note = "", isNote = true)
        val original = snapshot(deviceId = "device-a", notes = listOf(emptyNote))
        assertEquals(emptyNote, PortableSnapshot.fromJson(original.toJson()).notes.single())
    }

    @Test
    fun jsonRoundTripPreservesSnapshot() {
        val snapshot = snapshot(
            deviceId = "device-a",
            preferences = listOf(PortablePreference("nickname", "string", "Reader", 10, "device-a")),
            records = listOf(record("device-a", 120)),
            bookmarks = listOf(bookmark("bookmark-a")),
            notes = listOf(note("note-a"))
        )

        assertEquals(snapshot, PortableSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun bookmarkRemarkRoundTripsAndLegacyBackupDefaultsToEmpty() {
        val original = snapshot(
            deviceId = "device-a",
            bookmarks = listOf(bookmark("bookmark-a").copy(remark = "Review this page"))
        )
        assertEquals("Review this page", PortableSnapshot.fromJson(original.toJson()).bookmarks.single().remark)

        val legacy = JSONObject(original.toJson()).apply {
            getJSONArray("bookmarks").getJSONObject(0).remove("remark")
        }
        assertEquals("", PortableSnapshot.fromJson(legacy.toString()).bookmarks.single().remark)
    }

    @Test
    fun fullBackupRoundTripPreservesRuleLibraryAndGeneratedNoteSource() {
        val generated = note("generated").copy(
            origin = "highlight_rule",
            sourceRuleId = "r1",
            sourceMatchKey = "0:1:4:key",
            styleSnapshotJson = "{\"underlineMode\":3}"
        )
        val original = snapshot(deviceId = "device-a", notes = listOf(generated)).copy(
            highlightRules = listOf(
                HighlightRuleEntity(
                    "r1", "Rule", "a+", true, 0, 0, null, 3, 2f, 1f, 700,
                    true, sampleText = "aaa", rawJson = "{\"unknown\":true}", updatedAt = 100
                )
            ),
            bookHighlightRuleStates = listOf(BookHighlightRuleStateEntity("book-a", "r1", true, 0)),
            bookHighlightSettings = listOf(BookHighlightSettingsEntity("book-a", true, 2)),
            highlightRuleExclusions = listOf(
                HighlightRuleExclusionEntity("book-a", "r1", "0:8:9:key", 90)
            )
        )

        assertEquals(original, PortableSnapshot.fromJson(original.toJson()))
    }

    @Test
    fun comicFormatSurvivesJsonRoundTrip() {
        val snapshot = snapshot(deviceId = "device-a", format = "CBZ")

        val restored = PortableSnapshot.fromJson(snapshot.toJson())

        assertEquals("CBZ", restored.books.single().format)
    }

    @Test
    fun pinnedBookSurvivesJsonRoundTripAndLegacyJsonDefaultsToFalse() {
        val pinned = snapshot(deviceId = "device-a", isPinned = true)
        assertTrue(PortableSnapshot.fromJson(pinned.toJson()).books.single().isPinned)

        val legacyJson = JSONObject(pinned.toJson()).apply {
            getJSONArray("books").getJSONObject(0).remove("isPinned")
        }
        assertFalse(PortableSnapshot.fromJson(legacyJson.toString()).books.single().isPinned)
    }

    @Test
    fun mergeIsIdempotentAndDoesNotInflateDeviceContributions() {
        val local = snapshot(deviceId = "device-a", records = listOf(record("device-a", 10)))
        val remote = snapshot(deviceId = "device-b", records = listOf(record("device-b", 20)))

        val first = PortableSnapshotMerger.merge(local, remote)
        val second = PortableSnapshotMerger.merge(first, remote)

        assertEquals(2, second.readingRecords.size)
        assertEquals(30L, second.readingRecords.sumOf { it.duration })
        assertEquals(first, second)
    }

    @Test
    fun newerTombstonePreventsDeletedAnnotationFromReturning() {
        val remoteNote = note("note-a", updatedAt = 20)
        val local = snapshot(
            deviceId = "device-a",
            tombstones = listOf(SyncTombstoneEntity("note", "note-a", 30, "device-a"))
        )
        val remote = snapshot(deviceId = "device-b", notes = listOf(remoteNote))

        val merged = PortableSnapshotMerger.merge(local, remote)

        assertFalse(merged.notes.any { it.syncId == "note-a" })
        assertEquals(1, merged.tombstones.size)
    }

    @Test
    fun concurrentPreferenceMergeIsCommutativeAndUsesDeviceIdTieBreak() {
        val left = snapshot(
            deviceId = "device-a",
            preferences = listOf(PortablePreference("nickname", "string", "A", 100, "device-a"))
        )
        val right = snapshot(
            deviceId = "device-b",
            preferences = listOf(PortablePreference("nickname", "string", "B", 100, "device-b"))
        )

        val leftFirst = PortableSnapshotMerger.merge(left, right)
        val rightFirst = PortableSnapshotMerger.merge(right, left)

        assertEquals(leftFirst, rightFirst)
        assertEquals("B", leftFirst.preferences.single().value)
    }

    private fun snapshot(
        deviceId: String,
        format: String = "EPUB",
        preferences: List<PortablePreference> = emptyList(),
        records: List<ReadingRecordEntity> = emptyList(),
        bookmarks: List<BookmarkEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
        tombstones: List<SyncTombstoneEntity> = emptyList(),
        isPinned: Boolean = false
    ) = PortableSnapshot(
        createdAt = 100,
        sourceDeviceId = deviceId,
        preferences = preferences,
        books = listOf(
            PortableBook(
                id = "book-a",
                title = "Book",
                author = "Author",
                format = format,
                lastReadTime = 10,
                readingProgress = 0.25f,
                locatorJson = null,
                createdAt = 1,
                isFavorite = false,
                isPinned = isPinned,
                isCloudOnly = true,
                metadataUpdatedAt = 1,
                bodyAssetId = null,
                coverAssetId = null,
                remoteLibraryKey = null,
                remoteFileName = null,
                remoteFileSize = 0,
                remoteFileSha256 = null
            )
        ),
        folders = emptyList(),
        bookFolderLinks = emptyList(),
        tags = emptyList(),
        bookTagLinks = emptyList(),
        readingRecords = records,
        bookmarks = bookmarks,
        notes = notes,
        tombstones = tombstones,
        assets = emptyList()
    )

    private fun record(deviceId: String, duration: Long) = ReadingRecordEntity(
        bookId = "book-a",
        date = "2026-08-30",
        duration = duration,
        startTime = 1,
        endTime = duration + 1,
        sourceDeviceId = deviceId,
        updatedAt = duration + 1
    )

    private fun bookmark(syncId: String) = BookmarkEntity(
        bookId = "book-a",
        chapterIndex = 1,
        position = 0.5f,
        title = "Bookmark",
        createdAt = 10,
        syncId = syncId,
        updatedAt = 10
    )

    private fun note(syncId: String, updatedAt: Long = 10) = NoteEntity(
        bookId = "book-a",
        chapterIndex = 1,
        startPosition = 2,
        endPosition = 5,
        selectedText = "abc",
        note = "note",
        color = "yellow",
        createdAt = 10,
        syncId = syncId,
        updatedAt = updatedAt
    )
}
