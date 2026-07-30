package com.huangder.lumibooks.ui.reader

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.ActionMode
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.huangder.lumibooks.util.ChineseConverter
import com.huangder.lumibooks.util.epub.EpubPageProgressionDirection
import com.huangder.lumibooks.util.epub.EpubRenderSession
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import org.json.JSONArray
import org.json.JSONObject
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

private const val EPUB_ALLOWED_ORIGIN = "https://appassets.androidplatform.net"

internal class EpubContentWebView(context: android.content.Context) : WebView(context) {
    private var selectionActionMode: ActionMode? = null
    private data class PendingDrawCallback(
        var remainingDraws: Int,
        val callback: () -> Unit
    )

    private val pendingDrawCallbacks = mutableListOf<PendingDrawCallback>()

    fun runAfterNextDraw(callback: () -> Unit) {
        runAfterDraws(1, callback)
    }

    fun runAfterDraws(drawCount: Int, callback: () -> Unit) {
        pendingDrawCallbacks += PendingDrawCallback(drawCount.coerceAtLeast(1), callback)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pendingDrawCallbacks.isEmpty()) return

        val completed = mutableListOf<() -> Unit>()
        val iterator = pendingDrawCallbacks.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            pending.remainingDraws -= 1
            if (pending.remainingDraws <= 0) {
                completed += pending.callback
                iterator.remove()
            }
        }
        if (pendingDrawCallbacks.isNotEmpty()) postInvalidateOnAnimation()
        if (completed.isNotEmpty()) post {
            if (isAttachedToWindow) completed.forEach { it() }
        }
    }

    override fun onDetachedFromWindow() {
        pendingDrawCallbacks.clear()
        super.onDetachedFromWindow()
    }

    fun clearTextSelection() {
        dismissSelectionUi()
        clearDocumentSelection(attempt = 0)
    }

    private fun clearDocumentSelection(attempt: Int) {
        if (!isAttachedToWindow) return
        evaluateJavascript(
            "(function(){var s=window.getSelection&&window.getSelection();" +
                "if(s){try{s.collapse(document.body||document.documentElement,0);}catch(e){}" +
                "if(s.removeAllRanges)s.removeAllRanges();if(s.empty)s.empty();}" +
                "var a=document.activeElement;if(a&&a.blur)a.blur();return true;})()"
        ) {
            dismissSelectionUi()
            if (attempt < 2) {
                postDelayed({ clearDocumentSelection(attempt + 1) }, if (attempt == 0) 32L else 96L)
            }
        }
    }

    fun dismissSelectionUi() {
        selectionActionMode?.finish()
        selectionActionMode = null
        clearFocus()
        invalidate()
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(hiddenSelectionActionMode(callback), ActionMode.TYPE_PRIMARY)

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(hiddenSelectionActionMode(callback), type)

    override fun showContextMenu(): Boolean = false

    override fun showContextMenu(x: Float, y: Float): Boolean = false

    override fun onCreateContextMenu(menu: ContextMenu) = Unit

    private fun hiddenSelectionActionMode(delegate: ActionMode.Callback) =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = delegate.onCreateActionMode(mode, menu)
                menu.clear()
                if (created) {
                    selectionActionMode = mode
                    mode.hide(300_000L)
                }
                return created
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                delegate.onPrepareActionMode(mode, menu)
                menu.clear()
                mode.hide(300_000L)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode) {
                if (selectionActionMode === mode) selectionActionMode = null
                delegate.onDestroyActionMode(mode)
            }
        }
}

internal data class EpubSearchRequest(
    val token: Int,
    val chapterIndex: Int,
    val query: String,
    val charOffset: Int,
    val chapterTextLength: Int
)

internal data class EpubLocatorRequest(
    val token: Int,
    val chapterIndex: Int,
    val locatorJson: String
)

internal data class EpubPageRequest(
    val token: Int,
    val chapterIndex: Int,
    val pageIndex: Int
)

