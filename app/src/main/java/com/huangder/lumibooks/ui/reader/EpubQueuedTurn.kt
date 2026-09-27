package com.huangder.lumibooks.ui.reader

internal data class EpubQueuedTurn(
    val source: EpubPageTarget,
    val target: EpubPageTarget,
    val generation: Long,
    val direction: Int
) {
    fun accepts(current: EpubPageTarget, currentGeneration: Long, candidate: EpubPageTarget?): Boolean =
        current == source && currentGeneration == generation && candidate != null &&
            candidate.chapterIndex == target.chapterIndex &&
            (target.pageIndex == Int.MAX_VALUE || candidate.pageIndex == target.pageIndex)
}
