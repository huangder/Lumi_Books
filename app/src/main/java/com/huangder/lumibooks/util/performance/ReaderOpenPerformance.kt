package com.huangder.lumibooks.util.performance

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class ReaderOpenStage(val traceName: String) {
    BOOK_RECORD("reader_book_record"),
    PREFERENCES("reader_preferences"),
    PARSER_CREATE("reader_parser_create"),
    METADATA_PARSE("reader_metadata_parse"),
    FIRST_CHAPTER_DECODE("reader_first_chapter_decode"),
    PAGINATION("reader_pagination"),
    FIRST_FRAME("reader_first_content_draw"),
    TRANSITION_EXIT("reader_transition_exit")
}

sealed interface ReaderReadyEvent {
    val bookId: String
    val elapsedMs: Long

    data class FirstContentDrawn(
        override val bookId: String,
        override val elapsedMs: Long
    ) : ReaderReadyEvent

    data class Interactive(
        override val bookId: String,
        override val elapsedMs: Long
    ) : ReaderReadyEvent
}

internal data class ReaderOpenDiagnosticState(
    val phase: String = "navigation",
    val isLoading: Boolean = true,
    val pageReady: Boolean = false,
    val chapterIndex: Int? = null,
    val pageIndex: Int? = null,
    val totalPages: Int? = null,
    val details: Map<String, String> = emptyMap()
)

internal fun readerOpenPendingAttributes(
    elapsedMs: Long,
    activeStages: Collection<ReaderOpenStage>,
    state: ReaderOpenDiagnosticState
): Map<String, Any?> = buildMap {
    put("elapsedMs", elapsedMs)
    put("activeStages", activeStages.map { it.name }.sorted().joinToString(",").ifEmpty { "none" })
    put("phase", state.phase)
    put("isLoading", state.isLoading)
    put("pageReady", state.pageReady)
    state.chapterIndex?.let { put("chapter", it) }
    state.pageIndex?.let { put("page", it) }
    state.totalPages?.let { put("totalPages", it) }
    putAll(state.details)
}

internal data class ReaderOpenStageCompletion(
    val stage: ReaderOpenStage,
    val durationMs: Long,
    val result: String
)

internal class ReaderOpenStageLedger {
    private val startedAtMs = linkedMapOf<ReaderOpenStage, Long>()

    fun begin(stage: ReaderOpenStage, nowMs: Long): Boolean {
        if (startedAtMs.containsKey(stage)) return false
        startedAtMs[stage] = nowMs
        return true
    }

    fun finish(stage: ReaderOpenStage, nowMs: Long, result: String): ReaderOpenStageCompletion? {
        val startedAt = startedAtMs.remove(stage) ?: return null
        return ReaderOpenStageCompletion(stage, (nowMs - startedAt).coerceAtLeast(0L), result)
    }

    fun finishAll(nowMs: Long, result: String): List<ReaderOpenStageCompletion> =
        startedAtMs.keys.toList().mapNotNull { finish(it, nowMs, result) }

    fun activeStages(): List<ReaderOpenStage> = startedAtMs.keys.toList()
}

/** Local timing hooks consumed by Perfetto, Macrobenchmark, and diagnostic builds. */
object ReaderOpenPerformance {
    private data class Session(
        val bookId: String,
        val cookie: Int,
        val operationId: String,
        val startedAtMs: Long,
        var firstContentDrawn: Boolean = false,
        val stages: ReaderOpenStageLedger = ReaderOpenStageLedger(),
        var diagnosticState: ReaderOpenDiagnosticState = ReaderOpenDiagnosticState(),
        var bookFormat: String? = null,
        var watchdogJob: Job? = null
    )

    private val nextCookie = AtomicInteger(1)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _readyEvents = MutableSharedFlow<ReaderReadyEvent>(extraBufferCapacity = 8)
    val readyEvents: SharedFlow<ReaderReadyEvent> = _readyEvents.asSharedFlow()

    fun start(bookId: String) {
        val cookie = nextCookie.getAndUpdate { current ->
            if (current == Int.MAX_VALUE) 1 else current + 1
        }
        val session = Session(
            bookId = bookId,
            cookie = cookie,
            operationId = "reader-open-$cookie",
            startedAtMs = SystemClock.elapsedRealtime()
        )
        sessions.put(bookId, session)?.let { previous ->
            endAbandonedSession(previous, "superseded")
        }
        beginAsync(FIRST_CONTENT_SECTION, session.cookie)
        beginAsync(INTERACTIVE_SECTION, session.cookie)
        log(
            session = session,
            event = "open_started",
            attributes = mapOf("buildType" to BuildConfig.BUILD_TYPE)
        )
        startWatchdog(session)
    }

    fun operationId(bookId: String): String? = sessions[bookId]?.operationId

