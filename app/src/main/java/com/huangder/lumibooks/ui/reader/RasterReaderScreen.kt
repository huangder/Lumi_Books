package com.huangder.lumibooks.ui.reader
import com.huangder.lumibooks.ui.icons.AppIcons

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.ReaderSystemBarStyle
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.BookFileAccess
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.CbzReadingDirection
import com.huangder.lumibooks.ui.layout.currentAdaptiveWindowInfo
import com.huangder.lumibooks.domain.model.PdfPageMode
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.pdfconversion.PdfConversionEngine
import com.huangder.lumibooks.pdfconversion.PdfConversionState
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.ui.settings.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.Closeable
import java.util.LinkedHashMap
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal enum class PdfMultiTouchMode {
    UNDECIDED,
    PAN,
    ZOOM
}

private const val PDF_ZOOM_EPSILON = 1.01f
private const val PDF_PAGE_TURN_THRESHOLD_PX = 48f
private const val PDF_ANNOTATION_ZOOM_SLOP_MULTIPLIER = 1.5f
private const val PDF_ANNOTATION_ZOOM_DOMINANCE = 1.35f
private const val PDF_VELOCITY_IDLE_TIMEOUT_MS = 120L
private const val PDF_MAX_FLING_VELOCITY = 12_000f

private const val PAGE_RENDER_MIN_WIDTH_PX = 720
private const val THUMBNAIL_RENDER_WIDTH_PX = 240
private const val CATALOG_PREVIEW_WIDTH_PX = 640
/** 快速翻页时先显示的阅读页预览；清晰图到达后立即覆盖且不会反向降级。 */
private const val PREVIEW_RENDER_WIDTH_PX = 240

/** 漫画双页对开时封面单独成屏，与实体单行本的排布一致。 */
private const val COVER_ALONE_FIRST_PAGE = true

/** 单页渲染失败（被取消 / 内存不足）后的重试次数与退避间隔。 */
private const val PAGE_RENDER_MAX_ATTEMPTS = 3
/** 高分辨率档的单页位图很大，失败重试只会反复分配，这里只给一次重试。 */
private const val PAGE_RENDER_HIGH_RES_MAX_ATTEMPTS = 2
private const val PAGE_RENDER_RETRY_DELAY_MS = 300L
private const val CBZ_BITMAP_FALLBACK_DELAY_MS = 260L
private const val PDF_ZOOM_RENDER_DEBOUNCE_MS = 140L

/** 低清预览宽度：足够铺满页面区域，解码成本极低。 */

internal data class PdfPanResult(
    val offset: Float,
    val edgeDrag: Float
)

/** Applies a finger delta to a bounded zoom offset and returns any outward drag. */
internal fun consumePdfPanDelta(
    offset: Float,
    maxOffset: Float,
    delta: Float,
    edgeDrag: Float = 0f
): PdfPanResult {
    val boundedMax = maxOffset.coerceAtLeast(0f)
    val nextOffset = (offset + delta).coerceIn(-boundedMax, boundedMax)
    val consumed = nextOffset - offset
    val residual = delta - consumed
    val nextEdgeDrag = when {
        residual != 0f -> {
            if (edgeDrag == 0f || kotlin.math.sign(edgeDrag) == kotlin.math.sign(residual)) {
                edgeDrag + residual
            } else {
                residual
            }
        }
        edgeDrag != 0f && delta != 0f && kotlin.math.sign(edgeDrag) != kotlin.math.sign(delta) -> {
            val reduced = edgeDrag + delta
            if (kotlin.math.sign(reduced) == kotlin.math.sign(edgeDrag)) reduced else 0f
        }
        else -> edgeDrag
    }
    return PdfPanResult(nextOffset, nextEdgeDrag)
}

/** Resolves a two-finger gesture without treating small finger-spacing jitter as a pinch. */
internal fun resolvePdfTransformMode(
    panMotion: Float,
    zoomMotion: Float,
    touchSlop: Float,
    preferPan: Boolean
): PdfMultiTouchMode {
    val boundedSlop = touchSlop.coerceAtLeast(0f)
    if (preferPan) {
        return when {
            panMotion >= boundedSlop &&
                panMotion >= zoomMotion / PDF_ANNOTATION_ZOOM_DOMINANCE -> PdfMultiTouchMode.PAN
            zoomMotion >= boundedSlop * PDF_ANNOTATION_ZOOM_SLOP_MULTIPLIER &&
                zoomMotion > panMotion * PDF_ANNOTATION_ZOOM_DOMINANCE -> PdfMultiTouchMode.ZOOM
            else -> PdfMultiTouchMode.UNDECIDED
        }
    }
    return when {
        maxOf(panMotion, zoomMotion) < boundedSlop -> PdfMultiTouchMode.UNDECIDED
        zoomMotion > panMotion -> PdfMultiTouchMode.ZOOM
        else -> PdfMultiTouchMode.PAN
    }
}

/** A pinch may end with a pan while both fingers remain down, so release cannot rely on PAN alone. */
internal fun shouldStartPdfPanDecay(
    scale: Float,
    mode: PdfMultiTouchMode,
    navigationGesture: Boolean
): Boolean = navigationGesture && (
    mode == PdfMultiTouchMode.PAN ||
        (scale > PDF_ZOOM_EPSILON && mode == PdfMultiTouchMode.ZOOM)
    )

/** Tracks centroid deltas directly, surviving pointer-count changes that can reset platform velocity. */
internal class PdfPanVelocityEstimator(
    private val idleTimeoutMillis: Long = PDF_VELOCITY_IDLE_TIMEOUT_MS
) {
    private var lastEventTimeMillis = 0L
    private var lastMotionTimeMillis = Long.MIN_VALUE
    private var velocity = Offset.Zero

    fun reset(uptimeMillis: Long) {
        lastEventTimeMillis = uptimeMillis
        lastMotionTimeMillis = Long.MIN_VALUE
        velocity = Offset.Zero
    }

    fun addPan(uptimeMillis: Long, pan: Offset) {
        val elapsedMillis = (uptimeMillis - lastEventTimeMillis).coerceAtLeast(1L)
        lastEventTimeMillis = uptimeMillis
        if (pan.getDistance() < 0.01f) return

        val instantaneous = pan * (1_000f / elapsedMillis)
        velocity = if (lastMotionTimeMillis == Long.MIN_VALUE) {
            instantaneous
        } else {
            velocity * 0.35f + instantaneous * 0.65f
        }
        lastMotionTimeMillis = uptimeMillis
    }

    fun velocityAt(uptimeMillis: Long): Offset {
        if (lastMotionTimeMillis == Long.MIN_VALUE) return Offset.Zero
        val idleMillis = (uptimeMillis - lastMotionTimeMillis).coerceAtLeast(0L)
        if (idleMillis >= idleTimeoutMillis) return Offset.Zero
        val idleFactor = 1f - idleMillis.toFloat() / idleTimeoutMillis
        return velocity * idleFactor
    }
}

internal fun resolvePdfReleaseVelocity(tracked: Offset, estimated: Offset): Offset {
    fun axisVelocity(trackedAxis: Float, estimatedAxis: Float): Float {
        val selected = if (kotlin.math.abs(trackedAxis) >= PDF_MIN_FLING_VELOCITY) {
            trackedAxis
        } else {
            estimatedAxis
        }
        return selected.coerceIn(-PDF_MAX_FLING_VELOCITY, PDF_MAX_FLING_VELOCITY)
    }
    return Offset(
        axisVelocity(tracked.x, estimated.x),
        axisVelocity(tracked.y, estimated.y)
    )
}

