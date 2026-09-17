package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderCornerContent

/**
 * 角落信息区里各类内容允许的最大行数。
 *
 * 章节信息往往很长（「第 N 章 + 标题」），只占一行、超出部分用省略号，避免信息区被撑成两行；
 * 其余内容（阅读进度、页码、电量、时间）本身很短，保留两行余量以适配大字号与窄屏。
 */
internal fun readerCornerContentMaxLines(content: ReaderCornerContent): Int =
    if (content == ReaderCornerContent.CHAPTER_INFO) 1 else 2
