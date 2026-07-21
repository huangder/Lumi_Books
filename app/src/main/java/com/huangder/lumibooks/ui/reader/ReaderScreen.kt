package com.huangder.lumibooks.ui.reader

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import com.huangder.lumibooks.ui.components.ReaderSystemBarStyle
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ReaderPageDirection
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.tts.TtsPlaybackState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private data class ReaderLinkLocation(
    val chapterIndex: Int,
    val pageIndex: Int
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReaderScreen(bookId: String, onNavigateBack: () -> Unit, onPageReady: () -> Unit = {}, onLoadingComplete: () -> Unit = {}, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val readerScreenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx().toInt()
    }

    // 字号拖拽去抖：避免 PillSlider 拖拽时每秒触发 20-50 次重排
    var debouncedFontSize by remember { mutableFloatStateOf(uiState.fontSize) }
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.fontSize }
            .debounce(200)
            .collect { debouncedFontSize = it }
    }

    // ReadView 引用
    val readViewRef = remember { mutableStateOf<ReadView?>(null) }

    val startTtsFromCurrentPage: () -> Unit = {
        val readView = readViewRef.value
        if (readView == null) {
            Toast.makeText(context, R.string.tts_page_not_ready, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.startTts(readView::getTtsPageContent)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 通知权限被拒绝时，Android 仍允许前台媒体播放，只是不展示普通通知。
        startTtsFromCurrentPage()
    }
    val requestTtsStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTtsFromCurrentPage()
        }
    }

    LaunchedEffect(bookId) {
        viewModel.ttsPageTurnRequests.collect { request ->
            if (request.bookId != bookId) return@collect
            var readView = readViewRef.value
            repeat(60) {
                if (readView != null) return@repeat
                kotlinx.coroutines.delay(16L)
                readView = readViewRef.value
            }
            val activeReadView = readView ?: return@collect
            val current = activeReadView.getCurrentLocation()
            val target = request.location.chapterIndex to request.location.pageIndex
            if (current == target) return@collect

            val movedWithAnimation = when (target) {
                activeReadView.getNextPageLocation() -> activeReadView.turnToNextPage()
                activeReadView.getPrevPageLocation() -> activeReadView.turnToPreviousPage()
                else -> false
            }
            if (!movedWithAnimation) {
                activeReadView.jumpToChapter(target.first, target.second)
            }
        }
    }

    // MainActivity 引用（用于注册 ActionMode 拦截回调）
    val activity = context as? MainActivity

    // TOC 跳转标记（区分用户点击 TOC 和正常翻页带来的章节变化）
    val isTocJump = remember { mutableStateOf(false) }

    // 亮度控制：保存系统原始亮度，退出时恢复
    val window = (context as? android.app.Activity)?.window
    val savedBrightness = remember { mutableFloatStateOf(-1f) }

    DisposableEffect(Unit) {
        activity?.isInReaderScreen = true
        // 保存系统原始亮度
        savedBrightness.floatValue = window?.attributes?.screenBrightness ?: -1f
        onDispose {
            activity?.isInReaderScreen = false
            viewModel.saveAndPause()
            viewModel.clearError()
            // 恢复系统亮度
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = savedBrightness.floatValue
                w.attributes = attrs
            }
        }
    }

    // 应用阅读器亮度
    SideEffect {
        window?.let { w ->
            val targetBrightness = uiState.brightness
            val attrs = w.attributes
            attrs.screenBrightness = if (targetBrightness < 0f) {
                savedBrightness.floatValue  // 跟随系统
            } else {
                targetBrightness.coerceIn(0.01f, 1f)  // 自定义亮度，最低 1% 防全黑
            }
            w.attributes = attrs
        }
    }

    // 生命周期感知：进入后台暂停计时，回到前台恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听 loading 状态，完成后通知 NavGraph 关闭过渡页
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) onLoadingComplete()
    }

    LaunchedEffect(uiState.ttsErrorMessage) {
        val message = uiState.ttsErrorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearTtsError()
    }

    // 恢复阅读进度：pendingPageFraction > 0 时跳转到目标页
    LaunchedEffect(uiState.pageReady, uiState.pendingPageFraction) {
        if (uiState.pageReady && uiState.pendingPageFraction > 0f && readViewRef.value != null) {
            val totalPages = readViewRef.value!!.getChapterPageCount(uiState.currentChapterIndex)
            if (totalPages > 0) {
                val targetPage = (totalPages * uiState.pendingPageFraction).toInt()
                    .coerceIn(0, totalPages - 1)
                if (targetPage > 0) {
                    readViewRef.value!!.jumpToChapter(uiState.currentChapterIndex, targetPage)
                }
                viewModel.clearPendingPageFraction()
            }
        }
    }

    var showNotesList by remember { mutableStateOf(false) }
    var linkReturnLocation by remember(bookId) { mutableStateOf<ReaderLinkLocation?>(null) }
    var linkReturnToken by remember(bookId) { mutableStateOf(0) }
    var linkNavigationJob by remember(bookId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 每次书内链接跳转成功后重新计时，30 秒后自动隐藏原页返回按钮。
    LaunchedEffect(linkReturnLocation, linkReturnToken) {
        if (linkReturnLocation != null) {
            val activeToken = linkReturnToken
            kotlinx.coroutines.delay(30_000L)
            if (activeToken == linkReturnToken) {
                linkReturnLocation = null
            }
        }
    }

    // 🔥 原生选择 ActionMode 回调 → 等待笔记输入
    var pendingSelection by remember { mutableStateOf<PendingSelection?>(null) }
    var showNoteInput by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

    // 自定义选择菜单状态（null = 不显示）
    var selectionState by remember { mutableStateOf<SelectionState?>(null) }
    // 手柄拖拽中：true → 菜单立即隐藏；false → 以新坐标重新弹出
    var isSelectionDragging by remember { mutableStateOf(false) }
    // 每次拖拽结束后自增，触发 SelectionMenuOverlay 重置入场动画
    var menuReappearKey by remember { mutableStateOf(0) }
    // 高亮颜色选择器：true → 菜单从操作按钮切换为6色圆点
    var showHighlightColorPicker by remember { mutableStateOf(false) }
    // 编辑笔记模式：非null时打开 NoteInputSheet 预填原笔记文字
    var editingNote by remember { mutableStateOf<com.huangder.lumibooks.domain.model.Note?>(null) }

    // 拖拽检测：SpanWatcher + 防抖重弹（在 onSelectionStarted 中延迟注册）
    val dragHandler = remember { Handler(Looper.getMainLooper()) }
    var dragHideRunnable by remember { mutableStateOf<Runnable?>(null) }
    var dragWatcher by remember { mutableStateOf<SpanWatcher?>(null) }

    // TOC 跳转：当 currentChapterIndex 变化且是 TOC 触发时，跳转 ReadView
    LaunchedEffect(uiState.currentChapterIndex) {
        if (isTocJump.value && readViewRef.value != null) {
            isTocJump.value = false
            readViewRef.value?.jumpToChapter(uiState.currentChapterIndex)
        }
    }

    var showToc by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }

    // 搜索状态
    var showSearch by remember { mutableStateOf(false) }

    // 请求关闭状态（用于触发退出动画）
    var requestCloseNotesList by remember { mutableStateOf(false) }
    var requestCloseNoteInput by remember { mutableStateOf(false) }
    var requestCloseToc by remember { mutableStateOf(false) }
    var requestCloseTheme by remember { mutableStateOf(false) }
    var requestCloseAdvanced by remember { mutableStateOf(false) }
    var requestCloseSearch by remember { mutableStateOf(false) }

    // 处理返回键：触发退出动画，而不是直接关闭
    val isAnySheetOpen = showNotesList || showNoteInput || showToc || showThemeSheet || showAdvancedSheet || showSearch
    val shouldHandleVolumePageTurn = uiState.volumeKeyPageTurnEnabled &&
        !uiState.isMenuVisible &&
        !isAnySheetOpen &&
        selectionState == null

    DisposableEffect(
        activity,
        shouldHandleVolumePageTurn
    ) {
        if (!shouldHandleVolumePageTurn || activity == null) {
            return@DisposableEffect onDispose { }
        }

        val handler: (ReaderPageDirection) -> Unit = { direction ->
            when (direction) {
                ReaderPageDirection.PREVIOUS -> readViewRef.value?.turnToPreviousPage()
                ReaderPageDirection.NEXT -> readViewRef.value?.turnToNextPage()
            }
        }
        activity.readerVolumeKeyHandler = handler
        onDispose {
            if (activity.readerVolumeKeyHandler === handler) {
                activity.readerVolumeKeyHandler = null
            }
        }
    }

    val returnToLinkedSource = {
        linkReturnLocation?.let { source ->
            linkReturnLocation = null
            readViewRef.value?.jumpToChapter(source.chapterIndex, source.pageIndex)
        }
        Unit
    }
    ConfigurableBackHandler(enabled = !isAnySheetOpen && linkReturnLocation != null) {
        returnToLinkedSource()
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ReaderViewModel.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val selectedCustomBackground = uiState.customReaderBackgrounds.firstOrNull {
        it.selectionKey == uiState.readerBackgroundSelection
    }
    val readerBackgroundColorInt = when {
        selectedCustomBackground?.type == ReaderBackgroundType.COLOR ->
            runCatching { android.graphics.Color.parseColor(selectedCustomBackground.value) }
                .getOrDefault(0xFFFBFBFC.toInt())
        uiState.readerBackgroundSelection == "night" -> 0xFF1a1a1a.toInt()
        uiState.readerBackgroundSelection == "sepia" -> 0xFFf5e6d3.toInt()
        uiState.readerBackgroundSelection == "green" -> 0xFFe8f5e9.toInt()
        else -> 0xFFFBFBFC.toInt()
    }
    val readerBackgroundImagePath = selectedCustomBackground
        ?.takeIf { it.type == ReaderBackgroundType.IMAGE }
        ?.value
    val customBackgroundThemeColorInt = selectedCustomBackground?.dominantColor
        ?: readerBackgroundColorInt
    val automaticReaderTextColorInt = when {
        selectedCustomBackground != null -> {
            if (ColorUtils.calculateLuminance(customBackgroundThemeColorInt) < 0.42) {
                0xFFE8E8EA.toInt()
            } else {
                0xFF333333.toInt()
            }
        }
        uiState.readerBackgroundSelection == "night" -> 0xFFCCCCCC.toInt()
        uiState.readerBackgroundSelection == "sepia" -> 0xFF4a3728.toInt()
        uiState.readerBackgroundSelection == "green" -> 0xFF2e7d32.toInt()
        else -> 0xFF333333.toInt()
    }
    val readerTextColorInt = uiState.readerTextColor ?: automaticReaderTextColorInt
    val hasTopReaderStatus = uiState.readerTopLeftContent != ReaderCornerContent.NONE ||
        uiState.readerTopRightContent != ReaderCornerContent.NONE
    val menuBgColorInt = selectedCustomBackground?.dominantColor ?: readerBackgroundColorInt
    val menuBgColor = Color(menuBgColorInt)
    val menuContentColor = if (ColorUtils.calculateLuminance(menuBgColorInt) < 0.4) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    // 胶囊按钮背景色：基于阅读主题而非系统深色模式
    val capsuleBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF3A3A3C)
        "sepia" -> Color(0xFFE8D5C4)
        "green" -> Color(0xFFC8E6C9)
        else -> Color(0xFFEEEEEE)
    }
    val capsuleContentColor = if (ColorUtils.calculateLuminance(capsuleBgColor.toArgb()) < 0.4) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    // 目录进度条颜色：比文字深，跟随阅读主题
    val catalogProgressColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF555555)
        "sepia" -> Color(0xFFC4A88C)
        "green" -> Color(0xFFA5D6A7)
        else -> Color(0xFFD0D0D0)
    }

    val loadError = uiState.error
    if (!uiState.isLoading && loadError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.WindowBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.reader_load_failed),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = loadError,
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.back), color = AppColors.Accent)
                }
            }
        }
        return
    }

    // 主题背景色
    val composeBgColor = Color(customBackgroundThemeColorInt)
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val readerGlassContentScrim = menuBgColor.copy(alpha = 0.18f)
    val readerGlassBackdrop = rememberLayerBackdrop()
    ReaderSystemBarStyle(
        backgroundColor = composeBgColor,
        useDarkIcons = ColorUtils.calculateLuminance(customBackgroundThemeColorInt) >= 0.42
    )

    Box(Modifier.fillMaxSize().background(composeBgColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(readerGlassBackdrop)
                    else Modifier
                )
        ) {
            // ── 新 Canvas 引擎（TXT/EPUB） ──
            if (uiState.useNewEngine) {
            AndroidView(
                factory = { ctx ->
                    ReadView(ctx).apply {
                        setCallbacks(object : ReadViewCallbacks {
                            override fun onPageChanged(
                                globalPage: Int,
                                chapterIndex: Int,
                                pageInChapter: Int,
                                chapterTotalPages: Int
                            ) {
                                // 翻页时关闭选择菜单（选区已随页面切换失效）
                                selectionState = null
                                viewModel.onNewEnginePageChanged(
                                    globalPage, chapterIndex, pageInChapter, chapterTotalPages
                                )
                            }

                            override fun onMenuToggle() {
                                // 用户点击屏幕中心区域，关闭选择菜单
                                selectionState = null
                                isSelectionDragging = false
                                viewModel.toggleMenu()
                            }

                            override fun onLinkClick(href: String) {
                                if (openExternalBookLink(context, href)) return
                                val source = readViewRef.value?.getCurrentLocation()
                                    ?.let { ReaderLinkLocation(it.first, it.second) }
                                    ?: return

                                linkNavigationJob?.cancel()
                                linkNavigationJob = scope.launch {
                                    val target = viewModel.resolveBookLink(source.chapterIndex, href)
                                        ?: return@launch
                                    linkReturnLocation = source
                                    linkReturnToken += 1
                                    readViewRef.value?.jumpToCharacter(
                                        target.chapterIndex,
                                        target.characterOffset
                                    )
                                }
                            }

                            override fun onLoadingChanged(isLoading: Boolean) {}

                            override fun onSelectionStarted() {
                                // 🔥 拖拽进行中时跳过：primary SpanWatcher 每次 span 变化都触发此回调，
                                // 若不 guard，会取消 dragHideRunnable（300ms 重弹计时器），导致菜单永不重弹
                                if (isSelectionDragging) return
                                showHighlightColorPicker = false
                                val info = readViewRef.value?.getSelectionInfo() ?: return
                                val cStart = info.chapterStartOffset + info.pageStart
                                val cEnd = info.chapterStartOffset + info.pageEnd
                                val overlapping = findOverlappingNote(notes, info.chapterIndex, cStart, cEnd)
                                selectionState = SelectionState(
                                    chapterIndex = info.chapterIndex,
                                    pageInChapter = 0,
                                    charStart = cStart,
                                    charEnd = cEnd,
                                    selectedText = info.selectedText,
                                    touchX = info.selStartX,
                                    touchY = info.selTopY,
                                    hasHighlight = overlapping != null,
                                    hasNote = overlapping?.note?.isNotEmpty() == true,
                                    existingNote = overlapping,
                                    selTopY = info.selTopY,
                                    selBottomY = info.selBottomY,
                                    selStartX = info.selStartX,
                                    selEndX = info.selEndX
                                )
                                // 延迟注册拖拽检测 SpanWatcher
                                dragHideRunnable?.let { dragHandler.removeCallbacks(it) }
                                dragHandler.postDelayed({
                                    val tv = readViewRef.value?.curPageView?.textView
                                    val sp = tv?.text as? Spannable ?: return@postDelayed
                                    dragWatcher?.let { old ->
                                        sp.getSpans(0, sp.length, SpanWatcher::class.java)
                                            .filter { it === old }
                                            .forEach { sp.removeSpan(it) }
                                    }
                                    val watcher = object : SpanWatcher {
                                        override fun onSpanChanged(s: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
                                            if (what !== Selection.SELECTION_START && what !== Selection.SELECTION_END) return
                                            // 🔥 移除 "if (selectionState == null) return" 保护：
                                            // 拖拽中第一次触发后 selectionState 被清空，后续每次 span 变化都会命中该保护
                                            // 导致防抖计时器无法在持续拖拽时正确重置
                                            if (selectionState != null) selectionState = null
                                            isSelectionDragging = true
                                            dragHideRunnable?.let { dragHandler.removeCallbacks(it) }
                                            val r = Runnable {
                                                val fresh = readViewRef.value?.getSelectionInfo()
                                                if (fresh != null) {
                                                    val cs = fresh.chapterStartOffset + fresh.pageStart
                                                    val ce = fresh.chapterStartOffset + fresh.pageEnd
                                                    val ov = findOverlappingNote(notes, fresh.chapterIndex, cs, ce)
                                                    selectionState = SelectionState(
                                                        chapterIndex = fresh.chapterIndex,
                                                        pageInChapter = 0,
                                                        charStart = cs,
                                                        charEnd = ce,
                                                        selectedText = fresh.selectedText,
                                                        touchX = fresh.selStartX,
                                                        touchY = fresh.selTopY,
                                                        hasHighlight = ov != null,
                                                        hasNote = ov?.note?.isNotEmpty() == true,
                                                        existingNote = ov,
                                                        selTopY = fresh.selTopY,
                                                        selBottomY = fresh.selBottomY,
                                                        selStartX = fresh.selStartX,
                                                        selEndX = fresh.selEndX
                                                    )
                                                    menuReappearKey++
                                                }
                                                isSelectionDragging = false
                                            }
                                            dragHideRunnable = r
                                            dragHandler.postDelayed(r, 300L)
                                        }
                                        override fun onSpanAdded(s: Spannable, what: Any, start: Int, end: Int) {}
                                        override fun onSpanRemoved(s: Spannable, what: Any, start: Int, end: Int) {}
                                    }
                                    dragWatcher = watcher
                                    sp.setSpan(watcher, 0, sp.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                                }, 100L)
                            }

                            override fun onSelectionAction(
                                action: String,
                                selectedText: String,
                                chapterIndex: Int,
                                startPosition: Int,
                                endPosition: Int,
                                pageStart: Int,
                                pageEnd: Int
                            ) {
                                when (action) {
                                    "highlight" -> {
                                        viewModel.addNote(
                                            selectedText = selectedText,
                                            noteText = "",
                                            chapterIndex = chapterIndex,
                                            startPosition = startPosition,
                                            endPosition = endPosition,
                                            color = "#40FFEB3B"
                                        )
                                    }
                                    "note" -> {
                                        // 保存当前选区信息，打开笔记输入
                                        pendingSelection = PendingSelection(
                                            selectedText, chapterIndex, startPosition, endPosition
                                        )
                                        showNoteInput = true
                                    }
                                    "search" -> {
                                        showSearch = true
                                        searchQuery = selectedText
                                        isSearching = true
                                        hasSearched = true
                                        searchResults = emptyList()
                                        scope.launch {
                                            searchResults = viewModel.searchAllChapters(selectedText)
                                            isSearching = false
                                        }
                                    }
                                    "dismiss" -> {
                                        // 选区被清除 → 隐藏自定义菜单
                                        selectionState = null
                                    }
                                }
                            }
                        })
                        setContentProvider { chapterIndex ->
                            viewModel.getChapterText(chapterIndex)
                        }
                        readViewRef.value = this
                    }
                },
                update = { readView ->
                    val fontSizePx = debouncedFontSize * density.density
                    val measuredWidth = readView.width.takeIf { it > 0 } ?: readerScreenWidthPx
                    val contentWidthPx = (
                        measuredWidth - (
                            (uiState.marginLeftDp + uiState.marginRightDp) * density.density
                        ).toInt()
                    ).coerceAtLeast(1)
                    viewModel.updateReaderContentWidth(contentWidthPx)
                    readView.configure(
                        fontSizePx = fontSizePx,
                        theme = uiState.readerTheme,
                        chapterCount = uiState.chapterCount,
                        startChapter = uiState.currentChapterIndex,
                        startPage = uiState.currentPageIndex,
                        lineHeightMult = uiState.lineHeight,
                        letterSpacingDp = uiState.letterSpacing,
                        fontType = uiState.fontType,
                        customFontPath = uiState.customFontPath,
                        marginLeftDp = uiState.marginLeftDp,
                        marginRightDp = uiState.marginRightDp,
                        marginTopDp = uiState.marginTopDp,
                        marginBottomDp = uiState.marginBottomDp,
                        topOverlayInsetDp = if (hasTopReaderStatus) 38f else 0f,
                        bottomOverlayInsetDp = 0f,
                        paragraphSpacingDp = uiState.paragraphSpacing
                    )
                    readView.setReaderBackground(
                        backgroundColor = readerBackgroundColorInt,
                        textColor = readerTextColorInt,
                        imagePath = readerBackgroundImagePath
                    )
                    readView.setSavedNotes(notes)
                    // 简繁转换
                    readView.setChineseMode(uiState.chineseMode)
                    // 翻页效果
                    readView.setPageTransition(uiState.pageTransition)
                },
                modifier = Modifier.fillMaxSize()
            )

            // 段间距/首行缩进变化时，强制重新分页
            LaunchedEffect(uiState.paragraphSpacing, uiState.firstLineIndent) {
                readViewRef.value?.forceRelayout()
            }
        }

        // ── 旧 WebView 路径（PDF） ──
            if (!uiState.useNewEngine) {
                LegacyWebViewContent(uiState, viewModel, composeBgColor)
            }
        }

        // ── 覆盖层 UI（新旧引擎共享） ──
        ProvideLiquidGlassBackdrop(
            backdrop = readerGlassBackdrop.takeIf { isLiquidGlass }
        ) {
        if (!uiState.isLoading) {
            AnimatedVisibility(
                visible = linkReturnLocation != null && !uiState.isMenuVisible && !isAnySheetOpen,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 24.dp, top = 20.dp)
            ) {
                LinkReturnButton(
                    backgroundColor = capsuleBgColor,
                    contentColor = capsuleContentColor,
                    onClick = returnToLinkedSource
                )
            }

            // 顶部栏
            AnimatedVisibility(
                visible = uiState.isMenuVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val bookTitle = uiState.book?.title ?: ""
                val isCurrentPageBookmarked = bookmarks.any {
                    it.chapterIndex == uiState.currentChapterIndex &&
                    it.position.toInt() == uiState.currentPageIndex
                }
                ReaderTopBar(
                    title = bookTitle,
                    onBack = onNavigateBack,
                    bgColor = menuBgColor,
                    contentColor = menuContentColor,
                    glassContentScrimColor = readerGlassContentScrim,
                    isTtsActive = uiState.ttsActiveBookId == uiState.book?.id &&
                        uiState.ttsPlaybackState != TtsPlaybackState.IDLE,
                    onTtsClick = {
                        if (uiState.ttsActiveBookId == uiState.book?.id &&
                            uiState.ttsPlaybackState != TtsPlaybackState.IDLE
                        ) {
                            viewModel.toggleTtsPlayPause()
                        } else {
                            requestTtsStart()
                        }
                    },
                    isBookmarked = isCurrentPageBookmarked,
                    onBookmarkToggle = {
                        if (isCurrentPageBookmarked) {
                            bookmarks.firstOrNull {
                                it.chapterIndex == uiState.currentChapterIndex &&
                                it.position.toInt() == uiState.currentPageIndex
                            }?.let { viewModel.deleteBookmark(it) }
                        } else {
                            viewModel.addBookmark()
                        }
                    }
                )
            }

            if (uiState.totalPages > 0 || uiState.useNewEngine) {
                val chapterTitle = uiState.chapterTitles
                    .getOrNull(uiState.currentChapterIndex)
                    ?.trim()
                    .orEmpty()
                    .ifBlank {
                        stringResource(
                            R.string.reader_chapter_fallback,
                            uiState.currentChapterIndex + 1
                        )
                    }
                val bookProgressPercent = calculateBookProgressPercent(
                    chapterIndex = uiState.currentChapterIndex,
                    chapterCount = uiState.chapterCount,
                    pageIndex = uiState.currentPageIndex,
                    chapterPageCount = uiState.totalPages
                )

                // 底部渐变遮罩
                val menuAlpha = remember { Animatable(0f) }
                val menuOffset = remember { Animatable(60f) }
                val menuScope = rememberCoroutineScope()
                LaunchedEffect(uiState.isMenuVisible) {
                    if (uiState.isMenuVisible) {
                        menuOffset.snapTo(60f)
                        menuScope.launch { menuAlpha.animateTo(1f, tween(300)) }
                        menuScope.launch { menuOffset.animateTo(0f, tween(300, easing = AppEasing.Smooth)) }
                    } else {
                        menuScope.launch { menuAlpha.animateTo(0f, tween(200)) }
                        menuScope.launch { menuOffset.animateTo(60f, tween(200, easing = AppEasing.Accelerate)) }
                    }
                }

                if (!isLiquidGlass) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = menuAlpha.value }
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to menuBgColor.copy(alpha = 0f),
                                        0.2f to menuBgColor.copy(alpha = 0.4f),
                                        0.5f to menuBgColor.copy(alpha = 0.8f),
                                        0.8f to menuBgColor.copy(alpha = 0.95f),
                                        1.0f to menuBgColor
                                    )
                                )
                            )
                    )
                }

                // 胶囊菜单
                Box(modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = menuAlpha.value; translationY = menuOffset.value }
                ) {
                    FloatingReaderMenu(
                        visible = uiState.isMenuVisible,
                        chapterTitle = chapterTitle,
                        bookProgressPercent = bookProgressPercent,
                        currentPage = uiState.currentPageIndex + 1,
                        chapterPageCount = uiState.totalPages,
                        capsuleBgColor = capsuleBgColor,
                        capsuleContentColor = if (isLiquidGlass) menuContentColor else capsuleContentColor,
                        readerContentColor = menuContentColor,
                        catalogProgressColor = catalogProgressColor,
                        glassContentScrimColor = readerGlassContentScrim,
                        onCatalogClick = {
                            viewModel.hideMenu()
                            showToc = true
                        },
                        onBookmarkClick = {
                            viewModel.hideMenu()
                            showNotesList = true
                        },
                        onSearchClick = {
                            viewModel.hideMenu()
                            showSearch = true
                        },
                        onThemeClick = {
                            viewModel.hideMenu()
                            showThemeSheet = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 底部阅读状态
                if (!uiState.isMenuVisible) {
                    ReaderPageCornerOverlay(
                        chapterTitle = chapterTitle,
                        bookProgressPercent = bookProgressPercent,
                        currentPage = uiState.currentPageIndex + 1,
                        chapterPageCount = uiState.totalPages,
                        leftMarginDp = uiState.marginLeftDp,
                        rightMarginDp = uiState.marginRightDp,
                        topLeft = if (linkReturnLocation == null) {
                            uiState.readerTopLeftContent
                        } else {
                            ReaderCornerContent.NONE
                        },
                        topRight = uiState.readerTopRightContent,
                        bottomLeft = uiState.readerBottomLeftContent,
                        bottomRight = uiState.readerBottomRightContent,
                        contentColor = Color(readerTextColorInt).copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.ttsActiveBookId == uiState.book?.id &&
                    uiState.ttsPlaybackState != TtsPlaybackState.IDLE,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (uiState.isMenuVisible) 204.dp else 44.dp)
            ) {
                TtsPlayerPanel(
                    playbackState = uiState.ttsPlaybackState,
                    speechRate = uiState.ttsSpeechRate,
                    onPlayPause = viewModel::toggleTtsPlayPause,
                    onStop = viewModel::stopTts,
                    onSkipForward = viewModel::ttsSkipForward,
                    onSkipBackward = viewModel::ttsSkipBackward,
                    onRateChange = viewModel::setTtsSpeechRate,
                    readerBackgroundColor = composeBgColor,
                    readerContentColor = Color(readerTextColorInt)
                )
            }

            // 目录底部弹出
            TocSheet(
                visible = showToc,
                requestClose = requestCloseToc,
                tocEntries = uiState.tocEntries,
                currentChapter = uiState.currentChapterIndex,
                onChapterSelected = { idx ->
                    isTocJump.value = true
                    viewModel.setChapter(idx)
                    showToc = false
                    requestCloseToc = false
                },
                onDismiss = { showToc = false; requestCloseToc = false }
            )

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                requestClose = requestCloseTheme,
                currentFontSize = uiState.fontSize,
                currentTheme = uiState.readerTheme,
                currentBackgroundSelection = uiState.readerBackgroundSelection,
                customBackgrounds = uiState.customReaderBackgrounds,
                currentBrightness = uiState.brightness,
                currentOptimizeLayout = uiState.optimizeLayout,
                currentChineseMode = uiState.chineseMode,
                currentPageTransition = uiState.pageTransition,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onBackgroundSelect = { viewModel.selectReaderBackground(it) },
                onAddBackgroundColor = { viewModel.addCustomReaderBackgroundColor(it) },
                onAddBackgroundImage = { viewModel.addCustomReaderBackgroundImage(it) },
                onDeleteBackground = { viewModel.deleteCustomReaderBackground(it) },
                onBrightnessChange = { viewModel.saveBrightness(it) },
                onOptimizeLayoutChange = { viewModel.saveOptimizeLayout(it) },
                onChineseModeChange = { viewModel.saveChineseMode(it) },
                onPageTransitionChange = { viewModel.savePageTransition(it) },
                onOpenAdvanced = {
                    requestCloseTheme = true
                    showAdvancedSheet = true
                },
                onDismiss = { showThemeSheet = false; requestCloseTheme = false }
            )

            // 搜索弹窗
            SearchSheet(
                visible = showSearch,
                requestClose = requestCloseSearch,
                query = searchQuery,
                results = searchResults,
                isSearching = isSearching,
                hasSearched = hasSearched,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    isSearching = true
                    hasSearched = true
                    searchResults = emptyList()
                    scope.launch {
                        searchResults = viewModel.searchAllChapters(searchQuery)
                        isSearching = false
                    }
                },
                onResultClick = { result ->
                    val chapterLen = viewModel.getChapterTextLength(result.chapterIndex)
                    val estimatedPage = if (chapterLen > 0) {
                        (result.charOffset.toFloat() / chapterLen * uiState.totalPages).toInt()
                    } else 0
                    readViewRef.value?.jumpToChapter(result.chapterIndex, estimatedPage)
                    showSearch = false
                    requestCloseSearch = false
                    searchQuery = ""
                    searchResults = emptyList()
                    hasSearched = false
                },
                onDismiss = {
                    showSearch = false
                    requestCloseSearch = false
                    searchQuery = ""
                    searchResults = emptyList()
                    hasSearched = false
                }
            )

            // 高级排版设置弹窗
            val previewText = remember(uiState.currentChapterIndex) {
                viewModel.getChapterText(uiState.currentChapterIndex)
                    ?.toString()
                    ?.replace('\uFFFC', ' ')
                    ?.take(420) ?: ""
            }
            AdvancedSettingsSheet(
                visible = showAdvancedSheet,
                requestClose = requestCloseAdvanced,
                previewText = previewText,
                currentLineHeight = uiState.lineHeight,
                currentLetterSpacing = uiState.letterSpacing,
                currentFontType = uiState.fontType,
                customFontPath = uiState.customFontPath,
                currentMarginLeft = uiState.marginLeftDp,
                currentMarginRight = uiState.marginRightDp,
                currentMarginTop = uiState.marginTopDp,
                currentMarginBottom = uiState.marginBottomDp,
                currentBgColor = Color(readerBackgroundColorInt),
                currentBackgroundImagePath = readerBackgroundImagePath,
                currentTextColor = Color(readerTextColorInt),
                currentTextColorOverride = uiState.readerTextColor,
                currentFontSizeSp = uiState.fontSize,
                onLineHeightChange = { viewModel.saveLineHeight(it) },
                onLetterSpacingChange = { viewModel.saveLetterSpacing(it) },
                onFontTypeChange = { viewModel.saveFontType(it) },
                onImportFont = { uri ->
                    scope.launch {
                        val path = viewModel.importFont(context, uri)
                        if (path != null) {
                            viewModel.saveCustomFontPath(path)
                            viewModel.saveFontType("custom")
                        }
                    }
                },
                onMarginLeftChange = { viewModel.saveMarginLeft(it) },
                onMarginRightChange = { viewModel.saveMarginRight(it) },
                onMarginTopChange = { viewModel.saveMarginTop(it) },
                onMarginBottomChange = { viewModel.saveMarginBottom(it) },
                currentParagraphSpacing = uiState.paragraphSpacing,
                currentFirstLineIndent = uiState.firstLineIndent,
                onParagraphSpacingChange = { viewModel.saveParagraphSpacing(it) },
                onFirstLineIndentChange = { viewModel.saveFirstLineIndent(it) },
                readerTopLeftContent = uiState.readerTopLeftContent,
                readerTopRightContent = uiState.readerTopRightContent,
                readerBottomLeftContent = uiState.readerBottomLeftContent,
                readerBottomRightContent = uiState.readerBottomRightContent,
                volumeKeyPageTurnEnabled = uiState.volumeKeyPageTurnEnabled,
                onReaderCornerContentChange = viewModel::saveReaderCornerContent,
                onVolumeKeyPageTurnEnabledChange = { viewModel.saveVolumeKeyPageTurnEnabled(it) },
                onTextColorChange = { viewModel.saveReaderTextColor(it) },
                onResetSettings = { viewModel.resetAdvancedReaderSettings() },
                onDismiss = { showAdvancedSheet = false; requestCloseAdvanced = false }
            )
        }
    }
    }

    // ── 笔记/高亮列表 ──
    NotesListSheet(
        visible = showNotesList,
        requestClose = requestCloseNotesList,
        notes = viewModel.notes.collectAsState().value,
        bookmarks = bookmarks,
        onNoteClick = { note ->
            val estimatedPage = viewModel.estimatePageFromCharOffset(note.chapterIndex, note.startPosition)
            readViewRef.value?.jumpToChapter(note.chapterIndex, estimatedPage)
            showNotesList = false
            requestCloseNotesList = false
        },
        onDeleteNote = { note -> viewModel.deleteNote(note) },
        onBookmarkClick = { bm ->
            readViewRef.value?.jumpToChapter(bm.chapterIndex, bm.position.toInt())
            showNotesList = false
            requestCloseNotesList = false
        },
        onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm) },
        onDismiss = { showNotesList = false; requestCloseNotesList = false }
    )

    // ── 文字选择自定义菜单 ──
    SelectionMenuOverlay(
        state = selectionState,
        readerTheme = uiState.readerTheme,
        isDragging = isSelectionDragging,
        reappearKey = menuReappearKey,
        showColorPicker = showHighlightColorPicker,
        onDismiss = {
            selectionState = null
            showHighlightColorPicker = false
            readViewRef.value?.curPageView?.clearSelection()
        },
        onColorPicked = { hexColor ->
            val fresh = readViewRef.value?.getSelectionInfo()
            if (fresh != null) {
                viewModel.addNote(
                    selectedText = fresh.selectedText,
                    noteText = "",
                    chapterIndex = fresh.chapterIndex,
                    startPosition = fresh.chapterStartOffset + fresh.pageStart,
                    endPosition = fresh.chapterStartOffset + fresh.pageEnd,
                    color = hexColor
                )
            }
            selectionState = null
            showHighlightColorPicker = false
            readViewRef.value?.curPageView?.clearSelection()
        },
        onHighlight = {
            // 切换到颜色选择子菜单
            showHighlightColorPicker = true
        },
        onNote = {
            val fresh = readViewRef.value?.getSelectionInfo()
            if (fresh != null) {
                pendingSelection = PendingSelection(
                    fresh.selectedText, fresh.chapterIndex,
                    fresh.chapterStartOffset + fresh.pageStart,
                    fresh.chapterStartOffset + fresh.pageEnd
                )
                showNoteInput = true
            }
            selectionState = null
            showHighlightColorPicker = false
        },
        onSearch = {
            val fresh = readViewRef.value?.getSelectionInfo()
            val query = fresh?.selectedText ?: selectionState?.selectedText ?: return@SelectionMenuOverlay
            showSearch = true
            searchQuery = query
            isSearching = true
            hasSearched = true
            searchResults = emptyList()
            scope.launch {
                searchResults = viewModel.searchAllChapters(query)
                isSearching = false
            }
            selectionState = null
            showHighlightColorPicker = false
        },
        onCopy = {
            val fresh = readViewRef.value?.getSelectionInfo()
            val text = fresh?.selectedText ?: selectionState?.selectedText ?: return@SelectionMenuOverlay
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("selected", text))
            selectionState = null
            showHighlightColorPicker = false
            readViewRef.value?.curPageView?.clearSelection()
        },
        onRemoveHighlight = {
            // 移除高亮 = 删除该 Note（高亮和笔记共用同一条记录）
            selectionState?.existingNote?.let { viewModel.deleteNote(it) }
            selectionState = null
            showHighlightColorPicker = false
            readViewRef.value?.curPageView?.clearSelection()
        },
        onViewNote = {
            // 🔥 查看/修改笔记：打开 NoteInputSheet 预填原笔记文字
            val existing = selectionState?.existingNote
            if (existing != null) {
                editingNote = existing
                noteInputText = existing.note
                showNoteInput = true
            }
            selectionState = null
            showHighlightColorPicker = false
            readViewRef.value?.curPageView?.clearSelection()
        },
        onChangeHighlightColor = { hexColor ->
            // 修改已有高亮的颜色
            val existing = selectionState?.existingNote
            if (existing != null) {
                viewModel.updateNote(existing.copy(color = hexColor))
            }
            selectionState = null
            readViewRef.value?.curPageView?.clearSelection()
        },
        onDeleteHighlight = {
            // 删除高亮
            selectionState?.existingNote?.let { viewModel.deleteNote(it) }
            selectionState = null
            readViewRef.value?.curPageView?.clearSelection()
        }
    )

    // 🔥 笔记输入弹窗（自定义菜单触发"笔记"时弹出）
    NoteInputSheet(
        visible = showNoteInput,
        initialText = noteInputText,
        onTextChange = { noteInputText = it },
        onConfirm = {
            val editing = editingNote
            if (editing != null) {
                // 编辑模式：更新已有笔记
                viewModel.updateNote(editing.copy(note = noteInputText))
                editingNote = null
            } else {
                // 新建模式
                val ps = pendingSelection ?: return@NoteInputSheet
                viewModel.addNote(
                    selectedText = ps.selectedText,
                    noteText = noteInputText,
                    chapterIndex = ps.chapterIndex,
                    startPosition = ps.startPosition,
                    endPosition = ps.endPosition,
                    color = "#40FFEB3B"
                )
                pendingSelection = null
            }
            showNoteInput = false
            noteInputText = ""
            readViewRef.value?.curPageView?.clearSelection()
        },
        onCancel = {
            showNoteInput = false
            pendingSelection = null
            editingNote = null
            noteInputText = ""
        }
    )
}

