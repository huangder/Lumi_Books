package com.huangder.lumibooks.util.epub

import org.junit.Assert.*
import org.junit.Test

class NativeEpubDocumentTest {
    private fun compile(body: String, css: String = "", fixed: Boolean = false) = NativeEpubCompiler.compile(
        NativeEpubSource("OPS/chapter.xhtml", EpubResource("OPS/chapter.xhtml", "application/xhtml+xml",
            "<html><head></head><body>$body</body></html>".toByteArray()), listOf("OPS/book.css" to css),
            if (fixed) EpubRenditionLayout.PRE_PAGINATED else EpubRenditionLayout.REFLOWABLE))

    @Test fun specificityImportantInlineAndRelativeInheritance() {
        val doc = compile("<p id='a' class='x' style='color:green'>A<span>B<b>C</b></span></p>",
            "#a{color:red;font-size:2em}.x{color:blue}p{color:black}span{font-size:50%}b{color:white!important}").document!!
        val a = doc.runs.first { doc.text.substring(it.start, it.end) == "A" }.style
        val b = doc.runs.first { doc.text.substring(it.start, it.end) == "B" }.style
        val c = doc.runs.first { doc.text.substring(it.start, it.end) == "C" }.style
        assertEquals("green", a.color); assertEquals(32f, a.size)
        assertEquals(16f, b.size); assertEquals(16f, c.size); assertEquals(700, c.weight); assertEquals("white", c.color)
        val important = compile("<p id='a' style='color:green'>A</p>", "#a{color:red!important}p{color:blue!important}").document!!
        assertEquals("red", important.runs.first().style.color)
    }
    @Test fun declarationAndStylesheetOrderAndMarginExpansion() {
        val doc = compile("<p>A</p>", "p{margin:1em 2em 3em 4em;margin-left:5px;color:red;color:blue}").document!!
        val st = doc.blocks.single().style
        assertEquals(16f, st.marginTop); assertEquals(32f, st.marginRight)
        assertEquals(48f, st.marginBottom); assertEquals(5f, st.marginLeft); assertEquals("blue", st.color)
    }
    @Test fun rubyAndWhitespaceKeepRawDomCoordinates() {
        val doc = compile("\n<p id='p'>甲  <ruby>漢<rt>かん</rt></ruby> 字😀</p>\n").document!!
        assertTrue(doc.text.contains("甲 漢 字😀")); assertFalse(doc.text.contains("かん"))
        val offset = doc.text.indexOf('字'); val raw = doc.rawText.indexOf('字')
        assertEquals(raw, doc.sourcePosition(offset))
        val locator = doc.locator(offset); assertEquals(offset, doc.resolve(locator))
        assertEquals(raw, locator.textPosition); assertTrue(locator.domPath.isNotEmpty())
        val quoted = locator.copy(textPosition = 0, domPath = emptyList())
        assertEquals(offset, doc.resolve(quoted))
        assertEquals(1, doc.ruby.size)
        assertTrue((0 until (doc.sourceOffsets.size - 1)).all { i: Int -> doc.sourceOffsets[i] <= doc.sourceOffsets[i + 1] })
        assertEquals(doc.rawText.length, doc.sourcePosition(doc.text.length))
        assertEquals(0, doc.anchors["p"])
    }
    @Test fun unsupportedSemanticsFallBackPerChapter() {
        val inputs = listOf("<table><tr><td>A</td></tr></table>" to "",
            "<p>A</p>" to "p{float:left}", "<p>A</p>" to "p{position:absolute}",
            "<p>A</p>" to "p{column-count:2}", "<p>A</p>" to "p::before{content:'A'}",
            "<p>A</p>" to "@media(max-width:600px){p{color:red}}",
            "<p>A</p>" to "p{padding:2em}", "<p>A</p>" to "p{width:8em}",
            "<p>A</p>" to "p{border:1px solid black}", "<p>A</p>" to "p{opacity:0}",
            "<p>A</p>" to "p{white-space:pre}", "<p>A</p>" to "p{background:url(pic.png)}",
            "<script>bad()</script><p>A</p>" to "",
            "<p><svg><text>A</text></svg></p>" to "", "<p style='font-size:calc(2em + 1px)'>A</p>" to "")
        inputs.forEach { (body, css) -> val result = compile(body, css)
            assertEquals("$body / $css", ChapterRenderEngine.WEBVIEW, result.decision.engine)
            assertNull(result.document); assertNotNull(result.decision.reason) }
        assertEquals(ChapterRenderEngine.WEBVIEW, compile("<p>A</p>", fixed = true).decision.engine)
        assertEquals(ChapterRenderEngine.NATIVE, compile("<p>A</p>", "p{padding:0;text-justify:inter-ideograph}").decision.engine)
        assertEquals(ChapterRenderEngine.NATIVE, compile("<p>A</p>").decision.engine)
    }
    @Test fun screenMediaAndOptionalFontFacesUseNativeFlow() {
        assertEquals(ChapterRenderEngine.NATIVE,
            compile("<p>A</p>", "@charset 'UTF-8'; @media screen { p{color:red} }").decision.engine)
        val fontFace = compile("<p>A</p>", "@font-face{font-family:book;src:url(book.ttf)} p{font-family:book}")
        assertEquals(ChapterRenderEngine.NATIVE, fontFace.decision.engine)
        assertEquals("serif", fontFace.document!!.runs.first().style.family)
    }
    @Test fun publisherMetadataAndZeroBoxesPreserveBodyAndFootnotes() {
        val result = compile("<div class='div'><h3>Heading<a class='zy'>1</a></h3><p class='a'>正文</p></div>",
            "@namespace h 'http://www.w3.org/1999/xhtml';" +
                "body{padding:0%;font-family:book, sans-serif} .div{margin:0;padding:0}" +
                "p{border:0; padding:0 0px 0em 0%;margin:0}.a{text-indent:2em}" +
                ".zy{cursor:pointer;font-size:.6em;vertical-align:super}.unused{float:left;padding:2em}")
        assertEquals(result.decision.reason, ChapterRenderEngine.NATIVE, result.decision.engine)
        val doc = result.document!!
        assertEquals("sans-serif", doc.bodyStyle.family)
        assertEquals(32f, doc.blocks.last().style.indent)
        assertEquals("super", doc.runs.first { doc.text.substring(it.start, it.end) == "1" }.style.baseline)
        assertEquals(ChapterRenderEngine.NATIVE, compile("<p>A</p>", "p{padding:2em;padding:0}").decision.engine)
    }
    @Test fun verticalRubyAndBlockImagesAreClassified() {
        val doc = compile("<p>文字<ruby>漢<rt>かん</rt></ruby></p>", "body{writing-mode:vertical-rl}").document!!
        assertTrue(doc.bodyStyle.vertical); assertTrue(doc.blocks.single().style.vertical)
        val image = compile("<div><img src='pic.jpg'/></div><p>Caption</p>").document!!
        assertEquals("OPS/pic.jpg", image.blocks.first().imagePath)
        assertEquals(ChapterRenderEngine.WEBVIEW, compile("<p>inline<img src='pic.jpg'/></p>").decision.engine)
    }
}
