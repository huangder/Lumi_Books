package com.huangder.lumibooks.ui.bookshelf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookNotesUiState(
    val book: Book? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val notes: List<Note> = emptyList(),  // 包含高亮和笔记
    val isLoading: Boolean = true
) {
    val highlights: List<Note> get() = notes.filter { it.note.isBlank() }
    val noteItems: List<Note> get() = notes.filter { it.note.isNotBlank() }
}

@HiltViewModel
class BookNotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow(BookNotesUiState())
    val uiState: StateFlow<BookNotesUiState> = _uiState.asStateFlow()

    init {
        loadBook()
        loadBookmarks()
        loadNotes()
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            _uiState.value = _uiState.value.copy(book = book, isLoading = false)
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            readingRepository.getBookmarksByBookId(bookId).collectLatest { bookmarks ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
            }
        }
    }

    private fun loadNotes() {
        viewModelScope.launch {
            readingRepository.getNotesByBookId(bookId).collectLatest { notes ->
                _uiState.value = _uiState.value.copy(notes = notes)
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            readingRepository.deleteBookmark(bookmark)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            readingRepository.deleteNote(note)
        }
    }
}
