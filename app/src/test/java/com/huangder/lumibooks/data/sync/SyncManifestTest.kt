package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManifestTest {
    @Test
    fun v1ManifestRemainsReadableWithoutMetadataOrTombstones() {
        val manifest = SyncManifest.fromJson(
            """
            {
              "books": {
                "book-1": {
                  "fileName": "book-1.epub",
                  "sha256": "abc",
                  "sizeBytes": 123,
                  "lastModified": 456
                }
              },
              "data": {}
            }
            """.trimIndent()
        )

        assertEquals(1, manifest.version)
        assertEquals("book-1.epub", manifest.books.getValue("book-1").fileName)
        assertNull(manifest.books.getValue("book-1").metadata)
        assertNull(manifest.books.getValue("book-1").cover)
        assertTrue(manifest.deletedBooks.isEmpty())
    }

    @Test
    fun v2RoundTripPreservesMetadataCoverReadingDataAndTombstone() {
        val cover = SyncFileEntry("book-1.jpg", "cover-hash", 42, 800)
        val book = SyncFileEntry(
            fileName = "book-1.epub",
            sha256 = "book-hash",
            sizeBytes = 12_345,
            lastModified = 700,
            metadata = SyncBookMetadata(
                title = "Title",
                author = "Author",
                format = "EPUB",
                createdAt = 100,
                isFavorite = true,
                updatedAt = 900
            ),
            cover = cover
        )
        val data = SyncFileEntry("book-1.json", "data-hash", 77, 850)
        val tombstone = DeletedBookEntry(1_000, "old.pdf", "old.jpg", "old.json")
        val original = SyncManifest(
            books = mapOf("book-1" to book),
            data = mapOf("book-1" to data),
            deletedBooks = mapOf("old-book" to tombstone)
        )

        val decoded = SyncManifest.fromJson(original.toJson())

        assertEquals(SyncManifest.CURRENT_VERSION, decoded.version)
        assertEquals(original, decoded)
        assertTrue(decoded.books.getValue("book-1").metadata!!.isFavorite)
        assertEquals("book-1.jpg", decoded.books.getValue("book-1").cover!!.fileName)
    }

    @Test
    fun tombstoneAndEmptyMapsSurviveRoundTripWithoutCreatingBookEntries() {
        val manifest = SyncManifest(
            deletedBooks = mapOf(
                "deleted" to DeletedBookEntry(
                    deletedAt = 99,
                    fileName = null,
                    coverFileName = null,
                    dataFileName = null
                )
            )
        )

        val decoded = SyncManifest.fromJson(manifest.toJson())

        assertTrue(decoded.books.isEmpty())
        assertTrue(decoded.data.isEmpty())
        assertFalse("deleted" in decoded.books)
        assertEquals(99, decoded.deletedBooks.getValue("deleted").deletedAt)
    }

    @Test
    fun metadataConflictUsesStrictlyNewerTimestamp() {
        val older = SyncBookMetadata("Remote", "Author", "EPUB", 1, false, 99)
        val equal = older.copy(updatedAt = 100)
        val newer = older.copy(updatedAt = 101)

        assertFalse(shouldApplyRemoteMetadata(100, older))
        assertFalse(shouldApplyRemoteMetadata(100, equal))
        assertTrue(shouldApplyRemoteMetadata(100, newer))
        assertTrue(shouldApplyRemoteMetadata(null, null))
    }

    @Test
    fun tombstoneFiltersRemoteEntryAndPreventsResurrection() {
        val entry = SyncFileEntry("deleted.epub", "hash", 10, 20)
        val manifest = SyncManifest(
            books = mapOf("deleted" to entry, "active" to entry.copy(fileName = "active.epub")),
            deletedBooks = mapOf("deleted" to DeletedBookEntry(30, "deleted.epub", null, null))
        )

        assertEquals(setOf("active"), manifest.activeRemoteBooks().keys)
    }
}
