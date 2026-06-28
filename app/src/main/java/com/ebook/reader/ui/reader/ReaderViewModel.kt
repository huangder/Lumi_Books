package com.ebook.reader.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebook.reader.data.local.DataStoreManager
import com.ebook.reader.domain.model.Book
import com.ebook.reader.domain.model.Bookmark
import com.ebook.reader.domain.model.Note
import com.ebook.reader.domain.model.ReadingRecord
import com.ebook.reader.domain.repository.BookRepository
import com.ebook.reader.domain.repository.ReadingRepository
import com.ebook.reader.util.TimeUtils
import com.ebook.reader.util.parser.BookParser
import com.ebook.reader.util.parser.BookParserFactory
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
    val error: String? = null
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

                    _uiState.value = _uiState.value.copy(
                        book = book,
                        chapterCount = chapterCount,
                        currentChapterIndex = startChapter,
                        pendingPageFraction = pageFraction
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

    /** 预加载缓存：key=章节索引，value=HTML */
    private val preloadCache = mutableMapOf<Int, String>()

    /** 预加载相邻章节 */
    fun preloadAdjacentChapters() {
        val state = _uiState.value
        val parser = parser ?: return
        if (state.totalPages <= 0) return
        val progress = state.currentPageIndex.toFloat() / state.totalPages
        android.util.Log.e("PG", "preloadCheck: page=${state.currentPageIndex} total=${state.totalPages} progress=${progress}")

        // 过半 → 预加载下一章
        if (progress >= 0.4f) {
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
        }

        // 前 2 页 → 预加载上一章
        if (state.currentPageIndex <= 1) {
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
        }
    }

    /** 获取章节 HTML（优先用缓存） */
    fun getChapterHtml(index: Int): String {
        preloadCache[index]?.let { cached ->
            preloadCache.remove(index)
            return cached
        }
        return parser?.getChapterHtml(index) ?: ""
    }

    /** 获取相邻章节 HTML（用于预渲染，不清除缓存） */
    fun getAdjacentChapterHtml(index: Int): String? {
        return preloadCache[index] ?: try {
            parser?.getChapterHtml(index)
        } catch (_: Exception) { null }
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
