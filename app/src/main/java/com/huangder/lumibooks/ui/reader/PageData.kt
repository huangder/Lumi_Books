package com.ebook.reader.ui.reader

import android.graphics.Bitmap

/**
 * 一页预渲染内容：Bitmap + 章节/页码元数据。
 * 对动画层透明——动画只看到 Bitmap，不关心来自哪个章节。
 */
data class PageData(
    val bitmap: Bitmap,
    val chapterIndex: Int,
    val pageIndex: Int,       // 本章内页码 (0-based)
    val chapterTotal: Int     // 本章总页数（用于 UI 显示 "第X/Y页"）
) {
    /** 本章内已到最后一页 */
    val isLastInChapter: Boolean get() = pageIndex >= chapterTotal - 1

    /** 本章内第一页 */
    val isFirstInChapter: Boolean get() = pageIndex <= 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageData) return false
        return chapterIndex == other.chapterIndex && pageIndex == other.pageIndex
    }

    override fun hashCode(): Int = 31 * chapterIndex + pageIndex
}
