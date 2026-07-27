package com.huangder.lumibooks.util.epub

import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubRenderSession private constructor(
    override val epubPackage: EpubPackage,
    private val zipFile: ZipFile
) : EpubResourceProvider {
    val sessionToken: String = UUID.randomUUID().toString()
    private val entriesByLowercase: Map<String, ZipEntry> =
        zipFile.entries().asSequence().associateBy { it.name.lowercase() }
    private val spineByPath = epubPackage.spine.mapIndexed { index, item ->
        item.manifestItem.fullPath to index
    }.toMap()
    private val readerFontFiles = mutableMapOf<String, File>()
    private val readerFontKeysByPath = mutableMapOf<String, String>()

    val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(ASSET_DOMAIN)
        .addPathHandler("/epub/$sessionToken/", WebViewAssetLoader.PathHandler { requestedPath ->
            val resource = read(requestedPath) ?: return@PathHandler null
            val isTransformedDocument = spineByPath.containsKey(resource.path) &&
                (resource.mediaType.contains("html") || resource.mediaType.contains("xhtml"))
            WebResourceResponse(
                resource.mediaType,
                if (isTransformedDocument) "UTF-8" else null,
                ByteArrayInputStream(resource.bytes)
            ).apply {
                responseHeaders = mapOf(
                    "Content-Security-Policy" to CONTENT_SECURITY_POLICY,
                    "X-Content-Type-Options" to "nosniff",
                    "Referrer-Policy" to "no-referrer"
                )
            }
        })
        .addPathHandler("/reader-font/$sessionToken/", WebViewAssetLoader.PathHandler { requestedPath ->
            openReaderFont(requestedPath)
        })
        .build()

    @Synchronized
    fun readerFontUrl(filePath: String?): String? {
        val file = filePath?.takeIf(String::isNotBlank)?.let(::File) ?: return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!canonical.isFile || canonical.length() <= 0L || canonical.length() > MAX_READER_FONT_BYTES) return null
        val extension = canonical.extension.lowercase().takeIf { it in READER_FONT_EXTENSIONS } ?: "ttf"
        val canonicalPath = canonical.path
        val key = readerFontKeysByPath.getOrPut(canonicalPath) { UUID.randomUUID().toString() + "." + extension }
        readerFontFiles[key] = canonical
        return "https://$ASSET_DOMAIN/reader-font/$sessionToken/$key"
    }

    @Synchronized
    private fun openReaderFont(requestedPath: String): WebResourceResponse? {
        val file = readerFontFiles[requestedPath] ?: return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (canonical != file || !canonical.isFile || canonical.length() <= 0L || canonical.length() > MAX_READER_FONT_BYTES) {
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

    fun chapterUrl(chapterIndex: Int, fragment: String? = null): String {
        val path = epubPackage.spine.getOrNull(chapterIndex)?.manifestItem?.fullPath
            ?: error("Invalid EPUB chapter index: $chapterIndex")
        val encodedPath = path.split('/').joinToString("/") { Uri.encode(it) }
        return "https://$ASSET_DOMAIN/epub/$sessionToken/$encodedPath" +
            fragment?.takeIf(String::isNotBlank)?.let { "#${Uri.encode(it)}" }.orEmpty()
    }

    fun chapterIndexForUrl(url: String): Int? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.host != ASSET_DOMAIN) return null
        val prefix = "/epub/$sessionToken/"
        val encodedPath = uri.encodedPath?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        val path = EpubPathResolver.normalize(Uri.decode(encodedPath)) ?: return null
        return spineByPath[path]
    }

    fun resolveInternalLink(sourceChapterIndex: Int, href: String): Pair<Int, String?>? {
        val uri = runCatching { Uri.parse(href) }.getOrNull()
        if (uri != null && uri.host == ASSET_DOMAIN) {
            val index = chapterIndexForUrl(href) ?: return null
            return index to uri.fragment
        }
        if (uri != null && !uri.scheme.isNullOrBlank()) return null
        val sourcePath = epubPackage.spine.getOrNull(sourceChapterIndex)?.manifestItem?.fullPath ?: return null
        val targetPath = EpubPathResolver.resolve(sourcePath, href) ?: return null
        return spineByPath[targetPath]?.let { it to EpubPathResolver.fragment(href) }
    }

    @Synchronized
    override fun read(path: String): EpubResource? {
        val normalized = EpubPathResolver.normalize(path) ?: return null
        val entry = zipFile.getEntry(normalized) ?: entriesByLowercase[normalized.lowercase()] ?: return null
        if (entry.isDirectory) return null
        val bytes = runCatching {
            zipFile.getInputStream(entry).use { input -> input.readBytes(MAX_RESOURCE_BYTES) }
        }.getOrNull() ?: return null
        val item = epubPackage.manifestByPath[normalized]
        val mediaType = item?.mediaType?.ifBlank { null } ?: EpubMimeTypes.fromPath(normalized)
        val resource = EpubResource(normalized, mediaType, bytes)
        val spineItem = epubPackage.spine.firstOrNull { it.manifestItem.fullPath == normalized }
        return if (spineItem != null && (mediaType.contains("html") || mediaType.contains("xhtml"))) {
            resource.copy(bytes = EpubDocumentTransformer.transform(resource, spineItem.renditionLayout))
        } else resource
    }

    @Synchronized
    override fun close() {
        readerFontFiles.clear()
        readerFontKeysByPath.clear()
        zipFile.close()
    }

    companion object {
        const val ASSET_DOMAIN = "appassets.androidplatform.net"
        private const val MAX_RESOURCE_BYTES = 64 * 1024 * 1024
        private const val MAX_READER_FONT_BYTES = 64L * 1024L * 1024L
        private val READER_FONT_EXTENSIONS = setOf("ttf", "otf", "woff", "woff2")
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; " +
                "img-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "script-src 'unsafe-inline'; connect-src 'none'; frame-src 'none'; object-src 'none'"

        fun open(filePath: String, parsedPackage: EpubPackage? = null): EpubRenderSession {
            val epubPackage = parsedPackage ?: EpubPackageReader.read(filePath)
            return EpubRenderSession(epubPackage, ZipFile(filePath))
        }
    }
}

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "EPUB resource exceeds the safety limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
