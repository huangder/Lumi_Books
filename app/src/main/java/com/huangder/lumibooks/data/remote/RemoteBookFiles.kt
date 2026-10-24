package com.huangder.lumibooks.data.remote

import java.net.URLDecoder

/** Pure helpers shared by the WebDAV and OPDS sources: formats, file names and identities. */
object RemoteBookFiles {
    /** Extensions the existing import pipeline can read, in download preference order. */
    val SUPPORTED_EXTENSIONS = listOf("epub", "pdf", "mobi", "cbz", "md", "markdown", "txt")

    private val MIME_EXTENSIONS = mapOf(
        "application/epub+zip" to "epub",
        "application/pdf" to "pdf",
        "application/x-mobipocket-ebook" to "mobi",
        "application/x-mobi" to "mobi",
        "application/vnd.comicbook+zip" to "cbz",
        "application/x-cbz" to "cbz",
        "text/markdown" to "md",
        "text/x-markdown" to "md",
        "text/plain" to "txt"
    )

    /** Labels for formats a catalog may offer but the importer cannot read. */
    private val UNSUPPORTED_MIME_LABELS = mapOf(
        "application/x-mobi8-ebook" to "AZW3",
        "application/vnd.amazon.ebook" to "AZW",
        "application/x-fictionbook+xml" to "FB2",
        "application/fb2+zip" to "FB2",
        "application/x-zip-compressed-fb2" to "FB2",
        "application/vnd.comicbook-rar" to "CBR",
        "application/x-cbr" to "CBR",
        "application/rtf" to "RTF",
        "application/x-djvu" to "DJVU",
        "image/vnd.djvu" to "DJVU",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "DOCX",
        "application/x-cb7" to "CB7"
    )

    private val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f\\u007f]")
    private const val MAX_FILE_NAME_LENGTH = 120

    fun normalizeMimeType(mimeType: String?): String? = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    fun extensionForMimeType(mimeType: String?): String? =
        normalizeMimeType(mimeType)?.let(MIME_EXTENSIONS::get)

    fun unsupportedLabelForMimeType(mimeType: String?): String? {
        val normalized = normalizeMimeType(mimeType) ?: return null
        return UNSUPPORTED_MIME_LABELS[normalized]
            ?: normalized.substringAfterLast('/').substringAfterLast('.').uppercase().take(8)
    }

    fun extensionFromName(name: String?): String? = name
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { it in SUPPORTED_EXTENSIONS }

    fun mimeTypeForExtension(extension: String?): String = when (extension?.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "cbz" -> "application/vnd.comicbook+zip"
        "md", "markdown" -> "text/markdown"
        else -> "text/plain"
    }

    fun formatPreference(extension: String?): Int =
        SUPPORTED_EXTENSIONS.indexOf(extension).takeIf { it >= 0 } ?: Int.MAX_VALUE

    fun displayFormat(extension: String?): String = when (extension?.lowercase()) {
        null, "" -> ""
        "markdown" -> "MD"
        else -> extension.uppercase()
    }

    /** Removes path separators and characters that SAF providers or FAT volumes reject. */
    fun sanitizeFileName(raw: String?): String? {
        val cleaned = raw
            ?.replace(INVALID_FILE_NAME_CHARS, "_")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trim('.', ' ')
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: return null
        if (cleaned.length <= MAX_FILE_NAME_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 1..10 }
        return if (extension == null) {
            cleaned.take(MAX_FILE_NAME_LENGTH).trimEnd('.', ' ')
        } else {
            cleaned.dropLast(extension.length + 1)
                .take(MAX_FILE_NAME_LENGTH - extension.length - 1)
                .trimEnd('.', ' ') + "." + extension
        }
    }

    /** RFC 6266 file name, preferring the UTF-8 `filename*` form. */
    fun fileNameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val extended = Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            val decoded = runCatching {
                URLDecoder.decode(extended.groupValues[2].trim().trim('"').replace("+", "%2B"), charset)
            }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        val quoted = Regex("filename\\s*=\\s*\"((?:\\\\.|[^\"])*)\"", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.get(1)
            ?.replace(Regex("\\\\(.)"), "$1")
        if (!quoted.isNullOrBlank()) return quoted
        return Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    /** Last decoded path segment of a URL, or null when the URL ends in a directory. */
    fun fileNameFromUrl(url: String?): String? = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.let { segment -> runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment) }
        ?.takeIf(String::isNotBlank)

    /**
     * Picks a safe local file name with an importable extension. The server's name wins, then the
     * catalog title; the extension falls back to the format the user chose in the catalog.
     */
    fun resolveDownloadFileName(
        contentDisposition: String?,
        responseMimeType: String?,
        url: String,
        title: String?,
        expectedExtension: String?
    ): String {
        val serverName = fileNameFromContentDisposition(contentDisposition)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
        val urlName = fileNameFromUrl(url)
        val candidates = listOfNotNull(serverName, urlName)
        val named = candidates.firstOrNull { extensionFromName(it) != null }
        val extension = extensionFromName(named)
            ?: expectedExtension?.lowercase()?.takeIf { it in SUPPORTED_EXTENSIONS }
            ?: extensionForMimeType(responseMimeType)
            ?: "epub"
        val baseName = when {
            named != null -> named.substringBeforeLast('.')
            !title.isNullOrBlank() -> title
            else -> serverName?.substringBeforeLast('.') ?: urlName?.substringBeforeLast('.')
        }
        val sanitizedBase = sanitizeFileName(baseName)?.takeIf { it.isNotBlank() } ?: "book"
        return sanitizeFileName("$sanitizedBase.$extension") ?: "book.$extension"
    }

    /** Remote identity that never stores credentials or query strings with tokens in plain text. */
    fun remoteDocumentKey(sourceId: String, itemKey: String): String =
        "remote:" + sha256Hex("$sourceId\n$itemKey").take(40)

    fun sha256Hex(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
