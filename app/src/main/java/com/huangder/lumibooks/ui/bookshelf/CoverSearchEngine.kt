package com.huangder.lumibooks.ui.bookshelf

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class CoverSearchEngine(
    val queryParameter: String
) {
    BING("q"),
    BAIDU("word"),
    GOOGLE("q");

    fun buildImageSearchUrl(query: String): String {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return when (this) {
            BING -> "https://www.bing.com/images/search?q=$encodedQuery"
            BAIDU -> "https://m.baidu.com/s?word=$encodedQuery&tn=vsearch&pd=image_content"
            GOOGLE -> "https://www.google.com/search?tbm=isch&q=$encodedQuery"
        }
    }

    fun queryFromUrl(url: String): String? {
        val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return rawQuery
            .split('&')
            .asSequence()
            .map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }
            .firstOrNull { (name, _) -> name == queryParameter }
            ?.second
            ?.let { encoded -> runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        fun fromUrl(url: String): CoverSearchEngine? {
            val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return null
            return when {
                host == "bing.com" || host.endsWith(".bing.com") -> BING
                host == "baidu.com" || host.endsWith(".baidu.com") -> BAIDU
                host == "google.com" || host.endsWith(".google.com") ||
                    host.startsWith("www.google.") || host.startsWith("images.google.") -> GOOGLE
                else -> null
            }
        }
    }
}
