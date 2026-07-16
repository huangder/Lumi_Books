package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.ReadingRecord
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.parser.BookParser
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    val marginHorizDp: Float = 44f,
    val marginVertDp: Float = 72f,
    val readerTheme: String = "day",
    /** 亮度 0f~1f，-1f 跟随系统 */
    val brightness: Float = -1f,
    /** 自定义导入字体文件路径 */
    val customFontPath: String? = null,
    val error: String? = null,
    /** 全局页码（跨所有章节），新引擎用 */
    val globalPageIndex: Int = 0,
    /** 是否使用新 Canvas 引擎 */
    val useNewEngine: Boolean = true,
    /** 是否使用优化排版（per-book） */
    val optimizeLayout: Boolean = true
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

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
        loadBook()
        loadReaderSettings()
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
            dataStoreManager.marginHoriz.collectLatest { mh ->
                _uiState.value = _uiState.value.copy(marginHorizDp = mh)
            }
        }
        viewModelScope.launch {
            dataStoreManager.marginVert.collectLatest { mv ->
                _uiState.value = _uiState.value.copy(marginVertDp = mv)
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

    fun saveMarginHoriz(mh: Float) {
        _uiState.value = _uiState.value.copy(marginHorizDp = mh)
        viewModelScope.launch { dataStoreManager.saveMarginHoriz(mh) }
    }

    fun saveMarginVert(mv: Float) {
        _uiState.value = _uiState.value.copy(marginVertDp = mv)
        viewModelScope.launch { dataStoreManager.saveMarginVert(mv) }
    }

    fun saveReaderTheme(theme: String) {
        _uiState.value = _uiState.value.copy(readerTheme = theme)
        viewModelScope.launch { dataStoreManager.saveReaderTheme(theme) }
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
                    val content = withContext(Dispatchers.IO) {
                        parser!!.parse(book.filePath)
                    }

                    val chapterCount = content.chapters.size
                    val chapterTitles = content.chapters.map { it.title }
                    // 层级目录：优先使用 NCX/nav 解析的 tocEntries，回退到 flat list
                    val tocEntries = content.tocEntries.ifEmpty {
                        content.chapters.map { com.huangder.lumibooks.util.parser.TocEntry(it.title, 1, it.index) }
                    }
                    val progressFraction = book.readingProgress * chapterCount
                    val startChapter = progressFraction.toInt().coerceIn(0, chapterCount - 1)
                    val pageFraction = (progressFraction - startChapter).coerceIn(0f, 1f)

                    val isPdf = book.format.name == "PDF"
                    // 读取 per-book 排版设置
                    val optimize = dataStoreManager.optimizeLayout(bookId).first()
                    _uiState.value = _uiState.value.copy(
                        book = book,
                        chapterCount = chapterCount,
                        chapterTitles = chapterTitles,
                        tocEntries = tocEntries,
                        currentChapterIndex = startChapter,
                        pendingPageFraction = pageFraction,
                        useNewEngine = !isPdf,  // TXT/EPUB 用新 Canvas 引擎，PDF 保留 WebView
                        optimizeLayout = optimize
                    )

                    loadChapterContent()
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
     * 参考 HiReader 的 3 章节缓冲区设计——永远保持相邻章节已加载。
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
        loadChapterContent()
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
        loadChapterContent(startPage = targetPage)
        saveProgress()
    }

    /**
     * 跳转到指定章节（由Slider调用）
     */
    fun setChapter(chapterIndex: Int) {
        if (chapterIndex == _uiState.value.currentChapterIndex) return
        _uiState.value = _uiState.value.copy(currentChapterIndex = chapterIndex)
        loadChapterContent()
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

        // EPUB: 已由 Html.fromHtml() 生成 Spanned（含标题/粗体/斜体/链接/分段），直接返回
        if (raw is Spanned) return raw

        // TXT: 纯文本无格式，首行标题加大加粗 + 后空一行
        val newlineIdx = raw.indexOf('\n')
        return if (newlineIdx > 0) {
            val title = raw.substring(0, newlineIdx)
            val body = raw.substring(newlineIdx + 1)
            val spannable = SpannableString("$title\n\n$body")
            // 标题：1.5x 字号 + 粗体
            val titleEnd = title.length
            spannable.setSpan(AbsoluteSizeSpan(22, true), 0, titleEnd, 0)  // 22sp
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, titleEnd, 0)
            spannable
        } else {
            raw  // 无换行，保持原样
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
}
