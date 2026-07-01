package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ReaderViewModel.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    // 文本选择状态
    var selectionState by remember { mutableStateOf<SelectionState?>(null) }
    var showNoteInput by remember { mutableStateOf(false) }
    var showNotesList by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

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

                            override fun onLoadingChanged(isLoading: Boolean) {
                                // 新引擎的加载状态通过 onPageChanged 首次回调来结束
                            }

                            override fun onTextSelected(
                                chapterIndex: Int,
                                pageInChapter: Int,
                                charStart: Int,
                                charEnd: Int,
                                selectedText: String,
                                touchX: Float,
                                touchY: Float
                            ) {
                                // 检查该位置是否已有笔记/高亮
                                val existingNotes = viewModel.notes.value.filter {
                                    it.chapterIndex == chapterIndex &&
                                        it.startPosition <= charEnd &&
                                        it.endPosition >= charStart
                                }
                                selectionState = SelectionState(
                                    chapterIndex = chapterIndex,
                                    pageInChapter = pageInChapter,
                                    charStart = charStart,
                                    charEnd = charEnd,
                                    selectedText = selectedText,
                                    touchX = touchX,
                                    touchY = touchY,
                                    hasHighlight = existingNotes.any { it.note.isEmpty() },
                                    hasNote = existingNotes.any { it.note.isNotEmpty() },
                                    existingNote = existingNotes.firstOrNull { it.note.isNotEmpty() }
                                )
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
                chapterTitles = uiState.chapterTitles,
                currentChapter = uiState.currentChapterIndex,
                onChapterSelected = { idx ->
                    isTocJump.value = true
                    viewModel.setChapter(idx)
                    showToc = false
                    viewModel.toggleMenu()
                },
                onDismiss = { showToc = false }
            )

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                currentFontSize = uiState.fontSize,
                currentTheme = uiState.readerTheme,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onOpenAdvanced = {
                    showAdvancedSheet = true
                },
                onDismiss = { showThemeSheet = false }
            )

            // 搜索弹窗
            SearchSheet(
                visible = showSearch,
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
                    searchQuery = ""
                    searchResults = emptyList()
                    hasSearched = false
                },
                onDismiss = {
                    showSearch = false
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
                onDismiss = { showAdvancedSheet = false }
            )
        }
    }

    // ── 选择菜单 ──
    SelectionMenuOverlay(
        state = selectionState,
        onDismiss = { selectionState = null },
        onHighlight = {
            val s = selectionState ?: return@SelectionMenuOverlay
            viewModel.addNote(s.selectedText, "", s.chapterIndex, s.charStart, s.charEnd, "#FFEB3B")
            selectionState = null
        },
        onNote = {
            showNoteInput = true
        },
        onSearch = {
            val s = selectionState ?: return@SelectionMenuOverlay
            searchQuery = s.selectedText
            showSearch = true
            selectionState = null
        },
        onCopy = {
            val s = selectionState ?: return@SelectionMenuOverlay
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("selected", s.selectedText))
            selectionState = null
        },
        onRemoveHighlight = {
            val s = selectionState ?: return@SelectionMenuOverlay
            s.existingNote?.let { viewModel.deleteNote(it) }
            selectionState = null
        },
        onViewNote = {
            // TODO: 查看/修改已有笔记
        }
    )

    // ── 笔记输入容器 ──
    NoteInputSheet(
        visible = showNoteInput,
        initialText = noteInputText,
        onTextChange = { noteInputText = it },
        onConfirm = {
            val s = selectionState ?: return@NoteInputSheet
            viewModel.addNote(s.selectedText, noteInputText, s.chapterIndex, s.charStart, s.charEnd, "#FFEB3B")
            noteInputText = ""
            selectionState = null
            showNoteInput = false
        },
        onCancel = {
            noteInputText = ""
            showNoteInput = false
            selectionState = null
        }
    )

    // ── 笔记/高亮列表 ──
    NotesListSheet(
        visible = showNotesList,
        notes = viewModel.notes.collectAsState().value,
        onNoteClick = { note ->
            readViewRef.value?.jumpToChapter(note.chapterIndex, 0)
            showNotesList = false
        },
        onDeleteNote = { note -> viewModel.deleteNote(note) },
        onDismiss = { showNotesList = false }
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
            CatalogCapsule(chapterTitle, chapterProgress, capsuleBgColor, capsuleContentColor, onCatalogClick)
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
                .background(AppColors.Accent.copy(alpha = 0.8f))
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
    chapterTitles: List<String>,
    currentChapter: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
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

    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(200)) }
                launch { sheetOffset.animateTo(1f, tween(200, easing = AppEasing.Accelerate)) }
            }
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .navigationBarsPadding()
                .padding(AppSpace.lg)
        ) {
            Column {
                // 标题栏 ❌ 目录 ✅
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "关闭", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("目录", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.CardBg).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                    items(chapterTitles.size) { index ->
                        val isCurrent = index == currentChapter
                        val title = chapterTitles.getOrElse(index) { "" }.ifBlank { "第${index + 1}章" }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) AppColors.Accent.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    onChapterSelected(index)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = AppType.Body,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) AppColors.Accent else AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(AppSpace.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AppColors.BgGray)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("关闭", fontSize = AppType.Body, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * 全文搜索弹窗——底部弹出，可伸缩高度。
 * - 初始矮容器（搜索框+按钮）
 * - 有结果后平滑拉高至最多屏幕70%，超出滚动
 */
@Composable
private fun SearchSheet(
    visible: Boolean,
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

    val sheetAlpha = remember { Animatable(0f) }
    val sheetOffset = remember { Animatable(1f) }
    val hasResults = results.isNotEmpty()

    LaunchedEffect(visible) {
        if (visible) {
            coroutineScope {
                launch { sheetAlpha.animateTo(1f, tween(250)) }
                launch { sheetOffset.snapTo(1f); sheetOffset.animateTo(0f, tween(300, easing = AppEasing.Smooth)) }
            }
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(200)) }
                launch { sheetOffset.animateTo(1f, tween(200, easing = AppEasing.Accelerate)) }
            }
            onDismiss()
        }
    }

    // 搜索容器高度动画：矮 → 高
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(hasResults) {
        if (hasResults) {
            contentAlpha.animateTo(1f, tween(300, easing = AppEasing.Smooth))
        } else {
            contentAlpha.snapTo(0f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出容器
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .imePadding()
                .navigationBarsPadding()
                .padding(AppSpace.lg)
        ) {
            Column {
                // 标题栏 ❌ 搜索 ✅
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "关闭", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("搜索", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.CardBg).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                // 搜索输入框 + 按钮
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(AppColors.BgGray)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.material3.TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("输入关键词", fontSize = 14.sp, color = AppColors.TextSecondary) },
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
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (query.isNotBlank()) AppColors.Accent else AppColors.BgGray)
                            .cardPressEffect()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (query.isNotBlank()) onSearch() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.TextPrimary)
                        } else {
                            Text("搜索", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (query.isNotBlank()) Color.White else AppColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(AppSpace.md))

                // 结果区域：有结果时展开
                if (hasResults) {
                    HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                    Spacer(Modifier.height(AppSpace.sm))

                    Column(
                        modifier = Modifier
                            .graphicsLayer { alpha = contentAlpha.value }
                            .fillMaxHeight(if (results.size > 3) 0.7f else 0.35f)
                    ) {
                        Text(
                            "找到 ${results.size} 条结果",
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(bottom = AppSpace.sm)
                        )

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(results.size) { idx ->
                                val r = results[idx]
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                            onResultClick(r)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = r.chapterTitle,
                                            fontSize = AppType.Caption,
                                            color = AppColors.Accent,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = r.context,
                                            fontSize = 13.sp,
                                            color = AppColors.TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (idx < results.size - 1) {
                                    HorizontalDivider(color = AppColors.Divider.copy(alpha = 0.3f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                } else if (!isSearching && hasSearched) {
                    // 已搜索但无结果
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到匹配结果", fontSize = 14.sp, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

// ── 文本选择数据 ──

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
    val existingNote: com.huangder.lumibooks.domain.model.Note? = null
)

// ── 选择菜单覆盖层 ──

@Composable
private fun SelectionMenuOverlay(
    state: SelectionState?,
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onCopy: () -> Unit,
    onRemoveHighlight: () -> Unit,
    onViewNote: () -> Unit
) {
    if (state == null) return
    Box(
        Modifier.fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2C2C2E))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.hasHighlight || state.hasNote) {
                MenuChip("移除高亮", onRemoveHighlight)
                if (state.hasNote) {
                    MenuChip("查看笔记", onViewNote)
                    MenuChip("移除笔记", onRemoveHighlight)
                }
            } else {
                MenuChip("高亮", onHighlight)
                MenuChip("笔记", onNote)
            }
            MenuChip("搜索", onSearch)
            MenuChip("复制", onCopy)
        }
    }
}

@Composable
private fun MenuChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = Color.White,
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
                .fillMaxHeight(0.55f)
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .imePadding()
                .navigationBarsPadding()
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
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.CardBg).clickable { onConfirm() },
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

// ── 笔记/高亮列表弹窗 ──

@Composable
private fun NotesListSheet(
    visible: Boolean,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    onNoteClick: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDeleteNote: (com.huangder.lumibooks.domain.model.Note) -> Unit,
    onDismiss: () -> Unit
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

    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(200)) }
                launch { sheetOffset.animateTo(1f, tween(200, easing = AppEasing.Accelerate)) }
            }
            onDismiss()
        }
    }

    var activeTag by remember { mutableStateOf("highlight") }
    val highlights = notes.filter { it.note.isEmpty() }
    val noteList = notes.filter { it.note.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .navigationBarsPadding()
                .padding(AppSpace.lg)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "关闭", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("笔记", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.CardBg).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                    TagChip("高亮", highlights.size, activeTag == "highlight") { activeTag = "highlight" }
                    TagChip("笔记", noteList.size, activeTag == "note") { activeTag = "note" }
                }

                Spacer(Modifier.height(AppSpace.md))

                val items = if (activeTag == "highlight") highlights else noteList
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("暂无${if (activeTag == "highlight") "高亮" else "笔记"}", fontSize = 14.sp, color = AppColors.TextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items.size) { idx ->
                            val item = items[idx]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onNoteClick(item) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(item.selectedText, fontSize = 14.sp, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (item.note.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(item.note, fontSize = 13.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("第${item.chapterIndex + 1}章", fontSize = AppType.Caption, color = AppColors.Accent)
                                        Text(
                                            java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt)),
                                            fontSize = AppType.Caption,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                            if (idx < items.size - 1) {
                                HorizontalDivider(color = AppColors.Divider.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) AppColors.Accent else AppColors.BgGray)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            "$label ($count)",
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Color.White else AppColors.TextSecondary
        )
    }
}
