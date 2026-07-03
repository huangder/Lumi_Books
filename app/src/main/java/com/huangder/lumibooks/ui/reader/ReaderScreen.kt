package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.ImmersiveMode
import com.huangder.lumibooks.ui.reader.engine.ReadView
import com.huangder.lumibooks.ui.reader.engine.ReadViewCallbacks
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.DingliSong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(bookId: String, onNavigateBack: () -> Unit, onPageReady: () -> Unit = {}, onLoadingComplete: () -> Unit = {}, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    // ReadView 引用
    val readViewRef = remember { mutableStateOf<ReadView?>(null) }

    // TOC 跳转标记（区分用户点击 TOC 和正常翻页带来的章节变化）
    val isTocJump = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveAndPause()
            viewModel.clearError()
        }
    }

    // 监听 loading 状态，完成后通知 NavGraph 关闭过渡页
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) onLoadingComplete()
    }

    var showNotesList by remember { mutableStateOf(false) }

    // 🔥 原生选择 ActionMode 回调 → 等待笔记输入
    var pendingSelection by remember { mutableStateOf<PendingSelection?>(null) }
    var showNoteInput by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

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
    BackHandler(enabled = isAnySheetOpen) {
        when {
            showNotesList -> requestCloseNotesList = true
            showNoteInput -> showNoteInput = false  // 笔记输入没有动画，直接关闭
            showToc -> requestCloseToc = true
            showThemeSheet -> requestCloseTheme = true
            showAdvancedSheet -> requestCloseAdvanced = true
            showSearch -> requestCloseSearch = true
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ReaderViewModel.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val menuBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a)
        "sepia" -> Color(0xFFf5e6d3)
        "green" -> Color(0xFFe8f5e9)
        else -> Color.White
    }
    // 胶囊按钮背景色：基于阅读主题而非系统深色模式
    val capsuleBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF3A3A3C)
        "sepia" -> Color(0xFFE8D5C4)
        "green" -> Color(0xFFC8E6C9)
        else -> Color(0xFFEEEEEE)
    }
    val capsuleContentColor = when (uiState.readerTheme) {
        "night" -> Color(0xFFCCCCCC)
        "sepia" -> Color(0xFF4A3728)
        "green" -> Color(0xFF2E7D32)
        else -> AppColors.TextPrimary
    }
    // 目录进度条颜色：比文字深，跟随阅读主题
    val catalogProgressColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF555555)
        "sepia" -> Color(0xFFC4A88C)
        "green" -> Color(0xFFA5D6A7)
        else -> Color(0xFFD0D0D0)
    }

    // 全屏沉浸
    ImmersiveMode()

    // 主题背景色
    val composeBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a)
        "sepia" -> Color(0xFFf5e6d3)
        "green" -> Color(0xFFe8f5e9)
        else -> Color(0xFFFBFBFC)
    }

    Box(Modifier.fillMaxSize().background(composeBgColor)) {
        // ── 新 Canvas 引擎（TXT/EPUB） ──
        if (uiState.useNewEngine) {
            val bgColorInt = when (uiState.readerTheme) {
                "night" -> 0xFF1a1a1a.toInt()
                "sepia" -> 0xFFf5e6d3.toInt()
                "green" -> 0xFFe8f5e9.toInt()
                else -> 0xFFFBFBFC.toInt()
            }
            val textColorInt = when (uiState.readerTheme) {
                "night" -> 0xFFCCCCCC.toInt()
                "sepia" -> 0xFF4a3728.toInt()
                "green" -> 0xFF2e7d32.toInt()
                else -> 0xFF333333.toInt()
            }

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
                                viewModel.onNewEnginePageChanged(
                                    globalPage, chapterIndex, pageInChapter, chapterTotalPages
                                )
                            }

                            override fun onMenuToggle() {
                                viewModel.toggleMenu()
                            }

                            override fun onLoadingChanged(isLoading: Boolean) {}

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
                                        // 选区被清除
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
                    val fontSizePx = uiState.fontSize * density.density
                    readView.configure(
                        fontSizePx = fontSizePx,
                        theme = uiState.readerTheme,
                        chapterCount = uiState.chapterCount,
                        startChapter = uiState.currentChapterIndex,
                        startPage = uiState.currentPageIndex,
                        lineHeightMult = uiState.lineHeight,
                        letterSpacingDp = uiState.letterSpacing,
                        fontType = uiState.fontType,
                        marginHorizDp = uiState.marginHorizDp,
                        marginVertDp = uiState.marginVertDp
                    )
                    readView.setSavedNotes(notes)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 旧 WebView 路径（PDF） ──
        if (!uiState.useNewEngine) {
            LegacyWebViewContent(uiState, viewModel, composeBgColor)
        }

        // ── 覆盖层 UI（新旧引擎共享） ──
        if (!uiState.isLoading) {
            // 顶部栏
            AnimatedVisibility(
                visible = uiState.isMenuVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val bookTitle = uiState.book?.title ?: ""
                ReaderTopBar(title = bookTitle, onBack = onNavigateBack, bgColor = menuBgColor)
            }

            if (uiState.totalPages > 0 || uiState.useNewEngine) {
                val chapterTitle = uiState.book?.title ?: ""
                val chapterProgress = if (uiState.chapterCount > 0) {
                    ((uiState.currentChapterIndex.toFloat() / uiState.chapterCount) * 100).toInt()
                } else 0

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

                // 胶囊菜单
                Box(modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = menuAlpha.value; translationY = menuOffset.value }
                ) {
                    FloatingReaderMenu(
                        visible = uiState.isMenuVisible,
                        chapterTitle = chapterTitle,
                        chapterProgress = chapterProgress,
                        capsuleBgColor = capsuleBgColor,
                        capsuleContentColor = capsuleContentColor,
                        catalogProgressColor = catalogProgressColor,
                        onCatalogClick = { showToc = true },
                        onBookmarkClick = { showNotesList = true },
                        onSearchClick = { showSearch = true },
                        onThemeClick = { showThemeSheet = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 页码指示器
                if (!uiState.isMenuVisible) {
                    Text(
                        text = "${uiState.currentPageIndex + 1} / ${uiState.totalPages}",
                        color = AppColors.TextSecondary.copy(alpha = 0.5f),
                        fontSize = AppType.Caption,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    )
                }
            }

            // 目录底部弹出
            TocSheet(
                visible = showToc,
                requestClose = requestCloseToc,
                chapterTitles = uiState.chapterTitles,
                currentChapter = uiState.currentChapterIndex,
                onChapterSelected = { idx ->
                    isTocJump.value = true
                    viewModel.setChapter(idx)
                    showToc = false
                    requestCloseToc = false
                    viewModel.toggleMenu()
                },
                onDismiss = { showToc = false; requestCloseToc = false }
            )

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                requestClose = requestCloseTheme,
                currentFontSize = uiState.fontSize,
                currentTheme = uiState.readerTheme,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onOpenAdvanced = {
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
                    ?.take(200) ?: ""
            }
            val sheetBg = when (uiState.readerTheme) {
                "night" -> Color(0xFF1a1a1a)
                "sepia" -> Color(0xFFf5e6d3)
                "green" -> Color(0xFFe8f5e9)
                else -> Color(0xFFFBFBFC)
            }
            val sheetText = when (uiState.readerTheme) {
                "night" -> Color(0xFFCCCCCC)
                "sepia" -> Color(0xFF4a3728)
                "green" -> Color(0xFF2e7d32)
                else -> Color(0xFF333333)
            }
            AdvancedSettingsSheet(
                visible = showAdvancedSheet,
                requestClose = requestCloseAdvanced,
                previewText = previewText,
                currentLineHeight = uiState.lineHeight,
                currentLetterSpacing = uiState.letterSpacing,
                currentFontType = uiState.fontType,
                currentMarginHoriz = uiState.marginHorizDp,
                currentMarginVert = uiState.marginVertDp,
                currentBgColor = sheetBg,
                currentTextColor = sheetText,
                currentFontSizeSp = uiState.fontSize,
                onLineHeightChange = { viewModel.saveLineHeight(it) },
                onLetterSpacingChange = { viewModel.saveLetterSpacing(it) },
                onFontTypeChange = { viewModel.saveFontType(it) },
                onMarginHorizChange = { viewModel.saveMarginHoriz(it) },
                onMarginVertChange = { viewModel.saveMarginVert(it) },
                onDismiss = { showAdvancedSheet = false; requestCloseAdvanced = false }
            )
        }
    }

    // ── 笔记/高亮列表 ──
    NotesListSheet(
        visible = showNotesList,
        requestClose = requestCloseNotesList,
        notes = viewModel.notes.collectAsState().value,
        onNoteClick = { note ->
            readViewRef.value?.jumpToChapter(note.chapterIndex, 0)
            showNotesList = false
            requestCloseNotesList = false
        },
        onDeleteNote = { note -> viewModel.deleteNote(note) },
        onDismiss = { showNotesList = false; requestCloseNotesList = false }
    )

    // ── 文字选择框架（完整版） ──
    // 选择菜单覆盖层
    // 🔥 笔记输入弹窗（原生选择 ActionMode 触发"笔记"时弹出）
    NoteInputSheet(
        visible = showNoteInput,
        initialText = noteInputText,
        onTextChange = { noteInputText = it },
        onConfirm = {
            val ps = pendingSelection ?: return@NoteInputSheet
            viewModel.addNote(
                selectedText = ps.selectedText,
                noteText = noteInputText,
                chapterIndex = ps.chapterIndex,
                startPosition = ps.startPosition,
                endPosition = ps.endPosition,
                color = "#40FFEB3B"
            )
            showNoteInput = false
            pendingSelection = null
            readViewRef.value?.curPageView?.clearSelection()
        },
        onCancel = {
            showNoteInput = false
            pendingSelection = null
        }
    )
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
private fun ReaderTopBar(title: String, onBack: () -> Unit, bgColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(140.dp)
    ) {
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgGray.copy(alpha = 0.8f))
                    .cardPressEffect()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 12.sp,
                color = AppColors.TextSecondary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

@Composable
private fun FloatingReaderMenu(
    visible: Boolean,
    chapterTitle: String,
    chapterProgress: Int,
    capsuleBgColor: Color,
    capsuleContentColor: Color,
    catalogProgressColor: Color,
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
            CatalogCapsule(chapterTitle, chapterProgress, capsuleBgColor, capsuleContentColor, catalogProgressColor, onCatalogClick)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha1.value; translationY = offset1.value
            }) {
                ActionCapsule(Icons.Default.Bookmark, "笔记", capsuleBgColor, capsuleContentColor, Modifier.fillMaxWidth(), onBookmarkClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha2.value; translationY = offset2.value
            }) {
                ActionCapsule(Icons.Default.Search, "搜索", capsuleBgColor, capsuleContentColor, Modifier.fillMaxWidth(), onSearchClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha3.value; translationY = offset3.value
            }) {
                ActionCapsule(Icons.Default.Settings, "主题", capsuleBgColor, capsuleContentColor, Modifier.fillMaxWidth(), onThemeClick)
            }
        }
    }
}

