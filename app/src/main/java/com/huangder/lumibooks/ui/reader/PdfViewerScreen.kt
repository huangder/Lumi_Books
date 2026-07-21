package com.huangder.lumibooks.ui.reader

import android.Manifest
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.core.content.ContextCompat
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.ReaderSystemBarStyle
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.pdfconversion.PdfConversionEngine
import com.huangder.lumibooks.pdfconversion.PdfConversionState
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.ui.settings.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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
    val bookmarks by viewModel.bookmarks.collectAsState()
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
    var conversionSheet by remember { mutableStateOf<PdfConversionSheet?>(null) }
    var pendingReplaceAfterMineruSettings by remember { mutableStateOf(false) }
    var pendingManualReplace by remember { mutableStateOf(false) }
    var observedActiveConversion by remember { mutableStateOf(false) }
    var pendingModePage by remember { mutableStateOf<Int?>(null) }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    val isHorizontal = uiState.pdfPageMode == "horizontal"
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

    // 当前页是否已收藏（PDF 每页 = 一个 chapterIndex）
    val isCurrentPageBookmarked = bookmarks.any { it.chapterIndex == currentPage }

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
                    pagerState.animateScrollToPage(targetPage)
                } else {
                    listState.animateScrollToItem(targetPage)
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
    val menuOffset = remember { Animatable(60f) }
    LaunchedEffect(showMenu) {
        if (showMenu) {
            coroutineScope {
                launch { menuAlpha.animateTo(1f, tween(300)) }
                launch { menuOffset.animateTo(0f, tween(300, easing = AppEasing.Smooth)) }
            }
        } else {
            coroutineScope {
                launch { menuAlpha.animateTo(0f, tween(200)) }
                launch { menuOffset.animateTo(60f, tween(200, easing = AppEasing.Accelerate)) }
            }
        }
    }

    ProvideLiquidGlassBackdrop(pdfGlassBackdrop.takeIf { isLiquidGlass }) {
    Box(
        Modifier
            .fillMaxSize()
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
                ) { if (scale <= 1.01f) showMenu = !showMenu }
        ) {
            if (isHorizontal) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = scale <= 1.01f,
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
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) transformGesture = true
                                        if (transformGesture || scale > 1.01f) {
                                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                            val pan = event.calculatePan()
                                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                                            scale = newScale
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            if (newScale <= 1.01f) {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }
                                        pointersPressed = event.changes.any { it.pressed }
                                    } while (pointersPressed)
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
                            fitToViewport = true
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                val maxOffsetX = (newScale - 1f) * size.width / 2f
                                val maxOffsetY = (newScale - 1f) * size.height / 2f
                                scale = newScale
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                if (newScale <= 1.01f) {
                                    offsetX = 0f
                                    offsetY = 0f
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(pageCount) {
                            PdfPageItem(filePath = filePath, pageIndex = it, fitToViewport = false)
                        }
                    }
                }
            }
        }

        // ── 顶部栏（淡入淡出）──
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PdfTopBar(
                title = book?.title ?: "",
                currentPage = currentPage,
                pageCount = pageCount,
                isBookmarked = isCurrentPageBookmarked,
                pageMode = uiState.pdfPageMode,
                onBack = onNavigateBack,
                onPageModeToggle = {
                    pendingModePage = currentPage
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    viewModel.togglePdfPageMode()
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

        // ── 底部胶囊菜单（同时淡入+上移）──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = menuAlpha.value; translationY = menuOffset.value }
        ) {
            PdfBottomMenu(
                chapterTitle = book?.title ?: "",
                chapterProgress = if (pageCount > 0) {
                    ((currentPage + 1).toFloat() / pageCount * 100f).coerceIn(0f, 100f)
                } else {
                    0f
                },
                conversionState = conversionState,
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
                modifier = Modifier.align(Alignment.BottomCenter)
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
            onDismiss = { showPdfToc = false }
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
                    mineruSettingsLauncher.launch(
                        android.content.Intent(context, DetailActivity::class.java)
                            .putExtra("category", "mineru")
                    )
                },
                onOpenMineruWebsite = {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.huangder.lumibooks.mineru.MineruConfig.MANUAL_WEB_URL)
                        )
                    )
                },
                onPickManualResult = { replaceExisting ->
                    pendingManualReplace = replaceExisting
                    manualResultPicker.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "text/markdown",
                            "text/plain",
                            "application/octet-stream"
                        )
                    )
                },
                onOpenExisting = onOpenBook,
                onCancelConversion = viewModel::cancelPdfConversion,
                onStayPdf = viewModel::consumePdfConversionResult,
                onOpenConverted = { convertedBookId ->
                    viewModel.consumePdfConversionResult()
                    onOpenBook(convertedBookId)
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
    onBack: () -> Unit,
    onPageModeToggle: () -> Unit,
    onBookmarkToggle: () -> Unit = {}
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
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
                .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：返回按钮 + 页码
            LiquidGlassSurface(
                shape = CircleShape,
                fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
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

            // 中间：书名
            Text(
                text = title,
                fontSize = 12.sp,
                color = AppColors.TextSecondary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            // 阅读方向按钮：只随菜单出现，位于书签左侧。
            LiquidGlassSurface(
                shape = CircleShape,
                fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
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
            Spacer(Modifier.width(8.dp))

            // 右侧：书签按钮（与 EPUB ReaderTopBar 完全一致）
            LiquidGlassSurface(
                shape = CircleShape,
                fallbackColor = AppColors.BgGray.copy(alpha = 0.8f),
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

// ── 底部胶囊菜单 ──
@Composable
private fun PdfBottomMenu(
    chapterTitle: String,
    chapterProgress: Float,
    conversionState: PdfConversionState,
    onConversionClick: () -> Unit,
    onCatalogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PdfConversionCapsule(conversionState = conversionState, onClick = onConversionClick)
        // 目录胶囊
        PdfCatalogCapsule(title = chapterTitle, progress = chapterProgress, onClick = onCatalogClick)
    }
}

@Composable
private fun PdfConversionCapsule(conversionState: PdfConversionState, onClick: () -> Unit) {
    val running = conversionState as? PdfConversionState.Running
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = AppColors.BgGray,
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
    val offset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        offset.snapTo(1f)
        offset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }

    fun closeThen(action: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            offset.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .materialBottomSheetMotion(offset.value, predictiveBackProgress)
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    AppColors.CardBg,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
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
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 5 })
                        .togetherWith(
                            fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 }
                        )
                        .using(SizeTransform(clip = true))
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
        fontFamily = KaiTi,
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
        fontFamily = KaiTi,
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
private fun PdfCatalogCapsule(title: String, progress: Float, onClick: () -> Unit) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = AppColors.BgGray,
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

// ── PDF 页面渲染（每个页面独立打开文件，避免并发冲突）──

@Composable
private fun PdfPageItem(filePath: String, pageIndex: Int, fitToViewport: Boolean) {
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(filePath, pageIndex) { bitmap = renderPdfPage(filePath, pageIndex) }
    DisposableEffect(bitmap) {
        val renderedBitmap = bitmap
        onDispose {
            if (renderedBitmap != null && !renderedBitmap.isRecycled) renderedBitmap.recycle()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.pdf_page_desc, pageIndex + 1),
            modifier = if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            contentScale = if (fitToViewport) ContentScale.Fit else ContentScale.FillWidth
        )
    } else {
        Box(
            if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(600.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.TextSecondary.copy(alpha = 0.4f))
        }
    }
}

private suspend fun renderPdfPage(filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
    var bitmap: Bitmap? = null
    try {
        ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
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
    onDismiss: () -> Unit
) {
    if (!visible || filePath.isEmpty() || pageCount <= 0) return

    val sheetOffset = remember { Animatable(1f) }
    var isClosing by remember { mutableStateOf(false) }
    var pendingPage by remember { mutableStateOf<Int?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            pendingPage?.let { onPageSelected(it) }
            pendingPage = null
            onDismiss()
        }
    }

    // 单例 PdfRenderer，Sheet 可见期间存活
    var rendererHolder by remember { mutableStateOf<PdfRendererHolder?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            rendererHolder = withContext(Dispatchers.IO) {
                try {
                    val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
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
                .background(AppColors.Scrim.copy(alpha = 0.24f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    isClosing = true
                }
        )

        // Sheet 面板（70% 屏幕高度）
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress)
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pdf_toc),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.BgGray)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            isClosing = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.pdf_close),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 缩略图网格（3 列）
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
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
