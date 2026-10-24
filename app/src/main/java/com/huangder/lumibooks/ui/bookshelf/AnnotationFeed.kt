package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AnnotationFeedType { ALL, HIGHLIGHTS, UNDERLINES, NOTES, BOOKMARKS }

/** One mark in the cross-book feed. */
sealed interface AnnotationFeedEntry {
    val book: Book
    val createdAt: Long
    val key: String
    val tags: List<String>

    data class NoteEntry(override val book: Book, val note: Note) : AnnotationFeedEntry {
        override val createdAt: Long get() = note.createdAt
        override val key: String get() = "note-${note.id}"
        override val tags: List<String> get() = note.tags
    }

    data class BookmarkEntry(override val book: Book, val bookmark: Bookmark) : AnnotationFeedEntry {
        override val createdAt: Long get() = bookmark.createdAt
        override val key: String get() = "bookmark-${bookmark.id}"
        override val tags: List<String> get() = bookmark.tags
    }
}

/** Merges every book's marks newest first; ink strokes and marks of removed books are dropped. */
internal fun buildAnnotationFeed(
    books: List<Book>,
    notes: List<Note>,
    bookmarks: List<Bookmark>
): List<AnnotationFeedEntry> {
    val booksById = books.associateBy { it.id }
    val noteEntries = notes.asSequence()
        .filterNot { it.isInkStroke }
        .mapNotNull { note -> booksById[note.bookId]?.let { AnnotationFeedEntry.NoteEntry(it, note) } }
    val bookmarkEntries = bookmarks.asSequence()
        .mapNotNull { bookmark -> booksById[bookmark.bookId]?.let { AnnotationFeedEntry.BookmarkEntry(it, bookmark) } }
    return (noteEntries + bookmarkEntries).sortedByDescending { it.createdAt }.toList()
}

internal data class AnnotationFeedQuery(
    val type: AnnotationFeedType = AnnotationFeedType.ALL,
    val filter: ReadingMarkFilter = ReadingMarkFilter(),
    val searchText: String = "",
    /** Rule highlights are written in batches and would bury manual marks, so they start hidden. */
    val includeRuleHighlights: Boolean = false
)

internal fun List<AnnotationFeedEntry>.matching(query: AnnotationFeedQuery): List<AnnotationFeedEntry> =
    filter { it.matches(query) }

/** Rule highlights the current tab, filters and search would show when the toggle is on. */
internal fun List<AnnotationFeedEntry>.ruleHighlightCount(query: AnnotationFeedQuery): Int {
    val withRules = query.copy(includeRuleHighlights = true)
    return count { it is AnnotationFeedEntry.NoteEntry && it.note.isRuleHighlight && it.matches(withRules) }
}

internal fun List<AnnotationFeedEntry>.groupedByDay(zone: ZoneId): List<Pair<LocalDate, List<AnnotationFeedEntry>>> =
    groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.toList()

private val Note.isRuleHighlight: Boolean get() = isGeneratedByHighlightRule && !isNoteEntry

private fun AnnotationFeedEntry.matches(query: AnnotationFeedQuery): Boolean {
    val typeMatches = when (this) {
        is AnnotationFeedEntry.BookmarkEntry ->
            query.type == AnnotationFeedType.ALL || query.type == AnnotationFeedType.BOOKMARKS
        is AnnotationFeedEntry.NoteEntry -> when (query.type) {
            AnnotationFeedType.ALL -> true
            AnnotationFeedType.HIGHLIGHTS -> !note.isNoteEntry && note.type != "underline"
            AnnotationFeedType.UNDERLINES -> !note.isNoteEntry && note.type == "underline"
            AnnotationFeedType.NOTES -> note.isNoteEntry
            AnnotationFeedType.BOOKMARKS -> false
        }
    }
    if (!typeMatches) return false
    if (this is AnnotationFeedEntry.NoteEntry && note.isRuleHighlight && !query.includeRuleHighlights) return false
    val filterMatches = when (this) {
        is AnnotationFeedEntry.NoteEntry -> query.filter.matches(note)
        is AnnotationFeedEntry.BookmarkEntry -> query.filter.matches(bookmark)
    }
    return filterMatches && matchesSearch(query.searchText)
}

private fun AnnotationFeedEntry.matchesSearch(searchText: String): Boolean {
    val needle = searchText.trim()
    if (needle.isEmpty()) return true
    val fields = when (this) {
        is AnnotationFeedEntry.NoteEntry -> listOf(note.selectedText, note.note)
        is AnnotationFeedEntry.BookmarkEntry -> listOf(bookmark.title, bookmark.remark)
    } + listOf(book.title, book.author)
    return fields.any { it.contains(needle, ignoreCase = true) }
}
