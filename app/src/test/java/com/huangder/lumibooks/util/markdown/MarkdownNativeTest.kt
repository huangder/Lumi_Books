package com.huangder.lumibooks.util.markdown

import android.app.Application
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.markdown.native.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownNativeTest {
    @Test fun outlineAndMetadataHaveOneLogicalChapter() {
        val doc = MarkdownNativeParser.parse("---\ntitle: Book\nauthor: Me\n---\n" +
            (1..6).joinToString("\n\n") { "#".repeat(it) + " Title" } + "\n\n```md\n# hidden\n```")
        assertEquals("Book", doc.title)
        assertEquals("Me", doc.author)
        assertEquals((1..6).toList(), doc.toc.map { it.level })
        assertTrue(doc.toc.all { it.chapterIndex == 0 })
        assertEquals(listOf("title", "title-2", "title-3", "title-4", "title-5", "title-6"), doc.toc.map { it.anchor })
        doc.outline.forEach { assertTrue(doc.text.substring(it.offset).startsWith(it.title)) }
    }
    @Test fun blockSplittingPreservesTextLinksAndSurrogates() {
        val original = "abc中文😀".repeat(3000)
        val doc = MarkdownNativeParser.parse("[$original][ref]\n\n[ref]: https://example.com")
        assertEquals(original, doc.text.trimEnd())
        assertTrue(doc.blocks.all { it.text.length <= MarkdownNativeParser.MAX_BLOCK_CHARS })
        assertTrue(doc.blocks.all { !it.text.last().isHighSurrogate() })
        assertTrue(doc.blocks.all { it.runs.any { run -> run.style == "link" && run.value == "https://example.com" } })
        doc.blocks.forEach { assertEquals(it.index, doc.index.blockAt(it.start)) }
    }
    @Test fun gfmKeepsTasksTablesFootnotesAndEscapesHtml() {
        val doc = MarkdownNativeParser.parse("- [x] done\n- [ ] todo\n\n~~gone~~\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\ntext[^n]\n\n[^n]: note\n\n<script>alert(1)</script>\n\n[bad](javascript:alert)")
        assertTrue(doc.text, doc.text.contains("☑ done"))
        assertTrue(doc.text, doc.text.contains("☐ todo"))
        assertTrue(doc.blocks.count { it.kind == "table" } >= 2)
        assertTrue(doc.blocks.any { it.runs.any { r -> r.style == "strike" } })
        assertTrue(doc.outline.any { it.anchor == "footnote-n" })
        assertTrue(doc.text.contains("<script>"))
        assertFalse(doc.blocks.any { it.runs.any { r -> r.style == "link" && r.value.startsWith("javascript:") } })
    }
    @Test fun mathIgnoresCodeEscapesAndCurrency() {
        val doc = MarkdownNativeParser.parse("Math ${'$'}x^2${'$'} and \\(y+1\\).\n\n${'$'}${'$'}\na+b\n${'$'}${'$'}\n\n\\[c+d\\]\n\n`${'$'}code${'$'}` and \\${'$'}escaped and ${'$'}5 and ${'$'}10")
        val math = doc.blocks.flatMap { it.runs }.filter { it.style.startsWith("math") }
        assertEquals(listOf("x^2", "y+1", "\na+b\n", "c+d"), math.map { it.value })
    }
    @Test fun renderTwiceHasStableCoordinatesAndListNumbers() = runBlocking {
        val doc = MarkdownNativeParser.parse("3. **first**\n4. *second*\n\nText ~~gone~~ [link](https://example.com)")
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(), doc, emptyMap())
        val style = MarkdownRenderStyle(500, 800, 20f, 0xff111111.toInt(), false)
        try {
            repeat(2) { for (block in doc.blocks) assertEquals(block.text, source.renderBlock(block.index, style).toString()) }
            assertEquals(listOf("3.", "4."), doc.blocks.take(2).map { it.label })
        } finally { source.close() }
    }
    @Test fun pagedTraversalDoesNotDropOrRepeatCharacters() = runBlocking {
        val doc = MarkdownNativeParser.parse("# Heading\n\n" + (1..60).joinToString("\n\n") { "Paragraph $it. " + "中英 mixed words ".repeat(20) })
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(), doc, emptyMap())
        val pager = MarkdownPageProvider(source, MarkdownPageStyle(MarkdownRenderStyle(320, 420, 20f, 0xff111111.toInt(), false, 8), Typeface.DEFAULT, 1.5f, 0f))
        val collected = StringBuilder()
        var offset = 0
        var pages = 0
        try {
            while (offset < doc.text.length) {
                val page = pager.page(offset)
                assertTrue("Page must advance at $offset", page.end > offset)
                page.fragments.forEach { collected.append(it.text) }
                offset = page.end
                assertTrue(++pages < 1000)
            }
            assertEquals(doc.blocks.joinToString("") { it.text }, collected.toString())
        } finally { source.close() }
    }
    @Test fun tableRowsContinueAcrossPagesWithoutLosingCellText() = runBlocking {
        val doc = MarkdownNativeParser.parse("| First | Second |\n|---|---|\n| " + "long cell ".repeat(600) + " | " + "右列文字".repeat(600) + " |\n| end | tail |")
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(), doc, emptyMap())
        val pager = MarkdownPageProvider(source, MarkdownPageStyle(MarkdownRenderStyle(320, 220, 20f, 0xff111111.toInt(), false), Typeface.DEFAULT, 1.5f, 0f))
        val ranges = mutableMapOf<Int, MutableList<IntRange>>()
        var offset = 0
        var count = 0
        try {
            while (offset < doc.text.length) {
                val page = pager.page(offset)
                assertTrue(page.end > offset)
                page.fragments.filterNot { it.repeatedHeader }.forEach { fragment ->
                    fragment.cells.forEach { cell ->
                        if (cell.text.isNotEmpty()) ranges.getOrPut(fragment.block.index) { mutableListOf() }.add(cell.localStart until cell.localStart + cell.text.length)
                    }
                }
                offset = page.end
                assertTrue(++count < 1000)
            }
            doc.blocks.forEach { block ->
                val visited = ranges[block.index].orEmpty().flatMap { it.toList() }
                val expected = block.text.indices.filter { block.text[it] != '\t' }
                assertEquals(expected, visited.sorted())
                assertEquals(visited.size, visited.toSet().size)
            }
        } finally { source.close() }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test(timeout = 20000) fun nativeFormulaAndDataImageRenderWithoutChangingTextOffsets() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val doc = MarkdownNativeParser.parse("${'$'}x^2${'$'}\n\n![test](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aVV4AAAAASUVORK5CYII=)")
        val source = MarkwonBlockSource(ApplicationProvider.getApplicationContext(), doc, emptyMap())
        try {
            val style = MarkdownRenderStyle(320, 480, 20f, 0xff111111.toInt(), false)
            for (block in doc.blocks) {
                val rendered = source.renderBlock(block.index, style)
                assertEquals(block.text, rendered.toString())
                assertTrue(block.runs.toString(), rendered.getSpans(0, rendered.length, android.text.style.ImageSpan::class.java).isNotEmpty())
            }
        } finally { source.close(); Dispatchers.resetMain() }
    }
}
