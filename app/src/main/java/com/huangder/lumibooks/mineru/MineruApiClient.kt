package com.huangder.lumibooks.mineru

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

data class MineruRemoteResult(
    val resultFile: File,
    val isZip: Boolean
)

class MineruApiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {
    enum class Kind { AUTH, RATE_LIMIT, FILE_LIMIT, PAGE_LIMIT, NETWORK, UPLOAD, SERVICE, INVALID_RESULT }
}

@Singleton
class MineruApiClient @Inject constructor() {
    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(UPLOAD_WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun parse(
        source: File,
        mode: MineruMode,
        token: String?,
        workDirectory: File,
        onProgress: suspend (Int) -> Unit
    ): MineruRemoteResult = withContext(Dispatchers.IO) {
        workDirectory.mkdirs()
        when (mode) {
            MineruMode.AGENT -> parseWithAgent(source, workDirectory, onProgress)
            MineruMode.PRECISE -> parseWithPrecise(
                source,
                token ?: throw MineruApiException(
                    MineruApiException.Kind.AUTH,
                    "Missing MinerU token"
                ),
                workDirectory,
                onProgress
            )
            MineruMode.DISABLED -> throw MineruApiException(
                MineruApiException.Kind.SERVICE,
                "MinerU is disabled"
            )
        }
    }

    private suspend fun parseWithAgent(
        source: File,
        workDirectory: File,
        onProgress: suspend (Int) -> Unit
    ): MineruRemoteResult {
        onProgress(5)
        val fileName = randomPdfName()
        val createResponse = postJson(
            url = "$BASE_URL/api/v1/agent/parse/file",
            body = JSONObject()
                .put("file_name", fileName)
                .put("language", "ch")
                .put("enable_table", true)
                .put("is_ocr", true)
                .put("enable_formula", true)
        )
        val data = requireSuccess(createResponse)
        val taskId = data.optString("task_id").takeIf { it.isNotBlank() }
            ?: invalidResult("Agent API did not return task_id")
        val uploadUrl = data.optString("file_url").takeIf { it.isNotBlank() }
            ?: invalidResult("Agent API did not return file_url")

        uploadFile(source, uploadUrl, onProgress)
        onProgress(40)

        val resultUrl = poll(
            request = { getJson("$BASE_URL/api/v1/agent/parse/$taskId") },
            extractState = { response ->
                val resultData = requireSuccess(response)
                PollState(
                    state = resultData.optString("state"),
                    resultUrl = resultData.optString("markdown_url").takeIf { it.isNotBlank() },
                    error = resultData.optString("err_msg"),
                    failureKind = agentFailureKind(resultData.optString("err_code"))
                )
            },
            onProgress = onProgress
        )

        val markdownFile = File(workDirectory, "full.md")
        download(resultUrl, markdownFile, MAX_MARKDOWN_BYTES)
        onProgress(92)
        return MineruRemoteResult(markdownFile, isZip = false)
    }

    private suspend fun parseWithPrecise(
        source: File,
        token: String,
        workDirectory: File,
        onProgress: suspend (Int) -> Unit
    ): MineruRemoteResult {
        onProgress(5)
        val fileName = randomPdfName()
        val createResponse = postJson(
            url = "$BASE_URL/api/v4/file-urls/batch",
            token = token,
            body = JSONObject()
                .put("files", org.json.JSONArray().put(
                    JSONObject()
                        .put("name", fileName)
                        .put("data_id", UUID.randomUUID().toString())
                        .put("is_ocr", true)
                ))
                .put("model_version", "vlm")
                .put("enable_table", true)
                .put("enable_formula", true)
        )
        val data = requireSuccess(createResponse)
        val batchId = data.optString("batch_id").takeIf { it.isNotBlank() }
            ?: invalidResult("Precise API did not return batch_id")
        val uploadUrl = data.optJSONArray("file_urls")?.optString(0)?.takeIf { it.isNotBlank() }
            ?: invalidResult("Precise API did not return a file upload URL")

        uploadFile(source, uploadUrl, onProgress)
        onProgress(40)

        val resultUrl = poll(
            request = { getJson("$BASE_URL/api/v4/extract-results/batch/$batchId", token) },
            extractState = { response ->
                val resultData = requireSuccess(response)
                val item = resultData.optJSONArray("extract_result")?.optJSONObject(0)
                    ?: invalidResult("Precise API returned no extraction result")
                val progress = item.optJSONObject("extract_progress")
                PollState(
                    state = item.optString("state"),
                    resultUrl = item.optString("full_zip_url").takeIf { it.isNotBlank() },
                    error = item.optString("err_msg"),
                    extractedPages = progress?.optInt("extracted_pages", 0) ?: 0,
                    totalPages = progress?.optInt("total_pages", 0) ?: 0
                )
            },
            onProgress = onProgress
        )

        val zipFile = File(workDirectory, "mineru-result.zip")
        download(resultUrl, zipFile, MAX_RESULT_ZIP_BYTES)
        onProgress(92)
        return MineruRemoteResult(zipFile, isZip = true)
    }

    private suspend fun poll(
        request: () -> JSONObject,
        extractState: (JSONObject) -> PollState,
        onProgress: suspend (Int) -> Unit
    ): String {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActive()
            val state = extractState(request())
            when (state.state) {
                "done" -> return state.resultUrl ?: invalidResult("Completed task has no result URL")
                "failed" -> throw MineruApiException(
                    state.failureKind,
                    state.error.ifBlank { "MinerU parsing failed" }
                )
            }

            val pageProgress = if (state.totalPages > 0) {
                40 + (state.extractedPages * 42 / state.totalPages).coerceIn(0, 42)
            } else {
                40 + (attempt.coerceAtMost(21) * 2)
            }
            onProgress(pageProgress.coerceAtMost(82))
            attempt++
            delay((3_000L + attempt * 250L).coerceAtMost(10_000L))
        }
        throw MineruApiException(MineruApiException.Kind.NETWORK, "MinerU task timed out")
    }

