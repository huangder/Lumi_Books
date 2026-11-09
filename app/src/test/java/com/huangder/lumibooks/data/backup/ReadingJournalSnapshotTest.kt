package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadingJournalSnapshotTest {
    private fun book(want: Long? = 42L, updated: Long = 10) = PortableBook(
        "book", "Title", "Author", "EPUB", 4, .4f, null, 1, false,
        false, updated, "body", null, null, null, 0, null, wantToReadAt = want)
    private fun event(id: String, updated: Long) = ReadingJournalEventEntity(
        id, ReadingJournalEventEntity.TYPE_SESSION, "book", 1, updated, 60_000, true, "device", updated)
    private val note = NoteEntity(bookId = "book", chapterIndex = 2, startPosition = 0, endPosition = 4,
        selectedText = "text", note = "", color = "#fff", createdAt = 5, syncId = "n1", bookProgress = .25f)
    private fun snapshot(book: PortableBook = book(), events: List<ReadingJournalEventEntity> = emptyList()) =
        PortableSnapshot(
            createdAt = 1, sourceDeviceId = "device", preferences = emptyList(), books = listOf(book),
            folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
            readingRecords = emptyList(), bookmarks = emptyList(), notes = listOf(note), tombstones = emptyList(),
            assets = emptyList(), journalEvents = events
        )

    @Test fun journalFieldsSurviveABackupRoundTrip() {
        val restored = PortableSnapshot.fromJson(snapshot(events = listOf(event("e", 9))).toJson())
        assertEquals(42L, restored.books.single().wantToReadAt)
        assertEquals(42L, restored.books.single().toEntity("f", null).wantToReadAt)
        assertEquals(.25f, restored.notes.single().bookProgress!!, 0f)
        assertEquals(listOf(event("e", 9)), restored.journalEvents)
    }

    @Test fun olderBackupsKeepLocalValuesAndHaveNoJournal() {
        val legacy = JSONObject(snapshot().toJson()).apply {
            getJSONArray("books").getJSONObject(0).remove("wantToReadAt")
            getJSONArray("notes").getJSONObject(0).remove("bookProgress")
        }
        val restored = PortableSnapshot.fromJson(legacy.toString())
        assertNull(restored.books.single().wantToReadAt)
        assertEquals(7L, restored.books.single().toEntity("f", null, existingWantToReadAt = 7L).wantToReadAt)
        assertNull(restored.notes.single().bookProgress)
        assertTrue(restored.journalEvents.isEmpty())
        assertFalse(JSONObject(snapshot().toJson()).has("journalEvents"))
    }

    @Test fun mergeKeepsTheNewestCopyOfEachEntryAndTheNewerToReadMark() {
        val local = snapshot(book(want = 0L, updated = 20), listOf(event("a", 5), event("b", 9)))
        val remote = snapshot(book(want = 42L, updated = 10), listOf(event("a", 8), event("c", 3)))
        val merged = PortableSnapshotMerger.merge(local, remote)
        assertEquals(0L, merged.books.single().wantToReadAt)
        assertEquals(mapOf("a" to 8L, "b" to 9L, "c" to 3L), merged.journalEvents.associate { it.id to it.updatedAt })
    }
}
