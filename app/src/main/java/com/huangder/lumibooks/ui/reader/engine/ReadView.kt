package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import android.text.Selection
import android.text.Spannable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPageLocation
import com.huangder.lumibooks.util.ChineseConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal fun calculateReaderVerticalBalanceOffset(
    availableHeightPx: Float,
    lineHeightPx: Float,
    maxShiftPx: Float
): Float {
    if (availableHeightPx <= 0f || lineHeightPx <= 0f || maxShiftPx <= 0f) return 0f
    val completeLines = (availableHeightPx / lineHeightPx).toInt()
    if (completeLines <= 0) return 0f
    val remainder = availableHeightPx - completeLines * lineHeightPx
    return (remainder / 2f).coerceIn(0f, maxShiftPx)
}

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

data class ReaderTextAnchor(
    val chapterIndex: Int,
    val characterOffset: Int
)

/**
 * 跨页选择起始信息：用户在第 A 页开始选择，翻页到第 B 页继续扩展选区时，
 * 用此记录第 A 页的选区起止。
 *
 * @param startPageStart 第一页的页面内选区起始偏移
 * @param startPageEnd   第一页的页面内选区结束偏移
 * @param startText      第一页选中的文本
 */
data class TtsHighlightRange(
    val chapterIndex: Int,
    val start: Int,
    val end: Int
)

data class CrossPageSelectionState(
    val startChapterIndex: Int,
    val startChapterOffset: Int,
    val startPageStart: Int,
    val startPageEnd: Int,
    val startText: String,
    val startSelTopY: Float,
    val startSelBottomY: Float,
    val startSelStartX: Float,
    val startSelEndX: Float,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null
)

class ReadView(context: Context, externalLayoutEngine: PageLayoutEngine? = null) : FrameLayout(context) {

    companion object {
        private const val TAG = "ReadView"
        private const val JUMP_SETTLE_DELAY_MS = 120L
    }

    // ── 子组件 ──
    val layoutEngine: PageLayoutEngine = externalLayoutEngine ?: PageLayoutEngine()
    private val ownsLayoutEngine: Boolean = externalLayoutEngine == null
    lateinit var slotManager: PageSlotManager
        private set
    lateinit var animationController: PageAnimationController
        private set

    /** 当前实际生效的双页对开状态（供 Compose 层上报内容宽度等） */
    val isTwoPageSpreadActive: Boolean get() = currentTwoPageSpread

    // ── 3 个整屏跨页容器（动画控制器操作对象） ──
    private val prevSpreadView = FrameLayout(context).apply { clipChildren = false }
    private val curSpreadView = FrameLayout(context).apply { clipChildren = false }
    private val nextSpreadView = FrameLayout(context).apply { clipChildren = false }

