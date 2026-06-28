package com.ebook.reader.util.parser

data class Chapter(
    val index: Int,
    val title: String,
    val content: String,
    val htmlContent: String = ""  // 完整章节HTML，WebView渲染用
)

data class BookContent(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val coverPath: String? = null
)

/**
 * 电子书解析器接口
 * 解析文件 → 按章节拆分 → 提供HTML内容
 * 分页由WebView CSS columns处理，不在解析器层分页
 */
interface BookParser {
    fun parse(filePath: String): BookContent
    fun getChapterContent(chapterIndex: Int): String
    fun getChapterHtml(chapterIndex: Int): String
    fun getChapterCount(): Int
}
