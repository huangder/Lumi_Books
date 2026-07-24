package com.huangder.lumibooks.tts

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

internal data class MimoRequestMessage(val role: String, val content: String)

internal data class MimoRequestPayload(
    val model: String,
    val messages: List<MimoRequestMessage>,
    val voice: String
)

internal fun buildMimoRequestPayload(
    settings: ExternalTtsSettings,
    text: String
): MimoRequestPayload {
    val messages = buildList {
        if (settings.styleInstructions.isNotBlank()) {
            add(MimoRequestMessage(role = "user", content = settings.styleInstructions))
        }
        add(MimoRequestMessage(role = "assistant", content = text))
    }
    return MimoRequestPayload(
        model = settings.model,
        messages = messages,
        voice = settings.voice
    )
}

private fun MimoRequestPayload.toJson(): JSONObject = JSONObject().apply {
    put("model", model)
    put("messages", JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
            })
        }
    })
    put("stream", true)
    put("audio", JSONObject().apply {
        put("format", "pcm16")
        put("voice", voice)
    })
}
/** One chunk of decoded PCM16LE audio bytes ready for AudioTrack. */
typealias PcmChunk = ByteArray
private const val PCM_FRAME_BYTES = 2

/** Extracts a Base64-decoded PCM chunk from a MiMo SSE data payload. */
internal fun parseMimoDelta(data: String): PcmChunk? {
    if (data.isBlank()) return null
    val root = JSONObject(data)
    val choices = root.optJSONArray("choices") ?: return null
    if (choices.length() == 0) return null
    val choice = choices.optJSONObject(0) ?: return null
    val delta = choice.optJSONObject("delta") ?: return null
    val audio = delta.optJSONObject("audio") ?: return null
    val base64 = audio.optString("data", "")
    if (base64.isEmpty()) return null
    val pcm = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (error: IllegalArgumentException) {
        throw ExternalTtsException.InvalidAudio
    }
    if (pcm.isEmpty() || pcm.size % PCM_FRAME_BYTES != 0) {
        throw ExternalTtsException.InvalidAudio
    }
    return pcm
}
internal fun parseMimoStreamError(data: String): ExternalTtsException? {
    if (data.isBlank()) return null
    val root = JSONObject(data)
    val error = root.optJSONObject("error") ?: return null
    return mapMimoStreamError(
        code = error.optString("code", ""),
        message = error.optString("message", "MiMo streaming request failed")
    )
}

internal fun mapMimoStreamError(code: String, message: String): ExternalTtsException {
    val normalizedCode = code.lowercase()
    val safeMessage = message.ifBlank { "MiMo streaming request failed" }.take(200)
    return when {
        normalizedCode.contains("auth") ||
            normalizedCode.contains("unauthorized") ||
            normalizedCode.contains("permission") -> ExternalTtsException.Unauthorized
        normalizedCode.contains("rate") ||
            normalizedCode.contains("limit") ||
            normalizedCode.contains("quota") -> ExternalTtsException.RateLimited
        else -> ExternalTtsException.Service(200, safeMessage)
    }
}


/**
 * Constructs and executes HTTP requests against MiMo Chat TTS and OpenAI Speech endpoints.
 * Both protocols return 24 kHz mono PCM16LE. The client never follows cross-host redirects
 * and maps every transport failure to an [ExternalTtsException].
 */
