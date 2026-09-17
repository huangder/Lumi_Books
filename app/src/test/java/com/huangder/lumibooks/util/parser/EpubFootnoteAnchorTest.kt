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
    fun `recognizes compound duokan footnote bookmarks and excludes backlinks`() {
        assertTrue(
            classify(
                """<a href="text00019.html#ref_footnotebookmark_end_1_1" id="ref_footnotebookmark_start_1_1">""",
                "<img alt='' src='Image00014.png' />"
            )
        )
        assertFalse(
            classify(
                """<a class="calibre4" href="text00007.html#ref_footnotebookmark_start_1_1">""",
                "注释正文"
            )
        )
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

    @Test
    fun `footnote marker icons stay inline while illustration images keep block breaks`() {
        val html = """
            <p>正文<a epub:type="noteref" href="#fn1"><img src="note.png" class="epub-footnote"/></a>继续</p>
            <p><img src="figure.png"/></p>
        """.trimIndent()

        val (protectedHtml, images) = EpubParser.protectInlineFootnoteImages(html)

        assertEquals(1, images.size)
        assertTrue("注释图标应被替换为占位符: $protectedHtml", protectedHtml.contains("<img src=\"figure.png\"/>"))
        assertFalse("注释图标不应被替换: $protectedHtml", protectedHtml.contains("note.png"))

        // 只有插图前后会插入换行成为块级；注释图标还原后仍与正文同行。
        var withBreaks = EpubParser.IMAGE_TAG_REGEX.replace(protectedHtml, "\n$0\n")
        images.forEachIndexed { index, image ->
            withBreaks = withBreaks.replace(EpubParser.inlineImagePlaceholder(index), image)
        }
        assertTrue(withBreaks.contains("\n<img src=\"figure.png\"/>\n"))
        assertTrue(
            "注释图标应保持行内: $withBreaks",
            withBreaks.contains("""href="#fn1"><img src="note.png" class="epub-footnote"/></a>继续""")
        )
    }

    @Test
    fun `footnote marker anchors drop edge padding so no stray underline is drawn`() {
        // 《一生之敌》真实结构：<a epub:type="noteref"> 与图标之间留了一个空格，
        // 该空格同样落在 URLSpan 内，会被画成紧贴图标左侧的一小段下划线。
        val html = """
            <p>内阻力就像是《大白鲨》
            <sup><a epub:type="noteref" href="#footnote-11-23"> <img src="../images/image_003.png" alt="注释" class="epub-footnote1"/></a></sup>里的那条鲨鱼。</p>
            <p><a title="footnote 3" href="#fn3">&nbsp;<sup>[ 3 ]</sup>&#160;</a></p>
        """.trimIndent()

        val (protectedHtml, images) = EpubParser.protectInlineFootnoteImages(html)

        assertEquals(1, images.size)
        assertTrue(
            "图标两侧空白应被去掉: $protectedHtml",
            protectedHtml.contains(
                """<a epub:type="noteref" href="#footnote-11-23">${EpubParser.inlineImagePlaceholder(0)}</a>"""
            )
        )
        assertTrue("实体空白也应被去掉: $protectedHtml", protectedHtml.contains("""href="#fn3"><sup>[ 3 ]</sup></a>"""))
        assertTrue(protectedHtml.contains("内阻力就像是《大白鲨》"))
        assertTrue(protectedHtml.contains("里的那条鲨鱼。"))
    }

    @Test
    fun `referenced footnote containers are removed from the reader text`() {
        // 真实结构：注释容器嵌在章节外层 div 里（外层容器不能把内层一起吞掉）
        val html = """
            <div class="calibre3">
            <p>正文<a epub:type="noteref" href="#fn1">[1]</a></p>
            <aside epub:type="footnote" id="fn1">约翰·李·胡克（1917.8.22—2001.6.21），美国布鲁斯音乐大师。</aside>
            <div id="section3">没有注释语义的同名 div 必须保留</div>
            <p id="fn2">另一段没有被引用的注释</p>
            </div>
        """.trimIndent()

        val stripped = EpubParser.stripFootnoteBodies(html, setOf("fn1", "section3"))

        assertFalse("被引用的注释容器应移除: $stripped", stripped.contains("美国布鲁斯音乐大师"))
        assertTrue("没有引用标记的段落必须保留: $stripped", stripped.contains("另一段没有被引用的注释"))
        assertTrue("正文引用必须保留: $stripped", stripped.contains("正文"))
        assertTrue("外层容器必须保留: $stripped", stripped.contains("calibre3"))
        assertTrue("无注释语义的 div 必须保留: $stripped", stripped.contains("没有注释语义的同名 div 必须保留"))

        // 没有检测到引用 fragment 时不做任何删除
        assertEquals(html, EpubParser.stripFootnoteBodies(html, emptySet()))
    }

    @Test
    fun `paragraph shaped note bodies are removed`() {
        val html = """
            <div class="calibre3">
            <p>正文<a epub:type="noteref" href="#note-1">[1]</a>继续</p>
            <p id="note-1">[1] 这是一条按段落排版的注释正文。</p>
            <p id="note-2">没有被引用的段落必须保留</p>
            </div>
        """.trimIndent()

        val stripped = EpubParser.stripFootnoteBodies(html, setOf("note-1"))

        assertFalse("段落形态的注释正文应移除: $stripped", stripped.contains("按段落排版的注释正文"))
        assertTrue("其它段落必须保留: $stripped", stripped.contains("没有被引用的段落必须保留"))
        assertTrue("正文必须保留: $stripped", stripped.contains("继续"))
    }

    @Test
    fun `inline anchor at paragraph start marks a note body paragraph`() {
        // 《毛泽东选集》真实结构：注释正文是 <p> 内开头的行内锚点
        val html = """
            <div class="calibre3">
            <p>其政治代表是国家主义派<a class="zy" href="#id1a" id="id1">〔1〕</a>和国民党右派。</p>
            <p class="zs"><a class="hl" href="#id1" id="id1a">〔1〕</a>国家主义派指中国青年党，当时以其外围组织进行活动。</p>
            <p>后面还有正文。</p>
            </div>
        """.trimIndent()

        val stripped = EpubParser.stripFootnoteBodies(html, setOf("id1a"))

        assertFalse("注释段落应移除: $stripped", stripped.contains("国家主义派指中国青年党"))
        assertTrue("引用所在正文段必须保留: $stripped", stripped.contains("其政治代表是国家主义派"))
        assertTrue("引用后的正文必须保留: $stripped", stripped.contains("后面还有正文"))
        assertTrue("外层容器必须保留: $stripped", stripped.contains("calibre3"))
    }

    @Test
    fun `inline anchors in the middle of a paragraph are not treated as note bodies`() {
        val html = """<p>正文开头<a id="mid">（锚点）</a>后面还有很长的正文内容。</p>"""

        val stripped = EpubParser.stripFootnoteBodies(html, setOf("mid"))

        assertEquals(html, stripped)
    }

    @Test
    fun `note paragraph may start with a bracketed number before the anchor`() {
        val html = """
            <p>正文<a class="zy" href="#n1" id="r1">〔1〕</a></p>
            <p class="zs">〔1〕<a id="n1"> </a>国家主义派指中国青年党，当时以其外围组织进行活动。</p>
        """.trimIndent()

        val stripped = EpubParser.stripFootnoteBodies(html, setOf("n1"))

        assertFalse("带编号前缀的注释段应移除: $stripped", stripped.contains("国家主义派指中国青年党"))
        assertTrue("正文必须保留: $stripped", stripped.contains("正文"))
    }

    @Test
    fun `self referencing marker paragraph is never removed`() {
        // 引用锚点自身带 id 且 href 指向自己时必须保留，避免整段正文被删掉
        val html = """<p>正文开头<a id="fn1" href="#fn1">[1]</a>后面还有正文内容。</p>"""

        assertEquals(html, EpubParser.stripFootnoteBodies(html, setOf("fn1")))
    }

    @Test
    fun `mis-linked and id-less footnote bodies are paired in document order`() {
        // 《一生之敌》真实结构：第 3 条注释正文没有 id，且引用 href 重复指向第 1 条
        val html = """
            <p>正文一<a epub:type="noteref" href="#footnote-4-50"><img src="a.png" class="epub-footnote"/></a>继续</p>
            <aside epub:type="footnote" id="footnote-4-50">史蒂文·普莱斯菲尔德在1995年出版的畅销小说。</aside>
            <aside epub:type="footnote" id="footnote-4-30">史蒂文·普莱斯菲尔德创作的历史小说。</aside>
            <aside epub:type="footnote">约翰·斯坦贝克于1933年创作的青少年小说。</aside>
            <p>正文二<a epub:type="noteref" href="#footnote-4-30"><img src="b.png" class="epub-footnote"/></a>继续</p>
            <p>正文三<a epub:type="noteref" href="#footnote-4-50"><img src="c.png" class="epub-footnote"/></a>继续</p>
        """.trimIndent()

        val aligned = EpubParser.alignFootnoteReferences(html, "lumi-footnote-0-")

        val syntheticHref = aligned.textByHref.keys.single()
        assertTrue("合成 id 应带注释语义: $syntheticHref", syntheticHref.startsWith("#lumi-footnote"))
        assertTrue(
            "第 3 条引用应取到自己那段注释: ${aligned.textByHref}",
            aligned.textByHref.getValue(syntheticHref).contains("约翰·斯坦贝克")
        )
        assertTrue("引用 href 应改写到配对正文: ${aligned.html}", aligned.html.contains("""href="$syntheticHref""""))

        val fragments = aligned.hrefs.mapNotNull { EpubParser.footnoteFragmentOf(it) }.toSet()
        val stripped = EpubParser.stripFootnoteBodies(aligned.html, fragments)
        assertFalse("第 1 条注释正文应移除: $stripped", stripped.contains("1995年出版的畅销小说"))
        assertFalse("第 2 条注释正文应移除: $stripped", stripped.contains("创作的历史小说"))
        assertFalse("第 3 条（无 id）注释正文应移除: $stripped", stripped.contains("约翰·斯坦贝克"))
        assertTrue("正文必须保留: $stripped", stripped.contains("正文一"))
        assertTrue("正文必须保留: $stripped", stripped.contains("正文三"))
    }

    @Test
    fun `chapter without footnote references is left untouched`() {
        val html = """<p>普通正文<a href="#chapter2">下一章</a></p><aside id="x">不是注释</aside>"""

        val aligned = EpubParser.alignFootnoteReferences(html, "lumi-footnote-0-")

        assertEquals(html, aligned.html)
        assertTrue(aligned.hrefs.isEmpty())
        assertTrue(aligned.textByHref.isEmpty())
    }
}
