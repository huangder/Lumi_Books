package com.huangder.lumibooks.translation

import org.json.JSONObject

enum class TranslationProvider(val baseUrl: String, val auth: TranslationAuth) {
    MIMO("https://api.xiaomimimo.com/v1", TranslationAuth.API_KEY),
    DEEPSEEK("https://api.deepseek.com/v1", TranslationAuth.BEARER),
    OPENAI("https://api.openai.com/v1", TranslationAuth.BEARER),
    CUSTOM("", TranslationAuth.BEARER)
}

enum class TranslationAuth { BEARER, API_KEY }

data class TranslationSettings(
    val enabled: Boolean = false,
    val provider: TranslationProvider = TranslationProvider.MIMO,
    val baseUrl: String = TranslationProvider.MIMO.baseUrl,
    val auth: TranslationAuth = TranslationAuth.API_KEY,
    val model: String = "",
    val targetLanguage: String = "简体中文",
    val allowHttp: Boolean = false,
    // Changes only when credentials are replaced, so active requests can be invalidated.
    val credentialVersion: String = ""
) {
    fun normalized() = copy(baseUrl = baseUrl.trim().trimEnd('/'), model = model.trim(),
        targetLanguage = targetLanguage.trim(), auth = if (provider == TranslationProvider.CUSTOM) auth else provider.auth)

    val credentialScope: String get() = "${normalized().auth}|${normalized().baseUrl}"

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("provider", provider.name); put("baseUrl", baseUrl)
        put("auth", auth.name); put("model", model); put("targetLanguage", targetLanguage)
        put("allowHttp", allowHttp); put("credentialVersion", credentialVersion)
    }.toString()

    companion object {
        fun fromJson(raw: String?): TranslationSettings = runCatching {
            val json = JSONObject(raw ?: return TranslationSettings())
            val provider = TranslationProvider.valueOf(json.optString("provider", "MIMO"))
            TranslationSettings(json.optBoolean("enabled"), provider,
                json.optString("baseUrl", provider.baseUrl),
                TranslationAuth.valueOf(json.optString("auth", provider.auth.name)),
                json.optString("model"), json.optString("targetLanguage", "简体中文"),
                json.optBoolean("allowHttp"), json.optString("credentialVersion"))
        }.getOrDefault(TranslationSettings())
    }
}

data class TranslationConfiguration(val settings: TranslationSettings, val hasToken: Boolean) {
    val enabled: Boolean get() = settings.enabled && hasToken
}

enum class TranslationError {
    CONFIGURATION, MISSING_KEY, UNAUTHORIZED, RATE_LIMITED, MODEL_UNAVAILABLE,
    EMPTY_RESPONSE, NETWORK, TIMEOUT, SERVICE, STORAGE
}

class TranslationException(val error: TranslationError) : Exception(error.name)

sealed interface TranslationState {
    data object Hidden : TranslationState
    data object Manual : TranslationState
    data object Loading : TranslationState
    data class Success(val text: String) : TranslationState
    data class Failure(val error: TranslationError) : TranslationState
}
