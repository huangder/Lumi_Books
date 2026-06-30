package com.ebook.reader.ui.reader.engine

/**
 * 单页布局元数据。
 * 记录一章内某一页在 StaticLayout 中的行范围 + 字符偏移。
 */
data class PageLayout(
    val chapterIndex: Int,
    val pageIndex: Int,
    val startLine: Int,
    val endLine: Int,           // exclusive
    val startCharOffset: Int,
    val endCharOffset: Int      // exclusive
)