@Composable
private fun CatalogCapsule(
    title: String,
    progress: Int,
    bgColor: Color,
    contentColor: Color,
    progressColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(24.dp))
                .background(progressColor)
        )
        val leftColor = if (progress > 5) Color.White else contentColor
        val rightColor = if (progress > 70) Color.White.copy(alpha = 0.9f) else contentColor.copy(alpha = 0.5f)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.List, contentDescription = null, tint = leftColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("目录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = leftColor)
            Spacer(Modifier.weight(1f))
            Text("$progress%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rightColor)
        }
    }
}

@Composable
private fun ActionCapsule(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = contentColor)
    }
}

@Composable
private fun TocSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    chapterTitles: List<String>,
    currentChapter: Int,
    onChapterSelected: (Int) -> Unit,
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
    var pendingJumpIndex by remember { mutableStateOf<Int?>(null) }

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
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（70% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .graphicsLayer { translationY = sheetOffset.value * size.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "目录",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DingliSong,
                    color = Color.Black
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
                    Icon(Icons.Default.Close, "关闭", tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 章节列表
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chapterTitles.size) { index ->
                    val isCurrent = index == currentChapter
                    val title = chapterTitles.getOrElse(index) { "" }.ifBlank { "第${index + 1}章" }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LightBgGray)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                pendingJumpIndex = index
                                isClosing = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) AccentColor else Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出容器（自适应高度）
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetOffset.value * size.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Column {
                // 标题栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "搜索",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = DingliSong,
                        color = Color.Black
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
                        Icon(Icons.Default.Close, "关闭", tint = LightTextSecondary, modifier = Modifier.size(18.dp))
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
                            placeholder = { Text("输入关键词", fontSize = 14.sp, color = LightTextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.Black),
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
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("搜索", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (query.isNotBlank()) Color.White else LightTextSecondary)
                        }
                    }
                }

                // 结果区域（有结果时显示，自适应高度）
                if (hasResults) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "找到 ${results.size} 条结果",
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
                                        color = Color.Black,
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
                        Text("未找到匹配结果", fontSize = 14.sp, color = LightTextSecondary)
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

