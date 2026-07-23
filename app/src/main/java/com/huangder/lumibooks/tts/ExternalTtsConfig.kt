package com.huangder.lumibooks.tts

import java.util.Locale

enum class ExternalTtsProtocol(val key: String) {
    MIMO_CHAT("mimo_chat"),
    OPENAI_SPEECH("openai_speech");

    companion object {
        fun fromKey(value: String?): ExternalTtsProtocol =
            entries.firstOrNull { it.key == value } ?: OPENAI_SPEECH
    }
}

data class ExternalTtsSettings(
    val enabled: Boolean = false,
    val protocol: ExternalTtsProtocol = ExternalTtsProtocol.OPENAI_SPEECH,
    val baseUrl: String = "",
    val model: String = "",
    val voice: String = "",
    val styleInstructions: String = "",
    val allowHttp: Boolean = false,
    val consentVersion: Int = 0,
    val consentAcceptedAt: Long = 0L
) {
    val hasRequiredFields: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && voice.isNotBlank()

    fun normalized(): ExternalTtsSettings = copy(
        baseUrl = baseUrl.trim().removeSuffix("/"),
        model = model.trim(),
        voice = voice.trim(),
        styleInstructions = styleInstructions.trim()
    )
}

data class ExternalTtsResumePosition(
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val characterOffset: Int,
    val cacheKey: String? = null,
    val pageFingerprint: String? = null,
    val pcmFrameOffset: Long = 0L
)

object ExternalTtsConfig {
    const val CONSENT_VERSION = 1
    const val DEFAULT_MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
    const val DEFAULT_MIMO_MODEL = "mimo-v2.5-tts"
    const val DEFAULT_MIMO_VOICE = "mimo_default"
    const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini-tts"
    const val DEFAULT_OPENAI_VOICE = "coral"
    const val DEFAULT_AUDIO_CACHE_LIMIT_MB = 256
    const val MIN_AUDIO_CACHE_LIMIT_MB = 32
    const val MAX_AUDIO_CACHE_LIMIT_MB = 2_048

    const val TEST_TEXT = "This is a connection test from Lumi Books."

    fun defaults(protocol: ExternalTtsProtocol): ExternalTtsSettings = when (protocol) {
        ExternalTtsProtocol.MIMO_CHAT -> ExternalTtsSettings(
            protocol = protocol,
            baseUrl = DEFAULT_MIMO_BASE_URL,
            model = DEFAULT_MIMO_MODEL,
            voice = DEFAULT_MIMO_VOICE
        )
        ExternalTtsProtocol.OPENAI_SPEECH -> ExternalTtsSettings(
            protocol = protocol,
            baseUrl = DEFAULT_OPENAI_BASE_URL,
            model = DEFAULT_OPENAI_MODEL,
            voice = DEFAULT_OPENAI_VOICE
        )
    }

    fun pitchInstruction(pitch: Float): String {
        val normalized = String.format(Locale.US, "%.2f", pitch.coerceIn(0.5f, 2f))
        return "请以约 ${normalized} 倍的音高自然朗读。"
    }
}