internal data class EpubSelectionInfo(
    val text: String,
    val startLocatorJson: String,
    val endLocatorJson: String,
    val centerX: Float,
    val centerY: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private data class EpubPreloadRequest(
    val target: EpubPageTarget,
    val generation: Int
)

internal data class EpubPageText(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val text: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EpubWebViewReader(
    session: EpubRenderSession,
    chapterIndex: Int,
    fontSizeSp: Float,
    fontType: String,
    fontFilePath: String?,
    textColorOverride: Int?,
    theme: String,
    preservePublisherBackground: Boolean = true,
    bionicReadingEnabled: Boolean = false,
    chineseMode: String = "original",
    restoreLocatorJson: String?,
    restoreProgression: Float,
    initialFragment: String? = null,
    continuousScroll: Boolean = false,
    pageTransition: String = "slide",
    marginTopDp: Float = 0f,
    marginRightDp: Float = 0f,
    marginBottomDp: Float = 0f,
    marginLeftDp: Float = 0f,
    edgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    notes: List<Note> = emptyList(),
    searchRequest: EpubSearchRequest? = null,
    locatorRequest: EpubLocatorRequest? = null,
    pageRequest: EpubPageRequest? = null,
    selectionClearToken: Int = 0,
    onPageTextProviderReady: ((suspend (chapterIndex: Int, pageIndex: Int) -> EpubPageText?)?) -> Unit,
    onPageTurnHandlerReady: (((direction: Int) -> Boolean)?) -> Unit,
    onPageChanged: (pageIndex: Int, pageCount: Int, locatorJson: String?) -> Unit,
    onCenterTap: () -> Unit,
    onImagePreviewOpen: () -> Unit,
    onChapterTurn: (direction: Int) -> Unit,
    onInternalLink: (chapterIndex: Int, fragment: String?) -> Unit,
    onExternalLink: (href: String) -> Unit,
    onSelection: (EpubSelectionInfo) -> Unit,
    onSelectionCleared: () -> Unit,
    onRenderUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestPageChanged = rememberUpdatedState(onPageChanged)
    val latestCenterTap = rememberUpdatedState(onCenterTap)
    val latestImagePreviewOpen = rememberUpdatedState(onImagePreviewOpen)
    val latestChapterTurn = rememberUpdatedState(onChapterTurn)
    val latestInternalLink = rememberUpdatedState(onInternalLink)
    val latestExternalLink = rememberUpdatedState(onExternalLink)
    val latestSelection = rememberUpdatedState(onSelection)
    val latestSelectionCleared = rememberUpdatedState(onSelectionCleared)
    val latestRenderUnavailable = rememberUpdatedState(onRenderUnavailable)
    val latestChapterIndex = rememberUpdatedState(chapterIndex)
    val latestFontSizeSp = rememberUpdatedState(fontSizeSp)
    val latestFontType = rememberUpdatedState(fontType)
    val latestFontFilePath = rememberUpdatedState(fontFilePath)
    val latestTextColorOverride = rememberUpdatedState(textColorOverride)
    val latestTheme = rememberUpdatedState(theme)
    val latestPreservePublisherBackground = rememberUpdatedState(preservePublisherBackground)
    val latestBionicReadingEnabled = rememberUpdatedState(bionicReadingEnabled)
    val latestChineseMode = rememberUpdatedState(chineseMode)
    val latestRestoreLocator = rememberUpdatedState(restoreLocatorJson)
    val latestRestoreProgression = rememberUpdatedState(restoreProgression)
    val latestInitialFragment = rememberUpdatedState(initialFragment)
    val latestContinuousScroll = rememberUpdatedState(continuousScroll)
    val latestPageTransition = rememberUpdatedState(pageTransition)
    val latestMarginTopDp = rememberUpdatedState(marginTopDp)
    val latestMarginRightDp = rememberUpdatedState(marginRightDp)
    val latestMarginBottomDp = rememberUpdatedState(marginBottomDp)
    val latestMarginLeftDp = rememberUpdatedState(marginLeftDp)
    val latestEdgeTapMode = rememberUpdatedState(edgeTapMode)
    val latestNotes = rememberUpdatedState(notes)
    val latestSearchRequest = rememberUpdatedState(searchRequest)
    val latestLocatorRequest = rememberUpdatedState(locatorRequest)
    val latestPageRequest = rememberUpdatedState(pageRequest)
    val latestPageTextProviderReady = rememberUpdatedState(onPageTextProviderReady)
    val latestPageTurnHandlerReady = rememberUpdatedState(onPageTurnHandlerReady)
    val webViewState = remember(session) { mutableStateOf<WebView?>(null) }
    val pageTurnHostState = remember(session) { mutableStateOf<EpubPageTurnHost?>(null) }
    val previousPreloadTarget = remember(session) { mutableStateOf<EpubPageTarget?>(null) }
    val nextPreloadTarget = remember(session) { mutableStateOf<EpubPageTarget?>(null) }
    val previousPreloadGeneration = remember(session) { mutableStateOf(0) }
    val nextPreloadGeneration = remember(session) { mutableStateOf(0) }
    val loadedChapter = remember(session) { mutableStateOf(-1) }
    val activePageCount = remember(session) { mutableStateOf(1) }
    val configuredKey = remember(session) { mutableStateOf("") }
    val activeDocumentUrl = remember(session) { mutableStateOf("") }
    val chapterLoadPending = remember(session) { mutableStateOf(false) }
    var imagePreview by remember(session) { mutableStateOf<EpubImagePreviewRequest?>(null) }
    val imagePreviewProgress = remember(session) { Animatable(0f) }
    val imagePreviewScope = rememberCoroutineScope()
    var imagePreviewAnimationJob by remember(session) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val showImagePreview: (EpubImagePreviewRequest) -> Unit = { request ->
        latestImagePreviewOpen.value()
        imagePreviewAnimationJob?.cancel()
        imagePreview = request
        imagePreviewAnimationJob = imagePreviewScope.launch {
            imagePreviewProgress.snapTo(0f)
            imagePreviewProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }
    val dismissImagePreview: () -> Unit = dismiss@{
        if (imagePreview == null) return@dismiss
        imagePreviewAnimationJob?.cancel()
        imagePreviewAnimationJob = imagePreviewScope.launch {
            imagePreviewProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            imagePreview = null
        }
    }

    Box(modifier = modifier) {
    AndroidView(
        factory = { context ->
            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            EpubPageTurnHost(context).apply {
                val pageTurnHost = this
                val loadedChapterByView = mutableMapOf<EpubContentWebView, Int>()
                val activeVisualRequestByView = mutableMapOf<EpubContentWebView, Long>()

                fun chapterForView(view: EpubContentWebView): Int {
                    val documentUrl = view.url.orEmpty().substringBefore('#')
                    val matched = session.epubPackage.spine.indices.firstOrNull { index ->
                        session.chapterUrl(index).substringBefore('#') == documentUrl
                    }
                    if (matched != null) loadedChapterByView[view] = matched
                    return matched ?: loadedChapterByView[view] ?: latestChapterIndex.value
                }

                fun preloadRequestFor(
                    view: EpubContentWebView
                ): Pair<EpubPageTurnHost.PreloadSlot, EpubPreloadRequest>? {
                    val slot = when (pageTurnHost.roleOf(view)) {
                        EpubPageTurnHost.WebViewRole.PREVIOUS -> EpubPageTurnHost.PreloadSlot.PREVIOUS
                        EpubPageTurnHost.WebViewRole.NEXT -> EpubPageTurnHost.PreloadSlot.NEXT
                        else -> return null
                    }
                    val target = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        previousPreloadTarget.value
                    } else {
                        nextPreloadTarget.value
                    } ?: return null
                    val generation = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        previousPreloadGeneration.value
                    } else {
                        nextPreloadGeneration.value
                    }
                    return slot to EpubPreloadRequest(target, generation)
                }

                fun configurePreloadReader(view: EpubContentWebView, request: EpubPreloadRequest) {
                    val target = request.target
                    val isFixedLayout = session.epubPackage.spine.getOrNull(target.chapterIndex)
                        ?.renditionLayout == EpubRenditionLayout.PRE_PAGINATED
                    view.settings.textZoom = if (isFixedLayout) {
                        100
                    } else {
                        ((latestFontSizeSp.value / 16f) * 100f).toInt().coerceIn(50, 300)
                    }
                    configureReader(
                        view = view,
                        session = session,
                        chapterIndex = target.chapterIndex,
                        fontType = latestFontType.value,
                        fontFilePath = latestFontFilePath.value,
                        textColorOverride = latestTextColorOverride.value,
                        theme = latestTheme.value,
                        preservePublisherBackground = latestPreservePublisherBackground.value,
                        bionicReadingEnabled = latestBionicReadingEnabled.value,
                        chineseMode = latestChineseMode.value,
                        restoreLocatorJson = null,
                        restoreProgression = if (target.pageIndex == Int.MAX_VALUE) 1f else 0f,
                        initialFragment = null,
                        continuousScroll = false,
                        nativePagingEnabled = true,
                        pageTransition = "none",
                        edgeTapMode = latestEdgeTapMode.value,
                        marginTopDp = latestMarginTopDp.value,
                        marginRightDp = latestMarginRightDp.value,
                        marginBottomDp = latestMarginBottomDp.value,
                        marginLeftDp = latestMarginLeftDp.value,
                        notes = if (target.chapterIndex == latestChapterIndex.value) {
                            latestNotes.value
                        } else {
                            emptyList()
                        },
                        searchRequest = null,
                        locatorRequest = null,
                        pageRequest = EpubPageRequest(
                            token = request.generation,
                            chapterIndex = target.chapterIndex,
                            pageIndex = target.pageIndex
                        ),
                        preparePageRequest = true
                    )
                }

                fun updatePreload(
                    slot: EpubPageTurnHost.PreloadSlot,
                    target: EpubPageTarget?
                ) {
                    val targetState = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        previousPreloadTarget
                    } else {
                        nextPreloadTarget
                    }
                    val generationState = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        previousPreloadGeneration
                    } else {
                        nextPreloadGeneration
                    }
                    val view = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        pageTurnHost.previousWebView
                    } else {
                        pageTurnHost.nextWebView
                    }
                    if (targetState.value == target && pageTurnHost.preloadTarget(slot) == target) return
                    targetState.value = target
                    generationState.value += 1
                    val generation = generationState.value
                    val reusedCurrentPage = pageTurnHost.markPreloadLoading(slot, target, generation)
                    if (target == null) {
                        view.stopLoading()
                        return
                    }
                    if (reusedCurrentPage) return
                    val request = EpubPreloadRequest(target, generation)
                    if (loadedChapterByView[view] != target.chapterIndex) {
                        loadedChapterByView[view] = target.chapterIndex
                        view.loadUrl(session.chapterUrl(target.chapterIndex))
                    } else {
                        configurePreloadReader(view, request)
                    }
                }

                fun updateAdjacentPreloads(currentChapter: Int, currentPage: Int, pageCount: Int) {
                    val previous = when {
                        currentPage > 0 -> EpubPageTarget(currentChapter, currentPage - 1)
                        currentChapter > 0 -> EpubPageTarget(currentChapter - 1, Int.MAX_VALUE)
                        else -> null
                    }
                    val next = when {
                        currentPage + 1 < pageCount -> EpubPageTarget(currentChapter, currentPage + 1)
                        currentChapter + 1 < session.epubPackage.spine.size ->
                            EpubPageTarget(currentChapter + 1, 0)
                        else -> null
                    }
                    updatePreload(EpubPageTurnHost.PreloadSlot.PREVIOUS, previous)
                    updatePreload(EpubPageTurnHost.PreloadSlot.NEXT, next)
                }

                fun invalidateAdjacentPreloads() {
                    previousPreloadTarget.value = null
                    previousPreloadGeneration.value += 1
                    pageTurnHost.markPreloadLoading(
                        EpubPageTurnHost.PreloadSlot.PREVIOUS,
                        null,
                        previousPreloadGeneration.value
                    )
                    nextPreloadTarget.value = null
                    nextPreloadGeneration.value += 1
                    pageTurnHost.markPreloadLoading(
                        EpubPageTurnHost.PreloadSlot.NEXT,
                        null,
                        nextPreloadGeneration.value
                    )
                }

                fun handlePreparedMessage(view: EpubContentWebView, payload: JSONObject) {
                    val (slot, request) = preloadRequestFor(view) ?: return
                    if (payload.optInt("requestToken", Int.MIN_VALUE) != request.generation) return
                    val target = request.target
                    val expectedUrl = session.chapterUrl(target.chapterIndex).substringBefore('#')
                    if (view.url.orEmpty().substringBefore('#') != expectedUrl) return
                    val actualPage = payload.optInt("pageIndex", 0).coerceAtLeast(0)
                    val actualCount = payload.optInt("pageCount", 1).coerceAtLeast(1)
                    val targetReached = if (target.pageIndex == Int.MAX_VALUE) {
                        actualPage == actualCount - 1
                    } else {
                        actualPage == target.pageIndex
                    }
                    if (!targetReached) return
                    val visualRequestId = System.nanoTime()
                    view.postVisualStateCallback(
                        visualRequestId,
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (requestId != visualRequestId ||
                                    preloadRequestFor(view)?.second != request
                                ) return
                                view.runAfterNextDraw {
                                    if (preloadRequestFor(view)?.second != request) {
                                        return@runAfterNextDraw
                                    }
                                    if (view.url.orEmpty().substringBefore('#') != expectedUrl) {
                                        return@runAfterNextDraw
                                    }
                                    pageTurnHost.markPreloadReady(
                                        slot,
                                        target,
                                        request.generation,
                                        actualPage,
                                        view
                                    )
                                }
                            }
                        }
                    )
                }

                fun handleActiveMessage(
                    view: EpubContentWebView,
                    type: String,
                    payload: JSONObject
                ) {
                    if (pageTurnHost.roleOf(view) != EpubPageTurnHost.WebViewRole.ACTIVE) return
                    val messageChapterIndex = chapterForView(view)
                    when (type) {
                        "ready", "page" -> {
                            val expectedDocumentUrl = session.chapterUrl(messageChapterIndex)
                                .substringBefore('#')
                            if (view.url.orEmpty().substringBefore('#') != expectedDocumentUrl) return
                            val pageIndex = payload.optInt("pageIndex", 0).coerceAtLeast(0)
                            val pageCount = payload.optInt("pageCount", 1).coerceAtLeast(1)
                            activePageCount.value = pageCount
                            val locator = payload.optJSONObject("locator")?.withChapterHref(
                                session.epubPackage.spine.getOrNull(messageChapterIndex)
                                    ?.manifestItem?.fullPath.orEmpty()
                            )?.toString()
                            val packageRtl = session.epubPackage.pageProgressionDirection ==
                                EpubPageProgressionDirection.RTL
                            pageTurnHost.setReverseAxis(
                                payload.optBoolean("reverseAxis", packageRtl)
                            )
                            val visualRequestId =
                                (activeVisualRequestByView[view] ?: 0L) + 1L
                            activeVisualRequestByView[view] = visualRequestId
                            view.postVisualStateCallback(
                                visualRequestId,
                                object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        if (activeVisualRequestByView[view] != requestId ||
                                            pageTurnHost.roleOf(view) !=
                                            EpubPageTurnHost.WebViewRole.ACTIVE ||
                                            loadedChapterByView[view] != messageChapterIndex
                                        ) return
                                        val commitPage = commit@{
                                            if (activeVisualRequestByView[view] != requestId ||
                                                pageTurnHost.roleOf(view) !=
                                                EpubPageTurnHost.WebViewRole.ACTIVE
                                            ) return@commit
                                            pageTurnHost.setCurrentPage(
                                                messageChapterIndex,
                                                pageIndex
                                            ) {
                                                updateAdjacentPreloads(
                                                    messageChapterIndex,
                                                    pageIndex,
                                                    pageCount
                                                )
                                                latestPageChanged.value(
                                                    pageIndex,
                                                    pageCount,
                                                    locator
                                                )
                                            }
                                        }
                                        if (pageTurnHost.isAwaitingPage(
                                                messageChapterIndex,
                                                pageIndex
                                            )
                                        ) {
                                            view.runAfterNextDraw(commitPage)
                                        } else {
                                            commitPage()
                                        }
                                    }
                                }
                            )
                            if (type == "ready") {
                                chapterLoadPending.value = false
                                view.animate().cancel()
                                if (pageTurnHost.hasPendingPageHandoff()) {
                                    pageTurnHost.keepActiveWebViewCoveredForHandoff()
                                    view.postInvalidateOnAnimation()
                                } else {
                                    view.animate().alpha(1f).setDuration(170L).start()
                                }
                            }
                        }
                        "tap" -> when (payload.optString("zone")) {
                            "left" -> {
                                val action = latestEdgeTapMode.value.leftAction
                                if (!pageTurnHost.turnFromTap(action.toEpubTurnDirection())) {
                                    view.turnEpubPage(action)
                                }
                            }
                            "right" -> {
                                val action = latestEdgeTapMode.value.rightAction
                                if (!pageTurnHost.turnFromTap(action.toEpubTurnDirection())) {
                                    view.turnEpubPage(action)
                                }
                            }
                            "center" -> latestCenterTap.value()
                        }
                        "chapterTurn" -> {
                            val direction = payload.optInt("direction", 0)
                            if (direction != 0 && !pageTurnHost.requestTurn(direction)) {
                                latestChapterTurn.value(direction)
                            }
                        }
                        "link" -> {
                            val href = payload.optString("href").trim()
                            if (href.isEmpty()) return
                            val target = session.resolveInternalLink(messageChapterIndex, href)
                            if (target != null) {
                                latestInternalLink.value(target.first, target.second)
                            } else {
                                latestExternalLink.value(href)
                            }
                        }
                        "image" -> {
                            val source = payload.optString("source").trim()
                            if (source.isEmpty()) return
                            val pixelRatio = payload.optDouble(
                                "pixelRatio",
                                view.resources.displayMetrics.density.toDouble()
                            ).toFloat().coerceAtLeast(1f)
                            showImagePreview(
                                EpubImagePreviewRequest(
                                    source,
                                    payload.optString("alt"),
                                    payload.optDouble("left", 0.0).toFloat() * pixelRatio,
                                    payload.optDouble("top", 0.0).toFloat() * pixelRatio,
                                    payload.optDouble("right", 0.0).toFloat() * pixelRatio,
                                    payload.optDouble("bottom", 0.0).toFloat() * pixelRatio,
                                    payload.optInt("naturalWidth", 0).coerceAtLeast(0),
                                    payload.optInt("naturalHeight", 0).coerceAtLeast(0)
                                )
                            )
                        }
                        "selectionCleared" -> {
                            view.dismissSelectionUi()
                            latestSelectionCleared.value()
                        }
                        "selection" -> {
                            val selectedText = payload.optString("text")
                            val start = payload.optJSONObject("start") ?: return
                            val end = payload.optJSONObject("end") ?: return
                            val href = session.epubPackage.spine.getOrNull(messageChapterIndex)
                                ?.manifestItem?.fullPath.orEmpty()
                            val location = IntArray(2)
                            view.getLocationInWindow(location)
                            val pixelRatio = payload.optDouble(
                                "pixelRatio",
                                view.resources.displayMetrics.density.toDouble()
                            ).toFloat().coerceAtLeast(1f)
                            fun windowCoordinate(name: String, fallback: Double): Float =
                                payload.optDouble(name, fallback).toFloat() * pixelRatio
                            latestSelection.value(
                                EpubSelectionInfo(
                                    selectedText,
                                    start.withChapterHref(href).toString(),
                                    end.withChapterHref(href).toString(),
                                    location[0] + windowCoordinate("x", 0.0),
                                    location[1] + windowCoordinate("y", 0.0),
                                    location[0] + windowCoordinate(
                                        "left",
                                        payload.optDouble("x", 0.0)
                                    ),
                                    location[1] + windowCoordinate(
                                        "top",
                                        payload.optDouble("y", 0.0)
                                    ),
                                    location[0] + windowCoordinate(
                                        "right",
                                        payload.optDouble("x", 0.0)
                                    ),
                                    location[1] + windowCoordinate(
                                        "bottom",
                                        payload.optDouble("y", 0.0)
                                    )
                                )
                            )
                        }
                    }
                }

                fun attachWebView(view: EpubContentWebView) {
                    configureEpubWebViewSettings(view)
                    if (!WebViewFeature.isFeatureSupported(
                            WebViewFeature.WEB_MESSAGE_LISTENER
                        )
                    ) {
                        view.settings.javaScriptEnabled = false
                        if (pageTurnHost.roleOf(view) ==
                            EpubPageTurnHost.WebViewRole.ACTIVE
                        ) {
                            view.post { latestRenderUnavailable.value() }
                        }
                    } else {
                        WebViewCompat.addWebMessageListener(
                            view,
                            "lumiNative",
                            setOf(EPUB_ALLOWED_ORIGIN),
                            object : WebViewCompat.WebMessageListener {
                                override fun onPostMessage(
                                    sourceView: WebView,
                                    message: WebMessageCompat,
                                    sourceOrigin: Uri,
                                    isMainFrame: Boolean,
                                    replyProxy: JavaScriptReplyProxy
                                ) {
                                    if (!isMainFrame || sourceOrigin.scheme != "https" ||
                                        sourceOrigin.host != EpubRenderSession.ASSET_DOMAIN ||
                                        sourceOrigin.port != -1
                                    ) return
                                    val contentView = sourceView as? EpubContentWebView ?: return
                                    val root = runCatching {
                                        JSONObject(message.data ?: return)
                                    }.getOrNull() ?: return
                                    val type = root.optString("type")
                                    val payload = root.optJSONObject("payload") ?: JSONObject()
                                    if (type == "pagePrepared") {
                                        handlePreparedMessage(contentView, payload)
                                    } else {
                                        handleActiveMessage(contentView, type, payload)
                                    }
                                }
                            }
                        )
                    }

                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            sourceView: WebView?,
                            request: WebResourceRequest
                        ): WebResourceResponse {
                            return session.assetLoader.shouldInterceptRequest(request.url)
                                ?: blockedResponse()
                        }

                        override fun shouldOverrideUrlLoading(
                            sourceView: WebView?,
                            request: WebResourceRequest
                        ): Boolean {
                            val contentView = sourceView as? EpubContentWebView ?: return true
                            if (pageTurnHost.roleOf(contentView) !=
                                EpubPageTurnHost.WebViewRole.ACTIVE
                            ) return true
                            val sourceChapter = chapterForView(contentView)
                            val url = request.url.toString()
                            val internal = session.resolveInternalLink(sourceChapter, url)
                            if (internal != null) {
                                latestInternalLink.value(internal.first, internal.second)
                            } else {
                                latestExternalLink.value(url)
                            }
                            return true
                        }

                        override fun onReceivedError(
                            sourceView: WebView,
                            failedRequest: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            super.onReceivedError(sourceView, failedRequest, error)
                            if (!failedRequest.isForMainFrame) return
                            val contentView = sourceView as? EpubContentWebView ?: return
                            val (slot, request) = preloadRequestFor(contentView) ?: return
                            pageTurnHost.markPreloadFailed(
                                slot,
                                request.target,
                                request.generation
                            )
                        }

                        override fun onReceivedHttpError(
                            sourceView: WebView,
                            failedRequest: WebResourceRequest,
                            errorResponse: WebResourceResponse
                        ) {
                            super.onReceivedHttpError(
                                sourceView,
                                failedRequest,
                                errorResponse
                            )
                            if (!failedRequest.isForMainFrame) return
                            val contentView = sourceView as? EpubContentWebView ?: return
                            val (slot, request) = preloadRequestFor(contentView) ?: return
                            pageTurnHost.markPreloadFailed(
                                slot,
                                request.target,
                                request.generation
                            )
                        }

                        override fun onPageFinished(sourceView: WebView, url: String?) {
                            val contentView = sourceView as? EpubContentWebView ?: return
                            if (pageTurnHost.roleOf(contentView) !=
                                EpubPageTurnHost.WebViewRole.ACTIVE
                            ) {
                                val (_, request) = preloadRequestFor(contentView) ?: return
                                val expectedUrl = session.chapterUrl(
                                    request.target.chapterIndex
                                ).substringBefore('#')
                                if (url.orEmpty().substringBefore('#') == expectedUrl) {
                                    configurePreloadReader(contentView, request)
                                }
                                return
                            }

                            val sourceChapter = chapterForView(contentView)
                            val expectedUrl = session.chapterUrl(sourceChapter).substringBefore('#')
                            if (url.orEmpty().substringBefore('#') != expectedUrl) return
                            chapterLoadPending.value = false
                            val nativePageTurn = usesNativeEpubPageTurn(
                                session,
                                sourceChapter,
                                latestContinuousScroll.value,
                                latestPageTransition.value
                            )
                            configureReader(
                                view = contentView,
                                session = session,
                                chapterIndex = sourceChapter,
                                fontType = latestFontType.value,
                                fontFilePath = latestFontFilePath.value,
                                textColorOverride = latestTextColorOverride.value,
                                theme = latestTheme.value,
                                preservePublisherBackground =
                                    latestPreservePublisherBackground.value,
                                bionicReadingEnabled = latestBionicReadingEnabled.value,
                                chineseMode = latestChineseMode.value,
                                restoreLocatorJson = latestRestoreLocator.value,
                                restoreProgression = latestRestoreProgression.value,
                                initialFragment = latestInitialFragment.value,
                                continuousScroll = latestContinuousScroll.value,
                                nativePagingEnabled = nativePageTurn,
                                pageTransition = if (nativePageTurn) {
                                    "none"
                                } else {
                                    latestPageTransition.value
                                },
                                edgeTapMode = latestEdgeTapMode.value,
                                marginTopDp = latestMarginTopDp.value,
                                marginRightDp = latestMarginRightDp.value,
                                marginBottomDp = latestMarginBottomDp.value,
                                marginLeftDp = latestMarginLeftDp.value,
                                notes = latestNotes.value,
                                searchRequest = latestSearchRequest.value,
                                locatorRequest = latestLocatorRequest.value,
                                pageRequest = latestPageRequest.value
                            )
                        }
                    }
                }

                allWebViews().forEach(::attachWebView)

                pageTurnHost.onPageCommit = { direction, target ->
                    if (target.chapterIndex == latestChapterIndex.value) {
                        pageTurnHost.activeWebView.evaluateJavascript(
                            "window.LumiReader&&window.LumiReader.goToPage(" +
                                target.pageIndex + ");",
                            null
                        )
                    } else {
                        latestChapterTurn.value(direction)
                    }
                }
                pageTurnHost.onBusyEdgeTapDirection = { isLeftEdge ->
                    val action = if (isLeftEdge) {
                        latestEdgeTapMode.value.leftAction
                    } else {
                        latestEdgeTapMode.value.rightAction
                    }
                    if (action == ReaderEdgeTapAction.NEXT_PAGE) 1 else -1
                }

                webViewState.value = activeWebView
                pageTurnHostState.value = pageTurnHost
            }
        },
        update = { pageTurnHost ->
            val webView = pageTurnHost.activeWebView
            webViewState.value = webView
            val isFixedLayout = session.epubPackage.spine.getOrNull(chapterIndex)?.renditionLayout ==
                EpubRenditionLayout.PRE_PAGINATED
            val nativePageTurn = usesNativeEpubPageTurn(
                session = session,
                chapterIndex = chapterIndex,
                continuousScroll = continuousScroll,
                transition = pageTransition
            )
            pageTurnHost.setNativePagingEnabled(nativePageTurn)
            pageTurnHost.setNativeTouchPagingEnabled(nativePageTurn)
            if (nativePageTurn) pageTurnHost.setTransition(pageTransition)
            val fallbackBackground = when (theme) {
                "night" -> Color.rgb(0x11, 0x11, 0x11)
                "sepia" -> Color.rgb(0xF5, 0xE6, 0xD3)
                "green" -> Color.rgb(0xE8, 0xF5, 0xE9)
                else -> Color.WHITE
            }
            pageTurnHost.setPageBackgroundColor(fallbackBackground)
            webView.settings.textZoom = if (isFixedLayout) {
                100
            } else {
                ((fontSizeSp / 16f) * 100f).toInt().coerceIn(50, 300)
            }
            val nextConfigKey = configKey(
                chapterIndex, fontSizeSp, fontType, fontFilePath, textColorOverride, theme,
                preservePublisherBackground, bionicReadingEnabled, chineseMode, continuousScroll,
                pageTransition, edgeTapMode, marginTopDp, marginRightDp, marginBottomDp, marginLeftDp, initialFragment,
                searchRequest, locatorRequest, pageRequest
            )
            if (loadedChapter.value != chapterIndex) {
                val firstLoad = loadedChapter.value < 0
                val targetChapter = chapterIndex
                val targetUrl = session.chapterUrl(chapterIndex, initialFragment)
                loadedChapter.value = targetChapter
                configuredKey.value = ""
                chapterLoadPending.value = true
                webView.animate().cancel()
                val loadTarget = Runnable {
                    if (loadedChapter.value == targetChapter) {
                        activeDocumentUrl.value = targetUrl.substringBefore('#')
                        if (pageTurnHost.hasPendingPageHandoff()) {
                            pageTurnHost.keepActiveWebViewCoveredForHandoff()
                        } else {
                            webView.alpha = 0f
                        }
                        webView.loadUrl(targetUrl)
                    }
                }
                if (firstLoad) {
                    webView.alpha = 0f
                    loadTarget.run()
                } else if (pageTurnHost.hasPendingPageHandoff()) {
                    loadTarget.run()
                } else {
                    webView.animate()
                        .alpha(0f)
                        .setDuration(120L)
                        .withEndAction(loadTarget)
                        .start()
                }
            } else if (!chapterLoadPending.value && configuredKey.value != nextConfigKey) {
                previousPreloadTarget.value = null
                nextPreloadTarget.value = null
                previousPreloadGeneration.value = previousPreloadGeneration.value + 1
                nextPreloadGeneration.value = nextPreloadGeneration.value + 1
                pageTurnHost.markPreloadLoading(
                    EpubPageTurnHost.PreloadSlot.PREVIOUS,
                    null,
                    previousPreloadGeneration.value
                )
                pageTurnHost.markPreloadLoading(
                    EpubPageTurnHost.PreloadSlot.NEXT,
                    null,
                    nextPreloadGeneration.value
                )
                configureReader(
                    view = webView,
                    session = session,
                    chapterIndex = chapterIndex,
                    fontType = fontType,
                    fontFilePath = fontFilePath,
                    textColorOverride = textColorOverride,
                    theme = theme,
                    preservePublisherBackground = preservePublisherBackground,
                    bionicReadingEnabled = bionicReadingEnabled,
                    chineseMode = chineseMode,
                    restoreLocatorJson = restoreLocatorJson,
                    restoreProgression = restoreProgression,
                    initialFragment = initialFragment,
                    continuousScroll = continuousScroll,
                    nativePagingEnabled = nativePageTurn,
                    pageTransition = if (nativePageTurn) "none" else pageTransition,
                    edgeTapMode = edgeTapMode,
                    marginTopDp = marginTopDp,
                    marginRightDp = marginRightDp,
                    marginBottomDp = marginBottomDp,
                    marginLeftDp = marginLeftDp,
                    notes = notes,
                    searchRequest = searchRequest,
                    locatorRequest = locatorRequest,
                    pageRequest = pageRequest
                )
                configuredKey.value = nextConfigKey
            } else {
                pageTurnHost.allWebViews().forEach { view ->
                    applyHighlights(view, notes)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .blur((12f * imagePreviewProgress.value).dp)
    )

        imagePreview?.let { request ->
            EpubImagePreviewOverlay(
                session = session,
                request = request,
                progress = imagePreviewProgress.value,
                onDismissRequest = dismissImagePreview
            )
        }
    }

    LaunchedEffect(selectionClearToken) {
        if (selectionClearToken <= 0) return@LaunchedEffect
        pageTurnHostState.value?.let { host ->
            host.allWebViews().forEach(EpubContentWebView::clearTextSelection)
        }
    }

    DisposableEffect(session) {
        latestPageTextProviderReady.value { requestedChapter, requestedPage ->
            requestPageText(
                view = webViewState.value,
                loadedChapter = loadedChapter.value,
                requestedChapter = requestedChapter,
                requestedPage = requestedPage
            )
        }
        latestPageTurnHandlerReady.value pageTurn@{ direction ->
            if (direction == 0) return@pageTurn false
            val view = webViewState.value ?: return@pageTurn false
            if (pageTurnHostState.value?.requestTurn(direction) == true) return@pageTurn true
            view.turnEpubPage(
                if (direction > 0) ReaderEdgeTapAction.NEXT_PAGE
                else ReaderEdgeTapAction.PREVIOUS_PAGE
            )
            true
        }
        onDispose {
            imagePreviewAnimationJob?.cancel()
            latestPageTextProviderReady.value(null)
            latestPageTurnHandlerReady.value(null)
            pageTurnHostState.value?.let { host ->
                host.allWebViews().forEach { view ->
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.clearHistory()
                    view.removeAllViews()
                    view.destroy()
                }
                host.removeAllViews()
            }
            webViewState.value = null
            pageTurnHostState.value = null
        }
    }
}

