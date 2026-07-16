package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Spanned
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.text.RegexOption

/**
 * EPUB 解析器 — 按需加载章节
 *
 * parse() 只提取元数据（标题、作者、章节列表、封面），不处理图片。
 * getChapterHtml() / getChapterContent() 按需读取并处理单个章节。
 */
class EpubParser(private val context: Context? = null) : BookParser {
    private var chapters: List<Chapter> = emptyList()
    private var bookTitle: String = ""
    private var bookAuthor: String = ""
    private var basePath: String = ""
    private var epubFilePath: String = ""

    // 章节路径列表（spine 顺序）
    private var chapterPaths: List<String> = emptyList()

    // 按需缓存
    private val htmlCache = mutableMapOf<Int, String>()
    private val contentCache = mutableMapOf<Int, CharSequence>()

    override fun parse(filePath: String): BookContent {
        epubFilePath = filePath
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

        // 提取章节路径和标题（只读HTML提取标题，不处理图片）
        val chapterInfoList = extractChapterInfo(zipFile, opfContent)
        chapterPaths = chapterInfoList.map { it.second }

        // 构造 Chapter 列表（content/htmlContent 为空，按需加载）
        chapters = chapterInfoList.mapIndexed { index, (title, _) ->
            Chapter(index = index, title = title, content = "", htmlContent = "")
        }

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

    /**
     * 提取章节路径和标题（读HTML提取标题，但不处理图片）
     */
    private fun extractChapterInfo(zipFile: ZipFile, opfContent: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()

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

        // 按spine顺序记录章节路径
        for ((index, itemref) in itemrefs.withIndex()) {
            val href = items[itemref] ?: continue
            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href

            try {
                val entry = findEntry(zipFile, fullPath)
                if (entry != null) {
                    val rawHtml = zipFile.getInputStream(entry).bufferedReader().readText()
                    val title = extractTitle(rawHtml) ?: "第${index + 1}章"
                    result.add(title to fullPath)
                    android.util.Log.d("EpubParser", "extractChapterInfo: [$index] title=$title path=$fullPath")
                } else {
                    android.util.Log.w("EpubParser", "extractChapterInfo: [$index] findEntry FAILED for $fullPath")
                }
            } catch (e: Exception) {
                android.util.Log.e("EpubParser", "extractChapterInfo: [$index] exception", e)
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
                    if (rawHtml.isNotBlank()) {
                        val title = extractTitle(rawHtml) ?: "第${index + 1}章"
                        result.add(title to entry.name)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    /**
     * 按需加载单个章节的HTML（含图片Base64内嵌），带缓存
     */
    override fun getChapterHtml(chapterIndex: Int): String {
        htmlCache[chapterIndex]?.let { return it }
        if (chapterIndex !in chapterPaths.indices) return ""

        val path = chapterPaths[chapterIndex]
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(File(epubFilePath))
            val zipEntry = findEntry(zipFile, path) ?: return ""
            val rawHtml = zipFile.getInputStream(zipEntry).bufferedReader().readText()
            val processedHtml = processHtml(zipFile, rawHtml)
            htmlCache[chapterIndex] = processedHtml
            return processedHtml
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        } finally {
            try { zipFile?.close() } catch (_: Exception) {}
        }
    }

    /**
     * 按需加载单个章节的 Spanned，带缓存
     */
    override fun getChapterContent(chapterIndex: Int): CharSequence {
        contentCache[chapterIndex]?.let {
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex from cache, length=${it.length}")
            return it
        }
        if (chapterIndex !in chapterPaths.indices) {
            android.util.Log.w("EpubParser", "getChapterContent: idx=$chapterIndex out of range (chapterPaths.size=${chapterPaths.size})")
            return ""
        }

        val path = chapterPaths[chapterIndex]
        android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex path=$path")
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(File(epubFilePath))
            val zipEntry = findEntry(zipFile, path)
            if (zipEntry == null) {
                android.util.Log.e("EpubParser", "getChapterContent: findEntry failed for path=$path")
                return ""
            }
            val rawHtml = zipFile.getInputStream(zipEntry).bufferedReader().readText()
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex rawHtml.length=${rawHtml.length}")
            if (chapterIndex == 0) android.util.Log.d("EpubParser", "cover HTML: $rawHtml")
            val spanned = htmlToSpanned(rawHtml, zipFile)
            // 如果 Spanned 为空（纯图片章节图片加载失败），返回占位文本
            val result: CharSequence = if (spanned.isBlank()) android.text.SpannableString(" ") else spanned
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex result.length=${result.length} isBlank=${spanned.isBlank()}")
            contentCache[chapterIndex] = result
            return result
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "getChapterContent: exception for idx=$chapterIndex", e)
            e.printStackTrace()
            return ""
        } finally {
            try { zipFile?.close() } catch (_: Exception) {}
        }
    }

    override fun getChapterCount(): Int = chapterPaths.size

    /**
     * 处理HTML，将图片转为Base64内嵌，使WebView能直接显示
     */
    private fun processHtml(zipFile: ZipFile, html: String): String {
        var result = html

        val imgTagRegex = """<img\s[^>]*?>""".toRegex(RegexOption.IGNORE_CASE)
        val srcRegex = """src\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)

        result = imgTagRegex.replace(result) { tagMatch ->
            val tag = tagMatch.value
            val srcMatch = srcRegex.find(tag) ?: return@replace tag
            val imgPath = srcMatch.groupValues[1]

            if (imgPath.startsWith("data:")) return@replace tag

            val fullPath = resolveImagePath(imgPath)
            try {
                val entry = findEntry(zipFile, fullPath)
                if (entry != null) {
                    val bytes = zipFile.getInputStream(entry).readBytes()
                    if (bytes.isEmpty()) return@replace tag

                    val mimeType = detectMimeType(imgPath, bytes)
                    if (mimeType == "image/svg+xml") return@replace tag

                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    tag.replace(srcRegex, """src="data:$mimeType;base64,$base64"""")
                } else {
                    tag
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tag
            }
        }

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

    private fun resolveImagePath(imgPath: String): String {
        if (imgPath.startsWith("/")) return imgPath.substring(1)
        val rawPath = if (basePath.isNotEmpty()) "$basePath/$imgPath" else imgPath
        return normalizePath(rawPath)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                ".." -> { if (result.isNotEmpty()) result.removeAt(result.size - 1) }
                "." -> { /* 跳过 */ }
                "" -> { if (result.isEmpty()) result.add(part) }
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun findEntry(zipFile: ZipFile, path: String): java.util.zip.ZipEntry? {
        zipFile.getEntry(path)?.let { return it }

        val decoded = try { java.net.URLDecoder.decode(path, "UTF-8") } catch (e: Exception) { path }
        if (decoded != path) {
            zipFile.getEntry(decoded)?.let { return it }
        }

        val withoutDotSlash = path.removePrefix("./")
        if (withoutDotSlash != path) {
            zipFile.getEntry(withoutDotSlash)?.let { return it }
        }

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

    private fun detectMimeType(path: String, bytes: ByteArray): String {
        val extMime = when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".gif", true) -> "image/gif"
            path.endsWith(".svg", true) -> "image/svg+xml"
            path.endsWith(".webp", true) -> "image/webp"
            else -> null
        }
        if (extMime != null) return extMime

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

    private fun htmlToSpanned(html: String, zipFile: ZipFile): Spanned {
        val bodyContent = extractBody(html) ?: html

        // 检测是否是纯图片章节（封面）：有 SVG/image 引用但没有 <img> 标签
        val imgRefRegex = Regex("""(?:xlink:)?href\s*=\s*["']([^"']+\.(?:jpg|jpeg|png|webp|gif))["']""", RegexOption.IGNORE_CASE)
        val existingImgRegex = Regex("""<img\s[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val svgImageRefs = imgRefRegex.findAll(bodyContent).map { it.groupValues[1] }.distinct().toList()
        val existingImgSrcs = existingImgRegex.findAll(bodyContent).map { it.groupValues[1] }.toSet()

        // 如果有 SVG 图片引用且没有 <img> 标签，用 <img> 完全替换原始内容
        val preprocessed = if (svgImageRefs.isNotEmpty() && existingImgSrcs.isEmpty()) {
            svgImageRefs.joinToString("") { """<img src="$it"/>""" }
        } else {
            bodyContent
        }

        val cleaned = preprocessed
            .replace(Regex("""<svg[^>]*>.*?</svg>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // 清理空容器（SVG 删除后留下的空 div 等）
            .replace(Regex("""<div[^>]*>\s*</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<span[^>]*>\s*</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""\n{3,}"""), "\n\n")

        // 图片前后插入换行，使其独占一行（块级效果）
        val withImageBreaks = cleaned.replace(
            Regex("""(<img[^>]*/?>)""", RegexOption.IGNORE_CASE), "\n$1\n"
        )

        val imageGetter = EpubImageGetter(zipFile)
        return Html.fromHtml(withImageBreaks, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
    }

    private inner class EpubImageGetter(
        private val zipFile: ZipFile
    ) : Html.ImageGetter {
        override fun getDrawable(source: String): Drawable? {
            return try {
                android.util.Log.d("EpubParser", "getDrawable: source=$source")
                val entryPath = resolveImagePath(source) ?: run {
                    android.util.Log.w("EpubParser", "getDrawable: resolveImagePath returned null for $source")
                    return null
                }
                android.util.Log.d("EpubParser", "getDrawable: entryPath=$entryPath")
                val entry = findEntry(zipFile, entryPath) ?: run {
                    android.util.Log.w("EpubParser", "getDrawable: findEntry returned null for $entryPath")
                    return null
                }
                android.util.Log.d("EpubParser", "getDrawable: entry=${entry.name} size=${entry.size}")

                val bytes = zipFile.getInputStream(entry).readBytes()
                android.util.Log.d("EpubParser", "getDrawable: readBytes=${bytes.size}")

                // 先只读尺寸，不分配内存
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                android.util.Log.d("EpubParser", "getDrawable: originalSize=${opts.outWidth}x${opts.outHeight}")

                // 计算 inSampleSize，目标尺寸 800×1200
                val targetWidth = 800
                val targetHeight = 1200
                val sampleW = opts.outWidth / targetWidth
                val sampleH = opts.outHeight / targetHeight
                opts.inSampleSize = maxOf(sampleW, sampleH).coerceAtLeast(1)
                opts.inJustDecodeBounds = false
                android.util.Log.d("EpubParser", "getDrawable: inSampleSize=${opts.inSampleSize}")

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bitmap == null) {
                    android.util.Log.e("EpubParser", "getDrawable: decodeByteArray returned null!")
                    return null
                }
                android.util.Log.d("EpubParser", "getDrawable: decoded ${bitmap.width}x${bitmap.height}")

                // 缩放至页面可用宽度（屏幕宽度减去左右边距），高度等比
                val dm = android.content.res.Resources.getSystem().displayMetrics
                val marginPx = (44 * dm.density).toInt()  // 默认边距 44dp
                val pageW = dm.widthPixels - marginPx * 2
                val drawW: Int
                val drawH: Int
                if (bitmap.width > pageW) {
                    val ratio = pageW.toFloat() / bitmap.width
                    drawW = pageW
                    drawH = (bitmap.height * ratio).toInt()
                } else {
                    drawW = bitmap.width
                    drawH = bitmap.height
                }

                val drawable = BitmapDrawable(null, bitmap)
                drawable.setBounds(0, 0, drawW, drawH)
                drawable
            } catch (e: Throwable) {
                android.util.Log.e("EpubParser", "getDrawable: exception", e)
                null
            }
        }
    }

    private fun extractBody(html: String): String? {
        val bodyRegex = Regex(
            """<body[^>]*>(.*?)</body>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return bodyRegex.find(html)?.groupValues?.get(1)
    }

    private fun extractTitle(html: String): String? {
        val titleRegex = """<title[^>]*>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE)
        val titleTag = titleRegex.find(html)?.groupValues?.get(1)?.trim()
        if (!titleTag.isNullOrBlank() && !titleTag.matches(Regex("^\\s*(第?[\\d一二三四五六七八九十百千零]+[章节回卷部篇]|Chapter|CH\\s*\\d|\\d+[\\.、])\\s*$"))) {
            return titleTag
        }

        for (level in 1..6) {
            val headingRegex = """<h$level[^>]*>([^<]+)</h$level>""".toRegex(RegexOption.IGNORE_CASE)
            val heading = headingRegex.find(html)?.groupValues?.get(1)?.trim()
            if (!heading.isNullOrBlank()) {
                return heading
            }
        }

        if (!titleTag.isNullOrBlank()) return titleTag

        return null
    }

    private fun extractCover(zipFile: ZipFile, opfContent: String): String? {
        val ctx = context ?: return null

        try {
            val coverIdRegex = """<meta\s+name="cover"\s+content="([^"]+)"""".toRegex()
            val coverId = coverIdRegex.find(opfContent)?.groupValues?.get(1)

            val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""

            var coverHref: String? = null

            if (coverId != null) {
                val itemRegex = """<item\s+id="${Regex.escape(coverId)}"\s+href="([^"]+)"""".toRegex()
                coverHref = itemRegex.find(manifestContent)?.groupValues?.get(1)
            }

            if (coverHref == null) {
                """<item\s+id="([^"]*cover[^"]*)"\s+href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                    .find(manifestContent)?.let { coverHref = it.groupValues[2] }
            }
            if (coverHref == null) {
                """<item\s+[^>]*href="([^"]*cover[^"]*\.(jpg|jpeg|png|webp))"""".toRegex(RegexOption.IGNORE_CASE)
                    .find(manifestContent)?.let { coverHref = it.groupValues[1] }
            }

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

            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href
            val entry = findEntry(zipFile, fullPath) ?: return null
            val bytes = zipFile.getInputStream(entry).readBytes()
            if (bytes.isEmpty()) return null

            val coversDir = File(ctx.filesDir, "covers")
            coversDir.mkdirs()
            val coverFile = File(coversDir, "${zipFile.name.hashCode()}.jpg")
            coverFile.writeBytes(bytes)

            return coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun extractCoverPath(filePath: String): String? {
        val ctx = context ?: return null
        val file = File(filePath)
        if (!file.exists()) return null

        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(file)

            val containerEntry = zipFile.getEntry("META-INF/container.xml") ?: return null
            val containerContent = zipFile.getInputStream(containerEntry).bufferedReader().readText()
            val opfPath = extractOpfPath(containerContent)

            val opfEntry = zipFile.getEntry(opfPath) ?: return null
            val opfContent = zipFile.getInputStream(opfEntry).bufferedReader().readText()
            basePath = opfPath.substringBeforeLast("/")

            return extractCover(zipFile, opfContent)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { zipFile?.close() } catch (_: Exception) {}
        }
    }
}
