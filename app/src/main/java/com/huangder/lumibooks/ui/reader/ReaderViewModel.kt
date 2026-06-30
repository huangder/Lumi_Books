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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val chapterCount: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val chapterHtml: String = "",
    val isMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val pageReady: Boolean = false,
    val pendingPageFraction: Float = 0f,
    val fontSize: Float = 16f,
    val readerTheme: String = "day",
    val error: String? = null,
    /** 全局页码（跨所有章节），新引擎用 */
    val globalPageIndex: Int = 0,
    /** 是否使用新 Canvas 引擎 */
    val useNewEngine: Boolean = true
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
    }

    fun saveFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSize = size)
        viewModelScope.launch { dataStoreManager.saveFontSize(size) }
    }

    fun saveReaderTheme(theme: String) {
        _uiState.value = _uiState.value.copy(readerTheme = theme)
        viewModelScope.launch { dataStoreManager.saveReaderTheme(theme) }
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
                    val content = parser!!.parse(book.filePath)

                    val chapterCount = content.chapters.size
                    val progressFraction = book.readingProgress * chapterCount
                    val startChapter = progressFraction.toInt().coerceIn(0, chapterCount - 1)
                    val pageFraction = (progressFraction - startChapter).coerceIn(0f, 1f)

                    val isPdf = book.format.name == "PDF"
                    _uiState.value = _uiState.value.copy(
                        book = book,
                        chapterCount = chapterCount,
                        currentChapterIndex = startChapter,
                        pendingPageFraction = pageFraction,
                        useNewEngine = !isPdf  // TXT/EPUB 用新 Canvas 引擎，PDF 保留 WebView
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
        val html = getChapterHtml(state.currentChapterIndex)
        android.util.Log.e("PG", "loadChapterContent: chapter=" + state.currentChapterIndex + " html.length=" + (html?.length ?: 0))
        _uiState.value = _uiState.value.copy(chapterHtml = html, currentPageIndex = startPage, pageReady = false)
        // 🔥 激进预加载：进入章节后立即在后台拉取前后相邻章节到 preloadCache
        eagerPreloadAdjacent(state.currentChapterIndex)
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
        viewModelScope.launch {
            indices.forEach { idx ->
                try {
                    preloadCache[idx] = p.getChapterHtml(idx)
                    android.util.Log.d("PG", "eagerPreload done: chapter $idx")
                } catch (_: Exception) {
                    android.util.Log.d("PG", "eagerPreload failed: chapter $idx")
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
                        preloadCache[next] = parser.getChapterHtml(next)
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
                            preloadCache[next2] = parser.getChapterHtml(next2)
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
                        preloadCache[prev] = parser.getChapterHtml(prev)
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
                            preloadCache[prev2] = parser.getChapterHtml(prev2)
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
        val html = parser?.getChapterHtml(index) ?: ""
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

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { readingRepository.deleteBookmark(bookmark) }
    }

    fun addNote(selectedText: String, noteText: String) {
        val state = _uiState.value
        val book = state.book ?: return
        val note = Note(
            bookId = book.id,
            chapterIndex = state.currentChapterIndex,
            startPosition = 0,
            endPosition = selectedText.length,
            selectedText = selectedText,
            note = noteText,
            color = "#FFEB3B",
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch { readingRepository.insertNote(note) }
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

    private fun saveReadingSession() {
        val endTime = System.currentTimeMillis()
        val duration = endTime - sessionStartTime
        android.util.Log.e("READING", "saveReadingSession: duration=${duration}ms, bookId=$bookId")
        if (duration < 5000) {
            android.util.Log.e("READING", "Session too short, skipping")
            return
        }

        val record = ReadingRecord(
            bookId = bookId,
            date = TimeUtils.getCurrentDate(),
            duration = duration,
            startTime = sessionStartTime,
            endTime = endTime
        )
        try {
            kotlinx.coroutines.runBlocking {
                readingRepository.insertRecord(record)
            }
            android.util.Log.e("READING", "Record saved: ${record.date} ${record.duration}ms")
        } catch (e: Exception) {
            android.util.Log.e("READING", "Save failed: ${e.message}")
        }
    }
}
