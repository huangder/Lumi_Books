package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note

/** A snapshot of marks supplied by the page, never a new database query. */
internal data class NotesExportBook(
    val book: Book,
    val notes: List<Note>,
    val bookmarks: List<Bookmark>
)

internal data class NotesExportContent(
    val source: NotesExportBook,
    val chapterTitles: Map<Int, String>,
    val bookmarks: List<BookmarkExportItem>,
    val labels: BookNotesExportLabels
)

internal fun NotesExportContent.asTxt() = BookNotesExportDocument(
    fileName = BookNotesExportFormatter.suggestedFileName(source.book.title, labels.fileSuffix),
    text = BookNotesExportFormatter.format(
        bookTitle = source.book.title,
        notes = source.notes,
        bookmarks = bookmarks,
        chapterTitles = chapterTitles,
        labels = labels
    )
)

/** Keeps the feed's first-seen book order and its complete, already-filtered selection. */
internal fun notesExportBooks(entries: List<AnnotationFeedEntry>): List<NotesExportBook> =
    entries.asSequence()
        .filterNot { it.book.isHidden }
        .filterNot { it is AnnotationFeedEntry.NoteEntry && it.note.isInkStroke }
        .groupBy { it.book.id }
        .values.map { group ->
            NotesExportBook(
                book = group.first().book,
                notes = group.filterIsInstance<AnnotationFeedEntry.NoteEntry>().map { it.note },
                bookmarks = group.filterIsInstance<AnnotationFeedEntry.BookmarkEntry>().map { it.bookmark }
            )
        }
