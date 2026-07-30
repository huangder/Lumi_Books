package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.bookmarkPositionForCharacterOffset
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.util.parser.TxtEditOperation
import com.huangder.lumibooks.util.parser.TxtEncoding
import com.huangder.lumibooks.util.parser.TxtParser
import com.huangder.lumibooks.util.parser.TxtReplaceRange
import com.huangder.lumibooks.util.parser.TxtReplaceText
import com.huangder.lumibooks.util.parser.TxtSetChapterText
import com.huangder.lumibooks.util.parser.TxtTextMatch
import com.huangder.lumibooks.util.parser.applyTxtEditOperations
import com.huangder.lumibooks.util.parser.findTxtLiteralMatches
import com.huangder.lumibooks.util.parser.replaceTxtLiteral
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TxtEditorSheetMode { SEARCH, REPLACE }

enum class TxtSearchScope { CHAPTER, BOOK }

data class TxtEditorMatch(
    val chapterIndex: Int,
    val start: Int,
    val endExclusive: Int
)

data class TxtEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapterText: String = "",
    val chapterRevision: Int = 0,
    val targetSelectionStart: Int = 0,
    val targetSelectionEnd: Int = 0,
    val restoreScrollPosition: Int = 0,
    val sheetMode: TxtEditorSheetMode? = null,
    val searchQuery: String = "",
    val replacementText: String = "",
    val searchScope: TxtSearchScope = TxtSearchScope.CHAPTER,
    val matchCase: Boolean = false,
    val isSearching: Boolean = false,
    val searchProgress: Float = 0f,
    val currentMatch: TxtEditorMatch? = null,
    val currentMatchOrdinal: Int = 0,
    val totalMatches: Int = 0,
    val lastReplaceCount: Int? = null,
    val fatalErrorMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TxtEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val application: Application
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val initialChapterIndex: Int = savedStateHandle.get<Int>("chapterIndex") ?: 0
    private val initialCharOffset: Int = savedStateHandle.get<Int>("charOffset") ?: 0

    private val _uiState = MutableStateFlow(TxtEditorUiState())
    val uiState: StateFlow<TxtEditorUiState> = _uiState.asStateFlow()

    private var parser: TxtParser? = null
    private var chapterTitles: List<String> = emptyList()
    private val operations = mutableListOf<TxtEditOperation>()
    private val cursorPositions = mutableMapOf<Int, Int>()
    private val scrollPositions = mutableMapOf<Int, Int>()
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val book = bookRepository.getBookById(bookId) ?: error("Book not found")
                    val encoding = TxtEncoding.fromStorage(dataStoreManager.txtEncoding(bookId).first())
                    val txtParser = TxtParser(application).apply { selectedEncoding = encoding }
                    val content = txtParser.parse(book.filePath)
                    Triple(book, txtParser, content)
                }
                val (book, txtParser, content) = loaded
                parser = txtParser
                chapterTitles = content.chapters.map { it.title }
                val chapterCount = chapterTitles.size
                require(chapterCount > 0) { "TXT has no editable chapters" }
                val chapterIndex = initialChapterIndex.coerceIn(0, chapterCount - 1)
                val text = withContext(Dispatchers.IO) {
                    txtParser.getChapterContent(chapterIndex).toString()
                }
                val cursor = initialCharOffset.coerceIn(0, text.length)
                cursorPositions[chapterIndex] = cursor
                _uiState.value = TxtEditorUiState(
                    isLoading = false,
                    bookTitle = book.title,
                    chapterTitle = chapterTitles.getOrElse(chapterIndex) { "" },
                    chapterIndex = chapterIndex,
                    chapterCount = chapterCount,
                    chapterText = text,
                    chapterRevision = 1,
                    targetSelectionStart = cursor,
                    targetSelectionEnd = cursor
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fatalErrorMessage = "加载失败：${error.message}"
                )
            }
        }
    }

    fun openSheet(mode: TxtEditorSheetMode) {
        _uiState.value = _uiState.value.copy(sheetMode = mode)
    }

    fun closeSheet() {
        cancelSearch(clearResult = false)
        _uiState.value = _uiState.value.copy(sheetMode = null)
    }

    fun setSearchQuery(query: String) {
        searchGeneration++
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            currentMatch = null,
            currentMatchOrdinal = 0,
            totalMatches = 0,
            isSearching = false,
            searchProgress = 0f
        )
    }

    fun setReplacementText(text: String) {
        _uiState.value = _uiState.value.copy(replacementText = text)
    }

    fun setSearchScope(scope: TxtSearchScope) {
        if (_uiState.value.searchScope == scope) return
        cancelSearch(clearResult = true)
        _uiState.value = _uiState.value.copy(searchScope = scope)
    }

    fun setMatchCase(enabled: Boolean) {
        if (_uiState.value.matchCase == enabled) return
        cancelSearch(clearResult = true)
        _uiState.value = _uiState.value.copy(matchCase = enabled)
    }

    fun consumeReplaceCount() {
        _uiState.value = _uiState.value.copy(lastReplaceCount = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun invalidateSearchResult() {
        if (_uiState.value.currentMatch == null && _uiState.value.totalMatches == 0) return
        cancelSearch(clearResult = true)
    }

    fun hasPendingChanges(currentText: String): Boolean {
        return operations.isNotEmpty() || currentText != _uiState.value.chapterText
    }

    fun switchChapter(
        delta: Int,
        currentText: String,
        cursor: Int,
        scrollPosition: Int
    ) {
        val target = (_uiState.value.chapterIndex + delta)
            .coerceIn(0, (_uiState.value.chapterCount - 1).coerceAtLeast(0))
        if (target == _uiState.value.chapterIndex) return
        commitCurrent(currentText, cursor, scrollPosition)
        cancelSearch(clearResult = true)
        loadChapter(target, cursorOverride = null, revealMatch = null)
    }

    fun search(currentText: String, cursor: Int) {
        commitCurrent(currentText, cursor, scrollPositions[_uiState.value.chapterIndex] ?: 0)
        startSearch(SearchDirection.NEXT, _uiState.value.chapterIndex, cursor)
    }

    fun findNext(currentText: String, cursor: Int) {
        commitCurrent(currentText, cursor, scrollPositions[_uiState.value.chapterIndex] ?: 0)
        val match = _uiState.value.currentMatch
        startSearch(
            direction = SearchDirection.NEXT,
            anchorChapter = match?.chapterIndex ?: _uiState.value.chapterIndex,
            anchorOffset = match?.endExclusive ?: cursor
        )
    }

    fun findPrevious(currentText: String, cursor: Int) {
        commitCurrent(currentText, cursor, scrollPositions[_uiState.value.chapterIndex] ?: 0)
        val match = _uiState.value.currentMatch
        startSearch(
            direction = SearchDirection.PREVIOUS,
            anchorChapter = match?.chapterIndex ?: _uiState.value.chapterIndex,
            anchorOffset = match?.start ?: cursor
        )
    }

    fun replaceCurrent(currentText: String, cursor: Int) {
        val state = _uiState.value
        val match = state.currentMatch ?: return
        if (match.chapterIndex != state.chapterIndex || match.endExclusive > currentText.length) return
        val updated = currentText.replaceRange(match.start, match.endExclusive, state.replacementText)
        operations += TxtReplaceRange(
            chapterIndex = state.chapterIndex,
            start = match.start,
            endExclusive = match.endExclusive,
            replacement = state.replacementText
        )
        cursorPositions[state.chapterIndex] = match.start + state.replacementText.length
        _uiState.value = _uiState.value.copy(
            chapterText = updated,
            chapterRevision = _uiState.value.chapterRevision + 1,
            targetSelectionStart = match.start + state.replacementText.length,
            targetSelectionEnd = match.start + state.replacementText.length,
            currentMatch = null,
            lastReplaceCount = 1
        )
        startSearch(
            SearchDirection.NEXT,
            state.chapterIndex,
            match.start + state.replacementText.length
        )
    }

    fun replaceAll(currentText: String, cursor: Int) {
        val state = _uiState.value
        if (state.searchQuery.isEmpty()) return
        commitCurrent(currentText, cursor, scrollPositions[state.chapterIndex] ?: 0)
        val ignoreCase = !state.matchCase

        if (state.searchScope == TxtSearchScope.CHAPTER) {
            val (updated, count) = replaceTxtLiteral(
                text = _uiState.value.chapterText,
                query = state.searchQuery,
                replacement = state.replacementText,
                ignoreCase = ignoreCase
            )
            if (count == 0) return
            operations += TxtReplaceText(
                chapterIndex = state.chapterIndex,
                query = state.searchQuery,
                replacement = state.replacementText,
                ignoreCase = ignoreCase
            )
            cursorPositions[state.chapterIndex] = cursor.coerceIn(0, updated.length)
            _uiState.value = _uiState.value.copy(
                chapterText = updated,
                chapterRevision = _uiState.value.chapterRevision + 1,
                targetSelectionStart = cursor.coerceIn(0, updated.length),
                targetSelectionEnd = cursor.coerceIn(0, updated.length),
                currentMatch = null,
                lastReplaceCount = count
            )
        } else {
            val count = state.totalMatches
            if (count == 0) return
            val operation = TxtReplaceText(
                chapterIndex = null,
                query = state.searchQuery,
                replacement = state.replacementText,
                ignoreCase = ignoreCase
            )
            operations += operation
            val updated = replaceTxtLiteral(
                text = _uiState.value.chapterText,
                query = state.searchQuery,
                replacement = state.replacementText,
                ignoreCase = ignoreCase
            ).first
            _uiState.value = _uiState.value.copy(
                chapterText = updated,
                chapterRevision = _uiState.value.chapterRevision + 1,
                targetSelectionStart = cursor.coerceIn(0, updated.length),
                targetSelectionEnd = cursor.coerceIn(0, updated.length),
                currentMatch = null,
                lastReplaceCount = count
            )
        }
        startSearch(SearchDirection.NEXT, state.chapterIndex, cursor)
    }

    fun updatePosition(cursor: Int, scrollPosition: Int) {
        val chapterIndex = _uiState.value.chapterIndex
        cursorPositions[chapterIndex] = cursor
        scrollPositions[chapterIndex] = scrollPosition
    }

    fun save(
        currentText: String,
        cursor: Int,
        scrollPosition: Int,
        onSuccess: () -> Unit
    ) {
        commitCurrent(currentText, cursor, scrollPosition)
        val txtParser = parser ?: return
        val snapshot = operations.toList()
        if (snapshot.isEmpty()) {
            onSuccess()
            return
        }
        searchJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val annotationMigration = withContext(Dispatchers.IO) {
                prepareAnnotationMigration(txtParser, snapshot)
            }
            val result = withContext(Dispatchers.IO) {
                txtParser.rewriteWithOperations(snapshot)
            }
            if (result.success) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        applyAnnotationMigration(txtParser, annotationMigration)
                    }
                }
                operations.clear()
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "保存失败：${result.errorMessage.orEmpty()}"
                )
            }
        }
    }

    private fun commitCurrent(text: String, cursor: Int, scrollPosition: Int) {
        val state = _uiState.value
        cursorPositions[state.chapterIndex] = cursor.coerceIn(0, text.length)
        scrollPositions[state.chapterIndex] = scrollPosition.coerceAtLeast(0)
        if (text == state.chapterText) return
        operations += TxtSetChapterText(state.chapterIndex, text)
        _uiState.value = state.copy(chapterText = text)
    }

    private fun loadChapter(
        chapterIndex: Int,
        cursorOverride: Int?,
        revealMatch: TxtEditorMatch?
    ) {
        val txtParser = parser ?: return
        val operationSnapshot = operations.toList()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                applyTxtEditOperations(
                    chapterIndex,
                    txtParser.getChapterContent(chapterIndex).toString(),
                    operationSnapshot
                )
            }
            val start = revealMatch?.start
                ?: cursorOverride
                ?: cursorPositions[chapterIndex]
                ?: 0
            val end = revealMatch?.endExclusive ?: start
            _uiState.value = _uiState.value.copy(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitles.getOrElse(chapterIndex) { "" },
                chapterText = text,
                chapterRevision = _uiState.value.chapterRevision + 1,
                targetSelectionStart = start.coerceIn(0, text.length),
                targetSelectionEnd = end.coerceIn(0, text.length),
                restoreScrollPosition = scrollPositions[chapterIndex] ?: 0,
                currentMatch = revealMatch
            )
        }
    }

    private fun startSearch(
        direction: SearchDirection,
        anchorChapter: Int,
        anchorOffset: Int
    ) {
        val state = _uiState.value
        val query = state.searchQuery
        if (query.isEmpty()) {
            cancelSearch(clearResult = true)
            return
        }
        val txtParser = parser ?: return
        val generation = ++searchGeneration
        searchJob?.cancel()
        val operationSnapshot = operations.toList()
        val scope = state.searchScope
        val ignoreCase = !state.matchCase
        val activeChapter = state.chapterIndex
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchProgress = 0f)
            val result = withContext(Dispatchers.IO) {
                scanMatches(
                    txtParser = txtParser,
                    operations = operationSnapshot,
                    query = query,
                    ignoreCase = ignoreCase,
                    scope = scope,
                    activeChapter = activeChapter,
                    direction = direction,
                    anchorChapter = anchorChapter,
                    anchorOffset = anchorOffset,
                    onProgress = { progress ->
                        if (generation == searchGeneration) {
                            _uiState.value = _uiState.value.copy(searchProgress = progress)
                        }
                    }
                )
            }
            if (generation != searchGeneration) return@launch
            val match = result.match
            if (match == null) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchProgress = 1f,
                    currentMatch = null,
                    currentMatchOrdinal = 0,
                    totalMatches = 0
                )
                return@launch
            }

            val text = withContext(Dispatchers.IO) {
                applyTxtEditOperations(
                    match.chapterIndex,
                    txtParser.getChapterContent(match.chapterIndex).toString(),
                    operationSnapshot
                )
            }
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchProgress = 1f,
                chapterIndex = match.chapterIndex,
                chapterTitle = chapterTitles.getOrElse(match.chapterIndex) { "" },
                chapterText = text,
                chapterRevision = _uiState.value.chapterRevision + 1,
                targetSelectionStart = match.start,
                targetSelectionEnd = match.endExclusive,
                restoreScrollPosition = scrollPositions[match.chapterIndex] ?: 0,
                currentMatch = match,
                currentMatchOrdinal = result.ordinal,
                totalMatches = result.total
            )
        }
    }

    private suspend fun scanMatches(
        txtParser: TxtParser,
        operations: List<TxtEditOperation>,
        query: String,
        ignoreCase: Boolean,
        scope: TxtSearchScope,
        activeChapter: Int,
        direction: SearchDirection,
        anchorChapter: Int,
        anchorOffset: Int,
        onProgress: (Float) -> Unit
    ): SearchScanResult {
        val chapterIndices = if (scope == TxtSearchScope.CHAPTER) {
            listOf(activeChapter)
        } else {
            (0 until txtParser.getChapterCount()).toList()
        }
        var total = 0
        var first: TxtEditorMatch? = null
        var firstOrdinal = 0
        var forward: TxtEditorMatch? = null
        var forwardOrdinal = 0
        var backward: TxtEditorMatch? = null
        var backwardOrdinal = 0

        chapterIndices.forEachIndexed { position, chapterIndex ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val text = applyTxtEditOperations(
                chapterIndex,
                txtParser.getChapterContent(chapterIndex).toString(),
                operations
            )
            findTxtLiteralMatches(text, query, ignoreCase).forEach { match ->
                total++
                val editorMatch = TxtEditorMatch(chapterIndex, match.start, match.endExclusive)
                if (first == null) {
                    first = editorMatch
                    firstOrdinal = total
                }
                val isAtOrAfterAnchor = chapterIndex > anchorChapter ||
                    (chapterIndex == anchorChapter && match.start >= anchorOffset)
                if (isAtOrAfterAnchor && forward == null) {
                    forward = editorMatch
                    forwardOrdinal = total
                }
                val isBeforeAnchor = chapterIndex < anchorChapter ||
                    (chapterIndex == anchorChapter && match.start < anchorOffset)
                if (isBeforeAnchor) {
                    backward = editorMatch
                    backwardOrdinal = total
                }
            }
            onProgress((position + 1).toFloat() / chapterIndices.size.coerceAtLeast(1))
        }

        val selected = when (direction) {
            SearchDirection.NEXT -> forward ?: first
            SearchDirection.PREVIOUS -> backward ?: if (total > 0) {
                var last: TxtEditorMatch? = null
                chapterIndices.asReversed().forEach { chapterIndex ->
                    if (last == null) {
                        val text = applyTxtEditOperations(
                            chapterIndex,
                            txtParser.getChapterContent(chapterIndex).toString(),
                            operations
                        )
                        findTxtLiteralMatches(text, query, ignoreCase).lastOrNull()?.let {
                            last = TxtEditorMatch(chapterIndex, it.start, it.endExclusive)
                        }
                    }
                }
                last
            } else null
        }
        val ordinal = when {
            selected == null -> 0
            selected == forward -> forwardOrdinal
            selected == backward -> backwardOrdinal
            direction == SearchDirection.PREVIOUS -> total
            else -> firstOrdinal
        }
        return SearchScanResult(selected, ordinal, total)
    }

    private fun cancelSearch(clearResult: Boolean) {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            searchProgress = 0f,
            currentMatch = if (clearResult) null else _uiState.value.currentMatch,
            currentMatchOrdinal = if (clearResult) 0 else _uiState.value.currentMatchOrdinal,
            totalMatches = if (clearResult) 0 else _uiState.value.totalMatches
        )
    }

    private suspend fun prepareAnnotationMigration(
        txtParser: TxtParser,
        operationSnapshot: List<TxtEditOperation>
    ): AnnotationMigration? {
        val notes = readingRepository.getNotesByBookId(bookId).first()
        val bookmarks = readingRepository.getBookmarksByBookId(bookId).first()
        val annotatedChapters = buildSet {
            notes.mapTo(this) { it.chapterIndex }
            bookmarks.mapTo(this) { it.chapterIndex }
        }
        if (annotatedChapters.isEmpty()) return null

        val unreliableChapters = operationSnapshot.filterIsInstance<TxtSetChapterText>()
            .mapTo(mutableSetOf()) { it.chapterIndex }
        val stepsByChapter = mutableMapOf<Int, List<OffsetMigrationStep>>()
        annotatedChapters.forEach { chapterIndex ->
            if (chapterIndex !in 0 until txtParser.getChapterCount() ||
                chapterIndex in unreliableChapters
            ) return@forEach
            var text = txtParser.getChapterContent(chapterIndex).toString()
            val steps = mutableListOf<OffsetMigrationStep>()
            operationSnapshot.forEach { operation ->
                when (operation) {
                    is TxtSetChapterText -> Unit
                    is TxtReplaceRange -> {
                        if (operation.chapterIndex == chapterIndex &&
                            operation.start >= 0 &&
                            operation.endExclusive in operation.start..text.length
                        ) {
                            steps += OffsetMigrationStep(
                                listOf(TxtTextMatch(operation.start, operation.endExclusive)),
                                operation.replacement.length
                            )
                            text = text.replaceRange(
                                operation.start,
                                operation.endExclusive,
                                operation.replacement
                            )
                        }
                    }
                    is TxtReplaceText -> {
                        if (operation.chapterIndex == null || operation.chapterIndex == chapterIndex) {
                            val matches = findTxtLiteralMatches(
                                text,
                                operation.query,
                                operation.ignoreCase
                            )
                            if (matches.isNotEmpty()) {
                                steps += OffsetMigrationStep(matches, operation.replacement.length)
                                text = replaceTxtLiteral(
                                    text,
                                    operation.query,
                                    operation.replacement,
                                    operation.ignoreCase
                                ).first
                            }
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) stepsByChapter[chapterIndex] = steps
        }
        return AnnotationMigration(
            originalChapterCount = txtParser.getChapterCount(),
            notes = notes,
            bookmarks = bookmarks,
            stepsByChapter = stepsByChapter
        )
    }

    private suspend fun applyAnnotationMigration(
        txtParser: TxtParser,
        migration: AnnotationMigration?
    ) {
        migration ?: return
        if (txtParser.getChapterCount() != migration.originalChapterCount) return

        migration.notes.forEach { note ->
            val steps = migration.stepsByChapter[note.chapterIndex] ?: return@forEach
            val start = mapOffset(note.startPosition, steps, endBias = false)
            val end = mapOffset(note.endPosition, steps, endBias = true).coerceAtLeast(start)
            val updatedText = txtParser.getChapterContent(note.chapterIndex).toString()
            val safeStart = start.coerceIn(0, updatedText.length)
            val safeEnd = end.coerceIn(safeStart, updatedText.length)
            readingRepository.updateNote(
                note.copy(
                    startPosition = safeStart,
                    endPosition = safeEnd,
                    selectedText = updatedText.substring(safeStart, safeEnd)
                )
            )
        }
        migration.bookmarks.forEach { bookmark ->
            val offset = bookmark.characterOffset ?: return@forEach
            val steps = migration.stepsByChapter[bookmark.chapterIndex] ?: return@forEach
            readingRepository.insertBookmark(
                bookmark.copy(position = bookmarkPositionForCharacterOffset(mapOffset(offset, steps, false)))
            )
        }
    }

    private fun mapOffset(
        originalOffset: Int,
        steps: List<OffsetMigrationStep>,
        endBias: Boolean
    ): Int {
        var offset = originalOffset.coerceAtLeast(0)
        steps.forEach { step ->
            var delta = 0
            var mappedInsideMatch = false
            step.matches.forEach { match ->
                when {
                    offset < match.start -> return@forEach
                    offset >= match.endExclusive -> {
                        delta += step.replacementLength - (match.endExclusive - match.start)
                    }
                    else -> {
                        offset = match.start + delta + if (endBias) step.replacementLength else 0
                        mappedInsideMatch = true
                    }
                }
                if (mappedInsideMatch) return@forEach
            }
            if (!mappedInsideMatch) offset += delta
        }
        return offset.coerceAtLeast(0)
    }

    override fun onCleared() {
        searchJob?.cancel()
        parser?.close()
        parser = null
        super.onCleared()
    }

    private enum class SearchDirection { NEXT, PREVIOUS }

    private data class SearchScanResult(
        val match: TxtEditorMatch?,
        val ordinal: Int,
        val total: Int
    )

    private data class OffsetMigrationStep(
        val matches: List<TxtTextMatch>,
        val replacementLength: Int
    )

    private data class AnnotationMigration(
        val originalChapterCount: Int,
        val notes: List<Note>,
        val bookmarks: List<Bookmark>,
        val stepsByChapter: Map<Int, List<OffsetMigrationStep>>
    )
}
