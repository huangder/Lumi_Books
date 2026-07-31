package com.huangder.lumibooks.util.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubPathResolverTest {
    @Test
    fun normalizesEncodedAndRelativePaths() {
        assertEquals("OPS/Text/chapter 1.xhtml", EpubPathResolver.normalize("OPS/./Text/chapter%201.xhtml?x=1#top"))
        assertEquals("OPS/Images/cover.png", EpubPathResolver.resolve("OPS/Text/chapter.xhtml", "../Images/cover.png#image"))
        assertEquals("section 1", EpubPathResolver.fragment("chapter.xhtml#section%201"))
    }

    @Test
    fun rejectsTraversalAbsolutePathsAndSchemes() {
        assertNull(EpubPathResolver.normalize("../../outside.txt"))
        assertNull(EpubPathResolver.normalize("/etc/passwd"))
        assertNull(EpubPathResolver.normalize("file:///etc/passwd"))
        assertNull(EpubPathResolver.resolve("OPS/chapter.xhtml", "../../../outside.txt"))
        assertNull(EpubPathResolver.resolve("OPS/chapter.xhtml", "https://example.com/book.css"))
        assertNull(EpubPathResolver.normalize("OPS/%2e%2e/%2e%2e/outside.txt"))
    }

    @Test
    fun rejectsNullBytesAndWindowsStyleTraversal() {
        assertNull(EpubPathResolver.normalize("OPS/Text/%00chapter.xhtml"))
        assertNull(EpubPathResolver.normalize("..\\..\\secret.txt"))
    }
}
