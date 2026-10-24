package com.huangder.lumibooks.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavListingParserTest {
    private val collection = "https://dav.example.com/remote.php/dav/files/me/Books/".toHttpUrl()

    @Test
    fun `pretty printed listing keeps folders and files and skips the collection itself`() {
        val entries = WebdavListingParser.parse(PRETTY_LISTING.toByteArray(), collection)
        assertEquals(listOf("小说", "Sample Book.epub"), entries.map { it.name })
        val folder = entries[0]
        assertTrue(folder.isCollection)
        assertTrue(folder.url.encodedPath.endsWith("/"))
        val file = entries[1]
        assertFalse(file.isCollection)
        assertEquals(2048L, file.sizeBytes)
        assertTrue(file.lastModified > 0L)
    }

    @Test
    fun `absolute hrefs on the same origin are accepted and other hosts dropped`() {
        val entries = WebdavListingParser.parse(ABSOLUTE_LISTING.toByteArray(), collection)
        assertEquals(listOf("a.pdf"), entries.map { it.name })
    }

    @Test
    fun `deeper rows from depth infinity servers are ignored`() {
        val entries = WebdavListingParser.parse(DEEP_LISTING.toByteArray(), collection)
        assertEquals(listOf("top.txt"), entries.map { it.name })
    }

    private companion object {
        const val PRETTY_LISTING = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/remote.php/dav/files/me/Books/</d:href>
    <d:propstat>
      <d:prop>
        <d:resourcetype>
          <d:collection/>
        </d:resourcetype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/me/Books/%E5%B0%8F%E8%AF%B4/</d:href>
    <d:propstat>
      <d:prop>
        <d:resourcetype>
          <d:collection/>
        </d:resourcetype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/me/Books/Sample%20Book.epub</d:href>
    <d:propstat>
      <d:prop>
        <d:resourcetype/>
        <d:getcontentlength>2048</d:getcontentlength>
        <d:getlastmodified>Mon, 02 Jan 2006 15:04:05 GMT</d:getlastmodified>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
    <d:propstat>
      <d:prop><d:displayname/></d:prop>
      <d:status>HTTP/1.1 404 Not Found</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"""

        const val ABSOLUTE_LISTING = """<?xml version="1.0"?>
<D:multistatus xmlns:D="DAV:">
  <D:response><D:href>https://dav.example.com/remote.php/dav/files/me/Books</D:href>
    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
  <D:response><D:href>https://dav.example.com/remote.php/dav/files/me/Books/a.pdf</D:href>
    <D:propstat><D:prop><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
  <D:response><D:href>https://evil.example.net/remote.php/dav/files/me/Books/b.pdf</D:href>
    <D:propstat><D:prop><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
</D:multistatus>"""

        const val DEEP_LISTING = """<?xml version="1.0"?>
<D:multistatus xmlns:D="DAV:">
  <D:response><D:href>/remote.php/dav/files/me/Books/top.txt</D:href>
    <D:propstat><D:prop><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
  <D:response><D:href>/remote.php/dav/files/me/Books/sub/deep.txt</D:href>
    <D:propstat><D:prop><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
</D:multistatus>"""
    }
}
