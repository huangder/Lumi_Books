package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.epub.EpubComicIndex
import com.huangder.lumibooks.util.epub.EpubComicPosition
import com.huangder.lumibooks.util.epub.EpubRenderMode
import org.json.JSONObject

internal fun EpubComicIndex.textProgress(page: Int, fraction: Float = 0f): Float {
    val item = pages.getOrNull(page) ?: return 0f
    val chapterPages = pages.filter { it.chapterIndex == item.chapterIndex }
    val within = (chapterPages.indexOf(item) + fraction.coerceIn(0f, 1f)) / chapterPages.size
    return ((item.chapterIndex + within) / chapterTitles.size.coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal fun epubTextLocator(position: EpubComicPosition, mode: EpubRenderMode): String =
    if (mode == EpubRenderMode.READER_LAYOUT) {
        ReaderPositionLocator(position.chapterIndex, 0f, ReaderPositionFlow.PAGED).toJson()
    } else {
        JSONObject().put("version", 1).put("href", position.documentPath)
            .put("progression", 0).put("comicImage", position.json()).toString()
    }

internal fun comicPositionFromLocator(value: String?): EpubComicPosition? =
    EpubComicPosition.decode(value) ?: runCatching {
        JSONObject(value ?: "").optJSONObject("comicImage")?.toString()?.let(EpubComicPosition::decode)
    }.getOrNull()

internal const val EpubComicInkPenType = "epub_comic_ink_pen"
internal const val EpubComicInkHighlighterType = "epub_comic_ink_highlighter"
internal fun isEpubComicInk(type: String) = type == EpubComicInkPenType || type == EpubComicInkHighlighterType

internal fun encodeComicInk(stroke: PdfInkStroke, position: EpubComicPosition): String =
    JSONObject(PdfInkStrokeLocatorV1.encode(stroke)).put("comicImage", position.json()).toString()
