package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import com.huangder.lumibooks.domain.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** One cache per book, content revision and set of rules, accessed on the reader's UI thread. */
internal class EpubRuleHighlightCache(
    private val scope: CoroutineScope,
    loadFonts: suspend () -> Map<String, String>,
    private val loadNotes: suspend (Int) -> List<Note>
) {
    private val chapters = mutableStateMapOf<Int, List<Note>>()
    private val completedOrder = LinkedHashSet<Int>()
    private val pending = mutableMapOf<Int, Deferred<Unit>>()
    private val fonts = scope.async(start = CoroutineStart.LAZY) { loadFonts() }

    var fontUrls: Map<String, String> = emptyMap()
        private set

    fun notes(chapter: Int): List<Note>? = chapters[chapter]
    fun preparedNotes(): Map<Int, List<Note>> = chapters.toMap()

    fun prepare(chapter: Int): Deferred<Unit>? {
        if (chapter in chapters) {
            completedOrder.remove(chapter)
            completedOrder.add(chapter)
            return null
        }
        return pending.getOrPut(chapter) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    fonts.start()
                    val notes = loadNotes(chapter)
                    fontUrls = fonts.await()
                    chapters[chapter] = notes
                    completedOrder.add(chapter)
                    if (completedOrder.size > 12) {
                        val oldest = completedOrder.first()
                        completedOrder.remove(oldest)
                        chapters.remove(oldest)
                    }
                } finally {
                    pending.remove(chapter)
                }
            }
        }.also { it.start() }
    }
}

internal suspend fun State<EpubRuleHighlightCache?>.awaitChapter(chapter: Int) {
    do {
        val cache = value
        cache?.prepare(chapter)?.await()
    } while (value !== cache)
}

internal fun epubNotesWithRules(
    savedNotes: List<Note>,
    rules: EpubRuleHighlightCache?,
    chapter: Int
): List<Note> = epubNotesForChapter(savedNotes, chapter) + rules?.notes(chapter).orEmpty()
