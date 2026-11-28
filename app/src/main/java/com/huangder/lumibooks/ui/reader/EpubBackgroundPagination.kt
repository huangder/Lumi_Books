package com.huangder.lumibooks.ui.reader

import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One disposable WebView measures chapters without navigating any visible page. */
@Composable
internal fun EpubBackgroundPagination(
    session: BookRenderSession,
    signature: String,
    viewport: Pair<Int, Int>,
    appearance: EpubSpreadAppearance,
    chapterIndex: Int,
    continuous: Boolean,
    spread: Boolean,
    isIndexed: (Int) -> Boolean,
    idle: () -> Boolean,
    onCount: (String, Int, Int) -> Unit
) {
    val latestIdle = rememberUpdatedState(idle)
    val latestIndexed = rememberUpdatedState(isIndexed)
    val latestCount = rememberUpdatedState(onCount)
    var worker by remember(session, signature) { mutableStateOf<EpubContentWebView?>(null) }
    var finished by remember(session, signature) { mutableStateOf(false) }
    if (finished || viewport.first <= 0 || viewport.second <= 0) return
    key(session, signature) {
        AndroidView(
            modifier = Modifier.size(1.dp),
            factory = { context ->
                FrameLayout(context).apply {
                    alpha = 0f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    val web = EpubContentWebView(context)
                    configureEpubWebViewSettings(web)
                    web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                    web.settings.offscreenPreRaster = false
                    addView(web, FrameLayout.LayoutParams(viewport.first, viewport.second))
                    worker = web
                }
            },
            onRelease = { container ->
                (container.getChildAt(0) as? EpubContentWebView)?.destroy()
            }
        )
    }
    LaunchedEffect(worker, signature) {
        val web = worker ?: return@LaunchedEffect
        val order = (listOf(chapterIndex, chapterIndex + 1, chapterIndex - 1) +
            (0 until session.chapterCount)).filter { it in 0 until session.chapterCount }.distinct()
        delay(450)
        for (chapter in order) {
            if (latestIndexed.value(chapter)) continue
            if (session.renditionLayout(chapter) == EpubRenditionLayout.PRE_PAGINATED) {
                latestCount.value(signature, chapter, 1)
                continue
            }
            while (!latestIdle.value() && !web.destroyed) delay(120)
            if (web.destroyed) break
            val width = if (spread && !session.pagePresentation(chapter).isCover) {
                ((viewport.first - (16f * web.resources.displayMetrics.density).toInt()) / 2).coerceAtLeast(1)
            } else viewport.first
            val params = web.layoutParams
            if (params.width != width) {
                params.width = width
                web.layoutParams = params
            }
            val count = withTimeoutOrNull(8_000) {
                while (web.width != width || web.height != viewport.second) delay(16)
                measureEpubChapter(web, session, signature, appearance, chapter, continuous, spread)
            }
            if (count != null && !web.destroyed && !latestIndexed.value(chapter)) {
                latestCount.value(signature, chapter, count)
            }
            delay(120)
        }
        finished = true
    }
}

private suspend fun measureEpubChapter(
    web: EpubContentWebView,
    session: BookRenderSession,
    signature: String,
    appearance: EpubSpreadAppearance,
    chapter: Int,
    continuous: Boolean,
    spread: Boolean
): Int? {
    val url = session.chapterUrl(chapter).substringBefore('#')
    val documentReady = CompletableDeferred<Boolean>()
    web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
            session.assetLoader.shouldInterceptRequest(request.url) ?: blockedResponse()

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

        override fun onPageStarted(view: WebView, loaded: String?, icon: android.graphics.Bitmap?) {
            web.documentLifecycle.beginDocument()
        }

        override fun onPageFinished(view: WebView, loaded: String?) {
            if (loaded?.substringBefore('#') == url) documentReady.complete(true)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            documentReady.complete(false)
            web.destroy()
            return true
        }
    }
    try {
        web.settings.textZoom = epubTextZoom(appearance.fontSize, session.renditionLayout(chapter))
        web.loadUrl(url)
        if (!documentReady.await()) return null
        val configured = CompletableDeferred<Boolean>()
        configureReader(
            view = web, paginationSignature = signature, session = session, chapterIndex = chapter,
            fontType = appearance.fontType, fontFilePath = appearance.fontPath,
            letterSpacingDp = appearance.letterSpacing, textSpacing = appearance.textSpacing,
            bodyFontWeight = appearance.weight, textColorOverride = appearance.textColor, theme = appearance.theme,
            coverBackgroundColor = appearance.coverColor.takeIf { session.pagePresentation(chapter).isCover },
            readerBackgroundColor = appearance.paperColor, autoTextColor = appearance.autoTextColor,
            readerBackgroundHasImage = appearance.backgroundPath != null,
            readerBackgroundUrl = session.readerBackgroundUrl(appearance.backgroundPath),
            textAlignment = appearance.alignment, preservePublisherBackground = appearance.publisherBackground,
            imagePageCrop = appearance.crop, publisherPaintOnly = appearance.publisherPaint,
            bionicReadingEnabled = appearance.bionic, chineseMode = appearance.chinese,
            restoreLocatorJson = null, restoreProgression = 0f, restoreProgressionInclusive = false,
            initialFragment = null, continuousScroll = continuous, nativePagingEnabled = !continuous,
            pageTransition = "none", pageTransitionDurationMs = 1,
            edgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            marginTopDp = appearance.top, marginRightDp = appearance.right,
            marginBottomDp = appearance.bottom, marginLeftDp = appearance.left,
            notes = null, annotationStyle = ReaderAnnotationStyle.DEFAULT, locatorRequest = null, pageRequest = null,
            backgroundInHost = appearance.backgroundPath != null,
            useHostViewport = spread,
            onConfigured = { configured.complete(it) }
        )
        if (!configured.await()) return null
        val generation = web.documentLifecycle.configurationGeneration
        while (!web.destroyed) {
            val count = suspendCancellableCoroutine { continuation ->
                web.evaluateJavascript("window.LumiReader&&window.LumiReader.paginationMeasurement($generation)") {
                    if (continuation.isActive) continuation.resume(it.toIntOrNull())
                }
            }
            if (count != null && count > 0) return count
            delay(90)
        }
        return null
    } finally {
        if (!web.destroyed) web.stopLoading()
    }
}