class ExternalTtsHttpClient(
    private val tokenStore: ExternalTtsTokenStore
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // ── public API ────────────────────────────────────────────────────

    /**
     * Opens a streaming PCM flow for the given text using the configured protocol.
     * The returned [Flow] must be collected on [Dispatchers.IO]; cancellation of the
     * collecting coroutine cancels the underlying OkHttp [Call].
     */
    suspend fun synthesizeAsPcm(
        settings: ExternalTtsSettings,
        text: String
    ): Flow<PcmChunk> {
        val token = tokenStore.read()
            ?: throw ExternalTtsException.MissingApiKey
        val url = ExternalTtsEndpointValidator.validate(settings.baseUrl, settings.allowHttp)
            .getOrThrow()
        return when (settings.protocol) {
            ExternalTtsProtocol.MIMO_CHAT -> streamMimoChat(url.toString(), token, settings, text)
            ExternalTtsProtocol.OPENAI_SPEECH -> streamOpenAiSpeech(url.toString(), token, settings, text)
        }
    }

    /**
     * Sends a fixed short text to the configured endpoint and verifies:
     * - the HTTP round-trip succeeds (no 4xx/5xx, no timeout),
     * - the response body yields at least one non-empty PCM chunk.
     * Never alters reading state or resume positions.
     */
    suspend fun testConnection(settings: ExternalTtsSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = settings.normalized()
        ExternalTtsEndpointValidator.validate(normalized.baseUrl, normalized.allowHttp)
            .getOrElse { return@withContext Result.failure(it) }
        if (tokenStore.read() == null) {
            return@withContext Result.failure(ExternalTtsException.MissingApiKey)
        }
        try {
            var receivedAnything = false
            synthesizeAsPcm(normalized, ExternalTtsConfig.TEST_TEXT).collect { chunk ->
                if (chunk.isNotEmpty()) receivedAnything = true
            }
            if (!receivedAnything) throw ExternalTtsException.InvalidAudio
            Result.success(Unit)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    // ── MiMo Chat ─────────────────────────────────────────────────────

    private fun streamMimoChat(
        baseUrl: String,
        token: String,
        settings: ExternalTtsSettings,
        text: String
    ): Flow<PcmChunk> = callbackFlow {
        val endpointUrl = "$baseUrl/chat/completions"
        val request = Request.Builder()
            .url(endpointUrl)
            .header("api-key", token)
            .header("Content-Type", "application/json")
            .post(buildMimoRequestBody(settings, text))
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) close(mapNetworkError(e)) else close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        close(mapHttpError(resp))
                        return
                    }
                    try {
                        var receivedAudio = false
                        var receivedDone = false
                        resp.body?.charStream()?.useLines { lines ->
                            for (line in lines) {
                                if (!isActive || call.isCanceled()) break
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                if (data == "[DONE]") {
                                    receivedDone = true
                                    break
                                }
                                parseMimoStreamError(data)?.let { throw it }
                                parseMimoDelta(data)?.let { chunk ->
                                    receivedAudio = true
                                    emitChunk(chunk)
                                }
                            }
                        } ?: throw ExternalTtsException.InvalidAudio
                        if (!receivedDone || !receivedAudio) throw ExternalTtsException.InvalidAudio
                        close()
                    } catch (error: Exception) {
                        close(if (error is ExternalTtsException) error else mapNetworkError(error))
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }


    private fun buildMimoRequestBody(
        settings: ExternalTtsSettings,
        text: String
    ): okhttp3.RequestBody {
        return buildMimoRequestPayload(settings, text).toJson()
            .toString()
            .toRequestBody(MEDIA_TYPE_JSON)
    }



    // ── OpenAI Speech ──────────────────────────────────────────────────

    private fun streamOpenAiSpeech(
        baseUrl: String,
        token: String,
        settings: ExternalTtsSettings,
        text: String
    ): Flow<PcmChunk> = callbackFlow {
        val endpointUrl = "$baseUrl/audio/speech"
        val requestBody = buildOpenAiSpeechRequestBody(settings, text)
        val request = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val mapped = if (call.isCanceled()) null else mapNetworkError(e)
                close(mapped)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        close(mapHttpError(resp))
                        return
                    }
                    val body = resp.body
                    if (body == null || !isPcmContentType(body.contentType()?.toString())) {
                        close(ExternalTtsException.InvalidAudio)
                        return
                    }
                    try {
                        body.byteStream().use { stream ->
                            val buffer = ByteArray(READ_BUFFER_SIZE)
                            var carry: Byte? = null
                            var read: Int
                            while (stream.read(buffer).also { read = it } != -1) {
                                if (!isActive) {
                                    call.cancel()
                                    close()
                                    return
                                }
                                if (read == 0) continue
                                val chunk = if (carry == null) {
                                    buffer.copyOf(read)
                                } else {
                                    ByteArray(read + 1).also {
                                        it[0] = carry
                                        buffer.copyInto(it, destinationOffset = 1, endIndex = read)
                                    }
                                }
                                if (chunk.size % PCM_FRAME_BYTES == 0) {
                                    emitChunk(chunk)
                                    carry = null
                                } else {
                                    carry = chunk.last()
                                    emitChunk(chunk.copyOf(chunk.size - 1))
                                }
                            }
                            if (carry != null) throw ExternalTtsException.InvalidAudio
                        }
                        close()
                    } catch (error: Exception) {
                        close(if (error is ExternalTtsException) error else mapNetworkError(error))
                    }
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }

    /**
     * Bridges OkHttp's callback thread to the flow collector without silently discarding PCM
     * when the collector is temporarily slower than the network source.
     */
    private fun kotlinx.coroutines.channels.ProducerScope<PcmChunk>.emitChunk(chunk: PcmChunk) {
        if (chunk.isEmpty()) return
        runBlocking {
            send(chunk)
        }
    }

    private fun buildOpenAiSpeechRequestBody(
        settings: ExternalTtsSettings,
        text: String
    ): okhttp3.RequestBody {
        val body = JSONObject().apply {
            put("model", settings.model)
            put("input", text)
            put("voice", settings.voice)
            put("response_format", "pcm")
            if (settings.styleInstructions.isNotBlank()) {
                put("instructions", settings.styleInstructions)
            }
        }
        return body.toString().toRequestBody(MEDIA_TYPE_JSON)
    }

    // ── error mapping ──────────────────────────────────────────────────


    private fun mapHttpError(response: Response): ExternalTtsException {
        return when (response.code) {
            401, 403 -> ExternalTtsException.Unauthorized
            429 -> ExternalTtsException.RateLimited
            else -> {
                val message = try {
                    response.peekBody(256).string().trim()
                } catch (_: Exception) {
                    "HTTP ${response.code}"
                }
                ExternalTtsException.Service(response.code, message.take(200))
            }
        }
    }

    private fun isPcmContentType(value: String?): Boolean = value == null ||
        value.startsWith("audio/pcm", ignoreCase = true) ||
        value.startsWith("audio/L16", ignoreCase = true) ||
        value.startsWith("application/octet-stream", ignoreCase = true)

    private fun mapNetworkError(cause: Throwable): ExternalTtsException {
        if (cause is java.net.SocketTimeoutException ||
            cause is java.io.InterruptedIOException
        ) {
            return ExternalTtsException.Network(cause)
        }
        return ExternalTtsException.Network(cause)
    }

    // ── constants ──────────────────────────────────────────────────────

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val READ_WRITE_TIMEOUT_SECONDS = 45L
        const val READ_BUFFER_SIZE = 8192
        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
