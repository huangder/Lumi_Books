package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderFirstOpenHintPolicy
import com.huangder.lumibooks.data.local.ReaderPreferencesSnapshot
import com.huangder.lumibooks.domain.model.AnnotationEditPlan
import com.huangder.lumibooks.domain.model.AnnotationNoteEditPlanner
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderCornerMargins
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.ReaderPageAnimationSettings
import com.huangder.lumibooks.domain.model.ReaderPageTransition
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.PdfPageMode
import com.huangder.lumibooks.domain.model.normalizeReaderThemeSuiteName
import com.huangder.lumibooks.domain.model.readerThemeSuiteNameCodePointCount
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.pdfconversion.PdfConversionManager
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.pdfconversion.PdfConversionEngine
import com.huangder.lumibooks.mineru.MineruApiException
import com.huangder.lumibooks.mineru.MineruConfig
import com.huangder.lumibooks.pdfconversion.PdfTextExtractor
import com.huangder.lumibooks.mineru.MineruManualImportManager
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.pdfconversion.PdfConversionState
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.ReaderBackgroundImageProcessor
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.performance.ReaderOpenPerformance
import com.huangder.lumibooks.util.performance.ReaderOpenStage
import com.huangder.lumibooks.util.parser.BookParser
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.parser.BookLinkTarget
import com.huangder.lumibooks.util.parser.CbzParser
import com.huangder.lumibooks.util.parser.PdfParser
import com.huangder.lumibooks.util.parser.TxtEncoding
import com.huangder.lumibooks.util.parser.TxtIndexCoordinator
import com.huangder.lumibooks.util.parser.TxtParser
import com.huangder.lumibooks.util.parser.TxtIndexState
import com.huangder.lumibooks.util.parser.TxtOpenResult
import com.huangder.lumibooks.util.parser.TxtTocRule
import com.huangder.lumibooks.util.parser.TxtTocRuleDiagnostics
import com.huangder.lumibooks.util.parser.TxtReplaceText
import com.huangder.lumibooks.util.parser.TxtReplaceRange
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.util.epub.BookRenderSession
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.EpubLocator
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.ui.reader.engine.applyReaderPunctuationCompression
import com.huangder.lumibooks.R
import com.huangder.lumibooks.service.TtsForegroundService
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPageSource
import com.huangder.lumibooks.tts.TtsPageTurnRequest
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsProsodyMode
import com.huangder.lumibooks.tts.ExternalTtsException
import com.huangder.lumibooks.tts.SystemTtsException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.huangder.lumibooks.domain.model.bookmarkPositionForCharacterOffset
import kotlin.math.roundToInt

internal fun shouldStyleTxtChapterTitle(firstLine: String, chapterTitle: String): Boolean {
    val normalizedFirstLine = firstLine.trim()
    val normalizedChapterTitle = chapterTitle.trim()
    return normalizedFirstLine.isNotEmpty() &&
        normalizedFirstLine.length <= 80 &&
        normalizedFirstLine == normalizedChapterTitle
}

/** Keeps TXT chapter headings a few steps above the configured body size. */
internal fun txtChapterTitleFontSize(bodyFontSize: Float): Int {
    val normalizedBodyFontSize = bodyFontSize.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    return (normalizedBodyFontSize + 6f).roundToInt().coerceAtLeast(1)
}

internal fun mapReaderTxtOffsetToSource(
    sourceText: CharSequence,
    readerText: CharSequence,
    readerOffset: Int
): Int {
    if (sourceText.isEmpty() || readerText.isEmpty()) return 0
    var target = readerOffset.coerceIn(0, readerText.length)
    while (target < readerText.length && readerText[target].isWhitespace()) target++
    if (target >= readerText.length) return sourceText.length

    var targetNonWhitespaceOrdinal = 0
    for (index in 0 until target) {
        if (!readerText[index].isWhitespace()) targetNonWhitespaceOrdinal++
    }

    var sourceNonWhitespaceOrdinal = 0
    var lastContentOffset = 0
    sourceText.forEachIndexed { index, character ->
        if (!character.isWhitespace()) {
            lastContentOffset = index
            if (sourceNonWhitespaceOrdinal == targetNonWhitespaceOrdinal) return index
            sourceNonWhitespaceOrdinal++
        }
    }
    return lastContentOffset.coerceIn(0, sourceText.length)
}

internal data class TxtSourceRange(
    val start: Int,
    val endExclusive: Int
)

/**
 * Maps a non-empty reader selection back to its range in the original TXT chapter.
 *
 * Reader paragraph formatting can remove leading indentation and insert visual blank
 * lines. Mapping both bounds with [mapReaderTxtOffsetToSource] would make an end bound
 * at a visual newline consume the next paragraph, so the end is based on the final
 * selected non-whitespace character instead.
 */
internal fun mapReaderTxtRangeToSource(
    sourceText: CharSequence,
    readerText: CharSequence,
    readerStart: Int,
    readerEndExclusive: Int
): TxtSourceRange? {
    if (sourceText.isEmpty() || readerText.isEmpty()) return null
    val start = readerStart.coerceIn(0, readerText.length)
    val endExclusive = readerEndExclusive.coerceIn(start, readerText.length)
    if (start >= endExclusive) return null

    val firstContent = (start until endExclusive).firstOrNull { !readerText[it].isWhitespace() }
        ?: return null
    val lastContent = (endExclusive - 1 downTo start)
        .firstOrNull { !readerText[it].isWhitespace() }
        ?: return null

    fun sourceOffsetForContentOrdinal(contentOrdinal: Int): Int? {
        var ordinal = 0
        sourceText.forEachIndexed { index, character ->
            if (!character.isWhitespace()) {
                if (ordinal == contentOrdinal) return index
                ordinal++
            }
        }
        return null
    }

    fun contentOrdinalAt(readerIndex: Int): Int {
        var ordinal = 0
        for (index in 0 until readerIndex) {
            if (!readerText[index].isWhitespace()) ordinal++
        }
        return ordinal
    }

    val sourceStart = sourceOffsetForContentOrdinal(contentOrdinalAt(firstContent)) ?: return null
    val sourceLast = sourceOffsetForContentOrdinal(contentOrdinalAt(lastContent)) ?: return null
    return TxtSourceRange(sourceStart, sourceLast + 1)
}

data class ReaderUiState(
    val book: Book? = null,
    val chapterCount: Int = 0,
    val chapterTitles: List<String> = emptyList(),
    val tocEntries: List<com.huangder.lumibooks.util.parser.TocEntry> = emptyList(),
    /** CBZ 漫画的"话"分组（按子目录划分）；其他格式为空。 */
    val comicChapterEntries: List<com.huangder.lumibooks.util.parser.TocEntry> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val chapterHtml: String = "",
    val isMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val pageReady: Boolean = false,
    val isEpubChapterHandoffInProgress: Boolean = false,
    val pendingPageFraction: Float = 0f,
    val pendingPageFractionSemantics: ReaderPageFractionSemantics = ReaderPageFractionSemantics.START,
    val pendingReaderPosition: ReaderPositionLocator? = null,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    val fontType: String = "system",
    val marginLeftDp: Float = 38f,
    val marginRightDp: Float = 38f,
    val marginTopDp: Float = 64f,
    val marginBottomDp: Float = 64f,
    val readerTheme: String = "day",
    /** 亮度 0f~1f，-1f 跟随系统 */
    val brightness: Float = -1f,
    /** 自定义导入字体文件路径（当前选中的字体） */
    val customFontPath: String? = null,
    /** 所有已导入的自定义字体列表 */
    val customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset> = emptyList(),
    /** 正在下载的远程字体 key（非空时字体按钮显示下载中） */
    val fontDownloadKey: String? = null,
    /** 上次远程字体下载失败（按钮显示失败文案，点击重试） */
    val fontDownloadFailed: Boolean = false,
    val readerBackgroundSelection: String = "day",
    val readerBackgroundColorSelection: String = "day",
    val readerBackgroundImageOpacity: Float = 1f,
    val readerBackgroundImageBlurDp: Float = 0f,
    val customReaderBackgrounds: List<ReaderBackgroundPreset> = emptyList(),
    val preserveEpubBackground: Boolean = true,
    /** 书籍原排版下整页图片按屏幕比例裁切铺满（默认等比留白）。 */
    val pageImageCrop: Boolean = false,
    val readerTextColor: Int? = null,
    val readerThemeSuites: List<ReaderThemeSuite> = ReaderThemeSuites.defaults(),
    val activeReaderThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    val globalActiveReaderThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    /**
     * 两套排版各自的活动套装配额（阅读器排版 / 书籍原排版，后者默认「原排版」）；
     * [activeReaderThemeSuiteId] 始终是"当前模式正在用"的那一个，便于 UI 与既有逻辑直接读取。
     */
    val activeReaderLayoutThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    val activeBookLayoutThemeSuiteId: String = ReaderThemeSuites.PUBLISHER_ID,
    val globalActiveBookLayoutThemeSuiteId: String = ReaderThemeSuites.PUBLISHER_ID,
    val readerThemeSuiteBookScoped: Boolean = false,
    val error: String? = null,
    /** 全局页码（跨所有章节），新引擎用 */
    val globalPageIndex: Int = 0,
    /** 是否使用新 Canvas 引擎 */
    val useNewEngine: Boolean = true,
    /** 是否使用优化排版（per-book） */
    val optimizeLayout: Boolean = true,
    /** 是否加载 EPUB 自带 CSS 样式（per-book） */
    val useEpubCss: Boolean = false,
    val renderMode: EpubRenderMode = EpubRenderMode.READER_LAYOUT,
    val showEpubLayoutHint: Boolean = false,
    val showMobiLayoutHint: Boolean = false,
    val txtEncoding: TxtEncoding = TxtEncoding.AUTO,
    val txtTocRuleId: String = "auto",
    val txtTocRuleName: String? = null,
    val txtTocCustomRules: List<TxtTocRule> = emptyList(),
    val txtTocDiagnostics: List<TxtTocRuleDiagnostics> = emptyList(),
    val isTxtTocChanging: Boolean = false,
    val txtActiveCharsetName: String = "UTF-8",
    val txtIndexState: TxtIndexState = TxtIndexState.COMPLETE,
    val txtIndexProgress: Float? = null,
    val showTxtEncodingHint: Boolean = false,
    val isTxtEncodingChanging: Boolean = false,
    val epubLocatorJson: String? = null,
    /** 简繁转换模式："original" | "simplified" | "traditional" */
    val chineseMode: String = "original",
    /** 翻页效果："slide" | "scroll" | "fade" */
    val pageTransition: String = "slide",
    val pageAnimationSettings: ReaderPageAnimationSettings = ReaderPageAnimationSettings(),
    /** 阅读页显示效果："auto" | "day" | "night" */
    val readerDisplayMode: String = "auto",
    /** 段间距（dp），默认 8 */
    val paragraphSpacing: Float = 2f,
    /** 首行缩进字符数，默认 2 */
    val firstLineIndent: Float = 2f,
    /** PDF 阅读模式："vertical" | "vertical_paging" | "horizontal" */
    val pdfPageMode: String = "vertical",
    /** CBZ 漫画翻页方向："ltr" | "rtl"，仅影响横向翻页。 */
    val cbzReadingDirection: String = "ltr",
    val showReaderChapterProgress: Boolean = true,
    val showReaderPageNumber: Boolean = true,
    val showReaderBattery: Boolean = true,
    val volumeKeyPageTurnEnabled: Boolean = false,
    val bionicReadingEnabled: Boolean = false,
    val comicModeEnabled: Boolean = false,
    /** 正文字重（PR #19 #24）：>=600 视为加粗 */
    val bodyFontWeight: Int = 400,
    val eInkModeEnabled: Boolean = false,
    val twoPageSpreadEnabled: Boolean = true,
    /** 双页对开模式当前跨页的右半页（无右页时为 null） */
    val rightPageIndex: Int? = null,
    /** 右半页所属章节；跨章对开时与 currentChapterIndex 不同。 */
    val rightChapterIndex: Int? = null,
    val screenSleepTimeoutSeconds: Int = DataStoreManager.DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS,
    val readerEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    val readerWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
    val readerTopLeftContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.TOP_LEFT),
    val readerTopRightContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.TOP_RIGHT),
    val readerBottomLeftContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT),
    val readerBottomRightContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT),
    /** 四角信息区（页眉/页脚）边距；空字段表示跟随正文 / 沿用旧版默认位置。 */
    val readerCornerMargins: ReaderCornerMargins = ReaderCornerMargins(),
    val contentRevision: Long = 0L,
    val selectionMenuItems: Map<String, Boolean> = emptyMap()
)

data class ReaderTtsState(
    val playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val speechRate: Float = 1f,
    val speechRateMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE,
    val pitch: Float = 1f,
    val pitchMode: TtsProsodyMode = TtsProsodyMode.FOLLOW_ENGINE,
    val usesAndroidTts: Boolean = true,
    val activeBookId: String? = null,
    val errorMessage: String? = null,
    val sleepTimerRemainingMs: Long? = null
)

data class TtsSentencePosition(
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int
)

internal fun ReaderUiState.withReaderCornerContent(
    corner: ReaderPageCorner,
    content: ReaderCornerContent
): ReaderUiState {
    var updated = this
    if (content != ReaderCornerContent.NONE) {
        if (corner != ReaderPageCorner.TOP_LEFT && updated.readerTopLeftContent == content) {
            updated = updated.copy(readerTopLeftContent = ReaderCornerContent.NONE)
        }
        if (corner != ReaderPageCorner.TOP_RIGHT && updated.readerTopRightContent == content) {
            updated = updated.copy(readerTopRightContent = ReaderCornerContent.NONE)
        }
        if (corner != ReaderPageCorner.BOTTOM_LEFT && updated.readerBottomLeftContent == content) {
            updated = updated.copy(readerBottomLeftContent = ReaderCornerContent.NONE)
        }
        if (corner != ReaderPageCorner.BOTTOM_RIGHT && updated.readerBottomRightContent == content) {
            updated = updated.copy(readerBottomRightContent = ReaderCornerContent.NONE)
        }
    }
    return when (corner) {
        ReaderPageCorner.TOP_LEFT -> updated.copy(readerTopLeftContent = content)
        ReaderPageCorner.TOP_RIGHT -> updated.copy(readerTopRightContent = content)
        ReaderPageCorner.BOTTOM_LEFT -> updated.copy(readerBottomLeftContent = content)
        ReaderPageCorner.BOTTOM_RIGHT -> updated.copy(readerBottomRightContent = content)
    }
}

/**
 * A valid first page is enough to release TXT's transition overlay. Exact reader
 * position restoration is allowed to finish after the visible page is available.
 */
internal fun shouldReleasePagedReaderOnFirstPage(
    isTxtBook: Boolean,
    isContinuous: Boolean,
    chapterTotalPages: Int,
    hasPendingReaderPosition: Boolean,
    reachedPendingPosition: Boolean
): Boolean {
    if (isContinuous || chapterTotalPages <= 0) return false
    return !hasPendingReaderPosition || reachedPendingPosition || isTxtBook
}

/**
 * Large TXT files can use byte-sized virtual chapters only when there is no saved semantic
 * position to restore. Virtual chapter indexes are not stable enough to persist as a locator.
 */
internal fun shouldUseFastTxtOpen(
    fileSizeBytes: Long,
    largeFileThresholdBytes: Long,
    hasPersistedReaderPosition: Boolean,
    readingProgress: Float
): Boolean = fileSizeBytes >= largeFileThresholdBytes &&
    !hasPersistedReaderPosition &&
    readingProgress <= 0f

/** TXT virtual chapters are safe to persist only when the locator also carries a byte anchor. */
internal fun shouldPersistReaderLocator(
    formatName: String?,
    txtIndexState: TxtIndexState,
    sourceByteOffset: Long?
): Boolean = formatName != "TXT" ||
    txtIndexState == TxtIndexState.COMPLETE ||
    sourceByteOffset != null

internal fun txtChapterFractionForByte(
    chapterRange: Pair<Long, Long>?,
    sourceByteOffset: Long,
    fallback: Float = 0f
): Float {
    val (start, end) = chapterRange ?: return fallback.coerceIn(0f, 0.9999f)
    val length = end - start
    if (length <= 0L) return fallback.coerceIn(0f, 0.9999f)
    return ((sourceByteOffset.coerceIn(start, end) - start).toDouble() / length.toDouble())
        .toFloat()
        .coerceIn(0f, 0.9999f)
}

internal fun continuousTtsPageFraction(pageIndex: Int, totalPages: Int): Float? {
    if (totalPages <= 0 || pageIndex !in 0 until totalPages) return null
    return ((pageIndex + 0.5f) / totalPages.toFloat()).coerceIn(0f, 0.9999f)
}

/**
 * 连续滚动模式下的听书起点：用章节内滚动比例估算章节字符偏移。
 * 该屏幕不经过分页引擎，页数索引不可用，只能按文本长度近似。
 */
