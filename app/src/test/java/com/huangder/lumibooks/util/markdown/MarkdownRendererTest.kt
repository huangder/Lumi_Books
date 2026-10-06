package com.huangder.lumibooks.util.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun headingsProduceStableOutlineAndUniqueAnchors() {
        val result = MarkdownRenderer.render("""
            # Same
            ## Same
            ### Third
            #### Fourth
            ##### Fifth
            ###### Sixth
            # Same
        """.trimIndent())
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1), result.headings.map { it.level })
        assertEquals(listOf("same", "same-2", "third", "fourth", "fifth", "sixth", "same-3"), result.headings.map { it.anchor })
        assertEquals(7, result.html.split(" id=\"").size - 1)
    }

    @Test
    fun fencedHashAndFrontMatterDoNotBecomeHeadings() {
        val result = MarkdownRenderer.render("""
            ---
            title: Book title
            author: Writer
            ---
            ```md
            # inside code
            ```
            Body
        """.trimIndent())
        assertEquals("Book title", result.title)
        assertEquals("Writer", result.author)
        assertTrue(result.headings.isEmpty())
        assertTrue(result.html.contains("# inside code"))
    }

    @Test
    fun gfmAndUnsafeMarkupAreHandled() {
        val result = MarkdownRenderer.render("""
            - [x] done
            - [ ] todo

            ~~removed~~

            | A | B |
            |---|---|
            | 1 | 2 |
            <script>alert(1)</script>
            <img src="https://example.com/a.png" alt="remote">

            ![remote](https://example.com/a.png)
        """.trimIndent())
        assertTrue(result.html.contains("☑"))
        assertTrue(result.html.contains("☐"))
        assertTrue(result.html.contains("<del>removed</del>"))
        assertTrue(result.html.contains("<table"))
        assertFalse(result.html.contains("<script"))
        assertTrue(result.html.contains("md-missing-image"))
    }

    @Test
    fun footnotesAndAutolinksRemainSafeInternalAndExternalLinks() {
        val result = MarkdownRenderer.render("See https://example.org and footnote[^1].\n\n[^1]: Footnote text")
        assertTrue(result.html.contains("href=\"https://example.org\""))
        assertTrue(result.html.contains("href=\"#"))
        assertTrue(result.html.contains("Footnote text"))
        val unsafe = MarkdownRenderer.render("[bad](javascript:alert%281%29)\n\n<iframe src=\"https://example.org\"></iframe>")
        assertFalse(unsafe.html.contains("href=\"javascript:"))
        assertFalse(unsafe.html.contains("<iframe"))
    }

    @Test
    fun relativeImagePathIsValidated() {
        assertEquals("images/a.png", MarkdownRenderer.safeRelativePath("images/a.png"))
        assertEquals(null, MarkdownRenderer.safeRelativePath("../a.png"))
        assertEquals(null, MarkdownRenderer.safeRelativePath("%2e%2e/a.png"))
        assertEquals(null, MarkdownRenderer.safeRelativePath("https://example.com/a.png"))
    }
}