    fun recordDiagnosticEvent(
        bookId: String,
        event: String,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        result: String? = null
    ) {
        val session = sessions[bookId] ?: return
        log(
            session = session,
            event = event,
            level = level,
            attributes = attributes,
            throwable = throwable,
            result = result
        )
    }

    fun updateState(
        bookId: String,
        phase: String? = null,
        isLoading: Boolean? = null,
        pageReady: Boolean? = null,
        bookFormat: String? = null,
        chapterIndex: Int? = null,
        pageIndex: Int? = null,
        totalPages: Int? = null,
        details: Map<String, Any?> = emptyMap(),
        event: String? = null
    ) {
        val session = sessions[bookId] ?: return
        val snapshot = synchronized(session) {
            bookFormat?.let { session.bookFormat = it }
            session.diagnosticState.copy(
                phase = phase ?: session.diagnosticState.phase,
                isLoading = isLoading ?: session.diagnosticState.isLoading,
                pageReady = pageReady ?: session.diagnosticState.pageReady,
                chapterIndex = chapterIndex ?: session.diagnosticState.chapterIndex,
                pageIndex = pageIndex ?: session.diagnosticState.pageIndex,
                totalPages = totalPages ?: session.diagnosticState.totalPages,
                details = session.diagnosticState.details + details.mapValues { it.value?.toString().orEmpty() }
            ).also { session.diagnosticState = it }
        }
        if (event != null) {
            log(
                session = session,
                event = event,
                attributes = readerOpenPendingAttributes(
                    elapsedMs = SystemClock.elapsedRealtime() - session.startedAtMs,
                    activeStages = synchronized(session) { session.stages.activeStages() },
                    state = snapshot
                )
            )
        }
    }

    fun markFirstContentDrawn(bookId: String): Long? {
        val session = sessions[bookId] ?: return null
        synchronized(session) {
            if (session.firstContentDrawn) return null
            session.firstContentDrawn = true
        }
        finishStage(bookId, ReaderOpenStage.PAGINATION, "success")
        finishStage(bookId, ReaderOpenStage.FIRST_FRAME, "success")
        beginStage(bookId, ReaderOpenStage.TRANSITION_EXIT)
        endAsync(FIRST_CONTENT_SECTION, session.cookie)
        val elapsedMs = SystemClock.elapsedRealtime() - session.startedAtMs
        _readyEvents.tryEmit(ReaderReadyEvent.FirstContentDrawn(bookId, elapsedMs))
        log(
            session = session,
            event = "first_content_drawn",
            durationMs = elapsedMs,
            result = "success"
        )
        return elapsedMs
    }

    fun markInteractive(bookId: String): Long? {
        val session = sessions[bookId] ?: return null
        finishStage(bookId, ReaderOpenStage.TRANSITION_EXIT, "success")
        if (!sessions.remove(bookId, session)) return null
        session.watchdogJob?.cancel()
        val unfinished = finishAllStages(session, "ended_at_interactive")
        if (!session.firstContentDrawn) endAsync(FIRST_CONTENT_SECTION, session.cookie)
        endAsync(INTERACTIVE_SECTION, session.cookie)
        val elapsedMs = SystemClock.elapsedRealtime() - session.startedAtMs
        _readyEvents.tryEmit(ReaderReadyEvent.Interactive(bookId, elapsedMs))
        log(
            session = session,
            event = "open_interactive",
            attributes = mapOf(
                "firstContentDrawn" to session.firstContentDrawn,
                "unfinishedStages" to unfinished.joinToString(",") { it.name }.ifEmpty { "none" }
            ),
            durationMs = elapsedMs,
            result = "success"
        )
        return elapsedMs
    }

    fun cancel(bookId: String) = cancel(bookId, "navigation_cancelled")

    fun cancel(bookId: String, reason: String) {
        sessions.remove(bookId)?.let { endAbandonedSession(it, reason) }
    }

    inline fun <T> trace(section: String, block: () -> T): T {
        val tracing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        if (tracing) Trace.beginSection(section)
        return try {
            block()
        } finally {
            if (tracing) Trace.endSection()
        }
    }

    fun beginStage(bookId: String, stage: ReaderOpenStage) {
        val session = sessions[bookId] ?: return
        val started = synchronized(session) {
            session.stages.begin(stage, SystemClock.elapsedRealtime()).also { began ->
                if (began) {
                beginAsync(stage.traceName, session.cookie)
                }
            }
        }
        if (started) {
            log(session, "open_stage_started", attributes = mapOf("stage" to stage.name))
        }
    }

    fun endStage(bookId: String, stage: ReaderOpenStage) {
        finishStage(bookId, stage, "success")
    }

