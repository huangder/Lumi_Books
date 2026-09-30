package com.huangder.lumibooks.ui.reader

import android.text.Layout
import android.text.Spanned
import android.text.style.ImageSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import kotlin.math.roundToInt

/** Every input that can change measured content, independent of progress/menu state. */
internal data class ContinuousLayoutKey(
    val revision: Long,
    val imageWidth: Int,
    val textWidth: Int,
    val comic: Boolean,
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val alignment: ReaderTextAlignment,
    val typeface: ResolvedReaderTypeface,
    val chineseMode: String,
    val bionic: Boolean,
    val topMargin: Float,
    val bottomMargin: Float,
    val density: Float,
    val fontScale: Float
)

internal data class ContinuousViewportAnchor(
    val chapter: Int,
    val character: Int,
    val lineViewportTop: Int,
    val imageFraction: Float? = null,
    val imageIndex: Int? = null
)

private data class ImagePosition(val character: Int, val top: Int, val height: Int)

private fun imagePositions(text: CharSequence, width: Int, gap: Int, viewportHeight: Int): List<ImagePosition> {
    val images = continuousChapterImages(text)
    val spanned = text as? Spanned ?: return emptyList()
    var top = 0
    return images.mapIndexed { index, image ->
        val bounds = image.drawable.bounds
        val height = (width.toFloat() * bounds.height().coerceAtLeast(1) / bounds.width().coerceAtLeast(1))
            .roundToInt().coerceAtLeast(1)
        if (index == 0 && images.size == 1 && continuousChapterIsCover(text)) {
            top = ((viewportHeight - height) / 2).coerceAtLeast(0)
        }
        ImagePosition(spanned.getSpanStart(image), top, height).also { top += height + gap }
    }
}

internal fun captureContinuousViewportAnchor(
    chapter: Int, itemOffset: Int, text: CharSequence, layout: Layout?,
    imageWidth: Int, imageGap: Int, viewportHeight: Int
): ContinuousViewportAnchor? {
    val y = (-itemOffset).coerceAtLeast(0)
    val images = imagePositions(text, imageWidth, imageGap, if (chapter == 0) viewportHeight else 0)
    if (images.isNotEmpty()) {
        val index = images.indexOfLast { it.top <= y }.coerceAtLeast(0)
        val image = images[index]
        val inside = y >= image.top && y < image.top + image.height
        return ContinuousViewportAnchor(chapter, image.character, itemOffset + image.top,
            if (inside) (y - image.top).toFloat() / image.height else null, index)
    }
    layout ?: return null
    if (layout.lineCount == 0) return null
    val line = layout.getLineForVertical(y)
    val top = layout.getLineTop(line)
    val start = layout.getLineStart(line)
    val end = layout.getLineEnd(line)
    val image = (layout.text as? Spanned)?.getSpans(start, end, ImageSpan::class.java)?.firstOrNull()
    val imageOnly = image != null && (start until end).all { layout.text[it].isWhitespace() || layout.text[it] == '\uFFFC' }
    val imageBounds = if (imageOnly) continuousImageBounds(layout, image, Layout.JUSTIFICATION_MODE_NONE) else null
    val fraction = imageBounds?.takeIf { y >= it.top && y < it.bottom }
        ?.let { (y - it.top) / it.height().coerceAtLeast(1f) }
    return ContinuousViewportAnchor(chapter, start, itemOffset + top, fraction)
}

internal fun continuousViewportAnchorOffset(
    anchor: ContinuousViewportAnchor, text: CharSequence, layout: Layout?,
    imageWidth: Int, imageGap: Int, viewportHeight: Int
): Int? {
    val images = imagePositions(text, imageWidth, imageGap, if (anchor.chapter == 0) viewportHeight else 0)
    anchor.imageIndex?.let { index ->
        images.getOrNull(index)?.let { image ->
            return if (anchor.imageFraction != null) image.top + (image.height * anchor.imageFraction).roundToInt()
            else image.top - anchor.lineViewportTop
        }
    }
    layout ?: return null
    val line = layout.getLineForOffset(anchor.character.coerceIn(0, layout.text.length))
    val top = layout.getLineTop(line)
    return if (anchor.imageFraction != null) {
        val image = (layout.text as? Spanned)?.getSpans(layout.getLineStart(line),
            layout.getLineEnd(line), ImageSpan::class.java)?.firstOrNull()
        val bounds = image?.let { continuousImageBounds(layout, it, Layout.JUSTIFICATION_MODE_NONE) }
        if (bounds != null) (bounds.top + bounds.height() * anchor.imageFraction).roundToInt()
        else top - anchor.lineViewportTop
    } else top - anchor.lineViewportTop
}
