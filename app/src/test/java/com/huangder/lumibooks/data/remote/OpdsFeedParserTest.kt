package com.huangder.lumibooks.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpdsFeedParserTest {
    private val base = "https://books.example.com/opds/root.xml".toHttpUrl()

    @Test
    fun `navigation entries become folders with resolved links`() {
        val page = OpdsFeedParser.parse(NAVIGATION_FEED.toByteArray(), base, "src")
        assertEquals("Example Library", page.title)
        assertEquals(2, page.folders.size)
        assertEquals("https://books.example.com/opds/new", page.folders[0].url)
        assertEquals("Newest books", page.folders[0].subtitle)
        assertEquals("https://books.example.com/covers/newest.jpg", page.folders[0].coverUrl)
        assertEquals("https://books.example.com/authors", page.folders[1].url)
        assertTrue(page.books.isEmpty())
    }

    @Test
    fun `acquisition entry exposes metadata and prefers epub`() {
        val page = OpdsFeedParser.parse(ACQUISITION_FEED.toByteArray(), base, "src")
        val book = page.books.single()
        assertEquals("opds:urn:uuid:book-1", book.key)
        assertEquals("The Sample Book", book.title)
        assertEquals(listOf("Ada Writer", "Bo Second"), book.authors)
        assertEquals("Line one\nLine two", book.summary)
        assertEquals("Example Press", book.publisher)
        assertEquals("en", book.language)
        assertEquals("2021-04-01", book.published)
        assertEquals(listOf("Fiction", "History"), book.categories)
        assertEquals("https://books.example.com/covers/1.jpg", book.coverUrl)
        assertEquals("https://books.example.com/covers/1-thumb.jpg", book.thumbnailUrl)
        assertEquals("epub", book.preferredFormat?.extension)
        assertEquals(1_048_576L, book.preferredFormat?.sizeBytes)
        assertEquals(listOf("epub", "pdf"), book.supportedFormats.map { it.extension })
        // AZW3 is listed for the details page but never offered as a download.
        assertEquals("AZW3", book.formats.single { !it.isSupported }.label)
    }

    @Test
    fun `paid borrow and drm acquisitions are skipped`() {
        val page = OpdsFeedParser.parse(NON_FREE_FEED.toByteArray(), base, "src")
        val book = page.books.single()
        assertTrue(book.formats.isEmpty())
        assertNull(book.preferredFormat)
    }

    @Test
    fun `next link and search template are resolved`() {
        val page = OpdsFeedParser.parse(ACQUISITION_FEED.toByteArray(), base, "src")
        assertEquals("https://books.example.com/opds/root.xml?page=2", page.nextUrl)
        assertEquals("https://books.example.com/search?q={searchTerms}", page.searchTemplate)
    }

    @Test
    fun `opensearch description link is marked for a follow-up fetch`() {
        val page = OpdsFeedParser.parse(OPENSEARCH_LINK_FEED.toByteArray(), base, "src")
        assertEquals(
            OpdsFeedParser.OPENSEARCH_PREFIX + "https://books.example.com/opensearch.xml",
            page.searchTemplate
        )
        val description = """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <Url type="text/html" template="/html?q={searchTerms}"/>
              <Url type="application/atom+xml" template="/opds/search/{searchTerms}?start={startPage?}"/>
            </OpenSearchDescription>
        """.trimIndent()
        assertEquals(
            "https://books.example.com/opds/search/{searchTerms}?start=",
            OpdsFeedParser.parseOpenSearchTemplate(description.toByteArray(), "https://books.example.com/opensearch.xml".toHttpUrl())
        )
    }

    @Test
    fun `doctype is rejected`() {
        val hostile = """<?xml version="1.0"?><!DOCTYPE feed [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>&x;</title></feed>"""
        try {
            OpdsFeedParser.parse(hostile.toByteArray(), base, "src")
            fail("DOCTYPE must be rejected")
        } catch (error: RemoteException) {
            assertEquals(RemoteErrorKind.NOT_A_CATALOG, error.kind)
        }
    }

    @Test
    fun `html and json payloads are not treated as atom`() {
        assertFalse(OpdsFeedParser.looksLikeAtom("<html><body>Login</body></html>".toByteArray()))
        assertTrue(OpdsFeedParser.looksLikeAtom(NAVIGATION_FEED.toByteArray()))
        assertTrue(OpdsFeedParser.looksLikeJson("""  {"metadata":{}}""".toByteArray(), null))
        assertTrue(OpdsFeedParser.looksLikeJson("x".toByteArray(), "application/opds+json"))
    }

    private companion object {
        const val NAVIGATION_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <id>root</id>
  <title>Example Library</title>
  <entry>
    <title>New</title>
    <id>new</id>
    <content type="text">Newest books</content>
    <link rel="subsection" href="new" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
    <link rel="http://opds-spec.org/image/thumbnail" href="/covers/newest.jpg" type="image/jpeg"/>
  </entry>
  <entry>
    <title>Authors</title>
    <id>authors</id>
    <link href="/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  </entry>
  <entry>
    <title>Website</title>
    <id>web</id>
    <link rel="alternate" href="/web" type="text/html"/>
  </entry>
</feed>"""

        const val ACQUISITION_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Books</title>
  <link rel="next" href="?page=2" type="application/atom+xml;profile=opds-catalog"/>
  <link rel="search" href="/search?q={searchTerms}" type="application/atom+xml"/>
  <entry>
    <title>The Sample Book</title>
    <id>urn:uuid:book-1</id>
    <author><name>Ada Writer</name></author>
    <author><name>Bo Second</name></author>
    <dc:publisher>Example Press</dc:publisher>
    <dc:language>en</dc:language>
    <dc:issued>2021-04-01</dc:issued>
    <category term="fic" label="Fiction"/>
    <category term="History"/>
    <content type="html">&lt;p&gt;Line one&lt;/p&gt;&lt;p&gt;Line two&lt;/p&gt;</content>
    <link rel="http://opds-spec.org/image" href="/covers/1.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/image/thumbnail" href="/covers/1-thumb.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/get/1.pdf" type="application/pdf"/>
    <link rel="http://opds-spec.org/acquisition/open-access" href="/get/1.epub" type="application/epub+zip" length="1048576"/>
    <link rel="http://opds-spec.org/acquisition" href="/get/1.azw3" type="application/x-mobi8-ebook"/>
  </entry>
</feed>"""

        const val NON_FREE_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Store</title>
  <entry>
    <title>Paid</title>
    <id>paid</id>
    <link rel="http://opds-spec.org/acquisition/buy" href="/buy/1" type="application/epub+zip"/>
    <link rel="http://opds-spec.org/acquisition/borrow" href="/borrow/1" type="application/epub+zip"/>
    <link rel="http://opds-spec.org/acquisition" href="/drm/1" type="application/vnd.adobe.adept+xml"/>
    <link rel="http://opds-spec.org/acquisition" href="/lcp/1" type="application/atom+xml">
      <opds:indirectAcquisition type="application/vnd.readium.lcp.license.v1.0+json">
        <opds:indirectAcquisition type="application/epub+zip"/>
      </opds:indirectAcquisition>
    </link>
  </entry>
</feed>"""

        const val OPENSEARCH_LINK_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Calibre</title>
  <link rel="search" href="/opensearch.xml" type="application/opensearchdescription+xml"/>
</feed>"""
    }
}
