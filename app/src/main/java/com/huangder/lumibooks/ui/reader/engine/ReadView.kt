package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.animation.doOnEnd
import android.text.Selection
import android.text.Spannable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.os.SystemClock
import android.widget.FrameLayout
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.util.performance.ReaderPageTurnPerformance
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import com.huangder.lumibooks.ui.reader.mapGlobalProgress
import com.huangder.lumibooks.ui.reader.pageIndexForChapterFraction
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * Resolves one vertical margin pair for the whole paged reader.
 *
 * The page breaker can only place complete lines, so a viewport usually has a
 * small remainder after the last complete line. Move half of that remainder
 * from the bottom margin to the top margin once per reader configuration. The
 * result is quantized to the same integer pixels used by [TextView] padding
 * and preserves the total inset exactly.
 */
internal data class ReaderVerticalMargins(
    val topPx: Int,
    val bottomPx: Int
)

internal fun resolveReaderVerticalMargins(
    heightPx: Int,
    baseMarginTopPx: Float,
    baseMarginBottomPx: Float,
    fontSizePx: Float,
    lineHeightMultiplier: Float,
    lineSpacingExtraPx: Float,
    typeface: Typeface,
    protectedBottomInsetPx: Float = 0f
): ReaderVerticalMargins {
    val baseTopPx = baseMarginTopPx.toInt().coerceAtLeast(0)
    val baseBottomPx = baseMarginBottomPx.toInt().coerceAtLeast(0)
    val protectedBottomPx = protectedBottomInsetPx
        .toInt()
        .coerceIn(0, baseBottomPx)
    val maxShiftPx = (baseBottomPx - protectedBottomPx).coerceAtLeast(0)

    val fontSpacing = Paint(Paint.ANTI_ALIAS_FLAG).run {
        textSize = fontSizePx
        this.typeface = typeface
        fontSpacing
    }
    val estimatedLineHeight = fontSpacing * lineHeightMultiplier + lineSpacingExtraPx
    val availableHeightPx = (heightPx - baseTopPx - baseBottomPx).coerceAtLeast(0)
    val shiftPx = calculateReaderVerticalBalanceOffset(
        availableHeightPx = availableHeightPx.toFloat(),
        lineHeightPx = estimatedLineHeight,
        maxShiftPx = maxShiftPx.toFloat()
    ).roundToInt().coerceIn(0, maxShiftPx)

    return ReaderVerticalMargins(
        topPx = baseTopPx + shiftPx,
        bottomPx = baseBottomPx - shiftPx
    )
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
        private const val JUMP_SETTLE_TIMEOUT_MS = 5_000L
        private const val CURL_COMMIT_FRACTION = 0.14f
        private const val CURL_FLING_DP_PER_SECOND = 450f
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
    val prevPageView = PageContentView(context)
    val curPageView = PageContentView(context)
    val nextPageView = PageContentView(context)
    private val prevPageRightView = PageContentView(context)
    private val curPageRightView = PageContentView(context)
    private val nextPageRightView = PageContentView(context)

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
    private var bookmarkPullEnabled = true
    private var contentProvider: (suspend (Int) -> CharSequence?)? = null

    private var savedNotes: List<Note> = emptyList()
    private data class SearchHighlight(val chapterIndex: Int, val start: Int, val end: Int)
    private var searchHighlight: SearchHighlight? = null
    private var searchHighlightAnimator: ValueAnimator? = null
    private val jumpGenerationGate = JumpGenerationGate()
    private val isJumpSettling: Boolean
        get() = jumpGenerationGate.isSettling
    private var jumpTimeoutRunnable: Runnable? = null
    private val curlTurnSequencer = ReaderCurlTurnSequencer()
    private var curlPageGeneration = 0L
    private var rvDeferredCurlGesture = false
    private var rvDeferredCurlDirection = PageAnimationController.Direction.NONE
    private var rvDeferredCurlLatestX = 0f
    private var rvDeferredCurlLatestY = 0f
    private var rvDeferredCurlLatestTime = 0L
    private var rvDeferredCurlMetaState = 0
    private var pendingPageTurnDirection: PageAnimationController.Direction? = null

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
    private var rvSystemBackGestureCandidate = false
    private var rvSystemBackGestureSuppressed = false
    private var rvTouchDownTime = 0L
    private var rvHasMoved = false
    private var rvIsEdgeTouch = false
    private var rvIsHandlingPageGesture = false
    private var rvBoundaryGestureSuppressed = false
    private val bookmarkPullTracker = BookmarkPullGestureTracker()
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
    private var currentTextAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL
    private var currentFontType: String = "system"
    private var currentCustomFontPath: String? = null
    private var currentMarginLeftDp: Float = 38f
    private var currentMarginRightDp: Float = 38f
    private var currentMarginTopDp: Float = 64f
    private var currentMarginBottomDp: Float = 64f
    private var currentTopOverlayInsetDp: Float = 0f
    private var currentBottomOverlayInsetDp: Float = 0f
    private var currentParagraphSpacingDp: Float = 0f
    private var currentFirstLineIndent: Float = 2f
    private var currentBodyFontWeight: Int = 400
    private var currentBionicReadingEnabled: Boolean = false
    private var currentUseDisplayDensityForSpans: Boolean = false
    private var currentChineseMode: String = "original"
    private var currentPageTransition: String = "slide"
    private var slideTransitionDurationMs = ReaderPageAnimationSettings.SLIDE_DEFAULT_MS
    private var scrollTransitionDurationMs = ReaderPageAnimationSettings.SCROLL_DEFAULT_MS
    private var fadeTransitionDurationMs = ReaderPageAnimationSettings.FADE_DEFAULT_MS
    private var curlTransitionDurationMs = ReaderPageAnimationSettings.CURL_DEFAULT_MS
    private var currentEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    private var currentWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL
    /** 双页开关（不含方向）：设备/设置允许时 true，是否启用由实测宽高决定 */
    private var currentTwoPageSpreadEnabled: Boolean = false
    /** 当前实际生效的双页模式（= 开关 && 横屏） */
    private var currentTwoPageSpread: Boolean = false
    private var currentReaderBackgroundColor: Int? = null
    private var currentReaderBackgroundImagePath: String? = null
    private var currentReaderBackgroundImageOpacity: Float = 1f
    private var currentReaderBackgroundImageBlurDp: Float = 0f
    private var currentReaderTextColor: Int? = null
    private var lastRenderConfig: ReaderRenderConfig? = null
    private var pendingStartChapter: Int = 0
    private var pendingStartPage: Int = 0
    private val positionRequestTracker = ReaderPositionRequestTracker()
    private var configuredWidth: Int = 0
    private var configuredHeight: Int = 0

    // ── 跨页选择 ──
    /** 跨页选择状态：用户在第 A 页选择后翻页，在第 B 页继续选择时合并两页选区 */
    var crossPageSelection: CrossPageSelectionState? = null
        private set
    private var pendingRebuildSelection = false

    /** 褰撳墠鍙ュ彞 TTS 楂樹寒鑼冨洿锛堝叏灞€绔犺妭鍐呭亸绉伙級锛屼负绌哄垯涓嶆樉绀?*/
    var ttsHighlightRange: TtsHighlightRange? = null
        set(value) {
            if (field == value) return
            field = value
            if (::slotManager.isInitialized) slotManager.updateTtsHighlight(value)
        }

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
        slotManager.onJumpFinishedCallback = { generation, result ->
            finishJumpSettling(generation, result)
        }

        // 初始化动画控制器
        animationController = SlidePageAnim(animationSurface, slideTransitionDurationMs)

        animationController.onCanFlip = { dir ->
            if (isJumpSettling || isPageTurnBlockedAtBoundary(dir)) false else when (dir) {
                PageAnimationController.Direction.NEXT -> {
                    val next = slotManager.getNextSlot()
                    if (next.isLoaded) {
                        true
                    } else {
                        // A cross-chapter target can be waiting for the next
                        // chapter layout. Let the slot manager prefetch it and
                        // replay this direction when the slot becomes ready.
                        if (animationController !is CurlPageAnim &&
                            slotManager.hasPotentialNextPage()
                        ) {
                            pendingPageTurnDirection = PageAnimationController.Direction.NEXT
                        }
                        false
                    }
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
            val before = slotManager.getCurSlot()
            Log.d(
                "ReadViewCurlState",
                "complete direction=${animationController.currentDirection} " +
                    "cur=${before.chapterIndex}:${before.pageIndex} loaded=${before.isLoaded} " +
                    "next=${slotManager.getNextSlot().chapterIndex}:${slotManager.getNextSlot().pageIndex} " +
                    "nextLoaded=${slotManager.getNextSlot().isLoaded}"
            )
            when (animationController.currentDirection) {
                PageAnimationController.Direction.NEXT -> slotManager.shiftForward()
                PageAnimationController.Direction.PREV -> slotManager.shiftBackward()
                else -> {}
            }
            if (animationController is CurlPageAnim) {
                curlTurnSequencer.idle()
                val after = slotManager.getCurSlot()
                Log.d(
                    "ReadViewCurlState",
                    "shifted cur=${after.chapterIndex}:${after.pageIndex} loaded=${after.isLoaded} " +
                        "pending=${curlTurnSequencer.pendingSteps}"
                )
                post {
                    if (!resumeDeferredCurlGestureIfReady()) drainPendingCurlTurns()
                }
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
            curlPageGeneration++
            callbacks?.onPageChanged(globalPage, chapterIdx, pageInChapter, chapterTotal)
            startSearchHighlightAnimationIfReady(chapterIdx)
            configureCurrentPageView()
            invalidate()
        }
        slotManager.onSpreadPageChangedCallback = { rightGlobalPage, rightChapterIdx, rightPage ->
            callbacks?.onSpreadPageChanged(rightGlobalPage, rightChapterIdx, rightPage)
        }
        slotManager.onSlotReadyCallback = {
            postOnAnimation {
                if (!resumeDeferredCurlGestureIfReady()) drainPendingCurlTurns()
                drainPendingPageTurn()
            }
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
    private fun configureCurrentPageView(viewportHeightPx: Int = height) {
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

        val resolvedTypeface = resolveReaderTypeface(
            context = context,
            fontType = currentFontType,
            customFontPath = currentCustomFontPath,
            weight = currentBodyFontWeight
        )
        val verticalMargins = if (currentWritingMode.isVertical) {
            ReaderVerticalMargins(
                topPx = baseMarginTop.toInt().coerceAtLeast(0),
                bottomPx = baseMarginBottom.toInt().coerceAtLeast(0)
            )
        } else {
            resolveReaderVerticalMargins(
                heightPx = viewportHeightPx,
                baseMarginTopPx = baseMarginTop,
                baseMarginBottomPx = baseMarginBottom,
                fontSizePx = currentFontSizePx,
                lineHeightMultiplier = currentLineHeightMult,
                lineSpacingExtraPx = lineSpacingExtra,
                typeface = resolvedTypeface.typeface,
                protectedBottomInsetPx = currentBottomOverlayInsetDp * density
            )
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
                typeface = resolvedTypeface.typeface,
                marginLeftPx = marginLeft,
                marginTopPx = verticalMargins.topPx.toFloat(),
                marginRightPx = if (currentTwoPageSpread) gutterMargin else marginRight,
                marginBottomPx = verticalMargins.bottomPx.toFloat(),
                highlightColor = highlightColor,
                accentColor = accentColor,
                textAlignment = currentTextAlignment,
                writingMode = currentWritingMode,
                boldText = resolvedTypeface.fakeBold
            )
            right.configure(
                fontSizePx = currentFontSizePx,
                textColor = textColor,
                lineHeightMult = currentLineHeightMult,
                lineSpacingExtraPx = lineSpacingExtra,
                letterSpacingPx = currentLetterSpacingDp * density,
                typeface = resolvedTypeface.typeface,
                marginLeftPx = if (currentTwoPageSpread) gutterMargin else marginLeft,
                marginTopPx = verticalMargins.topPx.toFloat(),
                marginRightPx = marginRight,
                marginBottomPx = verticalMargins.bottomPx.toFloat(),
                highlightColor = highlightColor,
                accentColor = accentColor,
                textAlignment = currentTextAlignment,
                writingMode = currentWritingMode,
                boldText = resolvedTypeface.fakeBold
            )
            left.setReaderBackground(
                bgColor,
                currentReaderBackgroundImagePath,
                currentReaderBackgroundImageOpacity,
                currentReaderBackgroundImageBlurDp
            )
            right.setReaderBackground(
                bgColor,
                currentReaderBackgroundImagePath,
                currentReaderBackgroundImageOpacity,
                currentReaderBackgroundImageBlurDp
            )
        }
        setBackgroundColor(bgColor)
        prevGutterView.setBackgroundColor(bgColor)
        curGutterView.setBackgroundColor(bgColor)
        nextGutterView.setBackgroundColor(bgColor)
        this.bgColor = bgColor
    }

    fun setReaderBackground(
        backgroundColor: Int,
        textColor: Int,
        imagePath: String?,
        imageOpacity: Float = 1f,
        imageBlurDp: Float = 0f
    ) {
        if (currentReaderBackgroundColor == backgroundColor &&
            currentReaderTextColor == textColor &&
            currentReaderBackgroundImagePath == imagePath &&
            currentReaderBackgroundImageOpacity == imageOpacity &&
            currentReaderBackgroundImageBlurDp == imageBlurDp
        ) return

        animationController.abortAnim()
        currentReaderBackgroundColor = backgroundColor
        currentReaderTextColor = textColor
        currentReaderBackgroundImagePath = imagePath
        currentReaderBackgroundImageOpacity = imageOpacity.coerceIn(0f, 1f)
        currentReaderBackgroundImageBlurDp = imageBlurDp.coerceIn(0f, 40f)
        configureCurrentPageView()
        invalidate()
    }

    // ── 配置 ──

    fun setCallbacks(cbs: ReadViewCallbacks) {
        callbacks = cbs
    }

    fun setBookmarkPullEnabled(enabled: Boolean) {
        if (bookmarkPullEnabled == enabled) return
        bookmarkPullEnabled = enabled
        if (!enabled) {
            val finish = bookmarkPullTracker.finish(cancelled = true)
            if (finish.wasActive) callbacks?.onBookmarkPullFinished(false)
        }
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
        textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
        fontType: String = "system",
        customFontPath: String? = null,
        marginLeftDp: Float = 38f,
        marginRightDp: Float = 38f,
        marginTopDp: Float = 64f,
        marginBottomDp: Float = 64f,
        topOverlayInsetDp: Float = 0f,
        bottomOverlayInsetDp: Float = 0f,
        paragraphSpacingDp: Float = 2f,
        firstLineIndent: Float = 2f,
        bodyFontWeight: Int = 400,
        bionicReadingEnabled: Boolean = false,
        useDisplayDensityForSpans: Boolean = false,
        writingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
        twoPageSpread: Boolean = false,
        width: Int = this.width,
        height: Int = this.height
    ) {
        val positionRequestChanged = positionRequestTracker.observe(startChapter, startPage)
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
            currentTextAlignment = textAlignment
            currentFontType = fontType
            currentCustomFontPath = customFontPath
            currentMarginLeftDp = marginLeftDp
            currentMarginRightDp = marginRightDp
            currentMarginTopDp = marginTopDp
            currentMarginBottomDp = marginBottomDp
            currentTopOverlayInsetDp = topOverlayInsetDp
            currentBottomOverlayInsetDp = bottomOverlayInsetDp
            currentParagraphSpacingDp = paragraphSpacingDp
            currentFirstLineIndent = firstLineIndent
            currentBodyFontWeight = bodyFontWeight.coerceIn(100, 900)
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
        val textAlignmentChanged = currentTextAlignment != textAlignment
        val fontTypeChanged = currentFontType != fontType
        val customFontPathChanged = currentCustomFontPath != customFontPath
        val marginChanged = Math.abs(currentMarginLeftDp - marginLeftDp) > 0.5f ||
            Math.abs(currentMarginRightDp - marginRightDp) > 0.5f ||
            Math.abs(currentMarginTopDp - marginTopDp) > 0.5f ||
            Math.abs(currentMarginBottomDp - marginBottomDp) > 0.5f
        val overlayInsetChanged = Math.abs(currentTopOverlayInsetDp - topOverlayInsetDp) > 0.5f ||
            Math.abs(currentBottomOverlayInsetDp - bottomOverlayInsetDp) > 0.5f
        val paragraphSpacingChanged = Math.abs(currentParagraphSpacingDp - paragraphSpacingDp) > 0.01f
        val firstLineIndentChanged = Math.abs(currentFirstLineIndent - firstLineIndent) > 0.01f
        val normalizedBodyFontWeight = bodyFontWeight.coerceIn(100, 900)
        val bodyFontWeightChanged = currentBodyFontWeight != normalizedBodyFontWeight
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
                letterSpacingChanged || textAlignmentChanged || fontTypeChanged || customFontPathChanged || marginChanged ||
                overlayInsetChanged || paragraphSpacingChanged || bionicReadingChanged ||
                firstLineIndentChanged || bodyFontWeightChanged || paginationLayoutChanged ||
                writingModeChanged || spreadModeChanged || sizeChanged
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
            // A direct TOC jump advances the slot before Compose receives its page callback.
            // Repeated old snapshots must not command the slot back to the previous page.
            if (positionRequestChanged && startChapter >= 0 &&
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
        currentTextAlignment = textAlignment
        currentFontType = fontType
        currentCustomFontPath = customFontPath
        currentMarginLeftDp = marginLeftDp
        currentMarginRightDp = marginRightDp
        currentMarginTopDp = marginTopDp
        currentMarginBottomDp = marginBottomDp
        currentTopOverlayInsetDp = topOverlayInsetDp
        currentBottomOverlayInsetDp = bottomOverlayInsetDp
        currentParagraphSpacingDp = paragraphSpacingDp
        currentFirstLineIndent = firstLineIndent
        currentBodyFontWeight = normalizedBodyFontWeight
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

        val resolvedTypeface = resolveReaderTypeface(
            context = context,
            fontType = fontType,
            customFontPath = customFontPath,
            weight = currentBodyFontWeight
        )
        val verticalMargins = if (writingMode.isVertical) {
            ReaderVerticalMargins(
                topPx = baseMarginTop.toInt().coerceAtLeast(0),
                bottomPx = baseMarginBottom.toInt().coerceAtLeast(0)
            )
        } else {
            resolveReaderVerticalMargins(
                heightPx = height,
                baseMarginTopPx = baseMarginTop,
                baseMarginBottomPx = baseMarginBottom,
                fontSizePx = fontSizePx,
                lineHeightMultiplier = lineHeightMult,
                lineSpacingExtraPx = lineSpacing,
                typeface = resolvedTypeface.typeface,
                protectedBottomInsetPx = bottomOverlayInsetDp * density
            )
        }

        layoutEngine.configure(
            width = pageWidth,
            height = height,
            fontSizePx = fontSizePx,
            lineSpacingPx = lineSpacing,
            lineSpacingMult = lineHeightMult,
            letterSpacingPx = lsPx,
            fontType = fontType,
            customTypeface = resolvedTypeface.typeface,
            typeface = resolvedTypeface.typeface,
            fontWeight = currentBodyFontWeight,
            marginLeftPx = marginLeft,
            marginRightPx = if (resolvedSpread) gutterMargin else marginRight,
            marginTopPx = verticalMargins.topPx.toFloat(),
            marginBottomPx = verticalMargins.bottomPx.toFloat(),
            textColor = textColor,
            chapterCount = chapterCount,
            useDisplayDensityForSpans = useDisplayDensityForSpans,
            textAlignment = textAlignment,
            writingMode = writingMode
        )

        configureCurrentPageView(height)

        // 🔥 共用 TextPaint：让 PageLayoutEngine 的 StaticLayout 使用与 TextView 完全相同的 Paint
        // 对象，消除两个引擎的字体度量（font metrics）差异。这是根除分页不一致的关键。
        layoutEngine.sharedTextPaint = curPageView.textView.paint

        if (needsRelayout) {
            clearCurlTurnIntent()
            if (animationController is CurlPageAnim) animationController.abortAnim()
            slotManager.clearContentCache()
            if (writingModeChanged) {
                slotManager.pendingStartCharOffset = writingModeAnchor ?: -1
            }
            // Capture the visible character before any pagination-affecting
            // change so the new page geometry can restore the same content.
            if (
                fontSizeChanged || lineHeightChanged || letterSpacingChanged ||
                textAlignmentChanged || fontTypeChanged || customFontPathChanged ||
                marginChanged || overlayInsetChanged || paragraphSpacingChanged ||
                firstLineIndentChanged || bodyFontWeightChanged ||
                bionicReadingChanged || paginationLayoutChanged || writingModeChanged ||
                sizeChanged || spreadModeChanged
            ) {
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
        val current = slotManager.getCurSlot()
        if (current.isLoaded) {
            val primaryOffset = slotManager.getPrimaryContentView().chapterStartOffset
            slotManager.pendingStartCharOffset =
                primaryOffset.takeIf { it >= 0 } ?: current.contentView.chapterStartOffset
        }
        slotManager.clearContentCache()
        layoutEngine.invalidateAll()
        if (current.chapterIndex >= 0) {
            slotManager.initialize(current.chapterIndex, current.pageIndex.coerceAtLeast(0))
        }
    }

    /** 跳转到指定章节指定页 */
    fun jumpToChapter(chapterIndex: Int, pageInChapter: Int = 0) {
        slotManager.pendingStartCharOffset = -1
        slotManager.pendingStartPageFraction = null
        jumpToChapterInternal(chapterIndex, pageInChapter)
    }

    private fun jumpToChapterInternal(chapterIndex: Int, pageInChapter: Int) {
        val generation = beginJumpSettling()
        clearCurlTurnIntent()
        animationController.abortAnim()
        slotManager.jumpTo(chapterIndex, pageInChapter, generation)
    }

    /**
     * 根据全书阅读进度百分比（0-100）跳转。
     * 按章节比例定位（不依赖全量页数缓存），若目标章节已布局则精确到页。
     */
    fun jumpToGlobalProgress(progressPercent: Float) {
        val chapterCount = layoutEngine.getChapterCount().takeIf { it > 0 } ?: return
        val target = mapGlobalProgress(progressPercent, chapterCount) ?: return
        val chapterLayout = layoutEngine.getChapterLayout(target.chapterIndex)
        val targetPage = if (chapterLayout != null && chapterLayout.totalPages > 0) {
            pageIndexForChapterFraction(target.chapterFraction, chapterLayout.totalPages)
        } else {
            0
        }
        slotManager.pendingStartCharOffset = -1
        slotManager.pendingStartPageFraction = target.chapterFraction.takeIf { chapterLayout == null }
        jumpToChapterInternal(target.chapterIndex, targetPage)
    }

    /** 跳转到章节内包含指定字符偏移的页面。 */
    fun jumpToCharacter(chapterIndex: Int, characterOffset: Int) {
        val targetOffset = characterOffset.coerceAtLeast(0)
        val cachedLayout = layoutEngine.getChapterLayout(chapterIndex)
        val cachedPage = cachedLayout?.pages?.indexOfFirst { page ->
            targetOffset >= page.startCharOffset && targetOffset < page.endCharOffset
        } ?: -1

        slotManager.pendingStartPageFraction = null
        if (cachedPage >= 0) {
            slotManager.pendingStartCharOffset = -1
            jumpToChapterInternal(chapterIndex, cachedPage)
        } else {
            slotManager.pendingStartCharOffset = targetOffset
            layoutEngine.invalidateChapter(chapterIndex)
            jumpToChapterInternal(chapterIndex, 0)
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
        return slotManager.getNextPageLocation()
    }

    /** 查询上一页位置，不执行翻页。 */
    fun getPrevPageLocation(): Pair<Int, Int> {
        return slotManager.getPrevPageLocation()
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

    /** 正文字重开关（PR #19 #24）：仅横排分页生效 */
    fun setBoldText(enabled: Boolean) {
        setBodyFontWeight(if (enabled) 700 else 400)
    }

    fun applyRenderConfig(config: ReaderRenderConfig) {
        val previous = lastRenderConfig
        if (previous == config) return
        lastRenderConfig = config

        if (previous?.layout != config.layout) {
            with(config.layout) {
                configure(
                    fontSizePx = fontSizePx,
                    theme = theme,
                    chapterCount = chapterCount,
                    startChapter = startChapter,
                    startPage = startPage,
                    lineHeightMult = lineHeightMult,
                    letterSpacingDp = letterSpacingDp,
                    textAlignment = textAlignment,
                    fontType = fontType,
                    customFontPath = customFontPath,
                    marginLeftDp = marginLeftDp,
                    marginRightDp = marginRightDp,
                    marginTopDp = marginTopDp,
                    marginBottomDp = marginBottomDp,
                    topOverlayInsetDp = 0f,
                    bottomOverlayInsetDp = 0f,
                    paragraphSpacingDp = paragraphSpacingDp,
                    firstLineIndent = firstLineIndent,
                    bodyFontWeight = bodyFontWeight,
                    bionicReadingEnabled = bionicReadingEnabled,
                    useDisplayDensityForSpans = useDisplayDensityForSpans,
                    writingMode = writingMode,
                    twoPageSpread = twoPageSpread
                )
            }
        }
        if (previous?.background != config.background) {
            with(config.background) {
                setReaderBackground(color, textColor, imagePath, imageOpacity, imageBlurDp)
            }
        }
        if (previous?.chineseMode != config.chineseMode) setChineseMode(config.chineseMode)
        if (previous?.pageTransitionDurationMs != config.pageTransitionDurationMs ||
            previous.pageTransition != config.pageTransition
        ) {
            setPageTransitionTiming(config.pageTransition, config.pageTransitionDurationMs)
        }
        if (previous?.pageTransition != config.pageTransition) setPageTransition(config.pageTransition)
        if (previous?.edgeTapMode != config.edgeTapMode) setEdgeTapMode(config.edgeTapMode)
    }

    fun setBodyFontWeight(weight: Int) {
        val normalized = weight.coerceIn(100, 900)
        if (currentBodyFontWeight == normalized) return
        currentBodyFontWeight = normalized
        configureCurrentPageView()
        forceRelayout()
    }

    /** 设置翻页动画类型 */
    fun setPageTransition(mode: String) {
        if (currentPageTransition == mode) return
        clearCurlTurnIntent()
        currentPageTransition = mode

        val oldController = animationController
        oldController.abortAnim()
        (oldController as? CurlPageAnim)?.destroy()

        val newController = when (mode) {
            "none", "instant", "no_animation" -> NoPageAnim(animationSurface)
            "fade" -> FadePageAnim(animationSurface, fadeTransitionDurationMs)
            "scroll" -> ScrollPageAnim(animationSurface, scrollTransitionDurationMs)
            "curl" -> CurlPageAnim(animationSurface, baseDurationMs = curlTransitionDurationMs)
            else -> SlidePageAnim(animationSurface, slideTransitionDurationMs)
        }
        // 重新绑定回调
        newController.onCanFlip = animationController.onCanFlip
        newController.onAnimationComplete = animationController.onAnimationComplete
        newController.onTapLeft = animationController.onTapLeft
        newController.onTapCenter = animationController.onTapCenter
        newController.onTapRight = animationController.onTapRight
        newController.onLongPress = animationController.onLongPress

        animationController = newController
        if (newController is CurlPageAnim) {
            newController.onMotionStateChanged = { state ->
                when (state) {
                    CurlPageAnim.MotionState.IDLE -> {
                        curlTurnSequencer.idle()
                        post {
                            if (!resumeDeferredCurlGestureIfReady()) drainPendingCurlTurns()
                        }
                    }
                    CurlPageAnim.MotionState.DRAGGING -> curlTurnSequencer.dragging()
                    CurlPageAnim.MotionState.SETTLING -> curlTurnSequencer.settling()
                    CurlPageAnim.MotionState.DESTROYED -> curlTurnSequencer.clear()
                }
            }
        }
        // 重置页面位置状态（防止切换时残留偏移）
        resetPageViewPositions()
        invalidate()
    }

    /** Updates the selected transition's timing without restarting an in-flight turn. */
    fun setPageTransitionTiming(mode: String, durationMs: Int) {
        val sanitized = ReaderPageAnimationSettings.sanitizeDuration(mode, durationMs)
        when (mode) {
            ReaderPageAnimationSettings.MODE_SCROLL -> scrollTransitionDurationMs = sanitized
            ReaderPageAnimationSettings.MODE_FADE -> fadeTransitionDurationMs = sanitized
            ReaderPageAnimationSettings.MODE_CURL -> curlTransitionDurationMs = sanitized
            else -> slideTransitionDurationMs = sanitized
        }
        if (mode != currentPageTransition) return
        when (val controller = animationController) {
            is SlidePageAnim -> controller.setBaseDuration(sanitized)
            is ScrollPageAnim -> controller.setBaseDuration(sanitized)
            is FadePageAnim -> controller.setBaseDuration(sanitized)
            is CurlPageAnim -> controller.setBaseDuration(sanitized)
        }
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
        if (isPageTurnBlockedAtBoundary(PageAnimationController.Direction.PREV)) return false
        if (animationController is CurlPageAnim) {
            return requestCurlTurn(PageAnimationController.Direction.PREV)
        }
        if (isJumpSettling) return false
        finishRunningPageTurnForNewInput()
        val current = slotManager.getCurSlot()
        val isBookFirstPage = current.chapterIndex == 0 && current.pageIndex == 0
        val previous = slotManager.getPrevSlot()
        if (isBookFirstPage || !previous.isLoaded) return false
        clearCurrentSelection()
        ReaderPageTurnPerformance.beginIntent(
            preloaded = true,
            crossChapter = previous.chapterIndex != current.chapterIndex
        )
        ReaderPageTurnPerformance.markVisualStarted()
        startTapAnimation(PageAnimationController.Direction.PREV)
        return true
    }

    private fun effectiveEdgeTapAction(action: ReaderEdgeTapAction): ReaderEdgeTapAction =
        if (currentWritingMode.isVertical) action.reversed() else action

    fun turnToNextPage(): Boolean {
        if (isPageTurnBlockedAtBoundary(PageAnimationController.Direction.NEXT)) return false
        if (animationController is CurlPageAnim) {
            return requestCurlTurn(PageAnimationController.Direction.NEXT)
        }
        if (isJumpSettling) return false
        finishRunningPageTurnForNewInput()
        val next = slotManager.getNextSlot()
        if (!next.isLoaded) {
            if (slotManager.hasPotentialNextPage()) {
                pendingPageTurnDirection = PageAnimationController.Direction.NEXT
                return true
            }
            return false
        }
        clearCurrentSelection()
        val current = slotManager.getCurSlot()
        ReaderPageTurnPerformance.beginIntent(
            preloaded = true,
            crossChapter = next.chapterIndex != current.chapterIndex
        )
        ReaderPageTurnPerformance.markVisualStarted()
        startTapAnimation(PageAnimationController.Direction.NEXT)
        return true
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
     * - 水平 MOVE（卷曲达到 touch slop；其他模式 500ms 内）→ 拦截翻页
     * - 边缘短 UP → 触发 animationController.onTapLeft/Right（点击翻页）
     * - 中间短 UP → 触发 callbacks.onMenuToggle（菜单切换）
     * - 长按（非卷曲模式 >500ms 或无明显移动）→ 不拦截，TextView 原生触发选词
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // The vertical renderer owns its selection handles. Once a handle accepts DOWN,
        // keep the complete stream away from page-swipe classification until UP/CANCEL.
        if (ev.actionMasked != MotionEvent.ACTION_DOWN && isVerticalSelectionHandleDragActive()) {
            return super.dispatchTouchEvent(ev)
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val density = resources.displayMetrics.density
            rvSystemBackGestureCandidate = isSystemBackGestureStart(
                width.toFloat(), height.toFloat(), ev.x, ev.y, density
            )
            rvSystemBackGestureSuppressed = false
        } else if (rvSystemBackGestureSuppressed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                rvSystemBackGestureSuppressed = false
                rvSystemBackGestureCandidate = false
            }
            return super.dispatchTouchEvent(ev)
        }
        if (rvBoundaryGestureSuppressed && ev.actionMasked != MotionEvent.ACTION_DOWN) {
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                rvBoundaryGestureSuppressed = false
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(rvImageLongPressRunnable)
                // A child can consume the terminal event of a curl stream. If
                // the next gesture arrives while that stale drag is still
                // active, clear it before classifying the new intent.
                if (animationController is CurlPageAnim && animationController.isDragging) {
                    animationController.abortAnim()
                }
                // A new touch is the latest user intent; replace any delayed
                // turn that was waiting on an earlier gesture.
                pendingPageTurnDirection = null
                rvTouchStartX = ev.x
                rvTouchStartY = ev.y
                // MotionEvent timestamps use uptime; keep gesture classification
                // on the same clock so delayed/coalesced events cannot skew it.
                rvTouchDownTime = ev.eventTime
                rvHasMoved = false
                rvIsHandlingPageGesture = false
                rvDeferredCurlGesture = false
                rvDeferredCurlDirection = PageAnimationController.Direction.NONE
                rvBoundaryGestureSuppressed = false
                bookmarkPullTracker.start(
                    x = ev.rawX,
                    y = ev.rawY,
                    density = resources.displayMetrics.density,
                    enabled = bookmarkPullEnabled &&
                        !animationController.isRunning &&
                        !animationController.isDragging &&
                        !isJumpSettling,
                    startRegionY = ev.y,
                    gestureRegionHeight = height.toFloat()
                )
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
                    if (rvDeferredCurlGesture) {
                        updateDeferredCurlEvent(ev)
                        resumeDeferredCurlGestureIfReady()
                        return true
                    }
                    animationController.onTouchEvent(ev)
                    return true
                }

                val dx = abs(ev.x - rvTouchStartX)
                val dy = abs(ev.y - rvTouchStartY)
                val dt = (ev.eventTime - rvTouchDownTime).coerceAtLeast(0L)
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

                if (dx > touchSlop || dy > touchSlop) {
                    rvHasMoved = true
                    removeCallbacks(rvImageLongPressRunnable)
                    rvPendingImageLongPress = null
                }

                bookmarkPullTracker.move(ev.rawX, ev.rawY)?.let { update ->
                    if (update.justClaimed) {
                        rvHasMoved = true
                        val cancelEvent = MotionEvent.obtain(ev).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        clearCurrentSelection()
                        callbacks?.onBookmarkPullStart()
                    }
                    if (update.crossedThreshold) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    callbacks?.onBookmarkPullProgress(update.distancePx, update.armed)
                    return true
                }

                // 滚动模式沿 Y 轴，其余模式沿 X 轴。卷曲一旦达到
                // touch slop 就持续跟手；非卷曲模式仍保留 500ms 选择窗口。
                val pageSwipeIntent = when (animationController) {
                    is ScrollPageAnim -> dy > touchSlop && dy > dx
                    is CurlPageAnim -> isCurlPageSwipeIntent(
                        ev.x - rvTouchStartX,
                        ev.y - rvTouchStartY,
                        touchSlop
                    )
                    else -> dx > touchSlop && dx > dy
                }
                if (rvSystemBackGestureCandidate &&
                    isSystemBackGestureSwipe(
                        ev.x - rvTouchStartX,
                        ev.y - rvTouchStartY
                    )
                ) {
                    // Keep the whole edge stream out of the page classifier so
                    // Android's back gesture can claim it. Mark it moved to
                    // prevent an UP at the edge from being treated as a tap.
                rvSystemBackGestureCandidate = false
                rvSystemBackGestureSuppressed = true
                rvHasMoved = true
                removeCallbacks(rvImageLongPressRunnable)
                rvPendingImageLongPress = null
                val bookmarkFinish = bookmarkPullTracker.finish(cancelled = true)
                if (bookmarkFinish.wasActive) {
                    callbacks?.onBookmarkPullFinished(false)
                }
                return super.dispatchTouchEvent(ev)
                }
                // Curl is a direct-manipulation gesture: once the finger has
                // moved far enough, it must keep following the pointer even
                // when the user pauses longer than the text-selection window.
                // Other transitions retain the short-window guard so a slow
                // drag can still become a TextView selection.
                val withinPageGestureWindow =
                    animationController is CurlPageAnim || dt < 500L
                if (withinPageGestureWindow && pageSwipeIntent) {
                    bookmarkPullTracker.reset()
                    Log.d(TAG, "Handle page swipe at dx=$dx dy=$dy dt=$dt")
                    removeCallbacks(rvImageLongPressRunnable)
                    rvPendingImageLongPress = null
                    // Let the old settle continue under the initial touch. Commit it
                    // only after this stream is confirmed as a new page turn.
                    val pageDirection = directionForPageSwipe(
                        deltaX = ev.x - rvTouchStartX,
                        deltaY = ev.y - rvTouchStartY
                    )
                    if (animationController !is CurlPageAnim) {
                        finishRunningPageTurnForNewInput()
                    }
                    if (isPageTurnBlockedAtBoundary(pageDirection)) {
                        rvBoundaryGestureSuppressed = true
                        rvHasMoved = true
                        val cancelEvent = MotionEvent.obtain(ev).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        return true
                    }

                    rvIsHandlingPageGesture = true
                    clearCurrentSelection()
                    if (animationController is CurlPageAnim &&
                        !prepareCurlSwipe(pageDirection)
                    ) {
                        rvDeferredCurlGesture = true
                        rvDeferredCurlDirection = pageDirection
                        updateDeferredCurlEvent(ev)
                    }

                    // 先取消子 TextView 的原生触摸序列，再把完整序列交给动画控制器。
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()

                    if (rvDeferredCurlGesture) return true

                    val downEvent = MotionEvent.obtain(
                        ev.downTime,
                        ev.downTime,
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
                val bookmarkFinish = bookmarkPullTracker.finish(
                    cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL
                )
                if (bookmarkFinish.wasActive) {
                    callbacks?.onBookmarkPullFinished(bookmarkFinish.commit)
                    return true
                }
                if (rvImageLongPressHandled) {
                    rvImageLongPressHandled = false
                    return true
                }
                if (rvIsHandlingPageGesture) {
                    if (rvDeferredCurlGesture) {
                        updateDeferredCurlEvent(ev)
                        if (ev.actionMasked == MotionEvent.ACTION_UP &&
                            resumeDeferredCurlGestureIfReady()
                        ) {
                            rvIsHandlingPageGesture = false
                            animationController.onTouchEvent(ev)
                            return true
                        }
                        if (ev.actionMasked == MotionEvent.ACTION_CANCEL ||
                            !deferredCurlGestureCommits(ev)
                        ) {
                            curlTurnSequencer.cancelNewest(rvDeferredCurlDirection)
                        }
                        rvDeferredCurlGesture = false
                        rvDeferredCurlDirection = PageAnimationController.Direction.NONE
                        // prepareCurlSwipe() deliberately withholds DOWN/MOVE
                        // while a short cross-chapter target is loading. In
                        // that case any remaining dragging flag belongs to a
                        // previous stream, not this deferred gesture. Clear
                        // it before replaying the queued intent so the curl
                        // overlay cannot remain frozen between chapters.
                        if (animationController is CurlPageAnim &&
                            animationController.isDragging &&
                            !animationController.isRunning
                        ) {
                            Log.d(TAG, "Clearing stale deferred curl drag")
                            animationController.abortAnim()
                        }
                        // A target may have become ready while the finger was
                        // still down. Wait until this stream ends before
                        // starting the queued tap animation; otherwise the
                        // remaining MOVE events can mutate its snapshots.
                        post(::drainPendingCurlTurns)
                    } else {
                        animationController.onTouchEvent(ev)
                    }
                    rvIsHandlingPageGesture = false
                    return true
                }
                if (ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    rvDeferredCurlGesture = false
                    rvDeferredCurlDirection = PageAnimationController.Direction.NONE
                    rvIsHandlingPageGesture = false
                    rvBoundaryGestureSuppressed = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isVerticalSelectionHandleDragActive()) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (!rvHasMoved && (ev.eventTime - rvTouchDownTime).coerceAtLeast(0L) < 300L) {
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
            SystemClock.uptimeMillis(),
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
     * 🔥 忽略非卷曲触摸开始 500ms 内的 disallow 请求。
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
            val dt = SystemClock.uptimeMillis() - rvTouchDownTime
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
            ReaderPageTurnPerformance.markFirstFrame()
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
            ReaderPageTurnPerformance.markFirstFrame()
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
        bookmarkPullTracker.reset()
        rvBoundaryGestureSuppressed = false
        rvSystemBackGestureCandidate = false
        rvSystemBackGestureSuppressed = false
        ReaderPageTurnPerformance.cancel()
        jumpTimeoutRunnable?.let(::removeCallbacks)
        jumpTimeoutRunnable = null
        jumpGenerationGate.clear()
        animationController.abortAnim()
        (animationController as? CurlPageAnim)?.destroy()
        curlTurnSequencer.destroy()
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

    /** Non-curl animations finalize a committed turn before accepting new input. */
    private fun finishRunningPageTurnForNewInput() {
        val controller = animationController
        if (controller.isRunning) {
            controller.completeRunningFlipForNewInput()
        }
    }

    private fun updateDeferredCurlEvent(event: MotionEvent) {
        rvDeferredCurlLatestX = event.x
        rvDeferredCurlLatestY = event.y
        rvDeferredCurlLatestTime = event.eventTime
        rvDeferredCurlMetaState = event.metaState
    }

    private fun resumeDeferredCurlGestureIfReady(): Boolean {
        val controller = animationController as? CurlPageAnim ?: return false
        if (!rvDeferredCurlGesture || !rvIsHandlingPageGesture || isJumpSettling ||
            controller.isRunning || controller.isDragging
        ) return false
        val direction = rvDeferredCurlDirection
        val pendingDirection = when {
            curlTurnSequencer.pendingSteps > 0 -> PageAnimationController.Direction.NEXT
            curlTurnSequencer.pendingSteps < 0 -> PageAnimationController.Direction.PREV
            else -> PageAnimationController.Direction.NONE
        }
        if (direction == PageAnimationController.Direction.NONE || pendingDirection != direction ||
            !isCurlTargetReady(direction)
        ) return false

        val turn = curlTurnSequencer.pollTurn()
        if (turn.input != CurlTurnInput.SWIPE || turn.direction != direction) {
            curlTurnSequencer.restore(turn)
            return false
        }

        clearCurrentSelection()
        rvDeferredCurlGesture = false
        rvDeferredCurlDirection = PageAnimationController.Direction.NONE
        val down = MotionEvent.obtain(
            rvTouchDownTime,
            rvTouchDownTime,
            MotionEvent.ACTION_DOWN,
            rvTouchStartX,
            rvTouchStartY,
            rvDeferredCurlMetaState
        )
        val move = MotionEvent.obtain(
            rvTouchDownTime,
            rvDeferredCurlLatestTime.coerceAtLeast(rvTouchDownTime),
            MotionEvent.ACTION_MOVE,
            rvDeferredCurlLatestX,
            rvDeferredCurlLatestY,
            rvDeferredCurlMetaState
        )
        try {
            controller.onTouchEvent(down)
            controller.onTouchEvent(move)
        } finally {
            down.recycle()
            move.recycle()
        }
        return true
    }

    private fun deferredCurlGestureCommits(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return false
        val dx = event.x - rvTouchStartX
        val distanceCommit = abs(dx) / width.coerceAtLeast(1).toFloat() >= CURL_COMMIT_FRACTION
        val elapsed = (event.eventTime - rvTouchDownTime).coerceAtLeast(1L)
        val velocityCommit = abs(dx) * 1_000f / elapsed >=
            CURL_FLING_DP_PER_SECOND * resources.displayMetrics.density
        return distanceCommit || velocityCommit
    }

    private fun requestCurlTurn(direction: PageAnimationController.Direction): Boolean {
        val controller = animationController as? CurlPageAnim ?: return false
        if (direction == PageAnimationController.Direction.NONE) return false

        if (isJumpSettling) {
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                expedited = curlTurnSequencer.pendingSteps != 0,
                pageGeneration = curlPageGeneration
            )
            return true
        }

        if (handoffRunningCurlInput(controller, direction, gestureStartY = null)) return true

        if (curlTurnSequencer.pendingSteps != 0) {
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                expedited = true,
                pageGeneration = curlPageGeneration
            )
            drainPendingCurlTurns()
            return true
        }
        if (isCurlTargetReady(direction)) {
            clearCurrentSelection()
            if (controller.startFromTap(direction)) {
                curlTurnSequencer.settling()
                recordCurlTurnIntent(direction, preloaded = true)
                ReaderPageTurnPerformance.markVisualStarted()
                return true
            }
            return retryCurlTurnAfterStartFailure(QueuedCurlTurn(direction))
        }
        if (hasCurlTarget(direction)) {
            recordCurlTurnIntent(direction, preloaded = false)
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                pageGeneration = curlPageGeneration
            )
            return true
        }

        curlTurnSequencer.clear()
        return false
    }

    private fun prepareCurlSwipe(direction: PageAnimationController.Direction): Boolean {
        val controller = animationController as? CurlPageAnim ?: return true
        if (direction == PageAnimationController.Direction.NONE) return false
        val gestureStartY = rvTouchStartY
        // A live drag is the newest direct-manipulation intent. Old queued
        // turns must not keep auto-playing after this pointer stream ends.
        curlTurnSequencer.clear()
        if (isJumpSettling) {
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                gestureStartY = gestureStartY,
                input = CurlTurnInput.SWIPE,
                expedited = curlTurnSequencer.pendingSteps != 0,
                pageGeneration = curlPageGeneration
            )
            return false
        }
        if (handoffRunningCurlInput(controller, direction, gestureStartY)) {
            return false
        }

        if (curlTurnSequencer.pendingSteps != 0) {
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                gestureStartY = gestureStartY,
                input = CurlTurnInput.SWIPE,
                expedited = true,
                pageGeneration = curlPageGeneration
            )
            drainPendingCurlTurns()
            return false
        }
        if (isCurlTargetReady(direction)) {
            curlTurnSequencer.dragging()
            return true
        }
        if (hasCurlTarget(direction)) {
            curlTurnSequencer.offerWhileWaiting(
                direction = direction,
                gestureStartY = gestureStartY,
                input = CurlTurnInput.SWIPE,
                pageGeneration = curlPageGeneration
            )
        } else {
            curlTurnSequencer.clear()
        }
        return false
    }

    /** Returns true when the incoming turn was queued behind an in-flight curl. */
    private fun handoffRunningCurlInput(
        controller: CurlPageAnim,
        direction: PageAnimationController.Direction,
        gestureStartY: Float?
    ): Boolean {
        if (!controller.isRunning) {
            if (controller.isDragging) controller.abortAnim()
            return false
        }
        return when (
            curlRunningInputDisposition(
                if (gestureStartY != null) {
                    controller.completeRunningFlipForGestureHandoff()
                } else {
                    controller.completeRunningFlipForNewInput()
                }
            )
        ) {
            CurlRunningInputDisposition.QUEUE -> {
                curlTurnSequencer.offer(
                    direction = direction,
                    gestureStartY = gestureStartY,
                    input = if (gestureStartY == null) CurlTurnInput.TAP else CurlTurnInput.SWIPE,
                    expedited = true,
                    pageGeneration = curlPageGeneration
                )
                true
            }
            CurlRunningInputDisposition.REEVALUATE -> false
            CurlRunningInputDisposition.ABORT_AND_REEVALUATE -> {
                controller.abortAnim()
                false
            }
        }
    }

    private fun retryCurlTurnAfterStartFailure(
        turn: QueuedCurlTurn
    ): Boolean {
        val direction = turn.direction
        if (hasCurlTarget(direction)) {
            curlTurnSequencer.restore(turn)
            // The target slot may have just been populated and still needs its
            // first layout pass before it can be captured into a curl frame.
            postOnAnimation { drainPendingCurlTurns() }
            return true
        }
        curlTurnSequencer.clear()
        return false
    }

    private fun drainPendingCurlTurns() {
        val controller = animationController as? CurlPageAnim ?: return
        if (isJumpSettling || rvIsHandlingPageGesture || rvDeferredCurlGesture ||
            controller.isRunning || controller.isDragging
        ) return
        val turn = curlTurnSequencer.pollTurn()
        val direction = turn.direction
        if (direction == PageAnimationController.Direction.NONE) {
            curlTurnSequencer.idle()
            return
        }
        if (isCurlTargetReady(direction)) {
            clearCurrentSelection()
            val expedited = turn.expedited || curlTurnSequencer.pendingSteps != 0
            if (controller.startFromTap(direction, turn.gestureStartY, expedited)) {
                curlTurnSequencer.settling()
                recordCurlTurnIntent(direction, preloaded = true)
                ReaderPageTurnPerformance.markVisualStarted()
                return
            }
            retryCurlTurnAfterStartFailure(turn)
            return
        }
        if (hasCurlTarget(direction)) {
            curlTurnSequencer.restore(turn)
        } else {
            curlTurnSequencer.clear()
        }
    }

    private fun isCurlTargetReady(direction: PageAnimationController.Direction): Boolean =
        when (direction) {
            PageAnimationController.Direction.NEXT -> slotManager.getNextSlot().isLoaded
            PageAnimationController.Direction.PREV -> {
                val current = slotManager.getCurSlot()
                !(current.chapterIndex == 0 && current.pageIndex == 0) &&
                    slotManager.getPrevSlot().isLoaded
            }
            PageAnimationController.Direction.NONE -> false
        }

    private fun hasCurlTarget(direction: PageAnimationController.Direction): Boolean =
        when (direction) {
            // A chapter boundary may not have a known page count yet. Keep
            // the curl intent queued while PageSlotManager prepares the next
            // non-empty chapter instead of dropping the gesture as a book end.
            PageAnimationController.Direction.NEXT -> slotManager.hasPotentialNextPage()
            PageAnimationController.Direction.PREV -> {
                val current = slotManager.getCurSlot()
                current.pageIndex > 0 || current.chapterIndex > 0
            }
            PageAnimationController.Direction.NONE -> false
        }

    private fun recordCurlTurnIntent(
        direction: PageAnimationController.Direction,
        preloaded: Boolean
    ) {
        val current = slotManager.getCurSlot()
        val target = when (direction) {
            PageAnimationController.Direction.NEXT -> slotManager.getNextSlot()
            PageAnimationController.Direction.PREV -> slotManager.getPrevSlot()
            PageAnimationController.Direction.NONE -> return
        }
        ReaderPageTurnPerformance.beginIntent(
            preloaded = preloaded,
            crossChapter = target.chapterIndex != current.chapterIndex
        )
    }

    private fun directionForCurlSwipe(deltaX: Float): PageAnimationController.Direction {
        val physicalNext = deltaX < 0f
        val logicalNext = if (currentWritingMode.isVertical) !physicalNext else physicalNext
        return if (logicalNext) {
            PageAnimationController.Direction.NEXT
        } else {
            PageAnimationController.Direction.PREV
        }
    }

    /** Replays a non-curl turn that was accepted while NEXT was still loading. */
    private fun drainPendingPageTurn() {
        if (animationController is CurlPageAnim) return
        val direction = pendingPageTurnDirection ?: return
        if (isJumpSettling) return
        if (animationController.isRunning || animationController.isDragging) {
            // A slot can finish while a rejected gesture is still bouncing
            // back. Re-check on the next frame so the queued turn is not lost
            // when the one-shot slot-ready callback arrives too early.
            postOnAnimation(::drainPendingPageTurn)
            return
        }
        val ready = when (direction) {
            PageAnimationController.Direction.NEXT -> slotManager.getNextSlot().isLoaded
            PageAnimationController.Direction.PREV -> slotManager.getPrevSlot().isLoaded
            PageAnimationController.Direction.NONE -> false
        }
        if (!ready) {
            if (direction == PageAnimationController.Direction.NEXT &&
                slotManager.hasPotentialNextPage()
            ) {
                return
            }
            pendingPageTurnDirection = null
            return
        }
        pendingPageTurnDirection = null
        if (isPageTurnBlockedAtBoundary(direction)) return
        clearCurrentSelection()
        val target = if (direction == PageAnimationController.Direction.NEXT) {
            slotManager.getNextSlot()
        } else {
            slotManager.getPrevSlot()
        }
        val current = slotManager.getCurSlot()
        ReaderPageTurnPerformance.beginIntent(
            preloaded = true,
            crossChapter = target.chapterIndex != current.chapterIndex
        )
        ReaderPageTurnPerformance.markVisualStarted()
        startTapAnimation(direction)
    }

    private fun directionForPageSwipe(
        deltaX: Float,
        deltaY: Float
    ): PageAnimationController.Direction {
        if (animationController is ScrollPageAnim) {
            return when {
                deltaY < 0f -> PageAnimationController.Direction.NEXT
                deltaY > 0f -> PageAnimationController.Direction.PREV
                else -> PageAnimationController.Direction.NONE
            }
        }
        return directionForCurlSwipe(deltaX)
    }

    private fun isPageTurnBlockedAtBoundary(
        direction: PageAnimationController.Direction
    ): Boolean {
        val curl = animationController as? CurlPageAnim
        if (curl?.isRunning == true &&
            curl.currentDirection != PageAnimationController.Direction.NONE &&
            curl.currentDirection != direction
        ) {
            // Resolve the request against the page that the committed curl is
            // about to expose. This keeps reverse input usable at either book edge.
            return false
        }
        return when (direction) {
            PageAnimationController.Direction.PREV -> slotManager.isAtBookStart()
            PageAnimationController.Direction.NEXT -> slotManager.isAtBookEnd()
            PageAnimationController.Direction.NONE -> false
        }
    }

    private fun clearCurlTurnIntent() {
        curlTurnSequencer.clear()
        pendingPageTurnDirection = null
        rvDeferredCurlGesture = false
        rvIsHandlingPageGesture = false
        rvBoundaryGestureSuppressed = false
    }

    private fun beginJumpSettling(): Long {
        val generation = jumpGenerationGate.begin()
        jumpTimeoutRunnable?.let(::removeCallbacks)
        jumpTimeoutRunnable = Runnable {
            finishJumpSettling(generation, result = null)
        }.also { postDelayed(it, JUMP_SETTLE_TIMEOUT_MS) }
        return generation
    }

    private fun finishJumpSettling(
        generation: Long,
        result: PageSlotManager.CurrentSlotLoadResult?
    ) {
        if (!jumpGenerationGate.resolve(generation)) return
        jumpTimeoutRunnable?.let(::removeCallbacks)
        jumpTimeoutRunnable = null
        val currentSlotReady = slotManager.getCurSlot().isLoaded
        if (!shouldResumeQueuedCurlAfterJump(result, currentSlotReady)) {
            clearCurlTurnIntent()
            animationController.abortAnim()
            return
        }
        // A curl swipe can be queued while the target chapter is settling. The
        // slot-ready callback may have run before this generation was resolved,
        // so explicitly retry the queue after unlocking page turns.
        post(::drainPendingCurlTurns)
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
                textAlignment = currentTextAlignment,
                fontType = currentFontType,
                customFontPath = currentCustomFontPath,
                marginLeftDp = currentMarginLeftDp,
                marginRightDp = currentMarginRightDp,
                marginTopDp = currentMarginTopDp,
                marginBottomDp = currentMarginBottomDp,
                topOverlayInsetDp = currentTopOverlayInsetDp,
                bottomOverlayInsetDp = currentBottomOverlayInsetDp,
                paragraphSpacingDp = currentParagraphSpacingDp,
                firstLineIndent = currentFirstLineIndent,
                bodyFontWeight = currentBodyFontWeight,
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
        val startLine = layout.getLineForOffset(selStart)
        val endLine = layout.getLineForOffset(selEnd.coerceAtMost(spannable.length - 1))
        val pageOffsetY = pageView.getPageVerticalOffset()
        val topY = (tv.top + tv.paddingTop + layout.getLineTop(startLine)).toFloat() + pageOffsetY
        val bottomY = (tv.top + tv.paddingTop + layout.getLineBottom(endLine)).toFloat() + pageOffsetY
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