    fun <T> traceStage(bookId: String, stage: ReaderOpenStage, block: () -> T): T {
        beginStage(bookId, stage)
        return try {
            block().also { finishStage(bookId, stage, "success") }
        } catch (error: Throwable) {
            finishStage(
                bookId = bookId,
                stage = stage,
                result = if (error is CancellationException) "cancelled" else "failed",
                throwable = error
            )
            throw error
        }
    }

    suspend fun <T> traceStageSuspend(
        bookId: String,
        stage: ReaderOpenStage,
        block: suspend () -> T
    ): T {
        beginStage(bookId, stage)
        return try {
            block().also { finishStage(bookId, stage, "success") }
        } catch (error: Throwable) {
            finishStage(
                bookId = bookId,
                stage = stage,
                result = if (error is CancellationException) "cancelled" else "failed",
                throwable = error
            )
            throw error
        }
    }

    private fun startWatchdog(session: Session) {
        if (!BuildConfig.DIAGNOSTIC_BUILD) return
        session.watchdogJob = diagnosticScope.launch {
            delay(FIRST_SLOW_CHECKPOINT_MS)
            logPendingIfActive(session, FIRST_SLOW_CHECKPOINT_MS)
            delay(SECOND_SLOW_CHECKPOINT_MS - FIRST_SLOW_CHECKPOINT_MS)
            logPendingIfActive(session, SECOND_SLOW_CHECKPOINT_MS)
        }
    }

    private fun logPendingIfActive(session: Session, checkpointMs: Long) {
        if (sessions[session.bookId] !== session) return
        val (activeStages, state) = synchronized(session) {
            session.stages.activeStages() to session.diagnosticState
        }
        log(
            session = session,
            event = "open_still_pending",
            level = DiagnosticLevel.WARN,
            attributes = readerOpenPendingAttributes(checkpointMs, activeStages, state),
            durationMs = checkpointMs,
            result = "pending"
        )
    }

    private fun finishStage(
        bookId: String,
        stage: ReaderOpenStage,
        result: String,
        throwable: Throwable? = null
    ) {
        val session = sessions[bookId] ?: return
        val completion = synchronized(session) {
            val finished = session.stages.finish(stage, SystemClock.elapsedRealtime(), result) ?: return
            endAsync(stage.traceName, session.cookie)
            finished
        }
        log(
            session = session,
            event = "open_stage_finished",
            level = when {
                throwable == null -> DiagnosticLevel.INFO
                throwable is CancellationException -> DiagnosticLevel.INFO
                else -> DiagnosticLevel.ERROR
            },
            attributes = mapOf("stage" to stage.name),
            throwable = throwable,
            durationMs = completion.durationMs,
            result = result
        )
    }

    private fun finishAllStages(session: Session, result: String): List<ReaderOpenStage> {
        val now = SystemClock.elapsedRealtime()
        val completions = synchronized(session) { session.stages.finishAll(now, result) }
        completions.forEach { completion ->
            endAsync(completion.stage.traceName, session.cookie)
            log(
                session = session,
                event = "open_stage_finished",
                attributes = mapOf("stage" to completion.stage.name),
                durationMs = completion.durationMs,
                result = result
            )
        }
        return completions.map { it.stage }
    }

    private fun endAbandonedSession(session: Session, reason: String) {
        session.watchdogJob?.cancel()
        val unfinished = finishAllStages(session, reason)
        if (!session.firstContentDrawn) endAsync(FIRST_CONTENT_SECTION, session.cookie)
        endAsync(INTERACTIVE_SECTION, session.cookie)
        log(
            session = session,
            event = "open_cancelled",
            level = DiagnosticLevel.WARN,
            attributes = mapOf(
                "reason" to reason,
                "activeStages" to unfinished.joinToString(",") { it.name }.ifEmpty { "none" }
            ),
            durationMs = SystemClock.elapsedRealtime() - session.startedAtMs,
            result = reason
        )
    }

    private fun log(
        session: Session,
        event: String,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        durationMs: Long? = null,
        result: String? = null
    ) {
        if (!BuildConfig.DIAGNOSTIC_BUILD) return
        DiagnosticLoggerRegistry.logger?.log(
            category = "reader",
            event = event,
            level = level,
            attributes = attributes,
            throwable = throwable,
            operationId = session.operationId,
            screen = "Reader",
            bookId = session.bookId,
            bookFormat = session.bookFormat,
            durationMs = durationMs,
            result = result
        )
    }

    private fun beginAsync(section: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(section, cookie)
        }
    }

    private fun endAsync(section: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(section, cookie)
        }
    }

    private const val FIRST_SLOW_CHECKPOINT_MS = 10_000L
    private const val SECOND_SLOW_CHECKPOINT_MS = 30_000L
    private const val FIRST_CONTENT_SECTION = "book_open_to_first_content"
    private const val INTERACTIVE_SECTION = "book_open_to_interactive"
}
