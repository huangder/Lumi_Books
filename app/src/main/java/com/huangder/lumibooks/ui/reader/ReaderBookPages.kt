package com.huangder.lumibooks.ui.reader

/** Only confirmed counts participate in book numbering; an empty chapter is known as zero. */
internal data class ReaderBookPages(
    val generation: Long = 0,
    val chapterCounts: List<Int?> = emptyList(),
    val chapterStarts: Map<Int, List<Int>> = emptyMap()
) {
    val totalPages: Int?
        get() = if (chapterCounts.isNotEmpty() && chapterCounts.all { it != null })
            chapterCounts.sumOf { it ?: 0 } else null

    fun pageNumber(chapter: Int, page: Int): Int? {
        val count = chapterCounts.getOrNull(chapter) ?: return null
        if (page !in 0 until count) return null
        var preceding = 0
        for (index in 0 until chapter) preceding += chapterCounts[index] ?: return null
        return preceding + page + 1
    }

    fun pageAtCharacter(chapter: Int, character: Int): Int? {
        val starts = chapterStarts[chapter]?.takeIf { it.isNotEmpty() } ?: return null
        val result = starts.binarySearch(character.coerceAtLeast(0))
        return if (result >= 0) result else (-result - 2).coerceAtLeast(0)
    }

    fun withChapter(chapter: Int, count: Int, starts: List<Int>? = null): ReaderBookPages {
        if (chapter !in chapterCounts.indices || count < 0) return this
        if (chapterCounts[chapter] == count && (starts == null || chapterStarts[chapter] == starts)) return this
        return copy(chapterCounts = chapterCounts.toMutableList().apply { this[chapter] = count },
            chapterStarts = if (starts == null) chapterStarts else chapterStarts + (chapter to starts))
    }
}

internal fun readerBookPageLabel(
    pages: ReaderBookPages,
    chapter: Int,
    page: Int,
    rightChapter: Int? = null,
    rightPage: Int? = null
): String? {
    val total = pages.totalPages?.takeIf { it > 0 } ?: return null
    val first = pages.pageNumber(chapter, page) ?: return null
    val last = if (rightPage != null) pages.pageNumber(rightChapter ?: chapter, rightPage) else null
    if (rightPage != null && last == null) return null
    return if (last != null && last != first) "$first–$last / $total" else "$first / $total"
}

internal enum class ReaderReturnSide { LEFT, RIGHT }

internal fun readerPhysicalPageLabel(page: Int, total: Int, rightPage: Int? = null): String {
    val range = if (rightPage != null && rightPage != page) "${page + 1}–${rightPage + 1}"
        else "${page + 1}"
    return "$range / $total"
}

internal data class ReaderVisibleCharacters(val chapter: Int, val start: Int, val end: Int)

internal fun readerLocatorCharacter(locator: String?): Int? = locator?.let {
    runCatching { org.json.JSONObject(it) }.getOrNull()
        ?.takeIf { json -> json.has("textPosition") }
        ?.optInt("textPosition")?.takeIf { offset -> offset >= 0 }
}

/** Character anchors remain comparable while a new page index is being built. */
internal fun readerReturnSideAtCharacter(
    targetChapter: Int, targetCharacter: Int,
    chapter: Int, character: Int,
    visible: List<ReaderVisibleCharacters>
): ReaderReturnSide? {
    if (visible.any { it.chapter == targetChapter && targetCharacter in it.start until it.end }) return null
    return if (targetChapter < chapter || targetChapter == chapter && targetCharacter < character)
        ReaderReturnSide.LEFT else ReaderReturnSide.RIGHT
}

/** Positions are ordered in book order, independently of whether global page counts are ready. */
internal fun readerReturnSide(
    targetChapter: Int, targetPosition: Float,
    chapter: Int, position: Float,
    rightChapter: Int? = null, rightPosition: Float? = null
): ReaderReturnSide? {
    if (targetChapter == chapter && targetPosition == position ||
        targetChapter == rightChapter && targetPosition == rightPosition) return null
    return if (targetChapter < chapter || targetChapter == chapter && targetPosition < position)
        ReaderReturnSide.LEFT else ReaderReturnSide.RIGHT
}
