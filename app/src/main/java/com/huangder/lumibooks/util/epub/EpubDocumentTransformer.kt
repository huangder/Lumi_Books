package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.ByteArrayInputStream

object EpubDocumentTransformer {
    fun transform(resource: EpubResource, layout: EpubRenditionLayout): ByteArray {
        val document = parseAndSanitize(resource)
        return transform(document, layout)
    }

    /**
     * 将已解析（并 sanitize 过）的文档包装成阅读器文档：
     * 注入 viewport、分页 CSS 与 LumiReader 脚本。
     * MOBI 等非 EPUB 来源复用同一包装，保证分页/定位/高亮脚本行为一致。
     */
    fun transform(document: Document, layout: EpubRenditionLayout): ByteArray {
        val head = document.head().takeIf { it.tagName().isNotBlank() }
            ?: document.prependElement("head")
        if (head.selectFirst("meta[name=viewport]") == null) {
            head.prependElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1, maximum-scale=5")
        }
        head.appendElement("style").attr("id", "lumi-reader-style").appendText(READER_CSS)
        document.body().attr("data-lumi-layout", layout.name.lowercase())
        document.body().appendElement("script").attr("id", "lumi-reader-script").appendText(READER_SCRIPT)
        return document.outerHtml().toByteArray(Charsets.UTF_8)
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
  max-width: 100%;
  /* 竖版大图按整页宽度放大后会超出页面高度、下半截被裁掉；
     限制高度不超过当前分页的内容区高度，保持比例缩放到整页可见。 */
  max-height: var(--lumi-content-height, calc(var(--lumi-page-height, 100vh) - 32px));
  object-fit: contain;
}
body[data-lumi-layout="reflowable"] table,
body[data-lumi-layout="reflowable"] pre {
  max-width: 100%;
  overflow-x: auto;
}
html.lumi-ignore-publisher-background:not(.lumi-night):not(.lumi-sepia):not(.lumi-green) {
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
::selection { background: rgba(255, 193, 7, 0.42); }
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
  box-shadow: 0 18px 52px rgba(0, 0, 0, 0.15), 0 5px 18px rgba(0, 0, 0, 0.08);
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
#lumi-highlight-layer {
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
  z-index: -1;
}
.lumi-highlight-block {
  position: absolute;
  pointer-events: none;
  box-sizing: border-box;
}
.lumi-search-highlight-block {
  animation: lumi-search-highlight-pulse 2000ms linear forwards;
}
html.lumi-sepia #lumi-footnote-popover { background: #fff8ee; color: #3e2723; }
html.lumi-green #lumi-footnote-popover { background: #f3fbf3; color: #1b4d27; }
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

