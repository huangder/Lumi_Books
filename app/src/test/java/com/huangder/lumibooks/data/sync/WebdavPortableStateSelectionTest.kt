package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableBook
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavPortableStateSelectionTest {

    @Test
    fun `local downloaded book stays downloaded while remote placeholder stays cloud-only`() {
        val local = snapshot(listOf(book(id = "b1", isCloudOnly = false, metadataUpdatedAt = 2_000L)))
        val remote = snapshot(listOf(book(id = "b1", isCloudOnly = true, metadataUpdatedAt = 1_000L)))
        val merged = PortableSnapshotMerger.merge(local, remote)

        // The generic merger has no body assets in WebDAV snapshots, so it still derives a
        // cloud-only marker. This test documents why the per-device selection below must
        // override it before anything is applied or uploaded.
        assertTrue(merged.books.single().isCloudOnly)

        val forLocal = chooseWebdavBooksForDevice(
            localBooks = local.books,
            remoteBooks = remote.books,
            mergedBooks = merged.books,
            syncLibraryOrganization = true,
            syncReadingData = true,
            forLocal = true
        )
        val forRemote = chooseWebdavBooksForDevice(
            localBooks = local.books,
            remoteBooks = remote.books,
            mergedBooks = merged.books,
            syncLibraryOrganization = true,
            syncReadingData = true,
            forLocal = false
        )

        assertFalse(forLocal.single().isCloudOnly)
        assertTrue(forRemote.single().isCloudOnly)
    }

    @Test
    fun `both placeholders stay cloud-only on both sides`() {
        val local = snapshot(listOf(book(id = "b1", isCloudOnly = true)))
        val remote = snapshot(listOf(book(id = "b1", isCloudOnly = true)))
        val merged = PortableSnapshotMerger.merge(local, remote)

        val forLocal = chooseWebdavBooksForDevice(
            local.books, remote.books, merged.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = true
        )
        val forRemote = chooseWebdavBooksForDevice(
            local.books, remote.books, merged.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = false
        )

        assertTrue(forLocal.single().isCloudOnly)
        assertTrue(forRemote.single().isCloudOnly)
    }

    @Test
    fun `book present on one side keeps that side value`() {
        val localDownloaded = snapshot(listOf(book(id = "b1", isCloudOnly = false)))
        val emptyRemote = snapshot(emptyList())
        val mergedLocalOnly = PortableSnapshotMerger.merge(localDownloaded, emptyRemote)

        val forLocal = chooseWebdavBooksForDevice(
            localDownloaded.books, emptyRemote.books, mergedLocalOnly.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = true
        )
        val forRemote = chooseWebdavBooksForDevice(
            localDownloaded.books, emptyRemote.books, mergedLocalOnly.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = false
        )

        assertFalse(forLocal.single().isCloudOnly)
        assertFalse(forRemote.single().isCloudOnly)

        val emptyLocal = snapshot(emptyList())
        val remotePlaceholder = snapshot(listOf(book(id = "b2", isCloudOnly = true)))
        val mergedRemoteOnly = PortableSnapshotMerger.merge(emptyLocal, remotePlaceholder)

        val forLocalRemoteOnly = chooseWebdavBooksForDevice(
            emptyLocal.books, remotePlaceholder.books, mergedRemoteOnly.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = true
        )
        val forRemoteOnly = chooseWebdavBooksForDevice(
            emptyLocal.books, remotePlaceholder.books, mergedRemoteOnly.books,
            syncLibraryOrganization = true, syncReadingData = true, forLocal = false
        )

        assertTrue(forLocalRemoteOnly.single().isCloudOnly)
        assertTrue(forRemoteOnly.single().isCloudOnly)
    }

    @Test
    fun `local cloud placeholder remains in local payload when state snapshot omits it`() {
        val local = snapshot(listOf(book(id = "cloud-book", isCloudOnly = true)))
        val remote = snapshot(emptyList())
        val merged = PortableSnapshotMerger.merge(local, remote)

        val localPayload = chooseWebdavBooksForDevice(
            localBooks = local.books,
            remoteBooks = remote.books,
            mergedBooks = merged.books,
            syncLibraryOrganization = true,
            syncReadingData = true,
            forLocal = true
        )

        assertEquals(listOf("cloud-book"), localPayload.map { it.id })
        assertTrue(localPayload.single().isCloudOnly)
    }

    @Test
    fun `partial toggles keep device-local availability and merge only enabled fields`() {
        val local = snapshot(listOf(book(id = "b1", isCloudOnly = false).copy(lastReadTime = 10L)))
        val remote = snapshot(listOf(book(id = "b1", isCloudOnly = true).copy(lastReadTime = 20L)))
        val merged = PortableSnapshotMerger.merge(local, remote)

        // 书架组织开、阅读数据关：元数据用合并结果，进度与 availability 用本机值。
        val orgOnly = chooseWebdavBooksForDevice(
            local.books, remote.books, merged.books,
            syncLibraryOrganization = true, syncReadingData = false, forLocal = true
        )
        assertFalse(orgOnly.single().isCloudOnly)
        assertEquals(10L, orgOnly.single().lastReadTime)

        // 书架组织关、阅读数据开：元数据与本机 availability 用本机值，进度用合并结果。
        val readingOnly = chooseWebdavBooksForDevice(
            local.books, remote.books, merged.books,
            syncLibraryOrganization = false, syncReadingData = true, forLocal = true
        )
        assertFalse(readingOnly.single().isCloudOnly)
        assertEquals(20L, readingOnly.single().lastReadTime)
    }

    private fun book(
        id: String,
        isCloudOnly: Boolean,
        metadataUpdatedAt: Long = 1_000L
    ) = PortableBook(
        id = id,
        title = "Title",
        author = "Author",
        format = "EPUB",
        lastReadTime = 0L,
        readingProgress = 0f,
        locatorJson = null,
        createdAt = 1_000L,
        isFavorite = false,
        isCloudOnly = isCloudOnly,
        metadataUpdatedAt = metadataUpdatedAt,
        bodyAssetId = null,
        coverAssetId = null,
        remoteLibraryKey = "library",
        remoteFileName = "book.epub",
        remoteFileSize = 100L,
        remoteFileSha256 = null
    )

    private fun snapshot(books: List<PortableBook>) = PortableSnapshot(
        createdAt = 1_000L,
        sourceDeviceId = "device-a",
        preferences = emptyList(),
        books = books,
        folders = emptyList(),
        bookFolderLinks = emptyList(),
        tags = emptyList(),
        bookTagLinks = emptyList(),
        readingRecords = emptyList(),
        bookmarks = emptyList(),
        notes = emptyList(),
        tombstones = emptyList(),
        assets = emptyList()
    )
}