private fun usesNativeEpubPageTurn(
    session: EpubRenderSession,
    chapterIndex: Int,
    continuousScroll: Boolean,
    transition: String
): Boolean = !continuousScroll &&
    transition in setOf("slide", "curl") &&
    session.epubPackage.spine.getOrNull(chapterIndex)?.renditionLayout != EpubRenditionLayout.PRE_PAGINATED

private fun configKey(
    chapterIndex: Int,
    fontSizeSp: Float,
    fontType: String,
    fontFilePath: String?,
    textColorOverride: Int?,
    theme: String,
    preservePublisherBackground: Boolean,
    bionicReadingEnabled: Boolean,
    chineseMode: String,
    continuousScroll: Boolean,
    pageTransition: String,
    edgeTapMode: ReaderEdgeTapMode,
    marginTopDp: Float,
    marginRightDp: Float,
    marginBottomDp: Float,
    marginLeftDp: Float,
    initialFragment: String?,
    searchRequest: EpubSearchRequest?,
    locatorRequest: EpubLocatorRequest?,
    pageRequest: EpubPageRequest?
): String = listOf(
    chapterIndex,
    fontSizeSp,
    fontType,
    fontFilePath.orEmpty(),
    textColorOverride ?: -1,
    theme,
    preservePublisherBackground,
    bionicReadingEnabled,
    chineseMode,
    continuousScroll,
    pageTransition,
    edgeTapMode.key,
    marginTopDp,
    marginRightDp,
    marginBottomDp,
    marginLeftDp,
    initialFragment.orEmpty(),
    searchRequest?.token ?: -1,
    locatorRequest?.token ?: -1,
    pageRequest?.token ?: -1
).joinToString("|")

