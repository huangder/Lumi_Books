package com.ebook.reader.ui.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ebook.reader.ui.animation.AppEasing
import com.ebook.reader.ui.animation.SimpleSlideAnimation
import com.ebook.reader.ui.animation.SlideCoverAnimation
import com.ebook.reader.ui.animation.cardPressEffect
import com.ebook.reader.ui.components.ImmersiveMode
import com.ebook.reader.ui.theme.AppColors
import com.ebook.reader.ui.theme.AppRadius
import com.ebook.reader.ui.theme.AppSpace
import com.ebook.reader.ui.theme.AppType
import com.ebook.reader.ui.theme.DingliSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ReaderJsBridge(
    private val handlePageFlip: (direction: Int) -> Unit,
    private val handlePageChanged: (page: Int, total: Int) -> Unit,
    private val handleChapterFlip: (direction: Int) -> Unit,
    private val handleChapterFlipReady: (direction: Int) -> Unit,
    private val handleChapterSwapped: (direction: Int) -> Unit = {},
    private val handleCenterTap: () -> Unit,
    private val handlePaginationComplete: () -> Unit = {}
) {
    @JavascriptInterface fun onPageFlip(direction: Int) = handlePageFlip(direction)
    @JavascriptInterface fun onPageChanged(page: Int, total: Int) = handlePageChanged(page, total)
    @JavascriptInterface fun onChapterFlip(direction: Int) = handleChapterFlip(direction)
    @JavascriptInterface fun onChapterFlipReady(direction: Int) = handleChapterFlipReady(direction)
    @JavascriptInterface fun onChapterSwapped(direction: Int) = handleChapterSwapped(direction)
    @JavascriptInterface fun onCenterTap() = handleCenterTap()
    @JavascriptInterface fun onPaginationComplete() = handlePaginationComplete()
}

/** 异步执行 JS（挂起协程，不阻塞主线程） */
private suspend fun WebView.evaluateJavascriptSuspend(script: String): String {
    return suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result ->
            cont.resume(result?.removeSurrounding("\"") ?: "")
        }
    }
}

