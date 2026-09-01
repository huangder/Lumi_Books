package com.huangder.lumibooks.util.diagnostics

import org.json.JSONObject

enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR, FATAL }

enum class DiagnosticIssueType(val id: String, val categories: Set<String>) {
    IMPORT("import", setOf("app", "import", "conversion")),
    OPEN_BOOK("open_book", setOf("app", "reader", "import", "conversion")),
    RENDER("render", setOf("app", "reader", "conversion")),
    PAGE_TURN("page_turn", setOf("app", "reader", "interaction")),
    SELECTION("selection", setOf("app", "reader", "interaction")),
    TTS("tts", setOf("app", "tts", "reader")),
    SYNC("sync", setOf("app", "sync")),
    BACKUP("backup", setOf("app", "backup", "conversion")),
    OTHER("other", emptySet());

    companion object {
        fun fromId(value: String?): DiagnosticIssueType = values().firstOrNull { it.id == value } ?: OTHER
    }
}

data class DiagnosticEvent(
    val timestamp: Long,
    val level: DiagnosticLevel,
    val category: String,
    val event: String,
    val sessionId: String? = null,
    val operationId: String? = null,
    val screen: String? = null,
    val bookIdHash: String? = null,
    val bookFormat: String? = null,
    val durationMs: Long? = null,
    val result: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    val stackTrace: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("level", level.name)
        put("category", category)
        put("event", event)
        putNullable("sessionId", sessionId)
        putNullable("operationId", operationId)
        putNullable("screen", screen)
        putNullable("bookIdHash", bookIdHash)
        putNullable("bookFormat", bookFormat)
        putNullable("durationMs", durationMs)
        putNullable("result", result)
        put("attributes", JSONObject(attributes))
        putNullable("exceptionType", exceptionType)
        putNullable("exceptionMessage", exceptionMessage)
        putNullable("stackTrace", stackTrace)
    }

    companion object {
        fun fromJson(json: JSONObject): DiagnosticEvent = DiagnosticEvent(
            timestamp = json.optLong("timestamp"),
            level = runCatching { DiagnosticLevel.valueOf(json.optString("level")) }.getOrDefault(DiagnosticLevel.INFO),
            category = json.optString("category", "unknown"),
            event = json.optString("event", "unknown"),
            sessionId = json.optStringOrNull("sessionId"),
            operationId = json.optStringOrNull("operationId"),
            screen = json.optStringOrNull("screen"),
            bookIdHash = json.optStringOrNull("bookIdHash"),
            bookFormat = json.optStringOrNull("bookFormat"),
            durationMs = json.optLongOrNull("durationMs"),
            result = json.optStringOrNull("result"),
            attributes = json.optJSONObject("attributes")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optString(it) }
            }.orEmpty(),
            exceptionType = json.optStringOrNull("exceptionType"),
            exceptionMessage = json.optStringOrNull("exceptionMessage"),
            stackTrace = json.optStringOrNull("stackTrace")
        )
    }
}

data class DiagnosticSession(
    val id: String,
    val issueType: DiagnosticIssueType,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val recoveredCrash: Boolean = false
)

data class DiagnosticBundleRequest(
    val sessionId: String? = null,
    val issueType: DiagnosticIssueType,
    val userDescription: String,
    val screenshotUris: List<android.net.Uri> = emptyList(),
    val includePreviousCrash: Boolean = true,
    val shareCopyName: String = "lumi-diagnostic.zip"
)

interface DiagnosticLogger {
    fun log(
        category: String,
        event: String,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        operationId: String? = null,
        screen: String? = null,
        bookId: String? = null,
        bookFormat: String? = null,
        durationMs: Long? = null,
        result: String? = null
    )

    fun snapshot(): List<DiagnosticEvent>
    fun activeSession(): DiagnosticSession?
    fun startSession(issueType: DiagnosticIssueType): DiagnosticSession
    fun stopSession(sessionId: String)
    fun recordCrashSynchronously(throwable: Throwable, threadName: String? = null)
    fun hasPreviousCrash(): Boolean
    fun previousCrash(): DiagnosticEvent?
    suspend fun flush()
    fun clearPreviousCrash()
}

interface DiagnosticSessionManager {
    fun start(issueType: DiagnosticIssueType): DiagnosticSession
    fun stop(sessionId: String)
    suspend fun buildBundle(request: DiagnosticBundleRequest): java.io.File
}

/** Bridge for legacy utility singletons that cannot receive Hilt dependencies directly. */
object DiagnosticLoggerRegistry {
    @Volatile
    var logger: DiagnosticLogger? = null
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    if (value == null) put(name, JSONObject.NULL) else put(name, value)
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)
