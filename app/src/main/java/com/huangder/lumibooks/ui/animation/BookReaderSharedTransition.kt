package com.huangder.lumibooks.ui.animation

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.toAppPath
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.reader.readerTransitionPaperColor
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class BookReaderTransitionPhase {
    Library,
    Opening,
    ReaderLoading,
    Ready,
    Reader,
    Closing
}

internal fun BookReaderTransitionPhase.canTransitionTo(
    next: BookReaderTransitionPhase
): Boolean = when {
    next == BookReaderTransitionPhase.Opening -> true
    next == BookReaderTransitionPhase.Library -> true
    this == BookReaderTransitionPhase.Library -> false
    this == BookReaderTransitionPhase.Opening ->
        next == BookReaderTransitionPhase.ReaderLoading ||
            next == BookReaderTransitionPhase.Ready ||
            next == BookReaderTransitionPhase.Closing
    this == BookReaderTransitionPhase.ReaderLoading ->
        next == BookReaderTransitionPhase.Ready ||
            next == BookReaderTransitionPhase.Closing
    this == BookReaderTransitionPhase.Ready ->
        next == BookReaderTransitionPhase.Reader ||
            next == BookReaderTransitionPhase.Closing
    this == BookReaderTransitionPhase.Reader -> next == BookReaderTransitionPhase.Closing
    else -> false
}

internal data class BookCoverAnchor(
    val key: Any,
    val bookId: String,
    val bounds: Rect,
    val cornerRadiusDp: Float,
    val titleStyle: BookCoverTitleStyle?,
    val showReadingProgress: Boolean = true,
    val presentation: BookReaderPresentation = BookReaderPresentation.Window
)

internal enum class BookReaderPresentation { Window, CoverFlow }

internal enum class BookCoverTitleColor {
    Primary,
    Secondary
}

internal data class BookCoverTitleStyle(
    val fontSizeSp: Float,
    val maxLines: Int,
    val paddingDp: Float,
    val color: BookCoverTitleColor,
    val useKaiTi: Boolean,
    val textAlignCenter: Boolean,
    val takeCharacters: Int? = null
)

internal object BookReaderMotion {
    const val WINDOW_DURATION_MS = 650
    // Position and size share one 650 ms window so the paper handoff stays on
    // the same path at every stage of the hero transition.
    const val POSITION_DURATION_MS = WINDOW_DURATION_MS
    const val SIZE_DURATION_MS = 650
    const val COVER_STAGE_FRACTION = 0.30f
    const val COVER_STAGE_MS = 195
    // Keep a short overlap at the handoff so the cover does not visibly pop into
    // the paper shell on a busy frame. The midpoint remains the 30% handoff.
    const val COVER_CROSSFADE_FRACTION = 0.08f
    const val CONTENT_REVEAL_MS = 180
    const val CONTENT_EXIT_MS = 180
    const val CONTROL_POINT_OFFSET_DP = 28f
    const val LOADING_INDICATOR_DELAY_MS = 600L
    const val LIBRARY_BLUR_DP = 18f
    const val LIBRARY_DIM_ALPHA = 0.28f
    const val LIBRARY_SCALE = 0.90f
    const val DEFAULT_CORNER_RADIUS_DP = 14f

    // Opening eases at both ends; closing remaps this shared geometry below.
    val PositionEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val SizeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    val CloseEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    // Undo the opening easing so returning along the shared path starts fast
    // and decelerates, including a return interrupted partway through opening.
    fun closingTimelineEasing(startProgress: Float): androidx.compose.animation.core.Easing =
        androidx.compose.animation.core.Easing { elapsed ->
            if (elapsed <= 0f || elapsed >= 1f || startProgress <= 0f) elapsed
            else {
                val geometry = PositionEasing.transform(startProgress) *
                    (1f - CloseEasing.transform(elapsed))
                var low = 0f
                var high = startProgress
                repeat(20) {
                    val middle = (low + high) / 2f
                    if (PositionEasing.transform(middle) < geometry) low = middle else high = middle
                }
                1f - (low + high) / (2f * startProgress)
            }
        }

    fun sharedKey(bookId: String): String = "book-window-$bookId"

    fun scaledWindowDuration(): Int = VisualMotion.durationMillis(WINDOW_DURATION_MS)

    fun scaledCoverStageDuration(): Int =
        VisualMotion.durationMillis(COVER_STAGE_MS)

    fun coverFade(progress: Float): Float {
        val halfWindow = COVER_CROSSFADE_FRACTION / 2f
        return ((progress - (COVER_STAGE_FRACTION - halfWindow)) /
            COVER_CROSSFADE_FRACTION).coerceIn(0f, 1f)
    }

    fun cornerRadius(startRadius: Float, progress: Float): Float {
        val elapsed = WINDOW_DURATION_MS * progress.coerceIn(0f, 1f)
        val middleRadius = minOf(startRadius, 8f)
        return when {
            elapsed <= 100f -> startRadius
            elapsed <= 340f -> lerp(startRadius, middleRadius,
                FastOutSlowInEasing.transform((elapsed - 100f) / 240f))
            else -> lerp(middleRadius, 0f,
                FastOutSlowInEasing.transform((elapsed - 340f) / (WINDOW_DURATION_MS - 340f)))
        }
    }

    fun windowBounds(source: Rect, target: Rect, position: Float, size: Float, offsetPx: Float): Rect {
        val center = quadraticPoint(source.center,
            controlPoint(source.center, target.center, target.center, offsetPx), target.center, position)
        val width = lerp(source.width, target.width, size)
        val height = lerp(source.height, target.height, size)
        return Rect(center.x - width / 2f, center.y - height / 2f,
            center.x + width / 2f, center.y + height / 2f)
    }

