package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.util.epub.EpubPagePresentation
import com.huangder.lumibooks.util.epub.EpubPageSide
import com.huangder.lumibooks.util.epub.EpubRenditionLayout

internal data class EpubSpreadPage(
    val target: EpubPageTarget,
    val pageCount: Int,
    val locatorJson: String? = null
)

/** Pages remain in reading order; [firstSide] describes their physical placement. */
internal data class EpubSpreadTarget(
    val first: EpubPageTarget,
    val second: EpubPageTarget? = null,
    val firstSide: EpubPageSide = EpubPageSide.LEFT,
    val fullWidth: Boolean = false
) {
    val left: EpubPageTarget?
        get() = when (firstSide) {
            EpubPageSide.LEFT -> first
            EpubPageSide.RIGHT -> second
            EpubPageSide.CENTER -> null
        }
    val right: EpubPageTarget?
        get() = when (firstSide) {
            EpubPageSide.LEFT -> second
            EpubPageSide.RIGHT -> first
            EpubPageSide.CENTER -> null
        }
    val pages: List<EpubPageTarget> get() = listOfNotNull(first, second)
}

internal sealed interface EpubSpreadPlan {
    data class Ready(val spread: EpubSpreadTarget) : EpubSpreadPlan
    data class NeedsPageCount(val chapterIndex: Int) : EpubSpreadPlan
    data object Boundary : EpubSpreadPlan
}

/**
 * Incremental planner: callers load only a requested chapter count and retry the same operation.
 * A jump anchors a fresh sequence. Remembered links keep backward/forward turns inverse even when
 * an anchored sequence leaves one page before the anchor, or crosses an odd chapter boundary.
 */
