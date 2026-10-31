package com.huangder.lumibooks.util.performance

import kotlin.math.abs

/**
 * Debug-only logcat trace for book-layout paging. Each event is one line,
 * `t=<uptimeMs> ev=<name> key=value ...`, so tools/measure-epub-cross-chapter.cjs
 * can join gestures, page slots and WebView loads on one clock.
 */
internal object EpubTurnTrace {
    const val TAG = "EpubTurnTrace"

    @JvmField
    val enabled: Boolean = com.huangder.lumibooks.BuildConfig.DEBUG

    fun log(event: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        // Plain JVM unit tests have no Android stubs; tracing must never fail a caller.
        runCatching {
            android.util.Log.d(TAG, format(android.os.SystemClock.uptimeMillis(), event, *fields))
        }
    }

    /** Null values are omitted; spaces inside a value would split it into two tokens. */
    fun format(timeMs: Long, event: String, vararg fields: Pair<String, Any?>): String = buildString {
        append("t=").append(timeMs).append(" ev=").append(event)
        fields.forEach { (key, value) ->
            if (value == null) return@forEach
            append(' ').append(key).append('=').append(value.toString().replace(' ', '_'))
        }
    }

    /** Index-aligned diff of two `|`-joined configuration keys. */
    fun configKeyDiff(old: String, new: String, limit: Int = 6): String {
        if (old.isEmpty()) return "initial"
        if (old == new) return "same"
        val before = old.split('|')
        val after = new.split('|')
        val changed = (0 until maxOf(before.size, after.size))
            .filter { before.getOrNull(it) != after.getOrNull(it) }
        return buildString {
            if (before.size != after.size) {
                append("tokens:").append(before.size).append("->").append(after.size).append(',')
            }
            changed.take(limit).joinTo(this, ",") { index ->
                "#$index:${before.getOrNull(index)?.take(24)}->${after.getOrNull(index)?.take(24)}"
            }
            if (changed.size > limit) append(",+").append(changed.size - limit)
        }
    }
}

/**
 * Timing of one touch stream: when a page intent became clear and when the
 * page sheets actually began to follow the finger.
 */
internal class EpubGestureTrace(private val slopPx: Float) {
    private var active = false
    private var downX = 0f
    private var downY = 0f
    var intentAt = 0L
        private set
    private var movedAt = 0L
    var deltaX = 0f
        private set
    var deltaY = 0f
        private set
    /** Host state captured at intent time; appended to the summary. */
    val fields = mutableListOf<Pair<String, Any?>>()

    fun down(timeMs: Long, x: Float, y: Float) {
        active = true
        downX = x
        downY = y
        intentAt = 0L
        movedAt = 0L
        deltaX = 0f
        deltaY = 0f
        fields.clear()
    }

    /** True exactly once per stream, when the paging axis passes the slop and dominates. */
    fun move(timeMs: Long, x: Float, y: Float, vertical: Boolean): Boolean {
        if (!active) return false
        deltaX = x - downX
        deltaY = y - downY
        if (intentAt != 0L) return false
        val along = abs(if (vertical) deltaY else deltaX)
        val across = abs(if (vertical) deltaX else deltaY)
        if (along <= slopPx || along <= across) return false
        intentAt = timeMs
        return true
    }

    fun moved(timeMs: Long) {
        if (!active || movedAt != 0L) return
        movedAt = timeMs
        if (intentAt == 0L) intentAt = timeMs
    }

    /** Null for taps and streams that never showed a page intent. */
    fun finish(timeMs: Long, cancelled: Boolean): List<Pair<String, Any?>>? {
        if (!active) return null
        active = false
        if (intentAt == 0L) return null
        return listOf(
            "moved" to if (movedAt != 0L) 1 else 0,
            "waitMs" to if (movedAt != 0L) (movedAt - intentAt).coerceAtLeast(0L) else null,
            "heldMs" to timeMs - intentAt,
            "dx" to deltaX.toInt(),
            "dy" to deltaY.toInt(),
            "cancel" to if (cancelled) 1 else null
        ) + fields
    }
}
