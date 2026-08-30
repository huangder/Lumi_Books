package com.huangder.lumibooks.ui.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.positionChanged
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.BookFileAccess
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.pdfconversion.PdfConversionEngine
import com.huangder.lumibooks.pdfconversion.PdfConversionState
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.ui.settings.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.Closeable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private class PdfRendererHolder(
    val descriptor: ParcelFileDescriptor,
    val renderer: PdfRenderer
) : Closeable {
    override fun close() {
        synchronized(renderer) { runCatching { renderer.close() } }
        runCatching { descriptor.close() }
    }
}

private enum class PdfMultiTouchMode {
    UNDECIDED,
    PAN,
    ZOOM
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
fun PdfViewerScreen(
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
    ReaderSystemBarStyle(
        backgroundColor = com.huangder.lumibooks.ui.theme.ReaderColors.Light.background,
        useDarkIcons = true
    )

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
    val readerBackProgress = ConfigurableBackHandler(
        enabled = !isAnySheetOpen,
        onBack = exitReader
    )
    var pendingReplaceAfterMineruSettings by remember { mutableStateOf(false) }
    var pendingManualReplace by remember { mutableStateOf(false) }
    var observedActiveConversion by remember { mutableStateOf(false) }
    var pendingModePage by remember { mutableStateOf<Int?>(null) }
    val eInkMode = LocalEInkMode.current || uiState.eInkModeEnabled
    val motionEnabled = LocalMotionEnabled.current
    val effectivePdfPageMode = if (eInkMode) "horizontal" else uiState.pdfPageMode
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    LaunchedEffect(ttsState.errorMessage) {
        val message = ttsState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearTtsError()
    }
    val pdfGlassContentScrim = AppColors.WindowBg.copy(alpha = 0.18f)
    val pdfGlassBackdrop = rememberLayerBackdrop()
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
        Box(Modifier.fillMaxSize().background(com.huangder.lumibooks.ui.theme.ReaderColors.Light.background), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

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
        ((book?.readingProgress ?: 0f) * pageCount)
            .toInt()
            .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
    val verticalPage by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            (if (offset > 200) first + 1 else first).coerceIn(0, pageCount - 1)
        }
    }
    val isHorizontal = effectivePdfPageMode == "horizontal"
    val currentPage = if (isHorizontal) pagerState.currentPage else verticalPage

