package com.huangder.lumibooks.ui.reader.engine

/** A physical reflow page identified without depending on a cached layout. */
internal data class PageLocation(
    val chapterIndex: Int,
    val pageIndex: Int
)

/** One horizontal tablet spread. The left page is always the primary page. */
internal data class SpreadTarget(
    val left: PageLocation?,
    val right: PageLocation?
) {
    val primary: PageLocation? get() = left ?: right
}

/**
 * Pure planner for the native reader's continuous two-page mode.
 *
 * Pairing starts at [anchor]. This makes the exact page restored after an
 * open, jump, or relayout stay on the left, while sequential navigation keeps
 * the same parity and can put the next chapter in the unfinished right half
 * of the previous spread.
 */
internal object ReaderSpreadPlanner {
    fun spreadFor(
        target: PageLocation,
        anchor: PageLocation,
        pageCounts: List<Int>
    ): SpreadTarget? {
        val sequence = pageSequence(0, pageCounts)
        val anchorIndex = sequence.indexOf(anchor)
        val targetIndex = sequence.indexOf(target)
        if (anchorIndex < 0 || targetIndex < 0) return null

        // floorDiv also groups pages before the anchor in pairs. When the
        // prefix contains a single leftover page, keep the existing reader
        // convention of displaying it on the left rather than creating an
        // otherwise empty left half.
        val start = anchorIndex + Math.floorDiv(targetIndex - anchorIndex, 2) * 2
        if (start < 0) {
            return SpreadTarget(left = sequence.firstOrNull(), right = null)
        }
        return SpreadTarget(
            left = sequence.getOrNull(start),
            right = sequence.getOrNull(start + 1)
        )
    }

    fun next(
        current: SpreadTarget,
        anchor: PageLocation,
        pageCounts: List<Int>
    ): SpreadTarget? = adjacent(current, anchor, pageCounts, +1)

    fun previous(
        current: SpreadTarget,
        anchor: PageLocation,
        pageCounts: List<Int>
    ): SpreadTarget? = adjacent(current, anchor, pageCounts, -1)

    fun pageSequence(anchorChapter: Int, pageCounts: List<Int>): List<PageLocation> {
        if (anchorChapter !in pageCounts.indices) return emptyList()
        return buildList {
            for (chapter in anchorChapter until pageCounts.size) {
                repeat(pageCounts[chapter].coerceAtLeast(0)) { page ->
                    add(PageLocation(chapter, page))
                }
            }
        }
    }

    private fun adjacent(
        current: SpreadTarget,
        anchor: PageLocation,
        pageCounts: List<Int>,
        direction: Int
    ): SpreadTarget? {
        val first = current.left ?: current.right ?: return null
        val last = current.right ?: current.left ?: return null
        val sequence = pageSequence(0, pageCounts)
        val firstIndex = sequence.indexOf(first)
        val lastIndex = sequence.indexOf(last)
        if (firstIndex < 0 || lastIndex < 0) return null

        val targetIndex = if (direction > 0) lastIndex + 1 else firstIndex - 1
        val target = sequence.getOrNull(targetIndex) ?: return null
        return spreadFor(target, anchor, pageCounts)
    }
}
