package com.huangder.lumibooks.util.performance

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ReaderOpenStage(val traceName: String) {
    BOOK_RECORD("reader_book_record"),
    PREFERENCES("reader_preferences"),
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

/** Local-only timing hooks consumed by Perfetto and Macrobenchmark. */
object ReaderOpenPerformance {
    private data class Session(
        val cookie: Int,
        val startedAtMs: Long,
        var firstContentDrawn: Boolean = false,
        val activeStages: MutableSet<ReaderOpenStage> = mutableSetOf()
    )

    private val nextCookie = AtomicInteger(1)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val _readyEvents = MutableSharedFlow<ReaderReadyEvent>(extraBufferCapacity = 8)
    val readyEvents: SharedFlow<ReaderReadyEvent> = _readyEvents.asSharedFlow()

    fun start(bookId: String) {
        val session = Session(
            cookie = nextCookie.getAndUpdate { current ->
                if (current == Int.MAX_VALUE) 1 else current + 1
            },
            startedAtMs = SystemClock.elapsedRealtime()
        )
        sessions.put(bookId, session)?.let(::endAbandonedSession)
        beginAsync(FIRST_CONTENT_SECTION, session.cookie)
        beginAsync(INTERACTIVE_SECTION, session.cookie)
    }

    fun markFirstContentDrawn(bookId: String): Long? {
        val session = sessions[bookId] ?: return null
        synchronized(session) {
            if (session.firstContentDrawn) return null
            session.firstContentDrawn = true
            endStageLocked(session, ReaderOpenStage.PAGINATION)
            endStageLocked(session, ReaderOpenStage.FIRST_FRAME)
            beginStageLocked(session, ReaderOpenStage.TRANSITION_EXIT)
            endAsync(FIRST_CONTENT_SECTION, session.cookie)
            val elapsedMs = SystemClock.elapsedRealtime() - session.startedAtMs
            _readyEvents.tryEmit(ReaderReadyEvent.FirstContentDrawn(bookId, elapsedMs))
            return elapsedMs
        }
    }

    fun markInteractive(bookId: String): Long? {
        val session = sessions.remove(bookId) ?: return null
        synchronized(session) {
            endStageLocked(session, ReaderOpenStage.TRANSITION_EXIT)
            endAllStagesLocked(session)
        }
        if (!session.firstContentDrawn) {
            endAsync(FIRST_CONTENT_SECTION, session.cookie)
        }
        endAsync(INTERACTIVE_SECTION, session.cookie)
        val elapsedMs = SystemClock.elapsedRealtime() - session.startedAtMs
        _readyEvents.tryEmit(ReaderReadyEvent.Interactive(bookId, elapsedMs))
        return elapsedMs
    }

    fun cancel(bookId: String) {
        sessions.remove(bookId)?.let(::endAbandonedSession)
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
        synchronized(session) { beginStageLocked(session, stage) }
    }

    fun endStage(bookId: String, stage: ReaderOpenStage) {
        val session = sessions[bookId] ?: return
        synchronized(session) { endStageLocked(session, stage) }
    }

    inline fun <T> traceStage(bookId: String, stage: ReaderOpenStage, block: () -> T): T {
        beginStage(bookId, stage)
        return try {
            block()
        } finally {
            endStage(bookId, stage)
        }
    }

    suspend inline fun <T> traceStageSuspend(
        bookId: String,
        stage: ReaderOpenStage,
        crossinline block: suspend () -> T
    ): T {
        beginStage(bookId, stage)
        return try {
            block()
        } finally {
            endStage(bookId, stage)
        }
    }

    private fun endAbandonedSession(session: Session) {
        synchronized(session) { endAllStagesLocked(session) }
        if (!session.firstContentDrawn) endAsync(FIRST_CONTENT_SECTION, session.cookie)
        endAsync(INTERACTIVE_SECTION, session.cookie)
    }

    private fun beginStageLocked(session: Session, stage: ReaderOpenStage) {
        if (session.activeStages.add(stage)) beginAsync(stage.traceName, session.cookie)
    }

    private fun endStageLocked(session: Session, stage: ReaderOpenStage) {
        if (session.activeStages.remove(stage)) endAsync(stage.traceName, session.cookie)
    }

    private fun endAllStagesLocked(session: Session) {
        session.activeStages.toList().forEach { endStageLocked(session, it) }
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

    private const val FIRST_CONTENT_SECTION = "book_open_to_first_content"
    private const val INTERACTIVE_SECTION = "book_open_to_interactive"
}
