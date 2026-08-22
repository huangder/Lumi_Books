package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationNoteEditPlannerTest {
    private val chapterText = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun note(
        id: Long,
        start: Int,
        end: Int,
        type: String = "underline",
        color: String = "red",
        noteText: String = "",
        createdAt: Long = 100L
    ) = Note(
        id = id,
        bookId = "book",
        chapterIndex = 2,
        startPosition = start,
        endPosition = end,
        selectedText = chapterText.substring(start, end),
        note = noteText,
        color = color,
        createdAt = createdAt,
        type = type
    )

    @Test
    fun replacementUpdatesLeftRemainderDeletesCoveredNoteAndInsertsNewCoverage() {
        val first = note(1, 10, 20, color = "red", noteText = "memo")
        val second = note(2, 22, 25, color = "green")

        val plan = AnnotationNoteEditPlanner.replaceRange(
            existing = listOf(first, second),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 15,
            end = 30,
            type = "underline",
            color = "blue",
            createdAt = 999L
        )

        assertEquals(listOf(2L), plan.deletes.map { it.id })
        assertEquals(
            listOf(
                first.copy(
                    endPosition = 15,
                    selectedText = chapterText.substring(10, 15)
                )
            ),
            plan.updates
        )
        assertEquals(1, plan.inserts.size)
        assertEquals(0L, plan.inserts.single().id)
        assertEquals(15, plan.inserts.single().startPosition)
        assertEquals(30, plan.inserts.single().endPosition)
        assertEquals("blue", plan.inserts.single().color)
        assertEquals("", plan.inserts.single().note)
        assertEquals(999L, plan.inserts.single().createdAt)
    }

    @Test
    fun replacementInsideNoteKeepsIdentityAndNoteOnLeftAndCreatesRightRemainder() {
        val original = note(7, 10, 30, noteText = "memo", createdAt = 123L)

        val plan = AnnotationNoteEditPlanner.replaceRange(
            existing = listOf(original),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 15,
            end = 20,
            type = "underline",
            color = "blue",
            createdAt = 999L
        )

        assertEquals(emptyList<Note>(), plan.deletes)
        assertEquals(
            listOf(
                original.copy(
                    endPosition = 15,
                    selectedText = chapterText.substring(10, 15)
                )
            ),
            plan.updates
        )
        assertEquals(2, plan.inserts.size)
        val replacement = plan.inserts.first { it.color == "blue" }
        val right = plan.inserts.first { it.color == "red" }
        assertEquals(15, replacement.startPosition)
        assertEquals(20, replacement.endPosition)
        assertEquals(20, right.startPosition)
        assertEquals(30, right.endPosition)
        assertEquals("", right.note)
        assertEquals(123L, right.createdAt)
    }

    @Test
    fun differentAnnotationTypeIsNotPartOfPlan() {
        val highlight = note(1, 10, 20, type = "highlight", color = "yellow")
        val underline = note(2, 10, 20, type = "underline", color = "red")

        val plan = AnnotationNoteEditPlanner.replaceRange(
            existing = listOf(highlight, underline),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 15,
            end = 25,
            type = "underline",
            color = "blue",
            createdAt = 999L
        )

        assertTrue(plan.deletes.none { it.id == highlight.id })
        assertTrue(plan.updates.none { it.id == highlight.id })
        assertTrue(plan.inserts.none { it.type == "highlight" })
    }

    @Test
    fun removeInsideNoteSplitsAndKeepsNoteOnlyOnPersistedLeftPart() {
        val original = note(7, 10, 30, noteText = "memo", createdAt = 123L)

        val plan = AnnotationNoteEditPlanner.removeRange(
            existing = listOf(original),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 15,
            end = 20,
            type = "underline"
        )

        assertEquals(emptyList<Note>(), plan.deletes)
        assertEquals("memo", plan.updates.single().note)
        assertEquals(10, plan.updates.single().startPosition)
        assertEquals(15, plan.updates.single().endPosition)
        assertEquals(1, plan.inserts.size)
        assertEquals(20, plan.inserts.single().startPosition)
        assertEquals(30, plan.inserts.single().endPosition)
        assertEquals("", plan.inserts.single().note)
    }

    @Test
    fun removeFullyCoveredNoteDeletesItWithoutReplacement() {
        val original = note(7, 10, 20)

        val plan = AnnotationNoteEditPlanner.removeRange(
            existing = listOf(original),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 5,
            end = 25,
            type = "underline"
        )

        assertEquals(listOf(original), plan.deletes)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun invalidRangeProducesEmptyPlan() {
        val plan = AnnotationNoteEditPlanner.removeRange(
            existing = listOf(note(1, 1, 3)),
            chapterText = chapterText,
            bookId = "book",
            chapterIndex = 2,
            start = 5,
            end = 5,
            type = "underline"
        )

        assertEquals(AnnotationEditPlan.EMPTY, plan)
    }
}