internal fun pdfPageForEdgeDrag(
    currentPage: Int,
    pageCount: Int,
    edgeDrag: Float,
    threshold: Float = PDF_PAGE_TURN_THRESHOLD_PX
): Int {
    val target = when {
        edgeDrag <= -threshold -> currentPage + 1
        edgeDrag >= threshold -> currentPage - 1
        else -> currentPage
    }
    return target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

private const val PDF_MIN_FLING_VELOCITY = 80f

/** Continues a zoomed page pan after release until velocity decays or an edge is reached. */
private suspend fun animatePdfPanDecay(
    initialOffset: Float,
    initialVelocity: Float,
    maxOffset: Float,
    initialEdgeDrag: Float = 0f,
    onOffsetChange: (Float) -> Unit,
    onUnconsumedDelta: (Float) -> Unit = {}
): Float {
    if (kotlin.math.abs(initialVelocity) < PDF_MIN_FLING_VELOCITY) return initialEdgeDrag

    val animation = Animatable(initialOffset)
    var currentOffset = initialOffset
    var previousAnimationValue = initialOffset
    var edgeDrag = initialEdgeDrag
    animation.animateDecay(
        initialVelocity = initialVelocity,
        animationSpec = exponentialDecay()
    ) {
        // Animatable.value is the absolute decay position. Compare consecutive animation
        // frames rather than the clamped content offset, otherwise edge residuals compound.
        val delta = value - previousAnimationValue
        previousAnimationValue = value
        val result = consumePdfPanDelta(
            offset = currentOffset,
            maxOffset = maxOffset,
            delta = delta,
            edgeDrag = edgeDrag
        )
        val consumed = result.offset - currentOffset
        currentOffset = result.offset
        edgeDrag = result.edgeDrag
        onOffsetChange(currentOffset)
        onUnconsumedDelta(delta - consumed)
    }
    return edgeDrag
}

private enum class PdfPagerAxis {
    HORIZONTAL,
    VERTICAL
}

private sealed interface PdfConversionSheet {
    data class Confirm(val replaceExisting: Boolean = false) : PdfConversionSheet
    data class Existing(val convertedBookId: String) : PdfConversionSheet
    data class MineruNotConfigured(val replaceExisting: Boolean) : PdfConversionSheet
    data class MineruManual(val replaceExisting: Boolean) : PdfConversionSheet
    data object Progress : PdfConversionSheet
    data class Completed(
        val convertedBookId: String,
        val textPages: Int,
        val totalPages: Int,
        val manualImport: Boolean = false
    ) : PdfConversionSheet
    data object Cancel : PdfConversionSheet
    data class Failure(val errorCode: String) : PdfConversionSheet
}

@Composable
fun RasterReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val conversionState by viewModel.pdfConversionState.collectAsState()
    val mineruMode by viewModel.mineruMode.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? MainActivity

    val book = uiState.book
    val filePath = book?.filePath
    val pageCount = uiState.chapterCount
    val bookmarkedPages = remember(bookmarks) {
        bookmarks.mapTo(mutableSetOf()) { it.chapterIndex }
    }
    var showMenu by remember { mutableStateOf(false) }
    var showPdfToc by remember { mutableStateOf(false) }
    var showPdfBookmarks by remember { mutableStateOf(false) }
    var annotationMode by remember(bookId) { mutableStateOf(false) }
    var selectedInkTool by remember(bookId) { mutableStateOf(PdfInkTool.PEN) }
    var selectedInkColorSlot by remember(bookId) { mutableStateOf(0) }
    var inkColorExpanded by remember(bookId) { mutableStateOf(false) }
    var conversionSheet by remember { mutableStateOf<PdfConversionSheet?>(null) }
    val exitReader: () -> Unit = {
        viewModel.stopTts()
        onNavigateBack()
    }
    val openBookFromReader: (String) -> Unit = { targetBookId ->
        viewModel.stopTts()
        onOpenBook(targetBookId)
    }
    val isAnySheetOpen = showPdfToc || showPdfBookmarks || conversionSheet != null
    // 返回手势只负责触发退出：不走"内容跟随手指"的预见式动画，避免与阅读页既有的退出动画叠加。
    ConfigurableBackHandler(
        enabled = !isAnySheetOpen,
        onBack = exitReader
    )
    var pendingReplaceAfterMineruSettings by remember { mutableStateOf(false) }
    var pendingManualReplace by remember { mutableStateOf(false) }
    var observedActiveConversion by remember { mutableStateOf(false) }
    var pendingModePage by remember { mutableStateOf<Int?>(null) }
    val eInkMode = LocalEInkMode.current || uiState.eInkModeEnabled
    val rasterBackgroundColor = if (eInkMode) Color.White else AppColors.WindowBg
    ReaderSystemBarStyle(
        backgroundColor = rasterBackgroundColor,
        useDarkIcons = eInkMode || !LocalIsDarkTheme.current
    )
    val motionEnabled = LocalMotionEnabled.current
    val effectivePdfPageMode = if (eInkMode) "horizontal" else uiState.pdfPageMode
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    LaunchedEffect(ttsState.errorMessage) {
        val message = ttsState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearTtsError()
    }
    val pdfGlassContentScrim = AppColors.WindowBg.copy(alpha = 0.18f)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val mineruSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        conversionSheet = PdfConversionSheet.Confirm(pendingReplaceAfterMineruSettings)
    }
    val manualResultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            observedActiveConversion = true
            conversionSheet = PdfConversionSheet.Progress
            viewModel.importManualMineruResult(uri, pendingManualReplace)
        }
    }
    val ttsNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startConversion(
        replaceExisting: Boolean,
        engine: PdfConversionEngine,
        selectedMineruMode: MineruMode = MineruMode.DISABLED
    ) {
        observedActiveConversion = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.startPdfConversion(replaceExisting, engine, selectedMineruMode)
    }

    LaunchedEffect(conversionState) {
        when (val state = conversionState) {
            is PdfConversionState.Running -> observedActiveConversion = true
            is PdfConversionState.Succeeded -> {
                if (observedActiveConversion && state.bookId.isNotEmpty()) {
                    conversionSheet = PdfConversionSheet.Completed(
                        convertedBookId = state.bookId,
                        textPages = state.textPages,
                        totalPages = state.totalPages,
                        manualImport = state.manualImport
                    )
                    observedActiveConversion = false
                }
            }
            is PdfConversionState.Failed -> {
                if (observedActiveConversion) {
                    conversionSheet = PdfConversionSheet.Failure(state.errorCode)
                    observedActiveConversion = false
                }
            }
            PdfConversionState.Cancelled -> observedActiveConversion = false
            PdfConversionState.Idle -> Unit
        }
    }

    if (filePath == null || pageCount <= 0) {
        Box(
            Modifier.fillMaxSize().background(rasterBackgroundColor),
            Alignment.Center
        ) {
            CircularProgressIndicator(color = AppColors.TextSecondary)
        }
        return
    }

    // One page source stays alive for the whole reader session: PDF keeps a PdfRenderer open and
    // CBZ keeps the ZIP index open, so rapid scrolling never pays for per-page setup.
    val bookFormat = book?.format
    val contentRevision = uiState.contentRevision
    var pageSource by remember(filePath, bookFormat, contentRevision) {
        mutableStateOf<BitmapPageSource?>(null)
    }
    var pageSourceFailed by remember(filePath, bookFormat, contentRevision) {
        mutableStateOf(false)
    }
    LaunchedEffect(filePath, bookFormat, contentRevision) {
        pageSourceFailed = false
        pageSource = null
        val source = bookFormat?.let { format ->
            BitmapPageSourceFactory.create(context, filePath, format)
        }
        if (source == null) pageSourceFailed = true else pageSource = source
    }
    DisposableEffect(filePath, bookFormat, contentRevision) {
        onDispose {
            pageSource?.close()
            pageSource = null
        }
    }

    // 页面宽高比（按页缓存）：列表项在首次组合时就能拿到正确高度。
    // 若等到可见后再异步测量，项目高度会从兜底值跳到真实值，
    // 快速滑动（尤其是往回滑）时列表会被这种高度变化打断，表现为"卡在几页之间"。
    val pageAspectRatios = remember(pageSource) { mutableStateMapOf<Int, Float>() }

    // The raster experiment intentionally owns PDF annotations. Remove legacy text
    // annotations once while preserving any strokes created by this branch.
    LaunchedEffect(bookId) {
        viewModel.clearLegacyPdfAnnotations(bookId)
    }
    val inkStrokes = remember(notes) {
        val decoded = notes.asSequence()
            .filter { it.type == PdfInkPenType || it.type == PdfInkHighlighterType }
            .mapNotNull { note ->
                PdfInkStrokeLocatorV1.decode(
                    encoded = note.startLocatorJson,
                    id = note.id,
                    fallbackPage = note.chapterIndex,
                    fallbackColor = note.color
                )?.copy(
                    createdAt = note.createdAt,
                    tool = if (note.type == PdfInkHighlighterType) PdfInkTool.HIGHLIGHTER else PdfInkTool.PEN,
                    color = note.color
                )
            }
            .toList()
        bridgeLegacyCrossPageStrokes(decoded)
    }

    val startPage = remember(bookId, pageCount) {
        restoredRasterPageIndex(book.readingProgress, pageCount)
    }
    val pageMode = PdfPageMode.fromKey(effectivePdfPageMode)
    // 解码清晰度档位（正常 / 高清 / 原图）：跟随全局设置，在顶部栏直接切换。
    val renderMode = PageRenderMode.fromKey(uiState.pageRenderMode)
    val isHorizontal = pageMode == PdfPageMode.HORIZONTAL_PAGING
    val isVerticalPaging = pageMode == PdfPageMode.VERTICAL_PAGING

    // 漫画：翻页方向只影响横向翻页；双页对开仅在大屏/横屏的横向翻页下生效。
    val isComic = book?.format == BookFormat.CBZ
    val readingDirection = CbzReadingDirection.fromKey(uiState.cbzReadingDirection)
        ?: CbzReadingDirection.LEFT_TO_RIGHT
    val isRightToLeft = isComic && readingDirection.isRightToLeft
    val spreadEnabled = isComic && isHorizontal && uiState.twoPageSpreadEnabled &&
        currentAdaptiveWindowInfo().isWideLandscape
    // 分屏（display）序号即阅读顺序序号；从右往左只翻转 HorizontalPager 的排布方向，
    // 页序本身不镜像，这样手绘批注坐标、页码与书签始终是同一套逻辑页号。
    val displayCount = if (spreadEnabled) {
        CbzSpreadPlanner.spreadCount(pageCount, COVER_ALONE_FIRST_PAGE)
    } else {
        pageCount
    }

    fun readingIndexForPage(page: Int): Int =
        if (spreadEnabled) {
            CbzSpreadPlanner.spreadIndexOfPage(page, pageCount, COVER_ALONE_FIRST_PAGE)
        } else {
            page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }

    fun pageForReadingIndex(readingIndex: Int): Int = if (spreadEnabled) {
        CbzSpreadPlanner
            .spreadFor(readingIndex, pageCount, COVER_ALONE_FIRST_PAGE)
            ?.firstPage
            ?: 0
    } else {
        readingIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val pagerState = rememberPagerState(initialPage = readingIndexForPage(startPage)) { displayCount }
    val verticalPagerState = rememberPagerState(initialPage = startPage) { pageCount }
    val verticalPage by remember {
        derivedStateOf {
            val lastPage = (pageCount - 1).coerceAtLeast(0)
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            when {
                visible.isEmpty() -> startPage.coerceIn(0, lastPage)
                // 已经滚到底：最后一项完整落在视口内，直接算作最后一页，
                // 否则"页码/进度"永远到不了 100%。
                visible.last().index >= lastPage &&
                    visible.last().offset + visible.last().size <= info.viewportEndOffset ->
                    lastPage
                else -> {
                    // 其余情况以视口中线所在的页为准：漫画页很高，
                    // 用"首屏页 + 200px"这类固定阈值会长期停留在一页上。
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    (visible.firstOrNull { it.offset <= center && it.offset + it.size > center }
                        ?: visible.last()).index.coerceIn(0, lastPage)
                }
            }
        }
    }
    val currentPage = when (pageMode) {
        PdfPageMode.HORIZONTAL_PAGING -> pageForReadingIndex(pagerState.currentPage)
        PdfPageMode.VERTICAL_PAGING -> verticalPagerState.currentPage
        PdfPageMode.VERTICAL_SCROLL -> verticalPage
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCurrentPage by rememberUpdatedState(currentPage)
    val latestPageSource by rememberUpdatedState(pageSource)
    DisposableEffect(lifecycleOwner, bookId, pageCount) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    latestPageSource?.saveResumeSnapshot()
                    if (pageCount > 0) {
                        viewModel.saveProgressDirect(
                            bookId,
                            rasterReadingProgress(latestCurrentPage, pageCount)
                        )
                    }
                    viewModel.onAppBackgrounded()
                }
                Lifecycle.Event.ON_RESUME -> {
                    latestPageSource?.resumeLoading()
                    viewModel.onAppForegrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onAppForegrounded()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 先把整本的页面宽高比量好（从当前页向两端扩散），列表项首次组合就能拿到正确高度。
    LaunchedEffect(pageSource, pageCount, startPage) {
        val source = pageSource ?: return@LaunchedEffect
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        val anchor = startPage.coerceIn(0, lastPage)
        (0..lastPage)
            .sortedBy { page -> kotlin.math.abs(page - anchor) }
            .forEach { page ->
                if (pageAspectRatios[page] == null) {
                    source.pageAspectRatio(page, background = true)?.let { ratio -> pageAspectRatios[page] = ratio }
                }
                // 让出主线程，避免整本测量挡住可见页的解码。
                if (page % 4 == 0) yield()
            }
    }

    suspend fun scrollToPdfPage(targetPage: Int, animate: Boolean = !eInkMode) {
        // 页面还没准备好时（pageCount 为 0）不能做 coerceIn，否则会抛出空区间异常。
        if (pageCount <= 0) return
        val page = targetPage.coerceIn(0, pageCount - 1)
        // Announce a distant target before the pager/list has measured it.
        pageSource?.updateViewport(RasterViewport(visiblePages = setOf(page), anchor = page, scrolling = true))
        when (pageMode) {
            PdfPageMode.HORIZONTAL_PAGING -> {
                val displayIndex = readingIndexForPage(page)
                if (animate) pagerState.animateScrollToPage(displayIndex)
                else pagerState.scrollToPage(displayIndex)
            }
            PdfPageMode.VERTICAL_PAGING -> {
                if (animate) verticalPagerState.animateScrollToPage(page)
                else verticalPagerState.scrollToPage(page)
            }
            PdfPageMode.VERTICAL_SCROLL -> {
                if (animate) listState.animateScrollToItem(page) else listState.scrollToItem(page)
            }
        }
    }

    LaunchedEffect(pageMode, pendingModePage) {
        val targetPage = pendingModePage ?: return@LaunchedEffect
        scrollToPdfPage(targetPage, animate = false)
        pendingModePage = null
    }

    // 双页对开开关或窗口尺寸变化后，停在原来那一页所在的分屏。
    var settledDisplayPage by remember(bookId) { mutableStateOf(startPage) }
    LaunchedEffect(spreadEnabled, pageMode, pagerState) {
        if (pageMode != PdfPageMode.HORIZONTAL_PAGING) return@LaunchedEffect
        pagerState.scrollToPage(readingIndexForPage(settledDisplayPage))
    }
    LaunchedEffect(spreadEnabled, pageMode, pagerState) {
        if (pageMode != PdfPageMode.HORIZONTAL_PAGING) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { displayIndex -> settledDisplayPage = pageForReadingIndex(displayIndex) }
    }
    // 竖向两种模式直接以页号为准，切换回横向时用它恢复位置。
    LaunchedEffect(pageMode, currentPage) {
        if (pageMode != PdfPageMode.HORIZONTAL_PAGING) settledDisplayPage = currentPage
    }
    LaunchedEffect(
        bookId,
        pageCount,
        pageMode,
        ttsState.activeBookId,
        ttsState.playbackState
    ) {
        viewModel.ttsPageTurnRequests.collect { request ->
            if (request.bookId != bookId ||
                ttsState.activeBookId != bookId ||
                ttsState.playbackState == TtsPlaybackState.IDLE ||
                request.location.pageIndex != 0
            ) return@collect
            val targetPage = request.location.chapterIndex
            if (targetPage !in 0 until pageCount) return@collect
            scrollToPdfPage(targetPage)
        }
    }

    // 当前页是否已收藏（PDF 每页 = 一个 chapterIndex）
    val isCurrentPageBookmarked = bookmarks.any { it.chapterIndex == currentPage }

    LaunchedEffect(currentPage, ttsState.activeBookId, ttsState.playbackState) {
        if (ttsState.activeBookId == bookId &&
            ttsState.playbackState != TtsPlaybackState.IDLE
        ) {
            viewModel.onPdfTtsPageVisible(bookId, currentPage)
        }
    }

    // 目录胶囊拖动定位：拖动中实时跳页，松手时按落点收尾。
    var isCatalogScrubbing by remember(bookId) { mutableStateOf(false) }
    val catalogSeekJob = remember(bookId) { mutableStateOf<Job?>(null) }

    // 进度保存（节流：每翻 3 页才保存一次）
    var lastSavedPage by remember { mutableStateOf(-1) }
    LaunchedEffect(currentPage) {
        if (pageCount <= 0) return@LaunchedEffect
        if (kotlin.math.abs(currentPage - lastSavedPage) < 3) return@LaunchedEffect
        lastSavedPage = currentPage
        // 拖动定位时一帧能跨过几十页，这里只记账不写库，松手后统一保存落点。
        if (isCatalogScrubbing) return@LaunchedEffect
        viewModel.saveProgressDirect(bookId, rasterReadingProgress(currentPage, pageCount))
    }

    // 缩放状态
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 惯性平移的收尾动画：新手势开始时取消，避免它继续用旧缩放级别写回位移。
    val zoomPanDecayJob = remember { mutableStateOf<Job?>(null) }
    val rasterDisplayWidth = (LocalConfiguration.current.screenWidthDp * LocalDensity.current.density *
        if (spreadEnabled) 0.5f else 1f).toInt().coerceAtLeast(PAGE_RENDER_MIN_WIDTH_PX)
    LaunchedEffect(pageSource, pageMode, spreadEnabled, rasterDisplayWidth) {
        val source = pageSource ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            val visible = when (pageMode) {
                PdfPageMode.VERTICAL_SCROLL -> listState.layoutInfo.visibleItemsInfo.map { it.index }
                PdfPageMode.HORIZONTAL_PAGING -> pagerState.layoutInfo.visiblePagesInfo.flatMap {
                    if (spreadEnabled) {
                        val pair = CbzSpreadPlanner.spreadFor(it.index, pageCount, COVER_ALONE_FIRST_PAGE)
                        listOfNotNull(pair?.firstPage, pair?.secondPage)
                    } else listOf(it.index)
                }
                PdfPageMode.VERTICAL_PAGING -> verticalPagerState.layoutInfo.visiblePagesInfo.map { it.index }
            }
            val scrolling = isCatalogScrubbing || when (pageMode) {
                PdfPageMode.VERTICAL_SCROLL -> listState.isScrollInProgress
                PdfPageMode.HORIZONTAL_PAGING -> pagerState.isScrollInProgress
                PdfPageMode.VERTICAL_PAGING -> verticalPagerState.isScrollInProgress
            }
            val positions = when (pageMode) {
                PdfPageMode.VERTICAL_SCROLL -> listOf(listState.firstVisibleItemIndex.toFloat(), listState.firstVisibleItemScrollOffset.toFloat())
                PdfPageMode.HORIZONTAL_PAGING -> listOf(pagerState.currentPage.toFloat(), pagerState.currentPageOffsetFraction)
                PdfPageMode.VERTICAL_PAGING -> listOf(verticalPagerState.currentPage.toFloat(), verticalPagerState.currentPageOffsetFraction)
            }
            val anchor = when (pageMode) {
                PdfPageMode.VERTICAL_SCROLL -> verticalPage
                PdfPageMode.HORIZONTAL_PAGING -> pageForReadingIndex(pagerState.currentPage)
                PdfPageMode.VERTICAL_PAGING -> verticalPagerState.currentPage
            }
            RasterViewport(visible.toSet().ifEmpty { setOf(anchor) }, anchor, rasterDisplayWidth,
                spreadEnabled, scrolling, positions + listOf(scale, offsetX, offsetY))
        }.collect(source::updateViewport)
    }
    // 缩放回到 1 时位移必须归零。否则残留的平移会把内容推离屏幕边缘，
    // 在顶部/底部露出一条背景色"白块"，而且只能靠再次缩放才被重新夹取。
    LaunchedEffect(scale) {
        if (scale <= PDF_ZOOM_EPSILON) {
            offsetX = 0f
            offsetY = 0f
            zoomPanDecayJob.value?.cancel()
            zoomPanDecayJob.value = null
        }
    }
    val shouldHandleVolumePageTurn = uiState.volumeKeyPageTurnEnabled &&
        !showMenu &&
        !showPdfToc &&
        !annotationMode &&
        conversionSheet == null &&
        scale <= PDF_ZOOM_EPSILON

    DisposableEffect(
        activity,
        shouldHandleVolumePageTurn,
        pageMode,
        currentPage,
        pageCount
    ) {
        if (!shouldHandleVolumePageTurn || activity == null) {
            return@DisposableEffect onDispose { }
        }

        val handler: (ReaderPageDirection) -> Unit = handler@{ direction ->
            val pageDelta = if (direction == ReaderPageDirection.PREVIOUS) -1 else 1
            val targetPage = (currentPage + pageDelta).coerceIn(0, pageCount - 1)
            if (targetPage == currentPage) return@handler

            scope.launch {
                scrollToPdfPage(targetPage)
            }
        }
        activity.readerVolumeKeyHandler = handler
        onDispose {
            if (activity.readerVolumeKeyHandler === handler) {
                activity.readerVolumeKeyHandler = null
            }
        }
    }

    LaunchedEffect(currentPage, pageMode) {
        // Continuous scrolling uses one transformed document surface, so changing the
        // visible page must not reset the user's zoom or viewport. Pager modes still
        // reset per-page transforms when the page changes.
        if (pageMode != PdfPageMode.VERTICAL_SCROLL) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    // 菜单动画（同时淡入+移动，不是先后）
    val menuAlpha = remember { Animatable(0f) }
    LaunchedEffect(showMenu, eInkMode, motionEnabled) {
        if (eInkMode) {
            menuAlpha.snapTo(if (showMenu) 1f else 0f)
        } else if (!motionEnabled) {
            menuAlpha.animateTo(if (showMenu) 1f else 0f, tween(if (showMenu) 120 else 100))
        } else if (showMenu) {
            menuAlpha.animateTo(1f, tween(260, easing = AppEasing.Smooth))
        } else {
            menuAlpha.animateTo(0f, tween(180, easing = AppEasing.Accelerate))
        }
    }

    val pdfGlassBackdrop = rememberLayerBackdrop()
    ProvideLiquidGlassBackdrop(pdfGlassBackdrop.takeIf { isLiquidGlass }) {
    Box(
        Modifier
            .fillMaxSize()
            .background(rasterBackgroundColor)
    ) {
        // PDF 页面（上下连续滚动 / 上下分页 / 相册式左右分页）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(pdfGlassBackdrop)
                    else Modifier
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (annotationMode && inkColorExpanded) {
                        inkColorExpanded = false
                    } else {
                        showMenu = !showMenu
                    }
                }
        ) {
            when {
                pageSourceFailed -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (book?.format == BookFormat.CBZ) {
                                stringResource(R.string.cbz_open_failed)
                            } else {
                                stringResource(R.string.pdf_page_load_failed, currentPage + 1)
                            },
                            fontSize = 14.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
                isHorizontal -> {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !annotationMode,
                        // 从右往左阅读时整条页序反向排布，使"向右滑动 = 向后翻页"。
                        reverseLayout = isRightToLeft,
                        modifier = Modifier.fillMaxSize()
                    ) { displayIndex ->
                        val spread = if (spreadEnabled) {
                            CbzSpreadPlanner.spreadFor(
                                displayIndex,
                                pageCount,
                                COVER_ALONE_FIRST_PAGE
                            )
                        } else {
                            null
                        }
        if (spread != null) {
                            PdfSpreadPage(
                                firstPage = spread.firstPage,
                                secondPage = spread.secondPage,
                                isRightToLeft = isRightToLeft,
                                pageSource = pageSource,
                                aspectRatios = pageAspectRatios,
                                renderMode = renderMode,
                                annotationMode = annotationMode,
                                activeInkTool = selectedInkTool,
                                activeInkColor = ReaderHighlightPalette
                                    .getOrNull(selectedInkColorSlot)?.first
                                    ?: DefaultReaderHighlightColor,
                                existingStrokes = inkStrokes,
                                onStrokeCommitted = viewModel::addPdfInkStroke,
                                onStrokeErased = viewModel::deletePdfInkStroke
                            )
                        } else {
                            PdfPagerPage(
                                pageIndex = displayIndex,
                                pageCount = displayCount,
                                axis = PdfPagerAxis.HORIZONTAL,
                                pagerState = pagerState,
                                scope = scope,
                                pageSource = pageSource,
                                aspectRatios = pageAspectRatios,
                                renderMode = renderMode,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                onScaleChange = { scale = it },
                                onOffsetChange = { x, y -> offsetX = x; offsetY = y },
                                annotationMode = annotationMode,
                                activeInkTool = selectedInkTool,
                                activeInkColor = ReaderHighlightPalette
                                    .getOrNull(selectedInkColorSlot)?.first
                                    ?: DefaultReaderHighlightColor,
                                existingStrokes = inkStrokes,
                                onStrokeCommitted = viewModel::addPdfInkStroke,
                                onStrokeErased = viewModel::deletePdfInkStroke
                            )
                        }
                    }
                }
                isVerticalPaging -> {
                    VerticalPager(
                        state = verticalPagerState,
                        userScrollEnabled = !annotationMode,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        PdfPagerPage(
                            pageIndex = pageIndex,
                            pageCount = pageCount,
                            axis = PdfPagerAxis.VERTICAL,
                            pagerState = verticalPagerState,
                            scope = scope,
                            pageSource = pageSource,
                            aspectRatios = pageAspectRatios,
                            renderMode = renderMode,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            onScaleChange = { scale = it },
                            onOffsetChange = { x, y -> offsetX = x; offsetY = y },
                            annotationMode = annotationMode,
                            activeInkTool = selectedInkTool,
                            activeInkColor = ReaderHighlightPalette.getOrNull(selectedInkColorSlot)?.first
                                ?: DefaultReaderHighlightColor,
                            existingStrokes = inkStrokes,
                            onStrokeCommitted = viewModel::addPdfInkStroke,
                            onStrokeErased = viewModel::deletePdfInkStroke
                        )
                    }
                }
                else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageMode, annotationMode) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Let the previous fling run while idle; only a real new touch stops it.
                                zoomPanDecayJob.value?.cancel()
                                zoomPanDecayJob.value = null
                                val velocityTracker = VelocityTracker()
                                val velocityEstimator = PdfPanVelocityEstimator()
                                var trackedPanPosition = Offset.Zero
                                velocityTracker.addPosition(down.uptimeMillis, trackedPanPosition)
                                velocityEstimator.reset(down.uptimeMillis)
                                var releaseUptimeMillis = down.uptimeMillis
                                var transformGesture = false
                                var gestureMode = PdfMultiTouchMode.UNDECIDED
                                var pendingPan = Offset.Zero
                                var pendingZoom = 1f
                                var gestureScale = scale
                                var gestureOffsetX = offsetX
                                var gestureOffsetY = offsetY
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val eventUptimeMillis = event.changes.maxOfOrNull { it.uptimeMillis }
                                        ?: releaseUptimeMillis
                                    releaseUptimeMillis = eventUptimeMillis
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed >= 2) transformGesture = true
                                    if (pressed == 0) {
                                        break
                                    } else if (transformGesture ||
                                        (!annotationMode && gestureScale > PDF_ZOOM_EPSILON)
                                    ) {
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        velocityEstimator.addPan(eventUptimeMillis, pan)
                                        trackedPanPosition += pan
                                        event.changes.maxOfOrNull { it.uptimeMillis }?.let { uptimeMillis ->
                                            velocityTracker.addPosition(uptimeMillis, trackedPanPosition)
                                        }
                                        pendingPan += pan
                                        pendingZoom *= zoom
                                        if (gestureMode == PdfMultiTouchMode.UNDECIDED) {
                                            val zoomMotion = kotlin.math.abs(1f - pendingZoom) *
                                                event.calculateCentroidSize(useCurrent = false)
                                            gestureMode = resolvePdfTransformMode(
                                                panMotion = pendingPan.getDistance(),
                                                zoomMotion = zoomMotion,
                                                touchSlop = viewConfiguration.touchSlop,
                                                preferPan = annotationMode && transformGesture
                                            )
                                        }
                                        if (gestureMode == PdfMultiTouchMode.ZOOM) {
                                            val newScale = (gestureScale * pendingZoom).coerceIn(1f, 5f)
                                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                                            gestureScale = newScale
                                            gestureOffsetX = (gestureOffsetX + pendingPan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            gestureOffsetY = (gestureOffsetY + pendingPan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            scale = gestureScale
                                            offsetX = gestureOffsetX
                                            offsetY = gestureOffsetY
                                            if (gestureScale <= PDF_ZOOM_EPSILON) {
                                                gestureOffsetX = 0f
                                                gestureOffsetY = 0f
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            pendingPan = Offset.Zero
                                            pendingZoom = 1f
                                        } else if (gestureMode == PdfMultiTouchMode.PAN) {
                                            if (gestureScale > PDF_ZOOM_EPSILON) {
                                                val maxOffsetX = (gestureScale - 1f) * size.width / 2f
                                                val maxOffsetY = (gestureScale - 1f) * size.height / 2f
                                                val xResult = consumePdfPanDelta(
                                                    offset = gestureOffsetX,
                                                    maxOffset = maxOffsetX,
                                                    delta = pendingPan.x
                                                )
                                                val beforeOffsetY = gestureOffsetY
                                                val yResult = consumePdfPanDelta(
                                                    offset = beforeOffsetY,
                                                    maxOffset = maxOffsetY,
                                                    delta = pendingPan.y
                                                )
                                                gestureOffsetX = xResult.offset
                                                gestureOffsetY = yResult.offset
                                                offsetX = gestureOffsetX
                                                offsetY = gestureOffsetY
                                                val consumed = gestureOffsetY - beforeOffsetY
                                                val residual = pendingPan.y - consumed
                                                if (residual != 0f) {
                                                    listState.dispatchRawDelta(-residual)
                                                }
                                            } else {
                                                listState.dispatchRawDelta(-pendingPan.y)
                                            }
                                            pendingPan = Offset.Zero
                                            pendingZoom = 1f
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                }
                                val navigationGesture = !annotationMode || transformGesture
                                if (shouldStartPdfPanDecay(gestureScale, gestureMode, navigationGesture)) {
                                    val trackedVelocity = velocityTracker.calculateVelocity()
                                    val estimatedVelocity = velocityEstimator.velocityAt(releaseUptimeMillis)
                                    val velocity = resolvePdfReleaseVelocity(
                                        tracked = Offset(trackedVelocity.x, trackedVelocity.y),
                                        estimated = estimatedVelocity
                                    )
                                    zoomPanDecayJob.value = scope.launch {
                                        coroutineScope {
                                            if (gestureScale > PDF_ZOOM_EPSILON) {
                                                launch {
                                                    animatePdfPanDecay(
                                                        initialOffset = gestureOffsetX,
                                                        initialVelocity = velocity.x,
                                                        maxOffset = (gestureScale - 1f) * size.width / 2f,
                                                        onOffsetChange = { value ->
                                                            val liveMax = ((scale - 1f) * size.width / 2f)
                                                                .coerceAtLeast(0f)
                                                            offsetX = value.coerceIn(-liveMax, liveMax)
                                                        }
                                                    )
                                                }
                                            }
                                            animatePdfPanDecay(
                                                initialOffset = gestureOffsetY,
                                                initialVelocity = velocity.y,
                                                maxOffset = (gestureScale - 1f) * size.height / 2f,
                                                onOffsetChange = { value ->
                                                    // 每次写回都按"当前"缩放级别夹取：
                                                    // 若用户在这段动画里缩回原始大小，位移立即归零。
                                                    val liveMax = ((scale - 1f) * size.height / 2f)
                                                        .coerceAtLeast(0f)
                                                    offsetY = value.coerceIn(-liveMax, liveMax)
                                                },
                                                onUnconsumedDelta = { residual ->
                                                    if (residual != 0f) listState.dispatchRawDelta(-residual)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !annotationMode,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pageCount) {
                            PdfPageItem(
                                pageSource = pageSource,
                                aspectRatios = pageAspectRatios,
                                pageIndex = it,
                                fitToViewport = false,
                                zoomScale = scale,
                                renderMode = renderMode,
                                requestSelectedQuality = it == currentPage,
                                annotationEnabled = true,
                                annotationInteractive = false,
                                activeInkTool = selectedInkTool,
                                activeInkColor = ReaderHighlightPalette.getOrNull(selectedInkColorSlot)?.first
                                    ?: DefaultReaderHighlightColor,
                                existingStrokes = inkStrokes,
                                onStrokeCommitted = viewModel::addPdfInkStroke,
                                onStrokeErased = viewModel::deletePdfInkStroke
                            )
                        }
                    }
                    if (annotationMode) {
                        PdfDocumentInkCanvas(
                            pageCount = pageCount,
                            listState = listState,
                            activeTool = selectedInkTool,
                            activeColor = ReaderHighlightPalette.getOrNull(selectedInkColorSlot)?.first
                                ?: DefaultReaderHighlightColor,
                            strokes = inkStrokes,
                            onStrokeCommitted = viewModel::addPdfInkStroke,
                            onStrokeErased = viewModel::deletePdfInkStroke
                        )
                    }
                }
            }
            }
        }

        // ── 顶部栏（淡入淡出）──
        AnimatedVisibility(
            visible = showMenu,
            enter = when {
                eInkMode -> EnterTransition.None
                !motionEnabled -> fadeIn(tween(120))
                else -> slideInVertically(initialOffsetY = { -it }, animationSpec = tween(180, easing = AppEasing.Smooth)) + fadeIn(tween(180))
            },
            exit = when {
                eInkMode -> ExitTransition.None
                !motionEnabled -> fadeOut(tween(100))
                else -> slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(140, easing = AppEasing.Accelerate)) + fadeOut(tween(140))
            },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PdfTopBar(
                title = book?.title ?: "",
                currentPage = currentPage,
                pageCount = pageCount,
                isBookmarked = isCurrentPageBookmarked,
                pageMode = pageMode,
                renderMode = renderMode,
                eInkModeEnabled = eInkMode,
                glassContentScrimColor = pdfGlassContentScrim,
                showTtsAction = !isComic,
                isTtsActive = ttsState.activeBookId == bookId &&
                    ttsState.playbackState != TtsPlaybackState.IDLE,
                onBack = exitReader,
                onPageModeToggle = {
                    if (!eInkMode) {
                        pendingModePage = currentPage
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        viewModel.togglePdfPageMode()
                    }
                },
                onRenderModeToggle = viewModel::togglePageRenderMode,
                onTtsToggle = {
                    if (ttsState.activeBookId == bookId &&
                        ttsState.playbackState != TtsPlaybackState.IDLE
                    ) {
                        viewModel.toggleTtsPlayPause()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            runCatching {
                                ttsNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.startPdfTts(filePath, currentPage, pageCount)
                    }
                },
                onBookmarkToggle = {
                    if (isCurrentPageBookmarked) {
                        bookmarks.firstOrNull { it.chapterIndex == currentPage }
                            ?.let { viewModel.deleteBookmark(it) }
                    } else {
                        viewModel.addPdfBookmark(currentPage, book?.title ?: "")
                    }
                }
            )
        }

        // 普通主题使用阅读辅助渐变；液态玻璃直接采样原始书页。
        if (!isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = menuAlpha.value }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to AppColors.WindowBg.copy(alpha = 0f),
                                0.2f to AppColors.WindowBg.copy(alpha = 0.4f),
                                0.5f to AppColors.WindowBg.copy(alpha = 0.8f),
                                0.8f to AppColors.WindowBg.copy(alpha = 0.95f),
                                1.0f to AppColors.WindowBg
                            )
                        )
                    )
            )
        }

        // ── 底部胶囊菜单（淡入+从底部上移）──
        AnimatedVisibility(
            visible = showMenu,
            enter = slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(280, easing = AppEasing.Smooth)
            ) + fadeIn(tween(240)),
            exit = slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(220, easing = AppEasing.Accelerate)
            ) + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                PdfBottomMenu(
                    chapterTitle = if (isComic) {
                        uiState.chapterTitles.getOrNull(currentPage) ?: (book?.title ?: "")
                    } else {
                        book?.title ?: ""
                    },
                    chapterProgress = if (pageCount > 0) {
                        ((currentPage + 1).toFloat() / pageCount * 100f).coerceIn(0f, 100f)
                    } else {
                        0f
                    },
                    pageSource = pageSource,
                    pdfPageCount = pageCount,
                    conversionState = conversionState,
                    glassContentScrimColor = pdfGlassContentScrim,
                    isComic = isComic,
                    readingDirection = readingDirection,
                    // 只有当前窗口真正能双页对开时才高亮，避免竖屏下开关看起来"开了但没效果"。
                    twoPageSpreadEnabled = spreadEnabled,
                    showTwoPageSpreadToggle = isHorizontal,
                    onDirectionToggle = {
                        viewModel.toggleCbzReadingDirection()
                    },
                    onTwoPageSpreadToggle = {
                        viewModel.setTwoPageSpreadEnabled(!uiState.twoPageSpreadEnabled)
                    },
                    onConversionClick = {
                        showMenu = false
                        if (conversionState is PdfConversionState.Running) {
                            conversionSheet = PdfConversionSheet.Progress
                        } else {
                            scope.launch {
                                val convertedBookId = viewModel.findConvertedPdfBookId()
                                conversionSheet = if (convertedBookId == null) {
                                    PdfConversionSheet.Confirm()
                                } else {
                                    PdfConversionSheet.Existing(convertedBookId)
                                }
                            }
                        }
                    },
                     onCatalogClick = {
                         showMenu = false
                         showPdfToc = true
                     },
                     onCatalogProgressDragStart = {
                         isCatalogScrubbing = true
                     },
                     onCatalogProgressPageChange = { page ->
                         // 拖动过程中就跳到目标页：上一帧还没落地的跳转直接取消，不排队，
                         // 这样松手前页面已经加载好，不会再有"从远处滚动过去"的延迟。
                         catalogSeekJob.value?.cancel()
                         catalogSeekJob.value = scope.launch { scrollToPdfPage(page, animate = false) }
                     },
                     onCatalogProgressDragEnd = { finalProgress ->
                         val targetPage = pdfPageIndexForProgress(finalProgress, pageCount)
                         isCatalogScrubbing = false
                         lastSavedPage = targetPage
                         viewModel.saveProgressDirect(
                             bookId,
                             rasterReadingProgress(targetPage, pageCount)
                         )
                         catalogSeekJob.value?.cancel()
                         catalogSeekJob.value = scope.launch {
                             scrollToPdfPage(targetPage, animate = false)
                         }
                     },
                     onAnnotationClick = {
                        annotationMode = !annotationMode
                        inkColorExpanded = false
                        showMenu = true
                    },
                    onBookmarksClick = {
                        showMenu = false
                        showPdfBookmarks = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        val annotationBottomPadding by animateDpAsState(
            targetValue = if (showMenu) 206.dp else 24.dp,
            animationSpec = tween(300, easing = AppEasing.Smooth),
            label = "pdfAnnotationToolBottomPadding"
        )
        AnimatedVisibility(
            visible = annotationMode,
            enter = fadeIn(tween(220)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f)
            ),
            exit = fadeOut(tween(180)) + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(220, easing = AppEasing.Accelerate)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 24.dp,
                    bottom = annotationBottomPadding
                )
        ) {
            PdfAnnotationToolCapsule(
                selectedTool = selectedInkTool,
                colorExpanded = inkColorExpanded,
                selectedColorSlot = selectedInkColorSlot,
                onToolSelected = { tool ->
                    if (tool == selectedInkTool && tool != PdfInkTool.ERASER) {
                        inkColorExpanded = !inkColorExpanded
                    } else {
                        selectedInkTool = tool
                        inkColorExpanded = false
                    }
                },
                onColorSelected = { slot ->
                    selectedInkColorSlot = slot
                    inkColorExpanded = false
                }
            )
        }
        val ttsBottomPadding by animateDpAsState(
            targetValue = if (showMenu) 160.dp else 44.dp,
            animationSpec = if (eInkMode || !motionEnabled) tween(0) else spring(dampingRatio = 0.82f, stiffness = 360f),
            label = "ttsBottomPadding"
        )
        AnimatedVisibility(
            visible = ttsState.activeBookId == bookId &&
                ttsState.playbackState != TtsPlaybackState.IDLE &&
                !showPdfToc && !showPdfBookmarks && conversionSheet == null,
            enter = if (eInkMode) EnterTransition.None else if (!motionEnabled) fadeIn(tween(120)) else slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = if (eInkMode) ExitTransition.None else if (!motionEnabled) fadeOut(tween(100)) else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = ttsBottomPadding)
        ) {
            TtsPlayerPanel(
                playbackState = ttsState.playbackState,
                speechRate = ttsState.speechRate,
                speechRateMode = ttsState.speechRateMode,
                pitch = ttsState.pitch,
                pitchMode = ttsState.pitchMode,
                usesAndroidTts = ttsState.usesAndroidTts,
                sleepTimerRemainingMs = ttsState.sleepTimerRemainingMs,
                onPlayPause = viewModel::toggleTtsPlayPause,
                onStop = viewModel::stopTts,
                onSkipForward = viewModel::ttsSkipForward,
                onSkipBackward = viewModel::ttsSkipBackward,
                onRateChange = viewModel::setTtsSpeechRate,
                onRateModeChange = viewModel::setTtsSpeechRateMode,
                onPitchChange = viewModel::setTtsPitch,
                onPitchModeChange = viewModel::setTtsPitchMode,
                onSetSleepTimer = viewModel::setSleepTimer,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
                readerBackgroundColor = AppColors.WindowBg,
                readerContentColor = AppColors.TextPrimary
            )
        }

        // ── PDF 目录缩略图 Sheet ──
        PdfTocSheet(
            visible = showPdfToc,
            pageSource = pageSource,
            pageCount = pageCount,
            currentPage = currentPage,
            bookmarkedPages = bookmarkedPages,
            chapterGroups = uiState.comicChapterEntries.map { entry ->
                entry.title to entry.chapterIndex
            },
            onPageSelected = { page ->
                scope.launch {
                    scrollToPdfPage(page)
                }
                showPdfToc = false
            },
            onDismiss = { showPdfToc = false },
            eInkModeEnabled = eInkMode
        )

        PdfBookmarksSheet(
            visible = showPdfBookmarks,
            bookmarks = bookmarks,
            currentPage = currentPage,
            onPageSelected = { page ->
                scope.launch {
                    scrollToPdfPage(page)
                }
                showPdfBookmarks = false
            },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showPdfBookmarks = false },
            eInkModeEnabled = eInkMode
        )

        conversionSheet?.let { sheet ->
            PdfConversionBottomSheet(
                sheet = sheet,
                conversionState = conversionState,
                onDismiss = { conversionSheet = null },
                onSheetChange = { conversionSheet = it },
                mineruMode = mineruMode,
                onStartLocal = { replaceExisting ->
                    startConversion(
                        replaceExisting = replaceExisting,
                        engine = PdfConversionEngine.LOCAL
                    )
                },
                onStartMineru = { replaceExisting, selectedMode ->
                    startConversion(
                        replaceExisting = replaceExisting,
                        engine = PdfConversionEngine.MINERU,
                        selectedMineruMode = selectedMode
                    )
                },
                onOpenMineruSettings = { replaceExisting ->
                    pendingReplaceAfterMineruSettings = replaceExisting
                    runCatching {
                        mineruSettingsLauncher.launch(
                            android.content.Intent(context, DetailActivity::class.java)
                                .putExtra("category", "mineru")
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.pdf_convert_error_mineru_not_configured,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onOpenMineruWebsite = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(com.huangder.lumibooks.mineru.MineruConfig.MANUAL_WEB_URL)
                            )
                        )
                    }.onFailure {
                        Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                    }
                },
                onPickManualResult = { replaceExisting ->
                    pendingManualReplace = replaceExisting
                    runCatching {
                        manualResultPicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "text/markdown",
                                "text/plain",
                                "application/octet-stream"
                            )
                        )
                    }.onFailure {
                        Toast.makeText(context, R.string.mineru_manual_import_failed, Toast.LENGTH_LONG).show()
                    }
                },
                onOpenExisting = openBookFromReader,
                onCancelConversion = viewModel::cancelPdfConversion,
                onStayPdf = viewModel::consumePdfConversionResult,
                onOpenConverted = { convertedBookId ->
                    viewModel.consumePdfConversionResult()
                    openBookFromReader(convertedBookId)
                }
            )
        }
    }
}
}

