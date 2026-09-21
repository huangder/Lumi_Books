package com.huangder.lumibooks.ui.animation

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.huangder.lumibooks.ui.components.BookCoverProgressOverlay
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    const val POSITION_DURATION_MS = 600
    const val SIZE_DURATION_MS = 650
    const val SHELL_REVEAL_MS = 180
    const val CONTENT_REVEAL_MS = 160
    const val CONTROL_POINT_OFFSET_DP = 28f
    const val LIBRARY_BLUR_DP = 12f
    const val LIBRARY_DIM_ALPHA = 0.28f
    const val LIBRARY_SCALE = 0.985f
    const val DEFAULT_CORNER_RADIUS_DP = 14f

    val PositionEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val SizeEasing = CubicBezierEasing(0.25f, 0.90f, 0.35f, 1f)
    val HeroBoundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = WINDOW_DURATION_MS, easing = LinearEasing)
    }

    fun sharedKey(bookId: String): String = "book-window-$bookId"

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
    private val positionAnim = Animatable(0f)
    private val sizeAnim = Animatable(0f)
    private val radiusAnim = Animatable(BookReaderMotion.DEFAULT_CORNER_RADIUS_DP)
    private val coverFadeAnim = Animatable(0f)
    private val shellAlphaAnim = Animatable(1f)
    private val readerExitAlphaAnim = Animatable(1f)
    private val libraryProgressAnim = Animatable(0f)
    private val coverFlowProgressAnim = Animatable(0f)
    val coverFlowProgressSnapshot: State<Float> = coverFlowProgressAnim.asState()
    var presentation by mutableStateOf(BookReaderPresentation.Window)
        private set
    private val coverAnchors = mutableMapOf<Any, BookCoverAnchor>()
    private var motionJob: Job? = null
    private var revealJob: Job? = null
    private var colorJob: Job? = null

    val phaseSnapshot: State<BookReaderTransitionPhase> = phaseState
    val positionSnapshot: State<Float> = positionAnim.asState()
    val sizeSnapshot: State<Float> = sizeAnim.asState()
    val cornerRadiusSnapshot: State<Float> = radiusAnim.asState()
    val coverFadeSnapshot: State<Float> = coverFadeAnim.asState()
    val shellAlphaSnapshot: State<Float> = shellAlphaAnim.asState()
    val readerExitAlphaSnapshot: State<Float> = readerExitAlphaAnim.asState()
    val libraryProgressSnapshot: State<Float> = libraryProgressAnim.asState()

    val phase: BookReaderTransitionPhase
        get() = phaseState.value

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

    val usesHeroTransition: Boolean
        get() = activeBookId != null && phase != BookReaderTransitionPhase.Library

    private var coverFlowReturnReady = false

    fun updateCoverFlowReturnSource(bookId: String, bounds: Rect) {
        if (presentation != BookReaderPresentation.CoverFlow || activeBookId != bookId ||
            phase != BookReaderTransitionPhase.Closing || bounds.width <= 0f || bounds.height <= 0f) return
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
        presentation = sourcePresentation ?: anchor.presentation
        setPhase(BookReaderTransitionPhase.Opening)

        positionAnim.snapTo(0f)
        sizeAnim.snapTo(0f)
        radiusAnim.snapTo(startRadius)
        coverFadeAnim.snapTo(0f)
        shellAlphaAnim.snapTo(1f)
        readerExitAlphaAnim.snapTo(1f)
        libraryProgressAnim.snapTo(0f)
        coverFlowProgressAnim.snapTo(0f)

        if (presentation == BookReaderPresentation.CoverFlow) {
            // Load behind the untouched source, then run one uninterrupted choreography.
            // Splitting at 0.34 visibly stopped an already enlarged cover on slower books.
            setPhase(BookReaderTransitionPhase.ReaderLoading)
            return true
        }

        motionJob = scope.launch {
            coroutineScope {
                launch {
                    positionAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BookReaderMotion.POSITION_DURATION_MS,
                            easing = BookReaderMotion.PositionEasing
                        )
                    )
                }
                launch {
                    sizeAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BookReaderMotion.SIZE_DURATION_MS,
                            easing = BookReaderMotion.SizeEasing
                        )
                    )
                }
                launch {
                    radiusAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS
                            startRadius at 0
                            startRadius at 100
                            minOf(startRadius, 8f) at 340 using FastOutSlowInEasing
                            0f at BookReaderMotion.WINDOW_DURATION_MS using FastOutSlowInEasing
                        }
                    )
                }
                launch {
                    coverFadeAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS
                            0f at 0
                            0f at 160
                            1f at 460 using FastOutSlowInEasing
                            1f at BookReaderMotion.WINDOW_DURATION_MS
                        }
                    )
                }
                launch {
                    libraryProgressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            setPhase(BookReaderTransitionPhase.ReaderLoading)
            if (readerReady) revealReader()
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

    fun markReaderReady() {
        readerReady = true
        if (phaseState.value == BookReaderTransitionPhase.ReaderLoading) {
            revealReader()
        }
    }

    fun startClose(): Boolean {
        val bookId = activeBookId ?: return false
        val anchorKey = activeAnchorKey ?: return false
        if (phaseState.value == BookReaderTransitionPhase.Library) return false
        val anchor = coverAnchors[anchorKey]
        var targetBounds = sourceBounds ?: anchor?.bounds ?: return false
        var targetRadius = sourceCornerRadiusDp.takeIf { it > 0f }
            ?: anchor?.cornerRadiusDp
            ?: BookReaderMotion.DEFAULT_CORNER_RADIUS_DP
        coverTitleStyle = anchor?.titleStyle ?: coverTitleStyle
        coverShowsReadingProgress = anchor?.showReadingProgress ?: coverShowsReadingProgress

        motionJob?.cancel()
        revealJob?.cancel()
        sourceBounds = targetBounds
        sourceCornerRadiusDp = targetRadius
        closeAnchorKey = anchorKey
        coverFlowReturnReady = false
        setPhase(BookReaderTransitionPhase.Closing)
        val currentPosition = positionAnim.value.coerceIn(0f, 1f)
        val currentSize = sizeAnim.value.coerceIn(0f, 1f)

        motionJob = scope.launch {
            // Let the library destination scroll the target book into view and register
            // its current cover bounds before the reverse animation starts.
            for (frame in 0 until 18) {
                withFrameNanos { }
                if (phaseState.value != BookReaderTransitionPhase.Closing) return@launch
                if (presentation == BookReaderPresentation.CoverFlow) {
                    if (coverFlowReturnReady) break
                    continue
                }
                val candidate = coverAnchors.values
                    .filter { it.bookId == bookId }
                    .minWithOrNull(compareBy({ it.bounds.top }, { it.bounds.left }))
                if (candidate != null) {
                    targetBounds = candidate.bounds
                    targetRadius = candidate.cornerRadiusDp
                    coverTitleStyle = candidate.titleStyle
                    coverShowsReadingProgress = candidate.showReadingProgress
                    sourceBounds = candidate.bounds
                    sourceCornerRadiusDp = candidate.cornerRadiusDp
                    closeAnchorKey = candidate.key
                    break
                }
            }
            if (presentation == BookReaderPresentation.CoverFlow) {
                coverFlowProgressAnim.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                refreshCloseSource(bookId)
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
                return@launch
            }
            coroutineScope {
                launch {
                    readerExitAlphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    positionAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = scaledDuration(
                                baseMs = BookReaderMotion.POSITION_DURATION_MS,
                                progress = currentPosition
                            ),
                            easing = BookReaderMotion.PositionEasing
                        )
                    )
                }
                launch {
                    sizeAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = scaledDuration(
                                baseMs = BookReaderMotion.SIZE_DURATION_MS,
                                progress = currentSize
                            ),
                            easing = BookReaderMotion.SizeEasing
                        )
                    )
                }
                launch {
                    radiusAnim.animateTo(
                        targetValue = targetRadius,
                        animationSpec = tween(
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    coverFadeAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    libraryProgressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = BookReaderMotion.WINDOW_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            refreshCloseSource(bookId)
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
        }
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
    }

    private fun revealReader() {
        if (phaseState.value != BookReaderTransitionPhase.ReaderLoading) return
        revealJob?.cancel()
        setPhase(BookReaderTransitionPhase.Ready)
        revealJob = scope.launch {
            if (presentation == BookReaderPresentation.CoverFlow) {
                coverFlowProgressAnim.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                if (phaseState.value == BookReaderTransitionPhase.Ready) setPhase(BookReaderTransitionPhase.Reader)
                return@launch
            }
            shellAlphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = BookReaderMotion.SHELL_REVEAL_MS,
                    easing = FastOutSlowInEasing
                )
            )
            if (phaseState.value == BookReaderTransitionPhase.Ready) {
                setPhase(BookReaderTransitionPhase.Reader)
            }
        }
    }

    private fun scaledDuration(baseMs: Int, progress: Float): Int =
        (baseMs * progress.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(160, baseMs)

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
        if (presentation == BookReaderPresentation.CoverFlow) return
        if (
            phaseState.value != BookReaderTransitionPhase.Closing ||
            activeBookId != bookId
        ) {
            return
        }
        val refreshed = coverAnchors.values
            .filter { it.bookId == bookId }
            .minWithOrNull(compareBy({ it.bounds.top }, { it.bounds.left }))
            ?: return
        sourceBounds = refreshed.bounds
        sourceCornerRadiusDp = refreshed.cornerRadiusDp
        coverShowsReadingProgress = refreshed.showReadingProgress
        closeAnchorKey = refreshed.key
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
    val sharedTransitionScope: SharedTransitionScope,
    val animatedVisibilityScope: AnimatedVisibilityScope,
    val transitionState: BookReaderTransitionState
)

