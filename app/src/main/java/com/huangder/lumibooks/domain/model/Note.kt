package com.huangder.lumibooks.domain.model

data class Note(
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long,
    val type: String = "highlight",
    val syncId: String = "",
    val updatedAt: Long = createdAt,
    val isNote: Boolean = note.isNotBlank() || type == "note"
) {
    /** The annotation style (highlight/underline) is independent of note intent. */
    val isNoteEntry: Boolean get() = isNote || note.isNotBlank() || type == "note"
}
