package com.huangder.lumibooks.util.parser

import com.huangder.lumibooks.util.epub.EpubCssIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterPreparerTest {
    private val existingResources = setOf("OEBPS/Images/p0.jpg", "OEBPS/Images/logo1.png")

    @Test
    fun injectsBackgroundDecorationFromBookCss() {
        val index = EpubCssIndex.parse(
            listOf("OEBPS/Styles/main.css" to "body.zhizuobA1 { background-image: url(../Images/p0.jpg); background-size: cover; }")
        )

        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body class='zhizuobA1'><p>制作说明</p></body>",
            chapterPath = "OEBPS/Text/nvwu0001.xhtml",
            cssIndex = index,
            includeBackgroundDecorations = true,
            resourceExists = { it in existingResources }
        )

        assertTrue(prepared.html.contains("data-lumi-decoration=\"true\""))
        assertTrue(prepared.html.contains("src=\"/OEBPS/Images/p0.jpg\""))
        assertTrue(prepared.html.contains("width=\"100%\""))
        // cover 按满宽处理：100% 于正文列宽 1000 px 就是 1000 px。
        assertEquals(1000, prepared.sizeHints["/OEBPS/Images/p0.jpg"]!!.resolveWidthPx(1000, 1f))
    }

    @Test
    fun skipsDecorationWhenUserTurnsOffBookBackgrounds() {
        val index = EpubCssIndex.parse(
            listOf("main.css" to "body { background-image: url(../Images/p0.jpg); }")
        )

        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><p>正文</p></body>",
            chapterPath = "OEBPS/Text/chapter.xhtml",
            cssIndex = index,
            includeBackgroundDecorations = false,
            resourceExists = { true }
        )

        assertFalse(prepared.html.contains("data-lumi-decoration"))
    }

    @Test
    fun skipsGraphicsThatAreNotInTheBook() {
        val index = EpubCssIndex.parse(
            listOf("main.css" to "body { background-image: url(../Images/missing.jpg); }")
        )

        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><p>正文</p></body>",
            chapterPath = "OEBPS/Text/chapter.xhtml",
            cssIndex = index,
            includeBackgroundDecorations = true,
            resourceExists = { it in existingResources }
        )

        assertFalse(prepared.html.contains("data-lumi-decoration"))
    }

    @Test
    fun keepsPublisherWidthForDecorationImages() {
        val index = EpubCssIndex.parse(listOf("main.css" to "img.pp { width: 12%; }"))

        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><p>正文</p><div><img class='pp' src='../Images/gm.png'/></div></body>",
            chapterPath = "OEBPS/Text/chapter.xhtml",
            cssIndex = index,
            includeBackgroundDecorations = true,
            resourceExists = { true }
        )

        val hint = prepared.sizeHints["../Images/gm.png"]!!
        assertEquals(120, hint.resolveWidthPx(1000, 1f))
    }

    @Test
    fun inlineWidthAttributeAlsoDrivesImageSize() {
        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><img src='../Images/gm.png' width='12%'/></body>",
            chapterPath = "OEBPS/Text/chapter.xhtml",
            cssIndex = EpubCssIndex.EMPTY,
            includeBackgroundDecorations = true,
            resourceExists = { true }
        )

        // 没有 CSS 规则时也要保留 HTML 属性给的尺寸。
        assertEquals(120, prepared.sizeHints["../Images/gm.png"]!!.resolveWidthPx(1000, 1f))
    }

    @Test
    fun unwrapsVectorWrapperAroundRasterImage() {
        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><div><svg viewBox='0 0 1000 1333' width='100%' height='100%'>" +
                "<image xlink:href='../Images/cover.jpg' width='1000' height='1333'/></svg></div></body>",
            chapterPath = "OEBPS/Text/cover.xhtml",
            cssIndex = EpubCssIndex.EMPTY,
            includeBackgroundDecorations = true,
            resourceExists = { true }
        )

        assertFalse(prepared.html.contains("<svg"))
        assertTrue(prepared.html.contains("src=\"../Images/cover.jpg\""))
        assertTrue(prepared.inlineSvgSources.isEmpty())
    }

    @Test
    fun keepsRealVectorAsRasterisableSource() {
        val prepared = ReaderChapterPreparer.prepare(
            rawHtml = "<body><svg viewBox='0 0 24 24' width='1.2em'><path d='M0 0h24v24H0z'/></svg></body>",
            chapterPath = "OEBPS/Text/chapter.xhtml",
            cssIndex = EpubCssIndex.EMPTY,
            includeBackgroundDecorations = true,
            resourceExists = { true }
        )

        assertTrue(prepared.html.contains("src=\"lumi-inline-svg-0\""))
        assertEquals(1, prepared.inlineSvgSources.size)
        assertTrue(String(prepared.inlineSvgSources.getValue("lumi-inline-svg-0")).contains("<svg"))
    }

    @Test
    fun sizeHintUnitsAreConvertedWithoutStretching() {
        assertEquals(120, ReaderImageSizeHint("12%").resolveWidthPx(1000, 2f))
        assertEquals(120, ReaderImageSizeHint("60px").resolveWidthPx(1000, 2f))
        assertEquals(200, ReaderImageSizeHint("100").resolveWidthPx(1000, 2f))
        // em 依赖正文字号，解析器层拿不到，交回默认策略。
        assertEquals(null, ReaderImageSizeHint("1.2em").resolveWidthPx(1000, 2f))
        assertEquals(null, ReaderImageSizeHint("0%").resolveWidthPx(1000, 2f))
    }
}
