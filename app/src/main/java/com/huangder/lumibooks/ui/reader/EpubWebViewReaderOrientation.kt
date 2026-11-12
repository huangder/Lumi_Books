package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.ActionMode
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.animation.VisualMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.huangder.lumibooks.util.ChineseConverter
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.EpubPageProgressionDirection
import com.huangder.lumibooks.util.epub.EpubLocator
import com.huangder.lumibooks.util.epub.EpubRenditionLayout
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import com.huangder.lumibooks.util.performance.EpubTurnTrace
import org.json.JSONArray
import org.json.JSONObject
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.ReaderEdgeTapAction
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle
import com.huangder.lumibooks.ui.reader.engine.TtsSentenceHighlightSpan
import com.huangder.lumibooks.highlight.RuleStyleJson
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun EpubWebViewReader(
    session: BookRenderSession,
    twoPageSpread: Boolean = false,
    orientationPrewarm: Boolean = false,
    onSpreadChanged: (EpubSpreadState?) -> Unit = {},
    onFocusedPageChanged: (EpubSpreadPage) -> Unit = {},
    layoutPreviewActive: Boolean = false,
    imageAdjustments: com.huangder.lumibooks.domain.model.ReaderImageAdjustments = com.huangder.lumibooks.domain.model.ReaderImageAdjustments(),
    chapterIndex: Int,
    nativeCssEnabled: Boolean = false,
    fontSizeSp: Float,
    letterSpacingDp: Float = 0f,
    textSpacing: EpubTextSpacing = EpubTextSpacing(),
    fontType: String,
    fontFilePath: String?,
    customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset> = emptyList(),
    bodyFontWeight: Float = 400f,
    textColorOverride: Int?,
    theme: String,
    coverBackgroundColor: Int?,
    /**
     * Base color under the pages when the reader uses its own background
     * (custom preset or custom image). Null keeps the built-in theme colors so
     * day/night/sepia/green stay pixel-identical to before.
     */
    readerBackgroundColorOverride: Int? = null,
    readerBackgroundImagePath: String? = null,
    readerBackgroundImageOpacity: Float = 1f,
    readerBackgroundImageBlurDp: Float = 0f,
    /** Auto-derived text color, applied only where the reader background shows. */
    autoTextColor: Int? = null,
    textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    preservePublisherBackground: Boolean = true,
    /** 整页图片按屏幕比例裁切铺满（默认等比留白）。 */
    imagePageCrop: Boolean = false,
    /** 「原排版」套装：阅读器不参与配色，底色与文字颜色完全交给书籍自己。 */
    publisherPaintOnly: Boolean = false,
    bionicReadingEnabled: Boolean = false,
    chineseMode: String = "original",
    restoreLocatorJson: String?,
    restoreProgression: Float,
    /** 恢复分数是否为"页尾"语义（书籍原排版按 (pageIndex+1)/totalPages 存档）。 */
    restoreProgressionInclusive: Boolean = false,
    initialFragment: String? = null,
    continuousScroll: Boolean = false,
    pageTransition: String = "slide",
    pageTransitionDurationMs: Int = 260,
    marginTopDp: Float = 0f,
    marginRightDp: Float = 0f,
    marginBottomDp: Float = 0f,
    marginLeftDp: Float = 0f,
    edgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    longPressSelectionRange: LongPressSelectionRange = LongPressSelectionRange.DEFAULT,
    annotationStyle: ReaderAnnotationStyle = ReaderAnnotationStyle.DEFAULT,
    notes: List<Note> = emptyList(),
    highlightRules: List<HighlightRule> = emptyList(),
    ruleHighlightsRevision: Long = 0L,
    loadRuleHighlights: suspend (Int, List<HighlightRule>) -> List<Note> = { _, _ -> emptyList() },
    ttsCurrentSentence: TtsSentencePosition? = null,
    ttsFollowEnabled: Boolean = true,
    ttsManualResumeSentence: TtsSentencePosition? = null,
    ttsViewportGeneration: Long? = null,
    onTtsVisibility: (Long, TtsSentencePosition, Boolean) -> Unit = { _, _, _ -> },
    onManualScrollStarted: () -> Unit = {},
    onManualScrollSettled: (Int, Int?) -> Unit = { _, _ -> },
    ttsHighlightColor: Int = TtsSentenceHighlightSpan.computeHighlightColor(Color.WHITE),
    searchRequest: EpubSearchRequest? = null,
    locatorRequest: EpubLocatorRequest? = null,
    pageRequest: EpubPageRequest? = null,
    navigationRequest: EpubNavigationRequest? = null,
    bookFormat: String = "EPUB",
    selectionClearToken: Int = 0,
    dictionarySelection: EpubDictionarySelection? = null,
    selectionMenuVisible: Boolean = false,
    bookmarkPullEnabled: Boolean = true,
    onPageTextProviderReady: ((suspend (chapterIndex: Int, pageIndex: Int) -> EpubPageText?)?) -> Unit,
    onPageTurnHandlerReady: (((direction: Int) -> Boolean)?) -> Unit,
    onReleaseHandlerReady: (((onReleased: () -> Unit) -> Unit)?) -> Unit = {},
    onGlassSnapshotProviderReady: (com.huangder.lumibooks.ui.components.GlassSnapshotProvider?) -> Unit = {},
    onPageChanged: (chapterIndex: Int, pageIndex: Int, pageCount: Int, locatorJson: String?) -> Unit,
    paginationContentIdentity: String = "",
    onPaginationConfigured: (String) -> Unit = {},
    onPageCountConfirmed: (signature: String, chapterIndex: Int, pageCount: Int) -> Unit = { _, _, _ -> },
    onBookmarkPullStart: () -> Unit = {},
    onBookmarkPullProgress: (distancePx: Float, armed: Boolean) -> Unit = { _, _ -> },
    onBookmarkPullFinished: (commit: Boolean) -> Unit = {},
    onCenterTap: () -> Unit,
    onSelectionMenuDismiss: () -> Unit = {},
    onImagePreviewOpen: () -> Unit,
    onChapterTurn: (direction: Int) -> Unit,
    onBookEnd: () -> Unit = {},
    onBookEndPull: (Float) -> Unit = {},
    onBookEndPullEnd: (cancelled: Boolean) -> Unit = {},
    onInternalLink: (chapterIndex: Int, fragment: String?) -> Unit,
    onExternalLink: (href: String) -> Unit,
    onSelection: (EpubSelectionInfo) -> Unit,
    onSelectionCleared: () -> Unit,
    onSearchResolved: (token: Int, found: Boolean) -> Unit,
    onNavigationStage: (operationId: Long, stage: EpubNavigationStage) -> Unit = { _, _ -> },
    onNavigationResult: (EpubNavigationResult) -> Unit = {},
    onRenderUnavailable: (EpubRenderFailure) -> Unit,
    onRendererEngineResolved: (nativeCss: Boolean) -> Unit = {},
    autoScrollRunning: Boolean = false,
    autoScrollSpeed: Float = 1f,
    onAutoScrollEnd: () -> Unit = {},
    modifier: Modifier = Modifier
,
    orientationSpreadEnabled: Boolean = twoPageSpread
) {
    val args = EpubWebViewReaderOrientationArguments(
        session = session,
        twoPageSpread = twoPageSpread,
        orientationPrewarm = orientationPrewarm,
        onSpreadChanged = onSpreadChanged,
        onFocusedPageChanged = onFocusedPageChanged,
        layoutPreviewActive = layoutPreviewActive,
        imageAdjustments = imageAdjustments,
        chapterIndex = chapterIndex,
        nativeCssEnabled = nativeCssEnabled,
        fontSizeSp = fontSizeSp,
        letterSpacingDp = letterSpacingDp,
        textSpacing = textSpacing,
        fontType = fontType,
        fontFilePath = fontFilePath,
        customFonts = customFonts,
        bodyFontWeight = bodyFontWeight,
        textColorOverride = textColorOverride,
        theme = theme,
        coverBackgroundColor = coverBackgroundColor,
        readerBackgroundColorOverride = readerBackgroundColorOverride,
        readerBackgroundImagePath = readerBackgroundImagePath,
        readerBackgroundImageOpacity = readerBackgroundImageOpacity,
        readerBackgroundImageBlurDp = readerBackgroundImageBlurDp,
        autoTextColor = autoTextColor,
        textAlignment = textAlignment,
        preservePublisherBackground = preservePublisherBackground,
        imagePageCrop = imagePageCrop,
        publisherPaintOnly = publisherPaintOnly,
        bionicReadingEnabled = bionicReadingEnabled,
        chineseMode = chineseMode,
        restoreLocatorJson = restoreLocatorJson,
        restoreProgression = restoreProgression,
        restoreProgressionInclusive = restoreProgressionInclusive,
        initialFragment = initialFragment,
        continuousScroll = continuousScroll,
        pageTransition = pageTransition,
        pageTransitionDurationMs = pageTransitionDurationMs,
        marginTopDp = marginTopDp,
        marginRightDp = marginRightDp,
        marginBottomDp = marginBottomDp,
        marginLeftDp = marginLeftDp,
        edgeTapMode = edgeTapMode,
        longPressSelectionRange = longPressSelectionRange,
        annotationStyle = annotationStyle,
        notes = notes,
        highlightRules = highlightRules,
        ruleHighlightsRevision = ruleHighlightsRevision,
        loadRuleHighlights = loadRuleHighlights,
        ttsCurrentSentence = ttsCurrentSentence,
        ttsFollowEnabled = ttsFollowEnabled,
        ttsManualResumeSentence = ttsManualResumeSentence,
        ttsViewportGeneration = ttsViewportGeneration,
        onTtsVisibility = onTtsVisibility,
        onManualScrollStarted = onManualScrollStarted,
        onManualScrollSettled = onManualScrollSettled,
        ttsHighlightColor = ttsHighlightColor,
        searchRequest = searchRequest,
        locatorRequest = locatorRequest,
        pageRequest = pageRequest,
        navigationRequest = navigationRequest,
        bookFormat = bookFormat,
        selectionClearToken = selectionClearToken,
        dictionarySelection = dictionarySelection,
        selectionMenuVisible = selectionMenuVisible,
        bookmarkPullEnabled = bookmarkPullEnabled,
        onPageTextProviderReady = onPageTextProviderReady,
        onPageTurnHandlerReady = onPageTurnHandlerReady,
        onReleaseHandlerReady = onReleaseHandlerReady,
        onGlassSnapshotProviderReady = onGlassSnapshotProviderReady,
        onPageChanged = onPageChanged,
        paginationContentIdentity = paginationContentIdentity,
        onPaginationConfigured = onPaginationConfigured,
        onPageCountConfirmed = onPageCountConfirmed,
        onBookmarkPullStart = onBookmarkPullStart,
        onBookmarkPullProgress = onBookmarkPullProgress,
        onBookmarkPullFinished = onBookmarkPullFinished,
        onCenterTap = onCenterTap,
        onSelectionMenuDismiss = onSelectionMenuDismiss,
        onImagePreviewOpen = onImagePreviewOpen,
        onChapterTurn = onChapterTurn,
        onBookEnd = onBookEnd,
        onBookEndPull = onBookEndPull,
        onBookEndPullEnd = onBookEndPullEnd,
        onInternalLink = onInternalLink,
        onExternalLink = onExternalLink,
        onSelection = onSelection,
        onSelectionCleared = onSelectionCleared,
        onSearchResolved = onSearchResolved,
        onNavigationStage = onNavigationStage,
        onNavigationResult = onNavigationResult,
        onRenderUnavailable = onRenderUnavailable,
        onRendererEngineResolved = onRendererEngineResolved,
        autoScrollRunning = autoScrollRunning,
        autoScrollSpeed = autoScrollSpeed,
        onAutoScrollEnd = onAutoScrollEnd,
        modifier = modifier,
    )
    var anchor by remember(session) { mutableStateOf<Pair<Int, String?>?>(chapterIndex to restoreLocatorJson) }
    ReaderOrientationContent(session, modifier) { viewport, active, events ->
        args.Render(viewport, active, events, anchor, { chapter, locator -> if (locator != null) anchor = chapter to locator }, orientationSpreadEnabled)
    }
}

