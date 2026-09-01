package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note

internal data class BookmarkExportItem(
    val bookmark: Bookmark,
    val chapterTitle: String,
    val pageText: String?
)

internal data class BookNotesExportLabels(
    val highlightSection: String,
    val highlightContent: String,
    val chapter: String,
    val book: String,
    val noteSection: String,
    val noteSource: String,
    val userNote: String,
    val bookmarkSection: String,
    val bookmarkPage: String,
    val pageUnavailable: String,
    val fileSuffix: String,
    val chapterNumber: (Int) -> String
)

internal object BookNotesExportFormatter {

    fun format(
        bookTitle: String,
        notes: List<Note>,
        bookmarks: List<BookmarkExportItem>,
        chapterTitles: Map<Int, String>,
        labels: BookNotesExportLabels
    ): String = buildString {
        appendLine("【${labels.highlightSection}：】")
        notes.filter { it.note.isBlank() }.forEach { note ->
            appendLine()
            appendLine("【${labels.highlightContent}】${note.selectedText.trim()}")
            appendLine("【${labels.chapter}】${chapterTitle(note.chapterIndex, chapterTitles, labels)}")
            appendLine("【${labels.book}】$bookTitle")
        }

        appendLine()
        appendLine("【${labels.noteSection}：】")
        notes.filter { it.note.isNotBlank() }.forEach { note ->
            appendLine()
            appendLine("【${labels.noteSource}】${note.selectedText.trim()}")
            appendLine("【${labels.userNote}】${note.note.trim()}")
            appendLine("【${labels.chapter}】${chapterTitle(note.chapterIndex, chapterTitles, labels)}")
            appendLine("【${labels.book}】$bookTitle")
        }

        appendLine()
        appendLine("【${labels.bookmarkSection}：】")
        bookmarks.forEach { item ->
            appendLine()
            appendLine(
                "【${labels.bookmarkPage}】${pageExcerpt(item.pageText, item.bookmark.title, labels.pageUnavailable)}"
            )
            appendLine("【${labels.chapter}】${item.chapterTitle}")
            appendLine("【${labels.book}】$bookTitle")
        }
    }.trimEnd() + "\n"

    fun pageExcerpt(pageText: String?, fallback: String, pageUnavailable: String): String {
        val normalizedPage = pageText
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            .orEmpty()
        if (normalizedPage.isBlank()) return fallback.trim().ifBlank { pageUnavailable }

        val firstParagraph = normalizedPage
            .lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            .orEmpty()
            .collapseWhitespace()
        val flattenedPage = normalizedPage.collapseWhitespace()
        if (flattenedPage.codePointCount(0, flattenedPage.length) <= EXCERPT_CODE_POINTS * 2) {
            return flattenedPage
        }
        return firstParagraph.takeCodePoints(EXCERPT_CODE_POINTS) +
            "…" +
            flattenedPage.takeLastCodePoints(EXCERPT_CODE_POINTS)
    }

    fun suggestedFileName(bookTitle: String, fileSuffix: String): String {
        val safeTitle = bookTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "LumiBooks" }
        return "$safeTitle-$fileSuffix.txt"
    }

    private fun chapterTitle(
        chapterIndex: Int,
        chapterTitles: Map<Int, String>,
        labels: BookNotesExportLabels
    ): String {
        return chapterTitles[chapterIndex]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: labels.chapterNumber(chapterIndex + 1)
    }

    private fun String.collapseWhitespace(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.takeCodePoints(count: Int): String {
        if (isEmpty() || count <= 0) return ""
        val end = offsetByCodePoints(0, minOf(count, codePointCount(0, length)))
        return substring(0, end)
    }

    private fun String.takeLastCodePoints(count: Int): String {
        if (isEmpty() || count <= 0) return ""
        val total = codePointCount(0, length)
        val start = offsetByCodePoints(0, (total - count).coerceAtLeast(0))
        return substring(start)
    }

    private const val EXCERPT_CODE_POINTS = 5
}
