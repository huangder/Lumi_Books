package com.huangder.lumibooks.util.markdown

import com.huangder.lumibooks.util.markdown.native.MarkdownNativeParser
import com.huangder.lumibooks.util.parser.MarkdownParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MarkdownLargeDocumentTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test(timeout = 120000) fun giantParagraphAndCodeRemainBounded() {
        val paragraph = "word **bold** [link](https://example.com) 中文 ".repeat(25000)
        val code = "/* " + "continued comment\n".repeat(70000) + "*/\nval tail = 1"
        val source = paragraph + "\n\n```kotlin\n" + code + "\n```"
        val started = System.nanoTime()
        val doc = MarkdownNativeParser.parse(source)
        assertTrue(doc.blocks.all { it.text.length <= MarkdownNativeParser.MAX_BLOCK_CHARS })
        assertEquals("word bold link 中文 ".repeat(25000).trimEnd(),
            doc.blocks.filter { it.kind == "paragraph" }.joinToString("") { it.text })
        assertEquals(code, doc.blocks.filter { it.kind == "code" }.joinToString("") { it.text })
        assertTrue(doc.blocks.filter { it.kind == "code" }.drop(1).all { it.codeContext == "/*" })
        println("MarkdownGiant bytes=${source.toByteArray().size} blocks=${doc.blocks.size} ms=${(System.nanoTime() - started) / 1_000_000}")
    }

    @Test(timeout = 120000) fun largeDocumentsAreIndexedInBoundedBlocksAndReopenFromCache() {
        val sample = "# Heading\n\n" + "Native Markdown mixed 中文 text **bold** [link](https://example.com). ".repeat(35) +
            "\n\n```kotlin\nval answer = 42\n```\n\n| Left | Right |\n|---|---|\n| one | two |\n\n"
        for (mb in listOf(1, 5, 20)) {
            val source = File(temporary.root, "$mb.md")
            source.bufferedWriter().use { writer ->
                repeat((mb * 1024 * 1024) / sample.toByteArray().size + 1) { writer.write(sample) }
                writer.write("\nLUMI-NATIVE-END-$mb")
            }
            val parser = MarkdownParser()
            try {
                val start = System.nanoTime()
                val content = parser.parse(source.path)
                val cold = (System.nanoTime() - start) / 1_000_000
                assertEquals(1, content.chapters.size)
                assertTrue(parser.searchText.contains("LUMI-NATIVE-END-$mb"))
                assertTrue(parser.document!!.blocks.all { it.text.length <= MarkdownNativeParser.MAX_BLOCK_CHARS })
                val count = parser.document!!.blocks.size
                val warmStart = System.nanoTime()
                parser.parse(source.path)
                val warm = (System.nanoTime() - warmStart) / 1_000_000
                assertEquals(count, parser.document!!.blocks.size)
                assertFalse(temporary.root.walkTopDown().any { it.extension == "epub" })
                println("MarkdownIndex bytes=${source.length()} blocks=$count coldMs=$cold warmMs=$warm")
            } finally { parser.close() }
        }
    }
}
