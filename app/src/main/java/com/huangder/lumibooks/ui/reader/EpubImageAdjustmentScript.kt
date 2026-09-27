package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderImageAdjustments

/** SVG convolution is applied to image elements only, never the text/page or layout. */
internal fun epubImageAdjustmentScript(value: ReaderImageAdjustments): String {
    val settings = value.normalized()
    val s = settings.sharpen
    val matrix = "0 ${-s} 0 ${-s} ${1 + 4 * s} ${-s} 0 ${-s} 0"
    val color = settings.colorMatrixValues().mapIndexed { index, v ->
        if (index in listOf(4, 9, 14)) v / 255f else v
    }.joinToString(" ")
    return """
        (function() {
          if (!document.documentElement) return;
          var ns = 'http://www.w3.org/2000/svg';
          var root = document.getElementById('lumi-image-adjustments');
          if (!root) {
            root = document.createElementNS(ns, 'svg');
            root.id = 'lumi-image-adjustments';
            root.setAttribute('width', '0'); root.setAttribute('height', '0');
            root.style.cssText = 'position:absolute;pointer-events:none;overflow:hidden';
            document.documentElement.appendChild(root);
          }
          root.innerHTML = '<defs><filter id="lumi-image-filter" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB"><feConvolveMatrix order="3" kernelMatrix="$matrix" divisor="1" edgeMode="duplicate" preserveAlpha="true"/><feColorMatrix type="matrix" values="$color"/></filter></defs>';
          var style = document.getElementById('lumi-image-adjustment-style');
          if (!style) { style = document.createElement('style'); style.id = 'lumi-image-adjustment-style'; document.documentElement.appendChild(style); }
          style.textContent = ${org.json.JSONObject.quote(if (settings.isNeutral) "" else "img, svg image { filter: url(#lumi-image-filter) !important; }")};
        })();
    """.trimIndent()
}