    private suspend fun uploadFile(
        source: File,
        uploadUrl: String,
        onProgress: suspend (Int) -> Unit
    ) {
        validateRemoteUrl(uploadUrl, allowAliyun = true)
        val callerContext = coroutineContext
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun contentLength(): Long = source.length()

            override fun writeTo(sink: BufferedSink) {
                source.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var uploaded = 0L
                    var lastProgress = -1
                    while (true) {
                        callerContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        uploaded += read
                        val ratio = if (source.length() > 0) uploaded.toDouble() / source.length() else 1.0
                        val progress = 10 + (ratio * 28).toInt().coerceIn(0, 28)
                        if (progress != lastProgress) {
                            runBlocking { onProgress(progress) }
                            lastProgress = progress
                        }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Accept", "*/*")
            .put(requestBody)
            .build()
        val call = uploadClient.newCall(request)
        val cancellationHandle = callerContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    mapUploadHttpError(response)
                }
            }
        } catch (error: MineruApiException) {
            throw error
        } catch (error: IOException) {
            callerContext.ensureActive()
            throw MineruApiException(MineruApiException.Kind.NETWORK, "Upload failed", error)
        } finally {
            cancellationHandle.dispose()
        }
    }

    private fun mapUploadHttpError(response: Response): Nothing {
        val storageCode = readLimitedErrorBody(response)
            .let { body -> STORAGE_ERROR_CODE_REGEX.find(body)?.groupValues?.getOrNull(1) }
            ?.take(MAX_STORAGE_ERROR_CODE_CHARS)
            ?.takeIf { it.isNotBlank() }
        val suffix = storageCode?.let { " ($it)" }.orEmpty()
        when (response.code) {
            413 -> throw MineruApiException(MineruApiException.Kind.FILE_LIMIT, "Upload rejected: file too large")
            429 -> throw MineruApiException(MineruApiException.Kind.RATE_LIMIT, "Upload rate limited")
            else -> throw MineruApiException(
                MineruApiException.Kind.UPLOAD,
                "MinerU storage upload failed with HTTP ${response.code}$suffix"
            )
        }
    }

    private fun readLimitedErrorBody(response: Response): String {
        val stream = response.body?.byteStream() ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(1_024)
            val result = StringBuilder()
            while (result.length < MAX_ERROR_BODY_CHARS) {
                val remaining = MAX_ERROR_BODY_CHARS - result.length
                val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                result.append(buffer, 0, read)
            }
            result.toString()
        }
    }

    private fun postJson(url: String, body: JSONObject, token: String? = null): JSONObject {
        val connection = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            readJsonResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String, token: String? = null): JSONObject {
        val connection = openConnection(url).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            readJsonResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readJsonResponse(connection: HttpURLConnection): JSONObject {
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(4_096)
                val result = StringBuilder()
                while (result.length <= MAX_JSON_CHARS) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    result.append(buffer, 0, read)
                }
                if (result.length > MAX_JSON_CHARS) invalidResult("MinerU JSON response is too large")
                result.toString()
            }.orEmpty()
            mapHttpError(status, body)
            return JSONObject(body)
        } catch (error: MineruApiException) {
            throw error
        } catch (error: Exception) {
            throw MineruApiException(MineruApiException.Kind.NETWORK, "Invalid network response", error)
        }
    }

    private fun requireSuccess(response: JSONObject): JSONObject {
        val codeValue = response.opt("code")
        val success = when (codeValue) {
            is Number -> codeValue.toInt() == 0
            is String -> codeValue == "0"
            else -> false
        }
        if (!success) {
            val code = response.optString("code")
            val message = response.optString("msg").ifBlank { "MinerU request failed: $code" }
            val kind = when (code) {
                "A0202", "A0211" -> MineruApiException.Kind.AUTH
                "-30001" -> MineruApiException.Kind.FILE_LIMIT
                "-30003" -> MineruApiException.Kind.PAGE_LIMIT
                "-60005" -> MineruApiException.Kind.FILE_LIMIT
                "-60006" -> MineruApiException.Kind.PAGE_LIMIT
                "-60018", "-60019" -> MineruApiException.Kind.RATE_LIMIT
                else -> MineruApiException.Kind.SERVICE
            }
            throw MineruApiException(kind, message)
        }
        return response.optJSONObject("data") ?: invalidResult("MinerU response has no data")
    }

    private fun download(url: String, destination: File, maxBytes: Long) {
        validateRemoteUrl(url, allowAliyun = false)
        val connection = openConnection(url).apply { requestMethod = "GET" }
        try {
            checkHttpStatus(connection)
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) invalidResult("MinerU result exceeds the download limit")
            destination.parentFile?.mkdirs()
            var total = 0L
            connection.inputStream.buffered().use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) invalidResult("MinerU result exceeds the download limit")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        validateRemoteUrl(url, allowAliyun = url != BASE_URL && !url.startsWith(BASE_URL))
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            useCaches = false
        }
    }

    private fun validateRemoteUrl(url: String, allowAliyun: Boolean) {
        val uri = runCatching { URI(url) }.getOrElse { invalidResult("Invalid MinerU URL") }
        val host = uri.host?.lowercase() ?: invalidResult("MinerU URL has no host")
        val allowed = host == "mineru.net" ||
            host.endsWith(".mineru.net") ||
            host.endsWith(".openxlab.org.cn") ||
            (allowAliyun && host.endsWith(".aliyuncs.com"))
        if (uri.scheme != "https" || !allowed) invalidResult("Untrusted MinerU URL")
    }

    private fun checkHttpStatus(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) mapHttpError(status, "")
    }

    private fun mapHttpError(status: Int, body: String): Unit = when (status) {
        in 200..299 -> Unit
        401, 403 -> throw MineruApiException(MineruApiException.Kind.AUTH, "MinerU authorization failed")
        413 -> throw MineruApiException(MineruApiException.Kind.FILE_LIMIT, "File is too large")
        429 -> throw MineruApiException(MineruApiException.Kind.RATE_LIMIT, "MinerU rate limit reached")
        else -> throw MineruApiException(
            MineruApiException.Kind.SERVICE,
            "MinerU HTTP $status${body.take(160).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        )
    }

    private fun randomPdfName(): String = "document_${UUID.randomUUID()}.pdf"

    private fun agentFailureKind(code: String): MineruApiException.Kind = when (code) {
        "-30001" -> MineruApiException.Kind.FILE_LIMIT
        "-30003" -> MineruApiException.Kind.PAGE_LIMIT
        else -> MineruApiException.Kind.SERVICE
    }

    private fun invalidResult(message: String): Nothing {
        throw MineruApiException(MineruApiException.Kind.INVALID_RESULT, message)
    }

    private data class PollState(
        val state: String,
        val resultUrl: String? = null,
        val error: String = "",
        val failureKind: MineruApiException.Kind = MineruApiException.Kind.SERVICE,
        val extractedPages: Int = 0,
        val totalPages: Int = 0
    )

    private companion object {
        const val BASE_URL = "https://mineru.net"
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val UPLOAD_WRITE_TIMEOUT_MILLIS = 120_000L
        const val POLL_TIMEOUT_MILLIS = 30L * 60L * 1_000L
        const val MAX_JSON_CHARS = 2_000_000
        const val MAX_ERROR_BODY_CHARS = 16_384
        const val MAX_STORAGE_ERROR_CODE_CHARS = 80
        const val MAX_MARKDOWN_BYTES = 64L * 1_024L * 1_024L
        const val MAX_RESULT_ZIP_BYTES = 512L * 1_024L * 1_024L
        val STORAGE_ERROR_CODE_REGEX = Regex("<Code>([^<]+)</Code>")
    }
}
