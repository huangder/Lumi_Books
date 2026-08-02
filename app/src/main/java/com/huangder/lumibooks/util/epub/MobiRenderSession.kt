package com.huangder.lumibooks.util.epub

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.huangder.lumibooks.util.parser.MobiParser
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Book-layout render session for classic MOBI7.
 *
 * Serves per-chapter XHTML documents through [WebViewAssetLoader] at stable
 * synthetic hrefs (`chapter-{index:03}.html`), converts the rawml fragment
 * (`<sent>`/`<img recindex>`/`<a filepos>`) into standard XHTML, injects the
 * shared reader CSS + pagination script, and maps `filepos` links back to
 * chapters. The session shares the seekable lease opened by [MobiParser].
 */
class MobiRenderSession internal constructor(
    private val parser: MobiParser,
    private val mobiFile: MobiFile
) : BookRenderSession {

    val sessionToken: String = UUID.randomUUID().toString()

    override val chapterCount: Int
        get() = parser.sessionChapterRanges.size

    private val documentCache = mutableMapOf<Int, Document>()
    private val readerFontFiles = mutableMapOf<String, File>()
    private val readerFontKeysByPath = mutableMapOf<String, String>()
    private val documentLock = Any()

    override val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/mobi/$sessionToken/chapters/", WebViewAssetLoader.PathHandler { requestedPath ->
                try {
                    val chapterIndex = chapterIndexFromName(requestedPath) ?: return@PathHandler null
                    val bytes = chapterHtmlBytes(chapterIndex) ?: return@PathHandler null
                    runCatching {
                        android.util.Log.i(
                            "MobiSessionDebug",
                            "chapter requestedPath=$requestedPath index=$chapterIndex bytes=${bytes.size}"
                        )
                    }
                    WebResourceResponse("application/xhtml+xml", "UTF-8", ByteArrayInputStream(bytes)).apply {
                        responseHeaders = RESPONSE_HEADERS
                    }
                } catch (error: Throwable) {
                    runCatching { android.util.Log.e("MobiSessionDebug", "chapter handler failed: $requestedPath", error) }
                    null
                }
            })
            .addPathHandler("/mobi/$sessionToken/images/", WebViewAssetLoader.PathHandler { requestedPath ->
                try {
                    val recindex = requestedPath.toIntOrNull() ?: return@PathHandler null
                    val bytes = parser.sessionFile?.let { file ->
                        val record = parser.imageRecordIndex(recindex)
                        record?.let { file.imageRecordBytes(it) }
                    } ?: return@PathHandler null
                    if (bytes.isEmpty()) return@PathHandler null
                    runCatching {
                        android.util.Log.i("MobiSessionDebug", "image requestedPath=$requestedPath bytes=${bytes.size}")
                    }
                    WebResourceResponse(detectImageMime(bytes), null, ByteArrayInputStream(bytes)).apply {
                        responseHeaders = RESPONSE_HEADERS
                    }
                } catch (error: Throwable) {
                    runCatching { android.util.Log.e("MobiSessionDebug", "image handler failed: $requestedPath", error) }
                    null
                }
            })
            .addPathHandler("/mobi/$sessionToken/reader-font/", WebViewAssetLoader.PathHandler { requestedPath ->
                openReaderFont(requestedPath)
            })
            .build()
    }

    override fun chapterUrl(chapterIndex: Int, fragment: String?): String {
        if (chapterIndex !in 0 until chapterCount) error("Invalid MOBI chapter index: $chapterIndex")
        return "https://$ASSET_DOMAIN/mobi/$sessionToken/chapters/${chapterHref(chapterIndex)}" +
            fragment?.takeIf(String::isNotBlank)?.let { "#$it" }.orEmpty()
    }

    override fun chapterHref(chapterIndex: Int): String = parser.chapterHrefName(chapterIndex)

    override fun chapterIndexForUrl(url: String): Int? {
        val parsed = parseUrl(url) ?: return null
        if (parsed.scheme != "https" || parsed.host != ASSET_DOMAIN) return null
        val prefix = "/mobi/$sessionToken/chapters/"
        val name = parsed.path?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        return chapterIndexFromName(name)
    }

    override fun imageUrl(sourceChapterIndex: Int, source: String): String? {
        if (source.startsWith("data:", ignoreCase = true)) return source
        if (source.startsWith("recindex:", ignoreCase = true)) {
            val recindex = source.substringAfter(':').toIntOrNull() ?: return null
            return imageUrlForRecindex(recindex)
        }
        val parsed = parseUrl(source) ?: return null
        if (parsed.scheme == "https" && parsed.host == ASSET_DOMAIN) {
            val recindex = imageRecindexFromUrl(source) ?: return null
            if (parser.imageRecordIndex(recindex) == null) return null
            return source
        }
        return null
    }

    override fun readImageUrl(url: String): EpubResource? {
        if (url.startsWith("data:", ignoreCase = true)) {
            return readDataUri(url)
        }
        val recindex = imageRecindexFromUrl(url) ?: return null
        val record = parser.imageRecordIndex(recindex) ?: return null
        val bytes = mobiFile.imageRecordBytes(record) ?: return null
        if (bytes.isEmpty()) return null
        return EpubResource("image-$recindex", detectImageMime(bytes), bytes)
    }

    override fun resolveInternalLink(sourceChapterIndex: Int, href: String): Pair<Int, String?>? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        val parsed = parseUrl(trimmed)
        if (parsed != null && parsed.host == ASSET_DOMAIN) {
            val index = chapterIndexForUrl(trimmed) ?: return null
            return index to parsed.fragment
        }
        if (parsed != null && parsed.scheme != null && parsed.scheme != "filepos") return null
        if (trimmed.startsWith("filepos:", ignoreCase = true)) {
            val filepos = trimmed.substringAfter(':').toLongOrNull() ?: return null
            return parser.sessionFileposToChapter(filepos)?.let { it to null }
        }
        val nameMatch = Regex("""^chapter-(\d{3})\.html(?:#(.*))?$""").find(trimmed)
        if (nameMatch != null) {
            val index = nameMatch.groupValues[1].toIntOrNull() ?: return null
            if (index !in 0 until chapterCount) return null
            return index to nameMatch.groupValues[2].takeIf(String::isNotBlank)
        }
        if (trimmed.startsWith("#")) {
            return sourceChapterIndex to trimmed.substring(1).takeIf(String::isNotBlank)
        }
        return null
    }

    @Synchronized
    override fun readerFontUrl(filePath: String?): String? {
        val file = filePath?.takeIf(String::isNotBlank)?.let(::File) ?: return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!canonical.isFile || canonical.length() <= 0L || canonical.length() > MAX_READER_FONT_BYTES) return null
        val extension = canonical.extension.lowercase().takeIf { it in READER_FONT_EXTENSIONS } ?: "ttf"
        val canonicalPath = canonical.path
        val key = readerFontKeysByPath.getOrPut(canonicalPath) {
            UUID.randomUUID().toString() + "." + extension
        }
        readerFontFiles[key] = canonical
        return "https://$ASSET_DOMAIN/mobi/$sessionToken/reader-font/$key"
    }

    @Synchronized
    private fun openReaderFont(requestedPath: String): WebResourceResponse? {
        val file = readerFontFiles[requestedPath] ?: return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (canonical != file || !canonical.isFile || canonical.length() <= 0L ||
            canonical.length() > MAX_READER_FONT_BYTES
        ) {
            return null
        }
        val mimeType = when (canonical.extension.lowercase()) {
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "font/ttf"
        }
        return WebResourceResponse(mimeType, null, FileInputStream(canonical)).apply {
            responseHeaders = mapOf(
                "Cache-Control" to "private, max-age=3600",
                "X-Content-Type-Options" to "nosniff"
            )
        }
    }

    override fun renditionLayout(chapterIndex: Int): EpubRenditionLayout = EpubRenditionLayout.REFLOWABLE

    override fun pageProgressionDirection(chapterIndex: Int): EpubPageProgressionDirection =
        EpubPageProgressionDirection.LTR

    override fun searchText(chapterIndex: Int): String = synchronized(documentLock) {
        EpubDocumentTransformer.extractSearchText(chapterDocument(chapterIndex))
    }

    @Synchronized
    override fun close() {
        documentCache.clear()
        readerFontFiles.clear()
        readerFontKeysByPath.clear()
        mobiFile.close()
        parser.onSessionClosed(this)
    }

    private fun chapterFragment(chapterIndex: Int): String = synchronized(documentLock) {
        val range = parser.sessionChapterRanges.getOrNull(chapterIndex) ?: return ""
        MobiRawml.chapterFragment(
            rawml = parser.sessionRawml,
            range = range,
            chapterIndex = chapterIndex,
            charset = parser.sessionCharset,
            resolveImage = { recindex -> imageUrlForRecindex(recindex) },
            resolveLink = { filepos -> linkUrlForFilepos(filepos) }
        )
    }

    private fun chapterDocument(chapterIndex: Int): Document = synchronized(documentLock) {
        documentCache[chapterIndex]?.let { return it }
        try {
            val fragment = chapterFragment(chapterIndex)
            val fullHtml =
                "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<head><meta charset=\"utf-8\"/></head><body>$fragment</body></html>"
            val document = EpubDocumentTransformer.parseAndSanitize(
                fullHtml.toByteArray(Charsets.UTF_8),
                parser.chapterHrefName(chapterIndex),
                useXmlParser = false
            )
            EpubDocumentTransformer.transform(document, EpubRenditionLayout.REFLOWABLE)
            documentCache[chapterIndex] = document
            runCatching {
                android.util.Log.i("MobiSessionDebug", "chapter $chapterIndex document built, html=${document.outerHtml().length}")
            }
            document
        } catch (error: Throwable) {
            runCatching { android.util.Log.e("MobiSessionDebug", "chapter $chapterIndex document build failed", error) }
            throw error
        }
    }

    private fun chapterHtmlBytes(chapterIndex: Int): ByteArray? {
        if (chapterIndex !in 0 until chapterCount) return null
        return chapterDocument(chapterIndex).outerHtml().toByteArray(Charsets.UTF_8)
    }

    private fun imageUrlForRecindex(recindex: Int): String? {
        if (parser.imageRecordIndex(recindex) == null) return null
        return "https://$ASSET_DOMAIN/mobi/$sessionToken/images/$recindex"
    }

    private fun linkUrlForFilepos(filepos: Long): String? {
        val chapterIndex = parser.sessionFileposToChapter(filepos) ?: return null
        return chapterUrl(chapterIndex)
    }

    private fun imageRecindexFromUrl(url: String): Int? {
        val parsed = parseUrl(url) ?: return null
        if (parsed.scheme != "https" || parsed.host != ASSET_DOMAIN) return null
        val prefix = "/mobi/$sessionToken/images/"
        val name = parsed.path?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        return name.toIntOrNull()
    }

    private fun chapterIndexFromName(name: String): Int? {
        val match = Regex("""^chapter-(\d{3})\.html$""").find(name) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        return index.takeIf { it in 0 until chapterCount }
    }

    private fun readDataUri(url: String): EpubResource? {
        val comma = url.indexOf(',')
        if (comma <= 5) return null
        val metadata = url.substring(5, comma)
        val mediaType = metadata.substringBefore(';').ifBlank { "image/png" }
        if (!mediaType.startsWith("image/", ignoreCase = true)) return null
        val payload = url.substring(comma + 1)
        val bytes = runCatching {
            if (metadata.split(';').any { it.equals("base64", ignoreCase = true) }) {
                android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_RESOURCE_BYTES) return null
        return EpubResource("embedded-image", mediaType, bytes)
    }

    private fun detectImageMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
        else -> "application/octet-stream"
    }

    private class ParsedUrl(
        val scheme: String?,
        val host: String?,
        val path: String?,
        val fragment: String?
    )

    private fun parseUrl(value: String): ParsedUrl? {
        if (value.isBlank()) return null
        val schemeEnd = value.indexOf("://")
        val scheme = if (schemeEnd > 0) value.substring(0, schemeEnd) else null
        val rest = if (schemeEnd > 0) value.substring(schemeEnd + 3) else value
        val hashIndex = rest.indexOf('#')
        val pathAndHost = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest
        val fragment = if (hashIndex >= 0) {
            rest.substring(hashIndex + 1).takeIf(String::isNotBlank)
        } else {
            null
        }
        if (pathAndHost.isEmpty()) return ParsedUrl(scheme, null, null, fragment)
        val slashIndex = pathAndHost.indexOf('/')
        return if (slashIndex >= 0) {
            ParsedUrl(scheme, pathAndHost.substring(0, slashIndex), pathAndHost.substring(slashIndex), fragment)
        } else {
            ParsedUrl(scheme, pathAndHost, null, fragment)
        }
    }

    private companion object {
        const val ASSET_DOMAIN = BookRenderSession.ASSET_DOMAIN
        private const val MAX_RESOURCE_BYTES = 64 * 1024 * 1024
        private const val MAX_READER_FONT_BYTES = 64L * 1024L * 1024L
        private val READER_FONT_EXTENSIONS = setOf("ttf", "otf", "woff", "woff2")
        private val RESPONSE_HEADERS = mapOf(
            "Content-Security-Policy" to
                "default-src 'none'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; " +
                "img-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "script-src 'unsafe-inline'; connect-src 'none'; frame-src 'none'; object-src 'none'",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer"
        )
    }
}
