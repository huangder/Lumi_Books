package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtEditOperationsTest {
    @Test
    fun minimalReplacementReturnsNullForIdenticalText() {
        assertNull(computeMinimalTxtReplacement(3, "same", "same"))
    }

    @Test
    fun minimalReplacementHandlesInsertionAtStartMiddleAndEnd() {
        assertEquals(TxtReplaceRange(1, 0, 0, "X"), computeMinimalTxtReplacement(1, "abc", "Xabc"))
        assertEquals(TxtReplaceRange(1, 1, 1, "X"), computeMinimalTxtReplacement(1, "abc", "aXbc"))
        assertEquals(TxtReplaceRange(1, 3, 3, "X"), computeMinimalTxtReplacement(1, "abc", "abcX"))
    }

    @Test
    fun minimalReplacementHandlesDeletionAndReplacement() {
        assertEquals(TxtReplaceRange(2, 2, 4, ""), computeMinimalTxtReplacement(2, "abcdef", "abef"))
        assertEquals(TxtReplaceRange(2, 2, 4, "XYZ"), computeMinimalTxtReplacement(2, "abcdef", "abXYZef"))
    }

    @Test
    fun minimalReplacementDoesNotSplitSurrogatePairs() {
        val result = computeMinimalTxtReplacement(0, "A😀B", "A😁B")
        assertEquals(TxtReplaceRange(0, 1, 3, "😁"), result)
    }

    @Test
    fun minimalReplacementHandlesEmptyAndNewlineText() {
        assertEquals(TxtReplaceRange(0, 0, 0, "内容"), computeMinimalTxtReplacement(0, "", "内容"))
        assertEquals(TxtReplaceRange(0, 0, 2, ""), computeMinimalTxtReplacement(0, "内容", ""))
        assertEquals(
            TxtReplaceRange(0, 3, 3, "新行\n"),
            computeMinimalTxtReplacement(0, "第一\n第二", "第一\n新行\n第二")
        )
    }

    @Test
    fun chapterStructureDetectsHeadingAndSplitThresholdChanges() {
        assertEquals(false, TxtChapterStructure.mayChange("普通正文", "普通正文已修改"))
        assertEquals(true, TxtChapterStructure.mayChange("普通正文", "第2章 新章\n普通正文"))
        assertEquals(true, TxtChapterStructure.mayChange("第1章 旧名\n正文", "第1章 新名\n正文"))
        assertEquals(
            true,
            TxtChapterStructure.mayChange("a".repeat(31_999), "a".repeat(32_001))
        )
    }

    @Test
    fun offsetMappingKeepsBeforeMovesAfterAndExpandsInsideRange() {
        val steps = listOf(TxtOffsetMigrationStep(listOf(TxtTextMatch(5, 8)), replacementLength = 5))
        assertEquals(3, mapTxtOffsetThroughSteps(3, steps, endBias = false))
        assertEquals(12, mapTxtOffsetThroughSteps(10, steps, endBias = false))
        assertEquals(5, mapTxtOffsetThroughSteps(6, steps, endBias = false))
        assertEquals(10, mapTxtOffsetThroughSteps(6, steps, endBias = true))
    }

    @Test
    fun offsetMappingAccumulatesMultipleOrderedSteps() {
        val steps = listOf(
            TxtOffsetMigrationStep(listOf(TxtTextMatch(2, 2)), replacementLength = 3),
            TxtOffsetMigrationStep(listOf(TxtTextMatch(8, 10)), replacementLength = 0)
        )
        assertEquals(11, mapTxtOffsetThroughSteps(10, steps, endBias = false))
    }
}
