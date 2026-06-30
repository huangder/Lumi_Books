package com.ebook.reader.ui.reader

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
import com.ebook.reader.ui.animation.AppEasing
import com.ebook.reader.ui.animation.cardPressEffect
import com.ebook.reader.ui.components.ImmersiveMode
import com.ebook.reader.ui.reader.engine.ReadView
import com.ebook.reader.ui.reader.engine.ReadViewCallbacks
import com.ebook.reader.ui.theme.AppColors
import com.ebook.reader.ui.theme.AppRadius
import com.ebook.reader.ui.theme.AppSpace
import com.ebook.reader.ui.theme.AppType
import com.ebook.reader.ui.theme.DingliSong
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
    val menuBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a)
        "sepia" -> Color(0xFFf5e6d3)
        "green" -> Color(0xFFe8f5e9)
        else -> Color.White
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
                        startPage = uiState.currentPageIndex
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
                        onCatalogClick = { showToc = true },
                        onBookmarkClick = { viewModel.addBookmark() },
                        onSearchClick = { },
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

            // 目录覆盖层
            if (showToc) {
                TocOverlay(
                    chapterCount = uiState.chapterCount,
                    currentChapter = uiState.currentChapterIndex,
                    onChapterSelected = { idx ->
                        isTocJump.value = true
                        viewModel.setChapter(idx)
                        showToc = false
                        viewModel.toggleMenu()
                    },
                    onDismiss = { showToc = false }
                )
            }

            // 主题设置弹窗
            ThemeSettingsSheet(
                visible = showThemeSheet,
                currentFontSize = uiState.fontSize,
                currentTheme = uiState.readerTheme,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onThemeChange = { viewModel.saveReaderTheme(it) },
                onDismiss = { showThemeSheet = false }
            )
        }
    }
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
            .padding(top = 8.dp)
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
            CatalogCapsule(chapterTitle, chapterProgress, onCatalogClick)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha1.value; translationY = offset1.value
            }) {
                ActionCapsule(Icons.Default.Bookmark, "书签", Modifier.fillMaxWidth(), onBookmarkClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha2.value; translationY = offset2.value
            }) {
                ActionCapsule(Icons.Default.Search, "搜索", Modifier.fillMaxWidth(), onSearchClick)
            }
            Box(modifier = Modifier.weight(1f).graphicsLayer {
                alpha = alpha3.value; translationY = offset3.value
            }) {
                ActionCapsule(Icons.Default.Settings, "主题", Modifier.fillMaxWidth(), onThemeClick)
            }
        }
    }
}

@Composable
private fun CatalogCapsule(title: String, progress: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.BgGray)
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
        val leftColor = if (progress > 5) Color.White else AppColors.TextPrimary
        val rightColor = if (progress > 70) Color.White.copy(alpha = 0.9f) else AppColors.TextSecondary
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
private fun ActionCapsule(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.BgGray)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = AppColors.TextPrimary)
    }
}

@Composable
private fun TocOverlay(
    chapterCount: Int,
    currentChapter: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(200)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .fillMaxSize(0.7f)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.CardBg)
                .padding(AppSpace.lg)
        ) {
            Text(
                text = "目录",
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = DingliSong,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpace.md))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chapterCount) { index ->
                    val isCurrent = index == currentChapter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) AppColors.Accent.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onChapterSelected(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "第${index + 1}章",
                            fontSize = AppType.Body,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) AppColors.Accent else AppColors.TextPrimary
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
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("关闭", fontSize = AppType.Body, color = AppColors.TextSecondary)
            }
        }
    }
}
