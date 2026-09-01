package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterStructureTest {
    @Test
    fun matchingPatternIndex_preservesSupportedHeadingForms() {
        assertEquals(0, TxtChapterStructure.matchingPatternIndex("第一百二十三章 开始"))
        assertEquals(0, TxtChapterStructure.matchingPatternIndex("第42话 尾声"))
        assertEquals(0, TxtChapterStructure.matchingPatternIndex("剑中仙 第一章：谁敢动我妹！"))
        assertEquals(0, TxtChapterStructure.matchingPatternIndex("===剑中仙 第二章：界狱塔！"))
        assertEquals(1, TxtChapterStructure.matchingPatternIndex("卷三 风起"))
        assertEquals(1, TxtChapterStructure.matchingPatternIndex("篇12"))
        assertEquals(2, TxtChapterStructure.matchingPatternIndex("cHaPtEr  19 Arrival"))
        assertEquals(3, TxtChapterStructure.matchingPatternIndex("一百〇二"))
    }

    @Test
    fun matchingPatternIndex_rejectsBodyLikePrefixes() {
        assertNull(TxtChapterStructure.matchingPatternIndex("第一个普通句子"))
        assertNull(TxtChapterStructure.matchingPatternIndex("他说：第一章不是这里"))
        assertNull(TxtChapterStructure.matchingPatternIndex("Chapter house"))
        assertNull(TxtChapterStructure.matchingPatternIndex("Lumi 15MB 正文"))
        assertNull(TxtChapterStructure.matchingPatternIndex(""))
    }

    @Test
    fun decoratedHeading_matchesOriginalBoundsAndDelimiters() {
        assertTrue(TxtChapterStructure.isDecoratedHeading("<序章>"))
        assertTrue(TxtChapterStructure.isDecoratedHeading("<${"a".repeat(48)}>"))
        assertFalse(TxtChapterStructure.isDecoratedHeading("<>"))
        assertFalse(TxtChapterStructure.isDecoratedHeading("<a<b>"))
        assertFalse(TxtChapterStructure.isDecoratedHeading("<${"a".repeat(49)}>"))
    }
}