internal class EpubSpreadPlanner(
    private val chapterCount: Int,
    private val presentation: (Int) -> EpubPagePresentation,
    reverse: Boolean = false,
    private val isLandscape: Boolean = true
) {
    private val counts = mutableMapOf<Int, Int>()
    private val following = mutableMapOf<EpubSpreadTarget, EpubSpreadTarget>()
    private val preceding = mutableMapOf<EpubSpreadTarget, EpubSpreadTarget>()
    private var reverse = reverse
    private val leadingSide: EpubPageSide
        get() = if (reverse) EpubPageSide.RIGHT else EpubPageSide.LEFT
    private val trailingSide: EpubPageSide
        get() = if (reverse) EpubPageSide.LEFT else EpubPageSide.RIGHT

    fun updatePage(page: EpubSpreadPage) = updatePageCount(page.target.chapterIndex, page.pageCount)

    fun updatePageCount(chapterIndex: Int, pageCount: Int) {
        require(chapterIndex in 0 until chapterCount)
        require(pageCount > 0)
        val old = counts.put(chapterIndex, pageCount)
        if (old != null && old != pageCount) clearHistory()
    }

    fun setReverse(reverse: Boolean) {
        if (this.reverse != reverse) {
            this.reverse = reverse
            clearHistory()
        }
    }

    fun planAt(target: EpubPageTarget): EpubSpreadPlan = buildForward(target).also {
        if (it is EpubSpreadPlan.Ready) clearHistory()
    }

    fun planNext(current: EpubSpreadTarget): EpubSpreadPlan {
        following[current]?.let { return EpubSpreadPlan.Ready(it) }
        return when (val target = adjacent(current.second ?: current.first, 1)) {
            is PageResult.Found -> remember(current, buildForward(target.target), forward = true)
            is PageResult.Missing -> EpubSpreadPlan.NeedsPageCount(target.chapterIndex)
            PageResult.End -> EpubSpreadPlan.Boundary
        }
    }

    fun planPrevious(current: EpubSpreadTarget): EpubSpreadPlan {
        preceding[current]?.let { return EpubSpreadPlan.Ready(it) }
        return when (val previous = adjacent(current.first, -1)) {
            is PageResult.Found -> remember(current, buildBackward(previous.target), forward = false)
            is PageResult.Missing -> EpubSpreadPlan.NeedsPageCount(previous.chapterIndex)
            PageResult.End -> EpubSpreadPlan.Boundary
        }
    }

    private fun buildForward(target: EpubPageTarget): EpubSpreadPlan {
        val first = when (val result = normalize(target)) {
            is PageResult.Found -> result.target
            is PageResult.Missing -> return EpubSpreadPlan.NeedsPageCount(result.chapterIndex)
            PageResult.End -> return EpubSpreadPlan.Boundary
        }
        val info = presentation(first.chapterIndex)
        if (fullWidth(first, info)) return readySingle(first, EpubPageSide.CENTER, true)
        if (fixed(info) && info.preferredSide == trailingSide) return readySingle(first, trailingSide)
        return when (val next = adjacent(first, 1)) {
            PageResult.End -> readySingle(first, side(info, leadingSide))
            is PageResult.Missing -> EpubSpreadPlan.NeedsPageCount(next.chapterIndex)
            is PageResult.Found -> {
                val nextInfo = presentation(next.target.chapterIndex)
                if (!canPair(first, info, next.target, nextInfo)) return readySingle(first, side(info, leadingSide))
                when (val normalized = normalize(next.target)) {
                    is PageResult.Missing -> EpubSpreadPlan.NeedsPageCount(normalized.chapterIndex)
                    PageResult.End -> readySingle(first, side(info, leadingSide))
                    is PageResult.Found -> EpubSpreadPlan.Ready(
                        EpubSpreadTarget(first, normalized.target, leadingSide)
                    )
                }
            }
        }
    }

    private fun buildBackward(last: EpubPageTarget): EpubSpreadPlan {
        val info = presentation(last.chapterIndex)
        if (fullWidth(last, info)) return readySingle(last, EpubPageSide.CENTER, true)
        if (fixed(info) && info.preferredSide == leadingSide) return readySingle(last, leadingSide)
        return when (val previous = adjacent(last, -1)) {
            PageResult.End -> readySingle(last, side(info, leadingSide))
            is PageResult.Missing -> EpubSpreadPlan.NeedsPageCount(previous.chapterIndex)
            is PageResult.Found -> {
                val previousInfo = presentation(previous.target.chapterIndex)
                if (canPair(previous.target, previousInfo, last, info)) {
                    EpubSpreadPlan.Ready(EpubSpreadTarget(previous.target, last, leadingSide))
                } else {
                    readySingle(last, side(info, if (fixed(info)) trailingSide else leadingSide))
                }
            }
        }
    }

    private fun fixed(info: EpubPagePresentation) =
        info.renditionLayout == EpubRenditionLayout.PRE_PAGINATED

    private fun side(info: EpubPagePresentation, fallback: EpubPageSide): EpubPageSide =
        if (fixed(info)) info.preferredSide ?: fallback else fallback

    private fun fullWidth(target: EpubPageTarget, info: EpubPagePresentation): Boolean {
        if (info.isCover && target.pageIndex == 0) return true
        if (!fixed(info)) return false
        if (!info.renditionSpread.allowsSpread(isLandscape) || info.preferredSide == EpubPageSide.CENTER) {
            return true
        }
        // An explicit side takes priority over the common landscape-image heuristic.
        return info.preferredSide == null && info.viewportWidth != null && info.viewportHeight != null &&
            info.viewportWidth > info.viewportHeight
    }

    private fun canPair(
        firstTarget: EpubPageTarget,
        first: EpubPagePresentation,
        secondTarget: EpubPageTarget,
        second: EpubPagePresentation
    ): Boolean {
        if (fullWidth(firstTarget, first) || fullWidth(secondTarget, second) || fixed(first) != fixed(second)) return false
        if (!fixed(first)) return true
        return (first.preferredSide == null || first.preferredSide == leadingSide) &&
            (second.preferredSide == null || second.preferredSide == trailingSide)
    }

    private fun readySingle(target: EpubPageTarget, side: EpubPageSide, full: Boolean = false) =
        EpubSpreadPlan.Ready(EpubSpreadTarget(target, firstSide = side, fullWidth = full))

    private fun normalize(target: EpubPageTarget): PageResult {
        if (target.chapterIndex !in 0 until chapterCount) return PageResult.End
        val count = counts[target.chapterIndex] ?: return PageResult.Missing(target.chapterIndex)
        return PageResult.Found(target.copy(pageIndex = target.pageIndex.coerceIn(0, count - 1)))
    }

    private fun adjacent(target: EpubPageTarget, direction: Int): PageResult {
        val normalized = when (val result = normalize(target)) {
            is PageResult.Found -> result.target
            else -> return result
        }
        val count = counts.getValue(normalized.chapterIndex)
        val page = normalized.pageIndex + direction
        if (page in 0 until count) return PageResult.Found(normalized.copy(pageIndex = page))
        val chapter = normalized.chapterIndex + direction
        if (chapter !in 0 until chapterCount) return PageResult.End
        if (direction > 0) return PageResult.Found(EpubPageTarget(chapter, 0))
        val previousCount = counts[chapter] ?: return PageResult.Missing(chapter)
        return PageResult.Found(EpubPageTarget(chapter, previousCount - 1))
    }

    private fun remember(current: EpubSpreadTarget, result: EpubSpreadPlan, forward: Boolean): EpubSpreadPlan {
        if (result is EpubSpreadPlan.Ready) {
            val before = if (forward) current else result.spread
            val after = if (forward) result.spread else current
            following[before] = after
            preceding[after] = before
        }
        return result
    }

    private fun clearHistory() {
        following.clear()
        preceding.clear()
    }

    private sealed interface PageResult {
        data class Found(val target: EpubPageTarget) : PageResult
        data class Missing(val chapterIndex: Int) : PageResult
        data object End : PageResult
    }
}
