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
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.defaultReaderCornerContent
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.pdfconversion.PdfConversionManager
import com.huangder.lumibooks.pdfconversion.PdfConversionContract
import com.huangder.lumibooks.pdfconversion.PdfConversionEngine
import com.huangder.lumibooks.mineru.MineruApiException
import com.huangder.lumibooks.mineru.MineruConfig
import com.huangder.lumibooks.mineru.MineruManualImportManager
import com.huangder.lumibooks.mineru.MineruMode
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.pdfconversion.PdfConversionState
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.parser.BookParser
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.huangder.lumibooks.util.parser.BookLinkTarget
import com.huangder.lumibooks.util.parser.PdfParser
import com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter
import com.huangder.lumibooks.R
import com.huangder.lumibooks.service.TtsForegroundService
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPageContent
import com.huangder.lumibooks.tts.TtsPlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal fun shouldStyleTxtChapterTitle(firstLine: String, chapterTitle: String): Boolean {
    val normalizedFirstLine = firstLine.trim()
    val normalizedChapterTitle = chapterTitle.trim()
    return normalizedFirstLine.isNotEmpty() &&
        normalizedFirstLine.length <= 80 &&
        normalizedFirstLine == normalizedChapterTitle
}

data class ReaderUiState(
    val book: Book? = null,
    val chapterCount: Int = 0,
    val chapterTitles: List<String> = emptyList(),
    val tocEntries: List<com.huangder.lumibooks.util.parser.TocEntry> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val chapterHtml: String = "",
    val isMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val pageReady: Boolean = false,
    val pendingPageFraction: Float = 0f,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val fontType: String = "system",
    val marginLeftDp: Float = 38f,
    val marginRightDp: Float = 38f,
    val marginTopDp: Float = 64f,
    val marginBottomDp: Float = 64f,
    val readerTheme: String = "day",
    /** 亮度 0f~1f，-1f 跟随系统 */
    val brightness: Float = -1f,
    /** 自定义导入字体文件路径 */
    val customFontPath: String? = null,
    val readerBackgroundSelection: String = "day",
    val customReaderBackgrounds: List<ReaderBackgroundPreset> = emptyList(),
    val readerTextColor: Int? = null,
    val error: String? = null,
    /** 全局页码（跨所有章节），新引擎用 */
    val globalPageIndex: Int = 0,
    /** 是否使用新 Canvas 引擎 */
    val useNewEngine: Boolean = true,
    /** 是否使用优化排版（per-book） */
    val optimizeLayout: Boolean = true,
    /** 简繁转换模式："original" | "simplified" | "traditional" */
    val chineseMode: String = "original",
    /** 翻页效果："slide" | "scroll" | "fade" */
    val pageTransition: String = "slide",
    /** 段间距（dp），默认 8 */
    val paragraphSpacing: Float = 2f,
    /** 首行缩进字符数，默认 2 */
    val firstLineIndent: Float = 2f,
    /** PDF 阅读方向："vertical" | "horizontal" */
    val pdfPageMode: String = "vertical",
    val showReaderChapterProgress: Boolean = true,
    val showReaderPageNumber: Boolean = true,
    val showReaderBattery: Boolean = true,
    val volumeKeyPageTurnEnabled: Boolean = false,
    val readerEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    val readerTopLeftContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.TOP_LEFT),
    val readerTopRightContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.TOP_RIGHT),
    val readerBottomLeftContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT),
    val readerBottomRightContent: ReaderCornerContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT),
    val ttsPlaybackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val ttsSpeechRate: Float = 1f,
    val ttsActiveBookId: String? = null,
    val ttsErrorMessage: String? = null
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

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val ttsController: TtsController,
    private val pdfConversionManager: PdfConversionManager,
    private val mineruManualImportManager: MineruManualImportManager,
    private val mineruTokenStore: MineruTokenStore
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _pdfConversionState = MutableStateFlow<PdfConversionState>(PdfConversionState.Idle)
    val pdfConversionState: StateFlow<PdfConversionState> = _pdfConversionState.asStateFlow()
    private val _mineruMode = MutableStateFlow(MineruMode.DISABLED)
    val mineruMode: StateFlow<MineruMode> = _mineruMode.asStateFlow()
    private var manualImportJob: Job? = null

    val ttsPageTurnRequests = ttsController.pageTurnRequests

    private var parser: BookParser? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var pausedTime: Long = 0L  // 进入后台的时间戳
    private var isPaused: Boolean = false

    /** 应用进入后台：暂停计时 */
    fun onAppBackgrounded() {
        if (!isPaused) {
            pausedTime = System.currentTimeMillis()
            isPaused = true
            // 保存当前会话，防止进程被杀丢失数据
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
            dataStoreManager.migrateAdvancedReaderDefaults()
            loadBook()
            loadReaderSettings()
        }
        viewModelScope.launch {
            ttsController.playbackState.collectLatest { state ->
                _uiState.value = _uiState.value.copy(ttsPlaybackState = state)
            }
        }
        viewModelScope.launch {
            ttsController.speechRate.collectLatest { rate ->
                _uiState.value = _uiState.value.copy(ttsSpeechRate = rate)
            }
        }
        viewModelScope.launch {
            ttsController.activeBookId.collectLatest { activeBookId ->
                _uiState.value = _uiState.value.copy(ttsActiveBookId = activeBookId)
            }
        }
        viewModelScope.launch {
            ttsController.errors.collectLatest {
                _uiState.value = _uiState.value.copy(
                    ttsErrorMessage = context.getString(R.string.tts_playback_error)
                )
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

    private fun loadReaderSettings() {
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
            dataStoreManager.fontType.collectLatest { ft ->
                _uiState.value = _uiState.value.copy(fontType = ft)
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
            dataStoreManager.readerBackgroundSelection.collectLatest { selection ->
                _uiState.value = _uiState.value.copy(readerBackgroundSelection = selection)
            }
        }
        viewModelScope.launch {
            dataStoreManager.readerTextColor.collectLatest { color ->
                _uiState.value = _uiState.value.copy(readerTextColor = color)
            }
        }
        viewModelScope.launch {
            dataStoreManager.customReaderBackgrounds.collectLatest { presets ->
                _uiState.value = _uiState.value.copy(customReaderBackgrounds = presets)
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
    }

    fun saveFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSize = size)
        viewModelScope.launch { dataStoreManager.saveFontSize(size) }
    }

    fun saveLineHeight(lh: Float) {
        _uiState.value = _uiState.value.copy(lineHeight = lh)
        viewModelScope.launch { dataStoreManager.saveLineHeight(lh) }
    }

    fun saveLetterSpacing(ls: Float) {
        _uiState.value = _uiState.value.copy(letterSpacing = ls)
        viewModelScope.launch { dataStoreManager.saveLetterSpacing(ls) }
    }

    fun saveFontType(ft: String) {
        _uiState.value = _uiState.value.copy(fontType = ft)
        viewModelScope.launch { dataStoreManager.saveFontType(ft) }
    }

    fun saveMarginLeft(value: Float) {
        _uiState.value = _uiState.value.copy(marginLeftDp = value)
        viewModelScope.launch { dataStoreManager.saveMarginLeft(value) }
    }

    fun saveMarginRight(value: Float) {
        _uiState.value = _uiState.value.copy(marginRightDp = value)
        viewModelScope.launch { dataStoreManager.saveMarginRight(value) }
    }

    fun saveMarginTop(value: Float) {
        _uiState.value = _uiState.value.copy(marginTopDp = value)
        viewModelScope.launch { dataStoreManager.saveMarginTop(value) }
    }

    fun saveMarginBottom(value: Float) {
        _uiState.value = _uiState.value.copy(marginBottomDp = value)
        viewModelScope.launch { dataStoreManager.saveMarginBottom(value) }
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
        _uiState.value = _uiState.value.copy(
            readerTheme = theme,
            readerBackgroundSelection = theme
        )
        viewModelScope.launch {
            dataStoreManager.saveReaderBackgroundState(theme, theme)
        }
    }

    fun saveReaderTextColor(color: Int?) {
        _uiState.value = _uiState.value.copy(readerTextColor = color)
        viewModelScope.launch { dataStoreManager.saveReaderTextColor(color) }
    }

    fun selectReaderBackground(selection: String) {
        if (selection in setOf("day", "night", "sepia", "green")) {
            saveReaderTheme(selection)
            return
        }
        if (_uiState.value.customReaderBackgrounds.none { it.selectionKey == selection }) return

        _uiState.value = _uiState.value.copy(
            readerTheme = "day",
            readerBackgroundSelection = selection
        )
        viewModelScope.launch {
            dataStoreManager.saveReaderBackgroundState("day", selection)
        }
    }

    fun addCustomReaderBackgroundColor(color: Int) {
        val preset = ReaderBackgroundPreset(
            id = UUID.randomUUID().toString(),
            type = ReaderBackgroundType.COLOR,
            value = String.format(Locale.US, "#%08X", color),
            dominantColor = color
        )
        saveAddedReaderBackground(preset)
    }

    fun addCustomReaderBackgroundImage(uri: Uri) {
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
                        dominantColor = extractDominantColor(file)
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
        _uiState.value = state.copy(
            customReaderBackgrounds = remaining,
            readerTheme = if (wasSelected) "day" else state.readerTheme,
            readerBackgroundSelection = if (wasSelected) "day" else state.readerBackgroundSelection
        )
        viewModelScope.launch {
            val nextTheme = if (wasSelected) "day" else state.readerTheme
            val nextSelection = if (wasSelected) "day" else state.readerBackgroundSelection
            dataStoreManager.saveReaderBackgroundState(nextTheme, nextSelection, remaining)
            if (removed.type == ReaderBackgroundType.IMAGE) {
                withContext(Dispatchers.IO) { runCatching { File(removed.value).delete() } }
            }
        }
    }

    private fun saveAddedReaderBackground(preset: ReaderBackgroundPreset) {
        val updated = _uiState.value.customReaderBackgrounds + preset
        _uiState.value = _uiState.value.copy(
            customReaderBackgrounds = updated,
            readerTheme = "day",
            readerBackgroundSelection = preset.selectionKey
        )
        viewModelScope.launch {
            dataStoreManager.saveReaderBackgroundState("day", preset.selectionKey, updated)
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

    fun saveOptimizeLayout(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(optimizeLayout = enabled)
        viewModelScope.launch {
            dataStoreManager.saveOptimizeLayout(bookId, enabled)
            parser?.clearHtmlCache()
            preloadCache.clear()  // 清空 ViewModel 预加载缓存
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
        _uiState.value = _uiState.value.copy(pageTransition = mode)
        viewModelScope.launch {
            dataStoreManager.savePageTransition(mode)
        }
    }

    fun togglePdfPageMode() {
        val nextMode = if (_uiState.value.pdfPageMode == "horizontal") "vertical" else "horizontal"
        _uiState.value = _uiState.value.copy(pdfPageMode = nextMode)
        viewModelScope.launch { dataStoreManager.savePdfPageMode(nextMode) }
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

    fun saveReaderEdgeTapMode(mode: ReaderEdgeTapMode) {
        _uiState.value = _uiState.value.copy(readerEdgeTapMode = mode)
        viewModelScope.launch { dataStoreManager.saveReaderEdgeTapMode(mode) }
    }

    fun saveReaderCornerContent(corner: ReaderPageCorner, content: ReaderCornerContent) {
        _uiState.value = _uiState.value.withReaderCornerContent(corner, content)
        viewModelScope.launch { dataStoreManager.saveReaderCornerContent(corner, content) }
    }

    fun saveParagraphSpacing(value: Float) {
        parser?.paragraphSpacingDp = value
        parser?.clearHtmlCache()  // 同步清缓存，确保 configure() 重新分页时拿到新内容
        _uiState.value = _uiState.value.copy(paragraphSpacing = value)
        viewModelScope.launch {
            dataStoreManager.saveParagraphSpacing(value)
            loadChapterContent()
        }
    }

    fun saveFirstLineIndent(value: Float) {
        parser?.firstLineIndentChars = value
        parser?.clearHtmlCache()  // 同步清缓存
        _uiState.value = _uiState.value.copy(firstLineIndent = value)
        viewModelScope.launch {
            dataStoreManager.saveFirstLineIndent(value)
            loadChapterContent()
        }
    }

    fun resetAdvancedReaderSettings() {
        parser?.paragraphSpacingDp = 2f
        parser?.firstLineIndentChars = 2f
        parser?.clearHtmlCache()
        _uiState.value = _uiState.value.copy(
            lineHeight = 1.5f,
            letterSpacing = 0f,
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
            readerEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
            readerTopLeftContent = defaultReaderCornerContent(ReaderPageCorner.TOP_LEFT),
            readerTopRightContent = defaultReaderCornerContent(ReaderPageCorner.TOP_RIGHT),
            readerBottomLeftContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_LEFT),
            readerBottomRightContent = defaultReaderCornerContent(ReaderPageCorner.BOTTOM_RIGHT)
        )
        viewModelScope.launch {
            dataStoreManager.resetAdvancedReaderSettings()
            loadChapterContent()
        }
    }

    /** 从 URI 导入字体文件到内部存储，返回文件路径 */
    suspend fun importFont(context: android.content.Context, uri: android.net.Uri): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fontDir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                val target = java.io.File(fontDir, "custom_font.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists() && target.length() > 0) target.absolutePath else null
            } catch (_: Exception) { null }
        }
    }

    /** 用户离开阅读页时调用（DisposableEffect.onDispose） */
    fun saveAndPause() {
        saveReadingSession()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    parser = BookParserFactory.createParser(book.format, context)

                    // 读取设置
                    val optimize = dataStoreManager.optimizeLayout(bookId).first()
                    val chineseMode = dataStoreManager.chineseMode().first()
                    val pageTransition = dataStoreManager.pageTransition().first()
                    val paragraphSpacing = dataStoreManager.paragraphSpacing().first()
                    val firstLineIndent = dataStoreManager.firstLineIndent().first()
                    val pdfPageMode = dataStoreManager.pdfPageMode.first()

                    // 应用段间距和首行缩进到 parser
                    parser!!.paragraphSpacingDp = paragraphSpacing
                    parser!!.firstLineIndentChars = firstLineIndent

                    val content = withContext(Dispatchers.IO) {
                        parser!!.parse(book.filePath)
                    }

                    val chapterCount = content.chapters.size
                    require(chapterCount > 0) { "书籍没有可阅读内容" }
                    val chapterTitles = content.chapters.map { it.title }
                    val tocEntries = content.tocEntries.ifEmpty {
                        content.chapters.map { com.huangder.lumibooks.util.parser.TocEntry(it.title, 1, it.index) }
                    }
                    val progressFraction = book.readingProgress * chapterCount
                    val startChapter = progressFraction.toInt().coerceIn(0, chapterCount - 1)
                    val pageFraction = (progressFraction - startChapter).coerceIn(0f, 1f)

                    val isPdf = book.format.name == "PDF"
                    _uiState.value = _uiState.value.copy(
                        book = book,
                        chapterCount = chapterCount,
                        chapterTitles = chapterTitles,
                        tocEntries = tocEntries,
                        currentChapterIndex = startChapter,
                        pendingPageFraction = pageFraction,
                        useNewEngine = !isPdf,  // TXT/EPUB 用新 Canvas 引擎，PDF 保留 WebView
                        optimizeLayout = optimize,
                        chineseMode = chineseMode,
                        pageTransition = pageTransition,
                        paragraphSpacing = paragraphSpacing,
                        firstLineIndent = firstLineIndent,
                        pdfPageMode = pdfPageMode,
                        error = null
                    )

                    if (isPdf) {
                        // PDF 使用独立 PdfViewerScreen，不生成 Base64 HTML。
                        _uiState.value = _uiState.value.copy(isLoading = false, pageReady = true)
                    }
                    loadBookmarks()
                    loadNotes()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "书籍未找到"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
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
            currentChapterIndex = state.currentChapterIndex + 1
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
            currentChapterIndex = state.currentChapterIndex - 1
        )
        if (!state.useNewEngine) loadChapterContent(startPage = targetPage)
        saveProgress()
    }

    /**
     * 跳转到指定章节（由Slider调用）
     */
    fun setChapter(chapterIndex: Int) {
        if (chapterIndex == _uiState.value.currentChapterIndex) return
        _uiState.value = _uiState.value.copy(currentChapterIndex = chapterIndex)
        if (!_uiState.value.useNewEngine) loadChapterContent()
        saveProgress()
    }

    /**
     * 🔥 仅更新章节索引（不加载 HTML）。TXT Bitmap 引擎跨章翻页时用。
     */
    fun updatePosition(chapterIndex: Int, pageIndex: Int, totalPages: Int) {
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            currentPageIndex = pageIndex,
            totalPages = totalPages,
            pageReady = true
        )
        saveProgress()
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
        preloadAdjacentChapters()
    }

    /**
     * 新 Canvas 引擎页面切换回调。
     */
    fun onNewEnginePageChanged(
        globalPage: Int,
        chapterIndex: Int,
        pageInChapter: Int,
        chapterTotalPages: Int
    ) {
        _uiState.value = _uiState.value.copy(
            globalPageIndex = globalPage,
            currentChapterIndex = chapterIndex,
            currentPageIndex = pageInChapter,
            totalPages = chapterTotalPages,
            pageReady = true
        )
        ttsController.onPageVisible(bookId, chapterIndex, pageInChapter)
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            // 🔥 如果是恢复进度（pendingPageFraction > 0），不在此处 saveProgress
            // ReaderScreen 会在跳转到正确页面后再触发保存
            if (_uiState.value.pendingPageFraction <= 0f) {
                saveProgress()
            }
            return
        }
        saveProgress()
    }

    fun startTts(
        pageProvider: suspend (chapterIndex: Int, pageIndex: Int) -> TtsPageContent?
    ) {
        val state = _uiState.value
        val book = state.book ?: return
        if (!state.useNewEngine || state.isLoading) return

        ContextCompat.startForegroundService(
            context,
            TtsForegroundService.startIntent(context, book.title)
        )
        viewModelScope.launch {
            val result = ttsController.start(
                bookId = book.id,
                provider = pageProvider,
                startChapter = state.currentChapterIndex,
                startPage = state.currentPageIndex
            )
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    ttsErrorMessage = context.getString(R.string.tts_unavailable)
                )
            }
        }
    }

    fun stopTts() {
        ttsController.stop()
    }

    fun toggleTtsPlayPause() {
        when (_uiState.value.ttsPlaybackState) {
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

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { ttsController.setSpeechRate(rate) }
    }

    fun clearTtsError() {
        _uiState.value = _uiState.value.copy(ttsErrorMessage = null)
    }

    /** WebView 分页完成后调用 */
    fun onPaginationDone() {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun clearPendingPageFraction() {
        _uiState.value = _uiState.value.copy(pendingPageFraction = 0f)
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
     *  返回 CharSequence 支持标题格式化（Spannable）。 */
    fun getChapterText(index: Int): CharSequence? {
        val raw = try { parser?.getChapterContent(index) } catch (_: Exception) { null } ?: return null
        if (raw.isEmpty()) return raw

        val isTxt = raw !is Spanned
        var skipFirstParagraphIndent = false
        val chapterText = if (isTxt) {
            // 只有解析器识别出的真实章节标题才加大加粗。无目录 TXT 的章节标题是
            // “第 N 章 + 正文摘要”的合成值，不能把原始首段整行误当成标题。
            val newlineIdx = raw.indexOf('\n')
            if (newlineIdx > 0) {
                val title = raw.substring(0, newlineIdx)
                val parsedChapterTitle = _uiState.value.chapterTitles.getOrNull(index).orEmpty()
                if (shouldStyleTxtChapterTitle(title, parsedChapterTitle)) {
                    skipFirstParagraphIndent = true
                    val body = raw.substring(newlineIdx + 1)
                    val spannable = SpannableString("$title\n\n$body")
                    val titleEnd = title.length
                    spannable.setSpan(
                        AbsoluteSizeSpan(22, true),
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

        val state = _uiState.value
        return ReaderParagraphFormatter.applyFirstLineIndent(
            text = chapterText,
            indentCharacters = state.firstLineIndent,
            textSizePx = state.fontSize * context.resources.displayMetrics.scaledDensity,
            paragraphSpacingPx = state.paragraphSpacing * context.resources.displayMetrics.density,
            skipFirstNonEmptyParagraph = skipFirstParagraphIndent
        )
    }

    /** 在 IO 线程解析 EPUB 相对路径/锚点，返回原生阅读引擎可跳转的位置。 */
    suspend fun resolveBookLink(sourceChapterIndex: Int, href: String): BookLinkTarget? {
        return withContext(Dispatchers.IO) {
            parser?.resolveLink(sourceChapterIndex, href)
        }
    }

    /**
     * 预渲染命中时调用：仅更新章节索引和进度，不更新 chapterHtml（避免触发 loadDataWithBaseURL 破坏 DOM swap）
     */
    fun onChapterSwapped(direction: Int) {
        val state = _uiState.value
        val newIdx = (state.currentChapterIndex + direction).coerceIn(0, state.chapterCount - 1)
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = newIdx,
            currentPageIndex = 0
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

    // -- 书签和笔记 --

    fun addBookmark() {
        val state = _uiState.value
        val book = state.book ?: return
        val bookmark = Bookmark(
            bookId = book.id,
            chapterIndex = state.currentChapterIndex,
            position = state.currentPageIndex.toFloat(),
            title = "第${state.currentChapterIndex + 1}章 第${state.currentPageIndex + 1}页",
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
            title = "第${pageIndex + 1}页  $bookTitle",
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch { readingRepository.insertBookmark(bookmark) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { readingRepository.deleteBookmark(bookmark) }
    }

    fun addNote(
        selectedText: String,
        noteText: String,
        chapterIndex: Int = -1,
        startPosition: Int = 0,
        endPosition: Int = 0,
        color: String = "#FFEB3B"
    ) {
        val state = _uiState.value
        val book = state.book ?: return
        val note = Note(
            bookId = book.id,
            chapterIndex = if (chapterIndex >= 0) chapterIndex else state.currentChapterIndex,
            startPosition = if (startPosition > 0 || chapterIndex >= 0) startPosition else 0,
            endPosition = if (endPosition > 0 || chapterIndex >= 0) endPosition else selectedText.length,
            selectedText = selectedText,
            note = noteText,
            color = color,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch { readingRepository.insertNote(note) }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { readingRepository.updateNote(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { readingRepository.deleteNote(note) }
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
            readingRepository.getNotesByBookId(bookId).collect { list ->
                _notes.value = list
            }
        }
    }

    private fun saveProgress() {
        val state = _uiState.value
        val book = state.book ?: return
        if (state.chapterCount == 0) return

        // 进度 = (当前章节 + 页内偏移) / 总章节
        val pageProgress = if (state.totalPages > 0) {
            state.currentPageIndex.toFloat() / state.totalPages
        } else 0f
        val progress = ((state.currentChapterIndex + pageProgress) / state.chapterCount).coerceIn(0f, 1f)

        viewModelScope.launch {
            bookRepository.updateReadingProgress(book.id, progress)
            bookRepository.updateLastReadTime(book.id, System.currentTimeMillis())
        }
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
        val matchLength: Int       // 匹配文本长度
    )

    /**
     * 全书搜索关键词，返回匹配列表（章节索引 + 字符偏移 + 上下文）。
     * 搜索范围限制在前 [maxChapters] 章内（默认200章），防止大书超时。
     */
    fun searchAllChapters(query: String, maxChapters: Int = 200): List<SearchResult> {
        if (query.isEmpty()) return emptyList()

        val p = parser ?: return emptyList()
        val totalChapters = _uiState.value.chapterCount.coerceAtMost(maxChapters)
        val titles = _uiState.value.chapterTitles
        val results = mutableListOf<SearchResult>()

        for (chIdx in 0 until totalChapters) {
            val text = try {
                p.getChapterContent(chIdx).toString()
            } catch (_: Exception) { continue }

            var searchStart = 0
            while (true) {
                val foundIdx = text.indexOf(query, searchStart, ignoreCase = true)
                if (foundIdx == -1) break

                val ctxStart = (foundIdx - 12).coerceAtLeast(0)
                val ctxEnd = (foundIdx + query.length + 20).coerceAtMost(text.length)
                val context = text.substring(ctxStart, ctxEnd)
                    .replace('\n', ' ')
                    .replace('\r', ' ')

                val title = titles.getOrElse(chIdx) { "第${chIdx + 1}章" }.ifBlank { "第${chIdx + 1}章" }
                results.add(
                    SearchResult(
                        chapterIndex = chIdx,
                        chapterTitle = title,
                        charOffset = foundIdx,
                        context = context,
                        matchLength = query.length
                    )
                )
                searchStart = foundIdx + query.length
                if (results.size >= 200) break  // 最多200条结果
            }
            if (results.size >= 200) break
        }
        return results
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
        (parser as? PdfParser)?.close()
        preloadCache.clear()
        super.onCleared()
    }
}
