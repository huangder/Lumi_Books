package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.selectionRangeAt

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.ui.reader.engine.*
import com.huangder.lumibooks.util.epub.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

internal data class NativeEpubCallbacks(
    val page: (Int, Int, Int, String?) -> Unit,
    val count: (String, Int, Int) -> Unit,
    val signature: (String) -> Unit,
    val chapter: (Int) -> Unit,
    val menu: () -> Unit,
    val manual: () -> Unit,
    val selection: (EpubSelectionInfo) -> Unit,
    val selectionCleared: () -> Unit,
    val internalLink: (Int, String?) -> Unit,
    val externalLink: (String) -> Unit,
    val search: (Int, Boolean) -> Unit,
    val navigation: (EpubNavigationResult) -> Unit,
    val failure: () -> Unit,
    val bookmarkStart: () -> Unit,
    val bookmarkProgress: (Float, Boolean) -> Unit,
    val bookmarkFinish: (Boolean) -> Unit,
    val image: (EpubImagePreviewRequest) -> Unit
)
internal data class NativeEpubReaderStyle(val fontSizeSp: Float, val fontType: String, val fontPath: String?,
    val weight: Int, val color: Int?, val paper: Int, val publisherColors: Boolean,
    val top: Float, val right: Float, val bottom: Float, val left: Float,
    val textSpacing: EpubTextSpacing, val publisherNightShade: Boolean = false,
    val longPressSelectionRange: LongPressSelectionRange = LongPressSelectionRange.DEFAULT)

@Composable
internal fun NativeEpubReader(session: EpubRenderSession, document: NativeEpubDocument, chapterIndex: Int,
    style: NativeEpubReaderStyle, transition: String, duration: Int, edgeTapMode: ReaderEdgeTapMode,
    restoreLocator: String?, restoreProgression: Float, inclusive: Boolean, initialFragment: String?,
    navigation: EpubNavigationRequest?, search: EpubSearchRequest?, locator: EpubLocatorRequest?, page: EpubPageRequest?,
    notes: List<Note>, tts: TtsSentencePosition?, ttsFollow: Boolean, ttsColor: Int, clearToken: Int,
    bookmarkEnabled: Boolean, identity: String, callbacks: NativeEpubCallbacks,
    onTurnReady: (((Int) -> Boolean)?) -> Unit,
    onTextReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    onReleaseReady: (((() -> Unit) -> Unit)?) -> Unit,
    modifier: Modifier) {
    val latest = rememberUpdatedState(callbacks)
    key(session, document, chapterIndex) {
        AndroidView(modifier = modifier.then(Modifier.fillMaxSize()), factory = { context ->
            NativeEpubSurface(context, session, document, chapterIndex).apply {
                events = { latest.value }
                restoredLocator = restoreLocator
                restoredProgression = restoreProgression
                restoredInclusive = inclusive
                restoredFragment = initialFragment
                onTurnReady { direction -> turn(direction) }
                onTextReady { chapter, pageIndex -> pageText(chapter, pageIndex) }
                onReleaseReady { released -> release(); released() }
            }
        }, update = { view ->
            view.configure(style, transition, duration, edgeTapMode, identity)
            view.bookmarkEnabled = bookmarkEnabled
            view.setDecorations(notes, tts, ttsFollow, ttsColor, clearToken)
            view.requests(navigation, search, locator, page)
        }, onRelease = { view ->
            view.release(); onTurnReady(null); onTextReady(null); onReleaseReady(null)
        })
    }
}

internal fun nativeLocatorFromJson(json: String?): EpubLocator? = runCatching {
    val o = JSONObject(json ?: return null)
    EpubLocator(version = o.optInt("version", 2), href = o.optString("href"),
        domPath = o.optJSONArray("domPath")?.let { a -> List(a.length()) { a.optInt(it) } }.orEmpty(),
        textOffset = o.optInt("textOffset"), textPosition = o.optInt("textPosition"), textLength = o.optInt("textLength"),
        exact = o.optString("exact"), prefix = o.optString("prefix"), suffix = o.optString("suffix"),
        progression = o.optDouble("progression", 0.0).toFloat())
}.getOrNull()

