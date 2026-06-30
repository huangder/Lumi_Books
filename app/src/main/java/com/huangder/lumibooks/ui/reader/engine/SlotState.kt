package com.huangder.lumibooks.ui.reader.engine

/**
 * 页级槽位状态。
 */
data class SlotState(
    var chapterIndex: Int = -1,
    var pageIndex: Int = -1,
    var globalPageIndex: Int = -1,
    var isLoaded: Boolean = false,
    val surfaceView: PageSurfaceView
)
