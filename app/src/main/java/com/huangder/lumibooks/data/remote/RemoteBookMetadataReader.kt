package com.huangder.lumibooks.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huangder.lumibooks.util.epub.EpubCoverCandidates
import com.huangder.lumibooks.util.epub.EpubPackageReader
import com.huangder.lumibooks.util.parser.CbzArchiveOpener
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Metadata read from a downloaded book file, used to enrich WebDAV listings. */
data class LocalBookMetadata(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val published: String? = null,
    val categories: List<String> = emptyList(),
    val coverPath: String? = null
)

/**
 * Reads EPUB OPF / CBZ ComicInfo metadata and writes a small cover preview next to the file.
 * Nothing is written to the library cover directory; previews live in the cache.
 */
object RemoteBookMetadataReader {
    private const val MAX_OPF_BYTES = 2 * 1024 * 1024
    private const val COVER_WIDTH_PX = 480

    fun read(context: Context, file: File, extension: String, coverFile: File): LocalBookMetadata? =
        when (extension.lowercase()) {
            "epub" -> runCatching { readEpub(file, coverFile) }.getOrNull()
            "cbz" -> runCatching { readCbz(context, file, coverFile) }.getOrNull()
            else -> null
        }

    private fun readEpub(file: File, coverFile: File): LocalBookMetadata {
        val book = EpubPackageReader.read(file.absolutePath)
        val (opfXml, metadata) = ZipFile(file).use { zip ->
            val entry = zip.getEntry(book.opfPath) ?: error("Missing OPF")
            val bytes = zip.getInputStream(entry).use { input -> input.readNBytesCompat(MAX_OPF_BYTES) }
            bytes.toString(Charsets.UTF_8) to parseOpfMetadata(bytes)
        }
        val coverPath = runCatching {
            ZipFile(file).use { zip ->
                val candidates = EpubCoverCandidates.paths(book, opfXml) { path ->
                    zip.getEntry(path)?.let { entry ->
                        zip.getInputStream(entry).use { it.readNBytesCompat(MAX_OPF_BYTES).toString(Charsets.UTF_8) }
                    }
                }
                candidates.firstNotNullOfOrNull { path ->
                    val entry = zip.getEntry(path) ?: return@firstNotNullOfOrNull null
                    val bytes = zip.getInputStream(entry).use { it.readNBytesCompat(16 * 1024 * 1024) }
                    writeScaledCover(bytes, coverFile)
                }
            }
        }.getOrNull()
        return metadata.copy(
            title = metadata.title ?: book.title.takeIf(String::isNotBlank),
            authors = metadata.authors.ifEmpty { listOfNotNull(book.author.takeIf(String::isNotBlank)) },
            coverPath = coverPath
        )
    }

    private fun readCbz(context: Context, file: File, coverFile: File): LocalBookMetadata {
        val archive = CbzArchiveOpener.open(context, file.absolutePath)
        return try {
            val info = archive.comicInfo
            val firstPage = archive.index.pages.firstOrNull()
            val coverPath = firstPage?.let { page ->
                archive.openEntry(page.entryName)?.use { stream ->
                    writeScaledCover(stream.readNBytesCompat(32 * 1024 * 1024), coverFile)
                }
            }
            LocalBookMetadata(
                title = info?.resolveTitle("")?.takeIf(String::isNotBlank),
                authors = info?.resolveAuthor("")
                    ?.split(", ")
                    ?.filter(String::isNotBlank)
                    .orEmpty(),
                summary = info?.summary,
                language = info?.language,
                coverPath = coverPath
            )
        } finally {
            runCatching { archive.close() }
        }
    }

    private fun writeScaledCover(bytes: ByteArray, target: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_WIDTH_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val scaled = if (decoded.width > COVER_WIDTH_PX) {
            val height = (decoded.height.toFloat() / decoded.width * COVER_WIDTH_PX).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, COVER_WIDTH_PX, height, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, 88, output) }
        scaled.recycle()
        return target.absolutePath
    }

    internal fun parseOpfMetadata(opf: ByteArray): LocalBookMetadata {
        val head = opf.copyOfRange(0, minOf(opf.size, 4096)).toString(Charsets.ISO_8859_1)
        require(!head.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not allowed" }
        val document = DocumentBuilderFactory.newInstance().apply {
            // Some EPUB 2 files use the dc prefix without declaring it; match local names.
            isNamespaceAware = false
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newDocumentBuilder().parse(ByteArrayInputStream(opf))
        val metadata = document.getElementsByTagName("*").let { nodes ->
            (0 until nodes.length).map(nodes::item).firstOrNull { it.localNameValue() == "metadata" }
        } as? Element ?: return LocalBookMetadata()
        fun values(name: String): List<String> = metadata.descendants(name)
            .map { it.textContent.trim() }
            .filter(String::isNotBlank)
        val description = values("description").firstOrNull()?.let { raw ->
            if (raw.contains('<')) {
                Jsoup.parseBodyFragment(raw.replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")).wholeText().trim()
            } else {
                raw
            }
        }
        return LocalBookMetadata(
            title = values("title").firstOrNull(),
            authors = values("creator").distinct(),
            summary = description?.takeIf(String::isNotBlank),
            publisher = values("publisher").firstOrNull(),
            language = values("language").firstOrNull(),
            published = values("date").firstOrNull(),
            categories = values("subject").distinct()
        )
    }

    private fun Node.localNameValue(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.descendants(name: String): List<Node> {
        val nodes = getElementsByTagName("*")
        return (0 until nodes.length).map(nodes::item).filter { it.localNameValue() == name }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Entry too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
