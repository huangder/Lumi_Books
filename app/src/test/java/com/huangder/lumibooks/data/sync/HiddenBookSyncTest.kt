package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableBook
import com.huangder.lumibooks.data.backup.PortableSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hidden flag must survive backups and WebDAV, or a restore would expose hidden books. */
class HiddenBookSyncTest {
    @Test
    fun webdavMetadataCarriesTheHiddenFlag() {
        val metadata = SyncBookMetadata("书", "作者", "EPUB", 1, false, 2, isHidden = true)
        assertEquals(true, SyncBookMetadata.fromJson(metadata.toJson()).isHidden)
    }

    @Test
    fun metadataFromOlderAppsLeavesTheLocalChoiceAlone() {
        val legacy = SyncBookMetadata("书", "作者", "EPUB", 1, false, 2).toJson()
        assertFalse(legacy.has("isHidden"))
        assertNull(SyncBookMetadata.fromJson(legacy).isHidden)
    }

    @Test
    fun backupsRoundTripTheHiddenFlagAndOldBackupsRestoreVisible() {
        val hidden = snapshot(PortableBook(
            id = "a", title = "Book", author = "Author", format = "EPUB", lastReadTime = 1,
            readingProgress = 0f, locatorJson = null, createdAt = 1, isFavorite = false,
            isCloudOnly = true, metadataUpdatedAt = 1, bodyAssetId = null, coverAssetId = null,
            remoteLibraryKey = null, remoteFileName = null, remoteFileSize = 0, remoteFileSha256 = null,
            isHidden = true
        ))
        assertTrue(PortableSnapshot.fromJson(hidden.toJson()).books.single().isHidden)
        val legacy = JSONObject(hidden.toJson()).apply {
            getJSONArray("books").getJSONObject(0).remove("isHidden")
        }
        assertFalse(PortableSnapshot.fromJson(legacy.toString()).books.single().isHidden)
    }

    private fun snapshot(book: PortableBook) = PortableSnapshot(
        createdAt = 1, sourceDeviceId = "device", preferences = emptyList(), books = listOf(book),
        folders = emptyList(), bookFolderLinks = emptyList(), tags = emptyList(), bookTagLinks = emptyList(),
        readingRecords = emptyList(), bookmarks = emptyList(), notes = emptyList(), tombstones = emptyList(),
        assets = emptyList()
    )
}
