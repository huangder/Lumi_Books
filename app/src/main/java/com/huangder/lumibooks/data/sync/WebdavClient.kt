package com.huangder.lumibooks.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

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

    // ── Test connection ─────────────────────────────────────────────

    /** Send a GET to the server root to verify connectivity and auth.
     *  Uses GET instead of PROPFIND because some WebDAV servers reject PROPFIND
     *  on the root path but respond to GET (which is what browsers use). */
    @Throws(WebdavException::class)
    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", authHeader(username, password))
            .get()
            .build()

        val response = client.newCall(request).execute()
        val code = response.code
        response.close()
        when {
            code == 401 -> throw WebdavException("Authentication failed — check username and password")
            code == 404 -> throw WebdavException("Server not found at this address")
            code >= 500 -> throw WebdavException("Server error — HTTP $code")
            // Any 2xx/3xx is success; 403/405 etc. just mean GET isn't allowed,
            // which is fine — the server is there and auth worked (or no auth needed)
        }
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
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw WebdavException("PROPFIND failed — HTTP $code")
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
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw WebdavException("Download failed — HTTP $code")
        }
        val bytes = response.body?.bytes() ?: ByteArray(0)
        response.close()
        bytes
    }

    /** Download as streaming input — caller must close. */
    @Throws(WebdavException::class)
    suspend fun downloadStream(
        url: String,
        username: String,
        password: String
    ): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw WebdavException("Download failed — HTTP $code")
        }
        // Wrap in a closeable that also closes the response
        val bytes = response.body?.bytes() ?: ByteArray(0)
        response.close()
        ByteArrayInputStream(bytes)
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
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .put(data.toRequestBody(contentType.toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw WebdavException("Upload failed — HTTP $code")
        }
        response.close()
    }

    // ── MKCOL (create directory) ────────────────────────────────────

    @Throws(WebdavException::class)
    suspend fun createDirectory(
        url: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .method("MKCOL", null)
            .build()

        val response = client.newCall(request).execute()
        // 405 = already exists (on some servers), 201 = created
        if (!response.isSuccessful && response.code != 405) {
            val code = response.code
            response.close()
            throw WebdavException("MKCOL failed — HTTP $code")
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
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(username, password))
            .delete()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 404) {
            val code = response.code
            response.close()
            throw WebdavException("DELETE failed — HTTP $code")
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
            try {
                createDirectory(current, username, password)
            } catch (_: WebdavException) {
                // 405 or already exists — continue
            }
        }
    }

    // ── XML parsing ─────────────────────────────────────────────────

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
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

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

data class WebdavResource(
    val href: String,
    val isCollection: Boolean,
    val contentLength: Long,
    val lastModified: Long
)

class WebdavException(message: String) : Exception(message)