private fun configureReader(
    view: WebView,
    session: EpubRenderSession,
    chapterIndex: Int,
    fontType: String,
    fontFilePath: String?,
    textColorOverride: Int?,
    theme: String,
    preservePublisherBackground: Boolean,
    bionicReadingEnabled: Boolean,
    chineseMode: String,
    restoreLocatorJson: String?,
    restoreProgression: Float,
    initialFragment: String?,
    continuousScroll: Boolean,
    nativePagingEnabled: Boolean,
    pageTransition: String,
    edgeTapMode: ReaderEdgeTapMode,
    marginTopDp: Float,
    marginRightDp: Float,
    marginBottomDp: Float,
    marginLeftDp: Float,
    notes: List<Note>,
    searchRequest: EpubSearchRequest?,
    locatorRequest: EpubLocatorRequest?,
    pageRequest: EpubPageRequest?,
    preparePageRequest: Boolean = false
) {
    val progression = when (session.epubPackage.pageProgressionDirection) {
        EpubPageProgressionDirection.RTL -> "rtl"
        else -> "ltr"
    }
    val readerFontUrl = when {
        fontType == "system" -> null
        fontType == "serif" -> null
        else -> session.readerFontUrl(fontFilePath)
    }
    val fontFamily = when {
        fontType == "system" -> null
        fontType == "serif" -> "serif"
        readerFontUrl != null -> "Lumi Reader Override"
        else -> null
    }
    val chineseMapping = ChineseConverter.mappingStrings(chineseMode)
    val config = JSONObject()
        .put("theme", theme)
        .put("preservePublisherBackground", preservePublisherBackground)
        .put("bionicReading", bionicReadingEnabled)
        .put("chineseMode", chineseMode)
        .put("chineseSource", chineseMapping?.first.orEmpty())
        .put("chineseTarget", chineseMapping?.second.orEmpty())
        .putOpt("fontFamily", fontFamily)
        .putOpt("fontUrl", readerFontUrl)
        .putOpt("textColor", textColorOverride?.let { String.format("#%06X", it and 0xFFFFFF) })
        .put("progression", progression)
        .put("progressionValue", restoreProgression.coerceIn(0f, 1f))
        .put("flow", if (continuousScroll) "scrolled" else "paginated")
        .put("nativePaging", nativePagingEnabled)
        .put("transition", pageTransition)
        .put("edgeTapLeft", edgeTapMode.leftAction.toEpubTurnDirection())
        .put("edgeTapRight", edgeTapMode.rightAction.toEpubTurnDirection())
        .put(
            "insets",
            JSONObject()
                .put("top", marginTopDp)
                .put("right", marginRightDp)
                .put("bottom", marginBottomDp)
                .put("left", marginLeftDp)
        )
    val chapterPath = session.epubPackage.spine.getOrNull(chapterIndex)?.manifestItem?.fullPath
    val locatorJson = locatorRequest
        ?.takeIf { it.chapterIndex == chapterIndex }
        ?.locatorJson
        ?: restoreLocatorJson
    val locator = locatorJson
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.takeIf { it.optString("href") == chapterPath }
    val script = buildString {
        append("if(window.LumiReader){window.LumiReader.configure(")
        append(config.toString())
        append(");")
        if (locator != null) {
            append("window.LumiReader.restore(")
            append(locator.toString())
            append(");")
        }
        if (!initialFragment.isNullOrBlank()) {
            append("window.LumiReader.goToFragment(")
            append(JSONObject.quote(initialFragment))
            append(");")
        }
        if (searchRequest?.chapterIndex == chapterIndex && searchRequest.query.isNotBlank()) {
            val searchProgression = searchRequest.charOffset.toDouble() /
                searchRequest.chapterTextLength.coerceAtLeast(1).toDouble()
            append("window.LumiReader.findText(")
            append(JSONObject.quote(searchRequest.query))
            append(',')
            append(searchProgression.coerceIn(0.0, 1.0))
            append(");")
        }
        if (pageRequest?.chapterIndex == chapterIndex) {
            if (preparePageRequest) {
                append("window.LumiReader.preparePage(")
                append(pageRequest.pageIndex.coerceAtLeast(0))
                append(',')
                append(pageRequest.token)
                append(");")
            } else {
                append("window.LumiReader.goToPage(")
                append(pageRequest.pageIndex.coerceAtLeast(0))
                append(");")
            }
        }
        append("window.LumiReader.setHighlights(")
        append(highlightsJson(notes).toString())
        append(");}")
    }
    view.evaluateJavascript(script, null)
}

