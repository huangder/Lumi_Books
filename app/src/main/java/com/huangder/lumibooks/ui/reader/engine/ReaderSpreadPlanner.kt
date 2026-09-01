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
 * Pairing starts at [anchorChapter]. This makes a fresh open or a direct
 * chapter jump begin on the left, while sequential navigation keeps the
 * global page parity and can put the next chapter in the unfinished right
 * half of the previous spread.
 */
internal object ReaderSpreadPlanner {
    fun spreadFor(
        target: PageLocation,
        anchorChapter: Int,
        pageCounts: List<Int>
    ): SpreadTarget? {
        val sequence = pageSequence(anchorChapter, pageCounts)
        val index = sequence.indexOf(target)
        if (index < 0) return null
        // A target may be the second page of a spread (for example after a
        // chapter boundary). Always return the physical spread containing it,
        // rather than treating that page as a new left page.
        val start = index - (index and 1)
        return SpreadTarget(
            left = sequence.getOrNull(start),
            right = sequence.getOrNull(start + 1)
        )
    }

    fun next(
        current: SpreadTarget,
        anchorChapter: Int,
        pageCounts: List<Int>
    ): SpreadTarget? = adjacent(current, anchorChapter, pageCounts, +2)

    fun previous(
        current: SpreadTarget,
        anchorChapter: Int,
        pageCounts: List<Int>
    ): SpreadTarget? = adjacent(current, anchorChapter, pageCounts, -2)

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
        anchorChapter: Int,
        pageCounts: List<Int>,
        delta: Int
    ): SpreadTarget? {
        val first = current.left ?: current.right ?: return null
        val last = current.right ?: current.left ?: return null
        var sequence = pageSequence(anchorChapter, pageCounts)
        // Pages exposed by the bridge before a direct-jump anchor are outside
        // the new parity run. Use the complete known prefix for both
        // directions so turning forward again returns to the jump target.
        if (sequence.indexOf(first) < 0 || sequence.indexOf(last) < 0) {
            sequence = pageSequence(0, pageCounts)
        }
        val firstIndex = sequence.indexOf(first)
        val lastIndex = sequence.indexOf(last)
        if (firstIndex < 0 || lastIndex < 0) return null

        if (delta > 0) {
            val targetStart = lastIndex + 1
            if (targetStart !in sequence.indices) return null
            return SpreadTarget(
                left = sequence[targetStart],
                right = sequence.getOrNull(targetStart + 1)
            )
        }

        // A direct jump deliberately starts its destination chapter on the
        // left. The first backward spread is therefore the previous chapter's
        // trailing one or two pages, regardless of the old global parity.
        if (anchorChapter > 0 && first == PageLocation(anchorChapter, 0)) {
            val previousChapter = (anchorChapter - 1 downTo 0)
                .firstOrNull { (pageCounts.getOrNull(it)?.coerceAtLeast(0) ?: 0) > 0 }
            if (previousChapter != null) {
                val count = pageCounts[previousChapter].coerceAtLeast(0)
                val end = count - 1
                val start = (end - 1).coerceAtLeast(0)
                return SpreadTarget(
                    left = PageLocation(previousChapter, start),
                    right = PageLocation(previousChapter, end).takeIf { end > start }
                )
            }
        }

        val targetEnd = firstIndex - 1
        if (targetEnd !in sequence.indices) return null
        val targetStart = (targetEnd - 1).coerceAtLeast(0)
        return SpreadTarget(
            left = sequence[targetStart],
            right = sequence[targetEnd].takeIf { targetEnd > targetStart }
        )
    }
}
