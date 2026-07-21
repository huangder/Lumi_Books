package com.huangder.lumibooks.mineru

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class MineruEpubBuildResult(
    val chapterCount: Int,
    val markdownFile: File
)

@Singleton
class MineruEpubBuilder @Inject constructor() {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).build()

    fun build(
        remoteResult: MineruRemoteResult,
        outputFile: File,
        workDirectory: File,
        title: String,
        author: String
    ): MineruEpubBuildResult {
        val markdownFile = if (remoteResult.isZip) {
            val extractedDirectory = File(workDirectory, "extracted").apply {
                deleteRecursively()
                mkdirs()
            }
            extractSafely(remoteResult.resultFile, extractedDirectory)
            extractedDirectory.walkTopDown()
                .firstOrNull { it.isFile && it.name.equals("full.md", ignoreCase = true) }
                ?: extractedDirectory.walkTopDown()
                    .firstOrNull { it.isFile && it.extension.equals("md", ignoreCase = true) }
                ?: throw MineruApiException(
                    MineruApiException.Kind.INVALID_RESULT,
                    "MinerU result does not contain Markdown"
                )
        } else {
            remoteResult.resultFile
        }

        val markdown = markdownFile.readText(Charsets.UTF_8)
        if (markdown.isBlank()) {
            throw MineruApiException(MineruApiException.Kind.INVALID_RESULT, "MinerU returned empty Markdown")
        }
        val chapters = splitIntoChapters(markdown)
        val assets = collectAssets(markdownFile.parentFile ?: workDirectory)
        outputFile.parentFile?.mkdirs()
        outputFile.delete()

        ZipOutputStream(FileOutputStream(outputFile).buffered()).use { zip ->
            writeStoredMimetype(zip)
            writeTextEntry(zip, "META-INF/container.xml", containerXml())

            val chapterFiles = chapters.mapIndexed { index, chapter ->
                val name = "chapter-${index + 1}.xhtml"
                writeTextEntry(zip, "OEBPS/$name", chapterXhtml(title, chapter.markdown))
                name
            }
            assets.forEach { asset ->
                val entryName = "OEBPS/${asset.relativePath}"
                writeFileEntry(zip, entryName, asset.file)
            }
            writeTextEntry(zip, "OEBPS/nav.xhtml", navXhtml(title, chapters, chapterFiles))
            writeTextEntry(zip, "OEBPS/toc.ncx", tocNcx(title, chapters, chapterFiles))
            writeTextEntry(
                zip,
                "OEBPS/content.opf",
                contentOpf(title, author, chapters, chapterFiles, assets)
            )
        }

        return MineruEpubBuildResult(chapterCount = chapters.size, markdownFile = markdownFile)
    }

    private fun splitIntoChapters(markdown: String): List<MarkdownChapter> {
        val result = mutableListOf<MarkdownChapter>()
        var title = "正文"
        val content = StringBuilder()
        var inFence = false

        fun flush() {
            val body = content.toString().trim()
            if (body.isNotEmpty()) result += MarkdownChapter(title, body)
            content.clear()
        }

        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) inFence = !inFence
            val heading = if (!inFence && result.size < MAX_CHAPTERS - 1) {
                HEADING_REGEX.matchEntire(line)
            } else {
                null
            }
            if (heading != null && content.isNotBlank()) {
                flush()
                title = plainHeading(heading.groupValues[2])
            } else if (heading != null) {
                title = plainHeading(heading.groupValues[2])
            }
            content.appendLine(line)
        }
        flush()
        return result.ifEmpty { listOf(MarkdownChapter("正文", markdown)) }
    }

    private fun collectAssets(root: File): List<EpubAsset> {
        val rootPath = root.canonicalFile.toPath()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            .mapNotNull { file ->
                val canonical = file.canonicalFile
                if (!canonical.toPath().startsWith(rootPath)) return@mapNotNull null
                val relative = rootPath.relativize(canonical.toPath()).toString().replace(File.separatorChar, '/')
                if (relative.isBlank() || relative.startsWith("../")) null else EpubAsset(file, relative)
            }
            .take(MAX_ASSET_COUNT)
            .toList()
    }

    private fun extractSafely(zipFile: File, destination: File) {
        val root = destination.canonicalFile
        var totalBytes = 0L
        var entryCount = 0
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) invalidZip("MinerU result has too many files")

                val output = File(root, entry.name).canonicalFile
                if (output != root && !output.toPath().startsWith(root.toPath())) {
                    invalidZip("Unsafe path in MinerU result")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                    continue
                }

                output.parentFile?.mkdirs()
                zip.getInputStream(entry).buffered().use { input ->
                    FileOutputStream(output).buffered().use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                invalidZip("MinerU result is too large after extraction")
                            }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun chapterXhtml(bookTitle: String, markdown: String): String {
        val body = renderer.render(parser.parse(markdown))
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml">
            |<head>
            |  <meta charset="UTF-8" />
            |  <title>${xml(bookTitle)}</title>
            |  <style>
            |    body { line-height: 1.7; word-wrap: break-word; }
            |    img { max-width: 100%; height: auto; }
            |    table { border-collapse: collapse; max-width: 100%; }
            |    th, td { border: 1px solid #999; padding: 0.35em; }
            |    pre { white-space: pre-wrap; word-break: break-word; }
            |  </style>
            |</head>
            |<body>
            |$body
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun contentOpf(
        title: String,
        author: String,
        chapters: List<MarkdownChapter>,
        chapterFiles: List<String>,
        assets: List<EpubAsset>
    ): String {
        val chapterItems = chapterFiles.mapIndexed { index, file ->
            "<item id=\"chapter${index + 1}\" href=\"${xml(file)}\" media-type=\"application/xhtml+xml\"/>"
        }.joinToString("\n")
        val assetItems = assets.mapIndexed { index, asset ->
            "<item id=\"asset${index + 1}\" href=\"${xml(asset.relativePath)}\" media-type=\"${mediaType(asset.file)}\"/>"
        }.joinToString("\n")
        val spine = chapters.indices.joinToString("\n") { index ->
            "<itemref idref=\"chapter${index + 1}\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
            |<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            |  <dc:identifier id="book-id">urn:uuid:${UUID.randomUUID()}</dc:identifier>
            |  <dc:title>${xml(title)}</dc:title>
            |  <dc:creator>${xml(author)}</dc:creator>
            |  <dc:language>zh-CN</dc:language>
            |</metadata>
            |<manifest>
            |  <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            |  <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            |  $chapterItems
            |  $assetItems
            |</manifest>
            |<spine toc="ncx">
            |  $spine
            |</spine>
            |</package>
        """.trimMargin()
    }

    private fun navXhtml(
        title: String,
        chapters: List<MarkdownChapter>,
        files: List<String>
    ): String {
        val items = chapters.indices.joinToString("\n") { index ->
            "<li><a href=\"${xml(files[index])}\">${xml(chapters[index].title)}</a></li>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            |<head><title>${xml(title)}</title></head>
            |<body><nav epub:type="toc"><ol>$items</ol></nav></body>
            |</html>
        """.trimMargin()
    }

    private fun tocNcx(
        title: String,
        chapters: List<MarkdownChapter>,
        files: List<String>
    ): String {
        val points = chapters.indices.joinToString("\n") { index ->
            """<navPoint id="nav${index + 1}" playOrder="${index + 1}">
                |<navLabel><text>${xml(chapters[index].title)}</text></navLabel>
                |<content src="${xml(files[index])}"/>
                |</navPoint>""".trimMargin()
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
            |<head><meta name="dtb:uid" content="urn:uuid:${UUID.randomUUID()}"/></head>
            |<docTitle><text>${xml(title)}</text></docTitle>
            |<navMap>$points</navMap>
            |</ncx>
        """.trimMargin()
    }

    private fun containerXml(): String = """<?xml version="1.0" encoding="UTF-8"?>
        |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
        |<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        |</container>
    """.trimMargin()

    private fun writeStoredMimetype(zip: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }
        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeTextEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun plainHeading(value: String): String {
        return value.replace(Regex("[*_`~\\[\\]#]"), "").trim().take(120).ifBlank { "正文" }
    }

    private fun mediaType(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun invalidZip(message: String): Nothing {
        throw MineruApiException(MineruApiException.Kind.INVALID_RESULT, message)
    }

    private data class MarkdownChapter(val title: String, val markdown: String)
    private data class EpubAsset(val file: File, val relativePath: String)

    private companion object {
        val HEADING_REGEX = Regex("^(#{1,2})\\s+(.+?)\\s*$")
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
        const val MAX_CHAPTERS = 200
        const val MAX_ASSET_COUNT = 5_000
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_UNCOMPRESSED_BYTES = 768L * 1_024L * 1_024L
    }
}
