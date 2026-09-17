package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubCssIndexTest {
    @Test
    fun parsesSelectorsAndDeclarationsIncludingCommentsAndImport() {
        val css = """
            /* 制作说明 */
            @import url("fonts.css");
            h2, p.title {
              color: #903e55; /* 注释 */
              line-height: 130%;
            }
            @media screen and (max-width: 480px) {
              img { width: 100%; }
            }
        """.trimIndent()

        val index = EpubCssIndex.parse(listOf("OEBPS/Styles/main.css" to css))
        val document = Jsoup.parse("<html><body><p class='title'>x</p><img src='a.png'/></body></html>")

        assertEquals("#903e55", index.declaration(document.selectFirst("p")!!, "color"))
        // @media 里的规则被拍平，手机视口就是命中的那一种。
        assertTrue(index.declaresFullWidth(document.selectFirst("img")!!))
        assertEquals(listOf("fonts.css"), EpubCssIndex.imports(css))
        assertTrue(index.hasWidthRules)
    }

    @Test
    fun importantDeclarationWinsOverLaterNormalRule() {
        val css = """
            img { width: 100% !important; }
            img { width: 50%; }
        """.trimIndent()
        val index = EpubCssIndex.parse(listOf("main.css" to css))
        val image = Jsoup.parse("<img src='a.png'/>").selectFirst("img")!!

        assertEquals("100%", index.widthDeclaration(image))
        assertTrue(index.declaresFullWidth(image))
    }

    @Test
    fun fullWidthComesFromAttributeInlineStyleOrRule() {
        val index = EpubCssIndex.parse(listOf("main.css" to ".full { width: 100vw; }"))
        val byAttribute = Jsoup.parse("<img src='a.png' width='100%'/>").selectFirst("img")!!
        val byInlineStyle = Jsoup.parse("<img src='a.png' style='width:100%'/>").selectFirst("img")!!
        val byRule = Jsoup.parse("<img class='full' src='a.png'/>").selectFirst("img")!!
        val halfWidth = Jsoup.parse("<img src='a.png' width='50%'/>").selectFirst("img")!!
        val noWidth = Jsoup.parse("<img src='a.png'/>").selectFirst("img")!!

        assertTrue(index.declaresFullWidth(byAttribute))
        assertTrue(index.declaresFullWidth(byInlineStyle))
        assertTrue(index.declaresFullWidth(byRule))
        assertFalse(index.declaresFullWidth(halfWidth))
        assertFalse(index.declaresFullWidth(noWidth))
    }

    @Test
    fun backgroundImageIsResolvedWithItsStylesheetAndSize() {
        val css = """
            body.zhizuobA1 {
              background: #fff no-repeat center;
              background-image: url(../Images/p0.jpg);
              background-size: cover;
            }
            .box { background: url("../Images/p2.jpg") no-repeat; background-size: 60%; }
            .gradient { background-image: linear-gradient(#fff, #000); }
        """.trimIndent()
        val index = EpubCssIndex.parse(listOf("OEBPS/Styles/main.css" to css))
        val document = Jsoup.parse(
            "<html><body class='zhizuobA1'><div class='box'></div><div class='gradient'></div></body></html>"
        )

        val bodyBackground = index.background(document.body())!!
        assertEquals("../Images/p0.jpg", bodyBackground.url)
        assertEquals("cover", bodyBackground.size)
        assertEquals("OEBPS/Styles/main.css", bodyBackground.sourcePath)

        val boxBackground = index.background(document.selectFirst(".box")!!)!!
        assertEquals("../Images/p2.jpg", boxBackground.url)
        assertEquals("60%", boxBackground.size)

        assertNull(index.background(document.selectFirst(".gradient")!!))
    }

    @Test
    fun solidOnlyBackgroundHasNoDecorationImage() {
        val index = EpubCssIndex.parse(listOf("main.css" to "body { background: #cccdc8; }"))
        val body = Jsoup.parse("<html><body>x</body></html>").body()
        assertNull(index.background(body))
        assertFalse(index.hasBackgroundImageRules)
    }
}
