package com.huangder.lumibooks.ui.reader

import android.graphics.Color
import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import com.huangder.lumibooks.domain.model.*
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.GlassSnapshotProvider
import com.huangder.lumibooks.util.epub.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Styles shared by both leaves. Locators and decorations deliberately stay outside layout identity. */
internal data class EpubSpreadAppearance(
    val fontSize: Float,
    val letterSpacing: Float,
    val textSpacing: EpubTextSpacing,
    val fontType: String,
    val fontPath: String?,
    val customFonts: List<CustomFontPreset>,
    val weight: Float,
    val textColor: Int?,
    val theme: String,
    val coverColor: Int?,
    val paperColor: Int?,
    val backgroundPath: String?,
    val backgroundOpacity: Float,
    val backgroundBlur: Float,
    val autoTextColor: Int?,
    val alignment: ReaderTextAlignment,
    val publisherBackground: Boolean,
    val crop: Boolean,
    val publisherPaint: Boolean,
    val bionic: Boolean,
    val chinese: String,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float,
    val selectionRange: LongPressSelectionRange,
    val images: ReaderImageAdjustments
) {
    val backgroundColor: Int get() = paperColor ?: when (theme) {
        "night", "publisher_night" -> Color.rgb(0x11, 0x11, 0x11)
        "sepia_dark" -> Color.rgb(0x2B, 0x21, 0x18)
        "green_dark" -> Color.rgb(0x14, 0x2A, 0x1A)
        "sepia" -> Color.rgb(0xF5, 0xE6, 0xD3)
        "green" -> Color.rgb(0xE8, 0xF5, 0xE9)
        else -> Color.WHITE
    }
}

internal data class EpubSpreadCallbacks(
    val page: (Int, Int, Int, String?) -> Unit,
    val spread: (EpubSpreadState?) -> Unit,
    val focus: (EpubSpreadPage) -> Unit,
    val signature: (String) -> Unit,
    val count: (String, Int, Int) -> Unit,
    val navigationStage: (Long, EpubNavigationStage) -> Unit,
    val navigation: (EpubNavigationResult) -> Unit,
    val failure: (EpubRenderFailure) -> Unit,
    val menu: () -> Unit,
    val manual: () -> Unit,
    val selection: (EpubSelectionInfo) -> Unit,
    val selectionCleared: () -> Unit,
    val dismissSelection: () -> Unit,
    val internalLink: (Int, String?) -> Unit,
    val externalLink: (String) -> Unit,
    val search: (Int, Boolean) -> Unit,
    val image: () -> Unit,
    val bookmarkStart: () -> Unit,
    val bookmarkProgress: (Float, Boolean) -> Unit,
    val bookmarkFinish: (Boolean) -> Unit
)

