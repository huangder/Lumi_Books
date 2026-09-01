package com.huangder.lumibooks.util.diagnostics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) : DiagnosticLogger {
    companion object {
        private const val TAG = "LumiDiagnostic"
        private const val MAX_MEMORY_EVENTS = 4000
        private const val MAX_MEMORY_BYTES = 2L * 1024L * 1024L
        private const val MAX_PERSISTED_BYTES = 2L * 1024L * 1024L
        private const val MAX_VALUE_LENGTH = 256
        private const val SESSION_FILE = "active-session.json"
        private const val CRASH_FILE = "last-crash.json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>(MAX_MEMORY_EVENTS)
    private var memoryBytes = 0L
    private val directory = File(context.filesDir, "diagnostics").also { it.mkdirs() }
    private val eventFile = File(directory, "events.ndjson")
    private val oldEventFile = File(directory, "events.1.ndjson")
    private val crashFile = File(directory, CRASH_FILE)
    private val redactor = DiagnosticRedactor(context)
    private var session: DiagnosticSession? = null
    private val pendingWrites = mutableSetOf<Job>()

    init {
        loadRecentEvents()
        loadSession()
    }

    override fun log(
        category: String,
        event: String,
        level: DiagnosticLevel,
        attributes: Map<String, Any?>,
        throwable: Throwable?,
        operationId: String?,
        screen: String?,
        bookId: String?,
        bookFormat: String?,
        durationMs: Long?,
        result: String?
    ) {
        val diagnosticEvent = DiagnosticEvent(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            event = event,
            sessionId = activeSession()?.id,
            operationId = operationId?.take(MAX_VALUE_LENGTH),
            screen = screen?.take(MAX_VALUE_LENGTH),
            bookIdHash = bookId?.let(redactor::hashIdentifier),
            bookFormat = bookFormat?.take(MAX_VALUE_LENGTH),
            durationMs = durationMs,
            result = result?.take(MAX_VALUE_LENGTH),
            attributes = attributes.mapValues { (_, value) -> redactor.sanitize(value?.toString().orEmpty()) },
            exceptionType = throwable?.javaClass?.name,
            exceptionMessage = throwable?.message?.let(redactor::sanitize)?.take(MAX_VALUE_LENGTH),
            stackTrace = throwable?.let(::stackTrace)?.let(redactor::sanitizeStackTrace)?.take(8192)
        )
        append(diagnosticEvent)
        if (level >= DiagnosticLevel.WARN) {
            Log.println(level.logPriority, TAG, "$category/$event ${diagnosticEvent.attributes}")
        }
    }

    override fun snapshot(): List<DiagnosticEvent> = synchronized(lock) { events.toList() }

    override fun activeSession(): DiagnosticSession? = synchronized(lock) {
        session?.takeIf { it.stoppedAt == null }
    }

    override fun startSession(issueType: DiagnosticIssueType): DiagnosticSession {
        synchronized(lock) {
            session?.takeIf { it.stoppedAt == null }?.let { return it }
        }
        val newSession = DiagnosticSession(UUID.randomUUID().toString(), issueType, System.currentTimeMillis())
        synchronized(lock) {
            session = newSession
            persistSession(newSession)
        }
        log("app", "diagnostic_session_started", attributes = mapOf("issueType" to issueType.id))
        return newSession
    }

    override fun stopSession(sessionId: String) {
        val stopped = synchronized(lock) {
            val current = session?.takeIf { it.id == sessionId } ?: return
            current.copy(stoppedAt = System.currentTimeMillis()).also { persistSession(it) }
        }
        synchronized(lock) { session = null }
        log("app", "diagnostic_session_stopped", attributes = mapOf("sessionId" to stopped.id))
    }

    override fun recordCrashSynchronously(throwable: Throwable, threadName: String?) {
        val crash = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("thread", threadName ?: Thread.currentThread().name)
            put("exceptionType", throwable.javaClass.name)
            put("message", redactor.sanitize(throwable.message.orEmpty()))
            put("stackTrace", redactor.sanitizeStackTrace(stackTrace(throwable)))
            put("sessionId", activeSession()?.id ?: JSONObject.NULL)
        }
        runCatching { crashFile.writeText(crash.toString(), Charsets.UTF_8) }
        log("app", "uncaught_exception", DiagnosticLevel.FATAL, throwable = throwable)
    }

    override fun hasPreviousCrash(): Boolean = crashFile.exists() && crashFile.length() > 0

    override fun previousCrash(): DiagnosticEvent? = runCatching {
        val json = JSONObject(crashFile.readText())
        DiagnosticEvent(
            timestamp = json.optLong("timestamp"),
            level = DiagnosticLevel.FATAL,
            category = "app",
            event = "previous_uncaught_exception",
            sessionId = json.optString("sessionId").takeIf { it.isNotBlank() },
            attributes = mapOf("thread" to json.optString("thread")),
            exceptionType = json.optString("exceptionType"),
            exceptionMessage = json.optString("message"),
            stackTrace = json.optString("stackTrace")
        )
    }.getOrNull()

    override fun clearPreviousCrash() {
        runCatching { crashFile.delete() }
    }

    override suspend fun flush() {
        while (true) {
            val jobs = synchronized(lock) { pendingWrites.toList() }
            if (jobs.isEmpty()) return
            jobs.forEach { it.join() }
        }
    }

    private fun append(event: DiagnosticEvent) {
        synchronized(lock) {
            events.addLast(event)
            memoryBytes += event.toJson().toString().toByteArray(Charsets.UTF_8).size
            trimMemoryBuffer()
        }
        val line = event.toJson().toString() + "\n"
        val job = scope.launch {
            runCatching {
                synchronized(lock) {
                    if (eventFile.length() + line.toByteArray().size > MAX_PERSISTED_BYTES) {
                        runCatching { oldEventFile.delete() }
                        runCatching { eventFile.renameTo(oldEventFile) }
                    }
                    eventFile.appendText(line, Charsets.UTF_8)
                }
            }
        }
        synchronized(lock) { pendingWrites += job }
        job.invokeOnCompletion { synchronized(lock) { pendingWrites -= job } }
    }

    private fun loadRecentEvents() {
        val loaded = buildList {
            listOf(oldEventFile, eventFile).forEach { file ->
                if (!file.exists()) return@forEach
                file.useLines { lines -> lines.forEach { line -> runCatching { add(DiagnosticEvent.fromJson(JSONObject(line))) } } }
            }
        }
        synchronized(lock) {
            loaded.takeLast(MAX_MEMORY_EVENTS).forEach { event ->
                events.addLast(event)
                memoryBytes += event.toJson().toString().toByteArray(Charsets.UTF_8).size
            }
            trimMemoryBuffer()
        }
    }

    private fun trimMemoryBuffer() {
        while (events.size > MAX_MEMORY_EVENTS || memoryBytes > MAX_MEMORY_BYTES) {
            if (events.isEmpty()) break
            val removed = events.removeFirst()
            memoryBytes -= removed.toJson().toString().toByteArray(Charsets.UTF_8).size
        }
    }

    private fun loadSession() {
        val file = File(directory, SESSION_FILE)
        val loaded = runCatching { JSONObject(file.readText()).let { json ->
            DiagnosticSession(
                id = json.optString("id"),
                issueType = DiagnosticIssueType.fromId(json.optString("issueType")),
                startedAt = json.optLong("startedAt"),
                stoppedAt = json.optLong("stoppedAt").takeIf { it > 0 },
                recoveredCrash = hasPreviousCrash()
            )
        } }.getOrNull()
        synchronized(lock) { session = loaded?.takeIf { it.id.isNotBlank() && it.stoppedAt == null } }
    }

    private fun persistSession(value: DiagnosticSession) {
        val file = File(directory, SESSION_FILE)
        runCatching {
            file.writeText(JSONObject().apply {
                put("id", value.id)
                put("issueType", value.issueType.id)
                put("startedAt", value.startedAt)
                put("stoppedAt", value.stoppedAt ?: JSONObject.NULL)
            }.toString(), Charsets.UTF_8)
        }
    }

    private fun stackTrace(error: Throwable): String = StringWriter().also { writer ->
        PrintWriter(writer).use { error.printStackTrace(it) }
    }.toString()
}

private val DiagnosticLevel.logPriority: Int
    get() = when (this) {
        DiagnosticLevel.DEBUG -> Log.DEBUG
        DiagnosticLevel.INFO -> Log.INFO
        DiagnosticLevel.WARN -> Log.WARN
        DiagnosticLevel.ERROR, DiagnosticLevel.FATAL -> Log.ERROR
    }
