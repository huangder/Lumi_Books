package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import com.huangder.lumibooks.domain.model.Note

/**
 * 核心阅读视图。
 *
 * FrameLayout 包含 3 个 [PageContentView] 槽位，使用 [PageAnimationController]
 * 管理翻页动画。文字选择由系统 TextView 原生处理。
 */
class ReadView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "ReadView"
    }

    // ── 子组件 ──
    val layoutEngine = PageLayoutEngine()
    lateinit var slotManager: PageSlotManager
        private set
    lateinit var animationController: PageAnimationController
        private set

    // ── 3 个页槽 ──
    val prevPageView = PageContentView(context)
    val curPageView = PageContentView(context)
    val nextPageView = PageContentView(context)

    // ── 外部回调 ──
    private var callbacks: ReadViewCallbacks? = null
    private var contentProvider: (suspend (Int) -> CharSequence?)? = null

    private var savedNotes: List<Note> = emptyList()

    /** 设置已保存的笔记/高亮并刷新当前页。 */
    fun setSavedNotes(notes: List<Note>) {
        savedNotes = notes
        slotManager.refreshCurrentHighlights()
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
        isClickable = true
        isFocusable = true

        // 添加三个 PageContentView 到布局
        addView(prevPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nextPageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 初始化管理器
        slotManager = PageSlotManager(layoutEngine, prevPageView, curPageView, nextPageView)
        slotManager.contentProvider = { chapterIndex ->
            contentProvider?.invoke(chapterIndex)
        }
        slotManager.highlightProvider = { chapterIndex ->
            buildHighlights(chapterIndex)
        }

        // 初始化动画控制器
        animationController = SlidePageAnim(this)

        animationController.onCanFlip = { dir ->
            when (dir) {
                PageAnimationController.Direction.NEXT -> slotManager.getNextSlot().isLoaded
                PageAnimationController.Direction.PREV -> slotManager.getPrevSlot().isLoaded
                else -> false
            }
        }

        animationController.onAnimationComplete = {
            when (animationController.currentDirection) {
                PageAnimationController.Direction.NEXT -> slotManager.shiftForward()
                PageAnimationController.Direction.PREV -> slotManager.shiftBackward()
                else -> {}
            }
        }

        animationController.onTapLeft = {
            curPageView.clearSelection()
            slotManager.getPrevSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.PREV)
                }
            }
        }
        animationController.onTapCenter = {
            curPageView.clearSelection()
            callbacks?.onMenuToggle()
        }
        animationController.onTapRight = {
            curPageView.clearSelection()
            slotManager.getNextSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.NEXT)
                }
            }
        }

        // 长按选词
        animationController.onLongPress = { x, y ->
            val result = curPageView.selectWordAt(x, y)
            val text = result?.third ?: ""
            callbacks?.onTextSelected(text, x, y)
        }

        // 翻页后刷新高亮
        slotManager.onPageChangedCallback = { globalPage, chapterIdx, pageInChapter, chapterTotal ->
            callbacks?.onPageChanged(globalPage, chapterIdx, pageInChapter, chapterTotal)
            configureCurrentPageView()
            invalidate()
        }

        setWillNotDraw(false)
    }

    /** 构建某章的高亮列表（savedNotes → Triple(start, end, color)） */
    private fun buildHighlights(chapterIndex: Int): List<Triple<Int, Int, Int>> {
        return savedNotes
            .filter { it.chapterIndex == chapterIndex }
            .map { note ->
                val color = try {
                    android.graphics.Color.parseColor(note.color)
                } catch (_: IllegalArgumentException) {
                    0x40FFEB3B.toInt()
                }
                Triple(note.startPosition, note.endPosition, color)
            }
    }

    /** 配置所有 PageContentView 的 TextView 样式（防止翻页错版） */
    private fun configureCurrentPageView() {
        val (bgColor, textColor) = getThemeColors(currentTheme)
        val density = resources.displayMetrics.density
        val marginHoriz = currentMarginHorizDp * density
        val marginVert = currentMarginVertDp * density

        val customTypeface = if (currentFontType == "dingli_song") {
            try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/dingli_song.ttf") }
            catch (_: Exception) { android.graphics.Typeface.DEFAULT }
        } else android.graphics.Typeface.DEFAULT

        // 三个槽位都配置，确保翻页时样式一致
        for (view in listOf(prevPageView, curPageView, nextPageView)) {
            view.configure(
                fontSizePx = currentFontSizePx,
                textColor = textColor,
                lineHeightMult = currentLineHeightMult,
                letterSpacingPx = currentLetterSpacingDp * density,
                typeface = customTypeface,
                marginLeftPx = marginHoriz,
                marginTopPx = marginVert,
                marginRightPx = marginHoriz,
                marginBottomPx = marginVert
            )
            view.setBackgroundColor(bgColor)
        }
        setBackgroundColor(bgColor)
    }

    // ── 配置 ──

    fun setCallbacks(cbs: ReadViewCallbacks) {
        callbacks = cbs
    }

    fun setContentProvider(provider: suspend (Int) -> CharSequence?) {
        contentProvider = provider
        slotManager.contentProvider = provider
    }

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

        val (_, textColor) = getThemeColors(theme)
        val density = resources.displayMetrics.density
        val marginHoriz = marginHorizDp * density
        val marginVert = marginVertDp * density
        val lineSpacing = 2.5f * density
        val lsPx = letterSpacingDp * density

        val customTypeface = if (fontType == "dingli_song") {
            try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/dingli_song.ttf") }
            catch (_: Exception) { null }
        } else null

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

        configureCurrentPageView()

        if (needsRelayout) {
            layoutEngine.invalidateAll()
        }

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

    // ── 触摸 ──

    // 拦截所有触摸事件，不让子 View（PageContentView）处理
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return animationController.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (animationController.computeScroll()) {
            postInvalidateOnAnimation()
        }
    }

    // ── 绘制 ──

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        // 先设置子 View 的 translationX（翻页动画位置）
        animationController.onDraw(canvas)
        // 绘制子 View（PageContentView 包含的 TextView）
        super.dispatchDraw(canvas)
        // 再绘制阴影叠加层（在子 View 之上）
        (animationController as? SlidePageAnim)?.drawOverlay(canvas)
    }

    // ── 生命周期 ──

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationController.abortAnim()
        slotManager.destroy()
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
            else -> 0xFFFBFBFC.toInt() to 0xFF333333.toInt()
        }
    }

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
