package com.huangder.lumibooks.ui.reader

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.ActionMode
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.animation.PathInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
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
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.EpubPageProgressionDirection
import com.huangder.lumibooks.util.epub.EpubLocator
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import org.json.JSONArray
import org.json.JSONObject
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.math.abs

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
    val locator: EpubLocator
)

internal data class EpubLocatorRequest(
    val token: Int,
    val chapterIndex: Int,
    val locatorJson: String
)

internal data class EpubPageRequest(
    val token: Int,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterFraction: Float? = null
)

internal data class EpubSelectionInfo(
    val text: String,
    val startPosition: Int,
    val endPosition: Int,
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

private data class EpubPreparedPage(
    val requestedTarget: EpubPageTarget,
    val generation: Int,
    val actualTarget: EpubPageTarget,
    val pageCount: Int,
    val locatorJson: String?,
    val reverseAxis: Boolean
)

internal data class EpubPageText(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val text: String,
    val chapterText: String,
    val startCharacterOffset: Int,
    val endCharacterOffset: Int
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EpubWebViewReader(
    session: BookRenderSession,
    chapterIndex: Int,
    fontSizeSp: Float,
    letterSpacingDp: Float = 0f,
    fontType: String,
    fontFilePath: String?,
    bodyFontWeight: Int = 400,
    textColorOverride: Int?,
    theme: String,
    textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    preservePublisherBackground: Boolean = true,
    bionicReadingEnabled: Boolean = false,
    chineseMode: String = "original",
    restoreLocatorJson: String?,
    restoreProgression: Float,
    initialFragment: String? = null,
    continuousScroll: Boolean = false,
    pageTransition: String = "slide",
    pageTransitionDurationMs: Int = 260,
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
    bookmarkPullEnabled: Boolean = true,
    onPageTextProviderReady: ((suspend (chapterIndex: Int, pageIndex: Int) -> EpubPageText?)?) -> Unit,
    onPageTurnHandlerReady: (((direction: Int) -> Boolean)?) -> Unit,
    onPageChanged: (pageIndex: Int, pageCount: Int, locatorJson: String?) -> Unit,
    onBookmarkPullStart: () -> Unit = {},
    onBookmarkPullProgress: (distancePx: Float, armed: Boolean) -> Unit = { _, _ -> },
    onBookmarkPullFinished: (commit: Boolean) -> Unit = {},
    onCenterTap: () -> Unit,
    onImagePreviewOpen: () -> Unit,
    onChapterTurn: (direction: Int) -> Unit,
    onInternalLink: (chapterIndex: Int, fragment: String?) -> Unit,
    onExternalLink: (href: String) -> Unit,
    onSelection: (EpubSelectionInfo) -> Unit,
    onSelectionCleared: () -> Unit,
    onSearchResolved: (token: Int, found: Boolean) -> Unit,
    onRenderUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestPageChanged = rememberUpdatedState(onPageChanged)
    val latestBookmarkPullStart = rememberUpdatedState(onBookmarkPullStart)
    val latestBookmarkPullProgress = rememberUpdatedState(onBookmarkPullProgress)
    val latestBookmarkPullFinished = rememberUpdatedState(onBookmarkPullFinished)
    val latestCenterTap = rememberUpdatedState(onCenterTap)
    val latestImagePreviewOpen = rememberUpdatedState(onImagePreviewOpen)
    val latestChapterTurn = rememberUpdatedState(onChapterTurn)
    val latestInternalLink = rememberUpdatedState(onInternalLink)
    val latestExternalLink = rememberUpdatedState(onExternalLink)
    val latestSelection = rememberUpdatedState(onSelection)
    val latestSelectionCleared = rememberUpdatedState(onSelectionCleared)
    val latestSearchResolved = rememberUpdatedState(onSearchResolved)
    val latestRenderUnavailable = rememberUpdatedState(onRenderUnavailable)
    val latestChapterIndex = rememberUpdatedState(chapterIndex)
    val latestFontSizeSp = rememberUpdatedState(fontSizeSp)
    val latestLetterSpacingDp = rememberUpdatedState(letterSpacingDp)
    val latestFontType = rememberUpdatedState(fontType)
    val latestFontFilePath = rememberUpdatedState(fontFilePath)
    val latestBodyFontWeight = rememberUpdatedState(bodyFontWeight)
    val latestTextColorOverride = rememberUpdatedState(textColorOverride)
    val latestTheme = rememberUpdatedState(theme)
    val latestTextAlignment = rememberUpdatedState(textAlignment)
    val latestPreservePublisherBackground = rememberUpdatedState(preservePublisherBackground)
    val latestBionicReadingEnabled = rememberUpdatedState(bionicReadingEnabled)
    val latestChineseMode = rememberUpdatedState(chineseMode)
    val latestRestoreLocator = rememberUpdatedState(restoreLocatorJson)
    val latestRestoreProgression = rememberUpdatedState(restoreProgression)
    val latestInitialFragment = rememberUpdatedState(initialFragment)
    val latestContinuousScroll = rememberUpdatedState(continuousScroll)
    val latestPageTransition = rememberUpdatedState(pageTransition)
    val latestPageTransitionDurationMs = rememberUpdatedState(pageTransitionDurationMs)
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
    val ttsPageViewByChapter = remember(session) { mutableStateOf<Map<Int, WebView>>(emptyMap()) }
    val pageTurnHostState = remember(session) { mutableStateOf<EpubPageTurnHost?>(null) }
    val loadedChapter = remember(session) { mutableStateOf(-1) }
    val activePageCount = remember(session) { mutableStateOf(1) }
    val configuredKey = remember(session) { mutableStateOf("") }
    val activeDocumentUrl = remember(session) { mutableStateOf("") }
    val chapterLoadPending = remember(session) { mutableStateOf(false) }
    val readyChapter = remember(session) { mutableStateOf(-1) }
    val continuousChapterTurnDirection = remember(session) { mutableStateOf(0) }
    val dispatchedSearchToken = remember(session) { mutableStateOf(-1) }
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
                val preloadRequestByView = mutableMapOf<EpubContentWebView, EpubPreloadRequest>()
                val preparedPageByView = mutableMapOf<EpubContentWebView, EpubPreparedPage>()
                val preloadConfigurationByView = mutableMapOf<EpubContentWebView, String>()
                val readyTtsPageViewByChapter = mutableMapOf<Int, EpubContentWebView>()
                var nextPreloadGeneration = 0
                var rendererGoneHandled = false

                fun publishTtsPageViews() {
                    ttsPageViewByChapter.value = readyTtsPageViewByChapter.toMap()
                }

                fun forgetTtsPageView(view: EpubContentWebView) {
                    if (readyTtsPageViewByChapter.entries.removeAll { it.value === view }) {
                        publishTtsPageViews()
                    }
                }

                fun chapterForView(view: EpubContentWebView): Int {
                    val documentUrl = view.url.orEmpty().substringBefore('#')
                    val matched = session.chapterIndexForUrl(documentUrl)
                    if (matched != null) loadedChapterByView[view] = matched
                    return matched ?: loadedChapterByView[view] ?: latestChapterIndex.value
                }

                fun preloadRequestFor(
                    view: EpubContentWebView
                ): Pair<EpubPageTurnHost.PreloadSlot, EpubPreloadRequest>? {
                    val slot = pageTurnHost.preloadSlotOf(view) ?: return null
                    val request = preloadRequestByView[view] ?: return null
                    return slot to request
                }

                fun preloadConfigurationKey(chapterIndex: Int): String = configKey(
                    chapterIndex = chapterIndex,
                    fontSizeSp = latestFontSizeSp.value,
                    letterSpacingDp = latestLetterSpacingDp.value,
                    fontType = latestFontType.value,
                    fontFilePath = latestFontFilePath.value,
                    bodyFontWeight = latestBodyFontWeight.value,
                    textColorOverride = latestTextColorOverride.value,
                    theme = latestTheme.value,
                    textAlignment = latestTextAlignment.value,
                    preservePublisherBackground = latestPreservePublisherBackground.value,
                    bionicReadingEnabled = latestBionicReadingEnabled.value,
                    chineseMode = latestChineseMode.value,
                    continuousScroll = false,
                    pageTransition = "none",
                    pageTransitionDurationMs = latestPageTransitionDurationMs.value,
                    edgeTapMode = latestEdgeTapMode.value,
                    marginTopDp = latestMarginTopDp.value,
                    marginRightDp = latestMarginRightDp.value,
                    marginBottomDp = latestMarginBottomDp.value,
                    marginLeftDp = latestMarginLeftDp.value,
                    initialFragment = null,
                    locatorRequest = null,
                    pageRequest = null
                )

                fun activeConfigurationKey(chapterIndex: Int): String = configKey(
                    chapterIndex = chapterIndex,
                    fontSizeSp = latestFontSizeSp.value,
                    letterSpacingDp = latestLetterSpacingDp.value,
                    fontType = latestFontType.value,
                    fontFilePath = latestFontFilePath.value,
                    bodyFontWeight = latestBodyFontWeight.value,
                    textColorOverride = latestTextColorOverride.value,
                    theme = latestTheme.value,
                    textAlignment = latestTextAlignment.value,
                    preservePublisherBackground = latestPreservePublisherBackground.value,
                    bionicReadingEnabled = latestBionicReadingEnabled.value,
                    chineseMode = latestChineseMode.value,
                    continuousScroll = false,
                    pageTransition = latestPageTransition.value,
                    pageTransitionDurationMs = latestPageTransitionDurationMs.value,
                    edgeTapMode = latestEdgeTapMode.value,
                    marginTopDp = latestMarginTopDp.value,
                    marginRightDp = latestMarginRightDp.value,
                    marginBottomDp = latestMarginBottomDp.value,
                    marginLeftDp = latestMarginLeftDp.value,
                    initialFragment = null,
                    locatorRequest = null,
                    pageRequest = null
                )

                fun configurePreloadReader(view: EpubContentWebView, request: EpubPreloadRequest) {
                    val target = request.target
                    val configurationKey = preloadConfigurationKey(target.chapterIndex)
                    if (loadedChapterByView[view] == target.chapterIndex &&
                        preloadConfigurationByView[view] == configurationKey
                    ) {
                        view.evaluateJavascript(
                            "window.LumiReader&&window.LumiReader.preparePage(" +
                                target.pageIndex.coerceAtLeast(0) + "," +
                                request.generation + ");",
                            null
                        )
                        return
                    }
                    val isFixedLayout = session.renditionLayout(target.chapterIndex) ==
                        EpubRenditionLayout.PRE_PAGINATED
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
                        letterSpacingDp = latestLetterSpacingDp.value,
                        bodyFontWeight = latestBodyFontWeight.value,
                        textColorOverride = latestTextColorOverride.value,
                        theme = latestTheme.value,
                        textAlignment = latestTextAlignment.value,
                        preservePublisherBackground = latestPreservePublisherBackground.value,
                        bionicReadingEnabled = latestBionicReadingEnabled.value,
                        chineseMode = latestChineseMode.value,
                        restoreLocatorJson = null,
                        restoreProgression = if (target.pageIndex == Int.MAX_VALUE) 1f else 0f,
                        initialFragment = null,
                        continuousScroll = false,
                        nativePagingEnabled = true,
                        pageTransition = "none",
                        pageTransitionDurationMs = latestPageTransitionDurationMs.value,
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
                        locatorRequest = null,
                        pageRequest = EpubPageRequest(
                            token = request.generation,
                            chapterIndex = target.chapterIndex,
                            pageIndex = target.pageIndex
                        ),
                        preparePageRequest = true
                    )
                    preloadConfigurationByView[view] = configurationKey
                }

                fun updatePreload(
                    slot: EpubPageTurnHost.PreloadSlot,
                    target: EpubPageTarget?
                ) {
                    val view = if (slot == EpubPageTurnHost.PreloadSlot.PREVIOUS) {
                        pageTurnHost.previousWebView
                    } else {
                        pageTurnHost.nextWebView
                    }
                    val existingRequest = preloadRequestByView[view]
                    if (pageTurnHost.preloadTarget(slot) == target &&
                        (pageTurnHost.isPreloadReady(slot) || existingRequest?.target == target)
                    ) return
                    val generation = ++nextPreloadGeneration
                    preparedPageByView.remove(view)
                    forgetTtsPageView(view)
                    if (target == null) {
                        preloadRequestByView.remove(view)
                    } else {
                        preloadRequestByView[view] = EpubPreloadRequest(target, generation)
                    }
                    val reusedCurrentPage = pageTurnHost.markPreloadLoading(slot, target, generation)
                    if (target == null) {
                        view.stopLoading()
                        return
                    }
                    if (reusedCurrentPage) return
                    val request = preloadRequestByView.getValue(view)
                    if (loadedChapterByView[view] != target.chapterIndex) {
                        loadedChapterByView[view] = target.chapterIndex
                        preloadConfigurationByView.remove(view)
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
                        currentChapter + 1 < session.chapterCount ->
                            EpubPageTarget(currentChapter + 1, 0)
                        else -> null
                    }
                    updatePreload(EpubPageTurnHost.PreloadSlot.PREVIOUS, previous)
                    updatePreload(EpubPageTurnHost.PreloadSlot.NEXT, next)
                }

                fun invalidateAdjacentPreloads() {
                    updatePreload(EpubPageTurnHost.PreloadSlot.PREVIOUS, null)
                    updatePreload(EpubPageTurnHost.PreloadSlot.NEXT, null)
                }

                fun handlePreparedMessage(view: EpubContentWebView, payload: JSONObject) {
                    val request = preloadRequestByView[view] ?: return
                    if (payload.optInt("requestToken", Int.MIN_VALUE) != request.generation) return
                    val target = request.target
                    val expectedUrl = session.chapterUrl(target.chapterIndex).substringBefore('#')
                    if (view.url.orEmpty().substringBefore('#') != expectedUrl) return
                    val actualPage = payload.optInt("pageIndex", 0).coerceAtLeast(0)
                    val actualCount = payload.optInt("pageCount", 1).coerceAtLeast(1)
                    val actualTarget = EpubPageTarget(target.chapterIndex, actualPage)
                    val locator = payload.optJSONObject("locator")?.withChapterHref(
                        session.chapterHref(target.chapterIndex)
                    )?.toString()
                    val packageRtl = session.pageProgressionDirection(target.chapterIndex) ==
                        EpubPageProgressionDirection.RTL
                    val reverseAxis = payload.optBoolean("reverseAxis", packageRtl)
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
                                    preloadRequestByView[view] !== request ||
                                    resolvedPreloadSlot(
                                        pageTurnHost.roleOf(view),
                                        request.generation,
                                        preloadRequestByView[view]?.generation
                                    ) == null
                                ) return
                                view.runAfterNextDraw {
                                    if (preloadRequestByView[view] !== request) return@runAfterNextDraw
                                    if (view.url.orEmpty().substringBefore('#') != expectedUrl) {
                                        return@runAfterNextDraw
                                    }
                                    val currentSlot = resolvedPreloadSlot(
                                        pageTurnHost.roleOf(view),
                                        request.generation,
                                        preloadRequestByView[view]?.generation
                                    ) ?: return@runAfterNextDraw
                                    preparedPageByView[view] = EpubPreparedPage(
                                        requestedTarget = target,
                                        generation = request.generation,
                                        actualTarget = actualTarget,
                                        pageCount = actualCount,
                                        locatorJson = locator,
                                        reverseAxis = reverseAxis
                                    )
                                    pageTurnHost.markPreloadReady(
                                        currentSlot,
                                        target,
                                        request.generation,
                                        actualPage,
                                        actualCount,
                                        view
                                    )
                                    readyTtsPageViewByChapter[target.chapterIndex] = view
                                    publishTtsPageViews()
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
                                session.chapterHref(messageChapterIndex)
                            )?.toString()
                            val packageRtl = session.pageProgressionDirection(messageChapterIndex) ==
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
                                                pageIndex,
                                                pageCount
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
                                            ) && !pageTurnHost.isAwaitingPreparedActivePage(
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
                                readyTtsPageViewByChapter[messageChapterIndex] = view
                                publishTtsPageViews()
                                readyChapter.value = messageChapterIndex
                                chapterLoadPending.value = false
                                view.animate().cancel()
                                if (pageTurnHost.hasPendingPageHandoff()) {
                                    pageTurnHost.keepActiveWebViewCoveredForHandoff()
                                    view.postInvalidateOnAnimation()
                                } else if (continuousChapterTurnDirection.value != 0 &&
                                    latestContinuousScroll.value
                                ) {
                                    val entranceDirection = continuousChapterTurnDirection.value
                                    val travel = maxOf(
                                        pageTurnHost.height * 0.14f,
                                        64f * view.resources.displayMetrics.density
                                    )
                                    pageTurnHost.animate().cancel()
                                    pageTurnHost.translationY = if (entranceDirection > 0) {
                                        travel
                                    } else {
                                        -travel
                                    }
                                    pageTurnHost.alpha = 0f
                                    view.alpha = 0f
                                    val startEntrance = start@{
                                        if (continuousChapterTurnDirection.value != entranceDirection ||
                                            loadedChapterByView[view] != messageChapterIndex ||
                                            pageTurnHost.roleOf(view) !=
                                            EpubPageTurnHost.WebViewRole.ACTIVE
                                        ) return@start
                                        continuousChapterTurnDirection.value = 0
                                        view.alpha = 1f
                                        pageTurnHost.animate().cancel()
                                        pageTurnHost.animate()
                                            .translationY(0f)
                                            .alpha(1f)
                                            .setDuration(280L)
                                            .setInterpolator(
                                                PathInterpolator(0.2f, 0f, 0f, 1f)
                                            )
                                            .start()
                                    }
                                    val entranceRequestId = System.nanoTime()
                                    view.postVisualStateCallback(
                                        entranceRequestId,
                                        object : WebView.VisualStateCallback() {
                                            override fun onComplete(requestId: Long) {
                                                if (requestId == entranceRequestId) startEntrance()
                                            }
                                        }
                                    )
                                    view.postDelayed(startEntrance, 160L)
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
                            if (direction == 0) return
                            val targetChapter = messageChapterIndex + direction
                            if (targetChapter !in 0 until session.chapterCount) {
                                view.evaluateJavascript(
                                    "window.LumiReader&&window.LumiReader.cancelChapterTurn();",
                                    null
                                )
                                return
                            }
                            if (!pageTurnHost.requestTurn(direction)) {
                                if (latestContinuousScroll.value &&
                                    payload.optBoolean("animated", false)
                                ) {
                                    continuousChapterTurnDirection.value = direction
                                }
                                latestChapterTurn.value(direction)
                            }
                        }
                        "link" -> {
                            val href = payload.optString("href").trim()
                            if (href.isEmpty()) return
                            val target = session.resolveInternalLink(messageChapterIndex, href)
                            if (target != null) {
                                if (target.first == messageChapterIndex &&
                                    !target.second.isNullOrBlank()
                                ) {
                                    // 先让 ReaderScreen 捕获来源位置（用于左上角“返回刚才页”），
                                    // 再在当前 WebView 内直接跳转。
                                    latestInternalLink.value(target.first, target.second)
                                    view.jumpToInternalFragment(target.second)
                                } else if (target.first != messageChapterIndex) {
                                    latestInternalLink.value(target.first, target.second)
                                }
                            } else {
                                if (href.startsWith("https://" + BookRenderSession.ASSET_DOMAIN)) {
                                    view.showMissingLinkTarget()
                                }
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
                            val href = session.chapterHref(messageChapterIndex)
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
                                    start.optInt("textPosition", 0).coerceAtLeast(0),
                                    end.optInt("textPosition", selectedText.length)
                                        .coerceAtLeast(start.optInt("textPosition", 0)),
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
                        "searchResult" -> {
                            val request = latestSearchRequest.value ?: return
                            val token = payload.optInt("requestToken", Int.MIN_VALUE)
                            if (token != request.token ||
                                request.chapterIndex != messageChapterIndex ||
                                loadedChapterByView[view] != messageChapterIndex ||
                                pageTurnHost.roleOf(view) != EpubPageTurnHost.WebViewRole.ACTIVE
                            ) return
                            latestSearchResolved.value(token, payload.optBoolean("found", false))
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
                                        sourceOrigin.host != BookRenderSession.ASSET_DOMAIN ||
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
                                if (internal.first == sourceChapter &&
                                    !internal.second.isNullOrBlank()
                                ) {
                                    latestInternalLink.value(internal.first, internal.second)
                                    contentView.jumpToInternalFragment(internal.second)
                                } else if (internal.first != sourceChapter) {
                                    latestInternalLink.value(internal.first, internal.second)
                                }
                            } else {
                                if (url.startsWith("https://" + BookRenderSession.ASSET_DOMAIN)) {
                                    contentView.showMissingLinkTarget()
                                }
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
                            if (preloadRequestByView[contentView] !== request) return
                            pageTurnHost.markPreloadFailed(
                                slot,
                                request.target,
                                request.generation
                            )
                            preloadRequestByView.remove(contentView, request)
                            updatePreload(slot, request.target)
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
                            if (preloadRequestByView[contentView] !== request) return
                            pageTurnHost.markPreloadFailed(
                                slot,
                                request.target,
                                request.generation
                            )
                            preloadRequestByView.remove(contentView, request)
                            updatePreload(slot, request.target)
                        }

                        override fun onRenderProcessGone(
                            sourceView: WebView,
                            detail: RenderProcessGoneDetail
                        ): Boolean {
                            if (rendererGoneHandled) return true
                            rendererGoneHandled = true
                            chapterLoadPending.value = false
                            readyChapter.value = -1
                            dispatchedSearchToken.value = -1
                            loadedChapterByView.clear()
                            preloadRequestByView.clear()
                            preparedPageByView.clear()
                            val affectedViews = pageTurnHost.allWebViews().toList()
                            pageTurnHost.removeAllViews()
                            affectedViews.forEach { affected ->
                                runCatching { affected.stopLoading() }
                                runCatching { affected.removeAllViews() }
                                runCatching { affected.destroy() }
                            }
                            if (pageTurnHostState.value === pageTurnHost) {
                                webViewState.value = null
                                pageTurnHostState.value = null
                            }
                            pageTurnHost.post { latestRenderUnavailable.value() }
                            return true
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
                            readyChapter.value = -1
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
                                letterSpacingDp = latestLetterSpacingDp.value,
                                bodyFontWeight = latestBodyFontWeight.value,
                                textColorOverride = latestTextColorOverride.value,
                                theme = latestTheme.value,
                                textAlignment = latestTextAlignment.value,
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
                                pageTransitionDurationMs = latestPageTransitionDurationMs.value,
                                edgeTapMode = latestEdgeTapMode.value,
                                marginTopDp = latestMarginTopDp.value,
                                marginRightDp = latestMarginRightDp.value,
                                marginBottomDp = latestMarginBottomDp.value,
                                marginLeftDp = latestMarginLeftDp.value,
                                notes = latestNotes.value,
                                locatorRequest = latestLocatorRequest.value,
                                pageRequest = latestPageRequest.value
                            )
                            if (nativePageTurn) {
                                preloadConfigurationByView[contentView] =
                                    preloadConfigurationKey(sourceChapter)
                            } else {
                                preloadConfigurationByView.remove(contentView)
                            }
                        }
                    }
                }

                allWebViews().forEach(::attachWebView)

                pageTurnHost.onCapturedTapDirection = { x ->
                    val relativeX = if (pageTurnHost.width > 0) {
                        x / pageTurnHost.width.toFloat()
                    } else {
                        0.5f
                    }
                    when {
                        relativeX < 0.3f ->
                            latestEdgeTapMode.value.leftAction.toEpubTurnDirection()
                        relativeX > 0.7f ->
                            latestEdgeTapMode.value.rightAction.toEpubTurnDirection()
                        else -> 0
                    }
                }
                pageTurnHost.onBookmarkPullStart = {
                    latestBookmarkPullStart.value()
                }
                pageTurnHost.onBookmarkPullProgress = { distancePx, armed ->
                    latestBookmarkPullProgress.value(distancePx, armed)
                }
                pageTurnHost.onBookmarkPullFinished = { commit ->
                    latestBookmarkPullFinished.value(commit)
                }
                pageTurnHost.onPageCommit = { direction, target ->
                    val activeView = pageTurnHost.activeWebView
                    val request = preloadRequestByView[activeView]
                    val prepared = preparedPageByView[activeView]
                    val promotedPreparedPage = prepared?.takeIf {
                        pageTurnHost.isAwaitingPreparedActivePage(
                            target.chapterIndex,
                            target.pageIndex
                        ) &&
                            request != null &&
                            request.generation == it.generation &&
                            request.target == it.requestedTarget &&
                            it.actualTarget == target
                    }
                    val crossesChapter = target.chapterIndex != loadedChapter.value
                    if (crossesChapter && promotedPreparedPage != null) {
                        loadedChapter.value = target.chapterIndex
                        activeDocumentUrl.value = session.chapterUrl(target.chapterIndex)
                            .substringBefore('#')
                        configuredKey.value = activeConfigurationKey(target.chapterIndex)
                        chapterLoadPending.value = false
                        readyChapter.value = target.chapterIndex
                        latestChapterTurn.value(direction)
                    }
                    if (promotedPreparedPage != null) {
                        activePageCount.value = promotedPreparedPage.pageCount
                        pageTurnHost.setReverseAxis(promotedPreparedPage.reverseAxis)
                        pageTurnHost.setCurrentPage(
                            target.chapterIndex,
                            target.pageIndex,
                            promotedPreparedPage.pageCount
                        ) {
                            updateAdjacentPreloads(
                                target.chapterIndex,
                                target.pageIndex,
                                promotedPreparedPage.pageCount
                            )
                            latestPageChanged.value(
                                target.pageIndex,
                                promotedPreparedPage.pageCount,
                                promotedPreparedPage.locatorJson
                            )
                        }
                    } else if (crossesChapter) {
                        latestChapterTurn.value(direction)
                    } else {
                        activeView.evaluateJavascript(
                            "window.LumiReader&&window.LumiReader.goToPage(" +
                                target.pageIndex + ");",
                            null
                        )
                    }
                }
                pageTurnHost.onSlideLookaheadRequested = { slot, target ->
                    updatePreload(slot, target)
                }
                pageTurnHost.onSlideVisualPageAdvanced = { target, pageCount ->
                    updateAdjacentPreloads(target.chapterIndex, target.pageIndex, pageCount)
                }
                pageTurnHost.onInvalidatePreloads = ::invalidateAdjacentPreloads

                webViewState.value = activeWebView
                pageTurnHostState.value = pageTurnHost
            }
        },
        update = { pageTurnHost ->
            val webView = pageTurnHost.activeWebView
            webViewState.value = webView
            val isFixedLayout = session.renditionLayout(chapterIndex) ==
                EpubRenditionLayout.PRE_PAGINATED
            val nativePageTurn = usesNativeEpubPageTurn(
                session = session,
                chapterIndex = chapterIndex,
                continuousScroll = continuousScroll,
                transition = pageTransition
            )
            pageTurnHost.setNativePagingEnabled(nativePageTurn)
            pageTurnHost.setNativeTouchPagingEnabled(nativePageTurn)
            pageTurnHost.setBookmarkPullEnabled(bookmarkPullEnabled && !continuousScroll)
            if (nativePageTurn) {
                pageTurnHost.setTransition(pageTransition, latestPageTransitionDurationMs.value)
            }
            val fallbackBackground = when (theme) {
                "night" -> Color.rgb(0x11, 0x11, 0x11)
                "sepia_dark" -> Color.rgb(0x2B, 0x21, 0x18)
                "green_dark" -> Color.rgb(0x14, 0x2A, 0x1A)
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
                chapterIndex = chapterIndex,
                fontSizeSp = fontSizeSp,
                letterSpacingDp = letterSpacingDp,
                fontType = fontType,
                fontFilePath = fontFilePath,
                bodyFontWeight = bodyFontWeight,
                textColorOverride = textColorOverride,
                theme = theme,
                textAlignment = textAlignment,
                preservePublisherBackground = preservePublisherBackground,
                bionicReadingEnabled = bionicReadingEnabled,
                chineseMode = chineseMode,
                continuousScroll = continuousScroll,
                pageTransition = pageTransition,
                pageTransitionDurationMs = pageTransitionDurationMs,
                edgeTapMode = edgeTapMode,
                marginTopDp = marginTopDp,
                marginRightDp = marginRightDp,
                marginBottomDp = marginBottomDp,
                marginLeftDp = marginLeftDp,
                initialFragment = initialFragment,
                locatorRequest = locatorRequest,
                pageRequest = pageRequest
            )
            if (loadedChapter.value != chapterIndex) {
                val firstLoad = loadedChapter.value < 0
                val targetChapter = chapterIndex
                val targetUrl = session.chapterUrl(chapterIndex, initialFragment)
                loadedChapter.value = targetChapter
                configuredKey.value = ""
                chapterLoadPending.value = true
                readyChapter.value = -1
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
                } else if (continuousScroll && continuousChapterTurnDirection.value != 0) {
                    // The outgoing document already completed its gesture-driven fade.
                    loadTarget.run()
                } else {
                    webView.animate()
                        .alpha(0f)
                        .setDuration(120L)
                        .withEndAction(loadTarget)
                        .start()
                }
            } else if (!chapterLoadPending.value && configuredKey.value != nextConfigKey) {
                pageTurnHost.invalidatePreloads()
                readyChapter.value = -1
                configureReader(
                    view = webView,
                    session = session,
                    chapterIndex = chapterIndex,
                    fontType = fontType,
                    fontFilePath = fontFilePath,
                    letterSpacingDp = letterSpacingDp,
                    bodyFontWeight = bodyFontWeight,
                    textColorOverride = textColorOverride,
                    theme = theme,
                    textAlignment = textAlignment,
                    preservePublisherBackground = preservePublisherBackground,
                    bionicReadingEnabled = bionicReadingEnabled,
                    chineseMode = chineseMode,
                    restoreLocatorJson = restoreLocatorJson,
                    restoreProgression = restoreProgression,
                    initialFragment = initialFragment,
                    continuousScroll = continuousScroll,
                    nativePagingEnabled = nativePageTurn,
                    pageTransition = if (nativePageTurn) "none" else pageTransition,
                    pageTransitionDurationMs = pageTransitionDurationMs,
                    edgeTapMode = edgeTapMode,
                    marginTopDp = marginTopDp,
                    marginRightDp = marginRightDp,
                    marginBottomDp = marginBottomDp,
                    marginLeftDp = marginLeftDp,
                    notes = notes,
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

    LaunchedEffect(searchRequest?.token, chapterIndex, readyChapter.value, pageTurnHostState.value) {
        val request = searchRequest ?: return@LaunchedEffect
        if (request.chapterIndex != chapterIndex ||
            loadedChapter.value != chapterIndex ||
            readyChapter.value != chapterIndex ||
            dispatchedSearchToken.value == request.token
        ) return@LaunchedEffect
        val expectedHref = session.chapterHref(chapterIndex)
        if (request.locator.href != expectedHref) {
            dispatchedSearchToken.value = request.token
            latestSearchResolved.value(request.token, false)
            return@LaunchedEffect
        }
        val host = pageTurnHostState.value ?: return@LaunchedEffect
        val view = host.activeWebView
        if (view.url.orEmpty().substringBefore('#') !=
            session.chapterUrl(chapterIndex).substringBefore('#')
        ) return@LaunchedEffect
        dispatchedSearchToken.value = request.token
        host.allWebViews().forEach { candidate ->
            candidate.evaluateJavascript(
                "window.LumiReader&&window.LumiReader.clearSearchHighlight();",
                null
            )
        }
        view.evaluateJavascript(
            "window.LumiReader&&window.LumiReader.findText(" +
                request.locator.toJson().toString() + "," + request.token + ");",
            null
        )
        kotlinx.coroutines.delay(5_000L)
        val pending = latestSearchRequest.value
        if (pending?.token == request.token &&
            pending.chapterIndex == chapterIndex &&
            pageTurnHostState.value?.activeWebView === view
        ) {
            latestSearchResolved.value(request.token, false)
        }
    }

    DisposableEffect(session) {
        latestPageTextProviderReady.value { requestedChapter, requestedPage ->
            requestPageText(
                view = ttsPageViewByChapter.value[requestedChapter],
                loadedChapter = requestedChapter,
                requestedChapter = requestedChapter,
                requestedPage = requestedPage,
                expectedUrl = session.chapterUrl(requestedChapter).substringBefore('#')
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
            ttsPageViewByChapter.value = emptyMap()
            pageTurnHostState.value = null
        }
    }
}

internal fun usesNativeEpubPageTurn(
    session: BookRenderSession,
    chapterIndex: Int,
    continuousScroll: Boolean,
    transition: String
): Boolean = usesNativeEpubPageTurn(
    continuousScroll = continuousScroll,
    transition = transition,
    renditionLayout = session.renditionLayout(chapterIndex)
)

internal fun usesNativeEpubPageTurn(
    continuousScroll: Boolean,
    transition: String,
    renditionLayout: EpubRenditionLayout
): Boolean {
    if (continuousScroll) return false
    return when (transition) {
        "scroll" -> true
        "slide", "curl" -> renditionLayout != EpubRenditionLayout.PRE_PAGINATED
        else -> false
    }
}

private fun configKey(
    chapterIndex: Int,
    fontSizeSp: Float,
    letterSpacingDp: Float,
    fontType: String,
    fontFilePath: String?,
    bodyFontWeight: Int,
    textColorOverride: Int?,
    theme: String,
    textAlignment: ReaderTextAlignment,
    preservePublisherBackground: Boolean,
    bionicReadingEnabled: Boolean,
    chineseMode: String,
    continuousScroll: Boolean,
    pageTransition: String,
    pageTransitionDurationMs: Int,
    edgeTapMode: ReaderEdgeTapMode,
    marginTopDp: Float,
    marginRightDp: Float,
    marginBottomDp: Float,
    marginLeftDp: Float,
    initialFragment: String?,
    locatorRequest: EpubLocatorRequest?,
    pageRequest: EpubPageRequest?
): String = listOf(
    chapterIndex,
    fontSizeSp,
    letterSpacingDp,
    fontType,
    fontFilePath.orEmpty(),
    bodyFontWeight,
    textColorOverride ?: -1,
    theme,
    textAlignment.key,
    preservePublisherBackground,
    bionicReadingEnabled,
    chineseMode,
    continuousScroll,
    pageTransition,
    pageTransitionDurationMs,
    edgeTapMode.key,
    marginTopDp,
    marginRightDp,
    marginBottomDp,
    marginLeftDp,
    initialFragment.orEmpty(),
    locatorRequest?.token ?: -1,
    pageRequest?.token ?: -1
).joinToString("|")

private fun configureReader(
    view: WebView,
    session: BookRenderSession,
    chapterIndex: Int,
    fontType: String,
    fontFilePath: String?,
    letterSpacingDp: Float,
    bodyFontWeight: Int,
    textColorOverride: Int?,
    theme: String,
    textAlignment: ReaderTextAlignment,
    preservePublisherBackground: Boolean,
    bionicReadingEnabled: Boolean,
    chineseMode: String,
    restoreLocatorJson: String?,
    restoreProgression: Float,
    initialFragment: String?,
    continuousScroll: Boolean,
    nativePagingEnabled: Boolean,
    pageTransition: String,
    pageTransitionDurationMs: Int,
    edgeTapMode: ReaderEdgeTapMode,
    marginTopDp: Float,
    marginRightDp: Float,
    marginBottomDp: Float,
    marginLeftDp: Float,
    notes: List<Note>,
    locatorRequest: EpubLocatorRequest?,
    pageRequest: EpubPageRequest?,
    preparePageRequest: Boolean = false
) {
    val progression = when (session.pageProgressionDirection(chapterIndex)) {
        EpubPageProgressionDirection.RTL -> "rtl"
        else -> "ltr"
    }
    val readerFontUrl = when {
        fontType == "system" -> null
        fontType == "serif" -> null
        else -> session.readerFontUrl(fontFilePath)
    }
    val fontFamily = when {
        // Keep publisher CSS for the default reader font. Explicit platform
        // families are mapped to CSS names so WebView does not silently fall
        // back to a document-selected family.
        fontType == "system" -> null
        fontType == "serif" -> "serif"
        fontType == "sans_serif" -> "sans-serif"
        fontType == "monospace" -> "monospace"
        readerFontUrl != null -> "Lumi Reader Override"
        // A selected bundled/imported font that failed to load must not leave
        // the document's (possibly monospaced) family in control.
        fontType == "fangsong" || fontType == "kaiti" || fontType.startsWith("custom") ->
            "sans-serif"
        else -> null
    }
    val chineseMapping = ChineseConverter.mappingStrings(chineseMode)
    val config = JSONObject()
        .put("theme", theme)
        // Zero is the reader default, not an instruction to erase a
        // publisher's letter-spacing rule.
        .putOpt(
            "letterSpacingDp",
            letterSpacingDp.takeIf { abs(it) > 0.001f }?.coerceIn(-8f, 16f)
        )
        .put("textAlignment", textAlignment.key)
        .put("preservePublisherBackground", preservePublisherBackground)
        .put("bionicReading", bionicReadingEnabled)
        .put("chineseMode", chineseMode)
        .put("chineseSource", chineseMapping?.first.orEmpty())
        .put("chineseTarget", chineseMapping?.second.orEmpty())
        .putOpt("fontFamily", fontFamily)
        .putOpt("fontUrl", readerFontUrl)
        .put("bodyFontWeight", bodyFontWeight.coerceIn(100, 900))
        .putOpt("textColor", textColorOverride?.let { String.format("#%06X", it and 0xFFFFFF) })
        .put("progression", progression)
        .put("progressionValue", restoreProgression.coerceIn(0f, 1f))
        .put("flow", if (continuousScroll) "scrolled" else "paginated")
        .put("nativePaging", nativePagingEnabled)
        .put("transition", pageTransition)
        .put("transitionDurationMs", pageTransitionDurationMs.coerceIn(100, 1200))
        .put("edgeTapLeft", edgeTapMode.leftAction.toEpubTurnDirection())
        .put("edgeTapRight", edgeTapMode.rightAction.toEpubTurnDirection())
        .put("canTurnPrevious", chapterIndex > 0)
        .put("canTurnNext", chapterIndex + 1 < session.chapterCount)
        .put(
            "insets",
            JSONObject()
                .put("top", marginTopDp)
                .put("right", marginRightDp)
                .put("bottom", marginBottomDp)
                .put("left", marginLeftDp)
        )
    val chapterPath = session.chapterHref(chapterIndex)
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
        if (pageRequest?.chapterIndex == chapterIndex) {
            if (preparePageRequest) {
                append("window.LumiReader.preparePage(")
                append(pageRequest.pageIndex.coerceAtLeast(0))
                append(',')
                append(pageRequest.token)
                append(");")
            } else if (pageRequest.chapterFraction != null) {
                append("window.LumiReader.goToProgression(")
                append(pageRequest.chapterFraction.coerceIn(0f, 1f))
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

/**
 * 同章节内部锚点直接在当前 WebView 内跳转，避免经过 ReaderScreen -> setChapter
 * 的同章节 early-return 而静默失效。目标不存在时给用户一个可见反馈。
 */
private fun EpubContentWebView.jumpToInternalFragment(fragment: String?) {
    if (fragment.isNullOrBlank()) return
    evaluateJavascript(
        "window.LumiReader?window.LumiReader.goToFragment(" +
            JSONObject.quote(fragment) + "):false"
    ) { result ->
        if (result?.trim() == "false" && isAttachedToWindow) {
            post {
                if (isAttachedToWindow) showMissingLinkTarget()
            }
        }
    }
}

private fun EpubContentWebView.showMissingLinkTarget() {
    android.widget.Toast.makeText(
        context,
        context.getString(com.huangder.lumibooks.R.string.epub_link_target_missing),
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

private suspend fun requestPageText(
    view: WebView?,
    loadedChapter: Int,
    requestedChapter: Int,
    requestedPage: Int,
    expectedUrl: String
): EpubPageText? {
    if (view == null || loadedChapter != requestedChapter ||
        view.url.orEmpty().substringBefore('#') != expectedUrl
    ) return null
    return suspendCancellableCoroutine { continuation ->
        view.post {
            if (!continuation.isActive) return@post
            view.evaluateJavascript(
                "window.LumiReader?JSON.stringify(window.LumiReader.pageText(" +
                    requestedPage.coerceAtLeast(0) + ")):null"
            ) { encoded ->
                if (!continuation.isActive) return@evaluateJavascript
                if (view.url.orEmpty().substringBefore('#') != expectedUrl) {
                    continuation.resume(null)
                    return@evaluateJavascript
                }
                val decoded = runCatching { JSONArray("[$encoded]").optString(0) }.getOrNull()
                val payload = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                continuation.resume(
                    payload?.let {
                        EpubPageText(
                            chapterIndex = requestedChapter,
                            pageIndex = it.optInt("pageIndex", requestedPage).coerceAtLeast(0),
                            pageCount = it.optInt("pageCount", 1).coerceAtLeast(1),
                            text = it.optString("text"),
                            chapterText = it.optString("chapterText"),
                            startCharacterOffset = it.optInt("startCharacterOffset", 0)
                                .coerceAtLeast(0),
                            endCharacterOffset = it.optInt(
                                "endCharacterOffset",
                                it.optString("chapterText").length
                            ).coerceAtLeast(0)
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
        val item = JSONObject().put("exact", note.selectedText).put("color", note.color.toCssColor()).put("type", note.type)
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

private fun EpubLocator.toJson(): JSONObject = JSONObject()
    .put("version", version)
    .put("href", href)
    .put("domPath", JSONArray(domPath))
    .put("textOffset", textOffset)
    .put("textPosition", textPosition)
    .put("textLength", textLength)
    .put("exact", exact)
    .put("prefix", prefix)
    .put("suffix", suffix)
    .put("progression", progression)

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
