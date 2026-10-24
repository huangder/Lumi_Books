package com.huangder.lumibooks.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.home.ReadingStreakCalculator
import com.huangder.lumibooks.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
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
    val weeklyData: List<DailyReading> = emptyList(),
    val mostReadBooks: List<MostReadBook> = emptyList(),
    val annotationSummary: AnnotationPeriodSummary = AnnotationPeriodSummary(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val monthlyDailyData: Map<String, Long> = emptyMap(),
    val yearlyDailyData: Map<String, Long> = emptyMap(),
    val selectedTab: Int = 0,
    val displayMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val displayYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val displayWeekOffset: Int = 0,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    // Each section follows its own period with flatMapLatest, so paging replaces
    // the previous Room subscription instead of stacking another collector.
    init {
        observeDailyGoal()
        observeToday()
        observeStreaks()
        observeWeek()
        observeMonth()
        observeYear()
        observeSelectedPeriod()
    }

    private fun observeDailyGoal() {
        viewModelScope.launch {
            dataStoreManager.dailyGoal.reportErrors().collect { goal ->
                _uiState.update { it.copy(dailyGoal = goal) }
            }
        }
    }

    private fun observeToday() {
        viewModelScope.launch {
            readingRepository.getTotalDurationByDate(TimeUtils.getCurrentDate()).reportErrors().collect { duration ->
                _uiState.update { it.copy(todayReadingTime = duration ?: 0L) }
            }
        }
    }
    private fun observeStreaks() {
        viewModelScope.launch {
            combine(
                dataStoreManager.dailyGoal,
                readingRepository.getDailyTotalsBetween(READING_HISTORY_START_DATE, TimeUtils.getCurrentDate())
            ) { goal, dailyTotals ->
                val durations = dailyTotals.associate { it.date to it.totalDuration }
                val goalMs = goal * 60 * 1000L
                ReadingStreakCalculator.calculate(durations, LocalDate.now(), goalMs) to
                    ReadingStreakCalculator.longest(durations, goalMs)
            }.reportErrors().collect { (current, longest) ->
                _uiState.update { it.copy(currentStreak = current, longestStreak = longest) }
            }
        }
    }

    private fun observeWeek() {
        viewModelScope.launch {
            _uiState.map { it.displayWeekOffset }
                .distinctUntilChanged()
                .flatMapLatest { offset ->
                    val period = StatisticsPeriod.week(LocalDate.now(), offset)
                    readingRepository.getDailyTotalsBetween(period.startDate, period.endDate).map { dailyTotals ->
                        val durations = dailyTotals.associate { it.date to it.totalDuration }
                        period.days.map { day ->
                            val date = day.recordDate()
                            DailyReading(
                                date = date,
                                duration = durations[date] ?: 0L,
                                dayOfWeek = dayFormat.format(day.toDate())
                            )
                        }
                    }
                }
                .reportErrors()
                .collect { weeklyData -> _uiState.update { it.copy(weeklyData = weeklyData) } }
        }
    }

    private fun observeMonth() {
        viewModelScope.launch {
            _uiState.map { it.displayYear to it.displayMonth }
                .distinctUntilChanged()
                .flatMapLatest { (year, month) ->
                    val period = StatisticsPeriod.month(year, month)
                    readingRepository.getDailyTotalsBetween(period.startDate, period.endDate)
                }
                .reportErrors()
                .collect { dailyTotals ->
                    _uiState.update {
                        it.copy(
                            monthlyDailyData = dailyTotals.associate { total -> total.date to total.totalDuration },
                            monthlyReadingTime = dailyTotals.sumOf { total -> total.totalDuration }
                        )
                    }
                }
        }
    }
    private fun observeYear() {
        viewModelScope.launch {
            _uiState.map { it.displayYear }
                .distinctUntilChanged()
                .flatMapLatest { year ->
                    val period = StatisticsPeriod.year(year)
                    readingRepository.getDailyTotalsBetween(period.startDate, period.endDate)
                }
                .reportErrors()
                .collect { dailyTotals ->
                    _uiState.update {
                        it.copy(yearlyDailyData = dailyTotals.associate { total -> total.date to total.totalDuration })
                    }
                }
        }
    }

    /** Most-read books and the annotation card follow the selected week/month/year tab. */
    private fun observeSelectedPeriod() {
        val periods = _uiState.map { it.selectedPeriod() }.distinctUntilChanged()
        viewModelScope.launch {
            periods.flatMapLatest { period ->
                readingRepository.getMostReadBooksBetween(period.startDate, period.endDate, MOST_READ_LIMIT)
                    .map { durations ->
                        durations.mapNotNull { duration ->
                            val book = bookRepository.getBookById(duration.bookId) ?: return@mapNotNull null
                            MostReadBook(
                                bookId = duration.bookId,
                                title = book.title,
                                author = book.author,
                                coverPath = book.coverPath,
                                totalDuration = duration.totalDuration
                            )
                        }
                    }
            }.reportErrors().collect { books -> _uiState.update { it.copy(mostReadBooks = books) } }
        }
        viewModelScope.launch {
            periods.flatMapLatest { period ->
                val zone = ZoneId.systemDefault()
                val startMs = period.startMillis(zone)
                val endMs = period.endMillis(zone)
                combine(
                    readingRepository.observeTextNotesCreatedBetween(startMs, endMs),
                    readingRepository.observeBookmarksCreatedBetween(startMs, endMs)
                ) { notes, bookmarks -> summarizeAnnotations(notes, bookmarks) }
            }.reportErrors().collect { summary -> _uiState.update { it.copy(annotationSummary = summary) } }
        }
    }

    private fun StatisticsUiState.selectedPeriod(): StatisticsPeriod = when (selectedTab) {
        0 -> StatisticsPeriod.week(LocalDate.now(), displayWeekOffset)
        1 -> StatisticsPeriod.month(displayYear, displayMonth)
        else -> StatisticsPeriod.year(displayYear)
    }
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun previousWeek() {
        _uiState.update { it.copy(displayWeekOffset = it.displayWeekOffset - 1) }
    }

    fun nextWeek() {
        _uiState.update { if (it.displayWeekOffset >= 0) it else it.copy(displayWeekOffset = it.displayWeekOffset + 1) }
    }

    fun previousMonth() {
        _uiState.update {
            if (it.displayMonth == 0) it.copy(displayMonth = 11, displayYear = it.displayYear - 1)
            else it.copy(displayMonth = it.displayMonth - 1)
        }
    }

    fun nextMonth() {
        val now = Calendar.getInstance()
        _uiState.update {
            // 不超过当前月
            val atCurrentMonth = it.displayYear > now.get(Calendar.YEAR) ||
                (it.displayYear == now.get(Calendar.YEAR) && it.displayMonth >= now.get(Calendar.MONTH))
            when {
                atCurrentMonth -> it
                it.displayMonth == 11 -> it.copy(displayMonth = 0, displayYear = it.displayYear + 1)
                else -> it.copy(displayMonth = it.displayMonth + 1)
            }
        }
    }

    fun previousYear() {
        _uiState.update { it.copy(displayYear = it.displayYear - 1) }
    }

    fun nextYear() {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        _uiState.update {
            if (it.displayYear >= currentYear) return@update it
            val year = it.displayYear + 1
            // The month tab shares displayYear; never land it on a future month.
            val month = if (year == currentYear) minOf(it.displayMonth, now.get(Calendar.MONTH)) else it.displayMonth
            it.copy(displayYear = year, displayMonth = month)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun <T> Flow<T>.reportErrors(): Flow<T> = catch { error ->
        _uiState.update { it.copy(error = error.message) }
    }

    // Match ReaderViewModel's record keys, which are written with TimeUtils' default-locale format.
    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
    private fun LocalDate.recordDate(): String = dateFormat.format(toDate())
    private val StatisticsPeriod.startDate: String get() = start.recordDate()
    private val StatisticsPeriod.endDate: String get() = endInclusive.recordDate()

    private companion object {
        const val READING_HISTORY_START_DATE = "1970-01-01"
        const val MOST_READ_LIMIT = 5
    }
}
