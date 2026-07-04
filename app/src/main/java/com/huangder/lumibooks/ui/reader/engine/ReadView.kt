package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.FrameLayout
import com.huangder.lumibooks.domain.model.Note
import kotlin.math.abs

/**
 * 核心阅读视图。
 *
 * FrameLayout 包含 3 个 [PageContentView] 槽位，使用 [PageAnimationController]
 * 管理翻页动画。文字选择由系统 TextView 原生处理。
 */
/**
 * 选区信息快照，供 Compose 层自定义菜单使用。
 *
 * @param selectedText        选中的文本
 * @param chapterIndex        所在章节索引
 * @param chapterStartOffset  本页在章节中的字符起始偏移（用于计算章节级坐标）
 * @param pageStart           页面内选区起始字符偏移
 * @param pageEnd             页面内选区结束字符偏移
 * @param selTopY             选区顶边屏幕 Y 坐标（px）
 * @param selBottomY          选区底边屏幕 Y 坐标（px）
 * @param selStartX           选区起点屏幕 X 坐标（px）
 * @param selEndX             选区终点屏幕 X 坐标（px）
 */
data class SelectionInfo(
    val selectedText: String,
    val chapterIndex: Int,
    val chapterStartOffset: Int,
    val pageStart: Int,
    val pageEnd: Int,
    val selTopY: Float,
    val selBottomY: Float,
    val selStartX: Float,
    val selEndX: Float
)

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

    // ── 触摸分类追踪（ReadView 层统一拦截） ──
    private var rvTouchStartX = 0f
    private var rvTouchStartY = 0f
    private var rvTouchDownTime = 0L
    private var rvHasMoved = false
    private var rvIsEdgeTouch = false

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
            clearCurrentSelection()
            slotManager.getPrevSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.PREV)
                }
            }
        }
        animationController.onTapCenter = {
            clearCurrentSelection()
            callbacks?.onMenuToggle()
        }
        animationController.onTapRight = {
            clearCurrentSelection()
            slotManager.getNextSlot().let { slot ->
                if (slot.isLoaded) {
                    startTapAnimation(PageAnimationController.Direction.NEXT)
                }
            }
        }

        // 🔥 长按回调保留（边缘长按时触发），执行程序化选词
        animationController.onLongPress = { x, y ->
            Log.d(TAG, "onLongPress triggered at x=$x y=$y")
            val result = curPageView.selectWordAt(x, y)
            if (result != null) {
                val (pageStart, pageEnd, text) = result
                Log.d(TAG, "selected text=\"$text\" pageOffsets=($pageStart, $pageEnd)")
            } else {
                Log.w(TAG, "selectWordAt returned null at x=$x y=$y")
            }
        }

        // 🔥 为三个页面槽位设置原生选择 ActionMode 回调 + 选区检测
        setupNativeSelectionActionMode(prevPageView)
        setupNativeSelectionActionMode(curPageView)
        setupNativeSelectionActionMode(nextPageView)
        // SpanWatcher 在每次文本设置后注册（因为 setPageContent 创建新 Spannable）
        setupSelectionWatcher(prevPageView)
        setupSelectionWatcher(curPageView)
        setupSelectionWatcher(nextPageView)

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
        val (bgColor, textColor, accentColor) = getThemeColors(currentTheme)
        val density = resources.displayMetrics.density
        val marginHoriz = currentMarginHorizDp * density
        val marginVert = currentMarginVertDp * density
        val marginTop = marginVert + 6f * density   // 顶部微调
        val lineSpacingExtra = 2.5f * density

        // 选择高亮色jian
        // = accent + 25% alpha
        val highlightColor = (accentColor and 0x00FFFFFF) or 0x40000000.toInt()

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
                lineSpacingExtraPx = lineSpacingExtra,
                letterSpacingPx = currentLetterSpacingDp * density,
                typeface = customTypeface,
                marginLeftPx = marginHoriz,
                marginTopPx = marginTop,
                marginRightPx = marginHoriz,
                marginBottomPx = marginVert,
                highlightColor = highlightColor,
                accentColor = accentColor
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

        val (_, textColor, _) = getThemeColors(theme)
        val density = resources.displayMetrics.density
        val marginHoriz = marginHorizDp * density
        val marginVert = marginVertDp * density
        val marginTop = marginVert + 6f * density   // 顶部微调：补偿 includeFontPadding 的视觉不对称
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
            marginTopPx = marginTop,
            marginBottomPx = marginVert,
            textColor = textColor,
            chapterCount = chapterCount
        )

        configureCurrentPageView()

        // 🔥 共用 TextPaint：让 PageLayoutEngine 的 StaticLayout 使用与 TextView 完全相同的 Paint
        // 对象，消除两个引擎的字体度量（font metrics）差异。这是根除分页不一致的关键。
        layoutEngine.sharedTextPaint = curPageView.textView.paint

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

    /**
     * 🔥 统一触摸分类器（在所有子 View 之前执行）。
     *
     * 解决 setTextIsSelectable(true) 导致的触摸事件分发问题：
     * - TextView 消费触摸 → ReadView.onTouchEvent 不触发 → 菜单/翻页失效
     * - PageContentView 拦截后事件丢失 → PageAnimationController late-init 死代码
     *
     * 分类逻辑：
     * - 全区域 DOWN → 不拦截，穿透给 TextView（支持任意位置长按选词）
     * - 水平 MOVE（500ms内）→ 拦截，PageAnimationController late-init 处理翻页
     * - 边缘短 UP → 触发 animationController.onTapLeft/Right（点击翻页）
     * - 中间短 UP → 触发 callbacks.onMenuToggle（菜单切换）
     * - 长按（>500ms 或无明显移动）→ 不拦截，TextView 原生触发选词
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rvTouchStartX = ev.x
                rvTouchStartY = ev.y
                rvTouchDownTime = System.currentTimeMillis()
                rvHasMoved = false
                val w = width.toFloat()
                rvIsEdgeTouch = w > 0 && (ev.x / w < 0.3f || ev.x / w > 0.7f)
                // 🔥 不拦截任何 DOWN，全部穿透给 TextView（支持全区域长按选词）
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - rvTouchStartX)
                val dy = abs(ev.y - rvTouchStartY)
                val dt = System.currentTimeMillis() - rvTouchDownTime

                if (dx > 24f || dy > 24f) rvHasMoved = true

                // 仅在 500ms 窗口内拦截水平滑动（超过 500ms 视为选择扩展，不拦截）
                // 放宽条件：dx > 16f && dx > dy * 0.5f（允许一定垂直分量）
                if (dt < 500L && dx > 16f && dx > dy * 0.5f) {
                    Log.d(TAG, "Intercept swipe at dx=$dx dy=$dy dt=$dt")
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP -> {
                if (!rvHasMoved && System.currentTimeMillis() - rvTouchDownTime < 300L) {
                    if (rvIsEdgeTouch) {
                        // 边缘短按 → 点击翻页（复用 animationController 回调）
                        Log.d(TAG, "Edge tap at x=${ev.x} → page turn")
                        if (rvTouchStartX / width < 0.3f) {
                            animationController.onTapLeft?.invoke()
                        } else {
                            animationController.onTapRight?.invoke()
                        }
                    } else {
                        // 中间短按 → 菜单切换
                        Log.d(TAG, "Center tap detected → toggle menu")
                        clearCurrentSelection()
                        callbacks?.onMenuToggle()
                    }
                }
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                return false
            }
        }
        return false
    }

    /**
     * 🔥 忽略触摸开始 500ms 内的 disallow 请求。
     *
     * setTextIsSelectable(true) 的 TextView 可能在触摸后很快通过 Editor
     * 调用 requestDisallowInterceptTouchEvent(true)，阻止父 View 拦截滑动。
     * 我们在 500ms 窗口内忽略此请求，确保滑动翻页正常；
     * 500ms 后（长按已触发），允许 disallow 以支持选择拖拽。
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            val dt = System.currentTimeMillis() - rvTouchDownTime
            if (dt < 500L) {
                // 忽略早期的 disallow 请求（插入点光标控制器可能触发）
                return
            }
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
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

    /** @return Triple(backgroundColor, textColor, accentColor) */
    private fun getThemeColors(theme: String): Triple<Int, Int, Int> {
        return when (theme) {
            "night" -> Triple(0xFF1a1a1a.toInt(), 0xFFCCCCCC.toInt(), 0xFF4A90D9.toInt())
            "sepia" -> Triple(0xFFf5e6d3.toInt(), 0xFF4a3728.toInt(), 0xFFC77826.toInt())
            "green" -> Triple(0xFFe8f5e9.toInt(), 0xFF2e7d32.toInt(), 0xFF2E7D32.toInt())
            else   -> Triple(0xFFFBFBFC.toInt(), 0xFF333333.toInt(), 0xFF007AFF.toInt())
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

    // ── 选区工具方法 ──

    /**
     * 获取当前页选区的完整信息（供 Compose 层自定义菜单使用）。
     * @return null 表示当前无选区
     */
    fun getSelectionInfo(): SelectionInfo? {
        val tv = curPageView.textView
        val layout = tv.layout ?: return null
        val spannable = tv.text as? android.text.Spannable ?: return null
        val selStart = android.text.Selection.getSelectionStart(spannable)
        val selEnd = android.text.Selection.getSelectionEnd(spannable)
        if (selStart < 0 || selEnd <= selStart) return null

        val text = spannable.toString().substring(selStart, selEnd)
        val curSlot = slotManager.getCurSlot()
        val chapterIdx = curSlot.chapterIndex
        val chapterStartOffset = curPageView.chapterStartOffset

        val startLine = layout.getLineForOffset(selStart)
        val endLine = layout.getLineForOffset(selEnd.coerceAtMost(spannable.length - 1))
        val topY = (tv.top + tv.paddingTop + layout.getLineTop(startLine)).toFloat()
        val bottomY = (tv.top + tv.paddingTop + layout.getLineBottom(endLine)).toFloat()
        val startX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selStart)
        val endX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selEnd)

        return SelectionInfo(
            selectedText = text,
            chapterIndex = chapterIdx,
            chapterStartOffset = chapterStartOffset,
            pageStart = selStart,
            pageEnd = selEnd,
            selTopY = topY,
            selBottomY = bottomY,
            selStartX = startX,
            selEndX = endX
        )
    }

    /** 清除当前页的选区 */
    private fun clearCurrentSelection() {
        curPageView.clearSelection()
    }

    // ── 原生选择 ActionMode ──

    /**
     * 为 PageContentView 的 TextView 设置 customSelectionActionModeCallback。
     * onCreateActionMode 返回 false → 系统不创建 ActionMode → 浮动工具栏不显示。
     * 选区完全由系统原生管理，不受 ActionMode 生命周期干扰。
     * Compose 层通过 SpanWatcher + onSelectionActionModeStarted 检测选区。
     */
    private fun setupNativeSelectionActionMode(pageView: PageContentView) {
        pageView.textView.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // 🔥 返回 true → 系统创建 ActionMode → 选区保留（MIUI 在 false 时会清除选区）
                // 同步清空菜单项 + 立即隐藏 + 异步多次隐藏，彻底压制系统浮动工具栏
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    menu?.clear()
                    mode?.hide(Long.MAX_VALUE)
                }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // 🔥 拖拽结束时系统重新调用 onPrepareActionMode（刷新工具栏）
                menu?.clear()
                mode?.hide(Long.MAX_VALUE)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    menu?.clear()
                    mode?.hide(Long.MAX_VALUE)
                }
                return true
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
    }

    /**
     * 在每次文本设置后注册 SpanWatcher，检测选区变化。
     * 因为 setPageContent 创建新的 SpannableStringBuilder，旧 watcher 会丢失。
     */
    private fun setupSelectionWatcher(pageView: PageContentView) {
        pageView.onTextSet = { sp ->
            sp.setSpan(object : android.text.SpanWatcher {
                private fun checkSelection(s: android.text.Spannable) {
                    val start = android.text.Selection.getSelectionStart(s)
                    val end = android.text.Selection.getSelectionEnd(s)
                    if (start >= 0 && end > start) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callbacks?.onSelectionStarted()
                        }
                    }
                }
                override fun onSpanChanged(s: android.text.Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
                    if (what === android.text.Selection.SELECTION_START || what === android.text.Selection.SELECTION_END) {
                        checkSelection(s)
                    }
                }
                override fun onSpanAdded(s: android.text.Spannable, what: Any, start: Int, end: Int) {
                    if (what === android.text.Selection.SELECTION_START || what === android.text.Selection.SELECTION_END) {
                        checkSelection(s)
                    }
                }
                override fun onSpanRemoved(s: android.text.Spannable, what: Any, start: Int, end: Int) {}
            }, 0, sp.length, android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

}

/** 简单的四元组（避免依赖 kotlin Pair 的三元组包装） */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
