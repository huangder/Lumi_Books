package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.highlight.RuleStyleJson

/** Input lists are scoped to the current book. An empty tag selects untagged marks. */
internal data class ReadingMarkFilter(
    val tag: String? = null,
    val color: String? = null,
    val underlineMode: Int? = null
) {
    fun matches(bookmark: Bookmark): Boolean = matchesTags(bookmark.tags)

    fun matches(note: Note): Boolean {
        if (!matchesTags(note.tags)) return false
        if (note.isNoteEntry) return true
        if (color != null && note.color != color) return false
        if (note.type != "underline" || underlineMode == null) return true
        val mode = RuleStyleJson.decode(note.styleSnapshotJson)?.underlineMode
            ?.takeIf { it in 1..4 } ?: HighlightRule.UNDERLINE_WAVE
        return mode == underlineMode
    }

    private fun matchesTags(tags: List<String>): Boolean = when (tag) {
        null -> true
        "" -> tags.isEmpty()
        else -> tag in tags
    }
}
