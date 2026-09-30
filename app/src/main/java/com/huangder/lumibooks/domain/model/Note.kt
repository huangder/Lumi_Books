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
    val isNote: Boolean = note.isNotBlank() || type == "note",
    val origin: String = ORIGIN_MANUAL,
    val sourceRuleId: String? = null,
    val sourceMatchKey: String? = null,
    val styleSnapshotJson: String? = null,
    val tags: List<String> = emptyList()
) {
    /** The annotation style (highlight/underline) is independent of note intent. */
    val isNoteEntry: Boolean get() = isNote || note.isNotBlank() || type == "note"

    val isGeneratedByHighlightRule: Boolean
        get() = origin == ORIGIN_HIGHLIGHT_RULE && sourceRuleId != null && sourceMatchKey != null

    fun detachFromHighlightRule(): Note = copy(
        origin = ORIGIN_MANUAL,
        sourceRuleId = null,
        sourceMatchKey = null
    )

    companion object {
        const val ORIGIN_MANUAL = "manual"
        const val ORIGIN_HIGHLIGHT_RULE = "highlight_rule"
    }
}