internal fun continuousStartCharacterOffset(chapterFraction: Float, chapterLength: Int): Int? {
    if (chapterLength <= 0) return null
    return (chapterFraction.coerceIn(0f, 0.9999f) * chapterLength)
        .toInt()
        .coerceIn(0, chapterLength - 1)
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val ttsController: TtsController,
    private val pdfConversionManager: PdfConversionManager,
    private val pdfTextExtractor: PdfTextExtractor,
    private val mineruManualImportManager: MineruManualImportManager,
    private val mineruTokenStore: MineruTokenStore,
    private val webdavSyncManager: com.huangder.lumibooks.data.sync.WebdavSyncManager,
    private val webdavAutoSyncScheduler: com.huangder.lumibooks.data.sync.WebdavAutoSyncScheduler,
    private val fontDownloadManager: com.huangder.lumibooks.util.FontDownloadManager
) : ViewModel() {

    private companion object {
        const val CONTINUOUS_PROGRESS_SCALE = 10_000
        const val PROGRESS_SAVE_DEBOUNCE_MS = 250L
    }

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    /**
     * PageLayoutEngine 由 ViewModel 持有，跨 ReadView 实例存活。
     * ReadView 退出后 layoutCache 保留，重新进入时直接命中缓存，消除首屏等待。
     */
    val pageLayoutEngine = com.huangder.lumibooks.ui.reader.engine.PageLayoutEngine()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _ttsState = MutableStateFlow(ReaderTtsState())
    val ttsState: StateFlow<ReaderTtsState> = _ttsState.asStateFlow()

    val documentState: StateFlow<ReaderDocumentState> = _uiState
        .map(ReaderUiState::toDocumentState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderDocumentState())
    val renderSettingsState: StateFlow<ReaderRenderSettingsState> = _uiState
        .map(ReaderUiState::toRenderSettingsState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderRenderSettingsState())
    val positionState: StateFlow<ReaderPositionState> = _uiState
        .map(ReaderUiState::toPositionState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPositionState())
    val controlsState: StateFlow<ReaderControlsState> = _uiState
        .map(ReaderUiState::toControlsState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderControlsState())

    private val _ttsSentencePosition = MutableStateFlow<TtsSentencePosition?>(null)
    val ttsSentencePosition: StateFlow<TtsSentencePosition?> = _ttsSentencePosition.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _readerNotes = MutableStateFlow<List<Note>>(emptyList())
    val readerNotes: StateFlow<List<Note>> = _readerNotes.asStateFlow()

    private val _pdfConversionState = MutableStateFlow<PdfConversionState>(PdfConversionState.Idle)
    val pdfConversionState: StateFlow<PdfConversionState> = _pdfConversionState.asStateFlow()
    private val _mineruMode = MutableStateFlow(MineruMode.DISABLED)
    val mineruMode: StateFlow<MineruMode> = _mineruMode.asStateFlow()
    private var manualImportJob: Job? = null
    private var continuousProgressJob: Job? = null
    private var readerNotesJob: Job? = null
    private var processedBackgroundJob: Job? = null
    private val progressWriteMutex = Mutex()
    private var progressWriteVersion = 0L

    val ttsPageTurnRequests = ttsController.pageTurnRequests

    fun isTtsPageTurnRequestActive(request: TtsPageTurnRequest): Boolean =
        ttsController.isPageTurnRequestActive(request)

    fun acknowledgeTtsPageTurnRequest(request: TtsPageTurnRequest) {
        ttsController.acknowledgePageTurnRequest(request)
    }

    private fun notifyTtsPageVisible(chapterIndex: Int, pageIndex: Int) {
        notifyTtsPageVisible(
            chapterIndex,
            pageIndex,
            ttsController.pageChangeOriginFor(bookId, chapterIndex, pageIndex)
        )
    }

    private fun notifyTtsPageVisible(
        chapterIndex: Int,
        pageIndex: Int,
        origin: com.huangder.lumibooks.tts.TtsPageChangeOrigin
    ) {
        ttsController.onPageVisible(
            bookId = bookId,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            origin = origin
        )
    }

    private var parser: BookParser? = null
    private var largeTxtIndexJob: Job? = null
    @Volatile
    private var largeTxtFastRanges: List<Pair<Long, Long>?> = emptyList()
    private val firstChapterDecodeTraced = AtomicBoolean(false)
    private var renderSession: BookRenderSession? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var pausedTime: Long = 0L  // 进入后台的时间戳
    private var isPaused: Boolean = false

    /** 应用进入后台：暂停计时 */
    fun onAppBackgrounded() {
        if (!isPaused) {
            pausedTime = System.currentTimeMillis()
            isPaused = true
            // 保存当前会话，防止进程被杀丢失数据
            saveProgress()
            saveReadingSession()
            android.util.Log.e("READING", "App backgrounded, session saved")
        }
    }

    /** 应用回到前台：恢复计时 */
    fun onAppForegrounded() {
        if (isPaused) {
            // 重置会话起始时间，不计入后台时间
            sessionStartTime = System.currentTimeMillis()
            isPaused = false
            pausedTime = 0L
            android.util.Log.e("READING", "App foregrounded, timer reset")
        }
    }

    init {
        viewModelScope.launch {
            coroutineScope {
                val bookDeferred = async {
                    ReaderOpenPerformance.traceStageSuspend(bookId, ReaderOpenStage.BOOK_RECORD) {
                        bookRepository.getBookById(bookId)
                    }
                }
                val preferencesDeferred = async {
                    ReaderOpenPerformance.traceStageSuspend(bookId, ReaderOpenStage.PREFERENCES) {
                        dataStoreManager.ensureReaderMigrations()
                        dataStoreManager.readerPreferences(bookId).first()
                    }
                }
                val preferences = preferencesDeferred.await()
                applyReaderPreferences(preferences)
                observeReaderSettings()
                loadBook(preferences, bookDeferred.await())
            }
        }
        viewModelScope.launch {
            ttsController.playbackState.collectLatest { state ->
                _ttsState.value = _ttsState.value.copy(playbackState = state)
            }
        }
        viewModelScope.launch {
            ttsController.speechRate.collectLatest { rate ->
                _ttsState.value = _ttsState.value.copy(speechRate = rate)
            }
        }
        viewModelScope.launch {
            ttsController.speechRateMode.collectLatest { mode ->
                _ttsState.value = _ttsState.value.copy(speechRateMode = mode)
            }
        }
        viewModelScope.launch {
            ttsController.pitch.collectLatest { pitch ->
                _ttsState.value = _ttsState.value.copy(pitch = pitch)
            }
        }
        viewModelScope.launch {
            ttsController.pitchMode.collectLatest { mode ->
                _ttsState.value = _ttsState.value.copy(pitchMode = mode)
            }
        }
        viewModelScope.launch {
            ttsController.usesAndroidTts.collectLatest { usesAndroidTts ->
                _ttsState.value = _ttsState.value.copy(usesAndroidTts = usesAndroidTts)
            }
        }
        viewModelScope.launch {
            ttsController.activeBookId.collectLatest { activeBookId ->
                _ttsState.value = _ttsState.value.copy(activeBookId = activeBookId)
            }
        }
        viewModelScope.launch {
            ttsController.errors.collectLatest { error ->
                _ttsState.value = _ttsState.value.copy(errorMessage = ttsErrorMessage(error))
            }
        }
        viewModelScope.launch {
            ttsController.sleepTimerRemainingMs.collectLatest { remaining ->
                _ttsState.value = _ttsState.value.copy(sleepTimerRemainingMs = remaining)
            }
        }
        viewModelScope.launch {
            // 收集 TTS 当前朗读句子的字符偏移，用于 UI 淡高亮
            ttsController.currentSentence.collectLatest { segment ->
                val page = ttsController.currentPage.value
                if (segment != null && page != null) {
                    _ttsSentencePosition.value = TtsSentencePosition(
                            chapterIndex = page.location.chapterIndex,
                            startOffset = segment.startCharacterOffset,
                            endOffset = segment.endCharacterOffset
                        )
                } else {
                    _ttsSentencePosition.value = null
                }
            }
        }
        viewModelScope.launch {
            pdfConversionManager.observe(bookId).collectLatest { state ->
                _pdfConversionState.value = state
            }
        }
        viewModelScope.launch {
            combine(
                dataStoreManager.mineruMode,
                dataStoreManager.mineruConsentVersion
            ) { mode, consentVersion ->
                mode to consentVersion
            }.collectLatest { (modeKey, consentVersion) ->
                val mode = MineruMode.fromKey(modeKey)
                _mineruMode.value = withContext(Dispatchers.IO) {
                    when {
                        consentVersion < MineruConfig.CONSENT_VERSION -> MineruMode.DISABLED
                        mode == MineruMode.PRECISE && !mineruTokenStore.hasToken() -> MineruMode.DISABLED
                        else -> mode
                    }
                }
            }
        }
    }

    suspend fun findConvertedPdfBookId(): String? {
        return pdfConversionManager.findConvertedBookId(bookId)
    }

    fun startPdfConversion(
        replaceExisting: Boolean,
        engine: PdfConversionEngine = PdfConversionEngine.LOCAL,
        mineruMode: MineruMode = MineruMode.DISABLED
    ) {
        _pdfConversionState.value = PdfConversionState.Running(0, 0, 0)
        pdfConversionManager.enqueue(bookId, replaceExisting, engine, mineruMode.key)
    }

    fun cancelPdfConversion() {
        if (manualImportJob?.isActive == true) {
            manualImportJob?.cancel()
            return
        }
        pdfConversionManager.cancel(bookId)
    }

    fun importManualMineruResult(uri: Uri, replaceExisting: Boolean) {
        if (manualImportJob?.isActive == true) return
        _pdfConversionState.value = PdfConversionState.Running(
            currentPage = 0,
            totalPages = 0,
            progress = 0,
            manualImport = true
        )
        manualImportJob = viewModelScope.launch {
            try {
                val result = mineruManualImportManager.importForPdf(
                    uri = uri,
                    sourceBookId = bookId,
                    replaceExisting = replaceExisting
                )
                _pdfConversionState.value = PdfConversionState.Succeeded(
                    bookId = result.bookId,
                    textPages = result.chapterCount,
                    totalPages = result.chapterCount,
                    manualImport = true
                )
            } catch (_: CancellationException) {
                _pdfConversionState.value = PdfConversionState.Cancelled
            } catch (error: MineruApiException) {
                _pdfConversionState.value = PdfConversionState.Failed(
                    when (error.kind) {
                        MineruApiException.Kind.FILE_LIMIT -> PdfConversionContract.ERROR_MINERU_MANUAL_TOO_LARGE
                        MineruApiException.Kind.INVALID_RESULT -> PdfConversionContract.ERROR_MINERU_MANUAL_FORMAT
                        else -> PdfConversionContract.ERROR_MINERU_MANUAL_IMPORT
                    }
                )
            } catch (_: FileNotFoundException) {
                _pdfConversionState.value = PdfConversionState.Failed(PdfConversionContract.ERROR_FILE_MISSING)
            } catch (_: IOException) {
                _pdfConversionState.value = PdfConversionState.Failed(PdfConversionContract.ERROR_STORAGE)
            } catch (_: Throwable) {
                _pdfConversionState.value = PdfConversionState.Failed(PdfConversionContract.ERROR_MINERU_MANUAL_IMPORT)
            } finally {
                manualImportJob = null
            }
        }
    }

    fun consumePdfConversionResult() {
        pdfConversionManager.dismissResultNotification(bookId)
    }

    private fun observeReaderSettings() {
        viewModelScope.launch {
            dataStoreManager.readerPreferences(bookId)
                .collectLatest(::applyReaderPreferences)
        }
    }

    private fun applyReaderPreferences(preferences: ReaderPreferencesSnapshot) {
        val suiteState = preferences.readerThemeSuiteState
        val scopedReaderSuiteId = if (preferences.readerThemeSuiteBookScoped) {
            preferences.readerThemeSuiteBookActiveId
                ?.takeIf { id -> suiteState.suites.any { it.id == id } }
                ?: suiteState.activeSuiteId
        } else {
            suiteState.activeSuiteId
        }
        val scopedBookLayoutSuiteId = if (preferences.readerThemeSuiteBookScoped) {
            preferences.readerThemeSuiteBookActiveBookLayoutId
                ?.takeIf { id -> suiteState.suites.any { it.id == id } }
                ?: suiteState.activeBookLayoutSuiteId
        } else {
            suiteState.activeBookLayoutSuiteId
        }
        val layoutTarget = readerLayoutTargetFor(
            format = _uiState.value.book?.format?.name,
            renderMode = preferences.renderMode
        )
        val effectiveSuiteId = ReaderThemeSuites.resolveActiveId(
            suites = suiteState.suites,
            requestedId = when (layoutTarget) {
                ReaderLayoutTarget.READER_LAYOUT -> scopedReaderSuiteId
                ReaderLayoutTarget.BOOK_LAYOUT -> scopedBookLayoutSuiteId
            },
            layout = layoutTarget
        )
        if (preferences.readerThemeSuiteBookScoped) {
            if (preferences.readerThemeSuiteBookActiveId != scopedReaderSuiteId) {
                viewModelScope.launch {
                    dataStoreManager.saveReaderThemeSuiteBookActiveId(
                        bookId,
                        scopedReaderSuiteId,
                        ReaderLayoutTarget.READER_LAYOUT
                    )
                }
            }
            if (preferences.readerThemeSuiteBookActiveBookLayoutId != scopedBookLayoutSuiteId) {
                viewModelScope.launch {
                    dataStoreManager.saveReaderThemeSuiteBookActiveId(
                        bookId,
                        scopedBookLayoutSuiteId,
                        ReaderLayoutTarget.BOOK_LAYOUT
                    )
                }
            }
        }
        val effectiveSuite = suiteState.suites.firstOrNull { it.id == effectiveSuiteId }
        var nextState = _uiState.value.copy(
            fontSize = preferences.fontSize,
            lineHeight = preferences.lineHeight,
            letterSpacing = preferences.letterSpacing,
            textAlignment = preferences.textAlignment,
            fontType = preferences.fontType,
            marginLeftDp = preferences.marginLeft,
            marginRightDp = preferences.marginRight,
            marginTopDp = preferences.marginTop,
            marginBottomDp = preferences.marginBottom,
            readerTheme = preferences.readerTheme,
            brightness = preferences.brightness,
            customFontPath = preferences.customFontPath,
            customFonts = preferences.customFonts,
            readerBackgroundSelection = preferences.readerBackgroundSelection,
            readerBackgroundColorSelection = preferences.readerBackgroundColorSelection,
            readerBackgroundImageOpacity = preferences.readerBackgroundImageOpacity,
            readerBackgroundImageBlurDp = preferences.readerBackgroundImageBlurDp,
            customReaderBackgrounds = preferences.customReaderBackgrounds,
            preserveEpubBackground = preferences.preserveEpubBackground,
            pageImageCrop = preferences.pageImageCrop,
            readerTextColor = preferences.readerTextColor,
            pageAnimationSettings = preferences.pageAnimationSettings,
            pageTransition = if (preferences.eInkModeEnabled) "none" else preferences.pageTransition,
            readerThemeSuites = suiteState.suites,
            activeReaderThemeSuiteId = effectiveSuiteId,
            activeReaderLayoutThemeSuiteId = scopedReaderSuiteId,
            globalActiveReaderThemeSuiteId = suiteState.activeSuiteId,
            activeBookLayoutThemeSuiteId = scopedBookLayoutSuiteId,
            globalActiveBookLayoutThemeSuiteId = suiteState.activeBookLayoutSuiteId,
            readerThemeSuiteBookScoped = preferences.readerThemeSuiteBookScoped,
            pdfPageMode = if (preferences.eInkModeEnabled) "horizontal" else preferences.pdfPageMode,
            showReaderChapterProgress = preferences.showReaderChapterProgress,
            showReaderPageNumber = preferences.showReaderPageNumber,
            showReaderBattery = preferences.showReaderBattery,
            volumeKeyPageTurnEnabled = preferences.volumeKeyPageTurnEnabled,
            bionicReadingEnabled = preferences.bionicReadingEnabled,
            comicModeEnabled = preferences.comicModeEnabled,
            bodyFontWeight = preferences.bodyFontWeight,
            eInkModeEnabled = preferences.eInkModeEnabled,
            twoPageSpreadEnabled = preferences.twoPageSpreadEnabled,
            screenSleepTimeoutSeconds = preferences.screenSleepTimeoutSeconds,
            readerEdgeTapMode = preferences.readerEdgeTapMode,
            readerTopLeftContent = preferences.readerTopLeftContent,
            readerTopRightContent = preferences.readerTopRightContent,
            readerBottomLeftContent = preferences.readerBottomLeftContent,
            readerBottomRightContent = preferences.readerBottomRightContent,
            readerCornerMargins = preferences.readerCornerMargins,
            readerDisplayMode = preferences.readerDisplayMode,
            paragraphSpacing = preferences.paragraphSpacing,
            firstLineIndent = preferences.firstLineIndent,
            chineseMode = preferences.chineseMode,
            selectionMenuItems = preferences.selectionMenuItems
        )
        if (effectiveSuite != null) {
            nextState = nextState
                .withReaderThemeSettings(effectiveSuite.settingsFor(layoutTarget))
                .copy(
                    readerThemeSuites = suiteState.suites,
                    activeReaderThemeSuiteId = effectiveSuiteId,
                    activeReaderLayoutThemeSuiteId = scopedReaderSuiteId,
                    globalActiveReaderThemeSuiteId = suiteState.activeSuiteId,
                    activeBookLayoutThemeSuiteId = scopedBookLayoutSuiteId,
                    globalActiveBookLayoutThemeSuiteId = suiteState.activeBookLayoutSuiteId,
                    readerThemeSuiteBookScoped = preferences.readerThemeSuiteBookScoped
                )
        }
        _uiState.value = nextState
        updateHighlightPalettes(
            preferences.customHighlightPalettes,
            preferences.activeHighlightPaletteId
        )
        hydrateReaderBackgrounds(preferences.customReaderBackgrounds)
    }

    private fun hydrateReaderBackgrounds(presets: List<ReaderBackgroundPreset>) {
        if (presets.none { it.type == ReaderBackgroundType.IMAGE && it.dominantColor == null }) return
        viewModelScope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                presets.map { preset ->
                    if (preset.type == ReaderBackgroundType.IMAGE && preset.dominantColor == null) {
                        File(preset.value).takeIf(File::exists)
                            ?.let { preset.copy(dominantColor = extractDominantColor(it)) }
                            ?: preset
                    } else {
                        preset
                    }
                }
            }
            if (hydrated != presets) {
                _uiState.value = _uiState.value.copy(customReaderBackgrounds = hydrated)
                dataStoreManager.saveCustomReaderBackgrounds(hydrated)
            }
        }
    }

    @Suppress("unused")
    private fun loadReaderSettingsLegacy() {
        viewModelScope.launch {
            dataStoreManager.fontSize.collectLatest { size ->
                _uiState.value = _uiState.value.copy(fontSize = size)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerTheme.collectLatest { theme ->
                _uiState.value = _uiState.value.copy(readerTheme = theme)
            }
        }
        viewModelScope.launch {
            dataStoreManager.lineHeight.collectLatest { lh ->
                _uiState.value = _uiState.value.copy(lineHeight = lh)
            }
        }
        viewModelScope.launch {
            dataStoreManager.letterSpacing.collectLatest { ls ->
                _uiState.value = _uiState.value.copy(letterSpacing = ls)
            }
        }
        viewModelScope.launch {
            dataStoreManager.textAlignment.collectLatest { alignment ->
                _uiState.value = _uiState.value.copy(textAlignment = alignment)
            }
        }
        viewModelScope.launch {
            dataStoreManager.fontType.collectLatest { ft ->
                _uiState.value = _uiState.value.copy(fontType = ft)
                // 旧版本已选仿宋的用户升级后无本地字体：后台自动补下（失败则保持系统字体降级）
                if (ft == "fangsong" && DownloadedFonts.file(context, "fangsong") == null) {
                    fontDownloadManager.ensure("fangsong")
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginLeft.collectLatest { margin ->
                _uiState.value = _uiState.value.copy(marginLeftDp = margin)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginRight.collectLatest { margin ->
                _uiState.value = _uiState.value.copy(marginRightDp = margin)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginTop.collectLatest { margin ->
                _uiState.value = _uiState.value.copy(marginTopDp = margin)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginBottom.collectLatest { margin ->
                _uiState.value = _uiState.value.copy(marginBottomDp = margin)
            }
        }
        viewModelScope.launch {
            dataStoreManager.brightness.collectLatest { b ->
                _uiState.value = _uiState.value.copy(brightness = b)
            }
        }
        viewModelScope.launch {
            dataStoreManager.customFontPath.collectLatest { path ->
                _uiState.value = _uiState.value.copy(customFontPath = path)
            }
        }
        viewModelScope.launch {
            dataStoreManager.customFonts.collectLatest { fonts ->
                _uiState.value = _uiState.value.copy(customFonts = fonts)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerBackgroundSelection.collectLatest { selection ->
                _uiState.value = _uiState.value.copy(readerBackgroundSelection = selection)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerBackgroundColorSelection.collectLatest { selection ->
                _uiState.value = _uiState.value.copy(readerBackgroundColorSelection = selection)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerBackgroundImageOpacity.collectLatest { opacity ->
                _uiState.value = _uiState.value.copy(readerBackgroundImageOpacity = opacity)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerBackgroundImageBlurDp.collectLatest { blurDp ->
                _uiState.value = _uiState.value.copy(readerBackgroundImageBlurDp = blurDp)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerPageAnimationSettings.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(pageAnimationSettings = settings)
            }
        }
        viewModelScope.launch {
            dataStoreManager.pageTransition().collectLatest { mode ->
                if (!_uiState.value.eInkModeEnabled) {
                    _uiState.value = _uiState.value.copy(pageTransition = mode)
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.preserveEpubBackground.collectLatest { preserve ->
                _uiState.value = _uiState.value.copy(preserveEpubBackground = preserve)
                // 阅读器排版用它决定是否还原原书背景装饰，变更后要重排已缓存的章节。
                val activeParser = parser
                if (activeParser != null && activeParser.preserveEpubBackground != preserve) {
                    activeParser.preserveEpubBackground = preserve
                    activeParser.clearHtmlCache()
                    preloadCache.clear()
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.pageImageCrop.collectLatest { crop ->
                _uiState.value = _uiState.value.copy(pageImageCrop = crop)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerTextColor.collectLatest { color ->
                _uiState.value = _uiState.value.copy(readerTextColor = color)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerThemeSuiteState.collectLatest { state ->
                val current = _uiState.value
                val readerSuiteId = if (current.readerThemeSuiteBookScoped) {
                    current.activeReaderLayoutThemeSuiteId
                        .takeIf { id -> state.suites.any { it.id == id } }
                        ?: state.activeSuiteId
                } else state.activeSuiteId
                val bookLayoutSuiteId = if (current.readerThemeSuiteBookScoped) {
                    current.activeBookLayoutThemeSuiteId
                        .takeIf { id -> state.suites.any { it.id == id } }
                        ?: state.activeBookLayoutSuiteId
                } else state.activeBookLayoutSuiteId
                val layout = current.readerLayoutTarget()
                val effectiveId = ReaderThemeSuites.resolveActiveId(
                    suites = state.suites,
                    requestedId = when (layout) {
                        ReaderLayoutTarget.READER_LAYOUT -> readerSuiteId
                        ReaderLayoutTarget.BOOK_LAYOUT -> bookLayoutSuiteId
                    },
                    layout = layout
                )
                val effectiveSuite = state.suites.firstOrNull { it.id == effectiveId }
                val updated = current.copy(
                    readerThemeSuites = state.suites,
                    activeReaderThemeSuiteId = effectiveId,
                    activeReaderLayoutThemeSuiteId = readerSuiteId,
                    globalActiveReaderThemeSuiteId = state.activeSuiteId,
                    activeBookLayoutThemeSuiteId = bookLayoutSuiteId,
                    globalActiveBookLayoutThemeSuiteId = state.activeBookLayoutSuiteId
                )
                _uiState.value = effectiveSuite?.let {
                    updated.withReaderThemeSettings(it.settingsFor(layout))
                }
                    ?.copy(
                        readerThemeSuites = state.suites,
                        activeReaderThemeSuiteId = effectiveId,
                        activeReaderLayoutThemeSuiteId = readerSuiteId,
                        globalActiveReaderThemeSuiteId = state.activeSuiteId,
                        activeBookLayoutThemeSuiteId = bookLayoutSuiteId,
                        globalActiveBookLayoutThemeSuiteId = state.activeBookLayoutSuiteId
                    ) ?: updated
                reconcileProcessedBackgrounds()
            }
        }
        viewModelScope.launch {
            dataStoreManager.customReaderBackgrounds.collectLatest { presets ->
                _uiState.value = _uiState.value.copy(customReaderBackgrounds = presets)
                reconcileProcessedBackgrounds()
                val hydrated = withContext(Dispatchers.IO) {
                    presets.map { preset ->
                        if (preset.type == ReaderBackgroundType.IMAGE && preset.dominantColor == null) {
                            val file = File(preset.value)
                            if (file.exists()) {
                                preset.copy(dominantColor = extractDominantColor(file))
                            } else {
                                preset
                            }
                        } else {
                            preset
                        }
                    }
                }
                if (hydrated != presets) {
                    _uiState.value = _uiState.value.copy(customReaderBackgrounds = hydrated)
                    dataStoreManager.saveCustomReaderBackgrounds(hydrated)
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.pdfPageMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(pdfPageMode = mode)
            }
        }
        viewModelScope.launch {
            dataStoreManager.showReaderChapterProgress.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showReaderChapterProgress = show)
            }
        }
        viewModelScope.launch {
            dataStoreManager.showReaderPageNumber.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showReaderPageNumber = show)
            }
        }
        viewModelScope.launch {
            dataStoreManager.showReaderBattery.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showReaderBattery = show)
            }
        }
        viewModelScope.launch {
            dataStoreManager.volumeKeyPageTurnEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(volumeKeyPageTurnEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.bionicReadingEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(bionicReadingEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.comicMode.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(comicModeEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.bodyFontWeight.collectLatest { weight ->
                // When a book has its own suite selection, the effective suite settings
                // (applied by readerPreferences/readerThemeSuiteState) are authoritative.
                if (!_uiState.value.readerThemeSuiteBookScoped) {
                    _uiState.value = _uiState.value.copy(bodyFontWeight = weight)
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.eInkModeEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(eInkModeEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.twoPageSpreadEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(twoPageSpreadEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dataStoreManager.screenSleepTimeoutSeconds.collectLatest { seconds ->
                _uiState.value = _uiState.value.copy(screenSleepTimeoutSeconds = seconds)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerEdgeTapMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(readerEdgeTapMode = mode)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerCornerContent(ReaderPageCorner.TOP_LEFT).collectLatest { content ->
                _uiState.value = _uiState.value.copy(readerTopLeftContent = content)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerCornerContent(ReaderPageCorner.TOP_RIGHT).collectLatest { content ->
                _uiState.value = _uiState.value.copy(readerTopRightContent = content)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerCornerContent(ReaderPageCorner.BOTTOM_LEFT).collectLatest { content ->
                _uiState.value = _uiState.value.copy(readerBottomLeftContent = content)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerCornerContent(ReaderPageCorner.BOTTOM_RIGHT).collectLatest { content ->
                _uiState.value = _uiState.value.copy(readerBottomRightContent = content)
            }
        }
        viewModelScope.launch {
            dataStoreManager.displayMode().collectLatest { mode ->
                _uiState.value = _uiState.value.copy(readerDisplayMode = mode)
            }
        }
        viewModelScope.launch {
            combine(
                dataStoreManager.customHighlightPalettes,
                dataStoreManager.activeHighlightPaletteId
            ) { palettes, activeId -> palettes to activeId }
                .collectLatest { (palettes, activeId) ->
                    updateHighlightPalettes(palettes, activeId)
                }
        }
        viewModelScope.launch {
            dataStoreManager.selectionMenuItems.collectLatest { items ->
                _uiState.value = _uiState.value.copy(selectionMenuItems = items)
            }
        }
    }

    fun saveFontSize(size: Float) {
        updateCurrentThemeSettings { copy(fontSize = size) }
    }

    fun saveLineHeight(lh: Float) {
        updateCurrentThemeSettings { copy(lineHeight = lh) }
    }

    fun saveLetterSpacing(ls: Float) {
        updateCurrentThemeSettings { copy(letterSpacing = ls) }
    }

    fun saveTextAlignment(alignment: ReaderTextAlignment) {
        if (_uiState.value.textAlignment == alignment) return
        updateCurrentThemeSettings { copy(textAlignment = alignment) }
        _uiState.value = _uiState.value.copy(contentRevision = _uiState.value.contentRevision + 1)
        viewModelScope.launch {
            loadChapterContent()
        }
    }

    fun saveFontType(ft: String) {
        // 仿宋为按需下载字体：本地无文件时先下载，成功后再切换
        if (ft == "fangsong" && DownloadedFonts.file(context, "fangsong") == null) {
            _uiState.value = _uiState.value.copy(fontDownloadKey = "fangsong", fontDownloadFailed = false)
            viewModelScope.launch {
                val ok = fontDownloadManager.ensure("fangsong") != null
                _uiState.value = _uiState.value.copy(fontDownloadKey = null, fontDownloadFailed = !ok)
                if (ok) applyFontType(ft)
            }
            return
        }
        applyFontType(ft)
    }

    private fun applyFontType(ft: String) {
        updateCurrentThemeSettings { copy(fontType = ft) }
        var newState = _uiState.value
        // 🔥 选自定义字体时同步更新 customFontPath，让 ReadView 立即生效
        if (ft.startsWith("custom:")) {
            val id = ft.removePrefix("custom:")
            val path = _uiState.value.customFonts.find { it.id == id }?.path
            if (path != null) newState = newState.copy(customFontPath = path)
        }
        _uiState.value = newState
        viewModelScope.launch {
            if (ft.startsWith("custom:")) {
                val id = ft.removePrefix("custom:")
                val path = newState.customFontPath
                if (path != null) dataStoreManager.saveCustomFontPath(path)
            }
        }
    }

    fun saveMarginLeft(value: Float) {
        updateCurrentThemeSettings { copy(marginLeft = value) }
    }

    fun saveMarginRight(value: Float) {
        updateCurrentThemeSettings { copy(marginRight = value) }
    }

    fun saveMarginTop(value: Float) {
        updateCurrentThemeSettings { copy(marginTop = value) }
    }

    fun saveMarginBottom(value: Float) {
        updateCurrentThemeSettings { copy(marginBottom = value) }
    }

    fun updateReaderContentWidth(widthPx: Int) {
        val activeParser = parser ?: return
        val normalizedWidth = widthPx.coerceAtLeast(1)
        if (activeParser.contentWidth == normalizedWidth) return
        activeParser.contentWidth = normalizedWidth
        activeParser.clearHtmlCache()
        preloadCache.clear()
    }

    fun saveReaderTheme(theme: String) {
        // 「原排版」套装锁定配色：底色与文字颜色只能靠切换套装来改变。
        if (_uiState.value.keepsPublisherPaint()) return
        updateCurrentThemeSettings {
            copy(backgroundSelection = theme, backgroundColorSelection = theme)
        }
    }
    fun savePreserveEpubBackground(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(preserveEpubBackground = enabled)
        viewModelScope.launch { dataStoreManager.savePreserveEpubBackground(enabled) }
    }

    fun savePageImageCrop(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(pageImageCrop = enabled)
        viewModelScope.launch { dataStoreManager.savePageImageCrop(enabled) }
    }

    fun saveReaderTextColor(color: Int?) {
        if (_uiState.value.keepsPublisherPaint()) return
        updateCurrentThemeSettings { copy(textColor = color) }
    }

    fun selectReaderThemeSuite(suiteId: String) {
        val state = _uiState.value
        val layout = state.readerLayoutTarget()
        val suite = state.readerThemeSuites.firstOrNull { it.id == suiteId } ?: return
        // 「原排版」只属于书籍原排版，阅读器排版里不可选。
        if (!ReaderThemeSuites.supportsLayout(suite, layout)) return
        if (state.activeThemeSuiteId() == suiteId) return

        parser?.paragraphSpacingDp = suite.settings.paragraphSpacing
        parser?.firstLineIndentChars = suite.settings.firstLineIndent
        parser?.clearHtmlCache()
        _uiState.value = state.withReaderThemeSettings(
            suite.settingsFor(layout)
        ).copy(
            activeReaderThemeSuiteId = suiteId,
            activeReaderLayoutThemeSuiteId = if (layout == ReaderLayoutTarget.READER_LAYOUT) {
                suiteId
            } else {
                state.activeReaderLayoutThemeSuiteId
            },
            activeBookLayoutThemeSuiteId = if (layout == ReaderLayoutTarget.BOOK_LAYOUT) {
                suiteId
            } else {
                state.activeBookLayoutThemeSuiteId
            },
            globalActiveReaderThemeSuiteId =
                if (layout == ReaderLayoutTarget.READER_LAYOUT &&
                    !state.readerThemeSuiteBookScoped
                ) {
                    suiteId
                } else {
                    state.globalActiveReaderThemeSuiteId
                },
            globalActiveBookLayoutThemeSuiteId =
                if (layout == ReaderLayoutTarget.BOOK_LAYOUT &&
                    !state.readerThemeSuiteBookScoped
                ) {
                    suiteId
                } else {
                    state.globalActiveBookLayoutThemeSuiteId
                }
        )
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            if (state.readerThemeSuiteBookScoped) {
                dataStoreManager.saveReaderThemeSuiteBookActiveId(bookId, suiteId, layout)
            } else {
                val next = _uiState.value
                dataStoreManager.saveReaderThemeSuiteState(
                    suites = next.readerThemeSuites,
                    activeSuiteId = next.globalActiveReaderThemeSuiteId,
                    applyActiveSuite = layout == ReaderLayoutTarget.READER_LAYOUT,
                    activeBookLayoutSuiteId = next.globalActiveBookLayoutThemeSuiteId
                )
            }
            loadChapterContent()
        }
    }

    fun setApplyThemeSuiteToBook(enabled: Boolean) {
        val state = _uiState.value
        if (state.readerThemeSuiteBookScoped == enabled) return
        val layout = state.readerLayoutTarget()
        val nextActiveId = if (enabled) {
            state.activeThemeSuiteId()
        } else {
            when (layout) {
                ReaderLayoutTarget.READER_LAYOUT -> state.globalActiveReaderThemeSuiteId
                ReaderLayoutTarget.BOOK_LAYOUT -> state.globalActiveBookLayoutThemeSuiteId
            }
        }
        val suite = state.readerThemeSuites.firstOrNull { it.id == nextActiveId } ?: return
        parser?.paragraphSpacingDp = suite.settings.paragraphSpacing
        parser?.firstLineIndentChars = suite.settings.firstLineIndent
        parser?.clearHtmlCache()
        _uiState.value = state
            .withReaderThemeSettings(suite.settingsFor(layout))
            .copy(
                activeReaderThemeSuiteId = nextActiveId,
                activeReaderLayoutThemeSuiteId =
                    if (layout == ReaderLayoutTarget.READER_LAYOUT) {
                        nextActiveId
                    } else {
                        state.activeReaderLayoutThemeSuiteId
                    },
                activeBookLayoutThemeSuiteId =
                    if (layout == ReaderLayoutTarget.BOOK_LAYOUT) {
                        nextActiveId
                    } else {
                        state.activeBookLayoutThemeSuiteId
                    },
                globalActiveReaderThemeSuiteId = state.globalActiveReaderThemeSuiteId,
                globalActiveBookLayoutThemeSuiteId = state.globalActiveBookLayoutThemeSuiteId,
                readerThemeSuiteBookScoped = enabled
            )
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            dataStoreManager.setReaderThemeSuiteBookScoped(
                bookId = bookId,
                enabled = enabled,
                activeSuiteId = state.activeReaderLayoutThemeSuiteId.takeIf { enabled },
                activeBookLayoutSuiteId = state.activeBookLayoutThemeSuiteId.takeIf { enabled }
            )
            loadChapterContent()
        }
    }

    fun createReaderThemeSuite(rawName: String) {
        val name = normalizeReaderThemeSuiteName(rawName)
        if (readerThemeSuiteNameCodePointCount(name) !in 1..20) return
        val state = _uiState.value
        val reservedNames = setOf(
            context.getString(R.string.theme_day),
            context.getString(R.string.theme_night),
            context.getString(R.string.theme_sepia),
            context.getString(R.string.theme_green),
            context.getString(R.string.reader_theme_publisher)
        )
        if (reservedNames.any { it.equals(name, ignoreCase = true) }) return
        if (state.readerThemeSuites.any {
                it.customName?.equals(name, ignoreCase = true) == true
            }
        ) return

        val suite = ReaderThemeSuites.newCustom(UUID.randomUUID().toString(), name)
        val updated = state.readerThemeSuites + suite
        val layout = state.readerLayoutTarget()
        parser?.paragraphSpacingDp = suite.settings.paragraphSpacing
        parser?.firstLineIndentChars = suite.settings.firstLineIndent
        parser?.clearHtmlCache()
        _uiState.value = state.withReaderThemeSettings(
            suite.settingsFor(layout)
        ).copy(
            readerThemeSuites = updated,
            activeReaderThemeSuiteId = suite.id,
            activeReaderLayoutThemeSuiteId =
                if (layout == ReaderLayoutTarget.READER_LAYOUT) {
                    suite.id
                } else {
                    state.activeReaderLayoutThemeSuiteId
                },
            activeBookLayoutThemeSuiteId =
                if (layout == ReaderLayoutTarget.BOOK_LAYOUT) {
                    suite.id
                } else {
                    state.activeBookLayoutThemeSuiteId
                },
            globalActiveReaderThemeSuiteId =
                if (!state.readerThemeSuiteBookScoped && layout == ReaderLayoutTarget.READER_LAYOUT) {
                    suite.id
                } else {
                    state.globalActiveReaderThemeSuiteId
                },
            globalActiveBookLayoutThemeSuiteId =
                if (!state.readerThemeSuiteBookScoped && layout == ReaderLayoutTarget.BOOK_LAYOUT) {
                    suite.id
                } else {
                    state.globalActiveBookLayoutThemeSuiteId
                }
        )
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            val next = _uiState.value
            dataStoreManager.saveReaderThemeSuiteState(
                suites = updated,
                activeSuiteId = next.globalActiveReaderThemeSuiteId,
                applyActiveSuite = !state.readerThemeSuiteBookScoped &&
                    layout == ReaderLayoutTarget.READER_LAYOUT,
                activeBookLayoutSuiteId = next.globalActiveBookLayoutThemeSuiteId
            )
            if (state.readerThemeSuiteBookScoped) {
                dataStoreManager.saveReaderThemeSuiteBookActiveId(bookId, suite.id, layout)
            }
            loadChapterContent()
        }
    }

    fun deleteReaderThemeSuite(suiteId: String) {
        val state = _uiState.value
        val removedIndex = state.readerThemeSuites.indexOfFirst { it.id == suiteId }
        val removed = state.readerThemeSuites.getOrNull(removedIndex) ?: return
        if (removed.isBuiltIn) return

        val remaining = state.readerThemeSuites.filterNot { it.id == suiteId }
        val neighbor = remaining.getOrNull(removedIndex) ?: remaining.getOrNull(removedIndex - 1)
        val layout = state.readerLayoutTarget()

        // 阅读器排版：落到相邻套装（跳过「原排版」），兜底日间。
        val readerBucketAffected = state.activeReaderLayoutThemeSuiteId == suiteId ||
            state.globalActiveReaderThemeSuiteId == suiteId
        val nextReaderBucketId = if (readerBucketAffected) {
            neighbor?.takeIf { !it.isBookLayoutOnly }?.id
                ?: remaining.firstOrNull { it.id == ReaderThemeSuites.DAY_ID }?.id
                ?: remaining.firstOrNull { !it.isBookLayoutOnly }?.id
                ?: ReaderThemeSuites.DAY_ID
        } else {
            state.activeReaderLayoutThemeSuiteId
        }
        val nextGlobalReaderId = if (state.globalActiveReaderThemeSuiteId == suiteId) {
            nextReaderBucketId
        } else {
            state.globalActiveReaderThemeSuiteId
        }

        // 书籍原排版：删除后回落内置「原排版」。
        val bookBucketAffected = state.activeBookLayoutThemeSuiteId == suiteId ||
            state.globalActiveBookLayoutThemeSuiteId == suiteId
        val nextBookBucketId = if (bookBucketAffected) {
            ReaderThemeSuites.PUBLISHER_ID
        } else {
            state.activeBookLayoutThemeSuiteId
        }
        val nextGlobalBookId = if (state.globalActiveBookLayoutThemeSuiteId == suiteId) {
            ReaderThemeSuites.PUBLISHER_ID
        } else {
            state.globalActiveBookLayoutThemeSuiteId
        }

        val wasEffectiveRemoved = state.activeThemeSuiteId() == suiteId
        val nextEffectiveId = ReaderThemeSuites.resolveActiveId(
            suites = remaining,
            requestedId = when (layout) {
                ReaderLayoutTarget.READER_LAYOUT -> nextReaderBucketId
                ReaderLayoutTarget.BOOK_LAYOUT -> nextBookBucketId
            },
            layout = layout
        )
        var next = state.copy(
            readerThemeSuites = remaining,
            activeReaderThemeSuiteId = nextEffectiveId,
            activeReaderLayoutThemeSuiteId = nextReaderBucketId,
            activeBookLayoutThemeSuiteId = nextBookBucketId,
            globalActiveReaderThemeSuiteId = nextGlobalReaderId,
            globalActiveBookLayoutThemeSuiteId = nextGlobalBookId
        )
        if (wasEffectiveRemoved) {
            remaining.firstOrNull { it.id == nextEffectiveId }?.let { replacement ->
                parser?.paragraphSpacingDp = replacement.settings.paragraphSpacing
                parser?.firstLineIndentChars = replacement.settings.firstLineIndent
                parser?.clearHtmlCache()
                next = next.withReaderThemeSettings(replacement.settingsFor(layout))
            }
            refreshReaderNotes(_notes.value)
        }
        _uiState.value = next
        viewModelScope.launch {
            dataStoreManager.saveReaderThemeSuiteState(
                suites = remaining,
                activeSuiteId = nextGlobalReaderId,
                applyActiveSuite = !state.readerThemeSuiteBookScoped &&
                    wasEffectiveRemoved && layout == ReaderLayoutTarget.READER_LAYOUT,
                activeBookLayoutSuiteId = nextGlobalBookId
            )
            if (state.readerThemeSuiteBookScoped) {
                if (state.activeReaderLayoutThemeSuiteId == suiteId) {
                    dataStoreManager.saveReaderThemeSuiteBookActiveId(
                        bookId,
                        nextReaderBucketId,
                        ReaderLayoutTarget.READER_LAYOUT
                    )
                }
                if (state.activeBookLayoutThemeSuiteId == suiteId) {
                    dataStoreManager.saveReaderThemeSuiteBookActiveId(
                        bookId,
                        nextBookBucketId,
                        ReaderLayoutTarget.BOOK_LAYOUT
                    )
                }
            }
            if (wasEffectiveRemoved) loadChapterContent()
        }
    }

    fun reorderReaderThemeSuites(orderedIds: List<String>) {
        val state = _uiState.value
        if (orderedIds.size != state.readerThemeSuites.size ||
            orderedIds.toSet() != state.readerThemeSuites.map { it.id }.toSet()
        ) return

        val byId = state.readerThemeSuites.associateBy(ReaderThemeSuite::id)
        val reordered = orderedIds.mapNotNull(byId::get)
        _uiState.value = state.copy(readerThemeSuites = reordered)
        viewModelScope.launch {
            dataStoreManager.saveReaderThemeSuiteState(
                reordered,
                state.globalActiveReaderThemeSuiteId,
                applyActiveSuite = false,
                activeBookLayoutSuiteId = state.globalActiveBookLayoutThemeSuiteId
            )
        }
    }

    private fun ReaderUiState.withReaderThemeSettings(settings: ReaderThemeSettings): ReaderUiState {
        val resolvedCustomFontPath = settings.fontType
            .takeIf { it.startsWith("custom:") }
            ?.removePrefix("custom:")
            ?.let { id -> customFonts.firstOrNull { it.id == id }?.path }
        return copy(
            fontSize = settings.fontSize,
            lineHeight = settings.lineHeight,
            letterSpacing = settings.letterSpacing,
            textAlignment = settings.textAlignment,
            fontType = if (settings.fontType.startsWith("custom:") && resolvedCustomFontPath == null) {
                "system"
            } else {
                settings.fontType
            },
            customFontPath = resolvedCustomFontPath ?: customFontPath,
            marginLeftDp = settings.marginLeft,
            marginRightDp = settings.marginRight,
            marginTopDp = settings.marginTop,
            marginBottomDp = settings.marginBottom,
            paragraphSpacing = settings.paragraphSpacing,
            firstLineIndent = settings.firstLineIndent,
            readerTheme = settings.backgroundSelection
                .takeIf { id -> id in ReaderThemeSuites.THEME_IDS }
                ?: ReaderThemeSuites.DAY_ID,
            readerBackgroundSelection = settings.backgroundSelection,
            readerBackgroundColorSelection = settings.backgroundColorSelection,
            readerBackgroundImageOpacity = settings.backgroundImageOpacity,
            readerBackgroundImageBlurDp = settings.backgroundImageBlurDp,
            bodyFontWeight = settings.bodyFontWeight,
            readerTextColor = settings.textColor
        )
    }

    private fun ReaderUiState.withUpdatedActiveThemeSettings(
        layout: ReaderLayoutTarget = readerLayoutTarget(),
        transform: ReaderThemeSettings.() -> ReaderThemeSettings
    ): ReaderUiState = copy(
        readerThemeSuites = readerThemeSuites.map { suite ->
            if (suite.id == activeThemeSuiteIdFor(layout)) {
                suite.withSettings(layout, suite.settingsFor(layout).transform())
            } else {
                suite
            }
        }
    )

    private fun ReaderUiState.currentThemeSettings(
        layout: ReaderLayoutTarget = readerLayoutTarget()
    ): ReaderThemeSettings =
        readerThemeSuites.firstOrNull { it.id == activeThemeSuiteIdFor(layout) }?.settingsFor(layout)
            ?: ReaderThemeSettings(
                backgroundSelection = readerBackgroundSelection,
                backgroundColorSelection = readerBackgroundColorSelection,
                backgroundImageOpacity = readerBackgroundImageOpacity,
                backgroundImageBlurDp = readerBackgroundImageBlurDp,
                textColor = readerTextColor,
                fontSize = fontSize,
                fontType = fontType,
                bodyFontWeight = bodyFontWeight,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textAlignment = textAlignment,
                paragraphSpacing = paragraphSpacing,
                firstLineIndent = firstLineIndent,
                marginLeft = marginLeftDp,
                marginRight = marginRightDp,
                marginTop = marginTopDp,
                marginBottom = marginBottomDp
            )

    private fun persistCurrentThemeSettings(
        settings: ReaderThemeSettings,
        layout: ReaderLayoutTarget = _uiState.value.readerLayoutTarget()
    ) {
        val suiteId = _uiState.value.activeThemeSuiteIdFor(layout)
        viewModelScope.launch {
            dataStoreManager.updateReaderThemeSuite(suiteId, layout, settings)
        }
    }

    /**
     * 把当前排版模式对应的那套设置同步到扁平字段。
     * 打开过程的早期还不知道书籍格式（判断不出该用哪套），书籍信息到位后需要补一次。
     */
    private fun applyActiveThemeSettingsForCurrentLayout() {
        val state = _uiState.value
        val layout = state.readerLayoutTarget()
        val activeSuiteId = state.activeThemeSuiteIdFor(layout)
        val suite = state.readerThemeSuites.firstOrNull { it.id == activeSuiteId }
            ?: return
        // 同时把"当前模式正在用的套装"写回，主题面板高亮的卡片才会和真正生效的套装一致。
        _uiState.value = state.withReaderThemeSettings(
            suite.settingsFor(layout)
        ).copy(activeReaderThemeSuiteId = activeSuiteId)
    }

    private fun updateCurrentThemeSettings(
        transform: ReaderThemeSettings.() -> ReaderThemeSettings
    ): ReaderThemeSettings {
        val state = _uiState.value
        val layout = state.readerLayoutTarget()
        val updated = state.currentThemeSettings(layout).transform()
        _uiState.value = state
            .withUpdatedActiveThemeSettings(layout) { transform() }
            .withReaderThemeSettings(updated)
        persistCurrentThemeSettings(updated, layout)
        return updated
    }

    /** Ensures persisted image blur has a bitmap copy that curl snapshots can retain. */
    private fun reconcileProcessedBackgrounds() {
        processedBackgroundJob?.cancel()
        val state = _uiState.value
        val settings = state.currentThemeSettings()
        val selected = state.customReaderBackgrounds.firstOrNull {
            it.selectionKey == settings.backgroundSelection && it.type == ReaderBackgroundType.IMAGE
        } ?: return
        val blur = settings.backgroundImageBlurDp.coerceIn(0f, 40f)
        if (blur < 0.01f ||
            (selected.processedValue != null && selected.processedBlurDp != null &&
                kotlin.math.abs(selected.processedBlurDp - blur) < 0.01f &&
                File(selected.processedValue).isFile)
        ) return
        processedBackgroundJob = viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                val blurKey = "%.2f".format(Locale.US, blur).replace('.', '_')
                val file = File(context.filesDir, "reader_backgrounds/${selected.id}.blurred-$blurKey.jpg")
                if (file.isFile || ReaderBackgroundImageProcessor.createBlurredCopy(
                        File(selected.value), file, blur, context.resources.displayMetrics.density
                    )
                ) file.absolutePath else null
            } ?: return@launch

            val latestState = _uiState.value
            val latestSettings = latestState.currentThemeSettings()
            val latestSelected = latestState.customReaderBackgrounds.firstOrNull {
                it.id == selected.id && it.value == selected.value &&
                    it.selectionKey == latestSettings.backgroundSelection
            } ?: return@launch
            if (kotlin.math.abs(latestSettings.backgroundImageBlurDp.coerceIn(0f, 40f) - blur) >= 0.01f) {
                return@launch
            }

            val latest = latestState.customReaderBackgrounds
            val updated = latest.map {
                if (it.id == latestSelected.id) it.copy(
                    processedValue = target,
                    processedBlurDp = blur
                ) else it
            }
            if (updated != latest) {
                dataStoreManager.saveCustomReaderBackgrounds(updated)
                _uiState.value = _uiState.value.copy(customReaderBackgrounds = updated)
                latestSelected.processedValue
                    ?.takeUnless { it == target }
                    ?.let { oldPath -> withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } } }
            }
        }
    }

    fun selectReaderBackground(selection: String) {
        if (_uiState.value.keepsPublisherPaint()) return
        if (selection in setOf("day", "night", "sepia", "green")) {
            saveReaderTheme(selection)
            return
        }
        if (_uiState.value.customReaderBackgrounds.none { it.selectionKey == selection }) return

        val selectedPreset = _uiState.value.customReaderBackgrounds.first {
            it.selectionKey == selection
        }
        updateCurrentThemeSettings {
            copy(
                backgroundSelection = selection,
                backgroundColorSelection = if (selectedPreset.type == ReaderBackgroundType.COLOR) {
                    selection
                } else {
                    backgroundColorSelection
                }
            )
        }
    }

    fun addCustomReaderBackgroundColor(color: Int, displayName: String = "") {
        if (_uiState.value.keepsPublisherPaint()) return
        val preset = ReaderBackgroundPreset(
            id = UUID.randomUUID().toString(),
            type = ReaderBackgroundType.COLOR,
            value = String.format(Locale.US, "#%08X", color),
            dominantColor = color,
            name = displayName.trim().ifBlank { context.getString(R.string.background_name_default) }
        )
        saveAddedReaderBackground(preset)
    }

    fun addCustomReaderBackgroundImage(uri: Uri, displayName: String = "") {
        if (_uiState.value.keepsPublisherPaint()) return
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                val directory = File(context.filesDir, "reader_backgrounds").apply { mkdirs() }
                val file = File(directory, "$id.image")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                    ReaderBackgroundPreset(
                        id = id,
                        type = ReaderBackgroundType.IMAGE,
                        value = file.absolutePath,
                        dominantColor = extractDominantColor(file),
                        name = displayName.trim().ifBlank { context.getString(R.string.background_name_default) }
                    )
                } catch (_: Exception) {
                    file.delete()
                    null
                }
            } ?: return@launch
            saveAddedReaderBackground(preset)
        }
    }

    fun deleteCustomReaderBackground(id: String) {
        val state = _uiState.value
        val removed = state.customReaderBackgrounds.firstOrNull { it.id == id } ?: return
        val remaining = state.customReaderBackgrounds.filterNot { it.id == id }
        val wasSelected = state.readerBackgroundSelection == removed.selectionKey
        val repairedSuites = state.readerThemeSuites.map { suite ->
            ReaderLayoutTarget.entries.fold(suite) { acc, layout ->
                val layoutSettings = acc.settingsFor(layout)
                if (layoutSettings.backgroundSelection == removed.selectionKey) {
                    acc.withSettings(
                        layout,
                        layoutSettings.copy(
                            backgroundSelection = layoutSettings.backgroundColorSelection
                        )
                    )
                } else {
                    acc
                }
            }
        }
        val restoredSelection = repairedSuites
            .firstOrNull { it.id == state.activeThemeSuiteId() }
            ?.settingsFor(state.readerLayoutTarget())
            ?.backgroundSelection
            ?: ReaderThemeSuites.DAY_ID
        val restoredTheme = restoredSelection.takeIf { id -> id in ReaderThemeSuites.THEME_IDS }
            ?: ReaderThemeSuites.DAY_ID
        _uiState.value = state.copy(
            customReaderBackgrounds = remaining,
            readerThemeSuites = repairedSuites,
            readerTheme = if (wasSelected) restoredTheme else state.readerTheme,
            readerBackgroundSelection = if (wasSelected) restoredSelection else state.readerBackgroundSelection
        )
        viewModelScope.launch {
            dataStoreManager.saveCustomReaderBackgrounds(remaining)
            dataStoreManager.saveReaderThemeSuiteState(
                repairedSuites,
                state.globalActiveReaderThemeSuiteId,
                applyActiveSuite = !state.readerThemeSuiteBookScoped &&
                    state.globalActiveReaderThemeSuiteId == state.activeReaderThemeSuiteId,
                activeBookLayoutSuiteId = state.globalActiveBookLayoutThemeSuiteId
            )
            if (state.readerThemeSuiteBookScoped && wasSelected) {
                dataStoreManager.saveReaderThemeSuiteBookActiveId(
                    bookId,
                    restoredSelection,
                    state.readerLayoutTarget()
                )
            }
            if (removed.type == ReaderBackgroundType.IMAGE) {
                withContext(Dispatchers.IO) {
                    runCatching { File(removed.value).delete() }
                    removed.processedValue?.let { runCatching { File(it).delete() } }
                }
            }
        }
    }

    private fun saveAddedReaderBackground(preset: ReaderBackgroundPreset) {
        val state = _uiState.value
        val layout = state.readerLayoutTarget()
        val updated = state.customReaderBackgrounds + preset
        val updatedSuites = state.readerThemeSuites.map { suite ->
            if (suite.id == state.activeThemeSuiteIdFor(layout)) {
                val current = suite.settingsFor(layout)
                suite.withSettings(
                    layout,
                    current.copy(
                        backgroundSelection = preset.selectionKey,
                        backgroundColorSelection = if (preset.type == ReaderBackgroundType.COLOR) {
                            preset.selectionKey
                        } else {
                            current.backgroundColorSelection
                        }
                    )
                )
            } else {
                suite
            }
        }
        _uiState.value = _uiState.value.copy(
            customReaderBackgrounds = updated,
            readerThemeSuites = updatedSuites,
            readerTheme = "day",
            readerBackgroundSelection = preset.selectionKey,
            readerBackgroundColorSelection = if (preset.type == ReaderBackgroundType.COLOR) {
                preset.selectionKey
            } else {
                _uiState.value.readerBackgroundColorSelection
            }
        )
        val targetSuiteId = state.activeThemeSuiteIdFor(layout)
        val settings = updatedSuites.firstOrNull { it.id == targetSuiteId }
            ?.settingsFor(layout)
            ?: return
        viewModelScope.launch {
            dataStoreManager.saveCustomReaderBackgrounds(updated)
            dataStoreManager.updateReaderThemeSuite(targetSuiteId, layout, settings)
        }
    }

    private fun extractDominantColor(file: File): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
        ) ?: return 0xFFFBFBFC.toInt()
        return try {
            Palette.from(bitmap)
                .maximumColorCount(24)
                .generate()
                .getDominantColor(0xFFFBFBFC.toInt()) or 0xFF000000.toInt()
        } finally {
            bitmap.recycle()
        }
    }

    fun saveBrightness(value: Float) {
        _uiState.value = _uiState.value.copy(brightness = value)
        viewModelScope.launch { dataStoreManager.saveBrightness(value) }
    }

    fun saveCustomFontPath(path: String?) {
        _uiState.value = _uiState.value.copy(customFontPath = path)
        viewModelScope.launch { dataStoreManager.saveCustomFontPath(path) }
    }

    /** 删除指定自定义字体：从列表移除、删文件、若当前正用该字体则切换回 system */
    fun deleteCustomFont(id: String) {
        val current = _uiState.value
        val updated = current.customFonts.filter { it.id != id }
        val deletedPath = current.customFonts.find { it.id == id }?.path
        val deletedFontKey = "custom:$id"
        val repairedSuites = current.readerThemeSuites.map { suite ->
            ReaderLayoutTarget.entries.fold(suite) { acc, layout ->
                val layoutSettings = acc.settingsFor(layout)
                if (layoutSettings.fontType == deletedFontKey) {
                    acc.withSettings(layout, layoutSettings.copy(fontType = "system"))
                } else {
                    acc
                }
            }
        }
        // 如果当前用的是被删字体，切回 system
        if (current.fontType == deletedFontKey) {
            saveFontType("system")
            saveCustomFontPath(updated.firstOrNull()?.path)
        }
        _uiState.value = _uiState.value.copy(
            customFonts = updated,
            readerThemeSuites = repairedSuites
        )
        viewModelScope.launch {
            dataStoreManager.saveCustomFonts(updated)
            dataStoreManager.saveReaderThemeSuiteState(
                repairedSuites,
                current.globalActiveReaderThemeSuiteId,
                applyActiveSuite = false,
                activeBookLayoutSuiteId = current.globalActiveBookLayoutThemeSuiteId
            )
            if (deletedPath != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { java.io.File(deletedPath).delete() }
                }
            }
        }
    }

    @Synchronized
    fun getRenderSession(): BookRenderSession? {
        renderSession?.let { return it }
        val source = parser as? BookRenderSource ?: return null
        return source.openRenderSession().also { renderSession = it }
    }

    fun dismissEpubLayoutHint(doNotShowAgain: Boolean = false) {
        if (!_uiState.value.showEpubLayoutHint) return
        _uiState.value = _uiState.value.copy(showEpubLayoutHint = false)
        viewModelScope.launch {
            dataStoreManager.markEpubLayoutHintShown(bookId, doNotShowAgain)
        }
    }

    fun hideEpubLayoutHint() {
        _uiState.value = _uiState.value.copy(showEpubLayoutHint = false)
    }

    fun dismissMobiLayoutHint(doNotShowAgain: Boolean = false) {
        if (!_uiState.value.showMobiLayoutHint) return
        _uiState.value = _uiState.value.copy(showMobiLayoutHint = false)
        viewModelScope.launch {
            dataStoreManager.markMobiLayoutHintShown(bookId, doNotShowAgain)
        }
    }

    fun hideMobiLayoutHint() {
        _uiState.value = _uiState.value.copy(showMobiLayoutHint = false)
    }

    fun dismissTxtEncodingHint(doNotShowAgain: Boolean = false) {
        if (!_uiState.value.showTxtEncodingHint) return
        _uiState.value = _uiState.value.copy(showTxtEncodingHint = false)
        viewModelScope.launch {
            dataStoreManager.markTxtEncodingHintShown(bookId, doNotShowAgain)
        }
    }

    fun hideTxtEncodingHint() {
        _uiState.value = _uiState.value.copy(showTxtEncodingHint = false)
    }

    fun saveTxtEncoding(encoding: TxtEncoding) {
        val txtParser = parser as? TxtParser ?: return
        val state = _uiState.value
        val book = state.book ?: return
        if (state.isTxtEncodingChanging || state.txtEncoding == encoding) return

        viewModelScope.launch {
            val previousEncoding = state.txtEncoding
            _uiState.value = state.copy(isTxtEncodingChanging = true, pageReady = false)
            try {
                txtParser.selectedEncoding = encoding
                val content = withContext(Dispatchers.IO) { txtParser.parse(book.filePath) }
                val chapterTitles = content.chapters.map { it.title }
                val chapterCount = chapterTitles.size
                require(chapterCount > 0) { context.getString(R.string.error_txt_no_chapters) }
                val restoredChapter = state.currentChapterIndex.coerceIn(0, chapterCount - 1)
                val pageFraction = if (state.totalPages > 0) {
                    state.currentPageIndex.toFloat() / state.totalPages
                } else {
                    state.pendingPageFraction
                }.coerceIn(0f, 0.9999f)

                dataStoreManager.saveTxtEncoding(bookId, encoding.storageValue)
                preloadCache.clear()
                pageLayoutEngine.invalidateAll()
                _uiState.value = _uiState.value.copy(
                    chapterCount = chapterCount,
                    chapterTitles = chapterTitles,
                    tocEntries = content.tocEntries.ifEmpty {
                        chapterTitles.mapIndexed { index, title ->
                            com.huangder.lumibooks.util.parser.TocEntry(title, 1, index)
                        }
                    },
                    currentChapterIndex = restoredChapter,
                    currentPageIndex = 0,
                    totalPages = 0,
                    rightPageIndex = null,
                    rightChapterIndex = null,
                    pendingPageFraction = pageFraction,
                    pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
                    pendingReaderPosition = null,
                    txtEncoding = encoding,
                    txtActiveCharsetName = txtParser.activeCharsetName,
                    isTxtEncodingChanging = false,
                    contentRevision = _uiState.value.contentRevision + 1
                )
                loadChapterContent()
                preloadAdjacentChapters()
                saveProgress()
            } catch (error: Exception) {
                txtParser.selectedEncoding = previousEncoding
                runCatching { withContext(Dispatchers.IO) { txtParser.parse(book.filePath) } }
                _uiState.value = _uiState.value.copy(
                    isTxtEncodingChanging = false,
                    pageReady = true,
                    error = error.message
                )
            }
        }
    }

    fun saveRenderMode(mode: EpubRenderMode) {
        val state = _uiState.value
        val format = state.book?.format?.name
        if (format != "EPUB" && format != "MOBI") return
        if (state.renderMode == mode) return
        val chapterProgression = if (state.totalPages > 0) {
            state.currentPageIndex.toFloat() / state.totalPages
        } else {
            state.pendingPageFraction
        }.coerceIn(0f, 1f)
        saveProgress()
        val nextLayout = readerLayoutTargetFor(format, mode)
        val nextSuiteId = ReaderThemeSuites.resolveActiveId(
            suites = state.readerThemeSuites,
            requestedId = state.activeThemeSuiteIdFor(nextLayout),
            layout = nextLayout
        )
        val nextSuite = state.readerThemeSuites
            .firstOrNull { it.id == nextSuiteId }
        val switched = state.copy(
            renderMode = mode,
            activeReaderThemeSuiteId = nextSuiteId,
            currentPageIndex = 0,
            totalPages = 0,
            rightPageIndex = null,
            rightChapterIndex = null,
            pageReady = false,
            isEpubChapterHandoffInProgress = false,
            isLoading = state.isLoading,
            pendingPageFraction = chapterProgression,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            epubLocatorJson = null
        )
        // Each layout keeps its own typography/background; mirror the newly
        // active bucket into the flat reader fields right away.
        _uiState.value = nextSuite
            ?.let { switched.withReaderThemeSettings(it.settingsFor(nextLayout)) }
            ?: switched
        viewModelScope.launch {
            runCatching {
                dataStoreManager.saveRenderMode(bookId, mode)
                if (mode == EpubRenderMode.BOOK_LAYOUT && renderSession == null) {
                    withContext(Dispatchers.IO) { getRenderSession() }
                }
            }.onFailure { error ->
                android.util.Log.e("ReaderViewModel", "Failed to change render mode", error)
                DiagnosticLoggerRegistry.logger?.log(
                    category = "reader",
                    event = "render_mode_change_failed",
                    level = DiagnosticLevel.ERROR,
                    throwable = error
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pageReady = true,
                    error = error.localizedMessage ?: "EPUB ????????"
                )
            }
        }
    }

    fun fallbackFromUnsupportedEpubWebView() {
        val state = _uiState.value
        val format = state.book?.format?.name
        if ((format != "EPUB" && format != "MOBI") || state.renderMode != EpubRenderMode.BOOK_LAYOUT) return
        val chapterProgression = if (state.totalPages > 0) {
            state.currentPageIndex.toFloat() / state.totalPages
        } else {
            state.pendingPageFraction
        }.coerceIn(0f, 1f)
        _uiState.value = state.copy(
            renderMode = EpubRenderMode.READER_LAYOUT,
            currentPageIndex = 0,
            totalPages = 0,
            pageReady = false,
            isEpubChapterHandoffInProgress = false,
            isLoading = true,
            pendingPageFraction = chapterProgression,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            epubLocatorJson = null
        )
    }

    fun onEpubPageReady(pageIndex: Int, pageCount: Int, locatorJson: String?) {
        val previous = _uiState.value
        _uiState.value = previous.copy(
            currentPageIndex = pageIndex.coerceAtLeast(0),
            totalPages = pageCount.coerceAtLeast(1),
            pageReady = true,
            isEpubChapterHandoffInProgress = false,
            isLoading = false,
            // 没有新锚点时不要沿用旧锚点，否则退出时会把上一页的位置存进进度。
            epubLocatorJson = retainedEpubLocator(
                previous = previous.epubLocatorJson,
                incoming = locatorJson,
                chapterChanged = false,
                pageChanged = previous.currentPageIndex != pageIndex.coerceAtLeast(0)
            ),
            pendingPageFraction = 0f,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        notifyTtsPageVisible(_uiState.value.currentChapterIndex, pageIndex)
        saveProgress()
    }

    fun onEpubPageCommitted(
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
        locatorJson: String?
    ) {
        val previousState = _uiState.value
        val chapterChanged = previousState.currentChapterIndex != chapterIndex
        val normalizedPageIndex = pageIndex.coerceAtLeast(0)
        _uiState.value = previousState.copy(
            currentChapterIndex = chapterIndex,
            currentPageIndex = normalizedPageIndex,
            totalPages = pageCount.coerceAtLeast(1),
            pageReady = true,
            isEpubChapterHandoffInProgress = false,
            isLoading = false,
            epubLocatorJson = retainedEpubLocator(
                previous = previousState.epubLocatorJson,
                incoming = locatorJson,
                chapterChanged = chapterChanged,
                pageChanged = previousState.currentPageIndex != normalizedPageIndex
            ),
            pendingPageFraction = 0f,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        notifyTtsPageVisible(chapterIndex, pageIndex)
        if (chapterChanged) preloadAdjacentChapters()
        saveProgress()
    }

    fun onEpubChapterTurn(direction: Int) {
        val state = _uiState.value
        val target = epubChapterTurnTarget(
            currentChapterIndex = state.currentChapterIndex,
            chapterCount = state.chapterCount,
            direction = direction
        ) ?: return
        _uiState.value = state.copy(
            currentChapterIndex = target.chapterIndex,
            currentPageIndex = 0,
            totalPages = 0,
            pageReady = false,
            isEpubChapterHandoffInProgress = true,
            isLoading = true,
            epubLocatorJson = null,
            pendingPageFraction = target.chapterFraction,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        preloadAdjacentChapters()
    }

    fun saveOptimizeLayout(enabled: Boolean) {
        // 开启"优化排版"时，自动关闭"使用书籍CSS"
        val newUseEpubCss = if (enabled) false else _uiState.value.useEpubCss
        _uiState.value = _uiState.value.copy(optimizeLayout = enabled, useEpubCss = newUseEpubCss)
        parser?.useEpubCss = newUseEpubCss
        viewModelScope.launch {
            dataStoreManager.saveOptimizeLayout(bookId, enabled)
            if (enabled) dataStoreManager.saveUseEpubCss(bookId, false)
            parser?.clearHtmlCache()
            preloadCache.clear()  // 清空 ViewModel 预加载缓存
            loadChapterContent()
        }
    }

    fun saveUseEpubCss(enabled: Boolean) {
        // 开启"使用书籍CSS"时，自动关闭"优化排版"
        val newOptimize = if (enabled) false else _uiState.value.optimizeLayout
        _uiState.value = _uiState.value.copy(useEpubCss = enabled, optimizeLayout = newOptimize)
        parser?.useEpubCss = enabled
        viewModelScope.launch {
            dataStoreManager.saveUseEpubCss(bookId, enabled)
            if (enabled) dataStoreManager.saveOptimizeLayout(bookId, false)
            parser?.clearHtmlCache()
            preloadCache.clear()
            loadChapterContent()
        }
    }

    fun saveChineseMode(mode: String) {
        _uiState.value = _uiState.value.copy(chineseMode = mode)
        viewModelScope.launch {
            dataStoreManager.saveChineseMode(mode)
            // 简繁模式变更需要重新加载当前页（清缓存）
            loadChapterContent()
        }
    }

    fun savePageTransition(mode: String) {
        if (_uiState.value.eInkModeEnabled) return
        val normalizedMode = ReaderPageTransition.normalizeKey(mode)
        val state = _uiState.value
        val crossesContinuousBoundary =
            (state.pageTransition == "continuous") != (normalizedMode == "continuous")
        val chapterFraction = if (state.totalPages > 0) {
            state.currentPageIndex.toFloat().div(state.totalPages).coerceIn(0f, 0.9999f)
        } else {
            0f
        }
        _uiState.value = if (crossesContinuousBoundary) {
            state.copy(
                pageTransition = normalizedMode,
                currentPageIndex = 0,
                totalPages = 0,
                pendingPageFraction = chapterFraction,
                pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
                pendingReaderPosition = null,
                pageReady = false
            )
        } else {
            state.copy(pageTransition = normalizedMode)
        }
        viewModelScope.launch {
            dataStoreManager.savePageTransition(normalizedMode)
        }
    }

    fun saveReaderDisplayMode(mode: String) {
        if (_uiState.value.eInkModeEnabled) return
        _uiState.value = _uiState.value.copy(readerDisplayMode = mode)
        viewModelScope.launch {
            dataStoreManager.saveDisplayMode(mode)
        }
    }

    fun saveReaderWritingMode(mode: ReaderWritingMode) {
        if (!_uiState.value.useNewEngine) return
        _uiState.value = _uiState.value.copy(readerWritingMode = mode)
        viewModelScope.launch {
            dataStoreManager.saveReaderWritingMode(bookId, mode)
        }
    }

    fun togglePdfPageMode() {
        if (_uiState.value.eInkModeEnabled) return
        val nextMode = PdfPageMode.fromKey(_uiState.value.pdfPageMode).next().key
        _uiState.value = _uiState.value.copy(pdfPageMode = nextMode)
        viewModelScope.launch { dataStoreManager.savePdfPageMode(nextMode) }
    }

    /** CBZ 翻页方向：按书切换并记住；未设置过时交给 ComicInfo.xml 决定。 */
    fun toggleCbzReadingDirection() {
        val book = _uiState.value.book ?: return
        if (book.format.name != "CBZ") return
        val next = com.huangder.lumibooks.domain.model.CbzReadingDirection
            .fromKey(_uiState.value.cbzReadingDirection)
            ?.next()
            ?: com.huangder.lumibooks.domain.model.CbzReadingDirection.RIGHT_TO_LEFT
        _uiState.value = _uiState.value.copy(cbzReadingDirection = next.key)
        viewModelScope.launch { dataStoreManager.saveCbzReadingDirection(book.id, next.key) }
    }

    /** 漫画双页对开跟随全局阅读设置，从阅读器内直接切换。 */
    fun setTwoPageSpreadEnabled(enabled: Boolean) {
        if (_uiState.value.twoPageSpreadEnabled == enabled) return
        _uiState.value = _uiState.value.copy(twoPageSpreadEnabled = enabled)
        viewModelScope.launch { dataStoreManager.saveTwoPageSpreadEnabled(enabled) }
    }

    /** Applies a built-in or user TXT TOC rule and keeps the reader near its old byte anchor. */
    fun saveTxtTocRuleSelection(ruleId: String?) {
        val txtParser = parser as? TxtParser ?: return
        val state = _uiState.value
        val book = state.book ?: return
        if (state.isTxtTocChanging) return
        viewModelScope.launch {
            val previousId = state.txtTocRuleId
            val previousRule = txtParser.selectedTocRule
            val oldRange = txtParser.getChapterByteRange(state.currentChapterIndex)
            val oldFraction = if (state.totalPages > 0) {
                state.currentPageIndex.toFloat() / state.totalPages.toFloat()
            } else state.pendingPageFraction
            val oldAnchor = oldRange?.let { (start, end) ->
                start + ((end - start).coerceAtLeast(0L) * oldFraction.coerceIn(0f, 1f)).toLong()
            }
            val oldBookmarks = readingRepository.getBookmarksByBookId(book.id).first()
            val oldNotes = readingRepository.getNotesByBookId(book.id).first()
            val bookmarkAnchors = oldBookmarks.mapNotNull { bookmark ->
                val offset = bookmark.characterOffset ?: 0
                txtParser.characterOffsetToByte(bookmark.chapterIndex, offset)?.let { bookmark to it }
            }
            val noteAnchors = oldNotes.mapNotNull { note ->
                val start = txtParser.characterOffsetToByte(note.chapterIndex, note.startPosition) ?: return@mapNotNull null
                val end = txtParser.characterOffsetToByte(note.chapterIndex, note.endPosition, endBias = true) ?: start
                note to (start to end.coerceAtLeast(start))
            }
            val sourceFile = File(book.filePath).takeIf { it.isFile }
            val sourceLengthBefore = sourceFile?.length()
            val sourceModifiedBefore = sourceFile?.lastModified()
            if (sourceFile != null &&
                (sourceFile.length() != sourceLengthBefore || sourceFile.lastModified() != sourceModifiedBefore)
            ) {
                error("TXT source changed while preparing annotation migration")
            }
            _uiState.value = state.copy(isTxtTocChanging = true, pageReady = false)
            try {
                dataStoreManager.saveTxtTocRuleSelection(book.id, ruleId)
                val resolvedRule = dataStoreManager.resolveTxtTocRule(book.id)
                val effectiveRuleId = if (
                    !ruleId.isNullOrBlank() && ruleId != "auto" && resolvedRule == null
                ) {
                    dataStoreManager.saveTxtTocRuleSelection(book.id, null)
                    "auto"
                } else {
                    ruleId?.takeIf { it.isNotBlank() } ?: "auto"
                }
                txtParser.selectedTocRule = resolvedRule
                val content = withContext(Dispatchers.IO) { txtParser.parse(book.filePath) }
                if (sourceFile != null &&
                    (sourceFile.length() != sourceLengthBefore || sourceFile.lastModified() != sourceModifiedBefore)
                ) {
                    error("TXT source changed while migrating annotations")
                }
                require(content.chapters.isNotEmpty()) { context.getString(R.string.error_txt_no_chapters) }
                val bookmarkUpdates = bookmarkAnchors.mapNotNull { (bookmark, anchor) ->
                    txtParser.byteToCharacterPosition(anchor)?.let { (chapter, offset) ->
                        bookmark.copy(
                            chapterIndex = chapter,
                            position = bookmarkPositionForCharacterOffset(offset)
                        )
                    }
                }
                val noteUpdates = mutableListOf<Note>()
                val noteInserts = mutableListOf<Note>()
                noteAnchors.forEach { (note, range) ->
                    val startPosition = txtParser.byteToCharacterPosition(range.first) ?: return@forEach
                    val endPosition = txtParser.byteToCharacterPosition(range.second) ?: startPosition
                    if (startPosition.first == endPosition.first) {
                        noteUpdates += note.copy(
                            chapterIndex = startPosition.first,
                            startPosition = startPosition.second,
                            endPosition = endPosition.second.coerceAtLeast(startPosition.second)
                        )
                    } else {
                        var firstFragment = true
                        for (chapter in startPosition.first..endPosition.first) {
                            val chapterRange = txtParser.getChapterByteRange(chapter) ?: continue
                            val fragmentStart = if (chapter == startPosition.first) range.first else chapterRange.first
                            val fragmentEnd = if (chapter == endPosition.first) range.second else chapterRange.second
                            if (fragmentEnd <= fragmentStart) continue
                            val mappedStart = txtParser.byteToCharacterPosition(fragmentStart) ?: continue
                            val mappedEnd = txtParser.byteToCharacterPosition(fragmentEnd) ?: mappedStart
                            val relativeStart = (((fragmentStart - range.first).toDouble() / (range.second - range.first).coerceAtLeast(1L)) * note.selectedText.length)
                                .toInt().coerceIn(0, note.selectedText.length)
                            val relativeEnd = (((fragmentEnd - range.first).toDouble() / (range.second - range.first).coerceAtLeast(1L)) * note.selectedText.length)
                                .toInt().coerceIn(relativeStart, note.selectedText.length)
                            val fragment = note.copy(
                                id = if (firstFragment) note.id else 0L,
                                chapterIndex = chapter,
                                startPosition = mappedStart.second,
                                endPosition = mappedEnd.second.coerceAtLeast(mappedStart.second),
                                selectedText = note.selectedText.substring(relativeStart, relativeEnd)
                            )
                            if (firstFragment) noteUpdates += fragment else noteInserts += fragment
                            firstFragment = false
                        }
                    }
                }
                readingRepository.applyTxtResegmentation(bookmarkUpdates, noteUpdates, noteInserts)
                val targetChapter = oldAnchor?.let { anchor ->
                    (0 until txtParser.getChapterCount()).firstOrNull { index ->
                        val range = txtParser.getChapterByteRange(index) ?: return@firstOrNull false
                        anchor >= range.first && anchor < range.second
                    }
                } ?: state.currentChapterIndex.coerceIn(0, content.chapters.lastIndex)
                preloadCache.clear()
                pageLayoutEngine.invalidateAll()
                _uiState.value = _uiState.value.copy(
                    chapterCount = content.chapters.size,
                    chapterTitles = content.chapters.map { it.title },
                    tocEntries = content.tocEntries,
                    currentChapterIndex = targetChapter,
                    currentPageIndex = 0,
                    totalPages = 0,
                    pendingPageFraction = oldFraction.coerceIn(0f, 0.9999f),
                    pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
                    pendingReaderPosition = null,
                    txtTocRuleId = effectiveRuleId,
                    txtTocRuleName = resolvedRule?.name,
                    txtTocDiagnostics = txtParser.lastTocDiagnostics,
                    isTxtTocChanging = false,
                    contentRevision = _uiState.value.contentRevision + 1
                )
                loadChapterContent()
                preloadAdjacentChapters()
                saveProgress()
            } catch (error: Exception) {
                dataStoreManager.saveTxtTocRuleSelection(book.id, previousId)
                txtParser.selectedTocRule = previousRule
                runCatching { withContext(Dispatchers.IO) { txtParser.parse(book.filePath) } }
                _uiState.value = _uiState.value.copy(
                    isTxtTocChanging = false,
                    pageReady = true,
                    error = error.message
                )
            }
        }
    }

    fun saveTxtTocRule(rule: TxtTocRule) {
        viewModelScope.launch {
            val existing = dataStoreManager.txtTocCustomRules().first()
            val updated = (existing.filterNot { it.id == rule.id } + rule.copy(origin = com.huangder.lumibooks.util.parser.TxtTocRuleOrigin.CUSTOM))
                .mapIndexed { index, item -> item.copy(order = index) }
            dataStoreManager.saveTxtTocCustomRules(updated)
            _uiState.value = _uiState.value.copy(txtTocCustomRules = updated)
            saveTxtTocRuleSelection(rule.id)
        }
    }

    fun saveShowReaderChapterProgress(show: Boolean) {
        _uiState.value = _uiState.value.copy(showReaderChapterProgress = show)
        viewModelScope.launch { dataStoreManager.saveShowReaderChapterProgress(show) }
    }

    fun saveShowReaderPageNumber(show: Boolean) {
        _uiState.value = _uiState.value.copy(showReaderPageNumber = show)
        viewModelScope.launch { dataStoreManager.saveShowReaderPageNumber(show) }
    }

    fun saveShowReaderBattery(show: Boolean) {
        _uiState.value = _uiState.value.copy(showReaderBattery = show)
        viewModelScope.launch { dataStoreManager.saveShowReaderBattery(show) }
    }

    fun saveVolumeKeyPageTurnEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(volumeKeyPageTurnEnabled = enabled)
        viewModelScope.launch { dataStoreManager.saveVolumeKeyPageTurnEnabled(enabled) }
    }

    fun saveBionicReadingEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bionicReadingEnabled = enabled)
        viewModelScope.launch { dataStoreManager.saveBionicReadingEnabled(enabled) }
    }

    fun saveComicMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(comicModeEnabled = enabled)
        viewModelScope.launch { dataStoreManager.saveComicMode(enabled) }
    }

    fun saveSelectionMenuItems(items: Map<String, Boolean>) {
        _uiState.value = _uiState.value.copy(selectionMenuItems = items)
        viewModelScope.launch { dataStoreManager.saveSelectionMenuItems(items) }
    }

    fun replaceTxtText(
        searchText: String,
        replaceWith: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val book = _uiState.value.book
        val txtParser = parser as? TxtParser
        if (book?.format?.name != "TXT" || txtParser == null || searchText.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    txtParser.rewriteWithOperations(
                        listOf(
                            TxtReplaceText(
                                chapterIndex = null,
                                query = searchText,
                                replacement = replaceWith,
                                ignoreCase = false
                            )
                        )
                    )
                }
                if (result.success && result.changedChapterCount > 0) {
                    preloadCache.clear()
                    pageLayoutEngine.invalidateAll()
                    _uiState.value = _uiState.value.copy(
                        txtActiveCharsetName = txtParser.activeCharsetName,
                        contentRevision = _uiState.value.contentRevision + 1,
                        error = result.errorMessage
                    )
                    onResult(true)
                } else {
                    result.errorMessage?.let { message ->
                        _uiState.value = _uiState.value.copy(error = message)
                    }
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
                onResult(false)
            }
        }
    }

    /** 仅替换选中处：按章节内字符区间精确改写，不影响其他同名文本。 */
    fun replaceTxtRange(
        chapterIndex: Int,
        start: Int,
        endExclusive: Int,
        replaceWith: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val book = _uiState.value.book
        val txtParser = parser as? TxtParser
        if (book?.format?.name != "TXT" || txtParser == null ||
            start < 0 || endExclusive <= start
        ) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    txtParser.rewriteWithOperations(
                        listOf(
                            TxtReplaceRange(
                                chapterIndex = chapterIndex,
                                start = start,
                                endExclusive = endExclusive,
                                replacement = replaceWith
                            )
                        )
                    )
                }
                if (result.success && result.changedChapterCount > 0) {
                    preloadCache.clear()
                    pageLayoutEngine.invalidateAll()
                    _uiState.value = _uiState.value.copy(
                        txtActiveCharsetName = txtParser.activeCharsetName,
                        contentRevision = _uiState.value.contentRevision + 1,
                        error = result.errorMessage
                    )
                    onResult(true)
                } else {
                    result.errorMessage?.let { message ->
                        _uiState.value = _uiState.value.copy(error = message)
                    }
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
                onResult(false)
            }
        }
    }

    fun saveScreenSleepTimeoutSeconds(seconds: Int) {
        if (seconds !in DataStoreManager.SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS) return
        _uiState.value = _uiState.value.copy(screenSleepTimeoutSeconds = seconds)
        viewModelScope.launch { dataStoreManager.saveScreenSleepTimeoutSeconds(seconds) }
    }

    fun saveReaderEdgeTapMode(mode: ReaderEdgeTapMode) {
        _uiState.value = _uiState.value.copy(readerEdgeTapMode = mode)
        viewModelScope.launch { dataStoreManager.saveReaderEdgeTapMode(mode) }
    }

    fun saveReaderCornerContent(corner: ReaderPageCorner, content: ReaderCornerContent) {
        _uiState.value = _uiState.value.withReaderCornerContent(corner, content)
        viewModelScope.launch { dataStoreManager.saveReaderCornerContent(corner, content) }
    }

    /** 四角信息区（页眉/页脚）边距；空字段写回"跟随正文 / 旧版默认位置"。 */
    fun saveReaderCornerMargins(margins: ReaderCornerMargins) {
        _uiState.value = _uiState.value.copy(readerCornerMargins = margins)
        viewModelScope.launch { dataStoreManager.saveReaderCornerMargins(margins) }
    }

    fun saveParagraphSpacing(value: Float) {
        parser?.paragraphSpacingDp = value
        parser?.clearHtmlCache()  // 同步清缓存，确保 configure() 重新分页时拿到新内容
        updateCurrentThemeSettings { copy(paragraphSpacing = value) }
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            loadChapterContent()
        }
    }

    fun saveFirstLineIndent(value: Float) {
        parser?.firstLineIndentChars = value
        parser?.clearHtmlCache()  // 同步清缓存
        updateCurrentThemeSettings { copy(firstLineIndent = value) }
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            loadChapterContent()
        }
    }

    fun resetAdvancedReaderSettings() {
        parser?.paragraphSpacingDp = 2f
        parser?.firstLineIndentChars = 2f
        parser?.clearHtmlCache()
        val resetSettings = _uiState.value.currentThemeSettings(
            ReaderLayoutTarget.READER_LAYOUT
        ).copy(
            textColor = null,
            fontType = "system",
            lineHeight = 1.5f,
            letterSpacing = 0f,
            textAlignment = ReaderTextAlignment.NATURAL,
            paragraphSpacing = 2f,
            firstLineIndent = 2f,
            marginLeft = 38f,
            marginRight = 38f,
            marginTop = 64f,
            marginBottom = 64f
        )
        _uiState.value = _uiState.value
            .withUpdatedActiveThemeSettings(ReaderLayoutTarget.READER_LAYOUT) { resetSettings }
            .withReaderThemeSettings(resetSettings)
            .copy(
                lineHeight = 1.5f,
                letterSpacing = 0f,
                textAlignment = ReaderTextAlignment.NATURAL,
                fontType = "system",
                marginLeftDp = 38f,
                marginRightDp = 38f,
                marginTopDp = 64f,
                marginBottomDp = 64f,
                paragraphSpacing = 2f,
                firstLineIndent = 2f,
                readerTextColor = null,
                showReaderChapterProgress = true,
                showReaderPageNumber = true,
                showReaderBattery = true,
                volumeKeyPageTurnEnabled = false,
                bionicReadingEnabled = false,
                readerEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
                readerTopLeftContent = defaultReaderCornerContent(ReaderPageCorner.TOP_LEFT),
                readerTopRightContent = defaultReaderCornerContent(ReaderPageCorner.TOP_RIGHT),
                readerBottomLeftContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT),
                readerBottomRightContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT),
                readerCornerMargins = ReaderCornerMargins()
            )
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            dataStoreManager.resetAdvancedReaderSettings(
                _uiState.value.activeThemeSuiteIdFor(ReaderLayoutTarget.READER_LAYOUT)
            )
            loadChapterContent()
        }
    }

    /** 从 URI 导入字体文件到内部存储，注册到自定义字体列表，返回新建的 CustomFontPreset */
    fun resetBookLayoutReaderSettings() {
        // Only reachable while the book layout sheet is open, so every save*
        // call below lands in the book layout bucket of the active suite.
        saveFontType("system")
        saveTextAlignment(ReaderTextAlignment.NATURAL)
        saveMarginLeft(38f)
        saveMarginRight(38f)
        saveMarginTop(64f)
        saveMarginBottom(64f)
        saveReaderTextColor(null)
        saveVolumeKeyPageTurnEnabled(false)
        saveBionicReadingEnabled(false)
        saveScreenSleepTimeoutSeconds(DataStoreManager.DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS)
        saveReaderEdgeTapMode(ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT)
        ReaderPageCorner.entries.forEach { corner ->
            saveReaderCornerContent(corner, defaultReaderCornerContent(corner))
        }
        saveReaderCornerMargins(ReaderCornerMargins())
    }

    suspend fun importFont(
        context: android.content.Context,
        uri: android.net.Uri,
        displayName: String = ""
    ): com.huangder.lumibooks.domain.model.CustomFontPreset? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val trimmedName = displayName.trim()
                val nameEnd = trimmedName.offsetByCodePoints(
                    0,
                    trimmedName.codePointCount(0, trimmedName.length).coerceAtMost(6)
                )
                val safeName = trimmedName.substring(0, nameEnd)
                val id = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                val fontDir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                val target = java.io.File(fontDir, "custom_$id.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists() && target.length() > 0) {
                    val preset = com.huangder.lumibooks.domain.model.CustomFontPreset(
                        id = id,
                        path = target.absolutePath,
                        name = safeName
                    )
                    val updated = _uiState.value.customFonts + preset
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(customFonts = updated, customFontPath = preset.path)
                    }
                    dataStoreManager.saveCustomFonts(updated)
                    preset
                } else null
            } catch (_: Exception) { null }
        }
    }

    /** 用户离开阅读页时调用（DisposableEffect.onDispose） */
    fun saveAndPause() {
        continuousProgressJob?.cancel()
        saveProgress()
        saveReadingSession()
        webdavAutoSyncScheduler.onReaderExited()
    }

    private suspend fun loadBook(preferences: ReaderPreferencesSnapshot, book: Book?) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            largeTxtIndexJob?.cancel()
            largeTxtIndexJob = null
            try {
                if (book != null) {
                    val activeParser = BookParserFactory.createParser(book.format, context)
                    parser = activeParser
                    largeTxtFastRanges = emptyList()

                    val isEpub = book.format.name == "EPUB"
                    val supportsBookLayout = isEpub || book.format.name == "MOBI"
                    val isTxt = book.format.name == "TXT"
                    val renderMode = if (supportsBookLayout) preferences.renderMode
                    else EpubRenderMode.READER_LAYOUT
                    val showEpubLayoutHint = ReaderFirstOpenHintPolicy.shouldShow(
                        isSupportedFormat = isEpub,
                        wasShownForBook = preferences.epubLayoutHintShown,
                        acknowledgementCount = preferences.readerFirstOpenHintAcknowledgementCount,
                        disabled = preferences.readerFirstOpenHintsDisabled
                    )
                    val showMobiLayoutHint = ReaderFirstOpenHintPolicy.shouldShow(
                        isSupportedFormat = book.format.name == "MOBI",
                        wasShownForBook = preferences.mobiLayoutHintShown,
                        acknowledgementCount = preferences.readerFirstOpenHintAcknowledgementCount,
                        disabled = preferences.readerFirstOpenHintsDisabled
                    )
                    val txtEncoding = if (isTxt) {
                        TxtEncoding.fromStorage(preferences.txtEncoding)
                    } else {
                        TxtEncoding.AUTO
                    }
                    val txtTocRule = if (isTxt) dataStoreManager.resolveTxtTocRule(book.id) else null
                    val txtTocCustomRules = if (isTxt) dataStoreManager.txtTocCustomRules().first() else emptyList()
                    val txtTocRuleId = if (
                        isTxt && preferences.txtTocRuleId != "auto" && txtTocRule == null
                    ) "auto" else preferences.txtTocRuleId
                    if (isTxt && txtTocRuleId == "auto" && preferences.txtTocRuleId != "auto") {
                        dataStoreManager.saveTxtTocRuleSelection(book.id, null)
                    }
                    val showTxtEncodingHint = ReaderFirstOpenHintPolicy.shouldShow(
                        isSupportedFormat = isTxt,
                        wasShownForBook = preferences.txtEncodingHintShown,
                        acknowledgementCount = preferences.readerFirstOpenHintAcknowledgementCount,
                        disabled = preferences.readerFirstOpenHintsDisabled
                    )

                    val optimize = preferences.optimizeLayout
                    val useEpubCss = preferences.useEpubCss
                    val readerWritingMode = preferences.readerWritingMode
                    val chineseMode = preferences.chineseMode
                    val eInkModeEnabled = preferences.eInkModeEnabled
                    val twoPageSpreadEnabled = preferences.twoPageSpreadEnabled
                    val pageTransition = if (eInkModeEnabled) "none" else preferences.pageTransition
                    val readerDisplayMode = preferences.readerDisplayMode
                    val paragraphSpacing = preferences.paragraphSpacing
                    val firstLineIndent = preferences.firstLineIndent
                    val textAlignment = preferences.textAlignment
                    val pdfPageMode = if (eInkModeEnabled) "horizontal" else preferences.pdfPageMode
                    val serializedReaderPosition = book.locatorJson
                        ?.let(ReaderPositionLocator::fromJson)
                        ?.takeIf { renderMode == EpubRenderMode.READER_LAYOUT }

                    // 应用段间距和首行缩进到 parser
                    activeParser.paragraphSpacingDp = paragraphSpacing
                    activeParser.firstLineIndentChars = firstLineIndent
                    activeParser.useEpubCss = useEpubCss
                    activeParser.preserveEpubBackground = preferences.preserveEpubBackground
                    (activeParser as? TxtParser)?.selectedEncoding = txtEncoding
                    (activeParser as? TxtParser)?.selectedTocRule = txtTocRule

                    var fastTxtOpen: TxtOpenResult? = null
                    val content = ReaderOpenPerformance.traceStageSuspend(
                        bookId,
                        ReaderOpenStage.METADATA_PARSE
                    ) {
                        withContext(Dispatchers.IO) {
                            val useFastTxtOpen = activeParser is TxtParser && shouldUseFastTxtOpen(
                                fileSizeBytes = BookFileAccess.size(context, book.filePath),
                                largeFileThresholdBytes = TxtParser.LARGE_FILE_THRESHOLD_BYTES,
                                hasPersistedReaderPosition = serializedReaderPosition != null,
                                readingProgress = book.readingProgress
                            )
                            try {
                                if (useFastTxtOpen) {
                                    activeParser.parseFastOpen(book.filePath).also { fastTxtOpen = it }.content
                                } else {
                                    activeParser.parse(book.filePath)
                                }
                            } catch (firstError: Throwable) {
                                if (firstError is CancellationException) throw firstError
                                // A malformed or stale TXT TOC rule must not prevent the
                                // readable body from opening. Retry the same source as plain
                                // fallback chunks while keeping the user's rule unchanged.
                                if (activeParser is TxtParser) {
                                    runCatching {
                                        activeParser.parseWithoutToc(book.filePath)
                                    }.getOrElse { throw firstError }
                                } else {
                                    throw firstError
                                }
                            }
                        }
                    }

                    // 解析出更准确的作者时，静默回填数据库并用新值更新 UI
                    val parsedAuthor = content.author
                    val unknownAuthorTokens = setOf("未知作者", "著者不明", "unknown author", "unknown", "作者不详", "作者不詳", "")
                    val storedIsUnknown = book.author.trim().lowercase() in unknownAuthorTokens
                    val parsedIsReal = parsedAuthor.isNotBlank() && parsedAuthor.trim().lowercase() !in unknownAuthorTokens
                    var displayBook = book
                    if (storedIsUnknown && parsedIsReal) {
                        displayBook = displayBook.copy(author = parsedAuthor)
                    }
                    val parsedCoverPath = content.coverPath?.takeIf { it.isNotBlank() }
                    if (displayBook.coverPath.isNullOrBlank() && parsedCoverPath != null) {
                        displayBook = displayBook.copy(coverPath = parsedCoverPath)
                    }
                    if (displayBook != book) {
                        bookRepository.updateBookMetadata(displayBook)
                    }

                    val chapterCount = content.chapters.size
                    require(chapterCount > 0) { context.getString(R.string.error_book_no_content) }
                    ReaderOpenPerformance.beginStage(bookId, ReaderOpenStage.PAGINATION)
                    val chapterTitles = content.chapters.map { it.title }
                    val tocEntries = content.tocEntries.ifEmpty {
                        content.chapters.map { com.huangder.lumibooks.util.parser.TocEntry(it.title, 1, it.index) }
                    }
                    // CBZ 的目录是"话"分组，位图页阅读器用它给缩略图目录加分组标题。
                    val comicChapterEntries =
                        if (book.format.name == "CBZ") content.tocEntries else emptyList()
                    val storedReaderPosition = serializedReaderPosition?.let { saved ->
                        val byteOffset = saved.sourceByteOffset
                        if (activeParser is TxtParser && byteOffset != null) {
                            activeParser.byteToCharacterPosition(byteOffset)?.let { (chapter, offset) ->
                                saved.copy(
                                    chapterIndex = chapter,
                                    chapterFraction = txtChapterFractionForByte(
                                        activeParser.getChapterByteRange(chapter),
                                        byteOffset,
                                        saved.chapterFraction
                                    ),
                                    characterOffset = offset
                                )
                            } ?: saved
                        } else {
                            saved
                        }
                    }
                    val fallbackRestorePoint = bookLayoutRestorePoint(
                        readingProgress = book.readingProgress,
                        chapterCount = chapterCount
                    )
                    val startChapter = storedReaderPosition?.chapterIndex
                        ?.coerceIn(0, chapterCount - 1)
                        ?: fallbackRestorePoint.chapterIndex
                    val pageFraction = storedReaderPosition?.chapterFraction
                        ?: fallbackRestorePoint.chapterFraction
                    val isContinuousReader = readerWritingMode.usesContinuousScroll(
                        pageTransition,
                        eInkModeEnabled
                    )
                    val isRasterPageFormat = book.format.isRasterPageFormat
                    val cbzReadingDirection = if (book.format.name == "CBZ") {
                        com.huangder.lumibooks.domain.model.CbzReadingDirection.resolve(
                            storedKey = preferences.cbzReadingDirection,
                            prefersRightToLeft = (activeParser as? CbzParser)?.prefersRightToLeft == true
                        )
                    } else {
                        com.huangder.lumibooks.domain.model.CbzReadingDirection.LEFT_TO_RIGHT
                    }
                    _uiState.value = _uiState.value.copy(
                        book = displayBook,
                        chapterCount = chapterCount,
                        chapterTitles = chapterTitles,
                        tocEntries = tocEntries,
                        comicChapterEntries = comicChapterEntries,
                        currentChapterIndex = startChapter,
                        pendingPageFraction = pageFraction,
                        pendingPageFractionSemantics = if (
                            storedReaderPosition == null && !isContinuousReader
                        ) {
                            ReaderPageFractionSemantics.INCLUSIVE_PAGE_END
                        } else {
                            ReaderPageFractionSemantics.START
                        },
                        pendingReaderPosition = storedReaderPosition,
                        // TXT/EPUB 用新 Canvas 引擎；PDF/CBZ 由位图页阅读器渲染。
                        useNewEngine = !isRasterPageFormat,
                        optimizeLayout = optimize,
                        useEpubCss = useEpubCss,
                        readerWritingMode = readerWritingMode,
                        renderMode = renderMode,
                        showEpubLayoutHint = showEpubLayoutHint,
                        showMobiLayoutHint = showMobiLayoutHint,
                        txtEncoding = txtEncoding,
                        txtTocRuleId = txtTocRuleId,
                        txtTocRuleName = txtTocRule?.name,
                        txtTocCustomRules = txtTocCustomRules,
                        txtTocDiagnostics = (activeParser as? TxtParser)?.lastTocDiagnostics.orEmpty(),
                        txtActiveCharsetName = (activeParser as? TxtParser)?.activeCharsetName ?: "UTF-8",
                        txtIndexState = fastTxtOpen?.state ?: TxtIndexState.COMPLETE,
                        txtIndexProgress = fastTxtOpen?.let { 0f },
                        showTxtEncodingHint = showTxtEncodingHint,
                        epubLocatorJson = book.locatorJson.takeIf {
                            renderMode == EpubRenderMode.BOOK_LAYOUT &&
                                ReaderPositionLocator.fromJson(it) == null
                        },
                        chineseMode = chineseMode,
                        pageTransition = pageTransition,
                        readerDisplayMode = readerDisplayMode,
                        paragraphSpacing = paragraphSpacing,
                        firstLineIndent = firstLineIndent,
                        textAlignment = textAlignment,
                        pdfPageMode = pdfPageMode,
                        cbzReadingDirection = cbzReadingDirection.key,
                        eInkModeEnabled = eInkModeEnabled,
                        twoPageSpreadEnabled = twoPageSpreadEnabled,
                        error = null
                    )

                    // 书还没加载时 applyReaderPreferences 判断不出排版模式归属
                    // （format 为空 → 一律按阅读器排版取设置），书籍信息到位后必须再同步一次，
                    // 否则书籍原排版会套用阅读器排版那套背景/边距/字号。
                    applyActiveThemeSettingsForCurrentLayout()

                    if (supportsBookLayout && renderMode == EpubRenderMode.BOOK_LAYOUT) {
                        withContext(Dispatchers.IO) { getRenderSession() }
                    }

                    if (isRasterPageFormat) {
                        // PDF/CBZ 使用独立的位图页阅读器，不生成 Base64 HTML。
                        _uiState.value = _uiState.value.copy(isLoading = false, pageReady = true)
                    }
                    loadBookmarks()
                    loadNotes()
                    if (fastTxtOpen?.state == TxtIndexState.FAST_PARTIAL && activeParser is TxtParser) {
                        startLargeTxtIndexBuild(book, activeParser)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.book_not_found)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
    }

    /** Builds a semantic TOC after a large TXT has already become readable. */
    private fun startLargeTxtIndexBuild(
        book: Book,
        txtParser: TxtParser
    ) {
        largeTxtIndexJob?.cancel()
        largeTxtIndexJob = viewModelScope.launch(Dispatchers.IO) {
            val fastRanges = (0 until txtParser.getChapterCount()).map { index ->
                txtParser.getChapterByteRange(index)
            }
            largeTxtFastRanges = fastRanges
            try {
                val indexKey = txtParser.semanticIndexKey(book.filePath)
                val snapshot = TxtIndexCoordinator.getOrBuild(indexKey) {
                    txtParser.buildSemanticIndexSnapshot(book.filePath)
                }.await()
                if (parser !== txtParser || _uiState.value.book?.id != book.id) return@launch
                combine(ttsController.activeBookId, ttsController.playbackState) { activeBookId, state ->
                    state == TtsPlaybackState.IDLE ||
                        (activeBookId != null && activeBookId != book.id)
                }.first { installAllowed -> installAllowed }
                if (parser !== txtParser || _uiState.value.book?.id != book.id) return@launch
                val current = _uiState.value
                val oldFraction = if (current.totalPages > 0) {
                    (current.currentPageIndex.toFloat() / current.totalPages.toFloat())
                        .coerceIn(0f, 0.9999f)
                } else {
                    current.pendingPageFraction.coerceIn(0f, 0.9999f)
                }
                val oldRange = fastRanges.getOrNull(current.currentChapterIndex)
                val anchor = oldRange?.let { (start, end) ->
                    start + ((end - start).coerceAtLeast(0L) * oldFraction).toLong()
                }
                val isContinuous = current.readerWritingMode.usesContinuousScroll(
                    current.pageTransition,
                    current.eInkModeEnabled
                )
                val semantic = txtParser.installIndexSnapshot(snapshot)
                val mappedPosition = anchor?.let(txtParser::byteToCharacterPosition)
                val targetChapter = mappedPosition?.first
                    ?: current.currentChapterIndex.coerceIn(
                        0,
                        (semantic.chapters.size - 1).coerceAtLeast(0)
                    )
                val targetFraction = anchor?.let { byte ->
                    txtChapterFractionForByte(
                        txtParser.getChapterByteRange(targetChapter),
                        byte,
                        oldFraction
                    )
                } ?: oldFraction
                val pendingLocator = mappedPosition?.let { (_, offset) ->
                    ReaderPositionLocator(
                        chapterIndex = targetChapter,
                        chapterFraction = targetFraction,
                        flow = if (isContinuous) {
                            ReaderPositionFlow.CONTINUOUS
                        } else {
                            ReaderPositionFlow.PAGED
                        },
                        characterOffset = offset,
                        sourceByteOffset = anchor
                    )
                }
                withContext(Dispatchers.Main) {
                    pageLayoutEngine.invalidateAll()
                    _uiState.value = _uiState.value.copy(
                        chapterCount = semantic.chapters.size,
                        chapterTitles = semantic.chapters.map { it.title },
                        tocEntries = semantic.tocEntries.ifEmpty {
                            semantic.chapters.map { chapter ->
                                com.huangder.lumibooks.util.parser.TocEntry(chapter.title, 1, chapter.index)
                            }
                        },
                        currentChapterIndex = targetChapter,
                        currentPageIndex = 0,
                        totalPages = 0,
                        pendingPageFraction = targetFraction,
                        pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
                        pendingReaderPosition = pendingLocator,
                        txtIndexState = TxtIndexState.COMPLETE,
                        txtIndexProgress = 1f,
                        contentRevision = _uiState.value.contentRevision + 1
                    )
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    if (parser === txtParser && _uiState.value.book?.id == book.id) {
                        _uiState.value = _uiState.value.copy(
                            pendingPageFraction = 0f,
                            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
                            pendingReaderPosition = null,
                            txtIndexState = TxtIndexState.FAILED,
                            txtIndexProgress = null
                        )
                    }
                }
            }
        }
    }

    /**
     * 加载当前章节的HTML内容（分页由WebView JS处理）
     * @param startPage 加载完成后跳转到指定页（默认0）
     */
    private fun loadChapterContent(startPage: Int = 0) {
        val state = _uiState.value
        if (state.useNewEngine) return
        _uiState.value = _uiState.value.copy(chapterHtml = "", currentPageIndex = startPage, pageReady = false)
        viewModelScope.launch {
            val html = withContext(Dispatchers.IO) { getChapterHtml(state.currentChapterIndex) }
            android.util.Log.e("PG", "loadChapterContent: chapter=" + state.currentChapterIndex + " html.length=" + html.length)
            DiagnosticLoggerRegistry.logger?.log(
                category = "reader",
                event = "chapter_content_loaded",
                level = DiagnosticLevel.INFO,
                attributes = mapOf("chapter" to state.currentChapterIndex, "contentLength" to html.length)
            )
            _uiState.value = _uiState.value.copy(chapterHtml = html)
            // 🔥 激进预加载：进入章节后立即在后台拉取前后相邻章节到 preloadCache
            eagerPreloadAdjacent(state.currentChapterIndex)
        }
    }

    /**
     * 🔥 激进预加载：进入章节后立即在后台拉取前后相邻章节的HTML到 preloadCache。
     * 不阻塞当前章节渲染，fire-and-forget。
     */
    private fun eagerPreloadAdjacent(chapterIdx: Int) {
        val state = _uiState.value
        val p = parser ?: return
        // Raster page formats decode images on demand; the HTML preload cache does not apply.
        if (state.book?.format?.isRasterPageFormat == true) return
        val isPdf = state.book?.format?.name == "PDF"
        // PDF: 每页都是"章节"，预加载前后5页（页小，渲染快但边界频繁）
        // EPUB/TXT: 预加载前后2章
        val windowSize = if (isPdf) 5 else 2
        val indices = ((chapterIdx - windowSize)..(chapterIdx + windowSize))
            .filter { it != chapterIdx && it in 0 until state.chapterCount && it !in preloadCache }
        if (indices.isEmpty()) return
        android.util.Log.e("PG", "eagerPreload: chapter=$chapterIdx window=$windowSize indices=$indices")
        val optimize = _uiState.value.optimizeLayout
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                indices.forEach { idx ->
                    try {
                        preloadCache[idx] = p.getChapterHtml(idx, optimize)
                        android.util.Log.d("PG", "eagerPreload done: chapter $idx")
                    } catch (_: Exception) {
                        android.util.Log.d("PG", "eagerPreload failed: chapter $idx")
                    }
                }
            }
        }
    }

    /**
     * 翻到下一章（由JS在最后一页继续翻时触发）
     */
    fun nextChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex >= state.chapterCount - 1) return
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = state.currentChapterIndex + 1,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        if (!state.useNewEngine) loadChapterContent()
        saveProgress()
    }

    /**
     * 翻到上一章（由JS在第一页往前翻时触发）
     * @param targetPage 目标页码，默认0（第一页），翻到上一章末尾时传 totalPages
     */
    fun previousChapter(targetPage: Int = 0) {
        val state = _uiState.value
        if (state.currentChapterIndex <= 0) return
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = state.currentChapterIndex - 1,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        if (!state.useNewEngine) loadChapterContent(startPage = targetPage)
        saveProgress()
    }

    /**
     * 跳转到指定章节（由Slider调用）
     */
    fun setChapter(chapterIndex: Int) {
        val state = _uiState.value
        if (chapterIndex == state.currentChapterIndex) return
        val format = state.book?.format?.name
        val isBookLayout = (format == "EPUB" || format == "MOBI") &&
            state.renderMode == EpubRenderMode.BOOK_LAYOUT
        _uiState.value = state.copy(
            currentChapterIndex = chapterIndex,
            currentPageIndex = if (isBookLayout) 0 else state.currentPageIndex,
            totalPages = if (isBookLayout) 0 else state.totalPages,
            pageReady = if (isBookLayout) false else state.pageReady,
            isEpubChapterHandoffInProgress = false,
            isLoading = if (isBookLayout) true else state.isLoading,
            epubLocatorJson = if (isBookLayout) null else state.epubLocatorJson,
            pendingPageFraction = 0f,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        if (!state.useNewEngine) loadChapterContent()
        if (!isBookLayout) saveProgress()
    }

    /**
     * Updates the visible chapter/page without asking the parser to load HTML.
     */
    fun updatePosition(chapterIndex: Int, pageIndex: Int, totalPages: Int) {
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            currentPageIndex = pageIndex,
            totalPages = totalPages,
            pageReady = true
        )
    }

    fun ttsPageFractionForContinuousScroll(chapterIndex: Int, pageIndex: Int): Float? =
        continuousTtsPageFraction(
            pageIndex = pageIndex,
            totalPages = pageLayoutEngine.getChapterPageCount(chapterIndex)
        )

    /** Saves chapter-local scroll progress for continuous-scroll mode. */
    fun onContinuousScrollPosition(
        chapterIndex: Int,
        chapterFraction: Float,
        origin: com.huangder.lumibooks.tts.TtsPageChangeOrigin =
            com.huangder.lumibooks.tts.TtsPageChangeOrigin.USER
    ) {
        val currentState = _uiState.value
        // Ignore a final viewport callback from the outgoing continuous reader after switching back
        // to a paged mode. Otherwise it can overwrite the destination reader's restore counters.
        if (!currentState.readerWritingMode.usesContinuousScroll(
                currentState.pageTransition,
                currentState.eInkModeEnabled
            )
        ) return
        val progressScale = CONTINUOUS_PROGRESS_SCALE
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            currentPageIndex = (chapterFraction.coerceIn(0f, 0.9999f) * progressScale).toInt(),
            totalPages = progressScale,
            pageReady = true,
            isLoading = false
        )
        val chapterPageCount = pageLayoutEngine.getChapterPageCount(chapterIndex)
        if (chapterPageCount > 0) {
            val visiblePage = (chapterFraction.coerceIn(0f, 0.9999f) * chapterPageCount)
                .toInt()
                .coerceIn(0, chapterPageCount - 1)
            notifyTtsPageVisible(chapterIndex, visiblePage, origin)
        }
        continuousProgressJob?.cancel()
        val progressState = _uiState.value
        val writeVersion = ++progressWriteVersion
        continuousProgressJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350L)
            progressWriteMutex.withLock {
                if (writeVersion == progressWriteVersion) saveProgressFor(progressState)
            }
        }
    }

    /**
     * JS回调：更新当前页码和总页数
     */
    fun onPageChanged(page: Int, total: Int) {
        _uiState.value = _uiState.value.copy(
            currentPageIndex = page,
            totalPages = total,
            pageReady = true
        )
        saveProgress()
        android.util.Log.e("PG", "onPageChanged page=$page total=$total")
        DiagnosticLoggerRegistry.logger?.log(
            category = "interaction",
            event = "page_changed",
            level = DiagnosticLevel.INFO,
            attributes = mapOf("page" to page, "total" to total)
        )
        preloadAdjacentChapters()
    }

    /**
     * 新 Canvas 引擎页面切换回调。
     */
    fun onNewEnginePageChanged(
        globalPage: Int,
        chapterIndex: Int,
        pageInChapter: Int,
        chapterTotalPages: Int,
        origin: com.huangder.lumibooks.tts.TtsPageChangeOrigin =
            com.huangder.lumibooks.tts.TtsPageChangeOrigin.LAYOUT
    ) {
        val currentState = _uiState.value
        // The old paged view may emit one last callback while Compose swaps in continuous scroll.
        // Do not let it mark the page ready or replace the pending normalized restore fraction.
        if (currentState.readerWritingMode.usesContinuousScroll(
                currentState.pageTransition,
                currentState.eInkModeEnabled
            )
        ) return
        val pendingPosition = currentState.pendingReaderPosition
        val reachedPendingPosition = pendingPosition?.let { target ->
            if (target.chapterIndex != chapterIndex) {
                false
            } else {
                val offset = target.characterOffset
                val page = pageLayoutEngine.getPageLayout(chapterIndex, pageInChapter)
                if (offset == null) {
                    pageInChapter == restoredPagedPageIndex(
                        target.chapterFraction,
                        chapterTotalPages,
                        ReaderPageFractionSemantics.START
                    )
                } else {
                    page != null && offset >= page.startCharOffset && offset < page.endCharOffset
                }
            }
        } == true
        val hasRenderablePage = chapterTotalPages > 0 &&
            pageInChapter in 0 until chapterTotalPages
        val isTxtBook = currentState.book?.format?.name == "TXT"
        val canReleaseInitialLoading = shouldReleasePagedReaderOnFirstPage(
            isTxtBook = isTxtBook,
            isContinuous = false,
            chapterTotalPages = chapterTotalPages,
            hasPendingReaderPosition = pendingPosition != null,
            reachedPendingPosition = reachedPendingPosition
        )
        _uiState.value = currentState.copy(
            globalPageIndex = globalPage,
            currentChapterIndex = chapterIndex,
            currentPageIndex = pageInChapter,
            totalPages = chapterTotalPages,
            rightPageIndex = null,
            rightChapterIndex = null,
            pageReady = currentState.pageReady || hasRenderablePage,
            pendingPageFraction = if (reachedPendingPosition) 0f else currentState.pendingPageFraction,
            pendingPageFractionSemantics = if (reachedPendingPosition) {
                ReaderPageFractionSemantics.START
            } else {
                currentState.pendingPageFractionSemantics
            },
            pendingReaderPosition = if (reachedPendingPosition) null else currentState.pendingReaderPosition
        )
        if (!hasRenderablePage) return
        notifyTtsPageVisible(chapterIndex, pageInChapter, origin)
        // A TXT opened through the fast index may not be able to resolve an old
        // character anchor until the semantic index replaces the virtual chunks.
        // Do not let that deferred restore keep the transition overlay visible.
        val keepLoadingForPendingRestore = !canReleaseInitialLoading
        if (keepLoadingForPendingRestore) return
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            // 🔥 如果是恢复进度（pendingPageFraction > 0），不在此处 saveProgress
            // ReaderScreen 会在跳转到正确页面后再触发保存
            if (!hasPendingReaderRestore(
                    _uiState.value.pendingReaderPosition,
                    _uiState.value.pendingPageFraction
                )
            ) {
                saveProgress()
            }
            return
        }
        if (pendingPosition != null && !reachedPendingPosition) return
        scheduleProgressSave()
    }

    /**
     * 双页对开模式右半页切换回调。
     */
    fun onSpreadPageChanged(
        rightGlobalPage: Int,
        rightChapterIndex: Int,
        rightPageInChapter: Int
    ) {
        val currentState = _uiState.value
        if (currentState.readerWritingMode.usesContinuousScroll(
                currentState.pageTransition,
                currentState.eInkModeEnabled
            )
        ) return
        _uiState.value = currentState.copy(
            rightPageIndex = rightPageInChapter.takeIf { it >= 0 },
            rightChapterIndex = rightChapterIndex.takeIf { it >= 0 }
        )
    }

    fun startTts() {
        val state = _uiState.value
        val book = state.book ?: return
        if (!state.useNewEngine || state.isLoading) return
        val startChapter = state.currentChapterIndex
        val isContinuousScroll = state.readerWritingMode.usesContinuousScroll(
            state.pageTransition,
            state.eInkModeEnabled
        )
        // 连续滚动模式下 currentPageIndex 是 scaled 值（chapterFraction * CONTINUOUS_PROGRESS_SCALE），
        // 且分页引擎未按当前排版工作。改用“章节内滚动比例 × 章节文本长度”换算起始字符偏移，
        // 否则起始页会退化为 0，从本章第一句开始朗读。
        val continuousChapterFraction = state.currentPageIndex.toFloat() / CONTINUOUS_PROGRESS_SCALE
        val source = ReflowTtsPageSource(
            layoutEngine = pageLayoutEngine,
            chapterCount = state.chapterCount,
            chineseMode = state.chineseMode,
            chapterProvider = { chapterIndex ->
                withContext(Dispatchers.IO) { getChapterText(chapterIndex) }
            }
        )
        if (!isContinuousScroll) {
            startTtsSession(
                bookId = book.id,
                bookTitle = book.title,
                source = source,
                startChapter = startChapter,
                startPage = state.currentPageIndex
            )
            return
        }
        viewModelScope.launch {
            val chapterLength = withContext(Dispatchers.IO) {
                getChapterText(startChapter)?.length ?: 0
            }
            val startOffset = continuousStartCharacterOffset(continuousChapterFraction, chapterLength)
            startTtsSession(
                bookId = book.id,
                bookTitle = book.title,
                source = source,
                startChapter = startChapter,
                startPage = 0,
                startCharacterOffset = startOffset
            )
        }
    }

    internal fun startBookLayoutTts(
        webPageProvider: suspend (chapterIndex: Int, pageIndex: Int) -> EpubPageText?
    ) {
        val state = _uiState.value
        val book = state.book ?: return
        if (!state.useNewEngine || state.isLoading) return
        val source = OriginalLayoutEpubTtsPageSource(
            chapterCount = state.chapterCount,
            webPageProvider = webPageProvider,
            chapterTextProvider = { chapterIndex ->
                withContext(Dispatchers.IO) {
                    getChapterText(chapterIndex)
                        ?.toString()
                        ?.replace('\uFFFC', ' ')
                }
            }
        )
        startTtsSession(
            book.id,
            book.title,
            source,
            state.currentChapterIndex,
            state.currentPageIndex
        )
    }

    fun startPdfTts(filePath: String, currentPage: Int, pageCount: Int) {
        val book = _uiState.value.book ?: return
        if (filePath.isBlank() || pageCount <= 0) return
        startTtsSession(
            book.id,
            book.title,
            source = PdfTtsPageSource(pageCount) { pdfPageIndex ->
                pdfTtsPageText(filePath, pdfPageIndex, pageCount)
            },
            startChapter = currentPage.coerceIn(0, pageCount - 1),
            startPage = 0
        )
    }

    private fun startTtsSession(
        bookId: String,
        bookTitle: String,
        source: TtsPageSource,
        startChapter: Int,
        startPage: Int,
        startCharacterOffset: Int? = null
    ) {
        ContextCompat.startForegroundService(
            context,
            TtsForegroundService.startIntent(context, bookTitle)
        )
        viewModelScope.launch {
            val result = ttsController.start(
                bookId = bookId,
                source = source,
                startChapter = startChapter,
                startPage = startPage,
                startCharacterOffset = startCharacterOffset
            )
            if (result.isFailure) {
                _ttsState.value = _ttsState.value.copy(
                    errorMessage = ttsErrorMessage(result.exceptionOrNull())
                )
                context.stopService(android.content.Intent(context, TtsForegroundService::class.java))
            }
        }
    }

    private suspend fun pdfTtsPageText(
        filePath: String,
        pdfPageIndex: Int,
        pageCount: Int
    ): String = withContext(Dispatchers.IO) {
        if (pdfPageIndex !in 0 until pageCount) {
            throw IllegalStateException("No readable text found in this PDF page")
        }
        try {
            pdfTextExtractor.extractPages(File(filePath), setOf(pdfPageIndex))[pdfPageIndex]
        } catch (error: Exception) {
            throw IllegalStateException("Unable to extract PDF text", error)
        }?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No readable text found in this PDF page")
    }

    fun onPdfTtsPageVisible(bookId: String, pdfPageIndex: Int) {
        notifyTtsPageVisible(pdfPageIndex, 0)
    }

    private fun ttsErrorMessage(error: Throwable?): String = when {
        error?.message == "No readable text found in this PDF page" ->
            context.getString(R.string.pdf_convert_error_no_text)
        error?.message == "Unable to extract PDF text" ->
            context.getString(R.string.pdf_convert_error_storage)
        error === ExternalTtsException.MissingApiKey ->
            context.getString(R.string.external_tts_error_missing_key)
        error === ExternalTtsException.InsecureEndpoint ->
            context.getString(R.string.external_tts_error_insecure_endpoint)
        error === ExternalTtsException.Unauthorized ->
            context.getString(R.string.external_tts_error_unauthorized)
        error === ExternalTtsException.RateLimited ->
            context.getString(R.string.external_tts_error_rate_limited)
        error === ExternalTtsException.InvalidAudio ->
            context.getString(R.string.external_tts_error_invalid_audio)
        error is ExternalTtsException.Network ->
            context.getString(R.string.external_tts_error_network)
        error is ExternalTtsException.Service ->
            context.getString(R.string.external_tts_error_service)
        error is ExternalTtsException.InvalidConfiguration ->
            context.getString(R.string.external_tts_error_configuration)
        error is SystemTtsException.EngineUnavailable ->
            context.getString(R.string.tts_engine_unavailable_error)
        error is SystemTtsException.Initialization ||
            error is SystemTtsException.LanguageUnavailable ->
            context.getString(R.string.tts_unavailable)
        error is SystemTtsException.Playback ->
            context.getString(R.string.tts_playback_error)
        error is SystemTtsException.ProsodyRejected ->
            context.getString(R.string.tts_prosody_rejected_error)
        error?.message == "No readable text found" ->
            context.getString(R.string.tts_no_readable_text)
        else -> context.getString(R.string.tts_playback_error)
    }

    fun stopTts() {
        ttsController.stop()
    }

    fun toggleTtsPlayPause() {
        when (_ttsState.value.playbackState) {
            TtsPlaybackState.PLAYING -> ttsController.pause()
            TtsPlaybackState.PAUSED -> ttsController.resume()
            else -> Unit
        }
    }

    fun ttsSkipForward() {
        ttsController.skip(forward = true)
    }

    fun ttsSkipBackward() {
        ttsController.skip(forward = false)
    }

    /** 听书进行中双击某句：朗读从该句开始，阅读页保持不动。 */
    fun seekTtsToSentence(chapterIndex: Int, characterOffset: Int) {
        // 只允许调整当前这本书的朗读会话，避免影响其他书籍或已停止的会话。
        if (_ttsState.value.activeBookId != bookId) return
        ttsController.seekTo(
            chapterIndex = chapterIndex.coerceAtLeast(0),
            characterOffset = characterOffset.coerceAtLeast(0)
        )
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { ttsController.setSpeechRate(rate) }
    }

    fun setTtsSpeechRateMode(mode: TtsProsodyMode) {
        viewModelScope.launch { ttsController.setSpeechRateMode(mode) }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch { ttsController.setPitch(pitch) }
    }

    fun setTtsPitchMode(mode: TtsProsodyMode) {
        viewModelScope.launch { ttsController.setPitchMode(mode) }
    }

    fun clearTtsError() {
        _ttsState.value = _ttsState.value.copy(errorMessage = null)
    }

    fun setSleepTimer(minutes: Int) = ttsController.setSleepTimer(minutes)

    fun cancelSleepTimer() = ttsController.cancelSleepTimer()

    /** WebView 分页完成后调用 */
    fun onPaginationDone() {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun clearPendingPageFraction() {
        _uiState.value = _uiState.value.copy(
            pendingPageFraction = 0f,
            pendingPageFractionSemantics = ReaderPageFractionSemantics.START,
            pendingReaderPosition = null
        )
        if (_uiState.value.pageReady) scheduleProgressSave()
    }

    /** 直接保存进度（PDF 竖向滚动用） */
    fun saveProgressDirect(bookId: String, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        viewModelScope.launch {
            bookRepository.updateReadingProgress(bookId, p)
            bookRepository.updateLastReadTime(bookId, System.currentTimeMillis())
        }
    }

    /** 预加载缓存：key=章节索引，value=HTML，最大 24 条（激进预加载需要更大窗口） */
    private val preloadCache = object : LinkedHashMap<Int, String>(26, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > 24
        }
    }

    /** 预加载相邻章节（激进策略：进入章节即触发，大幅提前预加载窗口） */
    fun preloadAdjacentChapters() {
        val state = _uiState.value
        val parser = parser ?: return
        val optimize = state.optimizeLayout
        if (state.totalPages <= 0) return
        val progress = state.currentPageIndex.toFloat() / state.totalPages
        android.util.Log.e("PG", "preloadCheck: page=${state.currentPageIndex} total=${state.totalPages} progress=${progress}")

        // 🔥 进入章节就立即预加载下一章（progress >= 0 即触发，原来是 15%）
        if (progress >= 0f) {
            val next = state.currentChapterIndex + 1
            if (next < state.chapterCount && next !in preloadCache) {
                android.util.Log.e("PG", "Preloading next chapter $next")
                viewModelScope.launch {
                    try {
                        preloadCache[next] = parser.getChapterHtml(next, optimize)
                        android.util.Log.e("PG", "Preloaded chapter $next")
                    } catch (_: Exception) {}
                }
            }
            // 进度 > 30% → 再预加载下下章（原来 50%，更激进）
            if (progress >= 0.3f) {
                val next2 = state.currentChapterIndex + 2
                if (next2 < state.chapterCount && next2 !in preloadCache) {
                    android.util.Log.e("PG", "Preloading chapter+2: $next2")
                    viewModelScope.launch {
                        try {
                            preloadCache[next2] = parser.getChapterHtml(next2, optimize)
                            android.util.Log.e("PG", "Preloaded chapter+2 $next2")
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 🔥 前 8 页 → 预加载上一章（原来 3 页，大幅提前）
        if (state.currentPageIndex <= 8) {
            val prev = state.currentChapterIndex - 1
            if (prev >= 0 && prev !in preloadCache) {
                android.util.Log.e("PG", "Preloading prev chapter $prev")
                viewModelScope.launch {
                    try {
                        preloadCache[prev] = parser.getChapterHtml(prev, optimize)
                        android.util.Log.e("PG", "Preloaded chapter $prev")
                    } catch (_: Exception) {}
                }
            }
            // 🔥 前 3 页 → 再预加载上上章（原来 1 页）
            if (state.currentPageIndex <= 3) {
                val prev2 = state.currentChapterIndex - 2
                if (prev2 >= 0 && prev2 !in preloadCache) {
                    android.util.Log.e("PG", "Preloading chapter-2: $prev2")
                    viewModelScope.launch {
                        try {
                            preloadCache[prev2] = parser.getChapterHtml(prev2, optimize)
                            android.util.Log.e("PG", "Preloaded chapter-2 $prev2")
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /** 获取章节 HTML（优先用缓存，缓存不再 one-shot 删除，由 LRU 自动淘汰） */
    fun getChapterHtml(index: Int): String {
        preloadCache[index]?.let { cached ->
            return cached
        }
        // 缓存 miss，从 parser 获取并写入缓存
        val optimize = _uiState.value.optimizeLayout
        val html = parser?.getChapterHtml(index, optimize) ?: ""
        if (html.isNotEmpty()) {
            preloadCache[index] = html
        }
        return html
    }

    /** 获取相邻章节 HTML（用于预渲染，不清除缓存） */
    fun getAdjacentChapterHtml(index: Int): String? {
        return preloadCache[index] ?: try {
            val html = parser?.getChapterHtml(index)
            if (html != null) preloadCache[index] = html
            html
        } catch (_: Exception) { null }
    }

    /** 获取章节纯文本（TXT/EPUB 格式用，用于 StaticLayout 排版）。
     *  返回 CharSequence 支持标题格式化（Spannable）。
     *
     *  contentWidthPx 非空表示调用方（连续滚动阅读器）已量出真实内容宽度；
     *  用它刷新解析器的图片基准宽度，插图尺寸才能跟随左右边距。
     *
     *  这里的标点挤压是「阅读器自绘」变体（只改测量值），供分页引擎、TTS、设置预览等
     *  自己逐字绘制正文的通道使用。需要框架（原生 TextView）直接绘制正文的上下滚动模式，
     *  必须改用 [getFrameworkDrawnChapterText]：两套 span 不能混用，否则行末标点会被裁切。
     */
    fun getChapterText(index: Int, contentWidthPx: Int? = null): CharSequence? =
        buildChapterText(index, contentWidthPx, frameworkDrawsText = false)

    /**
     * 供框架直接绘制正文的通道使用（上下滚动模式的原生 TextView）。
     *
     * 标点改用 [com.huangder.lumibooks.ui.reader.engine.ReaderPunctuationReplacementSpan]：
     * 框架按半个字宽排版，并由 span 自己把整字宽字形居中画进槽位。若这里误用自绘变体，
     * 框架会按整字宽落笔，行内标点之后的内容整体右移，行尾会越过正文列右边缘被裁掉半截。
     */
    fun getFrameworkDrawnChapterText(index: Int, contentWidthPx: Int? = null): CharSequence? =
        buildChapterText(index, contentWidthPx, frameworkDrawsText = true)

    private fun buildChapterText(
        index: Int,
        contentWidthPx: Int?,
        frameworkDrawsText: Boolean
    ): CharSequence? {
        if (contentWidthPx != null) updateReaderContentWidth(contentWidthPx)
        val raw = if (firstChapterDecodeTraced.compareAndSet(false, true)) {
            ReaderOpenPerformance.traceStage(bookId, ReaderOpenStage.FIRST_CHAPTER_DECODE) {
                try { parser?.getChapterContent(index) } catch (_: Exception) { null }
            }
        } else {
            try { parser?.getChapterContent(index) } catch (_: Exception) { null }
        } ?: return null
        if (raw.isEmpty()) return raw

        val state = _uiState.value
        val isTxt = raw !is Spanned
        var skipFirstParagraphIndent = false
        val chapterText = if (isTxt) {
            // 只有解析器识别出的真实章节标题才加大加粗。无目录 TXT 的章节标题是
            // “第 N 章 + 正文摘要”的合成值，不能把原始首段整行误当成标题。
            val newlineIdx = raw.indexOf('\n')
            if (newlineIdx > 0) {
                val title = raw.substring(0, newlineIdx)
                val parsedChapterTitle = state.chapterTitles.getOrNull(index).orEmpty()
                if (shouldStyleTxtChapterTitle(title, parsedChapterTitle)) {
                    skipFirstParagraphIndent = true
                    val body = raw.substring(newlineIdx + 1)
                    val spannable = SpannableString("$title\n\n$body")
                    val titleEnd = title.length
                    spannable.setSpan(
                        AbsoluteSizeSpan(txtChapterTitleFontSize(state.fontSize), true),
                        0,
                        titleEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        titleEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable
                } else {
                    raw
                }
            } else {
                raw
            }
        } else {
            raw
        }

        // EpubParser has already applied paragraph spacing and indentation while decoding HTML.
        // Rebuilding the same 400K+ Spanned here is both redundant and quadratic on Android's
        // SpannableStringBuilder, which can block the main thread when Compose asks for a preview.
        val formatted = if (isTxt) {
            ReaderParagraphFormatter.applyFirstLineIndent(
                text = chapterText,
                indentCharacters = state.firstLineIndent,
                textSizePx = state.fontSize * context.resources.displayMetrics.scaledDensity,
                paragraphSpacingPx = state.paragraphSpacing * context.resources.displayMetrics.density,
                skipFirstNonEmptyParagraph = skipFirstParagraphIndent
            )
        } else {
            chapterText
        }
        val aligned = applyReaderTextAlignment(formatted, state.textAlignment)
        // 竖排由独立排版器逐字度量，不识别挤压 span，保持原字宽以免分页与绘制错位。
        if (state.readerWritingMode.isVertical) return aligned
        // 全角标点挤压：标点只占半个汉字宽，行内更紧凑、行尾也更容易对齐。
        // 放在最后一步，段落缩进/对齐都会连同 span 一起复制。
        return applyReaderPunctuationCompression(aligned, frameworkDrawsText = frameworkDrawsText)
    }

    internal fun resolveTxtEditorCharOffset(chapterIndex: Int, readerOffset: Int): Int {
        val sourceText = try {
            parser?.getChapterContent(chapterIndex)
        } catch (_: Exception) {
            null
        } ?: return readerOffset.coerceAtLeast(0)
        val readerText = getChapterText(chapterIndex) ?: sourceText
        return mapReaderTxtOffsetToSource(sourceText, readerText, readerOffset)
    }

    internal fun resolveTxtSourceRange(
        chapterIndex: Int,
        readerStart: Int,
        readerEndExclusive: Int
    ): TxtSourceRange? {
        val sourceText = try {
            parser?.getChapterContent(chapterIndex)
        } catch (_: Exception) {
            null
        } ?: return null
        val readerText = getChapterText(chapterIndex) ?: sourceText
        return mapReaderTxtRangeToSource(sourceText, readerText, readerStart, readerEndExclusive)
    }

    /** 在 IO 线程解析 EPUB 相对路径/锚点，返回原生阅读引擎可跳转的位置。 */
    suspend fun resolveBookLink(sourceChapterIndex: Int, href: String): BookLinkTarget? {
        return withContext(Dispatchers.IO) {
            parser?.resolveLink(sourceChapterIndex, href)
        }
    }

    /** Canvas 引擎注释气泡：该 href 是否为注释引用链接。 */
    suspend fun isFootnoteHref(chapterIndex: Int, href: String): Boolean {
        return withContext(Dispatchers.IO) {
            parser?.isFootnoteHref(chapterIndex, href) == true
        }
    }

    /** Canvas 引擎注释气泡：提取注释正文，失败返回 null（调用方回退为普通跳转）。 */
    suspend fun resolveFootnoteText(sourceChapterIndex: Int, href: String): String? {
        return withContext(Dispatchers.IO) {
            parser?.resolveFootnoteText(sourceChapterIndex, href)
        }
    }

    /** Resolves a TOC anchor inside its target chapter before the reader performs the jump. */
    suspend fun resolveTocTarget(
        chapterIndex: Int,
        anchor: String?
    ): BookLinkTarget? {
        if (chapterIndex !in 0 until _uiState.value.chapterCount) return null
        if (anchor.isNullOrBlank()) return BookLinkTarget(chapterIndex)
        return withContext(Dispatchers.IO) {
            parser?.resolveLink(chapterIndex, "#$anchor")
        } ?: BookLinkTarget(chapterIndex)
    }

    /**
     * 预渲染命中时调用：仅更新章节索引和进度，不更新 chapterHtml（避免触发 loadDataWithBaseURL 破坏 DOM swap）
     */
    fun onChapterSwapped(direction: Int) {
        val state = _uiState.value
        val newIdx = (state.currentChapterIndex + direction).coerceIn(0, state.chapterCount - 1)
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = newIdx,
            currentPageIndex = 0,
            rightPageIndex = null,
            rightChapterIndex = null
        )
        saveProgress()
        preloadAdjacentChapters()
    }

    fun toggleMenu() {
        _uiState.value = _uiState.value.copy(
            isMenuVisible = !_uiState.value.isMenuVisible
        )
    }

    fun hideMenu() {
        if (_uiState.value.isMenuVisible) {
            _uiState.value = _uiState.value.copy(isMenuVisible = false)
        }
    }

    /**
     * 重新解析文件并刷新阅读状态（TXT编辑后调用）。
     * 会保留当前章节和页面的最佳匹配位置。
     */
    fun reloadContent() {
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch
            val p = parser ?: return@launch
            val oldChapterIndex = _uiState.value.currentChapterIndex
            val oldChapterTitle = _uiState.value.chapterTitles.getOrNull(oldChapterIndex).orEmpty()

            val newContent = withContext(Dispatchers.IO) {
                p.parse(book.filePath)
            }
            val newChapterTitles = newContent.chapters.map { it.title }
            val newChapterCount = newContent.chapters.size

            // 尝试通过标题匹配恢复章节位置，失败则回退到索引
            val bestChapterIndex = if (oldChapterTitle.isNotBlank()) {
                newChapterTitles.indexOf(oldChapterTitle).coerceAtLeast(0)
            } else {
                oldChapterIndex.coerceIn(0, (newChapterCount - 1).coerceAtLeast(0))
            }

            _uiState.value = _uiState.value.copy(
                chapterCount = newChapterCount,
                chapterTitles = newChapterTitles,
                tocEntries = newContent.tocEntries.ifEmpty {
                    newChapterTitles.mapIndexed { i, t ->
                        com.huangder.lumibooks.util.parser.TocEntry(t, 1, i)
                    }
                },
                currentChapterIndex = bestChapterIndex,
                currentPageIndex = 0,
                contentRevision = _uiState.value.contentRevision + 1
            )
            preloadAdjacentChapters()
            saveProgress()
        }
    }

    // -- 书签和笔记 --

    fun addBookmark(characterOffset: Int? = null, title: String? = null) {
        val state = _uiState.value
        val book = state.book ?: return
        val bookmark = Bookmark(
            bookId = book.id,
            chapterIndex = state.currentChapterIndex,
            position = characterOffset?.let(::bookmarkPositionForCharacterOffset)
                ?: state.currentPageIndex.toFloat(),
            locatorJson = state.epubLocatorJson.takeIf { state.renderMode == EpubRenderMode.BOOK_LAYOUT },
            title = title?.takeIf { it.isNotBlank() }
                ?: context.getString(
                    R.string.bookmark_default_title,
                    state.currentChapterIndex + 1,
                    state.currentPageIndex + 1
                ),
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch { readingRepository.insertBookmark(bookmark) }
    }

    /** PDF 专用书签：每页 = 一个 chapterIndex */
    fun addPdfBookmark(pageIndex: Int, bookTitle: String) {
        val state = _uiState.value
        val book = state.book ?: return
        val bookmark = Bookmark(
            bookId = book.id,
            chapterIndex = pageIndex,
            position = 0f,
            title = context.getString(R.string.pdf_bookmark_default_title, pageIndex + 1, bookTitle),
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch { readingRepository.insertBookmark(bookmark) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { readingRepository.deleteBookmark(bookmark) }
    }

    fun updateBookmarkTitle(bookmark: Bookmark, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty() || title == bookmark.title) return
        viewModelScope.launch {
            readingRepository.updateBookmark(bookmark.copy(title = title))
        }
    }

    fun addNote(
        selectedText: String,
        noteText: String,
        chapterIndex: Int = -1,
        startPosition: Int = 0,
        endPosition: Int = 0,
        color: String = DefaultReaderHighlightColor,
        startLocatorJson: String? = null,
        endLocatorJson: String? = null,
        type: String = "highlight"
    ) {
        val state = _uiState.value
        val book = state.book ?: return
        val resolvedChapterIndex = if (chapterIndex >= 0) chapterIndex else state.currentChapterIndex
        val note = Note(
            bookId = book.id,
            chapterIndex = resolvedChapterIndex,
            startPosition = if (startPosition > 0 || chapterIndex >= 0) startPosition else 0,
            endPosition = if (endPosition > 0 || chapterIndex >= 0) endPosition else selectedText.length,
            startLocatorJson = startLocatorJson,
            endLocatorJson = endLocatorJson,
            selectedText = selectedText,
            note = noteText,
            color = color,
            createdAt = System.currentTimeMillis(),
            type = type
        )
        // Render immediately instead of waiting for Room's Flow to emit the inserted record.
        // The database observer replaces this temporary id=0 record with the persisted one.
        _notes.value = _notes.value + note
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            try {
                val noteToSave = if (
                    book.format.name == "EPUB" &&
                    note.startLocatorJson == null && note.endLocatorJson == null
                ) {
                    withContext(Dispatchers.IO) {
                        val chapterText = runCatching {
                            getChapterText(resolvedChapterIndex)
                        }.getOrNull()
                        chapterText?.let { text ->
                            val locators = createHighlightLocatorPair(
                                chapterText = text,
                                startPosition = note.startPosition,
                                endPosition = note.endPosition,
                                selectedText = note.selectedText
                            )
                            note.copy(
                                startLocatorJson = locators.first,
                                endLocatorJson = locators.second
                            )
                        } ?: note
                    }
                } else {
                    note
                }
                if (noteToSave != note) {
                    _notes.value = _notes.value.map { existing ->
                        if (existing === note) noteToSave else existing
                    }
                    refreshReaderNotes(_notes.value)
                }
                readingRepository.insertNote(noteToSave)
            } catch (_: Exception) {
                _notes.value = _notes.value.filterNot { existing ->
                    existing === note || (
                        existing.id == 0L && existing.createdAt == note.createdAt &&
                            existing.chapterIndex == note.chapterIndex &&
                            existing.selectedText == note.selectedText
                        )
                }
                refreshReaderNotes(_notes.value)
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { readingRepository.updateNote(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { readingRepository.deleteNote(note) }
    }

    private val cancelledPdfInkLocators = mutableSetOf<String>()

    internal fun addPdfInkStroke(stroke: PdfInkStroke) {
        val book = _uiState.value.book ?: return
        val locator = PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L))
        val note = Note(
            bookId = book.id,
            chapterIndex = stroke.page,
            startPosition = 0,
            endPosition = stroke.points.size,
            startLocatorJson = locator,
            endLocatorJson = locator,
            selectedText = "",
            note = "",
            color = stroke.color,
            createdAt = System.currentTimeMillis(),
            type = stroke.noteType()
        )
        _notes.value = _notes.value + note
        refreshReaderNotes(_notes.value)
        viewModelScope.launch {
            if (cancelledPdfInkLocators.remove(locator)) return@launch
            runCatching { readingRepository.insertNote(note) }.onFailure {
                _notes.value = _notes.value.filterNot { it.createdAt == note.createdAt }
                refreshReaderNotes(_notes.value)
            }
        }
    }

    internal fun deletePdfInkStroke(stroke: PdfInkStroke) {
        val locator = PdfInkStrokeLocatorV1.encode(stroke.copy(id = 0L))
        cancelledPdfInkLocators += locator
        val existing = _notes.value.firstOrNull { note ->
            (stroke.id > 0L && note.id == stroke.id) || note.startLocatorJson == locator
        } ?: return
        _notes.value = _notes.value.filterNot { note ->
            (existing.id > 0L && note.id == existing.id) || note.startLocatorJson == locator
        }
        refreshReaderNotes(_notes.value)
        if (existing.id > 0L) {
            viewModelScope.launch { readingRepository.deleteNote(existing) }
        }
    }

    fun clearLegacyPdfAnnotations(bookId: String) {
        viewModelScope.launch {
            readingRepository.deleteLegacyPdfAnnotationsByBookId(bookId)
        }
    }

    fun replaceAnnotationRange(
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        type: String,
        color: String
    ) {
        applyAnnotationRangeEdit(chapterIndex) { chapterText, existing, book ->
            AnnotationNoteEditPlanner.replaceRange(
                existing = existing,
                chapterText = chapterText,
                bookId = book.id,
                chapterIndex = chapterIndex,
                start = startPosition,
                end = endPosition,
                type = type,
                color = color,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    fun removeAnnotationRange(
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        type: String
    ) {
        applyAnnotationRangeEdit(chapterIndex) { chapterText, existing, book ->
            AnnotationNoteEditPlanner.removeRange(
                existing = existing,
                chapterText = chapterText,
                bookId = book.id,
                chapterIndex = chapterIndex,
                start = startPosition,
                end = endPosition,
                type = type
            )
        }
    }

    private fun applyAnnotationRangeEdit(
        chapterIndex: Int,
        buildPlan: (chapterText: String, existing: List<Note>, book: Book) -> AnnotationEditPlan
    ) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val snapshot = _notes.value
            try {
                val chapterText = withContext(Dispatchers.IO) {
                    getChapterText(chapterIndex)?.toString()
                } ?: return@launch
                val plan = buildPlan(chapterText, _readerNotes.value, book)
                if (plan.isEmpty) return@launch
                val prepared = withContext(Dispatchers.IO) {
                    prepareAnnotationLocators(plan, chapterText, book.format.name == "EPUB")
                }
                val optimistic = applyAnnotationPlanToMemory(snapshot, prepared)
                _notes.value = optimistic
                refreshReaderNotes(optimistic)
                readingRepository.applyAnnotationEdit(prepared)
            } catch (_: Exception) {
                _notes.value = snapshot
                refreshReaderNotes(snapshot)
            }
        }
    }

    private fun prepareAnnotationLocators(
        plan: AnnotationEditPlan,
        chapterText: String,
        needsLocators: Boolean
    ): AnnotationEditPlan {
        if (!needsLocators) return plan
        fun withLocators(note: Note): Note {
            val locators = createHighlightLocatorPair(
                chapterText = chapterText,
                startPosition = note.startPosition,
                endPosition = note.endPosition,
                selectedText = note.selectedText
            )
            return note.copy(startLocatorJson = locators.first, endLocatorJson = locators.second)
        }
        return plan.copy(
            updates = plan.updates.map(::withLocators),
            inserts = plan.inserts.map(::withLocators)
        )
    }

    private fun applyAnnotationPlanToMemory(
        current: List<Note>,
        plan: AnnotationEditPlan
    ): List<Note> {
        val deleteIds = plan.deletes.mapTo(mutableSetOf()) { it.id }
        val updatesById = plan.updates.associateBy { it.id }
        return current.mapNotNull { note ->
            when {
                note.id in deleteIds -> null
                note.id in updatesById -> updatesById.getValue(note.id)
                else -> note
            }
        } + plan.inserts
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            readingRepository.getBookmarksByBookId(bookId).collect { list ->
                _bookmarks.value = list
            }
        }
    }

    private fun loadNotes() {
        viewModelScope.launch {
            readingRepository.getNotesByBookId(bookId).collectLatest { list ->
                _notes.value = list
                _readerNotes.value = withContext(Dispatchers.IO) {
                    resolveReaderNotes(list)
                }
            }
        }
    }

    fun resolvedReaderNote(note: Note): Note? = _readerNotes.value.firstOrNull { candidate ->
        if (note.id != 0L) {
            candidate.id == note.id
        } else {
            candidate.createdAt == note.createdAt &&
                candidate.chapterIndex == note.chapterIndex &&
                candidate.selectedText == note.selectedText
        }
    }

    internal fun resolveAnnotationSelection(
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        selectedText: String,
        startLocatorJson: String?,
        endLocatorJson: String?
    ): ResolvedHighlightRange? {
        val chapterText = runCatching { getChapterText(chapterIndex) }.getOrNull() ?: return null
        return HighlightAnchorResolver.resolve(
            chapterText = chapterText,
            storedStart = startPosition,
            storedEnd = endPosition,
            selectedText = selectedText,
            reference = parseHighlightTextReference(startLocatorJson, endLocatorJson, selectedText)
        )
    }

    fun findOverlappingReaderNotes(
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        selectedText: String,
        startLocatorJson: String?,
        endLocatorJson: String?
    ): List<Note> {
        val chapterText = runCatching { getChapterText(chapterIndex) }.getOrNull()
            ?: return emptyList()
        return findOverlappingResolvedNotes(
            chapterText = chapterText,
            notes = _readerNotes.value,
            chapterIndex = chapterIndex,
            storedStart = startPosition,
            storedEnd = endPosition,
            selectedText = selectedText,
            startLocatorJson = startLocatorJson,
            endLocatorJson = endLocatorJson
        )
    }

    fun findOverlappingReaderNote(
        chapterIndex: Int,
        startPosition: Int,
        endPosition: Int,
        selectedText: String,
        startLocatorJson: String?,
        endLocatorJson: String?
    ): Note? = findOverlappingReaderNotes(
        chapterIndex,
        startPosition,
        endPosition,
        selectedText,
        startLocatorJson,
        endLocatorJson
    ).firstOrNull()

    private fun refreshReaderNotes(notes: List<Note>) {
        readerNotesJob?.cancel()
        readerNotesJob = viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveReaderNotes(notes) }
            if (_notes.value == notes) _readerNotes.value = resolved
        }
    }

    private fun resolveReaderNotes(notes: List<Note>): List<Note> {
        if (notes.isEmpty()) return emptyList()
        val chapterCache = mutableMapOf<Int, CharSequence?>()
        return notes.mapNotNull { note ->
            val readerChapterText = chapterCache.getOrPut(note.chapterIndex) {
                runCatching { getChapterText(note.chapterIndex) }.getOrNull()
            } ?: return@mapNotNull null
            resolveReaderNote(note, readerChapterText)
        }
    }

    private fun saveProgress() {
        val state = _uiState.value
        val writeVersion = ++progressWriteVersion
        viewModelScope.launch {
            progressWriteMutex.withLock {
                if (writeVersion == progressWriteVersion) saveProgressFor(state)
            }
        }
    }

    /**
     * 分页引擎回调驱动的进度保存：短暂防抖后写入。
     * 小窗/旋转等窗口 resize 会触发连续多次重排回调，中间可能夹带瞬态位置；
     * 防抖 + 版本号保证最终只写入最后一次（锚点修正后）的真实位置，
     * 避免瞬态 0 进度覆盖用户真实进度。退后台/离开阅读页走 saveProgress() 即时落库。
     */
    private fun scheduleProgressSave() {
        val state = _uiState.value
        val writeVersion = ++progressWriteVersion
        continuousProgressJob?.cancel()
        continuousProgressJob = viewModelScope.launch {
            kotlinx.coroutines.delay(PROGRESS_SAVE_DEBOUNCE_MS)
            progressWriteMutex.withLock {
                if (writeVersion == progressWriteVersion) saveProgressFor(state)
            }
        }
    }

    private suspend fun saveProgressFor(state: ReaderUiState) {
        val book = state.book ?: return
        if (state.chapterCount == 0) return
        if (hasPendingReaderRestore(state.pendingReaderPosition, state.pendingPageFraction)) return

        val isContinuousScroll = state.useNewEngine &&
            state.readerWritingMode.usesContinuousScroll(
                state.pageTransition,
                state.eInkModeEnabled
            )
        val progress = calculateSavedReadingProgress(
            currentChapterIndex = state.currentChapterIndex,
            chapterCount = state.chapterCount,
            currentPageIndex = state.currentPageIndex,
            totalPages = state.totalPages,
            isContinuousScroll = isContinuousScroll
        )
        val characterOffset = if (isContinuousScroll) {
            null
        } else {
            pageLayoutEngine.getPageLayout(
                state.currentChapterIndex,
                state.currentPageIndex
            )?.startCharOffset
        }
        val chapterFraction = if (state.totalPages > 0) {
            state.currentPageIndex.toFloat().div(state.totalPages)
        } else {
            0f
        }.coerceIn(0f, 0.9999f)
        val sourceByteOffset = (parser as? TxtParser)?.let { txtParser ->
            if (state.txtIndexState == TxtIndexState.FAST_PARTIAL) {
                largeTxtFastRanges.getOrNull(state.currentChapterIndex)?.let { (start, end) ->
                    start + ((end - start).coerceAtLeast(0L) * chapterFraction).toLong()
                }
            } else {
                withContext(Dispatchers.IO) {
                    characterOffset?.let {
                        txtParser.characterOffsetToByte(state.currentChapterIndex, it)
                    } ?: txtParser.getChapterByteRange(state.currentChapterIndex)?.let { (start, end) ->
                        start + ((end - start).coerceAtLeast(0L) * chapterFraction).toLong()
                    }
                }
            }
        }
        val readerPosition = if (
            state.renderMode == EpubRenderMode.READER_LAYOUT &&
            state.useNewEngine &&
            shouldPersistReaderLocator(book.format.name, state.txtIndexState, sourceByteOffset)
        ) {
            ReaderPositionLocator(
                chapterIndex = state.currentChapterIndex,
                chapterFraction = chapterFraction,
                flow = if (isContinuousScroll) {
                    ReaderPositionFlow.CONTINUOUS
                } else {
                    ReaderPositionFlow.PAGED
                },
                characterOffset = characterOffset,
                sourceByteOffset = sourceByteOffset
            ).toJson()
        } else {
            state.epubLocatorJson.takeIf { state.renderMode == EpubRenderMode.BOOK_LAYOUT }
        }
        bookRepository.updateReadingProgress(
            book.id,
            progress,
            readerPosition
        )
        bookRepository.updateLastReadTime(book.id, System.currentTimeMillis())
        // Trigger debounced WebDAV sync for reading progress
        webdavSyncManager.scheduleReadingProgressSync(book.id)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── 全文搜索 ──

    data class SearchResult(
        val chapterIndex: Int,
        val chapterTitle: String,
        val charOffset: Int,
        val context: String,       // 匹配位置前后文本片段
        val matchLength: Int,      // 匹配文本长度
        val epubLocator: EpubLocator? = null
    )

    /**
     * 全书搜索关键词，返回匹配列表（章节索引 + 字符偏移 + 上下文）。
     * CSS 原排版 EPUB 使用按次流式扫描和 locator；所有模式最多返回 [maxResults] 条。
     */
    suspend fun searchAllChapters(query: String, maxResults: Int = 200): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (query.isEmpty() || maxResults <= 0) return@withContext emptyList()
            val resultLimit = maxResults.coerceAtMost(200)

            val activeParser = parser ?: return@withContext emptyList()
            val state = _uiState.value
            val titles = state.chapterTitles
            val formatName = state.book?.format?.name
            if ((formatName == "EPUB" || formatName == "MOBI") &&
                state.renderMode == EpubRenderMode.BOOK_LAYOUT &&
                activeParser is BookSearchSource
            ) {
                return@withContext activeParser.searchBook(query, resultLimit).map { match ->
                    SearchResult(
                        chapterIndex = match.chapterIndex,
                        chapterTitle = titles.getOrElse(match.chapterIndex) {
                            context.getString(R.string.chapter_number, match.chapterIndex + 1)
                        }.ifBlank { context.getString(R.string.chapter_number, match.chapterIndex + 1) },
                        charOffset = match.charOffset,
                        context = match.context,
                        matchLength = match.matchLength,
                        epubLocator = match.locator
                    )
                }
            }

            val results = mutableListOf<SearchResult>()
            for (chIdx in 0 until state.chapterCount) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // Search the exact CharSequence the reader lays out. Searching parser output directly
                // makes offsets drift when the reader adds a title break or paragraph formatting.
                val text = getChapterText(chIdx)?.toString() ?: continue

                var searchStart = 0
                while (true) {
                    val foundIdx = text.indexOf(query, searchStart, ignoreCase = true)
                    if (foundIdx == -1) break

                    val ctxStart = (foundIdx - 12).coerceAtLeast(0)
                    val ctxEnd = (foundIdx + query.length + 20).coerceAtMost(text.length)
                    val contextSnippet = text.substring(ctxStart, ctxEnd)
                        .replace('\n', ' ')
                        .replace('\r', ' ')

                    val title = titles.getOrElse(chIdx) {
                        context.getString(R.string.chapter_number, chIdx + 1)
                    }.ifBlank { context.getString(R.string.chapter_number, chIdx + 1) }
                    results.add(
                        SearchResult(
                            chapterIndex = chIdx,
                            chapterTitle = title,
                            charOffset = foundIdx,
                            context = contextSnippet,
                            matchLength = query.length
                        )
                    )
                    searchStart = foundIdx + query.length
                    if (results.size >= resultLimit) break
                }
                if (results.size >= resultLimit) break
            }
            results
        }

    /**
     * 获取章节文本长度（用于估算搜索结果的页码位置）。
     */
    fun getChapterTextLength(chapterIndex: Int): Int {
        return try {
            parser?.getChapterContent(chapterIndex)?.length ?: 0
        } catch (_: Exception) { 0 }
    }

    /**
     * 根据章内字符偏移估算页码（与搜索结果跳转相同的算法）。
     */
    fun estimatePageFromCharOffset(chapterIndex: Int, charOffset: Int): Int {
        val chapterLen = getChapterTextLength(chapterIndex)
        val totalPages = _uiState.value.totalPages
        return if (chapterLen > 0 && totalPages > 0) {
            (charOffset.toFloat() / chapterLen * totalPages).toInt().coerceIn(0, totalPages - 1)
        } else 0
    }

    private fun saveReadingSession() {
        val endTime = System.currentTimeMillis()
        val duration = endTime - sessionStartTime
        android.util.Log.e("READING", "saveReadingSession: duration=${duration}ms, bookId=$bookId")
        if (duration < 5000) {
            android.util.Log.e("READING", "Session too short, skipping")
            return
        }

        val today = TimeUtils.getCurrentDate()
        try {
            kotlinx.coroutines.runBlocking {
                val existing = readingRepository.getRecordByBookAndDate(bookId, today)
                if (existing != null) {
                    // 同一天同一本书：累加时长
                    readingRepository.updateRecordDuration(existing.id, duration, endTime)
                    android.util.Log.e("READING", "Record updated: +${duration}ms, total=${existing.duration + duration}ms")
                } else {
                    // 新记录
                    val record = ReadingRecord(
                        bookId = bookId,
                        date = today,
                        duration = duration,
                        startTime = sessionStartTime,
                        endTime = endTime
                    )
                    readingRepository.insertRecord(record)
                    android.util.Log.e("READING", "Record inserted: ${record.date} ${record.duration}ms")
                }
            }
            // 重置会话起始时间，为下一段阅读做准备
            sessionStartTime = System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.e("READING", "Save failed: ${e.message}")
        }
    }

    override fun onCleared() {
        largeTxtIndexJob?.cancel()
        parser?.close()
        runCatching { renderSession?.close() }
        renderSession = null
        preloadCache.clear()
        super.onCleared()
    }
}
