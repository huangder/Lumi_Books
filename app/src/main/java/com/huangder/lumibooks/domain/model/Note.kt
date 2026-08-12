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
    /** "highlight" = 高亮（背景色块）, "underline" = 划线 */
    val type: String = "highlight"
) {
    val isUnderline: Boolean get() = type == "underline"
}
