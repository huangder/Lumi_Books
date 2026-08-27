package com.huangder.lumibooks.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class WebdavClientDownloadTest {
    @Test
    fun downloadToFileStreamsProgressAndComputesChecksum() = runTest {
        val content = ByteArray(96 * 1024) { index -> (index % 251).toByte() }
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content))
                .throttleBody(8 * 1024, 1, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        server.start()
        val directory = Files.createTempDirectory("webdav-download-test").toFile()
        val destination = directory.resolve("book.part")
        val progress = mutableListOf<Pair<Long, Long>>()

        try {
            val result = WebdavClient().downloadToFile(
                url = server.url("/books/book.epub").toString(),
                destination = destination,
                username = "user",
                password = "password",
                expectedSize = content.size.toLong()
            ) { bytesRead, totalBytes ->
                progress += bytesRead to totalBytes
            }

            assertArrayEquals(content, destination.readBytes())
            assertEquals(content.size.toLong(), result.bytesWritten)
            assertEquals(content.size.toLong(), result.totalBytes)
            assertEquals(sha256(content), result.sha256)
            assertTrue(progress.size > 1)
            assertTrue(progress.zipWithNext().all { (left, right) -> left.first <= right.first })
            assertEquals(content.size.toLong() to content.size.toLong(), progress.last())
        } finally {
            server.shutdown()
            destination.delete()
            directory.delete()
        }
    }

    @Test
    fun downloadStateClampsPercentageToValidRange() {
        assertEquals(0f, BookDownloadState.Downloading(10, 0).progress)
        assertEquals(0.5f, BookDownloadState.Downloading(50, 100).progress)
        assertEquals(1f, BookDownloadState.Downloading(150, 100).progress)
    }

    @Test
    fun moveUsesWebdavDestinationAndOverwriteHeaders() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()
        try {
            val source = server.url("/library/manifest.json.next").toString()
            val destination = server.url("/library/manifest.json").toString()

            WebdavClient().move(source, destination, "user", "password")

            val request = server.takeRequest()
            assertEquals("MOVE", request.method)
            assertEquals(destination, request.getHeader("Destination"))
            assertEquals("T", request.getHeader("Overwrite"))
        } finally {
            server.shutdown()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
