package com.ebook.reader.util.parser

data class Chapter(
    val index: Int,
    val title: String,
    val content: CharSequence,  // EPUB 为 Spanned（保留格式），TXT/PDF 为 String
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
    fun getChapterContent(chapterIndex: Int): CharSequence
    fun getChapterHtml(chapterIndex: Int): String
    fun getChapterCount(): Int

    /**
     * 🔥 轻量预渲染HTML：只返回body内容片段（不含完整<html>/<head>/<style>）。
     * 用于预渲染相邻章节DOM，减少Base64传输量20-40%。
     * 默认回退到 getChapterHtml。
     */
    fun getChapterHtmlLight(chapterIndex: Int): String {
        val full = getChapterHtml(chapterIndex)
        if (full.isEmpty()) return full
        val bodyMatch = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(full)
        return bodyMatch?.groupValues?.get(1) ?: full
    }
}
