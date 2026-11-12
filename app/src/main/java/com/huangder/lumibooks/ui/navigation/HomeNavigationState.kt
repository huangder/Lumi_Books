package com.huangder.lumibooks.ui.navigation

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.home.DailyReading
import com.huangder.lumibooks.ui.home.HomeUiState

/** Only values actually rendered by navigation; download progress is read at action time. */
internal data class HomeNavigationState(
    val isLoading: Boolean,
    val importMessage: String?,
    val archiveNoticePending: Boolean,
    val tagMessage: String?,
    val error: String?,
    val lastReadBook: Book?,
    val hiddenBookIds: Set<String>,
    val authorizedBookDirectories: List<String>,
    val importBooksLayoutMode: Int,
    val folderNames: Map<String, String>,
    val todayReadingTime: Long,
    val dailyGoal: Int,
    val weeklyData: List<DailyReading>,
    val streakDays: Int
)

internal fun HomeUiState.forNavigation() = HomeNavigationState(
    isLoading, importMessage, archiveNoticePending, tagMessage, error,
    books.maxByOrNull { it.lastReadTime }, hiddenBooks.mapTo(HashSet()) { it.id },
    authorizedBookDirectories, importBooksLayoutMode, folders.associate { it.id to it.name },
    todayReadingTime, dailyGoal, weeklyData, streakDays
)
