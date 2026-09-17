package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavDownloadRepairTest {

    @Test
    fun `cloud marker with existing matching file is repaired without redownload`() {
        assertTrue(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = true, filePath = "/books/b1.epub", remoteFileSize = 100L),
                localFileSize = 100L
            )
        )
    }

    @Test
    fun `unknown remote size still repairs when a non-empty file exists`() {
        assertTrue(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = true, filePath = "/books/b1.epub", remoteFileSize = 0L),
                localFileSize = 100L
            )
        )
    }

    @Test
    fun `size mismatch keeps the book download pending`() {
        assertFalse(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = true, filePath = "/books/b1.epub", remoteFileSize = 200L),
                localFileSize = 100L
            )
        )
    }

    @Test
    fun `blank path or missing file never repairs`() {
        assertFalse(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = true, filePath = "", remoteFileSize = 100L),
                localFileSize = 100L
            )
        )
        assertFalse(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = true, filePath = "/books/b1.epub", remoteFileSize = 100L),
                localFileSize = 0L
            )
        )
    }

    @Test
    fun `normal downloaded book is not treated as needing repair`() {
        assertFalse(
            cloudBookNeedsLocalRepair(
                book(isCloudOnly = false, filePath = "/books/b1.epub", remoteFileSize = 100L),
                localFileSize = 100L
            )
        )
    }

    private fun book(
        isCloudOnly: Boolean,
        filePath: String,
        remoteFileSize: Long
    ) = Book(
        id = "b1",
        title = "Title",
        author = "Author",
        filePath = filePath,
        coverPath = null,
        format = BookFormat.EPUB,
        lastReadTime = 0L,
        readingProgress = 0f,
        createdAt = 1_000L,
        isFavorite = false,
        isCloudOnly = isCloudOnly,
        remoteLibraryKey = "library",
        remoteFileName = "book.epub",
        remoteFileSize = remoteFileSize,
        remoteFileSha256 = null
    )
}
