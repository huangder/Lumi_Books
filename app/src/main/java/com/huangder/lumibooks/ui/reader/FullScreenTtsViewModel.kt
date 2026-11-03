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
    val rows = segments.filter { extractor.hasSpeakableContent(it.text) }.map { ListeningSentence(index, it) }
    val offset = utterance?.startCharacterOffset ?: page.startCharacterOffset
    val focus = rows.indexOfLast { it.segment.startCharacterOffset <= offset }.coerceAtLeast(0)
    val length = snapshot?.text?.length ?: page.endCharacterOffset
    return ListeningText(index, snapshot?.count ?: 0, rows, focus,
        if (length > 0) (offset.toFloat() / length).coerceIn(0f, 1f) else 0f)
}

internal fun ListeningText.withRangeProgress(frame: TtsListeningFrame, range: TtsTextSegment?): ListeningText {
    val utterance = frame.utterance ?: return this
    val offset = range?.startCharacterOffset?.takeIf {
        it in utterance.startCharacterOffset..utterance.endCharacterOffset
    } ?: return this
    val length = frame.chapter?.takeIf { it.index == chapter }?.text?.length
        ?: frame.page?.endCharacterOffset ?: return this
    return if (length > 0) copy(progress = (offset.toFloat() / length).coerceIn(0f, 1f)) else this
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
    val subtitleMode = savedState.getStateFlow("subtitles", false)
    var entered: Boolean
        get() = savedState["entered"] ?: false
        set(value) { savedState["entered"] = value }
    val chapterBusy = MutableStateFlow(false)
    val engineBusy = MutableStateFlow(false)
    val engineError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val engines = engine.getInstalledEngines()
    // Word callbacks update only progress; split the chapter only when the sentence frame changes.
    val text = controller.listeningFrame.map { frame ->
        frame to listeningText(frame.chapter, frame.page, frame.utterance)
    }.combine(controller.currentSentence) { (frame, text), range -> text.withRangeProgress(frame, range) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListeningText())

    init {
        if (isCurrentSession()) controller.setFullscreenVisible(sessionId, true)
        viewModelScope.launch { book.value = repository.getBookById(bookId) }
        viewModelScope.launch { controller.engineSwitchFailures.collect { engineError.emit(Unit) } }
    }
    fun isCurrentSession() = controller.activeBookId.value == bookId && controller.currentSessionId == sessionId
    fun toggleMode() { savedState["subtitles"] = !subtitleMode.value }
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
