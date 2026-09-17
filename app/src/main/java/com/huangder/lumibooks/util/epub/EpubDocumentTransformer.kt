package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.ByteArrayInputStream

object EpubDocumentTransformer {
    fun transform(
        resource: EpubResource,
        layout: EpubRenditionLayout,
        isCoverCandidate: Boolean = false
    ): ByteArray = transform(resource, layout, isCoverCandidate, EpubCssIndex.EMPTY)

    /** 与上同，但带上书籍样式表规则（原排版用来识别发布方声明的铺满宽图片）。 */
    internal fun transform(
        resource: EpubResource,
        layout: EpubRenditionLayout,
        isCoverCandidate: Boolean,
        cssIndex: EpubCssIndex
    ): ByteArray {
        val document = parseAndSanitize(resource)
        return transform(document, layout, isCoverCandidate, cssIndex)
    }

    /**
     * 将已解析（并 sanitize 过）的文档包装成阅读器文档：
     * 注入 viewport、分页 CSS 与 LumiReader 脚本。
     * MOBI 等非 EPUB 来源复用同一包装，保证分页/定位/高亮脚本行为一致。
     */
    fun transform(
        document: Document,
        layout: EpubRenditionLayout,
        isCoverCandidate: Boolean = false
    ): ByteArray = transform(document, layout, isCoverCandidate, EpubCssIndex.EMPTY)

    internal fun transform(
        document: Document,
        layout: EpubRenditionLayout,
        isCoverCandidate: Boolean,
        cssIndex: EpubCssIndex
    ): ByteArray {
        val head = document.head().takeIf { it.tagName().isNotBlank() }
            ?: document.prependElement("head")
        if (head.selectFirst("meta[name=viewport]") == null) {
            head.prependElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1, maximum-scale=5")
        }
        // 同 script：样式中的 '>'（如后代选择器）若被转义成 &gt;，HTML 解析模式下
        // 不会反转义，部分规则会失效，因此也用 DataNode 注入。
        head.appendElement("style").attr("id", "lumi-reader-style")
            .appendChild(DataNode(READER_CSS))
        document.body().attr("data-lumi-layout", layout.name.lowercase())
        // 整页只有图片的页面（封面、卷首图、插图页、固定版式的整页图）在阅读器里应当满屏，
        // 不该被页边距缩进去一圈。reflowable 沿用封面样式，固定版式只打标记、不动设计盒。
        markMediaOnlyPage(document, allowCoverStyling = layout == EpubRenditionLayout.REFLOWABLE)
        markFullBleedMedia(document, layout, cssIndex)
        // 必须以 DataNode 注入：文档以 XML 语法序列化，appendText 会把脚本里的
        // '<'、'>'、'&' 转义成 &lt; &gt; &amp;，浏览器在 <script> 内不会反转义，
        // 导致分页脚本语法错误、原排版空白卡死（text/html 章节尤其明显）。
        document.body().appendElement("script").attr("id", "lumi-reader-script")
            .appendChild(DataNode(READER_SCRIPT))
        return document.outerHtml().toByteArray(Charsets.UTF_8)
    }

    /**
     * 标记「整页只有图片」的页面。
     *
     * 以前只认第 1 个 spine（封面），卷首图、插图页、固定版式整页图都被页边距缩了一圈。
     * 现在任何章节只要正文里没有可见文字、且只有一张媒体，就按整页图处理：
     * reflowable 额外套用封面样式（满屏 contain），固定版式只用 [MEDIA_ONLY_ATTR] 让脚本
     * 忽略页边距，避免破坏出版方的设计盒。
     */
    private fun markMediaOnlyPage(document: Document, allowCoverStyling: Boolean) {
        val body = document.body()
        val textProbe = body.clone().apply {
            // Text inside an SVG is part of the cover artwork, not flowing chapter copy.
            select("script, style, noscript, svg").remove()
        }
        if (textProbe.text().isNotBlank()) return

        val media = body.select("img, svg, video, canvas").filter { element ->
            element.parents().none { parent ->
                parent.tagName().equals("svg", ignoreCase = true) ||
                    parent.tagName().equals("video", ignoreCase = true)
            }
        }
        if (media.size != 1) return

        body.attr(MEDIA_ONLY_ATTR, "true")
        if (!allowCoverStyling) return
        body.attr("data-lumi-cover", "true")
        val coverMedia = media.single().attr("data-lumi-cover-media", "true")
        var ancestor = coverMedia.parent()
        while (ancestor != null && ancestor !== body) {
            ancestor.attr("data-lumi-cover-container", "true")
            ancestor = ancestor.parent()
        }
    }

    /**
     * 标记出版社显式声明铺满宽度的块级图片。
     *
     * 判定口径：只认匹配到图片元素自身的 `width` 属性 / 内联 style / CSS 规则里的
     * `100%`、`100vw`；父容器声明的宽度不算。命中后由阅读器样式取消左右页边距，
     * 章首第一张这样的图连顶部页边距一起取消。固定版式不做此标记（设计盒优先）。
     */
    private fun markFullBleedMedia(
        document: Document,
        layout: EpubRenditionLayout,
        cssIndex: EpubCssIndex
    ) {
        if (layout != EpubRenditionLayout.REFLOWABLE) return
        val body = document.body()
        // 整页图页的页边距已经归零，再叠一层贴边规则只会和封面的绝对定位规则打架。
        if (body.hasAttr(MEDIA_ONLY_ATTR)) return
        val media = body.select("img, svg, video, canvas").filter { element ->
            element.parents().none { parent ->
                parent.tagName().equals("svg", ignoreCase = true) ||
                    parent.tagName().equals("video", ignoreCase = true)
            }
        }
        val firstBlock = firstVisualContent(body)
        media.forEach { element ->
            if (!cssIndex.declaresFullWidth(element)) return@forEach
            val bleedsTop = firstBlock != null &&
                (firstBlock === element || element.parents().contains(firstBlock))
            element.attr(
                BLEED_ATTR,
                if (bleedsTop) BLEED_HORIZONTAL_TOP else BLEED_HORIZONTAL
            )
        }
    }

    /** 正文里第一个真正有内容（含图片）的元素。 */
    private fun firstVisualContent(body: org.jsoup.nodes.Element): org.jsoup.nodes.Element? {
        for (child in body.children()) {
            if (child.tagName().equals("script", ignoreCase = true) ||
                child.tagName().equals("style", ignoreCase = true)
            ) {
                continue
            }
            if (child.text().isNotBlank() || child.selectFirst("img, svg, video, canvas") != null) {
                return child
            }
        }
        return null
    }

    internal fun extractSearchText(resource: EpubResource): String {
        return extractSearchText(parseAndSanitize(resource))
    }

    internal fun extractSearchText(document: Document): String {
        val body = document.body()
        val text = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                    if (node !is TextNode) return
                    var ancestor = node.parent()
                    while (ancestor != null && ancestor !== body) {
                        if (ancestor.nodeName().equals("style", true) ||
                            ancestor.nodeName().equals("noscript", true) ||
                            ancestor.nodeName().equals("script", true)
                        ) return
                        ancestor = ancestor.parent()
                    }
                    text.append(node.wholeText)
                }

