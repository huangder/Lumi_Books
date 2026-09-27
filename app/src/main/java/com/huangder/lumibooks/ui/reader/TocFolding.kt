package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.parser.TocEntry

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
 *
 * Folding is based only on the hierarchy level. This keeps the behavior consistent for
 * arbitrary parent titles instead of depending on a title keyword such as "volume".
 */
internal fun findTocFoldGroups(entries: List<TocEntry>): Map<Int, Int> = buildMap {
    entries.forEachIndexed { index, entry ->
        var endExclusive = index + 1
        while (endExclusive < entries.size) {
            val candidate = entries[endExclusive]
            if (candidate.level <= entry.level) break
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
