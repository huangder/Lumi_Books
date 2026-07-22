package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note

internal data class BookmarkExportItem(
    val bookmark: Bookmark,
    val chapterTitle: String,
    val pageText: String?
)

internal object BookNotesExportFormatter {

    fun format(
        bookTitle: String,
        notes: List<Note>,
        bookmarks: List<BookmarkExportItem>,
        chapterTitles: Map<Int, String>
    ): String = buildString {
        appendLine("【高亮：】")
        notes.filter { it.note.isBlank() }.forEach { note ->
            appendLine()
            appendLine("【高亮内容】${note.selectedText.trim()}")
            appendLine("【所在章节】${chapterTitle(note.chapterIndex, chapterTitles)}")
            appendLine("【书籍名】$bookTitle")
        }

        appendLine()
        appendLine("【笔记：】")
        notes.filter { it.note.isNotBlank() }.forEach { note ->
            appendLine()
            appendLine("【笔记原文内容】${note.selectedText.trim()}")
            appendLine("【用户所写笔记内容】${note.note.trim()}")
            appendLine("【所在章节】${chapterTitle(note.chapterIndex, chapterTitles)}")
            appendLine("【书籍名】$bookTitle")
        }

        appendLine()
        appendLine("【书签：】")
        bookmarks.forEach { item ->
            appendLine()
            appendLine(
                "【书签页内容】${pageExcerpt(item.pageText, item.bookmark.title)}"
            )
            appendLine("【所在章节】${item.chapterTitle}")
            appendLine("【书籍名】$bookTitle")
        }
    }.trimEnd() + "\n"

    fun pageExcerpt(pageText: String?, fallback: String): String {
        val normalizedPage = pageText
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            .orEmpty()
        if (normalizedPage.isBlank()) return fallback.trim().ifBlank { "（无法提取页面文字）" }

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

    fun suggestedFileName(bookTitle: String): String {
        val safeTitle = bookTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "LumiBooks" }
        return "$safeTitle-书签与笔记.txt"
    }

    private fun chapterTitle(chapterIndex: Int, chapterTitles: Map<Int, String>): String {
        return chapterTitles[chapterIndex]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "第${chapterIndex + 1}章"
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
