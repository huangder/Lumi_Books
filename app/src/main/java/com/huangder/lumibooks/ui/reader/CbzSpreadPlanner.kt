package com.huangder.lumibooks.ui.reader

/**
 * 双页对开的分页计划：把扁平页序切成"对开页"。
 *
 * [aloneFirstPage] 为 true 时第 1 页（封面）单独成屏，这是漫画单行本的常见排布。
 * 页码始终是逻辑页号，[CbzSpread.firstPage] 是阅读顺序上较前的一页；
 * 实际左右摆放由阅读方向决定（从右往左时较前的一页在右侧）。
 */
internal data class CbzSpread(
    val firstPage: Int?,
    val secondPage: Int?
)

internal object CbzSpreadPlanner {
    fun spreadCount(pageCount: Int, aloneFirstPage: Boolean): Int {
        if (pageCount <= 0) return 0
        if (!aloneFirstPage) return (pageCount + 1) / 2
        return 1 + ((pageCount - 1) + 1) / 2
    }

    fun spreadFor(spreadIndex: Int, pageCount: Int, aloneFirstPage: Boolean): CbzSpread? {
        if (pageCount <= 0 || spreadIndex < 0) return null
        if (spreadIndex >= spreadCount(pageCount, aloneFirstPage)) return null
        if (aloneFirstPage) {
            if (spreadIndex == 0) return CbzSpread(firstPage = 0, secondPage = null)
            val firstPage = 1 + (spreadIndex - 1) * 2
            return buildSpread(firstPage, pageCount)
        }
        return buildSpread(spreadIndex * 2, pageCount)
    }

    /** The spread that currently shows [pageIndex], so jumps land on a visible page. */
    fun spreadIndexOfPage(pageIndex: Int, pageCount: Int, aloneFirstPage: Boolean): Int {
        if (pageCount <= 0) return 0
        val page = pageIndex.coerceIn(0, pageCount - 1)
        if (aloneFirstPage && page == 0) return 0
        val offset = if (aloneFirstPage) 1 else 0
        return offset + (page - offset) / 2
    }

    private fun buildSpread(firstPage: Int, pageCount: Int): CbzSpread {
        val left = firstPage.takeIf { it in 0 until pageCount }
        val right = (firstPage + 1).takeIf { it in 0 until pageCount }
        return CbzSpread(firstPage = left, secondPage = right)
    }
}
