package com.huangder.lumibooks.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.ui.excerpt.ExcerptRequest
import com.huangder.lumibooks.ui.excerpt.withReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnnotationFeedUiState(
    val entries: List<AnnotationFeedEntry> = emptyList(),
    val type: AnnotationFeedType = AnnotationFeedType.ALL,
    val selectedTag: String? = null,
    val selectedColor: String? = null,
    val selectedLine: Int? = null,
    /** Tags used by any mark, for the filter menu. */
    val tags: List<String> = emptyList(),
    /** The tag catalog, for the tag editor. */
    val availableTags: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val includeRuleHighlights: Boolean = false,
    val ruleHighlightCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnnotationFeedViewModel @Inject constructor(
    bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val queryState = MutableStateFlow(AnnotationFeedQuery())

    private val feed = combine(
        bookRepository.getAllBooks(),
        readingRepository.observeTextNotes(),
        readingRepository.observeAllBookmarks()
    ) { books, notes, bookmarks -> buildAnnotationFeed(books, notes, bookmarks) }

    val uiState: StateFlow<AnnotationFeedUiState> = combine(
        feed, queryState, readingRepository.observeAnnotationTags()
    ) { all, query, catalog ->
        val styleTab = query.type == AnnotationFeedType.HIGHLIGHTS || query.type == AnnotationFeedType.UNDERLINES
        AnnotationFeedUiState(
            entries = all.matching(query),
            type = query.type,
            selectedTag = query.filter.tag,
            selectedColor = query.filter.color,
            selectedLine = query.filter.underlineMode,
            tags = all.flatMap { it.tags }.distinct().sorted(),
            availableTags = catalog,
            colors = if (styleTab) {
                all.matching(AnnotationFeedQuery(type = query.type, includeRuleHighlights = query.includeRuleHighlights))
                    .mapNotNull { (it as? AnnotationFeedEntry.NoteEntry)?.note?.color }
                    .distinct()
            } else emptyList(),
            includeRuleHighlights = query.includeRuleHighlights,
            ruleHighlightCount = all.ruleHighlightCount(query),
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnnotationFeedUiState())

    fun setType(type: AnnotationFeedType) = queryState.update {
        // Color and line only apply to one style tab, as on the per-book page.
        it.copy(type = type, filter = it.filter.copy(color = null, underlineMode = null))
    }

    fun setTag(tag: String?) = queryState.update { it.copy(filter = it.filter.copy(tag = tag)) }
    fun setColor(color: String?) = queryState.update { it.copy(filter = it.filter.copy(color = color)) }
    fun setLine(line: Int?) = queryState.update { it.copy(filter = it.filter.copy(underlineMode = line)) }
    fun setSearchText(text: String) = queryState.update { it.copy(searchText = text) }
    fun setIncludeRuleHighlights(include: Boolean) = queryState.update { it.copy(includeRuleHighlights = include) }

    fun deleteNote(note: Note) {
        viewModelScope.launch { readingRepository.deleteNote(note) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { readingRepository.deleteBookmark(bookmark) }
    }

    suspend fun updateNoteTags(note: Note, tags: List<String>) = readingRepository.updateNoteTags(note, tags)

    suspend fun updateBookmarkTags(bookmark: Bookmark, tags: List<String>) =
        readingRepository.updateBookmarkTags(bookmark, tags)

    fun updateBookmarkRemarkAndTags(bookmark: Bookmark, remark: String, tags: List<String>) {
        viewModelScope.launch { readingRepository.updateBookmark(bookmark.copy(remark = remark.trim(), tags = tags)) }
    }

    suspend fun createTag(name: String) = readingRepository.createAnnotationTag(name)

    fun renameTag(old: String, new: String) {
        queryState.update { if (it.filter.tag == old) it.copy(filter = it.filter.copy(tag = new.trim())) else it }
        viewModelScope.launch { readingRepository.renameAnnotationTag(old, new) }
    }

    fun deleteTag(name: String) {
        queryState.update { if (it.filter.tag == name) it.copy(filter = it.filter.copy(tag = null)) else it }
        viewModelScope.launch { readingRepository.deleteAnnotationTag(name) }
    }

    suspend fun excerptTheme(bookId: String, request: ExcerptRequest, dark: Boolean): ExcerptRequest =
        request.withReaderTheme(dataStoreManager.readerPreferences(bookId).first(), dark)
}