private fun ReaderEdgeTapAction.toEpubTurnDirection(): Int = when (this) {
    ReaderEdgeTapAction.PREVIOUS_PAGE -> -1
    ReaderEdgeTapAction.NEXT_PAGE -> 1
}

private fun WebView.turnEpubPage(action: ReaderEdgeTapAction) {
    val command = when (action) {
        ReaderEdgeTapAction.PREVIOUS_PAGE -> "previous"
        ReaderEdgeTapAction.NEXT_PAGE -> "next"
    }
    evaluateJavascript("window.LumiReader&&window.LumiReader.$command();", null)
}

private suspend fun requestPageText(
    view: WebView?,
    loadedChapter: Int,
    requestedChapter: Int,
    requestedPage: Int
): EpubPageText? {
    if (view == null || loadedChapter != requestedChapter) return null
    return suspendCancellableCoroutine { continuation ->
        view.post {
            if (!continuation.isActive) return@post
            view.evaluateJavascript(
                "window.LumiReader?JSON.stringify(window.LumiReader.pageText(" +
                    requestedPage.coerceAtLeast(0) + ")):null"
            ) { encoded ->
                if (!continuation.isActive) return@evaluateJavascript
                val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                val payload = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                continuation.resume(
                    payload?.let {
                        EpubPageText(
                            chapterIndex = requestedChapter,
                            pageIndex = it.optInt("pageIndex", requestedPage).coerceAtLeast(0),
                            pageCount = it.optInt("pageCount", 1).coerceAtLeast(1),
                            text = it.optString("text").trim()
                        )
                    }
                )
            }
        }
    }
}

