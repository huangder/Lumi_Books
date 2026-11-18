package com.huangder.lumibooks.util.epub

internal const val EPUB_READER_ALIGNMENT_SCRIPT = """
  var readerAlignmentMode = 'natural';
  var readerAlignmentOverrides = [];
  var readerLineSpacing = [];
  var readerSpacingSplits = [];
  var readerProseSelector = 'p,div,section,article,aside,blockquote,li,dt,dd,figcaption,td,th';
  var readerHeadingSelector = 'h1,h2,h3,h4,h5,h6,[role="heading"],[epub\\:type~="title"],' +
    '[epub\\:type~="subtitle"],[epub\\:type~="bridgehead"],[type~="title"],[type~="subtitle"]';

  function readerProseBlocks() {
    var blocks = Array.from(document.body.querySelectorAll(readerProseSelector));
    blocks.push(document.body);
    return blocks.filter(function (block) {
      if (block.closest(readerHeadingSelector + ',pre,math,svg') ||
          block.querySelector(readerProseSelector + ',' + readerHeadingSelector + ',pre,math,svg')) return false;
      return block.textContent.trim().length > 0;
    });
  }

  function setReaderAlignment(mode) {
    readerAlignmentMode = mode || 'natural';
    readerAlignmentOverrides.forEach(function (item) {
      if (item.value) item.block.style.setProperty(item.name, item.value, item.priority);
      else item.block.style.removeProperty(item.name);
    });
    readerAlignmentOverrides = [];
    if (!mode || state.fixed) return;
    readerProseBlocks().forEach(function (block) {
      if (getComputedStyle(block).writingMode.indexOf('horizontal') !== 0) return;
      function override(name, value) {
        readerAlignmentOverrides.push({ block: block, name: name,
          value: block.style.getPropertyValue(name), priority: block.style.getPropertyPriority(name) });
        block.style.setProperty(name, value, 'important');
      }
      override('text-align', mode);
      override('text-align-last', mode === 'justify' ? 'start' : 'auto');
      if (mode === 'justify' && window.CSS && CSS.supports('text-justify', 'auto')) override('text-justify', 'auto');
    });
  }

  function removeReaderLineSpacing(spans) {
    spans.forEach(function (span) {
      var parent = span.parentNode;
      if (!parent) return;
      while (span.firstChild) parent.insertBefore(span.firstChild, span);
      parent.removeChild(span);
    });
  }

  function splitReaderSpacingText(node, offset) {
    var suffix = node.splitText(offset);
    readerSpacingSplits.push({ prefix: node, suffix: suffix });
    return suffix;
  }

  function clearReaderLineSpacing() {
    removeReaderLineSpacing(readerLineSpacing);
    // Undo only our splits, in reverse order, so publisher text nodes and
    // their saved source strings for Chinese conversion stay paired.
    for (var i = readerSpacingSplits.length - 1; i >= 0; i--) {
      var part = readerSpacingSplits[i];
      if (part.prefix.nextSibling !== part.suffix) continue;
      part.prefix.appendData(part.suffix.data);
      part.suffix.parentNode.removeChild(part.suffix);
    }
    readerSpacingSplits = [];
    readerLineSpacing = [];
  }

  function readerHasCjkLetter(value) {
    var cp = value.codePointAt(0);
    return cp >= 0x3400 && cp <= 0x9fff || cp >= 0xf900 && cp <= 0xfaff ||
      cp >= 0x20000 && cp <= 0x323af || cp >= 0x3041 && cp <= 0x30fa;
  }

  var readerLineStartPunctuation = '、。，．：；！？）］｝】〕〉》」』”’…—・,.:;!?)]}';

  function readerBlockTextIndex(block) {
    var walker = document.createTreeWalker(block, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    var index = { nodes: [], text: '', breaks: new Set() };
    var node;
    while ((node = walker.nextNode())) {
      if (node.nodeType === Node.TEXT_NODE && node.nodeValue) {
        index.nodes.push({node: node, start: index.text.length, end: index.text.length + node.nodeValue.length});
        index.text += node.nodeValue;
      } else if (node.localName === 'br') index.breaks.add(index.text.length);
    }
    return index;
  }

  function readerLineGraphemes(text) {
    if (typeof Intl.Segmenter === 'function') {
      return Array.from(new Intl.Segmenter(undefined, {granularity: 'grapheme'}).segment(text));
    }
    var offset = 0;
    return Array.from(text).map(function (segment) {
      var item = {index: offset, segment: segment};
      offset += segment.length;
      return item;
    });
  }

  function readerLineRect(index, start, end) {
    var range = rangeAtOffsets(index, start, end);
    var rects = range && range.getClientRects();
    return rects && rects.length === 1 && rects[0].width > 0 ? rects[0] : null;
  }

  function readerRectOnLine(rect, line, fragment) {
    if (!rect || rect.left < fragment.left - 1 || rect.right > fragment.right + 1) return false;
    return Math.min(rect.bottom, line.bottom) - Math.max(rect.top, line.top) > Math.min(rect.height, line.height) / 2;
  }

  function adjustReaderPunctuationLines() {
    if (readerAlignmentMode !== 'natural' || state.fixed || state.writingMode.indexOf('horizontal') !== 0) return;
    readerProseBlocks().forEach(function (block) {
      if (block.querySelector('img,ruby,svg,math,pre,code,script,style')) return;
      var style = getComputedStyle(block);
      if (style.direction !== 'ltr' || !/^(start|left|-webkit-left)$/.test(style.textAlign) ||
          style.writingMode.indexOf('horizontal') !== 0 || !/^(normal|pre-line)$/.test(style.whiteSpace)) return;
      var index = readerBlockTextIndex(block);
      if (!Array.from(readerLineStartPunctuation).some(function (mark) { return index.text.includes(mark); })) return;
      var graphemes = readerLineGraphemes(index.text);
      var fragments = Array.from(block.getClientRects());
      var rightInset = (parseFloat(style.paddingRight) || 0) + (parseFloat(style.borderRightWidth) || 0);
      function rectAt(at, textIndex) {
        var item = graphemes[at];
        return readerLineRect(textIndex, item.index, item.index + item.segment.length);
      }
      var adjustments = [];
      var lowerBound = 0;
      for (var i = 1; i + 1 < graphemes.length; i++) {
        var first = graphemes[i];
        if (index.breaks.has(first.index) || /[\r\n\u2028\u2029]/.test(graphemes[i - 1].segment)) { lowerBound = i; continue; }
        if (!readerHasCjkLetter(first.segment) || !readerLineStartPunctuation.includes(graphemes[i + 1].segment)) continue;
        var preceding = rectAt(i - 1, index), next = rectAt(i, index), punctuation = rectAt(i + 1, index);
        if (!preceding || !next || !punctuation) continue;
        var fragment = fragments.find(function (box) {
          return preceding.right <= box.right + 1 && preceding.left >= box.left - 1 &&
            preceding.top < box.bottom && preceding.bottom > box.top;
        });
        if (!fragment || readerRectOnLine(next, preceding, fragment) || Math.abs(next.top - punctuation.top) > 1) continue;
        var edge = fragment.right - rightInset;
        var gap = edge - preceding.right;
        if (gap < next.width - 0.5 || gap > 2 * next.width || gap < 1) continue;
        var after = i + 2;
        while (after < graphemes.length && readerLineStartPunctuation.includes(graphemes[after].segment) &&
            !index.breaks.has(graphemes[after].index)) after++;
        var group = readerLineRect(index, first.index, after < graphemes.length ? graphemes[after].index : index.text.length);
        if (gap >= (group ? group.width : punctuation.right - next.left) - 0.5) continue;
        var lo = lowerBound, hi = i - 1;
        while (lo < hi) {
          var mid = (lo + hi) >> 1;
          if (readerRectOnLine(rectAt(mid, index), preceding, fragment)) hi = mid;
          else lo = mid + 1;
        }
        while (lo < i - 1 && /^\s+$/.test(graphemes[lo].segment)) lo++;
        if (i - lo < 2) continue;
        adjustments.push({ first: lo, last: i - 1, edge: edge, fragment: fragment, spans: [],
          delta: (gap - 0.35) / (i - lo - 1) });
        lowerBound = i;
      }
      adjustments.reverse().forEach(function (line) {
        var start = graphemes[line.first].index;
        var end = graphemes[line.last].index;
        for (var n = index.nodes.length - 1; n >= 0; n--) {
          var info = index.nodes[n];
          if (info.start >= end) continue;
          if (info.end <= start) break;
          var target = info.node;
          var a = Math.max(start, info.start) - info.start;
          var b = Math.min(end, info.end) - info.start;
          var tracking = parseFloat(getComputedStyle(target.parentElement).letterSpacing) || 0;
          if (b < target.nodeValue.length) splitReaderSpacingText(target, b);
          if (a > 0) target = splitReaderSpacingText(target, a);
          var span = document.createElement('span');
          span.setAttribute('data-lumi-line-spacing', 'true');
          span.style.setProperty('font', 'inherit', 'important');
          span.style.setProperty('display', 'inline', 'important');
          span.style.setProperty('letter-spacing', (tracking + line.delta) + 'px', 'important');
          target.parentNode.insertBefore(span, target);
          span.appendChild(target);
          line.spans.push(span);
        }
      });
      if (!adjustments.length) return;
      var adjusted = readerBlockTextIndex(block);
      var rejected = [];
      adjustments.forEach(function (line) {
        var first = rectAt(line.first, adjusted), last = rectAt(line.last, adjusted);
        var next = rectAt(line.last + 1, adjusted);
        if (!first || !last || !next || !readerRectOnLine(first, last, line.fragment) ||
            readerRectOnLine(next, last, line.fragment) || Math.abs(line.edge - last.right) > 1) {
          rejected.push.apply(rejected, line.spans);
        } else readerLineSpacing.push.apply(readerLineSpacing, line.spans);
      });
      removeReaderLineSpacing(rejected);
    });
  }
"""
