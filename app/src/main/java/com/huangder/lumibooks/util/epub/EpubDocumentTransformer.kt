package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream

object EpubDocumentTransformer {
    fun transform(resource: EpubResource, layout: EpubRenditionLayout): ByteArray {
        val document = Jsoup.parse(
            ByteArrayInputStream(resource.bytes),
            null,
            resource.path,
            Parser.xmlParser()
        )
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).charset(Charsets.UTF_8)
        sanitize(document)
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
  max-height: 100%;
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

    private const val READER_SCRIPT = """
(function () {
  'use strict';
  var state = {
    page: 0, total: 1, progression: 'ltr', fixed: false, flow: 'paginated', ready: false,
    writingMode: 'horizontal-tb', reverseAxis: false, pageStep: 1, pageOffsets: [0],
    viewportWidth: 0, viewportHeight: 0, paginating: false, configured: false, mediaSettled: false,
    pendingProgression: undefined, publisherBox: null, publisherBackground: null, scrollGuard: false, initialFragmentApplied: false,
    transition: 'slide', animationTimer: 0, suppressClickUntil: 0, preservePublisherBackground: true,
    bionicReading: false, pendingPreparedPage: null, prepareSerial: 0,
    insets: { top: 0, right: 0, bottom: 0, left: 0 }
  };
  var resizeTimer = 0;
  var selectionDispatchTimer = 0;
  var scrollNotifyTimer = 0;
  var touchStartX = 0;
  var touchStartY = 0;
  var touchStartTime = 0;
  var touchLastX = 0;
  var touchLastTime = 0;
  var touchVelocityX = 0;
  var touchBaseX = 0;
  var touchPaging = false;
  var pageStage = null;
  var pageStageCurrent = null;
  var pageStageTarget = null;
  var pageStageShadow = null;
  var pageStageFrom = 0;
  var pageStageTo = 0;
  var pageStageSide = 1;
  var pageStageProgress = 0;
  var pageStageActive = false;

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

  function locator(node, offset) {
    var text = node && node.nodeValue ? node.nodeValue : '';
    var safeOffset = Math.max(0, Math.min(offset || 0, text.length));
    return {
      version: 1,
      domPath: nodePath(node),
      textOffset: safeOffset,
      exact: text.substring(safeOffset, Math.min(text.length, safeOffset + 96)),
      prefix: text.substring(Math.max(0, safeOffset - 32), safeOffset),
      suffix: text.substring(safeOffset, Math.min(text.length, safeOffset + 32)),
      progression: state.total > 1 ? state.page / (state.total - 1) : 0
    };
  }

  function currentLocator() {
    var range = null;
    if (document.caretRangeFromPoint) range = document.caretRangeFromPoint(2, 2);
    if (!range && document.createRange) {
      range = document.createRange();
      range.selectNodeContents(document.body);
      range.collapse(true);
    }
    return range ? locator(range.startContainer, range.startOffset) : null;
  }

  function textIndex() {
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var nodes = [];
    var text = '';
    var node;
    while ((node = walker.nextNode())) {
      var parent = node.parentElement;
      if (!parent || /^(script|style|noscript)$/i.test(parent.tagName)) continue;
      nodes.push({ node: node, start: text.length, end: text.length + node.nodeValue.length });
      text += node.nodeValue;
    }
    return { nodes: nodes, text: text };
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
    var from = 0;
    var best = -1;
    var bestScore = -1;
    while (from <= index.text.length) {
      var at = index.text.indexOf(exact, from);
      if (at < 0) break;
      var score = 0;
      var prefix = target.prefix ? String(target.prefix) : '';
      var suffix = target.suffix ? String(target.suffix) : '';
      if (prefix && index.text.substring(Math.max(0, at - prefix.length), at) === prefix) score += 2;
      if (suffix && index.text.substring(at + exact.length, at + exact.length + suffix.length) === suffix) score += 2;
      if (score > bestScore) { best = at; bestScore = score; }
      from = at + Math.max(1, exact.length);
    }
    return best >= 0 ? rangeAtOffsets(index, best, best + exact.length) : null;
  }

  function rangeFromLocators(start, end) {
    var startNode = nodeAtPath((start && start.domPath) || []);
    var endNode = nodeAtPath((end && end.domPath) || []);
    if (startNode && endNode && startNode.nodeType === Node.TEXT_NODE && endNode.nodeType === Node.TEXT_NODE) {
      try {
        var direct = document.createRange();
        direct.setStart(startNode, Math.min(start.textOffset || 0, startNode.nodeValue.length));
        direct.setEnd(endNode, Math.min(end.textOffset || 0, endNode.nodeValue.length));
        return direct;
      } catch (_) {}
    }
    return quoteRange(start);
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
    var targetParallaxRatio = state.transition === 'curl' ? 0.10 : 0.24;
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
    var duration = state.transition === 'curl' ? 360 : 260;
    var easing = state.transition === 'curl' ? 'cubic-bezier(.18,.78,.16,1)' : 'cubic-bezier(.2,.72,.2,1)';
    pageStageCurrent.style.transition = 'transform ' + duration + 'ms ' + easing + ', clip-path ' + duration + 'ms ' + easing + ', filter ' + duration + 'ms ease';
    pageStageTarget.style.transition = 'transform ' + duration + 'ms ' + easing + ', filter ' + duration + 'ms ease';
    pageStageShadow.style.transition = 'transform ' + duration + 'ms ' + easing + ', opacity ' + duration + 'ms ease';
    void pageStage.offsetWidth;
    var destination = commit ? 1 : 0;
    requestAnimationFrame(function () { updatePageStage(destination, undefined, true); });
    clearTimeout(state.animationTimer);
    state.animationTimer = setTimeout(function () {
      var settledPage = commit ? pageStageTo : pageStageFrom;
      state.page = settledPage;
      settlePageStage(settledPage);
      if (commit && notify !== false) {
        post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
      }
    }, duration + 24);
  }

  function moveToPage(page, notify) {
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
    if (notify !== false) post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
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
          reverseAxis: state.reverseAxis
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
    return {
      top: box.marginTop + box.paddingTop + state.insets.top,
      right: box.marginRight + box.paddingRight + state.insets.right,
      bottom: box.marginBottom + box.paddingBottom + state.insets.bottom,
      left: box.marginLeft + box.paddingLeft + state.insets.left
    };
  }

  function applyPaginationBox(body) {
    var inset = readerBox();
    var horizontalInset = Math.min(state.viewportWidth - 1, Math.max(0, inset.left + inset.right));
    body.style.setProperty('--lumi-page-height', state.viewportHeight + 'px');
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
      moveToPage(typeof restoreProgression === 'number' ? Math.round(restoreProgression * Math.max(0, state.total - 1)) : state.page, false);
    } else {
      document.documentElement.style.overflow = 'hidden';
      applyPaginatedPublisherBackground();
      applyPaginationBox(body);
      var extent = Math.max(document.documentElement.scrollWidth, body.scrollWidth, state.viewportWidth);
      var physicalTotal = Math.max(1, Math.ceil((extent - 1) / state.pageStep));
      state.pageOffsets = collectOccupiedPages(physicalTotal - 1);
      state.total = Math.max(1, state.pageOffsets.length);
      var target = typeof restoreProgression === 'number'
        ? Math.round(restoreProgression * Math.max(0, state.total - 1))
        : state.page;
      moveToPage(target, false);
    }
    body.style.visibility = 'visible';
    state.ready = true;
    state.paginating = false;
    fulfillPreparedPageRequest();
    post('ready', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
    if (location.hash && !state.initialFragmentApplied) {
      state.initialFragmentApplied = true;
      requestAnimationFrame(function () { window.LumiReader.goToFragment(location.hash); });
    }
  }

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

      var index = 0;
      while (index < characters.length) {
        if (isBionicCjkCharacter(characters[index])) {
          var cjkEnd = index + 1;
          while (cjkEnd < characters.length && isBionicCjkCharacter(characters[cjkEnd])) cjkEnd++;
          for (var cjkIndex = index; cjkIndex < cjkEnd; cjkIndex += 2) {
            appendFixation(characters[cjkIndex]);
            if (cjkIndex + 1 < cjkEnd) plainText += characters[cjkIndex + 1];
          }
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
    config = config || {};
    var liveLocator = state.ready ? currentLocator() : null;
    var liveProgression = state.ready && state.total > 1 ? state.page / (state.total - 1) : undefined;
    state.progression = config.progression === 'rtl' ? 'rtl' : 'ltr';
    state.flow = config.flow === 'scrolled' ? 'scrolled' : 'paginated';
    state.transition = config.transition === 'fade' ? 'fade' :
      (config.transition === 'none' ? 'none' : (config.transition === 'curl' ? 'curl' : 'slide'));
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
    var node = nodeAtPath(target.domPath || []);
    if (node && node.nodeType === Node.TEXT_NODE) {
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

  function findText(exact, progression) {
    exact = exact ? String(exact) : '';
    if (!exact) return false;
    var index = textIndex();
    var haystack = index.text.toLocaleLowerCase();
    var needle = exact.toLocaleLowerCase();
    var expected = Math.max(0, Math.min(1, Number(progression) || 0)) * index.text.length;
    var cursor = 0;
    var best = -1;
    var distance = Number.MAX_VALUE;
    while (cursor <= haystack.length) {
      var found = haystack.indexOf(needle, cursor);
      if (found < 0) break;
      var candidateDistance = Math.abs(found - expected);
      if (candidateDistance < distance) { best = found; distance = candidateDistance; }
      cursor = found + Math.max(1, needle.length);
    }
    if (best < 0) return false;
    var range = rangeAtOffsets(index, best, best + exact.length);
    if (!range) return false;
    moveToPage(Math.max(0, Math.min(state.total - 1, pageForRange(range))), true);
    if (window.CSS && CSS.highlights && typeof Highlight !== 'undefined') {
      CSS.highlights.delete('lumi-search');
      CSS.highlights.set('lumi-search', new Highlight(range));
      var style = document.getElementById('lumi-search-color');
      if (!style) {
        style = document.createElement('style');
        style.id = 'lumi-search-color';
        style.textContent = '::highlight(lumi-search){background-color:rgba(255,193,7,.62);color:inherit;}';
        document.head.appendChild(style);
      }
    }
    return true;
  }

  function setHighlights(items) {
    if (!window.CSS || !CSS.highlights || typeof Highlight === 'undefined') return false;
    CSS.highlights.clear();
    var style = document.getElementById('lumi-highlight-colors');
    if (style) style.remove();
    style = document.createElement('style');
    style.id = 'lumi-highlight-colors';
    (items || []).forEach(function (item, index) {
      var range = rangeFromLocators(item.start, item.end);
      if (!range && item.exact) range = quoteRange({ exact: item.exact });
      if (!range) return;
      var name = 'lumi-note-' + index;
      CSS.highlights.set(name, new Highlight(range));
      var color = /^#[0-9a-f]{6,8}$/i.test(item.color || '') ? item.color : '#66ffeb3b';
      style.appendChild(document.createTextNode('::highlight(' + name + '){background-color:' + color + ';}'));
    });
    document.head.appendChild(style);
    return true;
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

  document.addEventListener('touchstart', function (event) {
    touchPaging = false;
    touchVelocityX = 0;
    if (event.touches.length !== 1) return;
    var touch = event.touches[0];
    touchStartX = touch.clientX;
    touchStartY = touch.clientY;
    touchLastX = touch.clientX;
    touchStartTime = Date.now();
    touchLastTime = touchStartTime;
    if (pageStageActive) {
      settlePageStage(state.page);
      post('page', { pageIndex: state.page, pageCount: state.total, reverseAxis: state.reverseAxis, locator: currentLocator() });
    }
    touchBaseX = state.flow === 'paginated' ? pageX(state.page) : 0;
    clearTimeout(state.animationTimer);
  }, { passive: true });

  document.addEventListener('touchmove', function (event) {
    if (state.flow !== 'paginated' || state.fixed || event.touches.length !== 1) return;
    var touch = event.touches[0];
    var dx = touch.clientX - touchStartX;
    var dy = touch.clientY - touchStartY;
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
    if (event.changedTouches.length !== 1) { touchPaging = false; return; }
    var touch = event.changedTouches[0];
    var dx = touch.clientX - touchStartX;
    var dy = touch.clientY - touchStartY;
    var elapsed = Math.max(1, Date.now() - touchStartTime);
    var velocityX = Math.abs(touchVelocityX) > 0.01 ? touchVelocityX : dx / elapsed;
    var anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;

    if (state.flow === 'scrolled' && !state.fixed) {
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
    var shouldTurn = horizontal && (Math.abs(dx) >= Math.min(72, state.viewportWidth * 0.16) ||
      (Math.abs(dx) >= 18 && Math.abs(velocityX) >= 0.42));
    var isTap = !touchPaging && !shouldTurn && !anchor && Math.abs(dx) < 12 && Math.abs(dy) < 12;
    var selection = window.getSelection && window.getSelection();
    var hasSelection = !!(selection && !selection.isCollapsed && selection.rangeCount > 0);
    if (touchPaging || shouldTurn || isTap) event.preventDefault();
    var wasPaging = touchPaging;
    touchPaging = false;
    if (isTap && hasSelection) {
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
        post('chapterTurn', { direction: advances ? 1 : -1 });
      }
      state.suppressClickUntil = Date.now() + 450;
      return;
    }
    if (wasPaging && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
    if (isTap && (!window.getSelection || window.getSelection().isCollapsed)) {
      var ratio = touch.clientX / viewportWidth();
      if (ratio < 0.3) post('tap', { zone: 'left' });
      else if (ratio > 0.7) post('tap', { zone: 'right' });
      else post('tap', { zone: 'center' });
      state.suppressClickUntil = Date.now() + 450;
    }
  }, { passive: false });

  document.addEventListener('touchcancel', function () {
    if (touchPaging && state.flow === 'paginated' && state.transition !== 'fade' && state.transition !== 'none') snapBackPage();
    touchPaging = false;
  }, { passive: true });

  document.addEventListener('submit', function (event) { event.preventDefault(); }, true);

  document.addEventListener('click', function (event) {
    if (Date.now() < state.suppressClickUntil) { event.preventDefault(); return; }
    var anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
    if (anchor) {
      event.preventDefault();
      post('link', { href: anchor.href });
      return;
    }
    var activeSelection = window.getSelection && window.getSelection();
    if (activeSelection && !activeSelection.isCollapsed) {
      clearDocumentSelection();
      post('selectionCleared', {});
      event.preventDefault();
      return;
    }
    var ratio = event.clientX / viewportWidth();
    if (ratio < 0.3) post('tap', { zone: 'left' });
    else if (ratio > 0.7) post('tap', { zone: 'right' });
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
        text: selection.toString(),
        start: locator(range.startContainer, range.startOffset),
        end: locator(range.endContainer, range.endOffset),
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
    next: function () { if (state.page + 1 < state.total) moveToPage(state.page + 1, true); else post('chapterTurn', { direction: 1 }); },
    previous: function () { if (state.page > 0) moveToPage(state.page - 1, true); else post('chapterTurn', { direction: -1 }); },
    goToPage: function (page) { moveToPage(page, true); },
    preparePage: preparePage,
    goToFragment: function (fragment) {
      var id = decodeURIComponent(String(fragment || '').replace(/^#/, ''));
      var target = document.getElementById(id);
      if (!target) return false;
      var range = document.createRange();
      range.selectNodeContents(target);
      range.collapse(true);
      moveToPage(Math.max(0, Math.min(state.total - 1, pageForRange(range))), true);
      return true;
    },
    restore: restore,
    currentLocator: currentLocator,
    setHighlights: setHighlights,
    findText: findText,
    pageText: pageText,
    visibleText: function () { return document.body ? document.body.innerText : ''; },
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
