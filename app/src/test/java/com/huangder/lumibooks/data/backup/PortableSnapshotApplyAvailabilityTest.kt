package com.huangder.lumibooks.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 覆盖 WebDAV 状态同步回写（replaceAssetDirectories=false、书本体被排除）时，
 * 本机“已下载/仅云端”状态必须被保留，不能被 bodyless 快照的推导结果翻转。
 */
class PortableSnapshotApplyAvailabilityTest {

    @Test
    fun `existing downloaded book keeps local availability when override is provided`() {
        val remoteCloudBook = portableBook(isCloudOnly = true)

        val entity = remoteCloudBook.toEntity(
            filePath = "/data/books/b1.epub",
            coverPath = null,
            existingIsCloudOnly = false
        )

        assertFalse(entity.isCloudOnly)
        assertEquals("/data/books/b1.epub", entity.filePath)
    }

    @Test
    fun `without the override a bodyless cloud marker still flips the record`() {
        val remoteCloudBook = portableBook(isCloudOnly = true)

        val entity = remoteCloudBook.toEntity(
            filePath = "/data/books/b1.epub",
            coverPath = null
        )

        assertTrue(entity.isCloudOnly)
    }

    @Test
    fun `downloaded marker on the snapshot restores as downloaded`() {
        val downloadedBook = portableBook(isCloudOnly = false)

        val entity = downloadedBook.toEntity(
            filePath = "/data/books/b1.epub",
            coverPath = null
        )

        assertFalse(entity.isCloudOnly)
    }

    @Test
    fun `body asset in a real backup restores as downloaded`() {
        val cloudMarkerBook = portableBook(isCloudOnly = true, bodyAssetId = "sha-file.epub")

        val entity = cloudMarkerBook.toEntity(
            filePath = "/restored/asset-file.epub",
            coverPath = null
        )

        assertFalse(entity.isCloudOnly)
    }

    private fun portableBook(isCloudOnly: Boolean, bodyAssetId: String? = null) = PortableBook(
        id = "b1",
        title = "Title",
        author = "Author",
        format = "EPUB",
        lastReadTime = 0L,
        readingProgress = 0f,
        locatorJson = null,
        createdAt = 1_000L,
        isFavorite = false,
        isCloudOnly = isCloudOnly,
        metadataUpdatedAt = 1_000L,
        bodyAssetId = bodyAssetId,
        coverAssetId = null,
        remoteLibraryKey = "library",
        remoteFileName = "book.epub",
        remoteFileSize = 100L,
        remoteFileSha256 = null
    )
}
