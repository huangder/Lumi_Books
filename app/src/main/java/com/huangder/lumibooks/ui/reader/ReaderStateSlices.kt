package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.parser.TocEntry
import com.huangder.lumibooks.util.parser.TxtEncoding

data class ReaderDocumentState(
    val book: Book? = null,
    val chapterCount: Int = 0,
    val chapterTitles: List<String> = emptyList(),
    val tocEntries: List<TocEntry> = emptyList(),
    val chapterHtml: String = "",
    val isLoading: Boolean = true,
    val pageReady: Boolean = false,
    val isEpubChapterHandoffInProgress: Boolean = false,
    val error: String? = null,
    val useNewEngine: Boolean = true,
    val optimizeLayout: Boolean = true,
    val useEpubCss: Boolean = false,
    val renderMode: EpubRenderMode = EpubRenderMode.READER_LAYOUT,
    val contentRevision: Long = 0L
)

data class ReaderRenderSettingsState(
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    val fontType: String = "system",
    val customFontPath: String? = null,
    val marginLeftDp: Float = 38f,
    val marginRightDp: Float = 38f,
    val marginTopDp: Float = 64f,
    val marginBottomDp: Float = 64f,
    val paragraphSpacing: Float = 2f,
    val firstLineIndent: Float = 2f,
    val bodyFontWeight: Int = 400,
    val readerTheme: String = "day",
    val readerBackgroundSelection: String = "day",
    val readerBackgroundColorSelection: String = "day",
    val readerBackgroundImageOpacity: Float = 1f,
    val readerBackgroundImageBlurDp: Float = 0f,
    val preserveEpubBackground: Boolean = true,
    val readerTextColor: Int? = null,
    val chineseMode: String = "original",
    val pageTransition: String = "slide",
    val pageAnimationSettings: ReaderPageAnimationSettings = ReaderPageAnimationSettings(),
    val bionicReadingEnabled: Boolean = false,
    val eInkModeEnabled: Boolean = false,
    val twoPageSpreadEnabled: Boolean = true,
    val readerEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    val readerWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL
)

data class ReaderPositionState(
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val globalPageIndex: Int = 0,
    val rightPageIndex: Int? = null,
    val rightChapterIndex: Int? = null,
    val pendingPageFraction: Float = 0f,
    val epubLocatorJson: String? = null
)

data class ReaderControlsState(
    val isMenuVisible: Boolean = false,
    val brightness: Float = -1f,
    val customFonts: List<CustomFontPreset> = emptyList(),
    val customReaderBackgrounds: List<ReaderBackgroundPreset> = emptyList(),
    val readerThemeSuites: List<ReaderThemeSuite> = ReaderThemeSuites.defaults(),
    val activeReaderThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    val showEpubLayoutHint: Boolean = false,
    val showMobiLayoutHint: Boolean = false,
    val txtEncoding: TxtEncoding = TxtEncoding.AUTO,
    val txtActiveCharsetName: String = "UTF-8",
    val showTxtEncodingHint: Boolean = false,
    val isTxtEncodingChanging: Boolean = false,
    val readerDisplayMode: String = "auto",
    /** PDF 阅读模式："vertical" | "vertical_paging" | "horizontal" */
    val pdfPageMode: String = "vertical",
    val showReaderChapterProgress: Boolean = true,
    val showReaderPageNumber: Boolean = true,
    val showReaderBattery: Boolean = true,
    val volumeKeyPageTurnEnabled: Boolean = false,
    val comicModeEnabled: Boolean = false,
    val screenSleepTimeoutSeconds: Int = DataStoreManager.DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS,
    val readerTopLeftContent: ReaderCornerContent =
        defaultReaderCornerContent(ReaderPageCorner.TOP_LEFT),
    val readerTopRightContent: ReaderCornerContent =
        defaultReaderCornerContent(ReaderPageCorner.TOP_RIGHT),
    val readerBottomLeftContent: ReaderCornerContent =
        defaultReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT),
    val readerBottomRightContent: ReaderCornerContent =
        defaultReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT),
    val selectionMenuItems: Map<String, Boolean> = emptyMap()
)