@Composable
internal fun EpubSpreadContent(
    session: BookRenderSession,
    chapterIndex: Int,
    appearance: EpubSpreadAppearance,
    restoreLocatorJson: String?,
    restoreProgression: Float,
    restoreProgressionInclusive: Boolean,
    initialFragment: String?,
    pageTransition: String,
    pageTransitionDurationMs: Int,
    edgeTapMode: ReaderEdgeTapMode,
    notes: List<Note>,
    ttsCurrentSentence: TtsSentencePosition?,
    ttsHighlightColor: Int,
    ttsFollowEnabled: Boolean,
    searchRequest: EpubSearchRequest?,
    locatorRequest: EpubLocatorRequest?,
    pageRequest: EpubPageRequest?,
    navigationRequest: EpubNavigationRequest?,
    selectionClearToken: Int,
    dictionarySelection: EpubDictionarySelection?,
    selectionMenuVisible: Boolean,
    bookmarkPullEnabled: Boolean,
    paginationContentIdentity: String,
    callbacks: EpubSpreadCallbacks,
    onPageTextProviderReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    onPageTurnHandlerReady: (((Int) -> Boolean)?) -> Unit,
    onReleaseHandlerReady: (((() -> Unit) -> Unit)?) -> Unit,
    onGlassSnapshotProviderReady: (GlassSnapshotProvider?) -> Unit,
    modifier: Modifier
) {
    val latest = rememberUpdatedState(callbacks)
    val latestAppearance = rememberUpdatedState(appearance)
    val latestNotes = rememberUpdatedState(notes)
    val latestTts = rememberUpdatedState(ttsCurrentSentence)
    val latestTtsColor = rememberUpdatedState(ttsHighlightColor)
    val latestRestoreInclusive = rememberUpdatedState(restoreProgressionInclusive)
    val latestSelectionMenu = rememberUpdatedState(selectionMenuVisible)
    var host by remember(session) { mutableStateOf<EpubSpreadTurnHost?>(null) }
    var viewport by remember(session) { mutableStateOf(0 to 0) }
    var state by remember(session) { mutableStateOf<EpubSpreadState?>(null) }
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val webVersion = remember { WebViewCompat.getCurrentWebViewPackage(context)?.versionName.orEmpty() }
    val signature = listOf("original-spread-v1", paginationContentIdentity, viewport,
        metrics.density, metrics.scaledDensity, webVersion, appearance,
        appearance.fontPath?.let { java.io.File(it).let { file -> file.length() to file.lastModified() } }
    ).joinToString("|")
    val latestSignature = rememberUpdatedState(signature)
    val ruleFonts = rememberEpubRuleFontUrls(session, notes, appearance.customFonts)
    val latestRuleFonts = rememberUpdatedState(ruleFonts)
    val initial = remember(session) {
        EpubNavigationRequest(Long.MIN_VALUE, EpubNavigationOrigin.RETURN_TO_SOURCE,
            chapterIndex, 0, chapterIndex,
            restoreLocatorJson?.let(EpubNavigationDestination::Locator)
                ?: initialFragment?.takeIf(String::isNotBlank)?.let(EpubNavigationDestination::Fragment)
                ?: EpubNavigationDestination.Page(0, restoreProgression), false)
    }
    val openingRequest = remember(session) { EpubSpreadOpeningRequest(initial) }
    val explicitRequest = navigationRequest ?: locatorRequest?.let {
        EpubNavigationRequest(Long.MIN_VALUE / 2 + it.token, EpubNavigationOrigin.RETURN_TO_SOURCE,
            chapterIndex, 0, it.chapterIndex, EpubNavigationDestination.Locator(it.locatorJson), false)
    } ?: pageRequest?.let {
        EpubNavigationRequest(Long.MIN_VALUE / 4 + it.token, EpubNavigationOrigin.PROGRESS,
            chapterIndex, 0, it.chapterIndex, EpubNavigationDestination.Page(it.pageIndex, it.chapterFraction), false)
    }
    val externalRequest = openingRequest.resolve(explicitRequest)
    var image by remember(session) { mutableStateOf<EpubImagePreviewRequest?>(null) }
    val imageProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var lastSearch by remember(session) { mutableStateOf<Int?>(null) }
    val latestSearch = rememberUpdatedState(searchRequest)

    fun decorate(view: EpubContentWebView, chapter: Int) {
        applyHighlights(view, epubNotesForChapter(latestNotes.value, chapter), latestRuleFonts.value)
        applyTtsHighlight(view, chapter, latestTts.value, latestTtsColor.value, false)
        view.evaluateVisualUpdate("images", epubImageAdjustmentScript(latestAppearance.value.images))
    }

    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext ->
            if (com.huangder.lumibooks.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            EpubSpreadTurnHost(viewContext).apply {
                configureReader = { view, request ->
                    val a = latestAppearance.value
                    val chapter = request.target.chapterIndex
                    view.settings.textZoom = if (session.renditionLayout(chapter) == EpubRenditionLayout.PRE_PAGINATED) 100
                        else ((a.fontSize / 16f) * 100f).toInt().coerceIn(50, 300)
                    val destination = request.destination
                    com.huangder.lumibooks.ui.reader.configureReader(
                        view = view, paginationSignature = latestSignature.value,
                        session = session, chapterIndex = chapter, fontType = a.fontType,
                        fontFilePath = a.fontPath, letterSpacingDp = a.letterSpacing,
                        textSpacing = a.textSpacing, bodyFontWeight = a.weight,
                        textColorOverride = a.textColor, theme = a.theme,
                        coverBackgroundColor = a.coverColor.takeIf { session.pagePresentation(chapter).isCover },
                        readerBackgroundColor = a.paperColor, autoTextColor = a.autoTextColor,
                        readerBackgroundHasImage = a.backgroundPath != null,
                        readerBackgroundUrl = session.readerBackgroundUrl(a.backgroundPath),
                        textAlignment = a.alignment, preservePublisherBackground = a.publisherBackground,
                        imagePageCrop = a.crop, publisherPaintOnly = a.publisherPaint,
                        bionicReadingEnabled = a.bionic, chineseMode = a.chinese,
                        restoreLocatorJson = (destination as? EpubNavigationDestination.Locator)?.json,
                        restoreProgression = (destination as? EpubNavigationDestination.Page)?.chapterFraction ?: 0f,
                        restoreProgressionInclusive = destination == initial.destination && latestRestoreInclusive.value,
                        initialFragment = (destination as? EpubNavigationDestination.Fragment)?.value,
                        continuousScroll = false, nativePagingEnabled = true,
                        pageTransition = "none", pageTransitionDurationMs = 1,
                        edgeTapMode = edgeTapMode, longPressSelectionRange = a.selectionRange,
                        marginTopDp = a.top, marginRightDp = a.right,
                        marginBottomDp = a.bottom, marginLeftDp = a.left,
                        notes = epubNotesForChapter(latestNotes.value, chapter), ruleFontUrls = latestRuleFonts.value,
                        locatorRequest = (destination as? EpubNavigationDestination.Locator)?.let {
                            EpubLocatorRequest(request.documentGeneration, chapter, it.json)
                        },
                        pageRequest = when (destination) {
                            is EpubNavigationDestination.Page -> EpubPageRequest(request.documentGeneration,
                                chapter, destination.index, destination.chapterFraction)
                            EpubNavigationDestination.ChapterStart -> EpubPageRequest(request.documentGeneration, chapter, 0)
                            else -> null
                        },
                        // A fresh document initially reports one page. Queue leaf positioning
                        // until pagination settles instead of clamping a later page to zero.
                        preparePageRequest = destination is EpubNavigationDestination.ChapterStart ||
                            destination is EpubNavigationDestination.Page && destination.chapterFraction == null,
                        backgroundImageOpacity = a.backgroundOpacity,
                        backgroundImageBlurDp = a.backgroundBlur
                    )
                    decorate(view, chapter)
                }
                onViewportChanged = { width, height -> viewport = width to height }
                onPageCountConfirmed = { chapter, count -> latest.value.count(latestSignature.value, chapter, count) }
                onSpreadChanged = { spread ->
                    state = spread
                    visibleWebViews().forEach { view ->
                        session.chapterIndexForUrl(view.url.orEmpty())?.let { decorate(view, it) }
                    }
                    latest.value.spread(spread)
                    val focused = spread.focused
                    latest.value.page(focused.target.chapterIndex, focused.target.pageIndex,
                        focused.pageCount, focused.locatorJson)
                }
                onFocusedPageChanged = { page ->
                    state = state?.copy(focused = page)
                    latest.value.focus(page)
                }
                onNavigationResult = { latest.value.navigation(it) }
                onNavigationStage = { request, stage -> latest.value.navigationStage(request.operationId, stage) }
                onFailure = { latest.value.failure(it) }
                onManualTurnStarted = { latest.value.manual() }
                onBookmarkPullStart = { latest.value.bookmarkStart() }
                onBookmarkPullProgress = { distance, armed -> latest.value.bookmarkProgress(distance, armed) }
                onBookmarkPullFinished = { latest.value.bookmarkFinish(it) }
                onCenterTap = {
                    if (latestSelectionMenu.value) latest.value.dismissSelection() else latest.value.menu()
                }
                onInteraction = interaction@{ view, chapter, type, payload ->
                    when (type) {
                        "link" -> {
                            val href = payload.optString("href").trim()
                            if (href.isEmpty()) return@interaction
                            focus(view)
                            session.resolveInternalLink(chapter, href)?.let {
                                latest.value.internalLink(it.first, it.second)
                            } ?: latest.value.externalLink(href)
                        }
                        "selectionCleared" -> { view.dismissSelectionUi(); latest.value.selectionCleared() }
                        "selection" -> {
                            val start = payload.optJSONObject("start") ?: return@interaction
                            val end = payload.optJSONObject("end") ?: return@interaction
                            focus(view, start.withChapterHref(session.chapterHref(chapter)).toString())
                            view.updateReaderSelectionHandles(payload)
                            val page = state?.let { listOfNotNull(it.first, it.second) }
                                ?.firstOrNull { readyView(it.target) === view }
                            latest.value.selection(epubSpreadSelection(view, chapter, page?.target?.pageIndex,
                                payload, session.chapterHref(chapter)))
                        }
                        "image" -> {
                            focus(view)
                            val source = payload.optString("source").trim()
                            if (source.isEmpty()) return@interaction
                            val scale = payload.optDouble("pixelRatio", view.resources.displayMetrics.density.toDouble())
                                .toFloat().coerceAtLeast(1f)
                            val origin = IntArray(2).also(view::getLocationInWindow)
                            val hostOrigin = IntArray(2).also(::getLocationInWindow)
                            fun x(key: String) = origin[0] - hostOrigin[0] + payload.optDouble(key).toFloat() * scale
                            fun y(key: String) = origin[1] - hostOrigin[1] + payload.optDouble(key).toFloat() * scale
                            image = EpubImagePreviewRequest(source, payload.optString("alt"),
                                x("left"), y("top"), x("right"), y("bottom"),
                                payload.optInt("naturalWidth"), payload.optInt("naturalHeight"))
                            latest.value.image()
                            scope.launch { imageProgress.snapTo(0f); imageProgress.animateTo(1f,
                                tween(220, easing = FastOutSlowInEasing)) }
                        }
                        "searchResult" -> {
                            val request = latestSearch.value
                            if (request?.token == payload.optInt("requestToken") && request.chapterIndex == chapter) {
                                latest.value.search(request.token, payload.optBoolean("found"))
                            }
                        }
                    }
                }
                host = this
            }
        }, update = { view ->
            view.selectionMenuVisible = selectionMenuVisible
            view.bookmarkPullEnabled = bookmarkPullEnabled
            view.update(session, signature, externalRequest, pageTransition, pageTransitionDurationMs,
                session.pageProgressionDirection(chapterIndex) == EpubPageProgressionDirection.RTL,
                edgeTapMode, appearance.backgroundColor)
        }, onRelease = { view -> view.release(); if (host === view) host = null })
        image?.let { request ->
            EpubImagePreviewOverlay(session, appearance.images, request, imageProgress.value) {
                scope.launch { imageProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing)); image = null }
            }
        }
    }

    LaunchedEffect(signature) {
        if (viewport.first > 0 && viewport.second > 0) latest.value.signature(signature)
    }
    LaunchedEffect(notes, ruleFonts, ttsCurrentSentence, ttsHighlightColor, ttsFollowEnabled, appearance.images, host) {
        host?.allWebViews()?.forEach { view ->
            session.chapterIndexForUrl(view.url.orEmpty())?.let { decorate(view, it) }
        }
    }
    LaunchedEffect(selectionClearToken) {
        if (selectionClearToken > 0) host?.allWebViews()?.forEach(EpubContentWebView::clearTextSelection)
    }
    LaunchedEffect(dictionarySelection, host, state) {
        host?.visibleWebViews()?.forEach { view ->
            val selection = dictionarySelection?.takeIf {
                val href = runCatching { JSONObject(it.startLocatorJson).optString("href") }.getOrNull()
                session.chapterIndexForUrl(view.url.orEmpty())?.let(session::chapterHref) == href
            }
            val script = selection?.let {
                val start = runCatching { JSONObject(it.startLocatorJson) }.getOrNull() ?: return@let null
                val end = runCatching { JSONObject(it.endLocatorJson) }.getOrNull() ?: return@let null
                "window.LumiReader&&window.LumiReader.setDictionarySelection($start,$end,${JSONObject.quote(it.exact)});"
            } ?: "window.LumiReader&&window.LumiReader.setDictionarySelection(null,null,null);"
            view.evaluateJavascript(script, null)
        }
    }
    LaunchedEffect(searchRequest?.token, navigationRequest?.operationId, host, state) {
        val request = searchRequest ?: return@LaunchedEffect
        if (lastSearch == request.token) return@LaunchedEffect
        if (navigationRequest != null) return@LaunchedEffect
        val focused = state?.focused?.takeIf { it.target.chapterIndex == request.chapterIndex }
            ?: return@LaunchedEffect
        val view = host?.readyView(focused.target) ?: return@LaunchedEffect
        lastSearch = request.token
        host?.allWebViews()?.forEach { it.evaluateJavascript("window.LumiReader&&window.LumiReader.clearSearchHighlight();", null) }
        view.evaluateJavascript("window.LumiReader&&window.LumiReader.findText(${request.locator.toJson()},${request.token});", null)
    }
    val latestTextReady = rememberUpdatedState(onPageTextProviderReady)
    val latestTurnReady = rememberUpdatedState(onPageTurnHandlerReady)
    val latestReleaseReady = rememberUpdatedState(onReleaseHandlerReady)
    val latestGlassReady = rememberUpdatedState(onGlassSnapshotProviderReady)
    DisposableEffect(session) {
        latestTextReady.value { chapter, page -> requestPageText(host?.readyView(EpubPageTarget(chapter, page))
            ?: host?.allWebViews()?.firstOrNull {
                session.chapterIndexForUrl(it.url.orEmpty()) == chapter &&
                    it.documentLifecycle.isStable(it.documentLifecycle.configurationGeneration, it.documentLifecycle.layoutRevision)
            },
            chapter, chapter, page, session.chapterUrl(chapter).substringBefore('#')) }
        latestTurnReady.value { direction -> host?.turn(direction) == true }
        latestReleaseReady.value { released -> host?.release(released) ?: released() }
        latestGlassReady.value { quality -> host?.captureGlassBackdrop(quality) }
        onDispose {
            latestTextReady.value(null); latestTurnReady.value(null)
            latestReleaseReady.value(null); latestGlassReady.value(null)
            host?.release(); latest.value.spread(null)
        }
    }
}

