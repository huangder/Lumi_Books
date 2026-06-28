package com.ebook.reader.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubParser(private val context: Context? = null) : BookParser {
    private var chapters: List<Chapter> = emptyList()
    private var bookTitle: String = ""
    private var bookAuthor: String = ""
    private var basePath: String = ""

    override fun parse(filePath: String): BookContent {
        val file = File(filePath)
        val zipFile = ZipFile(file)

        // 读取container.xml获取OPF路径
        val containerEntry = zipFile.getEntry("META-INF/container.xml")
        val containerContent = zipFile.getInputStream(containerEntry).bufferedReader().readText()
        val opfPath = extractOpfPath(containerContent)

        // 读取OPF文件
        val opfEntry = zipFile.getEntry(opfPath)
        val opfContent = zipFile.getInputStream(opfEntry).bufferedReader().readText()
        basePath = opfPath.substringBeforeLast("/")

        // 解析标题和作者
        bookTitle = extractMetadata(opfContent, "dc:title") ?: file.nameWithoutExtension
        bookAuthor = extractMetadata(opfContent, "dc:creator") ?: "未知作者"

        // 解析章节
        chapters = extractChapters(zipFile, opfContent)

        // 提取封面
        val coverPath = extractCover(zipFile, opfContent)

        zipFile.close()

        return BookContent(
            title = bookTitle,
            author = bookAuthor,
            chapters = chapters,
            coverPath = coverPath
        )
    }

    private fun extractOpfPath(containerXml: String): String {
        val rootfileRegex = """full-path="([^"]+)"""".toRegex()
        return rootfileRegex.find(containerXml)?.groupValues?.get(1) ?: "content.opf"
    }

    private fun extractMetadata(opfContent: String, tag: String): String? {
        val regex = """<$tag[^>]*>([^<]+)</$tag>""".toRegex()
        return regex.find(opfContent)?.groupValues?.get(1)
    }

    private fun extractChapters(zipFile: ZipFile, opfContent: String): List<Chapter> {
        val result = mutableListOf<Chapter>()

        // 解析spine顺序
        val spineRegex = """<spine[^>]*>(.*?)</spine>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val spineContent = spineRegex.find(opfContent)?.groupValues?.get(1) ?: ""
        val itemrefs = """idref="([^"]+)"""".toRegex().findAll(spineContent)
            .map { it.groupValues[1] }.toList()

        // 解析manifest
        val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""
        val items = mutableMapOf<String, String>()
        """<item\s+id="([^"]+)"\s+href="([^"]+)"[^>]*/>""".toRegex()
            .findAll(manifestContent).forEach { match ->
                items[match.groupValues[1]] = match.groupValues[2]
            }

        // 按spine顺序读取章节
        for ((index, itemref) in itemrefs.withIndex()) {
            val href = items[itemref] ?: continue
            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href

            try {
                val entry = zipFile.getEntry(fullPath)
                if (entry != null) {
                    val rawHtml = zipFile.getInputStream(entry).bufferedReader().readText()
                    val processedHtml = processHtml(zipFile, rawHtml)
                    val plainText = stripHtml(rawHtml)

                    result.add(
                        Chapter(
                            index = index,
                            title = extractTitle(rawHtml) ?: "第${index + 1}章",
                            content = plainText,
                            htmlContent = processedHtml
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 回退：如果spine为空，读取所有HTML
        if (result.isEmpty()) {
            val htmlEntries = zipFile.entries().toList().filter {
                it.name.endsWith(".html") || it.name.endsWith(".xhtml") || it.name.endsWith(".htm")
            }
            for ((index, entry) in htmlEntries.withIndex()) {
                try {
                    val rawHtml = zipFile.getInputStream(entry).bufferedReader().readText()
                    val processedHtml = processHtml(zipFile, rawHtml)
                    val plainText = stripHtml(rawHtml)
                    if (plainText.isNotBlank()) {
                        result.add(
                            Chapter(
                                index = index,
                                title = "第${index + 1}章",
                                content = plainText,
                                htmlContent = processedHtml
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    /**
     * 处理HTML，将图片转为Base64内嵌，使WebView能直接显示
     */
    private fun processHtml(zipFile: ZipFile, html: String): String {
        var result = html

        // 匹配所有 <img> 标签中的 src 属性（不限定属性顺序）
        // 同时匹配 src="..." 和 src='...' 两种引号
        val imgTagRegex = """<img\s[^>]*?>""".toRegex(RegexOption.IGNORE_CASE)
        val srcRegex = """src\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)

        result = imgTagRegex.replace(result) { tagMatch ->
            val tag = tagMatch.value
            val srcMatch = srcRegex.find(tag) ?: return@replace tag
            val imgPath = srcMatch.groupValues[1]

            // 跳过已经是data URI的图片
            if (imgPath.startsWith("data:")) return@replace tag

            // 解析完整路径
            val fullPath = resolveImagePath(imgPath)
            try {
                val entry = findEntry(zipFile, fullPath)
                if (entry != null) {
                    val bytes = zipFile.getInputStream(entry).readBytes()
                    if (bytes.isEmpty()) return@replace tag

                    val mimeType = detectMimeType(imgPath, bytes)
                    // SVG不转Base64（WebView原生支持更好）
                    if (mimeType == "image/svg+xml") return@replace tag

                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    // 替换src属性为data URI
                    tag.replace(srcRegex, """src="data:$mimeType;base64,$base64"""")
                } else {
                    tag
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tag
            }
        }

        // body 隐藏直到 JS 设置好 columns
        return """
            |<html>
            |<head>
            |<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            |<style>
            |  * { box-sizing: border-box; }
            |  body { font-family: sans-serif; font-size: 18px; line-height: 1.6; letter-spacing: 0.03em; color: #333; background: #fff; text-align: justify; word-wrap: break-word; overflow-wrap: break-word; margin: 0; padding: 0; overflow: hidden; visibility: hidden; }
            |  img { max-width: 100%; max-height: 85vh; height: auto; display: block; margin: 12px auto; border-radius: 4px; object-fit: contain; }
            |  p { margin: 8px 0; text-indent: 2em; }
            |  h1, h2, h3 { margin: 16px 0 8px 0; text-align: left; }
            |  h1 { font-size: 1.6em; } h2 { font-size: 1.3em; } h3 { font-size: 1.1em; }
            |  table { max-width: 100%; border-collapse: collapse; }
            |  td, th { padding: 4px 8px; border: 1px solid #ddd; }
            |</style>
            |</head>
            |<body>$result</body>
            |</html>
        """.trimMargin()
    }

    /**
     * 解析图片路径：处理相对路径、绝对路径、.. 等情况
     */
    private fun resolveImagePath(imgPath: String): String {
        // 已经是绝对路径
        if (imgPath.startsWith("/")) return imgPath.substring(1)

        // 拼接basePath
        val rawPath = if (basePath.isNotEmpty()) "$basePath/$imgPath" else imgPath

        // 规范化路径（处理 ../ 等）
        return normalizePath(rawPath)
    }

    /**
     * 规范化路径，处理 ../ 和 ./
     */
    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                ".." -> { if (result.isNotEmpty()) result.removeAt(result.size - 1) }
                "." -> { /* 跳过 */ }
                "" -> { if (result.isEmpty()) result.add(part) } // 保留开头的空字符串（绝对路径）
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    /**
     * 在ZIP中查找文件，尝试多种路径变体
     */
    private fun findEntry(zipFile: ZipFile, path: String): java.util.zip.ZipEntry? {
        // 直接查找
        zipFile.getEntry(path)?.let { return it }

        // 尝试URL解码（处理 %20 等编码）
        val decoded = try { java.net.URLDecoder.decode(path, "UTF-8") } catch (e: Exception) { path }
        if (decoded != path) {
            zipFile.getEntry(decoded)?.let { return it }
        }

        // 尝试去掉开头的 ./
        val withoutDotSlash = path.removePrefix("./")
        if (withoutDotSlash != path) {
            zipFile.getEntry(withoutDotSlash)?.let { return it }
        }

        // 模糊匹配：在ZIP中搜索同名文件
        val fileName = path.substringAfterLast("/")
        if (fileName.isNotEmpty()) {
            zipFile.entries().toList().forEach { entry ->
                if (entry.name.endsWith("/$fileName") || entry.name == fileName) {
                    return entry
                }
            }
        }

        return null
    }

    /**
     * 检测图片MIME类型（结合扩展名和文件头）
     */
    private fun detectMimeType(path: String, bytes: ByteArray): String {
        // 先按扩展名判断
        val extMime = when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".gif", true) -> "image/gif"
            path.endsWith(".svg", true) -> "image/svg+xml"
            path.endsWith(".webp", true) -> "image/webp"
            else -> null
        }
        if (extMime != null) return extMime

        // 按文件头magic bytes判断
        if (bytes.size >= 4) {
            return when {
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "image/gif"
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "image/webp"
                else -> "image/png"
            }
        }
        return "image/png"
    }

    private fun stripHtml(html: String): String {
        val htmlTagRegex = """<[^>]+>""".toRegex()
        return html
            .replace(htmlTagRegex, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun extractTitle(html: String): String? {
        val titleRegex = """<title[^>]*>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE)
        return titleRegex.find(html)?.groupValues?.get(1)
    }

    /**
     * 从 EPUB 中提取封面图片并保存到内部存储
     * 查找顺序：
     * 1. OPF meta name="cover" → manifest href
     * 2. manifest 中 id 包含 "cover" 的图片
     * 3. 文件名包含 "cover" 的图片
     */
    private fun extractCover(zipFile: ZipFile, opfContent: String): String? {
        val ctx = context ?: return null

        try {
            // 1. 从 OPF meta 中找 cover ID
            val coverIdRegex = """<meta\s+name="cover"\s+content="([^"]+)"""".toRegex()
            val coverId = coverIdRegex.find(opfContent)?.groupValues?.get(1)

            // 2. 从 manifest 中找图片 href
            val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""

            var coverHref: String? = null

            // 先找 meta 指定的 cover
            if (coverId != null) {
                val itemRegex = """<item\s+id="${Regex.escape(coverId)}"\s+href="([^"]+)"""".toRegex()
                coverHref = itemRegex.find(manifestContent)?.groupValues?.get(1)
            }

            // 再找 id 或 href 包含 "cover" 的图片
            if (coverHref == null) {
                """<item\s+id="([^"]*cover[^"]*)"\s+href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                    .find(manifestContent)?.let { coverHref = it.groupValues[2] }
            }
            if (coverHref == null) {
                """<item\s+[^>]*href="([^"]*cover[^"]*\.(jpg|jpeg|png|webp))"""".toRegex(RegexOption.IGNORE_CASE)
                    .find(manifestContent)?.let { coverHref = it.groupValues[1] }
            }

            // 最后找文件名包含 cover 的图片
            if (coverHref == null) {
                zipFile.entries().toList().forEach { entry ->
                    if (entry.name.contains("cover", ignoreCase = true) &&
                        (entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true) ||
                         entry.name.endsWith(".png", true) || entry.name.endsWith(".webp", true))) {
                        coverHref = entry.name
                        return@forEach
                    }
                }
            }

            val href = coverHref ?: return null

            // 3. 读取图片并保存到内部存储
            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href
            val entry = findEntry(zipFile, fullPath) ?: return null
            val bytes = zipFile.getInputStream(entry).readBytes()
            if (bytes.isEmpty()) return null

            // 保存到 app 内部存储
            val coversDir = File(ctx.filesDir, "covers")
            coversDir.mkdirs()
            // 用文件路径的 hash 作为文件名，避免冲突
            val coverFile = File(coversDir, "${zipFile.name.hashCode()}.jpg")
            coverFile.writeBytes(bytes)

            return coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun getChapterContent(chapterIndex: Int): String {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].content else ""
    }

    override fun getChapterHtml(chapterIndex: Int): String {
        return if (chapterIndex in chapters.indices) chapters[chapterIndex].htmlContent else ""
    }

    override fun getChapterCount(): Int = chapters.size
}
