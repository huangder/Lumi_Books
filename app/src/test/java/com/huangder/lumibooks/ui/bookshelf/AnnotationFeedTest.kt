package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnnotationFeedTest {
    private fun book(id: String, title: String) = Book(id = id, title = title, author = "Author $id", filePath = "",
        coverPath = null, format = BookFormat.EPUB, lastReadTime = 0, readingProgress = 0f, createdAt = 0)

    private fun note(id: Long, bookId: String, createdAt: Long, type: String = "highlight", text: String = "text",
                     note: String = "", color: String = "red") =
        Note(id = id, bookId = bookId, chapterIndex = 0, startPosition = 0, endPosition = 4,
            selectedText = text, note = note, color = color, createdAt = createdAt, type = type)

    private val first = book("a", "First Book")
    private val second = book("b", "Second Book")
    private val highlight = note(1, "a", 100, text = "Some highlighted words")
    private val thought = note(2, "a", 200, text = "Passage", note = "My thought")
    private val underline = note(3, "b", 400, type = "underline", color = "blue")
    private val ruleHighlight = note(4, "b", 500, text = "rule match").copy(
        origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "rule", sourceMatchKey = "key")
    private val ink = note(5, "a", 600, type = "pdf_ink_pen", text = "")
    private val orphan = note(6, "gone", 700)
    private val bookmark = Bookmark(id = 1, bookId = "b", chapterIndex = 2, position = 0f, title = "Chapter Mark",
        createdAt = 300, remark = "Remember this")

    private val feed = buildAnnotationFeed(listOf(first, second),
        listOf(highlight, thought, underline, ruleHighlight, ink, orphan), listOf(bookmark))

    private fun keys(entries: List<AnnotationFeedEntry>) = entries.map { it.key }

    @Test fun mergesBooksNewestFirstAndDropsInkAndOrphans() {
        assertEquals(listOf("note-4", "note-3", "bookmark-1", "note-2", "note-1"), keys(feed))
        assertEquals(second, feed.first().book)
    }

    @Test fun hidesRuleHighlightsUntilAskedAndCountsThemPerTab() {
        val query = AnnotationFeedQuery()
        assertEquals(listOf("note-3", "bookmark-1", "note-2", "note-1"), keys(feed.matching(query)))
        assertEquals(1, feed.ruleHighlightCount(query))
        assertEquals(0, feed.ruleHighlightCount(query.copy(type = AnnotationFeedType.NOTES)))
        val withRules = query.copy(type = AnnotationFeedType.HIGHLIGHTS, includeRuleHighlights = true)
        assertEquals(listOf("note-4", "note-1"), keys(feed.matching(withRules)))
    }

    @Test fun filtersByTypeAndStyle() {
        fun of(type: AnnotationFeedType, filter: ReadingMarkFilter = ReadingMarkFilter()) =
            keys(feed.matching(AnnotationFeedQuery(type = type, filter = filter)))
        assertEquals(listOf("note-1"), of(AnnotationFeedType.HIGHLIGHTS))
        assertEquals(listOf("note-3"), of(AnnotationFeedType.UNDERLINES))
        assertEquals(listOf("note-2"), of(AnnotationFeedType.NOTES))
        assertEquals(listOf("bookmark-1"), of(AnnotationFeedType.BOOKMARKS))
        assertEquals(emptyList<String>(), of(AnnotationFeedType.UNDERLINES, ReadingMarkFilter(color = "red")))
    }

    @Test fun searchesTextNotesRemarksAndBookTitlesIgnoringCase() {
        fun search(text: String) = keys(feed.matching(AnnotationFeedQuery(searchText = text)))
        assertEquals(listOf("note-1"), search("HIGHLIGHTED"))
        assertEquals(listOf("note-2"), search("my thought"))
        assertEquals(listOf("bookmark-1"), search("remember"))
        assertEquals(listOf("note-3", "bookmark-1"), search(" second "))
        assertEquals(keys(feed.matching(AnnotationFeedQuery())), search("   "))
    }

    @Test fun groupsByLocalDayKeepingOrder() {
        val day = LocalDate.of(2026, 7, 19)
        fun at(hour: Int, id: Long) = AnnotationFeedEntry.NoteEntry(first,
            note(id, "a", day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))
        val groups = listOf(at(10, 1), at(8, 2)).plus(
            AnnotationFeedEntry.NoteEntry(first, note(3, "a", day.minusDays(1).atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))
        ).groupedByDay(ZoneOffset.UTC)
        assertEquals(listOf(day, day.minusDays(1)), groups.map { it.first })
        assertEquals(listOf("note-1", "note-2"), keys(groups.first().second))
    }
}