                override fun tail(node: org.jsoup.nodes.Node, depth: Int) = Unit
            },
            body
        )
        return text.toString()
    }

    internal fun parseAndSanitize(bytes: ByteArray, path: String, useXmlParser: Boolean = true): Document {
        val parser = if (useXmlParser) Parser.xmlParser() else Parser.htmlParser()
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, path, parser)
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).charset(Charsets.UTF_8)
        sanitize(document)
        return document
    }

    private fun parseAndSanitize(resource: EpubResource): Document {
        return parseAndSanitize(resource.bytes, resource.path, useXmlParser = true)
    }

    private fun sanitize(document: Document) {
        document.select("script, iframe, frame, frameset, object, embed, applet, base").remove()
        document.select("meta[http-equiv]").filter { element ->
            element.attr("http-equiv").equals("refresh", ignoreCase = true) ||
                element.attr("http-equiv").equals("content-security-policy", ignoreCase = true)
        }.forEach { it.remove() }
        document.allElements.forEach { element ->
            element.attributes().asList()
                .filter { attribute ->
                    attribute.key.startsWith("on", ignoreCase = true) ||
                        attribute.key.equals("srcdoc", ignoreCase = true) ||
                        attribute.key.equals("formaction", ignoreCase = true)
                }
                .forEach { element.removeAttr(it.key) }
            if (element.tagName().equals("form", true)) {
                element.removeAttr("action")
                element.removeAttr("method")
            }
        }
    }

    private const val READER_CSS = """
html {
  width: 100% !important;
  height: 100% !important;
  overflow: hidden !important;
  overscroll-behavior: none !important;
}
/* 中文标点挤压：全角标点改用字体的半角字形，不再占满一个汉字宽度。
   字体缺少 halt 特性时浏览器保持原样，不会破坏排版。 */
body,
body * {
  font-feature-settings: "halt" 1 !important;
}
html.lumi-paginated {
  touch-action: none;
}
html.lumi-scrolled {
  height: auto !important;
  min-height: 100% !important;
  overflow-x: hidden !important;
  overflow-y: auto !important;
  overscroll-behavior-x: none !important;
  overscroll-behavior-y: contain !important;
  touch-action: pan-y pinch-zoom;
  -webkit-overflow-scrolling: touch;
}
body[data-lumi-layout="reflowable"] {
  overflow: visible !important;
  column-fill: auto !important;
  column-gap: var(--lumi-column-gap, 0px) !important;
  transform-origin: top left;
  backface-visibility: hidden;
  visibility: hidden;
}
html.lumi-paginated body[data-lumi-layout="reflowable"] {
  height: var(--lumi-page-height, 100vh) !important;
  max-height: var(--lumi-page-height, 100vh) !important;
}
body[data-lumi-layout="pre_paginated"] {
  width: 100% !important;
  height: 100% !important;
  overflow: hidden !important;
  visibility: hidden;
  transform-origin: top left;
}
body[data-lumi-layout="reflowable"] img,
body[data-lumi-layout="reflowable"] svg,
body[data-lumi-layout="reflowable"] video,
body[data-lumi-layout="reflowable"] canvas {
  /* 原书常见 p+p { text-indent } 会把整行图片向右推，造成插图偏右；
     块级化并水平居中，让插图按内容列居中显示。 */
  display: block;
  margin-left: auto;
  margin-right: auto;
  max-width: 100%;
  /* 竖版大图按整页宽度放大后会超出页面高度、下半截被裁掉；
     限制高度不超过当前分页的内容区高度，保持比例缩放到整页可见。 */
  max-height: var(--lumi-content-height, calc(var(--lumi-page-height, 100vh) - 32px));
  object-fit: contain;
}
/* 注释引用图标（脚注标记）是行内小图：上面的块级/居中处理会把它们推到单独一行、并放大到
   整页宽度，表现为"注释图标位置不对"。这里还原成行内、按字号缩放。
   `data-lumi-footnote-marker` 由脚本按注释引用启发式标注。 */
body[data-lumi-layout] img[data-lumi-footnote-marker="true"],
body[data-lumi-layout] sup img,
body[data-lumi-layout] img[class*="footnote" i],
body[data-lumi-layout] a[href*="footnote" i] img,
body[data-lumi-layout] a[id*="footnote" i] img {
  display: inline-block !important;
  margin: 0 0.08em !important;
  /* 不覆盖出版社给的 width/height：有的书把标记宽度写成 11px，保持原样；
     没写尺寸的按 1.2em 封顶，避免 72px 原图把整行撑大。 */
  max-width: 1.2em !important;
  max-height: 1.2em !important;
  vertical-align: -0.14em !important;
  object-fit: contain !important;
}
/* 注释引用标记不画下划线：出版社在 <a> 与图标之间留的空白也会被下划线照出来，
   表现为紧贴"注"字图标左侧的一小段横线。 */
body[data-lumi-layout] a[data-lumi-footnote-ref="true"] {
  text-decoration: none !important;
}
/* 同文档注释正文改由气泡呈现，正文流里不再重复出现（阅读器排版引擎同样如此）。
   标记由脚本 resolveFootnoteBodies() 按注释引用启发式添加；气泡克隆内容时会去掉该属性。 */
[data-lumi-footnote-body="true"] {
  display: none !important;
}
body[data-lumi-layout="reflowable"][data-lumi-cover="true"] {
  position: relative !important;
  width: 100% !important;
  min-width: 100% !important;
  height: var(--lumi-page-height, 100vh) !important;
  min-height: var(--lumi-page-height, 100vh) !important;
  margin: 0 !important;
  padding: 0 !important;
}
body[data-lumi-cover="true"] [data-lumi-cover-container="true"] {
  position: static !important;
  width: 100% !important;
  height: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  border: 0 !important;
}
body[data-lumi-cover="true"] [data-lumi-cover-media="true"] {
  position: absolute !important;
  inset: 0 !important;
  display: block !important;
  width: 100% !important;
  height: 100% !important;
  max-width: none !important;
  max-height: none !important;
  margin: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  object-fit: contain !important;
}
body[data-lumi-layout="reflowable"] table,
body[data-lumi-layout="reflowable"] pre {
  max-width: 100%;
  overflow-x: auto;
}
/* 固定版式书不给图片留兜底时，出版方写成 width/height:100% 的图会被非等比拉伸；
   只兜底 object-fit，不动 width/height，保住出版方的设计盒。 */
body[data-lumi-layout="pre_paginated"] img,
body[data-lumi-layout="pre_paginated"] svg,
body[data-lumi-layout="pre_paginated"] video,
body[data-lumi-layout="pre_paginated"] canvas {
  object-fit: contain;
}
/* 出版社显式声明铺满宽度的块级图片：取消左右页边距，贴到屏幕边缘。
   章首第一张这样的图连顶部页边距一起取消，和原书「顶部贴边」的观感一致。 */
body[data-lumi-layout="reflowable"] [data-lumi-bleed] {
  max-width: none !important;
  width: calc(100% + var(--lumi-inset-left, 0px) + var(--lumi-inset-right, 0px)) !important;
  margin-left: calc(-1 * var(--lumi-inset-left, 0px)) !important;
  margin-right: calc(-1 * var(--lumi-inset-right, 0px)) !important;
}
body[data-lumi-layout="reflowable"] [data-lumi-bleed="horizontal-top"] {
  margin-top: calc(-1 * var(--lumi-inset-top, 0px)) !important;
}
/* 整页图页不参与页边距排版：reflowable 由封面规则把 margin/padding 清零，
   固定版式保留出版方的设计盒（JS 只把缩放基准换成完整视口）。 */
/* 连续滚动下整页图页按满宽 + 自然高度，不能沿用分页的整屏高度，
   否则一张插图会占满一屏，滚动阅读时中间出现大片空白。 */
html.lumi-scrolled body[data-lumi-media-only="true"] {
  height: auto !important;
  min-height: 0 !important;
  max-height: none !important;
}
html.lumi-scrolled body[data-lumi-media-only="true"] [data-lumi-cover-media="true"] {
  position: static !important;
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  object-fit: contain !important;
}
/* 「整页图裁切填满」开启时整页图按屏幕比例裁切铺满，不再等比留白。 */
html.lumi-crop-page-image body[data-lumi-media-only="true"] [data-lumi-cover-media="true"] {
  object-fit: cover !important;
}
html.lumi-ignore-publisher-background:not(.lumi-night):not(.lumi-sepia):not(.lumi-green):not(.lumi-sepia-dark):not(.lumi-green-dark) {
  background-color: transparent !important;
  background-image: none !important;
}
html.lumi-ignore-publisher-background body {
  background-color: transparent !important;
  background-image: none !important;
}
html.lumi-night { filter: invert(0.86) hue-rotate(180deg); background: #111 !important; }
html.lumi-night img, html.lumi-night svg, html.lumi-night video, html.lumi-night canvas { filter: invert(1) hue-rotate(180deg); }
html.lumi-sepia { background: #f5e6d3 !important; }
html.lumi-sepia body { color: #3e2723 !important; }
html.lumi-green { background: #e8f5e9 !important; }
html.lumi-green body { color: #1b5e20 !important; }
html.lumi-sepia-dark { background: #2b2118 !important; }
html.lumi-sepia-dark body { color: #e8d5bc !important; }
html.lumi-green-dark { background: #142a1a !important; }
html.lumi-green-dark body { color: #c8e6c9 !important; }
::selection { background: rgba(255, 193, 7, 0.42); }
/* Duokan exports use a tiny 48x48 image as the only visible footnote marker.
   Keep the publisher layout but give the marker a practical visual and touch size. */
a[id*="footnotebookmark_start_"],
a[href*="#ref_footnotebookmark_end_"],
a[href*="#ref-footnotebookmark-end-"] {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  min-height: 28px;
  padding: 4px;
  margin: -4px;
  box-sizing: content-box;
  vertical-align: middle;
  touch-action: manipulation;
}
a[id*="footnotebookmark_start_"] img,
a[href*="#ref_footnotebookmark_end_"] img,
a[href*="#ref-footnotebookmark-end-"] img {
  width: 20px !important;
  height: 20px !important;
}
#lumi-footnote-popover {
  position: fixed;
  left: 12px;
  top: 12px;
  width: max-content;
  min-width: 112px;
  max-width: min(84vw, 420px);
  max-height: min(56vh, 520px);
  box-sizing: border-box;
  overflow: visible;
  z-index: 2147483646;
  padding: 16px 18px;
  border: 1px solid rgba(28, 28, 30, 0.14);
  border-radius: 14px;
  background: #fff;
  color: #242424;
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.10), 0 6px 24px rgba(0, 0, 0, 0.05);
  font-family: sans-serif;
  font-size: 16px;
  line-height: 1.62;
  text-align: start;
  filter: none;
  opacity: 0;
  transform: translateY(var(--lumi-footnote-motion-y, -8px)) scale(0.965);
  transform-origin: var(--lumi-footnote-arrow-x, 50%) top;
  will-change: opacity, transform;
  touch-action: pan-y;
  -webkit-user-select: text;
  user-select: text;
}
#lumi-footnote-popover[data-placement="below"] { --lumi-footnote-motion-y: -8px; transform-origin: var(--lumi-footnote-arrow-x, 50%) top; }
#lumi-footnote-popover[data-placement="above"] { --lumi-footnote-motion-y: 8px; transform-origin: var(--lumi-footnote-arrow-x, 50%) bottom; }
#lumi-footnote-popover[data-state="open"] {
  animation: lumi-footnote-enter 190ms cubic-bezier(0.2, 0.82, 0.25, 1) both;
}
#lumi-footnote-popover[data-state="closing"] {
  pointer-events: none;
  animation: lumi-footnote-exit 150ms cubic-bezier(0.4, 0, 1, 1) both;
}
#lumi-footnote-popover::before {
  content: '';
  position: absolute;
  left: var(--lumi-footnote-arrow-x, 28px);
  width: 14px;
  height: 14px;
  background: inherit;
  border: inherit;
  transform: translateX(-50%) rotate(45deg);
}
#lumi-footnote-popover[data-placement="below"]::before {
  top: -8px;
  border-right: 0;
  border-bottom: 0;
}
#lumi-footnote-popover[data-placement="above"]::before {
  bottom: -8px;
  border-left: 0;
  border-top: 0;
}
#lumi-footnote-content {
  max-height: calc(min(56vh, 520px) - 32px);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}
#lumi-footnote-content > :first-child { margin-top: 0 !important; }
#lumi-footnote-content > :last-child { margin-bottom: 0 !important; }
#lumi-footnote-content p { margin: 0 0 0.7em !important; }
#lumi-footnote-content img, #lumi-footnote-content svg { max-width: 100% !important; height: auto !important; }
#lumi-footnote-loading {
  display: block;
  width: 20px;
  height: 20px;
  margin: 4px auto;
  border: 2px solid rgba(80, 80, 80, 0.2);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: lumi-footnote-spin 0.8s linear infinite;
}
#lumi-highlight-layer,
#lumi-underline-layer {
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}
#lumi-highlight-layer {
  z-index: -1;
}
#lumi-underline-layer {
  z-index: 2147483000;
}
.lumi-highlight-block {
  position: absolute;
  pointer-events: none;
  box-sizing: border-box;
}
.lumi-underline-block {
  position: absolute;
  overflow: visible;
  pointer-events: none;
}
.lumi-search-highlight-block {
  animation: lumi-search-highlight-pulse 2000ms linear forwards;
}
html.lumi-sepia #lumi-footnote-popover { background: #fff8ee; color: #3e2723; }
html.lumi-green #lumi-footnote-popover { background: #f3fbf3; color: #1b4d27; }
html.lumi-sepia-dark #lumi-footnote-popover { background: #3a312a; color: #e8d5bc; }
html.lumi-green-dark #lumi-footnote-popover { background: #1e3527; color: #c8e6c9; }
@keyframes lumi-search-highlight-pulse {
  0%, 50%, 100% { opacity: 0; }
  25%, 75% { opacity: 1; }
}
@keyframes lumi-footnote-enter {
  from { opacity: 0; transform: translateY(var(--lumi-footnote-motion-y, -8px)) scale(0.965); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes lumi-footnote-exit {
  from { opacity: 1; transform: translateY(0) scale(1); }
  to { opacity: 0; transform: translateY(var(--lumi-footnote-motion-y, -8px)) scale(0.975); }
}
@keyframes lumi-footnote-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  #lumi-footnote-popover[data-state="open"],
  #lumi-footnote-popover[data-state="closing"] { animation-duration: 1ms; }
}
#lumi-page-stage {
  position: fixed;
  inset: 0;
  display: none;
  overflow: hidden;
  pointer-events: none;
  z-index: 2147483600;
  perspective: 1400px;
  transform-style: preserve-3d;
  isolation: isolate;
}
#lumi-page-stage[data-active="true"] { display: block; }
.lumi-page-surface {
  position: absolute;
  inset: 0;
  overflow: hidden;
  contain: strict;
  isolation: isolate;
  background-color: #fff;
  background-repeat: no-repeat;
  pointer-events: none;
  backface-visibility: hidden;
  transform-style: preserve-3d;
  will-change: transform, opacity, filter;
}
.lumi-page-surface > .lumi-page-copy {
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  visibility: visible !important;
  opacity: 1 !important;
  pointer-events: none !important;
  transition: none !important;
  backface-visibility: hidden !important;
}
#lumi-page-stage-shadow {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 18%;
  opacity: 0;
  pointer-events: none;
  z-index: 4;
  will-change: transform, opacity;
}
#lumi-page-stage-shadow[data-side="right"] {
  right: 0;
  background: linear-gradient(to left, rgba(0,0,0,.34), rgba(0,0,0,.10) 35%, transparent);
}
#lumi-page-stage-shadow[data-side="left"] {
  left: 0;
  background: linear-gradient(to right, rgba(0,0,0,.34), rgba(0,0,0,.10) 35%, transparent);
}
"""

    private const val MEDIA_ONLY_ATTR = "data-lumi-media-only"
    private const val BLEED_ATTR = "data-lumi-bleed"
    private const val BLEED_HORIZONTAL = "horizontal"
    private const val BLEED_HORIZONTAL_TOP = "horizontal-top"

    private val READER_SCRIPT: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString(
            READER_SCRIPT_PART_1.length + READER_SCRIPT_PART_2.length +
                READER_SCRIPT_PART_3.length
        ) {
            append(READER_SCRIPT_PART_1)
            append(READER_SCRIPT_PART_2)
            append(READER_SCRIPT_PART_3)
        }
    }

    private const val READER_SCRIPT_PART_1 = """
(function () {
  'use strict';
  var state = {
    page: 0, total: 1, progression: 'ltr', fixed: false, flow: 'paginated', ready: false,
    writingMode: 'horizontal-tb', reverseAxis: false, pageStep: 1, pageOffsets: [0],
    viewportWidth: 0, viewportHeight: 0, paginating: false, configured: false, mediaSettled: false,
    pendingProgression: undefined, publisherBox: null, publisherBackground: null, scrollGuard: false, initialFragmentApplied: false,
    transition: 'slide', transitionDurationMs: 260, nativePaging: false, animationTimer: 0, suppressClickUntil: 0, preservePublisherBackground: true,
    imagePageCrop: false, publisherPaintOnly: false,
    restoreProgressionInclusive: false, pendingLocator: null,
    readerBackgroundColor: null, autoTextColor: null, publisherHasImageBackground: false,
    readerBackgroundActive: false,
    readerBackgroundHasImage: false,
    readerBackgroundUrl: null,
    edgeTapLeft: -1, edgeTapRight: 1, canTurnPrevious: true, canTurnNext: true,
    bionicReading: false, chineseMode: 'original', chineseMap: null, pendingPreparedPage: null, prepareSerial: 0,
    highlightItems: [], ttsHighlight: null, searchHighlight: null,
    insets: { top: 0, right: 0, bottom: 0, left: 0 }
  };
  var resizeTimer = 0;
  var selectionDispatchTimer = 0;
  var scrollNotifyTimer = 0;
  var searchHighlightTimer = 0;
  var touchStartX = 0;
  var touchStartY = 0;
  var touchStartTime = 0;
  var touchLastX = 0;
  var touchLastTime = 0;
  var touchVelocityX = 0;
  var touchBaseX = 0;
  var touchPaging = false;
  var scrollChapterDragDirection = 0;
  var scrollChapterDragStartY = 0;
  var scrollChapterDragOffset = 0;
  var scrollChapterStartedAtTop = false;
  var scrollChapterStartedAtBottom = false;
  var scrollChapterTurnPending = false;
  var scrollChapterAnimationTimer = 0;
  var imageLongPressTimer = 0;
  var imageLongPressTarget = null;
  var imageLongPressTriggered = false;
  var pageStage = null;
  var pageStageCurrent = null;
  var pageStageTarget = null;
  var pageStageShadow = null;
  var pageStageFrom = 0;
  var pageStageTo = 0;
  var pageStageSide = 1;
  var pageStageProgress = 0;
  var pageStageActive = false;
  var pageStageGeneration = 0;
  var pageStageDurationOverride = 0;
  var footnoteRequestSerial = 0;
  var pageNotifySerial = 0;

  function post(type, payload) {
    try {
      if (window.lumiNative && window.lumiNative.postMessage) {
        window.lumiNative.postMessage(JSON.stringify({ type: type, payload: payload || {} }));
      }
    } catch (_) {}
  }

  function nodePath(node) {
    var path = [];
    var current = node;
    while (current && current !== document.documentElement) {
      var parent = current.parentNode;
      if (!parent) break;
      path.unshift(Array.prototype.indexOf.call(parent.childNodes, current));
      current = parent;
    }
    return path;
  }

  function nodeAtPath(path) {
    var node = document.documentElement;
    for (var i = 0; node && i < path.length; i++) node = node.childNodes[path[i]];
    return node;
  }

  function textOffsetForBoundary(index, node, offset) {
    for (var i = 0; i < index.nodes.length; i++) {
      var info = index.nodes[i];
      if (info.node === node) {
        return info.start + Math.max(0, Math.min(Number(offset) || 0, info.node.nodeValue.length));
      }
    }
    try {
      var prefixRange = document.createRange();
      prefixRange.selectNodeContents(document.body);
      prefixRange.setEnd(node, Math.max(0, Number(offset) || 0));
      return Math.max(0, Math.min(index.text.length, prefixRange.toString().length));
    } catch (_) {
      return 0;
    }
  }

  function locator(node, offset, quote) {
    var text = node && node.nodeValue ? node.nodeValue : '';
    var safeOffset = Math.max(0, Math.min(offset || 0, text.length));
    var index = quote && quote.index ? quote.index : textIndex();
    var textPosition = quote && typeof quote.textPosition === 'number'
      ? quote.textPosition : textOffsetForBoundary(index, node, safeOffset);
    var quoteStart = quote && typeof quote.quoteStart === 'number' ? quote.quoteStart : textPosition;
    var quoteEnd = quote && typeof quote.quoteEnd === 'number'
      ? quote.quoteEnd : Math.min(index.text.length, quoteStart + 96);
    var exact = quote && typeof quote.exact === 'string'
      ? quote.exact : index.text.substring(quoteStart, quoteEnd);
    return {
      version: 2,
      domPath: nodePath(node),
      textOffset: safeOffset,
      textPosition: Math.max(0, Math.min(index.text.length, textPosition)),
      textLength: index.text.length,
      exact: exact,
      prefix: index.text.substring(Math.max(0, quoteStart - 32), quoteStart),
      suffix: index.text.substring(quoteEnd, Math.min(index.text.length, quoteEnd + 32)),
      progression: state.total > 1 ? state.page / (state.total - 1) :
        (quoteStart / Math.max(1, index.text.length))
    };
  }

  function currentLocator() {
    var index = textIndex();
    for (var i = 0; i < index.nodes.length; i++) {
      var info = index.nodes[i];
      if (!info.node.nodeValue || !info.node.nodeValue.trim()) continue;
      var nodeRange = document.createRange();
      nodeRange.selectNodeContents(info.node);
      var rects = nodeRange.getClientRects();
      for (var r = 0; r < rects.length; r++) {
        var rect = rects[r];
        var physicalPage = physicalPageForRect(rect);
        var logicalPage = state.flow === 'scrolled'
          ? physicalPage : logicalPageForPhysical(physicalPage);
        if (logicalPage !== state.page) continue;
        var x = Math.max(1, Math.min(viewportWidth() - 2, rect.left + Math.min(4, rect.width / 2)));
        var y = Math.max(1, Math.min(viewportHeight() - 2, rect.top + Math.min(4, rect.height / 2)));
        var range = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
        if (range && document.body.contains(range.startContainer) && pageForRange(range) === state.page) {
          return locator(range.startContainer, range.startOffset, { index: index });
        }
        var startRange = document.createRange();
        startRange.setStart(info.node, 0);
        startRange.collapse(true);
        if (pageForRange(startRange) === state.page) {
          return locator(info.node, 0, { index: index, textPosition: info.start });
        }
      }
    }
    return {
      version: 2,
      textPosition: 0,
      textLength: index.text.length,
      progression: state.total > 1 ? state.page / (state.total - 1) : 0
    };
  }

  function currentPagePayload() {
    return {
      pageIndex: state.page,
      pageCount: state.total,
      reverseAxis: state.reverseAxis,
      pageSerial: pageNotifySerial,
      locator: currentLocator()
    };
  }

  function textIndex() {
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var nodes = [];
    var text = '';
    var node;
    while ((node = walker.nextNode())) {
      var parent = node.parentElement;
      if (!parent || parent.closest('script,style,noscript')) continue;
      nodes.push({ node: node, start: text.length, end: text.length + node.nodeValue.length });
      text += node.nodeValue;
    }
    return { nodes: nodes, text: text };
  }

  function normalizedQuoteIndex(index) {
    var value = '';
    var sourceOffsets = [];
    var converted = convertChineseText(index.text, state.chineseMap);
    for (var i = 0; i < converted.length; i++) {
      var character = converted.charAt(i);
      if (/\s/.test(character) || character === '\u00a0' || character === '\u200b' ||
          character === '\u200c' || character === '\u200d' || character === '\u2060' ||
          character === '\ufeff') continue;
      value += character;
      sourceOffsets.push(i);
    }
    return { value: value, sourceOffsets: sourceOffsets };
  }

  function normalizeQuoteText(value) {
    return convertChineseText(String(value || ''), state.chineseMap)
      .replace(/[\s\u00a0\u200b-\u200d\u2060\ufeff]/g, '');
  }

  function commonPrefixLength(source, expected) {
    var count = 0;
    while (count < source.length && count < expected.length &&
           source.charAt(count) === expected.charAt(count)) count++;
    return count;
  }

  function commonSuffixLength(source, expected) {
    var count = 0;
    while (count < source.length && count < expected.length &&
           source.charAt(source.length - 1 - count) === expected.charAt(expected.length - 1 - count)) count++;
    return count;
  }

  function rangeAtOffsets(index, start, end) {
    var startInfo = null;
    var endInfo = null;
    for (var i = 0; i < index.nodes.length; i++) {
      var info = index.nodes[i];
      if (!startInfo && start >= info.start && start <= info.end) startInfo = info;
      if (end >= info.start && end <= info.end) { endInfo = info; break; }
    }
    if (!startInfo || !endInfo) return null;
    var range = document.createRange();
    range.setStart(startInfo.node, Math.max(0, start - startInfo.start));
    range.setEnd(endInfo.node, Math.max(0, end - endInfo.start));
    return range;
  }

  function quoteRange(target) {
    var exact = target && target.exact ? String(target.exact) : '';
    if (!exact) return null;
    var index = textIndex();
    var normalized = normalizedQuoteIndex(index);
    var normalizedExact = normalizeQuoteText(exact);
    if (!normalizedExact) return null;
    var prefix = normalizeQuoteText(target && target.prefix).slice(-32);
    var suffix = target && Number(target.version || 1) >= 2
      ? normalizeQuoteText(target.suffix).slice(0, 32) : '';
    var expected = 0;
    if (target && typeof target.textPosition === 'number' && Number(target.textLength) > 0) {
      expected = Math.max(0, target.textPosition) / Math.max(1, target.textLength) * normalized.value.length;
    } else if (target && typeof target.progression === 'number') {
      expected = Math.max(0, Math.min(1, target.progression)) * normalized.value.length;
    }
    var from = 0;
    var best = -1;
    var bestScore = -1;
    var bestDistance = Number.MAX_VALUE;
    while (from <= normalized.value.length) {
      var at = normalized.value.indexOf(normalizedExact, from);
      if (at < 0) break;
      var score = commonSuffixLength(normalized.value.substring(0, at), prefix) +
        commonPrefixLength(normalized.value.substring(at + normalizedExact.length), suffix);
      var distance = Math.abs(at - expected);
      if (score > bestScore || (score === bestScore && distance < bestDistance)) {
        best = at;
        bestScore = score;
        bestDistance = distance;
      }
      from = at + 1;
    }
    if (best < 0) return null;
    var sourceStart = normalized.sourceOffsets[best];
    var sourceEnd = normalized.sourceOffsets[best + normalizedExact.length - 1] + 1;
    return rangeAtOffsets(index, sourceStart, sourceEnd);
  }

  function rangeMatchesExact(range, exact) {
    return !exact || normalizeQuoteText(range && range.toString()) === normalizeQuoteText(exact);
  }

  function rangeFromLocators(start, end, exact) {
    var index = textIndex();
    if (start && end && typeof start.textPosition === 'number' && typeof end.textPosition === 'number') {
      var positioned = rangeAtOffsets(index, start.textPosition, end.textPosition);
      if (positioned && rangeMatchesExact(positioned, exact || start.exact)) return positioned;
    }
    var startNode = nodeAtPath((start && start.domPath) || []);
    var endNode = nodeAtPath((end && end.domPath) || []);
    if (startNode && endNode && startNode.nodeType === Node.TEXT_NODE && endNode.nodeType === Node.TEXT_NODE) {
      try {
        var direct = document.createRange();
        direct.setStart(startNode, Math.min(start.textOffset || 0, startNode.nodeValue.length));
        direct.setEnd(endNode, Math.min(end.textOffset || 0, endNode.nodeValue.length));
        if (rangeMatchesExact(direct, exact || start.exact)) return direct;
      } catch (_) {}
    }
    if (!start) return null;
    var quote = Object.assign({}, start);
    if (exact) quote.exact = exact;
    return quoteRange(quote);
  }

  function physicalPageForRect(rect) {
    if (!rect) return 0;
    if (state.flow === 'scrolled') {
      return Math.max(0, Math.floor((rect.top + window.scrollY) / Math.max(1, state.viewportHeight)));
    }
    var physical = state.pageOffsets[state.page] || 0;
    var originalLeft = rect.left + (state.reverseAxis ? -physical : physical) * state.pageStep;
    return Math.max(0, Math.floor((Math.abs(originalLeft) + 0.5) / Math.max(1, state.pageStep)));
  }

  function logicalPageForPhysical(physicalPage) {
    var offsets = state.pageOffsets || [0];
    var exact = offsets.indexOf(physicalPage);
    if (exact >= 0) return exact;
    var nearest = 0;
    var distance = Number.MAX_VALUE;
    for (var i = 0; i < offsets.length; i++) {
      var candidate = Math.abs(offsets[i] - physicalPage);
      if (candidate < distance) { nearest = i; distance = candidate; }
    }
    return nearest;
  }

  function pageForRange(range) {
    var physical = physicalPageForRect(range && range.getBoundingClientRect());
    return state.flow === 'scrolled' ? Math.max(0, Math.min(state.total - 1, physical)) : logicalPageForPhysical(physical);
  }

  function viewportWidth() {
    var visual = window.visualViewport && window.visualViewport.width;
    return Math.max(1, Math.round(visual || window.outerWidth || window.innerWidth || document.documentElement.clientWidth));
  }

  function viewportHeight() {
    var visual = window.visualViewport && window.visualViewport.height;
    return Math.max(1, Math.round(visual || window.outerHeight || window.innerHeight || document.documentElement.clientHeight));
  }

  function updateReadingAxis() {
    var style = window.getComputedStyle(document.body);
    state.writingMode = String(style.writingMode || style.webkitWritingMode || 'horizontal-tb').toLowerCase();
    var verticalReverse = state.writingMode.indexOf('vertical-rl') === 0 ||
      state.writingMode.indexOf('sideways-rl') === 0;
    var horizontalRtl = state.writingMode.indexOf('horizontal') === 0 && style.direction === 'rtl';
    state.reverseAxis = state.progression === 'rtl' || verticalReverse || horizontalRtl;
  }

  function pageX(page) {
    var physical = state.pageOffsets[Math.max(0, Math.min(page, state.total - 1))] || 0;
    return physical * state.pageStep * (state.reverseAxis ? 1 : -1);
  }

  function ensurePageStage() {
    if (pageStage && pageStage.isConnected) return pageStage;
    pageStage = document.createElement('div');
    pageStage.id = 'lumi-page-stage';
    pageStage.setAttribute('aria-hidden', 'true');
    pageStageCurrent = document.createElement('div');
    pageStageCurrent.className = 'lumi-page-surface';
    pageStageCurrent.style.zIndex = '3';
    pageStageTarget = document.createElement('div');
    pageStageTarget.className = 'lumi-page-surface';
    pageStageTarget.style.zIndex = '2';
    pageStageShadow = document.createElement('div');
    pageStageShadow.id = 'lumi-page-stage-shadow';
    pageStage.appendChild(pageStageTarget);
    pageStage.appendChild(pageStageCurrent);
    pageStage.appendChild(pageStageShadow);
    document.documentElement.appendChild(pageStage);
    return pageStage;
  }

  function cloneBodyForPage(page) {
    var source = document.body;
    var sourceClone = source.cloneNode(true);
    var clone = document.createElement('div');
    Array.prototype.forEach.call(sourceClone.attributes || [], function (attribute) {
      if (attribute.name !== 'id') clone.setAttribute(attribute.name, attribute.value);
    });
    clone.classList.add('lumi-page-copy');
    while (sourceClone.firstChild) clone.appendChild(sourceClone.firstChild);
    clone.querySelectorAll('#lumi-reader-script, #lumi-page-stage, #lumi-publisher-background, #lumi-reader-background').forEach(function (node) {
      node.remove();
    });
    clone.style.setProperty('width', source.offsetWidth + 'px', 'important');
    clone.style.setProperty('height', source.offsetHeight + 'px', 'important');
    clone.style.setProperty('min-width', source.offsetWidth + 'px', 'important');
    clone.style.setProperty('min-height', source.offsetHeight + 'px', 'important');
    clone.style.setProperty('max-height', source.offsetHeight + 'px', 'important');
    clone.style.setProperty('overflow', 'visible', 'important');
    clone.style.setProperty('transform', 'translate3d(' + pageX(page) + 'px,0,0)', 'important');
    clone.style.setProperty('visibility', 'visible', 'important');
    clone.style.setProperty('opacity', '1', 'important');
    clone.style.setProperty('transition', 'none', 'important');
    clone.style.setProperty('pointer-events', 'none', 'important');
    return clone;
  }

  function isOpaqueBackgroundColor(color) {
    var normalized = String(color || '').replace(/\s+/g, '').toLowerCase();
    if (!normalized || normalized === 'transparent') return false;
    var rgba = normalized.match(/^rgba\([^,]+,[^,]+,[^,]+,([^)]+)\)$/);
    return !rgba || Number(rgba[1]) > 0.001;
  }

  function pagePaperBackground() {
    var root = document.documentElement;
    var body = document.body;
    var publisher = document.getElementById('lumi-publisher-background');
    var candidates = [];
    if (publisher && window.getComputedStyle(publisher).display !== 'none') candidates.push(window.getComputedStyle(publisher));
    candidates.push(window.getComputedStyle(body));
    candidates.push(window.getComputedStyle(root));
    var color = '';
    var image = 'none';
    var imageSource = null;
    candidates.forEach(function (style) {
      if (!color && isOpaqueBackgroundColor(style.backgroundColor)) color = style.backgroundColor;
      if (image === 'none' && String(style.backgroundImage || 'none') !== 'none') {
        image = style.backgroundImage;
        imageSource = style;
      }
    });
    if (!color) {
      if (state.readerBackgroundColor) color = state.readerBackgroundColor;
      else if (root.classList.contains('lumi-night')) color = '#111111';
      else if (root.classList.contains('lumi-sepia-dark')) color = '#2b2118';
      else if (root.classList.contains('lumi-green-dark')) color = '#142a1a';
      else if (root.classList.contains('lumi-sepia')) color = '#f5e6d3';
      else if (root.classList.contains('lumi-green')) color = '#e8f5e9';
      else color = '#ffffff';
    }
    return {
      color: color,
      image: image,
      size: imageSource ? imageSource.backgroundSize : 'auto',
      position: imageSource ? imageSource.backgroundPosition : '0% 0%',
      repeat: imageSource ? imageSource.backgroundRepeat : 'no-repeat',
      attachment: 'scroll',
      origin: imageSource ? imageSource.backgroundOrigin : 'padding-box',
      clip: imageSource ? imageSource.backgroundClip : 'border-box',
      blendMode: imageSource ? imageSource.backgroundBlendMode : 'normal'
    };
  }

  function applyPagePaper(surface, paper) {
    surface.style.backgroundColor = paper.color;
    surface.style.backgroundImage = paper.image;
    surface.style.backgroundSize = paper.size;
    surface.style.backgroundPosition = paper.position;
    surface.style.backgroundRepeat = paper.repeat;
    surface.style.backgroundAttachment = paper.attachment;
    surface.style.backgroundOrigin = paper.origin;
    surface.style.backgroundClip = paper.clip;
    surface.style.backgroundBlendMode = paper.blendMode;
  }

  function clearPageStage() {
    ++pageStageGeneration;
    if (!pageStage) return;
    pageStage.removeAttribute('data-active');
    pageStage.style.display = 'none';
    pageStageCurrent.replaceChildren();
    pageStageTarget.replaceChildren();
    pageStageCurrent.style.cssText = 'z-index:3';
    pageStageTarget.style.cssText = 'z-index:2';
    pageStageShadow.style.cssText = '';
    pageStageProgress = 0;
    pageStageActive = false;
  }

  function settlePageStage(page) {
    var body = document.body;
    body.style.transition = 'none';
    body.style.opacity = '1';
    body.style.transform = 'translate3d(' + pageX(page) + 'px,0,0)';
    body.style.visibility = 'visible';
    clearPageStage();
  }

  function settleActivePageStageForInput(notify) {
    clearTimeout(state.animationTimer);
    if (!pageStageActive) return false;
    ++pageNotifySerial;
    settlePageStage(state.page);
    if (notify !== false) {
      post('page', currentPagePayload());
    }
    return true;
  }

  function preparePageStage(targetPage) {
    if (state.flow !== 'paginated' || state.fixed || targetPage < 0 || targetPage >= state.total || targetPage === state.page) return false;
    if (pageStageActive && pageStageFrom === state.page && pageStageTo === targetPage) return true;
    ensurePageStage();
    clearTimeout(state.animationTimer);
    clearPageStage();
    pageStageFrom = state.page;
    pageStageTo = targetPage;
    pageStageSide = pageX(targetPage) < pageX(state.page) ? 1 : -1;
    var paper = pagePaperBackground();
    pageStage.style.backgroundColor = paper.color;
    applyPagePaper(pageStageCurrent, paper);
    applyPagePaper(pageStageTarget, paper);
    pageStageCurrent.appendChild(cloneBodyForPage(pageStageFrom));
    pageStageTarget.appendChild(cloneBodyForPage(pageStageTo));
    pageStage.dataset.active = 'true';
    pageStage.style.display = 'block';
    pageStageActive = true;
    document.body.style.visibility = 'hidden';
    updatePageStage(0, 0);
    return true;
  }

  function updatePageStage(progress, dragDx, keepTransition) {
    if (!pageStageActive) return;
    progress = Math.max(0, Math.min(1, Number(progress) || 0));
    pageStageProgress = progress;
    var width = Math.max(1, state.viewportWidth);
    var completeX = -pageStageSide * width;
    var currentX = typeof dragDx === 'number' ? dragDx : completeX * progress;
    // Keep the incoming page visually behind the current sheet instead of
    // making both pages look like a flat two-page translation.
    var targetParallaxRatio = state.transition === 'curl' ? 0.06 : 0.08;
    var targetParallax = pageStageSide * width * targetParallaxRatio * (1 - progress);
    if (!keepTransition) {
      pageStageCurrent.style.transition = 'none';
      pageStageTarget.style.transition = 'none';
      pageStageShadow.style.transition = 'none';
    }
    pageStageTarget.style.transform = 'translate3d(' + targetParallax + 'px,0,0)';
    pageStageTarget.style.opacity = '1';
    pageStageTarget.style.filter = 'brightness(' + (0.90 + progress * 0.10) + ')';
    pageStageTarget.style.clipPath = 'inset(0)';
    pageStageCurrent.style.clipPath = 'inset(0)';
    pageStageShadow.dataset.side = pageStageSide > 0 ? 'right' : 'left';
    if (state.transition === 'curl') {
      var edge = pageStageSide > 0 ? 100 - progress * 100 : progress * 100;
      var wave = Math.sin(progress * Math.PI) * 2.4;
      var clip;
      if (pageStageSide > 0) {
        clip = 'polygon(0 0,' + edge + '% 0,' + (edge + wave) + '% 18%,' + (edge - wave * 0.55) + '% 50%,' + (edge + wave) + '% 82%,' + edge + '% 100%,0 100%)';
      } else {
        clip = 'polygon(' + edge + '% 0,100% 0,100% 100%,' + edge + '% 100%,' + (edge - wave) + '% 82%,' + (edge + wave * 0.55) + '% 50%,' + (edge - wave) + '% 18%)';
      }
      pageStageCurrent.style.transformOrigin = pageStageSide > 0 ? '0% 50%' : '100% 50%';
      pageStageCurrent.style.transform = 'translate3d(' + (completeX * progress * 0.035) + 'px,0,0)';
      pageStageCurrent.style.clipPath = clip;
      pageStageCurrent.style.filter = 'brightness(' + (1 - progress * 0.08) + ')';
      pageStageShadow.style.width = '16%';
      pageStageShadow.style.opacity = String(Math.sin(progress * Math.PI) * 0.82);
      pageStageShadow.style.transform = 'translate3d(' + (completeX * progress) + 'px,0,0)';
    } else {
      pageStageCurrent.style.transformOrigin = '50% 50%';
      pageStageCurrent.style.transform = 'translate3d(' + currentX + 'px,0,0)';
      pageStageCurrent.style.filter = 'none';
      pageStageShadow.style.opacity = String(Math.min(0.68, progress * 0.82));
      pageStageShadow.style.transform = 'translate3d(' + currentX + 'px,0,0)';
    }
  }

  function animatePageStage(commit, notify) {
    if (!pageStageActive) return;
    var baseDuration = state.transitionDurationMs;
    var remaining = commit ? 1 - pageStageProgress : pageStageProgress;
    var duration = Math.max(110, Math.round(baseDuration * Math.max(0.3, remaining)));
    if (pageStageDurationOverride > 0) {
      duration = Math.min(duration, pageStageDurationOverride);
      pageStageDurationOverride = 0;
    }
    var easing = state.transition === 'curl' ? 'cubic-bezier(.18,.78,.16,1)' : 'cubic-bezier(.2,.72,.2,1)';
    pageStageCurrent.style.transition = 'transform ' + duration + 'ms ' + easing + ', clip-path ' + duration + 'ms ' + easing + ', filter ' + duration + 'ms ease';
    pageStageTarget.style.transition = 'transform ' + duration + 'ms ' + easing + ', filter ' + duration + 'ms ease';
    pageStageShadow.style.transition = 'transform ' + duration + 'ms ' + easing + ', opacity ' + duration + 'ms ease';
    void pageStage.offsetWidth;
    var destination = commit ? 1 : 0;
    var animationGeneration = pageStageGeneration;
    requestAnimationFrame(function () {
      if (!pageStageActive || animationGeneration !== pageStageGeneration) return;
      updatePageStage(destination, undefined, true);
    });
    clearTimeout(state.animationTimer);
    state.animationTimer = setTimeout(function () {
      if (!pageStageActive || animationGeneration !== pageStageGeneration) return;
      var settledPage = commit ? pageStageTo : pageStageFrom;
      state.page = settledPage;
      settlePageStage(settledPage);
      if (commit && notify !== false) {
        post('page', currentPagePayload());
      }
    }, duration + 24);
  }

  function moveToPage(page, notify) {
    var notificationSerial = ++pageNotifySerial;
    var previousPage = state.page;
    var targetPage = Math.max(0, Math.min(page, state.total - 1));
    var shouldAnimate = notify !== false && previousPage !== targetPage;
    var adjacent = Math.abs((state.pageOffsets[targetPage] || 0) - (state.pageOffsets[previousPage] || 0)) === 1;
    var canStage = shouldAnimate && adjacent && (state.transition === 'slide' || state.transition === 'curl') &&
      state.flow === 'paginated' && !state.fixed;
    if (canStage && preparePageStage(targetPage)) {
      state.page = targetPage;
      animatePageStage(true, notify);
      return;
    }
    if (pageStageActive && pageStageTo === targetPage) {
      state.page = targetPage;
      animatePageStage(true, notify);
      return;
    }
    state.page = targetPage;
    var body = document.body;
    clearTimeout(state.animationTimer);
    if (state.fixed) {
      // 固定排版整页的缩放/偏移由 paginate() 的 fixed 分支负责；翻页或再次 configure
      // 进入这里时不能覆盖 transform，否则整页会丢掉缩放（页边距看起来“不生效”）。
      clearPageStage();
      body.style.visibility = 'visible';
      body.style.transition = 'none';
      body.style.opacity = '1';
      window.scrollTo(0, 0);
    } else if (state.flow === 'scrolled') {
      clearPageStage();
      body.style.visibility = 'visible';
      body.style.transition = 'none';
      body.style.opacity = '1';
      body.style.transform = '';
      window.scrollTo(0, state.page * state.viewportHeight);
    } else {
      var x = pageX(state.page);
      window.scrollTo(0, 0);
      if (shouldAnimate && state.transition === 'fade') {
        var fadeOutDuration = Math.max(1, Math.round(state.transitionDurationMs / 2));
        var fadeInDuration = Math.max(1, state.transitionDurationMs - fadeOutDuration);
        body.style.transition = 'opacity ' + fadeOutDuration + 'ms ease-out';
        body.style.opacity = '0';
        state.animationTimer = setTimeout(function () {
          body.style.transition = 'none';
          body.style.transform = 'translate3d(' + x + 'px,0,0)';
          void body.offsetWidth;
          body.style.transition = 'opacity ' + fadeInDuration + 'ms ease-in';
          body.style.opacity = '1';
          state.animationTimer = setTimeout(function () { body.style.transition = 'none'; }, fadeInDuration);
        }, fadeOutDuration);
      } else {
        body.style.opacity = '1';
        body.style.transition = 'none';
        body.style.transform = 'translate3d(' + x + 'px,0,0)';
      }
    }
    if (notify !== false) {
      var notifyPage = function () {
        if (notificationSerial !== pageNotifySerial || state.page !== targetPage) return;
        post('page', currentPagePayload());
      };
      if (state.transition === 'none' && state.flow === 'paginated') {
        requestAnimationFrame(function () {
          void body.offsetWidth;
          requestAnimationFrame(notifyPage);
        });
      } else {
        notifyPage();
      }
    }
  }

  function syncToPage(page) {
    var notificationSerial = ++pageNotifySerial;
    var targetPage = Math.max(0, Math.min(page, state.total - 1));
    var body = document.body;
    clearTimeout(state.animationTimer);
    cancelImageLongPress();
    imageLongPressTriggered = false;
    touchPaging = false;
    clearPageStage();
    state.page = targetPage;
    body.style.visibility = 'visible';
    body.style.transition = 'none';
    body.style.opacity = '1';
    if (state.fixed) {
      window.scrollTo(0, 0);
    } else if (state.flow === 'scrolled') {
      body.style.transform = '';
      window.scrollTo(0, state.page * state.viewportHeight);
    } else {
      window.scrollTo(0, 0);
      body.style.transform = 'translate3d(' + pageX(state.page) + 'px,0,0)';
    }
    requestAnimationFrame(function () {
      void body.offsetWidth;
      requestAnimationFrame(function () {
        if (notificationSerial !== pageNotifySerial || state.page !== targetPage) return;
        post('page', currentPagePayload());
      });
    });
  }

  function turnByDirection(direction) {
    direction = Number(direction) < 0 ? -1 : 1;
    // Do not queue input behind a running transition. Finalize the already
    // committed page synchronously, then start the next adjacent animation.
    var interrupted = settleActivePageStageForInput(true);
    if (interrupted) pageStageDurationOverride = state.transition === 'curl' ? 190 : 150;
    var target = state.page + direction;
    if (target >= 0 && target < state.total) moveToPage(target, true);
    else post('chapterTurn', { direction: direction });
  }

  function fulfillPreparedPageRequest() {
    var request = state.pendingPreparedPage;
    if (!request || !state.ready || state.paginating) return false;
    state.pendingPreparedPage = null;
    var serial = state.prepareSerial;
    moveToPage(request.page, false);
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        if (serial !== state.prepareSerial) return;
        post('pagePrepared', {
          requestToken: request.token,
          pageIndex: state.page,
          pageCount: state.total,
          reverseAxis: state.reverseAxis,
          pageSerial: pageNotifySerial,
          locator: currentLocator()
        });
      });
    });
    return true;
  }

  function preparePage(page, requestToken) {
    state.prepareSerial += 1;
    state.pendingPreparedPage = {
      page: Math.max(0, Number(page) || 0),
      token: Number(requestToken) || 0
    };
    fulfillPreparedPageRequest();
  }

  function snapBackPage() {
    if (pageStageActive) {
      animatePageStage(false, false);
      return;
    }
    var body = document.body;
    body.style.transition = 'transform 190ms cubic-bezier(.2,.72,.2,1)';
    body.style.transform = 'translate3d(' + pageX(state.page) + 'px,0,0)';
    clearTimeout(state.animationTimer);
    state.animationTimer = setTimeout(function () { body.style.transition = 'none'; }, 210);
  }

  function settleMedia() {
    var imageJobs = Array.prototype.map.call(document.images || [], function (image) {
      if (image.complete) return Promise.resolve();
      if (image.decode) return image.decode().catch(function () {});
      return new Promise(function (resolve) {
        image.addEventListener('load', resolve, { once: true });
        image.addEventListener('error', resolve, { once: true });
      });
    });
    var fontJob = document.fonts && document.fonts.ready ? document.fonts.ready.catch(function () {}) : Promise.resolve();
    return Promise.all([fontJob, Promise.all(imageJobs)]);
  }

  function pixels(value) {
    var number = parseFloat(value || '0');
    return Number.isFinite(number) ? number : 0;
  }

  function capturePublisherBox(body) {
    if (state.publisherBox) return;
    var style = window.getComputedStyle(body);
    state.publisherBox = {
      marginTop: pixels(style.marginTop), marginRight: pixels(style.marginRight),
      marginBottom: pixels(style.marginBottom), marginLeft: pixels(style.marginLeft),
      paddingTop: pixels(style.paddingTop), paddingRight: pixels(style.paddingRight),
      paddingBottom: pixels(style.paddingBottom), paddingLeft: pixels(style.paddingLeft),
      inline: {
        boxSizing: body.style.boxSizing, margin: body.style.margin, padding: body.style.padding,
        width: body.style.width, height: body.style.height, maxHeight: body.style.maxHeight,
        minHeight: body.style.minHeight, columnWidth: body.style.columnWidth,
        columnGap: body.style.columnGap, columnFill: body.style.columnFill
      }
    };
  }

  function restorePublisherBox(body) {
    var box = state.publisherBox;
    if (!box) return;
    Object.keys(box.inline).forEach(function (name) { body.style[name] = box.inline[name]; });
  }

  function hasPublisherBackgroundImage(style) {
    var image = String(style && style.backgroundImage || 'none');
    return image !== 'none' && image !== '';
  }

  function publisherHasPaint(style) {
    var image = String(style && style.backgroundImage || 'none');
    var color = String(style && style.backgroundColor || 'transparent').replace(/\s+/g, '');
    return image !== 'none' && image !== '' ||
      !/^(transparent|rgba(0,0,0,0))$/i.test(color);
  }

  /**
   * 选择原书背景的实际绘制源。
   *
   * body 绘制在 html 之上；只要 html/body 任意一边有图片或渐变，就必须把它当成
   * 原书背景保留下来，不能因为另一边只是白色纯色就误判为“没有背景图”。
   */
  function publisherBackgroundSource(rootStyle, bodyStyle) {
    if (hasPublisherBackgroundImage(bodyStyle)) return bodyStyle;
    if (hasPublisherBackgroundImage(rootStyle)) return rootStyle;
    if (publisherHasPaint(rootStyle)) return rootStyle;
    if (publisherHasPaint(bodyStyle)) return bodyStyle;
    return null;
  }

  /**
   * 合并 html/body 的背景参数：图片/尺寸/位置等取实际图片源，底色优先取该源的
   * 不透明色，再回退到 html/body 的纯色底，避免透明照片失去原书纸张底色。
   */
  function publisherBackgroundComputed(rootStyle, bodyStyle) {
    var source = publisherBackgroundSource(rootStyle, bodyStyle);
    if (!source) return null;
    var color = String(source.backgroundColor || '');
    if (!isOpaqueBackgroundColor(color)) {
      if (isOpaqueBackgroundColor(rootStyle.backgroundColor)) {
        color = rootStyle.backgroundColor;
      } else if (isOpaqueBackgroundColor(bodyStyle.backgroundColor)) {
        color = bodyStyle.backgroundColor;
      }
    }
    return {
      color: color || source.backgroundColor,
      image: source.backgroundImage,
      size: source.backgroundSize,
      position: source.backgroundPosition,
      repeat: source.backgroundRepeat,
      attachment: source.backgroundAttachment,
      origin: source.backgroundOrigin,
      clip: source.backgroundClip,
      blendMode: source.backgroundBlendMode
    };
  }

  function capturePublisherBackground(body) {
    if (state.publisherBackground) return;
    var root = document.documentElement;
    var rootStyle = window.getComputedStyle(root);
    var bodyStyle = window.getComputedStyle(body);
    var source = publisherBackgroundSource(rootStyle, bodyStyle);
    // Only an image/gradient counts as the book's own background. A plain paper
    // color should not hide the reader background the user picked.
    state.publisherHasImageBackground = hasPublisherBackgroundImage(source);
    if (!source) return;
    var computed = publisherBackgroundComputed(rootStyle, bodyStyle);
    if (!computed) return;
    state.publisherBackground = {
      fromBody: source === bodyStyle,
      computed: computed,
      rootInline: {
        backgroundColor: root.style.backgroundColor,
        backgroundImage: root.style.backgroundImage,
        backgroundSize: root.style.backgroundSize,
        backgroundPosition: root.style.backgroundPosition,
        backgroundRepeat: root.style.backgroundRepeat,
        backgroundAttachment: root.style.backgroundAttachment,
        backgroundOrigin: root.style.backgroundOrigin,
        backgroundClip: root.style.backgroundClip,
        backgroundBlendMode: root.style.backgroundBlendMode
      },
      bodyInline: {
        backgroundColor: body.style.getPropertyValue('background-color'),
        backgroundImage: body.style.getPropertyValue('background-image'),
        position: body.style.getPropertyValue('position'),
        zIndex: body.style.getPropertyValue('z-index')
      },
      bodyPriority: {
        backgroundColor: body.style.getPropertyPriority('background-color'),
        backgroundImage: body.style.getPropertyPriority('background-image'),
        position: body.style.getPropertyPriority('position'),
        zIndex: body.style.getPropertyPriority('z-index')
      }
    };
  }

  function restoreInlineProperty(style, name, value, priority) {
    if (value) style.setProperty(name, value, priority || '');
    else style.removeProperty(name);
  }

  function publisherBackgroundLayer() {
    var layer = document.getElementById('lumi-publisher-background');
    if (layer) return layer;
    layer = document.createElementNS(
      document.documentElement.namespaceURI || 'http://www.w3.org/1999/xhtml',
      'div'
    );
    layer.id = 'lumi-publisher-background';
    layer.setAttribute('aria-hidden', 'true');
    document.documentElement.insertBefore(layer, document.body);
    return layer;
  }

  function sizePublisherBackgroundLayer(layer) {
    var declarations = {
      position: 'fixed', top: '0px', right: 'auto', bottom: 'auto', left: '0px',
      width: state.viewportWidth + 'px', height: state.viewportHeight + 'px',
      minWidth: '0px', minHeight: '0px', maxWidth: 'none', maxHeight: 'none',
      boxSizing: 'border-box', margin: '0px', padding: '0px', border: '0px',
      overflow: 'hidden', transform: 'none', pointerEvents: 'none', zIndex: '0'
    };
    Object.keys(declarations).forEach(function (name) {
      layer.style.setProperty(name.replace(/[A-Z]/g, function (letter) {
        return '-' + letter.toLowerCase();
      }), declarations[name], 'important');
    });
  }

  function restorePublisherBackground() {
    var saved = state.publisherBackground;
    var layer = document.getElementById('lumi-publisher-background');
    if (layer) layer.style.setProperty('display', 'none', 'important');
    if (!saved) return;
    var root = document.documentElement;
    var body = document.body;
    Object.keys(saved.rootInline).forEach(function (name) { root.style[name] = saved.rootInline[name]; });
    restoreInlineProperty(body.style, 'background-color', saved.bodyInline.backgroundColor, saved.bodyPriority.backgroundColor);
    restoreInlineProperty(body.style, 'background-image', saved.bodyInline.backgroundImage, saved.bodyPriority.backgroundImage);
    restoreInlineProperty(body.style, 'position', saved.bodyInline.position, saved.bodyPriority.position);
    restoreInlineProperty(body.style, 'z-index', saved.bodyInline.zIndex, saved.bodyPriority.zIndex);
  }

  function applyPaginatedPublisherBackground() {
    var saved = state.publisherBackground;
    restorePublisherBackground();
    if (!state.preservePublisherBackground) return;
    var root = document.documentElement;
    var rootStyle = window.getComputedStyle(root);
    var bodyStyle = window.getComputedStyle(document.body);
    var source = publisherBackgroundSource(rootStyle, bodyStyle);
    var computed = publisherBackgroundComputed(rootStyle, bodyStyle) ||
      (saved && saved.computed ? saved.computed : null);
    // Solid-only book paper must not cover the reader background.
    state.publisherHasImageBackground = hasPublisherBackgroundImage(source) ||
      !!(computed && hasPublisherBackgroundImage({ backgroundImage: computed.image }));
    if (!state.publisherHasImageBackground || !computed) return;
    root.style.setProperty('background-color', 'transparent', 'important');
    root.style.setProperty('background-image', 'none', 'important');
    var layer = publisherBackgroundLayer();
    sizePublisherBackgroundLayer(layer);
    layer.style.setProperty('background-color', computed.color, 'important');
    layer.style.setProperty('background-image', computed.image, 'important');
    layer.style.setProperty('background-size', computed.size, 'important');
    layer.style.setProperty('background-position', computed.position, 'important');
    layer.style.setProperty('background-repeat', computed.repeat, 'important');
    layer.style.setProperty('background-attachment', 'scroll', 'important');
    layer.style.setProperty('background-origin', computed.origin, 'important');
    layer.style.setProperty('background-clip', computed.clip, 'important');
    layer.style.setProperty('background-blend-mode', computed.blendMode, 'important');
    layer.style.setProperty('display', 'block', 'important');
    document.body.style.setProperty('background-color', 'transparent', 'important');
    document.body.style.setProperty('background-image', 'none', 'important');
    document.body.style.setProperty('position', 'relative');
    document.body.style.setProperty('z-index', '1');
  }

  function isMediaOnlyPage() {
    return document.body.getAttribute('data-lumi-media-only') === 'true';
  }

  function readerBox() {
    if (document.body.getAttribute('data-lumi-cover') === 'true' || isMediaOnlyPage()) {
      return { top: 0, right: 0, bottom: 0, left: 0 };
    }
    var box = state.publisherBox;
    var publisherHorizontalInset = Math.max(0,
      (box.marginLeft + box.paddingLeft + box.marginRight + box.paddingRight) / 2);
    return {
      top: box.marginTop + box.paddingTop + state.insets.top,
      right: publisherHorizontalInset + state.insets.right,
      bottom: box.marginBottom + box.paddingBottom + state.insets.bottom,
      left: publisherHorizontalInset + state.insets.left
    };
  }

  function clearPublisherRootHorizontalInset() {
    var root = document.documentElement;
    root.style.setProperty('margin-left', '0px', 'important');
    root.style.setProperty('margin-right', '0px', 'important');
    root.style.setProperty('padding-left', '0px', 'important');
    root.style.setProperty('padding-right', '0px', 'important');
  }

  // 铺满宽的图需要把「正文列之外的空间」还回去，所以把两侧与顶部留白写成变量给 CSS 用。
  function setInsetVariables(body, inset) {
    body.style.setProperty('--lumi-inset-left', inset.left + 'px');
    body.style.setProperty('--lumi-inset-right', inset.right + 'px');
    body.style.setProperty('--lumi-inset-top', inset.top + 'px');
    body.style.setProperty('--lumi-inset-bottom', inset.bottom + 'px');
  }

  function applyPaginationBox(body) {
    var inset = readerBox();
    var horizontalInset = Math.min(state.viewportWidth - 1, Math.max(0, inset.left + inset.right));
    clearPublisherRootHorizontalInset();
    body.style.setProperty('--lumi-page-height', state.viewportHeight + 'px');
    body.style.setProperty(
      '--lumi-content-height',
      Math.max(1, Math.round(state.viewportHeight - inset.top - inset.bottom)) + 'px'
    );
    setInsetVariables(body, inset);
    body.style.setProperty('--lumi-column-gap', horizontalInset + 'px');
    body.style.boxSizing = 'border-box';
    body.style.margin = '0px';
    body.style.padding = inset.top + 'px ' + inset.right + 'px ' + inset.bottom + 'px ' + inset.left + 'px';
    body.style.width = state.viewportWidth + 'px';
    body.style.minHeight = '0px';
    body.style.columnWidth = Math.max(1, state.viewportWidth - horizontalInset) + 'px';
    body.style.columnGap = horizontalInset + 'px';
    body.style.columnFill = 'auto';
    state.pageStep = state.viewportWidth;
  }

  function applyScrolledBox(body) {
    var inset = readerBox();
    setInsetVariables(body, inset);
    body.style.removeProperty('--lumi-page-height');
    body.style.removeProperty('--lumi-column-gap');
    body.style.boxSizing = 'border-box';
    body.style.margin = '0px';
    body.style.padding = inset.top + 'px ' + inset.right + 'px ' + inset.bottom + 'px ' + inset.left + 'px';
    body.style.width = '100%';
    body.style.minHeight = '100%';
  }

  function collectOccupiedPages(maxPhysicalPage) {
    var occupied = { 0: true };
    function mark(rect) {
      if (!rect || rect.width <= 0 || rect.height <= 0) return;
      var page = Math.floor((Math.abs(rect.left) + 0.5) / Math.max(1, state.pageStep));
      if (page >= 0 && page <= maxPhysicalPage) occupied[page] = true;
    }
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var node;
    while ((node = walker.nextNode())) {
      if (!node.nodeValue || !node.nodeValue.trim()) continue;
      var parent = node.parentElement;
      if (!parent || /^(script|style|noscript)$/i.test(parent.tagName)) continue;
      var range = document.createRange();
      range.selectNodeContents(node);
      Array.prototype.forEach.call(range.getClientRects(), mark);
    }
    document.querySelectorAll('img,svg,video,canvas,math,table,pre,hr').forEach(function (element) {
      Array.prototype.forEach.call(element.getClientRects(), mark);
    });
    return Object.keys(occupied).map(Number).sort(function (a, b) { return a - b; });
  }

  function paginate(restoreProgression) {
    if (state.paginating) return;
    state.paginating = true;
    var body = document.body;
    markFootnoteMarkers();
    resolveFootnoteBodies();
    capturePublisherBox(body);
    capturePublisherBackground(body);
    restorePublisherBox(body);
    restorePublisherBackground();
    body.style.transform = '';
    body.style.marginLeft = '';
    window.scrollTo(0, 0);
    state.viewportWidth = viewportWidth();
    state.viewportHeight = viewportHeight();
    if (state.viewportWidth < 2) {
      state.paginating = false;
      requestAnimationFrame(function () { paginate(restoreProgression); });
      return;
    }
    updateReadingAxis();
    state.fixed = body.getAttribute('data-lumi-layout') === 'pre_paginated';
    document.documentElement.classList.toggle('lumi-paginated', !state.fixed && state.flow === 'paginated');
    document.documentElement.classList.toggle('lumi-scrolled', !state.fixed && state.flow === 'scrolled');
    if (state.fixed) {
      var viewport = document.querySelector('meta[name="viewport"]');
      var content = viewport ? viewport.getAttribute('content') || '' : '';
      var widthMatch = content.match(/(?:^|,)\s*width\s*=\s*([0-9.]+)/i);
      var heightMatch = content.match(/(?:^|,)\s*height\s*=\s*([0-9.]+)/i);
      var svg = document.querySelector('svg[viewBox]');
      var viewBox = svg ? (svg.getAttribute('viewBox') || '').trim().split(/\s+/).map(Number) : [];
      var designWidth = widthMatch ? parseFloat(widthMatch[1]) : (viewBox.length === 4 ? viewBox[2] : body.scrollWidth);
      var designHeight = heightMatch ? parseFloat(heightMatch[1]) : (viewBox.length === 4 ? viewBox[3] : body.scrollHeight);
      // 固定排版书籍也要尊重阅读器的页边距设置：先把整页缩放塞进“视口减去 insets”的盒子，
      // 再按 insets 偏移。整页图页（封面/插图页）的 insets 为 0，仍然满屏。
      var fixedInset = readerBox();
      var availableWidth = Math.max(1, state.viewportWidth - fixedInset.left - fixedInset.right);
      var availableHeight = Math.max(1, state.viewportHeight - fixedInset.top - fixedInset.bottom);
      // 「整页图裁切填满」开启时按较大的比例缩放（铺满屏幕、裁掉溢出），否则等比塞进盒子。
      var cropping = state.imagePageCrop && isMediaOnlyPage();
      var widthRatio = availableWidth / Math.max(1, designWidth);
      var heightRatio = availableHeight / Math.max(1, designHeight);
      var scale = cropping ? Math.max(widthRatio, heightRatio) : Math.min(widthRatio, heightRatio);
      body.style.width = designWidth + 'px';
      body.style.height = designHeight + 'px';
      body.style.transform = 'scale(' + scale + ')';
      var offsetX = (availableWidth - designWidth * scale) / 2;
      var offsetY = (availableHeight - designHeight * scale) / 2;
      // 裁切时居中偏移为负（页面比视口大），必须原样保留，否则被钳成 0 后只裁右下角。
      body.style.marginLeft = (fixedInset.left + (cropping ? offsetX : Math.max(0, offsetX))) + 'px';
      body.style.marginTop = (fixedInset.top + (cropping ? offsetY : Math.max(0, offsetY))) + 'px';
      state.pageOffsets = [0];
      state.total = 1;
      state.page = 0;
    } else if (state.flow === 'scrolled') {
      document.documentElement.style.removeProperty('overflow');
      document.documentElement.style.removeProperty('overflow-x');
      document.documentElement.style.removeProperty('overflow-y');
      applyScrolledBox(body);
      body.style.height = 'auto';
      body.style.maxHeight = 'none';
      body.style.transform = '';
      body.style.columnWidth = 'auto';
      body.style.columnGap = 'normal';
      var scrollExtent = Math.max(body.scrollHeight, document.documentElement.scrollHeight, state.viewportHeight);
      state.pageOffsets = [0];
      state.total = Math.max(1, Math.ceil(scrollExtent / state.viewportHeight));
      moveToPage(typeof restoreProgression === 'number'
        ? Math.floor(restoreProgression * state.total + 0.000001) : state.page, false);
    } else {
      document.documentElement.style.overflow = 'hidden';
      applyPaginatedPublisherBackground();
      applyPaginationBox(body);
      var extent = Math.max(document.documentElement.scrollWidth, body.scrollWidth, state.viewportWidth);
      var physicalTotal = Math.max(1, Math.ceil((extent - 1) / state.pageStep));
      state.pageOffsets = collectOccupiedPages(physicalTotal - 1);
      state.total = Math.max(1, state.pageOffsets.length);
      var target = typeof restoreProgression === 'number'
        ? pageFromProgression(restoreProgression, state.total, state.restoreProgressionInclusive)
        : state.page;
      moveToPage(target, false);
    }
    // 早于分页到达的恢复锚点在这里生效：ready 载荷带回的才是最终页。
    applyPendingLocator();
    body.style.visibility = 'visible';
    // 分页完成后整页容器的尺寸才确定，这时再压制覆盖整页的纯色"纸张"背景。
    neutralizeSolidPagePaint();
    state.ready = true;
    state.paginating = false;
    rebuildHighlightLayer();
    fulfillPreparedPageRequest();
    post('ready', currentPagePayload());
    if (location.hash && !state.initialFragmentApplied) {
      state.initialFragmentApplied = true;
      requestAnimationFrame(function () { window.LumiReader.goToFragment(location.hash); });
    }
  }

"""

    private const val READER_SCRIPT_PART_2 = """
  function isBionicCjkCharacter(character) {
    var codePoint = character.codePointAt(0);
    return (codePoint >= 0x3400 && codePoint <= 0x4DBF) ||
      (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||
      (codePoint >= 0xF900 && codePoint <= 0xFAFF) ||
      (codePoint >= 0x20000 && codePoint <= 0x2FA1F) ||
      (codePoint >= 0x3040 && codePoint <= 0x30FF) ||
      (codePoint >= 0x31F0 && codePoint <= 0x31FF) ||
      (codePoint >= 0xFF66 && codePoint <= 0xFF9D) ||
      (codePoint >= 0x1100 && codePoint <= 0x11FF) ||
      (codePoint >= 0x3130 && codePoint <= 0x318F) ||
      (codePoint >= 0xA960 && codePoint <= 0xA97F) ||
      (codePoint >= 0xAC00 && codePoint <= 0xD7AF) ||
      (codePoint >= 0xD7B0 && codePoint <= 0xD7FF);
  }

  function isBionicWordCharacter(character) {
    if (isBionicCjkCharacter(character)) return false;
    if (/[A-Za-z0-9]/.test(character)) return true;
    return character.toLocaleUpperCase() !== character.toLocaleLowerCase();
  }

  var bionicCjkSegmenters = {};

  function bionicCjkLocale(value) {
    if (/[\u3040-\u30ff\u31f0-\u31ff]/.test(value)) return 'ja';
    if (/[\u1100-\u11ff\u3130-\u318f\ua960-\ua97f\uac00-\ud7ff]/.test(value)) return 'ko';
    return 'zh';
  }

  function splitBionicCjkUnits(value) {
    var rawUnits = [];
    var locale = bionicCjkLocale(value);
    try {
      if (typeof Intl !== 'undefined' && typeof Intl.Segmenter === 'function') {
        if (!bionicCjkSegmenters[locale]) {
          bionicCjkSegmenters[locale] = new Intl.Segmenter(locale, { granularity: 'word' });
        }
        Array.from(bionicCjkSegmenters[locale].segment(value)).forEach(function (part) {
          if (part.segment && Array.from(part.segment).some(isBionicCjkCharacter)) rawUnits.push(part.segment);
        });
      }
    } catch (_) { rawUnits = []; }

    // Older WebViews have no Intl.Segmenter. Character units still become 2/3-character
    // gaze chunks below, so the fallback never returns to alternating every character.
    if (!rawUnits.length) rawUnits = Array.from(value);

    var units = [];
    rawUnits.forEach(function (unit) {
      var characters = Array.from(unit);
      if (characters.length <= 4) {
        units.push(unit);
        return;
      }
      for (var index = 0; index < characters.length; index += 3) {
        units.push(characters.slice(index, index + 3).join(''));
      }
    });
    return units;
  }

  function applyBionicReading(enabled) {
    enabled = enabled === true;
    if (!document.body || state.bionicReading === enabled) return;
    state.bionicReading = enabled;

    if (!enabled) {
      var bionicSpans = Array.prototype.slice.call(document.querySelectorAll('span[data-lumi-bionic]'));
      var parents = [];
      bionicSpans.forEach(function (span) {
        var parent = span.parentNode;
        if (!parent) return;
        parents.push(parent);
        parent.replaceChild(document.createTextNode(span.textContent || ''), span);
      });
      parents.forEach(function (parent) { if (parent.normalize) parent.normalize(); });
      return;
    }

    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var textNodes = [];
    var current = null;
    while ((current = walker.nextNode())) {
      var parent = current.parentElement;
      if (!parent || !current.nodeValue || !current.nodeValue.trim()) continue;
      if (parent.closest('script,style,noscript,textarea,pre,code,svg,math,[data-lumi-bionic]')) continue;
      textNodes.push(current);
    }

    textNodes.forEach(function (textNode) {
      if (!textNode.parentNode) return;
      var characters = Array.from(textNode.nodeValue || '');
      var fragment = document.createDocumentFragment();
      var plainText = '';
      var hasFixation = false;

      function flushPlainText() {
        if (!plainText) return;
        fragment.appendChild(document.createTextNode(plainText));
        plainText = '';
      }

      function appendFixation(value) {
        if (!value) return;
        flushPlainText();
        var span = document.createElement('span');
        span.setAttribute('data-lumi-bionic', '1');
        span.style.setProperty('font-weight', '700', 'important');
        span.textContent = value;
        fragment.appendChild(span);
        hasFixation = true;
      }

      function appendBionicCjkRun(value) {
        var units = splitBionicCjkUnits(value);
        var unitIndex = 0;
        var fixation = true;
        while (unitIndex < units.length) {
          var targetLength = fixation ? 2 : 3;
          var chunk = '';
          var characterCount = 0;
          while (unitIndex < units.length && characterCount < targetLength) {
            var unit = units[unitIndex++];
            chunk += unit;
            characterCount += Array.from(unit).length;
          }
          if (fixation) appendFixation(chunk);
          else plainText += chunk;
          fixation = !fixation;
        }
      }

      var index = 0;
      while (index < characters.length) {
        if (isBionicCjkCharacter(characters[index])) {
          var cjkEnd = index + 1;
          while (cjkEnd < characters.length && isBionicCjkCharacter(characters[cjkEnd])) cjkEnd++;
          appendBionicCjkRun(characters.slice(index, cjkEnd).join(''));
          index = cjkEnd;
          continue;
        }

        if (isBionicWordCharacter(characters[index])) {
          var wordEnd = index + 1;
          while (wordEnd < characters.length && isBionicWordCharacter(characters[wordEnd])) wordEnd++;
          var fixationEnd = index + Math.ceil((wordEnd - index) / 2);
          appendFixation(characters.slice(index, fixationEnd).join(''));
          plainText += characters.slice(fixationEnd, wordEnd).join('');
          index = wordEnd;
          continue;
        }

        plainText += characters[index];
        index++;
      }

      flushPlainText();
      if (hasFixation) textNode.parentNode.replaceChild(fragment, textNode);
    });
  }

  function convertChineseText(value, mapping) {
    if (!mapping || !value) return value || '';
    return Array.from(value).map(function (character) {
      return mapping[character] || character;
    }).join('');
  }

  function applyChineseConversion(config) {
    var mode = config.chineseMode === 'simplified' || config.chineseMode === 'traditional' ?
      config.chineseMode : 'original';
    var source = Array.from(String(config.chineseSource || ''));
    var target = Array.from(String(config.chineseTarget || ''));
    var mapping = Object.create(null);
    if (mode !== 'original') {
      for (var index = 0; index < Math.min(source.length, target.length); index++) {
        mapping[source[index]] = target[index];
      }
    }
    state.chineseMode = mode;
    state.chineseMap = mode === 'original' ? null : mapping;

    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var textNodes = [];
    var current = null;
    while ((current = walker.nextNode())) {
      var parent = current.parentElement;
      if (!parent || !current.nodeValue) continue;
      if (parent.closest('script,style,noscript,textarea,pre,code,svg,math,' +
          '#lumi-page-stage,#lumi-footnote-popover')) continue;
      textNodes.push(current);
    }
    textNodes.forEach(function (textNode) {
      if (typeof textNode.__lumiOriginalText !== 'string') {
        textNode.__lumiOriginalText = textNode.nodeValue || '';
      }
      textNode.nodeValue = mode === 'original' ? textNode.__lumiOriginalText :
        convertChineseText(textNode.__lumiOriginalText, mapping);
    });
  }

  function applyReaderOverrides(config) {
    var existing = document.getElementById('lumi-reader-overrides');
    var family = config.fontFamily ? String(config.fontFamily) : '';
    var fontUrl = config.fontUrl ? String(config.fontUrl) : '';
    var textColor = config.textColor ? String(config.textColor) : '';
    var bodyFontWeight = Math.max(100, Math.min(900, Math.round(Number(config.bodyFontWeight) || 400)));
    var textAlignment = String(config.textAlignment || '');
    // 「自然对齐」在阅读器排版里就是中文两端对齐；原排版同样按两端对齐处理，
    // 否则整页右边缘会参差不齐。
    if (textAlignment === 'natural') textAlignment = 'justify';
    if (!/^(left|center|right|justify)$/.test(textAlignment)) textAlignment = '';
    var letterSpacingDp = Number(config.letterSpacingDp);
    var hasLetterSpacing = Number.isFinite(letterSpacingDp);
    if (!family && !textColor && !textAlignment && bodyFontWeight === 400 && !hasLetterSpacing) {
      if (existing) existing.remove();
      return;
    }
    var style = existing || document.createElement('style');
    style.id = 'lumi-reader-overrides';
    var rules = '';
    if (fontUrl) {
      rules += '@font-face{font-family:"Lumi Reader Override";src:url(' + JSON.stringify(fontUrl) + ');font-style:normal;font-weight:100 900;font-display:swap;}';
    }
    var textSelector = 'body,p,div,section,article,aside,header,footer,nav,h1,h2,h3,h4,h5,h6,' +
      'span,a,li,dt,dd,td,th,blockquote,figcaption,label';
    if (family) rules += textSelector + '{font-family:' + JSON.stringify(family) + ' !important;}';
    if (hasLetterSpacing) {
      letterSpacingDp = Math.max(-8, Math.min(16, letterSpacingDp));
      rules += textSelector + '{letter-spacing:' + letterSpacingDp.toFixed(3) + 'px !important;}';
    }
    if (textColor) rules += textSelector + '{color:' + textColor + ' !important;}';
    if (bodyFontWeight !== 400) rules += textSelector + '{font-weight:' + bodyFontWeight + ' !important;}';
    if (textAlignment) rules += textSelector + '{text-align:' + textAlignment + ' !important;}';
    style.textContent = rules;
    if (!existing) document.head.appendChild(style);
  }

  /**
   * Auto text color only applies where the reader background is visible; pages
   * that paint the book's own image background keep the book's text color.
   */
  function applyReaderAutoTextColor(config, readerBackgroundActive) {
    var existing = document.getElementById('lumi-reader-auto-text');
    var color = config && config.autoTextColor ? String(config.autoTextColor) : '';
    var explicit = config && config.textColor ? String(config.textColor) : '';
    if (!readerBackgroundActive || !color || explicit) {
      if (existing) existing.remove();
      return;
    }
    var style = existing || document.createElement('style');
    style.id = 'lumi-reader-auto-text';
    style.textContent = 'body,p,div,section,article,aside,header,footer,nav,h1,h2,h3,h4,h5,h6,' +
      'span,a,li,dt,dd,td,th,blockquote,figcaption,label{color:' + color + ' !important;}';
    if (!existing) document.head.appendChild(style);
  }

  /**
   * 自定义阅读背景时，让 html 自己画出这个底色。
   *
   * 只把底色交给 WebView 本体的话，页面自身（或 WebView 内部白色画布）会把底色
   * 盖掉，表现为翻页动画期间有底色、翻完页又变回白色。这里用 !important 规则
   * 让文档层直接铺出底色，body 保持透明以免再叠一层。
   */
  function applyReaderPaperBackground(active) {
    var existing = document.getElementById('lumi-reader-paper');
    // 背景是图片时不能在文档层铺底色：底色会盖住 WebView 下方的图片层，
    // 页面就只剩纯色（表现为"设置了图片背景还是白色/纯色"）。
    var color = active && !state.readerBackgroundHasImage && state.readerBackgroundColor
      ? String(state.readerBackgroundColor)
      : '';
    if (!color) {
      if (existing) existing.remove();
      return;
    }
    var style = existing || document.createElement('style');
    style.id = 'lumi-reader-paper';
    style.textContent =
      'html{background-color:' + color + ' !important;background-image:none !important;}' +
      'html body{background-color:transparent !important;background-image:none !important;}';
    if (!existing) document.head.appendChild(style);
  }

  /**
   * 书籍把白色（或其它纯色）铺在整页容器上时，同样属于"纸张底色"，不是内容背景，
   * 应当让位给用户设置的背景。只处理覆盖整页、且背景是纯色（无图片/渐变）的元素，
   * 图片、渐变、以及书内局部色块都保留。
   */
  function neutralizeSolidPagePaint() {
    if (!state.readerBackgroundActive || !state.readerBackgroundColor) return;
    var body = document.body;
    if (!body) return;
    var viewportWidth = state.viewportWidth || document.documentElement.clientWidth;
    var viewportHeight = state.viewportHeight || document.documentElement.clientHeight;
    if (!(viewportWidth > 2) || !(viewportHeight > 2)) return;
    var nodes = body.querySelectorAll('*');
    for (var i = 0; i < nodes.length; i++) {
      var element = nodes[i];
      var tag = String(element.tagName || '').toUpperCase();
      if (tag === 'IMG' || tag === 'SVG' || tag === 'VIDEO' || tag === 'CANVAS' ||
          tag === 'PICTURE' || tag === 'IFRAME') continue;
      if (element.id && element.id.indexOf('lumi-') === 0) continue;
      var style = window.getComputedStyle(element);
      var image = String(style.backgroundImage || 'none');
      if (image !== 'none' && image !== '') continue;
      var color = String(style.backgroundColor || '').replace(/\s+/g, '');
      if (!isOpaqueBackgroundColor(color)) continue;
      var rect = element.getBoundingClientRect();
      if (!(rect.width >= viewportWidth * 0.9) || !(rect.height >= viewportHeight * 0.9)) continue;
      element.style.setProperty('background-color', 'transparent', 'important');
      element.setAttribute('data-lumi-solid-page-paint', 'true');
    }
  }

  function restoreSolidPagePaint() {
    var nodes = document.querySelectorAll('[data-lumi-solid-page-paint]');
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].style.removeProperty('background-color');
      nodes[i].removeAttribute('data-lumi-solid-page-paint');
    }
  }

  /**
   * 把用户背景图画进页面内部（铺满视口的固定层），而不是只放在 WebView 下面。
   *
   * 页面之间要能互相遮挡：透明页在滑动翻页时会同时看到前后两页的文字。
   * 画在页面里之后，每个页面自带不透明背景，快照与翻页动画也天然一致。
   */
  function applyReaderPageBackgroundLayer(active) {
    var layer = document.getElementById('lumi-reader-background');
    var url = active && state.readerBackgroundHasImage && state.readerBackgroundUrl
      ? String(state.readerBackgroundUrl)
      : '';
    if (!url) {
      if (layer) {
        layer.style.setProperty('display', 'none', 'important');
      }
      return;
    }
    if (!layer) {
      layer = document.createElementNS(
        document.documentElement.namespaceURI || 'http://www.w3.org/1999/xhtml',
        'div'
      );
      layer.id = 'lumi-reader-background';
      layer.setAttribute('aria-hidden', 'true');
      document.documentElement.insertBefore(layer, document.body);
    }
    var width = state.viewportWidth || window.innerWidth;
    var height = state.viewportHeight || window.innerHeight;
    layer.style.cssText = 'position:fixed !important;left:0 !important;top:0 !important;' +
      'width:' + width + 'px !important;height:' + height + 'px !important;' +
      'z-index:0 !important;pointer-events:none !important;overflow:hidden !important;' +
      'background-color:' + (state.readerBackgroundColor || 'transparent') + ' !important;' +
      'background-image:url("' + url + '") !important;' +
      'background-size:cover !important;background-position:center !important;' +
      'background-repeat:no-repeat !important;';
    layer.style.setProperty('display', 'block', 'important');
    // 静态内容默认画在定位元素下面，给 body 提一层，文字才不会被背景层盖住。
    if (document.body) {
      document.body.style.setProperty('position', 'relative');
      document.body.style.setProperty('z-index', '1');
    }
  }

  function configure(config) {
    closeFootnotePopover(true);
    config = config || {};
    var liveLocator = state.ready ? currentLocator() : null;
    var liveProgression = state.ready && state.total > 1 ? state.page / (state.total - 1) : undefined;
    state.progression = config.progression === 'rtl' ? 'rtl' : 'ltr';
    state.flow = config.flow === 'scrolled' ? 'scrolled' : 'paginated';
    state.transition = config.transition === 'fade' ? 'fade' :
      (config.transition === 'none' ? 'none' : (config.transition === 'curl' ? 'curl' : 'slide'));
    state.transitionDurationMs = Math.max(100, Math.min(1200,
      Math.round(Number(config.transitionDurationMs) || (state.transition === 'fade' ? 400 : (state.transition === 'curl' ? 800 : 260)))));
    state.nativePaging = config.nativePaging === true;
    state.edgeTapLeft = Number(config.edgeTapLeft) > 0 ? 1 : -1;
    state.edgeTapRight = Number(config.edgeTapRight) < 0 ? -1 : 1;
    state.canTurnPrevious = config.canTurnPrevious !== false;
    state.canTurnNext = config.canTurnNext !== false;
    var insets = config.insets || {};
    state.insets = {
      top: Math.max(0, Number(insets.top) || 0), right: Math.max(0, Number(insets.right) || 0),
      bottom: Math.max(0, Number(insets.bottom) || 0), left: Math.max(0, Number(insets.left) || 0)
    };
    state.pendingProgression = typeof liveProgression === 'number' ? liveProgression :
      (typeof config.progressionValue === 'number' ? config.progressionValue : undefined);
    // 书籍原排版的进度分数按"页尾"保存（(pageIndex + 1) / totalPages），
    // 自研引擎用 ceil(f * N - ε) - 1 反解；这里同步这套语义，否则固定差一页。
    state.restoreProgressionInclusive = config.restoreProgressionInclusive === true;
    state.preservePublisherBackground = config.preservePublisherBackground !== false;
    // 整页图页是否按屏幕比例裁切铺满（默认等比留白）。
    state.imagePageCrop = config.imagePageCrop === true;
    document.documentElement.classList.toggle('lumi-crop-page-image', state.imagePageCrop);
    // 「原排版」套装：阅读器完全不参与配色，原书自己的底色与文字颜色照原样渲染。
    state.publisherPaintOnly = config.publisherPaintOnly === true;
    state.readerBackgroundColor = config.backgroundColor ? String(config.backgroundColor) : null;
    state.autoTextColor = config.autoTextColor ? String(config.autoTextColor) : null;
    state.readerBackgroundHasImage = config.backgroundImage === true;
    state.readerBackgroundUrl = config.backgroundUrl ? String(config.backgroundUrl) : null;
    state.configured = true;
    capturePublisherBackground(document.body);
    // The reader background shows whenever the book has no image/gradient of its
    // own, or when the user forces their background over the book's.
    var readerBackgroundActive = !state.publisherPaintOnly &&
      (!state.preservePublisherBackground || !state.publisherHasImageBackground);
    state.readerBackgroundActive = readerBackgroundActive;
    document.documentElement.classList.toggle(
      'lumi-ignore-publisher-background',
      readerBackgroundActive
    );
    applyReaderPaperBackground(readerBackgroundActive);
    if (readerBackgroundActive) {
      neutralizeSolidPagePaint();
    } else {
      restoreSolidPagePaint();
    }
    applyReaderPageBackgroundLayer(readerBackgroundActive);
    applyReaderOverrides(config);
    applyReaderAutoTextColor(config, readerBackgroundActive);
    applyChineseConversion(config);
    applyBionicReading(config.bionicReading === true);
    document.documentElement.classList.remove('lumi-night', 'lumi-sepia', 'lumi-green', 'lumi-sepia-dark', 'lumi-green-dark');
    if (config.theme === 'night') document.documentElement.classList.add('lumi-night');
    if (config.theme === 'sepia') document.documentElement.classList.add('lumi-sepia');
    if (config.theme === 'green') document.documentElement.classList.add('lumi-green');
    if (config.theme === 'sepia_dark') document.documentElement.classList.add('lumi-sepia-dark');
    if (config.theme === 'green_dark') document.documentElement.classList.add('lumi-green-dark');
    if (state.ready || state.mediaSettled) {
      paginate(state.pendingProgression);
      if (liveLocator) restore(liveLocator);
    }
  }

  /** 文字锚点换算成页号；锚点不可用时返回 null。 */
  function pageFromLocator(target) {
    if (!target) return null;
    var range = null;
    if (Number(target.version || 1) >= 2 && target.exact) range = quoteRange(target);
    var node = !range ? nodeAtPath(target.domPath || []) : null;
    if (!range && node && node.nodeType === Node.TEXT_NODE) {
      try {
        range = document.createRange();
        range.setStart(node, Math.min(target.textOffset || 0, node.nodeValue.length));
        range.collapse(true);
      } catch (_) { range = null; }
    }
    if (!range) range = quoteRange(target);
    if (range) return Math.max(0, Math.min(state.total - 1, pageForRange(range)));
    if (typeof target.progression === 'number') {
      return Math.round(target.progression * Math.max(0, state.total - 1));
    }
    return null;
  }

  function restore(target) {
    if (!target) return false;
    // 文档还没分页时页数只有 1，这时取页会被夹到第 0 页、精确锚点被丢掉；
    // 先存起来，等 paginate() 算完页数再应用。
    if (!state.ready) {
      state.pendingLocator = target;
      return true;
    }
    var page = pageFromLocator(target);
    if (page == null) return false;
    moveToPage(page, true);
    return true;
  }

  /** 分页完成后应用早于分页到达的恢复锚点（不额外发页通知，由 ready 载荷带回最终页）。 */
  function applyPendingLocator() {
    var pending = state.pendingLocator;
    if (!pending) return;
    state.pendingLocator = null;
    var page = pageFromLocator(pending);
    if (page == null) return;
    moveToPage(page, false);
  }

  function goToProgression(fraction) {
    var normalized = Number(fraction);
    if (!isFinite(normalized)) normalized = 0;
    normalized = Math.max(0, Math.min(1, normalized));
    moveToPage(Math.floor(normalized * state.total), true);
  }

  /**
   * 进度分数换算成页号。
   *
   * `inclusive` 用于"页尾"语义的存档（书籍原排版）：分数 f = (pageIndex + 1) / totalPages 时
   * pageIndex = ceil(f * totalPages - ε) - 1；否则按"页首"语义直接取整。
   */
  function pageFromProgression(fraction, total, inclusive) {
    var value = Number(fraction);
    if (!isFinite(value)) value = 0;
    value = Math.max(0, Math.min(1, value));
    var scaled = value * Math.max(1, total);
    if (inclusive) return Math.max(0, Math.ceil(scaled - 0.000001) - 1);
    return Math.max(0, Math.floor(scaled + 0.000001));
  }

  function pageText(pageIndex) {
    var targetPage = Math.max(0, Math.min(Number(pageIndex) || 0, state.total - 1));
    var index = textIndex();
    var pageStarts = [];
    pageStarts[0] = 0;
    function logicalPageForRect(rect) {
      var physicalPage = physicalPageForRect(rect);
      return state.flow === 'scrolled' ? physicalPage : logicalPageForPhysical(physicalPage);
    }
    function characterPage(node, offset) {
      var range = document.createRange();
      range.setStart(node, offset);
      range.setEnd(node, Math.min(node.nodeValue.length, offset + 1));
      var rects = range.getClientRects();
      for (var r = 0; r < rects.length; r++) {
        if (rects[r].width > 0 || rects[r].height > 0) return logicalPageForRect(rects[r]);
      }
      range.collapse(true);
      return pageForRange(range);
    }
    for (var i = 0; i < index.nodes.length; i++) {
      var info = index.nodes[i];
      if (!info.node.nodeValue || !info.node.nodeValue.trim()) continue;
      var range = document.createRange();
      range.selectNodeContents(info.node);
      var rects = range.getClientRects();
      var nodePages = {};
      for (var r = 0; r < rects.length; r++) {
        var rectPage = logicalPageForRect(rects[r]);
        nodePages[rectPage] = true;
      }
      var pages = Object.keys(nodePages).map(Number);
      if (pages.length <= 1) {
        var onlyPage = pages.length ? pages[0] : characterPage(info.node, 0);
        if (typeof pageStarts[onlyPage] !== 'number') pageStarts[onlyPage] = info.start;
        continue;
      }
      pages.sort(function (a, b) { return a - b; });
      if (typeof pageStarts[pages[0]] !== 'number') pageStarts[pages[0]] = info.start;
      for (var p = 1; p < pages.length; p++) {
        var requestedPage = pages[p];
        var low = 0;
        var high = info.node.nodeValue.length;
        while (low < high) {
          var middle = Math.floor((low + high) / 2);
          if (characterPage(info.node, middle) < requestedPage) low = middle + 1;
          else high = middle;
        }
        while (low > 0 && characterPage(info.node, low - 1) >= requestedPage) low--;
        if (typeof pageStarts[requestedPage] !== 'number') {
          pageStarts[requestedPage] = info.start + low;
        }
      }
    }
    var previousStart = 0;
    for (var page = 0; page < state.total; page++) {
      var candidate = typeof pageStarts[page] === 'number' ? pageStarts[page] : previousStart;
      pageStarts[page] = Math.max(previousStart, Math.min(index.text.length, candidate));
      previousStart = pageStarts[page];
    }
    var startOffset = pageStarts[targetPage];
    var endOffset = targetPage + 1 < state.total ? pageStarts[targetPage + 1] : index.text.length;
    return {
      pageIndex: targetPage,
      pageCount: state.total,
      text: index.text.substring(startOffset, endOffset),
      chapterText: index.text,
      startCharacterOffset: startOffset,
      endCharacterOffset: endOffset
    };
  }

  function clearSearchHighlight() {
    clearTimeout(searchHighlightTimer);
    searchHighlightTimer = 0;
    if (!state.searchHighlight) return;
    state.searchHighlight = null;
    rebuildHighlightLayer();
  }

  function findText(target, requestToken) {
    clearSearchHighlight();
    var range = target && target.exact ? quoteRange(target) : null;
    if (!range) {
      post('searchResult', { requestToken: Number(requestToken), found: false });
      return false;
    }
    var page = Math.max(0, Math.min(state.total - 1, pageForRange(range)));
    moveToPage(page, true);
    state.searchHighlight = Object.assign({}, target);
    rebuildHighlightLayer();
    searchHighlightTimer = setTimeout(function () {
      searchHighlightTimer = 0;
      if (!state.searchHighlight) return;
      state.searchHighlight = null;
      rebuildHighlightLayer();
    }, 2000);
    post('searchResult', {
      requestToken: Number(requestToken), found: true, pageIndex: page,
      pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator()
    });
    return true;
  }

  function annotationLayer(id) {
    var existing = document.getElementById(id);
    if (existing) existing.remove();
    var layer = document.createElement('div');
    layer.id = id;
    layer.setAttribute('aria-hidden', 'true');
    layer.style.width = Math.max(document.body.offsetWidth, document.body.scrollWidth, 1) + 'px';
    layer.style.height = Math.max(document.body.offsetHeight, document.body.scrollHeight, 1) + 'px';
    document.body.style.isolation = 'isolate';
    document.body.insertBefore(layer, document.body.firstChild);
    return layer;
  }

  function mergeHighlightRects(rects, vertical) {
    rects.sort(function (a, b) {
      return vertical ? (a.left - b.left || a.top - b.top) : (a.top - b.top || a.left - b.left);
    });
    var merged = [];
    rects.forEach(function (rect) {
      var previous = merged.length ? merged[merged.length - 1] : null;
      var sameTrack = previous && (vertical
        ? Math.abs(previous.left - rect.left) <= 2 && Math.abs(previous.right - rect.right) <= 2
        : Math.abs(previous.top - rect.top) <= 2 && Math.abs(previous.bottom - rect.bottom) <= 2);
      var touches = previous && (vertical ? rect.top <= previous.bottom + 2 : rect.left <= previous.right + 2);
      if (sameTrack && touches) {
        previous.left = Math.min(previous.left, rect.left);
        previous.top = Math.min(previous.top, rect.top);
        previous.right = Math.max(previous.right, rect.right);
        previous.bottom = Math.max(previous.bottom, rect.bottom);
      } else {
        merged.push({ left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom });
      }
    });
    return merged;
  }

  function shapeHighlightRects(rects, vertical) {
    var inlinePadding = 3;
    var blockInset = 1.5;
    var minimumBlockGap = blockInset * 2;
    var tracks = [];
    rects.forEach(function (rect) {
      var previous = tracks.length ? tracks[tracks.length - 1] : null;
      var sameTrack = previous && (vertical
        ? Math.abs(previous.start - rect.left) <= 2 && Math.abs(previous.end - rect.right) <= 2
        : Math.abs(previous.start - rect.top) <= 2 && Math.abs(previous.end - rect.bottom) <= 2);
      if (sameTrack) {
        previous.rects.push(rect);
        previous.start = Math.min(previous.start, vertical ? rect.left : rect.top);
        previous.end = Math.max(previous.end, vertical ? rect.right : rect.bottom);
      } else {
        tracks.push({
          start: vertical ? rect.left : rect.top,
          end: vertical ? rect.right : rect.bottom,
          rects: [rect]
        });
      }
    });
    tracks.forEach(function (track) {
      track.center = (track.start + track.end) / 2;
    });
    tracks.forEach(function (track, index) {
      var previous = index > 0 ? tracks[index - 1] : null;
      var next = index + 1 < tracks.length ? tracks[index + 1] : null;
      var minimumStart = previous
        ? (previous.center + track.center + minimumBlockGap) / 2
        : -Infinity;
      var maximumEnd = next
        ? (track.center + next.center - minimumBlockGap) / 2
        : Infinity;
      track.rects.forEach(function (rect) {
        if (vertical) {
          rect.top -= inlinePadding;
          rect.bottom += inlinePadding;
          rect.left = Math.max(rect.left + blockInset, minimumStart);
          rect.right = Math.min(rect.right - blockInset, maximumEnd);
        } else {
          rect.left -= inlinePadding;
          rect.right += inlinePadding;
          rect.top = Math.max(rect.top + blockInset, minimumStart);
          rect.bottom = Math.min(rect.bottom - blockInset, maximumEnd);
        }
      });
    });
    return rects;
  }

  function textRectsForRange(range) {
    if (!range || range.collapsed) return [];
    var common = range.commonAncestorContainer;
    var root = common.nodeType === Node.TEXT_NODE ? common.parentNode : common;
    if (!root) return [];
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var nodes = [];
    if (common.nodeType === Node.TEXT_NODE) nodes.push(common);
    else {
      var node;
      while ((node = walker.nextNode())) nodes.push(node);
    }
    var rects = [];
    nodes.forEach(function (node) {
      if (!node.nodeValue || !range.intersectsNode(node)) return;
      var start = node === range.startContainer ? range.startOffset : 0;
      var end = node === range.endContainer ? range.endOffset : node.nodeValue.length;
      start = Math.max(0, Math.min(start, node.nodeValue.length));
      end = Math.max(start, Math.min(end, node.nodeValue.length));
      if (start >= end || !node.nodeValue.substring(start, end).trim()) return;
      var textRange = document.createRange();
      textRange.setStart(node, start);
      textRange.setEnd(node, end);
      Array.prototype.forEach.call(textRange.getClientRects(), function (rect) {
        if (rect.width > 0 && rect.height > 0) rects.push(rect);
      });
    });
    return rects;
  }

  function scrollBoundaryState() {
    var root = document.scrollingElement || document.documentElement;
    var extent = Math.max(root.scrollHeight, document.body.scrollHeight, state.viewportHeight);
    return {
      atTop: window.scrollY <= 1,
      atBottom: window.scrollY + state.viewportHeight >= extent - 2
    };
  }

  function resetScrolledChapterDrag(animate) {
    clearTimeout(scrollChapterAnimationTimer);
    var body = document.body;
    if (body) {
      body.style.transition = animate
        ? 'transform 190ms cubic-bezier(.2,.72,.2,1), opacity 160ms ease-out'
        : 'none';
      body.style.transform = '';
      body.style.opacity = '1';
      if (animate) {
        scrollChapterAnimationTimer = setTimeout(function () {
          if (body.isConnected) body.style.transition = 'none';
        }, 210);
      }
    }
    scrollChapterDragDirection = 0;
    scrollChapterDragOffset = 0;
    touchPaging = false;
  }

  function updateScrolledChapterDrag(offset) {
    var body = document.body;
    if (!body) return;
    var limit = Math.max(96, state.viewportHeight * 0.34);
    var distance = Math.min(Math.abs(offset), limit);
    var signedDistance = scrollChapterDragDirection * distance;
    var progress = Math.min(1, distance / limit);
    scrollChapterDragOffset = signedDistance;
    body.style.visibility = 'visible';
    body.style.transition = 'none';
    body.style.transform = 'translate3d(0,' + signedDistance + 'px,0)';
    body.style.opacity = String(1 - progress * 0.72);
  }

  function completeScrolledChapterTurn() {
    var direction = scrollChapterDragDirection;
    if (!direction || scrollChapterTurnPending) return;
    scrollChapterTurnPending = true;
    clearTimeout(scrollChapterAnimationTimer);
    var body = document.body;
    var exitDistance = Math.max(Math.abs(scrollChapterDragOffset), state.viewportHeight * 0.34);
    body.style.transition = 'transform 150ms cubic-bezier(.32,0,.2,1), opacity 135ms ease-out';
    body.style.transform = 'translate3d(0,' + (direction * exitDistance) + 'px,0)';
    body.style.opacity = '0';
    scrollChapterAnimationTimer = setTimeout(function () {
      post('chapterTurn', { direction: direction < 0 ? 1 : -1, animated: true });
      scrollChapterTurnPending = false;
      touchPaging = false;
    }, 155);
  }

  function appendUnderlineRange(layer, range, color) {
    if (!range) return 0;
    var layerRect = layer.getBoundingClientRect();
    var scaleX = layer.offsetWidth > 0 ? layerRect.width / layer.offsetWidth : 1;
    var scaleY = layer.offsetHeight > 0 ? layerRect.height / layer.offsetHeight : scaleX;
    if (!isFinite(scaleX) || scaleX <= 0) scaleX = 1;
    if (!isFinite(scaleY) || scaleY <= 0) scaleY = scaleX;
    var vertical = state.writingMode.indexOf('vertical') === 0 || state.writingMode.indexOf('sideways') === 0;
    var count = 0;
    var rects = textRectsForRange(range).map(function (rect) {
      return { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom };
    });
    mergeHighlightRects(rects, vertical).forEach(function (rect) {
      var width = (rect.right - rect.left) / scaleX;
      var height = (rect.bottom - rect.top) / scaleY;
      if (width <= 0 || height <= 0) return;
      var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('class', 'lumi-underline-block');
      svg.style.left = ((rect.left - layerRect.left) / scaleX) + 'px';
      svg.style.top = ((rect.top - layerRect.top) / scaleY) + 'px';
      var svgWidth = vertical ? 5 : width;
      var svgHeight = vertical ? height : 5;
      svg.setAttribute('width', svgWidth);
      svg.setAttribute('height', svgHeight);
      svg.setAttribute('viewBox', '0 0 ' + svgWidth + ' ' + svgHeight);
      svg.style.width = svgWidth + 'px';
      svg.style.height = svgHeight + 'px';
      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      var d = '';
      if (vertical) {
        for (var y = 0; y <= height; y += 1) {
          var x = 1.75 + Math.sin(y / 5.5 * Math.PI * 2) * 1.1;
          d += (y === 0 ? 'M' : 'L') + x.toFixed(2) + ' ' + y.toFixed(2) + ' ';
        }
        svg.style.left = (((rect.left - layerRect.left) / scaleX) - 4) + 'px';
      } else {
        for (var x = 0; x <= width; x += 1) {
          var y = 1.75 + Math.sin(x / 5.5 * Math.PI * 2) * 1.1;
          d += (x === 0 ? 'M' : 'L') + x.toFixed(2) + ' ' + y.toFixed(2) + ' ';
        }
        svg.style.top = (((rect.bottom - layerRect.top) / scaleY) - 3) + 'px';
      }
      path.setAttribute('d', d);
      path.setAttribute('fill', 'none');
      path.setAttribute('stroke', color);
      path.setAttribute('stroke-width', '1.5');
      path.setAttribute('stroke-linecap', 'round');
      svg.appendChild(path);
      layer.appendChild(svg);
      count += 1;
    });
    return count;
  }

"""

