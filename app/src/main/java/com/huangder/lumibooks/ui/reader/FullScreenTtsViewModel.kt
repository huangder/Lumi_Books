package com.huangder.lumibooks.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.tts.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ListeningSentence(val chapter: Int, val segment: TtsTextSegment) {
    val id: String get() = "$chapter:${segment.startCharacterOffset}"
}

data class ListeningText(
    val chapter: Int = 0, val chapterCount: Int = 0,
    val sentences: List<ListeningSentence> = emptyList(), val focus: Int = 0,
    val progress: Float = 0f
)

internal fun listeningText(chapter: TtsChapterSnapshot?, page: TtsPageContent?, utterance: TtsTextSegment?): ListeningText {
    val index = page?.location?.chapterIndex ?: return ListeningText()
    val snapshot = chapter?.takeIf { it.index == index }
    val extractor = TtsTextExtractor()
    val segments = if (snapshot != null) extractor.splitIntoSegments(snapshot.text)
        else extractor.splitIntoSegments(page.text, page.startCharacterOffset)
    val rows = segments.flatMap { extractor.splitIntoClauses(it) }
        .filter { extractor.hasSpeakableContent(it.text) }.map { ListeningSentence(index, it) }
    val offset = utterance?.startCharacterOffset ?: page.startCharacterOffset
    val focus = rows.indexOfLast { it.segment.startCharacterOffset <= offset }.coerceAtLeast(0)
    val length = snapshot?.text?.length ?: page.endCharacterOffset
    return ListeningText(index, snapshot?.count ?: 0, rows, focus,
        if (length > 0) (offset.toFloat() / length).coerceIn(0f, 1f) else 0f)
}

internal fun ListeningText.withSpokenClause(clause: TtsTextSegment, length: Int): ListeningText {
    val offset = clause.startCharacterOffset
    val index = sentences.indexOfLast { it.segment.startCharacterOffset <= offset }
    val row = sentences.getOrNull(index)
    // A seek or a sentence carried over a page boundary can start inside a chapter row.
    val rows = if (row != null && offset < row.segment.endCharacterOffset) {
        if (row.segment.text == clause.text) sentences
        else sentences.toMutableList().apply { this[index] = ListeningSentence(chapter, clause) }
    } else {
        sentences.toMutableList().apply { add(index + 1, ListeningSentence(chapter, clause)) }
    }
    val focusIndex = if (row == null || offset >= row.segment.endCharacterOffset) index + 1 else index
    return copy(sentences = rows, focus = focusIndex,
        progress = if (length > 0) (offset.toFloat() / length).coerceIn(0f, 1f) else progress)
}

@HiltViewModel
class FullScreenTtsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    val controller: TtsController,
    repository: BookRepository,
    engine: TtsEngine,
    val settings: DataStoreManager
) : ViewModel() {
    val bookId: String = savedState[FullScreenTtsActivity.BOOK_ID] ?: ""
    val sessionId: Long = savedState[FullScreenTtsActivity.SESSION_ID] ?: -1L
    val book = MutableStateFlow<Book?>(null)
    private val displayMode = ListeningDisplayMode(savedState)
    val subtitleMode = displayMode.subtitles
    var entered: Boolean
        get() = savedState["entered"] ?: false
        set(value) { savedState["entered"] = value }
    val chapterBusy = MutableStateFlow(false)
    val engineBusy = MutableStateFlow(false)
    val engineError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val engines = engine.getInstalledEngines()
    // Chapter parsing can be expensive. A short spoken clause only moves the focused row.
    val text = controller.listeningFrame
        .map { it.chapter to it.page }
        .distinctUntilChanged()
        .map { (chapter, page) ->
            val rows = listeningText(chapter, page, null)
            val length = chapter?.takeIf { it.index == rows.chapter }?.text?.length ?: page?.endCharacterOffset ?: 0
            rows to length
        }
        .flowOn(Dispatchers.Default)
        .combine(controller.subtitleFrame) { (rows, length), subtitle ->
            when {
                subtitle.current == null -> rows.copy(focus = -1)
                subtitle.bookId != bookId || subtitle.chapterIndex != rows.chapter -> null
                else -> rows.withSpokenClause(subtitle.current, length)
            }
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListeningText())

    init {
        if (isCurrentSession()) controller.setFullscreenVisible(sessionId, true)
        viewModelScope.launch { book.value = repository.getBookById(bookId) }
        viewModelScope.launch { controller.engineSwitchFailures.collect { engineError.emit(Unit) } }
        viewModelScope.launch {
            settings.listeningPageSettings.map { it.defaultDisplayStyle }.distinctUntilChanged().collect { style ->
                displayMode.applyDefault(style)
            }
        }
    }
    fun isCurrentSession() = controller.activeBookId.value == bookId && controller.currentSessionId == sessionId
    fun toggleMode() = displayMode.toggle()
    fun togglePlayback() {
        if (controller.playbackState.value == TtsPlaybackState.PLAYING) controller.pause() else controller.resume()
    }
    fun seek(sentence: ListeningSentence) {
        if (isCurrentSession()) controller.seekTo(sentence.chapter, sentence.segment.startCharacterOffset, resumeIfPaused = false)
    }
    fun seekProgress(progress: Float) {
        val rows = text.value.sentences
        val length = controller.chapterSnapshot.value?.text?.length ?: controller.currentPage.value?.endCharacterOffset ?: return
        rows.lastOrNull { it.segment.startCharacterOffset <= progress * length }?.let(::seek)
            ?: rows.firstOrNull()?.let(::seek)
    }
    fun chapter(forward: Boolean) {
        if (chapterBusy.value || !isCurrentSession()) return
        chapterBusy.value = true
        viewModelScope.launch { try { controller.skipChapter(forward) } finally { chapterBusy.value = false } }
    }
    fun rate(value: Float) { viewModelScope.launch { controller.setSpeechRate(value) } }
    fun pitch(value: Float) { viewModelScope.launch { controller.setPitch(value) } }
    fun rateMode(mode: TtsProsodyMode) { viewModelScope.launch { controller.setSpeechRateMode(mode) } }
    fun pitchMode(mode: TtsProsodyMode) { viewModelScope.launch { controller.setPitchMode(mode) } }
    fun engine(selection: TtsProviderSelection) {
        if (engineBusy.value || !isCurrentSession()) return
        engineBusy.value = true
        viewModelScope.launch {
            try { if (controller.switchSessionEngine(selection).isFailure) engineError.emit(Unit) }
            finally { engineBusy.value = false }
        }
    }
    override fun onCleared() {
        controller.setFullscreenVisible(sessionId, false)
        super.onCleared()
    }
}
