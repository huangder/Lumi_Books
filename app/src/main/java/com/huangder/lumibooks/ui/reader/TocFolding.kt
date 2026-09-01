package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.parser.TocEntry

private val volumeTitlePattern = run {
    val number = "[0-9０-９一二三四五六七八九十百千零两〇]+"
    Regex(
        """^(?:第\s*$number\s*[卷篇部巻](?=$|\s|[:：·、，,．.\-—])""" +
            """|[卷篇部巻]\s*$number\s*[章回]?(?=$|\s|[:：·、，,．.\-—])""" +
            """|.+(?:\s|[:：·、，,．.\-—])第\s*$number\s*[卷篇部巻]\s*$""" +
            """|(?:Volume|Vol\.|Book|Part)\s*(?:[0-9]+|[IVXLCDM]+)(?=$|\s|[:：.\-—])""" +
            """|제\s*[0-9]+\s*권(?=$|\s|[:：.\-—]))""",
        RegexOption.IGNORE_CASE
    )
}

internal data class TocVisibleEntry(
    val sourceIndex: Int,
    val entry: TocEntry
)

internal data class TocViewportItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

internal fun isTocItemVisible(
    itemIndex: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    visibleItems: List<TocViewportItem>
): Boolean {
    if (itemIndex < 0) return false
    val item = visibleItems.firstOrNull { it.index == itemIndex } ?: return false
    return item.offset < viewportEndOffset && item.offset + item.size > viewportStartOffset
}

internal fun collapsedTocAncestors(
    sourceIndex: Int,
    foldGroups: Map<Int, Int>,
    collapsedGroups: Set<Int>
): Set<Int> = collapsedGroups.filterTo(mutableSetOf()) { groupIndex ->
    sourceIndex > groupIndex && sourceIndex < (foldGroups[groupIndex] ?: groupIndex + 1)
}

/**
 * Returns foldable entry indexes and the exclusive end of each entry's descendants.
 * Explicit EPUB groups use TOC levels; inferred volume headings delimit flat TXT/MOBI lists.
 */
internal fun findTocFoldGroups(entries: List<TocEntry>): Map<Int, Int> = buildMap {
    entries.forEachIndexed { index, entry ->
        val inferredVolume = !entry.isGroup && volumeTitlePattern.containsMatchIn(entry.title.trim())
        if (!entry.isGroup && !inferredVolume) return@forEachIndexed

        var endExclusive = index + 1
        while (endExclusive < entries.size) {
            val candidate = entries[endExclusive]
            val isBoundary = if (entry.isGroup) {
                candidate.level <= entry.level
            } else {
                candidate.level < entry.level ||
                    (candidate.level == entry.level &&
                        (candidate.isGroup || volumeTitlePattern.containsMatchIn(candidate.title.trim())))
            }
            if (isBoundary) break
            endExclusive++
        }

        if (endExclusive > index + 1) put(index, endExclusive)
    }
}

internal fun visibleTocEntries(
    entries: List<TocEntry>,
    foldGroups: Map<Int, Int>,
    collapsedGroups: Set<Int>
): List<TocVisibleEntry> = buildList {
    var hiddenUntil = 0
    entries.forEachIndexed { index, entry ->
        if (index < hiddenUntil) return@forEachIndexed

        add(TocVisibleEntry(index, entry))
        if (index in collapsedGroups) {
            hiddenUntil = maxOf(hiddenUntil, foldGroups[index] ?: index + 1)
        }
    }
}

internal fun currentTocVisibleIndex(
    entries: List<TocEntry>,
    visibleEntries: List<TocVisibleEntry>,
    foldGroups: Map<Int, Int>,
    collapsedGroups: Set<Int>,
    currentChapter: Int
): Int {
    val sourceIndex = entries.indexOfFirst { it.chapterIndex == currentChapter }
    if (sourceIndex < 0) return -1

    val directIndex = visibleEntries.indexOfFirst { it.sourceIndex == sourceIndex }
    if (directIndex >= 0) return directIndex

    return visibleEntries.indexOfLast { visible ->
        visible.sourceIndex in collapsedGroups &&
            sourceIndex > visible.sourceIndex &&
            sourceIndex < (foldGroups[visible.sourceIndex] ?: visible.sourceIndex + 1)
    }
}
