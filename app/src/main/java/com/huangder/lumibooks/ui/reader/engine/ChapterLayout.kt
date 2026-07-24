package com.huangder.lumibooks.ui.reader.engine

import android.text.StaticLayout

/**
 * 一章的完整布局信息。
 * 包含分页后的 PageLayout 列表和原始 StaticLayout。
 */
data class ChapterLayout(
    val chapterIndex: Int,
    val pages: List<PageLayout>,
    val staticLayout: StaticLayout,
    val totalPages: Int,
    /** 本章之前所有章节的累计页数 */
    val cumulativePagesBefore: Int = 0
)
