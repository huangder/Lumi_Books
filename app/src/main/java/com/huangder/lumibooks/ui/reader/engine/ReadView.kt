package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.coroutines.runBlocking

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

    /** 清除选择并重绘 */
    fun clearSelection() {
        selChapter = -1; selStart = -1; selEnd = -1
        // 重新渲染当前页（清除高亮）
        val curSlot = slotManager.getCurSlot()
        if (curSlot.isLoaded) {
            highlightPage(curSlot.chapterIndex, curSlot.pageIndex, curSlot.surfaceView)
        }
        invalidate()
    }

    /** 在当前页上应用选择高亮 */
    private fun applyHighlightOnCurrentPage() {
        val curSlot = slotManager.getCurSlot()
        if (curSlot.isLoaded && selChapter == curSlot.chapterIndex) {
            highlightPage(curSlot.chapterIndex, curSlot.pageIndex, curSlot.surfaceView)
        }
        invalidate()
    }

    private fun highlightPage(chapterIndex: Int, pageIndex: Int, surfaceView: PageSurfaceView) {
        val cl = layoutEngine.getChapterLayout(chapterIndex) ?: return
        val bitmap = surfaceView.getPageBitmap() ?: return
        if (selChapter == chapterIndex && selStart >= 0 && selEnd > selStart) {
            renderer.drawSelectionHighlight(bitmap, cl, pageIndex, selStart, selEnd)
        }
    }

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
                // 从点击位置向外扩展选取一个词（以空格/标点为界）
                var start = charOffset
                var end = charOffset
                // 从点按位置向外扩展到词边界
                fun isWordSep(c: Char): Boolean {
                    if (c.isWhitespace()) return true
                    val code = c.code
                    // CJK标点范围、ASCII标点、以及其他非字母数字字符
                    return !c.isLetterOrDigit() && code != 0x2018 && code != 0x2019 && code != 0x201C && code != 0x201D
                }
                while (start > 0 && !isWordSep(text[start - 1])) start--
                while (end < text.length && !isWordSep(text[end])) end++
                if (end > start) {
                    val selected = text.substring(start, end)
                    // 存储选择状态并绘制高亮
                    selChapter = chIdx
                    selStart = start
                    selEnd = end
                    applyHighlightOnCurrentPage()
                    callbacks?.onTextSelected(chIdx, pageIdx, start, end, selected, x, y)
                }
                }
            }
        }

        // 页面变化回调
        slotManager.onPageChangedCallback = { globalPage, chapterIdx, pageInChapter, chapterTotal ->
            callbacks?.onPageChanged(globalPage, chapterIdx, pageInChapter, chapterTotal)
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
