package com.huangder.lumibooks.domain.model

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class BookPositionSource { READING, LISTENING }

/** Orders deliberate actions before asynchronous persistence, independently of wall-clock changes. */
data class BookPositionUse(
    val source: BookPositionSource,
    val processId: String,
    val sequence: Long
) {
    fun canReplace(previous: BookPositionUse?): Boolean = previous == null ||
        processId != previous.processId || sequence > previous.sequence

    fun encode(): String = "${source.name}|$processId|$sequence"

    companion object {
        private val processId = UUID.randomUUID().toString()
        private val sequence = AtomicLong()

        fun create(source: BookPositionSource) = BookPositionUse(source, processId, sequence.incrementAndGet())

        fun decode(raw: String?): BookPositionUse? {
            val fields = raw?.split('|')?.takeIf { it.size == 3 } ?: return null
            val source = BookPositionSource.entries.firstOrNull { it.name == fields[0] } ?: return null
            val process = fields[1].takeIf { it.isNotBlank() } ?: return null
            val order = fields[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return BookPositionUse(source, process, order)
        }
    }
}
