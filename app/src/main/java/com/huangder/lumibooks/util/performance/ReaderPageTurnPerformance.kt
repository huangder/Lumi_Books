package com.huangder.lumibooks.util.performance

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/** Process-local page-turn markers consumed by Macrobenchmark and Perfetto. */
object ReaderPageTurnPerformance {
    private data class PendingTurn(
        val cookie: Int,
        val preloaded: Boolean,
        val crossChapter: Boolean,
        var visualStarted: Boolean = false
    )

    private val nextCookie = AtomicInteger(1)
    private var pending: PendingTurn? = null

    @Synchronized
    fun beginIntent(preloaded: Boolean, crossChapter: Boolean) {
        if (pending != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val turn = PendingTurn(
            cookie = nextCookie.getAndUpdate { current ->
                if (current == Int.MAX_VALUE) 1 else current + 1
            },
            preloaded = preloaded,
            crossChapter = crossChapter
        )
        pending = turn
        Trace.beginAsyncSection(PAGE_TURN_SECTION, turn.cookie)
        if (preloaded) Trace.beginAsyncSection(PRELOADED_SECTION, turn.cookie)
        if (crossChapter) Trace.beginAsyncSection(CROSS_CHAPTER_SECTION, turn.cookie)
    }

    @Synchronized
    fun markVisualStarted() {
        pending?.visualStarted = true
    }

    @Synchronized
    fun markFirstFrame() {
        val turn = pending?.takeIf(PendingTurn::visualStarted) ?: return
        pending = null
        end(turn)
    }

    @Synchronized
    fun cancel() {
        pending?.let(::end)
        pending = null
    }

    private fun end(turn: PendingTurn) {
        if (turn.crossChapter) Trace.endAsyncSection(CROSS_CHAPTER_SECTION, turn.cookie)
        if (turn.preloaded) Trace.endAsyncSection(PRELOADED_SECTION, turn.cookie)
        Trace.endAsyncSection(PAGE_TURN_SECTION, turn.cookie)
    }

    const val PAGE_TURN_SECTION = "reader_page_turn_to_first_frame"
    const val PRELOADED_SECTION = "reader_preloaded_turn_to_first_frame"
    const val CROSS_CHAPTER_SECTION = "reader_cross_chapter_turn_to_first_frame"
}