/** Three page slots use the same immutable geometry for drawing, hits and curl frames. */
internal class NativeEpubSurface(context: Context, private val session: EpubRenderSession,
    private val document: NativeEpubDocument, private val chapterIndex: Int) : FrameLayout(context) {
    lateinit var events: () -> NativeEpubCallbacks
    var restoredLocator: String? = null
    var restoredProgression = 0f
    var restoredInclusive = false
    var restoredFragment: String? = null
    var bookmarkEnabled = true
    private var released = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var layoutJob: Job? = null
    private var frameJob: Job? = null
    private var generation = 0
    private var style: NativeEpubReaderStyle? = null
    private var config: NativeEpubRenderConfig? = null
    private var layout: NativeEpubChapterLayout? = null
    private var current = 0
    private var identity = ""
    private var paginationSignature = ""
    private var transition = "slide"
    private var duration = 260
    private var edgeTap = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    private var animation: PageAnimationController? = null
    private val bitmapPool = RenderResourcePool<Bitmap> { if (!it.isRecycled) it.recycle() }
    private inner class Slot : View(context), PageBitmapSource, CurlFrameSource {
        var page = -1
        var bitmap: Bitmap? = null
        override val pageBitmap: Bitmap? get() = bitmap
        override fun acquireCurlFrame() = bitmapPool.acquire(bitmap)
        fun reset(index: Int) { if (page == index) return; page = index; bitmapPool.retire(bitmap); bitmap = null }
    }
    private val previousSlot = Slot(); private val currentSlot = Slot(); private val nextSlot = Slot()
    private val surface = PageAnimationSurface(this, previousSlot, currentSlot, nextSlot,
        { layout?.config?.paper ?: Color.WHITE }, { document.bodyStyle.vertical || session.pageProgressionDirection(chapterIndex) == EpubPageProgressionDirection.RTL },
        snapTranslationsToPixels = true, directPageRenderer = { canvas, view ->
            val page = (view as Slot).page
            layout?.takeIf { page in it.pages.indices }?.let { it.draw(canvas, page, highlights); true } ?: false
        })
    private var highlights = emptyList<Pair<IntRange, Int>>()
    private var notes = emptyList<Note>()
    private var tts: TtsSentencePosition? = null
    private var ttsColor = 0x55ffca28
    private var clearToken = -1
    private var selectedRange: IntRange? = null
    private var pendingNavigation: EpubNavigationRequest? = null
    private var pendingSearch: EpubSearchRequest? = null
    private var pendingLocator: EpubLocatorRequest? = null
    private var pendingPage: EpubPageRequest? = null
    private var navigationToken: Long? = null
    private var searchToken: Int? = null
    private var locatorToken: Int? = null
    private var pageToken: Int? = null
    private var downX = 0f; private var downY = 0f
    private var pulling = false; private var pulled = 0f
    private var selecting = false
    private var longPressed = false
    private val longPress = Runnable {
        if (!released && animation?.isRunning != true && !pulling) {
            selectWord(downX, downY); longPressed = selectedRange != null
            if (longPressed) animation?.abortAnim()
        }
    }
    init {
        setWillNotDraw(false)
        // Animation views are identities only. Native pages draw directly onto the host Canvas.
        addView(previousSlot, LayoutParams(0, 0)); addView(currentSlot, LayoutParams(0, 0)); addView(nextSlot, LayoutParams(0, 0))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { relayout() }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.i("NativeEpub",
            "measure chapter=$chapterIndex specs=${MeasureSpec.getSize(widthMeasureSpec)}x${MeasureSpec.getSize(heightMeasureSpec)} result=${measuredWidth}x$measuredHeight")
    }
    fun configure(style: NativeEpubReaderStyle, transition: String, duration: Int, edgeTap: ReaderEdgeTapMode, identity: String) {
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.i("NativeEpub", "configure chapter=$chapterIndex viewport=${width}x$height transition=$transition")
        this.style = style; this.edgeTap = edgeTap; this.identity = identity
        if (this.transition != transition || this.duration != duration || animation == null) {
            destroyAnimation(); this.transition = transition; this.duration = duration
            animation = if (transition == "curl") CurlPageAnim(surface, baseDurationMs = duration) else SlidePageAnim(surface, duration)
            animation!!.apply {
                onCanFlip = { direction -> canFlip(direction) }
                onTapLeft = { edge(edgeTap.leftAction) }; onTapRight = { edge(edgeTap.rightAction) }; onTapCenter = { events().menu() }
                onAnimationComplete = { val direction = currentDirection
                    if (direction != PageAnimationController.Direction.NONE) {
                        current += if (direction == PageAnimationController.Direction.NEXT) 1 else -1
                        selectedRange = null; refreshDecorations(); updateSlots(); publish()
                    }
                }
            }
        }
        requestLayout()
        post { relayout() }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.i("NativeEpub", "size chapter=$chapterIndex viewport=${w}x$h")
        relayout()
    }
    private fun relayout() {
        val st = style ?: return
        if (released || width <= 0 || height <= 0) return
        val metrics = resources.displayMetrics
        val face = resolveReaderTypeface(context, st.fontType, st.fontPath, st.weight).typeface
        val next = NativeEpubRenderConfig(width, height, st.fontSizeSp * metrics.scaledDensity, face, st.color, st.paper, st.publisherColors,
            st.left * metrics.density, st.top * metrics.density, st.right * metrics.density, st.bottom * metrics.density,
            lineHeightOverride = st.textSpacing.lineHeight.takeUnless { abs(it - 1.5f) <= 0.001f },
            paragraphSpacingPx = st.textSpacing.paragraphSpacingDp.takeUnless { abs(it - 2f) <= 0.001f }
                ?.coerceIn(0f, 30f)?.times(metrics.density),
            firstLineIndentChars = st.textSpacing.firstLineIndent.takeUnless { abs(it - 2f) <= 0.001f }
                ?.coerceIn(0f, 4f),
            publisherNightShade = st.publisherNightShade)
        if (next == config) return
        config = next; generation++; val token = generation
        val anchor = layout?.pages?.getOrNull(current)?.start
        layoutJob?.cancel(); frameJob?.cancel(); animation?.abortAnim()
        var candidate: NativeEpubChapterLayout? = null
        layoutJob = scope.launch {
            try {
                val built = withContext(Dispatchers.Default) {
                    val renderConfig = next.copy(embeddedTypefaces = loadEmbeddedTypefaces())
                    NativeEpubChapterRenderer.layout(document, renderConfig) {
                        session.nativeResource(it)?.bytes
                    }.also { candidate = it }
                }
                if (released || token != generation) { built.close(); candidate = null; return@launch }
                layout?.close(); layout = built; candidate = null
                previousSlot.reset(-1); currentSlot.reset(-1); nextSlot.reset(-1)
                current = anchor?.let { built.pageFor(it) } ?: initialPage(built)
                paginationSignature = "native-css-v1|$identity|$chapterIndex|$next"
                events().signature(paginationSignature); events().count(paginationSignature, chapterIndex, built.pages.size)
                refreshDecorations(); updateSlots(); publish(); resolveRequests()
                android.util.Log.i("NativeEpub", "ready chapter=$chapterIndex engine=NATIVE pages=${built.pages.size}")
            } catch (e: CancellationException) { candidate?.close(); throw e }
            catch (e: Exception) { candidate?.close(); if (!released && token == generation) {
                android.util.Log.i("NativeEpub", "Chapter fallback: ${e.message}"); events().failure() } }
        }
    }
    private fun loadEmbeddedTypefaces(): List<NativeEpubResolvedFont> {
        if (document.embeddedFonts.isEmpty()) return emptyList()
        val directory = File(context.cacheDir, "epub-native-fonts").apply { mkdirs() }
        return document.embeddedFonts.map { face ->
            val extension = face.resource.path.substringAfterLast('.', "ttf").lowercase()
            val digest = MessageDigest.getInstance("SHA-256").digest(face.resource.bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
            val file = File(directory, "$digest.$extension")
            if (!file.isFile || file.length() != face.resource.bytes.size.toLong()) {
                val temporary = File(directory, "$digest.tmp")
                temporary.writeBytes(face.resource.bytes)
                if (file.exists()) file.delete()
                check(temporary.renameTo(file)) { "Could not cache embedded font ${face.resource.path}" }
            }
            NativeEpubResolvedFont(
                face.family,
                face.weight,
                face.italic,
                Typeface.createFromFile(file)
            )
        }
    }
    private fun initialPage(built: NativeEpubChapterLayout): Int {
        pendingPage?.takeIf { it.chapterIndex == chapterIndex }?.let { request ->
            return request.chapterFraction?.let { pageIndexForChapterFraction(it, built.pages.size) }
                ?: request.pageIndex.coerceIn(0, built.pages.lastIndex)
        }
        pendingLocator?.takeIf { it.chapterIndex == chapterIndex }?.let { request ->
            nativeLocatorFromJson(request.locatorJson)?.let(document::resolve)?.let(built::pageFor)?.let { return it }
        }
        restoredFragment?.let { document.anchors[it]?.let { offset -> return built.pageFor(offset) } }
        nativeLocatorFromJson(restoredLocator)?.let { document.resolve(it)?.let { offset -> return built.pageFor(offset) } }
        val count = built.pages.size
        return (if (restoredInclusive && restoredProgression > 0) ceil(restoredProgression * count - .00001f).toInt() - 1
            else (restoredProgression * count).toInt()).coerceIn(0, count - 1)
    }
    private fun updateSlots() {
        val built = layout ?: return
        // Move existing frames rather than repainting/reallocating the two overlapping pages.
        val oldFrames = listOf(previousSlot, currentSlot, nextSlot).mapNotNull { slot -> slot.bitmap?.let { slot.page to it } }.toMap()
        val indices = listOf(current - 1, current, current + 1)
        listOf(previousSlot, currentSlot, nextSlot).forEachIndexed { i, slot ->
            slot.page = indices[i]; slot.bitmap = oldFrames[indices[i]]
        }
        oldFrames.filterKeys { it !in indices }.values.forEach(bitmapPool::retire)
        frameJob?.cancel()
        if (transition == "curl") {
            val token = generation
            frameJob = scope.launch {
                listOf(currentSlot, nextSlot, previousSlot).forEach { slot ->
                    val index = slot.page
                    if (index !in built.pages.indices || slot.bitmap != null) return@forEach
                    var frame: Bitmap? = null
                    try {
                        val decorations = highlights.toList()
                        val started = android.os.SystemClock.elapsedRealtimeNanos()
                        val bitmap = withContext(Dispatchers.Default) {
                            Bitmap.createBitmap(built.config.width, built.config.height, Bitmap.Config.ARGB_8888).also {
                                frame = it; synchronized(built) { built.draw(Canvas(it), index, decorations) }
                            }
                        }
                        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("NativeEpub",
                            "nativeFrameMs=${(android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f} chapter=$chapterIndex page=$index")
                        if (released || token != generation || slot.page != index) bitmap.recycle()
                        else { slot.bitmap = bitmapPool.track(bitmap); invalidate() }
                        frame = null
                    } finally { frame?.let { if (!it.isRecycled) it.recycle() } }
                }
            }
        }
        invalidate()
    }
    private fun canFlip(direction: PageAnimationController.Direction): Boolean {
        val built = layout ?: return false
        val target = current + if (direction == PageAnimationController.Direction.NEXT) 1 else -1
        if (target !in built.pages.indices) return false
        return transition != "curl" || currentSlot.bitmap != null && (if (target > current) nextSlot else previousSlot).bitmap != null
    }
    fun turn(direction: Int): Boolean {
        if (released || layout == null || animation?.isRunning == true || animation?.isDragging == true) return false
        val dir = if (direction > 0) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
        val target = current + if (direction > 0) 1 else -1
        if (target !in layout!!.pages.indices) { events().manual(); events().chapter(if (direction > 0) 1 else -1); return true }
        if (!canFlip(dir)) return false
        events().manual(); clearSelection()
        if (transition == "none") { current = target; updateSlots(); publish() }
        else when (val a = animation) { is CurlPageAnim -> return a.startFromTap(dir); is SlidePageAnim -> a.startFromTap(dir) }
        return true
    }
    private fun edge(action: ReaderEdgeTapAction) { turn(if (action == ReaderEdgeTapAction.NEXT_PAGE) 1 else -1) }
    private fun publish() {
        val built = layout ?: return
        events().page(chapterIndex, current, built.pages.size, document.locator(built.pages[current].start, current.toFloat() / built.pages.size).toJson().toString())
    }
    suspend fun pageText(chapter: Int, page: Int): EpubPageText? {
        if (released || chapter != chapterIndex) return null
        val built = layout ?: return null; val p = built.pages.getOrNull(page) ?: return null
        val a = document.sourcePosition(p.start); val b = document.sourcePosition(p.end)
        return EpubPageText(chapter, page, built.pages.size, document.rawText.substring(a, b), document.rawText, a, b)
    }
    fun requests(navigation: EpubNavigationRequest?, search: EpubSearchRequest?, locator: EpubLocatorRequest?, page: EpubPageRequest?) {
        pendingNavigation = navigation; pendingSearch = search; pendingLocator = locator; pendingPage = page
        resolveRequests()
    }
    private fun jump(offset: Int) { val built = layout ?: return
        animation?.abortAnim(); clearSelection(); current = built.pageFor(offset); updateSlots(); publish() }
    private fun resolveRequests() {
        val built = layout ?: return
        pendingNavigation?.takeIf { it.targetChapterIndex == chapterIndex && it.operationId != navigationToken }?.let { request ->
            navigationToken = request.operationId
            val page = when (val destination = request.destination) {
                EpubNavigationDestination.ChapterStart -> 0
                is EpubNavigationDestination.Fragment -> document.anchors[destination.value]?.let(built::pageFor)
                is EpubNavigationDestination.Locator -> nativeLocatorFromJson(destination.json)?.let(document::resolve)?.let(built::pageFor)
                is EpubNavigationDestination.Page -> destination.chapterFraction?.let { pageIndexForChapterFraction(it, built.pages.size) } ?: destination.index.coerceIn(0, built.pages.lastIndex)
            }
            if (page == null) events().navigation(EpubNavigationResult.Failed(request.operationId, EpubNavigationFailureReason.TARGET_NOT_FOUND))
            else { jump(built.pages[page].start); events().navigation(EpubNavigationResult.Committed(request.operationId, chapterIndex, current, built.pages.size,
                document.locator(built.pages[current].start).toJson().toString())) }
        }
        pendingSearch?.takeIf { it.chapterIndex == chapterIndex && it.token != searchToken }?.let {
            searchToken = it.token; val offset = document.resolve(it.locator)
            if (offset != null) jump(offset); events().search(it.token, offset != null)
        }
        pendingLocator?.takeIf { it.chapterIndex == chapterIndex && it.token != locatorToken }?.let {
            locatorToken = it.token; nativeLocatorFromJson(it.locatorJson)?.let(document::resolve)?.let(::jump) }
        pendingPage?.takeIf { it.chapterIndex == chapterIndex && it.token != pageToken }?.let {
            pageToken = it.token; val index = it.chapterFraction?.let { f -> pageIndexForChapterFraction(f, built.pages.size) } ?: it.pageIndex.coerceIn(0, built.pages.lastIndex)
            jump(built.pages[index].start) }
    }
    fun setDecorations(notes: List<Note>, tts: TtsSentencePosition?, follow: Boolean, color: Int, token: Int) {
        val changed = this.notes != notes || this.tts != tts || ttsColor != color
        val sentenceChanged = this.tts != tts
        this.notes = notes; this.tts = tts; ttsColor = color
        if (clearToken != token) { clearToken = token; clearSelection() }
        if (changed) { refreshDecorations(); invalidateFrames() }
        if (sentenceChanged && follow && tts?.chapterIndex == chapterIndex) jump(document.visiblePosition(tts.startOffset))
    }
    private fun refreshDecorations() {
        highlights = buildList {
            notes.filter { !it.isStale && it.chapterIndex == chapterIndex && it.type in setOf("highlight", "underline") }.forEach {
                val color = runCatching { Color.parseColor(it.color) }.getOrDefault(0x55ffca28)
                add((document.visiblePosition(it.startPosition) until document.visiblePosition(it.endPosition)) to ((color and 0x00ffffff) or 0x55000000)) }
            tts?.takeIf { it.chapterIndex == chapterIndex }?.let { add((document.visiblePosition(it.startOffset) until document.visiblePosition(it.endOffset)) to ttsColor) }
            selectedRange?.let { add(it to 0x5533aaff) }
        }
    }
    private fun invalidateFrames() {
        animation?.abortAnim(); frameJob?.cancel()
        listOf(previousSlot, currentSlot, nextSlot).forEach { bitmapPool.retire(it.bitmap); it.bitmap = null }
        updateSlots()
    }
    private fun hit(x: Float, y: Float): NativeEpubHit? {
        val items = layout?.pages?.getOrNull(current)?.items ?: return null
        for (item in items) {
            val hit = item.hit(x, y) ?: continue
            if (item !is NativeEpubGlyphItem) return hit
            val column = items.filterIsInstance<NativeEpubGlyphItem>()
                .filter { abs(it.bounds.left - item.bounds.left) < 0.5f }
            return hit.copy(lineRange = column.minOf { it.start } until column.maxOf { it.end })
        }
        return null
    }
    private fun selectWord(x: Float, y: Float) {
        val hit = hit(x, y) ?: return
        if (document.text.getOrNull(hit.start) == '\uFFFC') return
        selectedRange = document.text.selectionRangeAt(hit.start, style?.longPressSelectionRange ?: LongPressSelectionRange.DEFAULT,
            hit.lineRange)
        refreshDecorations(); invalidateFrames(); emitSelection(hit.bounds)
    }
    private fun emitSelection(bounds: RectF) {
        val range = selectedRange ?: return
        val a = document.sourcePosition(range.first); val b = document.sourcePosition(range.last + 1)
        val xy = IntArray(2); getLocationOnScreen(xy)
        events().selection(EpubSelectionInfo(document.text.substring(range.first, range.last + 1), a, b,
            document.locator(range.first).toJson().toString(), document.locator(range.last + 1).toJson().toString(),
            bounds.centerX() + xy[0], bounds.centerY() + xy[1], bounds.left + xy[0], bounds.top + xy[1], bounds.right + xy[0], bounds.bottom + xy[1]))
    }
    private fun clearSelection() { if (selectedRange != null) {
        selectedRange = null; refreshDecorations(); invalidateFrames(); events().selectionCleared() } }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (released || layout == null) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (animation?.isRunning == true) return true
                downX = event.x; downY = event.y; pulling = false; longPressed = false
                selecting = selectedRange != null && hit(event.x, event.y)?.let { it.start in selectedRange!! } == true
                if (!selecting) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) > ViewConfiguration.get(context).scaledTouchSlop) removeCallbacks(longPress)
                if (selecting || longPressed) { hit(event.x, event.y)?.let {
                    val anchor = selectedRange?.first ?: it.start
                    selectedRange = min(anchor, it.start) until max(anchor + 1, it.end)
                    refreshDecorations(); invalidateFrames(); emitSelection(it.bounds) }; return true }
                if (bookmarkEnabled && event.y - downY > 32 * resources.displayMetrics.density && abs(event.x - downX) < (event.y - downY) / 2) {
                    if (!pulling) { pulling = true; animation?.abortAnim(); events().bookmarkStart() }
                    pulled = event.y - downY; events().bookmarkProgress(pulled, pulled >= 96 * resources.displayMetrics.density); return true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (pulling) { events().bookmarkFinish(event.actionMasked == MotionEvent.ACTION_UP && pulled >= 96 * resources.displayMetrics.density); pulling = false; return true }
                if (longPressed || selecting) { longPressed = false; selecting = false; return true }
                if (event.actionMasked == MotionEvent.ACTION_UP && hypot(event.x - downX, event.y - downY) < ViewConfiguration.get(context).scaledTouchSlop) {
                    animation?.abortAnim()
                    if (selectedRange != null) { clearSelection(); return true }
                    val hit = hit(event.x, event.y)
                    val image = layout!!.pages[current].items.filterIsInstance<NativeEpubImageItem>().firstOrNull { it.hit(event.x, event.y) != null }
                    if (image != null && hit != null) {
                        val bounds = hit.bounds
                        events().image(EpubImagePreviewRequest(session.nativeImageUrl(image.path),
                            "", bounds.left, bounds.top, bounds.right, bounds.bottom,
                            bounds.width().toInt(), bounds.height().toInt())); return true
                    }
                    val href = hit?.let { h -> document.runs.firstOrNull { h.start >= it.start && h.start < it.end }?.href }
                    if (href != null) { session.resolveInternalLink(chapterIndex, href)?.let { events().internalLink(it.first, it.second) } ?: events().externalLink(href); return true }
                    val note = hit?.let { h -> val pos = document.sourcePosition(h.start); notes.firstOrNull { !it.isStale && it.chapterIndex == chapterIndex && pos in it.startPosition until it.endPosition } }
                    if (note != null) { selectedRange = document.visiblePosition(note.startPosition) until document.visiblePosition(note.endPosition); refreshDecorations(); emitSelection(hit!!.bounds); return true }
                    when { event.x < width * .3f -> edge(edgeTap.leftAction); event.x > width * .7f -> edge(edgeTap.rightAction); else -> events().menu() }
                    return true
                }
                // A boundary gesture must still leave this native chapter through ReaderScreen.
                val delta = event.x - downX
                val reversed = document.bodyStyle.vertical || session.pageProgressionDirection(chapterIndex) == EpubPageProgressionDirection.RTL
                if (event.actionMasked == MotionEvent.ACTION_UP && abs(delta) > width * .08f) {
                    val direction = if ((delta < 0) != reversed) 1 else -1
                    if (current + direction !in layout!!.pages.indices) { animation?.abortAnim(); events().manual(); events().chapter(direction); return true }
                }
            }
        }
        if (transition == "none") { if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - downX) > width * .08f) turn(if (event.x < downX) 1 else -1); return true }
        return animation?.onTouchEvent(event) ?: true
    }
    override fun dispatchDraw(canvas: Canvas) {
        val built = layout ?: return
        synchronized(built) {
            if (animation?.isRunning == true || animation?.isDragging == true) animation?.onDraw(canvas)
            else built.draw(canvas, current, highlights)
        }
    }
    override fun computeScroll() { if (animation?.computeScroll() == true) postInvalidateOnAnimation() }
    private fun destroyAnimation() { (animation as? CurlPageAnim)?.destroy(); animation?.abortAnim(); animation = null }
    fun release() {
        if (released) return
        released = true; generation++; removeCallbacks(longPress); destroyAnimation(); scope.cancel(); bitmapPool.destroy()
        // Wait for drawing jobs to release their layout before recycling decoded images.
        val old = layout; layout = null
        if (old != null) CoroutineScope(Dispatchers.Main.immediate).launch { layoutJob?.join(); frameJob?.join(); old.close() }
    }
}