private fun buildPaginationJs(isPdf: Boolean = false, bgColor: String = "#ffffff"): String {
    val anim = if (isPdf) SimpleSlideAnimation else SlideCoverAnimation
    val animationJs = anim.buildAnimationJs(bgColor)
    return """
(function() {
    var cur = 0, total = 1, vw, vh, PAD_H, PAD_V = 70;
    var wrap = null, outer = null, animId = null;
    var BG = '$bgColor';
    window.__paginationReady = false;
    var dg = { on: false, x0: 0, y0: 0, t0: 0, cx: 0, moved: false };

    function eo(t) { return 1 - Math.pow(1 - t, 3); }
    function eb(t) { return 1 - Math.pow(1 - t, 4); }
    function gx(el) {
        var s = el.style.transform;
        if (!s || s === 'none') return 0;
        var m = s.match(/translateX\(([-\d.]+)px\)/);
        return m ? +m[1] : 0;
    }

    // ── 动画模块（可替换）──
    """ + animationJs + """

    // ── 初始化（防止重复执行）──
    var __initRunning = false;
    function init() {
    if (__initRunning) return;
    __initRunning = true;
    try {
        vw = innerWidth; vh = innerHeight;
        PAD_H = Math.round(vw * 0.12);
        window.__paginationReady = false;

        var b = document.body;
        // 单独设置属性，避免 style.cssText 覆盖已注入的字体 CSS
        b.style.margin = '0';
        b.style.padding = '0';
        b.style.overflow = 'hidden';
        b.style.width = vw + 'px';
        b.style.height = vh + 'px';
        b.style.position = 'relative';

        // 清理旧元素
        var old = b.querySelectorAll('[data-pg]');
        for (var i = 0; i < old.length; i++) old[i].remove();

        // CSS：内容边距
        if (!document.getElementById('_pg')) {
            var s = document.createElement('style'); s.id = '_pg';
            s.textContent = 'p,h1,h2,h3,h4,h5,h6,li,blockquote,pre{margin-left:' + PAD_H + 'px !important;margin-right:' + PAD_H + 'px !important;margin-bottom:12px !important;}h1,h2,h3{margin-top:' + PAD_V + 'px !important;}img{max-width:100%;height:auto;display:block;margin:8px auto;}';
            document.head.appendChild(s);
        }

        // 创建 wrapper（CSS column）
        wrap = document.createElement('div');
        wrap.setAttribute('data-pg', '1');
        wrap.style.cssText = 'position:absolute;top:0;left:0;height:' + vh + 'px;padding:' + PAD_V + 'px 0;box-sizing:border-box;column-width:' + vw + 'px;column-gap:0;column-fill:auto;overflow:hidden;background:' + BG + ';';
        var kids = [];
        for (var i = 0; i < b.children.length; i++) {
            var c = b.children[i], t = c.tagName.toLowerCase();
            if (t !== 'script' && t !== 'style' && t !== 'link' && !c.getAttribute('data-pg')) kids.push(c);
        }
        for (var i = 0; i < kids.length; i++) wrap.appendChild(kids[i]);
        b.appendChild(wrap);

        // 计算页数
        var tw = wrap.scrollWidth;
        total = Math.max(1, Math.ceil(tw / vw));
        wrap.style.width = (total * vw) + 'px';

        // outer + 动画层
        outer = document.createElement('div');
        outer.setAttribute('data-pg', '1');
        outer.style.cssText = 'position:absolute;top:0;left:0;width:' + vw + 'px;height:' + vh + 'px;overflow:hidden;background:' + BG + ';z-index:0;';
        outer.appendChild(wrap);
        b.appendChild(outer);
        initLayers(b);

        cur = 0;
        wrap.style.transform = 'none';
        // 显示 body（章节切换期间由 Kotlin 控制可见性，避免动画中途露出内容）
        if (!window.__chapterTransition) b.style.visibility = 'visible';
        window.__paginateDebug = 'OK:' + total + ' tw=' + tw + ' sw=' + wrap.scrollWidth;
        window.__paginationReady = true;
        try { AndroidBridge.onPageChanged(0, total); } catch(e) {}
        try { AndroidBridge.onPaginationComplete(); } catch(e) {}
    } catch(e) {
        window.__paginateDebug = 'ERR:' + e.message;
        // 出错时也要确保 body 可见（但章节切换期间例外）
        if (!window.__chapterTransition) try { document.body.style.visibility = 'visible'; } catch(e2) {}
    }
    }

    // ── 预渲染系统 ──
    var preRendered = {};  // {chapterIndex: {wrapper, total}}

    window.preRenderChapter = function(chapterIndex, htmlB64) {
        if (preRendered[chapterIndex]) return;
        try {
            var html = atob(htmlB64);
            var w = document.createElement('div');
            w.setAttribute('data-pg', '1');
            w.style.cssText = 'position:absolute;top:0;left:0;height:' + vh + 'px;padding:' + PAD_V + 'px 0;box-sizing:border-box;column-width:' + vw + 'px;column-gap:0;column-fill:auto;overflow:hidden;background:' + BG + ';visibility:hidden;';
            w.innerHTML = html;
            // 保留 _wrap 结构
            var wrapDiv = w.querySelector('#_wrap');
            if (!wrapDiv) {
                wrapDiv = document.createElement('div');
                wrapDiv.id = '_wrap';
                while (w.firstChild) wrapDiv.appendChild(w.firstChild);
                w.appendChild(wrapDiv);
            }
            document.body.appendChild(w);
            var tw = w.scrollWidth;
            var t = Math.max(1, Math.ceil(tw / vw));
            w.style.width = (t * vw) + 'px';
            preRendered[chapterIndex] = { wrapper: w, total: t };
        } catch(e) {}
    };

    window.usePreRendered = function(chapterIndex) {
        var pr = preRendered[chapterIndex];
        if (!pr) return false;
        // 用预渲染的 wrapper 替换当前 wrapper
        if (wrap) wrap.remove();
        wrap = pr.wrapper;
        wrap.style.visibility = 'visible';
        wrap.style.transform = 'none';
        total = pr.total;
        outer.appendChild(wrap);
        cur = 0;
        window.__currentChapterIdx = chapterIndex;
        delete preRendered[chapterIndex];
        // 清理其他预渲染
        for (var k in preRendered) {
            if (preRendered[k]) preRendered[k].wrapper.remove();
            delete preRendered[k];
        }
        try { AndroidBridge.onPageChanged(0, total); } catch(e) {}
        return true;
    };

    window.checkPreRender = function() {
        // 告诉 Kotlin 当前页码，让它决定是否预渲染
        try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
    };

    // ── 设置页 ──
    function setP(p) {
        if (p < 0 || p >= total) return;
        cur = p;
        wrap.style.transition = 'none';
        wrap.style.transform = 'translateX(' + (-p * vw) + 'px)';
        try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
    }
    window.setPageImmediate = setP;
    window.flipToLastPage = function() {
        if (!window.__paginationReady || total <= 0) { setTimeout(flipToLastPage, 50); return; }
        setP(total - 1);
    };

    // ── 重新分页（字体/主题变化后调用）──
    window.repaginate = function(keepPage) {
        if (!wrap || !outer) return;
        vw = innerWidth; vh = innerHeight;
        // 先重置宽度，强制浏览器 reflow
        wrap.style.width = 'auto';
        wrap.style.columnWidth = vw + 'px';
        // 强制 reflow 后再计算 scrollWidth
        void wrap.offsetHeight;
        var tw = wrap.scrollWidth;
        var newTotal = Math.max(1, Math.ceil(tw / vw));
        var oldTotal = total;
        var oldCur = cur;
        // 更新页数和宽度
        total = newTotal;
        wrap.style.width = (total * vw) + 'px';
        // 确保当前页在有效范围内
        if (cur < 0) cur = 0;
        if (cur >= total) cur = total - 1;
        console.log('repaginate: oldTotal=' + oldTotal + ' newTotal=' + total + ' oldCur=' + oldCur + ' newCur=' + cur + ' tw=' + tw);
        wrap.style.transform = 'translateX(' + (-cur * vw) + 'px)';
        try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
    };

    // ── 更新主题（背景色+文字色+字体大小，主题变化时调用）──
    window.updateTheme = function(bg, textColor, fontSize) {
        BG = bg;
        try { document.body.style.background = bg; } catch(e) {}
        try { if (outer) outer.style.background = bg; } catch(e) {}
        try { if (wrap) wrap.style.background = bg; } catch(e) {}
        try { if (topLayer) topLayer.style.backgroundColor = bg; } catch(e) {}
        try { if (botLayer) botLayer.style.backgroundColor = bg; } catch(e) {}
        // 更新或创建 _theme style（CSS !important 覆盖行内样式）
        try {
            var s = document.getElementById('_theme');
            if (!s) {
                s = document.createElement('style');
                s.id = '_theme';
                document.head.appendChild(s);
            }
            var fs = fontSize || '16';
            var cssText = 'body{font-size:' + fs + 'px !important;background:' + bg + ' !important;color:' + textColor + ' !important;}p,h1,h2,h3,h4,h5,h6,li,blockquote,pre{color:' + textColor + ' !important;}';
            s.textContent = cssText;
        } catch(e) {}
    };

    // ── 触摸事件 ──
    document.addEventListener('touchstart', function(e) {
        if (window.__animating) return;
        var t = e.touches[0]; dg.x0 = t.clientX; dg.y0 = t.clientY; dg.cx = t.clientX;
        dg.t0 = Date.now(); dg.on = false; dg.moved = false;
    }, {passive: true});

    document.addEventListener('touchmove', function(e) {
        if (window.__animating) return;
        var t = e.touches[0], dx = t.clientX - dg.x0, dy = Math.abs(t.clientY - dg.y0);
        if (!dg.moved && Math.abs(dx) > 8 && Math.abs(dx) > dy) { dg.moved = true; dg.on = true; }
        if (dg.on) { e.preventDefault(); dg.cx = t.clientX; aDrag(dg.cx - dg.x0); }
    }, {passive: false});

    document.addEventListener('touchend', function(e) {
        if (window.__animating) return;
        var t = e.changedTouches[0], dx = t.clientX - dg.x0, dy = Math.abs(t.clientY - dg.y0), el = Date.now() - dg.t0;
        if (dg.on) {
            dEnd(dx, el > 0 ? dx / (el / 1000) : 0);
            dg.on = false; dg.moved = false; return;
        }
        if (el < 300 && dy < 20) {
            var x = t.clientX;
            if (x > vw * 0.3 && x < vw * 0.7) { try { AndroidBridge.onCenterTap(); } catch(e) {} }
            else if (x <= vw * 0.3) {
                if (cur > 0) { tapFlip(-1); }
                else try { AndroidBridge.onChapterFlipReady(-1); } catch(e) {}
            } else {
                if (cur < total - 1) { tapFlip(1); }
                else try { AndroidBridge.onChapterFlipReady(1); } catch(e) {}
            }
        }
    }, {passive: true});

    addEventListener('resize', init);
    if (document.readyState === 'complete') init();
    else addEventListener('load', init);
})();
""".trimIndent()
}

