package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
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
        val textPaint: TextPaint,
        val textAlignment: ReaderTextAlignment,
        val writingMode: ReaderWritingMode
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
    private var textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL
    private var writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL

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
    private val layoutCache = object : LinkedHashMap<Int, ChapterLayout>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ChapterLayout>?): Boolean {
            return size > 8  // 扩容：支持预加载 ch+1、ch+2 后仍保留前后滑动窗口
        }
    }
    private val cacheLock = Any()
    private var layoutGeneration: Long = 0L

    /** 各章累计页数前缀和，用于全局页码转换 */
    private val cumulativePageTotals = mutableListOf<Int>()

    /** 总章数 */
    private var chapterCount: Int = 0

    /** TXT title spans use dp units, so copied paints must retain display density. */
    private var useDisplayDensityForSpans: Boolean = false

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
        fontWeight: Int = 400,
        marginLeftPx: Float = 48f,
        marginRightPx: Float = 48f,
        marginTopPx: Float = 32f,
        marginBottomPx: Float = 32f,
        textColor: Int = 0xFF333333.toInt(),
        chapterCount: Int = 0,
        useDisplayDensityForSpans: Boolean = false,
        textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
        writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL
    ) {
        val baseTypeface = when {
            fontType == "serif" -> Typeface.SERIF
            fontType == "sans_serif" -> Typeface.SANS_SERIF
            fontType == "monospace" -> Typeface.MONOSPACE
            fontType == "fangsong" || fontType == "kaiti" || fontType.startsWith("custom") ->
                customTypeface ?: Typeface.DEFAULT
            else -> Typeface.DEFAULT
        }
        val tf = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, fontWeight.coerceIn(100, 900), false)
        } else {
            Typeface.create(baseTypeface, if (fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
        val changed = textWidth != width || textHeight != height ||
                ownTextPaint.textSize != fontSizePx || lineSpacingExtra != lineSpacingPx ||
                lineSpacingMultiplier != lineSpacingMult ||
                this.letterSpacing != letterSpacingPx ||
                marginLeft != marginLeftPx || marginRight != marginRightPx ||
                marginTop != marginTopPx || marginBottom != marginBottomPx ||
                this.useDisplayDensityForSpans != useDisplayDensityForSpans ||
                this.textAlignment != textAlignment ||
                this.writingMode != writingMode || ownTextPaint.typeface != tf

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

        ownTextPaint.typeface = tf
        this.chapterCount = chapterCount
        this.useDisplayDensityForSpans = useDisplayDensityForSpans
        this.textAlignment = textAlignment
        this.writingMode = writingMode

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
                textPaint = TextPaint(activeTextPaint).apply {
                    if (useDisplayDensityForSpans) {
                        density = activeTextPaint.density
                    }
                },
                textAlignment = textAlignment,
                writingMode = writingMode
            )
        }

        val sl = StaticLayout.Builder.obtain(text, 0, text.length, input.textPaint, input.visibleWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(
                if (input.textAlignment == ReaderTextAlignment.JUSTIFY) {
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    Layout.JUSTIFICATION_MODE_NONE
                }
            )
            .build()

        if (input.writingMode.isVertical) {
            val verticalPages = VerticalTextLayouter.layout(
                text = text,
                paint = input.textPaint,
                width = input.visibleWidth,
                height = input.visibleHeight,
                lineSpacingExtra = lineSpacingExtra,
                lineSpacingMultiplier = lineSpacingMultiplier,
                letterSpacing = letterSpacing
            ).mapIndexed { pageIndex, page ->
                PageLayout(
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndex,
                    startLine = 0,
                    endLine = 0,
                    startCharOffset = page.startOffset,
                    endCharOffset = page.endOffset,
                    verticalGeometry = page.geometry
                )
            }
            val cumulativeBefore = synchronized(cacheLock) {
                (0 until chapterIndex).sumOf { layoutCache[it]?.totalPages ?: 0 }
            }
            val result = ChapterLayout(
                chapterIndex = chapterIndex,
                pages = verticalPages,
                staticLayout = sl,
                totalPages = verticalPages.size,
                cumulativePagesBefore = cumulativeBefore
            )
            synchronized(cacheLock) {
                if (input.generation == layoutGeneration) layoutCache[chapterIndex] = result
            }
            return@withContext result
        }

        // 留出安全边距：
        // - 舍入余量，消除 TextView 与 StaticLayout 的取整差异
        // - descent 缓冲：某些自定义字体的字形 descent 超过 StaticLayout 报告的 lineBottom，
        //   不预留时最后一行字符会超出底边距被横向截断。
        val pages = mutableListOf<PageLayout>()
        // 🔥 descent 缓冲只做 1x + 2px 粗筛：
        // textPaint.descent() 只反映主字体，繁体字等走 fallback 字体时实际 descent 更大，
        // 但溢出由下方按最后一行实际 sl.getLineDescent() 的精确回退兜底；
        // 粗筛过大会在页底留下接近一整行的空白，让用户边距设置形同虚设。
        val descentBuffer = (input.textPaint.descent() + 2f).coerceAtLeast(4f)
        val effectiveVh = (input.visibleHeight.toFloat() - descentBuffer).coerceAtLeast(1f)
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

            // 🔥 精确回退：用最后一行的实际 descent（含 fallback 字体）验证不溢出。
            // lineTop(lastLine) 相对 pageStart 的偏移 = accumulatedHeight - lastLineHeight
            if (pageEndLine > pageStartLine) {
                val lastLine = pageEndLine - 1
                val lastLineHeight = (sl.getLineBottom(lastLine) - sl.getLineTop(lastLine)).toFloat()
                val lastLineTopOffset = accumulatedHeight - lastLineHeight
                val actualBottom = lastLineTopOffset +
                        (-sl.getLineAscent(lastLine)).toFloat() +    // ascent 为负
                        sl.getLineDescent(lastLine).toFloat()
                if (actualBottom > input.visibleHeight.toFloat() && pageEndLine > pageStartLine + 1) {
                    // 最后一行真实字形底部超出可视区，将其挪到下一页
                    pageEndLine--
                    accumulatedHeight -= lastLineHeight
                }
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
