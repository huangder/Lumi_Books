package com.huangder.lumibooks.util.parser

data class Chapter(
    val index: Int,
    val title: String,
    val content: CharSequence,  // EPUB 为 Spanned（保留格式），TXT/PDF 为 String
    val htmlContent: String = ""  // 完整章节HTML，WebView渲染用
)

/**
 * 目录条目——支持层级结构（卷→章）
 * @param title 显示标题
 * @param level 层级深度（1=顶级，2=卷下章节）
 * @param chapterIndex 对应 spine 中的索引（isGroup=true 时为 -1）
 * @param isGroup true=分组标题（不可点击，如"第X卷"），false=实际章节
 */
data class TocEntry(
    val title: String,
    val level: Int = 1,
    val chapterIndex: Int = -1,
    val isGroup: Boolean = false
)

data class BookContent(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val coverPath: String? = null,
    val tocEntries: List<TocEntry> = emptyList()  // 层级目录（EPUB NCX/nav）
)

/**
 * 电子书解析器接口
 * 解析文件 → 按章节拆分 → 提供HTML内容
 * 分页由WebView CSS columns处理，不在解析器层分页
 */
interface BookParser {
    fun parse(filePath: String): BookContent
    fun getChapterContent(chapterIndex: Int): CharSequence
    fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean = true): String
    fun getChapterCount(): Int
    /** 清空 HTML 缓存（排版设置变更时调用） */
    fun clearHtmlCache() {}

    /** 段间距（dp），0 = 使用默认值 */
    var paragraphSpacingDp: Float
    /** 首行缩进字符数，0 = 使用默认值 */
    var firstLineIndentChars: Float
    /** 阅读区域内容宽度（像素），用于图片缩放。0 = 未设置，回退到 DisplayMetrics */
    var contentWidth: Int

    /**
     * 轻量级封面提取：只提取封面图片路径，不解析章节内容。
     * 用于导入时快速获取封面，避免解析全部章节/图片的开销。
     */
    fun extractCoverPath(filePath: String): String? = null

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
