package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookNotesExportFormatterTest {

    @Test
    fun formatsHighlightsNotesAndBookmarksInSeparateSections() {
        val highlight = note(selectedText = "高亮原文", noteText = "", chapterIndex = 0)
        val note = note(selectedText = "笔记原文", noteText = "我的笔记", chapterIndex = 1)
        val bookmark = Bookmark(
            id = 7,
            bookId = "book",
            chapterIndex = 2,
            position = 3f,
            title = "第3章 第4页",
            createdAt = 3L
        )

        val text = BookNotesExportFormatter.format(
            bookTitle = "测试书",
            notes = listOf(highlight, note),
            bookmarks = listOf(
                BookmarkExportItem(
                    bookmark = bookmark,
                    chapterTitle = "第三章",
                    pageText = "一二三四五六七八九十甲乙"
                )
            ),
            chapterTitles = mapOf(0 to "开篇", 1 to "转折")
        )

        assertTrue(text.contains("【高亮：】"))
        assertTrue(text.contains("【高亮内容】高亮原文"))
        assertTrue(text.contains("【所在章节】开篇"))
        assertTrue(text.contains("【笔记：】"))
        assertTrue(text.contains("【笔记原文内容】笔记原文"))
        assertTrue(text.contains("【用户所写笔记内容】我的笔记"))
        assertTrue(text.contains("【书签：】"))
        assertTrue(text.contains("【书签页内容】一二三四五…八九十甲乙"))
        assertTrue(text.contains("【所在章节】第三章"))
        assertTrue(text.contains("【书籍名】测试书"))
    }

    @Test
    fun pageExcerptCountsUnicodeCodePointsAndUsesFirstParagraph() {
        val excerpt = BookNotesExportFormatter.pageExcerpt(
            pageText = "😀甲乙丙丁戊\n\n中间内容收尾甲乙丙丁戊",
            fallback = "fallback"
        )

        assertEquals("😀甲乙丙丁…甲乙丙丁戊", excerpt)
    }

    @Test
    fun pageExcerptFallsBackWhenPageTextIsUnavailable() {
        assertEquals(
            "第2章 第3页",
            BookNotesExportFormatter.pageExcerpt(null, "第2章 第3页")
        )
    }

    @Test
    fun suggestedFileNameRemovesInvalidPathCharacters() {
        assertEquals(
            "书_名-书签与笔记.txt",
            BookNotesExportFormatter.suggestedFileName("书/名")
        )
    }

    private fun note(
        selectedText: String,
        noteText: String,
        chapterIndex: Int
    ) = Note(
        id = chapterIndex.toLong(),
        bookId = "book",
        chapterIndex = chapterIndex,
        startPosition = 0,
        endPosition = selectedText.length,
        selectedText = selectedText,
        note = noteText,
        color = "#FFEB3B",
        createdAt = chapterIndex.toLong()
    )
}
