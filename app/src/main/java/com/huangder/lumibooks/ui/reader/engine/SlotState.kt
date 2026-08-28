package com.huangder.lumibooks.ui.reader.engine

/**
 * 页级槽位状态。
 *
 * 双页对开模式下，一个槽位表示一个跨页单元：
 * [pageIndex] 是主页面（左页；章首单独右页时为主页面），
 * [rightPageIndex] 是右半页（可能为 -1，表示无右页）。
 */
data class SlotState(
    var chapterIndex: Int = -1,
    var pageIndex: Int = -1,
    var globalPageIndex: Int = -1,
    var isLoaded: Boolean = false,
    val contentView: PageContentView,
    var rightChapterIndex: Int = -1,
    var rightPageIndex: Int = -1,
    var rightGlobalPageIndex: Int = -1,
    var rightIsLoaded: Boolean = false,
    var rightContentView: PageContentView? = null,
    /** 双页模式章首单独右页时，主内容位于右半页 */
    var primaryIsRight: Boolean = false,
    /** The page count used to render this slot, retained if the shared layout cache is evicted. */
    var chapterTotalPages: Int = 0
)
