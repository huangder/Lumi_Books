package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.selectionRangeAt

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.reader.engine.ReaderSelectionMagnifier
import com.huangder.lumibooks.ui.reader.engine.ReaderMagnifierSink
import com.huangder.lumibooks.util.markdown.MarkdownLocatorMigration
import com.huangder.lumibooks.util.markdown.native.*
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.math.abs

internal fun markdownPaginationSignature(identity: String, style: MarkdownPageStyle,
    density: Float, scaledDensity: Float): String = listOf(
    "markdown-layout-2", identity, style.render.width, style.render.height, style.render.fontSize,
    style.render.paragraphSpacing, style.render.firstLineIndent, style.lineHeight,
    style.letterSpacing, style.alignment, style.justify, density, scaledDensity
).joinToString("|")

/** Native document surface; menus, dialogs, themes and navigation remain owned by ReaderScreen. */
@Composable
internal fun MarkdownNativeReader(
    source: MarkdownBlockSource,
    state: ReaderUiState,
    typeface: Typeface,
    color: Int,
    paperColor: Int,
    dark: Boolean,
    continuous: Boolean,
    navigation: EpubNavigationRequest?,
    notes: List<Note>,
    ttsSentence: TtsSentencePosition?,
    selectionClearToken: Int,
    onPage: (Int, Int, String) -> Unit,
    onPageIndex: (List<Int>) -> Unit = {},
    onManualNavigation: () -> Unit = {},
    onSelection: (EpubSelectionInfo) -> Unit,
    onLink: (String) -> Unit,
    onMenu: () -> Unit,
    onSelectionCleared: () -> Unit = {},
    onSelectionDragging: (Boolean) -> Unit = {},
    onNavigation: (EpubNavigationResult) -> Unit,
    onTurnReady: (((Int) -> Unit)?) -> Unit,
    onTextReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    onPaginationReady: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPage by rememberUpdatedState(onPage)
    val latestPageIndex by rememberUpdatedState(onPageIndex)
    val latestManualNavigation by rememberUpdatedState(onManualNavigation)
    val latestSelection by rememberUpdatedState(onSelection)
    val latestLink by rememberUpdatedState(onLink)
    val latestMenu by rememberUpdatedState(onMenu)
    val latestSelectionCleared by rememberUpdatedState(onSelectionCleared)
    val latestSelectionDragging by rememberUpdatedState(onSelectionDragging)
    val latestNavigation by rememberUpdatedState(onNavigation)
    val latestPagination by rememberUpdatedState(onPaginationReady)
    val latestError by rememberUpdatedState(onError)
    val accent = com.huangder.lumibooks.ui.theme.AppColors.Accent.toArgb()
    val motion = com.huangder.lumibooks.ui.theme.LocalMotionEnabled.current
    key(source) {
    AndroidView(modifier = modifier, factory = { context ->
        MarkdownNativeSurface(context, source).apply {
            pageChanged = { page, count, locator -> latestPage(page, count, locator) }
            pageIndexChanged = { latestPageIndex(it) }
            manualNavigation = { latestManualNavigation() }
            selected = { latestSelection(it) }
            link = { latestLink(it) }
            menu = { latestMenu() }
            selectionCleared = { latestSelectionCleared() }
            selectionDragging = { latestSelectionDragging(it) }
            navigationFinished = { latestNavigation(it) }
            paginationReady = { latestPagination(it) }
            failed = { latestError(it) }
            onTurnReady { null }
            onTextReady { chapter, page -> if (chapter == 0) pageText(page) else null }
        }
    }, update = { view ->
        view.selectionAccent = if (state.eInkModeEnabled) color else accent
        view.paperColor = paperColor
        view.motionEnabled = motion && !state.eInkModeEnabled
        view.transition = state.pageTransition
        view.transitionDuration = com.huangder.lumibooks.ui.animation.VisualMotion.durationMillis(state.pageAnimationSettings.durationFor(state.pageTransition))
        view.configure(state, typeface, color, dark, continuous)
        view.setNotes(notes)
        view.setTts(ttsSentence)
        view.clearSelection(selectionClearToken)
        view.navigate(navigation)
    }, onRelease = { view -> view.release(); onTurnReady(null); onTextReady(null) })
    }
}

private data class SurfaceConfig(val fontSize: Float, val typeface: Typeface, val color: Int, val dark: Boolean,
    val lineHeight: Float, val spacing: Float, val paragraph: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float, val continuous: Boolean,
    val indent: Float, val alignment: com.huangder.lumibooks.domain.model.ReaderTextAlignment,
    val edgeTapMode: com.huangder.lumibooks.domain.model.ReaderEdgeTapMode, val eInk: Boolean)

/** Ignore padding and line-end whitespace: TextView's offset API clamps both to text. */
internal fun markdownAnnotationAt(view: TextView, offset: Int, notes: List<Note>, x: Float, y: Float): Note? {
    val position = offset + (markdownTextOffsetAt(view, x, y) ?: return null)
    return notes.asSequence().filter {
        !it.isStale && it.chapterIndex == 0 && it.type in setOf("highlight", "underline") &&
            position >= it.startPosition && position < it.endPosition
    }.minByOrNull { it.endPosition - it.startPosition }
}

internal fun markdownTextOffsetAt(view: TextView, x: Float, y: Float): Int? {
    val layout = view.layout ?: return null
    val localX = x - view.totalPaddingLeft + view.scrollX
    val localY = y - view.totalPaddingTop + view.scrollY
    if (localY < 0 || localY >= layout.height) return null
    val line = layout.getLineForVertical(localY.toInt())
    if (localX < layout.getLineLeft(line) || localX >= layout.getLineRight(line)) return null
    return layout.getOffsetForHorizontal(line, localX)
}

internal fun estimateMarkdownBlockHeight(block: MarkdownBlock, width: Int, fontSize: Float,
    lineHeight: Float, paragraphSpacing: Int): Int {
    val headingScale = if (block.kind == "heading") when (block.level) {
        1 -> 1.7f
        2 -> 1.45f
        3 -> 1.25f
        else -> 1.1f
    } else 1f
    val charScale = if (block.kind == "code") 0.6f else 1f
    val size = (fontSize * headingScale).coerceAtLeast(1f)
    val leadingMargin = when {
        block.kind == "table" -> (width / block.tableColumnCount.coerceAtLeast(1)).toFloat()
        block.kind == "quote" -> fontSize * 0.6f
        block.kind == "paragraph" && block.label.isNotBlank() -> fontSize * 2f
        block.level > 0 -> fontSize * block.level
        else -> 0f
    }
    val available = (width - leadingMargin - fontSize * 0.5f).coerceAtLeast(size)
    val lineWidth = available / size
    var lines = 1
    var used = 0f
    var index = 0
    while (index < block.text.length) {
        val codePoint = Character.codePointAt(block.text, index)
        index += Character.charCount(codePoint)
        if (codePoint == '\n'.code) {
            lines++
            used = 0f
            continue
        }
        val charWidth = when {
            Character.isWhitespace(codePoint) -> 0.33f
            codePoint < 128 -> if (Character.isUpperCase(codePoint)) 0.62f else 0.54f
            Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(codePoint) == Character.ENCLOSING_MARK.toInt() -> 0f
            else -> 1f
        } * charScale
        if (used > 0f && used + charWidth > lineWidth) {
            lines++
            used = 0f
        }
        used += charWidth
    }
    val line = (size * lineHeight).toInt().coerceAtLeast(1)
    return (lines * line + paragraphSpacing).coerceAtLeast(line)
}

internal fun markdownWordRange(text: CharSequence, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    val at = offset.coerceIn(0, text.length - 1)
    if (text[at].isWhitespace() || text[at] == '\uFFFC') return null
    val breaker = android.icu.text.BreakIterator.getWordInstance()
    breaker.setText(text.toString())
    val start = breaker.preceding(at + 1).coerceAtLeast(0)
    val end = breaker.following(at).takeIf { it >= 0 } ?: text.length
    return (start until end).takeIf { end > start }
}

