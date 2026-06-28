package com.ebook.reader.util.parser

import java.io.File

class TxtParser : BookParser {
    private var content: String = ""
    private var chapters: List<Chapter> = emptyList()

    override fun parse(filePath: String): BookContent {
        val file = File(filePath)
        content = file.readText()

        val fileName = file.nameWithoutExtension
        chapters = splitChapters(content)

        return BookContent(
            title = fileName,
            author = "未知作者",
            chapters = chapters
        )
    }

    private fun splitChapters(content: String): List<Chapter> {
        // 支持多种章节格式
        val patterns = listOf(
            // 中文：第X章、第X节、第X回、第X卷
            Regex("(?m)^第[一二三四五六七八九十百千零\\d]+[章节回卷]\\s*\\S"),
            // 中文：卷X、篇X
            Regex("(?m)^[卷篇][一二三四五六七八九十百千零\\d]+[章回]?\\s*\\S"),
            // 英文：Chapter X、CHAPTER X
            Regex("(?m)^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            // 数字开头：1、1.、1、
            Regex("(?m)^\\d{1,3}[.、\\s]\\s*\\S"),
            // 第X章 带数字
            Regex("(?m)^第\\d+章\\s*\\S")
        )

        var matches = emptyList<MatchResult>()
        for (pattern in patterns) {
            matches = pattern.findAll(content).toList()
            if (matches.size >= 2) break // 至少匹配到2个才算有效
        }

        // 无章节标记 → 智能分段（按段落块，每段约3000字）
        if (matches.size < 2) {
            return splitByContent(content)
        }

        val result = mutableListOf<Chapter>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val chapterContent = content.substring(start, end).trim()
            val title = matches[i].value.trim().take(50)
            result.add(
                Chapter(
                    index = i,
                    title = title,
                    content = chapterContent,
                    htmlContent = wrapHtml(chapterContent)
                )
            )
        }
        return result
    }

    /**
     * 无章节标记时，按内容智能分段
     * 每段约 targetSize 字，在空行处断开
     */
    private fun splitByContent(content: String): List<Chapter> {
        val targetSize = 3000
        val paragraphs = content.split(Regex("\n{2,}")) // 按空行分段
        val result = mutableListOf<Chapter>()
        val buffer = StringBuilder()
        var chapterIndex = 0

        for (para in paragraphs) {
            if (buffer.length + para.length > targetSize && buffer.isNotEmpty()) {
                result.add(createChapter(chapterIndex, buffer.toString().trim()))
                chapterIndex++
                buffer.clear()
            }
            buffer.append(para).append("\n\n")
        }
        if (buffer.isNotBlank()) {
            result.add(createChapter(chapterIndex, buffer.toString().trim()))
        }
        return result.ifEmpty { listOf(createChapter(0, content)) }
    }

    private fun createChapter(index: Int, content: String): Chapter {
        val title = content.lineSequence().firstOrNull()?.take(30)?.trim() ?: "第${index + 1}部分"
        return Chapter(
            index = index,
            title = "第${index + 1}章 $title",
            content = content,
            htmlContent = wrapHtml(content)
        )
    }

    private fun wrapHtml(text: String): String {
        // 将每段包装为<p>标签，适配CSS column分页
        val paragraphs = text.split("\n").filter { it.isNotBlank() }
        val htmlBody = paragraphs.joinToString("") { line ->
            val escaped = line.trim()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            "<p>$escaped</p>"
        }
        return """
            |<html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            |<style>
            |  * { box-sizing: border-box; }
            |  body { font-family: sans-serif; font-size: 18px; line-height: 1.7; color: #333; margin: 0; }
            |  p { text-indent: 2em; margin: 8px 0; }
            |</style></head>
            |<body>$htmlBody</body></html>
        """.trimMargin()
    }

    override fun getChapterContent(chapterIndex: Int): String {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].content else ""
    }

    override fun getChapterHtml(chapterIndex: Int): String {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].htmlContent else ""
    }

    override fun getChapterCount(): Int = chapters.size
}