@Composable
private fun ReaderPageCornerOverlay(
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    leftMarginDp: Float,
    rightMarginDp: Float,
    topLeft: ReaderCornerContent,
    topRight: ReaderCornerContent,
    bottomLeft: ReaderCornerContent,
    bottomRight: ReaderCornerContent,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (topLeft != ReaderCornerContent.NONE || topRight != ReaderCornerContent.NONE) {
            ReaderCornerStatusRow(
                left = topLeft,
                right = topRight,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        top = 20.dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp
                    )
            )
        }
        if (bottomLeft != ReaderCornerContent.NONE || bottomRight != ReaderCornerContent.NONE) {
            ReaderCornerStatusRow(
                left = bottomLeft,
                right = bottomRight,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                contentColor = contentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = leftMarginDp.coerceAtLeast(0f).dp,
                        end = rightMarginDp.coerceAtLeast(0f).dp,
                        bottom = 20.dp
                    )
            )
        }
        }
    }

@Composable
private fun ReaderCornerStatusRow(
    left: ReaderCornerContent,
    right: ReaderCornerContent,
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            ReaderCornerContentValue(
                content = left,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                contentColor = contentColor,
                alignEnd = false
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            ReaderCornerContentValue(
                content = right,
                chapterTitle = chapterTitle,
                bookProgressPercent = bookProgressPercent,
                currentPage = currentPage,
                chapterPageCount = chapterPageCount,
                contentColor = contentColor,
                alignEnd = true
            )
        }
    }
}

