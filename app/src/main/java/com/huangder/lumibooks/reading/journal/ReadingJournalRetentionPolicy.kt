package com.huangder.lumibooks.reading.journal

import java.time.Instant
import java.time.ZoneId

object ReadingJournalRetentionPolicy {
    const val CLEANUP_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Rough on-disk size of one event row including index entries, for the storage estimate. */
    const val ESTIMATED_BYTES_PER_EVENT = 160L

    /** Entries that ended before the returned instant are removed; null keeps everything. */
    fun cutoff(retention: JournalRetention, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val amount = retention.amount.coerceIn(1, JournalRetention.MAX_AMOUNT).toLong()
        val start = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = when (retention.unit) {
            JournalRetentionUnit.NEVER -> return null
            JournalRetentionUnit.MONTH -> start.minusMonths(amount)
            JournalRetentionUnit.YEAR -> start.minusYears(amount)
        }
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** True when [next] keeps less history than [previous], i.e. saving it would remove entries. */
    fun isShorter(next: JournalRetention, previous: JournalRetention, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val nextCutoff = cutoff(next, now, zone) ?: return false
        val previousCutoff = cutoff(previous, now, zone) ?: return true
        return nextCutoff > previousCutoff
    }

    fun cleanupDue(lastCleanupAt: Long, now: Long): Boolean =
        lastCleanupAt <= 0L || now < lastCleanupAt || now - lastCleanupAt >= CLEANUP_INTERVAL_MS
}