    // ── 页槽：左半页 + 右半页（单页模式只用左半页） ──
    val prevPageView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }
    val curPageView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }
    val nextPageView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }
    private val prevPageRightView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }
    private val curPageRightView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }
    private val nextPageRightView = PageContentView(context).apply { onSelectionReachEnd = { handleSelectionReachEnd() } }

    // ── 中缝遮挡条：翻页动画时下层页面从缝隙透出，用阅读背景色盖住 ──
    private val prevGutterView = View(context)
    private val curGutterView = View(context)
    private val nextGutterView = View(context)

    private val animationSurface by lazy {
        PageAnimationSurface(
            root = this,
            prevPageView = prevSpreadView,
            curPageView = curSpreadView,
            nextPageView = nextSpreadView,
            backgroundColorProvider = { bgColor },
            reversePageProgressProvider = { currentWritingMode.isVertical }
        )
    }

    // ── 外部回调 ──
    private var callbacks: ReadViewCallbacks? = null
    private var contentProvider: (suspend (Int) -> CharSequence?)? = null

    private var savedNotes: List<Note> = emptyList()
    private data class SearchHighlight(val chapterIndex: Int, val start: Int, val end: Int)
    private var searchHighlight: SearchHighlight? = null
    private var searchHighlightAnimator: ValueAnimator? = null
    private var pendingJumpChapter: Int? = null
    private var isJumpSettling = false

    /** 设置已保存的笔记/高亮并刷新当前页。 */
    fun setSavedNotes(notes: List<Note>) {
        // 🔥 引用相同或内容相同则跳过，防止 Compose recomposition 触发无意义的页面刷新
        if (notes === savedNotes) return
        if (notes == savedNotes) return
        savedNotes = notes
        // 鍒锋柊鎵€鏈夊凡鍔犺浇妲戒綅锛堝寘鍚缁勮繃椤电殑绗竴椤碉級锛岄槻姝㈣法椤甸珮浜彧鏄剧ず鍦ㄥ綋鍓嶉〉
        slotManager.refreshAllHighlights()
    }

    // ── 触摸分类追踪（ReadView 层统一拦截） ──
    private var rvTouchStartX = 0f
    private var rvTouchStartY = 0f
    private var rvTouchDownTime = 0L
    private var rvHasMoved = false
    private var rvIsEdgeTouch = false
    private var rvIsHandlingPageGesture = false
    private var rvPendingImageLongPress: ReaderImageHit? = null
    private var rvPendingImageView: PageContentView? = null
    private var rvImageLongPressHandled = false
    private val rvImageLongPressRunnable = Runnable { handlePendingImageLongPress() }

    // ── 配置状态 ──
    private var isConfigured = false
    private var currentFontSizePx: Float = 56f
    private var currentTheme: String = "day"
    private var currentChapterCount: Int = 0
    private var currentLineHeightMult: Float = 1.5f
    private var currentLetterSpacingDp: Float = 0f
    private var currentFontType: String = "system"
    private var currentCustomFontPath: String? = null
    private var currentMarginLeftDp: Float = 38f
    private var currentMarginRightDp: Float = 38f
    private var currentMarginTopDp: Float = 64f
    private var currentMarginBottomDp: Float = 64f
    private var currentTopOverlayInsetDp: Float = 0f
    private var currentBottomOverlayInsetDp: Float = 0f
    private var currentParagraphSpacingDp: Float = 0f
    private var currentBionicReadingEnabled: Boolean = false
    private var currentUseDisplayDensityForSpans: Boolean = false
    private var currentChineseMode: String = "original"
    private var currentPageTransition: String = "slide"
    private var currentEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    private var currentWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL
    /** 双页开关（不含方向）：设备/设置允许时 true，是否启用由实测宽高决定 */
    private var currentTwoPageSpreadEnabled: Boolean = false
    /** 当前实际生效的双页模式（= 开关 && 横屏） */
    private var currentTwoPageSpread: Boolean = false
    private var currentReaderBackgroundColor: Int? = null
    private var currentReaderBackgroundImagePath: String? = null
    private var currentReaderTextColor: Int? = null
    private var pendingStartChapter: Int = 0
    private var pendingStartPage: Int = 0
    private var configuredWidth: Int = 0
    private var configuredHeight: Int = 0

    // ── 跨页选择 ──
    /** 跨页选择状态：用户在第 A 页选择后翻页，在第 B 页继续选择时合并两页选区 */
    var crossPageSelection: CrossPageSelectionState? = null
        private set
    private var pendingRebuildSelection = false

    /** 褰撳墠鍙ュ彞 TTS 楂樹寒鑼冨洿锛堝叏灞€绔犺妭鍐呭亸绉伙級锛屼负绌哄垯涓嶆樉绀?*/
    var ttsHighlightRange: TtsHighlightRange? = null

    /** 当前阅读背景色（供 CurlPageAnim 背面绘制使用）。 */
    var bgColor: Int = 0xFFFBFBFC.toInt()
        private set

    init {
        isClickable = true
        isFocusable = true
        // 🔥 禁用裁剪：翻页动画需要子 View 在屏幕外绘制（左右滑动时上/下一页在屏幕外）
        clipChildren = false
        clipToPadding = false

        // 每个跨页容器内放左/右半页；单页模式下右半页隐藏
        fun addHalfPages(
            container: FrameLayout,
            gutter: View,
            left: PageContentView,
            right: PageContentView
        ) {
            container.addView(gutter, LayoutParams(0, LayoutParams.MATCH_PARENT))
            gutter.visibility = View.GONE
            container.addView(left, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            container.addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT))
            right.visibility = View.GONE
        }
        addHalfPages(prevSpreadView, prevGutterView, prevPageView, prevPageRightView)
        addHalfPages(curSpreadView, curGutterView, curPageView, curPageRightView)
        addHalfPages(nextSpreadView, nextGutterView, nextPageView, nextPageRightView)
        addView(prevSpreadView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curSpreadView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nextSpreadView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 初始化管理器
        slotManager = PageSlotManager(
            layoutEngine,
            prevPageView,
            curPageView,
            nextPageView,
            prevPageRightView,
            curPageRightView,
            nextPageRightView,
            spreadEnabled = { currentTwoPageSpread }
        )
        slotManager.contentProvider = { chapterIndex ->
            contentProvider?.invoke(chapterIndex)
        }
        slotManager.highlightProvider = { chapterIndex ->
            buildHighlights(chapterIndex)
        }

        // 初始化动画控制器
        animationController = SlidePageAnim(animationSurface)

        animationController.onCanFlip = { dir ->
            if (isJumpSettling) false else when (dir) {
                PageAnimationController.Direction.NEXT -> {
                    val next = slotManager.getNextSlot()
                    next.isLoaded
                }
                PageAnimationController.Direction.PREV -> {
                    val cur = slotManager.getCurSlot()
                    val isBookFirstPage = cur.chapterIndex == 0 && cur.pageIndex == 0
                    if (isBookFirstPage) false else {
                        val prev = slotManager.getPrevSlot()
                        prev.isLoaded
                    }
                }
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
            performEdgeTap(effectiveEdgeTapAction(currentEdgeTapMode.leftAction))
        }
        animationController.onTapCenter = {
            clearCurrentSelection()
            callbacks?.onMenuToggle()
        }
        animationController.onTapRight = {
            performEdgeTap(effectiveEdgeTapAction(currentEdgeTapMode.rightAction))
        }

        // 🔥 长按回调保留（边缘长按时触发），执行程序化选词
        animationController.onLongPress = { x, y ->
            Log.d(TAG, "onLongPress triggered at x=$x y=$y")
            val hitView = pageViewAt(x, y) ?: curPageView
            val result = hitView.selectWordAt(x - hitView.left, y - hitView.top)
            if (result != null) {
                val (pageStart, pageEnd, text) = result
                Log.d(TAG, "selected text=\"$text\" pageOffsets=($pageStart, $pageEnd)")
            } else {
                Log.w(TAG, "selectWordAt returned null at x=$x y=$y")
            }
        }

        // 🔥 为全部页面槽位设置选区检测 + 压制系统浮动工具栏
        // SpanWatcher 在每次文本设置后注册（因为 setPageContent 创建新 Spannable）
        for (pageView in listOf(
            prevPageView, curPageView, nextPageView,
            prevPageRightView, curPageRightView, nextPageRightView
        )) {
            setupSelectionWatcher(pageView)
            pageView.suppressSystemToolbar()
        }

        // 翻页后刷新高亮
        slotManager.onPageChangedCallback = { globalPage, chapterIdx, pageInChapter, chapterTotal ->
            callbacks?.onPageChanged(globalPage, chapterIdx, pageInChapter, chapterTotal)
            startSearchHighlightAnimationIfReady(chapterIdx)
            if (isJumpSettling && pendingJumpChapter == chapterIdx && chapterTotal > 0) {
                postDelayed({
                    if (pendingJumpChapter == chapterIdx) {
                        pendingJumpChapter = null
                        isJumpSettling = false
                    }
                }, JUMP_SETTLE_DELAY_MS)
            }
            configureCurrentPageView()
            invalidate()
        }
        slotManager.onSpreadPageChangedCallback = { rightGlobalPage, rightChapterIdx, rightPage ->
            callbacks?.onSpreadPageChanged(rightGlobalPage, rightChapterIdx, rightPage)
        }

        setWillNotDraw(false)
    }

    /** 构建某章的高亮列表（savedNotes → Triple(start, end, color)） */
    private fun buildHighlights(chapterIndex: Int): List<Triple<Int, Int, Int>> {
        val savedHighlights = savedNotes
            .filter { it.chapterIndex == chapterIndex }
            .map { note ->
                if (note.type == "underline") {
                    val color = try {
                        android.graphics.Color.parseColor(note.color)
                    } catch (_: IllegalArgumentException) {
                        0xFF333333.toInt()
                    }
                    Triple(note.startPosition, note.endPosition, (PageContentView.UNDERLINE_FLAG shl 24) or (color and 0x00FFFFFF))
                } else {
                    val color = try {
                        android.graphics.Color.parseColor(note.color)
                    } catch (_: IllegalArgumentException) {
                        0x40FFEB3B.toInt()
                    }
                    Triple(note.startPosition, note.endPosition, color)
                }
            }
        val transientHighlight = searchHighlight
            ?.takeIf { it.chapterIndex == chapterIndex }
            ?.let { Triple(it.start, it.end, 0x00FFE082) }
        val ttsHighlight = ttsHighlightRange
            ?.takeIf { it.chapterIndex == chapterIndex }
            ?.let { Triple(it.start, it.end, PageContentView.TTS_HIGHLIGHT_RGB) }
        return buildList { addAll(savedHighlights); if (transientHighlight != null) add(transientHighlight); if (ttsHighlight != null) add(ttsHighlight) }
    }

    /** 配置所有 PageContentView 的 TextView 样式（防止翻页错版） */
    private fun configureCurrentPageView() {
        val (themeBgColor, themeTextColor, accentColor) = getThemeColors(currentTheme)
        val bgColor = currentReaderBackgroundColor ?: themeBgColor
        val textColor = currentReaderTextColor ?: themeTextColor
        val density = resources.displayMetrics.density
        val marginLeft = currentMarginLeftDp * density
        val marginRight = currentMarginRightDp * density
        val gutterMargin = if (currentTwoPageSpread) {
            (currentMarginLeftDp.coerceAtMost(currentMarginRightDp) / 2f).coerceAtLeast(12f) * density
        } else {
            marginLeft
        }
        val baseMarginTop = (currentMarginTopDp + currentTopOverlayInsetDp) * density
        val baseMarginBottom = (currentMarginBottomDp + currentBottomOverlayInsetDp) * density
        val lineSpacingExtra = 2.5f * density

        // 选择高亮色jian
        // = accent + 25% alpha
        val highlightColor = (accentColor and 0x00FFFFFF) or 0x40000000.toInt()

        val customTypeface = when {
            currentFontType == "serif" -> android.graphics.Typeface.SERIF
            currentFontType == "fangsong" -> DownloadedFonts.typeface(context, "fangsong")
                ?: android.graphics.Typeface.DEFAULT
            currentFontType == "kaiti" -> try { androidx.core.content.res.ResourcesCompat.getFont(context, com.huangder.lumibooks.R.font.lxgw_wenkai) }
                catch (_: Exception) { null } ?: android.graphics.Typeface.DEFAULT
            currentFontType.startsWith("custom") -> {
                val path = currentCustomFontPath
                if (path != null) try { android.graphics.Typeface.createFromFile(java.io.File(path)) }
                    catch (_: Exception) { android.graphics.Typeface.DEFAULT }
                else android.graphics.Typeface.DEFAULT
            }
            else -> android.graphics.Typeface.DEFAULT
        }
        // 全部槽位都配置，确保翻页时样式一致；双页时左/右半页镜像边距
        for ((left, right) in listOf(
            prevPageView to prevPageRightView,
            curPageView to curPageRightView,
            nextPageView to nextPageRightView
        )) {
            left.configure(
                fontSizePx = currentFontSizePx,
                textColor = textColor,
                lineHeightMult = currentLineHeightMult,
                lineSpacingExtraPx = lineSpacingExtra,
                letterSpacingPx = currentLetterSpacingDp * density,
                typeface = customTypeface,
                marginLeftPx = marginLeft,
                marginTopPx = baseMarginTop,
                marginRightPx = if (currentTwoPageSpread) gutterMargin else marginRight,
                marginBottomPx = baseMarginBottom,
                highlightColor = highlightColor,
                accentColor = accentColor,
                writingMode = currentWritingMode
            )
            right.configure(
                fontSizePx = currentFontSizePx,
                textColor = textColor,
                lineHeightMult = currentLineHeightMult,
                lineSpacingExtraPx = lineSpacingExtra,
                letterSpacingPx = currentLetterSpacingDp * density,
                typeface = customTypeface,
                marginLeftPx = if (currentTwoPageSpread) gutterMargin else marginLeft,
                marginTopPx = baseMarginTop,
                marginRightPx = marginRight,
                marginBottomPx = baseMarginBottom,
                highlightColor = highlightColor,
                accentColor = accentColor,
                writingMode = currentWritingMode
            )
            left.setReaderBackground(bgColor, currentReaderBackgroundImagePath)
            right.setReaderBackground(bgColor, currentReaderBackgroundImagePath)
        }
        setBackgroundColor(bgColor)
        prevGutterView.setBackgroundColor(bgColor)
        curGutterView.setBackgroundColor(bgColor)
        nextGutterView.setBackgroundColor(bgColor)
        this.bgColor = bgColor
    }

    fun setReaderBackground(backgroundColor: Int, textColor: Int, imagePath: String?) {
        if (currentReaderBackgroundColor == backgroundColor &&
            currentReaderTextColor == textColor &&
            currentReaderBackgroundImagePath == imagePath
        ) return

        animationController.abortAnim()
        currentReaderBackgroundColor = backgroundColor
        currentReaderTextColor = textColor
        currentReaderBackgroundImagePath = imagePath
        configureCurrentPageView()
        invalidate()
    }

    // ── 配置 ──

    fun setCallbacks(cbs: ReadViewCallbacks) {
        callbacks = cbs
    }

    fun setContentProvider(provider: suspend (Int) -> CharSequence?) {
        slotManager.clearContentCache()
        val formattedProvider: suspend (Int) -> CharSequence? = { chapterIndex ->
            provider(chapterIndex)?.let { content ->
                BionicReadingFormatter.format(content, currentBionicReadingEnabled)
            }
        }
        contentProvider = formattedProvider
        slotManager.contentProvider = formattedProvider
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
        customFontPath: String? = null,
        marginLeftDp: Float = 38f,
        marginRightDp: Float = 38f,
        marginTopDp: Float = 64f,
        marginBottomDp: Float = 64f,
        topOverlayInsetDp: Float = 0f,
        bottomOverlayInsetDp: Float = 0f,
        paragraphSpacingDp: Float = 2f,
        bionicReadingEnabled: Boolean = false,
        useDisplayDensityForSpans: Boolean = false,
        writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
        twoPageSpread: Boolean = false,
        width: Int = this.width,
        height: Int = this.height
    ) {
        if (width <= 0 || height <= 0 || chapterCount <= 0) {
            // 仅在拿到真实起始位置时更新锚点；书籍尚未加载（chapterCount<=0）
            // 或起始章节无效时保留上一次的有效锚点，避免用 0 覆盖真实进度。
            if (chapterCount > 0 && startChapter >= 0) {
                pendingStartChapter = startChapter
                pendingStartPage = startPage
            }
            currentFontSizePx = fontSizePx
            currentTheme = theme
            currentChapterCount = chapterCount
            currentLineHeightMult = lineHeightMult
            currentLetterSpacingDp = letterSpacingDp
            currentFontType = fontType
            currentCustomFontPath = customFontPath
            currentMarginLeftDp = marginLeftDp
            currentMarginRightDp = marginRightDp
            currentMarginTopDp = marginTopDp
            currentMarginBottomDp = marginBottomDp
            currentTopOverlayInsetDp = topOverlayInsetDp
            currentBottomOverlayInsetDp = bottomOverlayInsetDp
            currentParagraphSpacingDp = paragraphSpacingDp
            currentBionicReadingEnabled = bionicReadingEnabled
            currentUseDisplayDensityForSpans = useDisplayDensityForSpans
            currentWritingMode = writingMode
            currentTwoPageSpreadEnabled = twoPageSpread
            currentTwoPageSpread = twoPageSpread && width > height
            return
        }

        val themeChanged = currentTheme != theme
        val chapterCountChanged = currentChapterCount != chapterCount
        val fontSizeChanged = Math.abs(currentFontSizePx - fontSizePx) > 0.5f
        val lineHeightChanged = Math.abs(currentLineHeightMult - lineHeightMult) > 0.01f
        val letterSpacingChanged = Math.abs(currentLetterSpacingDp - letterSpacingDp) > 0.05f
        val fontTypeChanged = currentFontType != fontType
        val customFontPathChanged = currentCustomFontPath != customFontPath
        val marginChanged = Math.abs(currentMarginLeftDp - marginLeftDp) > 0.5f ||
            Math.abs(currentMarginRightDp - marginRightDp) > 0.5f ||
            Math.abs(currentMarginTopDp - marginTopDp) > 0.5f ||
            Math.abs(currentMarginBottomDp - marginBottomDp) > 0.5f
        val overlayInsetChanged = Math.abs(currentTopOverlayInsetDp - topOverlayInsetDp) > 0.5f ||
            Math.abs(currentBottomOverlayInsetDp - bottomOverlayInsetDp) > 0.5f
        val paragraphSpacingChanged = Math.abs(currentParagraphSpacingDp - paragraphSpacingDp) > 0.01f
        val bionicReadingChanged = currentBionicReadingEnabled != bionicReadingEnabled
        val paginationLayoutChanged =
            currentUseDisplayDensityForSpans != useDisplayDensityForSpans
        val writingModeChanged = currentWritingMode != writingMode
        val resolvedSpread = twoPageSpread && width > height
        val spreadModeChanged = currentTwoPageSpread != resolvedSpread
        val writingModeAnchor = if (writingModeChanged) {
            slotManager.getCurSlot().takeIf { it.isLoaded }?.contentView?.chapterStartOffset
        } else {
            null
        }
        if (writingModeChanged) animationController.abortAnim()
        val sizeChanged = !isConfigured || configuredWidth != width || configuredHeight != height
        val needsRelayout = themeChanged || chapterCountChanged || fontSizeChanged || lineHeightChanged ||
                letterSpacingChanged || fontTypeChanged || customFontPathChanged || marginChanged ||
                overlayInsetChanged || paragraphSpacingChanged || bionicReadingChanged ||
                paginationLayoutChanged || writingModeChanged || spreadModeChanged || sizeChanged
        // 旋转/进出双页/窗口 resize（含 ColorOS 小窗）时，重新分页必须基于“当前真实槽位”的章与页，
        // 而不是 onLayout 传进来的过期 pendingStartChapter/pendingStartPage，
        // 否则整本书会跳回第 1 页（锚点只修正章内页码，不修正章节）。
        // 槽位在异步加载中（isLoaded=false）时 chapterIndex/pageIndex 仍是本次目标位置，
        // 必须继续作为锚点；否则连续 resize 期间会回退到 startChapter/pendingStartChapter
        // （初始化早期可能为 0），把阅读进度清零。
        val relayoutToCurrent = sizeChanged || spreadModeChanged
        val curSlotForRelayout = slotManager.getCurSlot()
        val hasCurrentAnchor = curSlotForRelayout.chapterIndex >= 0
        val effectiveStartChapter = if (relayoutToCurrent && hasCurrentAnchor) {
            curSlotForRelayout.chapterIndex
        } else {
            startChapter
        }
        val effectiveStartPage = if (relayoutToCurrent && hasCurrentAnchor) {
            curSlotForRelayout.pageIndex.coerceAtLeast(0)
        } else {
            startPage
        }

        // 🔥 无变化时提前返回，避免菜单切换等 recomposition 触发不必要的重配置
        if (isConfigured && !needsRelayout) {
            val currentSlot = slotManager.getCurSlot()
            if (startChapter >= 0 &&
                (currentSlot.chapterIndex != startChapter || currentSlot.pageIndex != startPage)
            ) {
                jumpToChapter(startChapter, startPage)
            }
            return
        }

        currentFontSizePx = fontSizePx
        currentTheme = theme
        currentChapterCount = chapterCount
        currentLineHeightMult = lineHeightMult
        currentLetterSpacingDp = letterSpacingDp
        currentFontType = fontType
        currentCustomFontPath = customFontPath
        currentMarginLeftDp = marginLeftDp
        currentMarginRightDp = marginRightDp
        currentMarginTopDp = marginTopDp
        currentMarginBottomDp = marginBottomDp
        currentTopOverlayInsetDp = topOverlayInsetDp
        currentBottomOverlayInsetDp = bottomOverlayInsetDp
        currentParagraphSpacingDp = paragraphSpacingDp
        currentBionicReadingEnabled = bionicReadingEnabled
        currentUseDisplayDensityForSpans = useDisplayDensityForSpans
        currentWritingMode = writingMode
        currentTwoPageSpreadEnabled = twoPageSpread
        currentTwoPageSpread = resolvedSpread
        pendingStartChapter = effectiveStartChapter
        pendingStartPage = effectiveStartPage
        if (writingModeChanged || spreadModeChanged) resetPageViewPositions()
        if (spreadModeChanged || sizeChanged) applyPageViewLayout()
        configuredWidth = width
        configuredHeight = height

        val (_, textColor, _) = getThemeColors(theme)
        val density = resources.displayMetrics.density
        val marginLeft = marginLeftDp * density
        val marginRight = marginRightDp * density
        val gutterMargin = if (resolvedSpread) {
            (marginLeftDp.coerceAtMost(marginRightDp) / 2f).coerceAtLeast(12f) * density
        } else {
            marginLeft
        }
        val gutterPx = if (resolvedSpread) (16f * density).toInt() else 0
        val pageWidth = if (resolvedSpread) {
            ((width - gutterPx) / 2).coerceAtLeast(1)
        } else {
            width
        }
        val baseMarginTop = (marginTopDp + topOverlayInsetDp) * density
        val baseMarginBottom = (marginBottomDp + bottomOverlayInsetDp) * density
        val lineSpacing = 2.5f * density
        val lsPx = letterSpacingDp * density

        val customTypeface = when {
            fontType == "serif" -> android.graphics.Typeface.SERIF
            fontType == "fangsong" -> DownloadedFonts.typeface(context, "fangsong")
            fontType == "kaiti" -> try { androidx.core.content.res.ResourcesCompat.getFont(context, com.huangder.lumibooks.R.font.lxgw_wenkai) }
                catch (_: Exception) { null }
            fontType.startsWith("custom") -> {
                val path = customFontPath
                if (path != null) try { android.graphics.Typeface.createFromFile(java.io.File(path)) }
                    catch (_: Exception) { null }
                else null
            }
            else -> null
        }

        layoutEngine.configure(
            width = pageWidth,
            height = height,
            fontSizePx = fontSizePx,
            lineSpacingPx = lineSpacing,
            lineSpacingMult = lineHeightMult,
            letterSpacingPx = lsPx,
            fontType = fontType,
            customTypeface = customTypeface,
            marginLeftPx = marginLeft,
            marginRightPx = if (resolvedSpread) gutterMargin else marginRight,
            marginTopPx = baseMarginTop,
            marginBottomPx = baseMarginBottom,
            textColor = textColor,
            chapterCount = chapterCount,
            useDisplayDensityForSpans = useDisplayDensityForSpans,
            writingMode = writingMode
        )

        configureCurrentPageView()

        // 🔥 共用 TextPaint：让 PageLayoutEngine 的 StaticLayout 使用与 TextView 完全相同的 Paint
        // 对象，消除两个引擎的字体度量（font metrics）差异。这是根除分页不一致的关键。
        layoutEngine.sharedTextPaint = curPageView.textView.paint

        if (needsRelayout) {
            slotManager.clearContentCache()
            if (writingModeChanged) {
                slotManager.pendingStartCharOffset = writingModeAnchor ?: -1
            }
            // 字号/旋转/模式变化前捕获当前内容位置，以便重新分页后修正页码
            if (fontSizeChanged || bionicReadingChanged || sizeChanged || spreadModeChanged) {
                val curSlot = slotManager.getCurSlot()
                if (curSlot.isLoaded) {
                    val primaryOffset = slotManager.getPrimaryContentView().chapterStartOffset
                    slotManager.pendingStartCharOffset =
                        primaryOffset.takeIf { it >= 0 } ?: curSlot.contentView.chapterStartOffset
                }
            }
            layoutEngine.invalidateAll()
        }

        if (!isConfigured || needsRelayout) {
            slotManager.setChapterCount(chapterCount)
            slotManager.initialize(effectiveStartChapter, effectiveStartPage)
            isConfigured = true
        }
    }

    /**
     * 强制重新分页并刷新当前页。
     * 用于段间距/首行缩进等文本内容变化后，
     * 需要在 configure() 之外单独触发的场景。
     */
    /** 鍙縅forceRelayout 杞伙細鍙己鍒堕噸鏂颁慨娓?SLOT_CUR 椤靛唴瀹癸紙甯︽渶鏂癶ighlight锛?*/
    fun refreshCurrentPage() {
        if (!isConfigured) return
        slotManager.refreshCurrent()
    }

    fun forceRelayout() {
        if (!isConfigured) return
        slotManager.clearContentCache()
        layoutEngine.invalidateAll()
        val curSlot = slotManager.getCurSlot()
        if (curSlot.chapterIndex >= 0) {
            slotManager.initialize(curSlot.chapterIndex, curSlot.pageIndex.coerceAtLeast(0))
        }
    }

    /** 跳转到指定章节指定页 */
    fun jumpToChapter(chapterIndex: Int, pageInChapter: Int = 0) {
        beginJumpSettling(chapterIndex)
        animationController.abortAnim()
        layoutEngine.invalidateChapter(chapterIndex)
        slotManager.jumpTo(chapterIndex, pageInChapter)
    }

    /**
     * 根据全书阅读进度百分比（0-100）跳转。
     * 按章节比例定位（不依赖全量页数缓存），若目标章节已布局则精确到页。
     */
    fun jumpToGlobalProgress(progressPercent: Float) {
        val chapterCount = layoutEngine.getChapterCount().takeIf { it > 0 } ?: return
        val rawPos = (progressPercent / 100f) * chapterCount
        val targetChapter = rawPos.toInt().coerceIn(0, chapterCount - 1)
        val chapterFraction = rawPos - targetChapter
        val chapterLayout = layoutEngine.getChapterLayout(targetChapter)
        val targetPage = if (chapterLayout != null && chapterLayout.totalPages > 0) {
            (chapterFraction * chapterLayout.totalPages).toInt()
                .coerceIn(0, chapterLayout.totalPages - 1)
        } else 0
        jumpToChapter(targetChapter, targetPage)
    }

    /** 跳转到章节内包含指定字符偏移的页面。 */
    fun jumpToCharacter(chapterIndex: Int, characterOffset: Int) {
        beginJumpSettling(chapterIndex)
        animationController.abortAnim()
        val targetOffset = characterOffset.coerceAtLeast(0)
        val cachedLayout = layoutEngine.getChapterLayout(chapterIndex)
        val cachedPage = cachedLayout?.pages?.indexOfFirst { page ->
            targetOffset >= page.startCharOffset && targetOffset < page.endCharOffset
        } ?: -1

        if (cachedPage >= 0) {
            slotManager.jumpTo(chapterIndex, cachedPage)
        } else {
            slotManager.pendingStartCharOffset = targetOffset
            layoutEngine.invalidateChapter(chapterIndex)
            slotManager.jumpTo(chapterIndex, 0)
        }
    }

    /** Opens the exact page containing a search match and flashes only that match twice. */
    fun jumpToSearchResult(chapterIndex: Int, startOffset: Int, matchLength: Int) {
        searchHighlightAnimator?.cancel()
        searchHighlight = SearchHighlight(
            chapterIndex = chapterIndex,
            start = startOffset.coerceAtLeast(0),
            end = (startOffset + matchLength).coerceAtLeast(startOffset + 1)
        )
        jumpToCharacter(chapterIndex, startOffset)
    }

    /** Returns the first visible character of the current page for a layout-independent bookmark. */
    fun getCurrentPageStartCharacterOffset(): Int? {
        val current = slotManager.getCurSlot()
        val primary = slotManager.getPrimaryContentView()
        return primary.chapterStartOffset.takeIf { current.isLoaded && it >= 0 }
    }

    /** Returns the first actually visible text glyph and its owning chapter. */
    fun getCurrentPageTextAnchor(): ReaderTextAnchor? {
        val current = slotManager.getCurSlot()
        if (!current.isLoaded || current.chapterIndex < 0) return null
        val characterOffset = slotManager.getPrimaryContentView().firstVisibleCharacterOffset() ?: return null
        return ReaderTextAnchor(current.chapterIndex, characterOffset)
    }

    fun getCurrentPageBookmarkTitle(): String? {
        return slotManager.getPrimaryContentView().textView.text
            ?.toString()
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(80)
    }

    /** 当前真实槽位位置，供书内链接保存返回点。 */
    fun getCurrentLocation(): Pair<Int, Int>? {
        val current = slotManager.getCurSlot()
        if (current.chapterIndex < 0 || current.pageIndex < 0) return null
        return current.chapterIndex to current.pageIndex
    }

    /** 查询下一页位置，不执行翻页。 */
    fun getNextPageLocation(): Pair<Int, Int> {
        val current = slotManager.getCurSlot()
        if (!current.isLoaded) return -1 to -1
        val layout = layoutEngine.getChapterLayout(current.chapterIndex) ?: return -1 to -1
        return when {
            current.pageIndex + 1 < layout.totalPages -> current.chapterIndex to current.pageIndex + 1
            current.chapterIndex + 1 < currentChapterCount -> current.chapterIndex + 1 to 0
            else -> -1 to -1
        }
    }

    /** 查询上一页位置，不执行翻页。 */
    fun getPrevPageLocation(): Pair<Int, Int> {
        val current = slotManager.getCurSlot()
        if (!current.isLoaded) return -1 to -1
        if (current.pageIndex > 0) return current.chapterIndex to current.pageIndex - 1
        if (current.chapterIndex <= 0) return -1 to -1
        val previousLayout = layoutEngine.getChapterLayout(current.chapterIndex - 1) ?: return -1 to -1
        return current.chapterIndex - 1 to previousLayout.totalPages - 1
    }

    /**
     * 返回指定页的纯文本与相邻位置。TTS 后台播放时也可通过该方法继续按当前排版分页。
     */
    suspend fun getTtsPageContent(chapterIndex: Int, pageIndex: Int): TtsPageContent? {
        if (chapterIndex !in 0 until currentChapterCount || pageIndex < 0) return null
        val provider = contentProvider ?: return null
        val fullText = withContext(Dispatchers.IO) { provider(chapterIndex) }
            ?.takeUnless { it.isEmpty() }
            ?: return null
        val chapterLayout = layoutEngine.layout(chapterIndex, fullText)
        val pageLayout = chapterLayout.pages.getOrNull(pageIndex) ?: return null

        var startOffset = pageLayout.startCharOffset
        while (startOffset < pageLayout.endCharOffset && fullText[startOffset] == '\n') {
            startOffset++
        }
        val rawPageText = if (startOffset < pageLayout.endCharOffset) {
            fullText.subSequence(startOffset, pageLayout.endCharOffset).toString()
        } else {
            ""
        }
        val pageText = ChineseConverter.convert(rawPageText, currentChineseMode)

        val previous = when {
            pageIndex > 0 -> TtsPageLocation(chapterIndex, pageIndex - 1)
            chapterIndex <= 0 -> null
            else -> {
                val previousText = withContext(Dispatchers.IO) { provider(chapterIndex - 1) }
                val previousLayout = previousText
                    ?.takeUnless { it.isEmpty() }
                    ?.let { layoutEngine.layout(chapterIndex - 1, it) }
                previousLayout?.takeIf { it.totalPages > 0 }?.let {
                    TtsPageLocation(chapterIndex - 1, it.totalPages - 1)
                }
            }
        }
        val next = when {
            pageIndex + 1 < chapterLayout.totalPages -> TtsPageLocation(chapterIndex, pageIndex + 1)
            chapterIndex + 1 < currentChapterCount -> TtsPageLocation(chapterIndex + 1, 0)
            else -> null
        }
        return TtsPageContent(
            location = TtsPageLocation(chapterIndex, pageIndex),
            text = pageText,
            previous = previous,
            next = next,
            startCharacterOffset = startOffset
        )
    }

    /** 设置简繁转换模式，刷新当前页 */
    fun setChineseMode(mode: String) {
        if (currentChineseMode == mode) return
        currentChineseMode = mode
        for (pageView in listOf(
            prevPageView, curPageView, nextPageView,
            prevPageRightView, curPageRightView, nextPageRightView
        )) {
            pageView.chineseMode = mode
        }
        slotManager.refreshCurrentPage()
    }

    /** 设置翻页动画类型 */
    fun setPageTransition(mode: String) {
        if (currentPageTransition == mode) return
        currentPageTransition = mode

        val oldController = animationController
        oldController.abortAnim()
        (oldController as? CurlPageAnim)?.destroy()

        val newController = when (mode) {
            "none", "instant", "no_animation" -> NoPageAnim(animationSurface)
            "fade" -> FadePageAnim(animationSurface)
            "scroll" -> ScrollPageAnim(animationSurface)
            "curl" -> CurlPageAnim(animationSurface)
            else -> SlidePageAnim(animationSurface)
        }
        // 重新绑定回调
        newController.onCanFlip = animationController.onCanFlip
        newController.onAnimationComplete = animationController.onAnimationComplete
        newController.onTapLeft = animationController.onTapLeft
        newController.onTapCenter = animationController.onTapCenter
        newController.onTapRight = animationController.onTapRight
        newController.onLongPress = animationController.onLongPress

        animationController = newController
        // 重置页面位置状态（防止切换时残留偏移）
        resetPageViewPositions()
        invalidate()
    }

    private fun resetPageViewPositions() {
        val pageWidth = width.toFloat()
        prevSpreadView.translationX = if (currentWritingMode.isVertical) pageWidth else -pageWidth
        prevSpreadView.translationY = 0f
        prevSpreadView.alpha = 0f
        curSpreadView.translationX = 0f
        curSpreadView.translationY = 0f
        curSpreadView.alpha = 1f
        nextSpreadView.translationX = if (currentWritingMode.isVertical) -pageWidth else pageWidth
        nextSpreadView.translationY = 0f
        nextSpreadView.alpha = 0f
    }

    /**
     * 单页模式下所有页槽全宽显示、右半页隐藏；
     * 双页对开模式下每页槽拆成左/右半页（中缝 16dp）。
     */
    private fun applyPageViewLayout() {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val gutterPx = (16f * density).toInt()
        val halfWidth = ((width - gutterPx) / 2).coerceAtLeast(1)
        for ((gutter, left, right) in listOf(
            Triple(prevGutterView, prevPageView, prevPageRightView),
            Triple(curGutterView, curPageView, curPageRightView),
            Triple(nextGutterView, nextPageView, nextPageRightView)
        )) {
            if (currentTwoPageSpread) {
                gutter.layoutParams = LayoutParams(gutterPx, LayoutParams.MATCH_PARENT).apply {
                    leftMargin = halfWidth
                }
                gutter.visibility = View.VISIBLE
                left.layoutParams = LayoutParams(halfWidth, LayoutParams.MATCH_PARENT).apply {
                    leftMargin = 0
                }
                right.layoutParams = LayoutParams(halfWidth, LayoutParams.MATCH_PARENT).apply {
                    leftMargin = halfWidth + gutterPx
                }
                right.visibility = View.VISIBLE
            } else {
                gutter.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
                gutter.visibility = View.GONE
                left.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                right.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
                right.visibility = View.GONE
            }
        }
    }

    /** 设置左右边缘短按的翻页方向；滑动手势方向保持不变。 */
    fun setEdgeTapMode(mode: ReaderEdgeTapMode) {
        currentEdgeTapMode = mode
    }

    private fun performEdgeTap(action: ReaderEdgeTapAction) {
        when (action) {
            ReaderEdgeTapAction.PREVIOUS_PAGE -> turnToPreviousPage()
            ReaderEdgeTapAction.NEXT_PAGE -> turnToNextPage()
        }
    }

    /** 获取指定章节的页数（需已布局） */
    fun getChapterPageCount(chapterIndex: Int): Int {
        return layoutEngine.getChapterPageCount(chapterIndex)
    }

    fun turnToPreviousPage(): Boolean {
        if (isJumpSettling) return false
        finishRunningPageTurnForNewInput()
        val current = slotManager.getCurSlot()
        val isBookFirstPage = current.chapterIndex == 0 && current.pageIndex == 0
        val previous = slotManager.getPrevSlot()
        if (isBookFirstPage || !previous.isLoaded) return false
        clearCurrentSelection()
        startTapAnimation(PageAnimationController.Direction.PREV)
        return true
    }

    private fun effectiveEdgeTapAction(action: ReaderEdgeTapAction): ReaderEdgeTapAction =
        if (currentWritingMode.isVertical) action.reversed() else action

    fun turnToNextPage(): Boolean {
        if (isJumpSettling) return false
        finishRunningPageTurnForNewInput()
        val next = slotManager.getNextSlot()
        if (!next.isLoaded) return false
        clearCurrentSelection()
        startTapAnimation(PageAnimationController.Direction.NEXT)
        return true
    }

    /** 閫夋嫨缁堢偣鎷栧埌椤甸潰鏈熬锛氳嚜鍔ㄧ炕鍒颁笅涓€椤靛苟鍦ㄦ柊椤甸噸寤洪€夊尯锛堣法椤甸€夋嫨锛?*/
    private fun handleSelectionReachEnd() {
        android.util.Log.d(TAG, "handleSelectionReachEnd isJump=" + isJumpSettling + " nextLoaded=" + slotManager.getNextSlot().isLoaded)
        if (isJumpSettling) return
        val next = slotManager.getNextSlot()
        if (!next.isLoaded) return
        turnToNextPage()
        postDelayed({ rebuildSelectionOnCurrentPage() }, 420L)
    }

    private fun rebuildSelectionOnCurrentPage() {
        val slot = slotManager.getCurSlot()
        val pageView = slot.contentView
        val tv = pageView.textView
        val sp = tv.text as? android.text.Spannable ?: return
        if (sp.isEmpty()) return
        android.util.Log.d(TAG, "rebuildSelectionOnCurrentPage len=" + sp.length)
        val x = tv.paddingLeft + 4f
        val y = tv.paddingTop + 4f
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        tv.dispatchTouchEvent(down)
        down.recycle()
        // 鍏堝彂 DOWN 锛屽欢杩熷啀鍙?UP锛屾瀯鎴愮湡瀹炵殑闀挎寜鏃堕棿宸€傚悓姝ュ彂閫?down+up 浼氳鍙栨秷闀挎寜銆?
        tv.postDelayed({
            val upTime = android.os.SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            tv.dispatchTouchEvent(up)
            up.recycle()
            // 闀挎寜閫夎瘝鍚庯紝灏嗛€夊尯璋冩暣鍒版柊椤靛紑澶村嚑涓瓧
            val sp2 = tv.text as? android.text.Spannable ?: return@postDelayed
            if (!sp2.isEmpty()) {
                android.text.Selection.setSelection(sp2, 0, minOf(4, sp2.length))
            }
        }, android.view.ViewConfiguration.getLongPressTimeout().toLong() + 120L)
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
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // The vertical renderer owns its selection handles. Once a handle accepts DOWN,
        // keep the complete stream away from page-swipe classification until UP/CANCEL.
        if (ev.actionMasked != MotionEvent.ACTION_DOWN && isVerticalSelectionHandleDragActive()) {
            return super.dispatchTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(rvImageLongPressRunnable)
                rvTouchStartX = ev.x
                rvTouchStartY = ev.y
                rvTouchDownTime = System.currentTimeMillis()
                rvHasMoved = false
                rvIsHandlingPageGesture = false
                rvImageLongPressHandled = false
                val hitView = pageViewAt(ev.x, ev.y)
                rvPendingImageView = hitView
                rvPendingImageLongPress = hitView?.getImageAt(
                    ev.x - hitView.left,
                    ev.y - hitView.top
                )?.takeIf { image ->
                    image.link == null && !image.hasAction && image.source.isNotBlank()
                }
                if (rvPendingImageLongPress != null) {
                    postDelayed(
                        rvImageLongPressRunnable,
                        android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
                val w = width.toFloat()
                rvIsEdgeTouch = w > 0 && (ev.x / w < 0.3f || ev.x / w > 0.7f)
                return super.dispatchTouchEvent(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                if (rvIsHandlingPageGesture) {
                    animationController.onTouchEvent(ev)
                    return true
                }

                val dx = abs(ev.x - rvTouchStartX)
                val dy = abs(ev.y - rvTouchStartY)
                val dt = System.currentTimeMillis() - rvTouchDownTime

                if (dx > 24f || dy > 24f) {
                    rvHasMoved = true
                    removeCallbacks(rvImageLongPressRunnable)
                    rvPendingImageLongPress = null
                }

                // 仅在 500ms 窗口内拦截水平滑动（超过 500ms 视为选择扩展，不拦截）
                // dx > dy * 0.3f：允许更自然的斜向拖拽（拇指弧线有垂直分量）
                if (dt < 500L && dx > 8f && dx > dy * 0.3f) {
                    Log.d(TAG, "Handle page swipe at dx=$dx dy=$dy dt=$dt")
                    removeCallbacks(rvImageLongPressRunnable)
                    rvPendingImageLongPress = null
                    rvIsHandlingPageGesture = true
                    clearCurrentSelection()

                    // Let the old settle continue under the initial touch. Commit it
                    // only after this stream is confirmed as a new horizontal turn.
                    finishRunningPageTurnForNewInput()

                    // 先取消子 TextView 的原生触摸序列，再把完整序列交给动画控制器。
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()

                    val downEvent = MotionEvent.obtain(
                        ev.downTime,
                        ev.eventTime,
                        MotionEvent.ACTION_DOWN,
                        rvTouchStartX,
                        rvTouchStartY,
                        ev.metaState
                    )
                    animationController.onTouchEvent(downEvent)
                    downEvent.recycle()
                    animationController.onTouchEvent(ev)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(rvImageLongPressRunnable)
                rvPendingImageLongPress = null
                if (rvImageLongPressHandled) {
                    rvImageLongPressHandled = false
                    return true
                }
                if (rvIsHandlingPageGesture) {
                    rvIsHandlingPageGesture = false
                    animationController.onTouchEvent(ev)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isVerticalSelectionHandleDragActive()) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (!rvHasMoved && System.currentTimeMillis() - rvTouchDownTime < 300L) {
                    val hitView = pageViewAt(rvTouchStartX, rvTouchStartY) ?: curPageView
                    val link = hitView.getLinkAt(
                        rvTouchStartX - hitView.left,
                        rvTouchStartY - hitView.top
                    )
                    if (link != null) {
                        Log.d(TAG, "EPUB link tap: $link")
                        clearCurrentSelection()
                        callbacks?.onLinkClick(link, rvTouchStartX, rvTouchStartY)
                    } else {
                        val image = hitView.getImageAt(
                            rvTouchStartX - hitView.left,
                            rvTouchStartY - hitView.top
                        )
                        when {
                            image?.link != null -> {
                                Log.d(TAG, "EPUB linked image tap: ${image.link}")
                                clearCurrentSelection()
                                callbacks?.onLinkClick(image.link, rvTouchStartX, rvTouchStartY)
                            }
                            image?.hasAction == true -> {
                                // Keep action-bearing images out of the preview path.
                                Log.d(TAG, "EPUB action image tap ignored by preview")
                            }
                            image != null -> {
                                // Plain image taps are deliberately inert; preview is long-press only.
                                Log.d(TAG, "Plain EPUB image tap ignored")
                            }
                            rvIsEdgeTouch -> {
                                // Edge short tap: turn the page through the existing animation callback.
                                Log.d(TAG, "Edge tap at x=${ev.x} -> page turn")
                                if (rvTouchStartX / width < 0.3f) {
                                    animationController.onTapLeft?.invoke()
                                } else {
                                    animationController.onTapRight?.invoke()
                                }
                            }
                            else -> {
                                // Center short tap: toggle the reader menu.
                                Log.d(TAG, "Center tap detected -> toggle menu")
                                clearCurrentSelection()
                                callbacks?.onMenuToggle()
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun handlePendingImageLongPress() {
        val image = rvPendingImageLongPress ?: return
        if (rvHasMoved || rvIsHandlingPageGesture) return
        rvPendingImageLongPress = null
        rvImageLongPressHandled = true

        val cancelEvent = MotionEvent.obtain(
            rvTouchDownTime,
            System.currentTimeMillis(),
            MotionEvent.ACTION_CANCEL,
            rvTouchStartX,
            rvTouchStartY,
            0
        )
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()

        clearCurrentSelection()
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val location = IntArray(2)
        val imageView = rvPendingImageView ?: curPageView
        imageView.getLocationOnScreen(location)
        callbacks?.onImageLongPress(
            slotManager.getCurSlot().chapterIndex.coerceAtLeast(0),
            image.copy(
                leftPx = image.leftPx + location[0],
                topPx = image.topPx + location[1],
                rightPx = image.rightPx + location[0],
                bottomPx = image.bottomPx + location[1]
            )
        )
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
        if (disallowIntercept && isVerticalSelectionHandleDragActive()) {
            super.requestDisallowInterceptTouchEvent(true)
            return
        }
        if (disallowIntercept) {
            val dt = System.currentTimeMillis() - rvTouchDownTime
            if (dt < 500L) {
                // 忽略早期的 disallow 请求（插入点光标控制器可能触发）
                return
            }
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun isVerticalSelectionHandleDragActive(): Boolean =
        prevPageView.isVerticalSelectionHandleDragActive() ||
            curPageView.isVerticalSelectionHandleDragActive() ||
            nextPageView.isVerticalSelectionHandleDragActive() ||
            prevPageRightView.isVerticalSelectionHandleDragActive() ||
            curPageRightView.isVerticalSelectionHandleDragActive() ||
            nextPageRightView.isVerticalSelectionHandleDragActive()

    /** 返回触摸位置所在的当前页半页视图（双页模式按 x 命中左/右半页）。 */
    private fun pageViewAt(x: Float, y: Float): PageContentView? {
        if (!currentTwoPageSpread) return curPageView
        val cur = slotManager.getCurSlot()
        val left = cur.contentView
        val right = cur.rightContentView
        val midX = left.left + left.width.toFloat()
        return if (x < midX) left else right ?: left
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
        if (animationController.drawsDirectlyOnCanvas &&
            (animationController.isRunning || animationController.isDragging)) {
            // 仿真翻页活动期完全由稳定快照绘制，避免实时子 View 从裁剪路径中漏出。
            animationController.onDraw(canvas)
        } else {
            // 先设置子 View 的 translationX/Y（翻页动画位置）
            animationController.onDraw(canvas)
            // 绘制子 View（PageContentView 包含的 TextView）
            super.dispatchDraw(canvas)
            // 再绘制阴影叠加层（在子 View 之上）
            when (val ctrl = animationController) {
                is SlidePageAnim -> ctrl.drawOverlay(canvas)
                is ScrollPageAnim -> ctrl.drawOverlay(canvas)
                is FadePageAnim -> ctrl.drawOverlay(canvas)
                is CurlPageAnim -> ctrl.drawOverlay(canvas)
            }
        }
    }

    // ── 生命周期 ──

    /**
     * 退出阅读时调用：在后台静默预跑当前章节的 content + layout，
     * 结果存入 layoutCache（由 ViewModel 持有的 engine，跨实例存活）。
     */
    fun preloadForExit() {
        slotManager.preloadCurrentChapter()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationController.abortAnim()
        (animationController as? CurlPageAnim)?.destroy()
        slotManager.destroy()
        // 只有自己创建的 engine 才清缓存；外部传入的由 ViewModel 保活，供重入命中
        if (ownsLayoutEngine) layoutEngine.invalidateAll()
    }

    // ── 内部方法 ──

    private fun startTapAnimation(dir: PageAnimationController.Direction) {
        if (isJumpSettling) return
        finishRunningPageTurnForNewInput()
        when (val ctrl = animationController) {
            is SlidePageAnim -> ctrl.startFromTap(dir)
            is ScrollPageAnim -> ctrl.startFromTap(dir)
            is FadePageAnim -> ctrl.startFromTap(dir)
            is CurlPageAnim -> ctrl.startFromTap(dir)
            is NoPageAnim -> ctrl.startFromTap(dir)
        }
    }

    /**
     * Rapid edge taps are not queued. The in-flight committed animation is
     * finalized synchronously, which shifts the slot tape, and the new tap can
     * immediately start against that new current page.
     */
    private fun finishRunningPageTurnForNewInput() {
        if (animationController.isRunning) {
            animationController.completeRunningFlipForNewInput()
        }
    }

    private fun beginJumpSettling(chapterIndex: Int) {
        pendingJumpChapter = chapterIndex
        isJumpSettling = true
    }

    private fun startSearchHighlightAnimationIfReady(chapterIndex: Int) {
        val highlight = searchHighlight ?: return
        if (highlight.chapterIndex != chapterIndex || searchHighlightAnimator?.isRunning == true) return

        searchHighlightAnimator = ValueAnimator.ofInt(0, 180, 0, 180, 0).apply {
            duration = 2_000L
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Int
                listOf(
                    prevPageView, curPageView, nextPageView,
                    prevPageRightView, curPageRightView, nextPageRightView
                ).forEach {
                    it.setSearchHighlightAlpha(alpha)
                }
            }
            doOnEnd {
                if (!it.isRunning) {
                    searchHighlight = null
                    slotManager.refreshCurrentHighlights()
                }
            }
            start()
        }
    }

    private fun balancedVerticalMargins(
        baseMarginTop: Float,
        baseMarginBottom: Float,
        fontSizePx: Float,
        lineHeightMultiplier: Float,
        lineSpacingExtraPx: Float,
        typeface: android.graphics.Typeface
    ): Pair<Float, Float> {
        val fontSpacing = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).run {
            textSize = fontSizePx
            this.typeface = typeface
            fontSpacing
        }
        val estimatedLineHeight = fontSpacing * lineHeightMultiplier + lineSpacingExtraPx
        val availableHeight = height - baseMarginTop - baseMarginBottom
        val shift = calculateReaderVerticalBalanceOffset(
            availableHeightPx = availableHeight,
            lineHeightPx = estimatedLineHeight,
            maxShiftPx = baseMarginBottom
        )
        return (baseMarginTop + shift) to (baseMarginBottom - shift)
    }

    /** @return Triple(backgroundColor, textColor, accentColor) */
    private fun getThemeColors(theme: String): Triple<Int, Int, Int> {
        return when (theme) {
            "night" -> Triple(0xFF1a1a1a.toInt(), 0xFFCCCCCC.toInt(), 0xFF4A90D9.toInt())
            "sepia_dark" -> Triple(0xFF2b2118.toInt(), 0xFFE8D5BC.toInt(), 0xFFC77826.toInt())
            "green_dark" -> Triple(0xFF142a1a.toInt(), 0xFFC8E6C9.toInt(), 0xFF2E7D32.toInt())
            "sepia" -> Triple(0xFFf5e6d3.toInt(), 0xFF4a3728.toInt(), 0xFFC77826.toInt())
            "green" -> Triple(0xFFe8f5e9.toInt(), 0xFF2e7d32.toInt(), 0xFF2E7D32.toInt())
            else   -> Triple(0xFFFBFBFC.toInt(), 0xFF333333.toInt(), 0xFF007AFF.toInt())
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val layoutWidth = right - left
        val layoutHeight = bottom - top
        if (changed && currentChapterCount > 0 &&
            (
                !isConfigured ||
                    configuredWidth != layoutWidth ||
                    configuredHeight != layoutHeight ||
                    (currentTwoPageSpreadEnabled && (layoutWidth > layoutHeight)) != currentTwoPageSpread
                )
        ) {
            configure(
                fontSizePx = currentFontSizePx,
                theme = currentTheme,
                chapterCount = currentChapterCount,
                startChapter = pendingStartChapter,
                startPage = pendingStartPage,
                lineHeightMult = currentLineHeightMult,
                letterSpacingDp = currentLetterSpacingDp,
                fontType = currentFontType,
                customFontPath = currentCustomFontPath,
                marginLeftDp = currentMarginLeftDp,
                marginRightDp = currentMarginRightDp,
                marginTopDp = currentMarginTopDp,
                marginBottomDp = currentMarginBottomDp,
                topOverlayInsetDp = currentTopOverlayInsetDp,
                bottomOverlayInsetDp = currentBottomOverlayInsetDp,
                paragraphSpacingDp = currentParagraphSpacingDp,
                bionicReadingEnabled = currentBionicReadingEnabled,
                useDisplayDensityForSpans = currentUseDisplayDensityForSpans,
                writingMode = currentWritingMode,
                twoPageSpread = currentTwoPageSpreadEnabled,
                width = layoutWidth,
                height = layoutHeight
            )
        }
    }

    // ── 选区工具方法 ──

    /**
     * 获取当前页选区的完整信息（供 Compose 层自定义菜单使用）。
     * @return null 表示当前无选区
     */
    fun getSelectionInfo(sourceView: PageContentView? = null): SelectionInfo? {
        val pageView = sourceView ?: slotManager.getPrimaryContentView()
        val tv = pageView.textView
        val spannable = tv.text as? android.text.Spannable ?: return null
        val selStart = android.text.Selection.getSelectionStart(spannable)
        val selEnd = android.text.Selection.getSelectionEnd(spannable)
        if (selStart < 0 || selEnd <= selStart) return null

        val text = spannable.toString().substring(selStart, selEnd)
        val slot = slotManager.getSlotForView(pageView) ?: return null
        val chapterIdx = slot.chapterIndex
        val chapterStartOffset = pageView.chapterStartOffset
        if (currentWritingMode.isVertical) {
            val (firstBounds, lastBounds) = pageView.getVerticalSelectionBounds() ?: return null
            return SelectionInfo(
                selectedText = text,
                chapterIndex = chapterIdx,
                chapterStartOffset = chapterStartOffset,
                pageStart = selStart,
                pageEnd = selEnd,
                selTopY = minOf(firstBounds.top, lastBounds.top),
                selBottomY = maxOf(firstBounds.bottom, lastBounds.bottom),
                selStartX = firstBounds.centerX,
                selEndX = lastBounds.centerX
            )
        }
        val layout = tv.layout ?: return null
        val hiddenStartLine = layout.getLineForOffset(selStart)
        val hiddenStartLineOffset = layout.getLineStart(hiddenStartLine)
        val visualStartLineInfo = pageView.getVisualLineInfo(selStart)

        Log.e(
            "ReaderSelectionDebug",
            "getSelectionInfo view=${System.identityHashCode(pageView)} " +
                "slotChapter=${slot.chapterIndex} slotPage=${slot.pageIndex} " +
                "chapterStart=$chapterStartOffset local=[$selStart,$selEnd) " +
                "absolute=[${chapterStartOffset + selStart},${chapterStartOffset + selEnd}) " +
                "hiddenLine=$hiddenStartLine@$hiddenStartLineOffset " +
                "visualLine=${visualStartLineInfo?.first}@${visualStartLineInfo?.second} " +
                "text=${text.take(80)}"
        )

        val startLine = layout.getLineForOffset(selStart)
        val endLine = layout.getLineForOffset(selEnd.coerceAtMost(spannable.length - 1))
        val topY = (tv.top + tv.paddingTop + layout.getLineTop(startLine)).toFloat()
        val bottomY = (tv.top + tv.paddingTop + layout.getLineBottom(endLine)).toFloat()
        // 双页模式下右半页视图位于父容器右半区，需要加上半页偏移，
        // 选区菜单/手柄坐标才能对齐屏幕。
        val viewOffsetX = pageView.left.toFloat()
        val startX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selStart) + viewOffsetX
        val endX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selEnd) + viewOffsetX

        // ── 跨页选择合并 ──
        // 如果有跨页选择状态，且当前选区所在的章节与跨页选择的章节相同（或相邻），
        // 并且当前选区在跨页选择的后面，则合并为完整的跨页选区
        val crossPage = crossPageSelection
        if (crossPage != null && crossPage.startChapterIndex == chapterIdx) {
            val crossPageStartAbs = crossPage.startChapterOffset + crossPage.startPageStart
            val currentEndAbs = chapterStartOffset + selEnd
            // 确认当前选区在跨页选区之后（跨页选区结束位置 <= 当前选区开始位置）
            if (crossPageStartAbs < currentEndAbs) {
                // 使用 pageView 的 originalSpannable 获取章节全文（避免主线程 I/O）
                val mergedText = crossPage.startText + text
                Log.d(TAG, "Cross-page selection merged: abs=[" + crossPageStartAbs + "," + currentEndAbs + ") startLen=" + crossPage.startText.length + " textLen=" + text.length + " mergedLen=" + mergedText.length + " text=" + mergedText.take(80))
                return SelectionInfo(
                    selectedText = mergedText,
                    chapterIndex = chapterIdx,
                    chapterStartOffset = crossPage.startChapterOffset,
                    pageStart = crossPage.startPageStart,
                    pageEnd = currentEndAbs - crossPage.startChapterOffset,
                    selTopY = crossPage.startSelTopY.coerceAtMost(topY),
                    selBottomY = crossPage.startSelBottomY.coerceAtMost(bottomY),
                    selStartX = crossPage.startSelStartX,
                    selEndX = endX
                )
            }
        }

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
        curPageRightView.clearSelection()
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
                            callbacks?.onSelectionStarted(pageView)
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
