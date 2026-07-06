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
    val monthlyDailyData: Map<String, Long> = emptyMap(),
    val yearlyDailyData: Map<String, Long> = emptyMap(),
    val selectedTab: Int = 0,
    val displayMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val displayYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val displayWeekOffset: Int = 0,
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
                loadMonthlyDailyData()
                loadYearlyDailyData()
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

    private fun loadWeeklyData(weekOffset: Int = 0) {
        viewModelScope.launch {
            val startOfWeek = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                add(Calendar.WEEK_OF_YEAR, weekOffset)
            }
            val endOfWeek = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }

            val startDate = dateFormat.format(startOfWeek.time)
            val endDate = dateFormat.format(endOfWeek.time)

            readingRepository.getDailyTotalsBetween(startDate, endDate).collectLatest { dailyTotals ->
                val durationMap = dailyTotals.associate { it.date to it.totalDuration }
                val weeklyData = (0..6).map { i ->
                    val cal = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val date = dateFormat.format(cal.time)
                    DailyReading(
                        date = date,
                        duration = durationMap[date] ?: 0,
                        dayOfWeek = dayFormat.format(cal.time)
                    )
                }
                _uiState.value = _uiState.value.copy(weeklyData = weeklyData)
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

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun previousWeek() {
        val newOffset = _uiState.value.displayWeekOffset - 1
        _uiState.value = _uiState.value.copy(displayWeekOffset = newOffset)
        loadWeeklyData(newOffset)
    }

    fun nextWeek() {
        if (_uiState.value.displayWeekOffset >= 0) return
        val newOffset = _uiState.value.displayWeekOffset + 1
        _uiState.value = _uiState.value.copy(displayWeekOffset = newOffset)
        loadWeeklyData(newOffset)
    }

    fun previousMonth() {
        val current = _uiState.value
        var newMonth = current.displayMonth - 1
        var newYear = current.displayYear
        if (newMonth < 0) {
            newMonth = 11
            newYear--
        }
        _uiState.value = current.copy(displayMonth = newMonth, displayYear = newYear)
        loadMonthlyDailyData(newYear, newMonth)
    }

    fun nextMonth() {
        val current = _uiState.value
        val now = Calendar.getInstance()
        // 不超过当前月
        if (current.displayYear == now.get(Calendar.YEAR) && current.displayMonth == now.get(Calendar.MONTH)) return
        var newMonth = current.displayMonth + 1
        var newYear = current.displayYear
        if (newMonth > 11) {
            newMonth = 0
            newYear++
        }
        _uiState.value = current.copy(displayMonth = newMonth, displayYear = newYear)
        loadMonthlyDailyData(newYear, newMonth)
    }

    fun previousYear() {
        val current = _uiState.value
        val newYear = current.displayYear - 1
        _uiState.value = current.copy(displayYear = newYear)
        loadYearlyDailyData(newYear)
    }

    fun nextYear() {
        val current = _uiState.value
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (current.displayYear >= currentYear) return
        val newYear = current.displayYear + 1
        _uiState.value = current.copy(displayYear = newYear)
        loadYearlyDailyData(newYear)
    }

    private fun loadMonthlyDailyData(year: Int = _uiState.value.displayYear, month: Int = _uiState.value.displayMonth) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startDate = dateFormat.format(calendar.time)

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endDate = dateFormat.format(calendar.time)

            readingRepository.getDailyTotalsBetween(startDate, endDate).collectLatest { dailyTotals ->
                _uiState.value = _uiState.value.copy(
                    monthlyDailyData = dailyTotals.associate { it.date to it.totalDuration },
                    monthlyReadingTime = dailyTotals.sumOf { it.totalDuration }
                )
            }
        }
    }

    private fun loadYearlyDailyData(year: Int = _uiState.value.displayYear) {
        viewModelScope.launch {
            val startDate = String.format("%04d-01-01", year)
            val endDate = String.format("%04d-12-31", year)

            readingRepository.getDailyTotalsBetween(startDate, endDate).collectLatest { dailyTotals ->
                _uiState.value = _uiState.value.copy(
                    yearlyDailyData = dailyTotals.associate { it.date to it.totalDuration }
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
