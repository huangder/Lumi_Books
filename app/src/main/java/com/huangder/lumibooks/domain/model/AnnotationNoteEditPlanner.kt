package com.huangder.lumibooks.domain.model

data class AnnotationEditPlan(
    val deletes: List<Note>,
    val updates: List<Note>,
    val inserts: List<Note>
) {
    val isEmpty: Boolean get() = deletes.isEmpty() && updates.isEmpty() && inserts.isEmpty()

    companion object {
        val EMPTY = AnnotationEditPlan(emptyList(), emptyList(), emptyList())
    }
}

/**
 * 将单章节标注区间运算转换为可事务持久化的 Note 增删改计划。
 * locator 在任何边界变化后置空，由上层按最新章节文本统一重建。
 */
object AnnotationNoteEditPlanner {
    fun replaceRange(
        existing: List<Note>,
        chapterText: String,
        bookId: String,
        chapterIndex: Int,
        start: Int,
        end: Int,
        type: String,
        color: String,
        createdAt: Long
    ): AnnotationEditPlan {
        if (!isValidRange(chapterText, start, end)) return AnnotationEditPlan.EMPTY

        val deletes = mutableListOf<Note>()
        val updates = mutableListOf<Note>()
        val inserts = mutableListOf<Note>()
        relevant(existing, bookId, chapterIndex, type).forEach { note ->
            if (!overlaps(note.startPosition, note.endPosition, start, end)) return@forEach
            val hasLeft = note.startPosition < start
            val hasRight = note.endPosition > end
            when {
                hasLeft -> {
                    updates += resizeExisting(note, chapterText, note.startPosition, start)
                    if (hasRight) {
                        inserts += newRemainder(note, chapterText, end, note.endPosition, keepNote = false)
                    }
                }
                hasRight -> updates += resizeExisting(note, chapterText, end, note.endPosition)
                else -> deletes += note
            }
        }
        inserts += Note(
            id = 0,
            bookId = bookId,
            chapterIndex = chapterIndex,
            startPosition = start,
            endPosition = end,
            selectedText = chapterText.substring(start, end),
            note = "",
            color = color,
            createdAt = createdAt,
            type = type
        )
        return AnnotationEditPlan(
            deletes = deletes.sortedBy { it.id },
            updates = updates.sortedBy { it.startPosition },
            inserts = inserts.sortedWith(compareBy<Note> { it.startPosition }.thenBy { it.endPosition })
        )
    }

    fun removeRange(
        existing: List<Note>,
        chapterText: String,
        bookId: String,
        chapterIndex: Int,
        start: Int,
        end: Int,
        type: String
    ): AnnotationEditPlan {
        if (!isValidRange(chapterText, start, end)) return AnnotationEditPlan.EMPTY

        val deletes = mutableListOf<Note>()
        val updates = mutableListOf<Note>()
        val inserts = mutableListOf<Note>()
        relevant(existing, bookId, chapterIndex, type).forEach { note ->
            if (!overlaps(note.startPosition, note.endPosition, start, end)) return@forEach
            val hasLeft = note.startPosition < start
            val hasRight = note.endPosition > end
            when {
                hasLeft -> {
                    updates += resizeExisting(note, chapterText, note.startPosition, start)
                    if (hasRight) {
                        inserts += newRemainder(note, chapterText, end, note.endPosition, keepNote = false)
                    }
                }
                hasRight -> updates += resizeExisting(note, chapterText, end, note.endPosition)
                else -> deletes += note
            }
        }
        return AnnotationEditPlan(
            deletes = deletes.sortedBy { it.id },
            updates = updates.sortedBy { it.startPosition },
            inserts = inserts.sortedWith(compareBy<Note> { it.startPosition }.thenBy { it.endPosition })
        )
    }

    private fun relevant(
        notes: List<Note>,
        bookId: String,
        chapterIndex: Int,
        type: String
    ): List<Note> = notes.filter {
        it.bookId == bookId && it.chapterIndex == chapterIndex && it.type == type &&
            it.startPosition >= 0 && it.endPosition > it.startPosition
    }

    private fun resizeExisting(note: Note, text: String, start: Int, end: Int): Note = note.copy(
        startPosition = start,
        endPosition = end,
        startLocatorJson = null,
        endLocatorJson = null,
        selectedText = text.substring(start, end)
    )

    private fun newRemainder(
        note: Note,
        text: String,
        start: Int,
        end: Int,
        keepNote: Boolean
    ): Note = note.copy(
        id = 0,
        startPosition = start,
        endPosition = end,
        startLocatorJson = null,
        endLocatorJson = null,
        selectedText = text.substring(start, end),
        note = if (keepNote) note.note else ""
    )

    private fun overlaps(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && firstEnd > secondStart

    private fun isValidRange(text: String, start: Int, end: Int): Boolean =
        start >= 0 && end > start && end <= text.length
}
