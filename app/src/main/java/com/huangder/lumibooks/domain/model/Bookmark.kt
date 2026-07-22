package com.huangder.lumibooks.domain.model

data class Bookmark(
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val position: Float,
    val title: String,
    val createdAt: Long
) {
    /** New bookmarks encode a chapter character offset as a negative position. */
    val characterOffset: Int?
        get() = if (position < 0f) (-position - 1f).toInt() else null
}

fun bookmarkPositionForCharacterOffset(offset: Int): Float = -(offset.coerceAtLeast(0) + 1).toFloat()