@Composable
private fun ReaderCornerContentValue(
    content: ReaderCornerContent,
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    contentColor: Color,
    alignEnd: Boolean
) {
    when (content) {
        ReaderCornerContent.NONE -> Unit
        ReaderCornerContent.BATTERY -> ReaderBatteryStatus(contentColor)
        else -> Text(
            text = when (content) {
                ReaderCornerContent.CHAPTER_INFO -> chapterTitle
                ReaderCornerContent.BOOK_PROGRESS -> formatReadingProgressPercent(bookProgressPercent)
                ReaderCornerContent.PAGE_NUMBER -> "$currentPage / ${chapterPageCount.coerceAtLeast(1)}"
                else -> ""
            },
            color = contentColor,
            fontSize = AppType.Caption,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReaderBatteryStatus(contentColor: Color, modifier: Modifier = Modifier) {
    val batteryPercent = rememberBatteryPercentage()
    val batteryDescription = stringResource(R.string.reader_battery_level, batteryPercent)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = batteryDescription
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalBatteryIcon(
            batteryPercent = batteryPercent,
            color = contentColor,
            modifier = Modifier
                .alignBy { it.measuredHeight }
                .offset(y = 1.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$batteryPercent%",
            color = contentColor,
            fontSize = AppType.Caption,
            lineHeight = AppType.Caption,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun HorizontalBatteryIcon(
    batteryPercent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(21.dp).height(11.dp)) {
        val strokeWidth = 1.dp.toPx()
        val terminalWidth = 1.5.dp.toPx()
        val terminalGap = 0.75.dp.toPx()
        val bodyWidth = size.width - terminalWidth - terminalGap
        val radius = 3.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth)
        )
        val innerPadding = 1.75.dp.toPx()
        val fillWidth = ((bodyWidth - innerPadding * 2f) *
            (batteryPercent.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = if (batteryPercent <= 20) Color(0xFFFF453A) else color,
                topLeft = Offset(innerPadding, innerPadding),
                size = Size(fillWidth, (size.height - innerPadding * 2f).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            topLeft = Offset(bodyWidth + terminalGap, size.height * 0.32f),
            size = Size(terminalWidth, size.height * 0.4f),
            cornerRadius = CornerRadius(terminalWidth / 2f, terminalWidth / 2f)
        )
    }
}

@Composable
private fun rememberBatteryPercentage(): Int {
    val context = LocalContext.current.applicationContext
    val batteryManager = remember(context) {
        context.getSystemService(BatteryManager::class.java)
    }
    var batteryPercent by remember(context) {
        mutableIntStateOf(
            batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?: 0
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return batteryPercent
}

private fun openExternalBookLink(context: Context, href: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(href.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme !in setOf("http", "https", "mailto", "tel")) return false

    return runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    }.isSuccess
}

/**
 * 旧 WebView 路径（PDF 格式保留使用）。
 * 简化版：单 WebView，无跨章 conveyor。
 */
@Composable
private fun LegacyWebViewContent(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    bgColor: Color
) {
    if (uiState.chapterHtml.isEmpty()) return

    val context = LocalContext.current
    val currentFontSize = remember { mutableFloatStateOf(uiState.fontSize) }
    val currentTheme = remember { mutableStateOf(uiState.readerTheme) }
    var prevFontSize by remember { mutableFloatStateOf(uiState.fontSize) }
    var prevTheme by remember { mutableStateOf(uiState.readerTheme) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // JS bridge for PDF (simplified)
    val bridge = remember {
        object {
            @android.webkit.JavascriptInterface
            fun onPageChanged(page: Int, total: Int) {
                viewModel.onPageChanged(page, total)
            }
            @android.webkit.JavascriptInterface
            fun onCenterTap() { viewModel.toggleMenu() }
            @android.webkit.JavascriptInterface
            fun onPaginationComplete() { viewModel.onPaginationDone() }
            @android.webkit.JavascriptInterface
            fun onPageFlip(dir: Int) {}
            @android.webkit.JavascriptInterface
            fun onChapterFlipReady(dir: Int) {
                if (dir > 0) viewModel.nextChapter() else viewModel.previousChapter()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                val bgJs = when (uiState.readerTheme) {
                    "night" -> "#1a1a1a"; "sepia" -> "#f5e6d3"; "green" -> "#e8f5e9"; else -> "#ffffff"
                }
                val textJs = when (uiState.readerTheme) {
                    "night" -> "#e0e0e0"; "sepia" -> "#3e2723"; "green" -> "#1b5e20"; else -> "#333333"
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val fs = currentFontSize.floatValue
                        view?.postDelayed({
                            val js = """
(function(){
var vw=innerWidth,vh=innerHeight;
var b=document.body;b.style.margin='0';b.style.padding='0';b.style.overflow='hidden';
b.style.width=vw+'px';b.style.height=vh+'px';
b.style.visibility='visible';
try{AndroidBridge.onPaginationComplete();}catch(e){}
try{AndroidBridge.onPageChanged(0,1);}catch(e){}
})();
""".trimIndent()
                            view.evaluateJavascript(js) {}
                        }, 300)
                    }
                }
                setBackgroundColor(android.graphics.Color.parseColor(bgJs))
                webViewRef.value = this
            }
        },
        update = { webView ->
            currentFontSize.floatValue = uiState.fontSize
            currentTheme.value = uiState.readerTheme
            val html = viewModel.getChapterHtml(uiState.currentChapterIndex)
            val tag = webView.tag as? String
            if (html.isNotEmpty() && tag != html.hashCode().toString()) {
                webView.tag = html.hashCode().toString()
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            val bgJs = when (uiState.readerTheme) {
                "night" -> "#1a1a1a"; "sepia" -> "#f5e6d3"; "green" -> "#e8f5e9"; else -> "#ffffff"
            }
            val textJs = when (uiState.readerTheme) {
                "night" -> "#e0e0e0"; "sepia" -> "#3e2723"; "green" -> "#1b5e20"; else -> "#333333"
            }
            webView.evaluateJavascript("document.body.style.background='$bgJs';document.body.style.color='$textJs';document.body.style.fontSize='${uiState.fontSize}px';") {}
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun LinkReturnButton(
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor.copy(alpha = 0.96f))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.reader_link_return),
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    bgColor: Color = Color.White,
    contentColor: Color = AppColors.TextPrimary,
    glassContentScrimColor: Color = Color.Transparent,
    isTtsActive: Boolean = false,
    onTtsClick: () -> Unit = {},
    isBookmarked: Boolean = false,
    onBookmarkToggle: () -> Unit = {}
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val controlBackground = if (contentColor == Color.White) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color(0xFFF2F2F7).copy(alpha = 0.8f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (!isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to bgColor,
                                0.3f to bgColor,
                                0.6f to bgColor.copy(alpha = 0.85f),
                                0.85f to bgColor.copy(alpha = 0.3f),
                                1.0f to bgColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(82.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                ReaderTopBarButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = contentColor,
                    backgroundColor = controlBackground,
                    contentScrimColor = glassContentScrimColor,
                    onClick = onBack
                )
            }
            Text(
                text = title,
                fontSize = 12.sp,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .width(82.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTopBarButton(
                    icon = Icons.Default.Headphones,
                    contentDescription = stringResource(R.string.tts_listen),
                    tint = if (isTtsActive) AppColors.Accent else contentColor,
                    backgroundColor = controlBackground,
                    contentScrimColor = glassContentScrimColor,
                    onClick = onTtsClick
                )
                ReaderTopBarButton(
                    icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(R.string.reader_bookmark),
                    tint = if (isBookmarked) AppColors.Accent else contentColor,
                    backgroundColor = controlBackground,
                    contentScrimColor = glassContentScrimColor,
                    onClick = onBookmarkToggle
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBarButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    contentScrimColor: Color,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = backgroundColor,
        contentScrimColor = contentScrimColor,
        modifier = Modifier
            .size(36.dp),
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FloatingReaderMenu(
    visible: Boolean,
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    capsuleBgColor: Color,
    capsuleContentColor: Color,
    readerContentColor: Color,
    catalogProgressColor: Color,
    glassContentScrimColor: Color,
    onCatalogClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSearchClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha0 = remember { Animatable(0f) }
    val offset0 = remember { Animatable(40f) }
    val alpha1 = remember { Animatable(0f) }
    val offset1 = remember { Animatable(40f) }
    val alpha2 = remember { Animatable(0f) }
    val offset2 = remember { Animatable(40f) }
    val alpha3 = remember { Animatable(0f) }
    val offset3 = remember { Animatable(40f) }

    LaunchedEffect(visible) {
        if (visible) {
            alpha0.snapTo(0f); offset0.snapTo(40f)
            alpha1.snapTo(0f); offset1.snapTo(40f)
            alpha2.snapTo(0f); offset2.snapTo(40f)
            alpha3.snapTo(0f); offset3.snapTo(40f)
            launch { alpha0.animateTo(1f, tween(250)); offset0.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha1.animateTo(1f, tween(250)); offset1.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha2.animateTo(1f, tween(250)); offset2.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
            kotlinx.coroutines.delay(100)
            launch { alpha3.animateTo(1f, tween(250)); offset3.animateTo(0f, tween(250, easing = AppEasing.Smooth)) }
        } else {
            alpha0.snapTo(0f); offset0.snapTo(40f)
            alpha1.snapTo(0f); offset1.snapTo(40f)
            alpha2.snapTo(0f); offset2.snapTo(40f)
            alpha3.snapTo(0f); offset3.snapTo(40f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.graphicsLayer {
            alpha = alpha0.value
            translationY = offset0.value
        }) {
            CatalogCapsule(
                title = chapterTitle,
                progress = bookProgressPercent,
                bgColor = capsuleBgColor,
                contentColor = capsuleContentColor,
                progressColor = catalogProgressColor,
                glassContentScrimColor = glassContentScrimColor,
                onClick = onCatalogClick
            )
        }
        ReaderMenuStatus(
            chapterTitle = chapterTitle,
            bookProgressPercent = bookProgressPercent,
            currentPage = currentPage,
            chapterPageCount = chapterPageCount,
            contentColor = readerContentColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha1.value; translationY = offset1.value
            }) {
                ActionCapsule(Icons.Default.Bookmark, stringResource(R.string.reader_notes), capsuleBgColor, capsuleContentColor, glassContentScrimColor, Modifier.fillMaxWidth(), onBookmarkClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha2.value; translationY = offset2.value
            }) {
                ActionCapsule(Icons.Default.Search, stringResource(R.string.reader_search), capsuleBgColor, capsuleContentColor, glassContentScrimColor, Modifier.fillMaxWidth(), onSearchClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha3.value; translationY = offset3.value
            }) {
                ActionCapsule(Icons.Default.Settings, stringResource(R.string.reader_theme), capsuleBgColor, capsuleContentColor, glassContentScrimColor, Modifier.fillMaxWidth(), onThemeClick)
            }
        }
    }
}

@Composable
private fun ReaderMenuStatus(
    chapterTitle: String,
    bookProgressPercent: Float,
    currentPage: Int,
    chapterPageCount: Int,
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterTitle,
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatReadingProgressPercent(bookProgressPercent),
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$currentPage / ${chapterPageCount.coerceAtLeast(1)}",
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        ReaderBatteryStatus(contentColor.copy(alpha = 0.68f))
    }
}

@Composable
private fun CatalogCapsule(
    title: String,
    progress: Float,
    bgColor: Color,
    contentColor: Color,
    progressColor: Color,
    glassContentScrimColor: Color,
    onClick: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        fallbackColor = bgColor,
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
                    .background(contentColor.copy(alpha = 0.10f))
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
                    .background(progressColor)
            )
        }
        val leftColor = if (isLiquidGlass) {
            contentColor
        } else if (progress > 5f) {
            Color.White
        } else {
            contentColor
        }
        val rightColor = if (isLiquidGlass) {
            contentColor.copy(alpha = 0.62f)
        } else if (progress > 70f) {
            Color.White.copy(alpha = 0.9f)
        } else {
            contentColor.copy(alpha = 0.5f)
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.List, contentDescription = null, tint = leftColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reader_toc), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = leftColor)
            Spacer(Modifier.weight(1f))
            Text(formatReadingProgressPercent(progress), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rightColor)
        }
    }
}

@Composable
private fun ActionCapsule(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    contentColor: Color,
    glassContentScrimColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = bgColor,
        contentScrimColor = glassContentScrimColor,
        modifier = modifier
            .height(44.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = contentColor)
        }
    }
}

@Composable
private fun TocSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    tocEntries: List<com.huangder.lumibooks.util.parser.TocEntry>,
    currentChapter: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val currentEntryIndex = remember(tocEntries, currentChapter) {
        tocEntries.indexOfFirst { !it.isGroup && it.chapterIndex == currentChapter }
    }
    val tocListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentEntryIndex.coerceAtLeast(0)
    )

    LaunchedEffect(currentEntryIndex) {
        if (currentEntryIndex < 0) return@LaunchedEffect
        snapshotFlow { tocListState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val currentItem = tocListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentEntryIndex }
            ?: return@LaunchedEffect
        val layoutInfo = tocListState.layoutInfo
        val centeredItemOffset = layoutInfo.viewportStartOffset +
            (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset - currentItem.size) / 2
        tocListState.scrollBy((currentItem.offset - centeredItemOffset).toFloat())
    }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    var pendingJumpIndex by remember { mutableStateOf<Int?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            pendingJumpIndex?.let { onChapterSelected(it) }
            pendingJumpIndex = null
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.20f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（70% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
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
                    stringResource(R.string.reader_toc),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBgGray)
                        .clickable { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.reader_close), tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 目录列表（支持层级：分组标题 + 缩进章节）
            LazyColumn(
                state = tocListState,
                modifier = Modifier.weight(1f)
            ) {
                items(tocEntries.size) { index ->
                    val entry = tocEntries[index]

                    if (entry.isGroup) {
                        // 分组标题（如"第X卷"）：灰色、粗体、不可点击、无背景
                        Text(
                            text = entry.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = if (index > 0) 16.dp else 4.dp,
                                bottom = 4.dp
                            )
                        )
                    } else {
                        // 实际章节：可点击，根据 level 缩进
                        val isCurrent = entry.chapterIndex == currentChapter
                        val indent = ((entry.level - 1) * 20).dp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indent, top = 2.dp, bottom = 2.dp, end = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) AccentColor.copy(alpha = 0.1f) else LightBgGray)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    if (entry.chapterIndex >= 0) {
                                        pendingJumpIndex = entry.chapterIndex
                                        isClosing = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = entry.title.ifBlank { stringResource(R.string.reader_chapter_fallback, entry.chapterIndex + 1) },
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) AccentColor else AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 全文搜索弹窗——底部弹出，可伸缩高度。
 */
@Composable
private fun SearchSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    query: String,
    results: List<ReaderViewModel.SearchResult>,
    isSearching: Boolean,
    hasSearched: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (ReaderViewModel.SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val hasResults = results.isNotEmpty()

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.20f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出容器（自适应高度）
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress)
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Column {
                // 标题栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.reader_search),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = KaiTi,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    // 关闭按钮
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LightBgGray)
                            .clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.reader_close), tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 搜索输入框 + 按钮
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(LightBgGray)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.material3.TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = 14.sp, color = LightTextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (query.isNotBlank()) AccentColor else LightBgGray)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (query.isNotBlank()) onSearch() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.OnAccent)
                        } else {
                            Text(stringResource(R.string.reader_search), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (query.isNotBlank()) AppColors.OnAccent else LightTextSecondary)
                        }
                    }
                }

                // 结果区域（有结果时显示，自适应高度）
                if (hasResults) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.search_results_found, results.size),
                        fontSize = 12.sp,
                        color = LightTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    // 结果列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        items(results.size) { idx ->
                            val r = results[idx]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightBgGray)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        onResultClick(r)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = r.chapterTitle,
                                        fontSize = 12.sp,
                                        color = AccentColor,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = r.context,
                                        fontSize = 14.sp,
                                        color = AppColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else if (!isSearching && hasSearched) {
                    // 已搜索但无结果
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.search_no_results), fontSize = 14.sp, color = LightTextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ── 文本选择数据 ──

