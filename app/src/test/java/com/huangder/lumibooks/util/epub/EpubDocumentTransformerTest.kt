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
        assertTrue(output.contains("edgeTapLeft: -1, edgeTapRight: 1"))
        assertTrue(output.contains("turnByDirection(state.edgeTapLeft)"))
        assertTrue(output.contains("turnByDirection(state.edgeTapRight)"))
        assertTrue(output.contains("if (state.nativePaging) post('tap', { zone: 'left' })"))
        assertTrue(output.contains("if (state.nativePaging) post('tap', { zone: 'right' })"))
        assertTrue(output.contains("if (state.nativePaging || state.flow !== 'paginated'"))
        assertTrue(output.contains("else post('tap', { zone: 'center' })"))
        assertTrue(output.contains("post('image', {"))
        assertTrue(output.contains("imageLongPressTimer"))
        assertTrue(output.contains("beginImageLongPress(imageFromTarget(event.target))"))
        assertTrue(output.contains("document.addEventListener('contextmenu'"))
        assertTrue(output.contains("if (imageTap) {"))
        assertTrue(output.contains("if (tappedImage) {"))
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
        assertTrue(output.contains("var computed = bodyHasPaint ?"))
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
        assertTrue(output.contains("pageStageCurrent.style.clipPath = clip"))
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
        assertTrue(output.contains("background: #fff"))
        assertTrue(output.contains("box-shadow: 0 24px 72px"))
        assertTrue(output.contains("@keyframes lumi-footnote-enter"))
        assertTrue(output.contains("@keyframes lumi-footnote-exit"))
        assertTrue(output.contains("data-state=\"closing\""))
        assertTrue(output.contains("isFootnoteReference"))
        assertTrue(output.contains("hasFootnoteMarkerLabel"))
        assertTrue(output.contains("showFootnotePopover"))
        assertTrue(output.contains("fetch(resourceUrl"))
        assertTrue(output.contains("post('link', { href: anchor.href })"))
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
    }
}
