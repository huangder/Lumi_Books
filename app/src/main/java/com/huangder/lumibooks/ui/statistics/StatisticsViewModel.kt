package com.huangder.lumibooks.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dao.BookDuration
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DailyReading(
    val date: String,
    val duration: Long,
    val dayOfWeek: String
)

data class MostReadBook(
    val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val totalDuration: Long
)

data class StatisticsUiState(
    val todayReadingTime: Long = 0,
    val monthlyReadingTime: Long = 0,
    val dailyGoal: Int = 30,
    val goalProgress: Float = 0f,
    val weeklyData: List<DailyReading> = emptyList(),
    val mostReadBooks: List<MostReadBook> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
    private val bookRepository: BookRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                loadTodayReadingTime()
                loadMonthlyReadingTime()
                loadDailyGoal()
                loadWeeklyData()
                loadMostReadBooks()
                _uiState.value = _uiState.value.copy(isLoading = false)
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
                val todayTime = duration ?: 0
                _uiState.value = _uiState.value.copy(
                    todayReadingTime = todayTime,
                    goalProgress = if (_uiState.value.dailyGoal > 0) {
                        (todayTime.toFloat() / (_uiState.value.dailyGoal * 60 * 1000)).coerceIn(0f, 1f)
                    } else 0f
                )
            }
        }
    }

    private fun loadMonthlyReadingTime() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val startDate = dateFormat.format(calendar.apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.time)

            val endDate = dateFormat.format(calendar.apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.time)

            readingRepository.getTotalDurationBetweenDates(startDate, endDate).collectLatest { duration ->
                _uiState.value = _uiState.value.copy(
                    monthlyReadingTime = duration ?: 0
                )
            }
        }
    }

    private fun loadDailyGoal() {
        viewModelScope.launch {
            dataStoreManager.dailyGoal.collectLatest { goal ->
                _uiState.value = _uiState.value.copy(dailyGoal = goal)
            }
        }
    }

    private fun loadWeeklyData() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val weeklyData = mutableListOf<DailyReading>()

            for (i in 6 downTo 0) {
                val date = dateFormat.format(calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                }.time)

                val dayOfWeek = dayFormat.format(calendar.time)

                readingRepository.getTotalDurationByDate(date).collectLatest { duration ->
                    weeklyData.add(
                        DailyReading(
                            date = date,
                            duration = duration ?: 0,
                            dayOfWeek = dayOfWeek
                        )
                    )

                    if (weeklyData.size == 7) {
                        _uiState.value = _uiState.value.copy(
                            weeklyData = weeklyData.sortedBy { it.date }
                        )
                    }
                }

                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun loadMostReadBooks() {
        viewModelScope.launch {
            readingRepository.getMostReadBooks(5).collectLatest { bookDurations ->
                val mostRead = bookDurations.mapNotNull { bd ->
                    val book = bookRepository.getBookById(bd.bookId) ?: return@mapNotNull null
                    MostReadBook(
                        bookId = bd.bookId,
                        title = book.title,
                        author = book.author,
                        coverPath = book.coverPath,
                        totalDuration = bd.totalDuration
                    )
                }
                _uiState.value = _uiState.value.copy(mostReadBooks = mostRead)
            }
        }
    }

    fun updateDailyGoal(goal: Int) {
        viewModelScope.launch {
            dataStoreManager.saveDailyGoal(goal)
            _uiState.value = _uiState.value.copy(dailyGoal = goal)
            loadTodayReadingTime()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
