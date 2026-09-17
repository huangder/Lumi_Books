package com.huangder.lumibooks.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlinx.coroutines.CancellationException
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

/**
 * Lightweight WebDAV client over OkHttp.
 * Supports PROPFIND (list), GET (download), PUT (upload), MKCOL (mkdir), DELETE.
 */
@Singleton
class WebdavClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)  // long write for book files
        .followRedirects(true)
        .build()

    // ── Authentication ──────────────────────────────────────────────

    private fun authHeader(username: String, password: String): String =
        Credentials.basic(username, password)

    // ── URL helpers ─────────────────────────────────────────────────

    private fun joinUrl(base: String, vararg segments: String): String {
        val sb = StringBuilder(base.trimEnd('/'))
        for (s in segments) {
            if (s.isEmpty()) continue
            sb.append('/')
            sb.append(s.trim('/'))
        }
        return sb.toString()
    }

    // ── Request helpers ────────────────────────────────────────────

    /** Build a request builder with the shared Authorization header.
     *  A malformed address (missing scheme, illegal characters, ...) is reported as a
     *  [WebdavException] so callers never have to handle [IllegalArgumentException]. */
    private fun requestFor(url: String, username: String, password: String): Request.Builder =
        try {
            Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
        } catch (error: IllegalArgumentException) {
            // Deliberately does not echo the typed address: it may contain embedded credentials.
            throw WebdavException(
                message = "Invalid WebDAV URL",
                kind = WebdavErrorKind.INVALID_URL,
                cause = error
            )
        }

    /** Execute [request], converting transport failures (DNS, timeout, TLS, refused, ...) into
     *  [WebdavException] with a classified [WebdavErrorKind]. Cancelled calls stay cancellations. */
    private fun execute(request: Request): okhttp3.Response {
        val call = client.newCall(request)
        return try {
            call.execute()
        } catch (error: IOException) {
            if (call.isCanceled()) {
                throw CancellationException("WebDAV request cancelled")
            }
            throw WebdavException(
                message = "Network error — ${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                kind = classifyTransportError(error),
                cause = error
            )
        }
    }

    private fun classifyTransportError(error: IOException): WebdavErrorKind = when (error) {
        is SocketTimeoutException -> WebdavErrorKind.TIMEOUT
        is SSLException -> WebdavErrorKind.TLS
        is UnknownHostException -> WebdavErrorKind.NETWORK
        else -> WebdavErrorKind.NETWORK
    }

    // ── Test connection ─────────────────────────────────────────────

    /**
     * Read-only WebDAV capability probe: `PROPFIND` with `Depth: 0`.
     *
     * GET is deliberately not used: fetching a collection is not a valid WebDAV operation and
     * plenty of servers (Nextcloud's `/remote.php/dav/` for example) answer it with 404, which
     * used to make a perfectly valid configuration look broken.
     *
     * Returns the final HTTP status code (200/207 mean the server speaks WebDAV and accepted the
     * credentials). Throws [WebdavException] only for transport failures / malformed addresses.
     *
     * Some servers only serve a collection at the `.../` form, so when the address has no trailing
     * slash and comes back as a redirect or 404/405 we retry once with a trailing slash.
     */
    @Throws(WebdavException::class)
    suspend fun probeCollection(
        url: String,
        username: String,
        password: String
    ): Int = withContext(Dispatchers.IO) {
        val base = url.trim()
        val withSlash = if (base.endsWith('/')) base else "$base/"

        var code = propfindStatus(base, username, password)
        val shouldRetry = (code in REDIRECT_CODES || code == 404 || code == 405) && withSlash != base
        if (shouldRetry) {
            code = propfindStatus(withSlash, username, password)
        }
        code
    }

    private fun propfindStatus(url: String, username: String, password: String): Int {
        val request = requestFor(url, username, password)
            .header("Depth", "0")
            .method("PROPFIND", PROPFIND_BODY_DEPTH_0.toRequestBody(XML_MEDIA_TYPE))
            .build()
        return execute(request).use { it.code }
    }

    // ── PROPFIND (list directory) ───────────────────────────────────

    /**
     * List all files/directories at [url] (Depth: 1).
     * Returns a map of href → {isCollection, contentLength, lastModified}.
     */
    @Throws(WebdavException::class)
    suspend fun listDirectory(
        url: String,
        username: String,
        password: String
    ): List<WebdavResource> = withContext(Dispatchers.IO) {
        val body = PROPFIND_BODY_DEPTH_1.toRequestBody(XML_MEDIA_TYPE)
        val request = requestFor(url, username, password)
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()

        val response = execute(request)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw httpException("PROPFIND", code)
        }

        val xml = response.body?.string() ?: ""
        response.close()
        parsePropfindResponse(xml, url)
    }

    // ── GET (download) ──────────────────────────────────────────────

    @Throws(WebdavException::class)
    suspend fun download(
        url: String,
        username: String,
        password: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .get()
            .build()

        val response = execute(request)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw httpException("Download", code)
        }
        val bytes = response.body?.bytes() ?: ByteArray(0)
        response.close()
        bytes
    }

    @Throws(WebdavException::class)
    suspend fun downloadVersioned(
        url: String,
        username: String,
        password: String
    ): WebdavVersionedData = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .get()
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw httpException("Download", response.code)
            }
            WebdavVersionedData(
                data = response.body?.bytes() ?: ByteArray(0),
                etag = response.header("ETag")
            )
        }
    }

    /** Download as streaming input — caller must close. */
    @Throws(WebdavException::class)
    suspend fun downloadStream(
        url: String,
        username: String,
        password: String
    ): InputStream = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .get()
            .build()

        val response = execute(request)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw httpException("Download", code)
        }
        // Wrap in a closeable that also closes the response
        val bytes = response.body?.bytes() ?: ByteArray(0)
        response.close()
        ByteArrayInputStream(bytes)
    }

    @Throws(WebdavException::class)
    suspend fun downloadToFile(
        url: String,
        destination: File,
        username: String,
        password: String,
        expectedSize: Long = 0L,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): WebdavDownloadResult = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .get()
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw httpException("Download", response.code)
            }
            val body = response.body ?: throw WebdavException("Download failed — empty response")
            val totalBytes = expectedSize.takeIf { it > 0L }
                ?: body.contentLength().takeIf { it > 0L }
                ?: 0L
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesRead = 0L
            destination.parentFile?.mkdirs()
            body.byteStream().buffered().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        bytesRead += count
                        onProgress(bytesRead, totalBytes)
                    }
                }
            }
            WebdavDownloadResult(
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                bytesWritten = bytesRead,
                totalBytes = totalBytes
            )
        }
    }

    // ── PUT (upload) ────────────────────────────────────────────────

    @Throws(WebdavException::class)
    suspend fun upload(
        url: String,
        data: ByteArray,
        username: String,
        password: String,
        contentType: String = "application/octet-stream"
    ) = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .put(data.toRequestBody(contentType.toMediaType()))
            .build()

        val response = execute(request)
        if (!response.isSuccessful) {
            throw uploadException("PUT", request.url.toString(), response)
        }
        response.close()
    }

    @Throws(WebdavException::class)
    suspend fun uploadConditional(
        url: String,
        data: ByteArray,
        username: String,
        password: String,
        etag: String?,
        contentType: String = "application/octet-stream"
    ): String? = withContext(Dispatchers.IO) {
        val builder = requestFor(url, username, password)
            .put(data.toRequestBody(contentType.toMediaType()))
        if (etag != null) builder.header("If-Match", etag) else builder.header("If-None-Match", "*")
        val request = builder.build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw uploadException("PUT_CONDITIONAL", request.url.toString(), response)
            }
            response.header("ETag")
        }
    }

    /** Atomically replace a WebDAV resource when the server supports the standard MOVE method. */
    @Throws(WebdavException::class)
    suspend fun move(
        sourceUrl: String,
        destinationUrl: String,
        username: String,
        password: String,
        overwrite: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val request = requestFor(sourceUrl, username, password)
            .header("Destination", destinationUrl)
            .header("Overwrite", if (overwrite) "T" else "F")
            .method("MOVE", null)
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw httpException("MOVE", response.code)
            }
        }
    }

    @Throws(WebdavException::class)
    suspend fun uploadStream(
        url: String,
        contentLength: Long,
        inputStreamProvider: () -> InputStream,
        username: String,
        password: String,
        contentType: String = "application/octet-stream"
    ): WebdavUploadResult = withContext(Dispatchers.IO) {
        val requestBody = object : RequestBody() {
            private val digest = MessageDigest.getInstance("SHA-256")
            private var finalizedSha256: String? = null
            private var writtenBytes: Long = 0L

            override fun contentType() = contentType.toMediaType()

            override fun contentLength(): Long = if (contentLength >= 0L) contentLength else -1L

            override fun writeTo(sink: BufferedSink) {
                digest.reset()
                writtenBytes = 0L
                inputStreamProvider().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        sink.write(buffer, 0, read)
                        writtenBytes += read.toLong()
                    }
                }
                finalizedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            }

            fun result(): WebdavUploadResult = WebdavUploadResult(
                sha256 = finalizedSha256.orEmpty(),
                bytesWritten = writtenBytes
            )
        }

        val request = requestFor(url, username, password)
            .put(requestBody)
            .build()

        val response = execute(request)
        if (!response.isSuccessful) {
            throw uploadException("PUT_STREAM", request.url.toString(), response)
        }
        response.close()
        requestBody.result()
    }

    // ── MKCOL (create directory) ────────────────────────────────────

    @Throws(WebdavException::class)
    suspend fun createDirectory(
        url: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .method("MKCOL", null)
            .build()

        val response = execute(request)
        // 405 = already exists (on some servers), 201 = created
        if (!response.isSuccessful && response.code != 405) {
            val code = response.code
            response.close()
            // 409 means the parent collection does not exist, i.e. the configured address does not
            // point at an existing directory. Report it separately from a generic HTTP failure.
            if (code == 409) {
                throw WebdavException(
                    message = "MKCOL failed — HTTP 409 (parent collection missing)",
                    statusCode = code,
                    serverCode = PARENT_COLLECTION_MISSING,
                    kind = WebdavErrorKind.CONFLICT
                )
            }
            throw httpException("MKCOL", code)
        }
        response.close()
    }

    // ── DELETE ──────────────────────────────────────────────────────

    @Throws(WebdavException::class)
    suspend fun delete(
        url: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val request = requestFor(url, username, password)
            .delete()
            .build()

        val response = execute(request)
        if (!response.isSuccessful && response.code != 404) {
            val code = response.code
            response.close()
            throw httpException("DELETE", code)
        }
        response.close()
    }

    // ── Ensure directory exists ─────────────────────────────────────

    /** Recursively create [path] segments under the server root. */
    suspend fun ensureDirectory(
        serverUrl: String,
        username: String,
        password: String,
        path: String
    ) {
        val segments = path.trim('/').split('/')
        var current = serverUrl
        for (seg in segments) {
            current = joinUrl(current, seg)
            createDirectory(current, username, password)
        }
    }

    // ── XML parsing ─────────────────────────────────────────────────

    /** Build an exception for a non-2xx HTTP response, keeping the status code and its meaning. */
    private fun httpException(operation: String, code: Int): WebdavException = WebdavException(
        message = "$operation failed — HTTP $code",
        statusCode = code,
        kind = WebdavFailureClassifier.kindForStatus(code)
    )

    private fun uploadException(operation: String, url: String, response: okhttp3.Response): WebdavException {
        val code = response.code
        val rawBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val detail = rawBody
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(300)
        val server = response.header("Server").orEmpty()
        val requestId = response.header("X-Request-Id")
            ?: response.header("X-Nutstore-Request-Id")
            ?: response.header("X-Cloud-Trace-Context")
        response.close()

        Log.e(
            TAG,
            "$operation failed: HTTP $code, url=$url, server=$server, requestId=${requestId.orEmpty()}, body=$detail"
        )
        DiagnosticLoggerRegistry.logger?.log(
            category = "sync",
            event = "http_request_failed",
            level = DiagnosticLevel.ERROR,
            attributes = mapOf(
                "operation" to operation,
                "statusCode" to code,
                "server" to server,
                "requestId" to requestId,
                "responseBodyPresent" to detail.isNotBlank()
            )
        )
        val suffix = if (detail.isBlank()) "" else " - $detail"
        val quotaMatch = Regex(
            "free\\s+rate\\s+is\\s+(\\d+)\\s+while\\s+you\\s+want\\s+to\\s+consume\\s+(\\d+)",
            RegexOption.IGNORE_CASE
        ).find(detail)
        val serverCode = detail.substringBefore(' ').takeIf { token ->
            token.isNotBlank() && token.matches(Regex("[A-Za-z][A-Za-z0-9_]+"))
        }
        return WebdavException(
            message = "HTTP $code$suffix",
            statusCode = code,
            kind = WebdavFailureClassifier.kindForStatus(code),
            serverCode = serverCode,
            availableBytes = quotaMatch?.groupValues?.getOrNull(1)?.toLongOrNull(),
            requiredBytes = quotaMatch?.groupValues?.getOrNull(2)?.toLongOrNull(),
            serverDetail = detail.takeIf { it.isNotBlank() }
        )
    }

    private fun parsePropfindResponse(xml: String, baseUrl: String): List<WebdavResource> {
        val resources = mutableListOf<WebdavResource>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            for (i in 0 until responses.length) {
                val element = responses.item(i) as? Element ?: continue
                val href = element.getElementsByTagNameNS("DAV:", "href")
                    .item(0)?.textContent?.trim('/') ?: continue

                // Skip the directory itself
                val basePath = URL(baseUrl).path.trimEnd('/')
                if (href == basePath.trimStart('/')) continue

                val props = element.getElementsByTagNameNS("DAV:", "propstat")
                val isCollection = element.getElementsByTagNameNS("DAV:", "resourcetype")
                    ?.item(0)?.childNodes?.item(0)?.localName == "collection"

                var contentLength = 0L
                var lastModified = 0L

                for (j in 0 until props.length) {
                    val propStat = props.item(j) as? Element ?: continue
                    val status = propStat.getElementsByTagNameNS("DAV:", "status")
                        .item(0)?.textContent ?: ""
                    if (!status.contains("200")) continue

                    val prop = propStat.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element ?: continue
                    prop.getElementsByTagNameNS("DAV:", "getcontentlength")
                        .item(0)?.textContent?.toLongOrNull()?.let { contentLength = it }
                    prop.getElementsByTagNameNS("DAV:", "getlastmodified")
                        .item(0)?.textContent?.let { lastModified = parseHttpDate(it) }
                }

                resources.add(
                    WebdavResource(
                        href = href,
                        isCollection = isCollection,
                        contentLength = contentLength,
                        lastModified = lastModified
                    )
                )
            }
        } catch (e: ParserConfigurationException) {
            // fall through — return empty
        } catch (_: Exception) {
            // fall through
        }
        return resources
    }

    private fun parseHttpDate(date: String): Long {
        return try {
            // RFC 1123: Mon, 02 Jan 2006 15:04:05 GMT
            val formats = listOf(
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            )
            for (fmt in formats) {
                try {
                    return fmt.parse(date)?.time ?: continue
                } catch (_: Exception) { }
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }

    // ── Constants ───────────────────────────────────────────────────

    private companion object {
        const val TAG = "WebDAV"
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

        /** Redirects that mean "this collection lives at another (usually slashed) URL". */
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        /** Marker for MKCOL 409 responses so the UI can explain the missing parent directory. */
        const val PARENT_COLLECTION_MISSING = "ParentCollectionMissing"

        const val PROPFIND_BODY_DEPTH_0 = """
<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
  </D:prop>
</D:propfind>
"""

        const val PROPFIND_BODY_DEPTH_1 = """
<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
  </D:prop>
</D:propfind>
"""
    }
}

data class WebdavUploadResult(
    val sha256: String,
    val bytesWritten: Long
)

data class WebdavDownloadResult(
    val sha256: String,
    val bytesWritten: Long,
    val totalBytes: Long
)

data class WebdavVersionedData(
    val data: ByteArray,
    val etag: String?
)

data class WebdavResource(
    val href: String,
    val isCollection: Boolean,
    val contentLength: Long,
    val lastModified: Long
)

class WebdavException(
    message: String,
    val statusCode: Int? = null,
    val serverCode: String? = null,
    val availableBytes: Long? = null,
    val requiredBytes: Long? = null,
    val serverDetail: String? = null,
    val kind: WebdavErrorKind = WebdavErrorKind.UNKNOWN,
    cause: Throwable? = null
) : Exception(message, cause)