internal class MarkdownNativeSurface(context: Context, private val source: MarkdownBlockSource) : FrameLayout(context) {
    var selectionAccent: Int = android.graphics.Color.BLACK
    var paperColor: Int = android.graphics.Color.WHITE
    var motionEnabled = true
    var transition = "slide"
    var transitionDuration = 260
    private var transitionView: MarkdownPageTransition? = null
    private var turnPending = false
    private var interactiveJob: Job? = null
    private var interactiveDown: MotionEvent? = null
    private var interactiveLatest: MotionEvent? = null
    var pageChanged: (Int, Int, String) -> Unit = { _, _, _ -> }
    var pageIndexChanged: (List<Int>) -> Unit = {}
    var manualNavigation: () -> Unit = {}
    var selected: (EpubSelectionInfo) -> Unit = {}
    var link: (String) -> Unit = {}
    var menu: () -> Unit = {}
    /** Called when a user taps outside an active Markdown selection. */
    var selectionCleared: () -> Unit = {}
    var selectionDragging: (Boolean) -> Unit = {}
    var navigationFinished: (EpubNavigationResult) -> Unit = {}
    var paginationReady: (Boolean) -> Unit = {}
    var failed: (String) -> Unit = {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
        failed(error.localizedMessage ?: "Markdown rendering failed")
    })
    private var config: SurfaceConfig? = null
    private var provider: MarkdownPageProvider? = null
    private var renderJob: Job? = null
    private var countJob: Job? = null
    private var pageIndexCache: ReaderPageIndexCache? = null
    private var paginationBook: com.huangder.lumibooks.domain.model.Book? = null
    private var paginationIdentity = ""
    private var current = 0
    private var currentEnd = 0
    private var generation = 0
    // Invalidates block work that was started for a previous viewport or jump.  A cancelled
    // coroutine can still finish a non-suspending layout call, so UI writes also check this token.
    private var windowGeneration = 0L
    private var requestId: Long? = null
    private var pendingRequest: EpubNavigationRequest? = null
    private var selectionToken = -1
    private var notes: List<Note> = emptyList()
    private var tts: TtsSentencePosition? = null
    private var searchRange: IntRange? = null
    private val history = ArrayDeque<Int>()
    private var pageStarts: List<Int> = emptyList()
    private val livePageStarts = mutableMapOf<Int, Int>()
    private var livePage = 0
    private var recycler: RecyclerView? = null
    private var continuousAdapter: BlockAdapter? = null
    private var continuousManager: LinearLayoutManager? = null
    private var continuousWindowJob: Job? = null
    private var restoreFrame: Runnable? = null
    private val headingOffsets = source.document.outline.associate { it.anchor to it.offset }
    private var progressPostPending = false
    private var scrolling = false
    private var selectionRefreshPosted = false
    private var initialRestore = true
    private var savedRestore: String? = null
    private var savedFraction = 0f
    private var downX = 0f
    private var downY = 0f
    private val touchSession = MarkdownTouchSession(ViewConfiguration.get(context).scaledTouchSlop,
        ViewConfiguration.getLongPressTimeout())
    private val textOwners = java.util.WeakHashMap<TextView, Int>()
    private val tableScrolls = mutableMapOf<Int, MutableList<HorizontalScrollView>>()
    private var syncingTableScroll = false
    private var selectionStartGlobal = -1
    private var selectionEndGlobal = -1
    private var pendingSelectionLongPress: Runnable? = null
    private var longPressSelectionRange = LongPressSelectionRange.DEFAULT
    private var draggingHandle = 0
    private var selectionGestureActive = false
    private var handleGrabX = 0f
    private var handleGrabY = 0f
    private val selectionMagnifier = ReaderSelectionMagnifier(this)
    private var lastSelectionPageTurn = 0L
    private val blockRenderPayload = "markdown-render"
    private val handlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private class SelectedRangeSpan(color: Int) : BackgroundColorSpan(color)
    private class ReadingRangeSpan(color: Int) : BackgroundColorSpan(color)

    private data class SelectionAnchor(val point: android.graphics.PointF, val lineHeight: Float)
    private fun selectionAnchor(offset: Int, trailing: Boolean = false): SelectionAnchor? {
        val location = IntArray(2); getLocationOnScreen(location)
        for ((view, start) in textOwners) {
            if (!view.isAttachedToWindow || view.text.isEmpty()) continue
            if (if (trailing) offset !in (start + 1)..(start + view.text.length) else offset !in start until (start + view.text.length)) continue
            val layout = view.layout ?: continue
            val local = (offset - start).coerceIn(0, view.text.length)
            val screen = IntArray(2); view.getLocationOnScreen(screen)
            val line = layout.getLineForOffset(if (trailing && local > 0) local - 1 else local)
            val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
            val x = if (trailing && local == layout.getLineStart(line + 1)) {
                if (layout.getParagraphDirection(line) < 0) layout.getLineLeft(line) else layout.getLineRight(line)
            } else layout.getPrimaryHorizontal(local)
            val point = android.graphics.PointF(screen[0] - location[0] + view.totalPaddingLeft - view.scrollX + x,
                screen[1] - location[1] + view.totalPaddingTop - view.scrollY + layout.getLineBottom(line).toFloat())
            if (point.y in paddingTop.toFloat()..(height - paddingBottom).toFloat()) return SelectionAnchor(point, lineHeight)
        }
        return null
    }
    private fun selectionPoint(offset: Int, trailing: Boolean = false) = selectionAnchor(offset, trailing)?.point
    internal fun setMagnifierSinkForTest(sink: ReaderMagnifierSink) { selectionMagnifier.sinkOverride = sink }
    private fun showHandleMagnifier() {
        val anchor = selectionAnchor(if (draggingHandle == 1) selectionStartGlobal else selectionEndGlobal, draggingHandle == 2) ?: return
        selectionMagnifier.showAt(anchor.point.x, anchor.point.y - anchor.lineHeight / 2f, anchor.lineHeight)
    }
    private fun finishHandleDrag() {
        if (draggingHandle == 0) return
        draggingHandle = 0
        selectionMagnifier.dismiss()
        parent?.requestDisallowInterceptTouchEvent(false)
        selectionDragging(false)
    }
    private fun applySelection() {
        for ((view, start) in textOwners) {
            // Compose's AndroidViewsHandler can have an empty global visible rect
            // while it draws this AndroidView normally. Refresh attached buffers,
            // including RecyclerView's small cache, without querying that ancestor.
            if (!view.isAttachedToWindow) continue
            val text = view.text as? android.text.Spannable ?: continue
            applyMarkdownAnnotationSpans(text, start, notes)
            text.getSpans(0, text.length, SelectedRangeSpan::class.java).forEach(text::removeSpan)
            text.getSpans(0, text.length, ReadingRangeSpan::class.java).forEach(text::removeSpan)
            listOfNotNull(tts?.let { it.startOffset until it.endOffset }, searchRange).forEach { range ->
                val a = maxOf(start, range.first); val b = minOf(start + text.length, range.last + 1)
                if (a < b) text.setSpan(ReadingRangeSpan(0x557fbb77), a - start, b - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val a = maxOf(start, selectionStartGlobal)
            val b = minOf(start + text.length, selectionEndGlobal)
            if (a < b) text.setSpan(SelectedRangeSpan((selectionAccent and 0x00ffffff) or 0x55000000), a - start, b - start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.invalidate()
        }
        invalidate()
    }
    private fun scheduleSelectionRefresh() {
        if (selectionRefreshPosted) return
        selectionRefreshPosted = true
        postOnAnimation {
            selectionRefreshPosted = false
            if (!scrolling) applySelection()
        }
    }
    private fun publishSelection(annotationOnly: Boolean = false) {
        val start = selectionStartGlobal; val end = selectionEndGlobal
        if (start < 0 || end <= start) return
        val first = selectionPoint(start); val last = selectionPoint(end, true)
        val location = IntArray(2); getLocationInWindow(location)
        val left = location[0] + (first?.x ?: paddingLeft.toFloat())
        val right = location[0] + (last?.x ?: (width - paddingRight).toFloat())
        val top = location[1] + (first?.y ?: paddingTop.toFloat()) - (provider?.style?.render?.fontSize ?: 0f)
        val bottom = location[1] + (last?.y ?: (height - paddingBottom).toFloat())
        selected(EpubSelectionInfo(source.document.text.substring(start, end), start, end, locator(start, end),
            MarkdownLocatorMigration.locator(source.document.text, MarkdownLocatorMigration.Match(start, end), true),
            (left + right) / 2, top, left, top, right, bottom, annotationOnly))
    }
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        if (selectionStartGlobal < 0) return
        handlePaint.color = selectionAccent
        val radius = 7f * resources.displayMetrics.density
        listOf(selectionPoint(selectionStartGlobal), selectionPoint(selectionEndGlobal, true)).forEach { point ->
            if (point != null) canvas.drawCircle(point.x, point.y + radius, radius, handlePaint)
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchSession.record(event, scrolling || (recycler?.scrollState ?: RecyclerView.SCROLL_STATE_IDLE) != RecyclerView.SCROLL_STATE_IDLE)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            selectionGestureActive = false
            // Clear stale disallow-intercept flags before RecyclerView sees this gesture.
            recycler?.requestDisallowInterceptTouchEvent(false)
            // Keep Compose ancestors from taking over this AndroidView's vertical gesture;
            // the nested RecyclerView still receives and arbitrates its own child events.
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) cancelSelectionLongPress()
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !touchSession.isSelectionCandidate() &&
            !selectionGestureActive) {
            cancelSelectionLongPress()
            if (draggingHandle == 0) {
                recycler?.requestDisallowInterceptTouchEvent(false)
            }
        }
        // TextView's Editor can keep disallow-intercept set after the native long
        // press. Owned handles must be captured before ViewGroup applies that flag.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && beginHandleDrag(event)) return onTouchEvent(event)
        if (draggingHandle != 0) return onTouchEvent(event)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            selectionGestureActive = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
    private fun cancelSelectionLongPress() {
        pendingSelectionLongPress?.let(::removeCallbacks)
        pendingSelectionLongPress = null
    }
    private fun scheduleSelectionLongPress(view: TextView, offset: Int, x: Float, y: Float, downTime: Long) {
        val local = markdownTextOffsetAt(view, x, y) ?: return
        val touched = view.text.getOrNull(local) ?: return
        if (touched.isWhitespace() || touched == '\uFFFC') return
        val stamp = windowGeneration
        val press = Runnable {
            pendingSelectionLongPress = null
            if (stamp != windowGeneration || !view.isAttachedToWindow || draggingHandle != 0 ||
                !touchSession.canStartSelection(android.os.SystemClock.uptimeMillis(), downTime)) return@Runnable
            val line = view.layout?.getLineForOffset(local)
            val visualLine = line?.let { view.layout.getLineStart(it) + offset until view.layout.getLineEnd(it) + offset }
            val range = source.document.text.selectionRangeAt(offset + local, longPressSelectionRange, visualLine) ?: return@Runnable
            if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("MarkdownNative", "long-press accepted range=${range.first}..${range.last}")
            selectionStartGlobal = range.first
            selectionEndGlobal = range.last + 1
            selectionGestureActive = true
            android.text.Selection.removeSelection(view.text as android.text.Spannable)
            applySelection()
            // Let RecyclerView own movement until a long press is actually accepted.
            view.parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            publishSelection()
        }
        pendingSelectionLongPress = press
        postDelayed(press, ViewConfiguration.getLongPressTimeout().toLong())
    }
    private fun beginHandleDrag(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && selectionStartGlobal >= 0) {
            val hit = 28 * resources.displayMetrics.density
            val radius = 7 * resources.displayMetrics.density
            val candidates = listOf(selectionAnchor(selectionStartGlobal), selectionAnchor(selectionEndGlobal, true))
                .mapIndexedNotNull { i, anchor -> anchor?.let {
                    val dx = event.x - it.point.x; val dy = event.y - (it.point.y + radius)
                    if (abs(dx) <= hit && abs(dy) <= hit) Triple(i + 1, it, dx * dx + dy * dy) else null
                } }
            val handle = candidates.minByOrNull { it.third }
            if (handle != null) {
                draggingHandle = handle.first
                handleGrabX = event.x - handle.second.point.x
                handleGrabY = event.y - (handle.second.point.y - handle.second.lineHeight / 2f)
                recycler?.stopScroll()
                parent?.requestDisallowInterceptTouchEvent(true)
                selectionDragging(true)
                showHandleMagnifier()
                return true
            }
        }
        return false
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return draggingHandle != 0 || super.onInterceptTouchEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        transitionView?.let { transition ->
            if (transition.isInteractive) return transition.dispatchTouchEvent(event)
        }
        if (draggingHandle == 0) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; interactiveDown?.recycle(); interactiveDown = MotionEvent.obtain(event); return true }
                MotionEvent.ACTION_MOVE -> {
                    if (config?.continuous != true && transitionView == null && interactiveJob == null &&
                        (abs(event.x - downX) > 12 * resources.displayMetrics.density || abs(event.y - downY) > 12 * resources.displayMetrics.density)) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (abs(dx) > abs(dy) && abs(dx) > 12 * resources.displayMetrics.density) {
                            interactiveLatest?.recycle(); interactiveLatest = MotionEvent.obtain(event)
                            prepareInteractive(if (dx < 0) 1 else -1)
                            return true
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (recycler != null && !touchSession.isTap(event)) return true
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val density = resources.displayMetrics.density
                    if (transitionView?.isInteractive == true) return transitionView!!.dispatchTouchEvent(event)
                    interactiveJob?.cancel(); interactiveJob = null
                    if (selectionStartGlobal >= 0) {
                        selectionStartGlobal = -1
                        selectionEndGlobal = -1
                        draggingHandle = 0
                        applySelection()
                        selectionCleared()
                        return true
                    }
                    if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()) return true
                    if (config?.continuous != true && abs(dx) > 60 * density && abs(dx) > abs(dy)) turn(if (dx < 0) 1 else -1)
                    else if (config?.continuous != true && abs(dy) > 60 * density && abs(dy) > abs(dx)) turn(if (dy < 0) 1 else -1)
                    else if (abs(dx) < 12 * density && abs(dy) < 12 * density) {
                        if (config?.continuous == true || event.x in width * .25f..width * .75f) {
                            if (touchSession.menuAllowed) menu()
                        }
                        else {
                            val action = if (event.x > width / 2) config?.edgeTapMode?.rightAction else config?.edgeTapMode?.leftAction
                            turn(if (action == com.huangder.lumibooks.domain.model.ReaderEdgeTapAction.NEXT_PAGE) 1 else -1)
                        }
                    }
                    return true
                }
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val screen = IntArray(2); getLocationOnScreen(screen)
            val targetX = screen[0] + event.x - handleGrabX
            val targetY = screen[1] + event.y - handleGrabY
            var nearest: Pair<TextView, Int>? = null
            var distance = Float.MAX_VALUE
            for ((view, start) in textOwners) {
                if (!view.isAttachedToWindow) continue
                val rect = android.graphics.Rect()
                if (!view.getGlobalVisibleRect(rect)) continue
                val dx = (rect.left - targetX).coerceAtLeast(0f) + (targetX - rect.right).coerceAtLeast(0f)
                val dy = (rect.top - targetY).coerceAtLeast(0f) + (targetY - rect.bottom).coerceAtLeast(0f)
                val squared = dx * dx + dy * dy
                if (squared < distance) { nearest = view to start; distance = squared }
            }
            nearest?.let { (view, start) ->
                val position = IntArray(2); view.getLocationOnScreen(position)
                val offset = start + view.getOffsetForPosition(targetX - position[0], targetY - position[1])
                if (draggingHandle == 1) selectionStartGlobal = offset.coerceIn(0, selectionEndGlobal - 1)
                else selectionEndGlobal = offset.coerceIn(selectionStartGlobal + 1, source.document.text.length)
                applySelection()
            }
            showHandleMagnifier()
            val edge = 32 * resources.displayMetrics.density
            val direction = if (event.y > height - paddingBottom - edge) 1 else if (event.y < paddingTop + edge) -1 else 0
            if (direction != 0) {
                if (recycler != null) recycler?.scrollBy(0, (direction * edge / 3).toInt())
                else if (android.os.SystemClock.uptimeMillis() - lastSelectionPageTurn > 700) {
                    lastSelectionPageTurn = android.os.SystemClock.uptimeMillis(); turn(direction)
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishHandleDrag(); publishSelection()
        }
        return true
    }

    private fun prepareInteractive(direction: Int) {
        val engine = provider ?: return
        val down = interactiveDown ?: return
        val targetOffset = if (direction > 0) currentEnd else history.lastOrNull() ?: return
        interactiveJob = scope.launch {
            try {
                val page = engine.page(targetOffset)
                ensureActive()
                val before = if (motionEnabled && transition != "none" && childCount > 0) snapshot() else null
                current = page.start; currentEnd = page.end
                removeAllViews()
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                page.fragments.forEachIndexed { i, part ->
                    if (i > 0 && !(part.block.kind == "table" && page.fragments[i - 1].block.tableId == part.block.tableId))
                        column.addView(Space(context), LinearLayout.LayoutParams(1, engine.style.render.paragraphSpacing))
                    column.addView(if (part.cells.isEmpty()) blockView(part.text, part.block.start + part.localStart, part.block) else tableView(part.block, part.cells),
                        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, part.height))
                }
                applySelection()
                if (before != null) {
                    column.measure(MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - paddingTop - paddingBottom, MeasureSpec.EXACTLY))
                    column.layout(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
                    val overlay = MarkdownPageTransition(context, before, snapshot(), paperColor)
                    transitionView = overlay
                    addView(overlay, LayoutParams(width, height).apply { leftMargin = -paddingLeft; topMargin = -paddingTop })
                    overlay.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                    overlay.layout(0, 0, width, height)
                    overlay.finished = { post { if (transitionView === overlay) stopTransition() } }
                    overlay.beginInteractive(transition, transitionDuration, direction > 0, down)
                    interactiveLatest?.let { overlay.dispatchTouchEvent(it) }
                }
                turnPending = false
                publish()
            } finally { interactiveJob = null }
        }
    }

    fun configure(state: ReaderUiState, typeface: Typeface, color: Int, dark: Boolean, continuous: Boolean) {
        longPressSelectionRange = state.longPressSelectionRange
        paginationBook = state.book
        val previousIdentity = paginationIdentity
        paginationIdentity = state.paginationContentKey() + "|" +
            readerPaginationFontKey(context, state.fontType, state.customFontPath, state.bodyFontWeight)
        val value = SurfaceConfig(state.fontSize, typeface, color, dark, state.lineHeight,
            state.letterSpacing, state.paragraphSpacing, state.marginLeftDp, state.marginTopDp,
            state.marginRightDp, state.marginBottomDp, continuous, state.firstLineIndent, state.textAlignment, state.readerEdgeTapMode, state.eInkModeEnabled)
        if (initialRestore) { savedRestore = state.epubLocatorJson; savedFraction = state.pendingPageFraction }
        if (value == config && previousIdentity == paginationIdentity) return
        val colorOnly = previousIdentity == paginationIdentity && config?.copy(color = value.color,
            dark = value.dark, edgeTapMode = value.edgeTapMode, eInk = value.eInk, continuous = value.continuous) == value
        config = value
        rebuild(preservePagination = colorOnly)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); if (w != oldw || h != oldh) rebuild() }
    private fun rebuild(preservePagination: Boolean = false) {
        cancelSelectionLongPress()
        finishHandleDrag()
        selectionMagnifier.dismiss()
        val c = config ?: return
        if (width <= 0 || height <= 0) return
        stopTransition()
        removeCallbacks(progressPublisher); progressPostPending = false
        renderJob?.cancel(); generation++
        closePageIndex()
        windowGeneration++
        cancelContinuousRenders(clearViews = true)
        recycler?.adapter = null; recycler = null
        provider?.clear()
        if (!preservePagination) { history.clear(); pageStarts = emptyList(); livePageStarts.clear(); livePage = 0 }
        paginationReady(pageStarts.isNotEmpty())
        pageIndexChanged(pageStarts)
        val density = resources.displayMetrics.density
        val scaled = resources.displayMetrics.scaledDensity
        setPadding((c.left * density).toInt(), (c.top * density).toInt(), (c.right * density).toInt(), (c.bottom * density).toInt())
        val style = MarkdownRenderStyle((width - paddingLeft - paddingRight).coerceAtLeast(1),
            (height - paddingTop - paddingBottom).coerceAtLeast(1), c.fontSize * scaled, c.color, c.dark, (c.paragraph * density).toInt(), c.indent)
        val alignment = when (c.alignment) {
            com.huangder.lumibooks.domain.model.ReaderTextAlignment.CENTER -> android.text.Layout.Alignment.ALIGN_CENTER
            com.huangder.lumibooks.domain.model.ReaderTextAlignment.RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
            else -> android.text.Layout.Alignment.ALIGN_NORMAL
        }
        provider = MarkdownPageProvider(source, MarkdownPageStyle(style, c.typeface, c.lineHeight, c.spacing * density / style.fontSize,
            alignment, c.alignment in setOf(com.huangder.lumibooks.domain.model.ReaderTextAlignment.NATURAL,
                com.huangder.lumibooks.domain.model.ReaderTextAlignment.JUSTIFY)))
        if (initialRestore) {
            current = locatorOffset(savedRestore) ?: (savedFraction * source.document.text.length).toInt()
            initialRestore = false
        }
        showContinuous()
        pendingRequest?.let { requestId = null; navigate(it) }
    }
    private fun locatorOffset(raw: String?): Int? = runCatching {
        val json = JSONObject(raw ?: return null)
        if (json.optInt("version") == 3 && json.optString("href") == "document.md") json.optInt("textPosition")
        else {
            val quote = json.optString("exact")
            if (quote.isNotBlank()) MarkdownLocatorMigration.match(source.document.text, quote, raw, json.optInt("textPosition"))?.start
            else (json.optDouble("progression", savedFraction.toDouble()) * source.document.text.length).toInt()
        }
    }.getOrNull()?.coerceIn(0, source.document.text.length)
    private fun locator(start: Int, end: Int = start): String = MarkdownLocatorMigration.locator(source.document.text,
        MarkdownLocatorMigration.Match(start.coerceIn(0, source.document.text.length), end.coerceIn(start, source.document.text.length)))
    private fun publish() {
        removeCallbacks(progressPublisher)
        progressPostPending = false
        lastPublishedOffset = current
        lastPublishAt = android.os.SystemClock.uptimeMillis()
        val found = pageStarts.binarySearch(current).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
        val count = if (pageStarts.isEmpty()) (livePage + 2).coerceAtLeast(1) else pageStarts.size
        val page = if (pageStarts.isEmpty()) livePage else found
        livePageStarts[page] = current
        pageChanged(page.coerceIn(0, count - 1), count, locator(current))
    }
    private fun showPage(offset: Int, complete: (() -> Unit)? = null, direction: Int = 0) {
        val engine = provider ?: return
        renderJob?.cancel()
        renderJob = scope.launch {
            val page = engine.page(offset)
            ensureActive()
            val before = if (direction != 0 && motionEnabled && transition != "none" && childCount > 0) snapshot() else null
            current = page.start; currentEnd = page.end
            android.util.Log.i("MarkdownNative", "page-ready start=${page.start} end=${page.end} fragments=${page.fragments.size}")
            removeAllViews()
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            page.fragments.forEachIndexed { i, part ->
                if (i > 0 && !(part.block.kind == "table" && page.fragments[i - 1].block.tableId == part.block.tableId))
                    column.addView(Space(context), LinearLayout.LayoutParams(1, engine.style.render.paragraphSpacing))
                column.addView(if (part.cells.isEmpty()) blockView(part.text, part.block.start + part.localStart, part.block)
                    else tableView(part.block, part.cells),
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, part.height))
            }
            applySelection()
            if (before != null) {
                column.measure(MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height - paddingTop - paddingBottom, MeasureSpec.EXACTLY))
                column.layout(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
                val after = snapshot()
                val overlay = MarkdownPageTransition(context, before, after, paperColor)
                transitionView = overlay
                addView(overlay, LayoutParams(width, height).apply { leftMargin = -paddingLeft; topMargin = -paddingTop })
                overlay.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                overlay.layout(0, 0, width, height)
                overlay.finished = { post { if (transitionView === overlay) stopTransition() } }
                overlay.start(transition, transitionDuration, direction > 0)
            }
            turnPending = false
            publish(); complete?.invoke()
            // One neighbour is prepared after first content. No whole-document layout is retained.
            if (page.end < engine.length) launch { engine.page(page.end) }
            if (countJob == null && pageStarts.isEmpty()) buildPageIndex(engine)
        }
    }
    private fun buildPageIndex(engine: MarkdownPageProvider) {
        val stamp = generation
        val book = paginationBook
        val style = engine.style
        val signature = markdownPaginationSignature(paginationIdentity, style,
            resources.displayMetrics.density, resources.displayMetrics.scaledDensity)
        countJob = scope.launch {
            val cache = book?.let { ReaderPageIndexCache.open(context, it, signature, 1) }
            if (stamp != generation || provider !== engine) { cache?.close(); return@launch }
            pageIndexCache = cache
            val saved = cache?.restored
            val savedStarts = saved?.chapterStarts?.get(0).orEmpty()
            if (saved?.totalPages != null && savedStarts.isNotEmpty() &&
                (savedStarts.last() < engine.length || engine.length == 0 && savedStarts == listOf(0))) {
                pageStarts = savedStarts; pageIndexChanged(pageStarts); publish(); paginationReady(true)
                return@launch
            }
            // Rendering is already visible; retain page boundaries only, not every block layout.
            val starts = ArrayList(savedStarts.takeIf { saved?.totalPages == null && it.size >= 2 &&
                it.last() <= engine.length }.orEmpty())
            // An incomplete artifact stores the next offset after its confirmed boundaries.
            var at = if (starts.isNotEmpty()) starts.removeAt(starts.lastIndex) else 0
            var lastSubmitted = android.os.SystemClock.uptimeMillis()
            var complete = false
            try {
                while (at < engine.length || starts.isEmpty()) {
                    ensureActive()
                    val page = engine.page(at)
                    if (stamp != generation || provider !== engine) return@launch
                    if (page.end <= at && engine.length > 0) return@launch
                    starts.add(at)
                    at = page.end
                    val now = android.os.SystemClock.uptimeMillis()
                    if (starts.size % 16 == 0 || now - lastSubmitted >= 400) {
                        cache?.submit(ReaderBookPages(chapterCounts = listOf(null), chapterStarts = mapOf(0 to (starts + at))))
                        lastSubmitted = now
                    }
                    if (engine.length == 0) break
                    yield()
                }
                complete = true
                pageStarts = starts.toList(); pageIndexChanged(pageStarts); publish(); paginationReady(true)
                cache?.submit(ReaderBookPages(chapterCounts = listOf(starts.size), chapterStarts = mapOf(0 to pageStarts)))
            } finally {
                // Even a cancelled scan commits its last confirmed boundary. The old
                // cache's signature remains fixed when a new layout starts.
                if (!complete && starts.isNotEmpty()) cache?.submit(ReaderBookPages(
                    chapterCounts = listOf(null), chapterStarts = mapOf(0 to (starts + at))))
                if (!complete) cache?.close()
            }
        }
    }
    suspend fun pageText(index: Int): EpubPageText? {
        val engine = provider ?: return null
        val at = pageStarts.getOrNull(index) ?: livePageStarts[index]
            ?: if (index == livePage + 1) currentEnd else return null
        val page = engine.page(at)
        livePageStarts[index] = at
        val text = source.document.text
        return EpubPageText(0, index, if (pageStarts.isEmpty()) index + 2 else pageStarts.size,
            text.substring(page.start, page.end), text, page.start, page.end)
    }
    private fun showContinuous() {
        removeAllViews()
        val list = RecyclerView(context)
        val manager = LinearLayoutManager(context)
        manager.initialPrefetchItemCount = 0
        list.layoutManager = manager
        continuousManager = manager
        val adapter = BlockAdapter()
        continuousAdapter = adapter
        list.adapter = adapter
        list.itemAnimator = null
        list.setItemViewCacheSize(1)
        list.setHasFixedSize(false)
        list.setItemViewCacheSize(1)
        list.recycledViewPool.setMaxRecycledViews(0, 6)
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        recycler = list
        restoreContinuous(current)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) manualNavigation()
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE
                if (scrolling) touchSession.onScrollStarted()
                if (!scrolling) {
                    scheduleSelectionRefresh()
                    scheduleProgressPublish(true)
                }
            }
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val index = manager.findFirstVisibleItemPosition()
                if (index < 0) return
                val indices = adapter.groupAt(index)
                val block = source.document.blocks.getOrNull(indices.firstOrNull() ?: return) ?: return
                val view = manager.findViewByPosition(index)
                if (restoringContinuous) return
                val tv = (view as? ViewGroup)?.getChildAt(0) as? TextView
                val layout = tv?.layout
                current = if (layout != null && view != null) {
                    block.start + layout.getLineStart(layout.getLineForVertical((-view.top - tv.totalPaddingTop).coerceAtLeast(0)))
                } else block.start
                currentEnd = source.document.blocks[indices.last()].end
                scheduleProgressPublish(false)
            }
        })
        currentEnd = source.document.blocks.getOrNull(source.document.index.blockAt(current) + 1)?.end ?: source.document.text.length
        // restoreContinuous publishes again after the target holder is laid out. Publishing the
        // old offset here races a TOC/progress request during the first attached frame and can
        // make the view model persist the document start before the requested jump lands.
        if (!restoringContinuous) publish()
    }
    private var restoringContinuous = false
    private var lastPublishedOffset = -1
    private var lastPublishAt = 0L
    private val progressPublishIntervalMs = 80L
    private val progressPublisher = object : Runnable {
        override fun run() {
            if (!progressPostPending) return
            val remaining = progressPublishIntervalMs - (android.os.SystemClock.uptimeMillis() - lastPublishAt)
            if (remaining > 0) {
                postDelayed(this, remaining)
                return
            }
            progressPostPending = false
            if (current != lastPublishedOffset) publish()
        }
    }
    private fun scheduleProgressPublish(immediate: Boolean) {
        if (restoringContinuous) return
        if (immediate) {
            removeCallbacks(progressPublisher)
            progressPostPending = false
            if (current != lastPublishedOffset) publish()
            return
        }
        if (progressPostPending) return
        progressPostPending = true
        postOnAnimation(progressPublisher)
    }
    private fun restoreContinuous(offset: Int, complete: (() -> Unit)? = null) {
        // Invalidate queued initial restores before checking attachment. Otherwise an old
        // offset-zero callback can cancel a newer TOC jump after the view becomes attached.
        windowGeneration++
        val restoreGeneration = windowGeneration
        restoreFrame?.let(::removeCallbacks)
        restoreFrame = null
        removeCallbacks(progressPublisher)
        progressPostPending = false
        // Keep already rendered holders as stable placeholders while RecyclerView reanchors.  A
        // nearby heading can remain on the same holder and does not need to be blanked first.
        cancelContinuousRenders(clearViews = false)
        renderJob?.cancel()
        restoringContinuous = true
        restoreContinuousWhenReady(offset, complete, restoreGeneration)
    }

    private fun restoreContinuousWhenReady(offset: Int, complete: (() -> Unit)?, restoreGeneration: Long) {
        if (restoreGeneration != windowGeneration || !scope.isActive) return
        val list = recycler
        val manager = list?.layoutManager as? LinearLayoutManager
        val engine = provider
        if (list == null || manager == null || engine == null || !list.isAttachedToWindow) {
            val retry = Runnable { restoreContinuousWhenReady(offset, complete, restoreGeneration) }
            restoreFrame = retry
            postOnAnimation(retry)
            return
        }
        restoreFrame = null
        list.stopScroll()
        renderJob = scope.launch {
            try {
                val index = source.document.index.blockAt(offset)
                val block = source.document.blocks[index]
                val layout = engine.block(index).second
                ensureActive()
                if (restoreGeneration != windowGeneration || recycler !== list) return@launch
                val local = (offset - block.start).coerceIn(0, block.text.length)
                val top = if (block.kind == "table") 0 else layout.getLineTop(layout.getLineForOffset(local))
                val item = continuousAdapter?.itemForBlock(index) ?: index
                current = offset
                // A distant RecyclerView jump can be issued before its first layout pass. Apply
                // the pending position again on animation frames and only commit once the target
                // group is actually in the laid out window. This also lets estimated heights
                // settle without losing the requested document coordinate.
                fun settle() {
                    if (restoreGeneration != windowGeneration || recycler !== list) return
                    list.stopScroll()
                    manager.scrollToPositionWithOffset(item, -top)
                    list.postOnAnimation {
                        if (restoreGeneration != windowGeneration || recycler !== list) return@postOnAnimation
                        val target = manager.findViewByPosition(item)
                        val visible = target != null &&
                            manager.findFirstVisibleItemPosition() <= item &&
                            manager.findLastVisibleItemPosition() >= item
                        val aligned = target != null &&
                            kotlin.math.abs(target.top + top) <= (24 * resources.displayMetrics.density).toInt()
                        val windowRendered = if (visible) {
                            (manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition())
                                .all { position ->
                                    val child = manager.findViewByPosition(position)
                                    val holder = child?.let { recycler?.getChildViewHolder(it) as? BlockHolder }
                                    holder?.renderedPosition == position && holder.job?.isActive != true
                                }
                        } else false
                        // A distant jump can need several layout passes while RecyclerView
                        // replaces estimated-height holders.  Never commit the navigation while
                        // the target is still absent: doing so clears the request and leaves a
                        // large document at its old position after the first few frames.
                        // The final heading may be aligned at the bottom by RecyclerView's
                        // end-of-document clamp rather than at the requested top coordinate.
                        if (!visible || (!aligned && list.canScrollVertically(1)) || !windowRendered || list.isLayoutRequested) {
                            // Cached holders may survive a nearby jump without onBind being
                            // called. Restart their cancelled work for the new window generation.
                            if (visible && !windowRendered) continuousAdapter?.refreshVisible(force = false)
                            settle()
                            return@postOnAnimation
                        }
                        restoringContinuous = false
                        continuousAdapter?.refreshVisible(force = false)
                        publish()
                        complete?.invoke()
                    }
                }
                list.postOnAnimation { settle() }
            } catch (cancelled: CancellationException) {
                if (restoreGeneration == windowGeneration) restoringContinuous = false
                throw cancelled
            }
        }
    }
    /** Stop all visible/prefetched block work before replacing the scroll anchor. */
    private fun cancelContinuousRenders(clearViews: Boolean) {
        continuousWindowJob?.cancel()
        continuousWindowJob = null
        val list = recycler ?: return
        for (i in 0 until list.childCount) {
            val holder = list.getChildViewHolder(list.getChildAt(i)) as? BlockHolder ?: continue
            holder.job?.cancel()
            holder.job = null
            holder.bindGeneration = -1L
            holder.renderedPosition = RecyclerView.NO_POSITION
            if (clearViews) holder.frame.removeAllViews()
        }
    }
    private fun snapshot(): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also {
        val canvas = android.graphics.Canvas(it)
        canvas.drawColor(paperColor)
        super.dispatchDraw(canvas)
    }
    private fun stopTransition() { interactiveJob?.cancel(); interactiveJob = null; interactiveDown?.recycle(); interactiveDown = null; interactiveLatest?.recycle(); interactiveLatest = null; transitionView?.let { it.release(); removeView(it) }; transitionView = null; turnPending = false }
    private inner class BlockHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        var job: Job? = null
        var renderedPosition = RecyclerView.NO_POSITION
        var renderedHeight = 0
        var bindGeneration = -1L
    }
    private data class MarkdownTableRow(val block: MarkdownBlock, val cells: List<MarkdownTableCell>)
    private inner class BlockAdapter : RecyclerView.Adapter<BlockHolder>() {
        private val groups: List<List<Int>> = buildList {
            var i = 0
            while (i < source.document.blocks.size) {
                val block = source.document.blocks[i]
                if (block.kind == "table") {
                    val id = block.tableId
                    val group = mutableListOf<Int>()
                    while (i < source.document.blocks.size && source.document.blocks[i].kind == "table" && source.document.blocks[i].tableId == id) {
                        group += i++
                    }
                    add(group)
                } else add(listOf(i++))
            }
        }
        private val blockToGroup = buildMap { groups.forEachIndexed { group, indices -> indices.forEach { put(it, group) } } }
        override fun getItemCount() = groups.size
        fun groupAt(position: Int): List<Int> = groups.getOrElse(position) { emptyList() }
        fun itemForBlock(index: Int): Int = blockToGroup[index] ?: index
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = BlockHolder(FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        })
        override fun onBindViewHolder(holder: BlockHolder, position: Int) {
            holder.job?.cancel(); holder.frame.removeAllViews(); holder.renderedPosition = RecyclerView.NO_POSITION
            val bindGeneration = windowGeneration
            holder.bindGeneration = bindGeneration
            val engine = provider ?: return
            val indices = groups[position]
            val block = source.document.blocks[indices.first()]
            val estimated = indices.sumOf { estimateBlockHeight(source.document.blocks[it], engine) }
            holder.frame.minimumHeight = estimated
            holder.frame.layoutParams = (holder.frame.layoutParams ?: RecyclerView.LayoutParams(-1, estimated)).also {
                it.height = estimated
            }
            // RecyclerView only binds its viewport and small view cache. During a
            // distant restore it has no visible positions yet; rejecting those
            // binds would leave permanent blank placeholders at the restored spot.
            holder.job = scope.launch {
                suspend fun stillCurrent(): Boolean {
                    ensureActive()
                    return bindGeneration == windowGeneration &&
                        holder.bindGeneration == bindGeneration &&
                        holder.bindingAdapterPosition == position
                }
                if (indices.size > 1 && block.kind == "table") {
                    val rows = indices.map { index ->
                        val rowBlock = source.document.blocks[index]
                        val cells = engine.tableCells(index)
                        MarkdownTableRow(rowBlock, cells)
                    }
                    if (!stillCurrent()) return@launch
                    val view = tableGroupView(rows)
                    view.measure(MeasureSpec.makeMeasureSpec(engine.style.render.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    val height = view.measuredHeight
                    holder.frame.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, height))
                    if (!stillCurrent()) return@launch
                    applyRenderedHeight(holder, height)
                    if (stillCurrent() && !scrolling) scheduleSelectionRefresh()
                    return@launch
                }
                val sourceIndex = indices.first()
                val (text, layout) = engine.block(sourceIndex)
                if (!stillCurrent()) return@launch
                if (block.kind == "table") {
                    val cells = engine.tableCells(sourceIndex)
                    if (!stillCurrent()) return@launch
                    val height = cells.maxOf { it.layout.height } + engine.style.render.fontSize.toInt()
                    val parts = cells.map { MarkdownCellFragment(it.start, text.subSequence(it.start, it.end), it.width, height, it.column) }
                    holder.frame.addView(tableView(block, parts), LayoutParams(LayoutParams.MATCH_PARENT, height))
                    if (!stillCurrent()) return@launch
                    applyRenderedHeight(holder, height + engine.style.render.paragraphSpacing)
                } else {
                    val view = blockView(text, block.start, block)
                    // TextView includes font fallback metrics and heading padding that the
                    // pagination layout does not. Size the scroll block from the actual view.
                    view.measure(MeasureSpec.makeMeasureSpec(engine.style.render.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    val contentHeight = maxOf(layout.height, view.measuredHeight)
                    holder.frame.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, contentHeight))
                    if (!stillCurrent()) return@launch
                    applyRenderedHeight(holder, contentHeight + engine.style.render.paragraphSpacing)
                }
                if (!stillCurrent()) return@launch
                holder.frame.setPadding(0, 0, 0, engine.style.render.paragraphSpacing)
                if (!scrolling) scheduleSelectionRefresh()
            }
        }
        override fun onViewRecycled(holder: BlockHolder) {
            holder.job?.cancel(); holder.job = null; holder.bindGeneration = -1L
            holder.renderedPosition = RecyclerView.NO_POSITION; holder.frame.removeAllViews()
        }

        fun refreshVisible(force: Boolean) {
            val manager = continuousManager ?: return
            val first = manager.findFirstVisibleItemPosition()
            val last = manager.findLastVisibleItemPosition()
            if (first < 0 || last < first) return
            val top = first
            val bottom = last
            for (i in top..bottom) getHolder(i)?.let {
                if ((force || it.renderedPosition != i) && it.job?.isActive != true) {
                    notifyItemChanged(i, blockRenderPayload)
                }
            }
            continuousWindowJob?.cancel()
            continuousWindowJob = scope.launch {
                val viewport = (bottom - top + 1).coerceAtLeast(1)
                for (i in (top - viewport * 2).coerceAtLeast(0)..(bottom + viewport * 2).coerceAtMost(itemCount - 1)) {
                    if (!isPositionInPrefetchWindow(i)) continue
                    val holder = getHolder(i) ?: continue
                    if (holder.renderedPosition == i || holder.job?.isActive == true) continue
                    notifyItemChanged(i, blockRenderPayload)
                }
            }
        }

        private fun getHolder(position: Int): BlockHolder? = continuousManager?.findViewByPosition(position)?.let { recycler?.getChildViewHolder(it) as? BlockHolder }

        override fun onBindViewHolder(holder: BlockHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(holder, position) else if (payloads.contains(blockRenderPayload)) onBindViewHolder(holder, position)
        }

    }

    private fun isPositionInPrefetchWindow(position: Int): Boolean {
        val manager = continuousManager ?: return false
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first < 0 || last < first) return position < 4
        val viewport = (last - first + 1).coerceAtLeast(1)
        return position in (first - viewport * 2).coerceAtLeast(0)..(last + viewport * 2)
    }

    private fun estimateBlockHeight(block: MarkdownBlock, engine: MarkdownPageProvider): Int {
        return estimateMarkdownBlockHeight(block, engine.style.render.width.coerceAtLeast(1),
            engine.style.render.fontSize, engine.style.lineHeight, engine.style.render.paragraphSpacing)
    }

    private fun applyRenderedHeight(holder: BlockHolder, height: Int) {
        if (holder.bindGeneration != windowGeneration || holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return
        val old = holder.frame.layoutParams?.height ?: holder.frame.height
        holder.renderedPosition = holder.bindingAdapterPosition
        holder.renderedHeight = height
        // Start restoration/counting only after the first visible block has rendered.
        if (paginationBook != null && countJob == null && pageStarts.isEmpty()) provider?.let(::buildPageIndex)
        holder.frame.minimumHeight = height
        if (old == height) return
        holder.frame.layoutParams = (holder.frame.layoutParams ?: RecyclerView.LayoutParams(-1, height)).also { it.height = height }
        holder.frame.requestLayout()
    }
    private fun tableView(block: MarkdownBlock, cells: List<MarkdownCellFragment>): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pad = (provider!!.style.render.fontSize / 2).toInt()
        var column = 0
        cells.forEach { cell ->
            while (column < cell.column) { row.addView(Space(context), LinearLayout.LayoutParams(cell.width, cell.height)); column++ }
            val textView = blockView(cell.text, block.start + cell.localStart, block, true)
            textView.setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            textView.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(1, (config!!.color and 0x00ffffff) or 0x44000000)
            }
            row.addView(textView, LinearLayout.LayoutParams(cell.width, cell.height))
            column++
        }
        cells.lastOrNull()?.let { cell ->
            while (column < block.tableColumnCount) { row.addView(Space(context), LinearLayout.LayoutParams(cell.width, cell.height)); column++ }
        }
        return object : HorizontalScrollView(context) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                if (syncingTableScroll) return
                val peers = tableScrolls[block.tableId] ?: return
                syncingTableScroll = true
                peers.forEach { if (it !== this && it.scrollX != l) it.scrollTo(l, 0) }
                syncingTableScroll = false
            }
        }.apply {
            isFillViewport = true
            isNestedScrollingEnabled = false
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            tableScrolls.getOrPut(block.tableId) { mutableListOf() }.add(this)
        }
    }
    private fun tableGroupView(rows: List<MarkdownTableRow>): View {
        val engine = provider!!
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val cells = row.cells.map { cell ->
                // StaticLayout retains the cell's image, emphasis and other document spans.
                val value = cell.layout.text
                val tv = blockView(value, row.block.start + cell.start, row.block, true)
                val pad = (engine.style.render.fontSize / 4).toInt()
                tv.setPadding(pad, pad, pad, pad)
                tv.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(1, (config!!.color and 0x00ffffff) or 0x44000000)
                }
                tv.measure(MeasureSpec.makeMeasureSpec(cell.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                tv to cell.width
            }
            val rowHeight = cells.maxOf { it.first.measuredHeight }
            cells.forEach { (view, width) -> line.addView(view, LinearLayout.LayoutParams(width, rowHeight)) }
            content.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight))
        }
        return object : HorizontalScrollView(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(ev.actionMasked != MotionEvent.ACTION_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL)
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
            isFillViewport = true
            isNestedScrollingEnabled = false
            addView(content, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }
    private fun blockView(content: CharSequence, offset: Int, block: MarkdownBlock, tableCell: Boolean = false): View {
        val engine = provider!!
        val c = config!!
        val text = SpannableStringBuilder(content)
        if (offset > block.start) text.getSpans(0, text.length, MarkdownParagraphIndentSpan::class.java).forEach(text::removeSpan)
        applyMarkdownAnnotationSpans(text, offset, notes)
        var selectionTouchDownTime = -1L
        val tv = object : MarkdownAnnotationTextView(context) {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                super.onSelectionChanged(selStart, selEnd)
                if (selStart >= 0 && selEnd > selStart && selEnd <= text.length) post {
                    if (selectionStart != selStart || selectionEnd != selEnd) return@post
                    if (draggingHandle != 0 || !touchSession.canStartSelection(android.os.SystemClock.uptimeMillis(), selectionTouchDownTime)) {
                        android.text.Selection.removeSelection(this.text as android.text.Spannable)
                        return@post
                    }
                    selectionStartGlobal = offset + selStart; selectionEndGlobal = offset + selEnd
                    android.text.Selection.removeSelection(this.text as android.text.Spannable)
                    applySelection()
                    // TextView haptics are disabled for Markdown blocks below.  Emit feedback
                    // only after a real, non-empty range has been accepted by the surface.
                    this@MarkdownNativeSurface.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    publishSelection()
                }
            }
        }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, engine.style.render.fontSize)
            typeface = c.typeface; setTextColor(c.color); setLinkTextColor(c.color)
            includeFontPadding = block.kind == "heading"
            letterSpacing = engine.style.letterSpacing
            setLineSpacing(0f, c.lineHeight)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            textAlignment = when (c.alignment) {
                com.huangder.lumibooks.domain.model.ReaderTextAlignment.CENTER -> View.TEXT_ALIGNMENT_CENTER
                com.huangder.lumibooks.domain.model.ReaderTextAlignment.RIGHT -> View.TEXT_ALIGNMENT_VIEW_END
                com.huangder.lumibooks.domain.model.ReaderTextAlignment.LEFT -> View.TEXT_ALIGNMENT_VIEW_START
                else -> View.TEXT_ALIGNMENT_TEXT_START
            }
            justificationMode = if (engine.style.justify) android.text.Layout.JUSTIFICATION_MODE_INTER_WORD else android.text.Layout.JUSTIFICATION_MODE_NONE
            val headingPad = if (block.kind == "heading") (engine.style.render.fontSize * 0.12f).toInt() else 0
            // Each Markdown block is a separate TextView. Unlike a whole EPUB
            // chapter, its last line has no following line box for the underline.
            setPadding(0, headingPad, 0, headingPad + (4f * resources.displayMetrics.density).toInt())
            setTextIsSelectable(true)
            // The surface owns selection and handles. Native Editor timing varies by ROM
            // and can create/clear its range before our long-press guard accepts it.
            setOnLongClickListener { true }
            // Android's selectable TextView otherwise emits LONG_PRESS feedback before the
            // surface knows whether the gesture produced a valid range (notably in code blocks).
            // MarkdownNativeSurface emits feedback after accepting a genuine selection.
            isHapticFeedbackEnabled = false
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
            }
            setText(text, TextView.BufferType.SPANNABLE)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!touchSession.isSelectionCandidate()) view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    MotionEvent.ACTION_DOWN -> {
                        selectionTouchDownTime = event.downTime
                        downX = event.x; downY = event.y
                        touchSession.selectionAllowed = markdownTextOffsetAt(view as TextView, event.x, event.y) != null
                        if (block.kind != "rule") scheduleSelectionLongPress(view, offset, event.x, event.y, event.downTime)
                        // Special blocks still belong to the reader surface.  They must not turn
                        // a normal tap into an annotation menu, but they also must not swallow
                        // the reader's global capsule toggle (especially in code-only documents).
                        touchSession.menuAllowed = true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (touchSession.canStartSelection(event.eventTime, event.downTime)) {
                            return@setOnTouchListener true
                        }
                        if (!touchSession.isTap(event)) return@setOnTouchListener true
                        val annotation = markdownAnnotationAt(view as TextView, offset, notes, event.x, event.y)
                        if (annotation != null) {
                            android.text.Selection.removeSelection(this.text as android.text.Spannable)
                            selectionStartGlobal = annotation.startPosition.coerceIn(0, source.document.text.length)
                            selectionEndGlobal = annotation.endPosition.coerceIn(selectionStartGlobal, source.document.text.length)
                            draggingHandle = 0
                            applySelection()
                            publishSelection(annotationOnly = true)
                            return@setOnTouchListener true
                        }
                        if (selectionStartGlobal >= 0) {
                            selectionStartGlobal = -1
                            selectionEndGlobal = -1
                            draggingHandle = 0
                            applySelection()
                            selectionCleared()
                            return@setOnTouchListener true
                        }
                        // Consume short UP events even when Android tentatively creates
                        // a selection; only a genuine long press may publish that range.
                        android.text.Selection.removeSelection(this.text as android.text.Spannable)
                        if (!touchSession.menuAllowed) return@setOnTouchListener true
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (!tableCell && !c.continuous && abs(dx) > 60 * resources.displayMetrics.density && abs(dx) > abs(dy)) { turn(if (dx < 0) 1 else -1); return@setOnTouchListener true }
                        if (!tableCell && !c.continuous && abs(dy) > 60 * resources.displayMetrics.density && abs(dy) > abs(dx)) { turn(if (dy < 0) 1 else -1); return@setOnTouchListener true }
                        if (abs(dx) < 12 && abs(event.y - downY) < 12) {
                            val at = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                            val url = text.getSpans(at, at, URLSpan::class.java).firstOrNull()?.url
                            if (url != null) link(url)
                            else if (tableCell || c.continuous || event.x in width * 0.25f..width * 0.75f) menu()
                            else {
                                val action = if (event.x > width / 2) c.edgeTapMode.rightAction else c.edgeTapMode.leftAction
                                turn(if (action == com.huangder.lumibooks.domain.model.ReaderEdgeTapAction.NEXT_PAGE) 1 else -1)
                            }
                            return@setOnTouchListener true
                        }
                    }
                }
                // The surface owns the entire selection gesture. Letting Editor
                // process DOWN/UP can cancel or replace its accepted custom range.
                true
            }
        }
        textOwners[tv] = offset
        if (!tableCell && block.runs.any { it.style == "math-block" }) {
            return HorizontalScrollView(context).apply {
                isFillViewport = true
                addView(tv, LayoutParams(maxOf(engine.style.render.width, android.text.Layout.getDesiredWidth(text, tv.paint).toInt() + 1), LayoutParams.WRAP_CONTENT))
            }
        }
        return tv
    }
    fun turn(direction: Int) {
        if (turnPending || transitionView != null) return
        manualNavigation()
        if (config?.continuous == true) { recycler?.smoothScrollBy(0, direction * (height - paddingTop - paddingBottom)); return }
        if (direction > 0 && currentEnd < source.document.text.length) { turnPending = true; history.addLast(current); livePage++; showPage(currentEnd, direction = direction) }
        else if (direction < 0 && current > 0) {
            if (history.isNotEmpty()) { turnPending = true; livePage = (livePage - 1).coerceAtLeast(0); showPage(history.removeLast(), direction = direction) }
            else {
                renderJob?.cancel()
                renderJob = scope.launch {
                    val engine = provider ?: return@launch
                    val target = current
                    val known = pageStarts
                    if (known.isNotEmpty()) {
                        val index = known.binarySearch(target).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
                        val previous = known.getOrElse((index - 1).coerceAtLeast(0)) { 0 }
                        showPage(previous, direction = direction)
                        return@launch
                    }
                    var at = 0
                    var previous = 0
                    while (at < target) {
                        ensureActive()
                        val next = engine.page(at).end
                        if (next <= at || next >= target) break
                        previous = at
                        at = next
                    }
                    showPage(previous, direction = direction)
                }
            }
        }
    }
    fun navigate(request: EpubNavigationRequest?) {
        if (request == null || request.operationId == requestId) return
        finishHandleDrag()
        selectionMagnifier.dismiss()
        stopTransition()
        pendingRequest = request
        if (provider == null) return
        requestId = request.operationId
        val destination = request.destination
        val offset = when (destination) {
            EpubNavigationDestination.ChapterStart -> 0
            is EpubNavigationDestination.Fragment -> headingOffsets[destination.value.removePrefix("#")]
            is EpubNavigationDestination.Locator -> locatorOffset(destination.json)
            is EpubNavigationDestination.Page -> if (destination.chapterFraction != null) (destination.chapterFraction * source.document.text.length).toInt()
                else pageStarts.getOrNull(destination.index) ?: livePageStarts[destination.index]
                    ?: if (destination.index == Int.MAX_VALUE) source.document.blocks.last().start else if (destination.index == livePage + 1) currentEnd else current
        }
        if (offset == null) { navigationFinished(EpubNavigationResult.Failed(request.operationId, EpubNavigationFailureReason.TARGET_NOT_FOUND)); return }
        current = offset.coerceIn(0, source.document.text.length)
        searchRange = if (request.origin == EpubNavigationOrigin.SEARCH && destination is EpubNavigationDestination.Locator) {
            val size = runCatching { JSONObject(destination.json).optString("exact").length }.getOrDefault(0)
            current until minOf(source.document.text.length, current + size)
        } else null
        history.clear()
        val complete = {
            if (pendingRequest?.operationId == request.operationId) {
                pendingRequest = null
                navigationFinished(EpubNavigationResult.Committed(request.operationId, 0, 0, 1, locator(current)))
            }
        }
        if (config?.continuous == true) {
            restoreContinuous(current, complete)
        } else showPage(current, complete)
    }
    fun setNotes(value: List<Note>) {
        if (notes == value) return
        notes = value
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("MarkdownNative",
            "notes-updated count=${value.size} attached=${textOwners.keys.count { it.isAttachedToWindow }}")
        applySelection()
    }
    fun setTts(value: TtsSentencePosition?) { if (tts != value) { tts = value; applySelection() } }
    fun clearSelection(token: Int) {
        if (selectionToken == token) return
        selectionToken = token
        cancelSelectionLongPress()
        finishHandleDrag()
        selectionMagnifier.dismiss()
        selectionStartGlobal = -1; selectionEndGlobal = -1; draggingHandle = 0
        fun clear(view: View) {
            if (view is TextView && view.text is android.text.Spannable) android.text.Selection.removeSelection(view.text as android.text.Spannable)
            if (view is ViewGroup) for (i in 0 until view.childCount) clear(view.getChildAt(i))
        }
        clear(this)
        applySelection()
    }
    override fun onDetachedFromWindow() {
        cancelSelectionLongPress()
        removeCallbacks(progressPublisher); progressPostPending = false
        finishHandleDrag(); selectionMagnifier.dismiss(); super.onDetachedFromWindow()
    }
    fun release() {
        cancelSelectionLongPress()
        closePageIndex()
        generation++
        restoreFrame?.let(::removeCallbacks); restoreFrame = null
        removeCallbacks(progressPublisher); progressPostPending = false
        finishHandleDrag(); selectionMagnifier.dismiss(); stopTransition(); renderJob?.cancel(); countJob?.cancel()
        windowGeneration++
        cancelContinuousRenders(clearViews = true)
        scope.cancel(); recycler?.adapter = null; provider?.clear(); removeAllViews()
    }

    private fun closePageIndex() {
        val counting = countJob
        val cache = pageIndexCache
        countJob = null
        pageIndexCache = null
        if (counting != null && !counting.isCompleted) {
            // The scan's finally block submits its last confirmed boundaries first.
            counting.invokeOnCompletion { cache?.close() }
            counting.cancel()
        } else cache?.close()
    }
}
