package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubDocumentTransformerTest {
    @Test
    fun expandsFirstImageOnlyDocumentAsCover() {
        val source = """
            <html><body><div class="cover"><img src="../Images/cover.jpg" alt="Cover"/></div></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/cover.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE,
            isCoverCandidate = true
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("true", document.body().attr("data-lumi-cover"))
        assertEquals("true", document.selectFirst("img")!!.attr("data-lumi-cover-media"))
        assertEquals("true", document.selectFirst("div")!!.attr("data-lumi-cover-container"))
        assertTrue(output.contains("object-fit: contain !important"))
        assertTrue(output.contains("return { top: 0, right: 0, bottom: 0, left: 0 }"))
    }

    @Test
    fun doesNotTreatIllustratedTextAsCover() {
        val source = """
            <html><body><img src="../Images/frontispiece.jpg"/><p>Preface</p></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE,
            isCoverCandidate = true
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertFalse(document.body().hasAttr("data-lumi-cover"))
        assertFalse(document.selectFirst("img")!!.hasAttr("data-lumi-cover-media"))
        assertFalse(document.body().hasAttr("data-lumi-media-only"))
    }

    @Test
    fun expandsIllustrationPageInsideTheBookAsFullScreen() {
        val source = """
            <html><body><div class="pic"><img src="../Images/plate.jpg" alt=""/></div></body></html>
        """.trimIndent()

        // 第 500 章里的整页插图以前被页边距缩进去一圈，现在按整页图处理。
        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/plate.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("true", document.body().attr("data-lumi-media-only"))
        assertEquals("true", document.body().attr("data-lumi-cover"))
        assertEquals("true", document.selectFirst("img")!!.attr("data-lumi-cover-media"))
        assertTrue(document.body().attr("data-lumi-layout") == "reflowable")
    }

    @Test
    fun fixedLayoutImagePageIgnoresMarginsWithoutCoverStyling() {
        val source = """
            <html><head><meta name="viewport" content="width=1200,height=1600"/></head>
            <body><img src="../Images/plate.jpg" style="width:1200px;height:1600px"/></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/page.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.PRE_PAGINATED
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("true", document.body().attr("data-lumi-media-only"))
        assertFalse(document.body().hasAttr("data-lumi-cover"))
        assertFalse(document.selectFirst("img")!!.hasAttr("data-lumi-cover-media"))
    }

    @Test
    fun marksPublisherFullWidthMediaAsBleeding() {
        val source = """
            <html><body>
              <img class="wide" src="../Images/banner.jpg"/>
              <p>正文</p>
              <img class="wide" src="../Images/second.jpg"/>
            </body></html>
        """.trimIndent()
        val cssIndex = EpubCssIndex.parse(listOf("OPS/Styles/main.css" to "img.wide { width: 100%; }"))

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE,
            isCoverCandidate = false,
            cssIndex = cssIndex
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())
        val images = document.select("img")

        // 章首那张连顶部页边距一起取消，后面那张只取消左右。
        assertEquals("horizontal-top", images[0].attr("data-lumi-bleed"))
        assertEquals("horizontal", images[1].attr("data-lumi-bleed"))
        assertTrue(output.contains("--lumi-inset-left"))
        assertTrue(output.contains("width: calc(100% + var(--lumi-inset-left, 0px) + var(--lumi-inset-right, 0px)) !important"))
    }

    @Test
    fun keepsHalfWidthMediaInsideTheMargins() {
        val source = """
            <html><body><p>正文</p><div><img src="../Images/gm.png" width="12%"/></div></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertFalse(document.selectFirst("img")!!.hasAttr("data-lumi-bleed"))
    }

    @Test
    fun doesNotBleedFixedLayoutMedia() {
        val source = """
            <html><head><meta name="viewport" content="width=1200,height=1600"/></head>
            <body><img class="wide" src="../Images/banner.jpg"/><p>正文</p></body></html>
        """.trimIndent()
        val cssIndex = EpubCssIndex.parse(listOf("main.css" to "img.wide { width: 100%; }"))

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/page.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.PRE_PAGINATED,
            isCoverCandidate = false,
            cssIndex = cssIndex
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertFalse(document.selectFirst("img")!!.hasAttr("data-lumi-bleed"))
    }

    @Test
    fun scrolledFlowKeepsFullPageImagesAtNaturalHeight() {
        val source = """
            <html><body><img src="../Images/plate.jpg"/></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/plate.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        // 分页时整页图占满一屏，滚动阅读时按满宽 + 自然高度，避免中间大片空白。
        assertTrue(output.contains("html.lumi-scrolled body[data-lumi-media-only=\"true\"]"))
        assertTrue(output.contains("html.lumi-crop-page-image body[data-lumi-media-only=\"true\"]"))
        assertTrue(output.contains("state.imagePageCrop = config.imagePageCrop === true"))
    }

    @Test
    fun restoresLocatorAfterPaginationAndInvertsInclusiveProgression() {
        val source = """
            <html><body><p>Chapter</p></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        // 未分页时先把锚点排队，等 paginate() 算完页数再应用（否则会被夹到第 0 页丢掉）。
        assertTrue(output.contains("if (!state.ready) {"))
        assertTrue(output.contains("state.pendingLocator = target;"))
        assertTrue(output.contains("applyPendingLocator();"))
        // 书籍原排版的进度分数是"页尾"语义，必须按 ceil(f * N - ε) - 1 反解。
        assertTrue(output.contains("function pageFromProgression(fraction, total, inclusive)"))
        assertTrue(output.contains("Math.ceil(scaled - 0.000001) - 1"))
        assertTrue(output.contains("pageFromProgression(restoreProgression, state.total, state.restoreProgressionInclusive)"))
        assertTrue(output.contains("state.restoreProgressionInclusive = config.restoreProgressionInclusive === true"))
    }

    @Test
    fun scrolledFlowAlsoMovesBookBackgroundIntoViewportLayer() {
        val source = """
            <html><body class="zhizuobA1"><p>Chapter</p></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        // 上下滚动时底图不能铺在整篇文档上（cover 会被放大到整本书高度、滚动错位），
        // 必须和分页模式一样搬进随视口大小的固定层：两处调用 = 分页 + 滚动。
        assertTrue(output.contains("function applyPublisherBackgroundLayer()"))
        assertEquals(
            2,
            Regex("applyPublisherBackgroundLayer\\(\\);").findAll(output).count()
        )
        // 分页完成后同步一次阅读器自己的背景层，保证滚动模式下几何与视口一致。
        assertTrue(output.contains("applyReaderPageBackgroundLayer(state.readerBackgroundActive);"))
    }

    @Test
    fun fixedLayoutKeepsPublisherDesignBox() {
        val source = """
            <html><head><meta name="viewport" content="width=860,height=1146"/></head>
            <body><img src="../Images/page.jpg"/></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("page.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.PRE_PAGINATED
        ).toString(Charsets.UTF_8)

        // 固定版式的设计盒由脚本按出版方 viewport 设置，不能被样式表里的 100% 覆盖，
        // 否则整页会被再缩放一次，图片被裁、页面偏移。
        // 保留 flow，供后续根据页面实际结构选择手势。
        assertFalse(output.contains("if (state.fixed) state.flow = 'paginated';"))
        val bodyRule = Regex(
            """(?m)^body\[data-lumi-layout="pre_paginated"\] \{([^}]*)\}"""
        ).find(output)
        assertNotNull(bodyRule)
        val declarations = bodyRule!!.groupValues[1]
        // 注意别被 max-width 误伤：这里只禁止整页设计盒被 width/height: 100% 覆盖。
        assertFalse(Regex("(?m)^\\s*width:\\s*100%").containsMatchIn(declarations))
        assertFalse(Regex("(?m)^\\s*height:\\s*100%").containsMatchIn(declarations))
        assertTrue(declarations.contains("max-width: none !important"))
        assertTrue(declarations.contains("max-height: none !important"))
        assertTrue(declarations.contains("overflow: hidden !important"))
        assertTrue(output.contains("classList.toggle('lumi-fixed-layout', state.fixed)"))
        assertTrue(output.contains("body.style.setProperty('position', 'fixed', 'important')"))
        assertTrue(output.contains("body.style.setProperty('left', fixedLeft + 'px', 'important')"))
        assertTrue(output.contains("body.style.setProperty('top', fixedTop + 'px', 'important')"))
        assertTrue(output.contains("document.documentElement.style.setProperty('overflow', 'hidden', 'important')"))
    }

    @Test
    fun expandsVectorOnlyFirstDocumentAsCover() {
        val source = """
            <html><body><svg viewBox="0 0 1200 1800"><text x="100" y="200">Book title</text></svg></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/cover.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE,
            isCoverCandidate = true
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("true", document.body().attr("data-lumi-cover"))
        assertEquals("true", document.selectFirst("svg")!!.attr("data-lumi-cover-media"))
    }

    @Test
    fun removesActiveContentButPreservesPublisherLayout() {
        val source = """
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head>
                <meta http-equiv="refresh" content="0;url=https://example.com"/>
                <meta http-equiv="Content-Security-Policy" content="script-src 'none'"/>
                <link rel="stylesheet" href="../Styles/book.css"/>
                <style>.hero { display:grid; --accent:red; }</style>
                <script src="publisher.js">alert('x')</script>
              </head>
              <body onload="steal()">
                <p id="target" class="hero" style="float:left" onclick="steal()">Publisher text</p>
                <svg viewBox="0 0 100 100"><text>SVG text</text></svg>
                <iframe src="https://example.com"></iframe>
                <object data="payload.bin"></object>
                <form action="https://example.com" method="post"><button formaction="https://example.com">Send</button></form>
              </body>
            </html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals(0, document.select("script[src], iframe, object, embed, meta[http-equiv=refresh], meta[http-equiv=Content-Security-Policy]").size)
        assertFalse(document.selectFirst("body")!!.hasAttr("onload"))
        assertFalse(document.selectFirst("p")!!.hasAttr("onclick"))
        assertFalse(document.selectFirst("form")!!.hasAttr("action"))
        assertFalse(document.selectFirst("button")!!.hasAttr("formaction"))
        assertEquals("../Styles/book.css", document.selectFirst("link[rel=stylesheet]")!!.attr("href"))
        assertEquals("float:left", document.selectFirst("p")!!.attr("style"))
        assertNotNull(document.selectFirst("svg[viewBox]"))
        assertTrue(output.contains("window.LumiReader"))
        assertTrue(output.contains("syncToPage: syncToPage"))
        assertTrue(output.contains("goToProgression: goToProgression"))
        assertTrue(output.contains("currentPosition: function () { return currentPagePayload(); }"))
        assertTrue(output.contains("documentHref: window.location.href"))
        assertTrue(output.contains("Math.floor(normalized * state.total)"))
        assertTrue(output.contains("notificationSerial !== pageNotifySerial"))
        assertFalse(output.contains("ResizeObserver(document.body"))
        assertFalse(output.contains("MutationObserver(document.body"))
        assertFalse(output.contains("window.scrollX / window.innerWidth"))
        assertTrue(output.contains("translate3d("))
        assertTrue(output.contains("pageOffsets"))
        assertTrue(output.contains("event.preventDefault()"))
        assertTrue(output.contains("{ passive: false }"))
        assertTrue(output.contains("applyReaderOverrides"))
        assertTrue(output.contains("lumi-reader-overrides"))
        assertTrue(output.contains("new Intl.Segmenter"))
        assertTrue(output.contains("appendBionicCjkRun"))
        assertTrue(output.contains("targetLength = fixation ? 2 : 3"))
        assertFalse(output.contains("cjkIndex += 2"))
        assertTrue(output.contains("@font-face"))
        assertTrue(output.contains("config.textColor"))
        assertTrue(output.contains("config.textAlignment"))
        assertTrue(output.contains("config.letterSpacingDp"))
        assertTrue(output.contains("font-weight:100 900"))
        assertTrue(output.contains("{text-align:' + textAlignment + ' !important;}"))
        assertTrue(output.contains("findText"))
        assertTrue(output.contains("version: 2"))
        assertTrue(output.contains("lumi-highlight-layer"))
        assertTrue(output.contains("rebuildHighlightLayer"))
        assertTrue(output.contains("range.getClientRects()"))
        assertTrue(output.contains("block.style.borderRadius"))
        assertTrue(output.contains("lumi-search-highlight-block"))
        assertTrue(output.contains("@keyframes lumi-search-highlight-pulse"))
        assertTrue(output.contains("searchHighlightTimer = setTimeout"))
        assertTrue(output.contains("clearTimeout(searchHighlightTimer)"))
        assertFalse(output.contains("CSS.highlights"))
        assertFalse(output.contains("::highlight("))
        assertTrue(output.contains("applyChineseConversion(config)"))
        assertTrue(output.contains("config.chineseSource"))
        assertTrue(output.contains("convertChineseText(String(value || ''), state.chineseMap)"))
        assertTrue(output.contains("quoteRange(target)"))
        assertTrue(output.contains("post('searchResult'"))
        assertTrue(output.contains("clearSearchHighlight"))
        assertTrue(output.contains("textNode.__lumiOriginalText"))
        assertTrue(output.contains("writingMode"))
        assertTrue(output.contains("reverseAxis: state.reverseAxis"))
        assertTrue(output.contains("pageSerial: pageNotifySerial"))
        assertTrue(output.contains("function currentPagePayload()"))
        assertTrue(output.contains("edgeTapLeft: -1, edgeTapRight: 1"))
        assertTrue(output.contains("turnByDirection(state.edgeTapLeft)"))
        assertTrue(output.contains("turnByDirection(state.edgeTapRight)"))
        assertTrue(output.contains("if (state.nativePaging) post('tap', { zone: 'left' })"))
        assertTrue(output.contains("if (state.nativePaging) post('tap', { zone: 'right' })"))
        assertTrue(output.contains("if (state.nativePaging || state.flow !== 'paginated'"))
        assertTrue(output.contains("else post('tap', { zone: 'center' })"))
        assertTrue(output.contains("post('image', {"))
        assertTrue(output.contains("imageLongPressTimer"))
        assertTrue(output.contains("var initialAnchor = event.target && event.target.closest ?"))
        assertTrue(output.contains("initialAnchor && isFootnoteReference(initialAnchor) ? null"))
        assertTrue(output.contains("document.addEventListener('contextmenu'"))
        assertTrue(output.contains("if (imageTap && !centerImageTap) {"))
        assertTrue(output.contains("var centerImageTap = imageTap"))
        assertTrue(output.contains("var footnoteAnchor = anchor && isFootnoteReference(anchor)"))
        assertTrue(output.contains("var imageTap = !footnoteAnchor && !touchPaging"))
        assertTrue(output.contains("var isTap = !footnoteAnchor && !touchPaging"))
        assertTrue(output.contains("var isCoverMedia = image.getAttribute('data-lumi-cover-media') === 'true'"))
        assertTrue(output.contains("(!anchor || centerImageTap)"))
        assertTrue(output.contains("if (tappedImage && !(anchor && isFootnoteReference(anchor))) {"))
        assertFalse(output.contains("postImagePreview(tappedImage)"))
        assertTrue(output.contains("interactiveFromTarget(image)"))
        assertTrue(output.contains("area[href]"))
        assertTrue(output.contains("img[usemap]"))
        assertTrue(output.contains("img[ismap]"))
        assertTrue(output.contains("image.hasAttribute('usemap')"))
        assertTrue(output.contains("!interactiveTarget"))
        assertTrue(output.contains("--lumi-column-gap"))
        assertTrue(output.contains("var publisherHorizontalInset = Math.max(0,"))
        assertTrue(output.contains("right: publisherHorizontalInset + state.insets.right"))
        assertTrue(output.contains("left: publisherHorizontalInset + state.insets.left"))
        // 固定排版书籍同样遵守页边距：先按“视口减去 insets”缩放，再按 insets 偏移。
        assertTrue(output.contains("var fixedInset = readerBox()"))
        assertTrue(output.contains("state.viewportWidth - fixedInset.left - fixedInset.right"))
        assertTrue(output.contains("state.viewportHeight - fixedInset.top - fixedInset.bottom"))
        // 裁切填满时居中偏移为负，必须原样保留；不裁切时才把负偏移钳成 0。
        assertTrue(output.contains("var cropping = state.imagePageCrop && isMediaOnlyPage()"))
        assertTrue(output.contains("var fixedLeft = fixedInset.left + (cropping ? offsetX : Math.max(0, offsetX))"))
        assertFalse(output.contains("Math.max(0, (state.viewportWidth - designWidth * scale) / 2)"))
        assertTrue(output.contains("html.lumi-scrolled"))
        assertTrue(output.contains("overflow-y: auto !important"))
        assertTrue(output.contains("config.transition === 'curl'"))
        assertTrue(output.contains("nativePaging: false"))
        assertTrue(output.contains("state.nativePaging = config.nativePaging === true"))
        assertTrue(output.contains("var shouldTurn = !state.nativePaging"))
        assertTrue(output.contains("preservePublisherBackground"))
        assertTrue(output.contains("lumi-ignore-publisher-background"))
        assertTrue(output.contains("replace(/\\s+/g, '')"))
        assertTrue(output.contains("root.style.setProperty('background-image', 'none', 'important')"))
        assertTrue(output.contains("root.style.setProperty('background-color', 'transparent', 'important')"))
        assertTrue(output.contains("var bodyStyle = window.getComputedStyle(document.body)"))
        assertTrue(output.contains("function publisherBackgroundSource(rootStyle, bodyStyle)"))
        assertTrue(output.contains("if (hasPublisherBackgroundImage(bodyStyle)) return bodyStyle;"))
        assertTrue(output.contains("if (hasPublisherBackgroundImage(rootStyle)) return rootStyle;"))
        assertTrue(output.contains("var computed = publisherBackgroundComputed(rootStyle, bodyStyle)"))
        assertFalse(output.contains("var computed = bodyHasPaint ?"))
        assertFalse(output.contains("var source = hasPaint(rootStyle) ? rootStyle"))
        assertTrue(output.contains("lumi-publisher-background"))
        assertTrue(output.contains("width: state.viewportWidth + 'px'"))
        assertTrue(output.contains("height: state.viewportHeight + 'px'"))
        assertTrue(output.contains("layer.style.setProperty('background-size', computed.size, 'important')"))
        assertTrue(output.contains("document.documentElement.insertBefore(layer, document.body)"))
        assertTrue(output.contains("document.body.style.setProperty('background-image', 'none', 'important')"))
        assertTrue(output.contains("if (!source) return"))
        assertTrue(output.contains("var liveLocator = state.ready ? currentLocator() : null"))
        assertTrue(output.contains("if (liveLocator) restore(liveLocator)"))
        assertFalse(output.contains("caretRangeFromPoint(2, 2)"))
        assertTrue(output.contains("logicalPage !== state.page"))
        assertTrue(output.contains("Math.floor(restoreProgression * state.total + 0.000001)"))
        assertTrue(output.contains("body.style.transform = 'translate3d('"))
        assertTrue(output.contains("contain: strict"))
        assertTrue(output.contains("upper.style.clipPath = clip"))
        assertTrue(output.contains("var forward = pageStageTo > pageStageFrom"))
        assertTrue(output.contains("pageStageTarget.style.clipPath = 'inset(0)'"))
        assertTrue(output.contains("if (pageStageActive) {"))
        assertTrue(output.contains("settleActivePageStageForInput(true);"))
        assertTrue(output.contains("{ passive: true, capture: true }"))
        assertTrue(output.contains("animationGeneration !== pageStageGeneration"))
        assertTrue(output.contains("pageStageDurationOverride"))
        assertTrue(output.contains("next: function () { turnByDirection(1); }"))
        assertTrue(output.contains("previous: function () { turnByDirection(-1); }"))
        assertTrue(output.contains("state.page = targetPage;"))
        assertFalse(output.contains("rotateY("))
        assertTrue(output.contains("source.offsetHeight + 'px'"))
        assertTrue(output.contains("pageStageActive || touchPaging"))
        assertTrue(output.contains("target.closest('#lumi-page-stage')"))
        assertTrue(output.contains("post('selectionCleared', {})"))
        assertTrue(output.contains("var hasSelection ="))
        assertTrue(output.contains("clearDocumentSelection()"))
        assertTrue(output.contains("removeAllRanges()"))
        assertTrue(output.contains("window.devicePixelRatio"))
        assertTrue(output.contains("range.getClientRects()"))
        assertTrue(output.contains("selectionDispatchTimer"))
        assertTrue(output.contains("left: left"))
        assertTrue(output.contains("bottom: bottom"))
        assertFalse(output.contains("background: transparent !important"))
        assertFalse(Regex("""html\s*,\s*body\s*\{[^}]*box-sizing\s*:\s*border-box""").containsMatchIn(output))
    }

    @Test
    fun preservesFootnoteSemanticsAndInjectsPopoverSupport() {
        val source = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <p>Text<a id="ref-1" epub:type="noteref" role="doc-noteref" href="notes.xhtml#note-1">1</a></p>
                <aside id="local-note" epub:type="footnote" role="doc-footnote"><p>Local note</p></aside>
              </body>
            </html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("OPS/Text/chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("noteref", document.selectFirst("#ref-1")!!.attr("epub:type"))
        assertEquals("doc-noteref", document.selectFirst("#ref-1")!!.attr("role"))
        assertEquals("footnote", document.selectFirst("#local-note")!!.attr("epub:type"))
        assertTrue(output.contains("#lumi-footnote-popover"))
        assertTrue(output.contains("a[id*=\"footnotebookmark_start_\"]"))
        assertTrue(output.contains("min-width: 28px"))
        assertTrue(output.contains("width: 20px !important"))
        assertTrue(output.contains("background: #fff"))
        assertTrue(output.contains("box-shadow: 0 24px 72px"))
        assertTrue(output.contains("@keyframes lumi-footnote-enter"))
        assertTrue(output.contains("@keyframes lumi-footnote-exit"))
        assertTrue(output.contains("data-state=\"closing\""))
        assertTrue(output.contains("isFootnoteReference"))
        assertTrue(output.contains("hasFootnoteMarkerLabel"))
        assertTrue(output.contains("footnotebookmark"))
        assertTrue(output.contains("isFootnoteBacklinkHint"))
        assertTrue(output.contains("showFootnotePopover"))
        assertTrue(output.contains("fetch(resourceUrl"))
        assertTrue(output.contains("post('link', { href: anchor.href })"))
        // 逻辑章节会通过 <base> 解析图片/CSS；原始 `href="#note"` 仍须视为当前 DOM，
        // 否则绝对 URL 会落到物理章节，被误判成跨文档链接，注释正文就留在正文流里。
        assertTrue(output.contains("rawHref.charAt(0) === '#'"))
        assertTrue(output.contains("var sameDocument = isSameDocumentLink(anchor, url)"))
        assertTrue(output.contains("loadFootnoteTarget(url, fragment, !!sameDocumentUrl)"))
        // 原排版：注释正文由气泡呈现，正文流里不再重复显示；标记不画下划线。
        assertTrue(output.contains("resolveFootnoteBodies()"))
        assertTrue(output.contains("pairSameDocumentFootnoteBodies"))
        assertTrue(output.contains("lumi-footnote-auto-"))
        assertTrue(output.contains("""[data-lumi-footnote-body="true"]"""))
        assertTrue(output.contains("""display: none !important"""))
        assertTrue(output.contains("""a[data-lumi-footnote-ref="true"]"""))
        assertTrue(output.contains("""text-decoration: none !important"""))
        assertTrue(output.contains("""node.removeAttribute('data-lumi-footnote-body')"""))
    }

    @Test
    fun marksFixedLayoutWithoutReplacingItsViewport() {
        val source = """
            <html><head><meta name="viewport" content="width=1200,height=1600"/></head>
            <body><svg viewBox="0 0 1200 1600"/></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("page.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.PRE_PAGINATED
        ).toString(Charsets.UTF_8)
        val document = Jsoup.parse(output, "", Parser.xmlParser())

        assertEquals("pre_paginated", document.body().attr("data-lumi-layout"))
        assertEquals(1, document.select("meta[name=viewport]").size)
        assertEquals("width=1200,height=1600", document.selectFirst("meta[name=viewport]")!!.attr("content"))
        assertFalse(output.contains("img, svg, video, canvas"))
        // 设计画布必须保留 1200x1600，再由脚本等比缩放；先 max-width:100% 会二次缩小。
        assertTrue(output.contains("max-width: none !important"))
        assertFalse(output.contains("body[data-lumi-layout=\"pre_paginated\"] {\n  width: 100%"))
        // 只有实际识别为单媒体页的固定版式章节才跨 spine 翻页；不能把所有固定版式书
        // 都假定为“一章一页”。
        assertTrue(output.contains("if (state.fixed && isMediaOnlyPage()) return;"))
        assertTrue(output.contains("state.flow === 'scrolled' && state.fixed && isMediaOnlyPage()"))
        assertTrue(output.contains("state.fixed && !isMediaOnlyPage()"))
        assertTrue(output.contains("direction: dy < 0 ? 1 : -1, animated: true"))
        // 媒体解码异常不能让 body 永远保持 visibility:hidden。
        assertTrue(output.contains("Promise.race([settled, timeout])"))
    }

    @Test
    fun readerBackgroundOnlyYieldsToBookImageBackgrounds() {
        val source = """
            <html><body><p>Chapter</p></body></html>
        """.trimIndent()

        val output = EpubDocumentTransformer.transform(
            EpubResource("chapter.xhtml", "application/xhtml+xml", source.toByteArray()),
            EpubRenditionLayout.REFLOWABLE
        ).toString(Charsets.UTF_8)

        // Reader background drives the page paper fallback and auto text color.
        assertTrue(output.contains("state.readerBackgroundColor = config.backgroundColor"))
        assertTrue(output.contains("state.autoTextColor = config.autoTextColor"))
        assertTrue(output.contains("if (state.readerBackgroundColor) color = state.readerBackgroundColor;"))
        assertTrue(output.contains("applyReaderAutoTextColor(config, readerBackgroundActive)"))
        assertTrue(output.contains("lumi-reader-auto-text"))
        // Only image/gradient book backgrounds keep the book paper; plain colors lose.
        assertTrue(output.contains("function hasPublisherBackgroundImage(style)"))
        assertTrue(output.contains("function publisherBackgroundSource(rootStyle, bodyStyle)"))
        assertTrue(output.contains("if (hasPublisherBackgroundImage(bodyStyle)) return bodyStyle;"))
        assertTrue(output.contains("if (hasPublisherBackgroundImage(rootStyle)) return rootStyle;"))
        assertTrue(output.contains("function publisherBackgroundComputed(rootStyle, bodyStyle)"))
        assertTrue(output.contains("var source = publisherBackgroundSource(rootStyle, bodyStyle);"))
        assertTrue(output.contains("state.publisherHasImageBackground = hasPublisherBackgroundImage(source)"))
        assertTrue(output.contains("if (!state.publisherHasImageBackground || !computed) return;"))
        assertTrue(
            output.contains(
                "var readerBackgroundActive = !state.publisherPaintOnly &&"
            )
        )
        // 「原排版」套装下阅读器完全不参与配色。
        assertTrue(output.contains("state.publisherPaintOnly = config.publisherPaintOnly === true"))
    }
}