/** 🔥 原生选择 ActionMode 触发的待处理操作 */
private data class PendingSelection(
    val selectedText: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int
)

private data class SelectionState(
    val chapterIndex: Int,
    val pageInChapter: Int,
    val charStart: Int,
    val charEnd: Int,
    val selectedText: String,
    val touchX: Float,
    val touchY: Float,
    val hasHighlight: Boolean = false,
    val hasNote: Boolean = false,
    val existingNote: com.huangder.lumibooks.domain.model.Note? = null,
    // 选区边界框（屏幕像素坐标），用于菜单定位
    val selTopY: Float = 0f,
    val selBottomY: Float = 0f,
    val selStartX: Float = 0f,
    val selEndX: Float = 0f
)

/** 查找与选区重叠的 Note（chapterIndex 匹配 + 范围相交） */
private fun findOverlappingNote(
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    chapterIndex: Int,
    selStart: Int,
    selEnd: Int
): com.huangder.lumibooks.domain.model.Note? {
    return notes.firstOrNull { n ->
        n.chapterIndex == chapterIndex && n.startPosition < selEnd && n.endPosition > selStart
    }
}

// ── 选择菜单覆盖层 ──

@Composable
private fun SelectionMenuOverlay(
    state: SelectionState?,
    readerTheme: String,
    isDragging: Boolean,
    reappearKey: Int,
    showColorPicker: Boolean = false,
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onCopy: () -> Unit,
    onRemoveHighlight: () -> Unit,
    onViewNote: () -> Unit,
    onColorPicked: (String) -> Unit = {},
    onChangeHighlightColor: (String) -> Unit = {},
    onDeleteHighlight: () -> Unit = {}
) {
    if (state == null) return
    // 拖拽手柄期间完全隐藏菜单，抬手后以新坐标重新弹出
    if (isDragging) return

    // 高亮颜色列表（来自 ui-design-spec.md）
    val highlightColors = listOf(
        "#FFEB3B" to Color(0xFFFFEB3B),  // 黄色
        "#FF8A80" to Color(0xFFFF8A80),  // 粉色
        "#69F0AE" to Color(0xFF69F0AE),  // 绿色
        "#82B1FF" to Color(0xFF82B1FF),  // 蓝色
        "#BCAAA4" to Color(0xFFBCAAA4),  // 棕色
        "#BDBDBD" to Color(0xFFBDBDBD)   // 灰色
    )

    // 颜色跟随阅读背景：深色背景→深色菜单，浅色背景→浅色菜单
    val menuBg = when (readerTheme) {
        "night"  -> Color.Black
        else     -> Color.White
    }
    val menuText = when (readerTheme) {
        "night"  -> Color.White
        else     -> Color.Black
    }
    val dividerColor = menuText.copy(alpha = 0.15f)

    // ── 宽度动画：操作菜单 360dp ↔ 颜色选择器 460dp ──
    val animMenuWidthDp by animateDpAsState(
        targetValue = if (showColorPicker) 380.dp else 360.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "menuWidth"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val menuWidthPx    = with(density) { animMenuWidthDp.toPx() }
    val menuHeightPx   = with(density) { 60.dp.toPx() }

    // 菜单定位：水平居中于选区，上下由选区位置决定
    val selCenterX = (state.selStartX + state.selEndX) / 2f
    val menuX = (selCenterX - menuWidthPx / 2f)
        .coerceIn(12f, (screenWidthPx - menuWidthPx - 12f).coerceAtLeast(12f))
    val selCenterY = (state.selTopY + state.selBottomY) / 2f
    val menuY = if (selCenterY > screenHeightPx * 0.5f) {
        (state.selTopY - menuHeightPx - 16f).coerceAtLeast(12f)
    } else {
        (state.selBottomY + 16f).coerceAtMost((screenHeightPx - menuHeightPx - 12f).coerceAtLeast(12f))
    }

    // ── 入场动画（首次显示 / 拖拽结束重新弹出） ──
    val enterAlpha = remember(reappearKey) { Animatable(0f) }
    val enterScale = remember(reappearKey) { Animatable(0.75f) }
    // 向上浮入：从菜单下方12dp处向上移动到原位
    val enterTranslateY = remember(reappearKey) { Animatable(12f) }
    LaunchedEffect(reappearKey) {
        launch { enterAlpha.animateTo(1f, tween(250)) }
        launch { enterScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        launch { enterTranslateY.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
    }

    // ── 内容切换交叉淡出淡入 ──
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(showColorPicker) {
        // 淡出 → 切换 → 淡入
        contentAlpha.animateTo(0f, tween(100))
        contentAlpha.animateTo(1f, tween(150))
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(menuX.toInt(), menuY.toInt()),
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = enterScale.value
                    scaleY = enterScale.value
                    translationY = enterTranslateY.value
                    alpha = enterAlpha.value * contentAlpha.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(22.dp))
                .background(menuBg)
                .padding(
                    horizontal = if (showColorPicker) 12.dp else 4.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showColorPicker) Arrangement.Center else Arrangement.Start
        ) {
            if (showColorPicker) {
                // 颜色选择子菜单：6个色块圆点，手动 Spacer 控制间距
                highlightColors.forEachIndexed { index, (hex, color) ->
                    if (index > 0) Spacer(Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onColorPicked(hex) }
                    )
                }
            } else if (state.hasHighlight && !state.hasNote) {
                // 🔥 纯高亮：色块改颜色 + 删除按钮（等高横向对齐）+ 搜索 + 复制
                highlightColors.forEachIndexed { index, (hex, color) ->
                    if (index > 0) Spacer(Modifier.width(10.dp))
                    val isCurrentColor = state.existingNote?.color?.equals(hex, ignoreCase = true) == true
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .then(
                                if (isCurrentColor) Modifier.border(2.dp, menuText, CircleShape)
                                else Modifier
                            )
                            .clip(CircleShape)
                            .background(color)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onChangeHighlightColor(hex) }
                    )
                }
                Spacer(Modifier.width(6.dp))
                MenuDivider(dividerColor)
                Spacer(Modifier.width(6.dp))
                // 删除按钮（垃圾桶图标，与色块等高）
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onDeleteHighlight() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.highlight_delete),
                        tint = menuText.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_search), menuText, onSearch)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_copy), menuText, onCopy)
            } else if (state.hasNote) {
                // 🔥 有笔记：查看/修改笔记 + 移除高亮 + 搜索 + 复制
                MenuChip(stringResource(R.string.menu_view_note), menuText, onViewNote)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_remove_highlight), menuText, onRemoveHighlight)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_search), menuText, onSearch)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_copy), menuText, onCopy)
            } else {
                MenuChip(stringResource(R.string.menu_highlight), menuText, onHighlight)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_note), menuText, onNote)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_search), menuText, onSearch)
                MenuDivider(dividerColor)
                MenuChip(stringResource(R.string.menu_copy), menuText, onCopy)
            }
        }
    }
}

