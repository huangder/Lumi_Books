package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.coroutines.runBlocking
import com.huangder.lumibooks.domain.model.Note

/**
 * 核心阅读视图。
 *
 * FrameLayout 包含 3 个 [PageSurfaceView] 槽位，使用 [PageAnimationController]
 * 管理翻页动画。所有触摸事件、绘制、动画都在此 View 内处理，不经过 Compose 重组。
 *
 * 用法（Compose 侧）:
 * ```
 * AndroidView(factory = { ReadView(it).apply {
 *     setCallbacks(callbacks)
 *     setContentProvider(provider)
 *     configure(fontSizePx, theme, chapterCount, startChapter, startPage)
 * }}, update = { view ->
 *     view.configure(fontSizePx, theme, chapterCount, startChapter, startPage)
 * })
 * ```
 */
class ReadView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "ReadView"
    }

    // ── 子组件 ──
    val layoutEngine = PageLayoutEngine()
    val renderer = PageRenderer()
    lateinit var slotManager: PageSlotManager
        private set
    lateinit var animationController: PageAnimationController
        private set

    // ── 3 个页槽 ──
    val prevPageView = PageSurfaceView(context)
    val curPageView = PageSurfaceView(context)
    val nextPageView = PageSurfaceView(context)

    // ── 外部回调 ──
    private var callbacks: ReadViewCallbacks? = null
    private var contentProvider: (suspend (Int) -> CharSequence?)? = null

    // ── 选择状态 ──
    var selChapter: Int = -1
        private set
    var selStart: Int = -1
        private set
    var selEnd: Int = -1
        private set

    private var savedNotes: List<Note> = emptyList()

    /** 清除选择并重绘 */
    fun clearSelection() {
        selChapter = -1; selStart = -1; selEnd = -1
        refreshAnnotationLayer(includeSelection = false)
        onSelectionChanged?.invoke(-1, -1, -1, -1, "")
    }

    /** 供外部调用的选区更新（菜单调整范围时使用） */
    fun updateSelection(newStart: Int, newEnd: Int) {
        selStart = newStart; selEnd = newEnd
        refreshAnnotationLayer(includeSelection = true)
        val curSlot = slotManager.getCurSlot()
        val text = kotlinx.coroutines.runBlocking { contentProvider?.invoke(curSlot.chapterIndex) }?.toString() ?: ""
        if (selStart in 0 until text.length && selEnd in selStart..text.length) {
            val sel = text.substring(selStart, selEnd)
            onSelectionChanged?.invoke(curSlot.chapterIndex, curSlot.pageIndex, selStart, selEnd, sel)
        }
    }

    /** 设置已保存的笔记/高亮并刷新当前页。 */
    fun setSavedNotes(notes: List<Note>) {
        savedNotes = notes
        refreshAnnotationLayer(includeSelection = selChapter >= 0 && selStart >= 0 && selEnd > selStart)
    }

    /**
     * 完整刷新：重绘文字层 + 标注层。
     * 仅在翻页、配置变更时调用（重量级）。
     */
    private fun refreshCurrentPageAnnotations(includeSelection: Boolean) {
        val curSlot = slotManager.getCurSlot()
        if (!curSlot.isLoaded) {
            invalidate()
            return
        }
        val cl = layoutEngine.getChapterLayout(curSlot.chapterIndex) ?: return

        // 文字层：重新渲染文字到 pageBitmap
        val bm = curSlot.surfaceView.getPageBitmap() ?: return
        renderer.renderPage(cl, curSlot.pageIndex, bm)
        curSlot.surfaceView.setPageBitmap(bm)

        // 标注层：渲染到独立的透明 Bitmap
        refreshAnnotationLayer(includeSelection)
    }

    /**
     * 快速刷新：仅重绘标注层（高亮/选区）。
     * 拖动手柄时调用（轻量级），文字层不动。
     */
    private fun refreshAnnotationLayer(includeSelection: Boolean) {
        val curSlot = slotManager.getCurSlot()
        if (!curSlot.isLoaded) return
        val cl = layoutEngine.getChapterLayout(curSlot.chapterIndex) ?: return

        // 收集本页的已保存标注
        val pageLayout = cl.pages.getOrNull(curSlot.pageIndex) ?: return
        val chapterIndex = curSlot.chapterIndex
        val pageStart = pageLayout.startCharOffset
        val pageEnd = pageLayout.endCharOffset

        val annotations = savedNotes
            .filter { it.chapterIndex == chapterIndex && it.endPosition > pageStart && it.startPosition < pageEnd }
            .map { note ->
                val color = try {
                    android.graphics.Color.parseColor(note.color)
                } catch (_: IllegalArgumentException) {
                    0x40FFEB3B.toInt()
                }
                PageRenderer.AnnotationSpec(note.startPosition, note.endPosition, color)
            }

        val selStart = if (includeSelection && selChapter == chapterIndex && selStart >= 0 && selEnd > selStart) selStart else -1
        val selEnd = if (selStart >= 0) selEnd else -1

        val annotationBm = renderer.renderAnnotationLayer(cl, curSlot.pageIndex, annotations, selStart, selEnd)
        curSlot.surfaceView.setAnnotationBitmap(annotationBm)

        invalidate()
    }

    /** Composed 手柄半径 dp → px，和之前 PageRenderer.drawHandle 保持一致 */
    val handleRadiusPx: Float get() = 9f * resources.displayMetrics.density

    /** 计算某个字符偏移对应的屏幕坐标（handle 圆心） */
    private fun charOffsetToScreenPos(sl: android.text.StaticLayout, charOffset: Int, pageStartY: Float): Pair<Float, Float>? {
        if (charOffset < 0 || charOffset >= sl.text.length) return null
        val line = sl.getLineForOffset(charOffset)
        val cx = renderer.renderMarginLeft + sl.getPrimaryHorizontal(charOffset)
        val cy = renderer.renderMarginTop + sl.getLineBottom(line) - pageStartY + handleRadiusPx * 0.5f
        return cx to cy
    }

    /** 手柄坐标数据类 */
    data class HandleCenters(val startCx: Float, val startCy: Float, val endCx: Float, val endCy: Float)

    /** 选区边界框数据类（屏幕坐标） */
    data class SelectionBounds(val topY: Float, val bottomY: Float, val startX: Float, val endX: Float)

    /** 获取选区的屏幕边界框，供菜单定位使用 */
    fun getSelectionBounds(): SelectionBounds? {
        if (selChapter < 0 || selStart < 0 || selEnd <= selStart) return null
        val curSlot = slotManager.getCurSlot()
        if (curSlot.chapterIndex != selChapter) return null
        val cl = layoutEngine.getChapterLayout(selChapter) ?: return null
        val sl = cl.staticLayout
        val pages = cl.pages
        val pageLayout = pages.getOrNull(curSlot.pageIndex) ?: return null
        val pageStartY = sl.getLineTop(pageLayout.startLine).toFloat()

        val startLine = sl.getLineForOffset(selStart)
        val endLine = sl.getLineForOffset((selEnd - 1).coerceIn(0, sl.text.length - 1))

        val topY = renderer.renderMarginTop + sl.getLineTop(startLine) - pageStartY
        val bottomY = renderer.renderMarginTop + sl.getLineBottom(endLine) - pageStartY
        val startX = renderer.renderMarginLeft + sl.getPrimaryHorizontal(selStart)
        val endOffset = (selEnd - 1).coerceIn(0, sl.text.length - 1)
        val endX = renderer.renderMarginLeft + sl.getPrimaryHorizontal(endOffset)

        return SelectionBounds(topY, bottomY, startX.coerceAtMost(endX), startX.coerceAtLeast(endX))
    }

    /** 获取两个手柄的屏幕坐标（圆心），供 Compose 覆盖层使用 */
    fun getSelectionHandleCenters(): HandleCenters? {
        if (selChapter < 0 || selStart < 0 || selEnd <= selStart) return null
        val curSlot = slotManager.getCurSlot()
        if (curSlot.chapterIndex != selChapter) return null
        val sl = layoutEngine.getChapterLayout(selChapter)?.staticLayout ?: return null
        val pages = layoutEngine.getChapterLayout(selChapter)?.pages ?: return null
        val pageLayout = pages.getOrNull(curSlot.pageIndex) ?: return null
        val pageStartY = sl.getLineTop(pageLayout.startLine).toFloat()

        val sc = charOffsetToScreenPos(sl, selStart, pageStartY) ?: return null
        val ec = charOffsetToScreenPos(sl, (selEnd - 1).coerceIn(0, sl.text.length - 1), pageStartY) ?: return null

        return HandleCenters(sc.first, sc.second, ec.first, ec.second)
    }

    /** 容错版坐标→字符偏移，拖拽手柄时使用。X 超出列范围时返回最近列，Y 超出页面时钳制。 */
    fun getCharOffsetAtPointRough(x: Float, y: Float): Int? {
        val curSlot = slotManager.getCurSlot()
        val chIdx = curSlot.chapterIndex
        val pageIdx = curSlot.pageIndex
        val cl = layoutEngine.getChapterLayout(chIdx) ?: return null
        val pageLayout = cl.pages.getOrNull(pageIdx) ?: return null
        val sl = cl.staticLayout

        val textX = (x - renderer.renderMarginLeft).coerceIn(0f, sl.width.toFloat())
        val textY = y - renderer.renderMarginTop + sl.getLineTop(pageLayout.startLine)

        val line = sl.getLineForVertical(textY.toInt().coerceIn(0, sl.height - 1))
            .coerceIn(pageLayout.startLine, (pageLayout.endLine - 1).coerceAtLeast(pageLayout.startLine))
        val offset = sl.getOffsetForHorizontal(line, textX)
        return offset.coerceIn(pageLayout.startCharOffset, (pageLayout.endCharOffset - 1).coerceAtLeast(pageLayout.startCharOffset))
    }

    /**
     * Compose 手柄拖拽时调用。
     * @param handleIndex 1=起始手柄, 2=结束手柄
     * @param screenX 当前触摸位置 X（ReadView 坐标系）
     * @param screenY 当前触摸位置 Y
     */
    fun moveSelectionHandle(handleIndex: Int, screenX: Float, screenY: Float) {
        val charOff = getCharOffsetAtPointRough(screenX, screenY) ?: return
        if (handleIndex == 1) {
            // 拖拽起始手柄
            if (charOff >= selEnd) {
                // 交叉！交换角色：原 start 变 end，新位置变 start
                val oldEnd = selEnd
                selEnd = oldEnd
                selStart = (oldEnd - 1).coerceAtLeast(0)
            } else {
                selStart = charOff
            }
        } else {
            // 拖拽结束手柄
            if (charOff + 1 <= selStart) {
                // 交叉！交换角色：原 end 变 start，新位置变 end
                selEnd = selStart + 1
                selStart = charOff
            } else {
                selEnd = charOff + 1
            }
        }
        // 拖动时只重绘标注层（轻量），不重绘文字层
        refreshAnnotationLayer(includeSelection = true)
        // 通知菜单更新选区范围（不更新手柄坐标，避免反馈环导致抽搐）
        val curSlot = slotManager.getCurSlot()
        val text = kotlinx.coroutines.runBlocking { contentProvider?.invoke(curSlot.chapterIndex) }?.toString() ?: ""
        if (selStart in 0 until text.length && selEnd in selStart..text.length) {
            val sel = text.substring(selStart, selEnd)
            callbacks?.onTextSelected(curSlot.chapterIndex, curSlot.pageIndex, selStart, selEnd, sel, screenX, screenY)
        }
    }

    /** 选择变化回调（通知 Compose 层更新手柄位置和菜单） */
    var onSelectionChanged: ((chapterIndex: Int, pageIndex: Int, selStart: Int, selEnd: Int, selectedText: String) -> Unit)? = null

    // ── 配置状态 ──
    private var isConfigured = false
    private var currentFontSizePx: Float = 56f
    private var currentTheme: String = "day"
    private var currentChapterCount: Int = 0
    private var currentLineHeightMult: Float = 1.5f
    private var currentLetterSpacingDp: Float = 0f
    private var currentFontType: String = "system"
    private var currentMarginHorizDp: Float = 44f
    private var currentMarginVertDp: Float = 72f
    private var pendingStartChapter: Int = 0
    private var pendingStartPage: Int = 0

    init {
        // 🔥 确保接收触摸事件
        isClickable = true
        isFocusable = true

        // 添加三个 PageSurfaceView 到布局
        addView(prevPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nextPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 初始化管理器
        slotManager = PageSlotManager(layoutEngine, renderer, prevPageView, curPageView, nextPageView)
        slotManager.contentProvider = { chapterIndex ->
            contentProvider?.invoke(chapterIndex)
        }

        // 初始化动画控制器
        animationController = SlidePageAnim(this)

        // 翻页前校验：目标槽位未加载则阻止翻页动画
        animationController.onCanFlip = { dir ->
            when (dir) {
                PageAnimationController.Direction.NEXT -> slotManager.getNextSlot().isLoaded
                PageAnimationController.Direction.PREV -> slotManager.getPrevSlot().isLoaded
                else -> false
            }
        }

        // 动画完成回调
        animationController.onAnimationComplete = {
            when (animationController.currentDirection) {
                PageAnimationController.Direction.NEXT -> slotManager.shiftForward()
                PageAnimationController.Direction.PREV -> slotManager.shiftBackward()
                else -> {}
            }
        }

        // 点击回调
        animationController.onTapLeft = {
            clearSelection()
            slotManager.getPrevSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.PREV)
                }
            }
        }
        animationController.onTapCenter = {
            callbacks?.onMenuToggle()
        }
        animationController.onTapRight = {
            clearSelection()
            slotManager.getNextSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.NEXT)
                }
            }
        }

        // 长按文本选择回调
        animationController.onLongPress = { x, y ->
            val curSlot = slotManager.getCurSlot()
            val chIdx = curSlot.chapterIndex
            val pageIdx = curSlot.pageIndex
            val charOffset = layoutEngine.getCharOffsetAtPoint(chIdx, pageIdx, x, y)
            if (charOffset != null) {
                val rawText = runBlocking { contentProvider?.invoke(chIdx) }
                val text = rawText?.toString() ?: ""
                if (text.isNotEmpty()) {
                // 从点按位置向外扩展选词：CJK 左右各 2-3 字，英文到词边界
                var start = charOffset
                var end = charOffset
                val isCJK = text[charOffset].code in 0x4E00..0x9FFF || text[charOffset].code in 0x3400..0x4DBF
                if (isCJK) {
                    start = (charOffset - 2).coerceAtLeast(0)
                    end = (charOffset + 3).coerceAtMost(text.length)
                } else {
                    fun isWordSep(c: Char): Boolean = c.isWhitespace() || (!c.isLetterOrDigit() && c != '\'' && c != '-')
                    while (start > 0 && !isWordSep(text[start - 1])) start--
                    while (end < text.length && !isWordSep(text[end])) end++
                }
                if (end > start) {
                    val selected = text.substring(start, end)
                    // 存储选择状态并绘制高亮
                    selChapter = chIdx
                    selStart = start
                    selEnd = end
                    refreshCurrentPageAnnotations(includeSelection = true)
                    // 通知 Compose 层更新手柄位置 + 显示菜单
                    onSelectionChanged?.invoke(chIdx, pageIdx, start, end, selected)
                    callbacks?.onTextSelected(chIdx, pageIdx, start, end, selected, x, y)
                }
                }
            }
        }

        // 页面变化回调
        slotManager.onPageChangedCallback = { globalPage, chapterIdx, pageInChapter, chapterTotal ->
            callbacks?.onPageChanged(globalPage, chapterIdx, pageInChapter, chapterTotal)
            refreshCurrentPageAnnotations(includeSelection = selChapter == chapterIdx && selStart >= 0 && selEnd > selStart)
            // 🔥 确保 ReadView 重绘（dispatchDraw 不经过子 View 的 invalidate 路径）
            invalidate()
        }

        setWillNotDraw(false)
    }

    // ── 配置 ──

    fun setCallbacks(cbs: ReadViewCallbacks) {
        callbacks = cbs
    }

    fun setContentProvider(provider: suspend (Int) -> CharSequence?) {
        contentProvider = provider
        slotManager.contentProvider = provider
    }

    /**
     * 配置引擎参数。会在参数变化时重新布局。
     */
    fun configure(
        fontSizePx: Float,
        theme: String,
        chapterCount: Int,
        startChapter: Int,
        startPage: Int,
        lineHeightMult: Float = 1.5f,
        letterSpacingDp: Float = 0f,
        fontType: String = "system",
        marginHorizDp: Float = 44f,
        marginVertDp: Float = 72f,
        width: Int = this.width,
        height: Int = this.height
    ) {
        if (width <= 0 || height <= 0) {
            // View 尚未布局，暂存参数
            pendingStartChapter = startChapter
            pendingStartPage = startPage
            currentFontSizePx = fontSizePx
            currentTheme = theme
            currentChapterCount = chapterCount
            return
        }

        val themeChanged = currentTheme != theme
        val fontSizeChanged = Math.abs(currentFontSizePx - fontSizePx) > 0.5f
        val lineHeightChanged = Math.abs(currentLineHeightMult - lineHeightMult) > 0.01f
        val letterSpacingChanged = Math.abs(currentLetterSpacingDp - letterSpacingDp) > 0.05f
        val fontTypeChanged = currentFontType != fontType
        val marginHorizChanged = Math.abs(currentMarginHorizDp - marginHorizDp) > 0.5f
        val marginVertChanged = Math.abs(currentMarginVertDp - marginVertDp) > 0.5f
        val sizeChanged = !isConfigured
        val needsRelayout = themeChanged || fontSizeChanged || lineHeightChanged ||
                letterSpacingChanged || fontTypeChanged || marginHorizChanged ||
                marginVertChanged || sizeChanged

        currentFontSizePx = fontSizePx
        currentTheme = theme
        currentChapterCount = chapterCount
        currentLineHeightMult = lineHeightMult
        currentLetterSpacingDp = letterSpacingDp
        currentFontType = fontType
        currentMarginHorizDp = marginHorizDp
        currentMarginVertDp = marginVertDp

        // 主题颜色
        val (bgColor, textColor) = getThemeColors(theme)

        // 边距 + 字间距 dp → px
        val density = resources.displayMetrics.density
        val marginHoriz = marginHorizDp * density
        val marginVert = marginVertDp * density
        val lineSpacing = 2.5f * density
        val lsPx = letterSpacingDp * density

        // 自定义字体 Typeface
        val customTypeface = if (fontType == "dingli_song") {
            try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/dingli_song.ttf") }
            catch (_: Exception) { null }
        } else null

        // 配置布局引擎
        layoutEngine.configure(
            width = width,
            height = height,
            fontSizePx = fontSizePx,
            lineSpacingPx = lineSpacing,
            lineSpacingMult = lineHeightMult,
            letterSpacingPx = lsPx,
            fontType = fontType,
            customTypeface = customTypeface,
            marginLeftPx = marginHoriz,
            marginRightPx = marginHoriz,
            marginTopPx = marginVert,
            marginBottomPx = marginVert,
            textColor = textColor,
            chapterCount = chapterCount
        )

        // 配置渲染器
        val visibleH = (height - marginVert * 2).toFloat().coerceAtLeast(1f)
        renderer.configure(
            width = width,
            height = height,
            backgroundColor = bgColor,
            textColor = textColor,
            marginLeftPx = marginHoriz,
            marginTopPx = marginVert,
            visibleHeightPx = visibleH
        )

        if (needsRelayout) {
            layoutEngine.invalidateAll()
        }

        // 初始化/重新加载槽位
        if (!isConfigured || needsRelayout) {
            slotManager.setChapterCount(chapterCount)
            slotManager.initialize(startChapter, startPage)
            isConfigured = true
        }
        refreshCurrentPageAnnotations(includeSelection = selChapter >= 0 && selStart >= 0 && selEnd > selStart)
    }

    /** 跳转到指定章节指定页 */
    fun jumpToChapter(chapterIndex: Int, pageInChapter: Int = 0) {
        animationController.abortAnim()
        layoutEngine.invalidateChapter(chapterIndex)
        slotManager.jumpTo(chapterIndex, pageInChapter)
    }

    /** 获取当前页面 Bitmap（供动画使用） */
    fun getCurBitmap() = curPageView.getPageBitmap()
    fun getPrevBitmap() = prevPageView.getPageBitmap()
    fun getNextBitmap() = nextPageView.getPageBitmap()

    /** 获取当前页标注层 Bitmap（供动画控制器叠加绘制） */
    fun getCurAnnotationBitmap() = curPageView.getAnnotationBitmap()

    // ── 绘制 ──

    /**
     * PageSurfaceView 仅作 Bitmap 存储容器，不参与 View 树的绘制。
     * 所有绘制（空闲 + 动画）统一由 [PageAnimationController.onDraw] 处理，
     * 避免 3 个堆叠子 View 同时绘制导致 z-order 错乱和动画穿透。
     */
    override fun dispatchDraw(canvas: Canvas) {
        animationController.onDraw(canvas)
    }

    // ── 触摸 ──

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return animationController.onTouchEvent(event)
    }

    // ── 滚动动画帧 ──

    override fun computeScroll() {
        if (animationController.computeScroll()) {
            postInvalidateOnAnimation()
        }
    }

    // ── 生命周期 ──

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationController.abortAnim()
        slotManager.destroy()
        renderer.destroy()
        layoutEngine.invalidateAll()
    }

    // ── 内部方法 ──

    private fun startTapAnimation(dir: PageAnimationController.Direction) {
        if (animationController.isRunning) return
        (animationController as? SlidePageAnim)?.startFromTap(dir)
    }

    private fun getThemeColors(theme: String): Pair<Int, Int> {
        return when (theme) {
            "night" -> 0xFF1a1a1a.toInt() to 0xFFCCCCCC.toInt()
            "sepia" -> 0xFFf5e6d3.toInt() to 0xFF4a3728.toInt()
            "green" -> 0xFFe8f5e9.toInt() to 0xFF2e7d32.toInt()
            else -> 0xFFFBFBFC.toInt() to 0xFF333333.toInt()  // day
        }
    }

    /**
     * 在 View 布局完成后初始化。
     * 如果 configure() 在布局结束前调用，参数会被暂存，在此处应用。
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && !isConfigured && currentChapterCount > 0) {
            configure(
                fontSizePx = currentFontSizePx,
                theme = currentTheme,
                chapterCount = currentChapterCount,
                startChapter = pendingStartChapter,
                startPage = pendingStartPage,
                width = right - left,
                height = bottom - top
            )
        }
    }
}