@Composable
private fun PdfPagerPage(
    pageIndex: Int,
    pageCount: Int,
    axis: PdfPagerAxis,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    pageSource: BitmapPageSource?,
    aspectRatios: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>,
    renderMode: PageRenderMode,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    annotationMode: Boolean,
    activeInkTool: PdfInkTool,
    activeInkColor: String,
    existingStrokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    val latestScale = rememberUpdatedState(scale)
    val latestOffsetX = rememberUpdatedState(offsetX)
    val latestOffsetY = rememberUpdatedState(offsetY)
    val panDecayJob = remember { mutableStateOf<Job?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pageIndex, axis) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Let the previous fling run while idle; only a real new touch stops it.
                    panDecayJob.value?.cancel()
                    panDecayJob.value = null
                    val velocityTracker = VelocityTracker()
                    val velocityEstimator = PdfPanVelocityEstimator()
                    var trackedPanPosition = Offset.Zero
                    velocityTracker.addPosition(down.uptimeMillis, trackedPanPosition)
                    velocityEstimator.reset(down.uptimeMillis)
                    var releaseUptimeMillis = down.uptimeMillis
                    var pointersPressed: Boolean
                    var transformGesture = false
                    var gestureMode = PdfMultiTouchMode.UNDECIDED
                    var documentPan = 0f
                    var edgeDrag = 0f
                    var pendingPan = Offset.Zero
                    var pendingZoom = 1f
                    var gestureScale = latestScale.value
                    var gestureOffsetX = latestOffsetX.value
                    var gestureOffsetY = latestOffsetY.value
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val eventUptimeMillis = event.changes.maxOfOrNull { it.uptimeMillis }
                            ?: releaseUptimeMillis
                        releaseUptimeMillis = eventUptimeMillis
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) transformGesture = true
                        if (transformGesture || (!annotationMode && gestureScale > PDF_ZOOM_EPSILON)) {
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            velocityEstimator.addPan(eventUptimeMillis, pan)
                            trackedPanPosition += pan
                            event.changes.maxOfOrNull { it.uptimeMillis }?.let { uptimeMillis ->
                                velocityTracker.addPosition(uptimeMillis, trackedPanPosition)
                            }
                            pendingPan += pan
                            pendingZoom *= zoom
                            if (gestureMode == PdfMultiTouchMode.UNDECIDED) {
                                val zoomMotion = kotlin.math.abs(1f - pendingZoom) *
                                    event.calculateCentroidSize(useCurrent = false)
                                gestureMode = resolvePdfTransformMode(
                                    panMotion = pendingPan.getDistance(),
                                    zoomMotion = zoomMotion,
                                    touchSlop = viewConfiguration.touchSlop,
                                    preferPan = annotationMode && transformGesture
                                )
                            }
                            if (gestureMode == PdfMultiTouchMode.PAN) {
                                val delta = if (axis == PdfPagerAxis.HORIZONTAL) pendingPan.x else pendingPan.y
                                if (gestureScale > PDF_ZOOM_EPSILON) {
                                    val xResult = consumePdfPanDelta(
                                        offset = gestureOffsetX,
                                        maxOffset = (gestureScale - 1f) * size.width / 2f,
                                        delta = pendingPan.x,
                                        edgeDrag = if (axis == PdfPagerAxis.HORIZONTAL) edgeDrag else 0f
                                    )
                                    val yResult = consumePdfPanDelta(
                                        offset = gestureOffsetY,
                                        maxOffset = (gestureScale - 1f) * size.height / 2f,
                                        delta = pendingPan.y,
                                        edgeDrag = if (axis == PdfPagerAxis.VERTICAL) edgeDrag else 0f
                                    )
                                    edgeDrag = if (axis == PdfPagerAxis.HORIZONTAL) {
                                        xResult.edgeDrag
                                    } else {
                                        yResult.edgeDrag
                                    }
                                    gestureOffsetX = xResult.offset
                                    gestureOffsetY = yResult.offset
                                    onOffsetChange(gestureOffsetX, gestureOffsetY)
                                } else {
                                    documentPan += delta
                                    pagerState.dispatchRawDelta(-delta)
                                }
                                pendingPan = Offset.Zero
                                pendingZoom = 1f
                            } else if (gestureMode == PdfMultiTouchMode.ZOOM) {
                                val newScale = (gestureScale * pendingZoom).coerceIn(1f, 5f)
                                val maxOffsetX = (newScale - 1f) * size.width / 2f
                                val maxOffsetY = (newScale - 1f) * size.height / 2f
                                gestureScale = newScale
                                gestureOffsetX = (gestureOffsetX + pendingPan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                gestureOffsetY = (gestureOffsetY + pendingPan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                onScaleChange(newScale)
                                onOffsetChange(gestureOffsetX, gestureOffsetY)
                                pendingPan = Offset.Zero
                                pendingZoom = 1f
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                        pointersPressed = event.changes.any { it.pressed }
                    } while (pointersPressed)
                    panDecayJob.value = scope.launch {
                        var finalEdgeDrag = edgeDrag
                        val navigationGesture = !annotationMode || transformGesture
                        if (shouldStartPdfPanDecay(gestureScale, gestureMode, navigationGesture)) {
                            val trackedVelocity = velocityTracker.calculateVelocity()
                            val estimatedVelocity = velocityEstimator.velocityAt(releaseUptimeMillis)
                            val velocity = resolvePdfReleaseVelocity(
                                tracked = Offset(trackedVelocity.x, trackedVelocity.y),
                                estimated = estimatedVelocity
                            )
                            val initialOffset = if (axis == PdfPagerAxis.HORIZONTAL) gestureOffsetX else gestureOffsetY
                            val maxOffset = (gestureScale - 1f) *
                                (if (axis == PdfPagerAxis.HORIZONTAL) size.width else size.height) / 2f
                            coroutineScope {
                                if (gestureScale > PDF_ZOOM_EPSILON) {
                                    launch {
                                        val crossInitial = if (axis == PdfPagerAxis.HORIZONTAL) {
                                            gestureOffsetY
                                        } else {
                                            gestureOffsetX
                                        }
                                        val crossVelocity = if (axis == PdfPagerAxis.HORIZONTAL) {
                                            velocity.y
                                        } else {
                                            velocity.x
                                        }
                                        val crossMax = (gestureScale - 1f) *
                                            (if (axis == PdfPagerAxis.HORIZONTAL) size.height else size.width) / 2f
                                        animatePdfPanDecay(
                                            initialOffset = crossInitial,
                                            initialVelocity = crossVelocity,
                                            maxOffset = crossMax,
                                            onOffsetChange = { offset ->
                                                val liveMax = ((latestScale.value - 1f) *
                                                    (if (axis == PdfPagerAxis.HORIZONTAL) size.height else size.width) / 2f)
                                                    .coerceAtLeast(0f)
                                                val clamped = offset.coerceIn(-liveMax, liveMax)
                                                if (axis == PdfPagerAxis.HORIZONTAL) {
                                                    gestureOffsetY = clamped
                                                } else {
                                                    gestureOffsetX = clamped
                                                }
                                                onOffsetChange(gestureOffsetX, gestureOffsetY)
                                            }
                                        )
                                    }
                                }
                                finalEdgeDrag = animatePdfPanDecay(
                                    initialOffset = initialOffset,
                                    initialVelocity = if (axis == PdfPagerAxis.HORIZONTAL) velocity.x else velocity.y,
                                    maxOffset = maxOffset,
                                    initialEdgeDrag = edgeDrag,
                                    onOffsetChange = { offset ->
                                        // 按"当前"缩放级别夹取，缩回原始大小后不会再被旧的位移量推回去。
                                        val liveMax = ((latestScale.value - 1f) *
                                            (if (axis == PdfPagerAxis.HORIZONTAL) size.width else size.height) / 2f)
                                            .coerceAtLeast(0f)
                                        val clamped = offset.coerceIn(-liveMax, liveMax)
                                        if (axis == PdfPagerAxis.HORIZONTAL) {
                                            gestureOffsetX = clamped
                                        } else {
                                            gestureOffsetY = clamped
                                        }
                                        onOffsetChange(gestureOffsetX, gestureOffsetY)
                                    }
                                )
                            }
                        }
                        val targetPage = if (navigationGesture &&
                            gestureScale > PDF_ZOOM_EPSILON && gestureMode == PdfMultiTouchMode.PAN
                        ) {
                            pdfPageForEdgeDrag(pageIndex, pageCount, finalEdgeDrag)
                        } else if (navigationGesture && transformGesture && gestureMode == PdfMultiTouchMode.PAN) {
                            when {
                                documentPan + finalEdgeDrag < -PDF_PAGE_TURN_THRESHOLD_PX -> pageIndex + 1
                                documentPan + finalEdgeDrag > PDF_PAGE_TURN_THRESHOLD_PX -> pageIndex - 1
                                else -> pageIndex
                            }.coerceIn(0, pageCount - 1)
                        } else {
                            pageIndex
                        }
                        val shouldSettlePager = navigationGesture &&
                            transformGesture &&
                            gestureScale <= PDF_ZOOM_EPSILON &&
                            gestureMode == PdfMultiTouchMode.PAN
                        if (targetPage != pageIndex || shouldSettlePager) {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        PdfPageItem(
            pageSource = pageSource,
            aspectRatios = aspectRatios,
            pageIndex = pageIndex,
            fitToViewport = true,
            // Pager 会预组合相邻页；只给当前页补缩放分辨率，避免同时分配三张大图。
            zoomScale = if (pageIndex == pagerState.currentPage) scale else 1f,
            renderMode = renderMode,
            requestSelectedQuality = pageIndex == pagerState.currentPage,
            annotationEnabled = true,
            annotationInteractive = annotationMode,
            activeInkTool = activeInkTool,
            activeInkColor = activeInkColor,
            existingStrokes = existingStrokes,
            onStrokeCommitted = onStrokeCommitted,
            onStrokeErased = onStrokeErased
        )
    }
}

/**
 * 双页对开的一屏：两页并排铺满可用宽度。从右往左阅读时较前的一页放在右侧，
 * 每页各自维护自己的手绘批注层，坐标不受镜像影响。
 */
@Composable
private fun PdfSpreadPage(
    firstPage: Int?,
    secondPage: Int?,
    isRightToLeft: Boolean,
    pageSource: BitmapPageSource?,
    aspectRatios: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>,
    renderMode: PageRenderMode,
    annotationMode: Boolean,
    activeInkTool: PdfInkTool,
    activeInkColor: String,
    existingStrokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    val leftPage = if (isRightToLeft) secondPage else firstPage
    val rightPage = if (isRightToLeft) firstPage else secondPage
    Row(modifier = Modifier.fillMaxSize()) {
        PdfSpreadSlot(
            pageIndex = leftPage,
            pageSource = pageSource,
            aspectRatios = aspectRatios,
            renderMode = renderMode,
            annotationMode = annotationMode,
            activeInkTool = activeInkTool,
            activeInkColor = activeInkColor,
            existingStrokes = existingStrokes,
            onStrokeCommitted = onStrokeCommitted,
            onStrokeErased = onStrokeErased,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        PdfSpreadSlot(
            pageIndex = rightPage,
            pageSource = pageSource,
            aspectRatios = aspectRatios,
            renderMode = renderMode,
            annotationMode = annotationMode,
            activeInkTool = activeInkTool,
            activeInkColor = activeInkColor,
            existingStrokes = existingStrokes,
            onStrokeCommitted = onStrokeCommitted,
            onStrokeErased = onStrokeErased,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun PdfSpreadSlot(
    pageIndex: Int?,
    pageSource: BitmapPageSource?,
    aspectRatios: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>,
    renderMode: PageRenderMode,
    annotationMode: Boolean,
    activeInkTool: PdfInkTool,
    activeInkColor: String,
    existingStrokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (pageIndex == null) return@Box
        PdfPageItem(
            pageSource = pageSource,
            aspectRatios = aspectRatios,
            pageIndex = pageIndex,
            fitToViewport = true,
            displayWidthFactor = 0.5f,
            renderMode = renderMode,
            annotationEnabled = true,
            annotationInteractive = annotationMode,
            activeInkTool = activeInkTool,
            activeInkColor = activeInkColor,
            existingStrokes = existingStrokes,
            onStrokeCommitted = onStrokeCommitted,
            onStrokeErased = onStrokeErased
        )
    }
}

// ── 顶部栏（与 EPUB ReaderTopBar 一致，增加 PDF 专属页码显示）──
@Composable
private fun PdfTopBar(
    title: String,
    currentPage: Int,
    pageCount: Int,
    isBookmarked: Boolean = false,
    pageMode: PdfPageMode,
    renderMode: PageRenderMode = PageRenderMode.NORMAL,
    eInkModeEnabled: Boolean = false,
    glassContentScrimColor: Color,
    /** 漫画没有文字层，朗读入口不适用。 */
    showTtsAction: Boolean = true,
    isTtsActive: Boolean,
    onBack: () -> Unit,
    onPageModeToggle: () -> Unit,
    onRenderModeToggle: () -> Unit = {},
    onTtsToggle: () -> Unit,
    onBookmarkToggle: () -> Unit = {}
) {
    val eInkMode = eInkModeEnabled || LocalEInkMode.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .then(
                if (isLiquidGlass) {
                    Modifier
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to AppColors.WindowBg,
                                0.3f to AppColors.WindowBg,
                                0.6f to AppColors.WindowBg.copy(alpha = 0.85f),
                                1.0f to AppColors.WindowBg.copy(alpha = 0f)
                            )
                        )
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 28.dp, top = 42.dp, end = 28.dp, bottom = 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧：返回按钮 + 页码（内部垂直居中，整体与右侧第一个按钮对齐）
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidGlassSurface(
                    shape = CircleShape,
                    fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                    contentScrimColor = glassContentScrimColor,
                    modifier = Modifier
                        .size(36.dp),
                    onClick = onBack,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.ArrowLeft, stringResource(R.string.pdf_back), tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                // 页码徽章：与返回键等高的胶囊，左侧控件保持同一视觉高度
                LiquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    fallbackColor = Color.Black.copy(alpha = 0.35f),
                    contentScrimColor = glassContentScrimColor,
                    modifier = Modifier
                        .height(36.dp)
                ) {
                    Text(
                        text = "${currentPage + 1} / $pageCount",
                        fontSize = 12.sp,
                        color = if (isLiquidGlass) AppColors.TextPrimary else Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                // 渲染清晰度档位：正常 → 高清 → 原图 → 正常。
                if (!eInkMode) {
                    Spacer(Modifier.width(6.dp))
                    val renderModeLabel = stringResource(
                        when (renderMode) {
                            PageRenderMode.NORMAL -> R.string.page_render_mode_normal
                            PageRenderMode.HIGH -> R.string.page_render_mode_high
                            PageRenderMode.NATIVE -> R.string.page_render_mode_native
                        }
                    )
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        fallbackColor = if (renderMode == PageRenderMode.NORMAL) {
                            AppColors.BgGray.copy(alpha = 0.8f)
                        } else {
                            AppColors.Accent.copy(alpha = 0.18f)
                        },
                        contentScrimColor = glassContentScrimColor,
                        modifier = Modifier.height(36.dp),
                        onClick = onRenderModeToggle
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Image,
                                contentDescription = stringResource(
                                    R.string.page_render_mode_desc,
                                    renderModeLabel
                                ),
                                tint = if (renderMode == PageRenderMode.NORMAL) {
                                    AppColors.TextSecondary
                                } else {
                                    AppColors.Accent
                                },
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = renderModeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isLiquidGlass) AppColors.TextPrimary else AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // 中间：书名
            ReaderTitleCapsule(
                title = title,
                contentColor = AppColors.TextSecondary.copy(alpha = if (isLiquidGlass) 0.88f else 0.7f),
                fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                glassContentScrimColor = glassContentScrimColor,
                isLiquidGlass = isLiquidGlass,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
                textAlign = TextAlign.Start
            )

            // 右侧按钮：竖向排列
            Column(
                modifier = Modifier.width(36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!eInkMode) {
                    LiquidGlassSurface(
                        shape = CircleShape,
                        fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                        contentScrimColor = glassContentScrimColor,
                        modifier = Modifier
                            .size(36.dp),
                        onClick = onPageModeToggle,
                        contentAlignment = Alignment.Center
                    ) {
                        val nextPageMode = pageMode.next()
                        Icon(
                            when (pageMode) {
                                PdfPageMode.HORIZONTAL_PAGING -> AppIcons.Cards
                                PdfPageMode.VERTICAL_PAGING -> AppIcons.Rows
                                PdfPageMode.VERTICAL_SCROLL -> AppIcons.Rows
                            },
                            contentDescription = stringResource(
                                when (nextPageMode) {
                                    PdfPageMode.HORIZONTAL_PAGING -> R.string.pdf_switch_to_horizontal
                                    PdfPageMode.VERTICAL_PAGING -> R.string.pdf_switch_to_vertical_paging
                                    PdfPageMode.VERTICAL_SCROLL -> R.string.pdf_switch_to_vertical
                                }
                            ),
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (showTtsAction) {
                    LiquidGlassSurface(
                        shape = CircleShape,
                        fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                        contentScrimColor = glassContentScrimColor,
                        modifier = Modifier.size(36.dp),
                        onClick = onTtsToggle,
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.Headphones,
                            contentDescription = stringResource(R.string.tts_listen),
                            tint = if (isTtsActive) AppColors.Accent else AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                LiquidGlassSurface(
                    shape = CircleShape,
                    fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                    contentScrimColor = glassContentScrimColor,
                    modifier = Modifier
                        .size(36.dp),
                    onClick = onBookmarkToggle,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Bookmark.resolve(isBookmarked),
                        contentDescription = stringResource(R.string.pdf_bookmark),
                        tint = if (isBookmarked) AppColors.Accent else AppColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ── 底部胶囊菜单 ──
@Composable
private fun PdfBottomMenu(
    chapterTitle: String,
    chapterProgress: Float,
    pageSource: BitmapPageSource?,
    pdfPageCount: Int,
    conversionState: PdfConversionState,
    glassContentScrimColor: Color,
    isComic: Boolean = false,
    readingDirection: CbzReadingDirection = CbzReadingDirection.LEFT_TO_RIGHT,
    twoPageSpreadEnabled: Boolean = false,
    /** 双页对开只在横向翻页下有意义，其他页模式隐藏该开关。 */
    showTwoPageSpreadToggle: Boolean = true,
    onConversionClick: () -> Unit,
    onDirectionToggle: () -> Unit = {},
    onTwoPageSpreadToggle: () -> Unit = {},
    onCatalogClick: () -> Unit,
    onCatalogProgressDragStart: (() -> Unit)? = null,
    onCatalogProgressPageChange: ((Int) -> Unit)? = null,
    onCatalogProgressDragEnd: ((Float) -> Unit)? = null,
    onAnnotationClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isComic) {
            // 漫画没有可重排文本，这里换成阅读方向与双页对开两个开关。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PdfActionCapsule(
                    icon = AppIcons.ArrowsLeftRight,
                    label = stringResource(
                        if (readingDirection.isRightToLeft) R.string.cbz_direction_rtl
                        else R.string.cbz_direction_ltr
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onDirectionToggle
                )
                if (showTwoPageSpreadToggle) {
                    PdfActionCapsule(
                        icon = AppIcons.Cards,
                        label = stringResource(R.string.cbz_two_page_spread),
                        active = twoPageSpreadEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = onTwoPageSpreadToggle
                    )
                }
            }
        } else {
            PdfConversionCapsule(
                conversionState = conversionState,
                glassContentScrimColor = glassContentScrimColor,
                onClick = onConversionClick
            )
        }
        // 目录胶囊
        PdfCatalogCapsule(
            title = chapterTitle,
            progress = chapterProgress,
            pageSource = pageSource,
            pageCount = pdfPageCount,
            glassContentScrimColor = glassContentScrimColor,
            onClick = onCatalogClick,
            onProgressDragStart = onCatalogProgressDragStart,
            onProgressPageChange = onCatalogProgressPageChange,
            onProgressDragEnd = onCatalogProgressDragEnd
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PdfActionCapsule(
                icon = AppIcons.PencilSimple,
                label = stringResource(R.string.pdf_annotation_tool),
                modifier = Modifier.weight(1f),
                onClick = onAnnotationClick
            )
            PdfActionCapsule(
                icon = AppIcons.Bookmark.filled,
                label = stringResource(R.string.reader_bookmark),
                modifier = Modifier.weight(1f),
                onClick = onBookmarksClick
            )
        }
    }
}

@Composable
private fun PdfConversionCapsule(
    conversionState: PdfConversionState,
    glassContentScrimColor: Color,
    onClick: () -> Unit
) {
    val running = conversionState as? PdfConversionState.Running
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = AppColors.BgGray,
        contentScrimColor = glassContentScrimColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (running != null) {
                CircularProgressIndicator(
                    progress = { running.progress / 100f },
                    modifier = Modifier.size(18.dp),
                    color = AppColors.Accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    AppIcons.Books,
                    contentDescription = null,
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (running == null) {
                    stringResource(R.string.pdf_convert_action)
                } else {
                    stringResource(R.string.pdf_convert_running, running.progress)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            if (running != null && running.totalPages > 0) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${running.currentPage} / ${running.totalPages}",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PdfConversionBottomSheet(
    sheet: PdfConversionSheet,
    conversionState: PdfConversionState,
    onDismiss: () -> Unit,
    onSheetChange: (PdfConversionSheet) -> Unit,
    mineruMode: MineruMode,
    onStartLocal: (Boolean) -> Unit,
    onStartMineru: (Boolean, MineruMode) -> Unit,
    onOpenMineruSettings: (Boolean) -> Unit,
    onOpenMineruWebsite: () -> Unit,
    onPickManualResult: (Boolean) -> Unit,
    onOpenExisting: (String) -> Unit,
    onCancelConversion: () -> Unit,
    onStayPdf: () -> Unit,
    onOpenConverted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val eInkMode = LocalEInkMode.current
    val offset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        offset.snapTo(1f)
        if (eInkMode) offset.snapTo(0f) else offset.animateBottomSheetIn()
    }

    fun closeThen(action: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            if (eInkMode) offset.snapTo(1f) else offset.animateBottomSheetOut()
            onDismiss()
            action()
        }
    }

    fun dismissForCurrentState() {
        when (sheet) {
            PdfConversionSheet.Cancel -> onSheetChange(PdfConversionSheet.Progress)
            is PdfConversionSheet.Completed -> closeThen(onStayPdf)
            else -> closeThen()
        }
    }

    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { dismissForCurrentState() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.28f * (1f - offset.value)
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { dismissForCurrentState() }
        )
        LiquidGlassColumnSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .materialBottomSheetMotion(offset.value, predictiveBackProgress),
            contentModifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.TextSecondary.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = sheet,
                transitionSpec = {
                    if (eInkMode) {
                        EnterTransition.None.togetherWith(ExitTransition.None)
                            .using(SizeTransform(clip = true))
                    } else {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 5 })
                            .togetherWith(
                                fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 }
                            )
                            .using(SizeTransform(clip = true))
                    }
                },
                label = "pdfConversionSheetContent"
            ) { currentSheet ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentSheet) {
                        is PdfConversionSheet.Confirm -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_choose_method_title),
                                message = stringResource(R.string.pdf_convert_choose_method_body)
                            )
                            PdfConversionMethodButton(
                                icon = AppIcons.DeviceMobile,
                                title = stringResource(R.string.pdf_convert_local_title),
                                description = stringResource(R.string.pdf_convert_sheet_body),
                                onClick = {
                                    onStartLocal(currentSheet.replaceExisting)
                                    onSheetChange(PdfConversionSheet.Progress)
                                }
                            )
                            PdfConversionMethodButton(
                                icon = AppIcons.CloudArrowUp,
                                title = stringResource(R.string.pdf_convert_mineru_title),
                                description = if (mineruMode == MineruMode.DISABLED) {
                                    stringResource(R.string.pdf_convert_mineru_not_configured_short)
                                } else {
                                    stringResource(
                                        R.string.pdf_convert_mineru_mode_description,
                                        if (mineruMode == MineruMode.AGENT) {
                                            stringResource(R.string.mineru_mode_agent_short)
                                        } else {
                                            stringResource(R.string.mineru_mode_precise_short)
                                        }
                                    )
                                },
                                cloud = true,
                                onClick = {
                                    if (mineruMode == MineruMode.DISABLED) {
                                        onSheetChange(
                                            PdfConversionSheet.MineruNotConfigured(
                                                currentSheet.replaceExisting
                                            )
                                        )
                                    } else {
                                        onStartMineru(currentSheet.replaceExisting, mineruMode)
                                        onSheetChange(PdfConversionSheet.Progress)
                                    }
                                }
                            )
                            PdfConversionMethodButton(
                                icon = AppIcons.File,
                                title = stringResource(R.string.pdf_convert_mineru_manual_title),
                                description = stringResource(R.string.pdf_convert_mineru_manual_description),
                                cloud = true,
                                onClick = {
                                    onSheetChange(
                                        PdfConversionSheet.MineruManual(currentSheet.replaceExisting)
                                    )
                                }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.cancel),
                                onClick = { closeThen() }
                            )
                        }
                        is PdfConversionSheet.MineruNotConfigured -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_mineru_not_configured_title),
                                message = stringResource(R.string.pdf_convert_mineru_not_configured_body)
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_go_to_mineru_settings),
                                primary = true,
                                onClick = {
                                    closeThen { onOpenMineruSettings(currentSheet.replaceExisting) }
                                }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.cancel),
                                onClick = { closeThen() }
                            )
                        }
                        is PdfConversionSheet.MineruManual -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_mineru_manual_sheet_title),
                                message = stringResource(R.string.pdf_convert_mineru_manual_sheet_body)
                            )
                            PdfConversionMethodButton(
                                icon = AppIcons.Globe,
                                title = stringResource(R.string.mineru_manual_open_website),
                                description = stringResource(R.string.pdf_convert_mineru_manual_website_hint),
                                cloud = true,
                                onClick = onOpenMineruWebsite
                            )
                            PdfConversionMethodButton(
                                icon = AppIcons.File,
                                title = stringResource(R.string.mineru_manual_import_result),
                                description = stringResource(R.string.pdf_convert_mineru_manual_import_hint),
                                onClick = { onPickManualResult(currentSheet.replaceExisting) }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.back),
                                onClick = {
                                    onSheetChange(
                                        PdfConversionSheet.Confirm(currentSheet.replaceExisting)
                                    )
                                }
                            )
                        }
                        is PdfConversionSheet.Existing -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_existing_title),
                                message = stringResource(R.string.pdf_convert_existing_body)
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_open_existing),
                                primary = true,
                                onClick = {
                                    closeThen { onOpenExisting(currentSheet.convertedBookId) }
                                }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_replace),
                                destructive = true,
                                onClick = { onSheetChange(PdfConversionSheet.Confirm(replaceExisting = true)) }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.cancel),
                                onClick = { closeThen() }
                            )
                        }
                        PdfConversionSheet.Progress -> {
                            val running = conversionState as? PdfConversionState.Running
                            PdfConversionProgressContent(running)
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_cancel_action),
                                onClick = { onSheetChange(PdfConversionSheet.Cancel) }
                            )
                        }
                        is PdfConversionSheet.Completed -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_complete_title),
                                message = if (currentSheet.manualImport) {
                                    stringResource(R.string.pdf_convert_mineru_manual_complete_body)
                                } else {
                                    stringResource(
                                        R.string.pdf_convert_complete_body,
                                        currentSheet.textPages,
                                        currentSheet.totalPages
                                    )
                                }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_open_result),
                                primary = true,
                                onClick = {
                                    closeThen {
                                        onOpenConverted(currentSheet.convertedBookId)
                                    }
                                }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_stay_pdf),
                                onClick = { closeThen(onStayPdf) }
                            )
                        }
                        PdfConversionSheet.Cancel -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_cancel_title),
                                message = stringResource(R.string.pdf_convert_cancel_body)
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_cancel_action),
                                destructive = true,
                                onClick = { closeThen(onCancelConversion) }
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.pdf_convert_keep_running),
                                primary = true,
                                onClick = { onSheetChange(PdfConversionSheet.Progress) }
                            )
                        }
                        is PdfConversionSheet.Failure -> {
                            PdfSheetText(
                                title = stringResource(R.string.pdf_convert_failed_title),
                                message = stringResource(
                                    pdfConversionErrorResource(currentSheet.errorCode)
                                )
                            )
                            PdfSheetButton(
                                label = stringResource(R.string.close),
                                primary = true,
                                onClick = { closeThen() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfConversionMethodButton(
    icon: ImageVector,
    title: String,
    description: String,
    cloud: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.BgGray)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (cloud) AppColors.Accent else AppColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PdfSheetText(title: String, message: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi),
        color = AppColors.TextPrimary
    )
    Text(
        text = message,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = AppColors.TextSecondary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PdfConversionProgressContent(
    running: PdfConversionState.Running?
) {
    val progress = running?.progress?.coerceIn(0, 100) ?: 0
    Text(
        text = stringResource(
            if (running?.manualImport == true) {
                R.string.pdf_convert_mineru_manual_progress_title
            } else {
                R.string.pdf_convert_progress_title
            }
        ),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi),
        color = AppColors.TextPrimary
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        if (running == null || running.totalPages <= 0) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = AppColors.Accent,
                strokeWidth = 5.dp
            )
        } else {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxSize(),
                color = AppColors.Accent,
                trackColor = AppColors.BgGray,
                strokeWidth = 5.dp
            )
            Text(
                text = "$progress%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }
    }
    Text(
        text = if (running != null && running.totalPages > 0) {
            stringResource(
                R.string.pdf_convert_progress_pages,
                running.currentPage,
                running.totalPages
            )
        } else {
            stringResource(R.string.pdf_convert_preparing)
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
        fontSize = 13.sp,
        color = AppColors.TextSecondary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PdfSheetButton(
    label: String,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        destructive -> Color(0xFFE85D5D)
        primary -> AppColors.Accent
        else -> AppColors.BgGray
    }
    val contentColor = if (primary) AppColors.OnAccent else if (destructive) Color.White else AppColors.TextPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .cardPressEffect()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

private fun pdfConversionErrorResource(errorCode: String): Int {
    return when (errorCode) {
        PdfConversionContract.ERROR_NO_TEXT -> R.string.pdf_convert_error_no_text
        PdfConversionContract.ERROR_ENCRYPTED -> R.string.pdf_convert_error_encrypted
        PdfConversionContract.ERROR_FILE_MISSING -> R.string.pdf_convert_error_file_missing
        PdfConversionContract.ERROR_STORAGE -> R.string.pdf_convert_error_storage
        PdfConversionContract.ERROR_MINERU_NOT_CONFIGURED -> R.string.pdf_convert_error_mineru_not_configured
        PdfConversionContract.ERROR_MINERU_FILE_LIMIT -> R.string.pdf_convert_error_mineru_file_limit
        PdfConversionContract.ERROR_MINERU_PAGE_LIMIT -> R.string.pdf_convert_error_mineru_page_limit
        PdfConversionContract.ERROR_MINERU_AUTH -> R.string.pdf_convert_error_mineru_auth
        PdfConversionContract.ERROR_MINERU_RATE_LIMIT -> R.string.pdf_convert_error_mineru_rate_limit
        PdfConversionContract.ERROR_MINERU_NETWORK -> R.string.pdf_convert_error_mineru_network
        PdfConversionContract.ERROR_MINERU_UPLOAD -> R.string.pdf_convert_error_mineru_upload
        PdfConversionContract.ERROR_MINERU_SERVICE -> R.string.pdf_convert_error_mineru_service
        PdfConversionContract.ERROR_MINERU_RESULT -> R.string.pdf_convert_error_mineru_result
        PdfConversionContract.ERROR_MINERU_MANUAL_FORMAT -> R.string.pdf_convert_error_mineru_manual_format
        PdfConversionContract.ERROR_MINERU_MANUAL_TOO_LARGE -> R.string.pdf_convert_error_mineru_manual_too_large
        PdfConversionContract.ERROR_MINERU_MANUAL_IMPORT -> R.string.pdf_convert_error_mineru_manual_import
        else -> R.string.pdf_convert_error_unknown
    }
}

@Composable
private fun PdfCatalogCapsule(
    title: String,
    progress: Float,
    pageSource: BitmapPageSource?,
    pageCount: Int,
    glassContentScrimColor: Color,
    onClick: () -> Unit,
    onProgressDragStart: (() -> Unit)? = null,
    onProgressPageChange: ((Int) -> Unit)? = null,
    onProgressDragEnd: ((Float) -> Unit)? = null
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    var previewBitmap by remember(pageSource) { mutableStateOf<Bitmap?>(null) }
    val dragSession = remember { CatalogProgressDragSession() }
    val scrubTracker = remember { CatalogProgressScrubTracker() }
    val latestOnClick = rememberUpdatedState(onClick)
    val latestOnDragStart = rememberUpdatedState(onProgressDragStart)
    val latestOnPageChange = rememberUpdatedState(onProgressPageChange)
    val latestOnDragEnd = rememberUpdatedState(onProgressDragEnd)
    val latestExternalProgress = rememberUpdatedState(progress)
    val latestPageCount = rememberUpdatedState(pageCount)

    LaunchedEffect(progress) {
        if (!isDragging) dragProgress = progress
    }

    val displayProgress = if (isDragging) dragProgress else progress
    val previewPage = pdfPageIndexForProgress(displayProgress, pageCount)
    val previewWidth = (LocalConfiguration.current.screenWidthDp - 120)
        .coerceIn(224, 248)
        .dp
    // Use the actual page ratio when available; the portrait ratio is only a loading fallback.
    val previewAspectRatio = previewBitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    } ?: (1f / 1.414f)
    val previewHeight = previewWidth / previewAspectRatio.coerceAtLeast(0.1f)

    // Render a reasonably dense bitmap once, then let Compose scale it into the large card.
    LaunchedEffect(pageSource, previewPage, isDragging) {
        if (pageCount <= 0 || !isDragging) {
            previewBitmap = null
            return@LaunchedEffect
        }
        previewBitmap = pageSource?.renderThumbnail(previewPage, CATALOG_PREVIEW_WIDTH_PX)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (isDragging) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackColor = AppColors.CardBg,
                contentScrimColor = glassContentScrimColor.copy(alpha = if (isLiquidGlass) 0.28f else 0.12f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Keep the preview prominent enough to identify a page while leaving
                    // the progress capsule visible below it.
                    .offset(y = -(previewHeight + 12.dp))
                    // The catalog capsule is only 48dp tall and passes that max constraint
                    // to its children; required* keeps this preview from being flattened.
                    .requiredWidth(previewWidth)
                    .requiredHeight(previewHeight)
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                onClick = null,
                interactive = false,
                contentAlignment = Alignment.Center
            ) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.pdf_page_desc, previewPage + 1),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.dp,
                        color = if (isLiquidGlass) AppColors.TextPrimary else AppColors.Accent
                    )
                }
                Text(
                    text = stringResource(R.string.pdf_page_desc, previewPage + 1),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            if (isLiquidGlass) AppColors.WindowBg.copy(alpha = 0.65f)
                            else AppColors.CardBg.copy(alpha = 0.90f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
        }
        LiquidGlassSurface(
            shape = RoundedCornerShape(24.dp),
            fallbackColor = AppColors.BgGray,
            contentScrimColor = glassContentScrimColor,
            modifier = Modifier.fillMaxSize(),
            onClick = null,
            interactive = false,
            contentAlignment = Alignment.TopStart
        ) {
            if (isLiquidGlass) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 5.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(AppColors.TextPrimary.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((displayProgress / 100f).coerceIn(0f, 1f))
                            .clip(CircleShape)
                            .background(AppColors.Accent.copy(alpha = 0.82f))
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((displayProgress / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(AppColors.Accent.copy(alpha = 0.8f))
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val foreground = if (isLiquidGlass || displayProgress <= 5f) AppColors.TextPrimary else Color.White
                Icon(AppIcons.Bookmark.filled, null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pdf_toc), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
                Spacer(Modifier.weight(1f))
                Text(
                    formatReadingProgressPercent(displayProgress),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isLiquidGlass && displayProgress > 70f) Color.White.copy(alpha = 0.9f)
                    else AppColors.TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onProgressDragEnd != null) {
                    if (latestOnDragEnd.value == null) {
                        detectTapGestures(onTap = { latestOnClick.value() })
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var cumulativeDrag = 0f
                        var dragging = false
                        var committed = false
                        // 拖动中"拖到哪就跳到哪"：一次拖动会跨过很多页，
                        // 只把目标页真正变化的那些帧转成跳转请求。
                        fun applyDrag(deltaPx: Float) {
                            val dragDp = with(density) { deltaPx.toDp().value }
                            val nextProgress = dragSession.dragBy(dragDp * 0.25f)
                            dragProgress = nextProgress
                            scrubTracker
                                .nextSeek(nextProgress, latestPageCount.value)
                                ?.let { page -> latestOnPageChange.value?.invoke(page) }
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = change.positionChange().x
                                if (!dragging) {
                                    cumulativeDrag += dx
                                    if (kotlin.math.abs(cumulativeDrag) >= viewConfiguration.touchSlop) {
                                        dragging = true
                                        isDragging = true
                                        dragSession.begin(latestExternalProgress.value)
                                        scrubTracker.reset()
                                        latestOnDragStart.value?.invoke()
                                        applyDrag(cumulativeDrag)
                                        change.consume()
                                    }
                                } else {
                                    change.consume()
                                    applyDrag(dx)
                                }

                                if (change.changedToUpIgnoreConsumed()) {
                                    if (!dragging) {
                                        latestOnClick.value()
                                    } else {
                                        committed = true
                                        dragSession.finish { latestOnDragEnd.value?.invoke(it) }
                                    }
                                    break
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            if (dragging && !committed) {
                                // 手势被系统抢占打断时，阅读位置在拖动中已经跟着走了，
                                // 这里仍然按当前进度收尾，避免停留在"跳到一半"的状态。
                                dragSession.finish { latestOnDragEnd.value?.invoke(it) }
                            }
                            isDragging = false
                        }
                    }
                }
        )
    }
}

@Composable
private fun PdfActionCapsule(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (active) AppColors.Accent else AppColors.TextPrimary
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = if (active) AppColors.Accent.copy(alpha = 0.14f) else AppColors.BgGray,
        contentScrimColor = AppColors.WindowBg.copy(alpha = 0.18f),
        modifier = modifier
            .height(44.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = contentColor)
        }
    }
}

@Composable
private fun PdfAnnotationToolCapsule(
    selectedTool: PdfInkTool,
    colorExpanded: Boolean,
    selectedColorSlot: Int,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val capsuleWidth by animateDpAsState(
        targetValue = if (colorExpanded) 246.dp else 174.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "pdfAnnotationCapsuleWidth"
    )
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = AppColors.BgGray,
        contentScrimColor = AppColors.WindowBg.copy(alpha = 0.18f),
        modifier = Modifier
            .width(capsuleWidth)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = colorExpanded,
            transitionSpec = {
                (fadeIn(tween(170)) + androidx.compose.animation.scaleIn(initialScale = 0.82f, animationSpec = spring(dampingRatio = 0.72f)))
                    .togetherWith(fadeOut(tween(100)) + androidx.compose.animation.scaleOut(targetScale = 0.82f))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "pdfAnnotationColorTransition"
        ) { expanded ->
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReaderHighlightPalette.forEachIndexed { index, (_, color) ->
                        Box(
                            modifier = Modifier
                                .size(if (index == selectedColorSlot) 25.dp else 21.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (index == selectedColorSlot) {
                                        Modifier.border(2.dp, AppColors.TextPrimary, CircleShape)
                                    } else Modifier
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onColorSelected(index) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PdfAnnotationToolButton(
                        tool = PdfInkTool.PEN,
                        selected = selectedTool == PdfInkTool.PEN,
                        onClick = { onToolSelected(PdfInkTool.PEN) }
                    )
                    PdfAnnotationToolButton(
                        tool = PdfInkTool.HIGHLIGHTER,
                        selected = selectedTool == PdfInkTool.HIGHLIGHTER,
                        onClick = { onToolSelected(PdfInkTool.HIGHLIGHTER) }
                    )
                    PdfAnnotationToolButton(
                        tool = PdfInkTool.ERASER,
                        selected = selectedTool == PdfInkTool.ERASER,
                        onClick = { onToolSelected(PdfInkTool.ERASER) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfAnnotationToolButton(
    tool: PdfInkTool,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.16f else 0.94f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 430f),
        label = "pdfAnnotationToolScale"
    )
    val icon = when (tool) {
        PdfInkTool.PEN -> AppIcons.PencilSimple
        PdfInkTool.HIGHLIGHTER -> AppIcons.PaintBrush
        PdfInkTool.ERASER -> AppIcons.Trash
    }
    val contentDescription = when (tool) {
        PdfInkTool.PEN -> stringResource(R.string.pdf_annotation_pen)
        PdfInkTool.HIGHLIGHTER -> stringResource(R.string.pdf_annotation_highlighter)
        PdfInkTool.ERASER -> stringResource(R.string.pdf_annotation_eraser)
    }
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) AppColors.Accent else AppColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── PDF 页面渲染（阅读器会话内复用 renderer，并串行化页面访问）──

@Composable
private fun PdfPageItem(
    pageSource: BitmapPageSource?,
    /** 全文档共用的页面宽高比缓存，避免同一页重复测量、也避免首帧兜底高度跳变。 */
    aspectRatios: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>,
    pageIndex: Int,
    fitToViewport: Boolean,
    /** 这一页在屏幕上的显示宽度占屏幕宽度的比例；双页对开的半屏页传 0.5f。 */
    displayWidthFactor: Float = 1f,
    /** 当前手势缩放倍率；达到新的半倍档时后台补一张更高分辨率页。 */
    zoomScale: Float = 1f,
    /** 解码清晰度档位：正常只解到够显示用，高清 / 原图按原图解码。 */
    renderMode: PageRenderMode = PageRenderMode.NORMAL,
    /** Only the reading anchor may consume the single high-resolution render slot. */
    requestSelectedQuality: Boolean = true,
    annotationEnabled: Boolean,
    annotationInteractive: Boolean,
    activeInkTool: PdfInkTool,
    activeInkColor: String,
    existingStrokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val screenWidthPx = configuration.screenWidthDp * density
    val baseDisplayWidthPx = (screenWidthPx * displayWidthFactor)
        .toInt()
        .coerceAtLeast(PAGE_RENDER_MIN_WIDTH_PX)
    // 缩放时按半倍档补高分辨率图，不在手势每一帧重新分配 Bitmap。
    val zoomRenderBucket = pdfZoomRenderBucket(zoomScale)
    val renderTargetWidthPx = (baseDisplayWidthPx * zoomRenderBucket).toInt()
    val effectiveRenderMode = if (requestSelectedQuality) renderMode else PageRenderMode.NORMAL
    var bitmap by remember(pageSource, pageIndex) { mutableStateOf(pageSource?.cachedReadablePage(pageIndex)) }
    var bitmapTargetWidthPx by remember(pageSource, pageIndex) { mutableStateOf(0) }
    var bitmapRenderMode by remember(pageSource, pageIndex) { mutableStateOf(PageRenderMode.NORMAL) }
    // 快速翻页的低清首帧。它只用于阅读区过渡，不参与最终画质和续读缓存。
    var previewBitmap by remember(pageSource, pageIndex) { mutableStateOf<Bitmap?>(null) }
    // 高分辨率档先铺一张“正常”档的解码结果：1× 显示下就是清晰的，
    // 不用让用户对着 240px 预览等原图解码。
    var baseBitmap by remember(pageSource, pageIndex) {
        mutableStateOf(pageSource?.cachedReadablePage(pageIndex))
    }
    var tileSource by remember(pageSource, pageIndex) { mutableStateOf<RasterTileSource?>(null) }
    var tileSourceResolved by remember(pageSource, pageIndex) { mutableStateOf(false) }
    var tileLoaded by remember(pageSource, pageIndex) { mutableStateOf(false) }
    var tileFailed by remember(pageSource, pageIndex) { mutableStateOf(false) }
    val visible = pageSource?.visiblePages?.collectAsState()?.value?.contains(pageIndex) == true
    // 载入中按页面真实宽高比占位：占位尺寸与最终图片完全一致，
    // 快速滚动时列表不会因为页面变高/变矮而反复回弹。
    val pageAspectRatio = aspectRatios[pageIndex] ?: pageSource?.cachedAspectRatio(pageIndex)
    // The page source owns decoded bitmaps (they are cached, never recycled by callers).
    LaunchedEffect(pageSource, pageIndex) {
        if (aspectRatios[pageIndex] == null) {
            pageSource?.pageAspectRatio(pageIndex)?.let { ratio -> aspectRatios[pageIndex] = ratio }
        }
    }
    LaunchedEffect(pageSource, pageIndex) {
        tileSourceResolved = false
        tileLoaded = false
        tileFailed = false
        tileSource = pageSource?.tiledPage(pageIndex)
        baseBitmap = pageSource?.cachedReadablePage(pageIndex)
        tileSourceResolved = true
    }
    // 预览和清晰图并行请求：快速移动时先给出旧版的低清首帧，停留后由
    // PDF NORMAL、CBZ 分块图或普通位图覆盖。迟到的预览不会覆盖已到达的清晰图。
    LaunchedEffect(pageSource, pageIndex) {
        val source = pageSource ?: return@LaunchedEffect
        val preview = source.renderPreview(pageIndex, PREVIEW_RENDER_WIDTH_PX) ?: return@LaunchedEffect
        if (bitmap == null && baseBitmap == null && !tileLoaded) {
            previewBitmap = preview
        }
    }
    val activeTileSource = tileSource?.takeUnless { tileFailed }
    // Every composed page must obtain a readable NORMAL bitmap even if visibility reporting lags
    // behind a fast fling or a tiled source fails. Visible PDF pages can then refine to the selected
    // quality; CBZ tiles provide their own high-detail refinement above this bitmap.
    val bitmapRequestMode = if (visible && activeTileSource == null) effectiveRenderMode else PageRenderMode.NORMAL
    val bitmapRequestWidthPx = if (visible && activeTileSource == null) renderTargetWidthPx else baseDisplayWidthPx
    LaunchedEffect(
        pageSource,
        pageIndex,
        bitmapRequestWidthPx,
        bitmapRequestMode,
        tileSourceResolved,
        activeTileSource,
        tileLoaded,
        visible
    ) {
        val source = pageSource ?: return@LaunchedEffect
        if (!tileSourceResolved) return@LaunchedEffect
        if (activeTileSource != null) {
            val readableFallback = bitmap ?: baseBitmap
            if (readableFallback != null || tileLoaded || !visible) return@LaunchedEffect
            // SSIV usually produces the first clear CBZ frame faster than a full-page decode.
            // Keep a short timeout so a slow codec can never strand the page blank.
            delay(CBZ_BITMAP_FALLBACK_DELAY_MS)
        }
        // 已有更宽的图时直接复用；缩小回去不需要再渲染一张小图。
        if (bitmap != null && bitmapTargetWidthPx >= bitmapRequestWidthPx &&
            bitmapRenderMode.ordinal >= bitmapRequestMode.ordinal
        ) {
            return@LaunchedEffect
        }
        // 解码可能因为快速滑动/缩放被取消，或在内存紧张时失败；只要这一页还显示在屏幕上
        // 就重试几次，否则会留下一块永远不消失的空白。
        // 高分辨率档的单页位图很大，失败重试只会反复分配，所以重试上限压到 2 次。
        val maxAttempts = if (bitmapRequestMode == PageRenderMode.NORMAL) {
            PAGE_RENDER_MAX_ATTEMPTS
        } else {
            PAGE_RENDER_HIGH_RES_MAX_ATTEMPTS
        }
        var attempt = 0
        while (attempt < maxAttempts) {
            val rendered = source.renderPage(pageIndex, bitmapRequestWidthPx, bitmapRequestMode)
            if (rendered != null) {
                bitmap = rendered
                bitmapTargetWidthPx = bitmapRequestWidthPx
                if (bitmapRequestMode.ordinal > bitmapRenderMode.ordinal) {
                    bitmapRenderMode = bitmapRequestMode
                }
                return@LaunchedEffect
            }
            attempt++
            delay(PAGE_RENDER_RETRY_DELAY_MS * attempt)
        }
        android.util.Log.w(
            "RasterReader",
            "Page $pageIndex rendered no bitmap after $attempt attempt(s) in $bitmapRequestMode"
        )
    }

    val stableRatio = pageAspectRatio ?: activeTileSource?.dimensions?.ratio
        ?: (bitmap ?: baseBitmap)?.let { it.width.toFloat() / it.height }
    val loadingBackground = if (LocalEInkMode.current) Color.White else AppColors.WindowBg
    Box(
        modifier = (if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth().then(
            if (stableRatio != null) Modifier.aspectRatio(stableRatio) else Modifier.height(600.dp)
        )).background(loadingBackground),
        contentAlignment = Alignment.Center
    ) {
        val tiled = activeTileSource
        val renderedPage = bitmap ?: baseBitmap ?: previewBitmap
        if (tiled != null) {
            CbzTiledPage(
                source = tiled,
                zoomScale = zoomScale,
                foreground = visible,
                fallback = renderedPage,
                modifier = Modifier.fillMaxSize(),
                onImageLoaded = {
                    tileLoaded = true
                    pageSource?.pageDrawn(pageIndex, true)
                },
                onError = { tileFailed = true }
            )
        } else if (renderedPage != null) {
            Image(
                bitmap = renderedPage.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page_desc, pageIndex + 1),
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    pageSource?.pageDrawn(pageIndex, bitmap != null && bitmapTargetWidthPx >= renderTargetWidthPx)
                },
                contentScale = if (fitToViewport) ContentScale.Fit else ContentScale.FillWidth
            )
        } else {
            Box(
                modifier = if (fitToViewport) {
                    Modifier.fillMaxSize()
                } else {
                    val ratio = pageAspectRatio
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (ratio != null && ratio > 0f) Modifier.aspectRatio(ratio)
                            else Modifier.height(600.dp)
                        )
                },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.TextSecondary.copy(alpha = 0.4f)
                )
            }
        }
        if (annotationEnabled) {
            PdfPageInkCanvas(
                pageIndex = pageIndex,
                activeTool = activeInkTool,
                activeColor = activeInkColor,
                strokes = existingStrokes,
                interactive = annotationInteractive,
                onStrokeCommitted = onStrokeCommitted,
                onStrokeErased = onStrokeErased
            )
        }
    }
}

@Composable
private fun BoxScope.PdfPageInkCanvas(
    pageIndex: Int,
    activeTool: PdfInkTool,
    activeColor: String,
    strokes: List<PdfInkStroke>,
    interactive: Boolean,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    var canvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var livePoints by remember(pageIndex) { mutableStateOf<List<PdfInkPoint>>(emptyList()) }
    val pageStrokes = remember(strokes, pageIndex) { strokes.filter { it.page == pageIndex } }

    fun normalize(position: Offset): PdfInkPoint {
        val width = canvasSize.width.coerceAtLeast(1)
        val height = canvasSize.height.coerceAtLeast(1)
        return PdfInkPoint(
            (position.x / width).coerceIn(0f, 1f),
            (position.y / height).coerceIn(0f, 1f)
        )
    }

    val gestureModifier = if (interactive) {
        Modifier.pointerInput(pageIndex, activeTool, activeColor, canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val points = ArrayList<PdfInkPoint>(32)
                    val pointsErased = mutableSetOf<Long>()
                    val pendingErasedStrokes = linkedMapOf<Long, PdfInkStroke>()
                    var cancelled = false
                    points += normalize(down.position)
                    livePoints = points.toList()

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.size > 1) {
                            cancelled = true
                            livePoints = emptyList()
                            // Leave this event unconsumed so the parent transform detector can
                            // take over as a normal two-finger zoom/pan gesture.
                        } else if (pressedChanges.isEmpty()) {
                            break
                        } else if (!cancelled) {
                            val change = pressedChanges.first()
                            val point = normalize(change.position)
                            points += point
                            livePoints = points.toList()
                            change.consume()
                            if (activeTool == PdfInkTool.ERASER) {
                                val erased = pageStrokes.filter { stroke ->
                                    val key = stroke.id.takeIf { it > 0L }
                                        ?: PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L)).hashCode().toLong()
                                    key !in pointsErased && strokeHitsPoint(stroke, point)
                                }
                                erased.forEach { stroke ->
                                    val key = stroke.id.takeIf { it > 0L }
                                        ?: PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L)).hashCode().toLong()
                                    pointsErased += key
                                    pendingErasedStrokes[key] = stroke
                                }
                            }
                        }
                    }

                    val completed = points.toList()
                    livePoints = emptyList()
                    if (!cancelled) {
                        if (activeTool == PdfInkTool.ERASER) {
                            pendingErasedStrokes.values.forEach(onStrokeErased)
                        } else if (completed.size >= 2) {
                            onStrokeCommitted(
                                PdfInkStroke(
                                    page = pageIndex,
                                    points = simplifyInkPoints(completed),
                                    tool = activeTool,
                                    color = activeColor,
                                    width = if (activeTool == PdfInkTool.HIGHLIGHTER) 0.018f else 0.006f
                                )
                            )
                        }
                    }
                }
            }
    } else {
        Modifier
    }
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier)
    ) {
        pageStrokes.forEach(::drawInkStroke)
        if (livePoints.size >= 2 && activeTool != PdfInkTool.ERASER) {
            drawInkStroke(
                PdfInkStroke(
                    page = pageIndex,
                    points = livePoints,
                    tool = activeTool,
                    color = activeColor,
                    width = if (activeTool == PdfInkTool.HIGHLIGHTER) 0.018f else 0.006f
                )
            )
        }
    }
}