// ── 选择菜单覆盖层 ──

@Composable
private fun SelectionMenuOverlay(
    state: SelectionState?,
    readerTheme: String,
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onCopy: () -> Unit,
    onRemoveHighlight: () -> Unit,
    onViewNote: () -> Unit
) {
    if (state == null) return

    // 根据阅读背景深浅选菜单颜色
    val isDarkTheme = readerTheme == "night"
    val menuBg = if (isDarkTheme) Color(0xFFEAEAEA) else Color(0xFF2C2C2E)
    val menuText = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val menuWidthPx = with(LocalDensity.current) { 280.dp.toPx() }
    val menuHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

    // 基于选区边界框定位菜单（非触摸点）
    val selCenterX = (state.selStartX + state.selEndX) / 2f
    val menuX = (selCenterX - menuWidthPx / 2f).coerceIn(12f, (screenWidthPx - menuWidthPx - 12f).coerceAtLeast(12f))
    // 选区中点在屏幕上半部分 → 菜单在选区下方；下半部分 → 菜单在选区上方
    val selCenterY = (state.selTopY + state.selBottomY) / 2f
    val menuY = if (selCenterY > screenHeightPx * 0.5f) {
        // 选区偏下，菜单显示在选区上方
        (state.selTopY - menuHeightPx - 16f).coerceAtLeast(12f)
    } else {
        // 选区偏上，菜单显示在选区下方
        (state.selBottomY + 16f).coerceAtMost((screenHeightPx - menuHeightPx - 12f).coerceAtLeast(12f))
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
                .clip(RoundedCornerShape(20.dp))
                .background(menuBg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.hasHighlight || state.hasNote) {
                MenuChip("移除高亮", menuText, onRemoveHighlight)
                if (state.hasNote) {
                    MenuChip("查看笔记", menuText, onViewNote)
                    MenuChip("移除笔记", menuText, onRemoveHighlight)
                }
            } else {
                MenuChip("高亮", menuText, onHighlight)
                MenuChip("笔记", menuText, onNote)
            }
            MenuChip("搜索", menuText, onSearch)
            MenuChip("复制", menuText, onCopy)
        }
    }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCancel() }
        )

        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
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
                    ) { Icon(Icons.Default.Close, "取消", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("笔记", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.15f)).clickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                androidx.compose.material3.TextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    placeholder = { Text("输入笔记...", fontSize = 14.sp, color = AppColors.TextSecondary) },
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
private val AccentColor = Color(0xFFE85D5D)
private val HighlightYellow = Color(0xFFFFEB3B)
private val HighlightBg = Color(0xFFFFFBF0)
private val LightTextSecondary = Color(0xFF6E6E73)
private val LightBgGray = Color(0xFFF2F2F7)
private val LightCardBg = Color.White

@Composable
private fun NotesListSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    onNoteClick: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDeleteNote: (com.huangder.lumibooks.domain.model.Note) -> Unit,
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

    Box(Modifier.fillMaxSize()) {
        // 遮罩层
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 容器层（60% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .graphicsLayer { translationY = sheetOffset.value * size.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(LightCardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "高亮与笔记",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DingliSong,
                    color = Color.Black
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
                    Icon(Icons.Default.Close, "关闭", tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tab 切换器（平滑动画）
            HighlightNoteTabSwitcher(
                activeTag = activeTag,
                highlightCount = highlights.size,
                noteCount = noteList.size,
                onTagChange = { activeTag = it }
            )

            Spacer(Modifier.height(16.dp))

            // 列表
            val items = if (activeTag == "highlight") highlights else noteList
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无${if (activeTag == "highlight") "高亮" else "笔记"}",
                        fontSize = 14.sp,
                        color = LightTextSecondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items.size) { idx ->
                        val item = items[idx]
                        HighlightNoteItem(
                            item = item,
                            onClick = {
                                pendingJumpNote = item
                                isClosing = true
                            },
                            onDelete = { onDeleteNote(item) }
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

@Composable
private fun HighlightNoteTabSwitcher(
    activeTag: String,
    highlightCount: Int,
    noteCount: Int,
    onTagChange: (String) -> Unit
) {
    // 动画：白色背景指示器的位置（0f = 高亮，1f = 笔记）
    val indicatorProgress by animateFloatAsState(
        targetValue = if (activeTag == "highlight") 0f else 1f,
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
        val tabWidth = maxWidth / 2
        val indicatorOffset = tabWidth * indicatorProgress

        // 白色背景指示器（平滑移动）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(tabWidth)
                .offset(x = indicatorOffset)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
        )

        // Tab 文字
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTagChange("highlight") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "高亮 ($highlightCount)",
                    fontSize = 14.sp,
                    fontWeight = if (activeTag == "highlight") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeTag == "highlight") Color.Black else LightTextSecondary
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
                    "笔记 ($noteCount)",
                    fontSize = 14.sp,
                    fontWeight = if (activeTag == "note") FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeTag == "note") Color.Black else LightTextSecondary
                )
            }
        }
    }
}