@Composable
private fun MenuDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(18.dp)
            .background(color)
    )
}

@Composable
private fun MenuChip(label: String, textColor: Color, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// ── 笔记输入弹窗 ──

@Composable
private fun NoteInputSheet(
    visible: Boolean,
    initialText: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (!visible) return

    val sheetAlpha = remember { Animatable(0f) }
    val sheetOffset = remember { Animatable(1f) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler(onBack = onCancel)

    LaunchedEffect(visible) {
        if (visible) {
            coroutineScope {
                launch { sheetAlpha.animateTo(1f, tween(250)) }
                launch { sheetOffset.snapTo(1f); sheetOffset.animateTo(0f, tween(300, easing = AppEasing.Smooth)) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(AppColors.Scrim.copy(alpha = 0.20f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCancel() }
        )

        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .materialBottomSheetMotion(
                    entryOffset = sheetOffset.value,
                    predictiveBackProgress = predictiveBackProgress,
                    alpha = sheetAlpha.value
                )
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(bottom = 16.dp)
                .padding(AppSpace.lg)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text(stringResource(R.string.reader_notes), fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = KaiTi, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.15f)).clickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                androidx.compose.material3.TextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.note_input_placeholder), fontSize = 14.sp, color = AppColors.TextSecondary) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.BgGray,
                        unfocusedContainerColor = AppColors.BgGray,
                        focusedIndicatorColor = AppColors.Accent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    maxLines = 10
                )
            }
        }
    }
}