/**
 * Document-level ink surface for continuous vertical reading. A gesture is split into
 * page-local normalized segments as it crosses page boundaries, so each segment remains
 * aligned after relayout, zoom, or reopening the document.
 */
@Composable
private fun BoxScope.PdfDocumentInkCanvas(
    pageCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    activeTool: PdfInkTool,
    activeColor: String,
    strokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var liveSegments by remember { mutableStateOf<Map<Int, List<PdfInkPoint>>>(emptyMap()) }

    fun locate(position: Offset): Pair<Int, PdfInkPoint>? {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val item = visibleItems.firstOrNull {
            position.y >= it.offset && position.y <= it.offset + it.size
        } ?: visibleItems.minByOrNull {
            kotlin.math.abs(position.y - (it.offset + it.size / 2f))
        } ?: return null
        val width = canvasSize.width.coerceAtLeast(1)
        val height = item.size.coerceAtLeast(1)
        return item.index.coerceIn(0, pageCount - 1) to PdfInkPoint(
            (position.x / width).coerceIn(0f, 1f),
            ((position.y - item.offset) / height).coerceIn(0f, 1f)
        )
    }

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(pageCount, activeTool, activeColor, canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val segments = linkedMapOf<Int, MutableList<PdfInkPoint>>()
                    val erasedKeys = mutableSetOf<Long>()
                    val pendingErasedStrokes = linkedMapOf<Long, PdfInkStroke>()
                    var cancelled = false
                    var previousLocated: Pair<Int, PdfInkPoint>? = null

                    fun addPoint(position: Offset) {
                        val located = locate(position) ?: return
                        val (page, point) = located
                        previousLocated?.let { (previousPage, previousPoint) ->
                            if (previousPage != page) {
                                val boundaryX = ((previousPoint.x + point.x) / 2f).coerceIn(0f, 1f)
                                if (previousPage < page) {
                                    segments.getOrPut(previousPage) { mutableListOf() }
                                        .add(PdfInkPoint(boundaryX, 1f))
                                    for (bridgePage in previousPage + 1 until page) {
                                        segments.getOrPut(bridgePage) { mutableListOf() }.apply {
                                            add(PdfInkPoint(boundaryX, 0f))
                                            add(PdfInkPoint(boundaryX, 1f))
                                        }
                                    }
                                    segments.getOrPut(page) { mutableListOf() }
                                        .add(PdfInkPoint(boundaryX, 0f))
                                } else {
                                    segments.getOrPut(previousPage) { mutableListOf() }
                                        .add(PdfInkPoint(boundaryX, 0f))
                                    for (bridgePage in previousPage - 1 downTo page + 1) {
                                        segments.getOrPut(bridgePage) { mutableListOf() }.apply {
                                            add(PdfInkPoint(boundaryX, 1f))
                                            add(PdfInkPoint(boundaryX, 0f))
                                        }
                                    }
                                    segments.getOrPut(page) { mutableListOf() }
                                        .add(PdfInkPoint(boundaryX, 1f))
                                }
                            }
                        }
                        segments.getOrPut(page) { mutableListOf() }.add(point)
                        previousLocated = located
                        liveSegments = if (activeTool == PdfInkTool.ERASER) {
                            emptyMap()
                        } else {
                            segments.mapValues { (_, points) -> points.toList() }
                        }
                        if (activeTool == PdfInkTool.ERASER) {
                            strokes.asSequence()
                                .filter { it.page == page }
                                .filter { stroke ->
                                    val key = stroke.id.takeIf { it > 0L }
                                        ?: PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L)).hashCode().toLong()
                                    key !in erasedKeys && strokeHitsPoint(stroke, point)
                                }
                                .forEach { stroke ->
                                    val key = stroke.id.takeIf { it > 0L }
                                        ?: PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L)).hashCode().toLong()
                                    erasedKeys += key
                                    pendingErasedStrokes[key] = stroke
                                }
                        }
                    }

                    addPoint(down.position)
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            cancelled = true
                            liveSegments = emptyMap()
                        } else if (pressed.isEmpty()) {
                            break
                        } else if (!cancelled) {
                            val change = pressed.first()
                            val y = change.position.y
                            // Keep the document moving while the pen reaches an edge. The next
                            // event is then mapped against the newly visible page.
                            if (y > size.height - 56f) {
                                listState.dispatchRawDelta(18f)
                            }
                            if (y < 56f) {
                                listState.dispatchRawDelta(-18f)
                            }
                            addPoint(change.position)
                            change.consume()
                        }
                    }

                    liveSegments = emptyMap()
                    if (!cancelled) {
                        if (activeTool == PdfInkTool.ERASER) {
                            pendingErasedStrokes.values.forEach(onStrokeErased)
                        } else {
                            segments.forEach { (page, points) ->
                                if (points.size >= 2) {
                                    onStrokeCommitted(
                                        PdfInkStroke(
                                            page = page,
                                            points = simplifyInkPoints(points),
                                            tool = activeTool,
                                            color = activeColor,
                                            width = if (activeTool == PdfInkTool.HIGHLIGHTER) 0.018f else 0.006f
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) {
        liveSegments.forEach { (page, points) ->
            if (activeTool != PdfInkTool.ERASER && points.size >= 2) {
                drawDocumentInkStroke(
                    PdfInkStroke(
                        page = page,
                        points = points,
                        tool = activeTool,
                        color = activeColor,
                        width = if (activeTool == PdfInkTool.HIGHLIGHTER) 0.018f else 0.006f
                    ),
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page }
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDocumentInkStroke(
    stroke: PdfInkStroke,
    item: androidx.compose.foundation.lazy.LazyListItemInfo?
) {
    if (item == null || stroke.points.isEmpty()) return
    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = item.offset + point.y * item.size
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val parsedColor = runCatching {
        Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(stroke.color)))
    }.getOrDefault(Color(0xFFD6C58D))
    drawPath(
        path = path,
        color = if (stroke.tool == PdfInkTool.HIGHLIGHTER) {
            parsedColor.copy(alpha = 0.34f)
        } else {
            parsedColor.copy(alpha = 0.94f)
        },
        style = Stroke(
            width = (stroke.width * size.width).coerceAtLeast(2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkStroke(stroke: PdfInkStroke) {
    if (stroke.points.isEmpty()) return
    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val parsedColor = runCatching {
        Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(stroke.color)))
    }.getOrDefault(Color(0xFFD6C58D))
    val color = if (stroke.tool == PdfInkTool.HIGHLIGHTER) {
        parsedColor.copy(alpha = 0.34f)
    } else {
        parsedColor.copy(alpha = 0.94f)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = (stroke.width * size.width).coerceAtLeast(2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun simplifyInkPoints(points: List<PdfInkPoint>): List<PdfInkPoint> {
    if (points.size <= 800) return points
    val step = (points.size / 800f).toInt().coerceAtLeast(2)
    return points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }
}

private fun bridgeLegacyCrossPageStrokes(strokes: List<PdfInkStroke>): List<PdfInkStroke> {
    if (strokes.size < 2) return strokes
    val bridged = strokes.toMutableList()
    val chronologicalIndices = strokes.indices.sortedBy { strokes[it].createdAt }
    chronologicalIndices.zipWithNext().forEach { (fromIndex, toIndex) ->
        val from = bridged[fromIndex]
        val to = bridged[toIndex]
        if (from.createdAt <= 0L || to.createdAt <= 0L ||
            kotlin.math.abs(to.createdAt - from.createdAt) > 1_500L ||
            from.tool != to.tool || from.color != to.color ||
            from.points.isEmpty() || to.points.isEmpty()
        ) return@forEach

        val fromPoint = from.points.last()
        val toPoint = to.points.first()
        if (kotlin.math.abs(fromPoint.x - toPoint.x) > 0.16f) return@forEach
        val boundaryX = ((fromPoint.x + toPoint.x) / 2f).coerceIn(0f, 1f)
        when {
            to.page == from.page + 1 && fromPoint.y >= 0.72f && toPoint.y <= 0.28f -> {
                bridged[fromIndex] = from.copy(
                    points = if (fromPoint.y >= 0.999f) from.points
                    else from.points + PdfInkPoint(boundaryX, 1f)
                )
                bridged[toIndex] = to.copy(
                    points = if (toPoint.y <= 0.001f) to.points
                    else listOf(PdfInkPoint(boundaryX, 0f)) + to.points
                )
            }
            to.page == from.page - 1 && fromPoint.y <= 0.28f && toPoint.y >= 0.72f -> {
                bridged[fromIndex] = from.copy(
                    points = if (fromPoint.y <= 0.001f) from.points
                    else from.points + PdfInkPoint(boundaryX, 0f)
                )
                bridged[toIndex] = to.copy(
                    points = if (toPoint.y >= 0.999f) to.points
                    else listOf(PdfInkPoint(boundaryX, 1f)) + to.points
                )
            }
        }
    }
    return bridged
}

private fun strokeHitsPoint(stroke: PdfInkStroke, point: PdfInkPoint): Boolean {
    val threshold = (stroke.width * 1.8f).coerceAtLeast(0.022f)
    if (stroke.points.size == 1) return distanceSquared(stroke.points.first(), point) <= threshold * threshold
    return stroke.points.zipWithNext().any { (start, end) ->
        distanceToSegmentSquared(point, start, end) <= threshold * threshold
    }
}

private fun distanceSquared(first: PdfInkPoint, second: PdfInkPoint): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}

private fun distanceToSegmentSquared(point: PdfInkPoint, start: PdfInkPoint, end: PdfInkPoint): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.000001f) return distanceSquared(point, start)
    val projection = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
    val t = projection.coerceIn(0f, 1f)
    return distanceSquared(
        point,
        PdfInkPoint(start.x + t * dx, start.y + t * dy)
    )
}

// ── PDF 目录缩略图 Sheet ──

@Composable
private fun PdfTocSheet(
    visible: Boolean,
    pageSource: BitmapPageSource?,
    pageCount: Int,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    /** 漫画的"话"分组（分组标题 → 首页页号）；为空时退化为纯页缩略图网格。 */
    chapterGroups: List<Pair<String, Int>> = emptyList(),
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    eInkModeEnabled: Boolean = false
) {
    if (!visible || pageCount <= 0) return

    val eInkMode = eInkModeEnabled || LocalEInkMode.current
    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    var pendingPage by remember { mutableStateOf<Int?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            if (eInkMode) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (eInkMode) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            pendingPage?.let { onPageSelected(it) }
            pendingPage = null
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 半透明遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.24f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    isClosing = true
                }
        )

        // Sheet 面板（70% 屏幕高度）
        LiquidGlassColumnSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pdf_toc),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.pdf_close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }

            Spacer(Modifier.height(16.dp))

            // 缩略图网格（3 列）
            val gridEntries = remember(pageCount, chapterGroups) {
                buildList {
                    val groupLabelsByPage = chapterGroups
                        .filter { (_, firstPage) -> firstPage in 0 until pageCount }
                        .associate { (label, firstPage) -> firstPage to label }
                    (0 until pageCount).forEach { pageIndex ->
                        groupLabelsByPage[pageIndex]?.let { label ->
                            add(PdfTocGridEntry.ChapterHeader(label, pageIndex))
                        }
                        add(PdfTocGridEntry.Page(pageIndex))
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    count = gridEntries.size,
                    key = { index ->
                        when (val entry = gridEntries[index]) {
                            is PdfTocGridEntry.ChapterHeader -> "header_${entry.firstPageIndex}"
                            is PdfTocGridEntry.Page -> "page_${entry.pageIndex}"
                        }
                    },
                    span = { index ->
                        if (gridEntries[index] is PdfTocGridEntry.ChapterHeader) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    }
                ) { index ->
                    when (val entry = gridEntries[index]) {
                        is PdfTocGridEntry.ChapterHeader -> ChapterHeaderLabel(entry.label)
                        is PdfTocGridEntry.Page -> PdfThumbnailItem(
                            pageSource = pageSource,
                            pageIndex = entry.pageIndex,
                            isCurrentPage = entry.pageIndex == currentPage,
                            isBookmarked = entry.pageIndex in bookmarkedPages,
                            onClick = {
                                pendingPage = entry.pageIndex
                                isClosing = true
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface PdfTocGridEntry {
    data class ChapterHeader(val label: String, val firstPageIndex: Int) : PdfTocGridEntry
    data class Page(val pageIndex: Int) : PdfTocGridEntry
}

@Composable
private fun ChapterHeaderLabel(label: String) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun PdfBookmarksSheet(
    visible: Boolean,
    bookmarks: List<Bookmark>,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
    eInkModeEnabled: Boolean = false
) {
    if (!visible) return
    val eInkMode = eInkModeEnabled || LocalEInkMode.current
    val sheetOffset = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { closing = true }

    LaunchedEffect(visible) {
        sheetOffset.snapTo(1f)
        if (eInkMode) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
    }
    LaunchedEffect(closing) {
        if (closing) {
            if (eInkMode) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.24f * (1f - sheetOffset.value)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { closing = true }
        )
        LiquidGlassColumnSheetContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            fallbackColor = AppColors.CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_bookmark),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.pdf_close),
                    onClick = { closing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }
            Spacer(Modifier.height(14.dp))
            if (bookmarks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_bookmarks),
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (bookmark.chapterIndex == currentPage) AppColors.Accent.copy(alpha = 0.12f)
                                    else AppColors.BgGray
                                )
                                .clickable {
                                    onPageSelected(bookmark.chapterIndex)
                                    closing = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Bookmark.filled, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bookmark.title.ifBlank { stringResource(R.string.reader_bookmark) },
                                    fontSize = 14.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.pdf_page_desc, bookmark.chapterIndex + 1),
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                            LiquidGlassIconButton(
                                imageVector = AppIcons.X,
                                contentDescription = stringResource(R.string.delete),
                                onClick = { onDelete(bookmark) },
                                size = 36.dp,
                                iconSize = 17.dp,
                                contentColor = Color(0xFFE85D5D),
                                normalContainerColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnailItem(
    pageSource: BitmapPageSource?,
    pageIndex: Int,
    isCurrentPage: Boolean,
    isBookmarked: Boolean,
    onClick: () -> Unit
) {
    val accentColor = AppColors.Accent
    var thumbnail by remember(pageSource, pageIndex) { mutableStateOf<Bitmap?>(null) }

    // Thumbnails are cached by the page source, so they are never recycled by callers.
    LaunchedEffect(pageSource, pageIndex) {
        thumbnail = pageSource?.renderThumbnail(pageIndex, THUMBNAIL_RENDER_WIDTH_PX)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isCurrentPage) Modifier.border(3.dp, accentColor, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .background(AppColors.BgGray)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            val renderedThumbnail = thumbnail
            if (renderedThumbnail != null) {
                Image(
                    bitmap = renderedThumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.pdf_page_desc, pageIndex + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                CircularProgressIndicator(
                    Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = AppColors.TextSecondary.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${pageIndex + 1}",
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            if (isBookmarked) {
                Icon(
                    imageVector = AppIcons.Bookmark.filled,
                    contentDescription = stringResource(R.string.pdf_bookmark),
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