private fun applyHighlights(view: WebView, notes: List<Note>) {
    view.evaluateJavascript(
        "window.LumiReader&&window.LumiReader.setHighlights(${highlightsJson(notes)});",
        null
    )
}

private fun highlightsJson(notes: List<Note>): JSONArray = JSONArray().apply {
    notes.forEach { note ->
        val item = JSONObject().put("exact", note.selectedText).put("color", note.color.toCssColor())
        note.startLocatorJson?.let { json ->
            runCatching { JSONObject(json) }.getOrNull()?.let { item.put("start", it) }
        }
        note.endLocatorJson?.let { json ->
            runCatching { JSONObject(json) }.getOrNull()?.let { item.put("end", it) }
        }
        put(item)
    }
}

private fun String.toCssColor(): String {
    if (length == 9 && startsWith("#")) return "#" + substring(3) + substring(1, 3)
    return this
}

private fun JSONObject.withChapterHref(href: String): JSONObject = apply {
    put("version", optInt("version", 1))
    put("href", href)
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureEpubWebViewSettings(view: WebView) {
    view.setBackgroundColor(Color.TRANSPARENT)
    view.isVerticalScrollBarEnabled = false
    view.isHorizontalScrollBarEnabled = false
    view.overScrollMode = WebView.OVER_SCROLL_NEVER
    view.settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        domStorageEnabled = false
        databaseEnabled = false
        builtInZoomControls = false
        displayZoomControls = false
        useWideViewPort = false
        loadWithOverviewMode = false
        setSupportZoom(false)
        mediaPlaybackRequiresUserGesture = true
        loadsImagesAutomatically = true
        blockNetworkLoads = true
        safeBrowsingEnabled = true
    }
}

private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0))
)
