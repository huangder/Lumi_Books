package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import com.huangder.lumibooks.ui.reader.engine.ReaderImageHit
import com.huangder.lumibooks.tts.TtsPageChangeOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
internal val LocalOrientationDecodeMutex = staticCompositionLocalOf<Mutex?> { null }
internal val LocalOrientationDecodePermits = staticCompositionLocalOf<Semaphore?> { null }
private class ContinuousOrientationSession {
    var originalLandscape: Boolean? = null
    var anchor by mutableStateOf<ContinuousViewportAnchor?>(null)
    var anchorLandscape: Boolean? = null
    var externalRequest: Long? = null
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ContinuousScrollReader(
    chapterCount: Int,
    currentChapter: Int,
    initialChapterFraction: Float,
    initialCharacterOffset: Int?,
    fontSize: Float,
    lineHeight: Float,
    letterSpacingDp: Float,
    textAlignment: ReaderTextAlignment,
    readerTypeface: ResolvedReaderTypeface,
    textColor: Int,
    backgroundColor: Int,
    backgroundImagePath: String?,
    backgroundImageOpacity: Float,
    backgroundImageBlurDp: Float,
    marginLeft: Float,
    marginRight: Float,
    marginTop: Float,
    marginBottom: Float,
    paragraphSpacing: Float,
    firstLineIndent: Float,
    bionicReadingEnabled: Boolean,
    lineGuideEnabled: Boolean = false,
    lineGuideDimLevel: Int = 2,
    contentRevision: Long,
    loadChapterText: (Int, Int?) -> CharSequence?,
    loadChapterPreviewText: ((Int, Int?) -> ContinuousChapterPreview?)? = null,
    onContentSizeChanged: (Int, Int) -> Unit,
    notes: List<com.huangder.lumibooks.domain.model.Note>,
    searchHighlight: ContinuousSearchHighlight?,
    scrollRequests: MutableStateFlow<ContinuousScrollRequest?>,
    onSearchHighlightFinished: () -> Unit,
    onMenuToggle: () -> Unit,
    onLinkClick: (chapterIndex: Int, href: String, anchorWindowX: Float, anchorWindowY: Float) -> Unit,
    onImageLongPress: (chapterIndex: Int, image: ReaderImageHit) -> Unit,
    selectionController: ContinuousSelectionController,
    onSelectionChanging: () -> Unit,
    onSelection: (chapterIndex: Int, selection: ContinuousTextSelection) -> Unit,
    onChapterVisible: (
        chapterIndex: Int,
        chapterFraction: Float,
        origin: TtsPageChangeOrigin
    ) -> Unit,
    onRestoreComplete: () -> Unit,
    onFirstContentDrawn: () -> Unit = {},
    onSentenceDoubleTap: (chapterIndex: Int, characterOffset: Int) -> Unit,
    ttsSentenceJumpEnabled: Boolean,
    longPressSelectionRange: com.huangder.lumibooks.domain.model.LongPressSelectionRange =
        com.huangder.lumibooks.domain.model.LongPressSelectionRange.DEFAULT,
    annotationStyle: com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle =
        com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle.DEFAULT,
    chineseMode: String = "original",
    ttsCurrentSentence: TtsSentencePosition? = null,
    comicModeEnabled: Boolean = false,
    imageAdjustments: ReaderImageAdjustments = ReaderImageAdjustments(),
    onSelectionCleared: () -> Unit = {},
    onViewportAnchor: (ContinuousViewportAnchor?) -> Unit = {},
    onVisibleCharacters: (List<ReaderVisibleCharacters>) -> Unit = {},
    bodyFontWeight: Float = 400f,
    autoScrollRunning: Boolean = false,
    autoScrollSpeed: Float = 1f,
    onAutoScrollEnd: () -> Unit = {},
    /** Optional last list item shown after the final chapter; receives whether it is mostly visible. */
    finishPage: (@Composable (visible: Boolean) -> Unit)? = null,
    ttsFollowEnabled: Boolean = true,
    ttsManualResumeSentence: TtsSentencePosition? = null,
    ttsFollowUserPageTurns: Boolean = false,
    onManualScrollStarted: () -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0),
            // Each item is a whole chapter. Item-count prefetch eagerly measures
            // the next chapter on the first drag, even when it is screens away.
            cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.35f, behindFraction = 0.25f) }
        )
) {
    val args = ContinuousScrollReaderOrientationArguments(
        chapterCount = chapterCount,
        currentChapter = currentChapter,
        initialChapterFraction = initialChapterFraction,
        initialCharacterOffset = initialCharacterOffset,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacingDp = letterSpacingDp,
        textAlignment = textAlignment,
        readerTypeface = readerTypeface,
        textColor = textColor,
        backgroundColor = backgroundColor,
        backgroundImagePath = backgroundImagePath,
        backgroundImageOpacity = backgroundImageOpacity,
        backgroundImageBlurDp = backgroundImageBlurDp,
        marginLeft = marginLeft,
        marginRight = marginRight,
        marginTop = marginTop,
        marginBottom = marginBottom,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        bionicReadingEnabled = bionicReadingEnabled,
        lineGuideEnabled = lineGuideEnabled,
        lineGuideDimLevel = lineGuideDimLevel,
        contentRevision = contentRevision,
        loadChapterText = loadChapterText,
        loadChapterPreviewText = loadChapterPreviewText,
        onContentSizeChanged = onContentSizeChanged,
        notes = notes,
        searchHighlight = searchHighlight,
        scrollRequests = scrollRequests,
        onSearchHighlightFinished = onSearchHighlightFinished,
        onMenuToggle = onMenuToggle,
        onLinkClick = onLinkClick,
        onImageLongPress = onImageLongPress,
        selectionController = selectionController,
        onSelectionChanging = onSelectionChanging,
        onSelection = onSelection,
        onChapterVisible = onChapterVisible,
        onRestoreComplete = onRestoreComplete,
        onFirstContentDrawn = onFirstContentDrawn,
        onSentenceDoubleTap = onSentenceDoubleTap,
        ttsSentenceJumpEnabled = ttsSentenceJumpEnabled,
        longPressSelectionRange = longPressSelectionRange,
        annotationStyle = annotationStyle,
        chineseMode = chineseMode,
        ttsCurrentSentence = ttsCurrentSentence,
        comicModeEnabled = comicModeEnabled,
        imageAdjustments = imageAdjustments,
        onSelectionCleared = onSelectionCleared,
        onViewportAnchor = onViewportAnchor,
        onVisibleCharacters = onVisibleCharacters,
        bodyFontWeight = bodyFontWeight,
        autoScrollRunning = autoScrollRunning,
        autoScrollSpeed = autoScrollSpeed,
        onAutoScrollEnd = onAutoScrollEnd,
        finishPage = finishPage,
        ttsFollowEnabled = ttsFollowEnabled,
        ttsManualResumeSentence = ttsManualResumeSentence,
        ttsFollowUserPageTurns = ttsFollowUserPageTurns,
        onManualScrollStarted = onManualScrollStarted,
        listState = listState,
    )
    val session = remember(scrollRequests) { ContinuousOrientationSession() }
    val mutex = remember(scrollRequests) { Mutex() }
    val permits = remember(scrollRequests) { Semaphore(2) }
    CompositionLocalProvider(LocalOrientationDecodeMutex provides mutex, LocalOrientationDecodePermits provides permits) {
        ReaderOrientationContent(scrollRequests) { viewport, active, events ->
            if (active && session.originalLandscape == null) session.originalLandscape = viewport.landscape
            args.Render(viewport, active, events, session)
        }
    }
}

