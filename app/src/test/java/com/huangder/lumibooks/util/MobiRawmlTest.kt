package com.huangder.lumibooks.util

import com.huangder.lumibooks.util.epub.MobiRawml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiRawmlTest {

    @Test
    fun `splits chapters on pagebreaks`() {
        val rawml = ("<html><body><h1>One</h1><mbp:pagebreak/><h2>Two</h2>" +
            "<mbp:pagebreak/><h3>Three</h3></body></html>").toByteArray(Charsets.UTF_8)
        val ranges = MobiRawml.splitChapters(rawml)
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(ranges[1].first, ranges[0].second)
        assertEquals(ranges[2].first, ranges[1].second)
        assertEquals(rawml.size, ranges[2].second)
        assertTrue(ranges[0].second < ranges[1].second)
    }

    @Test
    fun `splits chapters on headings when no pagebreak`() {
        val rawml = ("<html><body><h1>Title</h1><p>intro</p><h2>Section A</h2><p>a</p>" +
            "<h3>Section B</h3><p>b</p></body></html>").toByteArray(Charsets.UTF_8)
        val ranges = MobiRawml.splitChapters(rawml)
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(rawml.size, ranges[2].second)
    }

    @Test
    fun `single chapter fallback`() {
        val rawml = "<html><body><p>plain text only</p></body></html>".toByteArray(Charsets.UTF_8)
        val ranges = MobiRawml.splitChapters(rawml)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(rawml.size, ranges[0].second)
    }

    @Test
    fun `cleans sent img filepos and pagebreak markup`() {
        val rawml = ("<html><head><guide/></head><body><sent align=\"justify\">hello</sent>" +
            "<img recindex=\"2\"/><a filepos=0000000042>x</a><mbp:pagebreak/></body></html>")
            .toByteArray(Charsets.UTF_8)
        val fragment = MobiRawml.chapterFragment(
            rawml, 0 to rawml.size, 0, Charsets.UTF_8,
            resolveImage = { "image-url-$it" },
            resolveLink = { "link-$it" }
        )
        assertTrue(fragment.contains("<span align=\"justify\">hello</span>"))
        assertTrue(fragment.contains("<img src=\"image-url-2\"/>"))
        assertTrue(fragment.contains("<a href=\"link-42\">x</a>"))
        assertFalse(fragment.contains("pagebreak"))
        assertFalse(fragment.contains("<html>"))
        assertFalse(fragment.contains("<head>"))
        assertFalse(fragment.contains("<sent"))
    }

    @Test
    fun `recindex image and filepos link resolvers can drop content`() {
        val rawml = "<body><img recindex=\"9\"/><a filepos=0000009999>x</a></body>".toByteArray(Charsets.UTF_8)
        val fragment = MobiRawml.chapterFragment(
            rawml, 0 to rawml.size, 0, Charsets.UTF_8,
            resolveImage = { null },
            resolveLink = { null }
        )
        assertFalse(fragment.contains("<img"))
        assertTrue(fragment.contains("<a>x</a>"))
    }

    @Test
    fun `byte to char offset handles multibyte characters`() {
        val rawml = "abcd\u4E2D\u6587efg".toByteArray(Charsets.UTF_8) // abcd中文efg
        val ranges = listOf(0 to rawml.size)
        assertEquals(4, MobiRawml.byteToCharOffset(rawml, ranges[0], Charsets.UTF_8, 4))
        assertEquals(5, MobiRawml.byteToCharOffset(rawml, ranges[0], Charsets.UTF_8, 7))
        assertEquals(9, MobiRawml.byteToCharOffset(rawml, ranges[0], Charsets.UTF_8, rawml.size))
    }

    @Test
    fun `search text skips markup`() {
        val fragment = "<h1>Chapter One</h1><p>Hello <b>MOBI</b> world.</p><img src=\"recindex:1\"/>"
        val text = MobiRawml.searchText(fragment)
        assertTrue(text.contains("Chapter One"))
        assertTrue(text.contains("MOBI"))
        assertFalse(text.contains("<"))
    }

    @Test
    fun `chapter title comes from first heading`() {
        val fragment = "<h2>Chapter Two</h2><p>body</p>"
        assertEquals("Chapter Two", MobiRawml.chapterTitle(fragment, "fallback"))
        assertEquals("fallback", MobiRawml.chapterTitle("<p>no heading</p>", "fallback"))
    }
}