/** Convert leaf-local CSS coordinates to window pixels before opening the shared selection menu. */
internal fun epubSpreadSelection(view: WebView, chapter: Int, page: Int?, payload: JSONObject, href: String): EpubSelectionInfo {
    val text = payload.optString("text")
    val start = payload.getJSONObject("start")
    val end = payload.getJSONObject("end")
    val origin = IntArray(2).also(view::getLocationInWindow)
    val ratio = payload.optDouble("pixelRatio", view.resources.displayMetrics.density.toDouble()).toFloat().coerceAtLeast(1f)
    fun x(name: String, fallback: String = "x") = origin[0] + payload.optDouble(name, payload.optDouble(fallback, 0.0)).toFloat() * ratio
    fun y(name: String, fallback: String = "y") = origin[1] + payload.optDouble(name, payload.optDouble(fallback, 0.0)).toFloat() * ratio
    return EpubSelectionInfo(text, start.optInt("textPosition").coerceAtLeast(0),
        end.optInt("textPosition", text.length).coerceAtLeast(start.optInt("textPosition")),
        start.withChapterHref(href).toString(), end.withChapterHref(href).toString(),
        x("x"), y("y"), x("left"), y("top"), x("right"), y("bottom"),
        payload.optBoolean("annotationOnly"), chapter, page)
}
