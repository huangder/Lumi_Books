package com.huangder.lumibooks.data.sync

/**
 * Why a WebDAV call failed. Every failure path classifies itself with one of these so the UI can
 * explain what actually went wrong instead of falling back to a generic "request failed".
 */
enum class WebdavErrorKind {
    /** 401 — credentials rejected. */
    AUTH,

    /** 403 — authenticated but the path is not permitted (often "not a writable directory"). */
    FORBIDDEN,

    /** 404 — the configured address or path does not exist on the server. */
    NOT_FOUND,

    /** 409 — the parent collection a resource should be created in does not exist. */
    CONFLICT,

    /** 405/501 — the address is not a WebDAV endpoint that supports the required method. */
    NOT_SUPPORTED,

    /** 5xx — the server itself failed. */
    SERVER_ERROR,

    /** DNS, refused connection, dropped connection, ... */
    NETWORK,

    /** Connect/read/write timeout. */
    TIMEOUT,

    /** TLS handshake or certificate validation failure (e.g. self-signed certificate). */
    TLS,

    /** The configured address could not be parsed as an http(s) URL. */
    INVALID_URL,

    /** The server redirected the DAV request in an unsafe or unusable way. */
    REDIRECT,

    /** The server returned a response that is incompatible with the DAV probe. */
    INVALID_RESPONSE,

    UNKNOWN
}

/** User-facing bucket derived from a [WebdavErrorKind] plus an optional HTTP status code. */
enum class WebdavFailureCategory {
    AUTH,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    NOT_SUPPORTED,
    SERVER_ERROR,
    NETWORK,
    TIMEOUT,
    TLS,
    QUOTA,
    INVALID_URL,
    REDIRECT,
    INVALID_RESPONSE,
    UNKNOWN
}

/**
 * Pure mapping between transport/HTTP failures and the message bucket shown to the user.
 *
 * Kept free of Android dependencies so it can be unit tested directly.
 */
object WebdavFailureClassifier {

    /** Server code used by some providers when the upload traffic quota is exhausted. */
    const val QUOTA_SERVER_CODE = "TrafficRateExhausted"

    fun isSuccessStatus(statusCode: Int): Boolean = statusCode in 200..299

    /** Resolve an HTTP status code to a [WebdavErrorKind]. */
    fun kindForStatus(statusCode: Int): WebdavErrorKind = when (statusCode) {
        401 -> WebdavErrorKind.AUTH
        403 -> WebdavErrorKind.FORBIDDEN
        404 -> WebdavErrorKind.NOT_FOUND
        405, 501 -> WebdavErrorKind.NOT_SUPPORTED
        in 500..599 -> WebdavErrorKind.SERVER_ERROR
        else -> WebdavErrorKind.UNKNOWN
    }

    fun classify(
        kind: WebdavErrorKind,
        statusCode: Int?,
        serverCode: String? = null
    ): WebdavFailureCategory {
        if (serverCode == QUOTA_SERVER_CODE) return WebdavFailureCategory.QUOTA
        return when (kind) {
            WebdavErrorKind.AUTH -> WebdavFailureCategory.AUTH
            WebdavErrorKind.FORBIDDEN -> WebdavFailureCategory.FORBIDDEN
            WebdavErrorKind.NOT_FOUND -> WebdavFailureCategory.NOT_FOUND
            WebdavErrorKind.CONFLICT -> WebdavFailureCategory.CONFLICT
            WebdavErrorKind.NOT_SUPPORTED -> WebdavFailureCategory.NOT_SUPPORTED
            WebdavErrorKind.SERVER_ERROR -> WebdavFailureCategory.SERVER_ERROR
            WebdavErrorKind.NETWORK -> WebdavFailureCategory.NETWORK
            WebdavErrorKind.TIMEOUT -> WebdavFailureCategory.TIMEOUT
            WebdavErrorKind.TLS -> WebdavFailureCategory.TLS
            WebdavErrorKind.INVALID_URL -> WebdavFailureCategory.INVALID_URL
            WebdavErrorKind.REDIRECT -> WebdavFailureCategory.REDIRECT
            WebdavErrorKind.INVALID_RESPONSE -> WebdavFailureCategory.INVALID_RESPONSE
            WebdavErrorKind.UNKNOWN -> statusCode
                ?.let(::kindForStatus)
                ?.takeIf { it != WebdavErrorKind.UNKNOWN }
                ?.let { classify(it, statusCode, serverCode) }
                ?: WebdavFailureCategory.UNKNOWN
        }
    }
}