// ── 笔记/高亮列表弹窗（Page5 设计规范）──

// 设计规范颜色
private val AccentColor: Color @Composable get() = AppColors.Accent
private val HighlightYellow = Color(0xFFFFEB3B)
private val HighlightBg = Color(0xFFFFFBF0)
private val LightTextSecondary: Color @Composable get() = AppColors.TextSecondary
private val LightBgGray: Color @Composable get() = AppColors.BgGray
private val LightCardBg: Color @Composable get() = AppColors.CardBg

@Composable
private fun NotesListSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    bookmarks: List<com.huangder.lumibooks.domain.model.Bookmark> = emptyList(),
    onNoteClick: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDeleteNote: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onBookmarkClick: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    onDeleteBookmark: (com.huangder.lumibooks.domain.model.Bookmark) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    var pendingJumpNote by remember { mutableStateOf<com.huangder.lumibooks.domain.model.Note?>(null) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            pendingJumpNote?.let { onNoteClick(it) }
            pendingJumpNote = null
            onDismiss()
        }
    }

    var activeTag by remember { mutableStateOf("highlight") }
    val highlights = notes.filter { it.note.isEmpty() }
    val noteList = notes.filter { it.note.isNotEmpty() }
    // 追踪是否有笔记项处于"已滑开"状态，点空白处时先关闭滑开项而非关闭整个弹窗
    var anyItemRevealed by remember { mutableStateOf(false) }
    var resetRevealedKey by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        // 遮罩层：有滑开项时先关闭滑开项，否则关闭整个弹窗
        Box(
            Modifier.fillMaxSize()
                .background(AppColors.Scrim.copy(alpha = 0.20f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (anyItemRevealed) resetRevealedKey = resetRevealedKey + 1 else isClosing = true
                }
        )

        // 容器层（60% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress)
                .background(LightCardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(0.5.dp, AppColors.Divider, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.highlights_notes_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBgGray)
                        .clickable { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.reader_close), tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tab 切换器（平滑动画）
            HighlightNoteTabSwitcher(
                activeTag = activeTag,
                highlightCount = highlights.size,
                noteCount = noteList.size,
                bookmarkCount = bookmarks.size,
                onTagChange = { activeTag = it }
            )

            Spacer(Modifier.height(16.dp))

            // 列表
            if (activeTag == "bookmark") {
                // 书签列表
                if (bookmarks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_bookmarks_yet), fontSize = 14.sp, color = LightTextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(bookmarks.size, key = { bookmarks[it].id }) { idx ->
                            val bm = bookmarks[idx]
                            BookmarkListItem(
                                bookmark = bm,
                                onClick = {
                                    onBookmarkClick(bm)
                                    isClosing = true
                                },
                                onDelete = { onDeleteBookmark(bm) },
                                modifier = Modifier.animateItem()
                            )
                            if (idx < bookmarks.size - 1) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                val items = if (activeTag == "highlight") highlights else noteList
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (activeTag == "highlight") R.string.no_highlights else R.string.no_notes),
                            fontSize = 14.sp,
                            color = LightTextSecondary
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items.size, key = { items[it].id }) { idx ->
                            val item = items[idx]
                            HighlightNoteItem(
                                item = item,
                                onClick = {
                                    pendingJumpNote = item
                                    isClosing = true
                                },
                                onDelete = { onDeleteNote(item) },
                                resetRevealedKey = resetRevealedKey,
                                onRevealedChanged = { revealed -> anyItemRevealed = revealed },
                                modifier = Modifier.animateItem()
                            )
                            if (idx < items.size - 1) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightNoteTabSwitcher(
    activeTag: String,
    highlightCount: Int,
    noteCount: Int,
    bookmarkCount: Int = 0,
    onTagChange: (String) -> Unit
) {
    // 动画：白色背景指示器的位置（0=高亮，1=笔记，2=书签）
    val tabIndex = when (activeTag) {
        "highlight" -> 0f
        "note" -> 1f
        else -> 2f
    }
    val indicatorProgress by animateFloatAsState(
        targetValue = tabIndex,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tabIndicator"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(LightBgGray)
            .padding(2.dp)
    ) {
        val tabCount = 3
        val tabWidth = maxWidth / tabCount
        val indicatorOffset = tabWidth * indicatorProgress

        // 白色背景指示器（平滑移动）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(tabWidth)
                .offset(x = indicatorOffset)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.CardBg)
        )

        // Tab 文字
        Row(Modifier.fillMaxSize()) {
            // 高亮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTagChange("highlight") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.highlights_count, highlightCount),
                    fontSize = 14.sp,
                    fontWeight = if (activeTag == "highlight") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeTag == "highlight") AppColors.TextPrimary else LightTextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTagChange("note") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.notes_count, noteCount),
                    fontSize = 14.sp,
                    fontWeight = if (activeTag == "note") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeTag == "note") AppColors.TextPrimary else LightTextSecondary
                )
            }
            // 书签
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTagChange("bookmark") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.bookmarks_count, bookmarkCount),
                    fontSize = 14.sp,
                    fontWeight = if (activeTag == "bookmark") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeTag == "bookmark") AppColors.TextPrimary else LightTextSecondary
                )
            }
        }
    }
}