    fun controlPoint(
        start: Offset,
        end: Offset,
        screenCenter: Offset,
        offsetPx: Float
    ): Offset {
        val midpoint = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = hypot(dx, dy)
        if (distance <= 0.001f) return midpoint

        val perpendicular = Offset(-dy / distance, dx / distance)
        val towardCenter = Offset(screenCenter.x - midpoint.x, screenCenter.y - midpoint.y)
        val dot = perpendicular.x * towardCenter.x + perpendicular.y * towardCenter.y
        val sign = when {
            abs(dot) > 0.5f -> if (dot > 0f) 1f else -1f
            start.x <= screenCenter.x -> 1f
            else -> -1f
        }
        return Offset(
            x = midpoint.x + perpendicular.x * offsetPx * sign,
            y = midpoint.y + perpendicular.y * offsetPx * sign
        )
    }

    fun quadraticPoint(
        start: Offset,
        control: Offset,
        end: Offset,
        progress: Float
    ): Offset {
        val p = progress.coerceIn(0f, 1f)
        val inverse = 1f - p
        return Offset(
            x = inverse * inverse * start.x + 2f * inverse * p * control.x + p * p * end.x,
            y = inverse * inverse * start.y + 2f * inverse * p * control.y + p * p * end.y
        )
    }

    fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction.coerceIn(0f, 1f)

    fun coverFitScale(source: Rect, windowWidth: Float, windowHeight: Float): Float {
        if (source.width <= 0f || source.height <= 0f) return 1f
        return minOf(
            windowWidth / source.width,
            windowHeight / source.height
        )
    }

    fun edgeBlend(
        source: Rect,
        windowWidth: Float,
        windowHeight: Float
    ): Float {
        if (windowHeight <= 0f) return 0f
        val fitScale = coverFitScale(source, windowWidth, windowHeight)
        val coverHeight = source.height * fitScale
        return ((windowHeight - coverHeight) / windowHeight).coerceIn(0f, 1f)
    }
}

internal fun bookCoverMemoryCacheKey(bookId: String, coverPath: String?): String =
    "${bookId}_${coverPath.orEmpty()}"

private val nextCoverAnchorId = AtomicLong(1L)

private fun extractDominantCoverColor(path: String?): Int? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 256) {
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    ) ?: return null
    return try {
        Palette.from(bitmap)
            .generate()
            .getDominantColor(0)
            .takeIf { it != 0 }
    } finally {
        bitmap.recycle()
    }
}

