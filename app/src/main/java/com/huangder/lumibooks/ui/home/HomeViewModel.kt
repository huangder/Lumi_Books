package com.huangder.lumibooks.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.parser.BookParserFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** 每日阅读数据 */
data class DailyReading(val date: String, val duration: Long, val dayLabel: String)

data class HomeUiState(
    val books: List<Book> = emptyList(),
    val todayReadingTime: Long = 0,
    val dailyGoal: Int = 30, // 分钟
    val avatarUri: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortBy: SortBy = SortBy.LAST_READ,
    val isLoading: Boolean = false,
    val importMessage: String? = null,
    val error: String? = null,
    /** 当前日历周的阅读数据（周日至周六） */
    val weeklyData: List<DailyReading> = emptyList(),
    /** 连胜天数 */
    val streakDays: Int = 0
)

enum class SortBy {
    LAST_READ, TITLE, AUTHOR, DATE_ADDED
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayLabels = listOf(
        application.getString(R.string.day_sunday),
        application.getString(R.string.day_monday),
        application.getString(R.string.day_tuesday),
        application.getString(R.string.day_wednesday),
        application.getString(R.string.day_thursday),
        application.getString(R.string.day_friday),
        application.getString(R.string.day_saturday)
    )

    init {
        loadBooks()
        loadTodayReadingTime()
        loadAvatar()
        loadWeeklyData()
    }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                bookRepository.getAllBooks().collectLatest { books ->
                    val sortedBooks = sortBooks(books, _uiState.value.sortBy)
                    _uiState.value = _uiState.value.copy(
                        books = sortedBooks,
                        isLoading = false,
                        error = null
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

    private fun loadTodayReadingTime() {
        viewModelScope.launch {
            val today = TimeUtils.getCurrentDate()
            readingRepository.getTotalDurationByDate(today).collectLatest { duration ->
                _uiState.value = _uiState.value.copy(
                    todayReadingTime = duration ?: 0
                )
            }
        }
    }

    private fun loadAvatar() {
        viewModelScope.launch {
            dataStoreManager.avatarUri.collectLatest { uri ->
                _uiState.value = _uiState.value.copy(avatarUri = uri)
            }
        }
    }

    private fun loadWeeklyData() {
        viewModelScope.launch {
            // 日历周：从本周日开始，到本周六结束
            val startOfWeek = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.SUNDAY
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val today = LocalDate.now()
            combine(
                dataStoreManager.dailyGoal,
                readingRepository.getDailyTotalsBetween(READING_HISTORY_START_DATE, today.toString())
            ) { goal, dailyTotals ->
                goal to dailyTotals
            }.collectLatest { (goal, dailyTotals) ->
                val durationMap = dailyTotals.associate { it.date to it.totalDuration }
                val weeklyData = (0..6).map { i ->
                    val cal = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val date = dateFormat.format(cal.time)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
                    DailyReading(date, durationMap[date] ?: 0L, dayLabels[dayOfWeek])
                }
                val streak = ReadingStreakCalculator.calculate(
                    dailyDurations = durationMap,
                    today = today,
                    goalDurationMs = goal * 60 * 1000L
                )

                _uiState.value = _uiState.value.copy(
                    dailyGoal = goal,
                    weeklyData = weeklyData,
                    streakDays = streak
                )
            }
        }
    }

    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(minutes)
            _uiState.value = _uiState.value.copy(dailyGoal = minutes)
        }
    }

    fun searchBooks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadBooks()
        } else {
            viewModelScope.launch {
                bookRepository.searchBooks(query).collectLatest { books ->
                    val sortedBooks = sortBooks(books, _uiState.value.sortBy)
                    _uiState.value = _uiState.value.copy(
                        books = sortedBooks,
                        isSearchActive = true
                    )
                }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isSearchActive = active)
        if (!active) {
            _uiState.value = _uiState.value.copy(searchQuery = "")
            loadBooks()
        }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
        val sortedBooks = sortBooks(_uiState.value.books, sortBy)
        _uiState.value = _uiState.value.copy(books = sortedBooks)
    }

    private fun sortBooks(books: List<Book>, sortBy: SortBy): List<Book> {
        return when (sortBy) {
            SortBy.LAST_READ -> books.sortedByDescending { it.lastReadTime }
            SortBy.TITLE -> books.sortedBy { it.title.lowercase() }
            SortBy.AUTHOR -> books.sortedBy { it.author.lowercase() }
            SortBy.DATE_ADDED -> books.sortedByDescending { it.createdAt }
        }
    }

    fun insertBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.insertBook(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * 异步导入书籍：文件复制 + EPUB/PDF/TXT解析 + 数据库插入
     * 全部在 Dispatchers.IO 上执行，不阻塞主线程
     */
    fun importBook(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importMessage = context.getString(R.string.importing))
            try {
                withContext(Dispatchers.IO) {
                    val fileName = FileUtils.getFileNameFromUri(context, uri) ?: return@withContext
                    val extension = FileUtils.getFileExtension(fileName)
                    if (extension !in listOf("epub", "pdf", "txt")) return@withContext

                    val file = FileUtils.copyFileToInternal(context, uri, fileName) ?: return@withContext
                    val format = when (extension) {
                        "epub" -> BookFormat.EPUB
                        "pdf" -> BookFormat.PDF
                        else -> BookFormat.TXT
                    }

                    // 只提取封面，不解析全部章节（避免大文件卡死）
                    val coverPath = try {
                        val parser = BookParserFactory.createParser(format, context)
                        parser.extractCoverPath(file.absolutePath)
                    } catch (_: Exception) { null }

                    val book = Book(
                        id = FileUtils.generateBookId(),
                        title = fileName.substringBeforeLast('.'),
                        author = context.getString(R.string.book_author_unknown),
                        filePath = file.absolutePath,
                        coverPath = coverPath,
                        format = format,
                        lastReadTime = System.currentTimeMillis(),
                        readingProgress = 0f,
                        createdAt = System.currentTimeMillis()
                    )
                    bookRepository.insertBook(book)
                }
                _uiState.value = _uiState.value.copy(importMessage = context.getString(R.string.import_complete))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importMessage = context.getString(R.string.import_failed, e.message ?: ""))
            }
        }
    }

    fun clearImportMessage() {
        _uiState.value = _uiState.value.copy(importMessage = null)
    }

    /**
     * 重新提取原始封面（用于移除自定义封面后恢复）
     * 在 Dispatchers.IO 上执行 parse，不阻塞主线程
     */
    fun reExtractCover(context: Context, book: Book) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val parser = BookParserFactory.createParser(book.format, context)
                    val originalCover = parser.extractCoverPath(book.filePath)
                    bookRepository.updateBook(book.copy(coverPath = originalCover))
                } catch (_: Exception) { }
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.deleteBook(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            try {
                bookRepository.updateBook(book)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val READING_HISTORY_START_DATE = "1970-01-01"
    }
}
