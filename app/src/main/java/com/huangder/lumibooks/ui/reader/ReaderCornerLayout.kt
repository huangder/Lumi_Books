package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderCornerContent

/**
 * 角落信息区里各类内容允许的最大行数。
 *
 * 章节信息往往很长（「第 N 章 + 标题」），只占一行、超出部分用省略号，避免信息区被撑成两行；
 * 组合固定单行；其余单项保留原有的两行余量以适配大字号与窄屏。
 */
internal fun readerCornerContentMaxLines(content: ReaderCornerContent): Int =
    if (content == ReaderCornerContent.CHAPTER_INFO || content.isCombination) 1 else 2

/** 左侧章节仅在同排右侧无内容时占满整排。 */
internal fun readerLeftCornerUsesFullRow(
    left: ReaderCornerContent,
    right: ReaderCornerContent
): Boolean = left in setOf(
    ReaderCornerContent.CHAPTER_INFO,
    ReaderCornerContent.CHAPTER_INFO_PAGE_NUMBER,
    ReaderCornerContent.CHAPTER_INFO_BOOK_PROGRESS
) && right == ReaderCornerContent.NONE
