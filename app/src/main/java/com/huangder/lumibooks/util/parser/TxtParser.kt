package com.huangder.lumibooks.util.parser

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class TxtParser : BookParser {
    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    private var content: String = ""
    private var chapters: List<Chapter> = emptyList()

    /**
     * 按优先级尝试多种编码读取文件。
     * 使用 CharsetDecoder + REPORT 模式，避免 UTF-8 静默产生乱码。
     * （String(bytes, UTF_8) 对无效字节用 � 替换，不抛异常，导致 GBK fallback 永远不执行）
     */
    private fun readFileWithEncoding(file: File): String {
        val bytes = file.readBytes()
        // BOM 检测
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        // 无 BOM：用 REPORT 模式检测 UTF-8 有效性
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            // UTF-8 无效 → 回退到 GBK（中文 TXT 最常见编码）
            try {
                String(bytes, Charset.forName("GBK"))
            } catch (_: Exception) {
                // 最后兜底：UTF-8 REPLACE 模式（至少能显示部分字符）
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    override fun parse(filePath: String): BookContent {
        val file = File(filePath)
        content = readFileWithEncoding(file)

        val fileName = file.nameWithoutExtension
        chapters = splitChapters(content)

        return BookContent(
            title = fileName,
            author = "未知作者",
            chapters = chapters
        )
    }

    private fun splitChapters(content: String): List<Chapter> {
        // 支持多种章节格式：匹配章节标记所在行，用于定位章节起点
        val patterns = listOf(
            // 中文：第X章、第X节、第X回、第X卷
            Regex("(?m)^第[一二三四五六七八九十百千零\\d]+[章节回卷]"),
            // 中文：卷X、篇X
            Regex("(?m)^[卷篇][一二三四五六七八九十百千零\\d]+[章回]?"),
            // 英文：Chapter X、CHAPTER X
            Regex("(?m)^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            // 数字开头：1、1.、1、
            Regex("(?m)^\\d{1,3}[.、\\s]"),
            // 第X章 带数字
            Regex("(?m)^第\\d+章")
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
            // 提取章节标记所在行的完整文本作为标题（去除首尾空白，限制50字）
            val firstLineEnd = chapterContent.indexOf('\n').let { if (it == -1) chapterContent.length else it }
            val title = chapterContent.substring(0, firstLineEnd).trim().take(50)
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

    override fun getChapterContent(chapterIndex: Int): CharSequence {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].content else ""
    }

    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].htmlContent else ""
    }

    override fun getChapterCount(): Int = chapters.size
}
