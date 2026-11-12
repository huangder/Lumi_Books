package com.huangder.lumibooks.ui.reader

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.util.markdown.native.MarkdownBlockSource
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MarkdownNativeReader(
    source: MarkdownBlockSource,
    state: ReaderUiState,
    typeface: Typeface,
    color: Int,
    paperColor: Int,
    dark: Boolean,
    continuous: Boolean,
    navigation: EpubNavigationRequest?,
    notes: List<Note>,
    ttsSentence: TtsSentencePosition?,
    selectionClearToken: Int,
    onPage: (Int, Int, String) -> Unit,
    onPageIndex: (List<Int>) -> Unit = {},
    onManualNavigation: () -> Unit = {},
    onManualScrollSettled: () -> Unit = {},
    onVisibleCharacters: (List<ReaderVisibleCharacters>) -> Unit = {},
    ttsFollowEnabled: Boolean = false,
    onSelection: (EpubSelectionInfo) -> Unit,
    onLink: (String) -> Unit,
    onMenu: () -> Unit,
    onSelectionCleared: () -> Unit = {},
    onSelectionDragging: (Boolean) -> Unit = {},
    onNavigation: (EpubNavigationResult) -> Unit,
    onTurnReady: (((Int) -> Unit)?) -> Unit,
    onTextReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    onPaginationReady: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val args = MarkdownNativeReaderOrientationArguments(
        source = source,
        state = state,
        typeface = typeface,
        color = color,
        paperColor = paperColor,
        dark = dark,
        continuous = continuous,
        navigation = navigation,
        notes = notes,
        ttsSentence = ttsSentence,
        selectionClearToken = selectionClearToken,
        onPage = onPage,
        onPageIndex = onPageIndex,
        onManualNavigation = onManualNavigation,
        onManualScrollSettled = onManualScrollSettled,
        onVisibleCharacters = onVisibleCharacters,
        ttsFollowEnabled = ttsFollowEnabled,
        onSelection = onSelection,
        onLink = onLink,
        onMenu = onMenu,
        onSelectionCleared = onSelectionCleared,
        onSelectionDragging = onSelectionDragging,
        onNavigation = onNavigation,
        onTurnReady = onTurnReady,
        onTextReady = onTextReady,
        onPaginationReady = onPaginationReady,
        onError = onError,
        modifier = modifier,
    )
    var anchor by remember(source) { mutableStateOf(state.epubLocatorJson) }
    ReaderOrientationContent(source, modifier) { viewport, active, events ->
        args.Render(viewport, active, events, anchor, { anchor = it })
    }
}

private class MarkdownNativeReaderOrientationArguments(
    val source: MarkdownBlockSource,
    val state: ReaderUiState,
    val typeface: Typeface,
    val color: Int,
    val paperColor: Int,
    val dark: Boolean,
    val continuous: Boolean,
    val navigation: EpubNavigationRequest?,
    val notes: List<Note>,
    val ttsSentence: TtsSentencePosition?,
    val selectionClearToken: Int,
    val onPage: (Int, Int, String) -> Unit,
    val onPageIndex: (List<Int>) -> Unit,
    val onManualNavigation: () -> Unit,
    val onManualScrollSettled: () -> Unit,
    val onVisibleCharacters: (List<ReaderVisibleCharacters>) -> Unit,
    val ttsFollowEnabled: Boolean,
    val onSelection: (EpubSelectionInfo) -> Unit,
    val onLink: (String) -> Unit,
    val onMenu: () -> Unit,
    val onSelectionCleared: () -> Unit,
    val onSelectionDragging: (Boolean) -> Unit,
    val onNavigation: (EpubNavigationResult) -> Unit,
    val onTurnReady: (((Int) -> Unit)?) -> Unit,
    val onTextReady: ((suspend (Int, Int) -> EpubPageText?)?) -> Unit,
    val onPaginationReady: (Boolean) -> Unit,
    val onError: (String) -> Unit,
    val modifier: Modifier,
) {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun Render(viewport: ReaderViewport, active: Boolean, events: ReaderOrientationEvents, anchor: String?, updateAnchor: (String) -> Unit) {
        LaunchedEffect(active) { if (active) events.retryOnActivation() }
        val hiddenNavigation = remember(anchor, viewport, events.recoveryGeneration, state.paginationContentKey(), typeface, state.bodyFontWeight, state.lineHeight, state.letterSpacing,
            state.marginLeftDp, state.marginRightDp, state.marginTopDp, state.marginBottomDp, continuous) { anchor?.let { json ->
            EpubNavigationRequest(-System.nanoTime().coerceAtLeast(1), EpubNavigationOrigin.RETURN_TO_SOURCE,
                0, 0, 0, EpubNavigationDestination.Locator(json), false)
        } }
        if (!events.preparationFailed && (!active || events.viewportChanged) && hiddenNavigation != null) events.expect(hiddenNavigation.operationId)
        if (active && navigation != null) events.expectForeground(navigation.operationId)
        MarkdownNativeReaderContent(
            source = source,
            state = state,
            typeface = typeface,
            color = color,
            paperColor = paperColor,
            dark = dark,
            continuous = continuous,
            navigation = if (active && navigation != null) navigation else if ((!active || events.preparationPending) && !events.preparationFailed) hiddenNavigation else null,
            notes = notes,
            ttsSentence = ttsSentence,
            selectionClearToken = selectionClearToken,
            onPage = { page, count, locator -> events.dispatch("onPage", true) { updateAnchor(locator); onPage(page, count, locator) } },
            onPageIndex = { p0 -> events.dispatch("onPageIndex", true) { onPageIndex(p0) } },
            onManualNavigation = { events.dispatch("onManualNavigation", false) { onManualNavigation() } },
            onManualScrollSettled = { events.dispatch("onManualScrollSettled", false) { onManualScrollSettled() } },
            onVisibleCharacters = { p0 -> events.dispatch("onVisibleCharacters", false) { onVisibleCharacters(p0) } },
            ttsFollowEnabled = active && ttsFollowEnabled,
            onSelection = { p0 -> events.dispatch("onSelection", false) { onSelection(p0) } },
            onLink = { p0 -> events.dispatch("onLink", false) { onLink(p0) } },
            onMenu = { events.dispatch("onMenu", false) { onMenu() } },
            onSelectionCleared = { events.dispatch("onSelectionCleared", false) { onSelectionCleared() } },
            onSelectionDragging = { p0 -> events.dispatch("onSelectionDragging", false) { onSelectionDragging(p0) } },
            onNavigation = { result -> if (result is EpubNavigationResult.Committed) events.complete(result.operationId) else events.fail(result.operationId); if (result.operationId >= 0) events.dispatch("onNavigation") { onNavigation(result) } },
            onTurnReady = { p0 -> events.dispatch("onTurnReady", true) { onTurnReady(p0) } },
            onTextReady = { p0 -> events.dispatch("onTextReady", true) { onTextReady(p0) } },
            onPaginationReady = { p0 -> events.dispatch("onPaginationReady", true) { onPaginationReady(p0) } },
            onError = { p0 -> events.dispatch("onError", false) { onError(p0) } },
            backgroundPreparationEnabled = active,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