// 单个 Kotlin 字符串常量超过 64KB 会被编译器拒绝（UTF8 string too large），
// 阅读脚本因此拆成多段，运行时再拼接。
private const val READER_SCRIPT_PART_3 = """
  function appendHighlightRange(layer, range, color, extraClass) {
    if (!range) return 0;
    var layerRect = layer.getBoundingClientRect();
    var scaleX = layer.offsetWidth > 0 ? layerRect.width / layer.offsetWidth : 1;
    var scaleY = layer.offsetHeight > 0 ? layerRect.height / layer.offsetHeight : scaleX;
    if (!isFinite(scaleX) || scaleX <= 0) scaleX = 1;
    if (!isFinite(scaleY) || scaleY <= 0) scaleY = scaleX;
    var rects = textRectsForRange(range).map(function (rect) {
      return {
        left: (rect.left - layerRect.left) / scaleX,
        top: (rect.top - layerRect.top) / scaleY,
        right: (rect.right - layerRect.left) / scaleX,
        bottom: (rect.bottom - layerRect.top) / scaleY
      };
    });
    var vertical = state.writingMode.indexOf('vertical') === 0 || state.writingMode.indexOf('sideways') === 0;
    var merged = shapeHighlightRects(mergeHighlightRects(rects, vertical), vertical);
    merged.forEach(function (rect) {
      if (rect.right <= rect.left || rect.bottom <= rect.top) return;
      var block = document.createElement('span');
      block.className = 'lumi-highlight-block' + (extraClass ? ' ' + extraClass : '');
      block.style.left = rect.left + 'px';
      block.style.top = rect.top + 'px';
      block.style.width = (rect.right - rect.left) + 'px';
      block.style.height = (rect.bottom - rect.top) + 'px';
      block.style.backgroundColor = color;
      block.style.borderRadius = Math.min(6, (rect.right - rect.left) / 2, (rect.bottom - rect.top) / 2) + 'px';
      layer.appendChild(block);
    });
    return merged.length;
  }

  function rebuildHighlightLayer() {
    if (!document.body) return false;
    var layer = annotationLayer('lumi-highlight-layer');
    var underlineLayer = annotationLayer('lumi-underline-layer');
    (state.highlightItems || []).forEach(function (item) {
      var range = rangeFromLocators(item.start, item.end, item.exact);
      if (!range && item.exact) {
        var quote = Object.assign({}, item.start || {}, { exact: item.exact });
        range = quoteRange(quote);
      }
      if (!range) return;
      var color = /^#[0-9a-f]{6,8}$/i.test(item.color || '') ? item.color : '#66ffeb3b';
      if (item.type === 'underline') {
        appendUnderlineRange(underlineLayer, range, color);
      } else {
        appendHighlightRange(layer, range, color);
      }
    });
    if (state.ttsHighlight) {
      var tts = state.ttsHighlight;
      var ttsRange = rangeAtOffsets(textIndex(), tts.start, tts.end);
      if (ttsRange) appendHighlightRange(
        layer, ttsRange, tts.color, 'lumi-tts-highlight-block'
      );
    }
    if (state.searchHighlight && state.searchHighlight.exact) {
      var searchRange = quoteRange(state.searchHighlight);
      if (searchRange) appendHighlightRange(
        layer, searchRange, 'rgba(255,193,7,.62)', 'lumi-search-highlight-block'
      );
    }
    return true;
  }

  function setHighlights(items) {
    state.highlightItems = Array.isArray(items) ? items : [];
    return rebuildHighlightLayer();
  }

  function setTtsHighlight(start, end, color) {
    var index = textIndex();
    var normalizedStart = Number(start);
    var normalizedEnd = Number(end);
    var normalizedColor = String(color || '');
    if (!isFinite(normalizedStart) || !isFinite(normalizedEnd) ||
        normalizedStart < 0 || normalizedEnd <= normalizedStart ||
        normalizedEnd > index.text.length ||
        !/^#[0-9a-f]{6,8}$/i.test(normalizedColor)) {
      state.ttsHighlight = null;
    } else {
      state.ttsHighlight = {
        start: Math.floor(normalizedStart),
        end: Math.floor(normalizedEnd),
        color: normalizedColor
      };
    }
    return rebuildHighlightLayer();
  }

  function clearDocumentSelection() {
    clearTimeout(selectionDispatchTimer);
    var selection = window.getSelection && window.getSelection();
    if (!selection) return false;
    var hadSelection = !selection.isCollapsed && selection.rangeCount > 0;
    try { selection.collapse(document.body || document.documentElement, 0); } catch (error) {}
    if (selection.removeAllRanges) selection.removeAllRanges();
    if (selection.empty) selection.empty();
    return hadSelection;
  }

  function interactiveFromTarget(target) {
    if (!target || !target.closest) return null;
    return target.closest(
      'a[href],area[href],button,input,select,textarea,label,summary,img[usemap],img[ismap],' +
      '[contenteditable=""],[contenteditable="true"],[role="button"],[role="link"],' +
      '[role="checkbox"],[role="menuitem"],[role="radio"],[role="switch"],[role="tab"],' +
      '[onclick],[ondblclick],[onmousedown],[onmouseup],[ontouchstart],[ontouchend]'
    );
  }

  function imageFromTarget(target) {
    var image = target && target.closest ? target.closest('img') : null;
    if (!image) return null;
    var isCoverMedia = image.getAttribute('data-lumi-cover-media') === 'true';
    if ((!isCoverMedia && interactiveFromTarget(image)) || image.hasAttribute('usemap') || image.hasAttribute('ismap')) return null;
    return image;
  }

  function postImagePreview(image) {
    if (!image) return false;
    var source = String(image.currentSrc || image.src || image.getAttribute('src') || '').trim();
    if (!source) return false;
    var bounds = image.getBoundingClientRect();
    var width = viewportWidth();
    var height = viewportHeight();
    var left = Math.max(0, Math.min(width, bounds.left));
    var right = Math.max(left, Math.min(width, bounds.right));
    var top = Math.max(0, Math.min(height, bounds.top));
    var bottom = Math.max(top, Math.min(height, bounds.bottom));
    post('image', {
      source: source,
      alt: String(image.getAttribute('alt') || ''),
      left: left,
      top: top,
      right: right,
      bottom: bottom,
      naturalWidth: Math.max(0, image.naturalWidth || 0),
      naturalHeight: Math.max(0, image.naturalHeight || 0),
      pixelRatio: Math.max(1, window.devicePixelRatio || 1)
    });
    return true;
  }

  function cancelImageLongPress() {
    clearTimeout(imageLongPressTimer);
    imageLongPressTimer = 0;
    imageLongPressTarget = null;
  }

  function beginImageLongPress(image) {
    cancelImageLongPress();
    if (!image) return;
    imageLongPressTarget = image;
    imageLongPressTimer = setTimeout(function () {
      var target = imageLongPressTarget;
      imageLongPressTimer = 0;
      imageLongPressTarget = null;
      if (target && postImagePreview(target)) {
        imageLongPressTriggered = true;
        clearDocumentSelection();
        state.suppressClickUntil = Date.now() + 700;
      }
    }, 520);
  }

  function semanticTokens(element) {
    if (!element || !element.getAttribute) return '';
    var epubType = element.getAttribute('epub:type') || '';
    try {
      epubType += ' ' + (element.getAttributeNS('http://www.idpf.org/2007/ops', 'type') || '');
    } catch (_) {}
    return (epubType + ' ' + (element.getAttribute('role') || '') + ' ' +
      (element.getAttribute('rel') || '')).toLowerCase();
  }

  function hasFootnoteSemantics(element, reference) {
    var tokens = semanticTokens(element);
    if (reference && /(^|\s)(noteref|doc-noteref)(\s|$)/.test(tokens)) return true;
    if (!reference && /(^|\s)(footnote|endnote|rearnote|doc-footnote|doc-endnote)(\s|$)/.test(tokens)) return true;
    return false;
  }

  function hasFootnoteHint(value) {
    var candidate = String(value || '');
    return /(^|[\s_#./-])(footnotes?|endnotes?|rearnotes?|notes?|fn|en)([\s_./-]|\d|$)/i.test(candidate) ||
      /(?:footnotebookmark|duokan[-_]footnote)/i.test(candidate);
  }

  function isFootnoteBacklinkHint(value) {
    return /footnotebookmark[-_]?(?:start|back)/i.test(String(value || ''));
  }

  function hasFootnoteMarkerLabel(anchor) {
    var label = String(anchor.textContent || '').replace(/\s+/g, '');
    if (!label || label.length > 16) return false;
    if (/^(?:[\[［【〔](?:[0-9０-９]{1,3}|[*＊]{1,3})[\]］】〕])+$/.test(label)) return true;
    if (/^[*＊]{1,3}$/.test(label)) return true;
    return /^[①-⑳]$/.test(label);
  }

  function decodedFragment(url) {
    var fragment = String(url && url.hash || '').replace(/^#/, '');
    if (!fragment) return '';
    try { return decodeURIComponent(fragment); } catch (_) { return fragment; }
  }

  function footnoteTarget(documentValue, fragment) {
    if (!documentValue || !fragment) return null;
    var target = documentValue.getElementById(fragment);
    if (target) return target;
    var named = documentValue.getElementsByName ? documentValue.getElementsByName(fragment) : [];
    return named && named.length ? named[0] : null;
  }

  function isFootnoteReference(anchor) {
    if (!anchor || !anchor.href || hasFootnoteSemantics(anchor, false)) return false;
    var url;
    try { url = new URL(anchor.href, document.baseURI); } catch (_) { return false; }
    var fragment = decodedFragment(url);
    if (!fragment) return false;
    // Duokan footnote bodies link back to the source marker via *_start_*.
    if (isFootnoteBacklinkHint(fragment)) return false;
    if (hasFootnoteSemantics(anchor, true)) return true;
    if (hasFootnoteMarkerLabel(anchor)) return true;
    if (hasFootnoteHint(anchor.className) || hasFootnoteHint(anchor.id) ||
        hasFootnoteHint(anchor.getAttribute('title')) || hasFootnoteHint(fragment)) return true;
    var sameDocument = url.origin === location.origin && url.pathname === location.pathname && url.search === location.search;
    var target = sameDocument ? footnoteTarget(document, fragment) : null;
    if (target && (hasFootnoteSemantics(target, false) ||
        hasFootnoteSemantics(target.closest && target.closest('aside,li,section,div'), false))) return true;
    return !!(anchor.closest && anchor.closest('sup') && hasFootnoteHint(fragment));
  }

  /**
   * 给注释引用里的图标打标记，让它们按行内小图排版（CSS 依赖 `data-lumi-footnote-marker`）。
   * 出版社把这些"注"字小图当普通插图（常见 72px），页面的块级图片规则会把它们推到单独
   * 一行并放大，表现为"注释图标位置不对"。
   */
  function markFootnoteMarkers() {
    if (!document.body) return;
    var anchors = document.body.querySelectorAll('a[href]');
    for (var i = 0; i < anchors.length; i++) {
      var anchor = anchors[i];
      if (anchor.getAttribute('data-lumi-footnote-ref') === 'true') continue;
      if (!isFootnoteReference(anchor)) continue;
      anchor.setAttribute('data-lumi-footnote-ref', 'true');
      var images = anchor.querySelectorAll('img');
      for (var j = 0; j < images.length; j++) {
        images[j].setAttribute('data-lumi-footnote-marker', 'true');
      }
    }
  }

  function sameDocumentFootnoteUrl(anchor) {
    var url;
    try { url = new URL(anchor.href, document.baseURI); } catch (_) { return null; }
    if (url.origin !== location.origin || url.pathname !== location.pathname ||
        url.search !== location.search) return null;
    return url;
  }

  /**
   * 注释正文候选块，按文档顺序：
   * 1. 语义上明确标注为注释/尾注的块级元素（`<aside epub:type="footnote">` 等）；
   * 2. 同文档注释引用所指向的正文块 —— 覆盖"段首行内锚点"形态
   *    （`<p class="zs"><a id="id1a">〔1〕</a>注释正文…</p>`），这类段落没有注释语义、
   *    只靠 class 是认不出来的，之前就会一直留在正文里。
   */
  function footnoteBodyCandidates() {
    var result = [];
    if (!document.body) return result;
    var nodes = document.body.querySelectorAll('aside,section,div,blockquote,li,dd,dt,p,td');
    for (var i = 0; i < nodes.length; i++) {
      if (hasFootnoteSemantics(nodes[i], false)) result.push(nodes[i]);
    }
    var anchors = document.body.querySelectorAll('a[href]');
    for (var a = 0; a < anchors.length; a++) {
      var url = sameDocumentFootnoteUrl(anchors[a]);
      if (!url || !isFootnoteReference(anchors[a])) continue;
      var target = footnoteTarget(document, decodedFragment(url));
      var body = footnoteContentElement(target);
      if (body && result.indexOf(body) < 0) result.push(body);
    }
    return result;
  }

  /**
   * 把同文档的注释"引用"与注释"正文"一一配对。
   *
   * 出版方导出常有错漏：注释正文没有 id，或多个引用指向同一段正文（《一生之敌》就是这样）。
   * 先按 fragment 精确配对，再按文档顺序补配，并把引用 href 改写到配对正文的 id 上，
   * 与阅读器排版引擎（EpubParser.alignFootnoteReferences）的规则保持一致。
   * 跨文档引用不在本文档处理，避免误隐藏其它章节内容。
   */
  function pairSameDocumentFootnoteBodies() {
    var pairs = [];
    if (!document.body) return pairs;
    var references = [];
    var anchors = document.body.querySelectorAll('a[href]');
    for (var a = 0; a < anchors.length; a++) {
      if (sameDocumentFootnoteUrl(anchors[a]) && isFootnoteReference(anchors[a])) references.push(anchors[a]);
    }
    if (!references.length) return pairs;
    var candidates = footnoteBodyCandidates().filter(function (node) {
      // 引用所在的段落不是注释正文；单条注释正文过大时也不当作注释，避免误隐藏整章外壳。
      for (var r = 0; r < references.length; r++) {
        if (node === references[r] || node.contains(references[r])) return false;
      }
      return String(node.textContent || '').length <= 16000;
    });
    if (!candidates.length) return pairs;
    var used = [];
    for (var i = 0; i < references.length; i++) {
      var anchor = references[i];
      var fragment = decodedFragment(sameDocumentFootnoteUrl(anchor));
      var target = fragment ? footnoteTarget(document, fragment) : null;
      // 注释正文可能是"段首行内锚点"所在的段落，先解析成气泡实际展示的那一块再配对
      var resolved = footnoteContentElement(target);
      var body = null;
      if (resolved) {
        for (var c = 0; c < candidates.length; c++) {
          if (used.indexOf(c) < 0 && candidates[c] === resolved) {
            body = candidates[c];
            used.push(c);
            break;
          }
        }
      }
      if (!body) {
        for (var n = 0; n < candidates.length; n++) {
          if (used.indexOf(n) >= 0) continue;
          body = candidates[n];
          used.push(n);
          break;
        }
        // href 指错或正文缺 id：补配后把引用改写到该正文，气泡才能取到正确内容。
        if (body && fragment !== body.id) {
          if (!body.id) body.id = 'lumi-footnote-auto-' + (i + 1);
          anchor.setAttribute('href', '#' + body.id);
        }
      }
      if (body) pairs.push({ anchor: anchor, body: body });
    }
    return pairs;
  }

  /**
   * 同文档注释正文改由气泡呈现，正文流里不再重复出现（与阅读器排版引擎一致）。
   * 标记由 CSS `[data-lumi-footnote-body="true"]` 隐藏；气泡克隆内容时会去掉该标记。
   */
  function resolveFootnoteBodies() {
    var pairs = pairSameDocumentFootnoteBodies();
    for (var i = 0; i < pairs.length; i++) {
      pairs[i].body.setAttribute('data-lumi-footnote-body', 'true');
    }
  }

  function closeFootnotePopover(immediate) {
    footnoteRequestSerial++;
    var existing = document.getElementById('lumi-footnote-popover');
    if (!existing) return;
    if (immediate) {
      existing.remove();
      return;
    }
    if (existing.getAttribute('data-state') === 'closing') return;
    existing.setAttribute('data-state', 'closing');
    var removed = false;
    var removePopover = function (event) {
      if (event && event.target !== existing) return;
      if (removed) return;
      removed = true;
      if (existing.isConnected) existing.remove();
    };
    existing.addEventListener('animationend', removePopover);
    window.setTimeout(removePopover, 220);
  }

  function createFootnotePopover(anchor) {
    closeFootnotePopover(true);
    var popover = document.createElement('aside');
    popover.id = 'lumi-footnote-popover';
    popover.setAttribute('role', 'dialog');
    popover.setAttribute('aria-modal', 'false');
    popover.setAttribute('aria-label', String(anchor.getAttribute('title') || 'Note'));
    popover.innerHTML = '<div id="lumi-footnote-content"><span id="lumi-footnote-loading" aria-hidden="true"></span></div>';
    document.documentElement.appendChild(popover);
    positionFootnotePopover(popover, anchor.getBoundingClientRect());
    window.requestAnimationFrame(function () {
      if (popover.isConnected && popover.getAttribute('data-state') !== 'closing') {
        popover.setAttribute('data-state', 'open');
      }
    });
    return popover;
  }

  function positionFootnotePopover(popover, anchorBounds) {
    if (!popover || !anchorBounds) return;
    var width = viewportWidth();
    var height = viewportHeight();
    var gap = 12;
    var edge = 10;
    var popoverWidth = Math.min(popover.offsetWidth || 280, Math.max(112, width - edge * 2));
    var popoverHeight = popover.offsetHeight || 80;
    var anchorCenter = Math.max(edge, Math.min(width - edge, (anchorBounds.left + anchorBounds.right) / 2));
    var left = Math.max(edge, Math.min(width - popoverWidth - edge, anchorCenter - popoverWidth / 2));
    var roomBelow = height - anchorBounds.bottom - gap;
    var placeBelow = roomBelow >= Math.min(popoverHeight, height * 0.34) || anchorBounds.top < height / 2;
    var top = placeBelow ? anchorBounds.bottom + gap : anchorBounds.top - popoverHeight - gap;
    top = Math.max(edge, Math.min(height - popoverHeight - edge, top));
    popover.style.left = Math.round(left) + 'px';
    popover.style.top = Math.round(top) + 'px';
    popover.style.setProperty('--lumi-footnote-arrow-x', Math.max(18, Math.min(popoverWidth - 18, anchorCenter - left)) + 'px');
    popover.setAttribute('data-placement', placeBelow ? 'below' : 'above');
  }

  function sanitizeFootnoteContent(target) {
    var clone = target.cloneNode(true);
    var removable = clone.querySelectorAll('script,style,link,iframe,frame,object,embed,form,input,textarea,select,button');
    Array.prototype.forEach.call(removable, function (node) { node.remove(); });
    var all = [clone].concat(Array.prototype.slice.call(clone.querySelectorAll('*')));
    all.forEach(function (node) {
      if (!node.removeAttribute) return;
      var tokens = semanticTokens(node);
      if (/(^|\s)(backlink|doc-backlink)(\s|$)/.test(tokens)) {
        node.remove();
        return;
      }
      node.removeAttribute('id');
      node.removeAttribute('class');
      node.removeAttribute('style');
      node.removeAttribute('hidden');
      node.removeAttribute('data-lumi-footnote-body');
      node.removeAttribute('aria-hidden');
      node.removeAttribute('href');
      node.removeAttribute('target');
    });
    var holder = document.createElement('div');
    while (clone.firstChild) holder.appendChild(clone.firstChild);
    return holder;
  }

  function loadFootnoteTarget(url, fragment) {
    var sameDocument = url.origin === location.origin && url.pathname === location.pathname && url.search === location.search;
    if (sameDocument) return Promise.resolve(footnoteTarget(document, fragment));
    if (url.origin !== location.origin) return Promise.resolve(null);
    var resourceUrl = url.href.replace(/#.*$/, '');
    return fetch(resourceUrl, { credentials: 'omit' }).then(function (response) {
      if (!response.ok) return null;
      return response.text();
    }).then(function (source) {
      if (!source) return null;
      var parsed = new DOMParser().parseFromString(source, 'text/html');
      return footnoteTarget(parsed, fragment);
    }).catch(function () { return null; });
  }

  /**
   * 注释正文元素。
   *
   * 有些书（如《毛泽东选集》）的注释正文是"段首行内锚点"：
   * `<p class="zs"><a id="id1a">〔1〕</a>注释正文…</p>`，锚点本身只有编号。
   * 这种情况要取它所在的段落，否则气泡里只有"〔1〕"，看起来就是"注释没有内容"。
   */
  function footnoteContentElement(target) {
    if (!target) return null;
    var tag = String(target.tagName || '').toUpperCase();
    var inline = tag === 'A' || tag === 'SPAN' || tag === 'SUP' || tag === 'EM' ||
      tag === 'I' || tag === 'B' || tag === 'STRONG' || tag === 'FONT' || tag === 'SMALL';
    if (!inline) return target;
    var block = target.closest ? target.closest('p,li,dd,dt,td,blockquote,aside,section,div,figure') : null;
    if (!block || block === target) return target;
    return footnoteLeadingText(block, target) ? block : target;
  }

  /** 锚点之前是否只剩注释编号之类的标记（是则说明整块都是注释正文）。 */
  function footnoteLeadingText(block, node) {
    var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT, null);
    var text = '';
    var current;
    while ((current = walker.nextNode())) {
      var relation = current.compareDocumentPosition(node);
      if (relation & Node.DOCUMENT_POSITION_CONTAINS) break;
      if (relation & Node.DOCUMENT_POSITION_FOLLOWING) {
        text += String(current.nodeValue || '');
        continue;
      }
      break;
    }
    return /^[\s\[［【〔(（\]］】〕)）\d０-９*＊①-⑳※注:：.、，,；;]*$/.test(text);
  }

  function showFootnotePopover(anchor) {
    var url;
    try { url = new URL(anchor.href, document.baseURI); } catch (_) { return Promise.resolve(false); }
    var fragment = decodedFragment(url);
    if (!fragment) return Promise.resolve(false);
    var popover = createFootnotePopover(anchor);
    var requestSerial = footnoteRequestSerial;
    return loadFootnoteTarget(url, fragment).then(function (target) {
      var body = footnoteContentElement(target);
      if (!body || requestSerial !== footnoteRequestSerial || !popover.isConnected) {
        if (popover.isConnected) closeFootnotePopover();
        return false;
      }
      var content = popover.querySelector('#lumi-footnote-content');
      var sanitized = sanitizeFootnoteContent(body);
      content.textContent = '';
      while (sanitized.firstChild) content.appendChild(sanitized.firstChild);
      if (!content.textContent.trim() && !content.querySelector('img,svg,math')) {
        closeFootnotePopover();
        return false;
      }
      positionFootnotePopover(popover, anchor.getBoundingClientRect());
      return true;
    });
  }

  document.addEventListener('touchstart', function (event) {
    cancelImageLongPress();
    imageLongPressTriggered = false;
    touchPaging = false;
    pageStageDurationOverride = 0;
    if (event.target && event.target.closest && event.target.closest('#lumi-footnote-popover')) return;
    touchVelocityX = 0;
    if (event.touches.length !== 1) return;
    var touch = event.touches[0];
    touchStartX = touch.clientX;
    touchStartY = touch.clientY;
    touchLastX = touch.clientX;
    touchStartTime = Date.now();
    touchLastTime = touchStartTime;
    if (state.flow === 'scrolled' && !state.fixed && !scrollChapterTurnPending) {
      resetScrolledChapterDrag(false);
      var boundary = scrollBoundaryState();
      scrollChapterStartedAtTop = boundary.atTop;
      scrollChapterStartedAtBottom = boundary.atBottom;
      scrollChapterDragStartY = touchStartY;
    }
    var initialAnchor = event.target && event.target.closest ?
      event.target.closest('a[href],area[href]') : null;
    // Footnote markers are often tiny images wrapped in an anchor. Let their
    // short tap reach the link handler instead of arming image preview.
    beginImageLongPress(
      initialAnchor && isFootnoteReference(initialAnchor) ? null : imageFromTarget(event.target)
    );
    if (pageStageActive) {
      settleActivePageStageForInput(true);
      pageStageDurationOverride = state.transition === 'curl' ? 210 : 170;
    }
    touchBaseX = state.flow === 'paginated' ? pageX(state.page) : 0;
  }, { passive: true, capture: true });

  document.addEventListener('touchmove', function (event) {
    if (event.target && event.target.closest && event.target.closest('#lumi-footnote-popover')) return;
    if (event.touches.length !== 1) { cancelImageLongPress(); return; }
    var touch = event.touches[0];
    var dx = touch.clientX - touchStartX;
    var dy = touch.clientY - touchStartY;
    if (Math.abs(dx) >= 12 || Math.abs(dy) >= 12) cancelImageLongPress();
    if (state.flow === 'scrolled' && !state.fixed) {
      if (scrollChapterTurnPending) {
        event.preventDefault();
        return;
      }
      var direction = dy < 0 ? -1 : 1;
      var boundary = scrollBoundaryState();
      var canTurn = direction < 0 ? state.canTurnNext : state.canTurnPrevious;
      var atBoundary = direction < 0 ? boundary.atBottom : boundary.atTop;
      if (!scrollChapterDragDirection && canTurn && atBoundary &&
          Math.abs(dy) >= 6 && Math.abs(dy) > Math.abs(dx)) {
        scrollChapterDragDirection = direction;
        var startedAtBoundary = direction < 0 ? scrollChapterStartedAtBottom : scrollChapterStartedAtTop;
        scrollChapterDragStartY = startedAtBoundary ? touchStartY : touch.clientY;
      }
      if (scrollChapterDragDirection) {
        var offset = touch.clientY - scrollChapterDragStartY;
        if (offset * scrollChapterDragDirection <= 0) {
          resetScrolledChapterDrag(false);
          return;
        }
        touchPaging = true;
        event.preventDefault();
        updateScrolledChapterDrag(offset);
      }
      return;
    }
    if (state.nativePaging || state.flow !== 'paginated' || state.fixed) return;
    if (!touchPaging && (Math.abs(dx) < 6 || Math.abs(dx) <= Math.abs(dy))) return;
    touchPaging = true;
    event.preventDefault();
    var now = Date.now();
    var elapsed = Math.max(1, now - touchLastTime);
    touchVelocityX = (touch.clientX - touchLastX) / elapsed;
    touchLastX = touch.clientX;
    touchLastTime = now;
    if (state.transition === 'fade' || state.transition === 'none') return;
    var advances = state.reverseAxis ? dx > 0 : dx < 0;
    var targetPage = state.page + (advances ? 1 : -1);
    var effectiveDx = targetPage >= 0 && targetPage < state.total ? dx : dx * 0.28;
    if (targetPage >= 0 && targetPage < state.total && preparePageStage(targetPage)) {
      updatePageStage(Math.abs(effectiveDx) / Math.max(1, state.viewportWidth), effectiveDx);
    } else {
      var body = document.body;
      body.style.visibility = 'visible';
      body.style.transition = 'none';
      body.style.opacity = '1';
      body.style.transform = 'translate3d(' + (touchBaseX + effectiveDx) + 'px,0,0)';
    }
  }, { passive: false });

  document.addEventListener('touchend', function (event) {
    var completedImageLongPress = imageLongPressTriggered;
    cancelImageLongPress();
    imageLongPressTriggered = false;
    if (completedImageLongPress) {
      event.preventDefault();
      touchPaging = false;
      state.suppressClickUntil = Date.now() + 700;
      return;
    }
    var activePopover = document.getElementById('lumi-footnote-popover');
    var insidePopover = event.target && event.target.closest && event.target.closest('#lumi-footnote-popover');
    if (insidePopover) { touchPaging = false; return; }
    if (activePopover) {
      event.preventDefault();
      closeFootnotePopover();
      touchPaging = false;
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (event.changedTouches.length !== 1) { touchPaging = false; return; }
    var touch = event.changedTouches[0];
    var dx = touch.clientX - touchStartX;
    var dy = touch.clientY - touchStartY;
    var elapsed = Math.max(1, Date.now() - touchStartTime);
    var velocityX = Math.abs(touchVelocityX) > 0.01 ? touchVelocityX : dx / elapsed;
    var anchor = event.target && event.target.closest ? event.target.closest('a[href],area[href]') : null;
    var interactiveTarget = interactiveFromTarget(event.target);
    var tappedImage = imageFromTarget(event.target);
    var footnoteAnchor = anchor && isFootnoteReference(anchor);

    if (state.flow === 'scrolled' && !state.fixed) {
      pageStageDurationOverride = 0;
      if (scrollChapterDragDirection) {
        event.preventDefault();
        var chapterSwipe = Math.abs(scrollChapterDragOffset) >= 52 &&
          Math.abs(scrollChapterDragOffset) > Math.abs(dx) * 1.2;
        if (chapterSwipe) completeScrolledChapterTurn();
        else resetScrolledChapterDrag(true);
        state.suppressClickUntil = Date.now() + 450;
      }
      return;
    }

    var horizontal = state.flow === 'paginated' && !state.fixed && Math.abs(dx) >= Math.abs(dy) * 1.15;
    var shouldTurn = !state.nativePaging && horizontal && (Math.abs(dx) >= Math.min(72, state.viewportWidth * 0.16) ||
      (Math.abs(dx) >= 18 && Math.abs(velocityX) >= 0.42));
    var imageTap = !footnoteAnchor && !touchPaging && !shouldTurn && !!tappedImage &&
      Math.abs(dx) < 12 && Math.abs(dy) < 12;
    var tapRatio = touch.clientX / viewportWidth();
    var centerImageTap = imageTap && tapRatio >= 0.3 && tapRatio <= 0.7;
    // Covers are commonly wrapped in an anchor by EPUB generators. A short
    // center tap on that image is still the reader menu gesture; edge taps
    // and non-cover links retain their normal link/image behavior.
    var isTap = !footnoteAnchor && !touchPaging && !shouldTurn && (!anchor || centerImageTap) &&
      (!interactiveTarget || centerImageTap) && (!tappedImage || centerImageTap) &&
      Math.abs(dx) < 12 && Math.abs(dy) < 12;
    var selection = window.getSelection && window.getSelection();
    var hasSelection = !!(selection && !selection.isCollapsed && selection.rangeCount > 0);
    if (touchPaging || shouldTurn || imageTap || isTap) event.preventDefault();
    var wasPaging = touchPaging;
    touchPaging = false;
    if ((imageTap || isTap) && hasSelection) {
      pageStageDurationOverride = 0;
      clearDocumentSelection();
      post('selectionCleared', {});
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (shouldTurn) {
      var advances = state.reverseAxis ? dx > 0 : dx < 0;
      var target = state.page + (advances ? 1 : -1);
      if (target >= 0 && target < state.total) moveToPage(target, true);
      else {
        if (wasPaging && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
        else pageStageDurationOverride = 0;
        post('chapterTurn', { direction: advances ? 1 : -1 });
      }
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (wasPaging && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
    if (imageTap && !centerImageTap) {
      pageStageDurationOverride = 0;
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (isTap && (!window.getSelection || window.getSelection().isCollapsed)) {
      var ratio = tapRatio;
      if (ratio < 0.3) {
        if (state.nativePaging) post('tap', { zone: 'left' });
        else turnByDirection(state.edgeTapLeft);
      }
      else if (ratio > 0.7) {
        if (state.nativePaging) post('tap', { zone: 'right' });
        else turnByDirection(state.edgeTapRight);
      }
      else {
        pageStageDurationOverride = 0;
        post('tap', { zone: 'center' });
      }
      state.suppressClickUntil = Date.now() + 450;
    }
    else pageStageDurationOverride = 0;
  }, { passive: false });

  document.addEventListener('touchcancel', function () {
    cancelImageLongPress();
    imageLongPressTriggered = false;
    if (scrollChapterDragDirection) resetScrolledChapterDrag(true);
    else if (touchPaging && state.flow === 'paginated' && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
    else pageStageDurationOverride = 0;
    touchPaging = false;
  }, { passive: true });

  document.addEventListener('submit', function (event) { event.preventDefault(); }, true);

  document.addEventListener('contextmenu', function (event) {
    var image = imageFromTarget(event.target);
    if (!image) return;
    event.preventDefault();
    event.stopPropagation();
    cancelImageLongPress();
    if (!imageLongPressTriggered && postImagePreview(image)) {
      imageLongPressTriggered = true;
      clearDocumentSelection();
      state.suppressClickUntil = Date.now() + 700;
    }
  }, true);

  document.addEventListener('click', function (event) {
    if (Date.now() < state.suppressClickUntil) { event.preventDefault(); return; }
    var activePopover = document.getElementById('lumi-footnote-popover');
    var insidePopover = event.target && event.target.closest && event.target.closest('#lumi-footnote-popover');
    if (insidePopover) return;
    if (activePopover) {
      closeFootnotePopover();
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    var anchor = event.target && event.target.closest ? event.target.closest('a[href],area[href]') : null;
    var tappedImage = imageFromTarget(event.target);
    if (tappedImage && !(anchor && isFootnoteReference(anchor))) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    if (anchor) {
      event.preventDefault();
      event.stopPropagation();
      if (isFootnoteReference(anchor)) {
        showFootnotePopover(anchor).then(function (shown) {
          if (!shown) post('link', { href: anchor.href });
        });
      } else {
        post('link', { href: anchor.href });
      }
      return;
    }
    if (interactiveFromTarget(event.target)) return;
    var activeSelection = window.getSelection && window.getSelection();
    if (activeSelection && !activeSelection.isCollapsed) {
      clearDocumentSelection();
      post('selectionCleared', {});
      event.preventDefault();
      return;
    }
    var ratio = event.clientX / viewportWidth();
    if (ratio < 0.3) {
      if (state.nativePaging) post('tap', { zone: 'left' });
      else turnByDirection(state.edgeTapLeft);
    }
    else if (ratio > 0.7) {
      if (state.nativePaging) post('tap', { zone: 'right' });
      else turnByDirection(state.edgeTapRight);
    }
    else post('tap', { zone: 'center' });
  }, true);

  document.addEventListener('selectionchange', function () {
    clearTimeout(selectionDispatchTimer);
    selectionDispatchTimer = setTimeout(function () {
      var selection = window.getSelection();
      if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
        post('selectionCleared', {});
        return;
      }
      var range = selection.getRangeAt(0);
      var selectedText = selection.toString();
      var selectionIndex = textIndex();
      var selectionStart = textOffsetForBoundary(selectionIndex, range.startContainer, range.startOffset);
      var selectionEnd = textOffsetForBoundary(selectionIndex, range.endContainer, range.endOffset);
      var selectionQuote = {
        index: selectionIndex,
        quoteStart: selectionStart,
        quoteEnd: selectionEnd,
        exact: selectedText
      };
      var width = viewportWidth();
      var height = viewportHeight();
      var rects = Array.prototype.slice.call(range.getClientRects()).filter(function (item) {
        return item.width > 0 && item.height > 0 && item.right > 0 && item.left < width &&
          item.bottom > 0 && item.top < height;
      });
      var bounds = range.getBoundingClientRect();
      if (rects.length) {
        bounds = {
          left: Math.min.apply(null, rects.map(function (item) { return item.left; })),
          right: Math.max.apply(null, rects.map(function (item) { return item.right; })),
          top: Math.min.apply(null, rects.map(function (item) { return item.top; })),
          bottom: Math.max.apply(null, rects.map(function (item) { return item.bottom; }))
        };
      }
      var left = Math.max(0, Math.min(width, bounds.left));
      var right = Math.max(left, Math.min(width, bounds.right));
      var top = Math.max(0, Math.min(height, bounds.top));
      var bottom = Math.max(top, Math.min(height, bounds.bottom));
      post('selection', {
        text: selectedText,
        start: locator(range.startContainer, range.startOffset, Object.assign({}, selectionQuote, {
          textPosition: selectionStart
        })),
        end: locator(range.endContainer, range.endOffset, Object.assign({}, selectionQuote, {
          textPosition: selectionEnd
        })),
        x: (left + right) / 2,
        y: (top + bottom) / 2,
        left: left,
        right: right,
        top: top,
        bottom: bottom,
        pixelRatio: Math.max(1, window.devicePixelRatio || 1)
      });
    }, 160);
  });

  function scheduleRepagination() {
    if (!state.ready || state.paginating || pageStageActive || touchPaging) return;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (!state.paginating) paginate(state.total > 1 ? state.page / (state.total - 1) : 0);
    }, 120);
  }

  window.addEventListener('resize', function () {
    var width = viewportWidth();
    var height = viewportHeight();
    if (width === state.viewportWidth && height === state.viewportHeight) return;
    scheduleRepagination();
  });

  window.addEventListener('scroll', function () {
    if (state.fixed || state.scrollGuard) return;
    if (state.flow === 'scrolled') {
      clearTimeout(scrollNotifyTimer);
      scrollNotifyTimer = setTimeout(function () {
        var root = document.scrollingElement || document.documentElement;
        var extent = Math.max(root.scrollHeight, document.body.scrollHeight, state.viewportHeight);
        state.total = Math.max(1, Math.ceil(extent / Math.max(1, state.viewportHeight)));
        var nextPage = Math.max(0, Math.min(state.total - 1,
          Math.floor((window.scrollY + state.viewportHeight * 0.42) / Math.max(1, state.viewportHeight))));
        if (nextPage !== state.page) {
          state.page = nextPage;
          ++pageNotifySerial;
          post('page', currentPagePayload());
        }
      }, 70);
      return;
    }
    if (window.scrollX === 0 && window.scrollY === 0) return;
    state.scrollGuard = true;
    window.scrollTo(0, 0);
    requestAnimationFrame(function () { state.scrollGuard = false; });
  }, { passive: true });

  document.addEventListener('load', function (event) {
    var target = event.target;
    if (target && target.closest && target.closest('#lumi-page-stage')) return;
    if (target && /^(IMG|VIDEO|SVG)$/i.test(target.tagName || '')) scheduleRepagination();
  }, true);

  document.addEventListener('visibilitychange', function () {
    if (!document.hidden || !pageStageActive) return;
    touchPaging = false;
    settlePageStage(state.page);
  });

  if (document.fonts && document.fonts.addEventListener) {
    document.fonts.addEventListener('loadingdone', scheduleRepagination);
  }

  window.LumiReader = {
    configure: configure,
    next: function () { turnByDirection(1); },
    previous: function () { turnByDirection(-1); },
    goToPage: function (page) { moveToPage(page, true); },
    goToProgression: goToProgression,
    currentPosition: function () { return currentPagePayload(); },
    syncToPage: syncToPage,
    preparePage: preparePage,
    goToFragment: function (fragment) {
      var id = decodeURIComponent(String(fragment || '').replace(/^#/, ''));
      var target = document.getElementById(id);
      if (!target) return false;
      var range = document.createRange();
      range.selectNodeContents(target);
      // 不能 collapse：折叠后的空 range 没有 client rect，getBoundingClientRect()
      // 返回全零矩形，导致 pageForRange 算出的是当前页，跳转静默失效。
      moveToPage(Math.max(0, Math.min(state.total - 1, pageForRange(range))), true);
      return true;
    },
    restore: restore,
    currentLocator: currentLocator,
    setHighlights: setHighlights,
    setTtsHighlight: setTtsHighlight,
    findText: findText,
    clearSearchHighlight: clearSearchHighlight,
    cancelChapterTurn: function () { resetScrolledChapterDrag(true); },
    pageText: pageText,
    visibleText: function () { return document.body ? document.body.innerText : ''; },
    repaginate: function () {
      if (state.ready && !state.paginating && !pageStageActive) {
        paginate(state.total > 1 ? state.page / (state.total - 1) : 0);
      }
    },
    setTransition: function (transition) {
      state.transition = transition === 'curl' || transition === 'fade' || transition === 'none' ? transition : 'slide';
      return state.transition;
    }
  };

  settleMedia().then(function () {
    state.mediaSettled = true;
    if (state.configured) paginate(state.pendingProgression);
  });
})();
"""
}
