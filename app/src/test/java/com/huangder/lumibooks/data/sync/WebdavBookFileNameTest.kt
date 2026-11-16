package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavBookFileNameTest {
    private val book = Book(
        id = "phone-a", title = "书架标题", author = "", filePath = "/internal/id.epub",
        coverPath = null, format = BookFormat.EPUB, lastReadTime = 0, readingProgress = 0f,
        createdAt = 1, sourceDisplayName = "中文 #100% 原书.EPUB"
    )

    @Test
    fun sourceNameSurvivesUploadAndDownloadThroughTheManifest() = runTest {
        val wireId = "sha256-${"a".repeat(64)}"
        val fileName = webdavBookFileName(book, wireId)
        assertTrue(fileName.startsWith("中文 #100% 原书 - "))
        assertTrue(fileName.endsWith(".epub"))
        assertEquals(fileName, webdavBookFileName(book.copy(id = "phone-b", title = "新标题"), wireId))
        assertNotEquals(fileName, webdavBookFileName(book, "different-wire-id"))

        val content = "book body".toByteArray()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setBody(content.toString(Charsets.UTF_8)))
            val root = server.url("/dav").toString()
            val uploaded = WebdavClient().uploadStream(
                WebdavUrl.append(root, "Lumi Books", "books", fileName), content.size.toLong(),
                { content.inputStream() }, "user", "password"
            )
            val manifest = SyncManifest(books = mapOf(
                wireId to SyncFileEntry(fileName, uploaded.sha256, uploaded.bytesWritten, 1)
            ))
            val received = SyncManifest.fromJson(manifest.toJson()).books.getValue(wireId)
            val body = WebdavClient().download(
                WebdavUrl.append(root, "Lumi Books", "books", received.fileName), "user", "password"
            )
            assertArrayEquals(content, body)
            val put = server.takeRequest()
            val get = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("GET", get.method)
            assertEquals(put.path, get.path)
            assertEquals(fileName, get.requestUrl!!.pathSegments.last())
        }
    }

    @Test
    fun unsafeAndLongSourceNamesStayInOnePathSegment() {
        val sourceName = "目录/子目录\\书？: ${"📖".repeat(70)}.md"
        val name = webdavBookFileName(book.copy(
            sourceDisplayName = sourceName, format = BookFormat.MARKDOWN
        ), "wire")
        assertTrue(name.startsWith("目录_子目录_书？_ 📖"))
        assertTrue(name.endsWith(".mdpack"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 205)
        assertEquals(name, name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(webdavBookFileName(book.copy(sourceDisplayName = null), "wire").startsWith("书架标题 - "))
    }

    @Test
    fun fileMissingCheckDistinguishesIncompleteListingsFromMissingLegacyBodies() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/books/%E4%B9%A6%20%23100%25.epub</d:href></d:response>
                </d:multistatus>"""
            ))
            server.enqueue(MockResponse().setResponseCode(405))
            server.enqueue(MockResponse().setResponseCode(206).setBody("b"))
            server.enqueue(MockResponse().setResponseCode(404))
            val client = WebdavClient()
            val root = server.url("/books").toString()
            assertEquals(setOf("书 #100%.epub"), client.listBookFileNames(root, "user", "password"))
            assertFalse(client.isFileMissing(WebdavUrl.append(root, "old-id.epub"), "user", "password"))
            assertTrue(client.isFileMissing(WebdavUrl.append(root, "missing-id.epub"), "user", "password"))

            assertEquals("PROPFIND", server.takeRequest().method)
            assertEquals("HEAD", server.takeRequest().method)
            val fallback = server.takeRequest()
            assertEquals("GET", fallback.method)
            assertEquals("bytes=0-0", fallback.getHeader("Range"))
            assertEquals("/books/missing-id.epub", server.takeRequest().path)
        }
    }
}
