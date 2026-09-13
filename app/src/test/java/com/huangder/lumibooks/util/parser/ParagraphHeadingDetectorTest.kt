package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphHeadingDetectorTest {
    @Test
    fun normalizeDropsWhitespaceForTitleComparison() {
        assertEquals("第一章开始", normalizeHeadingText("第一章  开始"))
        assertEquals("Chapter1", normalizeHeadingText(" Chapter\n1 "))
    }

    @Test
    fun chapterTitleParagraphMatchesAfterNormalization() {
        assertTrue(isChapterTitleParagraph("第一章　开始", normalizeHeadingText("第一章 开始")))
    }

    @Test
    fun bodyParagraphIsNotTreatedAsChapterTitle() {
        assertFalse(
            isChapterTitleParagraph(
                "第一章 开始",
                normalizeHeadingText("第二章 结束")
            )
        )
        assertFalse(isChapterTitleParagraph("正文段落", normalizeHeadingText("")))
    }
}
