package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class EpubLogicalChapterSlicerTest {
    private val source = """<html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="book.css"/></head><body class="paper"><div class="chapter"><section><h2 id="a">A</h2><p>First</p></section><section><h2 id="b">B</h2><p>Last</p></section></div></body></html>"""

    @Test fun lastAnchorKeepsShellAndAncestors() {
        val doc = Jsoup.parse(sliceEpubLogicalChapter(source, "b", null), "", Parser.xmlParser())
        assertEquals("BLast", doc.body().text().replace(" ", ""))
        assertNotNull(doc.selectFirst("body.paper > div.chapter > section > h2#b"))
        assertNotNull(doc.selectFirst("head > link[href=book.css]"))
        assertEquals("http://www.w3.org/1999/xhtml", doc.selectFirst("html")!!.attr("xmlns"))
        assertEquals(1, doc.childrenSize())
    }

    @Test fun intervalDoesNotLeakNextChapter() {
        val doc = Jsoup.parse(sliceEpubLogicalChapter(source, "a", "b"), "", Parser.xmlParser())
        assertTrue(doc.body().text().contains("First"))
        assertFalse(doc.body().text().contains("Last"))
        assertNull(doc.getElementById("b"))
    }

    @Test fun invalidBoundariesKeepWholeDocument() {
        for ((start, end) in listOf("missing" to null, "a" to "missing", "b" to "a", "a" to "a")) {
            assertEquals(source, sliceEpubLogicalChapter(source, start, end))
        }
    }
}