private class EpubWebViewReaderOrientationArguments(
    val session: BookRenderSession,
    val twoPageSpread: Boolean,
    val orientationPrewarm: Boolean,
    val onSpreadChanged: (EpubSpreadState?) -> Unit,
    val onFocusedPageChanged: (EpubSpreadPage) -> Unit,
    val layoutPreviewActive: Boolean,
    val imageAdjustments: com.huangder.lumibooks.domain.model.ReaderImageAdjustments,
    val chapterIndex: Int,
    val nativeCssEnabled: Boolean,
    val fontSizeSp: Float,
    val letterSpacingDp: Float,
    val textSpacing: EpubTextSpacing,
    val fontType: String,
    val fontFilePath: String?,
    val customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset>,
    val bodyFontWeight: Float,
    val textColorOverride: Int?,
    val theme: String,
    val coverBackgroundColor: Int?,
    val readerBackgroundColorOverride: Int?,
    val readerBackgroundImagePath: String?,
    val readerBackgroundImageOpacity: Float,
    val readerBackgroundImageBlurDp: Float,
    val autoTextColor: Int?,
    val textAlignment: ReaderTextAlignment,
    val preservePublisherBackground: Boolean,
    val imagePageCrop: Boolean,
    val publisherPaintOnly: Boolean,
    val bionicReadingEnabled: Boolean,
    val chineseMode: String,
    val restoreLocatorJson: String?,
    val restoreProgression: Float,
    val restoreProgressionInclusive: Boolean,
    val initialFragment: String?,
    val continuousScroll: Boolean,
    val pageTransition: String,
    val pageTransitionDurationMs: Int,
    val marginTopDp: Float,
    val marginRightDp: Float,
    val marginBottomDp: Float,
    val marginLeftDp: Float,
    val edgeTapMode: ReaderEdgeTapMode,
    val longPressSelectionRange: LongPressSelectionRange,
    val annotationStyle: ReaderAnnotationStyle,
    val notes: List<Note>,
    val highlightRules: List<HighlightRule>,
    val ruleHighlightsRevision: Long,
    val loadRuleHighlights: suspend (Int, List<HighlightRule>) -> List<Note>,
    val ttsCurrentSentence: TtsSentencePosition?,
    val ttsFollowEnabled: Boolean,
    val ttsManualResumeSentence: TtsSentencePosition?,
    val ttsViewportGeneration: Long?,
    val onTtsVisibility: (Long, TtsSentencePosition, Boolean) -> Unit,
    val onManualScrollStarted: () -> Unit,
    val onManualScrollSettled: (Int, Int?) -> Unit,
    val ttsHighlightColor: Int,
    val searchRequest: EpubSearchRequest?,
    val locatorRequest: EpubLocatorRequest?,
    val pageRequest: EpubPageRequest?,
    val navigationRequest: EpubNavigationRequest?,
    val bookFormat: String,
    val selectionClearToken: Int,
    val dictionarySelection: EpubDictionarySelection?,
    val selectionMenuVisible: Boolean,
    val bookmarkPullEnabled: Boolean,
    val onPageTextProviderReady: ((suspend (chapterIndex: Int, pageIndex: Int) -> EpubPageText?)?) -> Unit,
    val onPageTurnHandlerReady: (((direction: Int) -> Boolean)?) -> Unit,
    val onReleaseHandlerReady: (((onReleased: () -> Unit) -> Unit)?) -> Unit,
    val onGlassSnapshotProviderReady: (com.huangder.lumibooks.ui.components.GlassSnapshotProvider?) -> Unit,
    val onPageChanged: (chapterIndex: Int, pageIndex: Int, pageCount: Int, locatorJson: String?) -> Unit,
    val paginationContentIdentity: String,
    val onPaginationConfigured: (String) -> Unit,
    val onPageCountConfirmed: (signature: String, chapterIndex: Int, pageCount: Int) -> Unit,
    val onBookmarkPullStart: () -> Unit,
    val onBookmarkPullProgress: (distancePx: Float, armed: Boolean) -> Unit,
    val onBookmarkPullFinished: (commit: Boolean) -> Unit,
    val onCenterTap: () -> Unit,
    val onSelectionMenuDismiss: () -> Unit,
    val onImagePreviewOpen: () -> Unit,
    val onChapterTurn: (direction: Int) -> Unit,
    val onBookEnd: () -> Unit,
    val onBookEndPull: (Float) -> Unit,
    val onBookEndPullEnd: (cancelled: Boolean) -> Unit,
    val onInternalLink: (chapterIndex: Int, fragment: String?) -> Unit,
    val onExternalLink: (href: String) -> Unit,
    val onSelection: (EpubSelectionInfo) -> Unit,
    val onSelectionCleared: () -> Unit,
    val onSearchResolved: (token: Int, found: Boolean) -> Unit,
    val onNavigationStage: (operationId: Long, stage: EpubNavigationStage) -> Unit,
    val onNavigationResult: (EpubNavigationResult) -> Unit,
    val onRenderUnavailable: (EpubRenderFailure) -> Unit,
    val onRendererEngineResolved: (nativeCss: Boolean) -> Unit,
    val autoScrollRunning: Boolean,
    val autoScrollSpeed: Float,
    val onAutoScrollEnd: () -> Unit,
    val modifier: Modifier,
) {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun Render(viewport: ReaderViewport, active: Boolean, events: ReaderOrientationEvents, anchor: Pair<Int, String?>?, updateAnchor: (Int, String?) -> Unit, spreadEnabled: Boolean) {
        LaunchedEffect(active) { if (active) events.retryOnActivation() }
        val hiddenNavigation = remember(anchor, viewport, events.recoveryGeneration, paginationContentIdentity, fontSizeSp, fontFilePath,
            fontType, bodyFontWeight, letterSpacingDp, textSpacing, marginLeftDp, marginRightDp, marginTopDp, marginBottomDp,
            nativeCssEnabled, textAlignment, bionicReadingEnabled, chineseMode, continuousScroll, imagePageCrop, imageAdjustments) { anchor?.second?.let { json ->
            EpubNavigationRequest(-System.nanoTime().coerceAtLeast(1), EpubNavigationOrigin.RETURN_TO_SOURCE,
                anchor.first, 0, anchor.first, EpubNavigationDestination.Locator(json), false,
                restoreSourceOnCancel = false)
        } }
        if (!events.preparationFailed && (!active || events.viewportChanged) && hiddenNavigation != null) events.expect(hiddenNavigation.operationId)
        if (active && navigationRequest != null) events.expectForeground(navigationRequest.operationId)
        EpubWebViewReaderContent(
            session = session,
            twoPageSpread = if (active) twoPageSpread else originalEpubSpreadEligible(spreadEnabled, bookFormat == "EPUB", (viewport.width / LocalDensity.current.density).toInt(), (viewport.height / LocalDensity.current.density).toInt(), false, pageTransition),
            orientationPrewarm = !active,
            onSpreadChanged = { p0 -> events.dispatch("onSpreadChanged", true) { onSpreadChanged(p0) } },
            onFocusedPageChanged = { p0 -> events.dispatch("onFocusedPageChanged", false) { updateAnchor(p0.target.chapterIndex, p0.locatorJson); onFocusedPageChanged(p0) } },
            layoutPreviewActive = layoutPreviewActive,
            imageAdjustments = imageAdjustments,
            chapterIndex = if (active) chapterIndex else anchor?.first ?: chapterIndex,
            nativeCssEnabled = nativeCssEnabled,
            fontSizeSp = fontSizeSp,
            letterSpacingDp = letterSpacingDp,
            textSpacing = textSpacing,
            fontType = fontType,
            fontFilePath = fontFilePath,
            customFonts = customFonts,
            bodyFontWeight = bodyFontWeight,
            textColorOverride = textColorOverride,
            theme = theme,
            coverBackgroundColor = coverBackgroundColor,
            readerBackgroundColorOverride = readerBackgroundColorOverride,
            readerBackgroundImagePath = readerBackgroundImagePath,
            readerBackgroundImageOpacity = readerBackgroundImageOpacity,
            readerBackgroundImageBlurDp = readerBackgroundImageBlurDp,
            autoTextColor = autoTextColor,
            textAlignment = textAlignment,
            preservePublisherBackground = preservePublisherBackground,
            imagePageCrop = imagePageCrop,
            publisherPaintOnly = publisherPaintOnly,
            bionicReadingEnabled = bionicReadingEnabled,
            chineseMode = chineseMode,
            restoreLocatorJson = if (active) restoreLocatorJson else anchor?.second ?: restoreLocatorJson,
            restoreProgression = restoreProgression,
            restoreProgressionInclusive = restoreProgressionInclusive,
            initialFragment = initialFragment,
            continuousScroll = continuousScroll,
            pageTransition = pageTransition,
            pageTransitionDurationMs = pageTransitionDurationMs,
            marginTopDp = marginTopDp,
            marginRightDp = marginRightDp,
            marginBottomDp = marginBottomDp,
            marginLeftDp = marginLeftDp,
            edgeTapMode = edgeTapMode,
            longPressSelectionRange = longPressSelectionRange,
            annotationStyle = annotationStyle,
            notes = notes,
            highlightRules = highlightRules,
            ruleHighlightsRevision = ruleHighlightsRevision,
            loadRuleHighlights = loadRuleHighlights,
            ttsCurrentSentence = ttsCurrentSentence,
            ttsFollowEnabled = active && ttsFollowEnabled,
            ttsManualResumeSentence = ttsManualResumeSentence,
            ttsViewportGeneration = ttsViewportGeneration,
            onTtsVisibility = { p0, p1, p2 -> events.dispatch("onTtsVisibility", false) { onTtsVisibility(p0, p1, p2) } },
            onManualScrollStarted = { events.dispatch("onManualScrollStarted", false) { onManualScrollStarted() } },
            onManualScrollSettled = { p0, p1 -> events.dispatch("onManualScrollSettled", false) { onManualScrollSettled(p0, p1) } },
            ttsHighlightColor = ttsHighlightColor,
            searchRequest = if (active) searchRequest else null,
            locatorRequest = if (active) locatorRequest else null,
            pageRequest = if (active) pageRequest else null,
            navigationRequest = if (active && navigationRequest != null) navigationRequest else if ((!active || events.preparationPending) && !events.preparationFailed) hiddenNavigation else null,
            bookFormat = bookFormat,
            selectionClearToken = selectionClearToken,
            dictionarySelection = if (active) dictionarySelection else null,
            selectionMenuVisible = active && selectionMenuVisible,
            bookmarkPullEnabled = active && bookmarkPullEnabled,
            onPageTextProviderReady = { p0 -> events.dispatch("onPageTextProviderReady", true) { onPageTextProviderReady(p0) } },
            onPageTurnHandlerReady = { p0 -> events.dispatch("onPageTurnHandlerReady", true) { onPageTurnHandlerReady(p0) } },
            onReleaseHandlerReady = { p0 -> events.dispatch("onReleaseHandlerReady", true) { onReleaseHandlerReady(p0) } },
            onGlassSnapshotProviderReady = { p0 -> events.dispatch("onGlassSnapshotProviderReady", true) { onGlassSnapshotProviderReady(p0) } },
            onPageChanged = { chapter, page, count, locator -> events.dispatch("onPageChanged", true) { updateAnchor(chapter, locator); onPageChanged(chapter, page, count, locator) } },
            paginationContentIdentity = paginationContentIdentity,
            onPaginationConfigured = { p0 -> events.dispatch("onPaginationConfigured", true) { onPaginationConfigured(p0) } },
            onPageCountConfirmed = { p0, p1, p2 -> events.dispatch("onPageCountConfirmed", true) { onPageCountConfirmed(p0, p1, p2) } },
            onBookmarkPullStart = { events.dispatch("onBookmarkPullStart", false) { onBookmarkPullStart() } },
            onBookmarkPullProgress = { p0, p1 -> events.dispatch("onBookmarkPullProgress", false) { onBookmarkPullProgress(p0, p1) } },
            onBookmarkPullFinished = { p0 -> events.dispatch("onBookmarkPullFinished", false) { onBookmarkPullFinished(p0) } },
            onCenterTap = { events.dispatch("onCenterTap", false) { onCenterTap() } },
            onSelectionMenuDismiss = { events.dispatch("onSelectionMenuDismiss", false) { onSelectionMenuDismiss() } },
            onImagePreviewOpen = { events.dispatch("onImagePreviewOpen", false) { onImagePreviewOpen() } },
            onChapterTurn = { p0 -> events.dispatch("onChapterTurn", false) { onChapterTurn(p0) } },
            onBookEnd = { events.dispatch("onBookEnd", false) { onBookEnd() } },
            onBookEndPull = { p0 -> events.dispatch("onBookEndPull", false) { onBookEndPull(p0) } },
            onBookEndPullEnd = { p0 -> events.dispatch("onBookEndPullEnd", false) { onBookEndPullEnd(p0) } },
            onInternalLink = { p0, p1 -> events.dispatch("onInternalLink", false) { onInternalLink(p0, p1) } },
            onExternalLink = { p0 -> events.dispatch("onExternalLink", false) { onExternalLink(p0) } },
            onSelection = { p0 -> events.dispatch("onSelection", false) { onSelection(p0) } },
            onSelectionCleared = { events.dispatch("onSelectionCleared", false) { onSelectionCleared() } },
            onSearchResolved = { p0, p1 -> events.dispatch("onSearchResolved", false) { onSearchResolved(p0, p1) } },
            onNavigationStage = { id, stage -> if (id >= 0) events.dispatch("onNavigationStage") { onNavigationStage(id, stage) } },
            onNavigationResult = { result -> events.finishNavigation(result); if (result.operationId >= 0) events.dispatch("onNavigationResult") { onNavigationResult(result) } },
            onRenderUnavailable = { failure -> events.dispatch("onRenderUnavailable") { onRenderUnavailable(failure) } },
            onRendererEngineResolved = { p0 -> events.dispatch("onRendererEngineResolved", true) { onRendererEngineResolved(p0) } },
            autoScrollRunning = active && autoScrollRunning,
            autoScrollSpeed = autoScrollSpeed,
            onAutoScrollEnd = { events.dispatch("onAutoScrollEnd", false) { onAutoScrollEnd() } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
