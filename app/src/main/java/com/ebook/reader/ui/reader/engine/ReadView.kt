package com.ebook.reader.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout

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

    // ── 配置状态 ──
    private var isConfigured = false
    private var currentFontSizePx: Float = 56f
    private var currentTheme: String = "day"
    private var currentChapterCount: Int = 0
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
            slotManager.getNextSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.NEXT)
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
        val sizeChanged = !isConfigured
        val needsRelayout = themeChanged || fontSizeChanged || sizeChanged

        currentFontSizePx = fontSizePx
        currentTheme = theme
        currentChapterCount = chapterCount

        // 主题颜色
        val (bgColor, textColor) = getThemeColors(theme)

        // 边距：上下 ≈ 1.78x 左右
        val density = resources.displayMetrics.density
        val marginHoriz = 36f * density
        val marginVert = 60f * density
        val lineSpacing = 2.5f * density

        // 配置布局引擎
        layoutEngine.configure(
            width = width,
            height = height,
            fontSizePx = fontSizePx,
            lineSpacingPx = lineSpacing,
            lineSpacingMult = 1.1f,
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
