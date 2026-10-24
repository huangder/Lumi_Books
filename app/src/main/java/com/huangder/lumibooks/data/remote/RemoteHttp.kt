package com.huangder.lumibooks.data.remote

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

enum class RemoteErrorKind {
    AUTH, FORBIDDEN, NOT_FOUND, SERVER, NETWORK, TIMEOUT, TLS, INVALID_URL,
    NOT_A_CATALOG, OPDS2_UNSUPPORTED, TOO_LARGE, NO_SPACE, LOCAL_NETWORK_PERMISSION,
    UNSUPPORTED_FORMAT, INVALID_FILE, DESTINATION_UNAVAILABLE, UNKNOWN
}

/** Failure of a remote library request, classified so the UI can explain it. */
class RemoteException(
    val kind: RemoteErrorKind,
    val statusCode: Int? = null,
    message: String = kind.name,
    cause: Throwable? = null
) : Exception(message, cause)

internal fun remoteKindForStatus(code: Int): RemoteErrorKind = when (code) {
    401 -> RemoteErrorKind.AUTH
    403 -> RemoteErrorKind.FORBIDDEN
    404, 410 -> RemoteErrorKind.NOT_FOUND
    else -> RemoteErrorKind.SERVER
}

internal fun IOException.toRemoteException(): RemoteException = RemoteException(
    kind = when (this) {
        is SocketTimeoutException -> RemoteErrorKind.TIMEOUT
        is SSLException -> RemoteErrorKind.TLS
        is UnknownHostException -> RemoteErrorKind.NETWORK
        else -> RemoteErrorKind.NETWORK
    },
    message = "${javaClass.simpleName}: ${message.orEmpty()}",
    cause = this
)

/**
 * Answers HTTP Basic and Digest challenges for exactly one origin. Requests to any other host
 * (for example a CDN the catalog redirects to) never receive the credentials.
 */
internal class OriginAuthenticator(
    private val origin: HttpUrl,
    private val username: String,
    private val password: String,
    /** WebDAV servers commonly expect Basic credentials up front, like the sync client sends. */
    preemptiveBasic: Boolean = false
) : Authenticator, Interceptor {
    private class DigestState(
        val realm: String,
        val nonce: String,
        val opaque: String?,
        val qop: String?,
        val algorithm: String?,
        var nonceCount: Int = 0
    )

    @Volatile private var digest: DigestState? = null
    @Volatile private var basicCharset: java.nio.charset.Charset? =
        if (preemptiveBasic) Charsets.ISO_8859_1 else null
    private val random = SecureRandom()

    fun allows(url: HttpUrl): Boolean = url.host.equals(origin.host, ignoreCase = true) && (
        (url.scheme == origin.scheme && url.port == origin.port) ||
            (origin.scheme == "http" && url.scheme == "https")
        )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null || !allows(request.url)) return chain.proceed(request)
        val header = preemptiveHeader(request)
        return chain.proceed(
            if (header == null) request else request.newBuilder().header(AUTHORIZATION, header).build()
        )
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (!allows(request.url) || priorCount(response) >= MAX_ATTEMPTS) return null
        val previous = request.header(AUTHORIZATION)
        val challenges = response.challenges()
        challenges.firstOrNull { it.scheme.equals("Digest", ignoreCase = true) }?.let { challenge ->
            val params = challenge.authParams
            val nonce = params["nonce"] ?: return null
            val stale = params["stale"].equals("true", ignoreCase = true)
            val current = digest
            if (previous?.startsWith("Digest", ignoreCase = true) == true &&
                !stale && current?.nonce == nonce
            ) {
                return null
            }
            val state = DigestState(
                realm = params["realm"].orEmpty(),
                nonce = nonce,
                opaque = params["opaque"],
                qop = params["qop"],
                algorithm = params["algorithm"]
            )
            digest = state
            return request.newBuilder().header(AUTHORIZATION, digestHeader(state, request)).build()
        }
        challenges.firstOrNull { it.scheme.equals("Basic", ignoreCase = true) }?.let { challenge ->
            if (previous?.startsWith("Basic", ignoreCase = true) == true) return null
            val charset = challenge.charset
            basicCharset = charset
            return request.newBuilder()
                .header(AUTHORIZATION, Credentials.basic(username, password, charset))
                .build()
        }
        return null
    }

    private fun preemptiveHeader(request: Request): String? {
        digest?.let { return digestHeader(it, request) }
        return basicCharset?.let { Credentials.basic(username, password, it) }
    }

    private fun digestHeader(state: DigestState, request: Request): String {
        val nonceCount = synchronized(state) { ++state.nonceCount }
        return HttpDigest.authorization(
            username = username,
            password = password,
            realm = state.realm,
            nonce = state.nonce,
            opaque = state.opaque,
            qopOptions = state.qop,
            algorithm = state.algorithm,
            method = request.method,
            uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: ""),
            nonceCount = nonceCount,
            cnonce = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        )
    }

    private fun priorCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MAX_ATTEMPTS = 3
    }
}

