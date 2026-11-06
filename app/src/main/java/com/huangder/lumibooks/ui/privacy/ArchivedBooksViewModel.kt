package com.huangder.lumibooks.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ArchivedBooksUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val restoringIds: Set<String> = emptySet(),
    val errorRes: Int? = null,
    val messageRes: Int? = null
)

@HiltViewModel
internal class ArchivedBooksViewModel @Inject constructor(private val repository: BookRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ArchivedBooksUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        loadJob = viewModelScope.launch {
            try {
                repository.getAllBooks().collect { books ->
                    _uiState.update { it.copy(
                        books = books.filter { book -> book.isArchived && !book.isHidden }
                            .sortedWith(compareByDescending<Book> { book -> book.lastReadTime }.thenBy { book -> book.id }),
                        isLoading = false
                    ) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, errorRes = R.string.archive_load_failed) }
            }
        }
    }

    fun restore(bookId: String) {
        if (bookId in _uiState.value.restoringIds) return
        _uiState.update { it.copy(restoringIds = it.restoringIds + bookId, errorRes = null) }
        viewModelScope.launch {
            try {
                repository.setBooksArchived(listOf(bookId), false)
                _uiState.update { it.copy(messageRes = R.string.archive_restore_success) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.archive_restore_failed) }
            } finally {
                _uiState.update { it.copy(restoringIds = it.restoringIds - bookId) }
            }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(messageRes = null) } }
}