@Composable
private fun BookmarkListItem(
    bookmark: com.huangder.lumibooks.domain.model.Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 72.dp.toPx() }
    val deletePx = with(density) { 500.dp.toPx() }

    var isRevealed by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val displayOffset = if (isDragging) rawOffset else animOffset.value
    val progress = remember(displayOffset) { (-displayOffset / revealPx).coerceAtLeast(0f) }
    val deleteIconAlpha = remember(progress) { progress.coerceIn(0f, 1f) }
    val deleteIconTranslationX = remember(progress) { (1f - progress.coerceAtMost(1f)) * 24f }

    fun dampedOverScroll(excess: Float): Float {
        if (excess == 0f) return 0f
        val d = density.density
        val sign = if (excess > 0f) 1f else -1f
        return 40f * d * (1f - Math.exp((-kotlin.math.abs(excess) / (80f * d)).toDouble())).toFloat() * sign
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 删除按钮
        Box(Modifier.matchParentSize().padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = deleteIconAlpha; translationX = deleteIconTranslationX }
                    .size(40.dp).clip(CircleShape).background(Color(0xFFE53935))
                    .clickable(enabled = isRevealed && !isDeleting) {
                        isDeleting = true
                        scope.launch {
                            animOffset.animateTo(-deletePx, tween(250, easing = FastOutSlowInEasing))
                            onDelete()
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp)) }
        }

        // 书签卡片
        Row(
            modifier = Modifier
                .offset { IntOffset(displayOffset.toInt(), 0) }
                .graphicsLayer {
                    if (isDeleting) alpha = 1f - (-displayOffset / deletePx).coerceIn(0f, 1f)
                    if (progress > 1f) scaleX = 1f - (progress - 1f) * 0.01f
                }
                .background(AppColors.BgGray, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { rawOffset = animOffset.value; isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val from = rawOffset; animOffset.snapTo(from)
                                if (isRevealed) {
                                    if (-from < revealPx * 0.3f) {
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)); isRevealed = false
                                    } else {
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                } else {
                                    if (-from > revealPx * 0.4f) {
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f)); isRevealed = true
                                    } else {
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                }
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animOffset.snapTo(rawOffset)
                                animOffset.animateTo(if (isRevealed) -revealPx else 0f, spring(dampingRatio = 0.6f, stiffness = 300f)); rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newRaw = rawOffset + dragAmount
                            rawOffset = when {
                                newRaw < -revealPx -> -revealPx - dampedOverScroll((-newRaw) - revealPx)
                                newRaw > 0f -> dampedOverScroll(newRaw)
                                else -> newRaw
                            }
                        }
                    )
                }
                .clickable(enabled = !isRevealed, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧书签图标
            Icon(Icons.Default.Bookmark, stringResource(R.string.reader_bookmark), tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(bookmark.title, fontSize = 14.sp, color = AppColors.TextPrimary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(java.util.Date(bookmark.createdAt)),
                    fontSize = 12.sp, color = LightTextSecondary
                )
            }
        }
    }
}