private class ContinuousScrollReaderOrientationArguments(
    val chapterCount: Int,
    val currentChapter: Int,
    val initialChapterFraction: Float,
    val initialCharacterOffset: Int?,
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacingDp: Float,
    val textAlignment: ReaderTextAlignment,
    val readerTypeface: ResolvedReaderTypeface,
    val textColor: Int,
    val backgroundColor: Int,
    val backgroundImagePath: String?,
    val backgroundImageOpacity: Float,
    val backgroundImageBlurDp: Float,
    val marginLeft: Float,
    val marginRight: Float,
    val marginTop: Float,
    val marginBottom: Float,
    val paragraphSpacing: Float,
    val firstLineIndent: Float,
    val bionicReadingEnabled: Boolean,
    val lineGuideEnabled: Boolean,
    val lineGuideDimLevel: Int,
    val contentRevision: Long,
    val loadChapterText: (Int, Int?) -> CharSequence?,
    val loadChapterPreviewText: ((Int, Int?) -> ContinuousChapterPreview?)?,
    val onContentSizeChanged: (Int, Int) -> Unit,
    val notes: List<com.huangder.lumibooks.domain.model.Note>,
    val searchHighlight: ContinuousSearchHighlight?,
    val scrollRequests: MutableStateFlow<ContinuousScrollRequest?>,
    val onSearchHighlightFinished: () -> Unit,
    val onMenuToggle: () -> Unit,
    val onLinkClick: (chapterIndex: Int, href: String, anchorWindowX: Float, anchorWindowY: Float) -> Unit,
    val onImageLongPress: (chapterIndex: Int, image: ReaderImageHit) -> Unit,
    val selectionController: ContinuousSelectionController,
    val onSelectionChanging: () -> Unit,
    val onSelection: (chapterIndex: Int, selection: ContinuousTextSelection) -> Unit,
    val onChapterVisible: (
        chapterIndex: Int,
        chapterFraction: Float,
        origin: TtsPageChangeOrigin
    ) -> Unit,
    val onRestoreComplete: () -> Unit,
    val onFirstContentDrawn: () -> Unit,
    val onSentenceDoubleTap: (chapterIndex: Int, characterOffset: Int) -> Unit,
    val ttsSentenceJumpEnabled: Boolean,
    val longPressSelectionRange: com.huangder.lumibooks.domain.model.LongPressSelectionRange,
    val annotationStyle: com.huangder.lumibooks.ui.reader.engine.ReaderAnnotationStyle,
    val chineseMode: String,
    val ttsCurrentSentence: TtsSentencePosition?,
    val comicModeEnabled: Boolean,
    val imageAdjustments: ReaderImageAdjustments,
    val onSelectionCleared: () -> Unit,
    val onViewportAnchor: (ContinuousViewportAnchor?) -> Unit,
    val onVisibleCharacters: (List<ReaderVisibleCharacters>) -> Unit,
    val bodyFontWeight: Float,
    val autoScrollRunning: Boolean,
    val autoScrollSpeed: Float,
    val onAutoScrollEnd: () -> Unit,
    val finishPage: (@Composable (visible: Boolean) -> Unit)?,
    val ttsFollowEnabled: Boolean,
    val ttsManualResumeSentence: TtsSentencePosition?,
    val ttsFollowUserPageTurns: Boolean,
    val onManualScrollStarted: () -> Unit,
    val listState: androidx.compose.foundation.lazy.LazyListState,
) {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun Render(viewport: ReaderViewport, active: Boolean, events: ReaderOrientationEvents, session: ContinuousOrientationSession) {
        val requests = remember { MutableStateFlow<ContinuousScrollRequest?>(null) }
        val privateList = rememberLazyListState(initialFirstVisibleItemIndex = currentChapter.coerceAtLeast(0))
        val privateSelection = remember { ContinuousSelectionController() }
        val anchor = session.anchor
        val sync = remember(anchor, viewport, contentRevision, fontSize, lineHeight, marginLeft, marginRight, marginTop, marginBottom) {
            anchor?.let { ContinuousScrollRequest(it.chapter, origin = TtsPageChangeOrigin.LAYOUT,
                characterOffset = it.character, lineViewportTop = it.lineViewportTop) }
        }
        val needsSync = !active || session.anchorLandscape != viewport.landscape
        val pendingExternal by scrollRequests.collectAsState()
        val external = pendingExternal?.takeIf { active && session.externalRequest != it.requestId }
        if (external != null) events.expectForeground(external.requestId)
        else if (needsSync && sync != null) events.expect(sync.requestId)
        LaunchedEffect(active, sync, needsSync, external) {
            if (external != null) requests.value = external
            else if (needsSync && sync != null) {
                if (!active) kotlinx.coroutines.delay(350)
                events.expect(sync.requestId)
                requests.value = sync
            }
        }
        ContinuousScrollReaderContent(
            onRestoreRequestComplete = { id, success ->
                if (success) {
                    events.complete(id)
                    if (events.active) scrollRequests.value?.takeIf { it.requestId == id }?.let { request ->
                        session.externalRequest = id
                        scrollRequests.compareAndSet(request, null)
                    }
                } else events.abandon(id)
            },
            chapterCount = chapterCount,
            currentChapter = if (active) currentChapter else anchor?.chapter ?: currentChapter,
            initialChapterFraction = if (active) initialChapterFraction else 0f,
            initialCharacterOffset = if (active) initialCharacterOffset else anchor?.character ?: initialCharacterOffset,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacingDp = letterSpacingDp,
            textAlignment = textAlignment,
            readerTypeface = readerTypeface,
            textColor = textColor,
            backgroundColor = backgroundColor,
            backgroundImagePath = backgroundImagePath,
            backgroundImageOpacity = backgroundImageOpacity,
            backgroundImageBlurDp = backgroundImageBlurDp,
            marginLeft = marginLeft,
            marginRight = marginRight,
            marginTop = marginTop,
            marginBottom = marginBottom,
            paragraphSpacing = paragraphSpacing,
            firstLineIndent = firstLineIndent,
            bionicReadingEnabled = bionicReadingEnabled,
            lineGuideEnabled = lineGuideEnabled,
            lineGuideDimLevel = lineGuideDimLevel,
            contentRevision = contentRevision,
            loadChapterText = loadChapterText,
            loadChapterPreviewText = loadChapterPreviewText,
            onContentSizeChanged = { p0, p1 -> events.dispatch("onContentSizeChanged", true) { onContentSizeChanged(p0, p1) } },
            notes = notes,
            searchHighlight = if (active) searchHighlight else null,
            scrollRequests = requests,
            onSearchHighlightFinished = { events.dispatch("onSearchHighlightFinished", false) { onSearchHighlightFinished() } },
            onMenuToggle = { events.dispatch("onMenuToggle", false) { onMenuToggle() } },
            onLinkClick = { p0, p1, p2, p3 -> events.dispatch("onLinkClick", false) { onLinkClick(p0, p1, p2, p3) } },
            onImageLongPress = { p0, p1 -> events.dispatch("onImageLongPress", false) { onImageLongPress(p0, p1) } },
            selectionController = if (active) selectionController else privateSelection,
            onSelectionChanging = { events.dispatch("onSelectionChanging", false) { onSelectionChanging() } },
            onSelection = { p0, p1 -> events.dispatch("onSelection", false) { onSelection(p0, p1) } },
            onChapterVisible = { p0, p1, p2 -> events.dispatch("onChapterVisible", true) { onChapterVisible(p0, p1, p2) } },
            onRestoreComplete = { events.dispatch("onRestoreComplete") { onRestoreComplete() } },
            onFirstContentDrawn = { events.dispatch("onFirstContentDrawn", true) { onFirstContentDrawn() } },
            onSentenceDoubleTap = { p0, p1 -> events.dispatch("onSentenceDoubleTap", false) { onSentenceDoubleTap(p0, p1) } },
            ttsSentenceJumpEnabled = ttsSentenceJumpEnabled,
            longPressSelectionRange = longPressSelectionRange,
            annotationStyle = annotationStyle,
            chineseMode = chineseMode,
            ttsCurrentSentence = ttsCurrentSentence,
            comicModeEnabled = comicModeEnabled,
            imageAdjustments = imageAdjustments,
            onSelectionCleared = { events.dispatch("onSelectionCleared", false) { onSelectionCleared() } },
            onViewportAnchor = { anchor -> events.dispatch("onViewportAnchor", true) { session.anchorLandscape = viewport.landscape; session.anchor = anchor; onViewportAnchor(anchor) } },
            onVisibleCharacters = { p0 -> events.dispatch("onVisibleCharacters", true) { onVisibleCharacters(p0) } },
            bodyFontWeight = bodyFontWeight,
            autoScrollRunning = active && autoScrollRunning,
            autoScrollSpeed = autoScrollSpeed,
            onAutoScrollEnd = { events.dispatch("onAutoScrollEnd", false) { onAutoScrollEnd() } },
            finishPage = if (active) finishPage else null,
            ttsFollowEnabled = active && ttsFollowEnabled,
            ttsManualResumeSentence = ttsManualResumeSentence,
            ttsFollowUserPageTurns = ttsFollowUserPageTurns,
            onManualScrollStarted = {
                requests.value?.let { request ->
                    events.abandon(request.requestId)
                    if (events.active) scrollRequests.compareAndSet(request, null)
                }
                events.dispatch("onManualScrollStarted", false) { onManualScrollStarted() }
            },
            listState = if (viewport.landscape == session.originalLandscape) listState else privateList,
        )
    }
}
