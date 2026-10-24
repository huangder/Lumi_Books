package com.huangder.lumibooks.data.remote

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.HttpUrl
import org.w3c.dom.Element

/** One child of a WebDAV collection, resolved against the listed collection. */
data class WebdavListingEntry(
    val url: HttpUrl,
    val name: String,
    val isCollection: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentType: String?
)

/**
 * Parses a `PROPFIND Depth: 1` multistatus body. Unlike the sync listing it tolerates pretty
 * printed XML, absolute hrefs and servers that answer with a deeper listing than requested, and
 * it drops entries pointing at another origin because WebDAV requests carry credentials.
 */
object WebdavListingParser {
    private const val DAV_NS = "DAV:"

    fun parse(xml: ByteArray, collectionUrl: HttpUrl): List<WebdavListingEntry> {
        val head = xml.copyOfRange(0, minOf(xml.size, 4096)).toString(Charsets.ISO_8859_1)
        if (head.contains("<!DOCTYPE", ignoreCase = true)) {
            throw RemoteException(RemoteErrorKind.SERVER, message = "DOCTYPE is not allowed")
        }
        val document = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { isXIncludeAware = false }
                runCatching { setExpandEntityReferences(false) }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        }.getOrElse { error ->
            throw RemoteException(RemoteErrorKind.SERVER, message = "Invalid PROPFIND response", cause = error)
        }
        val collectionSegments = normalizedSegments(collectionUrl)
        val responses = document.getElementsByTagNameNS(DAV_NS, "response")
        val result = mutableListOf<WebdavListingEntry>()
        val seen = mutableSetOf<List<String>>()
        for (index in 0 until responses.length) {
            val response = responses.item(index) as? Element ?: continue
            val href = response.firstText("href")?.trim()?.takeIf(String::isNotBlank) ?: continue
            val url = collectionUrl.resolve(href) ?: continue
            if (!sameOrigin(url, collectionUrl)) continue
            val segments = normalizedSegments(url)
            // Only direct children: skips the collection itself and deeper Depth:infinity rows.
            if (segments.size != collectionSegments.size + 1 ||
                segments.subList(0, collectionSegments.size) != collectionSegments
            ) {
                continue
            }
            if (!seen.add(segments)) continue

            var isCollection = false
            var size = 0L
            var lastModified = 0L
            var contentType: String? = null
            var displayName: String? = null
            val propstats = response.getElementsByTagNameNS(DAV_NS, "propstat")
            for (statIndex in 0 until propstats.length) {
                val propstat = propstats.item(statIndex) as? Element ?: continue
                val status = propstat.firstText("status").orEmpty()
                if (status.isNotBlank() && !Regex("\\s2\\d\\d\\b").containsMatchIn(status)) continue
                val prop = propstat.getElementsByTagNameNS(DAV_NS, "prop").item(0) as? Element ?: continue
                val resourceType = prop.getElementsByTagNameNS(DAV_NS, "resourcetype").item(0) as? Element
                if (resourceType?.getElementsByTagNameNS(DAV_NS, "collection")?.length?.let { it > 0 } == true) {
                    isCollection = true
                }
                prop.firstText("getcontentlength")?.trim()?.toLongOrNull()?.let { size = it }
                prop.firstText("getlastmodified")?.let { lastModified = parseHttpDate(it) }
                prop.firstText("getcontenttype")?.trim()?.takeIf(String::isNotBlank)?.let { contentType = it }
                prop.firstText("displayname")?.trim()?.takeIf(String::isNotBlank)?.let { displayName = it }
            }
            if (href.endsWith('/')) isCollection = true
            val name = segments.last().ifBlank { displayName.orEmpty() }
            result += WebdavListingEntry(
                url = if (isCollection) url.withTrailingSlash() else url,
                name = name.ifBlank { displayName ?: href },
                isCollection = isCollection,
                sizeBytes = size,
                lastModified = lastModified,
                contentType = contentType
            )
        }
        return result
    }

    fun HttpUrl.withTrailingSlash(): HttpUrl =
        if (encodedPath.endsWith('/')) this else newBuilder().addPathSegment("").build()

    /** Decoded path segments without the empty segment of a trailing slash. */
    internal fun normalizedSegments(url: HttpUrl): List<String> =
        url.pathSegments.dropLastWhile(String::isEmpty)

    private fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host.equals(b.host, ignoreCase = true) && a.port == b.port

    private fun Element.firstText(localName: String): String? =
        getElementsByTagNameNS(DAV_NS, localName).item(0)?.textContent

    private fun parseHttpDate(value: String): Long {
        val trimmed = value.trim()
        val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM d HH:mm:ss yyyy")
        for (pattern in patterns) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).parse(trimmed)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }
}
