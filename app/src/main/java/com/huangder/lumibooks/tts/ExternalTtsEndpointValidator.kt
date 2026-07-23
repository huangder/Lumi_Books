package com.huangder.lumibooks.tts

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ExternalTtsEndpointValidator {
    private val sensitiveQueryNames = setOf(
        "api_key", "apikey", "key", "token", "access_token", "authorization"
    )

    fun validate(rawUrl: String, allowHttp: Boolean): Result<HttpUrl> {
        val url = rawUrl.trim().toHttpUrlOrNull()
            ?: return Result.failure(ExternalTtsException.InvalidConfiguration("Invalid service URL"))
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return Result.failure(ExternalTtsException.InvalidConfiguration("Service URL must not include credentials"))
        }
        if (url.host.isBlank()) {
            return Result.failure(ExternalTtsException.InvalidConfiguration("Service URL must include a host"))
        }
        if (url.query != null || url.fragment != null) {
            return Result.failure(
                ExternalTtsException.InvalidConfiguration("Service URL must not include query or fragment data")
            )
        }
        if (url.scheme != "https" && !(allowHttp && url.scheme == "http")) {
            return Result.failure(ExternalTtsException.InsecureEndpoint)
        }
        return Result.success(url)
    }
}

sealed class ExternalTtsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InsecureEndpoint : ExternalTtsException("External TTS requires HTTPS")
    class InvalidConfiguration(message: String) : ExternalTtsException(message)
    data object MissingApiKey : ExternalTtsException("External TTS API key is missing")
    data object Unauthorized : ExternalTtsException("External TTS authentication failed")
    data object RateLimited : ExternalTtsException("External TTS rate limit reached")
    data object InvalidAudio : ExternalTtsException("External TTS returned invalid PCM audio")
    class Network(cause: Throwable) : ExternalTtsException("External TTS network request failed", cause)
    class Service(val statusCode: Int, message: String) : ExternalTtsException(message)
}
