package com.huangder.lumibooks.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the WebDAV connection probe.
 *
 * The original implementation verified the server with `GET` on the collection and treated a 404
 * as "server not found". Real WebDAV servers answer GET on a collection with 404 (Nextcloud's
 * `/remote.php/dav/`) or 5xx even though PROPFIND works fine, which made valid configurations
 * report "connection failed".
 */
class WebdavClientProbeTest {

    @Test
    fun `collection that rejects GET is reachable through PROPFIND`() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "GET") {
                    MockResponse().setResponseCode(404).setBody("File not found")
                } else {
                    MockResponse().setResponseCode(207).setBody(MULTISTATUS)
                }
        }
        server.start()
        try {
            val code = WebdavClient().probeCollection(server.url("/dav/").toString(), "user", "secret")

            assertEquals(207, code)
            // Exactly one request: the probe must never fall back to GET.
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("PROPFIND", request.method)
            assertEquals("0", request.getHeader("Depth"))
            assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Basic "))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `plain 200 response is accepted`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            assertEquals(
                200,
                WebdavClient().probeCollection(server.url("/dav/").toString(), "user", "secret")
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `address without trailing slash is retried once with the slash`() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/dav/") {
                    MockResponse().setResponseCode(207).setBody(MULTISTATUS)
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val code = WebdavClient().probeCollection(server.url("/dav").toString(), "user", "secret")

            assertEquals(207, code)
            assertEquals(2, server.requestCount)
            assertEquals("/dav", server.takeRequest().path)
            assertEquals("/dav/", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `status codes are reported to the caller`() = runTest {
        // Trailing slash keeps the probe from retrying, so every case is a single request.
        val expected = listOf(401, 403, 404, 405, 500, 501)
        for (code in expected) {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(code))
            server.start()
            try {
                assertEquals(
                    code,
                    WebdavClient().probeCollection(server.url("/dav/").toString(), "user", "secret")
                )
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `transport failure becomes a classified WebdavException`() = runTest {
        val server = MockWebServer()
        server.start()
        val url = server.url("/dav/").toString()
        server.shutdown()

        val error = captureWebdavException { WebdavClient().probeCollection(url, "user", "secret") }

        assertNotNull(error)
        assertEquals(WebdavErrorKind.NETWORK, error!!.kind)
        assertEquals(null, error.statusCode)
    }

    @Test
    fun `malformed address is reported as an invalid url`() = runTest {
        val error = captureWebdavException {
            WebdavClient().probeCollection("dav.example.com/dav", "user", "secret")
        }

        assertNotNull(error)
        assertEquals(WebdavErrorKind.INVALID_URL, error!!.kind)
    }

    @Test
    fun `createDirectory keeps the status code when the server rejects it`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        server.start()
        try {
            val error = captureWebdavException {
                WebdavClient().createDirectory(server.url("/dav/Books").toString(), "user", "secret")
            }

            assertNotNull(error)
            assertEquals(403, error!!.statusCode)
            assertEquals(WebdavErrorKind.FORBIDDEN, error.kind)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `createDirectory treats an existing collection as success`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(405))
        server.start()
        try {
            WebdavClient().createDirectory(server.url("/dav/Books").toString(), "user", "secret")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `createDirectory reports a missing parent collection as a conflict`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(409))
        server.start()
        try {
            val error = captureWebdavException {
                WebdavClient().createDirectory(server.url("/dav/Books").toString(), "user", "secret")
            }

            assertNotNull(error)
            assertEquals(409, error!!.statusCode)
            assertEquals(WebdavErrorKind.CONFLICT, error.kind)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `listDirectory keeps the status code when PROPFIND fails`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(409))
        server.start()
        try {
            val error = captureWebdavException {
                WebdavClient().listDirectory(server.url("/dav/Assets").toString(), "user", "secret")
            }

            assertNotNull(error)
            assertEquals(409, error!!.statusCode)
        } finally {
            server.shutdown()
        }
    }

    private suspend fun captureWebdavException(
        block: suspend () -> Unit
    ): WebdavException? = try {
        block()
        null
    } catch (error: WebdavException) {
        error
    }

    private companion object {
        const val MULTISTATUS = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/dav/</D:href>
    <D:propstat>
      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""
    }
}
