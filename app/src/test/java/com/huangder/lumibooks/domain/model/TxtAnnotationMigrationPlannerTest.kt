package com.huangder.lumibooks.domain.model

import com.huangder.lumibooks.util.parser.TxtOffsetMigrationStep
import com.huangder.lumibooks.util.parser.TxtTextMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtAnnotationMigrationPlannerTest {
    private fun note(start: Int, end: Int, selected: String) = Note(
        id = 1,
        bookId = "book",
        chapterIndex = 0,
        startPosition = start,
        endPosition = end,
        selectedText = selected,
        note = "",
        color = "yellow",
        createdAt = 1,
        type = "highlight"
    )

    @Test
    fun insertionBeforeAnnotationMovesRangeAndRefreshesSelectedText() {
        val original = "abcdefghij"
        val updated = "XXX$original"
        val plan = TxtAnnotationMigrationPlanner.plan(
            notes = listOf(note(5, 8, "fgh")),
            stepsByChapter = mapOf(
                0 to listOf(TxtOffsetMigrationStep(listOf(TxtTextMatch(0, 0)), 3))
            ),
            updatedTextsByChapter = mapOf(0 to updated)
        )

        assertEquals(8, plan.updates.single().startPosition)
        assertEquals(11, plan.updates.single().endPosition)
        assertEquals("fgh", plan.updates.single().selectedText)
    }

    @Test
    fun replacementInsideAnnotationExpandsRangeAndSelectedText() {
        val updated = "abcXYZghij"
        val plan = TxtAnnotationMigrationPlanner.plan(
            notes = listOf(note(2, 8, "cdefgh")),
            stepsByChapter = mapOf(
                0 to listOf(TxtOffsetMigrationStep(listOf(TxtTextMatch(3, 6)), 3))
            ),
            updatedTextsByChapter = mapOf(0 to updated)
        )

        assertEquals(2, plan.updates.single().startPosition)
        assertEquals(8, plan.updates.single().endPosition)
        assertEquals("cXYZgh", plan.updates.single().selectedText)
    }

    @Test
    fun deletingEntireAnnotatedTextDeletesRecord() {
        val updated = "abij"
        val plan = TxtAnnotationMigrationPlanner.plan(
            notes = listOf(note(2, 8, "cdefgh")),
            stepsByChapter = mapOf(
                0 to listOf(TxtOffsetMigrationStep(listOf(TxtTextMatch(2, 8)), 0))
            ),
            updatedTextsByChapter = mapOf(0 to updated)
        )

        assertEquals(listOf(1L), plan.deletes.map { it.id })
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun annotationsInUnchangedChaptersAreNotIncludedInPlan() {
        val untouched = note(1, 3, "bc").copy(chapterIndex = 1)
        val plan = TxtAnnotationMigrationPlanner.plan(
            notes = listOf(untouched),
            stepsByChapter = emptyMap(),
            updatedTextsByChapter = emptyMap()
        )

        assertEquals(AnnotationEditPlan.EMPTY, plan)
    }
}
