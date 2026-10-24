package com.huangder.lumibooks.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBookFilesTest {

    @Test
    fun `content disposition prefers the utf8 filename`() {
        assertEquals(
            "三体.epub",
            RemoteBookFiles.fileNameFromContentDisposition(
                "attachment; filename=\"fallback.epub\"; filename*=UTF-8''%E4%B8%89%E4%BD%93.epub"
            )
        )
        assertEquals("a b.pdf", RemoteBookFiles.fileNameFromContentDisposition("attachment; filename=\"a b.pdf\""))
        assertEquals("plain.txt", RemoteBookFiles.fileNameFromContentDisposition("inline; filename=plain.txt"))
        assertNull(RemoteBookFiles.fileNameFromContentDisposition(null))
    }

    @Test
    fun `download name falls back from server to url to title`() {
        assertEquals(
            "Server Name.epub",
            RemoteBookFiles.resolveDownloadFileName(
                contentDisposition = "attachment; filename=\"Server Name.epub\"",
                responseMimeType = "application/epub+zip",
                url = "https://h/get/123",
                title = "Catalog Title",
                expectedExtension = "epub"
            )
        )
        assertEquals(
            "book one.pdf",
            RemoteBookFiles.resolveDownloadFileName(null, null, "https://h/files/book%20one.pdf?token=x", "T", "pdf")
        )
        // Calibre style /get/epub/12 URLs carry no name: use the catalog title and the chosen format.
        assertEquals(
            "My Title_ Part 2.epub",
            RemoteBookFiles.resolveDownloadFileName(null, "application/epub+zip", "https://h/get/epub/12", "My Title: Part 2", "epub")
        )
    }

    @Test
    fun `sanitize removes separators and keeps the extension when truncating`() {
        assertEquals("a_b_c.epub", RemoteBookFiles.sanitizeFileName("a/b\\c.epub"))
        assertNull(RemoteBookFiles.sanitizeFileName(".."))
        val long = "x".repeat(300) + ".epub"
        val sanitized = RemoteBookFiles.sanitizeFileName(long)!!
        assertTrue(sanitized.length <= 120)
        assertTrue(sanitized.endsWith(".epub"))
    }

    @Test
    fun `mime and name map to importable extensions`() {
        assertEquals("epub", RemoteBookFiles.extensionForMimeType("application/epub+zip; charset=binary"))
        assertEquals("cbz", RemoteBookFiles.extensionForMimeType("application/x-cbz"))
        assertNull(RemoteBookFiles.extensionForMimeType("application/x-mobi8-ebook"))
        assertEquals("md", RemoteBookFiles.extensionFromName("notes.MD"))
        assertNull(RemoteBookFiles.extensionFromName("archive.rar"))
    }

    @Test
    fun `remote document key is stable and hides the raw identity`() {
        val key = RemoteBookFiles.remoteDocumentKey("source", "webdav:/secret/path.epub")
        assertEquals(key, RemoteBookFiles.remoteDocumentKey("source", "webdav:/secret/path.epub"))
        assertTrue(key.startsWith("remote:"))
        assertFalse(key.contains("secret"))
    }

    @Test
    fun `sniffer rejects html login pages`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        assertTrue(RemoteFileSniffer.matches(zip, "epub"))
        assertFalse(RemoteFileSniffer.matches("<!DOCTYPE html><html>".toByteArray(), "epub"))
        assertFalse(RemoteFileSniffer.matches("<!DOCTYPE html><html>".toByteArray(), "txt"))
        assertTrue(RemoteFileSniffer.matches("%PDF-1.7\n".toByteArray(), "pdf"))
        assertTrue(RemoteFileSniffer.matches("# Title\ntext".toByteArray(), "md"))
    }
}
