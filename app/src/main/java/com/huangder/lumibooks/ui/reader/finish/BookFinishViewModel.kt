package com.huangder.lumibooks.ui.reader.finish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class BookFinishUiState(
    val book: Book? = null,
    val totalDurationMs: Long = 0L,
    val activeDays: Int = 0,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val noteCount: Int = 0,
    val bookmarkCount: Int = 0,
    val review: String = "",
    val rating: Float = 0f,
    /** True when this finish page set the read flag, so the page can offer undo. */
    val markedByFinishPage: Boolean = false
) {
    val isMarkedRead: Boolean get() = book?.isManuallyMarkedRead == true
}

@HiltViewModel
class BookFinishViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {
    private val _state = MutableStateFlow(BookFinishUiState())
    val state: StateFlow<BookFinishUiState> = _state.asStateFlow()
    private var boundBookId: String? = null
    private var loadJob: Job? = null
    private var markedOnce = false

    fun bind(bookId: String) {
        if (bookId.isBlank() || bookId == boundBookId) return
        boundBookId = bookId
        markedOnce = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            combine(
                bookRepository.getAllBooks().map { books -> books.firstOrNull { it.id == bookId } },
                readingRepository.getActivityDurationByBookId(bookId),
                readingRepository.getBookDailyActivity(bookId),
                readingRepository.getNotesByBookId(bookId),
                readingRepository.getBookmarksByBookId(bookId)
            ) { book, duration, days, notes, bookmarks ->
                val activeDates = days.filter { it.totalDuration > 0L }.map { it.date }.sorted()
                _state.value.copy(
                    book = book,
                    totalDurationMs = duration.totalDuration,
                    activeDays = activeDates.size,
                    firstDate = activeDates.firstOrNull(),
                    lastDate = activeDates.lastOrNull(),
                    noteCount = notes.count { !it.isInkStroke },
                    bookmarkCount = bookmarks.size
                )
            }.combine(dataStoreManager.bookReview(bookId)) { state, review -> state.copy(review = review) }
                .combine(dataStoreManager.bookReviewRating(bookId)) { state, rating -> state.copy(rating = rating) }
                .collect { _state.value = it.copy(markedByFinishPage = _state.value.markedByFinishPage) }
        }
    }

    /** Called each time the finish page becomes visible; marks the book read once per session. */
    fun onFinishPageShown() {
        val id = boundBookId ?: return
        if (markedOnce) return
        markedOnce = true
        viewModelScope.launch {
            val book = bookRepository.getBookById(id) ?: return@launch
            if (!book.isManuallyMarkedRead) {
                bookRepository.setManuallyMarkedRead(id, true)
                _state.value = _state.value.copy(markedByFinishPage = true)
            }
        }
    }

    fun setMarkedRead(read: Boolean) {
        val id = boundBookId ?: return
        viewModelScope.launch {
            bookRepository.setManuallyMarkedRead(id, read)
            if (read) _state.value = _state.value.copy(markedByFinishPage = true)
        }
    }

    fun setRating(rating: Float) {
        val id = boundBookId ?: return
        _state.value = _state.value.copy(rating = rating)
        viewModelScope.launch { dataStoreManager.saveBookReviewRating(id, rating) }
    }

    /** Returns true the first time per book; the fireworks auto-play only on that call. */
    suspend fun claimCelebration(): Boolean {
        val id = boundBookId ?: return false
        if (dataStoreManager.bookFinishCelebrated(id).first()) return false
        dataStoreManager.setBookFinishCelebrated(id)
        return true
    }

    fun archive(onDone: () -> Unit) {
        val id = boundBookId ?: return
        viewModelScope.launch {
            bookRepository.setBooksArchived(listOf(id), true)
            onDone()
        }
    }
}
