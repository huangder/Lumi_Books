package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StaticLayout 分页引擎。
 *
 * 职责：
 * - 用 StaticLayout 排版章节文本
 * - 按可视区域高度切分为 [PageLayout] 列表
 * - LRU 缓存最近使用的章节布局（默认 5 章）
 * - 全局页码 ↔ 局部页码转换
 */
class PageLayoutEngine {

    private data class LayoutInput(
        val generation: Long,
        val visibleWidth: Int,
        val visibleHeight: Int,
        val textPaint: TextPaint
    )

    // ── 排版参数 ──
    private var textWidth: Int = 1080
    private var textHeight: Int = 1920
    private var marginLeft: Float = 48f
    private var marginRight: Float = 48f
    private var marginTop: Float = 32f
    private var marginBottom: Float = 32f
    private var lineSpacingExtra: Float = 8f
    private var lineSpacingMultiplier: Float = 1.0f
    private var letterSpacing: Float = 0f

    /**
     * 引擎自己的 TextPaint（当没有外部共用 Paint 时使用）。
     * 如果设置了 [sharedTextPaint]，则优先使用外部 Paint 以与 TextView 保持字体度量一致。
     */
    private val ownTextPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f
        color = 0xFF333333.toInt()
        linkColor = color
        isSubpixelText = true
    }

    /** 外部共用的 TextPaint（来自 TextView.getPaint()），消除两个引擎的字体度量差异 */
    var sharedTextPaint: TextPaint? = null

    /** 当前实际使用的 TextPaint */
    private val activeTextPaint: TextPaint get() = sharedTextPaint ?: ownTextPaint

    /** 可视区域宽度（减去边距），用 truncate 与 TextView padding 的 .toInt() 保持一致 */
    val visibleWidth: Int get() = (textWidth - marginLeft.toInt() - marginRight.toInt()).coerceAtLeast(1)

    /** 可视区域高度（减去边距），用 truncate 与 TextView padding 的 .toInt() 保持一致 */
    val visibleHeight: Int get() = (textHeight - marginTop.toInt() - marginBottom.toInt()).coerceAtLeast(1)

    // ── 缓存 ──
    private val layoutCache = object : LinkedHashMap<Int, ChapterLayout>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterLayout>?): Boolean {
            return size > 5
        }
    }
    private val cacheLock = Any()
    private var layoutGeneration: Long = 0L

    /** 各章累计页数前缀和，用于全局页码转换 */
    private val cumulativePageTotals = mutableListOf<Int>()

    /** 总章数 */
    private var chapterCount: Int = 0

    // ── 配置 ──

    fun configure(
        width: Int,
        height: Int,
        fontSizePx: Float,
        lineSpacingPx: Float = 8f,
        lineSpacingMult: Float = 1.0f,
        letterSpacingPx: Float = 0f,
        fontType: String = "system",
        customTypeface: android.graphics.Typeface? = null,
        marginLeftPx: Float = 48f,
        marginRightPx: Float = 48f,
        marginTopPx: Float = 32f,
        marginBottomPx: Float = 32f,
        textColor: Int = 0xFF333333.toInt(),
        chapterCount: Int = 0
    ) {
        val changed = textWidth != width || textHeight != height ||
                ownTextPaint.textSize != fontSizePx || lineSpacingExtra != lineSpacingPx ||
                lineSpacingMultiplier != lineSpacingMult ||
                this.letterSpacing != letterSpacingPx ||
                marginLeft != marginLeftPx || marginRight != marginRightPx ||
                marginTop != marginTopPx || marginBottom != marginBottomPx

        textWidth = width
        textHeight = height
        marginLeft = marginLeftPx
        marginRight = marginRightPx
        marginTop = marginTopPx
        marginBottom = marginBottomPx
        lineSpacingExtra = lineSpacingPx
        lineSpacingMultiplier = lineSpacingMult
        this.letterSpacing = letterSpacingPx
        ownTextPaint.textSize = fontSizePx
        ownTextPaint.letterSpacing = letterSpacingPx / fontSizePx  // StaticLayout 使用比例值
        ownTextPaint.color = textColor
        ownTextPaint.linkColor = textColor   // 🔥 同步：避免主题切换后 URLSpan 颜色不同步

        // 字体
        val tf = when (fontType) {
            "serif" -> Typeface.SERIF
            "sans_serif" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            "fangsong", "kaiti", "custom" -> customTypeface ?: Typeface.DEFAULT
            else -> Typeface.DEFAULT
        }
        ownTextPaint.typeface = tf
        this.chapterCount = chapterCount

        if (changed) {
            invalidateAll()
        }
    }

    // ── 分页 ──

    /**
     * 对一章文本进行排版和分页。
     * 在 IO 线程执行排版，返回 ChapterLayout。
     */
    suspend fun layout(
        chapterIndex: Int,
        text: CharSequence
    ): ChapterLayout = withContext(Dispatchers.IO) {
        val input = synchronized(cacheLock) {
            layoutCache[chapterIndex]?.let { return@withContext it }
            LayoutInput(
                generation = layoutGeneration,
                visibleWidth = visibleWidth,
                visibleHeight = visibleHeight,
                textPaint = TextPaint(activeTextPaint)
            )
        }

        val sl = StaticLayout.Builder.obtain(text, 0, text.length, input.textPaint, input.visibleWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(false)  // 关闭额外 padding，由 marginTop/marginBottom 精确控制
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)  // 与 TextView 默认值一致，消除断行差异
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)  // CJK 文本不需要断字
            .build()

        val pages = mutableListOf<PageLayout>()
        // 留出 1px 舍入余量，避免 TextView 与 StaticLayout 的取整差异把字形压到下边界外。
        val effectiveVh = (input.visibleHeight - 1).coerceAtLeast(1).toFloat()
        var pageStartLine = 0
        var pageIdx = 0
        var globalCharOffset = 0

        while (pageStartLine < sl.lineCount) {
            // 找到本页的最后一行
            var pageEndLine = pageStartLine
            var accumulatedHeight = 0f
            while (pageEndLine < sl.lineCount) {
                val lineBottom = sl.getLineBottom(pageEndLine)
                val lineTop = sl.getLineTop(pageEndLine)
                val lineHeight = (lineBottom - lineTop).toFloat()
                if (accumulatedHeight + lineHeight > effectiveVh && pageEndLine > pageStartLine) {
                    break
                }
                accumulatedHeight += lineHeight
                pageEndLine++
            }

            // 🔥 收尾优化：将纯空白行（仅有 \n 的行）纳入当前页底部
            // 这些行在页底不可见，不会影响视觉效果，但避免了下一页顶部出现空白
            while (pageEndLine < sl.lineCount) {
                val lineStart = sl.getLineStart(pageEndLine)
                val lineEnd = sl.getLineEnd(pageEndLine)
                if (lineEnd - lineStart == 1 && text[lineStart] == '\n') {
                    val lineBottom = sl.getLineBottom(pageEndLine)
                    val lineTop = sl.getLineTop(pageEndLine)
                    accumulatedHeight += (lineBottom - lineTop).toFloat()
                    pageEndLine++
                } else {
                    break
                }
            }

            val startChar = sl.getLineStart(pageStartLine)
            val endChar = if (pageEndLine < sl.lineCount) {
                sl.getLineStart(pageEndLine)
            } else {
                text.length
            }

            pages.add(
                PageLayout(
                    chapterIndex = chapterIndex,
                    pageIndex = pageIdx,
                    startLine = pageStartLine,
                    endLine = pageEndLine,
                    startCharOffset = startChar,
                    endCharOffset = endChar
                )
            )
            globalCharOffset += (endChar - startChar)
            pageStartLine = pageEndLine
            pageIdx++
        }

        val cumulativeBefore = synchronized(cacheLock) {
            if (chapterIndex > 0) {
                var sum = 0
                for (i in 0 until chapterIndex) {
                    layoutCache[i]?.let { sum += it.totalPages }
                }
                sum
            } else {
                0
            }
        }

        val result = ChapterLayout(
            chapterIndex = chapterIndex,
            pages = pages,
            staticLayout = sl,
            totalPages = pages.size,
            cumulativePagesBefore = cumulativeBefore
        )
        synchronized(cacheLock) {
            if (input.generation == layoutGeneration) {
                layoutCache[chapterIndex] = result
            }
        }
        result
    }

    // ── 全局页码转换 ──

    /**
     * 全局页码 → (章节索引, 章内页码)
     * 遍历缓存计算累计页数。时间复杂度 O(n)，n=章数，通常 < 2000。
     */
    fun globalToLocal(globalPageIndex: Int): Pair<Int, Int> {
        return synchronized(cacheLock) {
            var remaining = globalPageIndex
            for (ci in 0 until chapterCount) {
                val cl = layoutCache[ci]
                if (cl != null) {
                    if (remaining < cl.totalPages) {
                        return@synchronized ci to remaining
                    }
                    remaining -= cl.totalPages
                }
            }
            (chapterCount - 1).coerceAtLeast(0) to 0
        }
    }

    /**
     * (章节索引, 章内页码) → 全局页码
     */
    fun localToGlobal(chapterIndex: Int, pageInChapter: Int): Int {
        return synchronized(cacheLock) {
            var global = 0
            for (ci in 0 until chapterIndex.coerceAtMost(chapterCount)) {
                layoutCache[ci]?.let { global += it.totalPages }
            }
            global + pageInChapter
        }
    }

    /**
     * 获取章节数量
     */
    fun getChapterCount(): Int = chapterCount

    /**
     * 获取总页数（跨所有已布局章节）
     */
    fun getTotalPages(): Int {
        return synchronized(cacheLock) {
            var total = 0
            for (ci in 0 until chapterCount) {
                layoutCache[ci]?.let { total += it.totalPages }
            }
            total
        }
    }

    /**
     * 获取指定章的页数
     */
    fun getChapterPageCount(chapterIndex: Int): Int {
        return synchronized(cacheLock) { layoutCache[chapterIndex]?.totalPages ?: 0 }
    }

    /**
     * 获取章节布局（从缓存）
     */
    fun getChapterLayout(chapterIndex: Int): ChapterLayout? {
        return synchronized(cacheLock) { layoutCache[chapterIndex] }
    }

    /**
     * 获取章内某页的布局信息
     */
    fun getPageLayout(chapterIndex: Int, pageInChapter: Int): PageLayout? {
        return synchronized(cacheLock) {
            layoutCache[chapterIndex]?.pages?.getOrNull(pageInChapter)
        }
    }

    /**
     * 将画布坐标 (x, y) 转换为章节内的字符偏移量。
     * @return 字符偏移量，如果坐标不在文本区域则返回 null
     */
    fun getCharOffsetAtPoint(chapterIndex: Int, pageInChapter: Int, x: Float, y: Float): Int? {
        val chapterLayout = synchronized(cacheLock) { layoutCache[chapterIndex] } ?: return null
        val pageLayout = chapterLayout.pages.getOrNull(pageInChapter) ?: return null
        val sl = chapterLayout.staticLayout

        // 转换坐标：Canvas绘制时translate了marginLeft/marginTop和页偏移
        val textX = x - marginLeft
        val textY = y - marginTop + sl.getLineTop(pageLayout.startLine)

        if (textX < 0 || textY < 0) return null

        val line = sl.getLineForVertical(textY.toInt())
        if (line < pageLayout.startLine || line >= pageLayout.endLine) return null

        val offset = sl.getOffsetForHorizontal(line, textX)
        return offset.coerceIn(pageLayout.startCharOffset, pageLayout.endCharOffset - 1)
    }

    /**
     * 根据字符偏移量获取该字符所在行的视觉边界（用于高亮绘制）。
     * @return (left, top, right, bottom) 或 null
     */
    fun getCharBounds(chapterIndex: Int, charOffset: Int): android.graphics.Rect? {
        val chapterLayout = synchronized(cacheLock) { layoutCache[chapterIndex] } ?: return null
        val sl = chapterLayout.staticLayout
        if (charOffset < 0 || charOffset > sl.text.length) return null
        val line = sl.getLineForOffset(charOffset)
        val left = sl.getPrimaryHorizontal(charOffset)
        val top = sl.getLineTop(line).toFloat()
        val bottom = sl.getLineBottom(line).toFloat()
        return android.graphics.Rect(left.toInt(), top.toInt(), (left + 1).toInt(), bottom.toInt())
    }

    // ── 缓存管理 ──

    fun invalidateChapter(chapterIndex: Int) {
        synchronized(cacheLock) {
            layoutGeneration++
            layoutCache.remove(chapterIndex)
        }
    }

    fun invalidateAll() {
        synchronized(cacheLock) {
            layoutGeneration++
            layoutCache.clear()
            cumulativePageTotals.clear()
        }
    }
}