/** RFC 7616 digest response; MD5 and SHA-256 (plus their -sess variants). */
internal object HttpDigest {
    fun authorization(
        username: String,
        password: String,
        realm: String,
        nonce: String,
        opaque: String?,
        qopOptions: String?,
        algorithm: String?,
        method: String,
        uri: String,
        nonceCount: Int,
        cnonce: String
    ): String {
        val normalizedAlgorithm = algorithm?.trim()?.uppercase().orEmpty().ifBlank { "MD5" }
        val digestName = if (normalizedAlgorithm.startsWith("SHA-256")) "SHA-256" else "MD5"
        fun hash(value: String) = MessageDigest.getInstance(digestName)
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val nc = "%08x".format(nonceCount)
        var ha1 = hash("$username:$realm:$password")
        if (normalizedAlgorithm.endsWith("-SESS")) ha1 = hash("$ha1:$nonce:$cnonce")
        val ha2 = hash("$method:$uri")
        val qop = qopOptions?.split(',')?.map(String::trim)?.firstOrNull { it.equals("auth", ignoreCase = true) }
        val response = if (qop != null) {
            hash("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            hash("$ha1:$nonce:$ha2")
        }
        return buildString {
            append("Digest username=\"").append(quote(username)).append('"')
            append(", realm=\"").append(quote(realm)).append('"')
            append(", nonce=\"").append(quote(nonce)).append('"')
            append(", uri=\"").append(quote(uri)).append('"')
            if (algorithm != null) append(", algorithm=").append(algorithm.trim())
            append(", response=\"").append(response).append('"')
            if (qop != null) {
                append(", qop=").append(qop)
                append(", nc=").append(nc)
                append(", cnonce=\"").append(cnonce).append('"')
            }
            if (opaque != null) append(", opaque=\"").append(quote(opaque)).append('"')
        }
    }

    private fun quote(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

/** Shared HTTP plumbing for OPDS catalogs: credential scoping, size limits and file downloads. */
object RemoteHttp {
    private const val USER_AGENT = "LumiBooks-Android"

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    fun parseUrl(raw: String): HttpUrl = raw.trim().toHttpUrlOrNull()
        ?.takeIf { it.scheme == "http" || it.scheme == "https" }
        ?: throw RemoteException(RemoteErrorKind.INVALID_URL)

    fun clientFor(
        origin: HttpUrl,
        username: String,
        password: String?,
        preemptiveBasic: Boolean = false
    ): OkHttpClient {
        if (username.isBlank() || password.isNullOrEmpty()) return baseClient
        val authenticator = OriginAuthenticator(origin, username, password, preemptiveBasic)
        return baseClient.newBuilder()
            .addInterceptor(authenticator)
            .authenticator(authenticator)
            .build()
    }

    data class Fetched(val body: ByteArray, val finalUrl: HttpUrl, val contentType: String?)

    fun fetch(client: OkHttpClient, url: HttpUrl, accept: String, maxBytes: Long): Fetched {
        val request = Request.Builder().url(url).header("Accept", accept).get().build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (error: IOException) {
            if (call.isCanceled()) throw CancellationException("Remote request cancelled")
            throw error.toRemoteException()
        }
        response.use {
            if (!it.isSuccessful) throw RemoteException(remoteKindForStatus(it.code), it.code, "HTTP ${it.code}")
            val body = it.body ?: throw RemoteException(RemoteErrorKind.SERVER, it.code, "Empty response")
            if (body.contentLength() > maxBytes) throw RemoteException(RemoteErrorKind.TOO_LARGE)
            val bytes = try {
                body.byteStream().use { input -> readLimited(input, maxBytes) }
            } catch (error: IOException) {
                throw error.toRemoteException()
            }
            return Fetched(bytes, it.request.url, it.header("Content-Type"))
        }
    }

    /** Content length reported by a HEAD request, or 0 when the server does not say. */
    fun contentLength(client: OkHttpClient, url: HttpUrl): Long = runCatching {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
            if (response.isSuccessful) response.header("Content-Length")?.toLongOrNull() ?: 0L else 0L
        }
    }.getOrDefault(0L)

    data class Downloaded(
        val bytesWritten: Long,
        val sha256: String,
        val contentType: String?,
        val contentDisposition: String?,
        val finalUrl: String
    )

    /**
     * Streams [url] into [destination]. [isCancelled] is polled between buffers so a cancelled
     * download stops promptly and the caller can delete the partial file.
     */
    fun download(
        client: OkHttpClient,
        url: HttpUrl,
        destination: File,
        isCancelled: () -> Boolean,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Downloaded {
        val call = client.newCall(Request.Builder().url(url).get().build())
        val response = try {
            call.execute()
        } catch (error: IOException) {
            if (call.isCanceled() || isCancelled()) throw CancellationException("Remote download cancelled")
            throw error.toRemoteException()
        }
        response.use {
            if (!it.isSuccessful) throw RemoteException(remoteKindForStatus(it.code), it.code, "HTTP ${it.code}")
            val body = it.body ?: throw RemoteException(RemoteErrorKind.SERVER, it.code, "Empty response")
            val total = body.contentLength().coerceAtLeast(0L)
            ensureSpace(destination.parentFile, total)
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            try {
                body.byteStream().use { input ->
                    FileOutputStream(destination).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            if (isCancelled()) {
                                call.cancel()
                                throw CancellationException("Remote download cancelled")
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            written += count
                            onProgress(written, total)
                        }
                    }
                }
            } catch (error: IOException) {
                if (isCancelled()) throw CancellationException("Remote download cancelled")
                throw error.toRemoteException()
            }
            return Downloaded(
                bytesWritten = written,
                sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
                contentType = it.header("Content-Type"),
                contentDisposition = it.header("Content-Disposition"),
                finalUrl = it.request.url.toString()
            )
        }
    }

    fun ensureSpace(directory: File?, requiredBytes: Long) {
        val dir = directory ?: return
        dir.mkdirs()
        if (requiredBytes > 0L && dir.usableSpace in 1 until requiredBytes + SPACE_MARGIN_BYTES) {
            throw RemoteException(RemoteErrorKind.NO_SPACE)
        }
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw RemoteException(RemoteErrorKind.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private const val SPACE_MARGIN_BYTES = 16L * 1024 * 1024
}
