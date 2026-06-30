package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.components.ImmersiveMode
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.huangder.lumibooks.ui.theme.DingliSong
import java.io.File

@Composable
fun PdfViewerScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ImmersiveMode()

    val book = uiState.book
    val filePath = book?.filePath
    val pageCount = uiState.chapterCount
    var showMenu by remember { mutableStateOf(false) }

    if (filePath == null || pageCount <= 0) {
        Box(Modifier.fillMaxSize().background(com.huangder.lumibooks.ui.theme.ReaderColors.Light.background), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > 200) first + 1 else first
        }
    }

    // 初始跳转到上次阅读位置
    val startPage = remember {
        ((book?.readingProgress ?: 0f) * pageCount).toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    LaunchedEffect(Unit) {
        if (startPage > 0) {
            kotlinx.coroutines.delay(300) // 等列表初始化
            listState.scrollToItem(startPage)
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

    Box(Modifier.fillMaxSize().background(com.huangder.lumibooks.ui.theme.ReaderColors.Light.background)) {
        // PDF 页面列表（双指缩放 + 拖拽 + 点击切换菜单）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        // 缩放时限制偏移范围
                        val maxOffsetX = (newScale - 1f) * size.width / 2f
                        val maxOffsetY = (newScale - 1f) * size.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        // 缩放到 1x 时重置偏移
                        if (newScale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                    }
                }
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offsetX; translationY = offsetY
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { if (scale <= 1.01f) showMenu = !showMenu }
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(pageCount) { PdfPageItem(filePath = filePath, pageIndex = it) }
            }
        }

        // ── 顶部栏（淡入淡出）──
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PdfTopBar(title = book?.title ?: "", onBack = onNavigateBack)
        }

        // ── 底部渐变遮罩 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = menuAlpha.value }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0f),
                            0.2f to Color.White.copy(alpha = 0.4f),
                            0.5f to Color.White.copy(alpha = 0.8f),
                            0.8f to Color.White.copy(alpha = 0.95f),
                            1.0f to Color.White
                        )
                    )
                )
        )

        // ── 底部胶囊菜单（同时淡入+上移）──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = menuAlpha.value; translationY = menuOffset.value }
        ) {
            PdfBottomMenu(
                chapterTitle = book?.title ?: "",
                chapterProgress = if (pageCount > 0) ((currentPage.toFloat() / pageCount) * 100).toInt() else 0,
                onCatalogClick = { /* TODO */ },
                onBookmarkClick = { /* TODO */ },
                onSearchClick = { /* TODO */ },
                onThemeClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ── 顶部栏（和 EPUB 一致）──
@Composable
private fun PdfTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White,
                        0.3f to Color.White,
                        0.6f to Color.White.copy(alpha = 0.85f),
                        1.0f to Color.White.copy(alpha = 0f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgGray.copy(alpha = 0.8f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 12.sp,
                color = AppColors.TextSecondary.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

// ── 底部胶囊菜单（和 EPUB 一致）──
@Composable
private fun PdfBottomMenu(
    chapterTitle: String,
    chapterProgress: Int,
    onCatalogClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSearchClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 目录胶囊
        PdfCatalogCapsule(title = chapterTitle, progress = chapterProgress, onClick = onCatalogClick)
        // 三个功能胶囊
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PdfActionCapsule(Icons.Default.Bookmark, "书签", Modifier.weight(1f), onBookmarkClick)
            PdfActionCapsule(Icons.Default.Search, "搜索", Modifier.weight(1f), onSearchClick)
            PdfActionCapsule(Icons.Default.Settings, "主题", Modifier.weight(1f), onThemeClick)
        }
    }
}

@Composable
private fun PdfCatalogCapsule(title: String, progress: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.BgGray)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.Accent.copy(alpha = 0.8f))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bookmark, null, tint = if (progress > 5) Color.White else AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("目录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (progress > 5) Color.White else AppColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("$progress%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (progress > 70) Color.White.copy(alpha = 0.9f) else AppColors.TextSecondary)
        }
    }
}

@Composable
private fun PdfActionCapsule(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.BgGray)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = AppColors.TextPrimary)
    }
}

// ── PDF 页面渲染（每个页面独立打开文件，避免并发冲突）──

@Composable
private fun PdfPageItem(filePath: String, pageIndex: Int) {
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(filePath, pageIndex) { bitmap = renderPdfPage(filePath, pageIndex) }
    DisposableEffect(Unit) { onDispose { bitmap?.recycle() } }

    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "第${pageIndex + 1}页",
            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Box(Modifier.fillMaxWidth().height(600.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.TextSecondary.copy(alpha = 0.4f))
        }
    }
}

private fun renderPdfPage(filePath: String, pageIndex: Int): Bitmap? {
    return try {
        val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val page = renderer.openPage(pageIndex)
        val scale = 1.5f
        val w = (page.width * scale).toInt().coerceAtLeast(1)
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        fd.close()
        android.util.Log.e("PDF", "Rendered page $pageIndex: ${w}x$h")
        bmp
    } catch (e: Exception) {
        android.util.Log.e("PDF", "Failed page $pageIndex: ${e.message}")
        null
    }
}
