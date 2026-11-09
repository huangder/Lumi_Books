package com.huangder.lumibooks.reading.journal

import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.ReadingJournalEventEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ReadingJournalCoreTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(date: String, hour: Int, minute: Int = 0) =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private var ids = 0
    private fun extend(open: ReadingJournalEventEntity?, book: String, listening: Boolean, start: Long, end: Long) =
        JournalSessionMerger.extend(open, book, listening, start, end, end - start, "device", { "id${ids++}" }, zone)

    @Test fun continuousIncrementsExtendOneSession() {
        val first = extend(null, "a", false, at("2026-11-08", 9), at("2026-11-08", 9, 1))
        val second = extend(first, "a", false, at("2026-11-08", 9, 2), at("2026-11-08", 9, 3))
        assertEquals(first.id, second.id)
        assertEquals(at("2026-11-08", 9), second.startedAt)
        assertEquals(at("2026-11-08", 9, 3), second.endedAt)
        assertEquals(2 * 60_000L, second.durationMs)
    }

    @Test fun longPausesOtherBooksModesAndNewDaysStartNewSessions() {
        val open = extend(null, "a", false, at("2026-11-08", 9), at("2026-11-08", 9, 10))
        assertNotEquals(open.id, extend(open, "a", false, at("2026-11-08", 9, 15), at("2026-11-08", 9, 16)).id)
        assertNotEquals(open.id, extend(open, "b", false, at("2026-11-08", 9, 10), at("2026-11-08", 9, 11)).id)
        assertNotEquals(open.id, extend(open, "a", true, at("2026-11-08", 9, 10), at("2026-11-08", 9, 11)).id)
        val late = extend(null, "a", false, at("2026-11-08", 23, 59), at("2026-11-09", 0))
        assertNotEquals(late.id, extend(late, "a", false, at("2026-11-09", 0), at("2026-11-09", 0, 1)).id)
    }

    @Test fun retentionCutoffFollowsCalendarMonthsAndYears() {
        val now = at("2026-11-08", 15)
        assertEquals(at("2026-05-08", 0), ReadingJournalRetentionPolicy.cutoff(JournalRetention(6, JournalRetentionUnit.MONTH), now, zone))
        assertEquals(at("2024-11-08", 0), ReadingJournalRetentionPolicy.cutoff(JournalRetention(2, JournalRetentionUnit.YEAR), now, zone))
        assertNull(ReadingJournalRetentionPolicy.cutoff(JournalRetention(1, JournalRetentionUnit.NEVER), now, zone))
        assertEquals(at("1927-11-08", 0), ReadingJournalRetentionPolicy.cutoff(JournalRetention(500, JournalRetentionUnit.YEAR), now, zone))
    }

    @Test fun savingOnlyCleansUpWhenThePeriodGetsShorter() {
        val now = at("2026-11-08", 15)
        val year = JournalRetention(1, JournalRetentionUnit.YEAR)
        val sixMonths = JournalRetention(6, JournalRetentionUnit.MONTH)
        val never = JournalRetention(1, JournalRetentionUnit.NEVER)
        assertFalse(ReadingJournalRetentionPolicy.isShorter(year, year, now, zone))
        assertFalse(ReadingJournalRetentionPolicy.isShorter(year, sixMonths, now, zone))
        assertFalse(ReadingJournalRetentionPolicy.isShorter(never, year, now, zone))
        assertTrue(ReadingJournalRetentionPolicy.isShorter(sixMonths, year, now, zone))
        assertTrue(ReadingJournalRetentionPolicy.isShorter(year, never, now, zone))
    }

    @Test fun cleanupRunsAtMostDailyAndAfterClockChanges() {
        val now = at("2026-11-08", 12)
        assertTrue(ReadingJournalRetentionPolicy.cleanupDue(0, now))
        assertFalse(ReadingJournalRetentionPolicy.cleanupDue(now - 3_600_000, now))
        assertTrue(ReadingJournalRetentionPolicy.cleanupDue(now - 86_400_000, now))
        assertTrue(ReadingJournalRetentionPolicy.cleanupDue(now + 3_600_000, now))
    }

    private fun book(id: String, progress: Float = 0.2f, read: Boolean = false, want: Long = 0) = BookEntity(
        id = id, title = id, author = "", filePath = "", coverPath = null, format = "EPUB", lastReadTime = 0,
        readingProgress = progress, createdAt = 0, isManuallyMarkedRead = read, wantToReadAt = want
    )

    @Test fun milestonesOnlyFollowChangesAfterTheFirstSnapshot() {
        val detector = JournalMilestoneDetector { zone }
        val now = at("2026-11-08", 20)
        assertTrue(detector.update(listOf(book("a", 1f), book("b", want = 5)), now, "d", 0).isEmpty())
        assertTrue(detector.update(listOf(book("a", 1f), book("b", want = 5), book("c")), now, "d", 0).isEmpty())
        val changed = detector.update(listOf(book("a", 1f), book("b", want = 5), book("c", read = true)), now, "d", 0)
        assertEquals(listOf("finished:c:2026-11-08"), changed.map { it.id })
        assertEquals(ReadingJournalEventEntity.TYPE_FINISHED, changed.single().type)
    }

    @Test fun toReadMilestoneUsesTheMarkTimeAndIgnoresMarksBeforeEnabling() {
        val detector = JournalMilestoneDetector { zone }
        detector.update(listOf(book("a"), book("b")), 0, "d", 100)
        val events = detector.update(listOf(book("a", want = 150), book("b", want = 50)), 200, "d", 100)
        assertEquals(listOf("want:a:150"), events.map { it.id })
        assertEquals(150L, events.single().startedAt)
    }
}