internal fun ReaderUiState.toDocumentState() = ReaderDocumentState(
    book = book,
    chapterCount = chapterCount,
    chapterTitles = chapterTitles,
    tocEntries = tocEntries,
    chapterHtml = chapterHtml,
    isLoading = isLoading,
    pageReady = pageReady,
    isEpubChapterHandoffInProgress = isEpubChapterHandoffInProgress,
    error = error,
    useNewEngine = useNewEngine,
    optimizeLayout = optimizeLayout,
    useEpubCss = useEpubCss,
    renderMode = renderMode,
    contentRevision = contentRevision
)

internal fun ReaderUiState.toRenderSettingsState() = ReaderRenderSettingsState(
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    textAlignment = textAlignment,
    fontType = fontType,
    customFontPath = customFontPath,
    marginLeftDp = marginLeftDp,
    marginRightDp = marginRightDp,
    marginTopDp = marginTopDp,
    marginBottomDp = marginBottomDp,
    paragraphSpacing = paragraphSpacing,
    firstLineIndent = firstLineIndent,
    bodyFontWeight = bodyFontWeight,
    readerTheme = readerTheme,
    readerBackgroundSelection = readerBackgroundSelection,
    readerBackgroundColorSelection = readerBackgroundColorSelection,
    readerBackgroundImageOpacity = readerBackgroundImageOpacity,
    readerBackgroundImageBlurDp = readerBackgroundImageBlurDp,
    preserveEpubBackground = preserveEpubBackground,
    readerTextColor = readerTextColor,
    chineseMode = chineseMode,
    pageTransition = pageTransition,
    pageAnimationSettings = pageAnimationSettings,
    bionicReadingEnabled = bionicReadingEnabled,
    eInkModeEnabled = eInkModeEnabled,
    twoPageSpreadEnabled = twoPageSpreadEnabled,
    readerEdgeTapMode = readerEdgeTapMode,
    readerWritingMode = readerWritingMode
)

internal fun ReaderUiState.toPositionState() = ReaderPositionState(
    currentChapterIndex = currentChapterIndex,
    currentPageIndex = currentPageIndex,
    totalPages = totalPages,
    globalPageIndex = globalPageIndex,
    rightPageIndex = rightPageIndex,
    rightChapterIndex = rightChapterIndex,
    pendingPageFraction = pendingPageFraction,
    epubLocatorJson = epubLocatorJson
)

internal fun ReaderUiState.toControlsState() = ReaderControlsState(
    isMenuVisible = isMenuVisible,
    brightness = brightness,
    customFonts = customFonts,
    customReaderBackgrounds = customReaderBackgrounds,
    readerThemeSuites = readerThemeSuites,
    activeReaderThemeSuiteId = activeReaderThemeSuiteId,
    showEpubLayoutHint = showEpubLayoutHint,
    showMobiLayoutHint = showMobiLayoutHint,
    txtEncoding = txtEncoding,
    txtActiveCharsetName = txtActiveCharsetName,
    showTxtEncodingHint = showTxtEncodingHint,
    isTxtEncodingChanging = isTxtEncodingChanging,
    readerDisplayMode = readerDisplayMode,
    pdfPageMode = pdfPageMode,
    showReaderChapterProgress = showReaderChapterProgress,
    showReaderPageNumber = showReaderPageNumber,
    showReaderBattery = showReaderBattery,
    volumeKeyPageTurnEnabled = volumeKeyPageTurnEnabled,
    comicModeEnabled = comicModeEnabled,
    screenSleepTimeoutSeconds = screenSleepTimeoutSeconds,
    readerTopLeftContent = readerTopLeftContent,
    readerTopRightContent = readerTopRightContent,
    readerBottomLeftContent = readerBottomLeftContent,
    readerBottomRightContent = readerBottomRightContent,
    selectionMenuItems = selectionMenuItems
)
