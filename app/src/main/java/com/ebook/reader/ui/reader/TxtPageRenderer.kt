package com.ebook.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log

/**
 * TXT 格式页面渲染器：StaticLayout 排版 → Canvas → Bitmap。
 *
 * 参考 HiReader ChapterProvider.loadPageList() / PageLoader.drawContent()：
 * - 使用 StaticLayout 一次性排版整章文本
 * - 按页面高度切分行范围
 * - 渲染时只绘制该页行范围到 Bitmap
 *
 * 每个章节的排版缓存在 LRU（3 章）。
 */
class TxtPageRenderer(
    private val contentProvider: (chapterIndex: Int) -> String?
) : PageRenderer {

    private data class PageRange(val startLine: Int, val endLine: Int) // [start, end)
    private data class ChapterLayout(
        val pages: List<PageRange>,
        val layout: StaticLayout
    )

    private val layoutCache = object : LinkedHashMap<Int, ChapterLayout>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterLayout>?): Boolean = size > 3
    }

    private var _pageWidth: Int = 0
    private var _pageHeight: Int = 0
    private var _fontSizePx: Float = 48f
    private var _lineSpacingPx: Float = 0f
    private var _bgColor: Int = Color.WHITE
    private var _textColor: Int = Color.BLACK

    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
    }
    private val bgPaint: Paint = Paint().apply { style = Paint.Style.FILL }

    override val pageWidth: Int get() = _pageWidth
    override val pageHeight: Int get() = _pageHeight

    override fun configure(
        width: Int, height: Int,
        fontSizePx: Float, lineSpacingPx: Float,
        bgColor: Int, textColor: Int,
        isNightMode: Boolean
    ) {
        val changed = width != _pageWidth || height != _pageHeight ||
                fontSizePx != _fontSizePx || lineSpacingPx != _lineSpacingPx ||
                bgColor != _bgColor || textColor != _textColor
        _pageWidth = width
        _pageHeight = height
        _fontSizePx = fontSizePx
        _lineSpacingPx = lineSpacingPx
        _bgColor = bgColor
        _textColor = textColor
        textPaint.textSize = fontSizePx
        textPaint.color = textColor
        bgPaint.color = bgColor
        if (changed) {
            layoutCache.clear()
        }
    }

    override fun getPageCount(chapterIndex: Int): Int {
        return layoutCache[chapterIndex]?.pages?.size ?: -1
    }

    override fun renderPage(chapterIndex: Int, pageIndex: Int, targetBitmap: Bitmap?): PageData? {
        if (_pageWidth <= 0 || _pageHeight <= 0) return null
        val layout = getOrComputeLayout(chapterIndex) ?: return null
        if (pageIndex < 0 || pageIndex >= layout.pages.size) return null

        val bitmap = targetBitmap ?: Bitmap.createBitmap(_pageWidth, _pageHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        // 绘制背景
        canvas.drawColor(_bgColor)

        // 仅绘制该页的行范围
        val range = layout.pages[pageIndex]
        val sl = layout.layout
        var y = 0f
        canvas.save()
        for (line in range.startLine until range.endLine) {
            val lineStart = sl.getLineStart(line)
            val lineEnd = sl.getLineEnd(line)
            canvas.drawText(
                sl.text, lineStart, lineEnd,
                0f, y - sl.getLineAscent(line),
                textPaint
            )
            y += sl.getLineBottom(line) - sl.getLineTop(line) + _lineSpacingPx
        }
        canvas.restore()

        return PageData(
            bitmap = bitmap,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            chapterTotal = layout.pages.size
        )
    }

    private fun getOrComputeLayout(chapterIndex: Int): ChapterLayout? {
        layoutCache[chapterIndex]?.let { return it }

        val text = contentProvider(chapterIndex) ?: return null
        if (text.isEmpty()) return null

        val sl = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, _pageWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(_lineSpacingPx, 1f)
            .setIncludePad(false)
            .build()

        // 按页面高度切分行
        val pages = mutableListOf<PageRange>()
        var pageStart = 0
        while (pageStart < sl.lineCount) {
            var y = 0f
            var line = pageStart
            while (line < sl.lineCount) {
                val lineH = sl.getLineBottom(line) - sl.getLineTop(line) + _lineSpacingPx
                if (y + lineH > _pageHeight && line > pageStart) break
                y += lineH
                line++
            }
            pages.add(PageRange(pageStart, line))
            pageStart = line
        }

        val result = ChapterLayout(pages, sl)
        layoutCache[chapterIndex] = result
        Log.d("PG", "TxtPageRenderer: chapter=$chapterIndex pages=${pages.size} lines=${sl.lineCount}")
        return result
    }
}