    LaunchedEffect(isHorizontal, pendingModePage) {
        val targetPage = pendingModePage ?: return@LaunchedEffect
        if (isHorizontal) {
            pagerState.scrollToPage(targetPage)
        } else {
            listState.scrollToItem(targetPage)
        }
        pendingModePage = null
    }
    LaunchedEffect(
        bookId,
        pageCount,
        isHorizontal,
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
            if (isHorizontal) {
                if (eInkMode) pagerState.scrollToPage(targetPage) else pagerState.animateScrollToPage(targetPage)
            } else {
                if (eInkMode) listState.scrollToItem(targetPage) else listState.animateScrollToItem(targetPage)
            }
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

    // 进度保存（节流：每翻 3 页才保存一次）
    var lastSavedPage by remember { mutableStateOf(-1) }
    LaunchedEffect(currentPage) {
        if (pageCount > 0 && kotlin.math.abs(currentPage - lastSavedPage) >= 3) {
            lastSavedPage = currentPage
            viewModel.saveProgressDirect(bookId, currentPage.toFloat() / pageCount)
        }
    }

    // 缩放状态
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val shouldHandleVolumePageTurn = uiState.volumeKeyPageTurnEnabled &&
        !showMenu &&
        !showPdfToc &&
        !annotationMode &&
        conversionSheet == null &&
        scale <= 1.01f

    DisposableEffect(
        activity,
        shouldHandleVolumePageTurn,
        isHorizontal,
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
                if (isHorizontal) {
                    if (eInkMode) pagerState.scrollToPage(targetPage) else pagerState.animateScrollToPage(targetPage)
                } else {
                    if (eInkMode) listState.scrollToItem(targetPage) else listState.animateScrollToItem(targetPage)
                }
            }
        }
        activity.readerVolumeKeyHandler = handler
        onDispose {
            if (activity.readerVolumeKeyHandler === handler) {
                activity.readerVolumeKeyHandler = null
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, isHorizontal) {
        if (isHorizontal) {
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

    ProvideLiquidGlassBackdrop(pdfGlassBackdrop.takeIf { isLiquidGlass }) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (motionEnabled) {
                    scaleX = 1f - readerBackProgress * 0.04f
                    scaleY = 1f - readerBackProgress * 0.04f
                    translationX = readerBackProgress * 48.dp.toPx()
                }
                alpha = 1f - readerBackProgress * 0.08f
            }
            .background(
                if (isHorizontal) AppColors.WindowBg
                else com.huangder.lumibooks.ui.theme.ReaderColors.Light.background
            )
    ) {
        // PDF 页面（上下连续滚动 / 相册式左右分页）
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
                    if (scale <= 1.01f) {
                        if (annotationMode && inkColorExpanded) {
                            inkColorExpanded = false
                        } else {
                            showMenu = !showMenu
                        }
                    }
                }
        ) {
            if (isHorizontal) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !annotationMode && scale <= 1.01f,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pageIndex) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var pointersPressed: Boolean
                                    var transformGesture = false
                                    var gestureMode = PdfMultiTouchMode.UNDECIDED
                                    var documentPanX = 0f
                                    var pendingPan = Offset.Zero
                                    var pendingZoom = 1f
                                    do {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) transformGesture = true
                                        if (transformGesture || scale > 1.01f) {
                                            val pan = event.calculatePan()
                                            val zoom = event.calculateZoom()
                                            pendingPan += pan
                                            pendingZoom *= zoom
                                            if (gestureMode == PdfMultiTouchMode.UNDECIDED) {
                                                gestureMode = when {
                                                    scale > 1.01f -> PdfMultiTouchMode.ZOOM
                                                    kotlin.math.abs(pendingZoom - 1f) >= 0.035f -> PdfMultiTouchMode.ZOOM
                                                    pendingPan.getDistance() >= viewConfiguration.touchSlop -> PdfMultiTouchMode.PAN
                                                    else -> PdfMultiTouchMode.UNDECIDED
                                                }
                                            }
                                            if (gestureMode == PdfMultiTouchMode.PAN) {
                                                documentPanX += pendingPan.x
                                                pagerState.dispatchRawDelta(-pendingPan.x)
                                                pendingPan = Offset.Zero
                                                pendingZoom = 1f
                                            } else if (gestureMode == PdfMultiTouchMode.ZOOM) {
                                                val newScale = (scale * pendingZoom).coerceIn(1f, 5f)
                                                val maxOffsetX = (newScale - 1f) * size.width / 2f
                                                val maxOffsetY = (newScale - 1f) * size.height / 2f
                                                scale = newScale
                                                offsetX = (offsetX + pendingPan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                                offsetY = (offsetY + pendingPan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                                pendingPan = Offset.Zero
                                                pendingZoom = 1f
                                            }
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }
                                        pointersPressed = event.changes.any { it.pressed }
                                    } while (pointersPressed)
                                    if (transformGesture && gestureMode == PdfMultiTouchMode.PAN && scale <= 1.01f) {
                                        val targetPage = when {
                                            documentPanX < -48f -> pageIndex + 1
                                            documentPanX > 48f -> pageIndex - 1
                                            else -> pageIndex
                                        }.coerceIn(0, pageCount - 1)
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
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
                            filePath = filePath,
                            pageIndex = pageIndex,
                            fitToViewport = true,
                            annotationEnabled = true,
                            annotationInteractive = annotationMode,
                            activeInkTool = selectedInkTool,
                            activeInkColor = ReaderHighlightPalette.getOrNull(selectedInkColorSlot)?.first
                                ?: DefaultReaderHighlightColor,
                            existingStrokes = inkStrokes,
                            onStrokeCommitted = viewModel::addPdfInkStroke,
                            onStrokeErased = viewModel::deletePdfInkStroke
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isHorizontal, annotationMode) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var multiTouch = false
                                var gestureMode = PdfMultiTouchMode.UNDECIDED
                                var pendingPan = Offset.Zero
                                var pendingZoom = 1f
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed >= 2) {
                                        multiTouch = true
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        pendingPan += pan
                                        pendingZoom *= zoom
                                        if (gestureMode == PdfMultiTouchMode.UNDECIDED) {
                                            gestureMode = when {
                                                scale > 1.01f -> PdfMultiTouchMode.ZOOM
                                                kotlin.math.abs(pendingZoom - 1f) >= 0.035f -> PdfMultiTouchMode.ZOOM
                                                pendingPan.getDistance() >= viewConfiguration.touchSlop -> PdfMultiTouchMode.PAN
                                                else -> PdfMultiTouchMode.UNDECIDED
                                            }
                                        }
                                        if (gestureMode == PdfMultiTouchMode.ZOOM) {
                                            val newScale = (scale * pendingZoom).coerceIn(1f, 5f)
                                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                                            scale = newScale
                                            offsetX = (offsetX + pendingPan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + pendingPan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            if (newScale <= 1.01f) {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            pendingPan = Offset.Zero
                                            pendingZoom = 1f
                                        } else if (gestureMode == PdfMultiTouchMode.PAN) {
                                            listState.dispatchRawDelta(-pendingPan.y)
                                            pendingPan = Offset.Zero
                                            pendingZoom = 1f
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    } else if (pressed == 0) {
                                        break
                                    } else if (multiTouch) {
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
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
                        userScrollEnabled = scale <= 1.01f,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pageCount) {
                            PdfPageItem(
                                filePath = filePath,
                                pageIndex = it,
                                fitToViewport = false,
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
                pageMode = effectivePdfPageMode,
                eInkModeEnabled = eInkMode,
                glassContentScrimColor = pdfGlassContentScrim,
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
                    chapterTitle = book?.title ?: "",
                    chapterProgress = if (pageCount > 0) {
                        ((currentPage + 1).toFloat() / pageCount * 100f).coerceIn(0f, 100f)
                    } else {
                        0f
                    },
                    conversionState = conversionState,
                    glassContentScrimColor = pdfGlassContentScrim,
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
                sleepTimerRemainingMs = ttsState.sleepTimerRemainingMs,
                onPlayPause = viewModel::toggleTtsPlayPause,
                onStop = viewModel::stopTts,
                onSkipForward = viewModel::ttsSkipForward,
                onSkipBackward = viewModel::ttsSkipBackward,
                onRateChange = viewModel::setTtsSpeechRate,
                onSetSleepTimer = viewModel::setSleepTimer,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
                readerBackgroundColor = AppColors.WindowBg,
                readerContentColor = AppColors.TextPrimary
            )
        }

        // ── PDF 目录缩略图 Sheet ──
        PdfTocSheet(
            visible = showPdfToc,
            filePath = filePath,
            pageCount = pageCount,
            currentPage = currentPage,
            bookmarkedPages = bookmarkedPages,
            onPageSelected = { page ->
                scope.launch {
                    if (isHorizontal) pagerState.scrollToPage(page) else listState.scrollToItem(page)
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
                    if (isHorizontal) pagerState.scrollToPage(page) else listState.scrollToItem(page)
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

// ── 顶部栏（与 EPUB ReaderTopBar 一致，增加 PDF 专属页码显示）──
@Composable
private fun PdfTopBar(
    title: String,
    currentPage: Int,
    pageCount: Int,
    isBookmarked: Boolean = false,
    pageMode: String,
    eInkModeEnabled: Boolean = false,
    glassContentScrimColor: Color,
    isTtsActive: Boolean,
    onBack: () -> Unit,
    onPageModeToggle: () -> Unit,
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
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.pdf_back), tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                // 页码徽章：半透明黑底 + 圆角矩形
                LiquidGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    fallbackColor = Color.Black.copy(alpha = 0.35f),
                    contentScrimColor = glassContentScrimColor,
                    modifier = Modifier
                        .height(28.dp)
                ) {
                    Text(
                        text = "${currentPage + 1} / $pageCount",
                        fontSize = 12.sp,
                        color = if (isLiquidGlass) AppColors.TextPrimary else Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
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
                        val isHorizontal = pageMode == "horizontal"
                        Icon(
                            if (isHorizontal) Icons.Default.ViewCarousel else Icons.Default.ViewAgenda,
                            contentDescription = stringResource(
                                if (isHorizontal) R.string.pdf_switch_to_vertical else R.string.pdf_switch_to_horizontal
                            ),
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                LiquidGlassSurface(
                    shape = CircleShape,
                    fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
                    contentScrimColor = glassContentScrimColor,
                    modifier = Modifier.size(36.dp),
                    onClick = onTtsToggle,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = stringResource(R.string.tts_listen),
                        tint = if (isTtsActive) AppColors.Accent else AppColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
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
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
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
    conversionState: PdfConversionState,
    glassContentScrimColor: Color,
    onConversionClick: () -> Unit,
    onCatalogClick: () -> Unit,
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
        PdfConversionCapsule(
            conversionState = conversionState,
            glassContentScrimColor = glassContentScrimColor,
            onClick = onConversionClick
        )
        // 目录胶囊
        PdfCatalogCapsule(
            title = chapterTitle,
            progress = chapterProgress,
            glassContentScrimColor = glassContentScrimColor,
            onClick = onCatalogClick
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PdfActionCapsule(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.pdf_annotation_tool),
                modifier = Modifier.weight(1f),
                onClick = onAnnotationClick
            )
            PdfActionCapsule(
                icon = Icons.Default.Bookmark,
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
                    Icons.Default.AutoStories,
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
                                icon = Icons.Outlined.PhoneAndroid,
                                title = stringResource(R.string.pdf_convert_local_title),
                                description = stringResource(R.string.pdf_convert_sheet_body),
                                onClick = {
                                    onStartLocal(currentSheet.replaceExisting)
                                    onSheetChange(PdfConversionSheet.Progress)
                                }
                            )
                            PdfConversionMethodButton(
                                icon = Icons.Outlined.CloudUpload,
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
                                icon = Icons.Outlined.FileOpen,
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
                                icon = Icons.Outlined.Public,
                                title = stringResource(R.string.mineru_manual_open_website),
                                description = stringResource(R.string.pdf_convert_mineru_manual_website_hint),
                                cloud = true,
                                onClick = onOpenMineruWebsite
                            )
                            PdfConversionMethodButton(
                                icon = Icons.Outlined.FileOpen,
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
    glassContentScrimColor: Color,
    onClick: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = AppColors.BgGray,
        contentScrimColor = glassContentScrimColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        onClick = onClick,
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
                        .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(AppColors.Accent.copy(alpha = 0.82f))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.Accent.copy(alpha = 0.8f))
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val foreground = if (isLiquidGlass || progress <= 5f) AppColors.TextPrimary else Color.White
            Icon(Icons.Default.Bookmark, null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pdf_toc), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Spacer(Modifier.weight(1f))
            Text(
                formatReadingProgressPercent(progress),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (!isLiquidGlass && progress > 70f) Color.White.copy(alpha = 0.9f)
                else AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PdfActionCapsule(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = AppColors.BgGray,
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
            Icon(icon, null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = AppColors.TextPrimary)
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
        PdfInkTool.PEN -> Icons.Default.Edit
        PdfInkTool.HIGHLIGHTER -> Icons.Default.Brush
        PdfInkTool.ERASER -> Icons.Default.Delete
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
            contentDescription = tool.name,
            tint = if (selected) AppColors.Accent else AppColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── PDF 页面渲染（每个页面独立打开文件，避免并发冲突）──

@Composable
private fun PdfPageItem(
    filePath: String,
    pageIndex: Int,
    fitToViewport: Boolean,
    annotationEnabled: Boolean,
    annotationInteractive: Boolean,
    activeInkTool: PdfInkTool,
    activeInkColor: String,
    existingStrokes: List<PdfInkStroke>,
    onStrokeCommitted: (PdfInkStroke) -> Unit,
    onStrokeErased: (PdfInkStroke) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(filePath, pageIndex) { bitmap = renderPdfPage(context, filePath, pageIndex) }
    DisposableEffect(bitmap) {
        val renderedBitmap = bitmap
        onDispose {
            if (renderedBitmap != null && !renderedBitmap.isRecycled) renderedBitmap.recycle()
        }
    }

    Box(
        modifier = if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val renderedPage = bitmap
        if (renderedPage != null) {
            Image(
                bitmap = renderedPage.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page_desc, pageIndex + 1),
                modifier = if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                contentScale = if (fitToViewport) ContentScale.Fit else ContentScale.FillWidth
            )
        } else {
            Box(
                if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(600.dp),
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
                                    pointsErased += stroke.id.takeIf { it > 0L }
                                        ?: PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L)).hashCode().toLong()
                                    onStrokeErased(stroke)
                                }
                            }
                        }
                    }

                    val completed = points.toList()
                    livePoints = emptyList()
                    if (!cancelled && activeTool != PdfInkTool.ERASER && completed.size >= 2) {
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
                                    onStrokeErased(stroke)
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
                    if (!cancelled && activeTool != PdfInkTool.ERASER) {
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

private suspend fun renderPdfPage(context: Context, filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
    var bitmap: Bitmap? = null
    try {
        BookFileAccess.openDescriptor(context, filePath).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val scale = 1.5f
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
                        result.eraseColor(android.graphics.Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                    android.util.Log.d("PDF", "Rendered page $pageIndex: ${width}x$height")
                }
            }
        }
        if (!currentCoroutineContext().isActive) {
            bitmap?.recycle()
            null
        } else {
            bitmap
        }
    } catch (e: CancellationException) {
        bitmap?.recycle()
        throw e
    } catch (e: Exception) {
        bitmap?.recycle()
        android.util.Log.e("PDF", "Failed page $pageIndex: ${e.message}")
        null
    }
}

// ── PDF 目录缩略图 Sheet ──

@Composable
private fun PdfTocSheet(
    visible: Boolean,
    filePath: String,
    pageCount: Int,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    eInkModeEnabled: Boolean = false
) {
    if (!visible || filePath.isEmpty() || pageCount <= 0) return

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

    // 单例 PdfRenderer，Sheet 可见期间存活
    val context = LocalContext.current
    var rendererHolder by remember { mutableStateOf<PdfRendererHolder?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            rendererHolder = withContext(Dispatchers.IO) {
                try {
                    val fd = BookFileAccess.openDescriptor(context, filePath)
                    try {
                        PdfRendererHolder(fd, PdfRenderer(fd))
                    } catch (e: Exception) {
                        fd.close()
                        throw e
                    }
                } catch (_: Exception) { null }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { rendererHolder?.close() }
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
                    imageVector = Icons.Default.Close,
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(pageCount) { pageIdx ->
                    PdfThumbnailItem(
                        renderer = rendererHolder?.renderer,
                        pageIndex = pageIdx,
                        isCurrentPage = pageIdx == currentPage,
                        isBookmarked = pageIdx in bookmarkedPages,
                        onClick = {
                            pendingPage = pageIdx
                            isClosing = true
                        }
                    )
                }
            }
        }
    }
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
                    imageVector = Icons.Default.Close,
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
                            Icon(Icons.Default.Bookmark, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
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
                                imageVector = Icons.Default.Close,
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
    renderer: PdfRenderer?,
    pageIndex: Int,
    isCurrentPage: Boolean,
    isBookmarked: Boolean,
    onClick: () -> Unit
) {
    val accentColor = AppColors.Accent
    var thumbnail by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, renderer) {
        if (renderer != null) {
            thumbnail = renderPdfThumbnail(renderer, pageIndex)
        }
    }
    DisposableEffect(thumbnail) {
        val renderedThumbnail = thumbnail
        onDispose {
            if (renderedThumbnail != null && !renderedThumbnail.isRecycled) renderedThumbnail.recycle()
        }
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
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.pdf_bookmark),
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

private suspend fun renderPdfThumbnail(renderer: PdfRenderer, pageIndex: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val scale = 0.15f
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
                        result.eraseColor(android.graphics.Color.WHITE)
                        page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            if (!currentCoroutineContext().isActive) {
                bitmap?.recycle()
                null
            } else {
                bitmap
            }
        } catch (e: CancellationException) {
            bitmap?.recycle()
            throw e
        } catch (_: Exception) {
            bitmap?.recycle()
            null
        }
    }
