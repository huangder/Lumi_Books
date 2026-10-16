package com.huangder.lumibooks.util.markdown

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One spine item regardless of heading count. The outline never slices the body. */
object MarkdownEpubView {
    const val HREF = "OEBPS/document.xhtml"
    const val VERSION = 4
    fun build(document: MarkdownDocument, title: String, author: String, assets: Map<String, File>, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value; time = 0
            })
            zip.write(mime); zip.closeEntry()
            fun text(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            text("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            text(HREF, """<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><title>${xml(title)}</title><style>${MarkdownRenderer.STYLE}</style></head><body data-lumi-markdown="true">${document.html}</body></html>""")
            val items = assets.entries.sortedBy { it.key }.mapIndexed { i, (path, file) ->
                zip.putNextEntry(ZipEntry("OEBPS/$path").apply { time = 0 })
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                """<item id="asset$i" href="${xml(path)}" media-type="${mimeType(file.extension)}"/>"""
            }.joinToString("")
            // The parser supplies the Markdown outline directly; no EPUB navigation slicing.
            text("OEBPS/content.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">markdown</dc:identifier><dc:title>${xml(title)}</dc:title><dc:creator>${xml(author)}</dc:creator><dc:language>und</dc:language></metadata><manifest><item id="body" href="document.xhtml" media-type="application/xhtml+xml"/>$items</manifest><spine><itemref idref="body"/></spine></package>""")
        }
    }
    fun mimeType(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"; else -> "image/png"
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