@Composable
private fun HighlightNoteItem(
    item: com.huangder.lumibooks.domain.model.Note,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 滑动删除
    var rawOffset by remember { mutableFloatStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismissThreshold = 100f

    val displayOffset = if (isDragging) rawOffset else animOffset.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        // 删除背景
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = (-displayOffset / dismissThreshold).coerceIn(0f, 1f) }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "删除", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // 内容层
        Row(
            modifier = Modifier
                .offset { IntOffset(displayOffset.toInt(), 0) }
                .clip(RoundedCornerShape(12.dp))
                .background(HighlightBg)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val from = rawOffset
                                animOffset.snapTo(from)
                                if (-from > dismissThreshold * 1.5f) {
                                    animOffset.animateTo(-600f, tween(200)); onDelete()
                                } else {
                                    animOffset.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f))
                                }
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch { animOffset.snapTo(rawOffset); animOffset.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                            rawOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            rawOffset = (rawOffset + dragAmount).coerceIn(-dismissThreshold * 2f, 0f)
                        }
                    )
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(16.dp)
        ) {
            // 左侧黄色竖条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(HighlightYellow)
            )

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.selectedText,
                    fontSize = 14.sp,
                    color = Color.Black,
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
                    Text("第${item.chapterIndex + 1}章", fontSize = 12.sp, color = AccentColor)
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