internal val LocalBookReaderAnchorScope =
    staticCompositionLocalOf<BookReaderAnchorScope?> { null }

@Composable
internal fun rememberBookReaderTransitionState(): BookReaderTransitionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BookReaderTransitionState(scope) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.bookCoverTransitionAnchor(
    bookId: String,
    cornerRadiusDp: Float,
    titleStyle: BookCoverTitleStyle? = null,
    showReadingProgress: Boolean = true,
    presentation: BookReaderPresentation = BookReaderPresentation.Window
): Modifier {
    val anchors = LocalBookReaderAnchorScope.current ?: return this
    val anchorKey = remember(bookId) {
        "${BookReaderMotion.sharedKey(bookId)}-${nextCoverAnchorId.getAndIncrement()}"
    }
    DisposableEffect(anchorKey) {
        onDispose { anchors.transitionState.unregisterCoverAnchor(anchorKey) }
    }
    val sharedContentState = anchors.sharedTransitionScope.rememberSharedContentState(
        anchorKey
    )
    val sharedModifier = if (presentation == BookReaderPresentation.Window && anchors.transitionState.activeAnchorKey == anchorKey) {
        with(anchors.sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = anchors.animatedVisibilityScope,
                boundsTransform = BookReaderMotion.HeroBoundsTransform,
                renderInOverlayDuringTransition = false,
                zIndexInOverlay = 1f
            )
        }
    } else {
        Modifier
    }
    return this
        .onGloballyPositioned { coordinates ->
            anchors.transitionState.registerCoverAnchor(
                anchorKey = anchorKey,
                bookId = bookId,
                bounds = coordinates.boundsInRoot(),
                cornerRadiusDp = cornerRadiusDp,
                titleStyle = titleStyle,
                showReadingProgress = showReadingProgress,
                presentation = presentation
            )
        }
        .then(sharedModifier)
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.bookReaderTargetAnchor(bookId: String): Modifier {
    val anchors = LocalBookReaderAnchorScope.current ?: return this
    if (anchors.transitionState.presentation == BookReaderPresentation.CoverFlow) return this
    val anchorKey = anchors.transitionState.activeAnchorKey ?: return this
    val sharedContentState = anchors.sharedTransitionScope.rememberSharedContentState(
        anchorKey
    )
    return with(anchors.sharedTransitionScope) {
        this@bookReaderTargetAnchor
            .sharedBounds(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = anchors.animatedVisibilityScope,
                boundsTransform = BookReaderMotion.HeroBoundsTransform,
                renderInOverlayDuringTransition = false,
                zIndexInOverlay = 2f
            )
            .graphicsLayer { alpha = 0f }
    }
}

