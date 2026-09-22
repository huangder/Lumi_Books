package com.huangder.lumibooks.data.sync

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** URL construction shared by connection tests and every sync operation. */
object WebdavUrl {

    /**
     * Append raw path segments to a WebDAV base URL while preserving its existing path and query.
     * `HttpUrl` performs the required UTF-8/path escaping, so spaces, Chinese text and `#` in a
     * user supplied sync directory cannot change the request target.
     */
    fun append(baseUrl: String, vararg segments: String): String {
        val base = baseUrl.trim().toHttpUrlOrNull()
            ?: throw WebdavException(
                message = "Invalid WebDAV URL",
                kind = WebdavErrorKind.INVALID_URL
            )
        val builder = base.newBuilder()
        segments
            .asSequence()
            .flatMap { it.trim('/').split('/').asSequence() }
            .filter(String::isNotEmpty)
            .forEach(builder::addPathSegment)
        return builder.build().toString()
    }

    /** Append a segment that already came from a WebDAV href and is therefore percent-encoded. */
    fun appendEncoded(baseUrl: String, encodedSegment: String): String {
        val base = parse(baseUrl)
        return base.newBuilder()
            .addEncodedPathSegment(encodedSegment.trim('/'))
            .build()
            .toString()
    }

    fun host(url: String): String? = url.trim().toHttpUrlOrNull()?.host

    fun parse(url: String): HttpUrl = url.trim().toHttpUrlOrNull()
        ?: throw WebdavException(
            message = "Invalid WebDAV URL",
            kind = WebdavErrorKind.INVALID_URL
        )
}
