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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    val chapterPrepared: (Int, NativeEpubDocument) -> Unit,
    val handoff: (Int, Bitmap) -> Unit,
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
// The native CSS engine currently paints plain annotation rectangles only.
// Keep styled annotations on the WebView path, which applies their font metrics and paint.
internal fun nativeEpubDecorationsSupported(notes: List<Note>, chapterIndex: Int): Boolean =
    notes.none { !it.isStale && it.chapterIndex == chapterIndex && it.styleSnapshotJson != null }

internal data class NativeEpubReaderStyle(val fontSizeSp: Float, val fontType: String, val fontPath: String?,
    val weight: Float, val color: Int?, val paper: Int, val publisherColors: Boolean,
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
    notesForChapter: (Int) -> List<Note>, nativeAllowed: (Int) -> Boolean, prepareNotes: suspend (Int) -> Unit,
    onTurnReady: (((Int) -> Boolean)?) -> Unit,
    onTextReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    onReleaseReady: (((() -> Unit) -> Unit)?) -> Unit,
    modifier: Modifier) {
    val latest = rememberUpdatedState(callbacks)
    val latestNotes = rememberUpdatedState(notesForChapter)
    val latestAllowed = rememberUpdatedState(nativeAllowed)
    val latestPrepareNotes = rememberUpdatedState(prepareNotes)
    key(session) {
        var footnote by remember { mutableStateOf<NativeEpubFootnote?>(null) }
        AndroidView(modifier = modifier.then(Modifier.fillMaxSize()), factory = { context ->
            NativeEpubSurface(context, session, document, chapterIndex).apply {
                events = { latest.value }
                chapterNotes = { latestNotes.value(it) }
                canUseNative = { latestAllowed.value(it) }
                prepareChapterNotes = { latestPrepareNotes.value(it) }
                showFootnote = { footnote = it }
                restoredLocator = restoreLocator
                restoredProgression = restoreProgression
                restoredInclusive = inclusive
                restoredFragment = initialFragment
                onTurnReady { direction -> turn(direction) }
                onTextReady { chapter, pageIndex -> pageText(chapter, pageIndex) }
                onReleaseReady { released -> release(); released() }
            }
        }, update = { view ->
            view.selectChapter(chapterIndex, document, restoreLocator, restoreProgression, inclusive, initialFragment)
            view.configure(style, transition, duration, edgeTapMode, identity)
            view.bookmarkEnabled = bookmarkEnabled
            view.setDecorations(notes, tts, ttsFollow, ttsColor, clearToken)
            view.requests(navigation, search, locator, page)
        }, onRelease = { view ->
            view.release(); onTurnReady(null); onTextReady(null); onReleaseReady(null)
        })
        footnote?.let { note -> NativeEpubFootnoteDialog(session, note) { footnote = null } }
    }
}

@Composable
private fun NativeEpubFootnoteDialog(session: EpubRenderSession, note: NativeEpubFootnote, dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = androidx.compose.material3.MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                if (note.text.isNotBlank()) Text(note.text)
                for (path in note.imagePaths) key(path) {
                    val bitmap by produceState<Bitmap?>(null, session, path) {
                        value = withContext(Dispatchers.IO) {
                            session.nativeResource(path)?.bytes?.let { bytes ->
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                options.inJustDecodeBounds = false
                                options.inSampleSize = 1
                                while (max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) {
                                    options.inSampleSize *= 2
                                }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            }
                        }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, Modifier.fillMaxWidth()) }
                }
            }
        }
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

/** Chapter-specific publisher styles and fonts are already covered by the book fingerprint. */
internal fun nativeEpubPaginationSignature(identity: String, fontKey: String, config: NativeEpubRenderConfig): String =
    listOf("native-css-v4", identity, fontKey, config.width, config.height, config.fontPx,
        config.left, config.top, config.right, config.bottom, config.lineHeightOverride,
        config.paragraphSpacingPx, config.firstLineIndentChars, config.overridePublisherFont,
        config.readerFontWeight).joinToString("|")

