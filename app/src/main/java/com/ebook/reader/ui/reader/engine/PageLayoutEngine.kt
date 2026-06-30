package com.ebook.reader.ui.reader.engine

import android.graphics.Paint
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

    // ── 排版参数 ──
    private var textWidth: Int = 1080
    private var textHeight: Int = 1920
    private var marginLeft: Float = 48f
    private var marginRight: Float = 48f
    private var marginTop: Float = 32f
    private var marginBottom: Float = 32f
    private var lineSpacingExtra: Float = 8f
    private var lineSpacingMultiplier: Float = 1.0f

    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f  // 默认 16sp * 3.5 density → 56px
        color = 0xFF333333.toInt()
        isSubpixelText = true
    }

    /** 可视区域宽度（减去边距） */
    val visibleWidth: Int get() = (textWidth - marginLeft - marginRight).toInt().coerceAtLeast(1)

    /** 可视区域高度（减去边距） */
    val visibleHeight: Int get() = (textHeight - marginTop - marginBottom).toInt().coerceAtLeast(1)

    // ── 缓存 ──
    private val layoutCache = object : LinkedHashMap<Int, ChapterLayout>(5, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterLayout>?): Boolean {
            return size > 5
        }
    }

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
        marginLeftPx: Float = 48f,
        marginRightPx: Float = 48f,
        marginTopPx: Float = 32f,
        marginBottomPx: Float = 32f,
        textColor: Int = 0xFF333333.toInt(),
        chapterCount: Int = 0
    ) {
        val changed = textWidth != width || textHeight != height ||
                textPaint.textSize != fontSizePx || lineSpacingExtra != lineSpacingPx ||
                lineSpacingMultiplier != lineSpacingMult || marginLeft != marginLeftPx ||
                marginRight != marginRightPx || marginTop != marginTopPx ||
                marginBottom != marginBottomPx

        textWidth = width
        textHeight = height
        marginLeft = marginLeftPx
        marginRight = marginRightPx
        marginTop = marginTopPx
        marginBottom = marginBottomPx
        lineSpacingExtra = lineSpacingPx
        lineSpacingMultiplier = lineSpacingMult
        textPaint.textSize = fontSizePx
        textPaint.color = textColor
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
        // 检查缓存
        layoutCache[chapterIndex]?.let { return@withContext it }

        val vw = visibleWidth
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, vw)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()

        val pages = mutableListOf<PageLayout>()
        val vh = visibleHeight
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
                if (accumulatedHeight + lineHeight > vh && pageEndLine > pageStartLine) {
                    break
                }
                accumulatedHeight += lineHeight
                pageEndLine++
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

        val cumulativeBefore = if (chapterIndex > 0) {
            // 计算前面章节的累计页数
            var sum = 0
            for (i in 0 until chapterIndex) {
                layoutCache[i]?.let { sum += it.totalPages }
            }
            sum
        } else 0

        ChapterLayout(
            chapterIndex = chapterIndex,
            pages = pages,
            staticLayout = sl,
            totalPages = pages.size,
            cumulativePagesBefore = cumulativeBefore
        ).also { layoutCache[chapterIndex] = it }
    }

    // ── 全局页码转换 ──

    /**
     * 全局页码 → (章节索引, 章内页码)
     * 遍历缓存计算累计页数。时间复杂度 O(n)，n=章数，通常 < 2000。
     */
    fun globalToLocal(globalPageIndex: Int): Pair<Int, Int> {
        var remaining = globalPageIndex
        for (ci in 0 until chapterCount) {
            val cl = layoutCache[ci]
            if (cl != null) {
                if (remaining < cl.totalPages) {
                    return ci to remaining
                }
                remaining -= cl.totalPages
            }
        }
        // 如果全局页码超出范围，返回最后一章最后一页
        return (chapterCount - 1).coerceAtLeast(0) to 0
    }

    /**
     * (章节索引, 章内页码) → 全局页码
     */
    fun localToGlobal(chapterIndex: Int, pageInChapter: Int): Int {
        var global = 0
        for (ci in 0 until chapterIndex.coerceAtMost(chapterCount)) {
            layoutCache[ci]?.let { global += it.totalPages }
        }
        return global + pageInChapter
    }

    /**
     * 获取总页数（跨所有已布局章节）
     */
    fun getTotalPages(): Int {
        var total = 0
        for (ci in 0 until chapterCount) {
            layoutCache[ci]?.let { total += it.totalPages }
        }
        return total
    }

    /**
     * 获取指定章的页数
     */
    fun getChapterPageCount(chapterIndex: Int): Int {
        return layoutCache[chapterIndex]?.totalPages ?: 0
    }

    /**
     * 获取章节布局（从缓存）
     */
    fun getChapterLayout(chapterIndex: Int): ChapterLayout? {
        return layoutCache[chapterIndex]
    }

    /**
     * 获取章内某页的布局信息
     */
    fun getPageLayout(chapterIndex: Int, pageInChapter: Int): PageLayout? {
        return layoutCache[chapterIndex]?.pages?.getOrNull(pageInChapter)
    }

    // ── 缓存管理 ──

    fun invalidateChapter(chapterIndex: Int) {
        layoutCache.remove(chapterIndex)
    }

    fun invalidateAll() {
        layoutCache.clear()
        cumulativePageTotals.clear()
    }
}