@Composable
private fun HighlightNoteItem(
    item: com.huangder.lumibooks.domain.model.Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    resetRevealedKey: Int = 0,
    onRevealedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 从 note.color 解析高亮颜色，生成浅色背景版本
    val highlightColor = remember(item.color) {
        try { Color(android.graphics.Color.parseColor(item.color)) } catch (_: Exception) { Color(0xFFFFEB3B) }
    }
    val highlightBg = remember(highlightColor) { highlightColor.copy(alpha = 0.12f) }

    val density = LocalDensity.current
    val revealPx = with(density) { 72.dp.toPx() }   // 目标滑开距离（露出删除键）
    val deletePx = with(density) { 500.dp.toPx() }   // 删除动画滑出距离

    // 状态
    var isRevealed by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 🔥 父级信号：点空白处时收起已滑开的卡片
    LaunchedEffect(resetRevealedKey) {
        if (resetRevealedKey > 0 && isRevealed) {
            isRevealed = false
            animOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 280f))
            rawOffset = 0f
        }
    }
    // 通知父级当前展开状态
    LaunchedEffect(isRevealed) { onRevealedChanged(isRevealed) }

    var isDragging by remember { mutableStateOf(false) }
    val displayOffset = if (isDragging) rawOffset else animOffset.value

    // 进度 0→1（到达 revealPx 时为 1，可超出）
    val progress = remember(displayOffset) { (-displayOffset / revealPx).coerceAtLeast(0f) }
    // 删除图标：从右侧 24dp 滑入 + 淡入
    val deleteIconAlpha = remember(progress) { progress.coerceIn(0f, 1f) }
    val deleteIconTranslationX = remember(progress) { (1f - progress.coerceAtMost(1f)) * 24f }

    /** 阻尼函数：超出部分按对数衰减 */
    fun dampedOverScroll(excess: Float): Float {
        if (excess == 0f) return 0f
        val d = density.density
        val sign = if (excess > 0f) 1f else -1f
        return 40f * d * (1f - Math.exp((-kotlin.math.abs(excess) / (80f * d)).toDouble())).toFloat() * sign
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── 底层：删除按钮（固定右侧，滑入 + 淡入）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = deleteIconAlpha
                        translationX = deleteIconTranslationX
                    }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(enabled = isRevealed && !isDeleting) {
                        isDeleting = true
                        scope.launch {
                            animOffset.animateTo(-deletePx, tween(250, easing = FastOutSlowInEasing))
                            onDelete()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // ── 顶层：笔记卡片 ──
        Row(
            modifier = Modifier
                .offset { IntOffset(displayOffset.toInt(), 0) }
                .graphicsLayer {
                    // 删除动画中卡片淡出
                    if (isDeleting) alpha = 1f - (-displayOffset / deletePx).coerceIn(0f, 1f)
                    // 超出时微缩，增加弹性手感
                    if (progress > 1f) scaleX = 1f - (progress - 1f) * 0.01f
                }
                .background(highlightBg, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 🔥 对齐到动画当前位置，消除动画→拖拽切换时的跳变
                            rawOffset = animOffset.value
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val from = rawOffset
                                animOffset.snapTo(from)
                                if (isRevealed) {
                                    // 已展开状态：根据位置决定
                                    if (-from < revealPx * 0.3f) {
                                        // 滑回超过 70% → 关闭
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                        isRevealed = false
                                    } else {
                                        // 还在删除区 → 弹回露出位置
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                } else {
                                    // 未展开状态
                                    if (-from > revealPx * 0.4f) {
                                        animOffset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                        isRevealed = true
                                    } else {
                                        animOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                }
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animOffset.snapTo(rawOffset)
                                animOffset.animateTo(
                                    if (isRevealed) -revealPx else 0f,
                                    spring(dampingRatio = 0.6f, stiffness = 300f)
                                )
                                rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newRaw = rawOffset + dragAmount
                            rawOffset = when {
                                // 向左拖拽：超出 revealPx 后施加阻尼
                                newRaw < -revealPx -> {
                                    val excess = (-newRaw) - revealPx
                                    -revealPx - dampedOverScroll(excess)
                                }
                                // 向右拖拽超过原位（从展开状态滑回 + 超出）：施加阻尼
                                newRaw > 0f -> dampedOverScroll(newRaw)
                                // 正常范围：跟随手指
                                else -> newRaw
                            }
                        }
                    )
                }
                .clickable(enabled = !isRevealed, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(16.dp)
        ) {
            // 左侧高亮色竖条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(highlightColor)
            )

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.selectedText,
                    fontSize = 14.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 2
                )
                if (item.note.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.note,
                        fontSize = 13.sp,
                        color = LightTextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reader_chapter_fallback, item.chapterIndex + 1), fontSize = 12.sp, color = AccentColor)
                    Text(
                        java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt)),
                        fontSize = 12.sp,
                        color = AccentColor
                    )
                }
            }
        }
    }
}

private fun parseNoteColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (_: IllegalArgumentException) {
        Color(0xFFEBB700)
    }
}

// ── 选择手柄 Composable ──

/**
 * 文本选择手柄（圆形，深红棕色 + 白色边框）。
 * 在 Compose 层渲染，通过 [ReadView.moveSelectionHandle] 驱动选择范围变更。
 */
@Composable
private fun SelectionHandle(
    centerX: Float,
    centerY: Float,
    handleColor: Color = Color(0xFF6C231D),
    onDrag: (newCenterX: Float, newCenterY: Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    val handleSizeDp = 24.dp
    val handleRadiusPx = with(density) { handleSizeDp.toPx() / 2f }

    // 🔥 确保 pointerInput 内部捕获最新的值（避免 recompose 后使用旧 lambda）
    val currentCenterX by rememberUpdatedState(centerX)
    val currentCenterY by rememberUpdatedState(centerY)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        Modifier
            .offset {
                IntOffset(
                    (currentCenterX - handleRadiusPx).toInt(),
                    (currentCenterY - handleRadiusPx).toInt()
                )
            }
            .size(handleSizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(
                            currentCenterX + dragAmount.x,
                            currentCenterY + dragAmount.y
                        )
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f
            drawCircle(handleColor, r, Offset(cx, cy))
            drawCircle(Color.White, r, Offset(cx, cy), style = Stroke(3.dp.toPx()))
        }
    }
}