@Composable
fun ReaderScreen(bookId: String, onNavigateBack: () -> Unit, onPageReady: () -> Unit = {}, onLoadingComplete: () -> Unit = {}, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val flipOffset = remember { Animatable(0f) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
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

    // 加载超时保护：如果 chapterHtml 非空但 10 秒内未收到 onPaginationComplete，强制关闭加载画面
    LaunchedEffect(uiState.chapterHtml) {
        if (uiState.chapterHtml.isNotEmpty() && uiState.isLoading) {
            // HTML 已加载到 WebView，等 10 秒
            kotlinx.coroutines.delay(10_000)
            // 10 秒后如果还在 loading，说明 pagination JS 可能出错了
            if (viewModel.uiState.value.isLoading) {
                android.util.Log.e("PG", "LOADING TIMEOUT after 10s! Force dismissing. htmlLen=${uiState.chapterHtml.length}")
                // 检查 JS 诊断信息
                webViewRef.value?.evaluateJavascript("window.__paginateDebug||'no_diag'") { result ->
                    android.util.Log.e("PG", "JS diag on timeout: $result")
                }
                viewModel.onPaginationDone()
            }
        }
    }
    val isChapterAnimating = remember { mutableStateOf(false) }
    val paginationDeferred = remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    // 根据主题计算菜单遮罩背景色
    val menuBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a)
        "sepia" -> Color(0xFFf5e6d3)
        "green" -> Color(0xFFe8f5e9)
        else -> Color.White
    }

    val bridge = remember {
        ReaderJsBridge(
            handlePageFlip = { },
            handlePageChanged = { page, total -> viewModel.onPageChanged(page, total) },
            handleChapterFlip = { },
            handleChapterFlipReady = { direction ->
                // 🔥 简化 Miss Path：JS 已展示 loading 占位 + 1:1 跟手拖拽
                // Kotlin 侧仅负责异步获取相邻章节 HTML 并注入 JS 完成预渲染
                // 不再使用 Compose flipOffset 动画，不再调用 loadDataWithBaseURL
                val canFlip = if (direction > 0) viewModel.uiState.value.currentChapterIndex < viewModel.uiState.value.chapterCount - 1
                else viewModel.uiState.value.currentChapterIndex > 0
                if (!canFlip) return@ReaderJsBridge
                scope.launch {
                    val adjIdx = viewModel.uiState.value.currentChapterIndex + direction
                    val html = viewModel.getAdjacentChapterHtml(adjIdx)
                    if (html != null) {
                        val b64 = android.util.Base64.encodeToString(
                            html.toByteArray(), android.util.Base64.NO_WRAP
                        )
                        val wv = webViewRef.value
                        // 注入预渲染 + 触发 onPreRenderReady（如果用户还在拖拽中则无缝替换 loading 占位）
                        wv?.evaluateJavascript(
                            "window.preRenderChapter && preRenderChapter($adjIdx, '$b64');window.onPreRenderReady && onPreRenderReady($adjIdx);"
                        ) {}
                        android.util.Log.d("PG", "Emergency preRender done: chapter $adjIdx")
                    }
                }
            },
            handleChapterSwapped = { direction -> viewModel.onChapterSwapped(direction) },
            handleCenterTap = { viewModel.toggleMenu() },
            handlePaginationComplete = {
                android.util.Log.e("PG", "onPaginationComplete CALLED")
                paginationDeferred.value?.complete(Unit)
                onPageReady()
                // 跳到保存的页码，完成后再关闭加载画面
                val state = viewModel.uiState.value
                val fraction = state.pendingPageFraction
                if (fraction > 0f && state.totalPages > 0) {
                    val targetPage = (fraction * state.totalPages).toInt()
                        .coerceIn(0, state.totalPages - 1)
                    android.util.Log.e("PG", "Will jump to page $targetPage")
                    // 延迟跳页，跳完后再延迟关闭加载画面（等浏览器重绘）
                    webViewRef.value?.postDelayed({
                        webViewRef.value?.evaluateJavascript("setPageImmediate($targetPage)") {
                            // 跳页后等 200ms 让浏览器重绘，再关闭加载画面
                            webViewRef.value?.postDelayed({
                                viewModel.clearPendingPageFraction()
                                viewModel.onPaginationDone()
                            }, 200)
                        }
                    }, 500)
                } else {
                    viewModel.onPaginationDone()
                }
                // 🔥 分页完成后立即 JS 预渲染相邻章节（绕过 Compose 重组延迟）
                val chapterIdx = viewModel.uiState.value.currentChapterIndex
                val chapterCount = viewModel.uiState.value.chapterCount
                val isPdf = viewModel.uiState.value.book?.format?.name == "PDF"
                val windowSize = if (isPdf) 5 else 2
                ((chapterIdx - windowSize)..(chapterIdx + windowSize))
                    .filter { it != chapterIdx && it in 0 until chapterCount }
                    .forEach { adjIdx ->
                        val html = viewModel.getAdjacentChapterHtml(adjIdx)
                        if (html != null) {
                            val b64 = android.util.Base64.encodeToString(
                                html.toByteArray(), android.util.Base64.NO_WRAP
                            )
                            webViewRef.value?.evaluateJavascript(
                                "window.preRenderChapter && preRenderChapter($adjIdx, '$b64')"
                            ) {}
                            android.util.Log.d("PG", "Immediate JS pre-render: chapter $adjIdx")
                        }
                    }
            }
        )
    }

    // 全屏沉浸：隐藏状态栏和手势条
    ImmersiveMode()

    // 🔥 预渲染相邻章节（激进策略：距边界 8 页就开始预渲染，短章节也能覆盖）
    LaunchedEffect(uiState.currentPageIndex, uiState.totalPages) {
        if (uiState.totalPages <= 0) return@LaunchedEffect
        val page = uiState.currentPageIndex
        val total = uiState.totalPages
        val chapterIdx = uiState.currentChapterIndex
        // 🔥 最后 8 页 → 预渲染下一章（原来 5 页，更激进）
        if (page >= total - 8) {
            val nextIdx = chapterIdx + 1
            val html = viewModel.getAdjacentChapterHtml(nextIdx)
            if (html != null) {
                webViewRef.value?.evaluateJavascript("window.preRenderChapter && preRenderChapter($nextIdx, '${android.util.Base64.encodeToString(html.toByteArray(), android.util.Base64.NO_WRAP)}')") {}
            }
            // 🔥 最后 3 页 → 预渲染下下章（原来 2 页）
            if (page >= total - 3) {
                val next2 = chapterIdx + 2
                val html2 = viewModel.getAdjacentChapterHtml(next2)
                if (html2 != null) {
                    webViewRef.value?.evaluateJavascript("window.preRenderChapter && preRenderChapter($next2, '${android.util.Base64.encodeToString(html2.toByteArray(), android.util.Base64.NO_WRAP)}')") {}
                }
            }
        }
        // 🔥 前 5 页 → 预渲染上一章（原来 3 页）
        if (page <= 5) {
            val prevIdx = chapterIdx - 1
            val html = viewModel.getAdjacentChapterHtml(prevIdx)
            if (html != null) {
                webViewRef.value?.evaluateJavascript("window.preRenderChapter && preRenderChapter($prevIdx, '${android.util.Base64.encodeToString(html.toByteArray(), android.util.Base64.NO_WRAP)}')") {}
            }
            // 🔥 前 2 页 → 预渲染上上章（原来 1 页）
            if (page <= 2) {
                val prev2 = chapterIdx - 2
                val html2 = viewModel.getAdjacentChapterHtml(prev2)
                if (html2 != null) {
                    webViewRef.value?.evaluateJavascript("window.preRenderChapter && preRenderChapter($prev2, '${android.util.Base64.encodeToString(html2.toByteArray(), android.util.Base64.NO_WRAP)}')") {}
                }
            }
        }
    }

    var displayedHtml = uiState.chapterHtml
    // 根据主题计算 Compose 层背景色
    val composeBgColor = when (uiState.readerTheme) {
        "night" -> Color(0xFF1a1a1a)
        "sepia" -> Color(0xFFf5e6d3)
        "green" -> Color(0xFFe8f5e9)
        else -> Color(0xFFFBFBFC)
    }

    Box(Modifier.fillMaxSize().background(composeBgColor)) {
        // WebView 始终在组合中（加载时也创建，确保 HTML 能加载）
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = flipOffset.value * size.width }) {
            val isPdf = uiState.book?.format?.name == "PDF"
            HtmlContent(html = displayedHtml, bridge = bridge, isPdf = isPdf, fontSize = uiState.fontSize, theme = uiState.readerTheme, chapterIndex = uiState.currentChapterIndex, onWebViewCreated = { webViewRef.value = it })
        }

        // 过渡页在 NavGraph 层处理，这里只显示阅读内容
        if (!uiState.isLoading) {
            // 顶部栏（渐变 + 返回 + 标题）—— 只有淡入淡出
            AnimatedVisibility(
                visible = uiState.isMenuVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val bookTitle = uiState.book?.title ?: ""
                ReaderTopBar(title = bookTitle, onBack = onNavigateBack, bgColor = menuBgColor)
            }
            if (uiState.totalPages > 0) {
                val chapterTitle = uiState.book?.title ?: ""
                val chapterProgress = if (uiState.chapterCount > 0) {
                    ((uiState.currentChapterIndex.toFloat() / uiState.chapterCount) * 100).toInt()
                } else 0

                // 底部渐变遮罩（底层）
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

                // 胶囊菜单（上层，遮罩之上）
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

                // 页码（章节内，居中，菜单关闭时显示）
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

            // ── 目录覆盖层 ──
            if (showToc) {
                TocOverlay(
                    chapterCount = uiState.chapterCount,
                    currentChapter = uiState.currentChapterIndex,
                    onChapterSelected = { idx ->
                        viewModel.setChapter(idx)
                        showToc = false
                        viewModel.toggleMenu() // 关闭菜单
                    },
                    onDismiss = { showToc = false }
                )
            }

            // ── 主题设置弹窗 ──
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

@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit, bgColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(140.dp) // 避开摄像头 + 遮罩向下延伸虚化背景
    ) {
        // 底层：渐变遮罩（背景色 → 透明，从上往下，有实际遮罩效果）
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

        // 顶层：按钮 + 标题
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：返回按钮（胶囊 + 按压反馈）
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

            // 右侧：书名（小字、淡色）
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

    // visible 变为 true 时重置并播放交错动画
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
            // 关闭时快速重置
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
        // 目录胶囊
        Box(modifier = Modifier.graphicsLayer {
            alpha = alpha0.value
            translationY = offset0.value
        }) {
            CatalogCapsule(chapterTitle, chapterProgress, onCatalogClick)
        }

        // 三个功能胶囊
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
        // 进度条填充（主题色，从左到右）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.Accent.copy(alpha = 0.8f))
        )
        // 文字内容（进度条下方的文字变浅色）
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
private fun ReaderMenuOverlay(uiState: ReaderUiState, onDismiss: () -> Unit, onSetChapter: (Int) -> Unit, onAddBookmark: () -> Unit) {
    val pp = if (uiState.totalPages > 0) ((uiState.currentPageIndex + 1).toFloat() / uiState.totalPages * 100).toInt() else 0

    // 背景遮罩（淡入）
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        // 底部菜单（从底部滑入 + 淡入）
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    // 动画由外部 AnimatedVisibility 控制，这里设置样式
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AppColors.CardBg)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            MenuItemRow("目录", "· ${pp}%", { Icon(Icons.Default.List, null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp)) }) {}
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = AppColors.Divider)
            MenuItemRow("书签与高亮标记", null, { Text("${uiState.currentPageIndex}", color = AppColors.TextSecondary, fontSize = 15.sp) }) { onAddBookmark() }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = AppColors.Divider)
            MenuItemRow("在图书中搜索", null, { Icon(Icons.Default.Search, null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp)) }) {}
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = AppColors.Divider)
            MenuItemRow("主题与设置", null, { Text("大小", color = AppColors.TextSecondary, fontSize = 15.sp) }) {}
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = AppColors.Divider)
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = {}) { Icon(Icons.Default.Share, "分享", tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp)) }
                IconButton(onClick = {}) { Icon(Icons.Default.Settings, "设置", tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onAddBookmark) { Icon(Icons.Default.Bookmark, "书签", tint = AppColors.TextPrimary, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
private fun MenuItemRow(title: String, subtitle: String?, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFF1C1C1E), fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (subtitle != null) { Text(subtitle, color = Color(0xFF999999), fontSize = 15.sp); Spacer(Modifier.width(8.dp)) }
        trailing()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlContent(html: String, bridge: ReaderJsBridge, isPdf: Boolean = false, fontSize: Float = 16f, theme: String = "day", chapterIndex: Int = 0, onWebViewCreated: (WebView) -> Unit = {}) {
    // 用 remember 存储可变引用，让 onPageFinished 能读取最新值（而不是闭包捕获的旧值）
    val currentFontSize = remember { mutableFloatStateOf(fontSize) }
    val currentTheme = remember { mutableStateOf(theme) }
    var prevFontSize by remember { mutableFloatStateOf(fontSize) }
    var prevTheme by remember { mutableStateOf(theme) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.apply {
                    javaScriptEnabled = true; loadWithOverviewMode = true; useWideViewPort = true
                    builtInZoomControls = false; displayZoomControls = false
                    defaultTextEncodingName = "UTF-8"; mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                var lastHtmlLen = 0
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // 页面开始加载时立即设置背景色，避免闪白
                        val th = currentTheme.value
                        val bgColor = when (th) {
                            "night" -> "#1a1a1a"
                            "sepia" -> "#f5e6d3"
                            "green" -> "#e8f5e9"
                            else -> "#ffffff"
                        }
                        view?.setBackgroundColor(android.graphics.Color.parseColor(bgColor))
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        android.util.Log.e("PG", "onPageFinished")
                        // 从 remember 变量读取最新值（不是闭包捕获的旧值）
                        val fs = currentFontSize.floatValue
                        val th = currentTheme.value
                        val bgColor = when (th) {
                            "night" -> "#1a1a1a"
                            "sepia" -> "#f5e6d3"
                            "green" -> "#e8f5e9"
                            else -> "#ffffff"
                        }
                        val textColor = when (th) {
                            "night" -> "#e0e0e0"
                            "sepia" -> "#3e2723"
                            "green" -> "#1b5e20"
                            else -> "#333333"
                        }
                        // 注入 CSS 和分页 JS（150ms 延迟确保大章节 DOM layout 完成）
                        view?.postDelayed({
                            val css = "body{font-size:${fs}px !important;background:$bgColor !important;color:$textColor !important;}p,h1,h2,h3,h4,h5,h6,li,blockquote,pre{color:$textColor !important;}"
                            // 使用 _theme id 创建样式表，确保 updateTheme 能更新它
                            view.evaluateJavascript("(function(){var s=document.getElementById('_theme');if(!s){s=document.createElement('style');s.id='_theme';document.head.appendChild(s);}s.textContent='$css';})()") {
                                // 字体 CSS 生效后再运行分页 JS（用正确的字体和背景色计算页数）
                                val paginationJsCode = buildPaginationJs(isPdf, bgColor)
                                view.evaluateJavascript(paginationJsCode) {
                                    // 2 秒后检查 JS 层分页是否成功，用于诊断
                                    view.postDelayed({
                                        view.evaluateJavascript("JSON.stringify({ready:window.__paginationReady,dbg:window.__paginateDebug||''})") { result ->
                                            android.util.Log.e("PG", "Pagination diagnostic: $result")
                                        }
                                    }, 2000)
                                }
                            }
                        }, 150)
                    }
                    override fun onReceivedError(view: WebView?, errorCode: Int, desc: String?, url: String?) {
                        android.util.Log.e("PG", "WebView error: $errorCode $desc")
                    }
                }
                // 设置初始背景色为主题色（避免切换章节时闪白）
                val initBgColor = when (currentTheme.value) {
                    "night" -> "#1a1a1a"
                    "sepia" -> "#f5e6d3"
                    "green" -> "#e8f5e9"
                    else -> "#ffffff"
                }
                setBackgroundColor(android.graphics.Color.parseColor(initBgColor))
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            // 每次重组时更新 remember 变量，确保 onPageFinished 能读到最新值
            currentFontSize.floatValue = fontSize
            currentTheme.value = theme
            // 计算当前主题的颜色
            val bgColor = when (theme) { "night" -> "#1a1a1a"; "sepia" -> "#f5e6d3"; "green" -> "#e8f5e9"; else -> "#ffffff" }
            val textColor = when (theme) { "night" -> "#e0e0e0"; "sepia" -> "#3e2723"; "green" -> "#1b5e20"; else -> "#333333" }
            android.util.Log.d("THEME", "update called: theme=$theme bgColor=$bgColor textColor=$textColor")
            val bgColorInt = android.graphics.Color.parseColor(bgColor)
            val tag = webView.tag as? String
            if (html.isNotEmpty() && tag != html.hashCode().toString()) {
                webView.tag = html.hashCode().toString()
                // 加载 HTML 前先设置 WebView 背景色，避免闪白
                webView.setBackgroundColor(bgColorInt)
                // 将背景色注入 body 标签，确保页面从渲染第一帧起就是正确的背景色
                val htmlWithBg = html.replace("<body", "<body style=\"background:$bgColor\"", ignoreCase = true)
                webView.loadDataWithBaseURL(null, htmlWithBg, "text/html", "UTF-8", null)
                // 设置当前章节索引供 JS 边界拖拽和预渲染命中检测
                webView.evaluateJavascript("window.__currentChapterIdx = $chapterIndex", null)
            }
            val css = "body{font-size:${fontSize}px !important;background:$bgColor !important;color:$textColor !important;}p,h1,h2,h3,h4,h5,h6,li,blockquote,pre{color:$textColor !important;}"
            // 检测字体大小变化，变化后需要重新分页
            val fontChanged = prevFontSize != fontSize
            if (fontChanged) prevFontSize = fontSize
            // 检测主题变化，变化后更新背景色和文字色
            val themeChanged = prevTheme != theme
            if (themeChanged) prevTheme = theme
            // 主题变化时同步更新 WebView 背景色和 CSS
            if (themeChanged) {
                webView.setBackgroundColor(bgColorInt)
            }
            // 合并 CSS 注入和主题更新到一个 JS 调用，确保同步
            val js = buildString {
                append("(function(){")
                // 更新 CSS 样式
                append("var s=document.getElementById('_theme');if(!s){s=document.createElement('style');s.id='_theme';document.head.appendChild(s);}s.textContent='$css';")
                // 始终调用 updateTheme 确保所有层同步
                append("if(window.updateTheme)window.updateTheme('$bgColor','$textColor','$fontSize');")
                append("})()")
            }
            webView.evaluateJavascript(js) {
                if (fontChanged) {
                    // 字体变化后重新分页
                    webView.evaluateJavascript("window.repaginate(true)") {}
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ─── 目录覆盖层 ────────────────────────────────────────────────

@Composable
private fun TocOverlay(
    chapterCount: Int,
    currentChapter: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 淡入动画
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
            // 标题
            Text(
                text = "目录",
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = DingliSong,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpace.md))

            // 章节列表
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

            // 关闭按钮
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
