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
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false
) {
    val highlights: List<Note> get() = notes.filter { !it.isNoteEntry && it.type != "underline" }
    val underlines: List<Note> get() = notes.filter { !it.isNoteEntry && it.type == "underline" }
    val noteItems: List<Note> get() = notes.filter { it.isNoteEntry }
}

@HiltViewModel
class BookNotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val exportBuilder: BookNotesExportBuilder
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow(BookNotesUiState())
    val uiState: StateFlow<BookNotesUiState> = _uiState.asStateFlow()

    init {
        loadBook()
        loadBookmarks()
        loadNotes()
        viewModelScope.launch {
            readingRepository.observeAnnotationTags().collectLatest { tags ->
                _uiState.value = _uiState.value.copy(availableTags = tags)
            }
        }
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

    fun updateBookmarkRemark(bookmark: Bookmark, remark: String) {
        updateBookmarkRemarkAndTags(bookmark, remark, bookmark.tags)
    }

    fun updateBookmarkRemarkAndTags(bookmark: Bookmark, remark: String, tags: List<String>) {
        viewModelScope.launch {
            val current = _uiState.value.bookmarks.firstOrNull { it.syncId == bookmark.syncId } ?: bookmark
            readingRepository.updateBookmark(current.copy(remark = remark.trim(), tags = tags))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            readingRepository.deleteNote(note)
        }
    }

    fun updateNoteTags(note: Note, tags: List<String>) {
        viewModelScope.launch { readingRepository.updateNoteTags(note, tags) }
    }

    fun updateBookmarkTags(bookmark: Bookmark, tags: List<String>) {
        viewModelScope.launch { readingRepository.updateBookmarkTags(bookmark, tags) }
    }

    fun renameTag(old: String, new: String) {
        viewModelScope.launch { readingRepository.renameAnnotationTag(old, new) }
    }

    fun deleteTag(name: String) {
        viewModelScope.launch { readingRepository.deleteAnnotationTag(name) }
    }

    fun prepareExport(onComplete: (Result<BookNotesExportDocument>) -> Unit) {
        val snapshot = _uiState.value
        val book = snapshot.book ?: run {
            onComplete(Result.failure(IllegalStateException("Book is not loaded")))
            return
        }
        if (snapshot.isExporting) return

        _uiState.value = snapshot.copy(isExporting = true)
        viewModelScope.launch {
            val result = runCatching {
                exportBuilder.build(
                    book = book,
                    bookmarks = snapshot.bookmarks,
                    notes = snapshot.notes
                )
            }
            _uiState.value = _uiState.value.copy(isExporting = false)
            onComplete(result)
        }
    }
}
