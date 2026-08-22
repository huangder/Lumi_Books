package com.huangder.lumibooks.domain.model

import com.huangder.lumibooks.util.parser.TxtOffsetMigrationStep
import com.huangder.lumibooks.util.parser.mapTxtOffsetThroughSteps

object TxtAnnotationMigrationPlanner {
    fun plan(
        notes: List<Note>,
        stepsByChapter: Map<Int, List<TxtOffsetMigrationStep>>,
        updatedTextsByChapter: Map<Int, String>
    ): AnnotationEditPlan {
        val updates = mutableListOf<Note>()
        val deletes = mutableListOf<Note>()
        notes.forEach { note ->
            val steps = stepsByChapter[note.chapterIndex] ?: return@forEach
            val updatedText = updatedTextsByChapter[note.chapterIndex] ?: return@forEach
            val start = mapTxtOffsetThroughSteps(note.startPosition, steps, endBias = false)
                .coerceIn(0, updatedText.length)
            val end = mapTxtOffsetThroughSteps(note.endPosition, steps, endBias = true)
                .coerceIn(start, updatedText.length)
            if (start == end) {
                deletes += note
            } else {
                updates += note.copy(
                    startPosition = start,
                    endPosition = end,
                    selectedText = updatedText.substring(start, end)
                )
            }
        }
        return AnnotationEditPlan(
            deletes = deletes,
            updates = updates,
            inserts = emptyList()
        )
    }
}
