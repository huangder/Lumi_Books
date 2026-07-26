package com.huangder.lumibooks.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.util.parser.TxtParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TxtEditorUiState(
    val isLoading: Boolean = true,
    val bookTitle: String = "",
    val chapterText: String = "",
    val initialChapterText: String = "",
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class TxtEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val chapterIndex: Int = savedStateHandle.get<Int>("chapterIndex") ?: 0
    private val charOffset: Int = savedStateHandle.get<Int>("charOffset") ?: 0

    private val _uiState = MutableStateFlow(TxtEditorUiState())
    val uiState: StateFlow<TxtEditorUiState> = _uiState.asStateFlow()

    private var parser: TxtParser? = null

    init {
        loadChapter()
    }

    private fun loadChapter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val book = withContext(Dispatchers.IO) {
                    bookRepository.getBookById(bookId)
                }
                if (book == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "书籍未找到"
                    )
                    return@launch
                }

                val txtParser = TxtParser()
                withContext(Dispatchers.IO) {
                    txtParser.parse(book.filePath)
                }

                val text = withContext(Dispatchers.IO) {
                    txtParser.getChapterContent(chapterIndex).toString()
                }

                parser = txtParser
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bookTitle = book.title,
                    chapterText = text,
                    initialChapterText = text
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载失败：${e.message}"
                )
            }
        }
    }

    fun getCursorOffset(): Int = charOffset.coerceIn(0, _uiState.value.chapterText.length)

    fun isModified(): Boolean =
        _uiState.value.chapterText != _uiState.value.initialChapterText

    fun save(newText: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val p = parser ?: return@launch
            val success = withContext(Dispatchers.IO) {
                p.replaceChapterContent(chapterIndex, newText)
            }
            if (success) {
                _uiState.value = _uiState.value.copy(saveSuccess = true)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "保存失败"
                )
            }
        }
    }
}
