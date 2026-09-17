package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentSelectionTest {

    @Test
    fun directionForEdge_prefersBottomOverTop() {
        assertEquals(
            ReaderSelectionDirection.NEXT,
            ReaderContentSelectionRules.directionForEdge(beyondTop = false, beyondBottom = true)
        )
        assertEquals(
            ReaderSelectionDirection.PREV,
            ReaderContentSelectionRules.directionForEdge(beyondTop = true, beyondBottom = false)
        )
        assertNull(
            ReaderContentSelectionRules.directionForEdge(beyondTop = false, beyondBottom = false)
        )
    }

    @Test
    fun directionForHandleEdge_onlyExtendsOutwards() {
        // 结束手柄拖出页底 → 向后翻页
        assertEquals(
            ReaderSelectionDirection.NEXT,
            ReaderContentSelectionRules.directionForHandleEdge(
                draggingStartHandle = false, beyondTop = false, beyondBottom = true
            )
        )
        // 起始手柄拖出页顶 → 向前翻页
        assertEquals(
            ReaderSelectionDirection.PREV,
            ReaderContentSelectionRules.directionForHandleEdge(
                draggingStartHandle = true, beyondTop = true, beyondBottom = false
            )
        )
        // 向内收手柄（结束手柄越过页顶、起始手柄越过页底）不翻页
        assertNull(
            ReaderContentSelectionRules.directionForHandleEdge(
                draggingStartHandle = false, beyondTop = true, beyondBottom = false
            )
        )
        assertNull(
            ReaderContentSelectionRules.directionForHandleEdge(
                draggingStartHandle = true, beyondTop = false, beyondBottom = true
            )
        )
    }

    @Test
    fun canExtendWithinChapter_isLimitedToTheSameChapter() {
        assertTrue(
            ReaderContentSelectionRules.canExtendWithinChapter(
                targetChapterIndex = 3, selectionChapterIndex = 3
            )
        )
        assertFalse(
            ReaderContentSelectionRules.canExtendWithinChapter(
                targetChapterIndex = 4, selectionChapterIndex = 3
            )
        )
        assertFalse(
            ReaderContentSelectionRules.canExtendWithinChapter(
                targetChapterIndex = -1, selectionChapterIndex = 3
            )
        )
    }

    @Test
    fun focusOffsetForDirection_snapsToPageBoundary() {
        assertEquals(
            100,
            ReaderContentSelectionRules.focusOffsetForDirection(
                ReaderSelectionDirection.NEXT, targetPageStart = 100, targetPageEnd = 200
            )
        )
        assertEquals(
            200,
            ReaderContentSelectionRules.focusOffsetForDirection(
                ReaderSelectionDirection.PREV, targetPageStart = 100, targetPageEnd = 200
            )
        )
    }

    @Test
    fun moveFocus_clampsIntoChapterRange() {
        val selection = ReaderContentSelection(
            chapterIndex = 0, chapterLength = 50, anchor = 10, focus = 20
        )
        assertEquals(0, selection.moveFocus(-5).focus)
        assertEquals(50, selection.moveFocus(999).focus)
        val moved = selection.moveFocus(15)
        assertEquals(10, moved.start)
        assertEquals(15, moved.end)
    }

    @Test
    fun focusCanCrossTheAnchor_directionFlipsButRangeStaysSorted() {
        val selection = ReaderContentSelection(
            chapterIndex = 0, chapterLength = 100, anchor = 40, focus = 70
        )
        val shrunkPastAnchor = selection.extendFocusToPreviousPageEnd(20)
        assertEquals(20, shrunkPastAnchor.start)
        assertEquals(40, shrunkPastAnchor.end)
        assertFalse(shrunkPastAnchor.focusOnEndSide)
    }

    @Test
    fun extendFocusToNextPageStart_usesNewPageFirstCharacter() {
        val selection = ReaderContentSelection(
            chapterIndex = 2, chapterLength = 500, anchor = 120, focus = 180
        )
        val extended = selection.extendFocusToNextPageStart(200)
        assertEquals(120, extended.start)
        assertEquals(200, extended.end)
        assertTrue(extended.focusOnEndSide)
    }

    @Test
    fun selectedText_returnsChapterSliceInRange() {
        val chapter = "0123456789"
        val selection = ReaderContentSelection(
            chapterIndex = 0, chapterLength = chapter.length, anchor = 2, focus = 6
        )
        assertEquals("2345", selection.selectedText(chapter))
    }

    @Test
    fun selectedText_isEmptyForDegenerateRange() {
        val chapter = "0123456789"
        val selection = ReaderContentSelection(
            chapterIndex = 0, chapterLength = chapter.length, anchor = 4, focus = 4
        )
        assertTrue(selection.isEmpty)
        assertEquals("", selection.selectedText(chapter))
    }

    @Test
    fun selectedText_toleratesStaleChapterLength() {
        // 章节文本变化后仍按真实文本长度截取，不抛越界。
        val selection = ReaderContentSelection(
            chapterIndex = 0, chapterLength = 999, anchor = 1, focus = 99
        )
        assertEquals("123456789", selection.selectedText("0123456789"))
    }
}