    private val READER_SCRIPT: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString(READER_SCRIPT_PART_1.length + READER_SCRIPT_PART_2.length) {
            append(READER_SCRIPT_PART_1)
            append(READER_SCRIPT_PART_2)
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
    transition: 'slide', nativePaging: false, animationTimer: 0, suppressClickUntil: 0, preservePublisherBackground: true,
    edgeTapLeft: -1, edgeTapRight: 1,
    bionicReading: false, chineseMode: 'original', chineseMap: null, pendingPreparedPage: null, prepareSerial: 0,
    highlightItems: [], searchHighlight: null,
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
    clone.querySelectorAll('#lumi-reader-script, #lumi-page-stage, #lumi-publisher-background').forEach(function (node) {
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
      if (root.classList.contains('lumi-night')) color = '#111111';
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
      post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
    var baseDuration = state.transition === 'curl' ? 360 : 260;
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
        post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
    if (state.flow === 'scrolled') {
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
        body.style.transition = 'opacity 90ms ease-out';
        body.style.opacity = '0';
        state.animationTimer = setTimeout(function () {
          body.style.transition = 'none';
          body.style.transform = 'translate3d(' + x + 'px,0,0)';
          void body.offsetWidth;
          body.style.transition = 'opacity 140ms ease-in';
          body.style.opacity = '1';
          state.animationTimer = setTimeout(function () { body.style.transition = 'none'; }, 160);
        }, 90);
      } else {
        body.style.opacity = '1';
        body.style.transition = 'none';
        body.style.transform = 'translate3d(' + x + 'px,0,0)';
      }
    }
    if (notify !== false) {
      var notifyPage = function () {
        if (notificationSerial !== pageNotifySerial || state.page !== targetPage) return;
        post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
    if (state.flow === 'scrolled') {
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
        post('page', {
          pageIndex: state.page,
          pageCount: state.total,
          reverseAxis: state.reverseAxis,
          locator: currentLocator()
        });
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

  function capturePublisherBackground(body) {
    if (state.publisherBackground) return;
    var root = document.documentElement;
    var rootStyle = window.getComputedStyle(root);
    var bodyStyle = window.getComputedStyle(body);
    var hasPaint = function (style) {
      var image = String(style.backgroundImage || 'none');
      var color = String(style.backgroundColor || 'transparent').replace(/\s+/g, '');
      return image !== 'none' && image !== '' || !/^(transparent|rgba(0,0,0,0))$/i.test(color);
    };
    var source = hasPaint(rootStyle) ? rootStyle : (hasPaint(bodyStyle) ? bodyStyle : null);
    if (!source) return;
    var computed = {
      color: source.backgroundColor,
      image: source.backgroundImage,
      size: source.backgroundSize,
      position: source.backgroundPosition,
      repeat: source.backgroundRepeat,
      attachment: source.backgroundAttachment,
      origin: source.backgroundOrigin,
      clip: source.backgroundClip,
      blendMode: source.backgroundBlendMode
    };
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
    var bodyStyle = window.getComputedStyle(document.body);
    var bodyImage = String(bodyStyle.backgroundImage || 'none');
    var bodyColor = String(bodyStyle.backgroundColor || 'transparent').replace(/\s+/g, '');
    var bodyHasPaint = bodyImage !== 'none' && bodyImage !== '' ||
      !/^(transparent|rgba(0,0,0,0))$/i.test(bodyColor);
    var computed = bodyHasPaint ? {
      color: bodyStyle.backgroundColor,
      image: bodyStyle.backgroundImage,
      size: bodyStyle.backgroundSize,
      position: bodyStyle.backgroundPosition,
      repeat: bodyStyle.backgroundRepeat,
      attachment: bodyStyle.backgroundAttachment,
      origin: bodyStyle.backgroundOrigin,
      clip: bodyStyle.backgroundClip,
      blendMode: bodyStyle.backgroundBlendMode
    } : (saved && saved.computed ? saved.computed : null);
    if (!computed) return;
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

  function readerBox() {
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

  function applyPaginationBox(body) {
    var inset = readerBox();
    var horizontalInset = Math.min(state.viewportWidth - 1, Math.max(0, inset.left + inset.right));
    clearPublisherRootHorizontalInset();
    body.style.setProperty('--lumi-page-height', state.viewportHeight + 'px');
    body.style.setProperty(
      '--lumi-content-height',
      Math.max(1, Math.round(state.viewportHeight - inset.top - inset.bottom)) + 'px'
    );
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
      var scale = Math.min(state.viewportWidth / Math.max(1, designWidth), state.viewportHeight / Math.max(1, designHeight));
      body.style.width = designWidth + 'px';
      body.style.height = designHeight + 'px';
      body.style.transform = 'scale(' + scale + ')';
      body.style.marginLeft = Math.max(0, (state.viewportWidth - designWidth * scale) / 2) + 'px';
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
        ? Math.floor(restoreProgression * state.total + 0.000001)
        : state.page;
      moveToPage(target, false);
    }
    body.style.visibility = 'visible';
    state.ready = true;
    state.paginating = false;
    rebuildHighlightLayer();
    fulfillPreparedPageRequest();
    post('ready', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
    if (!family && !textColor) {
      if (existing) existing.remove();
      return;
    }
    var style = existing || document.createElement('style');
    style.id = 'lumi-reader-overrides';
    var rules = '';
    if (fontUrl) {
      rules += '@font-face{font-family:"Lumi Reader Override";src:url(' + JSON.stringify(fontUrl) + ');font-display:swap;}';
    }
    var textSelector = 'body,p,div,section,article,aside,header,footer,nav,h1,h2,h3,h4,h5,h6,' +
      'span,a,li,dt,dd,td,th,blockquote,figcaption,label';
    if (family) rules += textSelector + '{font-family:' + JSON.stringify(family) + ' !important;}';
    if (textColor) rules += textSelector + '{color:' + textColor + ' !important;}';
    style.textContent = rules;
    if (!existing) document.head.appendChild(style);
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
    state.nativePaging = config.nativePaging === true;
    state.edgeTapLeft = Number(config.edgeTapLeft) > 0 ? 1 : -1;
    state.edgeTapRight = Number(config.edgeTapRight) < 0 ? -1 : 1;
    var insets = config.insets || {};
    state.insets = {
      top: Math.max(0, Number(insets.top) || 0), right: Math.max(0, Number(insets.right) || 0),
      bottom: Math.max(0, Number(insets.bottom) || 0), left: Math.max(0, Number(insets.left) || 0)
    };
    state.pendingProgression = typeof liveProgression === 'number' ? liveProgression :
      (typeof config.progressionValue === 'number' ? config.progressionValue : undefined);
    state.preservePublisherBackground = config.preservePublisherBackground !== false;
    state.configured = true;
    capturePublisherBackground(document.body);
    applyReaderOverrides(config);
    applyChineseConversion(config);
    applyBionicReading(config.bionicReading === true);
    document.documentElement.classList.toggle('lumi-ignore-publisher-background', !state.preservePublisherBackground);
    document.documentElement.classList.remove('lumi-night', 'lumi-sepia', 'lumi-green');
    if (config.theme === 'night') document.documentElement.classList.add('lumi-night');
    if (config.theme === 'sepia') document.documentElement.classList.add('lumi-sepia');
    if (config.theme === 'green') document.documentElement.classList.add('lumi-green');
    if (state.ready || state.mediaSettled) {
      paginate(state.pendingProgression);
      if (liveLocator) restore(liveLocator);
    }
  }

  function restore(target) {
    if (!target) return false;
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
    if (range) {
      moveToPage(Math.max(0, Math.min(state.total - 1, pageForRange(range))), true);
      return true;
    }
    if (typeof target.progression === 'number') {
      moveToPage(Math.round(target.progression * Math.max(0, state.total - 1)), true);
      return true;
    }
    return false;
  }

  function pageText(pageIndex) {
    var targetPage = Math.max(0, Math.min(Number(pageIndex) || 0, state.total - 1));
    var index = textIndex();
    var parts = [];
    for (var i = 0; i < index.nodes.length; i++) {
      var info = index.nodes[i];
      if (!info.node.nodeValue || !info.node.nodeValue.trim()) continue;
      var range = document.createRange();
      range.selectNodeContents(info.node);
      var rects = range.getClientRects();
      var belongs = false;
      for (var r = 0; r < rects.length; r++) {
        var rect = rects[r];
        var physicalPage = physicalPageForRect(rect);
        var rectPage = state.flow === 'scrolled' ? physicalPage : logicalPageForPhysical(physicalPage);
        if (rectPage === targetPage) { belongs = true; break; }
      }
      if (belongs) parts.push(info.node.nodeValue);
    }
    return {
      pageIndex: targetPage,
      pageCount: state.total,
      text: parts.join(' ').replace(/\s+/g, ' ').trim()
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

  function highlightLayer() {
    var existing = document.getElementById('lumi-highlight-layer');
    if (existing) existing.remove();
    var layer = document.createElement('div');
    layer.id = 'lumi-highlight-layer';
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

  function appendHighlightRange(layer, range, color, extraClass) {
    if (!range) return 0;
    var layerRect = layer.getBoundingClientRect();
    var scaleX = layer.offsetWidth > 0 ? layerRect.width / layer.offsetWidth : 1;
    var scaleY = layer.offsetHeight > 0 ? layerRect.height / layer.offsetHeight : scaleX;
    if (!isFinite(scaleX) || scaleX <= 0) scaleX = 1;
    if (!isFinite(scaleY) || scaleY <= 0) scaleY = scaleX;
    var rects = Array.prototype.slice.call(range.getClientRects()).filter(function (rect) {
      return rect.width > 0 && rect.height > 0;
    }).map(function (rect) {
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
    var layer = highlightLayer();
    (state.highlightItems || []).forEach(function (item) {
      var range = rangeFromLocators(item.start, item.end, item.exact);
      if (!range && item.exact) {
        var quote = Object.assign({}, item.start || {}, { exact: item.exact });
        range = quoteRange(quote);
      }
      if (!range) return;
      var color = /^#[0-9a-f]{6,8}$/i.test(item.color || '') ? item.color : '#66ffeb3b';
      appendHighlightRange(layer, range, color);
    });
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
    if (interactiveFromTarget(image) || image.hasAttribute('usemap') || image.hasAttribute('ismap')) return null;
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
    return /(^|[\s_#./-])(footnotes?|endnotes?|rearnotes?|notes?|fn|en)([\s_./-]|\d|$)/i.test(String(value || ''));
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
    if (hasFootnoteSemantics(anchor, true)) return true;
    if (hasFootnoteHint(anchor.className) || hasFootnoteHint(anchor.id) ||
        hasFootnoteHint(anchor.getAttribute('title')) || hasFootnoteHint(fragment)) return true;
    var sameDocument = url.origin === location.origin && url.pathname === location.pathname && url.search === location.search;
    var target = sameDocument ? footnoteTarget(document, fragment) : null;
    if (target && (hasFootnoteSemantics(target, false) ||
        hasFootnoteSemantics(target.closest && target.closest('aside,li,section,div'), false))) return true;
    return !!(anchor.closest && anchor.closest('sup') && hasFootnoteHint(fragment));
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

  function showFootnotePopover(anchor) {
    var url;
    try { url = new URL(anchor.href, document.baseURI); } catch (_) { return Promise.resolve(false); }
    var fragment = decodedFragment(url);
    if (!fragment) return Promise.resolve(false);
    var popover = createFootnotePopover(anchor);
    var requestSerial = footnoteRequestSerial;
    return loadFootnoteTarget(url, fragment).then(function (target) {
      if (!target || requestSerial !== footnoteRequestSerial || !popover.isConnected) {
        if (popover.isConnected) closeFootnotePopover();
        return false;
      }
      var content = popover.querySelector('#lumi-footnote-content');
      var sanitized = sanitizeFootnoteContent(target);
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
    beginImageLongPress(imageFromTarget(event.target));
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

    if (state.flow === 'scrolled' && !state.fixed) {
      pageStageDurationOverride = 0;
      var root = document.scrollingElement || document.documentElement;
      var atTop = window.scrollY <= 1;
      var atBottom = window.scrollY + state.viewportHeight >= root.scrollHeight - 2;
      var chapterSwipe = Math.abs(dy) >= 52 && Math.abs(dy) > Math.abs(dx) * 1.2;
      if (chapterSwipe && ((dy > 0 && atTop) || (dy < 0 && atBottom))) {
        post('chapterTurn', { direction: dy < 0 ? 1 : -1 });
        state.suppressClickUntil = Date.now() + 450;
      }
      return;
    }

    var horizontal = state.flow === 'paginated' && !state.fixed && Math.abs(dx) >= Math.abs(dy) * 1.15;
    var shouldTurn = !state.nativePaging && horizontal && (Math.abs(dx) >= Math.min(72, state.viewportWidth * 0.16) ||
      (Math.abs(dx) >= 18 && Math.abs(velocityX) >= 0.42));
    var imageTap = !touchPaging && !shouldTurn && !!tappedImage && Math.abs(dx) < 12 && Math.abs(dy) < 12;
    var isTap = !touchPaging && !shouldTurn && !anchor && !interactiveTarget && !tappedImage && Math.abs(dx) < 12 && Math.abs(dy) < 12;
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
    if (imageTap) {
      pageStageDurationOverride = 0;
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (isTap && (!window.getSelection || window.getSelection().isCollapsed)) {
      var ratio = touch.clientX / viewportWidth();
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
    if (touchPaging && state.flow === 'paginated' && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
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
    var tappedImage = imageFromTarget(event.target);
    if (tappedImage) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    var anchor = event.target && event.target.closest ? event.target.closest('a[href],area[href]') : null;
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
          post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
    findText: findText,
    clearSearchHighlight: clearSearchHighlight,
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