@Stable
internal class BookReaderTransitionState internal constructor(
    private val scope: CoroutineScope
) {
    private val phaseState = mutableStateOf(BookReaderTransitionPhase.Library)
    private val windowProgressAnim = Animatable(0f)
    private val shellAlphaAnim = Animatable(1f)
    private val readerExitAlphaAnim = Animatable(1f)
    private val libraryProgressAnim = Animatable(0f)
    private val coverFlowProgressAnim = Animatable(0f)
    private val readerRevealAnim = Animatable(0f)
    val coverFlowProgressSnapshot: State<Float> = coverFlowProgressAnim.asState()
    val readerRevealSnapshot: State<Float> = readerRevealAnim.asState()
    var presentation by mutableStateOf(BookReaderPresentation.Window)
        private set
    private val coverAnchors = mutableMapOf<Any, BookCoverAnchor>()
    private var motionJob: Job? = null
    private var revealJob: Job? = null
    private var colorJob: Job? = null
    private var windowMotionComplete = false
    private var closeSourceLocked = false
    private var closeNavigationIssued by mutableStateOf(false)
    private var closeGeometryStarted = false
    var sessionId by mutableStateOf(0L)
        private set

    val phaseSnapshot: State<BookReaderTransitionPhase> = phaseState
    val windowProgressSnapshot: State<Float> = windowProgressAnim.asState()
    val positionSnapshot: State<Float> = derivedStateOf {
        BookReaderMotion.PositionEasing.transform(windowProgressAnim.value)
    }
    val sizeSnapshot: State<Float> = derivedStateOf {
        BookReaderMotion.SizeEasing.transform(windowProgressAnim.value)
    }
    val cornerRadiusSnapshot: State<Float> = derivedStateOf {
        BookReaderMotion.cornerRadius(sourceCornerRadiusDp, windowProgressAnim.value)
    }
    val coverFadeSnapshot: State<Float> = derivedStateOf {
        BookReaderMotion.coverFade(windowProgressAnim.value)
    }
    val shellAlphaSnapshot: State<Float> = shellAlphaAnim.asState()
    val readerExitAlphaSnapshot: State<Float> = readerExitAlphaAnim.asState()
    val libraryProgressSnapshot: State<Float> = libraryProgressAnim.asState()

    val phase: BookReaderTransitionPhase
        get() = phaseState.value

    // The reader sits below the paper shell during the handoff. Both use the
    // resolved paper color, so their crossfade keeps the shelf from leaking while
    // allowing the actual page content to fade in visibly.
    val readerLayerAlpha: Float
        get() = if (presentation == BookReaderPresentation.CoverFlow) {
            CoverFlowReaderMotion.readerAlpha(coverFlowProgressAnim.value) *
                readerRevealAnim.value *
                (if (phase == BookReaderTransitionPhase.Closing) readerExitAlphaAnim.value else 1f)
        } else when (phase) {
            BookReaderTransitionPhase.Opening, BookReaderTransitionPhase.ReaderLoading -> 0f
            BookReaderTransitionPhase.Ready -> readerRevealAnim.value
            BookReaderTransitionPhase.Closing -> readerExitAlphaAnim.value
            BookReaderTransitionPhase.Reader -> 1f
            BookReaderTransitionPhase.Library -> 0f
        }

    val overlayAlpha: Float
        get() = if (phase == BookReaderTransitionPhase.Closing) {
            maxOf(shellAlphaAnim.value, 1f - readerExitAlphaAnim.value)
        } else shellAlphaAnim.value

    var activeBookId by mutableStateOf<String?>(null)
        private set

    var coverPath by mutableStateOf<String?>(null)
        private set

    var coverTitle by mutableStateOf("")
        private set

    var coverBook by mutableStateOf<Book?>(null)
        private set

    var coverDownloadState by mutableStateOf<BookDownloadState?>(null)
        private set

    var coverTitleStyle by mutableStateOf<BookCoverTitleStyle?>(null)
        private set

    var coverShowsReadingProgress by mutableStateOf(true)
        private set

    var sourceBounds by mutableStateOf<Rect?>(null)
        private set

    var sourceCornerRadiusDp by mutableFloatStateOf(BookReaderMotion.DEFAULT_CORNER_RADIUS_DP)
        private set

    var readerReady by mutableStateOf(false)
        private set

    var activeAnchorKey by mutableStateOf<Any?>(null)
        private set

    var closeAnchorKey by mutableStateOf<Any?>(null)
        private set

    var dominantColorArgb by mutableStateOf<Int?>(null)
        private set

    var readerPaperColorArgb by mutableStateOf<Int?>(null)
        private set

    val closeNavigationReady: Boolean
        get() = phase == BookReaderTransitionPhase.Closing && closeNavigationIssued

    val usesHeroTransition: Boolean
        get() = activeBookId != null && phase != BookReaderTransitionPhase.Library

    private var coverFlowReturnReady = false

    fun updateCoverFlowReturnSource(bookId: String, bounds: Rect) {
        if (presentation != BookReaderPresentation.CoverFlow || activeBookId != bookId ||
            phase != BookReaderTransitionPhase.Closing || closeSourceLocked ||
            bounds.width <= 0f || bounds.height <= 0f) return
        sourceBounds = bounds
        coverFlowReturnReady = true
    }

    fun registerCoverAnchor(
        anchorKey: Any,
        bookId: String,
        bounds: Rect,
        cornerRadiusDp: Float,
        titleStyle: BookCoverTitleStyle?,
        showReadingProgress: Boolean = true,
        presentation: BookReaderPresentation = BookReaderPresentation.Window
    ) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        val anchor = BookCoverAnchor(
            key = anchorKey,
            bookId = bookId,
            bounds = bounds,
            cornerRadiusDp = cornerRadiusDp.coerceAtLeast(0f),
            titleStyle = titleStyle,
            showReadingProgress = showReadingProgress,
            presentation = presentation
        )
        if (coverAnchors[anchorKey] != anchor) {
            coverAnchors[anchorKey] = anchor
            refreshCloseSource(bookId)
        }
    }

    fun unregisterCoverAnchor(anchorKey: Any) {
        coverAnchors.remove(anchorKey)
    }

    fun sourceCoverAlpha(bookId: String, anchorKey: Any): Float {
        if (activeBookId != bookId) return 1f
        if (presentation == BookReaderPresentation.CoverFlow && phase != BookReaderTransitionPhase.Library) return 0f
        return when (phaseState.value) {
            BookReaderTransitionPhase.Opening ->
                if (activeAnchorKey == anchorKey) 0f else 1f
            BookReaderTransitionPhase.Closing ->
                if (closeAnchorKey == null || closeAnchorKey == anchorKey) 0f else 1f
            BookReaderTransitionPhase.Library -> 1f
            else -> 0f
        }
    }

    suspend fun startOpen(
        book: Book,
        downloadState: BookDownloadState?,
        sourceBounds: Rect?,
        sourceCornerRadiusDp: Float,
        sourcePresentation: BookReaderPresentation? = null
    ): Boolean {
        val bookId = book.id
        val anchor = selectCoverAnchor(bookId, sourceBounds, sourcePresentation)
            ?: if (sourcePresentation == BookReaderPresentation.CoverFlow &&
                sourceBounds != null && sourceBounds.width > 0f && sourceBounds.height > 0f) {
                BookCoverAnchor(
                    key = "cover-flow-launch-$bookId",
                    bookId = bookId,
                    bounds = sourceBounds,
                    cornerRadiusDp = 2f,
                    titleStyle = null,
                    presentation = sourcePresentation
                )
            } else return false
        val startRadius = if (sourceCornerRadiusDp > 0f) {
            sourceCornerRadiusDp
        } else {
            anchor.cornerRadiusDp
        }.coerceAtLeast(0f)

        motionJob?.cancel()
        revealJob?.cancel()
        colorJob?.cancel()
        sessionId++
        windowMotionComplete = false
        closeSourceLocked = false
        closeNavigationIssued = false
        closeGeometryStarted = false
        activeBookId = bookId
        closeAnchorKey = null
        coverBook = book
        coverDownloadState = downloadState
        coverPath = book.coverPath
        coverTitle = book.title
        coverTitleStyle = anchor.titleStyle
        coverShowsReadingProgress = anchor.showReadingProgress
        this.sourceBounds = if (sourcePresentation == BookReaderPresentation.CoverFlow && sourceBounds != null) {
            sourceBounds
        } else anchor.bounds
        this.sourceCornerRadiusDp = startRadius
        activeAnchorKey = anchor.key
        readerReady = false
        dominantColorArgb = null
        readerPaperColorArgb = null
        presentation = sourcePresentation ?: anchor.presentation
        setPhase(BookReaderTransitionPhase.Opening)

        windowProgressAnim.snapTo(0f)
        shellAlphaAnim.snapTo(1f)
        readerExitAlphaAnim.snapTo(1f)
        libraryProgressAnim.snapTo(0f)
        coverFlowProgressAnim.snapTo(0f)
        readerRevealAnim.snapTo(0f)

        if (presentation == BookReaderPresentation.CoverFlow) {
            setPhase(BookReaderTransitionPhase.ReaderLoading)
            motionJob = scope.launch {
                coverFlowProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = VisualMotion.tween(620, easing = FastOutSlowInEasing)
                )
                if (phase == BookReaderTransitionPhase.Ready && readerRevealAnim.value >= 0.999f) {
                    setPhase(BookReaderTransitionPhase.Reader)
                }
            }
            return true
        }

        motionJob = scope.launch {
            coroutineScope {
                launch {
                    windowProgressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = VisualMotion.tween(
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS,
                            easing = LinearEasing
                        )
                    )
                }
                launch {
                    libraryProgressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = VisualMotion.tween(
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            windowMotionComplete = true
            if (phase == BookReaderTransitionPhase.Opening) {
                setPhase(BookReaderTransitionPhase.ReaderLoading)
                if (readerReady) revealReader()
            }
        }
        colorJob = scope.launch {
            val color = withContext(Dispatchers.IO) {
                extractDominantCoverColor(book.coverPath)
            }
            if (activeBookId == bookId) {
                dominantColorArgb = color
            }
        }
        return true
    }

    fun markReaderReady(expectedSessionId: Long = sessionId) {
        if (expectedSessionId != sessionId || phase !in setOf(
                BookReaderTransitionPhase.Opening, BookReaderTransitionPhase.ReaderLoading)) return
        readerReady = true
        if (phaseState.value == BookReaderTransitionPhase.ReaderLoading &&
            (presentation == BookReaderPresentation.CoverFlow || windowMotionComplete)
        ) {
            revealReader()
        }
    }

    fun updateReaderPaperColor(colorArgb: Int, expectedSessionId: Long = sessionId) {
        if (expectedSessionId != sessionId || activeBookId == null) return
        readerPaperColorArgb = colorArgb
    }

    fun startClose(): Boolean {
        val bookId = activeBookId ?: return false
        val anchorKey = activeAnchorKey ?: return false
        if (phaseState.value == BookReaderTransitionPhase.Library) return false
        val visibleReaderAlpha = when (phase) {
            BookReaderTransitionPhase.Reader -> 1f
            BookReaderTransitionPhase.Ready -> 1f - shellAlphaAnim.value
            else -> 0f
        }
        val anchor = coverAnchors[anchorKey]
        val targetBounds = sourceBounds ?: anchor?.bounds ?: return false
        val targetRadius = sourceCornerRadiusDp.takeIf { it > 0f }
            ?: anchor?.cornerRadiusDp
            ?: BookReaderMotion.DEFAULT_CORNER_RADIUS_DP
        coverTitleStyle = anchor?.titleStyle ?: coverTitleStyle
        coverShowsReadingProgress = anchor?.showReadingProgress ?: coverShowsReadingProgress

        motionJob?.cancel()
        revealJob?.cancel()
        colorJob?.cancel()
        sourceBounds = targetBounds
        sourceCornerRadiusDp = targetRadius
        closeAnchorKey = anchorKey
        closeSourceLocked = false
        coverFlowReturnReady = false
        closeNavigationIssued = false
        closeGeometryStarted = false
        setPhase(BookReaderTransitionPhase.Closing)

        motionJob = scope.launch {
            readerExitAlphaAnim.snapTo(visibleReaderAlpha)
            if (presentation == BookReaderPresentation.CoverFlow) {
                readerExitAlphaAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = VisualMotion.tween(BookReaderMotion.CONTENT_EXIT_MS)
                )
                // The focused cover can move while the reader body fades. Wait for
                // its latest measured bounds, then lock it before the return path.
                if (!coverFlowReturnReady) {
                    for (frame in 0 until 18) {
                        withFrameNanos { }
                        if (phase != BookReaderTransitionPhase.Closing || activeBookId != bookId) {
                            return@launch
                        }
                        if (coverFlowReturnReady) break
                    }
                }
                closeSourceLocked = true
                coverFlowProgressAnim.animateTo(
                    0f,
                    VisualMotion.tween(380, easing = FastOutSlowInEasing)
                )
                clearToLibrary()
                return@launch
            }
            if (visibleReaderAlpha > 0f) {
                readerExitAlphaAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = VisualMotion.tween(
                        durationMillis = BookReaderMotion.CONTENT_EXIT_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            } else {
                readerExitAlphaAnim.snapTo(0f)
            }
            closeNavigationIssued = true
        }
        return true
    }

    /** Called after the reader route has been popped and the destination cover can register. */
    fun completeCloseAfterNavigation() {
        val bookId = activeBookId ?: return
        if (presentation != BookReaderPresentation.Window ||
            phase != BookReaderTransitionPhase.Closing || closeGeometryStarted
        ) return
        closeGeometryStarted = true
        closeNavigationIssued = false
        motionJob?.cancel()
        motionJob = scope.launch {
            shellAlphaAnim.snapTo(1f)
            runCloseGeometry(bookId)
        }
    }

    fun consumeCloseNavigation(): Boolean {
        if (!closeNavigationReady) return false
        closeNavigationIssued = false
        return true
    }

    fun fallbackToLibrary() {
        motionJob?.cancel()
        revealJob?.cancel()
        colorJob?.cancel()
        setPhase(BookReaderTransitionPhase.Library)
        activeBookId = null
        activeAnchorKey = null
        closeAnchorKey = null
        coverPath = null
        coverTitle = ""
        coverTitleStyle = null
        coverBook = null
        coverDownloadState = null
        sourceBounds = null
        readerReady = false
        dominantColorArgb = null
        readerPaperColorArgb = null
        closeNavigationIssued = false
        closeGeometryStarted = false
    }

    private fun revealReader() {
        if (phase != BookReaderTransitionPhase.ReaderLoading ||
            (presentation == BookReaderPresentation.Window && !windowMotionComplete)) return
        revealJob?.cancel()
        setPhase(BookReaderTransitionPhase.Ready)
        revealJob = scope.launch {
            if (presentation == BookReaderPresentation.CoverFlow) {
                readerRevealAnim.animateTo(
                    1f,
                    VisualMotion.tween(BookReaderMotion.CONTENT_REVEAL_MS, easing = FastOutSlowInEasing)
                )
                if (phaseState.value == BookReaderTransitionPhase.Ready && coverFlowProgressAnim.value >= 0.999f) {
                    setPhase(BookReaderTransitionPhase.Reader)
                }
                return@launch
            }
            coroutineScope {
                launch {
                    readerRevealAnim.animateTo(
                        1f,
                        VisualMotion.tween(
                            BookReaderMotion.CONTENT_REVEAL_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    shellAlphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = VisualMotion.tween(
                            durationMillis = BookReaderMotion.CONTENT_REVEAL_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            if (phaseState.value == BookReaderTransitionPhase.Ready && windowMotionComplete) {
                setPhase(BookReaderTransitionPhase.Reader)
            }
        }
    }

    private suspend fun runCloseGeometry(bookId: String) {
        if (sourceBounds == null) return
        var previousCandidate: BookCoverAnchor? = null
        var stableFrames = 0
        for (frame in 0 until 18) {
            withFrameNanos { }
            if (phase != BookReaderTransitionPhase.Closing || activeBookId != bookId) return
            val candidate = returnAnchor(bookId, allowFallback = frame >= 12)
            stableFrames = if (candidate != null && candidate == previousCandidate) stableFrames + 1 else 0
            previousCandidate = candidate
            if (candidate != null && stableFrames >= 2) {
                coverTitleStyle = candidate.titleStyle
                coverShowsReadingProgress = candidate.showReadingProgress
                sourceBounds = candidate.bounds
                sourceCornerRadiusDp = candidate.cornerRadiusDp
                closeAnchorKey = candidate.key
                break
            }
        }
        closeSourceLocked = true

        val closeWindowBaseDuration = (BookReaderMotion.WINDOW_DURATION_MS *
            windowProgressAnim.value.coerceIn(0f, 1f)).roundToInt()
        val closeEasing = BookReaderMotion.closingTimelineEasing(windowProgressAnim.value)

        coroutineScope {
            launch {
                windowProgressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = VisualMotion.tween(
                        durationMillis = closeWindowBaseDuration,
                        easing = closeEasing
                    )
                )
            }
            launch {
                libraryProgressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = VisualMotion.tween(
                        durationMillis = closeWindowBaseDuration,
                        easing = BookReaderMotion.CloseEasing
                    )
                )
            }
        }
        refreshCloseSource(bookId)
        clearToLibrary()
    }

    private fun clearToLibrary() {
        setPhase(BookReaderTransitionPhase.Library)
        activeBookId = null
        activeAnchorKey = null
        closeAnchorKey = null
        coverPath = null
        coverTitle = ""
        coverTitleStyle = null
        coverBook = null
        coverDownloadState = null
        sourceBounds = null
        readerReady = false
        dominantColorArgb = null
        readerPaperColorArgb = null
        closeNavigationIssued = false
        closeGeometryStarted = false
    }

    private fun selectCoverAnchor(bookId: String, sourceBounds: Rect?, presentation: BookReaderPresentation?): BookCoverAnchor? {
        val candidates = coverAnchors.values.filter { it.bookId == bookId && (presentation == null || it.presentation == presentation) }
        if (candidates.isEmpty()) return null
        if (sourceBounds == null) return candidates.last()
        return candidates.minByOrNull { anchor ->
            rectDistance(anchor.bounds, sourceBounds)
        }
    }

    private fun refreshCloseSource(bookId: String) {
        // CF bounds are supplied after focus restoration. A graphics-layer movement does not
        // necessarily run onGloballyPositioned, so its previously registered rectangle is stale.
        if (presentation == BookReaderPresentation.CoverFlow || closeSourceLocked) return
        if (
            phaseState.value != BookReaderTransitionPhase.Closing ||
            activeBookId != bookId
        ) {
            return
        }
        val refreshed = returnAnchor(bookId, allowFallback = false) ?: return
        sourceBounds = refreshed.bounds
        sourceCornerRadiusDp = refreshed.cornerRadiusDp
        coverShowsReadingProgress = refreshed.showReadingProgress
        closeAnchorKey = refreshed.key
    }

    private fun returnAnchor(bookId: String, allowFallback: Boolean = true): BookCoverAnchor? {
        val candidates = coverAnchors.values.filter {
            it.bookId == bookId && it.presentation == presentation
        }
        return candidates.firstOrNull { it.key == activeAnchorKey }
            ?: candidates.takeIf { allowFallback }?.minByOrNull { anchor ->
                sourceBounds?.let { rectDistance(anchor.bounds, it) } ?: 0f
            }
    }

    private fun rectDistance(first: Rect, second: Rect): Float {
        val left = abs(first.left - second.left)
        val top = abs(first.top - second.top)
        val right = abs(first.right - second.right)
        val bottom = abs(first.bottom - second.bottom)
        return left + top + right + bottom
    }

    private fun setPhase(next: BookReaderTransitionPhase) {
        if (
            phaseState.value == next ||
            phaseState.value.canTransitionTo(next)
        ) {
            phaseState.value = next
        }
    }
}

@Stable
internal class BookReaderAnchorScope(
    val transitionState: BookReaderTransitionState
)

internal val LocalBookReaderAnchorScope =
    staticCompositionLocalOf<BookReaderAnchorScope?> { null }

private class LibraryCoordinateSpace {
    var coordinates: LayoutCoordinates? = null
}

private val LocalLibraryCoordinateSpace = staticCompositionLocalOf<LibraryCoordinateSpace?> { null }

@Composable
internal fun rememberBookReaderTransitionState(): BookReaderTransitionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BookReaderTransitionState(scope) }
}

@Composable
internal fun Modifier.bookCoverTransitionAnchor(
    bookId: String,
    cornerRadiusDp: Float,
    anchorId: Any? = null,
    titleStyle: BookCoverTitleStyle? = null,
    showReadingProgress: Boolean = com.huangder.lumibooks.ui.theme.LocalCoverReadingProgressEnabled.current,
    presentation: BookReaderPresentation = BookReaderPresentation.Window
): Modifier {
    val anchors = LocalBookReaderAnchorScope.current ?: return this
    val librarySpace = LocalLibraryCoordinateSpace.current
    val anchorKey = remember(bookId, anchorId) {
        anchorId ?: "${BookReaderMotion.sharedKey(bookId)}-${nextCoverAnchorId.getAndIncrement()}"
    }
    DisposableEffect(anchorKey) {
        onDispose { anchors.transitionState.unregisterCoverAnchor(anchorKey) }
    }
    return this
        .onGloballyPositioned { coordinates ->
            val state = anchors.transitionState
            val root = coordinates.findRootCoordinates()
            // boundsInRoot clips lazy-list cells to the viewport. The return cover
            // needs its complete rectangle, including a partially visible edge.
            val bounds = root.localBoundingBoxOf(coordinates, clipBounds = false)
            val rootSize = root.size
            val scale = if (state.presentation == BookReaderPresentation.Window && state.usesHeroTransition)
                BookReaderMotion.lerp(1f, BookReaderMotion.LIBRARY_SCALE, state.libraryProgressSnapshot.value)
            else 1f
            val centerX = rootSize.width / 2f
            val centerY = rootSize.height / 2f
            val library = librarySpace?.coordinates?.takeIf { it.isAttached }
            val stableBounds = if (library != null) {
                // Measure inside the transformed library, so scale changes do not
                // alter the return anchor or depend on the previous render frame.
                val localBounds = library.localBoundingBoxOf(coordinates, clipBounds = false)
                val localCenter = Offset(library.size.width / 2f, library.size.height / 2f)
                val origin = root.localPositionOf(library, localCenter) - localCenter
                localBounds.translate(origin)
            } else Rect(
                centerX + (bounds.left - centerX) / scale, centerY + (bounds.top - centerY) / scale,
                centerX + (bounds.right - centerX) / scale, centerY + (bounds.bottom - centerY) / scale)
            anchors.transitionState.registerCoverAnchor(
                anchorKey = anchorKey,
                bookId = bookId,
                bounds = stableBounds,
                cornerRadiusDp = cornerRadiusDp,
                titleStyle = titleStyle,
                showReadingProgress = showReadingProgress,
                presentation = presentation
            )
        }
        .then(
            if (anchors.transitionState.activeBookId == bookId) {
                Modifier.graphicsLayer {
                    alpha = anchors.transitionState.sourceCoverAlpha(
                        bookId = bookId,
                        anchorKey = anchorKey
                    )
                }
            } else {
                Modifier
            }
        )
}

/** One full-screen coordinate space is shared by library content and the floating tab bar. */
@Composable
internal fun Modifier.bookReaderLibraryLayer(
    transition: BookReaderTransitionState,
    blurEnabled: Boolean
): Modifier {
    val allowBlur = blurEnabled && !LocalReduceTransparency.current
    val effects = remember(transition, LocalDensity.current.density, allowBlur) {
        if (allowBlur) arrayOfNulls<androidx.compose.ui.graphics.RenderEffect>(37) else emptyArray()
    }
    return graphicsLayer {
        val active = transition.phase != BookReaderTransitionPhase.Library
        val isCoverFlow = transition.presentation == BookReaderPresentation.CoverFlow
        val progress = if (!active) 0f else if (isCoverFlow)
            transition.coverFlowProgressSnapshot.value.coerceIn(0f, 1f)
        else transition.libraryProgressSnapshot.value.coerceIn(0f, 1f)
        alpha = BookReaderMotion.lerp(1f,
            if (isCoverFlow) 0.65f else BookReaderMotion.LIBRARY_DIM_ALPHA, progress)
        val scale = if (isCoverFlow) 1f else BookReaderMotion.lerp(1f, BookReaderMotion.LIBRARY_SCALE, progress)
        scaleX = scale
        scaleY = scale
        // Half-dp steps reuse effects in both directions instead of allocating one per frame.
        val step = (BookReaderMotion.LIBRARY_BLUR_DP * 2f * progress).roundToInt().coerceIn(0, 36)
        renderEffect = if (allowBlur && step > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            effects[step] ?: RenderEffect.createBlurEffect(
                step * 0.5f * density, step * 0.5f * density, Shader.TileMode.CLAMP
            ).asComposeRenderEffect().also { effects[step] = it }
        } else null
    }
}

@Composable
internal fun BookReaderLibraryLayer(
    transition: BookReaderTransitionState,
    blurEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val coordinateSpace = remember { LibraryCoordinateSpace() }
    CompositionLocalProvider(LocalLibraryCoordinateSpace provides coordinateSpace) {
        Box(Modifier.fillMaxSize().bookReaderLibraryLayer(transition, blurEnabled)
            .onPlaced { coordinateSpace.coordinates = it }) { content() }
    }
}

/** Clip the incoming full-size reader to the same moving window as its cover shell.
 * The tab bar uses the inverse clip because its host is above Navigation's destinations.
 */
@Composable
internal fun Modifier.bookReaderWindowClip(
    transition: BookReaderTransitionState,
    outside: Boolean = false
): Modifier {
    val windowShape = AppRoundedCornerShape(0.dp)
    return drawWithCache {
        val path = Path()
        onDrawWithContent {
            val source = transition.sourceBounds
            val phase = transition.phase
            // Navigation may retain AnimatedVisibility for a frame after Ready. Keep
            // its outside layer occluded when the window becomes the full reader.
            if (outside && phase == BookReaderTransitionPhase.Reader) return@onDrawWithContent
            val active = transition.presentation == BookReaderPresentation.Window && source != null &&
                phase != BookReaderTransitionPhase.Library && phase != BookReaderTransitionPhase.Reader &&
                (outside || phase == BookReaderTransitionPhase.Ready)
            if (active) {
                val bounds = BookReaderMotion.windowBounds(source, Rect(Offset.Zero, size),
                    transition.positionSnapshot.value, transition.sizeSnapshot.value,
                    BookReaderMotion.CONTROL_POINT_OFFSET_DP.dp.toPx())
                // The shell's corner radius is scaled with its original cover layer.
                val radius = transition.cornerRadiusSnapshot.value.dp.toPx()
                path.reset()
                val sourcePath = windowShape.copy(androidx.compose.foundation.shape.CornerSize(radius))
                    .createOutline(source.size, layoutDirection, this).toAppPath()
                sourcePath.transform(androidx.compose.ui.graphics.Matrix().apply {
                    scale(bounds.width / source.width, bounds.height / source.height)
                })
                path.addPath(sourcePath, bounds.topLeft)
                clipPath(path, if (outside) ClipOp.Difference else ClipOp.Intersect) { this@onDrawWithContent.drawContent() }
            } else {
                drawContent()
            }
        }
    }
}

@Composable
internal fun rememberBookLoadingIndicatorVisible(transition: BookReaderTransitionState): Boolean {
    val waiting = transition.phase == BookReaderTransitionPhase.ReaderLoading ||
        (transition.phase == BookReaderTransitionPhase.Ready &&
            transition.readerRevealSnapshot.value < 0.999f)
    var visible by remember(transition.sessionId) { mutableStateOf(false) }
    LaunchedEffect(transition.sessionId, waiting) {
        visible = false
        if (waiting) {
            delay(250L)
            visible = true
        }
    }
    return waiting && visible
}

@Composable
internal fun BookHeroWindowOverlay(
    transition: BookReaderTransitionState,
    modifier: Modifier = Modifier
) {
    if (transition.presentation == BookReaderPresentation.CoverFlow) {
        CoverFlowReaderOverlay(transition, modifier)
        return
    }
    val phase = transition.phase
    if (
        phase != BookReaderTransitionPhase.Opening &&
        phase != BookReaderTransitionPhase.ReaderLoading &&
        phase != BookReaderTransitionPhase.Ready &&
        phase != BookReaderTransitionPhase.Closing
    ) {
        return
    }
    val source = transition.sourceBounds ?: return
    if (source.width <= 0f || source.height <= 0f) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val pageColor = Color(readerTransitionPaperColor(transition.readerPaperColorArgb ?: 0xFFFBFBFC.toInt()))
    val heroShape = AppRoundedCornerShape(0.dp)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val target = Rect(
            left = 0f,
            top = 0f,
            right = constraints.maxWidth.toFloat(),
            bottom = constraints.maxHeight.toFloat()
        )
        val screenCenter = target.center
        val controlOffsetPx = with(density) {
            BookReaderMotion.CONTROL_POINT_OFFSET_DP.dp.toPx()
        }
        val control = BookReaderMotion.controlPoint(
            start = source.center,
            end = target.center,
            screenCenter = screenCenter,
            offsetPx = controlOffsetPx
        )
        val startWidth = with(density) { source.width.toDp() }
        val startHeight = with(density) { source.height.toDp() }
        val titleStyle = transition.coverTitleStyle ?: BookCoverTitleStyle(
            fontSizeSp = 14f,
            maxLines = 3,
            paddingDp = 8f,
            color = BookCoverTitleColor.Primary,
            useKaiTi = true,
            textAlignCenter = true
        )
        val titleColor = when (titleStyle.color) {
            BookCoverTitleColor.Primary -> AppColors.TextPrimary
            BookCoverTitleColor.Secondary -> AppColors.TextSecondary
        }
        val titleText = titleStyle.takeCharacters
            ?.let(transition.coverTitle::take)
            ?: transition.coverTitle
        val dominantColor = transition.dominantColorArgb
            ?.let(::Color)
            ?: pageColor

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = source.left.roundToInt(),
                        y = source.top.roundToInt()
                    )
                }
                .size(width = startWidth, height = startHeight)
                .graphicsLayer {
                    val positionProgress = transition.positionSnapshot.value.coerceIn(0f, 1f)
                    val sizeProgress = transition.sizeSnapshot.value.coerceIn(0f, 1f)
                    val center = BookReaderMotion.quadraticPoint(
                        start = source.center,
                        control = control,
                        end = target.center,
                        progress = positionProgress
                    )
                    val width = BookReaderMotion.lerp(source.width, target.width, sizeProgress)
                    val height = BookReaderMotion.lerp(source.height, target.height, sizeProgress)
                    transformOrigin = TransformOrigin.Center
                    scaleX = if (source.width > 0f) width / source.width else 1f
                    scaleY = if (source.height > 0f) height / source.height else 1f
                    translationX = center.x - source.center.x
                    translationY = center.y - source.center.y
                    shape = heroShape.copy(androidx.compose.foundation.shape.CornerSize(
                        transition.cornerRadiusSnapshot.value.coerceAtLeast(0f).dp))
                    clip = true
                    alpha = transition.overlayAlpha.coerceIn(0f, 1f)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (
                            1f - transition.coverFadeSnapshot.value
                            ).coerceIn(0f, 1f)
                    }
                    .background(dominantColor)
            )
        }

        if (transition.coverBook != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = source.left.roundToInt(),
                            y = source.top.roundToInt()
                        )
                    }
                    .size(width = startWidth, height = startHeight)
                    .graphicsLayer {
                        val positionProgress =
                            transition.positionSnapshot.value.coerceIn(0f, 1f)
                        val sizeProgress = transition.sizeSnapshot.value.coerceIn(0f, 1f)
                        val center = BookReaderMotion.quadraticPoint(
                            start = source.center,
                            control = control,
                            end = target.center,
                            progress = positionProgress
                        )
                        val windowWidth = BookReaderMotion.lerp(
                            source.width,
                            target.width,
                            sizeProgress
                        )
                        val windowHeight = BookReaderMotion.lerp(
                            source.height,
                            target.height,
                            sizeProgress
                        )
                        val fitScale = BookReaderMotion.coverFitScale(
                            source = source,
                            windowWidth = windowWidth,
                            windowHeight = windowHeight
                        )
                        val coverWidth = source.width * fitScale
                        val coverHeight = source.height * fitScale
                        val windowLeft = center.x - windowWidth / 2f
                        val windowTop = center.y - windowHeight / 2f
                        val coverLeft = windowLeft + (windowWidth - coverWidth) / 2f
                        val coverTop = windowTop + (windowHeight - coverHeight) / 2f
                        transformOrigin = TransformOrigin(
                            pivotFractionX = 0f,
                            pivotFractionY = 0f
                        )
                        scaleX = fitScale
                        scaleY = fitScale
                        translationX = coverLeft - source.left
                        translationY = coverTop - source.top
                        shape = heroShape.copy(androidx.compose.foundation.shape.CornerSize(
                            transition.cornerRadiusSnapshot.value.coerceAtLeast(0f).dp))
                        clip = true
                        alpha = (1f - transition.coverFadeSnapshot.value).coerceIn(0f, 1f) *
                            transition.overlayAlpha.coerceIn(0f, 1f)
                    }
            ) {
                transition.coverPath?.let { coverPath ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverPath)
                            .memoryCacheKey(
                                bookCoverMemoryCacheKey(
                                    bookId = transition.activeBookId.orEmpty(),
                                    coverPath = coverPath
                                )
                            )
                            .build(),
                        contentDescription = transition.coverTitle.ifBlank { null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.BgGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = titleText,
                        modifier = Modifier.padding(titleStyle.paddingDp.dp),
                        fontSize = titleStyle.fontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (titleStyle.useKaiTi) {
                            resolveAppFontFamily(KaiTi)
                        } else {
                            null
                        },
                        color = titleColor,
                        textAlign = if (titleStyle.textAlignCenter) {
                            TextAlign.Center
                        } else {
                            TextAlign.Start
                        },
                        maxLines = titleStyle.maxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (transition.coverShowsReadingProgress) {
                    transition.coverBook?.let { book ->
                        com.huangder.lumibooks.ui.components.BookCoverReadingProgress(book, compact = true)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.28f)
                        .graphicsLayer {
                            val sizeProgress = transition.sizeSnapshot.value.coerceIn(0f, 1f)
                            val windowWidth = BookReaderMotion.lerp(
                                source.width,
                                target.width,
                                sizeProgress
                            )
                            val windowHeight = BookReaderMotion.lerp(
                                source.height,
                                target.height,
                                sizeProgress
                            )
                            alpha = BookReaderMotion.edgeBlend(
                                source = source,
                                windowWidth = windowWidth,
                                windowHeight = windowHeight
                            )
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    dominantColor,
                                    dominantColor.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.28f)
                        .graphicsLayer {
                            val sizeProgress = transition.sizeSnapshot.value.coerceIn(0f, 1f)
                            val windowWidth = BookReaderMotion.lerp(
                                source.width,
                                target.width,
                                sizeProgress
                            )
                            val windowHeight = BookReaderMotion.lerp(
                                source.height,
                                target.height,
                                sizeProgress
                            )
                            alpha = BookReaderMotion.edgeBlend(
                                source = source,
                                windowWidth = windowWidth,
                                windowHeight = windowHeight
                            )
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    dominantColor.copy(alpha = 0f),
                                    dominantColor
                                )
                            )
                        )
                )
            }
        }

        if (rememberBookLoadingIndicatorVisible(transition)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .graphicsLayer {
                        alpha = 0.72f * (1f - transition.readerRevealSnapshot.value.coerceIn(0f, 1f))
                    },
                color = AppColors.Accent,
                strokeWidth = 2.dp
            )
        }
    }
}
