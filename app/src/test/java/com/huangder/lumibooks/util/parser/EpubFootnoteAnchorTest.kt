package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canvas 引擎（阅读器排版）注释气泡的识别与正文提取。
 * 与 WebView 版（EpubDocumentTransformer 注入 JS）的启发式保持一致。
 */
class EpubFootnoteAnchorTest {

    private fun classify(openTag: String, inner: String) =
        EpubParser.isFootnoteAnchorTag(openTag, inner)

    @Test
    fun `bracketed number labels are footnote references`() {
        assertTrue(classify("""<a href="#note-01">""", "[01]"))
        assertTrue(classify("""<a href="notes.xhtml#n1">""", "[1]"))
        assertTrue(classify("""<a href="#n">""", "［2］"))
        assertTrue(classify("""<a href="#n">""", "【3】"))
        assertTrue(classify("""<a href="#n">""", "〔4〕"))
        assertTrue(classify("""<a href="#n">""", "[０１]"))
        assertTrue(classify("""<a href="#n">""", "[1][2]"))
        assertTrue(classify("""<a href="#n">""", "[*]"))
        assertTrue(classify("""<a href="#n">""", "*"))
        assertTrue(classify("""<a href="#n">""", "①"))
        assertTrue(classify("""<a href="#n" class="zhang">""", " <sup> [ 01 ] </sup> "))
        // 《毛泽东选集》的真实格式：class="zy"，标记为〔1〕或 *
        assertTrue(classify("""<a class="zy" href="#id1a" id="id1">""", "〔1〕"))
        assertTrue(classify("""<a class="zy" href="#id0a" id="id0">""", "*"))
    }

    @Test
    fun `semantic and hint attributes are footnote references`() {
        assertTrue(classify("""<a epub:type="noteref" role="doc-noteref" href="#fn1">""", "1"))
        assertTrue(classify("""<a class="duokan-footnote" href="#ref_end_1">""", "1"))
        assertTrue(classify("""<a id="fnref2" href="#fn2">""", "2"))
        assertTrue(classify("""<a title="footnote 3" href="#x">""", "3"))
        assertTrue(classify("""<a href="#fn3">""", "参见"))
    }

    @Test
    fun `note bodies backlinks and plain links are not references`() {
        // 自身是注释正文/返回链接
        assertFalse(classify("""<a epub:type="footnote" id="fn1" href="#ref1">""", "注释正文"))
        assertFalse(classify("""<a role="doc-backlink" href="#ref1">""", "↩"))
        // 普通链接
        assertFalse(classify("""<a href="#toc">""", "目录"))
        assertFalse(classify("""<a href="chapter2.xhtml">""", "下一章"))
        assertFalse(classify("""<a href="https://example.com">""", "[01]"))
        assertFalse(classify("""<a href="#n">""", "[1234]"))
        assertFalse(classify("""<a href="#n">""", "[1]注"))
        assertFalse(classify("""<a href="#n">""", "第一章"))
    }

    @Test
    fun `extracts footnote body text and strips backlinks`() {
        val html = """
            <html><body>
            <p>正文<a id="ref1" href="#note-1">[01]</a>继续</p>
            <aside id="note-1" epub:type="footnote">
              <p>[01] 这是一条注释正文。</p>
              <a epub:type="backlink" role="doc-backlink" href="#ref1">↩ 返回</a>
            </aside>
            </body></html>
        """.trimIndent()

        val text = EpubParser.extractFootnoteElementText(html, "note-1")

        assertNotNull(text)
        assertTrue("应包含注释正文: $text", text!!.contains("这是一条注释正文"))
        assertFalse("应移除返回链接: $text", text.contains("返回"))
        assertFalse("应移除返回箭头: $text", text.contains("↩"))
    }

    @Test
    fun `resolves target by name attribute and case-insensitive id`() {
        val byName = EpubParser.extractFootnoteElementText(
            """<p><a name="note-9">[9]</a> name 锚点的注释。</p>""",
            "note-9"
        )
        assertNotNull(byName)
        assertTrue("应取到父段落正文: $byName", byName!!.contains("name 锚点的注释"))

        val byCase = EpubParser.extractFootnoteElementText(
            """<p id="Note-9">大小写不同的注释。</p>""",
            "note-9"
        )
        assertNotNull(byCase)
    }

    @Test
    fun `extracts mao xuanji inline note body`() {
        // 《毛泽东选集》真实结构：注释正文是 <p> 内的行内锚点
        val html = """
            <html><body>
            <p>其政治代表是国家主义派<a class="zy" href="#id1a" id="id1">〔1〕</a>和国民党右派。</p>
            <p class="zs"><a class="hl" href="#id1" id="id1a">〔1〕</a>国家主义派指中国青年党，当时以其外围组织进行活动。</p>
            </body></html>
        """.trimIndent()

        val text = EpubParser.extractFootnoteElementText(html, "id1a")

        assertNotNull(text)
        assertTrue("应取到父段落注释正文: $text", text!!.contains("国家主义派指中国青年党"))
    }

    @Test
    fun `missing or empty target returns null`() {
        assertNull(EpubParser.extractFootnoteElementText("<p>正文</p>", "nope"))
        assertNull(EpubParser.extractFootnoteElementText("""<p id="empty"></p>""", "empty"))
        assertNull(EpubParser.extractFootnoteElementText("<p>正文</p>", ""))
    }
}
