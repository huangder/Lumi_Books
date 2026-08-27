package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 封面网络搜索页专用轻量 ViewModel：
 * 提供书籍查询（按 bookId）与「位图保存为自定义封面」
 */
@HiltViewModel
class CoverSearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    application: Application
) : AndroidViewModel(application) {

    sealed interface SaveResult {
        data object Success : SaveResult
        data class Error(val message: String?) : SaveResult
    }

    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveResult = MutableSharedFlow<SaveResult>(extraBufferCapacity = 1)
    val saveResult: SharedFlow<SaveResult> = _saveResult

    /**
     * 将浏览器裁剪捕获的位图保存为书籍自定义封面
     * （命名/清理规则与 HomeViewModel.updateCustomCover 一致）
     */
    fun updateCustomCoverFromBitmap(book: Book, bitmap: Bitmap) {
        viewModelScope.launch {
            var newCoverPath: String? = null
            try {
                newCoverPath = withContext(Dispatchers.IO) {
                    FileUtils.saveCoverBitmap(getApplication(), bitmap, book.id)
                } ?: error("Unable to save the captured cover image")

                bookRepository.updateBookMetadata(book.copy(coverPath = newCoverPath))
                withContext(Dispatchers.IO) {
                    FileUtils.deleteOtherCustomCovers(getApplication(), book.id, newCoverPath)
                }
                _saveResult.tryEmit(SaveResult.Success)
            } catch (error: Exception) {
                newCoverPath?.let { failedCover ->
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteAppOwnedFile(getApplication(), failedCover)
                    }
                }
                _saveResult.tryEmit(SaveResult.Error(error.message))
            } finally {
                withContext(Dispatchers.IO) {
                    bitmap.recycle()
                }
            }
        }
    }
}
