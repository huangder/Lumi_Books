package com.huangder.lumibooks.data.remote

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteHttpTest {

    @Test
    fun `digest response matches the RFC 2617 example`() {
        val header = HttpDigest.authorization(
            username = "Mufasa",
            password = "Circle Of Life",
            realm = "testrealm@host.com",
            nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
            opaque = "5ccc069c403ebaf9f0171e9517f40e41",
            qopOptions = "auth,auth-int",
            algorithm = null,
            method = "GET",
            uri = "/dir/index.html",
            nonceCount = 1,
            cnonce = "0a4f113b"
        )
        assertTrue(header, header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(header.contains("qop=auth"))
        assertTrue(header.contains("nc=00000001"))
    }

    @Test
    fun `basic challenge is answered for the catalog origin`() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == null) {
                    MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"opds\"")
                } else {
                    MockResponse().setResponseCode(200).setBody(FEED)
                }
        }
        server.start()
        try {
            val source = RemoteSource("id", RemoteSourceType.OPDS, "Lib", server.url("/opds").toString(), "user")
            val page = OpdsClient().loadPage(source, "secret")
            assertEquals("Lib Feed", page.title)
            server.takeRequest()
            assertTrue(server.takeRequest().getHeader("Authorization")!!.startsWith("Basic "))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `digest challenge is answered`() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization")
                return if (auth?.startsWith("Digest ") == true && auth.contains("username=\"user\"")) {
                    MockResponse().setResponseCode(200).setBody(FEED)
                } else {
                    MockResponse().setResponseCode(401)
                        .addHeader("WWW-Authenticate", "Digest realm=\"calibre\", nonce=\"abc\", qop=\"auth\", algorithm=\"MD5\"")
                }
            }
        }
        server.start()
        try {
            val source = RemoteSource("id", RemoteSourceType.OPDS, "Lib", server.url("/opds").toString(), "user")
            assertEquals("Lib Feed", OpdsClient().loadPage(source, "secret").title)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `credentials never follow a redirect to another origin`() = runTest {
        // A CDN that challenges: if credentials leaked, the authenticator would answer it.
        val other = MockWebServer()
        other.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == null) {
                    MockResponse().setResponseCode(200).setBody("PK\u0003\u0004data")
                } else {
                    MockResponse().setResponseCode(500)
                }
        }
        other.start()
        val catalog = MockWebServer()
        catalog.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == null) {
                    MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"opds\"")
                } else {
                    MockResponse().setResponseCode(302).addHeader("Location", other.url("/cdn/book.epub").toString())
                }
        }
        catalog.start()
        try {
            val source = RemoteSource("id", RemoteSourceType.OPDS, "Lib", catalog.url("/opds").toString(), "user")
            val target = File.createTempFile("download", ".part")
            try {
                val result = OpdsClient().download(source, "secret", catalog.url("/get/1").toString(), target, { false }) { _, _ -> }
                assertEquals(8L, result.bytesWritten)
                // Both servers listen on localhost but on different ports, i.e. different origins.
                assertEquals(1, other.requestCount)
                assertNull(other.takeRequest().getHeader("Authorization"))
            } finally {
                target.delete()
            }
        } finally {
            catalog.shutdown()
            other.shutdown()
        }
    }

    @Test
    fun `json catalogs report opds2 as unsupported`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "application/opds+json").setBody("{\"metadata\":{}}"))
        server.start()
        try {
            val source = RemoteSource("id", RemoteSourceType.OPDS, "Lib", server.url("/opds").toString())
            val kind = runCatching { OpdsClient().loadPage(source, null) }.exceptionOrNull() as RemoteException
            assertEquals(RemoteErrorKind.OPDS2_UNSUPPORTED, kind.kind)
        } finally {
            server.shutdown()
        }
    }

    private companion object {
        const val FEED = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Lib Feed</title></feed>"""
    }
}
