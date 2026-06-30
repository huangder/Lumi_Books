package com.ebook.reader.ui.reader

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/**
 * TXT 格式阅读内容区：TxtPageRenderer + PageCanvas。
 * 所有页面状态为 Compose State，变更自动触发重组。
 */
@Composable
fun TxtReaderContent(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    onMenuToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = context.resources.displayMetrics.density

    // 画布像素尺寸
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val w = contentSize.width; val h = contentSize.height
    val fontSizePx = uiState.fontSize * density

    // ── 主题颜色 ──
    val (bgColor, textColor) = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a) to Color(0xFFAAAAAA)
        "sepia" -> Color(0xFFf5e6d3) to Color(0xFF5B4636)
        "green" -> Color(0xFFe8f5e9) to Color(0xFF2E7D32)
        else -> Color(0xFFFBFBFC) to Color(0xFF1A1A1A)
    }
    val bgColorInt = android.graphics.Color.rgb(
        (bgColor.red * 255).toInt(), (bgColor.green * 255).toInt(), (bgColor.blue * 255).toInt())
    val textColorInt = android.graphics.Color.rgb(
        (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt())

    // ── 页面渲染器 ──
    val renderer = remember {
        TxtPageRenderer { chapterIndex -> viewModel.getChapterText(chapterIndex) }
    }

    // ── 页面状态 (Compose State — 变更自动触发 Canvas 重组) ──
    var curPage by remember { mutableStateOf<PageData?>(null) }
    var prevPage by remember { mutableStateOf<PageData?>(null) }
    var nextPage by remember { mutableStateOf<PageData?>(null) }

    // ── Bitmap 复用池 ──
    val bitmapPool = remember { mutableListOf<Bitmap>() }
    fun acquireBitmap(): Bitmap {
        val bw = if (w > 0) w else 1080
        val bh = if (h > 0) h else 1920
        bitmapPool.removeAll { it.isRecycled }
        val b = bitmapPool.firstOrNull { it.width == bw && it.height == bh }
        if (b != null) { bitmapPool.remove(b); b.eraseColor(0); return b }
        return Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
    }
    fun releaseBitmap(bm: Bitmap?) {
        if (bm != null && !bm.isRecycled && bitmapPool.size < 4) bitmapPool.add(bm)
        else bm?.recycle()
    }

    // ── 渲染一页到 Bitmap ──
    fun renderPage(chapterIndex: Int, pageIndex: Int): PageData? {
        val bmp = acquireBitmap()
        return try { renderer.renderPage(chapterIndex, pageIndex, bmp) }
        catch (e: Exception) { releaseBitmap(bmp); Log.e("PG", "renderPage err", e); null }
    }

    // ── 获取上一页（跨章透明） ──
    fun getPrevPage(ch: Int, pg: Int): PageData? {
        if (pg > 0) return renderPage(ch, pg - 1)
        val prevCh = ch - 1
        if (prevCh < 0) return null
        val total = renderer.getPageCount(prevCh)
        return if (total > 0) renderPage(prevCh, total - 1) else null
    }

    // ── 获取下一页（跨章透明） ──
    fun getNextPage(ch: Int, pg: Int): PageData? {
        val total = renderer.getPageCount(ch)
        if (total > 0 && pg < total - 1) return renderPage(ch, pg + 1)
        val nextCh = ch + 1
        if (nextCh >= uiState.chapterCount) return null
        return renderPage(nextCh, 0)
    }

    // ── 初始化三页窗口 ──
    fun initPages(chapterIndex: Int, pageIndex: Int) {
        releaseBitmap(curPage?.bitmap); releaseBitmap(prevPage?.bitmap); releaseBitmap(nextPage?.bitmap)
        curPage = renderPage(chapterIndex, pageIndex)
        if (curPage != null) {
            prevPage = getPrevPage(chapterIndex, pageIndex)
            nextPage = getNextPage(chapterIndex, pageIndex)
        }
        Log.d("PG", "initPages ch=$chapterIndex pg=$pageIndex cur=${curPage != null} prev=${prevPage != null} next=${nextPage != null}")
    }

    // ── 翻页后滑动窗口 ──
    fun shiftForward() {
        releaseBitmap(prevPage?.bitmap)
        prevPage = curPage
        curPage = nextPage
        val c = curPage ?: return
        nextPage = getNextPage(c.chapterIndex, c.pageIndex)
        Log.d("PG", "shiftForward cur=ch${c.chapterIndex}p${c.pageIndex}")
    }
    fun shiftBackward() {
        releaseBitmap(nextPage?.bitmap)
        nextPage = curPage
        curPage = prevPage
        val c = curPage ?: return
        prevPage = getPrevPage(c.chapterIndex, c.pageIndex)
        Log.d("PG", "shiftBackward cur=ch${c.chapterIndex}p${c.pageIndex}")
    }

    // ── 进度记忆：计算初始页码 ──
    val startPage = remember(uiState.currentChapterIndex) {
        val fraction = uiState.pendingPageFraction
        if (fraction > 0f && curPage != null) {
            val total = curPage!!.chapterTotal
            if (total > 0) (fraction * total).toInt().coerceIn(0, total - 1) else uiState.currentPageIndex
        } else uiState.currentPageIndex
    }

    // ── 初始化 / 配置变更时重建 ──
    var everInited by remember { mutableStateOf(false) }
    LaunchedEffect(contentSize, uiState.fontSize, uiState.readerTheme) {
        if (w > 0 && h > 0) {
            renderer.configure(w, h, fontSizePx, fontSizePx * 0.4f, bgColorInt, textColorInt,
                isNightMode = uiState.readerTheme == "night", density = density)
            if (!everInited) {
                // 首次加载：用 progress 记住的页码
                initPages(uiState.currentChapterIndex, startPage)
                everInited = true
            } else {
                // 主题/字体变更：保持当前页重建
                val c = curPage
                if (c != null) initPages(c.chapterIndex, c.pageIndex)
            }
            if (curPage != null && uiState.isLoading) {
                viewModel.onPaginationDone()
            }
        }
    }

    // ── 章节跳转 (TOC/滑块) ──
    LaunchedEffect(uiState.currentChapterIndex) {
        if (everInited && uiState.currentChapterIndex != (curPage?.chapterIndex ?: -1)) {
            initPages(uiState.currentChapterIndex, uiState.currentPageIndex)
            if (curPage != null && uiState.isLoading) viewModel.onPaginationDone()
        }
    }

    // ── 翻页回调 ──
    val handlePageChanged: (Int, Int, Int) -> Unit = { ch, pg, total ->
        viewModel.updatePosition(ch, pg, total)
    }

    Box(modifier = modifier.fillMaxSize().background(bgColor).onSizeChanged { contentSize = it }) {
        if (w > 0 && curPage != null) {
            PageCanvas(
                curPage = curPage!!,
                prevPage = prevPage,
                nextPage = nextPage,
                bgColor = bgColor,
                onPageChanged = handlePageChanged,
                onMenuToggle = onMenuToggle,
                onShiftForward = ::shiftForward,
                onShiftBackward = ::shiftBackward
            )
        }
    }
}