@Composable
internal fun BookReaderLibraryLayer(
    transition: BookReaderTransitionState,
    blurEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val layerActive = transition.phase == BookReaderTransitionPhase.Opening ||
        transition.phase == BookReaderTransitionPhase.ReaderLoading ||
        (transition.presentation == BookReaderPresentation.CoverFlow && transition.phase == BookReaderTransitionPhase.Ready) ||
        transition.phase == BookReaderTransitionPhase.Closing
    Box(
        modifier = if (layerActive) {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val isCoverFlow = transition.presentation == BookReaderPresentation.CoverFlow
                    val progress = if (isCoverFlow) transition.coverFlowProgressSnapshot.value.coerceIn(0f, 1f)
                        else transition.libraryProgressSnapshot.value.coerceIn(0f, 1f)
                    alpha = BookReaderMotion.lerp(
                        1f,
                        if (isCoverFlow) 0.65f else BookReaderMotion.LIBRARY_DIM_ALPHA,
                        progress
                    )
                    val scale = BookReaderMotion.lerp(
                        1f,
                        if (isCoverFlow) 1f else BookReaderMotion.LIBRARY_SCALE,
                        progress
                    )
                    scaleX = scale
                    scaleY = scale
                    renderEffect = if (
                        blurEnabled &&
                        progress > 0.01f &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        val radius = BookReaderMotion.LIBRARY_BLUR_DP * density * progress
                        RenderEffect.createBlurEffect(
                            radius,
                            radius,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
        } else {
            Modifier.fillMaxSize()
        }
    ) {
        content()
    }
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
    val pageColor = MaterialTheme.colorScheme.background
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
        val compactProgress = source.width < with(density) { 140.dp.toPx() }
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
                    shape = RoundedCornerShape(
                        transition.cornerRadiusSnapshot.value.coerceAtLeast(0f).dp
                    )
                    clip = true
                    alpha = if (phase == BookReaderTransitionPhase.Closing) {
                        1f - transition.readerExitAlphaSnapshot.value
                    } else {
                        transition.shellAlphaSnapshot.value.coerceIn(0f, 1f)
                    }
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
                        shape = RoundedCornerShape(
                            transition.cornerRadiusSnapshot.value.coerceAtLeast(0f).dp
                        )
                        clip = true
                        alpha = if (phase == BookReaderTransitionPhase.Closing) {
                            (
                                1f - transition.coverFadeSnapshot.value
                                ).coerceIn(0f, 1f) *
                                (
                                    1f - transition.readerExitAlphaSnapshot.value
                                    ).coerceIn(0f, 1f)
                        } else {
                            (
                                1f - transition.coverFadeSnapshot.value
                                ).coerceIn(0f, 1f) *
                                transition.shellAlphaSnapshot.value.coerceIn(0f, 1f)
                        }
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
                transition.coverBook?.let { coverBook ->
                    BookCoverProgressOverlay(
                        book = coverBook,
                        downloadState = transition.coverDownloadState,
                        compact = compactProgress,
                        showReadingProgress = transition.coverShowsReadingProgress
                    )
                }
            }
        }

        if (
            phase == BookReaderTransitionPhase.ReaderLoading &&
            !transition.readerReady
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .graphicsLayer { alpha = 0.58f },
                color = AppColors.Accent,
                strokeWidth = 2.dp
            )
        }
    }
}