/** Three page slots use the same immutable geometry for drawing, hits and curl frames. */
internal class NativeEpubSurface(context: Context, private val session: EpubRenderSession,
    private var document: NativeEpubDocument, private var chapterIndex: Int) : FrameLayout(context) {
    lateinit var events: () -> NativeEpubCallbacks
    var showFootnote: (NativeEpubFootnote) -> Unit = {}
    var chapterNotes: (Int) -> List<Note> = { emptyList() }
    var canUseNative: (Int) -> Boolean = { true }
    var prepareChapterNotes: suspend (Int) -> Unit = {}
    var restoredLocator: String? = null
    var restoredProgression = 0f
    var restoredInclusive = false
    var restoredFragment: String? = null
    var bookmarkEnabled = true
    private var released = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var layoutJob: Job? = null
    private var frameJob: Job? = null
    private val preparation = mutableMapOf<Int, Job>()
    private val adjacent = mutableMapOf<Int, NativeEpubChapterLayout>()
    private val unavailable = mutableSetOf<Int>()
    private val turns = ReaderCurlTurnSequencer()
    private var refreshFramesAfterTurn = false
    private var handingOff = false
    private var displayedChapter: Int? = null
    private var generation = 0
    private var displayedGeneration = -1
    private var style: NativeEpubReaderStyle? = null
    private var config: NativeEpubRenderConfig? = null
    private var layout: NativeEpubChapterLayout? = null
    private var current = 0
    private var identity = ""
    private var renderedPaginationSignature: String? = null
    private var transition = "slide"
    private var duration = 260
    private var edgeTap = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT
    private var animation: PageAnimationController? = null
    private val bitmapPool = RenderResourcePool<Bitmap> { if (!it.isRecycled) it.recycle() }
    private data class PageRef(val chapter: Int, val layout: NativeEpubChapterLayout, val index: Int)
    private inner class Slot : View(context), PageBitmapSource, CurlFrameSource {
        var page: PageRef? = null
        var bitmap: Bitmap? = null
        override val pageBitmap: Bitmap? get() = bitmap
        override fun acquireCurlFrame() = bitmapPool.acquire(bitmap)
        fun reset() { page = null; bitmapPool.retire(bitmap); bitmap = null }
    }
    private val previousSlot = Slot(); private val currentSlot = Slot(); private val nextSlot = Slot()
    private val surface = PageAnimationSurface(this, previousSlot, currentSlot, nextSlot,
        { layout?.config?.paper ?: Color.WHITE }, { document.bodyStyle.vertical || session.pageProgressionDirection(chapterIndex) == EpubPageProgressionDirection.RTL },
        snapTranslationsToPixels = true, directPageRenderer = { canvas, view ->
            val page = (view as Slot).page
            page?.let { it.layout.draw(canvas, it.index, decorationsFor(it)); true } ?: false
        })
    private var highlights = NativeEpubHighlights()
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
    private var touchActive = false
    private var queueGesture = false
    private var queuedGestureMoved = false
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
            animation = when (transition) {
                "curl" -> CurlPageAnim(surface, baseDurationMs = duration)
                "wave" -> WavePageAnim(surface, duration)
                else -> SlidePageAnim(surface, duration)
            }
            animation!!.apply {
                onCanFlip = { direction -> canFlip(direction) }
                onTapLeft = { edge(edgeTap.leftAction) }; onTapRight = { edge(edgeTap.rightAction) }; onTapCenter = { events().menu() }
                onAnimationComplete = { val direction = currentDirection
                    if (direction != PageAnimationController.Direction.NONE) {
                        slotFor(direction).page?.let(::commitTurn)
                    }
                }
            }
            if (layout != null && displayedChapter == chapterIndex && generation == displayedGeneration) updateSlots()
        }
        requestLayout()
        post { relayout() }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.i("NativeEpub", "size chapter=$chapterIndex viewport=${w}x$h")
        relayout()
    }
    fun selectChapter(chapter: Int, source: NativeEpubDocument, locator: String?, progression: Float,
                      inclusive: Boolean, fragment: String?) {
        if (chapter == chapterIndex) return
        chapterIndex = chapter
        document = source
        restoredLocator = locator
        restoredProgression = progression
        restoredInclusive = inclusive
        restoredFragment = fragment
        handingOff = false
        selectedRange = null
        config = null
        resetTurns()
    }

    private fun buildLayout(source: NativeEpubDocument, settings: NativeEpubRenderConfig,
                            work: CoroutineScope): NativeEpubChapterLayout {
        val renderConfig = settings.copy(embeddedTypefaces = if (settings.overridePublisherFont) emptyList()
            else resolveNativeEpubFonts(source, context.cacheDir))
        return NativeEpubChapterRenderer.layout(source, renderConfig, checkActive = { work.ensureActive() }) {
            session.nativeResource(it)?.bytes
        }
    }

    private fun relayout() {
        val st = style ?: return
        if (released || width <= 0 || height <= 0) return
        val metrics = resources.displayMetrics
        val face = resolveReaderTypeface(context, st.fontType, st.fontPath, st.weight).typeface
        val next = NativeEpubRenderConfig(width, height, st.fontSizeSp * metrics.scaledDensity, face, st.color, st.paper, st.publisherColors,
            st.left * metrics.density, st.top * metrics.density, st.right * metrics.density, st.bottom * metrics.density,
            lineHeightOverride = st.textSpacing.lineHeight.takeUnless { abs(it - 1.5f) <= 0.001f },
            paragraphSpacingPx = st.textSpacing.paragraphSpacingDp?.coerceIn(0f, 30f)?.times(metrics.density),
            firstLineIndentChars = st.textSpacing.firstLineIndent.takeUnless { abs(it - 2f) <= 0.001f }
                ?.coerceIn(0f, 4f),
            publisherNightShade = st.publisherNightShade, overridePublisherFont = st.fontType != "system",
            readerFontWeight = st.weight.toInt().coerceIn(100, 900), density = metrics.density)
        val signature = nativeEpubPaginationSignature(identity,
            readerPaginationFontKey(context, st.fontType, st.fontPath, st.weight), next)
        if (next == config) {
            if (signature != renderedPaginationSignature) {
                // Content identity can change while geometry is still identical. Re-key only
                // a completed layout; any in-flight layout publishes the latest identity below.
                layout?.takeIf { displayedGeneration == generation && displayedChapter == chapterIndex }?.let {
                    renderedPaginationSignature = signature
                    publishPagination(chapterIndex, it)
                    adjacent.forEach { (chapter, prepared) -> publishPagination(chapter, prepared) }
                }
            }
            return
        }
        config = next; generation++; val token = generation
        val anchor = layout?.takeIf { displayedChapter == chapterIndex }?.pages?.getOrNull(current)?.start
        layoutJob?.cancel(); frameJob?.cancel(); resetTurns()
        clearPreparation()
        val source = document
        var candidate: NativeEpubChapterLayout? = null
        layoutJob = scope.launch {
            try {
                val built = withContext(Dispatchers.Default) {
                    buildLayout(source, next, this).also { candidate = it }
                }
                if (released || token != generation) { built.close(); candidate = null; return@launch }
                layout?.close(); layout = built; candidate = null; displayedChapter = chapterIndex
                displayedGeneration = token
                previousSlot.reset(); currentSlot.reset(); nextSlot.reset()
                current = anchor?.let { built.pageFor(it) } ?: initialPage(built)
                val latestStyle = style ?: st
                renderedPaginationSignature = nativeEpubPaginationSignature(identity,
                    readerPaginationFontKey(context, latestStyle.fontType, latestStyle.fontPath, latestStyle.weight), next)
                publishPagination(chapterIndex, built)
                refreshDecorations(); updateSlots(); publish(); resolveRequests()
                android.util.Log.i("NativeEpub", "ready chapter=$chapterIndex engine=NATIVE pages=${built.pages.size}")
            } catch (e: CancellationException) { candidate?.close(); throw e }
            catch (e: Exception) { candidate?.close(); if (!released && token == generation) {
                android.util.Log.i("NativeEpub", "Chapter fallback: ${e.message}"); events().failure() } }
        }
    }
    private fun publishPagination(chapter: Int, built: NativeEpubChapterLayout) {
        val signature = renderedPaginationSignature ?: return
        events().signature(signature)
        events().count(signature, chapter, built.pages.size)
    }

    private fun clearPreparation() {
        preparation.values.forEach { it.cancel() }
        preparation.clear()
        adjacent.values.forEach { it.close() }
        adjacent.clear()
        unavailable.clear()
    }

    private fun prepareAdjacent(target: Int) {
        val settings = config ?: return
        if (target !in 0 until session.chapterCount || target in adjacent || target in unavailable ||
            target in preparation || !canUseNative(target)) return
        val token = generation
        var candidate: NativeEpubChapterLayout? = null
        preparation[target] = scope.launch(start = CoroutineStart.LAZY) {
            try {
                prepareChapterNotes(target)
                val source = if (canUseNative(target)) withContext(Dispatchers.IO) { session.compileNativeChapter(target)?.document }
                    else null
                val built = source?.let {
                    withContext(Dispatchers.Default) { buildLayout(it, settings, this).also { candidate = it } }
                }
                if (released || token != generation || abs(target - chapterIndex) != 1) return@launch
                if (built == null) unavailable += target
                else {
                    adjacent[target] = built
                    candidate = null
                    publishPagination(target, built)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == generation) unavailable += target
                android.util.Log.i("NativeEpub", "Prewarm chapter=$target: ${failure.message}")
            } finally {
                candidate?.close()
                if (!released && token == generation && coroutineContext.isActive) {
                    preparation.remove(target)
                    if (animation?.isRunning != true && (animation !is CurlPageAnim || animation?.isDragging != true)) updateSlots()
                }
            }
        }
        preparation.getValue(target).start()
    }

    private fun slotFor(direction: PageAnimationController.Direction) =
        if (direction == PageAnimationController.Direction.NEXT) nextSlot else previousSlot

    private fun targetPage(step: Int): PageRef? {
        val built = layout ?: return null
        val index = current + step
        if (index in built.pages.indices) return PageRef(chapterIndex, built, index)
        val target = chapterIndex + step
        if (!canUseNative(target)) return null
        return adjacent[target]?.let { PageRef(target, it, if (step > 0) 0 else it.pages.lastIndex) }
    }

    private fun commitTurn(page: PageRef) {
        val previous = layout ?: return
        events().manual()
        val oldChapter = chapterIndex
        if (page.chapter != oldChapter) {
            adjacent.remove(page.chapter)
            adjacent[oldChapter] = previous
            chapterIndex = page.chapter
            document = page.layout.document
            layout = page.layout
            displayedChapter = page.chapter
            notes = chapterNotes(page.chapter)
            events().chapterPrepared(page.chapter, document)
            publishPagination(chapterIndex, page.layout)
        }
        current = page.index
        selectedRange = null
        refreshDecorations()
        updateSlots()
        adjacent.keys.filter { abs(it - chapterIndex) != 1 }.forEach { adjacent.remove(it)?.close() }
        preparation.keys.filter { abs(it - chapterIndex) != 1 }.forEach { preparation.remove(it)?.cancel() }
        unavailable.retainAll(setOf(chapterIndex - 1, chapterIndex + 1))
        publish()
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
        val indices = listOf(targetPage(-1), PageRef(chapterIndex, built, current), targetPage(1))
        listOf(previousSlot, currentSlot, nextSlot).forEachIndexed { i, slot ->
            slot.page = indices[i]; slot.bitmap = oldFrames[indices[i]]
        }
        oldFrames.filterKeys { it !in indices }.values.forEach(bitmapPool::retire)
        frameJob?.cancel()
        if (transition == "curl") {
            val token = generation
            frameJob = scope.launch {
                listOf(currentSlot, nextSlot, previousSlot).forEach { slot ->
                    val page = slot.page ?: return@forEach
                    if (slot.bitmap != null) return@forEach
                    var frame: Bitmap? = null
                    try {
                        val decorations = decorationsFor(page)
                        val started = android.os.SystemClock.elapsedRealtimeNanos()
                        val bitmap = withContext(Dispatchers.Default) {
                            Bitmap.createBitmap(built.config.width, built.config.height, Bitmap.Config.ARGB_8888).also {
                                frame = it; page.layout.draw(Canvas(it), page.index, decorations)
                            }
                        }
                        if (com.huangder.lumibooks.BuildConfig.DEBUG) android.util.Log.d("NativeEpub",
                            "nativeFrameMs=${(android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f} chapter=${page.chapter} page=${page.index}")
                        if (released || token != generation || slot.page != page) bitmap.recycle()
                        else { slot.bitmap = bitmapPool.track(bitmap); invalidate() }
                        frame = null
                    } finally { frame?.let { if (!it.isRecycled) it.recycle() } }
                }
            }
        }
        invalidate()
    }
    private fun canFlip(direction: PageAnimationController.Direction): Boolean {
        if (released || handingOff || displayedChapter != chapterIndex || displayedGeneration != generation) return false
        val step = if (direction == PageAnimationController.Direction.NEXT) 1 else -1
        val target = targetPage(step) ?: return false
        val slot = slotFor(direction)
        return slot.page == target && (transition != "curl" || currentSlot.bitmap != null && slot.bitmap != null)
    }
    fun turn(direction: Int): Boolean = requestTurn(direction)

    private fun requestTurn(direction: Int, swipeStartY: Float? = null): Boolean {
        if (direction == 0 || !canAcceptTurns()) return false
        val destination = if (direction > 0) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
        val backlog = turns.pendingSteps
        if (abs(backlog) >= 8 && (backlog > 0) == (direction > 0)) return true
        turns.offer(destination, swipeStartY, if (swipeStartY == null) CurlTurnInput.TAP else CurlTurnInput.SWIPE,
            expedited = animation?.isRunning == true || backlog != 0)
        drainTurns()
        return true
    }

    private fun canAcceptTurns(): Boolean = !released && !handingOff && layout != null &&
        displayedChapter == chapterIndex && displayedGeneration == generation

    private fun drainTurns() {
        if (!canAcceptTurns() || (touchActive && !queueGesture) || animation?.isRunning == true ||
            animation?.isDragging == true) return
        val turn = turns.peekTurn() ?: return
        val built = layout ?: return
        val dir = turn.direction
        val step = if (dir == PageAnimationController.Direction.NEXT) 1 else -1
        clearSelection()
        if (!canFlip(dir)) {
            if (current + step in built.pages.indices) return
            val chapter = chapterIndex + step
            if (chapter !in 0 until session.chapterCount || chapter in unavailable || !canUseNative(chapter)) {
                turns.clear()
                handingOff = chapter in 0 until session.chapterCount
                if (handingOff) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    built.draw(Canvas(bitmap), current, highlights)
                    events().handoff(chapter, bitmap)
                }
                events().manual(); events().chapter(step)
            } else {
                if (chapter in adjacent && slotFor(dir).page != targetPage(step)) updateSlots()
                else prepareAdjacent(chapter)
            }
            return
        }
        turns.pollTurn()
        val tapDuration = if (turn.expedited) duration.coerceAtMost(120) else duration
        if (transition == "none") slotFor(dir).page?.let(::commitTurn)
        else when (val controller = animation) {
            is CurlPageAnim -> controller.startFromTap(dir, turn.gestureStartY, turn.expedited)
            is WavePageAnim -> {
                controller.setWaveDuration(tapDuration)
                controller.startFromTap(dir)
                controller.setWaveDuration(duration)
            }
            is SlidePageAnim -> {
                controller.setBaseDuration(tapDuration)
                controller.startFromTap(dir)
                controller.setBaseDuration(duration)
            }
        }
    }

    private fun resetTurns() {
        turns.clear()
        touchActive = false
        removeCallbacks(longPress)
        refreshFramesAfterTurn = false
        animation?.abortAnim()
    }
    private fun edge(action: ReaderEdgeTapAction) { turn(if (action == ReaderEdgeTapAction.NEXT_PAGE) 1 else -1) }
    private fun publish() {
        val built = layout ?: return
        events().page(chapterIndex, current, built.pages.size, document.locator(built.pages[current].start, current.toFloat() / built.pages.size).toJson().toString())
        if (current >= built.pages.lastIndex - 2) prepareAdjacent(chapterIndex + 1)
        if (current <= 2) prepareAdjacent(chapterIndex - 1)
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
    private fun jump(offset: Int) { val built = layout?.takeIf { displayedChapter == chapterIndex } ?: return
        resetTurns(); clearSelection(); current = built.pageFor(offset); updateSlots(); publish() }
    private fun resolveRequests() {
        val built = layout?.takeIf { displayedChapter == chapterIndex } ?: return
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
        highlights = highlightsFor(chapterIndex, document, notes, selectedRange)
    }
    private fun decorationsFor(page: PageRef) = if (page.chapter == chapterIndex) highlights
        else highlightsFor(page.chapter, page.layout.document, chapterNotes(page.chapter), null)

    private fun highlightsFor(chapter: Int, source: NativeEpubDocument, chapterNotes: List<Note>, selection: IntRange?) =
        NativeEpubHighlights(annotations = buildList {
            chapterNotes.filter { !it.isStale && it.chapterIndex == chapter && it.type in setOf("highlight", "underline") }.forEach {
                val color = runCatching { Color.parseColor(it.color) }.getOrDefault(0x55ffca28)
                add((source.visiblePosition(it.startPosition) until source.visiblePosition(it.endPosition)) to ((color and 0x00ffffff) or 0x55000000)) }
            selection?.let { add(it to 0x5533aaff) }
        }, sentence = tts?.takeIf { it.chapterIndex == chapter }?.let {
            val start = source.visiblePosition(it.startOffset)
            val end = source.visiblePosition(it.endOffset)
            (start until end).takeUnless { it.isEmpty() }?.to(ttsColor)
        })
    private fun invalidateFrames() {
        if (animation?.isRunning == true) {
            refreshFramesAfterTurn = true
            return
        }
        refreshFramesAfterTurn = false
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
        events().selection(EpubSelectionInfo(document.selectionText(range.first, range.last + 1), a, b,
            document.locator(range.first).toJson().toString(), document.locator(range.last + 1).toJson().toString(),
            bounds.centerX() + xy[0], bounds.centerY() + xy[1], bounds.left + xy[0], bounds.top + xy[1], bounds.right + xy[0], bounds.bottom + xy[1]))
    }
    private fun clearSelection() { if (selectedRange != null) {
        selectedRange = null; refreshDecorations(); invalidateFrames(); events().selectionCleared() } }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!canAcceptTurns()) return true
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            touchActive = true
            queueGesture = animation?.isRunning == true || turns.peekTurn() != null
            queuedGestureMoved = false
            downX = event.x
            downY = event.y
        } else if (!touchActive) return true
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchActive = false
            if (turns.peekTurn() != null || refreshFramesAfterTurn) postInvalidateOnAnimation()
        }
        if (queueGesture) {
            handleQueuedGesture(event)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pulling = false; longPressed = false
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
                    val href = hit?.let { h -> if (h.imagePath != null) document.imageLinks[h.start]
                        else document.runs.firstOrNull { h.start >= it.start && h.start < it.end }?.href }
                    href?.let(document.footnotes::get)?.let { showFootnote(it); return true }
                    if (href != null) { session.resolveInternalLink(chapterIndex, href)?.let { events().internalLink(it.first, it.second) } ?: events().externalLink(href); return true }
                    if (hit?.imagePath != null) {
                        val bounds = hit.bounds
                        events().image(EpubImagePreviewRequest(session.nativeImageUrl(hit.imagePath),
                            "", bounds.left, bounds.top, bounds.right, bounds.bottom,
                            bounds.width().toInt(), bounds.height().toInt())); return true
                    }
                    val note = hit?.let { h -> val pos = document.sourcePosition(h.start); notes.firstOrNull { !it.isStale && it.chapterIndex == chapterIndex && pos in it.startPosition until it.endPosition } }
                    if (note != null) { selectedRange = document.visiblePosition(note.startPosition) until document.visiblePosition(note.endPosition); refreshDecorations(); emitSelection(hit!!.bounds); return true }
                    when { event.x < width * .3f -> edge(edgeTap.leftAction); event.x > width * .7f -> edge(edgeTap.rightAction); else -> events().menu() }
                    return true
                }
                val direction = swipeDirection(event)
                if (direction != null) {
                    val destination = if (direction > 0) PageAnimationController.Direction.NEXT else PageAnimationController.Direction.PREV
                    if (transition == "none" ||
                        !(animation is CurlPageAnim && animation?.isDragging == true) && !canFlip(destination)) {
                        animation?.abortAnim()
                        requestTurn(direction, downY)
                        return true
                    }
                }
            }
        }
        if (transition == "none") return true
        return animation?.onTouchEvent(event) ?: true
    }

    private fun swipeDirection(event: MotionEvent): Int? {
        if (event.actionMasked != MotionEvent.ACTION_UP) return null
        val deltaX = event.x - downX
        if (abs(deltaX) <= width * .08f || abs(deltaX) <= abs(event.y - downY)) return null
        val reversed = document.bodyStyle.vertical || session.pageProgressionDirection(chapterIndex) == EpubPageProgressionDirection.RTL
        return if ((deltaX < 0) != reversed) 1 else -1
    }

    private fun handleQueuedGesture(event: MotionEvent) {
        val withinTapSlop = hypot(event.x - downX, event.y - downY) < ViewConfiguration.get(context).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!withinTapSlop) queuedGestureMoved = true
            MotionEvent.ACTION_POINTER_DOWN -> touchActive = false
            MotionEvent.ACTION_UP -> {
                val swipe = swipeDirection(event)
                if (swipe != null) requestTurn(swipe, downY)
                else if (!queuedGestureMoved && withinTapSlop &&
                    event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                    when {
                        event.x < width * .3f -> edge(edgeTap.leftAction)
                        event.x > width * .7f -> edge(edgeTap.rightAction)
                    }
                }
            }
        }
    }
    override fun dispatchDraw(canvas: Canvas) {
        val built = layout ?: return
        val controller = animation
        val direction = controller?.currentDirection ?: PageAnimationController.Direction.NONE
        val hasTarget = direction != PageAnimationController.Direction.NONE && slotFor(direction).page != null
        if (hasTarget && (controller?.isRunning == true || controller?.isDragging == true)) controller.onDraw(canvas)
        else built.draw(canvas, current, highlights)
    }
    override fun computeScroll() {
        if (animation?.computeScroll() == true) postInvalidateOnAnimation()
        if (refreshFramesAfterTurn && animation?.isRunning != true && !touchActive) invalidateFrames()
        drainTurns()
    }
    private fun destroyAnimation() { resetTurns(); (animation as? CurlPageAnim)?.destroy(); animation = null }
    fun release() {
        if (released) return
        released = true; generation++; removeCallbacks(longPress); destroyAnimation(); scope.cancel(); bitmapPool.destroy()
        clearPreparation()
        // Wait for drawing jobs to release their layout before recycling decoded images.
        val old = layout; layout = null
        if (old != null) CoroutineScope(Dispatchers.Main.immediate).launch { layoutJob?.join(); frameJob?.join(); old.close() }
    }
}

internal fun resolveNativeEpubFonts(document: NativeEpubDocument, cacheDir: File): List<NativeEpubResolvedFont> {
    if (document.embeddedFonts.isEmpty()) return emptyList()
    val directory = File(cacheDir, "epub-native-fonts").apply { mkdirs() }
    return document.embeddedFonts.map { face ->
        val extension = face.resource.path.substringAfterLast('.', "ttf").lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(face.resource.bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        val file = File(directory, "$digest.$extension")
        if (!file.isFile || file.length() != face.resource.bytes.size.toLong()) {
            val temporary = File.createTempFile(digest, ".tmp", directory)
            try {
                temporary.writeBytes(face.resource.bytes)
                if (!temporary.renameTo(file)) {
                    check(file.isFile && file.length() == face.resource.bytes.size.toLong()) {
                        "Could not cache embedded font ${face.resource.path}"
                    }
                }
            } finally { temporary.delete() }
        }
        NativeEpubResolvedFont(face.family, face.weight, face.italic, Typeface.createFromFile(file))
    }
}
