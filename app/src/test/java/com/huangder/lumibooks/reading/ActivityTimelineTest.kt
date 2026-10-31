package com.huangder.lumibooks.reading

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTimelineTest {
    private var tick = 0L
    private var wall = Instant.parse("2026-10-26T04:00:00Z").toEpochMilli()
    private fun timeline(zone: String = "Asia/Shanghai") = ActivityTimeline({ tick }, { wall }, { ZoneId.of(zone) })
    private fun advance(ms: Long) { tick += ms; wall += ms }

    @Test fun foregroundReadingAndBackgroundAudioAreMutuallyExclusiveEvenAcrossBooks() {
        val timeline = timeline()
        val records = mutableListOf<ActivityIncrement>()
        records += timeline.setReadingBook("reading")
        advance(3_000) // Short reading intervals must survive.
        records += timeline.setListeningBook("audio")
        advance(30_000)
        records += timeline.setReadingBook(null) // Lock screen: audio keeps counting.
        advance(60_000)
        records += timeline.setReadingBook("reading")
        advance(15_000)
        records += timeline.setListeningBook(null)
        advance(2_000) // Audio paused: visible reader resumes.
        records += timeline.setReadingBook(null)
        advance(120_000)
        records += timeline.checkpoint()
        assertEquals(5_000L, records.sumOf { it.readingDuration })
        assertEquals(105_000L, records.sumOf { it.listeningDuration })
        assertTrue(records.filter { it.listeningDuration > 0 }.all { it.bookId == "audio" })
        assertTrue(records.filter { it.readingDuration > 0 }.all { it.bookId == "reading" })
    }

    @Test fun checkpointsAndDuplicateSignalsNeverCountTheSameElapsedTimeTwice() {
        val timeline = timeline()
        timeline.setListeningBook("book")
        advance(15_000)
        val first = timeline.checkpoint()
        assertTrue(timeline.setListeningBook("book").isEmpty())
        advance(15_000)
        val second = timeline.setListeningBook(null)
        assertTrue(timeline.setListeningBook(null).isEmpty())
        assertEquals(30_000L, (first + second).sumOf { it.listeningDuration })
    }

    @Test fun midnightSplitsBothCategoriesAndWallClockChangesDoNotInflateDuration() {
        wall = Instant.parse("2026-10-26T15:59:55Z").toEpochMilli()
        val timeline = timeline()
        timeline.setListeningBook("book")
        advance(15_000)
        wall += 3_600_000 // Changing the clock cannot add an hour of activity.
        val intervals = timeline.checkpoint()
        assertEquals(listOf("2026-10-26", "2026-10-27"), intervals.map { it.date })
        assertEquals(listOf(5_000L, 10_000L), intervals.map { it.listeningDuration })
        timeline.setListeningBook(null)
        timeline.setReadingBook("book")
        advance(1_000)
        assertEquals(1_000L, timeline.checkpoint().single().readingDuration)
    }

    @Test fun localMidnightUsesTheActualDayLengthAcrossDaylightSavingTime() {
        wall = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli() // Midnight in New York.
        val timeline = timeline("America/New_York")
        timeline.setReadingBook("book")
        advance(24 * 3_600_000L)
        val intervals = timeline.checkpoint()
        assertEquals(listOf("2026-03-08", "2026-03-09"), intervals.map { it.date })
        assertEquals(listOf(23 * 3_600_000L, 3_600_000L), intervals.map { it.readingDuration })
    }
}
